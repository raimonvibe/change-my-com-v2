package com.raimonvibe.imageconverter.billing;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserService;
import com.stripe.model.Event;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Activates the monthly subscription when Stripe checkout completes:
 * links the Stripe subscription to the user and grants the plan's credits.
 */
@Component
public class CheckoutCompletedHandler implements StripeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(CheckoutCompletedHandler.class);

  private final UserService userService;

  public CheckoutCompletedHandler(UserService userService) {
    this.userService = userService;
  }

  @Override
  public String eventType() {
    return "checkout.session.completed";
  }

  @Override
  public void handle(Event event) {
    try {
      logger.info("Processing checkout.session.completed event");

      // Parse the JSON from the event
      Gson gson = new Gson();
      String jsonString = event.getData().getObject().toJson();
      JsonObject dataObject = gson.fromJson(jsonString, JsonObject.class);

      String email = null;
      if (dataObject.has("customer_details") && !dataObject.get("customer_details").isJsonNull()) {
        JsonObject customerDetails = dataObject.getAsJsonObject("customer_details");
        if (customerDetails.has("email") && !customerDetails.get("email").isJsonNull()) {
          email = customerDetails.get("email").getAsString();
        }
      }

      String subscriptionId = null;
      if (dataObject.has("subscription") && !dataObject.get("subscription").isJsonNull()) {
        subscriptionId = dataObject.get("subscription").getAsString();
      }

      Map<String, String> metadata = new HashMap<>();
      if (dataObject.has("metadata") && !dataObject.get("metadata").isJsonNull()) {
        JsonObject metadataObj = dataObject.getAsJsonObject("metadata");
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

      if (BillingPlan.MONTHLY_PLAN_ID.equals(metadata.get(BillingPlan.METADATA_KEY))) {
        User user = userService.ensureUserByEmail(email);
        user.setStripeSubscriptionId(subscriptionId);
        user.setSubscriptionStatus("active");
        user.setAutoRenewal(true);
        userService.activateSubscription(user, BillingPlan.MONTHLY_CREDITS);
        logger.info("✓ Activated subscription for user ID: {}, subscriptionId: {}, credits: {}",
            user.getId(), subscriptionId, BillingPlan.MONTHLY_CREDITS);
      } else {
        logger.warn("Metadata check failed - metadata: {}", metadata);
      }
    } catch (Exception e) {
      logger.error("Error handling checkout.session.completed: {}", e.getMessage(), e);
    }
  }
}
