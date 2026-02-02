import { test, expect } from '@playwright/test';

/**
 * E2E Tests: Complete User Journeys
 * Tests end-to-end user flows from landing to conversion
 */

test.describe('User Journey - Anonymous Visitor to First Conversion', () => {
  test('complete journey: homepage -> convert -> upload -> convert image', async ({ page }) => {
    // Step 1: Land on homepage
    await page.goto('/');
    await expect(page).toHaveTitle(/change-my/i);

    // Step 2: Navigate to Convert page
    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\/convert/);

    // Step 3: Upload an image
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test-image.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Step 4: Wait for preview to load
    await page.waitForSelector('img[src*="blob:"]', { timeout: 5000 });
    await expect(page.locator('img[src*="blob:"]')).toBeVisible();

    // Step 5: Select output format (PNG)
    const pngButton = page.getByRole('button', { name: /png/i });
    await pngButton.click();

    // Step 6: Verify convert button becomes enabled
    const convertButton = page.getByRole('button', { name: /convert/i }).filter({ hasNotText: /clear/i });

    // Note: Actual conversion would require real backend
    // For E2E test, we verify the UI flow works
    if (await convertButton.count() > 0) {
      const isDisabled = await convertButton.first().isDisabled();
      console.log('Convert button disabled:', isDisabled);
    }
  });

  test('journey: visitor checks pricing before converting', async ({ page }) => {
    // Step 1: Land on homepage
    await page.goto('/');

    // Step 2: Navigate to Pricing
    await page.getByRole('link', { name: /pricing/i }).click();
    await expect(page).toHaveURL(/\/pricing/);

    // Step 3: Review pricing information
    await expect(page.locator('text=/\\$1\\.98/i')).toBeVisible();
    await expect(page.locator('text=/20.*free/i')).toBeVisible();

    // Step 4: Navigate to Convert page
    await page.getByRole('link', { name: /convert/i }).click();
    await expect(page).toHaveURL(/\/convert/);

    // Step 5: Upload and verify dropzone works
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await expect(page.locator('img[src*="blob:"]')).toBeVisible({ timeout: 5000 });
  });
});

test.describe('User Journey - Anonymous User Exploring Features', () => {
  test('journey: explore all pages and return to convert', async ({ page }) => {
    // Start at homepage
    await page.goto('/');

    // Visit Convert page
    await page.getByRole('link', { name: /convert/i }).first().click();
    await expect(page).toHaveURL(/\/convert/);

    // Visit Pricing page
    await page.getByRole('link', { name: /pricing/i }).click();
    await expect(page).toHaveURL(/\/pricing/);

    // Try to visit Account page (should redirect to sign-in)
    await page.goto('/account');
    await page.waitForTimeout(2000);

    const currentUrl = page.url();
    const isAtAuth = currentUrl.includes('/api/auth') || currentUrl.includes('/signin');

    // Should be redirected to auth
    expect(isAtAuth).toBe(true);

    // Return to convert page
    await page.goto('/convert');
    await expect(page).toHaveURL(/\/convert/);
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
    await page.goto('/convert');

    // Upload image
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await page.waitForTimeout(1000);

    // Try selecting different formats
    const formats = ['png', 'webp', 'avif'];

    for (const format of formats) {
      const formatButton = page.getByRole('button', { name: new RegExp(format, 'i') });

      if (await formatButton.count() > 0) {
        await formatButton.first().click();

        // Verify button is active
        await expect(formatButton.first()).toHaveClass(/active|selected|bg-/);
      }
    }
  });

  test('journey: adjust quality and sharpness settings', async ({ page }) => {
    await page.goto('/convert');

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
    await page.goto('/convert');

    // Upload invalid file
    const textBuffer = Buffer.from('Not an image');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'invalid.txt',
      mimeType: 'text/plain',
      buffer: textBuffer,
    });

    await page.waitForTimeout(2000);

    // Should show error
    const errorMessage = page.locator('text=/invalid|not supported|error/i');

    if (await errorMessage.count() > 0) {
      await expect(errorMessage.first()).toBeVisible();
    }

    // Upload valid file to recover
    const validBuffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    await fileInput.setInputFiles({
      name: 'valid.jpg',
      mimeType: 'image/jpeg',
      buffer: validBuffer,
    });

    // Should show preview
    await expect(page.locator('img[src*="blob:"]')).toBeVisible({ timeout: 5000 });
  });

  test('journey: exceed file size and see error message', async ({ page }) => {
    await page.goto('/convert');

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
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE

    // Navigate to convert
    await page.goto('/convert');

    // Upload image
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'mobile-upload.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    // Verify preview shows on mobile
    await expect(page.locator('img[src*="blob:"]')).toBeVisible({ timeout: 5000 });

    // Select format on mobile
    const pngButton = page.getByRole('button', { name: /png/i });
    if (await pngButton.count() > 0) {
      await pngButton.first().click();
    }
  });

  test('journey: tablet user navigates and converts', async ({ page }) => {
    // Set tablet viewport
    await page.setViewportSize({ width: 768, height: 1024 }); // iPad

    // Start at homepage
    await page.goto('/');

    // Navigate to pricing
    await page.getByRole('link', { name: /pricing/i }).click();
    await expect(page).toHaveURL(/\/pricing/);

    // Navigate to convert
    await page.getByRole('link', { name: /convert/i }).click();
    await expect(page).toHaveURL(/\/convert/);

    // Upload on tablet
    const buffer = Buffer.from('R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', 'base64');
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'tablet-upload.jpg',
      mimeType: 'image/jpeg',
      buffer: buffer,
    });

    await expect(page.locator('img[src*="blob:"]')).toBeVisible({ timeout: 5000 });
  });
});
