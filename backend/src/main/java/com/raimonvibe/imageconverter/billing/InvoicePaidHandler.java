package com.raimonvibe.imageconverter.billing;

import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Grants the monthly credit allowance on each paid renewal invoice,
 * guarded by an atomic per-day update to prevent duplicate additions.
 */
@Component
public class InvoicePaidHandler implements StripeEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(InvoicePaidHandler.class);

  private final UserRepository userRepository;

  public InvoicePaidHandler(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public String eventType() {
    return "invoice.paid";
  }

  @Override
  public void handle(Event event) {
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
        int rowsAffected = userRepository.atomicAddCreditsForRenewal(user.getId(), BillingPlan.MONTHLY_CREDITS, today, "active");

        if (rowsAffected > 0) {
          logger.info("Monthly renewal: Added {} credits for user ID: {} (previous lastReset: {})",
                     BillingPlan.MONTHLY_CREDITS, user.getId(), user.getLastPaidReset());
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
}
