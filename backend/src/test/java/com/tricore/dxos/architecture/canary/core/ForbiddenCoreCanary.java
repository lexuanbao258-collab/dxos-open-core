package com.tricore.dxos.architecture.canary.core;

import com.tricore.dxos.architecture.canary.infrastructure.ForbiddenAdapterCanary;

/**
 * Test-only negative fixture. Must never be included in the production scan.
 */
public final class ForbiddenCoreCanary {
    private final ForbiddenAdapterCanary forbiddenDependency;

    public ForbiddenCoreCanary(ForbiddenAdapterCanary forbiddenDependency) {
        this.forbiddenDependency = forbiddenDependency;
    }
}
