package com.raimonvibe.imageconverter.billing;

import com.google.gson.Gson;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private final UserService userService;

  public StripeWebhookController(UserService userService) {
    this.userService = userService;
  }

  @Value("${app.stripe.webhookSecret:}")
  private String webhookSecret;

  @PostMapping("/webhook")
  public ResponseEntity<String> webhook(HttpServletRequest request, @RequestBody byte[] payloadBytes, @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {
    String payload = new String(payloadBytes, StandardCharsets.UTF_8);
    Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      return ResponseEntity.status(400).body("Invalid signature");
    }
    if ("checkout.session.completed".equals(event.getType())) {
      Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
      if (session != null) {
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;
        Map<String, String> metadata = session.getMetadata();
        
        if (metadata != null && "unlimited_conversions".equals(metadata.get("subscription"))) {
          if (email != null) {
            User user = userService.ensureUserByEmail(email);
            // Add a large number of credits for unlimited conversions (999999)
            userService.addCredits(user, 999999, "subscription_activated");
          }
        }
      }
    }
    return ResponseEntity.ok("ok");
  }
}