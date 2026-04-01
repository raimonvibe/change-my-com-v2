package com.raimonvibe.imageconverter.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AnonymousUserController.
 * SECURITY: Ensures GET /api/anonymous/remaining is public and returns only safe data
 * (remaining count, dailyLimit 20, authenticated: false). No user data or IP is exposed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AnonymousUserController Integration Tests")
public class AnonymousUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 200 and safe JSON without authentication")
    void testGetRemaining_NoAuthRequired() throws Exception {
        mockMvc.perform(get("/api/anonymous/remaining"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("SECURITY: Response must contain only remaining, dailyLimit, authenticated")
    void testGetRemaining_SafeResponseShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/anonymous/remaining"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remaining").isNumber())
            .andExpect(jsonPath("$.dailyLimit").value(20))
            .andExpect(jsonPath("$.authenticated").value(false))
            .andReturn();

        String content = result.getResponse().getContentAsString();
        // Must not expose IP, email, or any PII
        assertFalse(content.contains("ip") || content.contains("email") || content.contains("address"),
            "Response must not expose IP or email");
    }

    @Test
    @DisplayName("Remaining should be a number; dailyLimit should be 20")
    void testGetRemaining_Structure() throws Exception {
        mockMvc.perform(get("/api/anonymous/remaining"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remaining").isNumber())
            .andExpect(jsonPath("$.dailyLimit").value(20))
            .andExpect(jsonPath("$.authenticated").value(false));
    }
}
