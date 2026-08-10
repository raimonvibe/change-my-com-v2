-- Brings the migrations in line with the JPA entities.
--
-- Nothing had ever executed these files: flyway-core was on the classpath but
-- Spring Boot 4 moved the autoconfiguration into a separate module that was not
-- a dependency, and no code calls migrate(). Hibernate built the schema from
-- the entities instead, so two gaps between the two went unnoticed.
--
-- Written forward-only and idempotently rather than by editing V1, so it
-- converges on the same result whether it meets a database built by these
-- migrations, one built by Hibernate, or an empty one.

-- 1. WebhookEvent maps to @Table(name = "webhook_events"), but V1 creates
--    "webhook_event". A database built by V1 therefore lacks the table the
--    entity looks for. Rename it when the old name is the only one present;
--    a Hibernate-built database already has the plural name and is untouched.
DO $$
BEGIN
    IF to_regclass('public.webhook_event') IS NOT NULL
       AND to_regclass('public.webhook_events') IS NULL THEN
        ALTER TABLE webhook_event RENAME TO webhook_events;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_stripe_id
    ON webhook_events(stripe_event_id);

-- 2. The CreditLedger entity has no table in any migration at all. Columns
--    follow Spring Boot's default naming strategy, which is what Hibernate
--    validates against: user_id for the @ManyToOne, created_at for createdAt.
--    OffsetDateTime maps to TIMESTAMP WITH TIME ZONE.
CREATE TABLE IF NOT EXISTS credit_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    delta INTEGER,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_credit_ledger_user ON credit_ledger(user_id);
