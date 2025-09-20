package com.raimonvibe.imageconverter.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    // Production-optimized limits
    private static final long AUTH_LIMIT = 300;   // requests per minute for authenticated users
    private static final long ANON_LIMIT = 60;    // requests per minute for anonymous users
    private static final long CONVERT_LIMIT = 10; // conversions per minute (separate bucket)
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Autowired
    private SecurityAuditLogger auditLogger;
    
    private Bucket newBucket(boolean isAuth) {
        long limit = isAuth ? AUTH_LIMIT : ANON_LIMIT;
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(limit)
                .refillIntervally(limit, REFILL_PERIOD)
                .build())
            .build();
    }
    
    private Bucket newConvertBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(CONVERT_LIMIT)
                .refillIntervally(CONVERT_LIMIT, REFILL_PERIOD)
                .build())
            .build();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        boolean isAuth = request.getUserPrincipal() != null;
        String baseKey = isAuth
                ? "u:" + request.getUserPrincipal().getName()
                : "ip:" + normalizeIp(request.getRemoteAddr());

        // Separate rate limiting for convert endpoint
        String requestPath = request.getRequestURI();
        boolean isConvertRequest = "/api/convert".equals(requestPath) && "POST".equals(request.getMethod());
        
        String bucketKey = isConvertRequest ? baseKey + ":convert" : baseKey;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> 
            isConvertRequest ? newConvertBucket() : newBucket(isAuth));
            
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            long limit = isConvertRequest ? CONVERT_LIMIT : (isAuth ? AUTH_LIMIT : ANON_LIMIT);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            chain.doFilter(request, response);
        } else {
            long waitSec = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("Retry-After", String.valueOf(waitSec));
            
            // Log rate limit exceeded
            auditLogger.logRateLimitExceeded(baseKey, requestPath);
            
            response.sendError(429, "Too Many Requests");
        }
    }

    private String normalizeIp(String addr) {
        if (addr == null) return "unknown";
        return addr.replaceAll("^\\[|\\]$", "");
    }
}
