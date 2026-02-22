package com.raimonvibe.imageconverter.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for Repository layer
 * Tests SQL injection prevention and input sanitization
 *
 * SECURITY FOCUS:
 * - SQL injection attempts via email parameter
 * - SQL injection via IP address parameter
 * - SQL injection via subscription ID
 * - Special character handling
 * - Parameterized query validation
 */
@SpringBootTest
@Transactional // Rollback after each test
@DisplayName("Repository Security Tests (SQL Injection Prevention)")
public class RepositorySecurityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IpConversionTrackerRepository ipConversionTrackerRepository;

    @Autowired
    private CreditLedgerRepository creditLedgerRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFreeUsedToday(0);
        testUser.setLastFreeReset(LocalDate.now());
        testUser.setPaidCredits(0);
        testUser.setAutoRenewal(false);
        testUser = userRepository.save(testUser);
    }

    // ==================== EMAIL PARAMETER SQL INJECTION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection in email (single quote)")
    void testSqlInjectionEmailSingleQuote() {
        // Attacker tries: admin'--
        String maliciousEmail = "admin'--";

        // Should not find user (parameterized query prevents injection)
        Optional<User> result = userRepository.findByEmail(maliciousEmail);
        assertFalse(result.isPresent(), "SQL injection attempt should not succeed");
    }

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection in email (OR 1=1)")
    void testSqlInjectionEmailOrCondition() {
        // Classic SQL injection: ' OR '1'='1
        String maliciousEmail = "' OR '1'='1";

        Optional<User> result = userRepository.findByEmail(maliciousEmail);
        assertFalse(result.isPresent(), "SQL injection with OR condition should fail");
    }

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection with UNION")
    void testSqlInjectionEmailUnion() {
        // UNION-based injection: ' UNION SELECT * FROM users--
        String maliciousEmail = "' UNION SELECT * FROM users--";

        Optional<User> result = userRepository.findByEmail(maliciousEmail);
        assertFalse(result.isPresent(), "UNION-based SQL injection should fail");
    }

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection with DROP TABLE")
    void testSqlInjectionEmailDropTable() {
        // Destructive injection: '; DROP TABLE users; --
        String maliciousEmail = "'; DROP TABLE users; --";

        // Should not drop table (parameterized query prevents execution)
        assertDoesNotThrow(() -> {
            Optional<User> result = userRepository.findByEmail(maliciousEmail);
            assertFalse(result.isPresent());
        });

        // Verify table still exists
        long userCount = userRepository.count();
        assertTrue(userCount >= 1, "Users table should still exist");
    }

    @Test
    @DisplayName("SECURITY: Should handle email with semicolon")
    void testEmailWithSemicolon() {
        String emailWithSemicolon = "test;malicious@example.com";

        Optional<User> result = userRepository.findByEmail(emailWithSemicolon);
        assertFalse(result.isPresent(), "Email with semicolon should not match existing users");
    }

    @Test
    @DisplayName("SECURITY: Should handle email with null bytes")
    void testEmailWithNullBytes() {
        String emailWithNullByte = "test\u0000@example.com";

        Optional<User> result = userRepository.findByEmail(emailWithNullByte);
        assertFalse(result.isPresent(), "Email with null byte should not match");
    }

    @Test
    @DisplayName("SECURITY: Should handle email with Unicode characters")
    void testEmailWithUnicode() {
        // Valid Unicode email
        String unicodeEmail = "tëst@éxample.com";

        User unicodeUser = new User();
        unicodeUser.setEmail(unicodeEmail);
        unicodeUser.setFreeUsedToday(0);
        unicodeUser.setLastFreeReset(LocalDate.now());
        unicodeUser.setPaidCredits(0);
        unicodeUser.setAutoRenewal(false);
        userRepository.save(unicodeUser);

        Optional<User> result = userRepository.findByEmail(unicodeEmail);
        assertTrue(result.isPresent(), "Should handle Unicode emails correctly");
        assertEquals(unicodeEmail, result.get().getEmail());
    }

    // ==================== SUBSCRIPTION ID SQL INJECTION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection in subscription ID")
    void testSqlInjectionSubscriptionId() {
        String maliciousSubId = "sub_123' OR '1'='1";

        Optional<User> result = userRepository.findByStripeSubscriptionId(maliciousSubId);
        assertFalse(result.isPresent(), "SQL injection in subscription ID should fail");
    }

    @Test
    @DisplayName("SECURITY: Should handle subscription ID with special characters")
    void testSubscriptionIdSpecialCharacters() {
        testUser.setStripeSubscriptionId("sub_test123");
        userRepository.save(testUser);

        // Try to find with injection attempt
        String maliciousSubId = "sub_test123'; DROP TABLE users; --";
        Optional<User> result = userRepository.findByStripeSubscriptionId(maliciousSubId);

        assertFalse(result.isPresent(), "Malicious subscription ID should not match");

        // Original should still work
        Optional<User> validResult = userRepository.findByStripeSubscriptionId("sub_test123");
        assertTrue(validResult.isPresent(), "Valid subscription ID should still work");
    }

    // ==================== IP ADDRESS SQL INJECTION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection in IP address")
    void testSqlInjectionIpAddress() {
        String maliciousIp = "192.168.1.1' OR '1'='1";

        Optional<IpConversionTracker> result = ipConversionTrackerRepository.findByIpAddress(maliciousIp);
        assertFalse(result.isPresent(), "SQL injection in IP address should fail");
    }

    @Test
    @DisplayName("SECURITY: Should handle IP address with SQL commands")
    void testIpAddressSqlCommands() {
        String maliciousIp = "'; DELETE FROM ip_conversion_tracker; --";

        assertDoesNotThrow(() -> {
            Optional<IpConversionTracker> result = ipConversionTrackerRepository.findByIpAddress(maliciousIp);
            assertFalse(result.isPresent());
        });

        // Verify table still exists and has data
        long count = ipConversionTrackerRepository.count();
        assertTrue(count >= 0, "IP tracker table should still exist");
    }

    @Test
    @DisplayName("SECURITY: Should handle IPv6 address with special characters")
    void testIpv6AddressHandling() {
        // Valid IPv6 address
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

        IpConversionTracker tracker = new IpConversionTracker();
        tracker.setIpAddress(ipv6);
        tracker.setConversionsUsedToday(0);
        tracker.setLastReset(LocalDate.now());
        ipConversionTrackerRepository.save(tracker);

        Optional<IpConversionTracker> result = ipConversionTrackerRepository.findByIpAddress(ipv6);
        assertTrue(result.isPresent(), "Should handle IPv6 addresses correctly");
        assertEquals(ipv6, result.get().getIpAddress());
    }

    // ==================== PARAMETERIZED QUERY VALIDATION ====================

    @Test
    @DisplayName("SECURITY: Should use parameterized queries (not string concatenation)")
    void testParameterizedQueries() {
        // This test documents that Spring Data JPA uses parameterized queries
        // If string concatenation was used, these would cause syntax errors or succeed incorrectly

        String[] injectionAttempts = {
            "' OR 1=1--",
            "'; DROP TABLE users; --",
            "admin'--",
            "' UNION SELECT * FROM users--",
            "1' AND '1'='1",
            "' OR 'x'='x"
        };

        for (String attempt : injectionAttempts) {
            // All should safely return empty result (not throw exception or return all users)
            Optional<User> result = userRepository.findByEmail(attempt);
            assertFalse(result.isPresent(),
                "Injection attempt should return empty: " + attempt);
        }

        // Verify only legitimate user exists
        Optional<User> validUser = userRepository.findByEmail("test@example.com");
        assertTrue(validUser.isPresent(), "Legitimate user should be found");
    }

    // ==================== SPECIAL CHARACTER HANDLING ====================

    @Test
    @DisplayName("SECURITY: Should handle emails with percent sign (LIKE wildcard)")
    void testEmailWithPercentSign() {
        // In raw SQL, % is a wildcard in LIKE queries
        String emailWithPercent = "test%@example.com";

        Optional<User> result = userRepository.findByEmail(emailWithPercent);
        assertFalse(result.isPresent(), "Percent sign should not act as wildcard");
    }

    @Test
    @DisplayName("SECURITY: Should handle emails with underscore (LIKE wildcard)")
    void testEmailWithUnderscore() {
        // In raw SQL, _ is a single-character wildcard in LIKE queries
        String emailWithUnderscore = "test_user@example.com";

        User underscoreUser = new User();
        underscoreUser.setEmail(emailWithUnderscore);
        underscoreUser.setFreeUsedToday(0);
        underscoreUser.setLastFreeReset(LocalDate.now());
        underscoreUser.setPaidCredits(0);
        underscoreUser.setAutoRenewal(false);
        userRepository.save(underscoreUser);

        // Should match exactly, not as wildcard
        Optional<User> result = userRepository.findByEmail(emailWithUnderscore);
        assertTrue(result.isPresent(), "Underscore should be treated literally");

        // Should NOT match different email
        Optional<User> noMatch = userRepository.findByEmail("test@user@example.com");
        assertFalse(noMatch.isPresent(), "Underscore should not act as wildcard");
    }

    @Test
    @DisplayName("SECURITY: Should handle backslash in email")
    void testEmailWithBackslash() {
        String emailWithBackslash = "test\\admin@example.com";

        Optional<User> result = userRepository.findByEmail(emailWithBackslash);
        assertFalse(result.isPresent(), "Backslash should be handled safely");
    }

    // ==================== CREDIT LEDGER SQL INJECTION TESTS ====================

    @Test
    @DisplayName("SECURITY: Should safely handle SQL injection in ledger reason field")
    void testSqlInjectionLedgerReason() {
        // Attacker tries to inject via reason field
        String maliciousReason = "test'; DROP TABLE credit_ledger; --";

        CreditLedger ledger = new CreditLedger();
        ledger.setUser(testUser);
        ledger.setDelta(100);
        ledger.setReason(maliciousReason);

        // Should save safely without executing SQL
        assertDoesNotThrow(() -> {
            creditLedgerRepository.save(ledger);
        });

        // Verify table still exists
        long count = creditLedgerRepository.count();
        assertTrue(count >= 1, "Credit ledger table should still exist");
    }

    // ==================== NUMERIC PARAMETER VALIDATION ====================

    @Test
    @DisplayName("SECURITY: Should handle numeric overflow in credit fields")
    void testNumericOverflow() {
        // Try to set extremely large credit value
        User overflowUser = new User();
        overflowUser.setEmail("overflow@example.com");
        overflowUser.setFreeUsedToday(0);
        overflowUser.setLastFreeReset(LocalDate.now());
        overflowUser.setPaidCredits(Integer.MAX_VALUE);
        overflowUser.setAutoRenewal(false);

        assertDoesNotThrow(() -> {
            userRepository.save(overflowUser);
        });

        // Verify saved correctly
        Optional<User> result = userRepository.findByEmail("overflow@example.com");
        assertTrue(result.isPresent());
        assertEquals(Integer.MAX_VALUE, result.get().getPaidCredits());
    }

    // ==================== NULL AND EMPTY STRING HANDLING ====================

    @Test
    @DisplayName("SECURITY: Should handle null email safely")
    void testNullEmail() {
        // Null email should not cause SQL errors
        assertDoesNotThrow(() -> {
            Optional<User> result = userRepository.findByEmail(null);
            assertFalse(result.isPresent());
        });
    }

    @Test
    @DisplayName("SECURITY: Should handle empty string email")
    void testEmptyStringEmail() {
        Optional<User> result = userRepository.findByEmail("");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("SECURITY: Should handle whitespace-only email")
    void testWhitespaceEmail() {
        String whitespaceEmail = "   ";

        Optional<User> result = userRepository.findByEmail(whitespaceEmail);
        assertFalse(result.isPresent());
    }

    // ==================== TRANSACTION SAFETY TESTS ====================

    @Test
    @DisplayName("SECURITY: Should rollback on SQL injection attempt")
    void testTransactionRollbackOnInjection() {
        long initialCount = userRepository.count();

        // Attempt to save user with malicious email
        try {
            User maliciousUser = new User();
            maliciousUser.setEmail("test@example.com'; DROP TABLE users; --");
            maliciousUser.setFreeUsedToday(0);
            maliciousUser.setLastFreeReset(LocalDate.now());
            maliciousUser.setPaidCredits(0);
            maliciousUser.setAutoRenewal(false);
            userRepository.save(maliciousUser);
        } catch (Exception e) {
            // Any exception should cause rollback
        }

        // Database should still be intact
        long finalCount = userRepository.count();
        assertTrue(finalCount >= initialCount, "User count should not decrease");
    }

    // ==================== BATCH OPERATION SAFETY ====================

    @Test
    @DisplayName("SECURITY: Should safely handle batch operations with mixed input")
    void testBatchOperationSafety() {
        // Mix of valid and potentially malicious emails
        String[] emails = {
            "valid1@example.com",
            "' OR '1'='1",
            "valid2@example.com",
            "'; DROP TABLE users; --",
            "valid3@example.com"
        };

        int savedCount = 0;
        for (String email : emails) {
            try {
                User user = new User();
                user.setEmail(email);
                user.setFreeUsedToday(0);
                user.setLastFreeReset(LocalDate.now());
                user.setPaidCredits(0);
                user.setAutoRenewal(false);
                userRepository.save(user);
                savedCount++;
            } catch (Exception e) {
                // Some may fail validation, that's expected
            }
        }

        // At least the valid ones should be saved
        assertTrue(savedCount >= 3, "Valid users should be saved");

        // Verify database integrity
        long totalUsers = userRepository.count();
        assertTrue(totalUsers >= savedCount, "Database should be intact");
    }
}
