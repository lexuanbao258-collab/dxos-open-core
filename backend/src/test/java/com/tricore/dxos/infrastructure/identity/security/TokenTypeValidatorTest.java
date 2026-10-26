package com.tricore.dxos.infrastructure.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenTypeValidatorTest {

    private final AccessTokenTypeValidator validator = new AccessTokenTypeValidator();

    @Test
    void acceptsBearerTokenType() {
        OAuth2TokenValidatorResult result = validator.validate(jwtWithPayloadType("Bearer"));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsMissingTokenType() {
        assertInvalidTokenType(jwtWithPayloadType(null));
    }

    @Test
    void rejectsWrongTokenType() {
        assertInvalidTokenType(jwtWithPayloadType("Refresh"));
    }

    @Test
    void tokenTypeMatchingIsExact() {
        for (String tokenType : new String[] {"bearer", " Bearer", "Bearer ", "", " "}) {
            assertInvalidTokenType(jwtWithPayloadType(tokenType));
        }
    }

    private void assertInvalidTokenType(Jwt jwt) {
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        OAuth2Error error = result.getErrors().iterator().next();
        assertEquals("invalid_token", error.getErrorCode());
        assertEquals("The required token type is missing or invalid", error.getDescription());
    }

    private Jwt jwtWithPayloadType(String tokenType) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));

        if (tokenType != null) {
            builder.claim("typ", tokenType);
        }

        return builder.build();
    }
}
