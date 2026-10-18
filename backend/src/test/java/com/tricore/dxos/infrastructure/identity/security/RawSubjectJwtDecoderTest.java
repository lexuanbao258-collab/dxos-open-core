package com.tricore.dxos.infrastructure.identity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class RawSubjectJwtDecoderTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "tricore-api";

    private KeyPair keyPair;
    private JwtDecoder decoder;
    private ConfigurableJWTProcessor<SecurityContext> nimbusProcessor;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processor = new AtomicReference<>();
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic())
                .jwtProcessorCustomizer(SecurityConfig.jwtProcessorCustomizer(processor))
                .build();
        nimbusProcessor = processor.get();
        decoder = SecurityConfig.configureJwtDecoder(nimbusProcessor, ISSUER, AUDIENCE);
    }

    @Test
    void decodeUsesJwtOverloadAndReachesRawSubjectGuard() throws Exception {
        ConfigurableJWTProcessor<SecurityContext> recordedProcessor = spy(nimbusProcessor);
        JwtDecoder recordedDecoder = SecurityConfig.configureJwtDecoder(recordedProcessor, ISSUER, AUDIENCE);

        assertEquals("user-123", recordedDecoder.decode(signedToken("user-123", true)).getSubject());
        verify(recordedProcessor).process(any(JWT.class), isNull());
        verify(recordedProcessor, never()).process(anyString(), isNull());

        String numericToken = signedToken(123, true);
        assertEquals("123", new NimbusJwtDecoder(nimbusProcessor).decode(numericToken).getSubject());
        assertThrows(JwtException.class, () -> recordedDecoder.decode(numericToken));
    }

    @Test
    void acceptsValidStringSubject() throws Exception {
        assertEquals("user-123", decoder.decode(signedToken("user-123", true)).getSubject());
    }

    @Test
    void acceptsAndPreservesValidUnicodeStringSubject() throws Exception {
        String subject = " User-\u00e9-e\u0301 ";

        Jwt jwt = decoder.decode(signedToken(subject, true));

        assertEquals(subject, jwt.getSubject());
    }

    @Test
    void acceptsStringThatLooksNumeric() throws Exception {
        assertEquals("123", decoder.decode(signedToken("123", true)).getSubject());
    }

    @Test
    void rejectsInvalidSignatureAfterRawSubjectCheck() throws Exception {
        String token = signedToken("user-123", true);
        String[] segments = token.split("\\.");
        char firstSignatureCharacter = segments[2].charAt(0);
        segments[2] = (firstSignatureCharacter == 'A' ? 'B' : 'A') + segments[2].substring(1);

        assertThrows(JwtException.class, () -> decoder.decode(String.join(".", segments)));
    }

    @Test
    void rejectsMissingSubject() throws Exception {
        assertThrows(JwtException.class, () -> decoder.decode(signedToken(null, false)));
    }

    @Test
    void rejectsEmptyStringSubject() throws Exception {
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("", true)));
    }

    @Test
    void rejectsBlankStringSubject() throws Exception {
        assertThrows(JwtException.class, () -> decoder.decode(signedToken("   ", true)));
    }

    @Test
    void rejectsSignedJwtWithEscapedLoneHighSurrogateSubject() throws Exception {
        String token = signedRawPayloadWithSubjectJsonEscape("\\uD800");

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );

        assertTrue(
                payload.contains("\"sub\":\"\\uD800\""),
                "The signed JWT must retain the escaped malformed surrogate"
        );

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test
    void rejectsRawJsonNumericSubject() throws Exception {
        assertRawSubjectRejected(123, JsonNodeType.NUMBER);
    }

    @Test
    void rejectsRawJsonBooleanSubject() throws Exception {
        assertRawSubjectRejected(true, JsonNodeType.BOOLEAN);
    }

    @Test
    void rejectsRawJsonObjectSubject() throws Exception {
        assertRawSubjectRejected(Map.of("id", "user-123"), JsonNodeType.OBJECT);
    }

    @Test
    void rejectsRawJsonArraySubject() throws Exception {
        assertRawSubjectRejected(List.of("user-123"), JsonNodeType.ARRAY);
    }

    private void assertRawSubjectRejected(Object subject, JsonNodeType expectedType) throws Exception {
        String token = signedToken(subject, true);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);
        JsonNode rawSubject = new ObjectMapper().readTree(payload).get("sub");
        assertEquals(expectedType, rawSubject.getNodeType(), "The signed JWT must retain the raw JSON type");

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    private String signedRawPayloadWithSubjectJsonEscape(String subjectJsonEscape) throws Exception {
        Instant now = Instant.now();

        String payload = "{"
                + "\"iss\":\"" + ISSUER + "\","
                + "\"aud\":\"" + AUDIENCE + "\","
                + "\"iat\":" + now.minusSeconds(30).getEpochSecond() + ","
                + "\"exp\":" + now.plusSeconds(300).getEpochSecond() + ","
                + "\"typ\":\"Bearer\","
                + "\"sub\":\"" + subjectJsonEscape + "\""
                + "}";

        JWSObject jws = new JWSObject(
                new JWSHeader(JWSAlgorithm.RS256),
                new Payload(payload)
        );
        jws.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));

        return jws.serialize();
    }

    private String signedToken(Object subject, boolean includeSubject) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("typ", "Bearer");
        if (includeSubject) {
            claims.claim("sub", subject);
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }
}
