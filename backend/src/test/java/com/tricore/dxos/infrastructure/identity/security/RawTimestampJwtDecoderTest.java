package com.tricore.dxos.infrastructure.identity.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RawTimestampJwtDecoderTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "tricore-api";
    private static final Object ABSENT = new Object();

    private KeyPair keyPair;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processor = new AtomicReference<>();
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .jwtProcessorCustomizer(SecurityConfig.jwtProcessorCustomizer(processor))
                .build();
        decoder = SecurityConfig.configureJwtDecoder(processor.get(), ISSUER, AUDIENCE);
    }

    @Test
    void acceptsFutureNumericExpiration() throws Exception {
        long future = Instant.now().plusSeconds(3600).getEpochSecond();
        SignedJWT token = signedToken(future, ABSENT);
        assertInstanceOf(Number.class, token.getPayload().toJSONObject().get("exp"));

        Jwt decoded = decoder.decode(token.serialize());
        assertEquals(Instant.ofEpochSecond(future), decoded.getExpiresAt());
    }

    @Test
    void rejectsMissingExpiration() throws Exception {
        SignedJWT token = signedToken(ABSENT, ABSENT);
        assertNull(token.getPayload().toJSONObject().get("exp"));
        assertThrows(JwtException.class, () -> decoder.decode(token.serialize()));
    }

    @Test
    void rejectsExpiredNumericExpiration() throws Exception {
        SignedJWT token = signedToken(Instant.now().minusSeconds(600).getEpochSecond(), ABSENT);
        assertInstanceOf(Number.class, token.getPayload().toJSONObject().get("exp"));
        assertThrows(JwtException.class, () -> decoder.decode(token.serialize()));
    }

    @ParameterizedTest(name = "raw {0} exp is rejected")
    @MethodSource("invalidExpirationValues")
    void rejectsInvalidRawExpiration(String type, Object rawValue, Class<?> rawType) throws Exception {
        SignedJWT token = signedToken(rawValue, ABSENT);
        assertInstanceOf(rawType, token.getPayload().toJSONObject().get("exp"));
        assertThrows(JwtException.class, () -> decoder.decode(token.serialize()));
    }

    static Stream<Arguments> invalidExpirationValues() {
        return Stream.of(
                Arguments.of("String", "9999999999", String.class),
                Arguments.of("Boolean", true, Boolean.class),
                Arguments.of("Object", Map.of("seconds", 9999999999L), Map.class),
                Arguments.of("Array", List.of(9999999999L), List.class)
        );
    }

    @Test
    void acceptsMissingNotBefore() throws Exception {
        SignedJWT token = signedToken(Instant.now().plusSeconds(3600).getEpochSecond(), ABSENT);
        assertNull(token.getPayload().toJSONObject().get("nbf"));

        Jwt decoded = decoder.decode(token.serialize());
        assertNull(decoded.getNotBefore());
    }

    @Test
    void acceptsPastNumericNotBefore() throws Exception {
        long past = Instant.now().minusSeconds(60).getEpochSecond();
        SignedJWT token = signedToken(Instant.now().plusSeconds(3600).getEpochSecond(), past);
        assertInstanceOf(Number.class, token.getPayload().toJSONObject().get("nbf"));

        Jwt decoded = decoder.decode(token.serialize());
        assertEquals(Instant.ofEpochSecond(past), decoded.getNotBefore());
    }

    @Test
    void rejectsFutureNumericNotBeforeBeyondSkew() throws Exception {
        SignedJWT token = signedToken(
                Instant.now().plusSeconds(3600).getEpochSecond(),
                Instant.now().plusSeconds(600).getEpochSecond()
        );
        assertInstanceOf(Number.class, token.getPayload().toJSONObject().get("nbf"));
        assertThrows(JwtException.class, () -> decoder.decode(token.serialize()));
    }

    @ParameterizedTest(name = "raw {0} nbf is rejected")
    @MethodSource("invalidNotBeforeValues")
    void rejectsInvalidRawNotBefore(String type, Object rawValue, Class<?> rawType) throws Exception {
        SignedJWT token = signedToken(Instant.now().plusSeconds(3600).getEpochSecond(), rawValue);
        assertInstanceOf(rawType, token.getPayload().toJSONObject().get("nbf"));
        assertThrows(JwtException.class, () -> decoder.decode(token.serialize()));
    }

    static Stream<Arguments> invalidNotBeforeValues() {
        return Stream.of(
                Arguments.of("String", "0", String.class),
                Arguments.of("Boolean", true, Boolean.class),
                Arguments.of("Object", Map.of("seconds", 0), Map.class),
                Arguments.of("Array", List.of(0), List.class)
        );
    }

    private SignedJWT signedToken(Object expiration, Object notBefore) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("user-123")
                .issueTime(Date.from(Instant.now().minusSeconds(30)))
                .claim("typ", "Bearer");
        if (expiration != ABSENT) {
            claims.claim("exp", expiration);
        }
        if (notBefore != ABSENT) {
            claims.claim("nbf", notBefore);
        }

        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return token;
    }
}
