package com.tricore.dxos.infrastructure.identity.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class SubjectValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            "invalid_token",
            "JWT subject is missing or invalid",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subjectClaim = jwt.getClaim("sub");

        if (!(subjectClaim instanceof String subject) || subject.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        return OAuth2TokenValidatorResult.success();
    }
}