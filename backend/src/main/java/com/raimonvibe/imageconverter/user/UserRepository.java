package com.raimonvibe.imageconverter.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByStripeSubscriptionId(String stripeSubscriptionId);

    // Atomic credit update methods to prevent race conditions
    // clearAutomatically = true ensures JPA persistence context is cleared after update
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.paidCredits = u.paidCredits + :credits WHERE u.id = :userId")
    int atomicAddCredits(@Param("userId") Long userId, @Param("credits") int credits);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.paidCredits = u.paidCredits + :credits, u.lastPaidReset = :resetDate WHERE u.id = :userId")
    int atomicAddCreditsAndUpdateReset(@Param("userId") Long userId, @Param("credits") int credits, @Param("resetDate") LocalDate resetDate);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.paidCredits = u.paidCredits - 1 WHERE u.id = :userId AND u.paidCredits > 0")
    int atomicDecrementCredits(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.freeUsedToday = u.freeUsedToday + 1, u.lastFreeReset = :today WHERE u.id = :userId")
    int atomicIncrementFreeUsage(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.paidCredits = u.paidCredits + :credits, u.lastPaidReset = :today, u.subscriptionStatus = :status WHERE u.id = :userId AND (u.lastPaidReset IS NULL OR u.lastPaidReset != :today)")
    int atomicAddCreditsForRenewal(@Param("userId") Long userId, @Param("credits") int credits, @Param("today") LocalDate today, @Param("status") String status);
}
