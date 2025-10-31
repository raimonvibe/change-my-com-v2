import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  // Generate a random nonce for this request
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');

  // Clone the request headers
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);

  // Create response with modified headers
  const response = NextResponse.next({
    request: {
      headers: requestHeaders,
    },
  });

  // Build the strict CSP with nonce
  const cspHeader = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic' https://accounts.google.com https://js.stripe.com`,
    `style-src 'self' 'nonce-${nonce}' 'unsafe-inline' https://accounts.google.com`,
    "img-src 'self' data: blob: https://www.change-my.com https://*.googleusercontent.com https://api.producthunt.com https://www.google-analytics.com",
    "font-src 'self' data: https://fonts.gstatic.com",
    "connect-src 'self' https://www.change-my.com https://imageconverter-backend.onrender.com https://accounts.google.com https://www.google-analytics.com https://api.stripe.com https://www.noupe.com",
    "frame-src https://accounts.google.com https://js.stripe.com https://hooks.stripe.com https://cards.producthunt.com https://www.producthunt.com",
    "media-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "upgrade-insecure-requests",
  ].join('; ');

  // Set security headers
  response.headers.set('Content-Security-Policy', cspHeader);
  response.headers.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');

  return response;
}

// Configure which routes the middleware runs on
export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico, icon.png, apple-icon.png (metadata files)
     */
    '/((?!api|_next/static|_next/image|favicon.ico|icon.png|apple-icon.png).*)',
  ],
};
