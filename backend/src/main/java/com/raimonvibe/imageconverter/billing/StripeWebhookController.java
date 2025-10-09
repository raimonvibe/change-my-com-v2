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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

  private final UserService userService;
  private final UserRepository userRepository;

  public StripeWebhookController(UserService userService, UserRepository userRepository) {
    this.userService = userService;
    this.userRepository = userRepository;
  }

  @Value("${app.stripe.webhookSecret:}")
  private String webhookSecret;

  @PostMapping("/webhook")
  public ResponseEntity<String> webhook(HttpServletRequest request, @RequestBody byte[] payloadBytes, @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {
    logger.info("=== WEBHOOK RECEIVED === Signature present: {}", sigHeader != null);

    String payload = new String(payloadBytes, StandardCharsets.UTF_8);
    logger.info("Webhook payload length: {} bytes", payloadBytes.length);

    Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      logger.error("Stripe webhook signature verification failed: {}", e.getMessage());
      logger.error("Webhook secret configured: {}", webhookSecret != null && !webhookSecret.isEmpty());
      return ResponseEntity.status(400).body("Invalid signature");
    }

    logger.info("=== Received Stripe webhook event: {} ===", event.getType());

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
        logger.info("Unhandled event type: {}", event.getType());
    }
    return ResponseEntity.ok("ok");
  }

  private void handleCheckoutCompleted(Event event) {
    try {
      logger.info("Raw event data: {}", event.getData());

      // Try to get the session from the event data
      Session session = null;
      if (event.getDataObjectDeserializer().getObject().isPresent()) {
        session = (Session) event.getDataObjectDeserializer().getObject().get();
      }

      if (session == null) {
        logger.error("Session object is null, trying alternative deserialization");
        // Try alternative approach using Stripe API
        String sessionId = event.getData().getObject().get("id").getAsString();
        logger.info("Attempting to retrieve session with ID: {}", sessionId);
        session = Session.retrieve(sessionId);
      }

      if (session != null) {
        String email = session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null;
        String subscriptionId = session.getSubscription();
        Map<String, String> metadata = session.getMetadata();

        logger.info("Processing checkout.session.completed for email: {}, subscriptionId: {}, metadata: {}",
                    email, subscriptionId, metadata);

        if (metadata != null && "monthly_1000".equals(metadata.get("subscription"))) {
          if (email != null && subscriptionId != null) {
            User user = userService.ensureUserByEmail(email);
            user.setStripeSubscriptionId(subscriptionId);
            user.setSubscriptionStatus("active");
            user.setAutoRenewal(true); // Enable auto-renewal by default when subscribing
            userService.activateSubscription(user, 1000);
            logger.info("Activated subscription for user: {}, subscriptionId: {}, auto-renewal: enabled", email, subscriptionId);
          } else {
            logger.warn("Missing email or subscriptionId in checkout session");
          }
        } else {
          logger.warn("Metadata missing or subscription type not matched. Metadata: {}", metadata);
        }
      } else {
        logger.error("Failed to deserialize checkout session from webhook event - session is still null after all attempts");
      }
    } catch (Exception e) {
      logger.error("Error handling checkout.session.completed: {}", e.getMessage(), e);
    }
  }

  private void handleInvoicePaid(Event event) {
    try {
      Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
      if (invoice != null && invoice.getLines() != null && invoice.getLines().getData().size() > 0) {
        // Get subscription ID from the first line item
        String subscriptionId = invoice.getLines().getData().get(0).getSubscription();

        logger.info("Processing invoice.paid for subscriptionId: {}", subscriptionId);

        // Find user by subscription ID
        User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (user != null && user.getAutoRenewal()) {
          // Only renew if auto-renewal is enabled
          user.setPaidCredits(1000);
          user.setLastPaidReset(LocalDate.now());
          user.setSubscriptionStatus("active");
          userRepository.save(user);
          logger.info("Renewed 1000 credits for user: {} (auto-renewal enabled)", user.getEmail());
        } else if (user != null) {
          logger.info("User {} has auto-renewal disabled, not adding credits", user.getEmail());
        } else {
          logger.warn("No user found for subscription ID: {}", subscriptionId);
        }
      }
    } catch (Exception e) {
      logger.error("Error processing invoice.paid: {}", e.getMessage());
    }
  }

  private void handleSubscriptionUpdated(Event event) {
    try {
      Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
      if (subscription != null) {
        String subscriptionId = subscription.getId();
        String status = subscription.getStatus();
        Boolean cancelAtPeriodEnd = subscription.getCancelAtPeriodEnd();

        logger.info("Processing subscription.updated for subscriptionId: {}, status: {}, cancelAtPeriodEnd: {}",
                    subscriptionId, status, cancelAtPeriodEnd);

        User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (user != null) {
          user.setSubscriptionStatus(status);
          // If user canceled subscription, disable auto-renewal
          if (cancelAtPeriodEnd) {
            user.setAutoRenewal(false);
            logger.info("Auto-renewal disabled for user: {} (subscription set to cancel at period end)", user.getEmail());
          }
          userRepository.save(user);
          logger.info("Updated subscription status for user: {} to {}", user.getEmail(), status);
        }
      }
    } catch (Exception e) {
      logger.error("Error processing subscription.updated: {}", e.getMessage());
    }
  }

  private void handleSubscriptionDeleted(Event event) {
    try {
      Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
      if (subscription != null) {
        String subscriptionId = subscription.getId();

        logger.info("Processing subscription.deleted for subscriptionId: {}", subscriptionId);

        User user = userRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (user != null) {
          user.setSubscriptionStatus("canceled");
          user.setAutoRenewal(false);
          // Don't remove credits immediately - let them use remaining credits
          userRepository.save(user);
          logger.info("Canceled subscription for user: {}, auto-renewal disabled", user.getEmail());
        }
      }
    } catch (Exception e) {
      logger.error("Error processing subscription.deleted: {}", e.getMessage());
    }
  }
}