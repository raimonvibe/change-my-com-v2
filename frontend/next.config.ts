import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Static export for Render deployment
  output: 'export',

  // Images configuration for static export
  images: {
    unoptimized: true, // Required for static export
  },

  // Environment variables
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY,
  },

  // Trailing slash for better static hosting compatibility
  trailingSlash: true,

  // Disable experimental CSS optimization to prevent critters module errors
  experimental: {
    // optimizeCss: true, // Disabled - causes critters module errors
  }
};

export default nextConfig;
