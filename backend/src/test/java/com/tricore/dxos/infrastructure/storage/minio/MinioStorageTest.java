package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.tricore.dxos.infrastructure.storage.minio.MinioTestSupport.*;

class MinioStorageTest {
    @TempDir Path directory;
    final ObjectKey key = new ObjectKey(" ../ Mixed/Key %2f ");
    final ObjectMapper mapper = new ObjectMapper();
    S3Client client;
    MinioStorageProperties p;
    MinioObjectStorageAdapter adapter;
    @BeforeEach void setup() {
        client = mock(S3Client.class); p = properties(directory);
        adapter = new MinioObjectStorageAdapter(p, client, mapper);
    }
    static S3Exception s3(int status, String code) {
        var builder = S3Exception.builder();
        builder.statusCode(status); builder.message("provider secret");
        builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage("secret").build());
        return (S3Exception) builder.build();
    }
    @Test void keysAreOpaqueReversibleAndBounded() {
        var codec = new MinioObjectKeyCodec("tests", 1024);
        var values = List.of("../a", "a/b", "a\\b", "%2f", " X ", "x", "X", "é", "e\u0301", "\uD800", "\uD801");
        Set<String> physical = new HashSet<>();
        for (String v : values) {
            var k = new ObjectKey(v); String encoded = codec.encode(k);
            assertThat(encoded).matches("tests/v1/[A-Za-z0-9_-]+");
            assertThat(codec.decode(encoded)).isEqualTo(k); assertThat(physical.add(encoded)).isTrue();
        }
        error(() -> codec.encode(new ObjectKey("x".repeat(1000))), StorageErrorCode.INVALID_INPUT);
        error(() -> codec.encode(null), StorageErrorCode.INVALID_INPUT);
    }
    @Test void metadataEnvelopePreservesCaseUnicodeAndCodeUnits() {
        var codec = new MinioMetadataCodec(mapper, 1800);
        var m = new ObjectMetadata(key, "text/plain; charset=UTF-8", 0,
                Map.of("Source", "A", "source", "B", "unicode", "\uD800\u0000Tiếng Việt", "empty", ""));
        assertThat(codec.decode(key, 0, Map.of("dxos-envelope-v1", codec.encode(m)))).isEqualTo(m);
        error(() -> codec.decode(key, 0, Map.of()), StorageErrorCode.UNAVAILABLE);
        error(() -> codec.decode(key, 0, Map.of("dxos-envelope-v1", "broken")), StorageErrorCode.UNAVAILABLE);
        error(() -> new MinioMetadataCodec(mapper, 10).encode(m), StorageErrorCode.INVALID_INPUT);
    }
    @Test void createOnlyUsesConditionalPutWithStagedExactContentAndNoHead() throws Exception {
        var command = command(key, "hello", StoreMode.CREATE_ONLY);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(inv -> {
            PutObjectRequest request = inv.getArgument(0); RequestBody body = inv.getArgument(1);
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            assertThat(request.contentLength()).isEqualTo(5);
            try (var input = body.contentStreamProvider().newStream()) { assertThat(input.readAllBytes()).isEqualTo("hello".getBytes()); }
            return PutObjectResponse.builder().build();
        });
        assertThat(adapter.store(command)).isEqualTo(command.metadata());
        verify(client, never()).headObject(any(HeadObjectRequest.class));
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }
    @Test void overwriteReplacesCompleteEnvelopeWithoutConditionalHeader() {
        var c = new StoreObjectCommand(new ObjectMetadata(key, "application/octet-stream", 0, Map.of()),
                InputStream.nullInputStream(), StoreMode.OVERWRITE);
        adapter.store(c);
        var captor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().ifNoneMatch()).isNull();
        assertThat(new MinioMetadataCodec(mapper, 1800).decode(key, 0, captor.getValue().metadata())).isEqualTo(c.metadata());
    }
    @Test void conflictIsSanitizedAndStagingIsCleaned() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3(412, "PreconditionFailed"));
        error(() -> adapter.store(command(key, "hello", StoreMode.CREATE_ONLY)), StorageErrorCode.CONFLICT);
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }
    @Test void shortLongAndZeroDeclaredLengthDoNotMutateOrCloseInput() throws Exception {
        for (long length : new long[]{0, 2, 100}) {
            boolean[] closed = {false};
            var input = new ByteArrayInputStream(new byte[]{1, 2, 3}) { @Override public void close() { closed[0] = true; } };
            var c = new StoreObjectCommand(new ObjectMetadata(key, "text/plain", length, Map.of()), input, StoreMode.OVERWRITE);
            error(() -> adapter.store(c), StorageErrorCode.INVALID_INPUT);
            assertThat(closed[0]).isFalse(); assertThat(input.available()).isZero();
        }
        verifyNoInteractions(client);
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }
    @Test void callerReadFailuresAreInvalidAndNeverCloseCaller() {
        for (boolean runtime : new boolean[]{false, true}) {
            boolean[] closed = {false};
            var input = new InputStream() {
                public int read() throws IOException { if (runtime) throw new IllegalStateException("secret"); throw new IOException("secret"); }
                public void close() { closed[0] = true; }
            };
            error(() -> adapter.store(new StoreObjectCommand(new ObjectMetadata(key, "text/plain", 1, Map.of()), input,
                    StoreMode.CREATE_ONLY)), StorageErrorCode.INVALID_INPUT);
            assertThat(closed[0]).isFalse();
        }
        verifyNoInteractions(client);
    }
    @Test void stagingConsumesCurrentPositionAndHandlesZeroReturningInput() throws Exception {
        var c = command(key, "prefixhello", StoreMode.CREATE_ONLY); c.content().skipNBytes(6);
        var m = new ObjectMetadata(key, "text/plain", 5, Map.of());
        try (var staged = MinioStagedUpload.create(new StoreObjectCommand(m, c.content(), StoreMode.CREATE_ONLY), directory, 100)) {
            assertThat(Files.readString(staged.file())).isEqualTo("hello");
        }
        var input = new ByteArrayInputStream(new byte[]{42}) { public int read(byte[] b, int off, int len) { return 0; } };
        try (var staged = MinioStagedUpload.create(new StoreObjectCommand(new ObjectMetadata(key, "text/plain", 1, Map.of()),
                input, StoreMode.CREATE_ONLY), directory, 100)) { assertThat(Files.size(staged.file())).isEqualTo(1); }
    }
    @Test void oversizedOrUnsafeInputIsRejectedBeforeNetwork() {
        error(() -> adapter.store(new StoreObjectCommand(new ObjectMetadata(key, "text/plain", p.maxObjectBytes()+1, Map.of()),
                InputStream.nullInputStream(), StoreMode.OVERWRITE)), StorageErrorCode.INVALID_INPUT);
        error(() -> adapter.store(new StoreObjectCommand(new ObjectMetadata(key, "text/plain\r\nInjected: x", 0, Map.of()),
                InputStream.nullInputStream(), StoreMode.OVERWRITE)), StorageErrorCode.INVALID_INPUT);
        error(() -> adapter.store(null), StorageErrorCode.INVALID_INPUT);
        error(() -> adapter.read(null), StorageErrorCode.INVALID_INPUT);
        error(() -> adapter.metadata(null), StorageErrorCode.INVALID_INPUT);
        error(() -> adapter.delete(null), StorageErrorCode.INVALID_INPUT);
        verifyNoInteractions(client);
    }
    @Test void getUsesMetadataFromTheSameResponseAndDoesNotHead() throws Exception {
        var m = command(key, "old", StoreMode.CREATE_ONLY).metadata();
        var response = new ResponseInputStream<>(GetObjectResponse.builder().contentLength(3L)
                .metadata(Map.of("dxos-envelope-v1", new MinioMetadataCodec(mapper, 1800).encode(m))).build(),
                new ByteArrayInputStream("old".getBytes()));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);
        try (var resource = adapter.read(key)) {
            assertThat(resource.metadata()).isEqualTo(m);
            assertThat(resource.content().readAllBytes()).isEqualTo("old".getBytes());
        }
        verify(client, never()).headObject(any(HeadObjectRequest.class));
    }
    @Test void metadataUsesHeadWithoutGet() {
        var m = command(key, "hello", StoreMode.CREATE_ONLY).metadata();
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(5L)
                .metadata(Map.of("dxos-envelope-v1", new MinioMetadataCodec(mapper, 1800).encode(m))).build());
        assertThat(adapter.metadata(key)).isEqualTo(m);
        verify(client, never()).getObject(any(GetObjectRequest.class));
    }
    @Test void missingHeadChecksBucketAndRepeatedDeleteDoesNotSilentlySucceed() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3(404, null));
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());
        error(() -> adapter.delete(key), StorageErrorCode.NOT_FOUND);
        error(() -> adapter.metadata(key), StorageErrorCode.NOT_FOUND);
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3(404, "NoSuchBucket"));
        error(() -> adapter.delete(key), StorageErrorCode.UNAVAILABLE);
    }
    @Test void successfulDeleteAndAccessFailuresAreMapped() {
        adapter.delete(key); verify(client).deleteObject(any(DeleteObjectRequest.class));
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3(403, null));
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3(403, null));
        error(() -> adapter.delete(key), StorageErrorCode.ACCESS_FAILURE);
    }
    @Test void scopedListDisambiguatesHiddenMissingKeyFromDeniedExistingKey() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(s3(403, "AccessDenied"));
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());
        error(() -> adapter.metadata(key), StorageErrorCode.NOT_FOUND);
        String physical = new MinioObjectKeyCodec(p.keyPrefix(), p.maxKeyBytes()).encode(key);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key(physical).build()).build());
        error(() -> adapter.metadata(key), StorageErrorCode.ACCESS_FAILURE);
    }
    @Test void errorMappingNeverLeaksAndDoesNotInventConflicts() {
        assertThat(MinioStorageErrorMapper.map(s3(409, "ConditionalRequestConflict"), true).code()).isEqualTo(StorageErrorCode.UNAVAILABLE);
        assertThat(MinioStorageErrorMapper.map(s3(412, "PreconditionFailed"), false).code()).isEqualTo(StorageErrorCode.UNAVAILABLE);
        assertThat(MinioStorageErrorMapper.map(s3(404, "NoSuchKey"), false).code()).isEqualTo(StorageErrorCode.NOT_FOUND);
        for (RuntimeException e : List.of(s3(500, "InternalError"), s3(404, "NoSuchBucket"), new IllegalStateException("secret")))
            error(() -> { throw MinioStorageErrorMapper.map(e, false); }, StorageErrorCode.UNAVAILABLE);
    }
    @Test void ownedReadCloseIsIdempotentAndMetadataSurvives() throws Exception {
        int[] closes = {0}; var m = command(key, "hello", StoreMode.CREATE_ONLY).metadata();
        var resource = new MinioObjectContent(m, new ByteArrayInputStream("hello".getBytes()) { public void close() { closes[0]++; } });
        var input = resource.content(); assertThat(input.read()).isEqualTo('h'); input.close(); resource.close();
        assertThat(closes[0]).isEqualTo(1); assertThat(resource.metadata()).isEqualTo(m);
        error(resource::content, StorageErrorCode.INVALID_INPUT); error(input::read, StorageErrorCode.INVALID_INPUT);
    }
    @Test void truncatedExcessAndLazyFailuresReleaseAndSanitize() {
        var m = command(key, "hello", StoreMode.CREATE_ONLY).metadata();
        for (InputStream provider : List.of(new ByteArrayInputStream(new byte[2]), new ByteArrayInputStream(new byte[6]),
                new InputStream() { public int read() throws IOException { throw new IOException("secret"); } })) {
            var resource = new MinioObjectContent(m, provider);
            error(() -> resource.content().readAllBytes(), StorageErrorCode.UNAVAILABLE); resource.close();
        }
        var resource = new MinioObjectContent(m, new ByteArrayInputStream(new byte[5]) {
            public void close() throws IOException { throw new IOException("secret"); }
        });
        error(resource::close, StorageErrorCode.UNAVAILABLE); resource.close();
    }
}
