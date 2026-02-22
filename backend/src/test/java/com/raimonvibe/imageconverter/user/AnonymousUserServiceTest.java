package com.raimonvibe.imageconverter.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnonymousUserService
 * Tests IP-based conversion tracking for anonymous users
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnonymousUserService Unit Tests")
public class AnonymousUserServiceTest {

    @Mock
    private IpConversionTrackerRepository ipConversionTrackerRepository;

    @InjectMocks
    private AnonymousUserService anonymousUserService;

    private IpConversionTracker testTracker;
    private static final int FREE_DAILY_LIMIT = 20;
    private static final String TEST_IP_V4 = "192.168.1.100";
    private static final String TEST_IP_V6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

    @BeforeEach
    void setUp() {
        testTracker = new IpConversionTracker();
        testTracker.setId(1L);
        testTracker.setIpAddress(TEST_IP_V4);
        testTracker.setConversionsUsedToday(0);
        testTracker.setLastReset(LocalDate.now());
    }

    // ==================== CONVERSION CONSUMPTION TESTS ====================

    @Test
    @DisplayName("Should allow conversion for new IP address")
    void testConsumeOneConversion_NewIpAddress() {
        // Given: IP address doesn't exist in database
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.empty());
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenAnswer(invocation -> {
            IpConversionTracker saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // When: Consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should succeed and create new tracker
        assertTrue(result);
        verify(ipConversionTrackerRepository, times(1)).save(any(IpConversionTracker.class));
    }

    @Test
    @DisplayName("Should increment conversions for existing IP address")
    void testConsumeOneConversion_ExistingIp() {
        // Given: IP address exists with 5 conversions used
        testTracker.setConversionsUsedToday(5);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(testTracker);

        // When: Consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should succeed and increment to 6
        assertTrue(result);
        assertEquals(6, testTracker.getConversionsUsedToday());
        verify(ipConversionTrackerRepository, times(1)).save(testTracker);
    }

    @Test
    @DisplayName("Should reject conversion when daily limit reached")
    void testConsumeOneConversion_LimitReached() {
        // Given: IP address has reached limit (20 conversions)
        testTracker.setConversionsUsedToday(20);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Try to consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should be rejected
        assertFalse(result);
        assertEquals(20, testTracker.getConversionsUsedToday());
        verify(ipConversionTrackerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reset conversions at midnight (new day)")
    void testConsumeOneConversion_ResetAtMidnight() {
        // Given: IP address was at limit yesterday
        testTracker.setConversionsUsedToday(20);
        testTracker.setLastReset(LocalDate.now().minusDays(1)); // Yesterday
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(testTracker);

        // When: Consume one conversion today
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should reset and allow conversion
        assertTrue(result);
        assertEquals(LocalDate.now(), testTracker.getLastReset());
        assertEquals(1, testTracker.getConversionsUsedToday()); // Reset to 0, then incremented to 1
    }

    @Test
    @DisplayName("Should handle IPv4 address")
    void testConsumeOneConversion_IPv4() {
        // Given: IPv4 address
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.empty());
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(testTracker);

        // When: Consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should succeed
        assertTrue(result);
        verify(ipConversionTrackerRepository, times(1)).save(any(IpConversionTracker.class));
    }

    @Test
    @DisplayName("Should handle IPv6 address")
    void testConsumeOneConversion_IPv6() {
        // Given: IPv6 address
        IpConversionTracker ipv6Tracker = new IpConversionTracker();
        ipv6Tracker.setIpAddress(TEST_IP_V6);
        ipv6Tracker.setConversionsUsedToday(0);
        ipv6Tracker.setLastReset(LocalDate.now());

        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V6)).thenReturn(Optional.empty());
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(ipv6Tracker);

        // When: Consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V6, FREE_DAILY_LIMIT);

        // Then: Should succeed
        assertTrue(result);
        verify(ipConversionTrackerRepository, times(1)).save(any(IpConversionTracker.class));
    }

    @Test
    @DisplayName("Should allow last conversion when at limit - 1")
    void testConsumeOneConversion_LastConversion() {
        // Given: IP address has 19 conversions (1 remaining)
        testTracker.setConversionsUsedToday(19);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(testTracker);

        // When: Consume one conversion
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should succeed and reach limit
        assertTrue(result);
        assertEquals(20, testTracker.getConversionsUsedToday());
    }

    // ==================== REMAINING CONVERSIONS TESTS ====================

    @Test
    @DisplayName("Should return full limit for new IP address")
    void testGetRemainingConversions_NewIp() {
        // Given: IP address doesn't exist
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.empty());

