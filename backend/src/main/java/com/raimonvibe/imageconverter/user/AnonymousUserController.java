package com.raimonvibe.imageconverter.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousUserController {
    private final AnonymousUserService anonymousUserService;

    @Value("${app.anonymous.dailyLimit:20}")
    private int dailyLimit;

    public AnonymousUserController(AnonymousUserService anonymousUserService) {
        this.anonymousUserService = anonymousUserService;
    }

    @GetMapping("/remaining")
    public Map<String, Object> getRemainingConversions(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        int remaining = anonymousUserService.getRemainingConversions(clientIp, dailyLimit);
        
        return Map.of(
            "remaining", remaining,
            "dailyLimit", dailyLimit,
            "authenticated", false
        );
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
