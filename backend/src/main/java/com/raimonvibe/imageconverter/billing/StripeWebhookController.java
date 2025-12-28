package com.raimonvibe.imageconverter.billing;

import com.google.gson.Gson;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserService;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

  private final UserService userService;
  private final UserRepository userRepository;
  private final WebhookEventRepository webhookEventRepository;

  public StripeWebhookController(UserService userService, UserRepository userRepository, WebhookEventRepository webhookEventRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
    this.webhookEventRepository = webhookEventRepository;
  }

  @Value("${app.stripe.webhookSecret:}")
  private String webhookSecret;

  // Security: Maximum payload size for Stripe webhooks (512KB - Stripe's typical max is ~100KB)
  private static final int MAX_PAYLOAD_SIZE = 512 * 1024; // 512KB

  // Log webhook secret status on startup (without exposing the actual secret)
  @jakarta.annotation.PostConstruct
  private void logWebhookSecretStatus() {
    if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
      logger.warn("⚠️  STRIPE_WEBHOOK_SECRET environment variable is not set or empty");
      logger.warn("⚠️  Webhook signature verification will fail until this is configured");
    } else {
      // Log that it's configured without exposing the actual value
      logger.info("✓ Stripe webhook secret is configured (length: {} chars)", webhookSecret.length());
      // Verify it starts with expected prefix
      if (webhookSecret.startsWith("whsec_")) {
        logger.info("✓ Webhook secret format appears correct (starts with whsec_)");
      } else {
        logger.warn("⚠️  Webhook secret does not start with 'whsec_' - may be incorrect format");
      }
    }
  }

  @PostMapping("/webhook")
  @Transactional
  public ResponseEntity<String> webhook(HttpServletRequest request, @RequestBody byte[] payloadBytes, @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) throws IOException {
    // Security: Require signature header
    if (sigHeader == null || sigHeader.trim().isEmpty()) {
      logger.error("SECURITY: Webhook request missing Stripe-Signature header - rejecting");
      return ResponseEntity.status(400).body("Missing signature header");
    }
    
    // Security check: Ensure webhook secret is configured
    if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
      logger.error("CRITICAL: Stripe webhook secret is not configured!");
      logger.error("CRITICAL: Please check:");
      logger.error("  1. STRIPE_WEBHOOK_SECRET environment variable is set on Render");
      logger.error("  2. Service has been restarted after setting the variable");
      logger.error("  3. Variable name is exactly 'STRIPE_WEBHOOK_SECRET' (case-sensitive)");
      logger.error("  4. Spring profile is set to 'prod' (SPRING_PROFILES_ACTIVE=prod)");
      return ResponseEntity.status(500).body("Webhook secret not configured");
    }

    // Security: Validate payload size to prevent DoS attacks
    if (payloadBytes == null || payloadBytes.length == 0) {
      logger.error("SECURITY: Empty webhook payload - rejecting");
      return ResponseEntity.status(400).body("Empty payload");
    }
    
    if (payloadBytes.length > MAX_PAYLOAD_SIZE) {
      logger.error("SECURITY: Webhook payload too large: {} bytes (max: {} bytes) - rejecting", payloadBytes.length, MAX_PAYLOAD_SIZE);
      return ResponseEntity.status(413).body("Payload too large");
    }

    String payload = new String(payloadBytes, StandardCharsets.UTF_8);
    logger.info("Webhook payload length: {} bytes", payloadBytes.length);

    Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      logger.error("Stripe webhook signature verification failed: {}", e.getMessage());
      logger.error("This usually means: 1) Webhook secret mismatch, 2) Request not from Stripe, or 3) Payload was modified");
      return ResponseEntity.status(400).body("Invalid signature");
    }

    // Security: Validate event ID and type
    if (event.getId() == null || event.getId().trim().isEmpty()) {
      logger.error("SECURITY: Webhook event missing ID - rejecting");
      return ResponseEntity.status(400).body("Invalid event: missing ID");
    }
    
    if (event.getType() == null || event.getType().trim().isEmpty()) {
      logger.error("SECURITY: Webhook event missing type - rejecting");
      return ResponseEntity.status(400).body("Invalid event: missing type");
    }

    logger.info("=== Received Stripe webhook event: {} (id: {}) ===", event.getType(), event.getId());

    // ATOMIC idempotency check: Try to save the event FIRST
    // The database UNIQUE constraint on stripeEventId prevents race conditions
    try {
      webhookEventRepository.save(new WebhookEvent(event.getId(), event.getType()));
      logger.info("Recorded webhook event {} for processing", event.getId());
    } catch (DataIntegrityViolationException e) {
      // Event already exists in database - this is a duplicate webhook
      logger.warn("Webhook event {} already processed (caught by DB constraint), skipping", event.getId());
      return ResponseEntity.ok("already_processed");
    } catch (Exception e) {
      logger.error("Failed to record webhook event {}: {}", event.getId(), e.getMessage());
      // Return 500 so Stripe will retry later
      return ResponseEntity.status(500).body("Failed to record event");
    }

    // Now process the event - this will only happen once per event ID
    // Security: Only process known event types
    try {
      switch (event.getType()) {
        case "checkout.session.completed":
          handleCheckoutCompleted(event);
          break;
        case "invoice.paid":
          handleInvoicePaid(event);
          break;
        case "customer.subscription.updated":
          handleSubscriptionUpdated(event);
          break;
        case "customer.subscription.deleted":
          handleSubscriptionDeleted(event);
          break;
        default:
          logger.info("Unhandled event type: {} (id: {}) - ignoring", event.getType(), event.getId());
          // Return 200 to acknowledge receipt but don't process unknown events
      }
    } catch (Exception e) {
      logger.error("Error processing webhook event {}: {}", event.getId(), e.getMessage(), e);
      // Event is already recorded as processed, but processing failed
      // We return 200 to prevent Stripe from retrying (which would be ignored anyway)
      return ResponseEntity.ok("processing_failed");
    }

    return ResponseEntity.ok("ok");
  }

  private void handleCheckoutCompleted(Event event) {
    try {
      logger.info("Processing checkout.session.completed event");

      // Parse the JSON from the event
      Gson gson = new Gson();
      String jsonString = event.getData().getObject().toJson();
      com.google.gson.JsonObject dataObject = gson.fromJson(jsonString, com.google.gson.JsonObject.class);

      String email = null;
      if (dataObject.has("customer_details") && !dataObject.get("customer_details").isJsonNull()) {
        com.google.gson.JsonObject customerDetails = dataObject.getAsJsonObject("customer_details");
        if (customerDetails.has("email") && !customerDetails.get("email").isJsonNull()) {
          email = customerDetails.get("email").getAsString();
        }
      }

      String subscriptionId = null;
      if (dataObject.has("subscription") && !dataObject.get("subscription").isJsonNull()) {
        subscriptionId = dataObject.get("subscription").getAsString();
      }

      Map<String, String> metadata = new java.util.HashMap<>();
      if (dataObject.has("metadata") && !dataObject.get("metadata").isJsonNull()) {
        com.google.gson.JsonObject metadataObj = dataObject.getAsJsonObject("metadata");
        for (String key : metadataObj.keySet()) {
          metadata.put(key, metadataObj.get(key).getAsString());
        }
      }

      logger.info("Session details - subscriptionId: {}, metadata keys: {}", subscriptionId, metadata.keySet());

      // Security: Validate inputs before processing
      if (email == null || email.trim().isEmpty()) {
        logger.error("SECURITY: Missing or empty email in checkout.session.completed event");
        return;
      }
      
      // Security: Validate email format (basic check)
      if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
        logger.error("SECURITY: Invalid email format in checkout.session.completed event");
        return;
      }
      
      if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
        logger.error("SECURITY: Missing or empty subscriptionId in checkout.session.completed event");
        return;
      }
      
      // Security: Validate subscriptionId format (Stripe IDs start with 'sub_')
      if (!subscriptionId.startsWith("sub_")) {
        logger.error("SECURITY: Invalid subscriptionId format in checkout.session.completed event: {}", subscriptionId);
        return;
      }

      if (metadata.containsKey("subscription") && "monthly_1000".equals(metadata.get("subscription"))) {
        User user = userService.ensureUserByEmail(email);
        user.setStripeSubscriptionId(subscriptionId);
        user.setSubscriptionStatus("active");
        user.setAutoRenewal(true);
        userService.activateSubscription(user, 1000);
        logger.info("✓ Activated subscription for user ID: {}, subscriptionId: {}, credits: 1000", user.getId(), subscriptionId);
      } else {
        logger.warn("Metadata check failed - metadata: {}", metadata);
      }
    } catch (Exception e) {
      logger.error("Error handling checkout.session.completed: {}", e.getMessage(), e);
    }
  }

  private void handleInvoicePaid(Event event) {
    try {
      Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
      if (invoice == null) {
        logger.error("SECURITY: invoice.paid event missing invoice object");
        return;
      }
      
      if (invoice.getLines() == null || invoice.getLines().getData() == null || invoice.getLines().getData().isEmpty()) {
        logger.error("SECURITY: invoice.paid event missing line items");
        return;
      }
      
      // Get subscription ID from the first line item
      String subscriptionId = invoice.getLines().getData().get(0).getSubscription();
      
      // Security: Validate subscriptionId
      if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
        logger.error("SECURITY: invoice.paid event missing subscriptionId");
        return;
      }
      
      if (!subscriptionId.startsWith("sub_")) {
        logger.error("SECURITY: Invalid subscriptionId format in invoice.paid event: {}", subscriptionId);
        return;
      }

      logger.info("Processing invoice.paid for subscriptionId: {}", subscriptionId);

      // Find user by subscription ID
      User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
      if (user != null && user.getAutoRenewal()) {
        LocalDate today = LocalDate.now();

        // Atomic update: only adds credits if this is a NEW billing period (monthly renewal)
        // The WHERE clause prevents duplicate additions within the same day
        int rowsAffected = userRepository.atomicAddCreditsForRenewal(user.getId(), 1000, today, "active");

        if (rowsAffected > 0) {
          logger.info("Monthly renewal: Added 1000 credits for user ID: {} (previous lastReset: {})",
                     user.getId(), user.getLastPaidReset());
        } else {
          logger.info("Skipping credit addition for user ID: {} - already added today ({})",
                     user.getId(), today);
        }
      } else if (user != null) {
        logger.info("User ID: {} has auto-renewal disabled, not adding credits", user.getId());
      } else {
        logger.warn("No user found for subscription ID: {}", subscriptionId);
      }
    } catch (Exception e) {
      logger.error("Error processing invoice.paid: {}", e.getMessage());
    }
  }

  private void handleSubscriptionUpdated(Event event) {
    try {
      Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
      if (subscription == null) {
        logger.error("SECURITY: subscription.updated event missing subscription object");
        return;
      }
      
      String subscriptionId = subscription.getId();
      String status = subscription.getStatus();
      Boolean cancelAtPeriodEnd = subscription.getCancelAtPeriodEnd();
      
      // Security: Validate subscriptionId
      if (subscriptionId == null || subscriptionId.trim().isEmpty() || !subscriptionId.startsWith("sub_")) {
        logger.error("SECURITY: Invalid subscriptionId in subscription.updated event: {}", subscriptionId);
        return;
      }
      
      // Security: Validate status
      if (status == null || status.trim().isEmpty()) {
        logger.error("SECURITY: Missing status in subscription.updated event");
        return;
      }

      logger.info("Processing subscription.updated for subscriptionId: {}, status: {}, cancelAtPeriodEnd: {}",
                  subscriptionId, status, cancelAtPeriodEnd);

      User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
      if (user != null) {
        user.setSubscriptionStatus(status);
        // If user canceled subscription, disable auto-renewal
        if (cancelAtPeriodEnd) {
          user.setAutoRenewal(false);
          logger.info("Auto-renewal disabled for user ID: {} (subscription set to cancel at period end)", user.getId());
        }
        userRepository.save(user);
        logger.info("Updated subscription status for user ID: {} to {}", user.getId(), status);
      }
    } catch (Exception e) {
      logger.error("Error processing subscription.updated: {}", e.getMessage());
    }
  }

  private void handleSubscriptionDeleted(Event event) {
    try {
      Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
      if (subscription == null) {
        logger.error("SECURITY: subscription.deleted event missing subscription object");
        return;
      }
      
      String subscriptionId = subscription.getId();
      
      // Security: Validate subscriptionId
      if (subscriptionId == null || subscriptionId.trim().isEmpty() || !subscriptionId.startsWith("sub_")) {
        logger.error("SECURITY: Invalid subscriptionId in subscription.deleted event: {}", subscriptionId);
        return;
      }

      logger.info("Processing subscription.deleted for subscriptionId: {}", subscriptionId);

      User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
      if (user != null) {
        user.setSubscriptionStatus("canceled");
        user.setAutoRenewal(false);
        // Don't remove credits immediately - let them use remaining credits
        userRepository.save(user);
        logger.info("Canceled subscription for user ID: {}, auto-renewal disabled", user.getId());
      }
    } catch (Exception e) {
      logger.error("Error processing subscription.deleted: {}", e.getMessage());
    }
  }
}