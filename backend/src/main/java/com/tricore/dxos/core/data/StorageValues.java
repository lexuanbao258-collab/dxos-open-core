package com.tricore.dxos.core.data;

/** Data-local structural validation; invalid contract inputs use the normalized error. */
final class StorageValues {
    private StorageValues() {}

    static <T> T required(T value) {
        if (value == null) {
            throw new StorageException(StorageErrorCode.INVALID_INPUT);
        }
        return value;
    }

    static String text(String value) {
        if (required(value).isBlank()) {
            throw new StorageException(StorageErrorCode.INVALID_INPUT);
        }
        return value;
    }
}
