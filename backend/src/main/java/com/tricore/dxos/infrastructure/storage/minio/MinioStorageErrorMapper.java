package com.tricore.dxos.infrastructure.storage.minio;

import com.tricore.dxos.core.data.*;
import software.amazon.awssdk.services.s3.model.S3Exception;

final class MinioStorageErrorMapper {
    private MinioStorageErrorMapper() {}
    static StorageException map(RuntimeException error, boolean createOnly) {
        if (error instanceof StorageException normalized) return normalized;
        StorageErrorCode result = StorageErrorCode.UNAVAILABLE;
        if (error instanceof S3Exception s3) {
            String code = s3.awsErrorDetails() == null ? "" : s3.awsErrorDetails().errorCode();
            if (s3.statusCode() == 401 || s3.statusCode() == 403 || "InvalidAccessKeyId".equals(code)
                    || "SignatureDoesNotMatch".equals(code) || "AccessDenied".equals(code)) {
                result = StorageErrorCode.ACCESS_FAILURE;
            } else if ("NoSuchKey".equals(code)) {
                result = StorageErrorCode.NOT_FOUND;
            } else if (createOnly && s3.statusCode() == 412 && "PreconditionFailed".equals(code)) {
                result = StorageErrorCode.CONFLICT;
            } else if ("EntityTooLarge".equals(code) || "MetadataTooLarge".equals(code)) {
                result = StorageErrorCode.INVALID_INPUT;
            }
            // NoSuchBucket, unclassified 404/409 and transport faults do not prove key absence/conflict.
        }
        return new StorageException(result);
    }
}
