package com.tricore.dxos.core.data;

import java.io.InputStream;

/**
 * One read resource: metadata and stream describe the same object version.
 * Caller closes this resource, including after partial consumption or failure.
 * The adapter owns the returned stream and releases it on close; close is idempotent.
 * The stream is single-use and unusable after close (INVALID_INPUT).
 * Closing the stream also releases its underlying read resources.
 * Metadata remains available after close.
 * Adapters normalize failures during lazy reads and close to StorageException,
 * never exposing raw provider IOExceptions or causes through the stream.
 */
public interface ObjectContent extends AutoCloseable {
    ObjectMetadata metadata();

    InputStream content();

    @Override
    void close();
}
