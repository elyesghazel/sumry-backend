# Security

Sumry holds financial data, so security reports are welcome and taken seriously.

## Reporting a vulnerability

Please report privately through GitHub: **Security > Report a vulnerability** on this
repository (or on [sumry-frontend](https://github.com/elyesghazel/sumry-frontend) if it only
affects the web app). Do not open a public issue for it.

Include what you found, how to reproduce it, and the version or commit you tested. You will
get an answer within a week. Once a fix is released, the advisory is published and you are
credited unless you prefer not to be.

Only test against your own instance. Never against someone else's server or data.

## Supported versions

Fixes go into `main` and the next release image. Run `latest` or the newest `v*` tag; older
tags do not get backports.

## What the API does to protect data

- Passwords are bcrypt hashes and never leave the server. Sign-in answers the same way for an
  unknown email and a wrong password.
- Sign-in is rate-limited per client address and per email; signups per client address.
- Sessions are signed JWTs. Signing out, signing out everywhere and changing the password
  revoke existing tokens.
- Every account, transaction, budget, plan and goal is checked against the signed-in user.
- Registration is closed by default.
- Only `/actuator/health` is exposed, without details. The API docs are off unless
  `SPRINGDOC_ENABLED=true`.
- The schema is managed by Flyway; Hibernate only validates it.
- The container runs as a non-root user. The self-host stack adds read-only filesystems,
  dropped capabilities and an internal network for the database, see
  [deploy/README.md](deploy/README.md#hardening).
- Account numbers (IBANs, Swiss postal accounts) in statements, notes and shortcut text are
  masked before they are stored.

## Running your own instance

- Generate fresh secrets for `JWT_SECRET` and `POSTGRES_PASSWORD`; never reuse the examples.
- Keep registration `closed` or `invite` on an instance reachable from the internet.
- Put it behind HTTPS (`SUMRY_DOMAIN` in the self-host stack does this).
- Back up the database (`deploy/backup.sh`) and keep the backups off the host.
