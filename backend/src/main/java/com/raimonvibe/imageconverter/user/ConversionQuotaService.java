package com.raimonvibe.imageconverter.user;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Selects and applies the {@link ConversionQuotaPolicy} matching the caller
 * (authenticated vs anonymous). Controllers only see one entry point.
 */
@Service
public class ConversionQuotaService {

    private final List<ConversionQuotaPolicy> policies;

    public ConversionQuotaService(List<ConversionQuotaPolicy> policies) {
        this.policies = policies;
    }

    /**
     * Consumes one conversion from the applicable quota.
     *
     * @return true when the conversion is allowed, false when the quota is exhausted
     */
    public boolean consumeOneConversion(Principal principal, HttpServletRequest request) {
        return policies.stream()
                .filter(p -> p.supports(principal))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No quota policy for caller"))
                .consumeOneConversion(principal, request);
    }
}
