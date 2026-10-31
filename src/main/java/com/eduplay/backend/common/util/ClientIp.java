package com.eduplay.backend.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * Remote address of the caller. Behind a reverse proxy, set FORWARD_HEADERS_STRATEGY=native so
     * Tomcat resolves this from X-Forwarded-For only for trusted proxies; raw headers are never read
     * here because clients can forge them.
     */
    public static String of(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}
