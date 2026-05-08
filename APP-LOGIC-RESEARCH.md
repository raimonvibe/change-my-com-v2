# App Logic Research – Change-My.com Image Converter

This document summarizes how the application works end-to-end: architecture, auth, conversions (free vs paid), billing, and security.

---

## 1. High-level architecture

- **Frontend**: Next.js (App Router), React, Tailwind, Zustand. Deployed on Vercel. No server-side session; auth is JWT (Google ID token) passed in `Authorization` for API calls.
- **Backend**: Spring Boot (Java 17), stateless REST API. Uses PostgreSQL (Flyway migrations), ImageMagick for conversion. Deployed on Render/Railway.
- **Auth**: Google OAuth via NextAuth; frontend gets `idToken`, sends `Authorization: Bearer <idToken>` to backend. Backend validates token with GoogleIdTokenAuthFilter and sets `Principal` (email). No login = no `Authorization` header = anonymous (IP-based).
- **Billing**: Stripe Checkout (subscription); paid credits are **only** granted via Stripe webhooks (signature-verified). No API can add paid credits.

---

## 2. Conversion model (free vs paid)

### 2.1 Limits (hardcoded in backend)

| Context | Limit | Where |
|--------|--------|--------|
| Free (anonymous) | 20/day per IP | `ConvertController.FREE_DAILY_LIMIT_ANONYMOUS`, `AnonymousUserController.FREE_DAILY_LIMIT` |
| Free (logged-in) | 20/day per user | `ConvertController.FREE_DAILY_LIMIT`, `UserController.FREE_DAILY_LIMIT` |
| Paid (subscriber) | 1000/month | Granted by webhook; consumed from `User.paidCredits` |
| Rate limit (anon) | 60 req/min general, 10 conversions/min | `RateLimitFilter` |
| Rate limit (auth) | 300 req/min general, 10 conversions/min | `RateLimitFilter` |

Note: README says “20/month” for anonymous; implementation is **20/day** (daily reset).

### 2.2 Who can convert without signing in

- **Anonymous conversions are allowed.**  
  Security: `POST /api/convert` and `POST /api/convert/gif` are `permitAll()` in `SecurityConfig`. No login required.
- **Identification**: When `Principal == null`, backend uses **client IP** for both:
  - **Rate limiting** (`RateLimitFilter`): key = `ip:<clientIp>` (and `:convert` for convert endpoints).
  - **Daily quota** (`AnonymousUserService`): one row per IP in `ip_conversion_tracker`, reset by date.
- **Client IP**: `X-Forwarded-For` (first hop) if present, else `request.getRemoteAddr()`. Same logic in `RateLimitFilter`, `ConvertController`, and `AnonymousUserController` so that behind a proxy each user gets their own limit.

### 2.3 Request flow for a conversion

1. **Frontend**  
   - Convert page: `token = session?.idToken`.  
   - If not signed in: `token` is undefined → fetch to `/api/convert` or `/api/convert/gif` **without** `Authorization` header.  
   - If signed in: sends `Authorization: Bearer <token>`.

2. **Backend filter order**  
   - `GoogleIdTokenAuthFilter` → `RateLimitFilter` → controller.  
   - If no Bearer token or invalid token: `Principal` stays null (filter does not block request).

3. **RateLimitFilter**  
   - For convert: bucket key = `ip:<clientIp>:convert` (anon) or `u:<email>:convert` (auth).  
   - Limit 10 conversions per minute per key.  
   - If over limit → **429 Too Many Requests** (no conversion).

4. **ConvertController**  
   - **If `principal != null`** (logged in):
     - Load/create user by email; call `userService.consumeOneConversion(user, FREE_DAILY_LIMIT)`.
     - Logic: use **paid credits first** (atomic decrement); if none, use **free daily** (atomic increment up to 20/day). Reset free counter by calendar day.
   - **If `principal == null`** (anonymous):
     - `clientIp = getClientIpAddress(request)` (X-Forwarded-For or remote addr).
     - `anonymousUserService.consumeOneConversion(clientIp, FREE_DAILY_LIMIT_ANONYMOUS)`.
     - One row per IP in `ip_conversion_tracker`; daily reset; max 20/day per IP.
   - If consumption fails (over limit) → **402 Payment Required** (body empty; frontend shows limit modal).
   - If consumption OK → run ImageService (ImageMagick), stream result back.

