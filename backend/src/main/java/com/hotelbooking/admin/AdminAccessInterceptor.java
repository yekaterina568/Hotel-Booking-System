package com.hotelbooking.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class AdminAccessInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private final byte[] token;

    AdminAccessInterceptor(String token) {
        this.token = token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (token.length == 0) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return false;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                && MessageDigest.isEqual(token, authorization.substring(BEARER_PREFIX.length())
                        .getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
