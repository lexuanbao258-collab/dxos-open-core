package com.tricore.dxos.common.error;

import java.util.Map;

public record ApiError(int status, String code, String message, Map<String, String> errors) {
}
