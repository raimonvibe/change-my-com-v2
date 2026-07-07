package com.raimonvibe.imageconverter.user;

import com.raimonvibe.imageconverter.common.ClientIpResolver;
import com.raimonvibe.imageconverter.config.ConversionLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Quota policy for anonymous callers: no login required, the free daily limit
 * is enforced per client IP.
 */
@Component
public class AnonymousQuotaPolicy implements ConversionQuotaPolicy {

    private static final Logger logger = LoggerFactory.getLogger(AnonymousQuotaPolicy.class);

    private final AnonymousUserService anonymousUserService;

    public AnonymousQuotaPolicy(AnonymousUserService anonymousUserService) {
        this.anonymousUserService = anonymousUserService;
    }

    @Override
    public boolean supports(Principal principal) {
        return principal == null;
    }

    @Override
    public boolean consumeOneConversion(Principal principal, HttpServletRequest request) {
        final String clientIp = ClientIpResolver.resolve(request);
        boolean allowed = anonymousUserService.consumeOneConversion(clientIp, ConversionLimits.FREE_DAILY_LIMIT);
        if (!allowed) {
            logger.info("Conversion denied for IP {} - limit reached", clientIp);
        }
        return allowed;
    }
}
