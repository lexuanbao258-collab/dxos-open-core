package com.tricore.dxos.infrastructure.identity.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "The required audience is missing",
            null
    );

    private final String requiredAudience;

    AudienceValidator(String requiredAudience) {
        if (requiredAudience == null || requiredAudience.isBlank()) {
            throw new IllegalArgumentException("Required audience must not be blank");
        }

        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience != null && audience.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
