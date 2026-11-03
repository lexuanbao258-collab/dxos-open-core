package com.tricore.dxos.infrastructure.identity.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class AccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN_TYPE = new OAuth2Error(
            "invalid_token",
            "The required token type is missing or invalid",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if ("Bearer".equals(jwt.getClaimAsString("typ"))) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN_TYPE);
    }
}
