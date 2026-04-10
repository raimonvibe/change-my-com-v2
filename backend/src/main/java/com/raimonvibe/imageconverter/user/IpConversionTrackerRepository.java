package com.raimonvibe.imageconverter.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IpConversionTrackerRepository extends JpaRepository<IpConversionTracker, Long> {
    Optional<IpConversionTracker> findByIpAddress(String ipAddress);
}
