package com.tricore.dxos.infrastructure.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.text.ParseException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            @Value("${dxos.openapi.enabled:false}") boolean openApiEnabled,
            @Value("${server.address:}") String bindAddress
    ) throws Exception {
        requireLoopbackOpenApi(openApiEnabled, bindAddress);
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/v1/health").permitAll();
                    // Never silently expose OpenAPI on a public listener or when disabled.
                    if (openApiEnabled) {
                        authorize.requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**"
                        ).permitAll();
                    } else {
                        authorize.requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**"
                        ).denyAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> {
                        })
                );

        return http.build();
    }

    static void requireLoopbackOpenApi(boolean enabled, String bindAddress) {
        if (enabled && !"127.0.0.1".equals(bindAddress)) {
            throw new IllegalStateException(
                    "Swagger/OpenAPI requires server.address=127.0.0.1 in the local MVP");
        }
    }

    @Bean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${tricore.security.oidc.audience}") String audience
    ) {
        AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processor = new AtomicReference<>();
        NimbusJwtDecoder.withIssuerLocation(issuer)
                .jwtProcessorCustomizer(jwtProcessorCustomizer(processor))
                .build();
        return configureJwtDecoder(processor.get(), issuer, audience);
    }

    static Consumer<ConfigurableJWTProcessor<SecurityContext>> jwtProcessorCustomizer(
            AtomicReference<ConfigurableJWTProcessor<SecurityContext>> processorReference
    ) {
        return processor -> {
            processor.setJWTClaimsSetVerifier((claims, context) -> {
                if (!(claims.getClaim("sub") instanceof String)) {
                    throw new BadJWTException("Invalid JWT subject");
                }
            });
            processorReference.set(processor);
        };
    }

    static JwtDecoder configureJwtDecoder(
            ConfigurableJWTProcessor<SecurityContext> processor, String issuer, String audience
    ) {
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(new RawSubjectJwtProcessor(processor));
        OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        issuerAndTimestampValidator,
                        new AudienceValidator(audience),
                        new AccessTokenTypeValidator(),
                        new SubjectValidator(),
                        new ExpirationValidator()
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }

    private record RawSubjectJwtProcessor(JWTProcessor<SecurityContext> delegate)
            implements JWTProcessor<SecurityContext> {

        @Override
        public JWTClaimsSet process(String token, SecurityContext context)
                throws ParseException, BadJOSEException, JOSEException {
            return delegate.process(token, context);
        }

        @Override
        public JWTClaimsSet process(JWT jwt, SecurityContext context)
                throws BadJOSEException, JOSEException {
            verifyRawSubject(jwt);
            return delegate.process(jwt, context);
        }

        @Override
        public JWTClaimsSet process(PlainJWT jwt, SecurityContext context)
                throws BadJOSEException, JOSEException {
            return delegate.process(jwt, context);
        }

        @Override
        public JWTClaimsSet process(SignedJWT jwt, SecurityContext context)
                throws BadJOSEException, JOSEException {
            verifyRawSubject(jwt);
            return delegate.process(jwt, context);
        }

        @Override
        public JWTClaimsSet process(EncryptedJWT jwt, SecurityContext context)
                throws BadJOSEException, JOSEException {
            return delegate.process(jwt, context);
        }

        private static void verifyRawSubject(JWT jwt) throws BadJWTException {
            // Spring passes its already-parsed JWT here. Nimbus would coerce numeric sub to String
            // while constructing JWTClaimsSet, before the claims-set verifier can see its type.
            if (jwt instanceof SignedJWT signedJwt
                    && !(signedJwt.getPayload().toJSONObject().get("sub") instanceof String)) {
                throw new BadJWTException("Invalid JWT subject");
            }
        }
    }
}
