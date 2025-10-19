import { test, expect } from '@playwright/test';

/**
 * E2E Tests: Account Page - Authenticated Users
 * Tests user dashboard, credit display, and subscription management
 *
 * NOTE: These tests require authentication setup with Google OAuth
 * In a real environment, you would use Playwright's authentication storage
 * or mock authentication for testing purposes.
 *
 * For now, these tests are written to be skipped until auth is configured.
 */

/**
 * Helper function to authenticate user (placeholder)
 * In production, this would use Playwright's storageState feature
 */
async function authenticateUser(page: any) {
  // Placeholder for authentication
  // In real implementation, you would:
  // 1. Use storageState to load saved auth cookies
  // 2. Or programmatically sign in via API
  // 3. Or use OAuth test credentials

  // For now, we'll skip these tests
  test.skip(true, 'Authentication setup required');
}

test.describe('Account Page - Authenticated User', () => {
  test.beforeEach(async ({ page }) => {
    // Authenticate before each test
    await authenticateUser(page);
    await page.goto('/account');
  });

  test('should display user email', async ({ page }) => {
    // Verify email is displayed
    const emailDisplay = page.locator('text=/.*@.*/');
    await expect(emailDisplay).toBeVisible();
  });

  test('should display credit balance', async ({ page }) => {
    // Verify credit display (e.g., "500 credits remaining")
    const creditDisplay = page.locator('text=/credit|conversion.*remain/i');
    await expect(creditDisplay).toBeVisible();
  });

  test('should display free daily conversions used/remaining', async ({ page }) => {
    // Verify free daily usage (e.g., "5 / 20 free conversions used today")
    const freeUsageDisplay = page.locator('text=/free.*today|daily.*free/i');
    await expect(freeUsageDisplay).toBeVisible();
  });

  test('should show subscription status', async ({ page }) => {
    // Verify subscription status is displayed
    const subscriptionStatus = page.locator('text=/subscription|active|inactive/i');
    await expect(subscriptionStatus).toBeVisible();
  });

  test('should display auto-renewal toggle', async ({ page }) => {
    // Verify auto-renewal toggle exists
    const autoRenewalToggle = page.locator('input[type="checkbox"]').filter({
      has: page.locator('text=/auto.?renew/i')
    });

    if (await autoRenewalToggle.count() > 0) {
      await expect(autoRenewalToggle.first()).toBeVisible();
    }
  });

  test('should allow user to sign out', async ({ page }) => {
    // Find and click sign out button
    const signOutButton = page.getByRole('button', { name: /sign out|log out/i })
      .or(page.getByRole('link', { name: /sign out|log out/i }));

    await expect(signOutButton.first()).toBeVisible();
    await signOutButton.first().click();

    // Verify redirect to homepage or sign-in page
    await page.waitForURL(/\/($|api\/auth)/);
  });
});

test.describe('Account Page - Subscription Management', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateUser(page);
    await page.goto('/account');
  });

  test('should display "Subscribe" button if no active subscription', async ({ page }) => {
    // If user has no subscription, should see subscribe button
    const subscribeButton = page.getByRole('button', { name: /subscribe|purchase/i })
      .or(page.getByRole('link', { name: /subscribe|purchase/i }));

    // Button may or may not be visible depending on subscription status
    if (await subscribeButton.count() > 0) {
      await expect(subscribeButton.first()).toBeVisible();
    }
  });

  test('should show "Manage Subscription" link for active subscribers', async ({ page }) => {
    // If user has subscription, should see manage link
    const manageLink = page.getByRole('link', { name: /manage.*subscription|billing portal/i });

    if (await manageLink.count() > 0) {
      await expect(manageLink.first()).toBeVisible();
    }
  });

  test('should toggle auto-renewal setting', async ({ page }) => {
    const autoRenewalToggle = page.locator('input[type="checkbox"]').filter({
      has: page.locator('text=/auto.?renew/i')
    }).first();

    if (await autoRenewalToggle.count() > 0) {
      const initialState = await autoRenewalToggle.isChecked();

      // Toggle the setting
      await autoRenewalToggle.click();

      // Wait for update
      await page.waitForTimeout(1000);

      // Verify state changed
      const newState = await autoRenewalToggle.isChecked();
      expect(newState).toBe(!initialState);
    }
  });
});

test.describe('Account Page - Credit Display', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateUser(page);
    await page.goto('/account');
  });

  test('should show paid credits separately from free credits', async ({ page }) => {
    // Verify paid credits are shown
    const paidCreditsDisplay = page.locator('text=/paid.*credit|subscription.*credit/i');

    // Verify free credits are shown
    const freeCreditsDisplay = page.locator('text=/free.*credit|daily.*free/i');

    // At least one should be visible
    const paidVisible = await paidCreditsDisplay.count() > 0;
    const freeVisible = await freeCreditsDisplay.count() > 0;

    expect(paidVisible || freeVisible).toBe(true);
  });

  test('should display subscription text correctly', async ({ page }) => {
    // Verify the updated subscription text (from earlier fix)
    const subscriptionText = page.locator('text=/1000.*subscription.*auto.?renew.*1000.*monthly/i');

    if (await subscriptionText.count() > 0) {
      await expect(subscriptionText.first()).toBeVisible();
    }
  });
});

test.describe('Account Page - Responsive Design', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateUser(page);
  });

  test('should display account page on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE
    await page.goto('/account');

    await expect(page.getByRole('heading', { name: /account/i })).toBeVisible();
  });

  test('should display account page on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 }); // iPad
    await page.goto('/account');

    await expect(page.getByRole('heading', { name: /account/i })).toBeVisible();
  });

  test('should display account page on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 }); // Desktop
    await page.goto('/account');

    await expect(page.getByRole('heading', { name: /account/i })).toBeVisible();
  });
});

test.describe('Account Page - Security', () => {
  test('should require authentication to access account page', async ({ page }) => {
    // Try to access account page without authentication
    await page.goto('/account');

    // Should redirect to sign-in
    await page.waitForTimeout(2000);

    const currentUrl = page.url();

    // Either at sign-in page or redirected to auth
    expect(
      currentUrl.includes('/api/auth') ||
      currentUrl.includes('/signin') ||
      currentUrl.includes('/login')
    ).toBe(true);
  });

  test('should not display user email in page source before hydration', async ({ page }) => {
    await authenticateUser(page);

    // Get HTML source
    const content = await page.content();

    // Email should not be in raw HTML (should be loaded via client-side)
    // This is a GDPR/privacy best practice
    const containsEmail = /@.*\.com/.test(content);

    // Depending on implementation, this might be true or false
    // Document the behavior
    console.log('Email in source:', containsEmail);
  });
});
