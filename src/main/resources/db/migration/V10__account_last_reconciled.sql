-- When the user last confirmed an account against the real bank or wallet. Null until
-- the first reconcile; the apps nudge for a check once it is two weeks old.
ALTER TABLE user_accounts
    ADD COLUMN last_reconciled_at timestamp(6);
