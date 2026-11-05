// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.core.data;

/** Stable Data capability errors; transport and provider mappings belong outside Core. */
public enum StorageErrorCode {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    UNAVAILABLE,
    ACCESS_FAILURE
}
