package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class SupabaseRealSupport {
    static String required(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank()) throw new AssertionError("BLOCKED: REAL SUPABASE CREDENTIALS / CONFIGURATION ("+name+")");
        return value;
    }
    static SupabaseStorageProperties properties(String prefix) {
        try {
            return new SupabaseStorageProperties(URI.create(required("SUPABASE_STORAGE_REST_BASE_URL")),
                required("SUPABASE_STORAGE_API_KEY"),required("SUPABASE_STORAGE_API_KEY_MODE"),required("SUPABASE_STORAGE_BUCKET"),prefix,
                Long.valueOf(required("SUPABASE_STORAGE_MAX_OBJECT_BYTES")),Integer.valueOf(required("SUPABASE_STORAGE_MAX_METADATA_BYTES")),
                Integer.valueOf(required("SUPABASE_STORAGE_MAX_KEY_BYTES")),Integer.valueOf(required("SUPABASE_STORAGE_MAX_RESPONSE_BYTES")),
                Path.of(required("SUPABASE_STORAGE_STAGING_DIRECTORY")),Duration.parse(required("SUPABASE_STORAGE_CONNECT_TIMEOUT")),
                Duration.parse(required("SUPABASE_STORAGE_REQUEST_TIMEOUT")));
        } catch (IllegalArgumentException error) { throw new AssertionError("Invalid private Supabase test configuration"); }
    }
    static StoreObjectCommand command(ObjectKey key,String value,StoreMode mode) {
        return command(key,value,mode,Map.of("Source","á", "source", "", "payload",value));
    }
    static StoreObjectCommand command(ObjectKey key,String value,StoreMode mode,Map<String,String> customMetadata) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        return new StoreObjectCommand(new ObjectMetadata(key,"text/plain; charset=UTF-8",bytes.length,customMetadata),new ByteArrayInputStream(bytes),mode);
    }
    private SupabaseRealSupport() {}
}
