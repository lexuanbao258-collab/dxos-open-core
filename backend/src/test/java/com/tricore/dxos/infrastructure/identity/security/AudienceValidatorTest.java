package com.tricore.dxos.infrastructure.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceValidatorTest {

    private final AudienceValidator validator =
            new AudienceValidator("tricore-api");

    @Test
    void acceptsRequiredAudience() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithAudience(List.of("tricore-api")));

        assertFalse(result.hasErrors());
    }

    @Test
    void acceptsRequiredAudienceAmongMultipleAudiences() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithAudience(
                        List.of("account", "tricore-api")
                ));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsMissingRequiredAudience() {
        OAuth2TokenValidatorResult result =
                validator.validate(jwtWithAudience(List.of("another-api")));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsBlankRequiredAudience() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AudienceValidator(" ")
        );
    }

    private Jwt jwtWithAudience(List<String> audience) {
        Instant now = Instant.now();

        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .audience(audience)
                .build();
    }
}
