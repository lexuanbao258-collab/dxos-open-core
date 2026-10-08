package com.tricore.dxos.core.data;

import java.io.InputStream;

/** Single-use synchronous input. Caller owns and closes content on success and failure. */
public record StoreObjectCommand(ObjectMetadata metadata, InputStream content, StoreMode mode) {
    public StoreObjectCommand {
        StorageValues.required(metadata);
        StorageValues.required(content);
        StorageValues.required(mode);
    }
}
