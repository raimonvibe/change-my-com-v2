package com.raimonvibe.imageconverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HealthController
 * Tests health check endpoint availability
 *
 * NOTE: Minimal test count to avoid rate limit exhaustion in test suite
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("HealthController Integration Tests")
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== ESSENTIAL HEALTH CHECK TESTS ====================

    @Test
    @DisplayName("Should return 200 OK with 'ok' response")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"))
            .andExpect(content().contentType("text/plain;charset=UTF-8"));
    }

    @Test
    @DisplayName("Should reject POST requests (405 Method Not Allowed)")
    void testHealthCheck_PostNotAllowed() throws Exception {
        mockMvc.perform(post("/health"))
            .andExpect(status().isMethodNotAllowed());
    }
}
