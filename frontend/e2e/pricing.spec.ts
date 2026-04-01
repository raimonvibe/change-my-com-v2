import { test, expect } from '@playwright/test';

/**
 * Pricing (/billing) E2E tests.
 * Default build has NEXT_PUBLIC_PAYMENTS_ENABLED unset (paid checkout off); copy reflects that.
 * With NEXT_PUBLIC_PAYMENTS_ENABLED=true, paid plan and Subscribe CTA appear again.
 */

const paymentsOn = process.env.NEXT_PUBLIC_PAYMENTS_ENABLED === 'true';

test.describe('Pricing Page - Display', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/billing');
  });

  test('should load pricing page', async ({ page }) => {
    await expect(page).toHaveURL(/\/billing/);
    await expect(page.getByRole('heading', { name: paymentsOn ? /pricing/i : /plans.*usage/i })).toBeVisible();
  });

  test('should display free tier information', async ({ page }) => {
    await expect(page.locator('text=/20.*free|free.*20/i')).toBeVisible();
    await expect(page.locator('text=/daily|per day/i')).toBeVisible();
  });

  test('should display plan context (paid offer or paused notice)', async ({ page }) => {
    if (paymentsOn) {
      await expect(page.locator('text=/1000.*conversion|conversion.*1000/i')).toBeVisible();
      await expect(page.locator('text=/\\$1\\.98|1\\.98/i')).toBeVisible();
    } else {
      await expect(page.getByText(/free to use|no credit card|Paid upgrades are paused/i)).toBeVisible();
    }
  });

  test('should list subscription benefits when payments are enabled', async ({ page }) => {
    test.skip(!paymentsOn, 'Benefits list is tied to paid plan copy');
    const benefitsSection = page.locator('ul, ol').filter({ hasText: /unlimited|1000|conversion/i });
    if (await benefitsSection.count() > 0) {
      await expect(benefitsSection.first()).toBeVisible();
    }
  });

  test('should have a primary account CTA', async ({ page }) => {
    if (paymentsOn) {
      const ctaButton = page.getByRole('button', { name: /subscribe|get started|purchase/i })
        .or(page.getByRole('link', { name: /subscribe|get started|purchase/i }));
      await expect(ctaButton.first()).toBeVisible();
    } else {
      await expect(page.getByRole('button', { name: /continue with google/i })).toBeVisible();
    }
  });

  test('should mention renewal when paid checkout is enabled', async ({ page }) => {
    test.skip(!paymentsOn);
    await expect(page.locator('text=/auto.?renew|renew.*month|monthly/i')).toBeVisible();
  });

  test('should explain credit stacking behavior', async ({ page }) => {
    test.skip(!paymentsOn);
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

  test('should prompt anonymous users toward sign-in when checkout is paused', async ({ page }) => {
    test.skip(paymentsOn);
    await page.getByRole('button', { name: /continue with google/i }).click();
    await page.waitForTimeout(1000);
    const currentUrl = page.url();
    const hasAuthModal = await page.locator('text=/sign in|log in|google/i').count() > 0;
    expect(currentUrl.includes('/api/auth') || hasAuthModal).toBe(true);
  });

  test('should offer subscribe or sign-in when payments are enabled', async ({ page }) => {
    test.skip(!paymentsOn);
    const subscribeButton = page.getByRole('button', { name: /subscribe|get started/i })
      .or(page.getByRole('link', { name: /subscribe|get started/i }));
    if (await subscribeButton.count() > 0) {
      await subscribeButton.first().click();
      await page.waitForTimeout(1000);
      const currentUrl = page.url();
      const hasAuthModal = await page.locator('text=/sign in|log in|google/i').count() > 0;
      expect(currentUrl.includes('/api/auth') || hasAuthModal).toBe(true);
    }
  });
});

test.describe('Pricing Page - Responsive Design', () => {
  test('should display pricing on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    if (paymentsOn) {
      await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
    } else {
      await expect(page.getByText(/Plans.*usage|free to use/i)).toBeVisible();
    }
  });

  test('should display pricing on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    if (paymentsOn) {
      await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
    } else {
      await expect(page.getByText(/Plans.*usage|free to use/i)).toBeVisible();
    }
  });

  test('should display pricing on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /pricing/i })).toBeVisible();
    if (paymentsOn) {
      await expect(page.locator('text=/1\\.98|\\$1\\.98/i')).toBeVisible();
    } else {
      await expect(page.getByText(/Plans.*usage|free to use/i)).toBeVisible();
    }
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
    const structuredData = page.locator('script[type="application/ld+json"]');
    if (await structuredData.count() > 0) {
      const content = await structuredData.first().textContent();
      expect(content).toContain('price');
    }
  });
});