        // When: Get remaining conversions
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return full limit
        assertEquals(20, remaining);
    }

    @Test
    @DisplayName("Should calculate remaining conversions correctly")
    void testGetRemainingConversions_PartiallyUsed() {
        // Given: IP address has used 7 conversions
        testTracker.setConversionsUsedToday(7);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Get remaining conversions
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return 13 (20 - 7)
        assertEquals(13, remaining);
    }

    @Test
    @DisplayName("Should return 0 when limit reached")
    void testGetRemainingConversions_LimitReached() {
        // Given: IP address has reached limit
        testTracker.setConversionsUsedToday(20);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Get remaining conversions
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return 0
        assertEquals(0, remaining);
    }

    @Test
    @DisplayName("Should return full limit for new day")
    void testGetRemainingConversions_NewDay() {
        // Given: IP address was at limit yesterday
        testTracker.setConversionsUsedToday(20);
        testTracker.setLastReset(LocalDate.now().minusDays(1));
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Get remaining conversions today
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return full limit (reset)
        assertEquals(20, remaining);
    }

    @Test
    @DisplayName("Should return 1 when one conversion remaining")
    void testGetRemainingConversions_OneRemaining() {
        // Given: IP address has used 19 conversions
        testTracker.setConversionsUsedToday(19);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Get remaining conversions
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return 1
        assertEquals(1, remaining);
    }

    @Test
    @DisplayName("Should never return negative remaining conversions")
    void testGetRemainingConversions_NeverNegative() {
        // Given: IP address somehow has more than limit (edge case)
        testTracker.setConversionsUsedToday(25);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Get remaining conversions
        int remaining = anonymousUserService.getRemainingConversions(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should return 0 (not negative)
        assertEquals(0, remaining);
    }

    // ==================== IP ADDRESS ISOLATION TESTS ====================

    @Test
    @DisplayName("Should track different IP addresses separately")
    void testConsumeOneConversion_DifferentIpsIndependent() {
        // Given: Two different IP addresses
        String ip1 = "192.168.1.100";
        String ip2 = "192.168.1.101";

        IpConversionTracker tracker1 = new IpConversionTracker();
        tracker1.setIpAddress(ip1);
        tracker1.setConversionsUsedToday(20); // IP1 at limit
        tracker1.setLastReset(LocalDate.now());

        IpConversionTracker tracker2 = new IpConversionTracker();
        tracker2.setIpAddress(ip2);
        tracker2.setConversionsUsedToday(0); // IP2 unused
        tracker2.setLastReset(LocalDate.now());

        when(ipConversionTrackerRepository.findByIpAddress(ip1)).thenReturn(Optional.of(tracker1));
        when(ipConversionTrackerRepository.findByIpAddress(ip2)).thenReturn(Optional.of(tracker2));
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenReturn(tracker2);

        // When: IP1 tries to convert (should fail), IP2 tries to convert (should succeed)
        boolean result1 = anonymousUserService.consumeOneConversion(ip1, FREE_DAILY_LIMIT);
        boolean result2 = anonymousUserService.consumeOneConversion(ip2, FREE_DAILY_LIMIT);

        // Then: IP1 blocked, IP2 allowed
        assertFalse(result1);
        assertTrue(result2);
        assertEquals(20, tracker1.getConversionsUsedToday()); // IP1 unchanged
        assertEquals(1, tracker2.getConversionsUsedToday());  // IP2 incremented
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Should handle exact limit boundary")
    void testConsumeOneConversion_ExactLimit() {
        // Given: IP address has exactly 20 conversions
        testTracker.setConversionsUsedToday(20);
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.of(testTracker));

        // When: Try to consume (21st conversion)
        boolean result = anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Should be rejected (20 is not < 20)
        assertFalse(result);
    }

    @Test
    @DisplayName("Should create tracker with today's date for new IP")
    void testConsumeOneConversion_NewTrackerHasTodayDate() {
        // Given: New IP address
        when(ipConversionTrackerRepository.findByIpAddress(TEST_IP_V4)).thenReturn(Optional.empty());
        when(ipConversionTrackerRepository.save(any(IpConversionTracker.class))).thenAnswer(invocation -> {
            IpConversionTracker saved = invocation.getArgument(0);
            // Verify the saved tracker has today's date
            assertEquals(LocalDate.now(), saved.getLastReset());
            assertEquals(1, saved.getConversionsUsedToday());
            assertEquals(TEST_IP_V4, saved.getIpAddress());
            return saved;
        });

        // When: Consume conversion
        anonymousUserService.consumeOneConversion(TEST_IP_V4, FREE_DAILY_LIMIT);

        // Then: Verification in the answer() callback above
        verify(ipConversionTrackerRepository, times(1)).save(any(IpConversionTracker.class));
    }
}
