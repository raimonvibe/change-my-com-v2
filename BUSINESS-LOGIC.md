# Business Logic: Conversion Credits & Testing

## 1. Conversion credit rules

### 1.1 Anonymous (not signed in)

- **Limit:** 20 free conversions **per day** per IP.
- **Tracking:** Backend stores one row per IP in `ip_conversion_tracker`; resets at midnight (calendar day).
- **No login required:** User can convert without an account. When they hit 20 in a day, they see "Daily limit reached" and can sign in or subscribe.
- **Subscribers testing anonymous:** If you are a subscriber and want to test the anonymous flow, use an **incognito/private window** and do **not** sign in. Your IP gets the same 20/day as any other visitor. Your subscription is only used when you are **signed in**.

### 1.2 Logged in (no subscription)

- **Limit:** 20 free conversions **per day** per user.
- **Tracking:** `app_user.free_used_today` and `last_free_reset`; resets at midnight.
- **Paid credits:** 0. No subscription.

### 1.3 Logged in (subscriber)

- **Order of use:** **Paid credits first**, then free daily.
  - Each conversion consumes **1 paid credit** if `paid_credits > 0`.
  - When `paid_credits` reaches 0, the user then gets the same **20 free conversions per day** as a non-subscriber (same `free_used_today` / `last_free_reset`).
- **Subscribers do not get “extra” 20 free on top of paid in the same sense:** the 20 free/day apply only after paid credits are used for that conversion. So effectively: use paid first; when paid = 0, use 20 free/day.
- **Paid credits:** Granted **only** by Stripe webhooks (checkout completed, invoice.paid). No API or admin action can add paid credits.

### 1.4 Summary table

| State              | Paid credits used first? | Free daily (20/day)      |
|--------------------|--------------------------|---------------------------|
| Anonymous (by IP)  | N/A                      | Yes                       |
| Logged in, no sub  | N/A (0 paid)             | Yes                       |
| Logged in, sub     | Yes                      | Yes, after paid = 0       |

---

## 2. How to test

### 2.1 Test anonymous (not logged in)

1. Open the app in an **incognito/private window** (or a different browser where you are not signed in).
2. Go to the **Convert** page. Do **not** sign in.
3. You should see a line like: **"X of 20 free conversions left today"** (when the frontend fetches `/api/anonymous/remaining`).
4. Upload an image and convert. Each conversion decreases the anonymous count for that IP.
5. After 20 conversions in the same day (same IP), the next conversion returns **402** and the limit modal appears.
6. This proves: anonymous users get 20 free/day by IP; your **live subscription is not used** when you are not logged in.

### 2.2 Test subscriber (logged in, paid first)

1. **Sign in** with the Google account that has the subscription.
2. Go to the **Convert** page. The UI should show **paid credits** (and possibly “X free left today” when paid is 0).
3. Convert images. Each conversion should **use 1 paid credit** until paid credits = 0.
4. After paid credits reach 0, conversions use the **20 free/day** quota (same as a non-subscriber).
5. This proves: subscribers use paid conversions first; free daily is used only when paid = 0.

### 2.3 Test “subscriber not getting free” (paid first, then free)

- The intended behavior is: **subscribers use paid first, then get 20 free/day when paid = 0**. They do **not** get an extra 20 free on top of paid in the same conversion; they get 20 free/day only when the system is using the free tier for that conversion (i.e. when paid credits are 0).
- To verify:
  1. Sign in as subscriber with paid credits > 0. Convert until paid credits = 0 (check account/UI).
  2. Convert again: the next conversions should use **free daily** (20/day) and the UI should show “X free left today” and no paid credits.
  3. After 20 free in that day, you get 402 and the limit modal.

---

## 3. Backend reference (where the rules live)

| Rule                         | Location |
|-----------------------------|----------|
| Anonymous 20/day by IP      | `AnonymousUserService`, `ConvertController` (when `principal == null`) |
| Logged-in: paid first       | `UserService.consumeOneConversion()` – checks `paidCredits > 0` first |
| Logged-in: 20 free/day      | `UserService.consumeOneConversion()` – after paid, uses `freeUsedToday` / `FREE_DAILY_LIMIT` |
| Paid credits only from Stripe | `StripeWebhookController` (checkout.session.completed, invoice.paid) |
| Anonymous remaining (for UI) | `GET /api/anonymous/remaining` – no auth; returns `remaining`, `dailyLimit` |

---

## 4. Quick checklist for “app works when not logged in”

- [ ] Open app in **incognito**, do **not** sign in.
- [ ] On Convert page, see **"X of 20 free conversions left today"** (or similar).
- [ ] Convert an image; count decreases; download works.
- [ ] After 20 in that day (same IP), next convert shows limit modal / 402.
- [ ] Sign in (same or other window): subscriber uses **paid credits first**; after paid = 0, uses 20 free/day.

Your **live subscription** is only used when you are **logged in**. To test anonymous behavior, always use incognito and no sign-in.
