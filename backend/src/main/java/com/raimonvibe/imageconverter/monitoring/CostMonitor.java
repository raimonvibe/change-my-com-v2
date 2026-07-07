package com.raimonvibe.imageconverter.monitoring;

import com.raimonvibe.imageconverter.common.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CostMonitor {

    private static final Logger logger = LoggerFactory.getLogger(CostMonitor.class);

    private final AtomicLong totalConversions = new AtomicLong(0);
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    public void recordConversion(long fileSizeBytes, long processingTimeMs, String userEmail, String format) {
        totalConversions.incrementAndGet();
        totalBytesProcessed.addAndGet(fileSizeBytes);
        totalProcessingTimeMs.addAndGet(processingTimeMs);

        double estimatedCost = estimateCost(fileSizeBytes, processingTimeMs);

        logger.info("Cost metrics - User: {}, Size: {}, Time: {}ms, Format: {}, Est. cost: ${}, Total conversions: {}, Total data: {}, Total time: {}s",
                maskEmail(userEmail),
                formatBytes(fileSizeBytes),
                processingTimeMs,
                format,
                String.format("%.4f", estimatedCost),
                totalConversions.get(),
                formatBytes(totalBytesProcessed.get()),
                totalProcessingTimeMs.get() / 1000
        );
    }

    private String maskEmail(String email) {
        return EmailMasker.mask(email);
    }
    
    private double estimateCost(long fileSizeBytes, long processingTimeMs) {
        // Rough cost estimation based on:
        // - CPU time: ~$0.0001 per second
        // - Memory usage: ~$0.00001 per MB
        // - Storage (temp): ~$0.0001 per MB
        
        double cpuCost = (processingTimeMs / 1000.0) * 0.0001;
        double memoryCost = (fileSizeBytes / (1024.0 * 1024.0)) * 0.00001;
        double storageCost = (fileSizeBytes / (1024.0 * 1024.0)) * 0.0001;
        
        return cpuCost + memoryCost + storageCost;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
    
    public void recordFailedUpload(String reason, long fileSizeBytes) {
        logger.warn("Failed upload - Reason: {}, Size: {}, Cost: Wasted bandwidth",
                reason,
                formatBytes(fileSizeBytes)
        );
    }
    
    // Getters for monitoring
    public long getTotalConversions() { return totalConversions.get(); }
    public long getTotalBytesProcessed() { return totalBytesProcessed.get(); }
    public long getTotalProcessingTimeMs() { return totalProcessingTimeMs.get(); }
}
