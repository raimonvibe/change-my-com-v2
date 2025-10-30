package com.raimonvibe.imageconverter.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class IpConversionTracker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String ipAddress;
    
    @Column(nullable = false)
    private Integer conversionsUsedToday = 0;
    
    @Column(nullable = false)
    private LocalDate lastReset = LocalDate.now();
}
