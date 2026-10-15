package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.springframework.http.MediaType;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.util.Map;

/** Single PUT publication; all upload validation completes before any storage mutation. */
public final class MinioObjectStorageAdapter implements ObjectStoragePort {
    private final MinioStorageProperties properties;
    private final S3Client client;
    private final MinioObjectKeyCodec keys;
    private final MinioMetadataCodec metadata;

    public MinioObjectStorageAdapter(MinioStorageProperties properties, S3Client client, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        keys = new MinioObjectKeyCodec(properties.keyPrefix(), properties.maxKeyBytes());
        metadata = new MinioMetadataCodec(mapper, properties.maxMetadataBytes());
    }
    @Override public ObjectMetadata store(StoreObjectCommand command) {
        if (command == null) throw invalid();
        String physical = keys.encode(command.metadata().key());
        validateContentType(command.metadata().contentType());
        String envelope = metadata.encode(command.metadata());
        boolean createOnly = command.mode() == StoreMode.CREATE_ONLY;
        try (var staged = MinioStagedUpload.create(command, properties.stagingDirectory(), properties.maxObjectBytes())) {
            var request = PutObjectRequest.builder().bucket(properties.bucket()).key(physical)
                    .contentLength(command.metadata().contentLength()).contentType(command.metadata().contentType())
                    .metadata(Map.of("dxos-envelope-v1", envelope));
            if (createOnly) request.ifNoneMatch("*");
            client.putObject(request.build(), RequestBody.fromFile(staged.file()));
            return command.metadata();
        } catch (RuntimeException error) { throw MinioStorageErrorMapper.map(error, createOnly); }
    }
    @Override public ObjectContent read(ObjectKey key) {
        String physical = keys.encode(key);
        ResponseInputStream<GetObjectResponse> response = null;
        try {
            response = client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(physical).build());
            var description = metadata.decode(key, response.response().contentLength(), response.response().metadata());
            // Abort on close: partial reads release the connection without draining an arbitrarily large object.
            var owned = response;
            var stream = new java.io.FilterInputStream(owned) {
                @Override public void close() { owned.abort(); }
            };
            return new MinioObjectContent(description, stream);
        } catch (RuntimeException error) {
            if (response != null) try { response.abort(); } catch (RuntimeException ignored) { /* sanitized primary error */ }
            throw MinioStorageErrorMapper.map(resolveAccessFailure(error, physical), false);
        }
    }
    @Override public ObjectMetadata metadata(ObjectKey key) {
        String physical = keys.encode(key);
        try {
            var info = head(physical);
            return metadata.decode(key, info.contentLength(), info.metadata());
        } catch (RuntimeException error) { throw MinioStorageErrorMapper.map(error, false); }
    }
    @Override public void delete(ObjectKey key) {
        String physical = keys.encode(key);
        try {
            head(physical); // S3 DELETE is idempotent; the port requires a missing-key error.
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(physical).build());
        } catch (RuntimeException error) { throw MinioStorageErrorMapper.map(error, false); }
    }
    private HeadObjectResponse head(String physical) {
        try {
            return client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(physical).build());
        } catch (S3Exception error) {
            // HEAD has no error body. A scoped LIST distinguishes a missing bucket from a missing key.
            if (error.statusCode() == 404) {
                client.listObjectsV2(ListObjectsV2Request.builder().bucket(properties.bucket())
                        .prefix(physical).maxKeys(1).build());
                throw new StorageException(StorageErrorCode.NOT_FOUND);
            }
            throw resolveAccessFailure(error, physical);
        }
    }
    private RuntimeException resolveAccessFailure(RuntimeException error, String physical) {
        // Without unscoped ListBucket permission S3 may hide missing keys behind 403.
        // A scoped list is allowed by our policy; never infer absence from 403 alone.
        if (error instanceof S3Exception s3 && s3.statusCode() == 403) {
            try {
                var listing = client.listObjectsV2(ListObjectsV2Request.builder().bucket(properties.bucket())
                        .prefix(physical).maxKeys(1).build());
                if (listing.contents().stream().noneMatch(o -> physical.equals(o.key())))
                    return new StorageException(StorageErrorCode.NOT_FOUND);
            } catch (RuntimeException listFailure) { return listFailure; }
        }
        return error;
    }
    private static void validateContentType(String value) {
        try {
            if (value.chars().anyMatch(c -> c < 32 || c > 126)) throw invalid();
            MediaType parsed = MediaType.parseMediaType(value);
            if (parsed.isWildcardType() || parsed.isWildcardSubtype()) throw invalid();
        } catch (RuntimeException error) { throw invalid(); }
    }
    private static StorageException invalid() { return new StorageException(StorageErrorCode.INVALID_INPUT); }
}
