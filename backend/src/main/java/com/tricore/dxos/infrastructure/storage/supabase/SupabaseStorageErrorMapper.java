package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.tricore.dxos.core.data.*;
import java.util.Set;

final class SupabaseStorageErrorMapper {
    private static final Set<String> MISSING = Set.of("NoSuchKey", "not_found", "NotFound");
    private static final Set<String> DUPLICATE = Set.of("KeyAlreadyExists", "ResourceAlreadyExists", "already_exists", "Duplicate");
    private static final Set<String> INVALID = Set.of("InvalidKey", "InvalidMimeType", "InvalidRequest", "EntityTooLarge", "InvalidChecksum");
    private static final Set<String> ACCESS = Set.of("AccessDenied", "InvalidJWT", "InvalidSignature", "SignatureDoesNotMatch", "unauthorized");
    static StorageException response(int status, JsonNode body, boolean createOnly) {
        String code = body == null ? "" : body.path("code").asText("");
        if (code.isEmpty() && body != null) code = body.path("error").asText("");
        StorageErrorCode mapped;
        if (status == 401 || status == 403 || ACCESS.contains(code)) mapped = StorageErrorCode.ACCESS_FAILURE;
        else if (MISSING.contains(code)) mapped = StorageErrorCode.NOT_FOUND;
        else if ("PreconditionFailed".equals(code) || "precondition_failed".equals(code) || (createOnly && DUPLICATE.contains(code))) mapped = StorageErrorCode.CONFLICT;
        else if (INVALID.contains(code) || status == 413) mapped = StorageErrorCode.INVALID_INPUT;
        else mapped = StorageErrorCode.UNAVAILABLE;
        return new StorageException(mapped);
    }
    static StorageException transport(Exception error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        return new StorageException(error instanceof javax.net.ssl.SSLHandshakeException
                ? StorageErrorCode.ACCESS_FAILURE : StorageErrorCode.UNAVAILABLE);
    }
}