5. **After response**  
   - Frontend: on 401 → “Please sign in”; on 402 → limit modal; on 200 → blob download.  
   - If logged in, frontend may call `refetchUserData()` (GET `/api/user/me` with Bearer) to refresh credits in UI.

---

## 3. User and anonymous data model

### 3.1 Logged-in user (`app_user`)

- **Identity**: `email` (from Google JWT).
- **Free tier**: `free_used_today`, `last_free_reset` (date). Reset when date changes; max 20/day.
- **Paid**: `paid_credits`, `last_paid_reset`, `stripe_subscription_id`, `subscription_status`, `auto_renewal`.
- **Credits consumption** (in `UserService.consumeOneConversion`):
  1. If `paid_credits > 0`: atomic decrement (DB update), log in `CreditLedger`, return true.
  2. Else if `free_used_today < 20` and same day: atomic increment free usage, log, return true.
  3. Else return false → 402.

All credit changes use **atomic repository methods** (`atomicDecrementCredits`, `atomicIncrementFreeUsage`, etc.) and optional `CreditLedger` entries for auditing.

### 3.2 Anonymous (IP)

- **Table**: `ip_conversion_tracker` (ip_address, conversions_used_today, last_reset).
- **AnonymousUserService**:
  - `consumeOneConversion(ip, limit)`: if new day, reset count and persist; then if under limit, increment and save; return true/false.
  - `getRemainingConversions(ip, limit)`: used by `GET /api/anonymous/remaining` (no auth) for UI to show “X of 20 left today”.

---

## 4. Billing and Stripe

### 4.1 Checkout (subscription)

- **Endpoint**: `POST /api/billing/checkout` with `successUrl`, `cancelUrl` query params.  
- **Auth**: Required (`authenticated()`). Principal = email from JWT.
- **Logic** (BillingController): Validate redirect URLs (whitelist); find or create Stripe customer by email; create Stripe Checkout Session (subscription, metadata `subscription: monthly_1000`), return `{ id, url }`. Frontend redirects to Stripe.

### 4.2 Webhook (only source of paid credits)

- **Endpoint**: `POST /stripe/webhook`. Public in SecurityConfig but **must** send `Stripe-Signature`; body verified with `STRIPE_WEBHOOK_SECRET`.
- **Idempotency**: Event ID stored in `webhook_event` (unique). Duplicates return 200 “already_processed”.
- **Events handled**:
  - **checkout.session.completed**: From session get email + subscription ID; if metadata `subscription == "monthly_1000"`, ensure user, set `stripe_subscription_id`, `subscription_status=active`, `auto_renewal=true`, call `userService.activateSubscription(user, 1000)` (adds 1000 credits and sets `last_paid_reset`).
  - **invoice.paid**: Find user by subscription ID; if `auto_renewal` true, call `atomicAddCreditsForRenewal` (only adds 1000 if `last_paid_reset != today` to avoid double-add in same day).
  - **customer.subscription.updated**: Update `subscription_status`; if `cancel_at_period_end` set `auto_renewal=false`.
  - **customer.subscription.deleted**: Set `subscription_status=canceled`, `auto_renewal=false`; do **not** zero out `paid_credits` (user keeps remaining balance).

So: **paid credits exist only after Stripe webhook processing**. No other API can grant them.

### 4.3 User-facing billing

- **Account page**: Shows credits and subscription; can toggle “auto-renewal” (calls `POST /api/user/toggle-auto-renewal`). Backend uses Stripe API to cancel subscription at period end when turning off.
- **Pricing/billing page**: Starts checkout (redirect to Stripe).

---

