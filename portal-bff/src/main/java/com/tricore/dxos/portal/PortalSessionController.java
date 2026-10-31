package com.tricore.dxos.portal;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class PortalSessionController {
    private final PortalAuthorization authorization;

    PortalSessionController(PortalAuthorization authorization) {
        this.authorization = authorization;
    }

    @GetMapping("/bff/session")
    ResponseEntity<Map<String, Object>> session(Authentication authentication, CsrfToken csrf,
                                                HttpServletRequest request) {
        boolean active = authorization.usableToken(authentication, request) != null;
        Map<String, Object> body = active
                ? Map.of("authenticated", true, "csrfToken", csrf.getToken())
                : Map.of("authenticated", false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
