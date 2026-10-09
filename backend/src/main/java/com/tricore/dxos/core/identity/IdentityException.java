package com.tricore.dxos.core.identity;

import java.util.Objects;

/**
 * A normalized Identity capability failure.
 * Provider-specific exceptions must be translated at the adapter boundary.
 */
public final class IdentityException extends RuntimeException {

    private final IdentityErrorCode code;

    public IdentityException(IdentityErrorCode code) {
        super(Objects.requireNonNull(code, "code").name(), null, false, false);
        this.code = code;
    }

    public IdentityErrorCode code() {
        return code;
    }
}