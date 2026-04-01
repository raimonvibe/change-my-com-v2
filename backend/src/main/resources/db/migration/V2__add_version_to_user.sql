-- Add version column for optimistic locking to prevent race conditions
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
