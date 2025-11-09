package com.raimonvibe.imageconverter.user;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final CreditLedgerRepository creditLedgerRepository;
  private final EntityManager entityManager;

  public UserService(UserRepository userRepository, CreditLedgerRepository creditLedgerRepository, EntityManager entityManager) {
    this.userRepository = userRepository;
    this.creditLedgerRepository = creditLedgerRepository;
    this.entityManager = entityManager;
  }

  @Transactional
  public User ensureUserByEmail(String email) {
    return userRepository.findByEmail(email).map(user -> {
      // Fix null autoRenewal for existing users (from old schema/rebuilds)
      if (user.getAutoRenewal() == null) {
        // Default to true if they have an active subscription, false otherwise
        boolean defaultValue = user.getStripeSubscriptionId() != null;
        user.setAutoRenewal(defaultValue);
        return userRepository.save(user);
      }
      return user;
    }).orElseGet(() -> {
      User u = new User();
      u.setEmail(email);
      u.setFreeUsedToday(0);
      u.setLastFreeReset(LocalDate.now());
      u.setPaidCredits(0);
      u.setAutoRenewal(false); // Default for new users
      return userRepository.save(u);
    });
  }

  @Transactional
  public void addCredits(User user, int credits, String reason) {
    // Use atomic database update to prevent race conditions
    userRepository.atomicAddCredits(user.getId(), credits);
    userRepository.flush();
    // Refresh user entity to get updated credits from database
    entityManager.refresh(user);
    // Log the credit change
    CreditLedger ledger = new CreditLedger();
    ledger.setUser(user);
    ledger.setDelta(credits);
    ledger.setReason(reason);
    creditLedgerRepository.save(ledger);
  }

  @Transactional
  public void activateSubscription(User user, int credits) {
    // Use atomic database update to prevent race conditions
    LocalDate today = LocalDate.now();
    userRepository.atomicAddCreditsAndUpdateReset(user.getId(), credits, today);
    userRepository.flush();
    // Refresh user entity to get updated values from database
    entityManager.refresh(user);
    // Log the credit addition
    CreditLedger ledger = new CreditLedger();
    ledger.setUser(user);
    ledger.setDelta(credits);
    ledger.setReason("subscription_activated");
    creditLedgerRepository.save(ledger);
  }

  @Transactional
  public User saveUser(User user) {
    return userRepository.save(user);
  }

  @Transactional
  public boolean consumeOneConversion(User user, int freeDailyLimit) {
    LocalDate today = LocalDate.now();
    if (!today.equals(user.getLastFreeReset())) {
      user.setLastFreeReset(today);
      user.setFreeUsedToday(0);
      userRepository.save(user);
      userRepository.flush();
      // Refresh to ensure we have latest state
      entityManager.refresh(user);
    }

    // If user has paid credits (subscriber), use those FIRST
    // Credits are only added via Stripe webhooks when subscription is renewed
    if (user.getPaidCredits() > 0) {
      // Atomic decrement - only decrements if credits > 0
      int rowsAffected = userRepository.atomicDecrementCredits(user.getId());
      if (rowsAffected > 0) {
        userRepository.flush();
        // Refresh user entity to get updated credits from database
        entityManager.refresh(user);
        CreditLedger ledger = new CreditLedger();
        ledger.setUser(user);
        ledger.setDelta(-1);
        ledger.setReason("conversion_paid");
        creditLedgerRepository.save(ledger);
        return true;
      }
    }

    // Otherwise, use free daily conversions
    if (user.getFreeUsedToday() < freeDailyLimit) {
      userRepository.atomicIncrementFreeUsage(user.getId(), today);
      userRepository.flush();
      // Refresh user entity to get updated free usage from database
      entityManager.refresh(user);
      CreditLedger ledger = new CreditLedger();
      ledger.setUser(user);
      ledger.setDelta(0);
      ledger.setReason("conversion_free");
      creditLedgerRepository.save(ledger);
      return true;
    }

    return false;
  }
}
