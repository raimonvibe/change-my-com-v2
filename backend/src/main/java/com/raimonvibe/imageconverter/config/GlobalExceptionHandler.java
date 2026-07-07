package com.raimonvibe.imageconverter.config;

import com.raimonvibe.imageconverter.monitoring.CostMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Central exception-to-response mapping for exceptions that escape controller
 * try/catch blocks. Responses use a consistent JSON error body and never leak
 * exception details (messages are logged server-side only).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final CostMonitor costMonitor;

    public GlobalExceptionHandler(CostMonitor costMonitor) {
        this.costMonitor = costMonitor;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        String message = "File is too large. Maximum allowed size is 20MB. Please choose a smaller file.";
        
        // Record failed upload for cost tracking
        costMonitor.recordFailedUpload("File too large", 20L * 1024 * 1024); // Assume max size
        
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", message));
    }

    /** Fallback for validation failures not handled locally by a controller. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Unhandled IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request."));
    }
}
