# Image Converter – Setup Guide

This project contains:
- backend/ — Spring Boot + PostgreSQL + Stripe + Google Sign-In
- frontend/ — Next.js (TypeScript) + Tailwind + Lucide + NextAuth (Google) + Zustand
- docker-compose.yml — Local PostgreSQL

Prerequisites:
- Java 17+
- Node.js 20+
- Docker + Docker Compose
- ImageMagick (for server-side conversion)
  - Ubuntu: sudo apt-get update && sudo apt-get install -y imagemagick

Environment variables you need:

**📚 Complete environment variable guide:** [SECRETS-MANAGEMENT.md](SECRETS-MANAGEMENT.md)

Quick reference for local development:
- See SECRETS-MANAGEMENT.md for complete setup instructions
- Backend: Environment variables (DATABASE_URL, GOOGLE_CLIENT_ID, STRIPE_SECRET_KEY, etc.)
- Frontend: .env.local file (NEXT_PUBLIC_API_URL, NEXTAUTH_SECRET, etc.)

How to run locally:

1) Start PostgreSQL
   docker compose up -d

2) Start backend
   cd backend
   ./mvnw spring-boot:run

   The backend will create/update the schema and run on http://localhost:8080

3) Start frontend
   cd frontend
   npm install
   npm run dev

   Open http://localhost:3000

Google Sign-In:
- Configure an OAuth 2.0 Client in Google Cloud Console
- Authorized redirect URI (NextAuth app router): http://localhost:3000/api/auth/callback/google
- Put GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET into frontend .env.local
- Put the same GOOGLE_CLIENT_ID into backend env (Spring validates ID token audience)

Stripe (Test mode):
- Set STRIPE_SECRET_KEY and STRIPE_PUBLISHABLE_KEY in backend env
- Create a Price for $1 that represents a pack of 5 conversions (handled as quantity credits server-side)
- Set STRIPE_WEBHOOK_SECRET and run stripe listen --forward-to http://localhost:8080/api/stripe/webhook (optional for local)
- The backend’s BillingController creates a Checkout Session; on successful payment, credits are added via webhook.

Image conversion:
- Supported formats include: jpg, jpeg, png, webp, avif, heic, tiff, bmp, gif, svg
- Limits: 5 free conversions per user per day; $1 buys 5 extra conversions.
- Conversion endpoint: POST /api/convert (multipart/form-data) with fields: file, to, quality

Notes:
- Do not commit secrets.
- If running behind a different hostname/port, update NEXT_PUBLIC_API_URL and allowed origins in backend application.yml (app.auth.allowedOrigins).
- Ensure ImageMagick is installed on the server where the backend runs.
