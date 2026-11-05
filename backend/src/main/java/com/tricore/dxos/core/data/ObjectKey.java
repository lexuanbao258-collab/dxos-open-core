// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.core.data;

/** Opaque, case-sensitive key within the configured storage namespace; preserved verbatim. */
public record ObjectKey(String value) {
    public ObjectKey {
        value = StorageValues.text(value);
    }
}
