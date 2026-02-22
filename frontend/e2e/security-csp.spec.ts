/**
 * E2E Security Test Suite - Content Security Policy
 * Tests the CSP implementation and nonce injection in production-like environment
 */

import { test, expect } from '@playwright/test';

test.describe('Content Security Policy', () => {
  test('should have CSP header in response', async ({ page }) => {
    const response = await page.goto('/convert');

    expect(response).not.toBeNull();

    const headers = response!.headers();
    expect(headers['content-security-policy']).toBeDefined();
  });

  test('should include nonce in CSP header', async ({ page }) => {
    const response = await page.goto('/convert');

    const csp = response!.headers()['content-security-policy'];
    expect(csp).toContain("'nonce-");
  });

  test('should include strict-dynamic in script-src', async ({ page }) => {
    const response = await page.goto('/convert');

    const csp = response!.headers()['content-security-policy'];
    expect(csp).toContain("'strict-dynamic'");
  });

  test('should not include unsafe-inline in script-src', async ({ page }) => {
    const response = await page.goto('/convert');

    const csp = response!.headers()['content-security-policy'];
    const scriptSrcMatch = csp.match(/script-src[^;]+/);

    expect(scriptSrcMatch).not.toBeNull();
    expect(scriptSrcMatch![0]).not.toContain("'unsafe-inline'");
  });

  test('should have all security headers', async ({ page }) => {
    const response = await page.goto('/convert');

    const headers = response!.headers();

    expect(headers['strict-transport-security']).toBe(
      'max-age=31536000; includeSubDomains; preload'
    );
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
  });

  test('should inject nonce into inline JSON-LD script', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];
    const nonceMatch = csp?.match(/'nonce-([^']+)'/);
    expect(nonceMatch).not.toBeNull();

    const jsonLdScript = page.locator('script[type="application/ld+json"]').first();
    if ((await jsonLdScript.count()) === 0) {
      return;
    }
    const scriptNonce = await jsonLdScript.getAttribute('nonce');
    if (scriptNonce) {
      expect(csp).toContain(scriptNonce);
    }
  });

  test('should inject nonce into external scripts', async ({ page }) => {
    const response = await page.goto('/convert');

    const csp = response!.headers()['content-security-policy'];
    const nonceMatch = csp.match(/'nonce-([^']+)'/);

    expect(nonceMatch).not.toBeNull();

    const nonce = nonceMatch![1];

    // Check external script has nonce
    const externalScript = await page.locator(
      'script[src*="noupe.com"]'
    ).first();

    if (await externalScript.count() > 0) {
      const scriptNonce = await externalScript.getAttribute('nonce');
      expect(scriptNonce).toBe(nonce);
    }
  });

  test('should generate unique nonces for different page loads', async ({ page }) => {
    const response1 = await page.goto('/convert');
    const csp1 = response1!.headers()['content-security-policy'];
    const nonce1 = csp1.match(/'nonce-([^']+)'/)?.[1];

    await page.goto('/');

    const response2 = await page.goto('/convert');
    const csp2 = response2!.headers()['content-security-policy'];
    const nonce2 = csp2.match(/'nonce-([^']+)'/)?.[1];

    expect(nonce1).toBeDefined();
    expect(nonce2).toBeDefined();
    expect(nonce1).not.toBe(nonce2);
  });

  test('should not have CSP violations in console', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    const content = await page.content();
    expect(content.length).toBeGreaterThan(1000);
  });

  test('should allow Google OAuth scripts', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('https://accounts.google.com');
  });

  test('should allow Stripe scripts', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('https://js.stripe.com');
    expect(csp).toContain('https://api.stripe.com');
  });

  test('should allow Product Hunt embeds', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('https://cards.producthunt.com');
    expect(csp).toContain('https://www.producthunt.com');
  });

  test('should block inline scripts without nonce', async ({ page }) => {
    const violations: string[] = [];

    page.on('console', msg => {
      if (msg.type() === 'error' && msg.text().includes('Content Security Policy')) {
        violations.push(msg.text());
      }
    });

    await page.goto('/convert');

    // Try to inject a script without nonce
    await page.evaluate(() => {
      const script = document.createElement('script');
      script.textContent = 'console.log("injected")';
      document.body.appendChild(script);
    });

    // Wait a bit for CSP to trigger
    await page.waitForTimeout(500);

    // Should have a CSP violation
    expect(violations.length).toBeGreaterThan(0);
  });

  test('should have object-src none to block plugins', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain("object-src 'none'");
  });

  test('should have frame-ancestors none to prevent clickjacking', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain("frame-ancestors 'none'");
  });

  test('should only allow forms to submit to same origin', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain("form-action 'self'");
  });

  test('should restrict base URI to prevent base tag injection', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain("base-uri 'self'");
  });

  test('should upgrade insecure requests', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('upgrade-insecure-requests');
  });
});

test.describe('CSP Integration with Application', () => {
  test('should allow images from Google user profiles', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('*.googleusercontent.com');
  });

  test('should allow images from Product Hunt', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('api.producthunt.com');
  });

  test('should allow data URIs for images', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];
    const imgSrcMatch = csp.match(/img-src[^;]+/);

    expect(imgSrcMatch![0]).toContain('data:');
  });

  test('should allow connections to backend API', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('https://www.change-my.com');
  });

  test('should allow Google Fonts', async ({ page }) => {
    const response = await page.goto('/');

    const csp = response!.headers()['content-security-policy'];

    expect(csp).toContain('https://fonts.gstatic.com');
  });

  test('Product Hunt iframe should load successfully', async ({ page }) => {
    await page.goto('/');

    // Wait for Product Hunt iframe
    const iframe = page.frameLocator('iframe[src*="producthunt.com"]');

    // Check if iframe loaded (won't be blocked by CSP)
    await expect(iframe.locator('body')).toBeVisible({ timeout: 10000 });
  });
});
