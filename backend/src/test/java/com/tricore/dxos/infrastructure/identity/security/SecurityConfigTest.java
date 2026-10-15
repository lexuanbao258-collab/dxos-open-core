package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.request.controller.RequestController;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RequestController.class,
        properties = {
                "OIDC_ISSUER_URI=https://issuer.example.test",
                "OIDC_AUDIENCE=tricore-api"
        }
)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RequestService requestService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymousRequestBeforeController() throws Exception {
        mvc.perform(get("/api/v1/requests"))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                                {"status":401,"code":"AUTHENTICATION_REQUIRED",
                                 "message":"Authentication is required","errors":{}}
                                """, JsonCompareMode.STRICT)
                );

        verifyNoInteractions(requestService);
    }

    @Test
    void anonymousRequestDoesNotCreateHttpSession() throws Exception {
        mvc.perform(get("/api/v1/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));

        verifyNoInteractions(requestService);
    }

    @Test
    void authenticatedBearerPostDoesNotRequireCsrfToken() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("user-123")
                .build();
        when(jwtDecoder.decode("test-token")).thenReturn(jwt);

        CreateRequestDto input = new CreateRequestDto("Repair printer", "Printer is offline", "IT_SUPPORT");
        UUID id = UUID.fromString("f3bf54b7-fdab-4b45-85dd-94cbd8587a9d");
        Instant timestamp = Instant.parse("2026-10-04T01:00:00Z");
        when(requestService.create(input)).thenReturn(new RequestResponse(
                id, input.title(), input.description(), input.requestType(),
                RequestStatus.NEW, null, null, 0L, timestamp, timestamp));

        mvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Repair printer","description":"Printer is offline",
                                 "requestType":"IT_SUPPORT"}
                                """))
                .andExpect(status().isCreated());

        verify(requestService).create(input);
    }

    @Test
    void spoofedIdentityHeadersCannotAuthenticateAnonymousRequest() throws Exception {
        mvc.perform(get("/api/v1/requests")
                        .header("X-User", "admin")
                        .header("X-Role", "workflow:create"))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                                {"status":401,"code":"AUTHENTICATION_REQUIRED",
                                 "message":"Authentication is required","errors":{}}
                                """, JsonCompareMode.STRICT)
                );

        verifyNoInteractions(requestService);
    }

    @Test
    void rejectsMalformedBearerTokenBeforeController() throws Exception {
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                                {"status":401,"code":"AUTHENTICATION_REQUIRED",
                                 "message":"Authentication is required","errors":{}}
                                """, JsonCompareMode.STRICT),
                        result -> {
                            String authenticate = result.getResponse().getHeader("WWW-Authenticate");
                            assertFalse(authenticate != null && authenticate.toLowerCase(Locale.ROOT)
                                    .contains("error_description"));
                        }
                );

        verifyNoInteractions(requestService);
    }
}
