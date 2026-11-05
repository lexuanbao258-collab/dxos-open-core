// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.request.domain;

import java.util.UUID;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(UUID id) {
        super("Request not found: " + id);
    }
}
