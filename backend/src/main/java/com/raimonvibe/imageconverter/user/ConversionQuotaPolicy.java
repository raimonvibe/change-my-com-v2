package com.raimonvibe.imageconverter.user;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;

/**
 * Strategy for enforcing the per-day conversion quota. There is one policy per
 * caller kind (authenticated user vs anonymous IP); the applicable policy is
 * selected by {@link ConversionQuotaService}. This replaces the credit-check
 * block that was copy-pasted between ConvertController.convert() and convertGif().
 */
public interface ConversionQuotaPolicy {

    /** Whether this policy applies to the current caller. */
    boolean supports(Principal principal);

    /**
     * Atomically consumes one conversion from the caller's quota.
     *
     * @return true when the conversion is allowed, false when the quota is exhausted
     */
    boolean consumeOneConversion(Principal principal, HttpServletRequest request);
}
