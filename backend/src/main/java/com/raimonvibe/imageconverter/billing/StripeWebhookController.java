package com.raimonvibe.imageconverter.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe webhook endpoint. Processing is a pipeline: validate request →
 * verify signature → record for idempotency → dispatch to the matching
 * {@link StripeEventHandler}. Event-type-specific logic lives in the handler
 * components (Strategy pattern), not here.
 */
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

  private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

  private final WebhookEventRepository webhookEventRepository;

  /** Registry of event handlers keyed by Stripe event type. */
  private final Map<String, StripeEventHandler> eventHandlers;

  public StripeWebhookController(WebhookEventRepository webhookEventRepository,
                                 List<StripeEventHandler> handlers) {
    this.webhookEventRepository = webhookEventRepository;
    this.eventHandlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(StripeEventHandler::eventType, Function.identity()));
  }

  @Value("${app.stripe.webhookSecret:}")
  private String webhookSecret;

  // Security: Maximum payload size for Stripe webhooks (512KB - Stripe's typical max is ~100KB)
  private static final int MAX_PAYLOAD_SIZE = 512 * 1024; // 512KB

  @PostMapping(value = "/webhook", consumes = "application/json")
  @Transactional
  public ResponseEntity<String> webhook(@RequestBody byte[] payloadBytes,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader,
                                        HttpServletRequest request) throws IOException {
    logRequestInfo(request, sigHeader);

    ResponseEntity<String> rejection = validateRequest(payloadBytes, sigHeader);
    if (rejection != null) {
      return rejection;
    }

    // Convert to string for Stripe SDK (must use exact bytes for signature verification)
    String payload = new String(payloadBytes, StandardCharsets.UTF_8);
    logger.info("Webhook payload length: {} bytes", payloadBytes.length);
    logEventMetadata(payload);

    Event event;
    try {
      event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
      logger.info("✓ Webhook signature verification successful");
    } catch (SignatureVerificationException e) {
      logSignatureFailure(e, sigHeader, payload);
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

    // Dispatch to the registered handler for this event type - happens once per event ID
    StripeEventHandler handler = eventHandlers.get(event.getType());
    if (handler == null) {
      // Return 200 to acknowledge receipt but don't process unknown events
      logger.info("Unhandled event type: {} (id: {}) - ignoring", event.getType(), event.getId());
      return ResponseEntity.ok("ok");
    }

    try {
      handler.handle(event);
    } catch (Exception e) {
      logger.error("Error processing webhook event {}: {}", event.getId(), e.getMessage(), e);
      // Event is already recorded as processed, but processing failed
      // We return 200 to prevent Stripe from retrying (which would be ignored anyway)
      return ResponseEntity.ok("processing_failed");
    }

    return ResponseEntity.ok("ok");
  }

  /**
   * Structural request validation before any signature work: signature header
   * present, webhook secret configured, payload non-empty and within limits.
   *
   * @return an error response when the request must be rejected, otherwise null
   */
  private ResponseEntity<String> validateRequest(byte[] payloadBytes, String sigHeader) {
    // Security: Require signature header (PRIMARY SECURITY CHECK)
    if (sigHeader == null || sigHeader.trim().isEmpty()) {
      logger.error("SECURITY: Webhook request missing Stripe-Signature header - rejecting");
      return ResponseEntity.status(400).body("Missing signature header");
    }

    if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
      logger.error("CRITICAL: Stripe webhook secret is not configured!");
      logger.error("CRITICAL: Check the STRIPE_WEBHOOK_SECRET environment variable and restart the service");
      return ResponseEntity.status(500).body("Webhook secret not configured");
    }

    if (!webhookSecret.startsWith("whsec_")) {
      logger.error("CRITICAL: Webhook secret format is incorrect! Secret should start with 'whsec_' " +
          "(current length: {} chars, starts with: {})",
          webhookSecret.length(), webhookSecret.substring(0, Math.min(10, webhookSecret.length())));
    }

    // Security: Validate payload size to prevent DoS attacks
    if (payloadBytes == null || payloadBytes.length == 0) {
      logger.error("SECURITY: Empty webhook payload - rejecting");
      return ResponseEntity.status(400).body("Empty payload");
    }

    if (payloadBytes.length > MAX_PAYLOAD_SIZE) {
      logger.error("SECURITY: Webhook payload too large: {} bytes (max: {} bytes) - rejecting",
          payloadBytes.length, MAX_PAYLOAD_SIZE);
      return ResponseEntity.status(413).body("Payload too large");
    }

    return null;
  }

  private void logRequestInfo(HttpServletRequest request, String sigHeader) {
    String clientIp = request.getRemoteAddr();
    String userAgent = request.getHeader("User-Agent");
    logger.info("Webhook request from IP: {}, User-Agent: {}", clientIp, userAgent);

    // Security: Optional User-Agent verification (warning only, not blocking)
    // This is a defense-in-depth measure - signature verification is the primary security
    if (userAgent != null && !userAgent.startsWith("Stripe/")) {
      logger.warn("SECURITY: Webhook User-Agent does not match expected Stripe pattern: {}", userAgent);
      logger.warn("SECURITY: This may indicate a spoofed request, but signature verification will be the final check");
    }

    if (sigHeader != null) {
      String[] sigParts = sigHeader.split(",");
      if (sigParts.length > 0) {
        String timestamp = sigParts[0].contains("=") ? sigParts[0].split("=")[1] : "unknown";
        logger.info("Webhook signature timestamp: {}", timestamp);
      }
    }
  }

  /** Security: Only extract and log event metadata, never the full payload. */
  private void logEventMetadata(String payload) {
    try {
      com.google.gson.JsonObject payloadJson = new com.google.gson.Gson().fromJson(payload, com.google.gson.JsonObject.class);
      if (payloadJson.has("type")) {
        logger.info("Webhook event type: {}", payloadJson.get("type").getAsString());
      }
      if (payloadJson.has("id")) {
        logger.info("Webhook event ID: {}", payloadJson.get("id").getAsString());
      }
    } catch (Exception e) {
      logger.warn("Could not parse payload JSON for debugging: {}", e.getMessage());
    }
  }

  private void logSignatureFailure(SignatureVerificationException e, String sigHeader, String payload) {
    logger.error("Stripe webhook signature verification failed: {}", e.getMessage());
    logger.error("This usually means:");
    logger.error("  1) Webhook secret mismatch - Check if you're using TEST secret with LIVE project or vice versa");
    logger.error("  2) Request not from Stripe - Verify webhook is coming from Stripe servers");
    logger.error("  3) Payload was modified - Check if proxy/load balancer is modifying requests");
    logger.error("  4) Wrong webhook endpoint - Ensure webhook secret matches the endpoint in Stripe Dashboard");
    // Security: Only log secret prefix (first 10 chars), never the full secret
    logger.error("Current webhook secret length: {} chars, prefix: {}",
                 webhookSecret.length(),
                 webhookSecret.substring(0, Math.min(10, webhookSecret.length())));

    // Additional debugging: Log signature header structure (safe - these are hashes, not secrets)
    String[] sigParts = sigHeader.split(",");
    if (sigParts.length > 0) {
      // Only log timestamp, not the signature hash itself
      String timestampPart = sigParts[0];
      logger.error("Signature timestamp: {}", timestampPart.contains("=") ? timestampPart.split("=")[1] : "unknown");
      logger.error("Signature has {} parts", sigParts.length);
    }

    // Security: Only log event metadata, not full payload
    try {
      com.google.gson.JsonObject payloadJson = new com.google.gson.Gson().fromJson(payload, com.google.gson.JsonObject.class);
      if (payloadJson.has("type")) {
        logger.error("Failed event type: {}", payloadJson.get("type").getAsString());
      }
      if (payloadJson.has("id")) {
        logger.error("Failed event ID: {}", payloadJson.get("id").getAsString());
      }
    } catch (Exception parseEx) {
      logger.warn("Could not parse payload for debugging");
    }

    // Check if payload might have been modified
    if (payload.contains("\r\n") || payload.contains("\r")) {
      logger.error("WARNING: Payload contains CR/LF characters - might have been modified by proxy");
    }
  }
}
