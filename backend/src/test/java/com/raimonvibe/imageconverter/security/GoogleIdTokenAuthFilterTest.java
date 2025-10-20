package com.raimonvibe.imageconverter.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Security tests for GoogleIdTokenAuthFilter
 * Tests OAuth authentication to prevent authentication bypass and token attacks
 *
 * SECURITY FOCUS:
 * - Token signature verification
 * - Expired token rejection
 * - Invalid token handling
 * - Token replay attacks
 * - Missing/malformed Authorization header
 * - Email claim validation
 * - Authentication context isolation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleIdTokenAuthFilter Security Tests")
public class GoogleIdTokenAuthFilterTest {

    @Mock
    private UserService userService;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private GoogleIdTokenAuthFilter googleIdTokenAuthFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User testUser;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Clear security context before each test
        SecurityContextHolder.clearContext();

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
    }

    // ==================== VALID TOKEN TESTS ====================

    @Test
    @DisplayName("Should authenticate user with valid token")
    void testValidToken() throws Exception {
        // Given: Valid token
        String validToken = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + validToken);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(validToken)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(userService.ensureUserByEmail("test@example.com")).thenReturn(testUser);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: User should be authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("test@example.com", auth.getPrincipal());
        assertTrue(auth.isAuthenticated());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ==================== INVALID TOKEN TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject expired token")
    void testExpiredToken() throws Exception {
        // Given: Expired token
        String expiredToken = "expired.jwt.token";
        request.addHeader("Authorization", "Bearer " + expiredToken);

        // Token verifier returns null for expired/invalid tokens
        when(googleIdTokenVerifier.verify(expiredToken)).thenReturn(null);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: User should NOT be authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(userService, never()).ensureUserByEmail(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("SECURITY: Should reject token with invalid signature")
    void testInvalidSignature() throws Exception {
        // Given: Token with invalid signature
        String invalidToken = "invalid.signature.token";
        request.addHeader("Authorization", "Bearer " + invalidToken);

        // Google verifier throws exception or returns null for invalid signature
        when(googleIdTokenVerifier.verify(invalidToken))
            .thenThrow(new IOException("Invalid signature"));

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: User should NOT be authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(userService, never()).ensureUserByEmail(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("SECURITY: Should reject malformed token")
    void testMalformedToken() throws Exception {
        // Given: Malformed token (not valid JWT format)
        String malformedToken = "not-a-valid-jwt";
        request.addHeader("Authorization", "Bearer " + malformedToken);

        when(googleIdTokenVerifier.verify(malformedToken))
            .thenThrow(new IllegalArgumentException("Malformed token"));

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: User should NOT be authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(userService, never()).ensureUserByEmail(anyString());
    }

    // ==================== MISSING/EMPTY HEADER TESTS ====================

    @Test
    @DisplayName("SECURITY: Should allow request without Authorization header (anonymous)")
    void testNoAuthorizationHeader() throws Exception {
        // Given: No Authorization header
        // (request has no auth header)

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Request should continue without authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(googleIdTokenVerifier, never()).verify(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("SECURITY: Should ignore empty Authorization header")
    void testEmptyAuthorizationHeader() throws Exception {
        // Given: Empty Authorization header
        request.addHeader("Authorization", "");

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should be treated as anonymous
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(googleIdTokenVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("SECURITY: Should ignore Authorization header without Bearer prefix")
    void testAuthorizationWithoutBearer() throws Exception {
        // Given: Authorization header without "Bearer " prefix
        request.addHeader("Authorization", "some-token");

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should be ignored
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(googleIdTokenVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("SECURITY: Should handle Bearer token with only whitespace")
    void testBearerWithOnlyWhitespace() throws Exception {
        // Given: Bearer with only whitespace
        request.addHeader("Authorization", "Bearer    ");

        // Whitespace-only token
        when(googleIdTokenVerifier.verify("   ")).thenReturn(null);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should not authenticate
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
    }

    // ==================== EMAIL CLAIM VALIDATION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should reject token without email claim")
    void testTokenWithoutEmail() throws Exception {
        // Given: Valid token but no email claim
        String tokenWithoutEmail = "valid.token.no.email";
        request.addHeader("Authorization", "Bearer " + tokenWithoutEmail);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(tokenWithoutEmail)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(null); // No email claim

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should not authenticate
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(userService, never()).ensureUserByEmail(anyString());
    }

    @Test
    @DisplayName("SECURITY NOTE: Empty email strings are processed (needs validation)")
    void testTokenWithEmptyEmail() throws Exception {
        // SECURITY NOTE: Current implementation doesn't validate empty email strings
        // Empty email passes the null check on line 56 of GoogleIdTokenAuthFilter
        //
        // RECOMMENDATION: Add validation:
        //   if (email != null && !email.isBlank()) {
        //
        // Current behavior documented below:

        String token = "valid.token.empty.email";
        request.addHeader("Authorization", "Bearer " + token);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(""); // Empty email

        User emptyEmailUser = new User();
        emptyEmailUser.setId(999L);
        emptyEmailUser.setEmail("");
        when(userService.ensureUserByEmail("")).thenReturn(emptyEmailUser);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Current behavior - DOES authenticate with empty email (not ideal)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth); // Currently authenticates
        assertEquals("", auth.getPrincipal()); // With empty email

        // This is a minor security concern that should be fixed
        verify(userService, times(1)).ensureUserByEmail("");
    }

    // ==================== TOKEN REPLAY ATTACK TESTS ====================

    @Test
    @DisplayName("SECURITY: Should handle same token used multiple times (replay)")
    void testTokenReplayAttack() throws Exception {
        // Given: Same token used in multiple requests
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(userService.ensureUserByEmail("test@example.com")).thenReturn(testUser);

        // When: First request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);
        Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth1);

        // Clear context to simulate new request
        SecurityContextHolder.clearContext();

        // When: Second request with same token (replay)
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        request2.addHeader("Authorization", "Bearer " + token);

        googleIdTokenAuthFilter.doFilterInternal(request2, response2, filterChain);

        // Then: Token should still be validated by Google
        // Google's token verifier checks exp (expiration) claim
        // Short-lived tokens prevent long-term replay attacks
        verify(googleIdTokenVerifier, times(2)).verify(token);
    }

    // ==================== USER CREATION TESTS ====================

    @Test
    @DisplayName("Should create user if not exists")
    void testCreateUserIfNotExists() throws Exception {
        // Given: New user token
        String token = "new.user.token";
        request.addHeader("Authorization", "Bearer " + token);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("newuser@example.com");
        when(userService.ensureUserByEmail("newuser@example.com")).thenReturn(testUser);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: User should be created/ensured
        verify(userService, times(1)).ensureUserByEmail("newuser@example.com");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
    }

    // ==================== SECURITY CONTEXT ISOLATION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should isolate authentication between requests")
    void testAuthenticationIsolation() throws Exception {
        // Given: First request authenticates as user1
        String token1 = "user1.token";
        request.addHeader("Authorization", "Bearer " + token1);

        GoogleIdToken mockIdToken1 = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload1 = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token1)).thenReturn(mockIdToken1);
        when(mockIdToken1.getPayload()).thenReturn(mockPayload1);
        when(mockPayload1.getEmail()).thenReturn("user1@example.com");

        User user1 = new User();
        user1.setId(1L);
        user1.setEmail("user1@example.com");
        when(userService.ensureUserByEmail("user1@example.com")).thenReturn(user1);

        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("user1@example.com", auth1.getPrincipal());

        // Clear context (simulates new request)
        SecurityContextHolder.clearContext();

        // Given: Second request without auth header (anonymous)
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        googleIdTokenAuthFilter.doFilterInternal(request2, response2, filterChain);

        // Then: Should be anonymous (no auth from previous request)
        Authentication auth2 = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth2);
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("SECURITY: Should handle UserService exceptions gracefully")
    void testUserServiceException() throws Exception {
        // Given: Token valid but UserService throws exception
        String token = "valid.token";
        request.addHeader("Authorization", "Bearer " + token);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(userService.ensureUserByEmail("test@example.com"))
            .thenThrow(new RuntimeException("Database error"));

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should continue without authentication (fail gracefully)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("SECURITY: Should continue filter chain even on auth failure")
    void testFilterChainContinuesOnFailure() throws Exception {
        // Given: Invalid token
        String invalidToken = "invalid.token";
        request.addHeader("Authorization", "Bearer " + invalidToken);

        when(googleIdTokenVerifier.verify(invalidToken)).thenReturn(null);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Filter chain should continue (let controller handle 401)
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // ==================== TOKEN FORMAT TESTS ====================

    @Test
    @DisplayName("SECURITY: Should handle token with special characters")
    void testTokenWithSpecialCharacters() throws Exception {
        // JWT tokens can contain special characters in base64url encoding
        String token = "eyJhbGc.eyJzdWI_1234.SflKxw-RQ";
        request.addHeader("Authorization", "Bearer " + token);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(token)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(userService.ensureUserByEmail("test@example.com")).thenReturn(testUser);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should authenticate successfully
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
    }

    @Test
    @DisplayName("SECURITY: Should reject extremely long token (potential DoS)")
    void testExtremelyLongToken() throws Exception {
        // Given: Extremely long token (potential DoS attack)
        String longToken = "x".repeat(100000);
        request.addHeader("Authorization", "Bearer " + longToken);

        when(googleIdTokenVerifier.verify(longToken))
            .thenThrow(new IllegalArgumentException("Token too long"));

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should handle gracefully without crashing
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
    }

    // ==================== AUTHORIZATION HEADER VARIATIONS ====================

    @Test
    @DisplayName("SECURITY: Should handle case-sensitive Bearer keyword")
    void testCaseSensitiveBearer() throws Exception {
        // Given: Lowercase "bearer" instead of "Bearer"
        request.addHeader("Authorization", "bearer valid.token");

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should be ignored (case-sensitive check)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth);
        verify(googleIdTokenVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("SECURITY: Should extract token after 'Bearer ' prefix correctly")
    void testBearerPrefixExtraction() throws Exception {
        // Given: Token with proper Bearer prefix
        String actualToken = "actual.jwt.token";
        request.addHeader("Authorization", "Bearer " + actualToken);

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = mock(GoogleIdToken.Payload.class);

        when(googleIdTokenVerifier.verify(actualToken)).thenReturn(mockIdToken);
        when(mockIdToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn("test@example.com");
        when(userService.ensureUserByEmail("test@example.com")).thenReturn(testUser);

        // When: Filter processes request
        googleIdTokenAuthFilter.doFilterInternal(request, response, filterChain);

        // Then: Should verify with exact token (not including "Bearer ")
        verify(googleIdTokenVerifier, times(1)).verify(actualToken);
        verify(googleIdTokenVerifier, never()).verify("Bearer " + actualToken);
    }
}
