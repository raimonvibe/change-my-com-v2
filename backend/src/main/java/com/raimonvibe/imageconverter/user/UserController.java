package com.raimonvibe.imageconverter.user;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal principal) {
    if (principal == null) return Map.of("authenticated", false);

    try {
      // Use ensureUserByEmail to create user if they don't exist
      var user = userService.ensureUserByEmail(principal.getName());
      boolean reset = !LocalDate.now().equals(user.getLastFreeReset());
      int freeRemaining = (reset ? 20 : Math.max(0, 20 - user.getFreeUsedToday()));
      return Map.of(
          "authenticated", true,
          "email", user.getEmail(),
          "freeRemaining", freeRemaining,
          "paidCredits", user.getPaidCredits(),
          "subscriptionStatus", user.getSubscriptionStatus() != null ? user.getSubscriptionStatus() : "none",
          "autoRenewal", user.getAutoRenewal() != null ? user.getAutoRenewal() : false
      );
    } catch (Exception e) {
      // Log the error and return unauthenticated
      System.err.println("Error in /api/user/me: " + e.getMessage());
      e.printStackTrace();
      return Map.of("authenticated", false, "error", "Failed to fetch user data");
    }
  }

  @PostMapping("/toggle-auto-renewal")
  public Map<String, Object> toggleAutoRenewal(Principal principal) {
    if (principal == null) return Map.of("success", false, "error", "Not authenticated");

    try {
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
      user.setAutoRenewal(newValue);
      userService.saveUser(user);

      System.out.println("Auto-renewal toggled for " + user.getEmail() + ": " + currentValue + " -> " + newValue);

      return Map.of(
          "success", true,
          "autoRenewal", newValue,
          "message", newValue ? "Auto-renewal enabled" : "Auto-renewal disabled"
      );
    } catch (Exception e) {
      System.err.println("Error toggling auto-renewal: " + e.getMessage());
      e.printStackTrace();
      return Map.of("success", false, "error", "Failed to toggle auto-renewal");
    }
  }
}
