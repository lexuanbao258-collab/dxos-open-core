package com.tricore.dxos.infrastructure.identity.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

final class ExpirationValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_EXPIRATION = new OAuth2Error(
            "invalid_token",
            "JWT expiration is missing or invalid",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object expirationClaim = jwt.getClaim("exp");

        if (!(expirationClaim instanceof Instant)) {
            return OAuth2TokenValidatorResult.failure(INVALID_EXPIRATION);
        }

        return OAuth2TokenValidatorResult.success();
    }
}