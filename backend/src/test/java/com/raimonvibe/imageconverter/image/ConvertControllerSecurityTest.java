package com.raimonvibe.imageconverter.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security tests for ConvertController.
 * Ensures error responses never leak paths, stack traces, or internal details.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ConvertController Security Tests")
public class ConvertControllerSecurityTest {

    /** Whitelist of safe validation messages (must match ConvertController.SAFE_VALIDATION_MESSAGES). */
    private static final Set<String> SAFE_VALIDATION_MESSAGES = Set.of(
        "File is empty.",
        "File too large.",
        "Unsupported extension.",
        "Invalid file.",
        "File contains suspicious content.",
        "Invalid or unsupported image signature.",
        "Unsupported MIME type.",
        "Invalid file or format. Please check file type and size."
    );

    /** Substrings that must never appear in client-facing error messages (information disclosure). */
    private static final Set<String> FORBIDDEN_IN_ERROR = Set.of(
        "Exception", "at com.", "at java.", "at org.",
        "Caused by:", "StackTrace", "java.lang", "IOException",
        "/etc/", "C:\\", "\\var\\", "path:", "file:"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SECURITY: X-Error-Message on validation failure must be whitelisted")
    void testValidationErrorHeaderIsWhitelisted() throws Exception {
        // Unsupported extension triggers validation error
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.xyz",
            "application/octet-stream",
            new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 }
        );

        MvcResult result = mockMvc.perform(multipart("/api/convert")
                .file(file)
                .param("to", "png")
                .param("quality", "85"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        String errorHeader = result.getResponse().getHeader("X-Error-Message");
        if (errorHeader != null) {
            assertTrue(SAFE_VALIDATION_MESSAGES.contains(errorHeader),
                "X-Error-Message must be whitelisted, got: " + errorHeader);
        }
    }

    @Test
    @DisplayName("SECURITY: X-Error-Message must never contain path or stack trace fragments")
    void testErrorHeaderNoPathOrStack() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.exe",
            "image/jpeg",
            new byte[] { 'M', 'Z', (byte) 0x90, (byte) 0x00 }
        );

        MvcResult result = mockMvc.perform(multipart("/api/convert")
                .file(file)
                .param("to", "png")
                .param("quality", "85"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        String errorHeader = result.getResponse().getHeader("X-Error-Message");
        if (errorHeader != null) {
            for (String forbidden : FORBIDDEN_IN_ERROR) {
                assertFalse(errorHeader.contains(forbidden),
                    "X-Error-Message must not contain '" + forbidden + "': " + errorHeader);
            }
        }
    }

    @Test
    @DisplayName("SECURITY: 413 response body must not leak internal paths or exception details")
    void test413ResponseNoLeak() throws Exception {
        // Exceed 20MB limit so request is rejected with 413 or 400 (not 422 from conversion)
        byte[] large = new byte[21 * 1024 * 1024];
        large[0] = (byte) 0xFF;
        large[1] = (byte) 0xD8;
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large.jpg",
            "image/jpeg",
            large
        );

        MvcResult result = mockMvc.perform(multipart("/api/convert")
                .file(file)
                .param("to", "png")
                .param("quality", "85"))
            .andReturn();

        int status = result.getResponse().getStatus();
        // 413 (Spring multipart), 400 (FileValidator), or 429 (rate limit in test suite)
        assertTrue(status == 413 || status == 400 || status == 429,
            "Expected 413, 400, or 429 for oversized upload, got: " + status);

        String body = result.getResponse().getContentAsString();
        if (status == 413 || status == 400) {
            for (String forbidden : FORBIDDEN_IN_ERROR) {
                assertFalse(body.contains(forbidden),
                    "Response body must not contain '" + forbidden + "'");
            }
        }
    }

    @Test
    @DisplayName("SECURITY: Invalid format 400 X-Error-Message must not leak internal details")
    void testInvalidFormatReturnsSafeMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "x.jpg",
            "image/jpeg",
            new byte[] { (byte) 0xFF, (byte) 0xD8, 0x00, 0x00 }
        );

        MvcResult result = mockMvc.perform(multipart("/api/convert")
                .file(file)
                .param("to", "invalid")
                .param("quality", "85"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        String errorHeader = result.getResponse().getHeader("X-Error-Message");
        if (errorHeader != null) {
            for (String forbidden : FORBIDDEN_IN_ERROR) {
                assertFalse(errorHeader.contains(forbidden), "No leak in X-Error-Message: " + errorHeader);
            }
        }
    }
}
