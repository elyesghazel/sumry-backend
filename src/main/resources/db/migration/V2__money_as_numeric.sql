-- Money was stored as double precision, which cannot represent 0.10 exactly; every sum
-- drifted by fractions of a cent. Two decimal places of exact numeric is what the
-- domain actually is.

ALTER TABLE transactions
    ALTER COLUMN amount         TYPE numeric(14, 2) USING round(amount::numeric, 2),
    ALTER COLUMN balance_before TYPE numeric(14, 2) USING round(balance_before::numeric, 2),
    ALTER COLUMN balance_after  TYPE numeric(14, 2) USING round(balance_after::numeric, 2);

ALTER TABLE user_accounts
    ALTER COLUMN current_balance TYPE numeric(14, 2) USING round(current_balance::numeric, 2);

ALTER TABLE user_budgets
    ALTER COLUMN max_amount TYPE numeric(14, 2) USING round(max_amount::numeric, 2);

ALTER TABLE planned_transactions
    ALTER COLUMN amount TYPE numeric(14, 2) USING round(amount::numeric, 2);

-- Indexes the access patterns have always wanted and never had.
CREATE INDEX IF NOT EXISTS idx_transactions_account_date ON transactions (user_account_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions (category_id);
CREATE INDEX IF NOT EXISTS idx_transactions_transfer_group ON transactions (transfer_group_id) WHERE transfer_group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_categories_user ON categories (user_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts_user ON user_accounts (user_id);
