package com.tricore.dxos.infrastructure.storage.minio;

import com.tricore.dxos.core.data.*;
import java.util.Base64;

/** Encodes UTF-16 code units manually: even unpaired surrogates remain distinct. */
final class MinioObjectKeyCodec {
    private final String prefix;
    private final int maxBytes;
    MinioObjectKeyCodec(String prefix, int maxBytes) {
        this.prefix = prefix + "/v1/";
        this.maxBytes = maxBytes;
    }
    String encode(ObjectKey key) {
        if (key == null) throw invalid();
        String value = key.value();
        long size = prefix.length() + (value.length() * 2L * 8 + 5) / 6;
        if (size > maxBytes) throw invalid();
        byte[] bytes = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            bytes[2 * i] = (byte) (value.charAt(i) >>> 8);
            bytes[2 * i + 1] = (byte) value.charAt(i);
        }
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    ObjectKey decode(String physical) {
        try {
            if (physical == null || !physical.startsWith(prefix) || physical.length() > maxBytes) throw invalid();
            byte[] bytes = Base64.getUrlDecoder().decode(physical.substring(prefix.length()));
            if (bytes.length % 2 != 0) throw invalid();
            char[] chars = new char[bytes.length / 2];
            for (int i = 0; i < chars.length; i++) chars[i] = (char) (((bytes[2*i] & 255) << 8) | (bytes[2*i+1] & 255));
            ObjectKey key = new ObjectKey(new String(chars));
            if (!encode(key).equals(physical)) throw invalid();
            return key;
        } catch (RuntimeException error) { throw invalid(); }
    }
    private static StorageException invalid() { return new StorageException(StorageErrorCode.INVALID_INPUT); }
}
