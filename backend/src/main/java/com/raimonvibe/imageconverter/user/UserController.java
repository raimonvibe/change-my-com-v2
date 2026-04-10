package com.raimonvibe.imageconverter.user;

import com.stripe.Stripe;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionUpdateParams;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

  private static final Logger logger = LoggerFactory.getLogger(UserController.class);
  private final UserService userService;

  @Value("${app.stripe.secretKey:}")
  private String stripeSecretKey;

  /** Free conversions per day for logged-in users. Must match ConvertController.FREE_DAILY_LIMIT. */
  private static final int FREE_DAILY_LIMIT = 20;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal principal) {
    logger.info("User /me endpoint called - principal present: {}", principal != null);

    if (principal == null) {
      logger.info("No principal found - returning unauthenticated");
      return Map.of("authenticated", false);
    }

    try {
      // Use ensureUserByEmail to create user if they don't exist
      var user = userService.ensureUserByEmail(principal.getName());
      logger.info("Processing /me request for user ID: {}", user.getId());
      boolean reset = !LocalDate.now().equals(user.getLastFreeReset());
      int freeRemaining = (reset ? FREE_DAILY_LIMIT : Math.max(0, FREE_DAILY_LIMIT - user.getFreeUsedToday()));
      return Map.of(
          "authenticated", true,
          "email", user.getEmail(),
          "freeRemaining", freeRemaining,
          "dailyLimit", FREE_DAILY_LIMIT,
          "paidCredits", user.getPaidCredits(),
          "subscriptionStatus", user.getSubscriptionStatus() != null ? user.getSubscriptionStatus() : "none",
          "autoRenewal", user.getAutoRenewal() != null ? user.getAutoRenewal() : false
      );
    } catch (Exception e) {
      logger.error("Error in /api/user/me: {}", e.getMessage(), e);
      return Map.of("authenticated", false, "error", "Failed to fetch user data");
    }
  }

  @PostMapping("/toggle-auto-renewal")
  public Map<String, Object> toggleAutoRenewal(Principal principal) {
    if (principal == null) return Map.of("success", false, "error", "Not authenticated");

    try {
      Stripe.apiKey = stripeSecretKey;
      var user = userService.ensureUserByEmail(principal.getName());

      // Only allow toggling if user has an active subscription
      if (user.getStripeSubscriptionId() == null) {
        return Map.of("success", false, "error", "No active subscription");
      }

      // Handle null autoRenewal (from old data/schema migrations)
      Boolean currentValue = user.getAutoRenewal();
      if (currentValue == null) {
        currentValue = true; // Default to true for active subscriptions
      }

      boolean newValue = !currentValue;

      // CRITICAL: Actually cancel/resume the Stripe subscription
      if (!newValue) {
        // Toggling OFF - set cancel_at_period_end in Stripe (do NOT use subscription.cancel() which cancels immediately and can break re-toggle)
        logger.info("Setting Stripe subscription {} to cancel at period end for user ID: {}", user.getStripeSubscriptionId(), user.getId());
        Subscription subscription = Subscription.retrieve(user.getStripeSubscriptionId());
        String status = subscription.getStatus();
        boolean activeOrTrialing = "active".equals(status) || "trialing".equals(status);
        if (activeOrTrialing && !Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
          SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
              .setCancelAtPeriodEnd(true)
              .build();
          subscription.update(params);
          logger.info("Successfully set subscription {} to cancel at period end for user ID: {}", user.getStripeSubscriptionId(), user.getId());
        } else if (!activeOrTrialing) {
          logger.info("Subscription {} no longer active (status: {}), updating DB only for user ID: {}", user.getStripeSubscriptionId(), status, user.getId());
        } else {
          logger.info("Subscription {} already set to cancel at period end for user ID: {}", user.getStripeSubscriptionId(), user.getId());
        }
      } else {
        // Toggling ON - this should not be allowed (user must subscribe via checkout)
        logger.warn("User ID: {} attempted to re-enable auto-renewal, but this is not supported", user.getId());
        return Map.of("success", false, "error", "Please subscribe again via the pricing page to re-enable auto-renewal");
      }

      user.setAutoRenewal(newValue);
      userService.saveUser(user);

      logger.info("Auto-renewal toggled for user ID: {}: {} -> {}", user.getId(), currentValue, newValue);

      return Map.of(
          "success", true,
          "autoRenewal", newValue,
          "message", newValue ? "Auto-renewal enabled" : "Auto-renewal disabled. Your subscription will not renew next month."
      );
    } catch (Exception e) {
      logger.error("Error toggling auto-renewal: {}", e.getMessage(), e);
      return Map.of("success", false, "error", "Failed to toggle auto-renewal: " + e.getMessage());
    }
  }
}
