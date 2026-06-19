import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Images configuration
  images: {
    unoptimized: true,
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '**.googleusercontent.com',
      },
    ],
  },

  // Environment variables
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY,
    NEXT_PUBLIC_PAYMENTS_ENABLED: process.env.NEXT_PUBLIC_PAYMENTS_ENABLED,
  },

  // Disable experimental CSS optimization to prevent critters module errors
  experimental: {
    // optimizeCss: true, // Disabled - causes critters module errors
  },

  // Note: Next.js 16 removed the eslint config option
  // Use ESLint CLI directly or configure in package.json scripts

  // HTTPS Redirects and Security Headers
  async redirects() {
    return [
      // Redirect non-www to www (for all protocols)
      {
        source: '/:path*',
        has: [
          {
            type: 'host',
            value: 'change-my.com',
          },
        ],
        destination: 'https://www.change-my.com/:path*',
        permanent: true,
      },
      // Redirect HTTP to HTTPS (for www domain)
      {
        source: '/:path*',
        has: [
          {
            type: 'header',
            key: 'x-forwarded-proto',
            value: 'http',
          },
          {
            type: 'host',
            value: 'www.change-my.com',
          },
        ],
        destination: 'https://www.change-my.com/:path*',
        permanent: true,
      },
    ];
  },

  // Security headers are now handled by middleware.ts for nonce-based CSP
  // This provides better security by generating unique nonces per request
};

export default nextConfig;
