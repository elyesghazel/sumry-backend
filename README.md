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
| Admin | `admin/users/{id}/plan` - set a user's plan; `admin/invites` - create (`{expiresInDays?, note?}`, the code is shown once), list, `DELETE admin/invites/{id}` revokes (role ADMIN) |
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
`SUMRY_REGISTRATION_MODE`, `SPRINGDOC_ENABLED`, and for the statement email
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
`ADMIN` (set it in the database: `UPDATE users SET role = 'ADMIN' WHERE email = '...'`).
The older `SUMRY_REGISTRATION_ENABLED=true` still means `open` when no mode is set. Signups
are limited to 5 per client address per hour.

### Building

```bash
./gradlew test                    # integration tests need Docker (Testcontainers), else they skip
cp .env.example .env              # fill in fresh local values
docker compose up -d --build     # local stack with Postgres and Redis
```
