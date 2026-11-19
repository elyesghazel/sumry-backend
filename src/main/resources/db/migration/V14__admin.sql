-- Admin user management (#13).

-- Signup date. Existing users have none recorded and stay null; new rows get now().
ALTER TABLE users ADD COLUMN created_at timestamp(6);
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE users ADD COLUMN last_login_at timestamp(6);
-- Set while an admin has locked the account: login is refused and tokens are revoked.
ALTER TABLE users ADD COLUMN locked_at timestamp(6);

-- One row per admin action. The user ids are plain columns without a foreign key on
-- purpose: deleting a user (or the admin) keeps the history, the id just no longer
-- resolves to a username. detail never holds personal or financial data.
CREATE TABLE admin_audit (
    id             uuid PRIMARY KEY,
    admin_id       uuid         NOT NULL,
    action         varchar(32)  NOT NULL,
    target_user_id uuid,
    detail         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at     timestamp(6) NOT NULL DEFAULT now(),
    -- Insertion order, so actions within the same microsecond still list in order.
    seq            bigint GENERATED ALWAYS AS IDENTITY
);
CREATE INDEX admin_audit_seq_idx ON admin_audit (seq DESC);

-- One-time password reset tokens created by an admin. Only a SHA-256 hash is stored.
CREATE TABLE password_reset_tokens (
    id         uuid PRIMARY KEY,
    token_hash varchar(64)  NOT NULL UNIQUE,
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    expires_at timestamp(6) NOT NULL,
    used_at    timestamp(6)
);
