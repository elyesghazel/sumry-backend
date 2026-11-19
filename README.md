# Name and Slogan

**Sumry** - See your money. Understand it.
**Sumry** - Your finances, summarized.

## Core Identity
Sumry is a **personal finance tracker** that values calm over clutter.  
It exists to make money management **feel effortless** — no walls of numbers, no manipulative gamification, no 20-step setup. Just understanding at a glance.

Tone: grounded, confident, human.

## API

Spring Boot / Kotlin, PostgreSQL, Redis. Every route under `/api` needs a bearer token from
`POST /api/users/login` except `register` (closed by default), `GET /api/config` and
`/actuator/health`.

| Area | Routes |
|---|---|
| Session | `users/login`, `users/logout`, `users/logout-all`, `users/password`, `users/preferences`, `users/me` (profile, plan, limits, `aiEnabled`) |
| Registration | `users/register` (takes `inviteCode` in invite mode), `config` (public, `{registrationMode}`) |
| Account deletion | `DELETE users/me` with `{"password": "..."}` - removes the user and all their data in one transaction |
| Admin | `admin/users` (list, counts only), `admin/stats`, `admin/audit?page=&size=`; per user `admin/users/{id}/plan`, `/lock`, `/unlock`, `/password-reset`, `DELETE admin/users/{id}`; `admin/invites` - create (`{expiresInDays?, note?}`, the code is shown once), list, `DELETE admin/invites/{id}` revokes (role ADMIN) |
| Password reset | `users/password-reset` (no token) with `{"token": "...", "newPassword": "..."}` - redeems a one-time token from `admin/users/{id}/password-reset` |
| Accounts | `users/accounts` — CRUD, `reconcile/{id}` books the gap to the real balance; accounts carry a currency |
| Transactions | `transactions/` list, `transactions/search` (filtered, paged, with sums), `transactions/quick` (natural language), transfers, `cashflow/{account}` |
| Categories | `category/user`, `category/`, `category/label` |
| Budgets | `budgets`, `budgets/progress?date=` — periods, start day, rollover, pace |
| Planned | `planned`, `planned/{id}/book`, `planned/upcoming`, `planned/safe-to-spend`; a job auto-books opted-in plans |
| Goals | `goals`, `goals/{id}/contribute` |
| Rules | `rules`, `rules/test`, `rules/apply?dryRun=` |
| Insights | `insights/subscriptions`, `insights/forecast` |
| Import / export | `import/preview`, `import/commit` (PostFinance, Revolut, generic CSV), `export/transactions.csv|json` |
| Statement | `statement?month=`, `statement/html`, `statement/send` (needs `SUMRY_MAIL_*`) |
| FX | `fx?base=` — ECB rates via frankfurter.dev, cached in Redis |

Money is `numeric(14,2)`; each transaction stores its account currency, the rate at booking
and `baseAmount` in the user's base currency, which is what every report sums. The schema
is owned by Flyway (`src/main/resources/db/migration`); Hibernate only validates.

### Environment

`JWT_SECRET` (base64, ≥32 bytes), `JWT_EXPIRATION_MS`, `SUMRY_CORS_ALLOWED_ORIGINS`,
`SUMRY_REGISTRATION_MODE`, `SUMRY_BOOTSTRAP_ADMIN_EMAIL` (creates the first admin on an
empty database and logs a one-time password), `SPRINGDOC_ENABLED`, and for the statement email
`SUMRY_MAIL_HOST/PORT/USER/PASSWORD/FROM` plus `SUMRY_STATEMENT_SCHEDULED=true` to send on
the 1st.

The Claude check on statement imports (`import/check`) is optional and off by default. It
needs `SUMRY_AI_BRIDGE_URL` and `SUMRY_AI_BRIDGE_TOKEN` (a headless `claude -p` bridge on the
host) and only serves the users listed in `SUMRY_AI_USERS`, comma-separated user ids or
emails. Everyone else gets 403 and the bridge is never called; with an empty list AI is off
for everyone. `GET /api/users/me/capabilities` returns `{"aiEnabled": ...}` for the current
user.

Plan limits are off unless `SUMRY_LIMITS_ENABLED=true`; while off every user is treated as
premium. When on, `FREE` users get at most `SUMRY_FREE_MAX_ACCOUNTS` (default 2) accounts,
no `import/*` and no statement email; blocked calls answer 403
`{"error":"plan_limit","limit":"accounts|statement_import|statement_email"}`.

`SUMRY_REGISTRATION_MODE` is `closed` (default), `invite` or `open`. In invite mode a signup
needs an unused, unexpired code from `POST /api/admin/invites`, which needs a user with role
`ADMIN` (the bootstrap admin, or set it in the database:
`UPDATE users SET role = 'ADMIN' WHERE email = '...'`).
The older `SUMRY_REGISTRATION_ENABLED=true` still means `open` when no mode is set. Signups
are limited to 5 per client address per hour.

### Admin

Every `/api/admin` route needs role `ADMIN`, checked on the server. The admin views show
profile fields and counts (accounts, transactions) only, never amounts, balances or notes,
and there is no way to sign in as another user.

- Lock: login is refused and every token the user holds (sessions and Shortcut tokens) stops
  working at once. Unlock lets them sign in again; old tokens stay revoked.
- Password reset: creates a one-time token, valid 24 hours, shown to the admin once (only a
  hash is stored). Until mail delivery exists the admin passes it on by hand. Redeeming it
  signs the user out everywhere.
- An admin cannot lock or delete their own account through these routes.
- Every admin action (plan, lock, unlock, delete, password reset, invite create/revoke) writes
  a row to `admin_audit`. When a user is deleted their audit rows stay: the user ids there
  have no foreign key, so they are kept as bare ids that no longer resolve to a username. The
  audit detail never holds the username, email or invite note.
- Signup dates are recorded from V14 on; users created before have none and are left out of
  the signups-per-week stat.

### Self-hosting

`deploy/` has a compose stack with Postgres, Redis, the API, the web app and Caddy with
automatic HTTPS. See [deploy/README.md](deploy/README.md).

### Building

```bash
./gradlew test                    # integration tests need Docker (Testcontainers), else they skip
cp .env.example .env              # fill in fresh local values
docker compose up -d --build     # local stack with Postgres and Redis
```
