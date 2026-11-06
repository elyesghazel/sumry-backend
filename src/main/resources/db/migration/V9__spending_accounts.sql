-- Savings accounts hold money that is not meant for daily spending. Safe-to-spend only
-- counts accounts flagged as spending; every existing account starts flagged so nothing
-- changes until the user switches one off.
ALTER TABLE user_accounts
    ADD COLUMN spending boolean NOT NULL DEFAULT true;
