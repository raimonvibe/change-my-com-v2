package com.raimonvibe.imageconverter.user;

import com.raimonvibe.imageconverter.config.ConversionLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Quota policy for logged-in users: free daily conversions first, then paid
 * credits. Paid credits only exist on this path; the anonymous policy never
 * touches User.paidCredits.
 */
@Component
public class AuthenticatedQuotaPolicy implements ConversionQuotaPolicy {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticatedQuotaPolicy.class);

    private final UserService userService;

    public AuthenticatedQuotaPolicy(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supports(Principal principal) {
        return principal != null;
    }

    @Override
    public boolean consumeOneConversion(Principal principal, HttpServletRequest request) {
        final String email = principal.getName();
        final User user = userService.ensureUserByEmail(email);
        boolean allowed = userService.consumeOneConversion(user, ConversionLimits.FREE_DAILY_LIMIT);
        if (!allowed) {
            logger.info("Conversion denied for user {} - insufficient credits", email);
        }
        return allowed;
    }
}
