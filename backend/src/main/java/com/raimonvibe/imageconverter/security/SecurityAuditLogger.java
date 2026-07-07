package com.raimonvibe.imageconverter.security;

import com.raimonvibe.imageconverter.common.ClientIpResolver;
import com.raimonvibe.imageconverter.common.EmailMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SecurityAuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public void logSecurityEvent(String event, HttpServletRequest request, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String ip = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String method = request.getMethod();
        String uri = request.getRequestURI();
        
        logger.warn("SECURITY_EVENT [{}] {} {} {} {} {} - {}", 
            timestamp, event, ip, method, uri, userAgent, details);
    }
    
    public void logRateLimitExceeded(String identifier, String endpoint) {
        logger.warn("RATE_LIMIT_EXCEEDED [{}] {} - {}",
            LocalDateTime.now().format(TIMESTAMP_FORMAT), maskIdentifier(identifier), endpoint);
    }
    
    public void logFileUploadAttempt(String filename, String contentType, long size, String result) {
        logger.info("FILE_UPLOAD [{}] {} {} {} bytes - {}", 
            LocalDateTime.now().format(TIMESTAMP_FORMAT), filename, contentType, size, result);
    }
    
    public void logAuthenticationAttempt(String email, boolean success, String reason) {
        String status = success ? "SUCCESS" : "FAILED";
        logger.info("AUTH_ATTEMPT [{}] {} {} - {}",
            LocalDateTime.now().format(TIMESTAMP_FORMAT), maskEmail(email), status, reason);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    /**
     * Masks email addresses in identifiers for privacy in logs
     * Handles both "u:email@domain.com" and "ip:xxx.xxx.xxx.xxx" formats
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "unknown";
        }

        // If it's a user identifier (u:email), mask the email part
        if (identifier.startsWith("u:")) {
            String email = identifier.substring(2);
            return "u:" + maskEmail(email);
        }

        // IP addresses and other identifiers can be logged as-is
        return identifier;
    }

    private String maskEmail(String email) {
        return EmailMasker.mask(email);
    }
}
