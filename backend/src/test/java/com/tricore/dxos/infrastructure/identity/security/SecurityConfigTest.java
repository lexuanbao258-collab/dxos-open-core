// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.gateway.helpdesk.HelpdeskController;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
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

import java.util.Locale;
import java.util.UUID;
import java.util.List;

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
        controllers = HelpdeskController.class,
        properties = {
                "OIDC_ISSUER_URI=https://issuer.example.test",
                "OIDC_AUDIENCE=tricore-api",
                "dxos.helpdesk.enabled=true"
        }
)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HelpdeskService helpdeskService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymousRequestBeforeController() throws Exception {
        mvc.perform(get("/api/v1/mvp/requests"))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                                {"status":401,"code":"AUTHENTICATION_REQUIRED",
                                 "message":"Authentication is required","errors":{}}
                                """, JsonCompareMode.STRICT)
                );

        verifyNoInteractions(helpdeskService);
    }

    @Test
    void anonymousRequestDoesNotCreateHttpSession() throws Exception {
        mvc.perform(get("/api/v1/mvp/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));

        verifyNoInteractions(helpdeskService);
    }

    @Test
    void anonymousPostReturnsSafeUnauthorizedWithoutCreatingSession() throws Exception {
        mvc.perform(post("/api/v1/mvp/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Repair printer","description":"Printer is offline",
                                 "requestType":"IT_HELPDESK"}
                                """))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                                {"status":401,"code":"AUTHENTICATION_REQUIRED",
                                 "message":"Authentication is required","errors":{}}
                                """, JsonCompareMode.STRICT),
                        result -> assertNull(result.getRequest().getSession(false))
                );

        verifyNoInteractions(helpdeskService);
    }
    @Test
    void authenticatedBearerPostDoesNotRequireCsrfToken() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("user-123")
                .build();
        when(jwtDecoder.decode("test-token")).thenReturn(jwt);

        UUID id = UUID.fromString("f3bf54b7-fdab-4b45-85dd-94cbd8587a9d");
        when(helpdeskService.create("Repair printer", "Printer is offline"))
                .thenReturn(new HelpdeskService.RequestView(
                        id, "Repair printer", "Printer is offline", "IT_HELPDESK",
                        "user-123", null, null, 0L, "workflow-id", "SUBMITTED", 0L,
                        List.of(), null, null, null));

        mvc.perform(post("/api/v1/mvp/requests")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Repair printer","description":"Printer is offline",
                                 "requestType":"IT_HELPDESK"}
                                """))
                .andExpect(status().isCreated());

        verify(helpdeskService).create("Repair printer", "Printer is offline");
    }

    @Test
    void spoofedIdentityHeadersCannotAuthenticateAnonymousRequest() throws Exception {
        mvc.perform(get("/api/v1/mvp/requests")
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

        verifyNoInteractions(helpdeskService);
    }

    @Test
    void rejectsMalformedBearerTokenBeforeController() throws Exception {
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mvc.perform(get("/api/v1/mvp/requests")
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

        verifyNoInteractions(helpdeskService);
    }
    @Test
    void rejectedJwtSubjectReturnsSafeUnauthorizedBeforeController() throws Exception {
        when(jwtDecoder.decode("invalid-subject-token"))
                .thenThrow(new BadJwtException("JWT subject is missing or invalid"));

        mvc.perform(get("/api/v1/mvp/requests")
                        .header("Authorization", "Bearer invalid-subject-token"))
                .andExpectAll(
                        status().isUnauthorized(),
                        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                        content().json("""
                            {"status":401,"code":"AUTHENTICATION_REQUIRED",
                             "message":"Authentication is required","errors":{}}
                            """, JsonCompareMode.STRICT),
                        result -> {
                            String body = result.getResponse().getContentAsString();
                            assertFalse(body.contains("JWT subject"));
                            assertFalse(body.contains("invalid-subject-token"));
                        }
                );

        verifyNoInteractions(helpdeskService);
    }
}
