package com.tricore.dxos.core.data;

/** Opaque, case-sensitive key within the configured storage namespace; preserved verbatim. */
public record ObjectKey(String value) {
    public ObjectKey {
        value = StorageValues.text(value);
    }
}
