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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

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
      logger.error("Stripe webhook signature verification failed: {}", e.getMessage());
      return ResponseEntity.status(400).body("Invalid signature");
    }

    logger.info("Received Stripe webhook event: {}", event.getType());

    if ("checkout.session.completed".equals(event.getType())) {
      Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
      if (session != null) {
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;
        Map<String, String> metadata = session.getMetadata();

        logger.info("Processing checkout.session.completed for email: {}, metadata: {}", email, metadata);

        if (metadata != null && "monthly_1000".equals(metadata.get("subscription"))) {
          if (email != null) {
            User user = userService.ensureUserByEmail(email);
            userService.addCredits(user, 1000, "subscription_activated");
            logger.info("Added 1000 credits to user: {}", email);
          } else {
            logger.warn("No email found in checkout session");
          }
        } else {
          logger.warn("Metadata missing or subscription type not matched. Metadata: {}", metadata);
        }
      } else {
        logger.error("Failed to deserialize checkout session from webhook event");
      }
    }
    return ResponseEntity.ok("ok");
  }
}