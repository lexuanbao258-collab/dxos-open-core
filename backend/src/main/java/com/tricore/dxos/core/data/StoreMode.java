package com.tricore.dxos.core.data;

/** Mandatory write policy; no implicit default. */
public enum StoreMode {
    /** Create only if absent; an existing key produces CONFLICT without changing it. */
    CREATE_ONLY,
    /** Create if absent, otherwise replace both content and all metadata. */
    OVERWRITE
}
