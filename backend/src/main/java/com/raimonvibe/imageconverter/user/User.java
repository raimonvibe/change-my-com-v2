package com.raimonvibe.imageconverter.user;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.raimonvibe.imageconverter.user.UserService;
import com.raimonvibe.imageconverter.user.AnonymousUserService;
import com.raimonvibe.imageconverter.user.User;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Data;

@Entity
@Table(name = "app_user")
@Data
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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
