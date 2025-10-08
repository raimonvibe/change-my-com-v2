package com.raimonvibe.imageconverter.user;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final CreditLedgerRepository creditLedgerRepository;

  public UserService(UserRepository userRepository, CreditLedgerRepository creditLedgerRepository) {
    this.userRepository = userRepository;
    this.creditLedgerRepository = creditLedgerRepository;
  }

  @Transactional
  public User ensureUserByEmail(String email) {
    return userRepository.findByEmail(email).orElseGet(() -> {
      User u = new User();
      u.setEmail(email);
      u.setFreeUsedToday(0);
      u.setLastFreeReset(LocalDate.now());
      u.setPaidCredits(0);
      return userRepository.save(u);
    });
  }

  @Transactional
  public void addCredits(User user, int credits, String reason) {
    user.setPaidCredits(user.getPaidCredits() + credits);
    userRepository.save(user);
    CreditLedger ledger = new CreditLedger();
    ledger.setUser(user);
    ledger.setDelta(credits);
    ledger.setReason(reason);
    creditLedgerRepository.save(ledger);
  }

  @Transactional
  public boolean consumeOneConversion(User user, int freeDailyLimit) {
    LocalDate today = LocalDate.now();
    if (!today.equals(user.getLastFreeReset())) {
      user.setLastFreeReset(today);
      user.setFreeUsedToday(0);
    }
    if (user.getFreeUsedToday() < freeDailyLimit) {
      user.setFreeUsedToday(user.getFreeUsedToday() + 1);
      userRepository.save(user);
      CreditLedger ledger = new CreditLedger();
      ledger.setUser(user);
      ledger.setDelta(0);
      ledger.setReason("conversion_free");
      creditLedgerRepository.save(ledger);
      return true;
    }
    if (user.getPaidCredits() > 0) {
      user.setPaidCredits(user.getPaidCredits() - 1);
      userRepository.save(user);
      CreditLedger ledger = new CreditLedger();
      ledger.setUser(user);
      ledger.setDelta(-1);
      ledger.setReason("conversion_paid");
      creditLedgerRepository.save(ledger);
      return true;
    }
    return false;
  }
}
