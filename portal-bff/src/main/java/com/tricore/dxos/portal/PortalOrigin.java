package com.tricore.dxos.portal;

import java.net.URI;

final class PortalOrigin {
    private PortalOrigin() {}

    static String checked(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                || value.endsWith("/")) {
            throw new IllegalArgumentException("Portal public origin must be an HTTPS origin without path or credentials");
        }
        return uri.toString();
    }
}
