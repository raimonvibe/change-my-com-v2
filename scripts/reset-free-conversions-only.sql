-- Reset FREE conversion usage to zero (fresh start).
-- Subscriber paid_credits and subscription data are NOT touched.
--
-- Run this once against your PostgreSQL database when you want to:
--   - Give everyone a fresh 20 free conversions per day (anonymous + logged-in free tier)
--   - Keep all paid_credits and subscription balances unchanged
--
-- How to run (pick one):
--   Local:  psql -U postgres -d imageconverter -f scripts/reset-free-conversions-only.sql
--   Render: Use Dashboard → Shell, or connect with psql and paste/run this file.
--   Railway: railway run psql $DATABASE_URL -f scripts/reset-free-conversions-only.sql
--   Or paste the statements below into your DB client (TablePlus, DBeaver, etc.).

BEGIN;

-- Anonymous (IP-based): reset daily count so each IP gets 20 free again today
UPDATE ip_conversion_tracker
SET conversions_used_today = 0,
    last_reset = CURRENT_DATE;

-- Logged-in users: reset only FREE tier usage (free_used_today, last_free_reset)
-- paid_credits, last_paid_reset, stripe_subscription_id, subscription_status, auto_renewal are NOT modified
UPDATE app_user
SET free_used_today = 0,
    last_free_reset = CURRENT_DATE;

COMMIT;
