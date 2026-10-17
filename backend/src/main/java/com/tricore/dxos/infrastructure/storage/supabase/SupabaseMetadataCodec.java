package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import java.io.*;
import java.util.*;

/** One envelope, independent of HTTP header naming/case restrictions. */
final class SupabaseMetadataCodec {
    private final ObjectMapper mapper;
    private final int maxBytes;
    SupabaseMetadataCodec(ObjectMapper mapper, int maxBytes) { this.mapper = mapper; this.maxBytes = maxBytes; }
    String encode(ObjectMetadata metadata) {
        try {
            checkEncodedSize(metadata);
            var envelope = mapper.createObjectNode();
            envelope.put("version", 1);
            envelope.put("contentType", units(metadata.contentType()));
            var custom = envelope.putArray("custom");
            metadata.customMetadata().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                var pair = custom.addArray(); pair.add(units(e.getKey())); pair.add(units(e.getValue()));
            });
            var outer = mapper.createObjectNode(); outer.set("dxos-envelope-v1", envelope);
            var bytes = new ByteArrayOutputStream();
            // Base64 needs four header bytes for each (possibly partial) three JSON bytes.
            mapper.writeValue(new LimitedOutputStream(bytes, (maxBytes / 4L) * 3), outer);
            String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
            if (encoded.length() > maxBytes) throw new StorageException(StorageErrorCode.INVALID_INPUT);
            return encoded;
        } catch (Exception error) { throw new StorageException(StorageErrorCode.INVALID_INPUT); }
    }
    ObjectMetadata decode(ObjectKey key, long size, JsonNode outer) {
        try {
            if (outer == null) throw new IllegalArgumentException();
            // Count into a bounded sink instead of allocating a second copy of provider JSON.
            mapper.writeValue(new LimitedOutputStream(OutputStream.nullOutputStream(), maxBytes), outer);
            JsonNode e = outer.get("dxos-envelope-v1");
            if (e == null || !e.isObject() || !e.path("version").isIntegralNumber() || !e.path("version").canConvertToInt() || e.path("version").intValue() != 1
                    || !e.path("contentType").isTextual() || !e.path("custom").isArray()) throw new IllegalArgumentException();
            Map<String, String> custom = new HashMap<>();
            for (JsonNode pair : e.path("custom")) {
                if (!pair.isArray() || pair.size() != 2 || !pair.get(0).isTextual() || !pair.get(1).isTextual()) throw new IllegalArgumentException();
                String name = fromUnits(pair.get(0).textValue());
                if (custom.put(name, fromUnits(pair.get(1).textValue())) != null) throw new IllegalArgumentException();
            }
            return new ObjectMetadata(key, fromUnits(e.get("contentType").textValue()), size, custom);
        } catch (Exception error) { throw new StorageException(StorageErrorCode.UNAVAILABLE); }
    }
    private void checkEncodedSize(ObjectMetadata metadata) {
        // All variable JSON strings contain only Base64 ASCII, without JSON escaping.
        // Check before units(), sorting, tree construction or serialization. Long arithmetic
        // also prevents overflow in the UTF-16 byte-array length calculation in units().
        long jsonBytes = "{\"dxos-envelope-v1\":{\"version\":1,\"contentType\":\"\",\"custom\":[]}}".length()
                + unitBytes(metadata.contentType());
        checkJsonSize(jsonBytes);
        boolean first = true;
        for (var entry : metadata.customMetadata().entrySet()) {
            jsonBytes += (first ? 7 : 8) + unitBytes(entry.getKey()) + unitBytes(entry.getValue());
            checkJsonSize(jsonBytes);
            first = false;
        }
    }
    private static long unitBytes(String value) { return 4 * ((2L * value.length() + 2) / 3); }
    private void checkJsonSize(long bytes) {
        if (4 * ((bytes + 2) / 3) > maxBytes) throw new IllegalArgumentException();
    }
    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long limit;
        private long count;
        LimitedOutputStream(OutputStream delegate, long limit) { this.delegate = delegate; this.limit = limit; }
        @Override public void write(int value) throws IOException {
            if (count == limit) throw new IOException("metadata limit");
            delegate.write(value); count++;
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > limit - count) throw new IOException("metadata limit");
            delegate.write(bytes, offset, length); count += length;
        }
    }
    // Encode code units inside JSON too, avoiding charset replacement of isolated surrogates.
    private static String units(String value) {
        byte[] b = new byte[value.length()*2];
        for (int i=0;i<value.length();i++) { b[2*i]=(byte)(value.charAt(i)>>>8); b[2*i+1]=(byte)value.charAt(i); }
        return Base64.getEncoder().encodeToString(b);
    }
    private static String fromUnits(String value) {
        byte[] b=Base64.getDecoder().decode(value); if (b.length%2!=0) throw new IllegalArgumentException();
        char[] c=new char[b.length/2]; for(int i=0;i<c.length;i++) c[i]=(char)(((b[2*i]&255)<<8)|(b[2*i+1]&255));
        String result=new String(c); if (!units(result).equals(value)) throw new IllegalArgumentException(); return result;
    }
}
