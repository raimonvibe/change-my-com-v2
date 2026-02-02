package com.raimonvibe.imageconverter.config;

import com.raimonvibe.imageconverter.monitoring.CostMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler
 * Tests exception handling and error responses
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
public class GlobalExceptionHandlerTest {

    @Mock
    private CostMonitor costMonitor;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        // Mock is reset before each test by @ExtendWith(MockitoExtension.class)
    }

    // ==================== MAX UPLOAD SIZE EXCEEDED TESTS ====================

    @Test
    @DisplayName("Should handle MaxUploadSizeExceededException with 413 status")
    void testHandleMaxUploadSizeExceeded_Status() {
        // Given: Upload size exceeded exception
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        // When: Handle exception
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Should return 413 Payload Too Large
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return error message in response body")
    void testHandleMaxUploadSizeExceeded_ErrorMessage() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        // When
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Should contain error message
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));

        String errorMessage = response.getBody().get("error");
        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("8MB"), "Error message should mention 8MB limit");
        assertTrue(errorMessage.toLowerCase().contains("groot"), "Error message should be in Dutch");
    }

    @Test
    @DisplayName("Should record failed upload in cost monitor")
    void testHandleMaxUploadSizeExceeded_RecordsCost() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        // When
        exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Should record failed upload
        verify(costMonitor, times(1)).recordFailedUpload(
            eq("File too large"),
            eq(8L * 1024 * 1024)
        );
    }

    @Test
    @DisplayName("Should handle null exception gracefully")
    void testHandleMaxUploadSizeExceeded_NullException() {
        // When: Handle null exception (edge case)
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleMaxUploadSizeExceeded(null);

        // Then: Should still return proper error response
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    @DisplayName("Should use correct max file size (8MB) in cost tracking")
    void testHandleMaxUploadSizeExceeded_CorrectMaxSize() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(15 * 1024 * 1024);

        // When
        exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Should record with 8MB limit (not the actual exceeded size)
        long expectedSize = 8L * 1024 * 1024; // 8MB in bytes
        verify(costMonitor).recordFailedUpload(anyString(), eq(expectedSize));
    }

    @Test
    @DisplayName("Should return JSON-compatible response body")
    void testHandleMaxUploadSizeExceeded_ResponseFormat() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        // When
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Response body should be a Map (JSON-compatible)
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        assertEquals(1, response.getBody().size(), "Should have exactly one key-value pair");
    }

    @Test
    @DisplayName("Should provide user-friendly error message")
    void testHandleMaxUploadSizeExceeded_UserFriendlyMessage() {
        // Given
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20 * 1024 * 1024);

        // When
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: Message should be user-friendly (in Dutch)
        String message = response.getBody().get("error");
        assertFalse(message.contains("Exception"), "Should not expose technical details");
        assertFalse(message.contains("Stack"), "Should not expose technical details");
        assertTrue(message.contains("bestand") || message.contains("Bestand"),
            "Should mention 'bestand' (file in Dutch)");
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    @DisplayName("Should handle multiple consecutive exceptions")
    void testHandleMultipleExceptions() {
        // Given: Multiple exceptions
        MaxUploadSizeExceededException ex1 = new MaxUploadSizeExceededException(10 * 1024 * 1024);
        MaxUploadSizeExceededException ex2 = new MaxUploadSizeExceededException(12 * 1024 * 1024);

        // When: Handle multiple exceptions
        ResponseEntity<Map<String, String>> response1 = exceptionHandler.handleMaxUploadSizeExceeded(ex1);
        ResponseEntity<Map<String, String>> response2 = exceptionHandler.handleMaxUploadSizeExceeded(ex2);

        // Then: Both should be handled correctly
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response1.getStatusCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response2.getStatusCode());

        // Cost monitor should be called twice
        verify(costMonitor, times(2)).recordFailedUpload(anyString(), anyLong());
    }

    @Test
    @DisplayName("Should maintain consistent behavior across multiple invocations")
    void testConsistentBehavior() {
        // Given: Same exception multiple times
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        // When: Handle same exception 3 times
        ResponseEntity<Map<String, String>> response1 = exceptionHandler.handleMaxUploadSizeExceeded(ex);
        ResponseEntity<Map<String, String>> response2 = exceptionHandler.handleMaxUploadSizeExceeded(ex);
        ResponseEntity<Map<String, String>> response3 = exceptionHandler.handleMaxUploadSizeExceeded(ex);

        // Then: All responses should be identical
        assertEquals(response1.getStatusCode(), response2.getStatusCode());
        assertEquals(response2.getStatusCode(), response3.getStatusCode());
        assertEquals(response1.getBody().get("error"), response2.getBody().get("error"));
        assertEquals(response2.getBody().get("error"), response3.getBody().get("error"));
    }
}
