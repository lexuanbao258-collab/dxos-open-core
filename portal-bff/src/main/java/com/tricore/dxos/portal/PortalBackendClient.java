package com.tricore.dxos.portal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/bff/api/v1/mvp/requests")
class PortalBackendClient {
    private static final int MAX_BODY_BYTES = 32_768;
    private static final int MAX_RESPONSE_BYTES = 2_000_000;
    private static final Set<String> ACTIONS = Set.of("triage", "assign", "start", "resolve", "confirm");
    private final PortalAuthorization authorization;
    private final URI backend;
    private final HttpClient http;

    @Autowired
    PortalBackendClient(PortalAuthorization authorization,
                        @Value("${dxos.portal-bff.backend-base-url}") String backendBaseUrl) {
        this(authorization, backendBaseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    PortalBackendClient(PortalAuthorization authorization, String backendBaseUrl, HttpClient http) {
        this.authorization = authorization;
        this.backend = checkedBackend(backendBaseUrl);
        this.http = http;
    }

    static URI checkedBackend(String value) {
        URI uri = URI.create(value);
        boolean secure = "https".equals(uri.getScheme());
        boolean loopback = "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
        if ((!secure && !loopback) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("Backend URL must be an HTTPS origin or loopback HTTP origin");
        }
        return uri;
    }

    @GetMapping
    ResponseEntity<?> list(@RequestParam(required = false) UUID after,
                           @RequestParam(defaultValue = "50") int limit,
                           OAuth2AuthenticationToken principal, HttpServletRequest request) {
        if (limit < 1 || limit > 100) return invalid();
        String query = "?limit=" + limit + (after == null ? "" : "&after=" + after);
        return forward("GET", "/api/v1/mvp/requests" + query, null, principal, request);
    }

    @PostMapping
    ResponseEntity<?> create(HttpServletRequest request, OAuth2AuthenticationToken principal) {
        return forwardWrite("/api/v1/mvp/requests", request, principal);
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable UUID id, OAuth2AuthenticationToken principal,
                          HttpServletRequest request) {
        return forward("GET", "/api/v1/mvp/requests/" + id, null, principal, request);
    }

    @GetMapping("/{id}/history")
    ResponseEntity<?> history(@PathVariable UUID id, OAuth2AuthenticationToken principal,
                              HttpServletRequest request) {
        return forward("GET", "/api/v1/mvp/requests/" + id + "/history", null, principal, request);
    }

    @PostMapping("/{id}/{action}")
    ResponseEntity<?> action(@PathVariable UUID id, @PathVariable String action,
                             HttpServletRequest request, OAuth2AuthenticationToken principal) {
        if (!ACTIONS.contains(action)) return ResponseEntity.notFound().build();
        return forwardWrite("/api/v1/mvp/requests/" + id + "/" + action, request, principal);
    }

    private ResponseEntity<?> forwardWrite(String path, HttpServletRequest request,
                                           OAuth2AuthenticationToken principal) {
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) return ResponseEntity.status(413).build();
            return forward("POST", path, body, principal, request);
        } catch (IOException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private ResponseEntity<?> forward(String method, String path, byte[] body,
                                      OAuth2AuthenticationToken principal, HttpServletRequest servletRequest) {
        OAuth2AccessToken token = authorization.usableToken(principal, servletRequest);
        if (token == null) return ResponseEntity.status(401).build();
        URI target = backend.resolve(path);
        HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token.getTokenValue())
                .header("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (body == null) request.GET();
        else request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream upstream = response.body()) {
                int status = response.statusCode();
                if (status == 401) {
                    authorization.invalidate(servletRequest);
                    return ResponseEntity.status(401).build();
                }
                if (status < 200 || status > 599 || status >= 300 && status < 400) {
                    return ResponseEntity.status(502).build();
                }
                byte[] responseBody = upstream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (responseBody.length > MAX_RESPONSE_BYTES) return ResponseEntity.status(502).build();
                return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                        .header("Cache-Control", "no-store").body(responseBody);
            }
        } catch (IOException exception) {
            // A POST may have committed before the connection failed. Never retry it automatically.
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", body == null ? "UPSTREAM_UNAVAILABLE" : "COMMIT_OUTCOME_UNKNOWN"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", body == null ? "UPSTREAM_UNAVAILABLE" : "COMMIT_OUTCOME_UNKNOWN"));
        }
    }

    private static ResponseEntity<?> invalid() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
