# Security remediation plan

Findings from a review of the Sumry backend on 2026-08-23, against the deployed
commit `800e2e7`. Written for an agent to execute top to bottom.

Repo: `ssh://git@git.elyesghazel.ch:2222/sumry/backend.git`
Deployed by: `/opt/docker/sumry/docker-compose.yml` on elyes-ghazel-server.

## Ground rules

- One logical change per commit, Conventional Commits, subject line only, no
  attribution trailers.
- The API is live and holds real financial data. **Take a dump before any change
  that touches persistence:** `docker exec apps-sumry-db pg_dump -U sumry -d sumry > db-dump/pre-<change>-$(date +%Y%m%d-%H%M%S).sql`
- Rebuild and verify with `docker compose up -d --build backend` from
  `/opt/docker/sumry`, then check `curl -s https://sumry-api.elyesghazel.ch/actuator/health`.
- After each fix, re-run the probe in "Verification" below. Do not batch all
  fixes into one deploy — F1 and F2 change response shapes the web client
  consumes.

## What is already correct — do not "fix" these

Verified by reading every mutating path, not by sampling:

- **Ownership checks are consistent and complete.** Every account, transaction,
  transfer, category and budget path resolves the caller from
  `principal.name` and rejects cross-user access. `TransactionService.ownedAccount`
  and `ownedTransferLegs` are applied on all of log, update, delete, transfer,
  reconcile and cashflow. No IDOR found.
- CORS is pinned to `https://sumry.elyesghazel.ch` (not a wildcard).
- Actuator exposes only `/actuator/health` with `show-details=never`; `/actuator/env`
  and `/actuator/beans` return 401. Swagger is off in prod (`SPRINGDOC_ENABLED=false`).
- Passwords are bcrypt. Login is not user-enumerable (uniform message on both
  bad email and bad password). Password change requires the current password.
- No SQL injection surface — all queries are Spring Data derived or JPQL, no
  string concatenation.
- JWT is HS256 over a key validated at startup to be ≥32 bytes; the `role` claim
  is read from the signed token, so it cannot be forged client-side.
- CSRF disabled is correct here: stateless bearer-token API, no cookies.

---

## Status

| | Finding | State |
|---|---|---|
| F1 | Password hash returned by the API | **Fixed** — `94e8ee2`, deployed and verified 2026-08-23 |
| F2 | Open registration | **Fixed** — `97b3778`, deployed and verified 2026-08-23 |
| F3 | No login rate limiting | Open |
| F4 | 30-day tokens, no revocation | Open |
| F5 | Container runs as root | Open |
| F6 | `ddl-auto=update` in prod | Open |
| F7 | Cleanups | Open |

F3–F7 remain. Start at F3.

---

## F1 — HIGH — bcrypt password hash is returned by the API — FIXED

**What.** Several endpoints return JPA entities instead of DTOs. `UserAccount.user`
and `UserPreferences.user` have no `@JsonIgnore`, and `User.password` is not
annotated either, so Jackson serialises the bcrypt hash. `User.preferences ↔
UserPreferences.user` is an unbroken bidirectional cycle, so it repeats until
Jackson's depth cap.

**Confirmed live.** `GET /api/users/accounts/user/{ownId}` returns HTTP 200,
**118'868 bytes**, containing the hash `$2a$10$...` **499 times**.

**Blast radius — state this accurately.** Ownership *is* enforced on this
endpoint, so this is not cross-user disclosure; a user can only obtain their own
hash. It still matters: the hash is handed to every client holding a valid token
and lands in browser devtools, HTTP logs, reverse-proxy logs and Cloudflare. At
bcrypt cost 10 an offline attack on a weak password is feasible. The ADMIN-only
variants (`GET /api/users`, `GET /api/users/accounts/all`, `GET /api/category/admin`)
would leak *every* user's hash, but are inert today because no ADMIN account
exists — that is luck, not design.

**Fix.** Both layers, because either alone is fragile:

1. Annotate the entity so a hash can never serialise even from a path someone
   adds later — in `module/user/model/User.kt`:
   ```kotlin
   @field:JsonIgnore
   @Column(nullable = false)
   var password: String,
   ```
   Use `@field:JsonIgnore` (not bare `@JsonIgnore`) — on a Kotlin constructor
   property the bare form can land on the constructor parameter and not apply.
   Verify with the probe, do not assume.
2. Break the serialisation cycle: `@JsonIgnore` on `UserPreferences.user`
   (`module/user/model/UserPreferences.kt:27`) and on `UserAccount.user`
   (`module/user/model/UserAccount.kt:19`). `User.accounts` already has it.
