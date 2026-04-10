package com.raimonvibe.imageconverter.user;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Data;

@Entity
@Table(name = "app_user", indexes = {
    @Index(name = "idx_user_email", columnList = "email")
})
@Data
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(unique = true, nullable = false)
  private String email;

  private Integer freeUsedToday = 0;
  private LocalDate lastFreeReset = LocalDate.now();

  private Integer paidCredits = 0;
  private LocalDate lastPaidReset = null;

  // Stripe subscription tracking
  private String stripeSubscriptionId = null;
  private String subscriptionStatus = null; // active, canceled, past_due, etc.
  private Boolean autoRenewal = false; // Whether user wants auto-renewal
}
