# Scripts

## reset-free-conversions-only.sql

Resets **only** free conversion usage to zero so everyone gets a fresh 20 free/day:

- **Anonymous (IP):** `ip_conversion_tracker` → `conversions_used_today = 0`, `last_reset = today`
- **Logged-in free tier:** `app_user` → `free_used_today = 0`, `last_free_reset = today`

**Subscriber balances are unchanged:** `paid_credits`, `last_paid_reset`, `stripe_subscription_id`, `subscription_status`, and `auto_renewal` are not modified.

Run manually when you want a clean slate for free usage (e.g. after testing or a policy reset).

### Examples

```bash
# Local (from repo root)
psql -U postgres -d imageconverter -f scripts/reset-free-conversions-only.sql

# With connection string
psql "postgresql://user:pass@host:5432/imageconverter" -f scripts/reset-free-conversions-only.sql
```

Or open the file, copy the `BEGIN;` … `COMMIT;` block, and run it in your database client (Render Shell, Railway, TablePlus, DBeaver, etc.).