3. Stop returning entities. Change these signatures to the existing DTOs:
   - `UserAccountController.getAll()` and `.getFromUser()` → `UserAccountResponseDto`
     (`UserAccountMapper.toDtoList` already exists)
   - `UserController.getAll()` → `UserResponseDto` (`UserMapper.toDto` exists)
   - `CategoryController.getAll()` → `CategoryResponseDTO`

**Do not** simply delete the `/user/{userId}` endpoint — check whether the web
client at `web-sumry` calls it first.

**Verify.** The probe must return zero matches for `$2a$` and a response under
~2 KB.

**Outcome.** All three annotations and all four controller/service signatures were
changed. The endpoint went from 118'868 bytes with 499 hashes to **589 bytes with
zero**, and no `$2a$` appears anywhere across the authenticated read surface.
The web client never called any of the four changed endpoints (it uses
`/api/users/accounts/`, already a DTO), so nothing downstream broke.

---

## F2 — MEDIUM — registration is open to the internet — FIXED

**What.** `POST /api/users/register` is `permitAll` in `config/SecurityConfig.kt:36`.
Confirmed reachable unauthenticated (empty body → 400, not 401). Anyone who finds
`sumry-api.elyesghazel.ch` can create accounts. `register` also returns 409
"Email already exists", which enumerates registered emails.

**Fix.** This is a single-user instance, so close it. Pick one:

- *Simplest:* drop `/api/users/register` from the `permitAll` matcher so it needs
  an existing token, and require ADMIN on the controller method.
- *If self-registration is ever wanted:* gate on a
  `SUMRY_REGISTRATION_ENABLED` env flag defaulting to `false`.

**Outcome — note the deviation.** The env-flag option was taken:
`sumry.registration.enabled` / `SUMRY_REGISTRATION_ENABLED`, defaulting to
`false`, checked at the top of `UserService.register`. The ADMIN option was
rejected because no ADMIN account exists, so it would have made registration
permanently impossible without direct database access.

Two consequences to know:

- Registration now answers **403 `{"error":"Registration is disabled on this instance"}`**,
  not the 401 originally predicted here — the request reaches the controller and
  is refused in the service, rather than being blocked at the security filter.
- The web client's signup page no longer works. Intended on a single-user
  instance, but it is a visible behaviour change.

The "make conflict responses uniform" step was **not** done and is no longer
needed: with registration closed the endpoint never reaches the duplicate checks,
so the enumeration path is gone. If registration is ever re-enabled, revisit it.

**Verify.** `curl -X POST .../api/users/register -d '{...}'` returns 403.

---

## F3 — MEDIUM — no rate limiting on login

**What.** No app-level limiter and no Traefik rate-limit middleware on the
`sumry-api` router (confirmed by inspecting the container labels). Online
password guessing against `/api/users/login` is unthrottled. bcrypt cost 10
slows it but does not bound it.

**Fix.** Cheapest effective option is Traefik, no code change — in
`/opt/docker/sumry/docker-compose.yml`:

```yaml
- "traefik.http.middlewares.sumry-login-rl.ratelimit.average=5"
- "traefik.http.middlewares.sumry-login-rl.ratelimit.burst=10"
- "traefik.http.middlewares.sumry-login-rl.ratelimit.period=1m"
- "traefik.http.routers.sumry-api.middlewares=sumry-login-rl"
```

Note this rate-limits the whole router. If that proves too blunt for normal app
use, split a second router matching `Path(/api/users/login)` and attach the
middleware only there.

Because the docker-compose file changes, follow the infra checklist: update the
service README, regenerate the overview, push docs, and commit the docker repo.

---

## F4 — MEDIUM — 30-day tokens with no revocation

**What.** `JWT_EXPIRATION_MS=2592000000`. There is no denylist, no refresh
token, and no `tokenVersion` on the user. `changePassword` does not invalidate
tokens already issued, so a stolen token stays valid for up to 30 days *after*
the user changes their password in response to the theft.

**Fix.** Smallest change that actually closes it: add an `int tokenVersion` to
`User`, put it in the JWT claims at login, compare it in
`JwtAuthenticationFilter`, and increment it in `changePassword`. Then shorten
`JWT_EXPIRATION_MS` to something like 7 days.

Shortening the lifetime *alone* does not fix revocation — do not stop there and
call it done.

---

## F5 — LOW — container runs as root

