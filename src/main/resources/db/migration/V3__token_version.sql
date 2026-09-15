-- Bumping this invalidates every token issued before the bump: a password change or
-- "sign out everywhere" no longer leaves a stolen 30-day token alive.
ALTER TABLE users ADD COLUMN token_version integer NOT NULL DEFAULT 0;
