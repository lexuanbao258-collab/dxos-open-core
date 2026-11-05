// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.application.authorization;

/** Sanitized application failure; HTTP mapping belongs to later secured integration. */
public final class WorkflowAccessException extends RuntimeException {
    private final WorkflowAccessDecision decision;

    public WorkflowAccessException(WorkflowAccessDecision decision) {
        super(decision.name(), null, false, false);
        this.decision = decision;
    }

    public WorkflowAccessDecision decision() {
        return decision;
    }
}
