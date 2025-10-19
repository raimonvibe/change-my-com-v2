package com.raimonvibe.imageconverter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitFilter
 * Tests rate limiting logic for authenticated/anonymous users and conversion endpoints
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter Unit Tests")
public class RateLimitFilterTest {

    @Mock
    private SecurityAuditLogger auditLogger;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Reset the filter to clear buckets between tests
        rateLimitFilter = new RateLimitFilter();

        // Use reflection to set the private auditLogger field
        var field = RateLimitFilter.class.getDeclaredField("auditLogger");
        field.setAccessible(true);
        field.set(rateLimitFilter, auditLogger);
    }

    // ==================== ANONYMOUS USER TESTS ====================

    @Test
    @DisplayName("Should allow request for anonymous user within limit")
    void testAnonymousUser_WithinLimit() throws IOException, ServletException {
        // Given: Anonymous user (no principal)
        request.setRemoteAddr("192.168.1.100");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Request should be allowed
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);

        // Verify headers
        assertNotNull(response.getHeader("X-RateLimit-Limit"));
        assertEquals("60", response.getHeader("X-RateLimit-Limit")); // Anonymous limit
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should block anonymous user after exceeding limit (60 requests)")
    void testAnonymousUser_ExceedLimit() throws IOException, ServletException {
        // Given: Anonymous user making many requests
        request.setRemoteAddr("192.168.1.101");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make 61 requests (limit is 60)
        for (int i = 0; i < 60; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus(), "Request " + (i + 1) + " should succeed");
        }

        // 61st request should be blocked
        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should return 429 Too Many Requests
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        verify(auditLogger, times(1)).logRateLimitExceeded(anyString(), anyString());
    }

    // ==================== AUTHENTICATED USER TESTS ====================

    @Test
    @DisplayName("Should allow request for authenticated user within limit")
    void testAuthenticatedUser_WithinLimit() throws IOException, ServletException {
        // Given: Authenticated user
        Principal principal = () -> "test@example.com";
        request.setUserPrincipal(principal);
        request.setRemoteAddr("192.168.1.102");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Request should be allowed
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);

        // Verify headers
        assertEquals("300", response.getHeader("X-RateLimit-Limit")); // Authenticated limit
    }

    @Test
    @DisplayName("Should allow more requests for authenticated users (300 vs 60)")
    void testAuthenticatedUser_HigherLimit() throws IOException, ServletException {
        // Given: Authenticated user
        Principal principal = () -> "test@example.com";

        // When: Make 100 requests (would exceed anonymous limit of 60)
        int successCount = 0;
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setUserPrincipal(principal);
            req.setRemoteAddr("192.168.1.103");
            req.setMethod("GET");
            req.setRequestURI("/api/user/me");

            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitFilter.doFilter(req, res, filterChain);

            if (res.getStatus() == 200) {
                successCount++;
            }
        }

        // Then: All should succeed (authenticated limit is 300)
        assertEquals(100, successCount, "All 100 requests should succeed for authenticated user");
    }

    // ==================== CONVERSION ENDPOINT TESTS ====================

    @Test
    @DisplayName("Should apply separate limit to /api/convert endpoint (10 req/min)")
    void testConvertEndpoint_SeparateLimit() throws IOException, ServletException {
        // Given: Convert request
        request.setRemoteAddr("192.168.1.104");
        request.setMethod("POST");
        request.setRequestURI("/api/convert");

        // When: Make 11 requests (conversion limit is 10)
        for (int i = 0; i < 10; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus(), "Request " + (i + 1) + " should succeed");
            assertEquals("10", response.getHeader("X-RateLimit-Limit")); // Convert limit
        }

        // 11th request should be blocked
        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should be rate limited
        assertEquals(429, response.getStatus());
    }

    @Test
    @DisplayName("Should apply separate limit to /api/convert/gif endpoint")
    void testConvertGifEndpoint_SeparateLimit() throws IOException, ServletException {
        // Given: GIF convert request
        request.setRemoteAddr("192.168.1.105");
        request.setMethod("POST");
        request.setRequestURI("/api/convert/gif");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should use convert limit (10)
        assertEquals(200, response.getStatus());
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    @DisplayName("Should apply separate limit to /stripe/webhook endpoint")
    void testWebhookEndpoint_SeparateLimit() throws IOException, ServletException {
        // Given: Webhook request
        request.setRemoteAddr("192.168.1.106");
        request.setMethod("POST");
        request.setRequestURI("/stripe/webhook");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should use convert limit (10)
        assertEquals(200, response.getStatus());
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    @DisplayName("Should NOT apply conversion limit to non-POST requests")
    void testConvertEndpoint_GetRequest() throws IOException, ServletException {
        // Given: GET request to convert endpoint
        request.setRemoteAddr("192.168.1.107");
        request.setMethod("GET");
        request.setRequestURI("/api/convert");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should use general limit (60), not conversion limit (10)
        assertEquals(200, response.getStatus());
        assertEquals("60", response.getHeader("X-RateLimit-Limit"));
    }

    // ==================== BUCKET ISOLATION TESTS ====================

    @Test
    @DisplayName("Should track different IPs separately")
    void testDifferentIps_SeparateBuckets() throws IOException, ServletException {
        // Given: Two different IP addresses
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("192.168.1.100");
        request1.setMethod("GET");
        request1.setRequestURI("/api/user/me");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("192.168.1.101");
        request2.setMethod("GET");
        request2.setRequestURI("/api/user/me");

        // When: IP1 uses all 60 requests
        for (int i = 0; i < 60; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request1, response, filterChain);
        }

        // IP2 makes first request
        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request2, response, filterChain);

        // Then: IP2 should still be allowed (separate bucket)
        assertEquals(200, response.getStatus());
        assertEquals("59", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Should track general and conversion buckets separately for same IP")
    void testSameIp_SeparateBucketsForConversion() throws IOException, ServletException {
        // Given: Same IP, different endpoints
        String ip = "192.168.1.108";

        // General endpoint request
        MockHttpServletRequest generalRequest = new MockHttpServletRequest();
        generalRequest.setRemoteAddr(ip);
        generalRequest.setMethod("GET");
        generalRequest.setRequestURI("/api/user/me");

        // Conversion endpoint request
        MockHttpServletRequest convertRequest = new MockHttpServletRequest();
        convertRequest.setRemoteAddr(ip);
        convertRequest.setMethod("POST");
        convertRequest.setRequestURI("/api/convert");

        // When: Use all general requests (60)
        for (int i = 0; i < 60; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(generalRequest, response, filterChain);
        }

        // Try conversion request
        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(convertRequest, response, filterChain);

        // Then: Conversion should still work (separate bucket)
        assertEquals(200, response.getStatus());
        assertEquals("10", response.getHeader("X-RateLimit-Limit"));
    }

    // ==================== HEADER TESTS ====================

    @Test
    @DisplayName("Should set rate limit headers on success")
    void testRateLimitHeaders_Success() throws IOException, ServletException {
        // Given: Anonymous user
        request.setRemoteAddr("192.168.1.109");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Headers should be set
        assertNotNull(response.getHeader("X-RateLimit-Limit"));
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
        assertNotNull(response.getHeader("X-RateLimit-Reset"));
        assertEquals("60", response.getHeader("X-RateLimit-Limit"));
        assertEquals("59", response.getHeader("X-RateLimit-Remaining")); // 60 - 1
    }

    @Test
    @DisplayName("Should set Retry-After header when rate limited")
    void testRetryAfterHeader_WhenLimited() throws IOException, ServletException {
        // Given: Anonymous user at limit
        request.setRemoteAddr("192.168.1.110");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Exceed limit
        for (int i = 0; i < 60; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // 61st request
        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Retry-After header should be set
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(Integer.parseInt(response.getHeader("Retry-After")) > 0);
    }

    @Test
    @DisplayName("Should decrement remaining count with each request")
    void testRemainingCount_Decrements() throws IOException, ServletException {
        // Given: Anonymous user
        request.setRemoteAddr("192.168.1.111");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make 3 requests
        rateLimitFilter.doFilter(request, response, filterChain);
        String remaining1 = response.getHeader("X-RateLimit-Remaining");

        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, filterChain);
        String remaining2 = response.getHeader("X-RateLimit-Remaining");

        response = new MockHttpServletResponse();
        rateLimitFilter.doFilter(request, response, filterChain);
        String remaining3 = response.getHeader("X-RateLimit-Remaining");

        // Then: Remaining should decrease
        assertEquals("59", remaining1);
        assertEquals("58", remaining2);
        assertEquals("57", remaining3);
    }

    // ==================== IP NORMALIZATION TESTS ====================

    @Test
    @DisplayName("Should normalize IPv6 addresses by removing brackets")
    void testIpNormalization_IPv6() throws IOException, ServletException {
        // Given: IPv6 address with brackets
        request.setRemoteAddr("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should work (brackets normalized)
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle null IP address")
    void testIpNormalization_NullIp() throws IOException, ServletException {
        // Given: Null IP address
        request.setRemoteAddr(null);
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Make request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then: Should work (IP normalized to "unknown")
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ==================== AUDIT LOGGING TESTS ====================

    @Test
    @DisplayName("Should log rate limit exceeded event")
    void testAuditLogging_RateLimitExceeded() throws IOException, ServletException {
        // Given: Anonymous user at limit
        request.setRemoteAddr("192.168.1.112");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Exceed limit
        for (int i = 0; i < 61; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then: Audit log should be called
        verify(auditLogger, times(1)).logRateLimitExceeded(
            eq("ip:192.168.1.112"),
            eq("/api/user/me")
        );
    }

    @Test
    @DisplayName("Should include user principal in audit log for authenticated users")
    void testAuditLogging_AuthenticatedUser() throws IOException, ServletException {
        // Given: Authenticated user at limit
        Principal principal = () -> "test@example.com";
        request.setUserPrincipal(principal);
        request.setRemoteAddr("192.168.1.113");
        request.setMethod("GET");
        request.setRequestURI("/api/user/me");

        // When: Exceed limit (300 requests)
        for (int i = 0; i < 301; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then: Audit log should include user principal
        verify(auditLogger, times(1)).logRateLimitExceeded(
            eq("u:test@example.com"),
            eq("/api/user/me")
        );
    }
}
