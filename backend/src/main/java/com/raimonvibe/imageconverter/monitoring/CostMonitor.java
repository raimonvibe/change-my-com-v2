package com.raimonvibe.imageconverter.monitoring;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CostMonitor {
    
    private final AtomicLong totalConversions = new AtomicLong(0);
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    public void recordConversion(long fileSizeBytes, long processingTimeMs, String userEmail, String format) {
        totalConversions.incrementAndGet();
        totalBytesProcessed.addAndGet(fileSizeBytes);
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        
        // Log cost metrics
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("=== COST METRICS ===");
        System.out.println("Timestamp: " + timestamp);
        System.out.println("User: " + (userEmail != null ? userEmail : "anonymous"));
        System.out.println("File size: " + formatBytes(fileSizeBytes));
        System.out.println("Processing time: " + processingTimeMs + "ms");
        System.out.println("Target format: " + format);
        System.out.println("Total conversions today: " + totalConversions.get());
        System.out.println("Total data processed today: " + formatBytes(totalBytesProcessed.get()));
        System.out.println("Total processing time today: " + (totalProcessingTimeMs.get() / 1000) + "s");
        
        // Cost estimation (rough estimates)
        double estimatedCost = estimateCost(fileSizeBytes, processingTimeMs);
        System.out.println("Estimated cost: $" + String.format("%.4f", estimatedCost));
        System.out.println("=== END COST METRICS ===");
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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("=== FAILED UPLOAD COST ===");
        System.out.println("Timestamp: " + timestamp);
        System.out.println("Reason: " + reason);
        System.out.println("File size: " + formatBytes(fileSizeBytes));
        System.out.println("Cost: Wasted bandwidth and processing time");
        System.out.println("=== END FAILED UPLOAD COST ===");
    }
    
    // Getters for monitoring
    public long getTotalConversions() { return totalConversions.get(); }
    public long getTotalBytesProcessed() { return totalBytesProcessed.get(); }
    public long getTotalProcessingTimeMs() { return totalProcessingTimeMs.get(); }
}
