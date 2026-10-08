package com.tricore.dxos.core.data;

import java.util.Objects;

/** Sanitized failure: no external message, provider cause, suppression or stack trace. */
public final class StorageException extends RuntimeException {
    private final StorageErrorCode code;

    public StorageException(StorageErrorCode code) {
        super(Objects.requireNonNull(code, "code").name(), null, false, false);
        this.code = code;
    }

    public StorageErrorCode code() {
        return code;
    }
}
