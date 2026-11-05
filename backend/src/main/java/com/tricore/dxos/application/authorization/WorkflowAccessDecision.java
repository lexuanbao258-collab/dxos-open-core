// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.application.authorization;

/** Resource-policy outcomes; ALLOWED still requires the operation authority. */
public enum WorkflowAccessDecision {
    ALLOWED,
    FORBIDDEN,
    HIDDEN,
    UNAVAILABLE
}
