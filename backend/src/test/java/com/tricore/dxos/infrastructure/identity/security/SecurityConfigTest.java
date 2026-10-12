package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.request.controller.RequestController;
import com.tricore.dxos.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(requestService);
    }

    @Test
    void rejectsMalformedBearerTokenBeforeController() throws Exception {
        when(jwtDecoder.decode(anyString()))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(requestService);
    }
}
