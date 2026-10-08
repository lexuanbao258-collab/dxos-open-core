package com.tricore.dxos.core.data;

import java.util.Map;

/** Immutable content description; length is the exact byte count, including zero. */
public record ObjectMetadata(ObjectKey key, String contentType, long contentLength,
                             Map<String, String> customMetadata) {
    public ObjectMetadata {
        StorageValues.required(key);
        contentType = StorageValues.text(contentType);
        if (contentLength < 0) {
            throw new StorageException(StorageErrorCode.INVALID_INPUT);
        }
        StorageValues.required(customMetadata).forEach((name, value) -> {
            StorageValues.text(name);
            StorageValues.required(value);
        });
        customMetadata = Map.copyOf(customMetadata);
    }
}
