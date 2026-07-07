package com.raimonvibe.imageconverter.billing;

import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mirrors Stripe subscription status changes onto the local user, disabling
 * auto-renewal when the subscription is set to cancel at period end.
 */
@Component
public class SubscriptionUpdatedHandler implements StripeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SubscriptionUpdatedHandler.class);

  private final UserRepository userRepository;

  public SubscriptionUpdatedHandler(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public String eventType() {
    return "customer.subscription.updated";
  }

  @Override
  public void handle(Event event) {
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
}
