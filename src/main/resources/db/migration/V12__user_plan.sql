-- Which plan a user is on. Only enforced when SUMRY_LIMITS_ENABLED is set (the hosted
-- instance); self-hosted instances ignore it and every user gets everything.
ALTER TABLE users
    ADD COLUMN plan varchar(16) NOT NULL DEFAULT 'FREE';

ALTER TABLE users
    ADD CONSTRAINT users_plan_check CHECK (plan IN ('FREE', 'PREMIUM'));
