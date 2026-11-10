# Agent Instructions

These rules are mandatory for every change in this repository. Read
[docs/architecture.md](docs/architecture.md), [docs/environments.md](docs/environments.md),
[docs/testing.md](docs/testing.md) and [docs/operations.md](docs/operations.md) before editing
code, tests, workflows or deployment files.

## Architecture

- **Modular monolith only.** The plugin is one Gradle module and one process. Do not split it
  into microservices or add a second runtime for domain logic.
- **Clean layers.** Keep RuneLite event wiring in `TranslatorPlugin`; put behaviour in the
  injected application components (`TranslationStore`, `MenuEntryTranslator`,
  `WidgetTranslator`, `TranslatorAPI`, `InterfaceTextCapture`); keep value objects and pure
  helpers free of I/O.
- **Dependency injection is mandatory.** Services are constructed by RuneLite's Guice
  container. Use constructor or field `@Inject`; do not add static singletons, service
  locators or hidden global state. `new` is only acceptable for value objects, exceptions,
  short-lived DTOs and third-party builders.
- **Dependency direction.** Event/UI code may depend on application services; application
  services must not depend on Swing widgets or RuneLite event classes. External I/O belongs
  in an adapter (`TranslatorAPI`, the collection server), never in the domain layer.
- The collection server is a **single-purpose edge adapter**, not a microservice. It has no
  independent domain model and must not grow into one.

## Environments and security

- `dev` and `prod` are separate. Local development uses `./gradlew run`, a local collection
  server and local dictionaries. Production is the Plugin Hub build, the Cloudflare Worker,
  D1 and `main` in the translation repository.
- Never run destructive security scans, stress tests or audit tools against production.
  Read-only checks of `/health` and `/metrics` are allowed.
- Never commit tokens, `.env` files, production database dumps or user data.
- Translation and deployment changes go through a pull request; do not write directly to
  production data.

## Testing and delivery

- Java changes require `./gradlew test`.
- The Node collection service requires Playwright E2E coverage in `server/e2e`.
- Run `cd server; npm ci; npm run test:e2e` after changing the server API.
- Translation data must pass `node scripts/validate-translations.mjs` in the translation
  repository before merge.
- CI lives in `.github/workflows`: `Plugin CI`, `Server E2E` and the translation repository
  workflows. Do not bypass required checks.
- Playwright covers the HTTP collection contract. RuneLite's Swing UI is exercised manually
  with the development client; do not pretend a browser test covers it.

## Observability and hosting

- Logs use SLF4J/Logback in Java and stdout/JSON in Node. Use open-source observability
  (Prometheus, Grafana, Loki, Uptime Kuma) for core monitoring; no proprietary analytics SDK.
- The collection service exposes `/health` and Prometheus-compatible `/metrics`.
- Cloudflare Workers + D1 is the production edge. AWS free tier may be used only for
  auxiliary, non-runtime workloads and only after an ADR; it must not duplicate the monolith.
  See [docs/adr/0001-hosting.md](docs/adr/0001-hosting.md).

## Automation

- GitHub Actions is the automation layer. Translation changes are proposed by PR, validated
  by CI and merged after review. Server deployment remains a protected manual step until a
  protected environment with secrets is configured.
- MCP tools may be used for DevOps and repository operations, but they must respect the same
  review, secret-handling and environment rules as a human.
- Update these documents when a rule, path, environment or deployment target changes.
