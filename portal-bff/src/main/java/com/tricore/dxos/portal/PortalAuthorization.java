package com.tricore.dxos.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class PortalAuthorization {
    private final OAuth2AuthorizedClientManager clients;

    PortalAuthorization(OAuth2AuthorizedClientManager clients) {
        this.clients = clients;
    }

    OAuth2AccessToken usableToken(Authentication authentication, HttpServletRequest request) {
        if (!(authentication instanceof OAuth2AuthenticationToken principal) || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            OAuth2AuthorizeRequest authorize = OAuth2AuthorizeRequest
                    .withClientRegistrationId(principal.getAuthorizedClientRegistrationId()).principal(principal).build();
            OAuth2AuthorizedClient client = clients.authorize(authorize);
            OAuth2AccessToken token = client == null ? null : client.getAccessToken();
            if (token != null && token.getExpiresAt() != null && Instant.now().isBefore(token.getExpiresAt())) {
                return token;
            }
        } catch (RuntimeException exception) {
            // Authorization and refresh failures must not disclose provider details.
        }
        invalidate(request);
        return null;
    }

    void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // A concurrent request may already have invalidated this session.
            }
        }
        SecurityContextHolder.clearContext();
    }
}
