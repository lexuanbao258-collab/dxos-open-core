// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.application.helpdesk;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HelpdeskUseCaseTest {
    private final UUID id = UUID.randomUUID();

    @Test
    void preservesSixTypedUseCaseInputsWithoutExecutingAnyMutation() {
        assertEquals("sample-key", new HelpdeskUseCase.Create("title", "description", "sample-key").idempotencyKey());
        assertEquals(id, new HelpdeskUseCase.Triage(id, 0).requestId());
        assertEquals("worker", new HelpdeskUseCase.Assign(id, "worker", 1).assigneeId());
        assertEquals(2, new HelpdeskUseCase.Start(id, 2).expectedVersion());
        assertEquals("resolved", new HelpdeskUseCase.Resolve(id, 3, "resolved").resolution());
        assertEquals(4, new HelpdeskUseCase.Confirm(id, 4).expectedVersion());
    }

    @Test
    void createRejectsMissingIdempotencyKeyAndInvalidFields() {
        assertThrows(NullPointerException.class, () -> new HelpdeskUseCase.Create("title", "description", null));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Create("title", "description", " "));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Create("", "description", "k"));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Create("t".repeat(201), "d", "k"));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Create("t", "d".repeat(4001), "k"));
    }

    @Test
    void writeUseCasesRejectMissingTargetsAndNegativeVersions() {
        assertThrows(NullPointerException.class, () -> new HelpdeskUseCase.Triage(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Triage(id, -1));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Assign(id, "worker", -1));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Assign(id, " ", 0));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Start(id, -1));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Resolve(id, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Resolve(id, -1, "done"));
        assertThrows(IllegalArgumentException.class, () -> new HelpdeskUseCase.Confirm(id, -1));
    }
}
