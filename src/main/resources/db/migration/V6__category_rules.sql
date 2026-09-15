-- "Anything from Coop is groceries": a pattern over the note that picks the category, so
-- imports and quick entries land categorised and history can be recategorised in one go.
CREATE TABLE category_rules (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users (id),
    pattern     varchar(255) NOT NULL,
    match_type  varchar(16) NOT NULL DEFAULT 'CONTAINS',
    category_id uuid NOT NULL REFERENCES categories (id),
    -- Only fires for entries on this account when set.
    account_id  uuid REFERENCES user_accounts (id),
    priority    integer NOT NULL DEFAULT 100,
    is_active   boolean NOT NULL DEFAULT true,
    hits        integer NOT NULL DEFAULT 0,
    created_at  timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_category_rules_user ON category_rules (user_id, priority) WHERE is_active;
