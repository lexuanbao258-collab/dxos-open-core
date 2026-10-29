package com.tricore.dxos.portal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

@WebMvcTest(controllers = PortalSessionController.class,
        properties = {"dxos.portal-bff.backend-base-url=http://127.0.0.1:1",
                "dxos.portal-bff.public-origin=https://portal.example.test"})
@Import({PortalSecurity.class, PortalAuthorization.class, PortalSecurityTest.Registration.class})
class PortalSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean OAuth2AuthorizedClientManager manager;

    @BeforeEach
    void usableAuthorization() {
        OAuth2AuthorizedClient client = org.mockito.Mockito.mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "test-only-access-token", Instant.now(), Instant.now().plusSeconds(300)));
        when(manager.authorize(any())).thenReturn(client);
    }

    @Test
    void anonymousSessionDoesNotExposeTokens() throws Exception {
        mvc.perform(get("/bff/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void forgedHeadersCannotEnterBackendApi() throws Exception {
        mvc.perform(get("/bff/api/v1/mvp/requests")
                        .header("X-User", "admin").header("X-Role", "workflow:read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrf() throws Exception {
        mvc.perform(post("/bff/logout").with(oauth2Login()))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedInSessionReturnsOnlySessionStateAndCsrf() throws Exception {
        mvc.perform(get("/bff/session").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.csrfToken").isString())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void failedRefreshDoesNotReportHealthySessionOrExposeProviderDetails() throws Exception {
        when(manager.authorize(any())).thenThrow(new OAuth2AuthorizationException(
                new OAuth2Error("invalid_grant", "sensitive provider detail", null)));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/bff/session").session(session).with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.csrfToken").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void missingAuthorizationDoesNotReportHealthySession() throws Exception {
        when(manager.authorize(any())).thenReturn(null);
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/bff/session").session(session).with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.csrfToken").doesNotExist());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void authorizationRedirectUsesPkce() throws Exception {
        String redirect = mvc.perform(get("/oauth2/authorization/keycloak"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).contains("code_challenge=").contains("code_challenge_method=S256")
                .contains("state=");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Registration {
        @Bean
        ClientRegistrationRepository registrations() {
            ClientRegistration client = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("portal-test").clientSecret("test-only")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("https://portal.example.test/login/oauth2/code/keycloak")
                    .scope("openid")
                    .authorizationUri("https://idp.example.test/auth")
                    .tokenUri("https://idp.example.test/token")
                    .jwkSetUri("https://idp.example.test/keys")
                    .userInfoUri("https://idp.example.test/userinfo")
                    .userNameAttributeName("sub").build();
            return new InMemoryClientRegistrationRepository(client);
        }
    }
}
