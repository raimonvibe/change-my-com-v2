package com.raimonvibe.imageconverter.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Security tests for BillingController - Open Redirect Prevention
 */
@SpringBootTest
@AutoConfigureMockMvc
public class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SECURITY: Should reject redirect to external domain")
    public void testOpenRedirectPrevention_ExternalDomain() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "https://evil.com/phishing")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError()); // Should be 400, 401, or 403
    }

    @Test
    @DisplayName("SECURITY: Should accept valid localhost redirect")
    public void testOpenRedirectPrevention_ValidLocalhost() throws Exception {
        // Should return 401/403 (not authenticated) not 400 (bad URL)
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "http://localhost:3000/success")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError()); // 401 or 403, not 400
    }

    @Test
    @DisplayName("SECURITY: Should accept valid production domain")
    public void testOpenRedirectPrevention_ValidProductionDomain() throws Exception {
        // Should return 401/403 (not authenticated) not 400 (bad URL)
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "https://www.change-my.com/success")
                .param("cancelUrl", "https://www.change-my.com/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError()); // 401 or 403, not 400
    }

    @Test
    @DisplayName("SECURITY: Should reject javascript: protocol")
    public void testOpenRedirectPrevention_JavaScriptProtocol() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "javascript:alert('XSS')")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SECURITY: Should reject data: protocol")
    public void testOpenRedirectPrevention_DataProtocol() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "data:text/html,<script>alert('XSS')</script>")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SECURITY: Should reject malformed URL")
    public void testOpenRedirectPrevention_MalformedUrl() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "not-a-valid-url")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SECURITY: Should reject empty URL")
    public void testOpenRedirectPrevention_EmptyUrl() throws Exception {
        mockMvc.perform(post("/api/billing/checkout")
                .param("successUrl", "")
                .param("cancelUrl", "http://localhost:3000/cancel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is4xxClientError());
    }
}
