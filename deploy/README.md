# Self-hosting Sumry

One compose stack: Postgres 16, Redis, the API, the web app and Caddy in front. Caddy sends
`/api/*` to the API and everything else to the web app, so both live on one origin. With a
domain set, Caddy gets and renews a certificate on its own.

Needs Docker with Compose v2 and about 1.5 GB of free RAM.

## Install

```bash
git clone https://github.com/elyesghazel/sumry-backend.git
cd sumry-backend/deploy
cp .env.example .env
# fill in the two secrets
sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|; s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env
# set SUMRY_DOMAIN and SUMRY_BOOTSTRAP_ADMIN_EMAIL in .env
docker compose up -d
```

The API takes up to a minute on the first start (database migrations). `docker compose ps`
shows every service as healthy when it is ready.

Without `SUMRY_DOMAIN` the stack serves plain HTTP on port 80. Use that only on a private
network or behind your own TLS proxy (then point the proxy at port 80 and change
`SUMRY_HTTP_PORT` if 80 is taken).

## First admin

Registration is closed by default. Set `SUMRY_BOOTSTRAP_ADMIN_EMAIL` before the first start.
When the database has no users, the API creates that user as admin and prints a generated
password once:

```bash
docker compose logs backend | grep "Bootstrap admin"
```

Sign in and change the password right away (Settings, or `PUT /api/users/password`). The
password is never stored in plain text and is not printed again. Once any user exists the
variable is ignored, so it can stay set.

To let other people in, set `SUMRY_REGISTRATION_MODE=invite` and create codes as the admin
with `POST /api/admin/invites`, or `open` to allow anyone to sign up.

Lost the log line before signing in? Stop the stack, run `docker compose down -v` (this
deletes all data) and start again. Only do this on a fresh install.

## Updates

```bash
docker compose pull
docker compose up -d
```

`SUMRY_VERSION` pins both images to a release tag instead of `latest`.

## Backups

```bash
./backup.sh            # writes backups/sumry-<timestamp>.dump
./backup.sh /mnt/nas   # or into another directory
```

Run it from cron, for example `15 3 * * * /path/to/deploy/backup.sh`, and copy the files off
the host. Restore into a running stack:

```bash
docker compose exec -T postgres pg_restore -U sumry -d sumry --clean --if-exists < backups/sumry-<timestamp>.dump
```

## Privacy policy and imprint

Copy `legal/*.example.md` to `legal/privacy.md` and `legal/imprint.md`, adapt them (templates, not legal advice) and uncomment the `volumes` lines of `frontend` in `docker-compose.yml`.
The app then shows `/privacy` and `/imprint`, links them and asks for privacy consent at signup; without the files nothing changes.
With plan limits on, an optional `legal/premium.md` (see `premium.example.md`) holds your payment details and replaces the generic "How to pay" text on the Plan tab.

## Hardening

- Every container has a read-only root filesystem, `no-new-privileges`, all capabilities
  dropped and a memory limit. Writable paths are volumes or tmpfs.
- Postgres, Redis, the API and the web app run as non-root users. Caddy runs as root inside
  its container with only `NET_BIND_SERVICE`, because its certificate volumes are
  root-owned.
- Postgres and Redis sit on an internal network with no route out; only the API reaches
  them. Only Caddy publishes ports.
- `/actuator` and the API docs are not routed by Caddy.

## Running a second instance on the same host

The compose project is called `sumry`. For another stack next to it, give it its own
name and ports, and set the same name when you run `backup.sh`:

```bash
export COMPOSE_PROJECT_NAME=sumry-test
```
