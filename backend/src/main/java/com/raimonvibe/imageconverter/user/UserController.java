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
          "paidCredits", user.getPaidCredits()
      );
    } catch (Exception e) {
      // Log the error and return unauthenticated
      System.err.println("Error in /api/user/me: " + e.getMessage());
      e.printStackTrace();
      return Map.of("authenticated", false, "error", "Failed to fetch user data");
    }
  }
}
