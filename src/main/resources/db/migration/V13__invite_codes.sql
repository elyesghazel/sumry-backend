-- Single-use invite codes for SUMRY_REGISTRATION_MODE=invite. Only a SHA-256 hash of the
-- code is stored; the code itself is shown once, when the admin creates it.
-- Both user references go null when that user deletes their account, so the audit row stays.
CREATE TABLE invite_codes (
    id          uuid PRIMARY KEY,
    code_hash   varchar(64) NOT NULL UNIQUE,
    code_hint   varchar(8)  NOT NULL,
    note        varchar(255),
    created_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at  timestamp(6) NOT NULL DEFAULT now(),
    expires_at  timestamp(6),
    used_by     uuid REFERENCES users (id) ON DELETE SET NULL,
    used_at     timestamp(6),
    revoked_at  timestamp(6)
);
