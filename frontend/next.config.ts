import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Vercel optimizations
  images: {
    domains: ['localhost', 'your-backend.onrender.com'],
    formats: ['image/webp', 'image/avif'],
  },
  
  // Environment variables
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
    NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY: process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY,
  },
  
  // Disable experimental CSS optimization to prevent critters module errors
  experimental: {
    // optimizeCss: true, // Disabled - causes critters module errors
  }
};

export default nextConfig;
