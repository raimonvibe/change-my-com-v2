package com.raimonvibe.imageconverter.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Data;

@Entity
@Data
public class CreditLedger {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private User user;

  private Integer delta; // positive for purchase, negative for consumption
  private String reason; // e.g., "purchase_pack", "conversion"
  private OffsetDateTime createdAt = OffsetDateTime.now();
}
