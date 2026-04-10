/**
 * Gates Stripe checkout and subscribe CTAs. Webhook / existing subscriber flows stay on the API.
 *
 * Re-enable paid checkout:
 * - Frontend: NEXT_PUBLIC_PAYMENTS_ENABLED=true at build time (see next.config.ts).
 * - Backend: BILLING_PAYMENTS_ENABLED=true (see application.yml → app.billing.paymentsEnabled).
 */
export const PAYMENTS_ENABLED = process.env.NEXT_PUBLIC_PAYMENTS_ENABLED === 'true';
