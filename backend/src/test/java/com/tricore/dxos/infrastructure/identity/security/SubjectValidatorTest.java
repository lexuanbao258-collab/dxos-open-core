package com.tricore.dxos.infrastructure.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectValidatorTest {

    private final SubjectValidator validator = new SubjectValidator();

    @Test
    void acceptsValidSubject() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithSubject("user-123"));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsMissingSubject() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithoutSubject());

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsBlankSubject() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithSubject(""));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsWhitespaceOnlySubject() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithSubject("   "));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsNonStringSubject() {
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("sub", 123)
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }
    private Jwt jwtWithSubject(String subject) {
        Instant now = Instant.now();

        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("sub", subject)
                .build();
    }

    private Jwt jwtWithoutSubject() {
        Instant now = Instant.now();

        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}