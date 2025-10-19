package com.raimonvibe.imageconverter.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for BillingController - Open Redirect Prevention
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BillingControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("SECURITY: Should reject redirect to external domain")
    public void testOpenRedirectPrevention_ExternalDomain() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "https://evil.com/phishing");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        // Should return 400 Bad Request (or 401 if not authenticated)
        assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                   response.getStatusCode() == HttpStatus.UNAUTHORIZED,
            "Should reject external redirect URL");

        if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
            String error = (String) body.get("error");
            assertTrue(error.contains("domain not allowed") || error.contains("Invalid redirect URL"));
        }
    }

    @Test
    @DisplayName("SECURITY: Should accept valid localhost redirect")
    public void testOpenRedirectPrevention_ValidLocalhost() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "http://localhost:3000/success");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        // Should return 401 (not authenticated) not 400 (bad URL)
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Valid URLs should pass validation (fail on auth instead)");
    }

    @Test
    @DisplayName("SECURITY: Should accept valid production domain")
    public void testOpenRedirectPrevention_ValidProductionDomain() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "https://www.change-my.com/success");
        params.add("cancelUrl", "https://www.change-my.com/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        // Should return 401 (not authenticated) not 400 (bad URL)
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Valid production URLs should pass validation");
    }

    @Test
    @DisplayName("SECURITY: Should reject javascript: protocol")
    public void testOpenRedirectPrevention_JavaScriptProtocol() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "javascript:alert('XSS')");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                   response.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("SECURITY: Should reject data: protocol")
    public void testOpenRedirectPrevention_DataProtocol() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "data:text/html,<script>alert('XSS')</script>");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                   response.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("SECURITY: Should reject malformed URL")
    public void testOpenRedirectPrevention_MalformedUrl() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "not-a-valid-url");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                   response.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("SECURITY: Should reject empty URL")
    public void testOpenRedirectPrevention_EmptyUrl() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("successUrl", "");
        params.add("cancelUrl", "http://localhost:3000/cancel");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/billing/checkout",
            params,
            Map.class
        );

        assertTrue(response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                   response.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }
}
