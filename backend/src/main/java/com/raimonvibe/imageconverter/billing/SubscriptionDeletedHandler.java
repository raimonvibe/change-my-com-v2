package com.raimonvibe.imageconverter.billing;

import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Marks the local subscription as canceled when Stripe deletes it.
 * Remaining paid credits are intentionally kept so the user can spend them.
 */
@Component
public class SubscriptionDeletedHandler implements StripeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionDeletedHandler.class);

  private final UserRepository userRepository;

  public SubscriptionDeletedHandler(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public String eventType() {
    return "customer.subscription.deleted";
  }

  @Override
  public void handle(Event event) {
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
