package com.raimonvibe.imageconverter.config;

import com.raimonvibe.imageconverter.monitoring.CostMonitor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

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
}
