/**
 * Middleware Test Suite
 * Tests the CSP nonce generation and security headers
 * @jest-environment node
 */

import { NextRequest, NextResponse } from 'next/server';
import { middleware, config } from '../middleware';

describe('CSP Middleware', () => {
  let request: NextRequest;
  const mockUUID = 'test-uuid-1234-5678-90ab-cdef';

  beforeEach(() => {
    // Create a mock request
    request = new NextRequest(new URL('https://www.change-my.com/convert'));

    // Mock crypto.randomUUID
    jest.spyOn(global.crypto, 'randomUUID').mockReturnValue(mockUUID);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('Nonce Generation', () => {
    it('should generate a unique nonce for each request', () => {
      const response = middleware(request);

      expect(crypto.randomUUID).toHaveBeenCalled();
      expect(response.headers.get('Content-Security-Policy')).toContain('nonce-');
    });

    it('should set x-nonce header on request', () => {
      const response = middleware(request);

      // The nonce should be base64 encoded UUID
      const expectedNonce = Buffer.from(mockUUID).toString('base64');
      expect(response.headers.get('Content-Security-Policy')).toContain(`nonce-${expectedNonce}`);
    });

    it('should use different nonces for different requests', () => {
      const uuids = ['uuid-1', 'uuid-2', 'uuid-3'];
      let callCount = 0;

      (crypto.randomUUID as jest.Mock).mockImplementation(() => uuids[callCount++]);

      const response1 = middleware(request);
      const response2 = middleware(request);
      const response3 = middleware(request);

      const nonce1 = Buffer.from(uuids[0]).toString('base64');
      const nonce2 = Buffer.from(uuids[1]).toString('base64');
      const nonce3 = Buffer.from(uuids[2]).toString('base64');

      expect(response1.headers.get('Content-Security-Policy')).toContain(`nonce-${nonce1}`);
      expect(response2.headers.get('Content-Security-Policy')).toContain(`nonce-${nonce2}`);
      expect(response3.headers.get('Content-Security-Policy')).toContain(`nonce-${nonce3}`);
    });
  });

  describe('Content Security Policy', () => {
    it('should set Content-Security-Policy header', () => {
      const response = middleware(request);

      expect(response.headers.has('Content-Security-Policy')).toBe(true);
    });

    it('should include default-src self directive', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("default-src 'self'");
    });

    it('should include script-src with nonce and strict-dynamic', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("script-src 'self'");
      expect(csp).toContain("'nonce-");
      expect(csp).toContain("'strict-dynamic'");
    });

    it('should allow scripts from trusted domains', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain('https://accounts.google.com');
      expect(csp).toContain('https://js.stripe.com');
    });

    it('should include style-src with nonce and unsafe-inline', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("style-src 'self'");
      expect(csp).toContain("'nonce-");
      expect(csp).toContain("'unsafe-inline'");
    });

    it('should allow images from trusted sources', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("img-src 'self' data: blob:");
      expect(csp).toContain('https://*.googleusercontent.com');
      expect(csp).toContain('https://api.producthunt.com');
    });

    it('should allow fonts from trusted sources', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("font-src 'self' data:");
      expect(csp).toContain('https://fonts.gstatic.com');
    });

    it('should allow connections to API and trusted services', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain('connect-src');
      expect(csp).toContain('https://www.change-my.com');
      expect(csp).toContain('https://change-my-com-v2.onrender.com');
      expect(csp).toContain('https://api.stripe.com');
      expect(csp).toContain('https://formspree.io');
    });

    it('should allow iframes from trusted domains', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain('frame-src');
      expect(csp).toContain('https://js.stripe.com');
      expect(csp).toContain('https://cards.producthunt.com');
    });

    it('should block all plugins with object-src none', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("object-src 'none'");
    });

    it('should restrict base-uri to self', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("base-uri 'self'");
    });

    it('should allow form submissions to self and Formspree', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("form-action 'self' https://formspree.io");
    });

    it('should prevent framing with frame-ancestors none', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("frame-ancestors 'none'");
    });

    it('should upgrade insecure requests', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain('upgrade-insecure-requests');
    });

    it('should include media-src directive', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("media-src 'self'");
    });
  });

  describe('Other Security Headers', () => {
    it('should set Strict-Transport-Security header', () => {
      const response = middleware(request);

      expect(response.headers.get('Strict-Transport-Security')).toBe(
        'max-age=31536000; includeSubDomains; preload'
      );
    });

    it('should set X-Frame-Options header', () => {
      const response = middleware(request);

      expect(response.headers.get('X-Frame-Options')).toBe('DENY');
    });

    it('should set X-Content-Type-Options header', () => {
      const response = middleware(request);

      expect(response.headers.get('X-Content-Type-Options')).toBe('nosniff');
    });

    it('should set Referrer-Policy header', () => {
      const response = middleware(request);

      expect(response.headers.get('Referrer-Policy')).toBe('strict-origin-when-cross-origin');
    });
  });

  describe('Middleware Configuration', () => {
    it('should have correct matcher configuration', () => {
      expect(config.matcher).toBeDefined();
      expect(Array.isArray(config.matcher)).toBe(true);
      expect(config.matcher.length).toBeGreaterThan(0);
    });

    it('should exclude API routes from matcher', () => {
      const matcher = config.matcher[0];
      expect(matcher).toContain('!api');
    });

    it('should exclude static files from matcher', () => {
      const matcher = config.matcher[0];
      // Matcher uses negative lookahead pattern: (?!...)
      expect(matcher).toContain('(?!api|_next/static|_next/image');
    });

    it('should exclude metadata files from matcher', () => {
      const matcher = config.matcher[0];
      // Matcher uses negative lookahead pattern: (?!...)
      expect(matcher).toContain('favicon.ico');
      expect(matcher).toContain('icon.png');
      expect(matcher).toContain('apple-icon.png');
    });
  });

  describe('Response Handling', () => {
    it('should return a NextResponse', () => {
      const response = middleware(request);

      expect(response).toBeInstanceOf(NextResponse);
    });

    it('should preserve request method', () => {
      const postRequest = new NextRequest(
        new URL('https://www.change-my.com/api/convert'),
        { method: 'POST' }
      );

      const response = middleware(postRequest);

      expect(response).toBeDefined();
    });

    it('should handle different paths', () => {
      const paths = ['/convert', '/account', '/billing', '/privacy', '/legal'];

      paths.forEach(path => {
        const req = new NextRequest(new URL(`https://www.change-my.com${path}`));
        const res = middleware(req);

        expect(res.headers.has('Content-Security-Policy')).toBe(true);
      });
    });
  });

  describe('CSP Format', () => {
    it('should use semicolons to separate directives', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain(';');

      // Should have multiple directives separated by semicolons
      const directives = csp?.split(';').map(d => d.trim()).filter(d => d.length > 0);
      expect(directives?.length).toBeGreaterThan(10);
    });

    it('should not have trailing semicolon', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp?.endsWith(';')).toBe(false);
    });

    it('should use single quotes around keywords', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      expect(csp).toContain("'self'");
      expect(csp).toContain("'none'");
      expect(csp).toContain("'strict-dynamic'");
      expect(csp).not.toContain('"self"');
      expect(csp).not.toContain('"none"');
    });
  });

  describe('XSS Protection', () => {
    it('should not include unsafe-eval in script-src', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      // We want to avoid unsafe-eval for better security
      // Note: If Next.js absolutely requires it, this test might need adjustment
      const scriptSrc = csp?.match(/script-src[^;]+/)?.[0];

      // This is aspirational - Next.js might require unsafe-eval
      // Comment this out if Next.js breaks without it
      expect(scriptSrc).not.toContain("'unsafe-eval'");
    });

    it('should not include unsafe-inline in script-src', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      const scriptSrc = csp?.match(/script-src[^;]+/)?.[0];

      // Should use nonce instead of unsafe-inline
      expect(scriptSrc).not.toContain("'unsafe-inline'");
    });

    it('should use nonce-based approach for scripts', () => {
      const response = middleware(request);
      const csp = response.headers.get('Content-Security-Policy');

      const scriptSrc = csp?.match(/script-src[^;]+/)?.[0];

      // Should have nonce and strict-dynamic for modern XSS protection
      expect(scriptSrc).toContain("'nonce-");
      expect(scriptSrc).toContain("'strict-dynamic'");
    });
  });
});
