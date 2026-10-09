package com.tricore.dxos.core.identity;

/**
 * Provider-independent authority requirement used for authorization checks.
 */
public record RequiredAuthority(String value) {

    public RequiredAuthority {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required authority value must not be blank"
            );
        }
    }
}