## 5. Auth flow (frontend ↔ backend)

### 5.1 NextAuth (frontend)

- **Provider**: Google (scopes: openid, email, profile). Session strategy: JWT.
- **Callbacks**: On sign-in, store `access_token`, `id_token`, `refresh_token`, `expires`. Session callback exposes `session.idToken` (from JWT’s `id_token`). On expiry, refresh with Google OAuth token endpoint; on refresh failure set `session.error`.
- **Result**: When logged in, `useSession()` gives `session.idToken`; when not, `session` is null or unauthenticated.

### 5.2 Backend auth

- **GoogleIdTokenAuthFilter**: Reads `Authorization: Bearer <token>`. If present, verifies with Google (using `GOOGLE_CLIENT_ID`); on success sets `SecurityContext` with principal = email. On failure or missing token, does **not** block – request continues with `Principal == null`.
- **User creation**: First API call that needs a user (e.g. `/api/user/me`, or convert when logged in) triggers `userService.ensureUserByEmail(email)` (find or create `app_user`).

### 5.3 Frontend state

- **useAuthStore** (Zustand, persisted to localStorage): `email`, `authenticated`, `freeRemaining`, `paidCredits`, `subscriptionStatus`, `autoRenewal`. Used for UI (e.g. “X conversions left”).
- **useUserData**: When `status === 'authenticated'` and `session?.idToken`, fetches `GET /api/user/me` with Bearer and updates auth store. On logout (`unauthenticated`) calls `reset()`. `refetch()` used after conversion to refresh credits.

So: **anonymous users never send Bearer token**; backend sees `Principal == null` and uses IP for rate limit and daily free quota.

---

## 6. Security (relevant to logic)

- **Convert endpoints**: No auth required; quota enforced in controller (free by IP or user, paid by user credits).
- **CORS**: Explicit origins (e.g. localhost:3000, www.change-my.com); `Authorization` allowed so browser sends Bearer.
- **Rate limiting**: Per client (IP or user); convert endpoints have separate 10/min bucket. Uses same IP as conversion quota (X-Forwarded-For when present).
- **File validation**: `FileValidator` – size (20MB), extension whitelist, magic-byte checks. Used before conversion.
- **Stripe**: Webhook secret required; payload verified; idempotency by event ID. Redirect URLs for checkout whitelisted.

---

## 7. Important files reference

| Area | Files |
|------|--------|
| Conversion entrypoints | `ConvertController.java` (POST /api/convert, POST /api/convert/gif) |
| Free (anonymous) quota | `AnonymousUserService.java`, `AnonymousUserController.java` (GET /api/anonymous/remaining) |
| Logged-in user quota & credits | `UserService.java`, `UserController.java` (GET /api/user/me) |
| Credit DB updates | `UserRepository.java` (atomic methods), `IpConversionTrackerRepository` |
| Billing | `BillingController.java` (checkout), `StripeWebhookController.java` (webhook) |
| Auth | `GoogleIdTokenAuthFilter.java`, NextAuth `route.ts` (session + idToken) |
| Rate limit | `RateLimitFilter.java` (client IP = X-Forwarded-For or remote addr) |
| Security rules | `SecurityConfig.java` (permitAll for convert + anonymous remaining) |
| Frontend convert + auth | `page.tsx` (convert page), `useUserData.ts`, `useAuthStore.ts` |

---

## 8. Summary

- **Free conversions work without sign-in**: same `/api/convert` and `/api/convert/gif` endpoints; backend uses `Principal == null` and client IP (X-Forwarded-For or remote addr) for both rate limiting and 20/day quota in `ip_conversion_tracker`.
- **Paid conversions** require login; credits come only from Stripe webhooks (subscription / renewal); consumption prefers paid credits then free daily.
- **Auth** is JWT (Google ID token) in header; no cookie-based session. Anonymous = no header = IP-based behavior.
- **Consistency**: Rate limit and anonymous quota use the same client IP so that behind a proxy each visitor gets their own limit and free conversions work as intended.
