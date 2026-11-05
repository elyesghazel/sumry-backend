# Name and Slogan

**Sumry** - See your money. Understand it.
**Sumry** - Your finances, summarized.

## Core Identity
Sumry is a **personal finance tracker** that values calm over clutter.  
It exists to make money management **feel effortless** — no walls of numbers, no manipulative gamification, no 20-step setup. Just understanding at a glance.

Tone: grounded, confident, human.

## API

Spring Boot / Kotlin, PostgreSQL, Redis. Every route under `/api` needs a bearer token from
`POST /api/users/login` except `register` (off by default) and `/actuator/health`.

| Area | Routes |
|---|---|
| Session | `users/login`, `users/logout`, `users/logout-all`, `users/password`, `users/preferences` |
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
`SUMRY_REGISTRATION_ENABLED`, `SPRINGDOC_ENABLED`, and for the statement email
`SUMRY_MAIL_HOST/PORT/USER/PASSWORD/FROM` plus `SUMRY_STATEMENT_SCHEDULED=true` to send on
the 1st.

### Building

```bash
./gradlew test
cp .env.example .env              # fill in fresh local values
docker compose up -d --build     # local stack with Postgres and Redis
```
