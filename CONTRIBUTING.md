# Contributing

Issues and pull requests are welcome. For anything bigger than a fix, open an issue first so
we can agree on the approach.

## Setup

See [Development](README.md#development). The API runs with `docker compose up -d --build`,
the web app from [sumry-frontend](https://github.com/elyesghazel/sumry-frontend) with
`npm run dev` against it.

## Tests

```bash
./gradlew test
```

Integration tests start Postgres and Redis in Testcontainers, so Docker has to be running.
New endpoints get a test, and anything that touches user data has to stay covered by the
account deletion test (it fails when a table with user data is not cleaned up).

Schema changes are a new Flyway migration in `src/main/resources/db/migration`. Never edit a
migration that has already been released.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/), subject line only:

```
feat(planned): add postpone
fix(import): keep the sign on revolut fees
docs(readme): document plan limits
```

- Types: `feat` `fix` `refactor` `perf` `docs` `style` `test` `build` `ci` `chore` `revert`.
- Scope is the module you touched. Imperative, lowercase, no trailing period.
- One logical change per commit. A body only to explain a why the diff cannot show.
- Pull request titles follow the same rules.

## Style

Hyphens, not em dashes, in docs and UI text. Keep the tone plain.

By contributing you agree that your work is released under the [AGPL-3.0](LICENSE).
