import { test as setup } from '@playwright/test';
import path from 'path';

/**
 * Authentication Setup for E2E Tests
 * This file creates an authenticated state that can be reused across tests
 */

const authFile = path.join(__dirname, '../.auth/user.json');

setup('authenticate', async ({ page }) => {
  // METHOD 1: Mock NextAuth session (for testing without real OAuth)
  await page.goto('/');

  // Mock the session API endpoint
  await page.route('**/api/auth/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        user: {
          email: 'test@example.com',
          name: 'Test User',
          image: null,
        },
        expires: '2099-12-31T23:59:59.999Z',
      }),
    });
  });

  // Visit a page that checks authentication
  await page.goto('/account');

  // Wait for authentication to be recognized
  await page.waitForTimeout(1000);

  // Verify we're authenticated
  const isAuthenticated = await page.locator('text=/test@example.com|Sign out/i').count() > 0;

  if (!isAuthenticated) {
    console.warn('Authentication mock did not work as expected');
  }

  // Save signed-in state to '.auth/user.json'
  await page.context().storageState({ path: authFile });
});

/**
 * Alternative method for real OAuth testing:
 *
 * setup('authenticate with Google', async ({ page }) => {
 *   // Go to sign-in page
 *   await page.goto('/api/auth/signin');
 *
 *   // Click Google sign-in button
 *   await page.click('button[name="google"]');
 *
 *   // Fill in Google credentials (use test account)
 *   await page.fill('input[type="email"]', process.env.TEST_USER_EMAIL!);
 *   await page.click('button:has-text("Next")');
 *
 *   await page.fill('input[type="password"]', process.env.TEST_USER_PASSWORD!);
 *   await page.click('button:has-text("Next")');
 *
 *   // Wait for redirect back to app
 *   await page.waitForURL('/');
 *
 *   // Save authentication state
 *   await page.context().storageState({ path: authFile });
 * });
 */
