package com.tricore.dxos.core.identity;

public record Authority(String value) {

    public Authority {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Authority value must not be blank");
        }
    }
}