`backend/Dockerfile` has no `USER`. Confirmed: `docker exec apps-sumry-api id` →
`uid=0(root)`. `no-new-privileges:true` is set in compose, which limits it, but
add to the runtime stage:

```dockerfile
RUN addgroup -S app && adduser -S -G app app
USER app
```

---

## F6 — LOW — `ddl-auto=update` in production, Flyway disabled

`application.properties` has `spring.jpa.hibernate.ddl-auto=update` with
`spring.flyway.enabled=false`, while `flyway-core` is on the classpath. Hibernate
mutates the live schema on every deploy with no migration history and no review
step. An entity rename silently orphans a column; there is no rollback path.

More operational than security, but it is the reason a bad deploy could cost
data. Move to Flyway: baseline from the current schema, set `ddl-auto=validate`.

---

## F7 — LOW — cleanups

Do these as one commit each; none is urgent.

1. **`config/Configuration.kt` is a self-shadowing annotation.** It declares
   `annotation class Configuration` in `ch.sumry.core.config` while annotating
   itself — there is no import of Spring's `@Configuration`. Currently inert
   because `SecurityConfig` and `CorsConfig` both import Spring's explicitly, but
   a new config class in that package that forgets the import would silently not
   register — including a security config. Delete the file.
2. **`security/MethodSecurityTestConfig.kt`** is test scaffolding in the main
   source set, duplicating `@EnableMethodSecurity` from `SecurityConfig`. Delete.
3. **Unused dependencies:** no code references Redis or Spring Shell. Drop
   `spring-boot-starter-data-redis` and `spring-shell-starter` from
   `build.gradle.kts`, and remove the `redis` service from the compose file — it
   is a running container serving nothing.
4. **Password policy is inconsistent:** `UserCreateDto` requires 6 chars,
   `PasswordChangeDto` requires 8. Raise both to 12.
5. **`GlobalExceptionHandler` NPE handler echoes `ex.message`** in a 500 body.
   Return a fixed string and log the detail server-side instead.
6. **`TransactionService.getByUserIdAdmin` ignores its `principal` parameter**,
   relying entirely on the controller's `@PreAuthorize`. Add the service-level
   check for defence in depth.
7. **jjwt is 0.11.5**, several years old, and the code uses its deprecated
   `parserBuilder`/`setSigningKey` API. Bump to 0.12.x and migrate the calls.
   No specific CVE was verified against 0.11.5 — treat this as hygiene, and
   check the advisory database rather than repeating this as a known-vuln claim.

---

## Verification probe

Run after each fix. Needs `SUMRY_EMAIL`/`SUMRY_PASSWORD`; send a real
User-Agent, because Cloudflare 403s default script UAs on this zone.

```fish
set -l UA "sumry-security-review/1.0"
set -l tok (curl -s -A "$UA" -X POST "$SUMRY_API_URL/api/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SUMRY_EMAIL\",\"password\":\"$SUMRY_PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
set -l uid 8bcfe0b3-3971-4f24-b820-a7d435083b94

# F1: must print 0
curl -s -A "$UA" "$SUMRY_API_URL/api/users/accounts/user/$uid" \
  -H "Authorization: Bearer $tok" | grep -c '\$2a\$'

# F2: must be 403
curl -s -o /dev/null -w '%{http_code}\n' -A "$UA" \
  -X POST "$SUMRY_API_URL/api/users/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"probe","email":"probe@example.com","password":"probe12345"}'

# Regression: these must all still work
curl -s -o /dev/null -w 'accounts=%{http_code} ' -A "$UA" "$SUMRY_API_URL/api/users/accounts/" -H "Authorization: Bearer $tok"
curl -s -o /dev/null -w 'tx=%{http_code} ' -A "$UA" "$SUMRY_API_URL/api/transactions/" -H "Authorization: Bearer $tok"
curl -s -o /dev/null -w 'cats=%{http_code}\n' -A "$UA" "$SUMRY_API_URL/api/category/user" -H "Authorization: Bearer $tok"
```

Also re-run the sumry MCP server's own tools after F1 — it parses
`UserAccountResponseDto`, so a shape change there breaks it:
`~/.claude/mcp/sumry/`.

## Suggested order

~~F1 → F2~~ (done) → F3 → F5 → F7 → F4 → F6.

F1 and F2 were the ones that mattered. F4 and F6 are larger changes worth doing
deliberately rather than rushing in behind the others.

Note for F3: it changes `/opt/docker/sumry/docker-compose.yml`, not this repo, so
it also triggers the infra checklist — service README, regenerated overview,
docs push, and a commit in the docker repo.
