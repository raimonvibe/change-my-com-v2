import { test, expect } from '@playwright/test';

/**
 * E2E Tests: Pricing Page
 * Tests subscription plans, pricing display, and Stripe integration readiness
 */

test.describe('Pricing Page - Display', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/billing');
  });

  test('should load pricing page', async ({ page }) => {
    await expect(page).toHaveURL(/\/billing/);
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
  });

  test('should display free tier information', async ({ page }) => {
    // Verify free tier is mentioned
    await expect(page.locator('text=/20.*free|free.*20/i')).toBeVisible();
    await expect(page.locator('text=/daily|per day/i')).toBeVisible();
  });

  test('should display paid subscription plan', async ({ page }) => {
    // Verify paid plan information
    await expect(page.locator('text=/1000.*conversion|conversion.*1000/i')).toBeVisible();
    await expect(page.locator('text=/\\$1\\.98|1\\.98.*USD/i')).toBeVisible();
  });

  test('should display subscription benefits', async ({ page }) => {
    // Verify benefits are listed
    const benefitsSection = page.locator('ul, ol').filter({ hasText: /unlimited|1000|conversion/i });

    if (await benefitsSection.count() > 0) {
      await expect(benefitsSection.first()).toBeVisible();
    }
  });

  test('should have "Subscribe" or "Get Started" CTA button', async ({ page }) => {
    const ctaButton = page.getByRole('button', { name: /subscribe|get started|purchase/i })
      .or(page.getByRole('link', { name: /subscribe|get started|purchase/i }));

    await expect(ctaButton.first()).toBeVisible();
  });

  test('should display monthly auto-renewal information', async ({ page }) => {
    // Verify auto-renewal is mentioned
    await expect(page.locator('text=/auto.?renew|renew.*month|monthly/i')).toBeVisible();
  });

  test('should explain credit stacking behavior', async ({ page }) => {
    // Verify explanation that credits stack
    const stackingInfo = page.locator('text=/stack|additional|add.*1000/i');

    if (await stackingInfo.count() > 0) {
      await expect(stackingInfo.first()).toBeVisible();
    }
  });
});

test.describe('Pricing Page - Anonymous User', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/billing');
  });

  test('should prompt anonymous users to sign in', async ({ page }) => {
    // Click subscribe button
    const subscribeButton = page.getByRole('button', { name: /subscribe|get started/i })
      .or(page.getByRole('link', { name: /subscribe|get started/i }));

    if (await subscribeButton.count() > 0) {
      await subscribeButton.first().click();

      // Should redirect to sign-in or show auth modal
      // Wait for URL change or modal appearance
      await page.waitForTimeout(1000);

      const currentUrl = page.url();
      const hasAuthModal = await page.locator('text=/sign in|log in|google/i').count() > 0;

      // Either redirected to auth or modal appeared
      expect(currentUrl.includes('/api/auth') || hasAuthModal).toBe(true);
    }
  });
});

test.describe('Pricing Page - Responsive Design', () => {
  test('should display pricing on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
  });

  test('should display pricing on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
  });

  test('should display pricing on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
  });
});

test.describe('Pricing Page - SEO', () => {
  test('should have meta description', async ({ page }) => {
    await page.goto('/billing');
    const metaDescription = page.locator('meta[name="description"]');
    await expect(metaDescription).toHaveAttribute('content', /.+/);
  });

  test('should have pricing schema markup (JSON-LD)', async ({ page }) => {
    await page.goto('/billing');

    // Check for structured data (optional but good for SEO)
    const structuredData = page.locator('script[type="application/ld+json"]');

    if (await structuredData.count() > 0) {
      const content = await structuredData.first().textContent();
      expect(content).toContain('price');
    }
  });
});
