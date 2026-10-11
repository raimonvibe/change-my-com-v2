# Stripe webhook — API version

Read this before turning payments on (see `PAYMENTS-ENV.md`).

## The rule

The webhook endpoint in the Stripe dashboard must send events in the **same API version that `stripe-java` pins**.

| Backend library | Pinned Stripe API version |
|-----------------|---------------------------|
| `stripe-java` **34.0.0** (`backend/pom.xml`) | **`2026-09-30.endive`** |

## Why

`InvoicePaidHandler`, `SubscriptionUpdatedHandler` and `SubscriptionDeletedHandler` read the event with `event.getDataObjectDeserializer().getObject()`. When the event's API version differs from the library's pinned version, that returns empty: the handler logs an error (`SECURITY: ... missing ... object`) and **ignores the event**, so plans are not upgraded, renewed or cancelled.

## When creating the endpoint

1. Stripe Dashboard → **Developers → Webhooks → Add endpoint**.
2. URL: `https://<your-backend>/stripe/webhook`
3. **API version:** choose `2026-09-30.endive` (do not leave the account default if it is older).
4. Events: `checkout.session.completed`, `invoice.paid`, `customer.subscription.updated`, `customer.subscription.deleted`.
5. Copy the signing secret into `STRIPE_WEBHOOK_SECRET` on the backend.

## When `stripe-java` gets a new major version

Every major `stripe-java` release pins a newer API version (see "This release changes the pinned API version to …" in its changelog). After merging such an upgrade, update the webhook endpoint's API version in the dashboard to match, and update the table above.
