package com.raimonvibe.imageconverter.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    private static final long AUTH_LIMIT = 120;   // requests per minuut voor ingelogde users
    private static final long ANON_LIMIT = 30;    // requests per minuut voor anonieme users
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
private Bucket newBucket(boolean isAuth) {
    long limit = isAuth ? AUTH_LIMIT : ANON_LIMIT;
    return Bucket.builder()
        .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, REFILL_PERIOD)))
        .build();
}

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        boolean isAuth = request.getUserPrincipal() != null;
        String key = isAuth
                ? "u:" + request.getUserPrincipal().getName()
                : "ip:" + normalizeIp(request.getRemoteAddr());

        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(isAuth));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(isAuth ? AUTH_LIMIT : ANON_LIMIT));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            chain.doFilter(request, response);
        } else {
            long waitSec = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("Retry-After", String.valueOf(waitSec));
            response.sendError(429, "Too Many Requests");
        }
    }

    private String normalizeIp(String addr) {
        if (addr == null) return "unknown";
        return addr.replaceAll("^\\[|\\]$", "");
    }
}
