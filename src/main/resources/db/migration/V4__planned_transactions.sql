-- The table existed for a year with no code behind it. What it needs to actually run:
-- a way to plan transfers, an opt-in for booking automatically, and a watermark so the
-- job never books the same occurrence twice.
ALTER TABLE planned_transactions
    ADD COLUMN to_account_id  uuid REFERENCES user_accounts (id),
    ADD COLUMN auto_book      boolean NOT NULL DEFAULT false,
    ADD COLUMN last_booked_on date,
    ADD COLUMN is_active      boolean NOT NULL DEFAULT true,
    ADD COLUMN created_at     timestamp(6) NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_planned_user ON planned_transactions (user_id) WHERE is_active;
