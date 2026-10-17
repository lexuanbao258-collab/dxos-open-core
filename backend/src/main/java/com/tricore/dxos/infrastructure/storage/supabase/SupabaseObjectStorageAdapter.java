package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.springframework.http.MediaType;

public final class SupabaseObjectStorageAdapter implements ObjectStoragePort {
    private final SupabaseStorageProperties properties;
    private final SupabaseRestStorageClient client;
    private final SupabaseObjectKeyCodec keys;
    private final SupabaseMetadataCodec metadata;
    SupabaseObjectStorageAdapter(SupabaseStorageProperties properties, SupabaseRestStorageClient client, ObjectMapper mapper) {
        this.properties = properties; this.client = client;
        keys = new SupabaseObjectKeyCodec(properties.keyPrefix(), properties.maxKeyBytes());
        metadata = new SupabaseMetadataCodec(mapper, properties.maxMetadataBytes());
    }
    @Override public ObjectMetadata store(StoreObjectCommand command) {
        if (command == null) throw invalid();
        String key = keys.encode(command.metadata().key());
        validateContentType(command.metadata().contentType());
        String envelope = metadata.encode(command.metadata());
        try (var staged = StagedObjectUpload.create(command, properties.stagingDirectory(), properties.maxObjectBytes())) {
            client.upload(key, staged.file(), command.metadata().contentType(), envelope, command.mode());
        }
        return command.metadata();
    }
    @Override public ObjectContent read(ObjectKey key) {
        String physical = keys.encode(key);
        String version = client.info(physical, null).version();
        var exact = client.info(physical, version);
        ObjectMetadata description = metadata.decode(key, exact.size(), exact.metadata());
        return new SupabaseObjectContent(description, client.download(physical, exact));
    }
    @Override public ObjectMetadata metadata(ObjectKey key) {
        var info = client.info(keys.encode(key), null);
        return metadata.decode(key, info.size(), info.metadata());
    }
    @Override public void delete(ObjectKey key) { client.delete(keys.encode(key)); }
    private static void validateContentType(String value) {
        try {
            if (value.chars().anyMatch(c -> c < 32 || c > 126)) throw invalid();
            MediaType parsed = MediaType.parseMediaType(value);
            if (parsed.isWildcardType() || parsed.isWildcardSubtype()) throw invalid();
        } catch (RuntimeException error) { throw invalid(); }
    }
    private static StorageException invalid() { return new StorageException(StorageErrorCode.INVALID_INPUT); }
}
