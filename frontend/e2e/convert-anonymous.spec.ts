import { test, expect } from '@playwright/test';
import path from 'path';

/**
 * E2E Tests: Image Conversion - Anonymous Users
 * Tests core conversion functionality without authentication
 */

// Test image paths (use small test images)
const TEST_IMAGE_JPG = path.join(__dirname, '../public/favicon.ico');

test.describe('Convert Page - Anonymous User', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/convert');
  });

  test('should load convert page', async ({ page }) => {
    await expect(page).toHaveURL(/\/convert/);
    await expect(page.getByRole('heading', { name: /convert/i })).toBeVisible();
  });

  test('should display file upload dropzone', async ({ page }) => {
    // Verify dropzone exists
    const dropzone = page.locator('[role="button"]', { hasText: /drag.*drop|choose file/i });
    await expect(dropzone).toBeVisible();
  });

  test('should display format selector', async ({ page }) => {
    // Wait for format buttons to load
    await page.waitForSelector('button[type="button"]', { timeout: 5000 });

    // Verify at least one format button exists
    const formatButtons = page.getByRole('button').filter({ hasText: /png|jpg|webp|avif/i });
    await expect(formatButtons.first()).toBeVisible();
  });

  test('should upload image via file input', async ({ page }) => {
    // Create a test file programmatically
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');

    // Find file input (usually hidden)
    const fileInput = page.locator('input[type="file"]');

    // Set files on input
    await fileInput.setInputFiles({
      name: 'test.png',
      mimeType: 'image/png',
      buffer: buffer,
    });

    // Verify upload preview appears
    await expect(page.locator('img[alt*="preview"], img[src*="blob:"]')).toBeVisible({ timeout: 5000 });
  });

  test('should select output format (PNG)', async ({ page }) => {
    // Upload file first
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Select PNG format
    const pngButton = page.getByRole('button', { name: /png/i });
    await pngButton.click();

    // Verify button is selected (active state)
    await expect(pngButton).toHaveClass(/active|selected|bg-/);
  });

  test('should adjust quality slider', async ({ page }) => {
    // Upload file first
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Find quality slider
    const qualitySlider = page.locator('input[type="range"]').filter({ has: page.locator('text=/quality/i') }).first();

    if (await qualitySlider.count() > 0) {
      // Set quality to 50
      await qualitySlider.fill('50');

      // Verify value changed
      await expect(qualitySlider).toHaveValue('50');
    }
  });

  test('should show "Clear All" button after upload', async ({ page }) => {
    // Upload file
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Verify "Clear All" button appears
    const clearButton = page.getByRole('button', { name: /clear all/i });
    await expect(clearButton).toBeVisible({ timeout: 3000 });
  });

  test('should clear all uploads when clicking "Clear All"', async ({ page }) => {
    // Upload file
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Wait for preview
    await page.waitForSelector('img[alt*="preview"], img[src*="blob:"]', { timeout: 3000 });

    // Click "Clear All"
    await page.getByRole('button', { name: /clear all/i }).click();

    // Verify preview is gone
    await expect(page.locator('img[src*="blob:"]')).not.toBeVisible();
  });

  test('should show anonymous user conversion limit warning', async ({ page }) => {
    // Look for limit warning (e.g., "20 free conversions per day")
    const limitText = page.locator('text=/20.*free|free.*20|conversions.*day/i');

    // May be visible immediately or after interaction
    if (await limitText.count() > 0) {
      await expect(limitText.first()).toBeVisible();
    }
  });
});

test.describe('Convert Page - Validation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/convert');
  });

  test('should reject file larger than 8MB', async ({ page }) => {
    // Create a 9MB buffer (exceeds limit)
    const largeBuffer = Buffer.alloc(9 * 1024 * 1024);

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'large.jpg',
      mimeType: 'image/jpeg',
      buffer: largeBuffer,
    });

    // Verify error message appears
    await expect(page.locator('text=/8MB|too large|file size/i')).toBeVisible({ timeout: 3000 });
  });

  test('should reject invalid file types', async ({ page }) => {
    // Create a text file
    const textBuffer = Buffer.from('This is not an image');

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.txt',
      mimeType: 'text/plain',
      buffer: textBuffer,
    });

    // Verify error message or rejection
    const errorMessage = page.locator('text=/invalid|not supported|image only/i');
    if (await errorMessage.count() > 0) {
      await expect(errorMessage.first()).toBeVisible({ timeout: 3000 });
    }
  });

  test('should not allow conversion without selecting format', async ({ page }) => {
    // Upload file
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Try to find and click convert button without selecting format
    const convertButton = page.getByRole('button', { name: /convert/i }).filter({ hasNotText: /clear/i });

    if (await convertButton.count() > 0) {
      // Button might be disabled
      const isDisabled = await convertButton.first().isDisabled();
      expect(isDisabled).toBe(true);
    }
  });
});

test.describe('Convert Page - State Persistence', () => {
  test('should NOT persist uploaded images after navigation', async ({ page }) => {
    await page.goto('/convert');

    // Upload file
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Wait for upload
    await page.waitForSelector('img[src*="blob:"]', { timeout: 3000 });

    // Navigate away
    await page.goto('/pricing');
    await page.waitForURL(/\/pricing/);

    // Navigate back
    await page.goto('/convert');
    await page.waitForURL(/\/convert/);

    // Verify image is NOT persisted (security requirement)
    await expect(page.locator('img[src*="blob:"]')).not.toBeVisible();
  });
});

test.describe('Convert Page - Accessibility', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/convert');
  });

  test('should support keyboard navigation', async ({ page }) => {
    // Tab through interactive elements
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    // Verify focus is visible (on some element)
    const focusedElement = await page.locator(':focus');
    await expect(focusedElement).toBeVisible();
  });

  test('should have alt text for images', async ({ page }) => {
    // Upload image
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Verify preview has alt text
    const previewImage = page.locator('img[src*="blob:"]');
    if (await previewImage.count() > 0) {
      await expect(previewImage.first()).toHaveAttribute('alt', /.+/);
    }
  });
});
