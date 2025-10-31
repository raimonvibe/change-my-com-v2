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
      {
        source: '/(.*)',
        has: [
          {
            type: 'header',
            key: 'x-forwarded-proto',
            value: 'http',
          },
        ],
        destination: 'https://www.change-my.com/$1',
        permanent: true,
      },
    ];
  },

  // Security headers are now handled by middleware.ts for nonce-based CSP
  // This provides better security by generating unique nonces per request
};

export default nextConfig;
