# Architecture

## Status

The project is a **modular monolith**. The plugin ships as one Gradle module and one RuneLite
process. The collection server is a small edge adapter with a single responsibility; it is not
an independent business service.

## Layers

| Layer | Code | Responsibility |
| --- | --- | --- |
| Entry point | `TranslatorPlugin` | RuneLite lifecycle and event wiring only. |
| Application | `TranslationStore`, `MenuEntryTranslator`, `WidgetTranslator`, `TranslatorAPI`, `InterfaceTextCapture` | Translation lookup, menu/dialogue rewriting, journal layout, download/capture orchestration. |
| Domain/value | `MenuTarget`, `TranslationType`, `WidgetReflow` | Pure parsing, classification and text layout. No I/O. |
| Configuration | `TranslatorConfig`, `TranslationSettings` | User settings and in-memory settings view. |
| Edge adapter | `server/server.mjs`, `server/worker.mjs` | Opt-in collection API, queue and translation marks. No plugin business rules. |

## Dependency injection

RuneLite creates the plugin through Guice. All collaborators are injected:

- `@Singleton` for long-lived stores and adapters;
- `@Inject` constructors or fields for components;
- no static singleton accessors, service locators or module-level mutable state in Java.

`new` is reserved for value objects, exceptions, DTOs and builders. If a class needs a
collaborator that is expensive, stateful or replaceable in a test, it must be injected.

## Dependency direction

```
RuneLite events / Swing
        │
        ▼
application services ──► domain/value objects
        │
        ▼
external adapters (HTTP, files, collection API)
```

Application code must not import Swing widgets or RuneLite event classes. The entry point may
call application code, but application code must not call back into the entry point.

## External boundaries

- **Translation repository**: read-only manifest and dictionaries; SHA-256 verified before use.
- **Collection server**: opt-in text upload and queue API. It is an adapter with its own
  deployment, not a separately owned domain service.
- **iolave data**: legacy read-only dictionary source for types not migrated yet.

These boundaries are allowed to be separate processes for availability and security. They do
not justify splitting the plugin into microservices.

## Rules for new code

1. Put behaviour in an injected component, not in the event handler.
2. Keep I/O behind a named adapter or method that can be tested with a local server.
3. Do not add a new process, queue, database or network hop without an ADR.
4. Do not introduce a second source of truth for translations.
5. Keep the hot menu path allocation-free when translation is disabled.

See [ADR-0001](adr/0001-hosting.md) for the hosting decision.
