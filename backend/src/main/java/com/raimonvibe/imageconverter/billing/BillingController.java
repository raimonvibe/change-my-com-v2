package com.raimonvibe.imageconverter.billing;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private static final Logger logger = LoggerFactory.getLogger(BillingController.class);

    // Whitelist of allowed redirect domains for security
    private static final Set<String> ALLOWED_REDIRECT_HOSTS = Set.of(
        "localhost:3000",           // Development
        "localhost:5173",           // Development (Vite)
        "www.change-my.com",        // Production
        "change-my.com"             // Production (no www)
    );

    @Value("${app.stripe.secretKey:}")
    private String stripeSecretKey;

    @Value("${app.stripe.priceUsd:1.98}")
    private Double priceUsd;

    public BillingController() {
        // Empty constructor to avoid instantiation issues
    }

    /**
     * Validates redirect URLs to prevent open redirect vulnerability.
     * Only allows URLs from whitelisted domains.
     */
    private void validateRedirectUrl(String url, String paramName) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException(paramName + " is required");
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            // Construct host:port string for comparison
            String hostWithPort = host;
            if (port != -1 && port != 80 && port != 443) {
                hostWithPort = host + ":" + port;
            }

            // Validate scheme (must be http or https)
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                logger.warn("Invalid redirect URL scheme: {} for {}", scheme, paramName);
                throw new IllegalArgumentException("Invalid redirect URL: only HTTP(S) allowed");
            }

            // Validate host against whitelist
            if (!ALLOWED_REDIRECT_HOSTS.contains(hostWithPort) && !ALLOWED_REDIRECT_HOSTS.contains(host)) {
                logger.warn("Blocked redirect to unauthorized domain: {} for {}", hostWithPort, paramName);
                throw new IllegalArgumentException("Invalid redirect URL: domain not allowed");
            }

            logger.debug("Validated redirect URL for {}: {}", paramName, url);

        } catch (URISyntaxException e) {
            logger.warn("Malformed redirect URL for {}: {}", paramName, url);
            throw new IllegalArgumentException("Invalid redirect URL: malformed");
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(
            @RequestParam("successUrl") String successUrl,
            @RequestParam("cancelUrl") String cancelUrl,
            java.security.Principal principal) throws Exception {

        // Ensure user is authenticated (security config requires it, but double-check)
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required to subscribe"));
        }

        // SECURITY: Validate redirect URLs to prevent open redirect attacks
        try {
            validateRedirectUrl(successUrl, "successUrl");
            validateRedirectUrl(cancelUrl, "cancelUrl");
        } catch (IllegalArgumentException e) {
            logger.error("Redirect URL validation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }

        Stripe.apiKey = stripeSecretKey;

        long priceCents = Math.round(priceUsd * 100); // Convert $1.98 to cents (198 cents)

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("subscription", "monthly_1000")
                .setCustomerEmail(principal.getName()) // Always set email from authenticated user
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(priceCents)
                                                .setRecurring(
                                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                                                .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                                                .build())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("1000 Conversions per Month")
                                                                .build())
                                                .build())
                                .build())
                .build();

        Session session = Session.create(params);
        return ResponseEntity.ok(Map.of("id", session.getId(), "url", session.getUrl()));
    }
}