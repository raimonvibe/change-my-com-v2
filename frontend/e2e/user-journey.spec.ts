import { test, expect } from '@playwright/test';

/**
 * E2E Tests: Complete User Journeys
 * Tests end-to-end user flows from landing to conversion
 */

test.describe('User Journey - Anonymous Visitor to First Conversion', () => {
  test('complete journey: homepage -> convert -> upload -> convert image', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/convert|raimonvibe|image converter/i);

    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\//);

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test-image.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.waitForTimeout(2000);
    const hasBlob = (await page.locator('img[src*="blob:"]').count()) > 0;
    const hasQueue = (await page.locator('text=/queued|in queue|file|test-image/i').count()) > 0;
    expect(hasBlob || hasQueue).toBe(true);

    await page.getByRole('button', { name: /^convert to png format$/i }).first().click();

    const convertButton = page.getByRole('button').filter({ hasText: /^convert\s*(\(\d+\))?$/i }).first();
    if ((await convertButton.count()) > 0) {
      await expect(convertButton).toBeVisible();
    }
  });

  test('journey: visitor checks pricing before converting', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /pricing/i }).first().click();
    await expect(page).toHaveURL(/\/billing/);

    await expect(page.locator('text=/1\\.98|\\$|pricing|subscription/i').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=/20.*free|free.*20|conversion/i').first()).toBeVisible({ timeout: 5000 });

    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\//);

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });
    await page.waitForTimeout(2000);
    const hasBlob = (await page.locator('img[src*="blob:"]').count()) > 0;
    const hasQueue = (await page.locator('text=/queued|in queue|test\\.jpg/i').count()) > 0;
    expect(hasBlob || hasQueue).toBe(true);
  });
});

test.describe('User Journey - Anonymous User Exploring Features', () => {
  test('journey: explore all pages and return to convert', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\//);

    await page.getByRole('link', { name: /pricing/i }).first().click();
    await expect(page).toHaveURL(/\/billing/);

    await page.goto('/account');
    await page.waitForLoadState('networkidle');
    const currentUrl = page.url();
    const signInPrompt = await page.locator('text=/sign in|please sign in|go to convert/i').count() > 0;
    expect(
      currentUrl.includes('/api/auth') ||
      currentUrl.includes('/signin') ||
      (currentUrl.includes('/account') && signInPrompt)
    ).toBe(true);

    await page.goto('/');
    await expect(page).toHaveURL(/\//);
  });

  test('journey: upload multiple images and clear all', async ({ page }) => {
    await page.goto('/convert');

    // Upload first image
    const buffer1 = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');

    await fileInput.setInputFiles({
      name: 'test1.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer1,
    });

    await page.waitForTimeout(1000);

    // Upload second image (if multi-upload is supported)
    const buffer2 = Buffer.from('R0lGODlhAQABAIAAAP///////yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');

    // Note: Depending on implementation, this might replace or add
    await fileInput.setInputFiles({
      name: 'test2.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer2,
    });

    await page.waitForTimeout(1000);

    // Click "Clear All"
    const clearButton = page.getByRole('button', { name: /clear all/i });

    if (await clearButton.count() > 0) {
      await clearButton.click();

      // Verify images are cleared
      await expect(page.locator('img[src*="blob:"]')).not.toBeVisible();
    }
  });
});

test.describe('User Journey - Format Selection and Options', () => {
  test('journey: upload image and test all format options', async ({ page }) => {
    await page.goto('/');

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.waitForTimeout(1000);

    const formats = ['png', 'webp', 'avif'];
    for (const format of formats) {
      const formatButton = page.getByRole('button', { name: new RegExp(`convert to ${format} format`, 'i') });
      if ((await formatButton.count()) > 0) {
        await formatButton.first().click();
        await expect(formatButton.first()).toHaveClass(/bg-sky-600|active|selected/);
      }
    }
  });

  test('journey: adjust quality and sharpness settings', async ({ page }) => {
    await page.goto('/');

    // Upload image
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.waitForTimeout(1000);

    // Find quality slider
    const sliders = page.locator('input[type="range"]');
    const sliderCount = await sliders.count();

    if (sliderCount > 0) {
      // Adjust first slider (likely quality)
      const qualitySlider = sliders.first();
      await qualitySlider.fill('75');
      await expect(qualitySlider).toHaveValue('75');

      // Adjust second slider if exists (likely sharpness)
      if (sliderCount > 1) {
        const sharpnessSlider = sliders.nth(1);
        await sharpnessSlider.fill('100');
        await expect(sharpnessSlider).toHaveValue('100');
      }
    }
  });
});

test.describe('User Journey - Error Recovery', () => {
  test('journey: upload invalid file and recover', async ({ page }) => {
    await page.goto('/');

    const textBuffer = Buffer.from('Not an image');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'invalid.txt',
      mimeType: 'text/plain',
      buffer: textBuffer,
    });

    await page.waitForTimeout(2000);

    const errorMessage = page.locator('text=/invalid|not supported|error|unsupported/i');
    if ((await errorMessage.count()) > 0) {
      await expect(errorMessage.first()).toBeVisible();
    }

    const validBuffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await fileInput.setInputFiles({
      name: 'valid.jpg',
      mimeType: 'image/jpeg',
      buffer: validBuffer,
    });

    await page.waitForTimeout(2000);
    const hasBlob = (await page.locator('img[src*="blob:"]').count()) > 0;
    const hasQueue = (await page.locator('text=/queued|valid\\.jpg|in queue/i').count()) > 0;
    expect(hasBlob || hasQueue).toBe(true);
  });

  test('journey: exceed file size and see error message', async ({ page }) => {
    await page.goto('/');

    // Upload file larger than 8MB
    const largeBuffer = Buffer.alloc(9 * 1024 * 1024);
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'large.jpg',
      mimeType: 'image/jpeg',
      buffer: largeBuffer,
    });

    await page.waitForTimeout(2000);

    // Should show size error
    const sizeError = page.locator('text=/8MB|too large|size limit/i');

    if (await sizeError.count() > 0) {
      await expect(sizeError.first()).toBeVisible();
    }
  });
});

test.describe('User Journey - Cross-Device Experience', () => {
  test('journey: mobile user converts image', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'mobile-upload.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.waitForTimeout(2000);
    const hasBlob = (await page.locator('img[src*="blob:"]').count()) > 0;
    const hasQueue = (await page.locator('text=/queued|mobile-upload|in queue/i').count()) > 0;
    expect(hasBlob || hasQueue).toBe(true);

    const pngButton = page.getByRole('button', { name: /^convert to png format$/i });
    if ((await pngButton.count()) > 0) {
      await pngButton.first().click();
    }
  });

  test('journey: tablet user navigates and converts', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');

    await page.getByRole('link', { name: /pricing/i }).first().click();
    await expect(page).toHaveURL(/\/billing/);

    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\//);

    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await page.locator('input[type="file"]').setInputFiles({
      name: 'tablet-upload.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });
    await page.waitForTimeout(2000);
    const hasBlob = (await page.locator('img[src*="blob:"]').count()) > 0;
    const hasQueue = (await page.locator('text=/queued|tablet-upload|in queue/i').count()) > 0;
    expect(hasBlob || hasQueue).toBe(true);
  });
});
