package com.raimonvibe.imageconverter.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService
 * Tests credit consumption logic, user creation, and subscription activation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditLedgerRepository creditLedgerRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private static final int FREE_DAILY_LIMIT = 20;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFreeUsedToday(0);
        testUser.setLastFreeReset(LocalDate.now());
        testUser.setPaidCredits(0);
        testUser.setAutoRenewal(false);
    }

    // ==================== CREDIT CONSUMPTION TESTS ====================

    @Test
    @DisplayName("Should consume paid credits first when available")
    void testConsumeOneConversion_UsePaidCreditsFirst() {
        // Given: User has paid credits
        testUser.setPaidCredits(100);
        testUser.setFreeUsedToday(0);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Paid credits should be decremented
        assertTrue(result);
        assertEquals(99, testUser.getPaidCredits());
        assertEquals(0, testUser.getFreeUsedToday());
        verify(userRepository, times(1)).save(testUser);

        // Verify ledger entry
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(creditLedgerRepository, times(1)).save(ledgerCaptor.capture());
        CreditLedger ledger = ledgerCaptor.getValue();
        assertEquals(-1, ledger.getDelta());
        assertEquals("conversion_paid", ledger.getReason());
    }

    @Test
    @DisplayName("Should consume free credits when no paid credits available")
    void testConsumeOneConversion_UseFreeCredits() {
        // Given: User has no paid credits
        testUser.setPaidCredits(0);
        testUser.setFreeUsedToday(5);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Free credits should be incremented
        assertTrue(result);
        assertEquals(0, testUser.getPaidCredits());
        assertEquals(6, testUser.getFreeUsedToday());
        verify(userRepository, times(1)).save(testUser);

        // Verify ledger entry
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(creditLedgerRepository, times(1)).save(ledgerCaptor.capture());
        CreditLedger ledger = ledgerCaptor.getValue();
        assertEquals(0, ledger.getDelta());
        assertEquals("conversion_free", ledger.getReason());
    }

    @Test
    @DisplayName("Should reject conversion when free limit reached and no paid credits")
    void testConsumeOneConversion_RejectWhenLimitReached() {
        // Given: User has no paid credits and reached free limit
        testUser.setPaidCredits(0);
        testUser.setFreeUsedToday(20); // Limit reached
        testUser.setLastFreeReset(LocalDate.now());

        // When: Try to consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Should be rejected
        assertFalse(result);
        assertEquals(0, testUser.getPaidCredits());
        assertEquals(20, testUser.getFreeUsedToday());
        verify(userRepository, never()).save(any());
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reset free credits at midnight (new day)")
    void testConsumeOneConversion_ResetFreeCreditsNewDay() {
        // Given: User's last reset was yesterday
        testUser.setPaidCredits(0);
        testUser.setFreeUsedToday(20); // Was at limit
        testUser.setLastFreeReset(LocalDate.now().minusDays(1)); // Yesterday
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion today
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Free credits should reset and allow conversion
        assertTrue(result);
        assertEquals(LocalDate.now(), testUser.getLastFreeReset());
        assertEquals(1, testUser.getFreeUsedToday()); // Reset to 0, then incremented to 1
    }

    @Test
    @DisplayName("Should consume paid credits even on new day (paid credits don't reset daily)")
    void testConsumeOneConversion_PaidCreditsNotResetDaily() {
        // Given: User has paid credits and new day
        testUser.setPaidCredits(500);
        testUser.setFreeUsedToday(20);
        testUser.setLastFreeReset(LocalDate.now().minusDays(1)); // Yesterday
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Paid credits should be used (not reset)
        assertTrue(result);
        assertEquals(499, testUser.getPaidCredits());
        assertEquals(0, testUser.getFreeUsedToday()); // Free reset to 0 but not used
    }

    // ==================== SUBSCRIPTION ACTIVATION TESTS ====================

    @Test
    @DisplayName("Should add credits to existing balance when activating subscription")
    void testActivateSubscription_StackCredits() {
        // Given: User already has 200 credits
        testUser.setPaidCredits(200);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Activate subscription with 1000 credits
        userService.activateSubscription(testUser, 1000);

        // Then: Credits should stack (not replace)
        assertEquals(1200, testUser.getPaidCredits());
        assertEquals(LocalDate.now(), testUser.getLastPaidReset());
        verify(userRepository, times(1)).save(testUser);

        // Verify ledger entry
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(creditLedgerRepository, times(1)).save(ledgerCaptor.capture());
        CreditLedger ledger = ledgerCaptor.getValue();
        assertEquals(1000, ledger.getDelta());
        assertEquals("subscription_activated", ledger.getReason());
    }

    @Test
    @DisplayName("Should add credits to zero balance")
    void testActivateSubscription_FromZero() {
        // Given: User has no credits
        testUser.setPaidCredits(0);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Activate subscription
        userService.activateSubscription(testUser, 1000);

        // Then: Credits should be added
        assertEquals(1000, testUser.getPaidCredits());
    }

    // ==================== USER CREATION TESTS ====================

    @Test
    @DisplayName("Should create new user with default values when not exists")
    void testEnsureUserByEmail_CreateNew() {
        // Given: User doesn't exist
        String email = "newuser@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // When: Ensure user
        User result = userService.ensureUserByEmail(email);

        // Then: New user should be created with defaults
        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(0, result.getFreeUsedToday());
        assertEquals(0, result.getPaidCredits());
        assertEquals(LocalDate.now(), result.getLastFreeReset());
        assertFalse(result.getAutoRenewal());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should return existing user when found")
    void testEnsureUserByEmail_ReturnExisting() {
        // Given: User exists
        String email = "existing@example.com";
        testUser.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));

        // When: Ensure user
        User result = userService.ensureUserByEmail(email);

        // Then: Existing user should be returned
        assertNotNull(result);
        assertEquals(email, result.getEmail());
        assertEquals(testUser.getId(), result.getId());
        verify(userRepository, never()).save(any()); // No save needed
    }

    @Test
    @DisplayName("Should fix null autoRenewal for existing users without subscription")
    void testEnsureUserByEmail_FixNullAutoRenewal_NoSubscription() {
        // Given: Existing user with null autoRenewal and no subscription
        testUser.setAutoRenewal(null);
        testUser.setStripeSubscriptionId(null);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When: Ensure user
        User result = userService.ensureUserByEmail(testUser.getEmail());

        // Then: autoRenewal should default to false
        assertNotNull(result.getAutoRenewal());
        assertFalse(result.getAutoRenewal());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should fix null autoRenewal for existing users with subscription")
    void testEnsureUserByEmail_FixNullAutoRenewal_WithSubscription() {
        // Given: Existing user with null autoRenewal but has subscription
        testUser.setAutoRenewal(null);
        testUser.setStripeSubscriptionId("sub_123456");
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When: Ensure user
        User result = userService.ensureUserByEmail(testUser.getEmail());

        // Then: autoRenewal should default to true
        assertNotNull(result.getAutoRenewal());
        assertTrue(result.getAutoRenewal());
        verify(userRepository, times(1)).save(testUser);
    }

    // ==================== ADD CREDITS TESTS ====================

    @Test
    @DisplayName("Should add credits with ledger entry")
    void testAddCredits() {
        // Given: User has 100 credits
        testUser.setPaidCredits(100);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Add 500 credits
        userService.addCredits(testUser, 500, "manual_adjustment");

        // Then: Credits should be added
        assertEquals(600, testUser.getPaidCredits());
        verify(userRepository, times(1)).save(testUser);

        // Verify ledger entry
        ArgumentCaptor<CreditLedger> ledgerCaptor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(creditLedgerRepository, times(1)).save(ledgerCaptor.capture());
        CreditLedger ledger = ledgerCaptor.getValue();
        assertEquals(500, ledger.getDelta());
        assertEquals("manual_adjustment", ledger.getReason());
        assertEquals(testUser, ledger.getUser());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Should handle consumption when paid credits is exactly 1")
    void testConsumeOneConversion_LastPaidCredit() {
        // Given: User has exactly 1 paid credit
        testUser.setPaidCredits(1);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Should succeed and go to 0
        assertTrue(result);
        assertEquals(0, testUser.getPaidCredits());
    }

    @Test
    @DisplayName("Should handle consumption when free used is exactly at limit - 1")
    void testConsumeOneConversion_LastFreeCredit() {
        // Given: User has used 19 free credits (1 remaining)
        testUser.setPaidCredits(0);
        testUser.setFreeUsedToday(19);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(creditLedgerRepository.save(any(CreditLedger.class))).thenReturn(null);

        // When: Consume one conversion
        boolean result = userService.consumeOneConversion(testUser, FREE_DAILY_LIMIT);

        // Then: Should succeed and reach limit
        assertTrue(result);
        assertEquals(20, testUser.getFreeUsedToday());
    }
}
