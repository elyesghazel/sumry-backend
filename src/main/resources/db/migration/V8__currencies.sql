-- Accounts get a currency; a Revolut account in EUR keeps EUR balances. Every transaction
-- remembers the rate it was booked at and its value in the base currency, so reports
-- add up in one currency without re-pricing history whenever the rate moves.
ALTER TABLE user_accounts
    ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'CHF';

ALTER TABLE transactions
    ADD COLUMN currency    varchar(3) NOT NULL DEFAULT 'CHF',
    ADD COLUMN fx_rate     numeric(14, 6) NOT NULL DEFAULT 1,
    ADD COLUMN base_amount numeric(14, 2);

UPDATE transactions SET base_amount = amount WHERE base_amount IS NULL;
ALTER TABLE transactions ALTER COLUMN base_amount SET NOT NULL;
