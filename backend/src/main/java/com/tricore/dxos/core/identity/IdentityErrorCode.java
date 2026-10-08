package com.tricore.dxos.core.identity;

/**
 * Stable Identity capability errors.
 * Provider-specific errors must be translated at the adapter boundary.
 */
public enum IdentityErrorCode {
    AUTHENTICATION_FAILED,
    INVALID_AUTHENTICATION_CONTEXT,
    AUTHENTICATION_EXPIRED,
    ACCESS_DENIED,
    IDENTITY_NOT_FOUND,
    IDENTITY_PROVIDER_UNAVAILABLE
}