package com.raimonvibe.imageconverter.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single source of truth for resolving the real client IP behind proxies.
 * Replaces the getClientIp copies that previously lived in ConvertController,
 * AnonymousUserController, RateLimitFilter, SecurityAuditLogger and
 * WebhookRateLimitInterceptor, so rate limiting and anonymous quotas always
 * agree on the client identity.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * Resolution order: first entry of X-Forwarded-For, then X-Real-IP, then the socket address.
     */
    public static String resolve(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isUsable(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isUsable(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isUsable(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
