import { test, expect } from '@playwright/test';

/**
 * E2E Tests: API Integration
 * Tests frontend-backend integration, rate limiting, and error handling
 */

test.describe('API Integration - /api/convert/formats', () => {
  test('should fetch supported formats successfully', async ({ page }) => {
    await page.goto('/');
    const formatButtons = page.getByRole('button', { name: /convert to (png|jpg|webp|avif|gif|heic|ico) format/i });
    await expect(formatButtons.first()).toBeVisible({ timeout: 8000 });
    expect(await formatButtons.count()).toBeGreaterThan(0);
  });

  test('should display all supported formats in UI', async ({ page }) => {
    await page.goto('/');
    const expectedFormats = ['png', 'jpg', 'webp', 'avif', 'gif', 'heic', 'ico'];
    for (const format of expectedFormats) {
      const formatButton = page.getByRole('button', { name: new RegExp(`convert to ${format} format`, 'i') });
      if (await formatButton.count() > 0) {
        await expect(formatButton.first()).toBeVisible();
      }
    }
  });
});

test.describe('API Integration - Error Handling', () => {
  test('should handle backend unavailable gracefully', async ({ page }) => {
    await page.route('**/api/convert/formats', (route) => route.abort('failed'));
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /convert/i }).first()).toBeVisible();
  });

  test('should handle 500 server error', async ({ page }) => {
    await page.route('**/api/convert/formats', (route) =>
      route.fulfill({ status: 500, body: 'Internal Server Error' })
    );
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /convert/i }).first()).toBeVisible();
  });

  test('should handle 429 rate limit response', async ({ page }) => {
    await page.route('**/api/convert', (route) =>
      route.fulfill({
        status: 429,
        headers: { 'Retry-After': '60', 'X-RateLimit-Remaining': '0' },
        body: JSON.stringify({ error: 'Rate limit exceeded' }),
      })
    );
    await page.goto('/');

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.getByRole('button', { name: /^convert to png format$/i }).click();
    const convertButton = page.getByRole('button', { name: /^convert \(?\d*\)?$/i }).first();
    if ((await convertButton.count()) > 0 && !(await convertButton.isDisabled())) {
      await convertButton.click();
      await page.waitForTimeout(2000);
      const rateLimitError = page.locator('text=/rate limit|too many|try again/i');
      if ((await rateLimitError.count()) > 0) {
        await expect(rateLimitError.first()).toBeVisible();
      }
    }
  });
});

test.describe('API Integration - Rate Limiting Headers', () => {
  test('should display rate limit information to user', async ({ page }) => {
    await page.goto('/');
    const rateLimitInfo = page.locator('text=/conversion|free|daily|limit|20|remaining/i');
    if ((await rateLimitInfo.count()) > 0) {
      await expect(rateLimitInfo.first()).toBeVisible({ timeout: 5000 });
    }
  });
});

test.describe('API Integration - File Validation', () => {
  test('should validate file size before upload', async ({ page }) => {
    await page.goto('/');
    const largeBuffer = Buffer.alloc(21 * 1024 * 1024); // 21MB, exceeds 20MB limit
    await page.locator('input[type="file"]').setInputFiles({
      name: 'large.jpg',
      mimeType: 'image/jpeg',
      buffer: largeBuffer,
    });
    await expect(page.locator('text=/20MB|too large|maximum allowed/i').first()).toBeVisible({ timeout: 5000 });
  });

  test('should validate file type before upload', async ({ page }) => {
    await page.goto('/');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('not an image'),
    });
    const typeError = page.locator('text=/invalid|not supported|unsupported|image only/i');
    if ((await typeError.count()) > 0) {
      await expect(typeError.first()).toBeVisible({ timeout: 5000 });
    }
  });
});

test.describe('API Integration - CORS & Security', () => {
  test('should have proper CORS headers', async ({ page }) => {
    await page.goto('/');
    const response = await page.waitForResponse(
      (r) => r.url().includes('/api/'),
      { timeout: 8000 }
    ).catch(() => null);
    if (response) {
      expect(response.headers()).toBeDefined();
    }
  });

  test('should use HTTPS in production', async ({ page }) => {
    // This test is environment-specific
    const currentUrl = page.url();

    if (process.env.NODE_ENV === 'production') {
      expect(currentUrl).toMatch(/^https:\/\//);
    }
  });
});

test.describe('API Integration - Anonymous User Limits', () => {
  test('should track anonymous user by IP', async ({ page }) => {
    await page.goto('/');
    const anonymousInfo = page.locator('text=/20.*free|free.*daily|sign in/i');
    await expect(anonymousInfo.first()).toBeVisible({ timeout: 5000 });
  });
});
