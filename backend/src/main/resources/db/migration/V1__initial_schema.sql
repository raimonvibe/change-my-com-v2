-- Initial schema migration for Change-My.com Image Converter
-- This migration creates the base tables if they don't exist

-- Users table
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    free_used_today INTEGER DEFAULT 0,
    last_free_reset DATE DEFAULT CURRENT_DATE,
    paid_credits INTEGER DEFAULT 0,
    last_paid_reset DATE,
    stripe_subscription_id VARCHAR(255),
    subscription_status VARCHAR(50),
    auto_renewal BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_stripe_sub ON app_user(stripe_subscription_id);

-- Anonymous user tracking (IP-based)
CREATE TABLE IF NOT EXISTS ip_conversion_tracker (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    conversions_used_today INTEGER DEFAULT 0,
    last_reset DATE DEFAULT CURRENT_DATE
);

CREATE INDEX IF NOT EXISTS idx_ip_tracker_ip ON ip_conversion_tracker(ip_address);

-- Webhook event tracking (idempotency)
CREATE TABLE IF NOT EXISTS webhook_event (
    id BIGSERIAL PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_event_stripe_id ON webhook_event(stripe_event_id);
