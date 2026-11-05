// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.core.identity;

/**
 * Provider-independent reason for an authorization decision.
 */
public enum AuthorizationReason {
    AUTHORITY_GRANTED,
    MISSING_AUTHORITY
}