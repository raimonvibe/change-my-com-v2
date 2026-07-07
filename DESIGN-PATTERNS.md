# Design Patterns in change-my.com

This document maps the design patterns applied across the app, why each was
chosen, and where to find it. The refactor kept all external behavior (HTTP
responses, error messages, quotas, UI) identical — it reorganized *how* the
code is structured, not *what* it does.

## Backend (Spring Boot, `backend/`)

### Configuration constants — single source of truth

| Class | Replaces |
|---|---|
| `config/ConversionLimits` | `FREE_DAILY_LIMIT = 20` duplicated in 3 controllers with "must match" comments; the 20MB upload limit; the 8000px dimension cap |
| `image/ImageFormats` | Output whitelist duplicated between `ConvertController.ALLOWED_OUT` and `ImageService.supportedFormats()`; input whitelist duplicated between `ImageService.ALLOWED_FORMAT_HINTS` and `FileValidator.ALLOWED_EXT` |
| `billing/BillingPlan` | The `"subscription"` / `"monthly_1000"` metadata strings and the `1000` credit amount hardcoded in both `BillingController` (checkout) and the webhook handlers |

These are deliberately fixed in code (not `@ConfigurationProperties`) because
the original code documented a security decision: *"Fixed in code so config
cannot break the limit."* A bad environment variable can never raise a quota.

### Utility consolidation

- `common/ClientIpResolver` — one implementation of "resolve the real client
  IP behind a proxy" (X-Forwarded-For → X-Real-IP → socket address). Replaces
  five diverging private `getClientIp` copies, so rate limiting, anonymous
  quotas and audit logging always agree on client identity.
- `common/EmailMasker` — one email-masking implementation for
  privacy-compliant logs (was duplicated in `CostMonitor` and
  `SecurityAuditLogger`).

### Template Method — `image/MagickCommandExecutor`

ImageMagick can be installed as IM6 (`convert`/`identify`) or IM7 (`magick`),
sometimes only under `/usr/bin`. Every call site used to duplicate the
"try each command until one works" loop plus process setup, output capture and
timeout handling. `MagickCommandExecutor.run()` now owns the invariant steps;
callers only vary the argument list per attempt using the shared command
lists (`CONVERT_COMMANDS`, `IDENTIFY_COMMANDS`, `GIF_EXTRACT_COMMANDS`).

### Strategy — image conversion

- `image/FormatConversionStrategy` + `FormatConversionStrategies` (factory):
  format-specific behavior that used to be scattered `if ("png"...)` /
  `if ("ico"...)` branches inside `ImageService.convert()`:
  - `PngStrategy` rejects sources >4000px (PNG compression too slow).
  - `IcoStrategy` ignores the width option and emits fixed multi-size icon args.
  - A default no-op strategy covers all other formats.
- `image/SharpeningStrategy` (enum): the four sharpening tiers
  (SUBTLE 1–50, ADAPTIVE 51–100, PROFESSIONAL 101–150, MAXIMUM 151–200),
  formerly a 90-line if/else chain. `SharpeningStrategy.forLevel(n).apply(args, n)`.

`ImageService` remains the **Facade** the controllers talk to.

### Strategy + Registry — Stripe webhooks

`billing/StripeEventHandler` is implemented by four Spring components:

- `CheckoutCompletedHandler` (`checkout.session.completed`)
- `InvoicePaidHandler` (`invoice.paid`)
- `SubscriptionUpdatedHandler` (`customer.subscription.updated`)
- `SubscriptionDeletedHandler` (`customer.subscription.deleted`)

`StripeWebhookController` collects them into a map keyed by
`eventType()` and dispatches after signature verification and the atomic
idempotency insert. Supporting a new event type means adding one component —
no controller change. The controller itself is now a short pipeline:
validate request → verify signature → record for idempotency → dispatch.

The redundant `WebhookRateLimitInterceptor` (100 req/min) was removed:
`RateLimitFilter` already applies a stricter 10 req/min bucket to
`/stripe/webhook` and runs earlier in the chain, so the interceptor could
never trigger.

