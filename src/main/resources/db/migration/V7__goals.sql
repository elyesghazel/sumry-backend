-- Savings goals. Linked to an account, the goal reads that account's balance; without
-- one it keeps its own running total that contributions add to.
CREATE TABLE goals (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES users (id),
    name          varchar(255) NOT NULL,
    target_amount numeric(14, 2) NOT NULL,
    target_date   date,
    account_id    uuid REFERENCES user_accounts (id),
    saved_amount  numeric(14, 2) NOT NULL DEFAULT 0,
    color         varchar(16),
    icon          varchar(64),
    is_active     boolean NOT NULL DEFAULT true,
    achieved_at   timestamp(6),
    created_at    timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_goals_user ON goals (user_id) WHERE is_active;
