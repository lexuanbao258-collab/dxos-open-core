package com.tricore.dxos.portal;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortalBackendClientTest {
    @Test
    void publicOriginIsPinnedHttps() {
        assertEquals("https://portal.example.test", PortalOrigin.checked("https://portal.example.test"));
        for (String value : new String[]{"http://portal.example.test", "https://portal.example.test/path",
                "https://user:secret@portal.example.test", "https://portal.example.test?next=other"}) {
            assertThrows(IllegalArgumentException.class, () -> PortalOrigin.checked(value), value);
        }
    }

    @Test
    void backendOriginMustBeHttpsOrLiteralLoopback() {
        assertEquals("https://api.example.test", PortalBackendClient.checkedBackend("https://api.example.test").toString());
        assertEquals("http://127.0.0.1:8080", PortalBackendClient.checkedBackend("http://127.0.0.1:8080").toString());
        for (String value : new String[]{"http://api.example.test", "http://localhost:8080",
                "https://user:secret@api.example.test", "https://api.example.test/other",
                "https://api.example.test?target=other"}) {
            assertThrows(IllegalArgumentException.class, () -> PortalBackendClient.checkedBackend(value), value);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyServerHeldBearerIsForwardedAndRedirectsAreRejected() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<InputStream> upstream = response(302, new ByteArrayInputStream(new byte[0]));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(upstream);
        PortalBackendClient bff = bff(manager(), http);
        ResponseEntity<?> result = bff.get(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                principal(), new MockHttpServletRequest());
        assertEquals(502, result.getStatusCode().value());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer server-access-token", request.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals("https://api.example.test/api/v1/mvp/requests/00000000-0000-0000-0000-000000000001",
                request.getValue().uri().toString());
        assertTrue(request.getValue().headers().firstValue("Cookie").isEmpty());
        assertTrue(request.getValue().headers().firstValue("X-User").isEmpty());
        assertTrue(request.getValue().headers().firstValue("X-Role").isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedWriteIsReportedAsUncertainWithoutRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection lost after send"));
        PortalBackendClient bff = bff(manager(), http);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<?> result = bff.create(request, principal());
        assertEquals(503, result.getStatusCode().value());
        assertEquals("COMMIT_OUTCOME_UNKNOWN", ((Map<?, ?>) result.getBody()).get("code"));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void oversizedWriteIsRejectedBeforeAuthorizationOrNetwork() {
        HttpClient http = mock(HttpClient.class);
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        PortalBackendClient bff = bff(manager, http);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[32_769]);
        assertEquals(413, bff.create(request, principal()).getStatusCode().value());
        verifyNoInteractions(manager, http);
    }

    @Test
    void upstreamBodyBelowLimitIsReturnedAndClosed() throws Exception {
        byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CloseTrackingInputStream stream = new CloseTrackingInputStream(body);
        HttpClient http = httpResponse(200, stream);

        ResponseEntity<?> result = bff(manager(), http).get(id(), principal(), new MockHttpServletRequest());

        assertEquals(200, result.getStatusCode().value());
        assertTrue(java.util.Arrays.equals(body, (byte[]) result.getBody()));
        assertTrue(stream.closed);
    }

    @Test
    void upstreamBodyAtLimitIsReturnedAndClosed() throws Exception {
        CloseTrackingInputStream stream = new CloseTrackingInputStream(new byte[2_000_000]);
        HttpClient http = httpResponse(200, stream);

        ResponseEntity<?> result = bff(manager(), http).get(id(), principal(), new MockHttpServletRequest());

        assertEquals(200, result.getStatusCode().value());
        assertEquals(2_000_000, ((byte[]) result.getBody()).length);
        assertTrue(stream.closed);
    }

    @Test
    void upstreamBodyOverLimitIsRejectedAndClosedBeforeFullRead() throws Exception {
        CloseTrackingInputStream stream = new CloseTrackingInputStream(new byte[2_000_010]);
        HttpClient http = httpResponse(200, stream);

        ResponseEntity<?> result = bff(manager(), http).get(id(), principal(), new MockHttpServletRequest());

        assertEquals(502, result.getStatusCode().value());
        assertEquals(null, result.getBody());
        assertEquals(9, stream.available());
        assertTrue(stream.closed);
    }

    @Test
    void upstreamReadFailureOnWriteIsUncertainAndNeverReplayed() throws Exception {
        FailingInputStream stream = new FailingInputStream();
        HttpClient http = httpResponse(200, stream);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ResponseEntity<?> result = bff(manager(), http).create(request, principal());

        assertEquals(503, result.getStatusCode().value());
        assertEquals(Map.of("code", "COMMIT_OUTCOME_UNKNOWN"), result.getBody());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertTrue(stream.closed);
    }

    @Test
    void missingAuthorizationInvalidatesSessionWithoutCallingBackend() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        assertUnusable(manager);
    }

    @Test
    void missingBackendTokenInvalidatesSessionWithoutCallingBackend() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(manager.authorize(any())).thenReturn(client);
        assertUnusable(manager);
    }

    @Test
    void expiredTokenWithoutRefreshInvalidatesSessionWithoutCallingBackend() {
        assertUnusable(managerWithToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "expired-token", Instant.now().minusSeconds(300), Instant.now().minusSeconds(1))));
    }

    @Test
    void refreshedTokenIsUsedInsteadOfExpiredToken() throws Exception {
        OAuth2AuthorizedClientManager manager = managerWithToken(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "refreshed-token", Instant.now(), Instant.now().plusSeconds(300)));
        HttpClient http = httpResponse(200, new ByteArrayInputStream(new byte[0]));

        ResponseEntity<?> result = bff(manager, http).get(id(), principal(), new MockHttpServletRequest());

        assertEquals(200, result.getStatusCode().value());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer refreshed-token", request.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void refreshFailureInvalidatesSessionWithoutCallingBackend() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenThrow(new OAuth2AuthorizationException(
                new OAuth2Error("invalid_grant", "sensitive provider detail", null)));
        assertUnusable(manager);
    }

    @Test
    void revokedBackendAuthorizationReturnsSanitized401AndInvalidatesSession() throws Exception {
        HttpClient http = httpResponse(401, new ByteArrayInputStream("sensitive".getBytes()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        ResponseEntity<?> result = bff(manager(), http).get(id(), principal(), request);

        assertEquals(401, result.getStatusCode().value());
        assertEquals(null, result.getBody());
        assertTrue(session.isInvalid());
    }

    @Test
    void backend403PreservesSessionAndStatus() throws Exception {
        HttpClient http = httpResponse(403, new ByteArrayInputStream("{}".getBytes()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        ResponseEntity<?> result = bff(manager(), http).get(id(), principal(), request);

        assertEquals(403, result.getStatusCode().value());
        assertTrue(!session.isInvalid());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void onlyApprovedPathsAndMethodsAreMapped() throws Exception {
        HttpClient http = mock(HttpClient.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(bff(manager(), http)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/bff/api/v1/mvp/other"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/bff/api/v1/mvp/requests/00000000-0000-0000-0000-000000000001/delete"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/bff/api/v1/mvp/requests"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
        verifyNoInteractions(http);
    }

    private static void assertUnusable(OAuth2AuthorizedClientManager manager) {
        HttpClient http = mock(HttpClient.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();

        ResponseEntity<?> result = bff(manager, http).get(id(), principal(), request);

        assertEquals(401, result.getStatusCode().value());
        assertEquals(null, result.getBody());
        assertTrue(session.isInvalid());
        verifyNoInteractions(http);
    }

    private static PortalBackendClient bff(OAuth2AuthorizedClientManager manager, HttpClient http) {
        return new PortalBackendClient(new PortalAuthorization(manager), "https://api.example.test", http);
    }

    private static UUID id() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static HttpClient httpResponse(int status, InputStream stream) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<InputStream> upstream = response(status, stream);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(upstream);
        return http;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, InputStream stream) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(stream);
        return response;
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingInputStream extends InputStream {
        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("secret upstream detail");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static OAuth2AuthorizedClientManager manager() {
        return managerWithToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "server-access-token", Instant.now(), Instant.now().plusSeconds(300)));
    }

    private static OAuth2AuthorizedClientManager managerWithToken(OAuth2AccessToken token) {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId("test").clientSecret("test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://idp.example.test/auth")
                .tokenUri("https://idp.example.test/token").build();
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "test-user", token));
        return manager;
    }

    private static OAuth2AuthenticationToken principal() {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "test-user"), "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak");
    }
}
