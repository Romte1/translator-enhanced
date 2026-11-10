# Testing

## Java plugin

```bash
./gradlew test
```

Tests cover translation parsing, menu matching, layout/reflow and dictionary validation. Use
the development client for UI checks:

```bash
./gradlew run
```

The Swing client is not a web application; Playwright is not used for the RuneLite UI.

## Collection API — Playwright E2E

```bash
cd server
npm ci
npm run test:e2e
```

The suite starts a local server with a temporary data directory and verifies:

- `/health` and `/metrics` are public;
- the queue rejects requests without the admin token;
- a text can be collected, appears in the pending queue, is marked per language and then
  disappears from that language's queue.

The test uses Playwright's API request context, so no browser download is required. It never
targets the production worker.

## Translation data

In the translation repository:

```bash
node scripts/validate-translations.mjs
```

The validator checks duplicate keys, tags, numbers, placeholders, empty values and manifest
hashes. A translation PR is not merged until it passes.

## CI

| Workflow | Scope |
| --- | --- |
| `Plugin CI` | `./gradlew test` on every push/PR. |
| `Server E2E` | Playwright against the local collection server. |
| `Validate translations` | Dictionary and manifest validation in the data repository. |
| `Mark translated texts` | Queue bookkeeping after a merge to `main`. |

## Test data

- Use unique text values (timestamp/random suffix) in E2E.
- Use a dedicated `DATA_DIR` for local runs.
- Never use real player text or production tokens in fixtures.
- Keep tests deterministic; do not assert on global queue size unless the test owns the data.
