package com.raimonvibe.imageconverter.user;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymousUserService {
    private final IpConversionTrackerRepository ipConversionTrackerRepository;

    public AnonymousUserService(IpConversionTrackerRepository ipConversionTrackerRepository) {
        this.ipConversionTrackerRepository = ipConversionTrackerRepository;
    }

    @Transactional
    public boolean consumeOneConversion(String ipAddress, int freeDailyLimit) {
        IpConversionTracker tracker = ipConversionTrackerRepository.findByIpAddress(ipAddress)
            .orElseGet(() -> {
                IpConversionTracker newTracker = new IpConversionTracker();
                newTracker.setIpAddress(ipAddress);
                newTracker.setConversionsUsedToday(0);
                newTracker.setLastReset(LocalDate.now());
                return newTracker;
            });

        LocalDate today = LocalDate.now();
        if (!today.equals(tracker.getLastReset())) {
            tracker.setLastReset(today);
            tracker.setConversionsUsedToday(0);
        }

        if (tracker.getConversionsUsedToday() < freeDailyLimit) {
            tracker.setConversionsUsedToday(tracker.getConversionsUsedToday() + 1);
            ipConversionTrackerRepository.save(tracker);
            return true;
        }

        return false;
    }

    public int getRemainingConversions(String ipAddress, int freeDailyLimit) {
        IpConversionTracker tracker = ipConversionTrackerRepository.findByIpAddress(ipAddress)
            .orElse(null);
        
        if (tracker == null) {
            return freeDailyLimit;
        }

        LocalDate today = LocalDate.now();
        if (!today.equals(tracker.getLastReset())) {
            return freeDailyLimit;
        }

        return Math.max(0, freeDailyLimit - tracker.getConversionsUsedToday());
    }
}
