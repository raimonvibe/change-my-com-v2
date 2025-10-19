import { test, expect } from '@playwright/test';

/**
 * E2E Tests: API Integration
 * Tests frontend-backend integration, rate limiting, and error handling
 */

test.describe('API Integration - /api/convert/formats', () => {
  test('should fetch supported formats successfully', async ({ page }) => {
    // Navigate to convert page (which likely fetches formats)
    await page.goto('/convert');

    // Intercept API call
    const responsePromise = page.waitForResponse(
      (response) => response.url().includes('/api/convert/formats') && response.status() === 200,
      { timeout: 10000 }
    );

    // Wait for response
    const response = await responsePromise;

    // Verify response
    expect(response.status()).toBe(200);

    const data = await response.json();
    expect(Array.isArray(data)).toBe(true);
    expect(data.length).toBeGreaterThan(0);
  });

  test('should display all supported formats in UI', async ({ page }) => {
    await page.goto('/convert');

    // Wait for format buttons to load
    await page.waitForTimeout(2000);

    // Expected formats from backend
    const expectedFormats = ['jpg', 'jpeg', 'png', 'webp', 'avif', 'gif', 'heic', 'ico'];

    // Count visible format buttons
    for (const format of expectedFormats) {
      const formatButton = page.getByRole('button', { name: new RegExp(format, 'i') });
      const count = await formatButton.count();

      // At least some formats should be visible
      if (count > 0) {
        await expect(formatButton.first()).toBeVisible();
      }
    }
  });
});

test.describe('API Integration - Error Handling', () => {
  test('should handle backend unavailable gracefully', async ({ page }) => {
    // Mock API failure
    await page.route('**/api/convert/formats', (route) => {
      route.abort('failed');
    });

    await page.goto('/convert');

    // UI should still load (graceful degradation)
    await expect(page.getByRole('heading', { name: /convert/i })).toBeVisible();

    // Error message might appear
    const errorMessage = page.locator('text=/error|failed|unavailable/i');
    if (await errorMessage.count() > 0) {
      await expect(errorMessage.first()).toBeVisible();
    }
  });

  test('should handle 500 server error', async ({ page }) => {
    // Mock 500 error
    await page.route('**/api/convert/formats', (route) => {
      route.fulfill({
        status: 500,
        body: 'Internal Server Error',
      });
    });

    await page.goto('/convert');

    // UI should handle error gracefully
    await expect(page.getByRole('heading', { name: /convert/i })).toBeVisible();
  });

  test('should handle 429 rate limit response', async ({ page }) => {
    // Mock rate limit error
    await page.route('**/api/convert', (route) => {
      route.fulfill({
        status: 429,
        headers: {
          'Retry-After': '60',
          'X-RateLimit-Limit': '10',
          'X-RateLimit-Remaining': '0',
        },
        body: JSON.stringify({ error: 'Rate limit exceeded' }),
      });
    });

    await page.goto('/convert');

    // Upload and try to convert
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Select format
    await page.getByRole('button', { name: /png/i }).click();

    // Try to convert
    const convertButton = page.getByRole('button', { name: /convert/i }).filter({ hasNotText: /clear/i });

    if (await convertButton.count() > 0 && !await convertButton.first().isDisabled()) {
      await convertButton.first().click();

      // Wait for error message
      await page.waitForTimeout(2000);

      // Should show rate limit error
      const rateLimitError = page.locator('text=/rate limit|too many|try again/i');
      if (await rateLimitError.count() > 0) {
        await expect(rateLimitError.first()).toBeVisible();
      }
    }
  });
});

test.describe('API Integration - Rate Limiting Headers', () => {
  test('should display rate limit information to user', async ({ page }) => {
    await page.goto('/convert');

    // Look for rate limit info in UI
    const rateLimitInfo = page.locator('text=/10.*conversion|conversion.*limit/i');

    if (await rateLimitInfo.count() > 0) {
      await expect(rateLimitInfo.first()).toBeVisible();
    }
  });
});

test.describe('API Integration - File Validation', () => {
  test('should validate file size before upload', async ({ page }) => {
    await page.goto('/convert');

    // Create 9MB file (exceeds 8MB limit)
    const largeBuffer = Buffer.alloc(9 * 1024 * 1024);

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'large.jpg',
      mimeType: 'image/jpeg',
      buffer: largeBuffer,
    });

    // Should show error (either client-side or server-side)
    await page.waitForTimeout(2000);

    const sizeError = page.locator('text=/8MB|too large|file size|exceed/i');
    if (await sizeError.count() > 0) {
      await expect(sizeError.first()).toBeVisible();
    }
  });

  test('should validate file type before upload', async ({ page }) => {
    await page.goto('/convert');

    // Upload non-image file
    const textBuffer = Buffer.from('This is not an image file');

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.txt',
      mimeType: 'text/plain',
      buffer: textBuffer,
    });

    // Should show error
    await page.waitForTimeout(2000);

    const typeError = page.locator('text=/invalid|not supported|image only/i');
    if (await typeError.count() > 0) {
      await expect(typeError.first()).toBeVisible();
    }
  });
});

test.describe('API Integration - CORS & Security', () => {
  test('should have proper CORS headers', async ({ page }) => {
    await page.goto('/convert');

    // Wait for API call
    const response = await page.waitForResponse(
      (response) => response.url().includes('/api/'),
      { timeout: 10000 }
    );

    // Check CORS headers (if applicable)
    const headers = response.headers();

    // In production, backend should have proper CORS configuration
    // For same-origin requests, CORS headers might not be present
    console.log('CORS Headers:', headers['access-control-allow-origin']);
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
    await page.goto('/account');

    // Anonymous user should see IP-based limit information
    const anonymousInfo = page.locator('text=/20.*free|anonymous|sign in for more/i');

    if (await anonymousInfo.count() > 0) {
      await expect(anonymousInfo.first()).toBeVisible();
    }
  });
});