### Strategy — conversion quota

`user/ConversionQuotaPolicy` with two implementations:

- `AuthenticatedQuotaPolicy` — free daily conversions, then paid credits
  (only this path can touch `User.paidCredits`).
- `AnonymousQuotaPolicy` — free daily limit per client IP.

`ConversionQuotaService` selects the applicable policy. This replaces the
credit-check block that was copy-pasted between `ConvertController.convert()`
and `convertGif()`, and let the controller drop three constructor dependencies.

### Chain of Responsibility — upload validation

`security/FileValidator.validate()` now runs an ordered chain of
`ValidationStep`s over a shared `ValidationContext`:

1. presence and size
2. extension whitelist
3. magic-byte signature (+ suspicious-content scan)
4. MIME type (with the strictly-guarded HEIC exemption)

Magic-byte detection uses a registry of `FormatSignature` matchers instead of
a hardcoded if-chain, shared by both `validate()` and `detectFormat()`.

### Expanded exception handling

`config/GlobalExceptionHandler` gained an `IllegalArgumentException` fallback
(400 with a generic JSON error) for validation failures that escape
controller-local handling. Responses never leak exception details.

## Frontend (Next.js, `frontend/`)

### Single source of truth — `src/lib/conversionConfig.ts`

Format groups, size/dimension limits, accepted extensions, dropzone accept
map, resize presets and slider ranges — previously duplicated between
`page.tsx` and `lib/validation.ts` with drifting values. Everything imports
from this module; it mirrors the backend `ImageFormats`/`ConversionLimits`.

### Adapter — `src/lib/apiClient.ts`

`apiFetch()` wraps `fetch()` with base-URL resolution, optional Bearer auth,
per-attempt timeout (AbortController) and a single retry on transient network
errors. It replaces raw `fetch` calls previously scattered across
`page.tsx`, `useUserData`, the account page and the billing page — and the
configured-but-unused axios instance, which was removed along with the axios
dependency. Non-idempotent calls (auto-renewal toggle, checkout) opt out of
retries with `retries: 0`.

### Strategy + Template Method — `src/lib/conversionApi.ts`

`runConversion()` is the shared request pipeline (build form → POST with
timeout/retry → map status codes → blob URL). The raster/GIF differences live
in two `ConversionStrategy` objects (`rasterConversionStrategy`,
`gifConversionStrategy`): endpoint, form fields, timeouts, fallback error
copy, progress estimation, and whether a credit-limit response stops the rest
of the queue. This replaces the ~90% duplicated logic between the old
`convertGif()` and the inline raster loop.

### Custom hooks (Facade)

- `src/hooks/useConversionQueue.ts` — owns job list state, blob URL
  lifecycle, simulated progress and the start loop that runs each queued job
  through the strategy pipeline.
- `src/hooks/useImageValidation.ts` — client-side extension and
  browser-dimension checks (HEIC skips, backend validates those).
- `src/hooks/useUserData.ts` — (pre-existing) session ↔ Zustand store sync,
  now using `apiFetch`.

### Container / Presentational split

`src/app/page.tsx` shrank from ~1,335 to ~400 lines and is now a container:
state, wiring, dropzone. The presentational pieces live in
`src/components/convert/`:

- `FormatPicker` (format grid + PNG/auto-resize advisories)
- `GifFormatPicker` (multi-format ZIP selection)
- `ImageSettings` (quality/sharpness/resize controls + warnings)
- `JobQueue` (status banner + per-job rows)
- `LimitReachedModal`, `PreviewModal`

### Pre-existing patterns kept

- **Observer** — Zustand `useAuthStore` (with persist middleware).
- **Provider** — `app/providers.tsx` (`SessionProvider`).
- **Error Boundary** — `components/ErrorBoundary.tsx`.

## Verification

- Backend: `./mvnw test` — 260 tests, all passing.
- Frontend: `npm test` — 166 tests across 14 suites, all passing.
- Frontend: `npm run build` — production build with TypeScript checks, clean.
