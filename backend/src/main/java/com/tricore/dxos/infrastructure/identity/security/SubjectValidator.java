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

        if (!(subjectClaim instanceof String subject)
                || subject.isBlank()
                || !isWellFormedUtf16(subject)) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }

        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);

            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }

        return true;
    }
}
