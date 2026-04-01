import { test, expect } from '@playwright/test';

/**
 * E2E Tests: Homepage & Navigation
 * Tests landing page, navigation, and basic UI elements
 */

test.describe('Homepage - Anonymous User', () => {
  test('should load homepage successfully', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(/convert|raimonvibe|image converter/i);

    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();
  });

  test('should display navigation menu with correct links', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const nav = page.getByRole('navigation');
    await expect(nav.getByRole('link', { name: /convert/i }).first()).toBeVisible({ timeout: 5000 });
    await expect(nav.getByRole('link', { name: /pricing/i }).first()).toBeVisible({ timeout: 5000 });
    const signIn = page.getByRole('button', { name: /sign in/i }).or(page.getByRole('link', { name: /sign in/i }));
    if ((await signIn.count()) > 0) {
      await expect(signIn.first()).toBeVisible();
    }
  });

  test('should navigate to Convert page', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\//);
  });

  test('should navigate to Pricing page', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /pricing/i }).first().click();
    await expect(page).toHaveURL(/\/billing/);
  });

  test('should be responsive on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  });

  test('should be responsive on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  });

  test('should be responsive on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  });
});

test.describe('Homepage - SEO & Performance', () => {
  test('should have meta description', async ({ page }) => {
    await page.goto('/');

    const metaDescription = page.locator('meta[name="description"]');
    await expect(metaDescription).toHaveAttribute('content', /.+/);
  });

  test('should have Open Graph tags', async ({ page }) => {
    await page.goto('/');

    const ogTitle = page.locator('meta[property="og:title"]');
    const ogDescription = page.locator('meta[property="og:description"]');

    await expect(ogTitle).toHaveAttribute('content', /.+/);
    await expect(ogDescription).toHaveAttribute('content', /.+/);
  });

  test('should load within performance budget (3s)', async ({ page }) => {
    const startTime = Date.now();
    await page.goto('/');
    const loadTime = Date.now() - startTime;

    expect(loadTime).toBeLessThan(3000);
  });
});
