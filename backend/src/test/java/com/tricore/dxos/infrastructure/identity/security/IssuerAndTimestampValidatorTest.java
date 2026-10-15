package com.tricore.dxos.infrastructure.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssuerAndTimestampValidatorTest {
    private static final String ISSUER = "http://localhost:8180/realms/tricore";
    private final OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(ISSUER);

    @Test
    void rejectsWrongIssuerOnOtherwiseCurrentJwt() {
        Instant now = Instant.now();
        Instant issuedAt = now.minusSeconds(30);
        Instant expiresAt = now.plusSeconds(600);

        assertFalse(validator.validate(jwt(ISSUER, issuedAt, expiresAt)).hasErrors());
        assertTrue(validator.validate(jwt("http://localhost:8180/realms/other", issuedAt, expiresAt))
                .hasErrors());
    }

    @Test
    void rejectsExpiredJwtWithExpectedIssuer() {
        Instant now = Instant.now();

        assertFalse(validator.validate(jwt(ISSUER, now.minusSeconds(30), now.plusSeconds(600)))
                .hasErrors());
        assertTrue(validator.validate(jwt(ISSUER, now.minusSeconds(7200), now.minusSeconds(3600)))
                .hasErrors());
    }

    private Jwt jwt(String issuer, Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("iss", issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
