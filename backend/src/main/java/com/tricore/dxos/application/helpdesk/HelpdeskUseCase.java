// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.application.helpdesk;

import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral R29 command types. The six records are contracts for future application
 * handlers; instantiating a command never performs authorization, storage or a transition.
 *
 * <p>The authoritative workflow state/version and idempotency outcome must be resolved by the
 * future service through the reviewed core-service contract, not synthesized by these records.
 */
public sealed interface HelpdeskUseCase permits HelpdeskUseCase.Create, HelpdeskUseCase.Triage,
        HelpdeskUseCase.Assign, HelpdeskUseCase.Start, HelpdeskUseCase.Resolve,
        HelpdeskUseCase.Confirm {

    private static UUID requestId(UUID value) {
        return Objects.requireNonNull(value, "requestId");
    }

    private static long version(long value) {
        if (value < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        return value;
    }

    private static String text(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be nonblank and at most "
                    + maximumLength + " characters");
        }
        return value;
    }

    /**
     * An explicit idempotency key is a required command input, NOT yet an implemented replay
     * guarantee. The authoritative uniqueness/reconciliation policy is blocked on R27/R28.
     */
    record Create(String title, String description, String idempotencyKey) implements HelpdeskUseCase {
        public Create {
            title = text(title, "title", 200);
            description = text(description, "description", 4000);
            idempotencyKey = text(idempotencyKey, "idempotencyKey", 256);
        }
    }

    record Triage(UUID requestId, long expectedVersion) implements HelpdeskUseCase {
        public Triage {
          requestId = HelpdeskUseCase.requestId(requestId);
            expectedVersion = version(expectedVersion);
        }
    }

    record Assign(UUID requestId, String assigneeId, long expectedAssignmentVersion)
            implements HelpdeskUseCase {
        public Assign {
           requestId = HelpdeskUseCase.requestId(requestId);
            assigneeId = text(assigneeId, "assigneeId", 1024);
            expectedAssignmentVersion = version(expectedAssignmentVersion);
        }
    }

    record Start(UUID requestId, long expectedVersion) implements HelpdeskUseCase {
        public Start {
           requestId = HelpdeskUseCase.requestId(requestId);
            expectedVersion = version(expectedVersion);
        }
    }

    record Resolve(UUID requestId, long expectedVersion, String resolution)
            implements HelpdeskUseCase {
        public Resolve {
          requestId = HelpdeskUseCase.requestId(requestId);
            expectedVersion = version(expectedVersion);
            resolution = text(resolution, "resolution", 4000);
        }
    }

    record Confirm(UUID requestId, long expectedVersion) implements HelpdeskUseCase {
        public Confirm {
          requestId = HelpdeskUseCase.requestId(requestId);
            expectedVersion = version(expectedVersion);
        }
    }
}
