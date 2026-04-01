# Payment feature — environment variables

Paid checkout is **off** unless you set both flags below. The API always enforces the backend flag; the frontend flag only controls UI (subscribe buttons, copy).

## Enable Stripe checkout again

| Where | Variable | Value |
|--------|-----------|--------|
| **Backend** | `BILLING_PAYMENTS_ENABLED` | `true` |
| **Frontend** | `NEXT_PUBLIC_PAYMENTS_ENABLED` | `true` |

- **Frontend:** `NEXT_PUBLIC_*` is baked in at **build** time. Change the value and **redeploy / rebuild** the Next.js app.
- **Backend:** Maps to `app.billing.paymentsEnabled` in `application.yml`. Set in your host’s env (Render, Railway, etc.) and restart the API.

## Keep checkout disabled (default)

- Omit both variables, or set them to anything other than the string `true` (e.g. unset, `false`).

## Unchanged

Stripe secrets (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PUBLISHABLE_KEY`, etc.) are still required for webhooks and existing subscribers when you use Stripe; toggling checkout does not remove those.
