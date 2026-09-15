-- A budget was a bare monthly cap. Now it knows its period, whether unspent money rolls
-- into the next one, and which day the month starts on (payday, for most people).
ALTER TABLE user_budgets
    ADD COLUMN period     varchar(16) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN rollover   boolean NOT NULL DEFAULT false,
    ADD COLUMN start_day  integer NOT NULL DEFAULT 1,
    ADD COLUMN created_at timestamp(6) NOT NULL DEFAULT now();
