package com.raimonvibe.imageconverter.user;

import com.raimonvibe.imageconverter.common.ClientIpResolver;
import com.raimonvibe.imageconverter.config.ConversionLimits;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousUserController {
    private static final int FREE_DAILY_LIMIT = ConversionLimits.FREE_DAILY_LIMIT;

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
        return ClientIpResolver.resolve(request);
    }
}
