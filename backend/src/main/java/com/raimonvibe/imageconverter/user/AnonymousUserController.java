package com.raimonvibe.imageconverter.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousUserController {
    /** Must match ConvertController.FREE_DAILY_LIMIT_ANONYMOUS so remaining and enforcement stay in sync. */
    private static final int FREE_DAILY_LIMIT = 20;

    private final AnonymousUserService anonymousUserService;

    public AnonymousUserController(AnonymousUserService anonymousUserService) {
        this.anonymousUserService = anonymousUserService;
    }

    @GetMapping("/remaining")
    public Map<String, Object> getRemainingConversions(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        int remaining = anonymousUserService.getRemainingConversions(clientIp, FREE_DAILY_LIMIT);
        
        return Map.of(
            "remaining", remaining,
            "dailyLimit", FREE_DAILY_LIMIT,
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
