package com.tricore.dxos.architecture.canary.core;

import com.tricore.dxos.architecture.canary.infrastructure.ForbiddenAdapterCanary;

/**
 * Test-only negative fixture. Must never be included in the production scan.
 */
public final class ForbiddenCoreCanary {
    private final ForbiddenAdapterCanary forbiddenDependency;
    private java.sql.Connection forbiddenJdbc;
    private javax.crypto.Cipher jdkOutsideApprovedJavaPackages;
    private jakarta.validation.Valid forbiddenJakarta;
    private org.slf4j.Logger forbiddenExternalLibrary;

    public ForbiddenCoreCanary(ForbiddenAdapterCanary forbiddenDependency) {
        this.forbiddenDependency = forbiddenDependency;
    }

    public static final class AllowedCoreCanary {
        private final java.util.List<AllowedCoreCanary> peers = java.util.List.of();
        private final java.time.Instant createdAt = java.time.Instant.EPOCH;
    }
}
