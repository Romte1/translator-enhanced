# Contributing

## Development

Requires JDK 11 or newer.

```bash
./gradlew compileJava   # compile
./gradlew test          # unit tests
./gradlew run           # development client with the plugin loaded
```

The `run` task loads the plugin in a development client, so it can be tested without
installing anything.

## Project layout

```
TranslatorPlugin      events only: wires RuneLite events to the components below
├─ TranslationStore   keeps the dictionaries in memory, applies corrections
├─ MenuEntryTranslator right click menus (MenuTarget splits colour tags/name/suffix)
├─ WidgetTranslator   chatbox dialogues, dialogue options, journals
└─ TranslatorAPI      HTTP: dictionary download and data collection

server/server.mjs               self-hosted collection service
server/worker.mjs               the same service for Cloudflare Workers + D1 (free)
```

Everything that is not an event handler lives in its own class. Dictionaries are immutable
once published, so the client thread never sees a half-loaded map, and the hot path
(`MenuEntryAdded`) is a single field read when menu translation is disabled.

## Translation data

The reviewed menu and interface dictionaries live in a separate repository:
[translator-enhanced-translations](https://github.com/Romte1/translator-enhanced-translations).
Its manifest pins every file with SHA-256. The plugin downloads the selected dictionaries,
validates them, caches the last valid copy and keeps the bundled interface dictionary as an
offline fallback.

Translation workers, rules, validation and GitHub Actions live in that repository. The
plugin repository only contains the client code and the small interface fallback.

```
player opens a journal
        │
        ▼
  exact match? ── yes ──► reviewed translation applied
        │ no
        ▼
  text captured locally and (opt-in) sent to the collection service
        │
        ▼
  queue aggregated from every client, deduplicated, ordered by how many players saw it
        │
        ▼
  worker: rules + reviewed examples + LLM ──► validation ──► pull request ──► CI ──► merge
        │
        ▼
  clients download the dictionary on start, no release needed
```

* **Collection is opt-in** (`General -> Enable text capture`, off by default) and carries the
  required warning. Only the text, the type and an anonymous hash are stored; no account, no
  player name. Texts that already have a translation are never sent.
* **Nothing ships unreviewed.** The worker only proposes; the pull request is reviewed and
  the translation repository validator checks tags, numbers, placeholders, duplicates and
  the manifest.
* **Data and code are separate.** Menu and interface dictionaries are downloaded from the
  translation repository; only the interface dictionary has a bundled fallback. Translation
  changes therefore do not need a plugin release.
* **Per-language queues.** The source texts are shared; the "translated" mark is per target
  language, and each language has its own dictionary file.

Details: [translation repository](https://github.com/Romte1/translator-enhanced-translations)
and [server/README.md](server/README.md).

## Finding a text that is not translated

1. Open that screen in game.
2. Enable `Journals -> Capture untranslated` (local file) or `General -> Enable text capture`
   (shared queue).
3. Work in the
   [translation repository](https://github.com/Romte1/translator-enhanced-translations):
   run its `merge-captures.ps1` or worker and translate what it reports.
4. If the whole interface is ignored, add its root to `WidgetTranslator.QUEST_INTERFACES`.

## Translation rules and workers

The rules, workers, validation and GitHub Actions live in the
[translation repository](https://github.com/Romte1/translator-enhanced-translations). See its
README for the worker commands, secrets and manifest workflow.

## Publishing on the Plugin Hub

[docs/plugin-hub.md](docs/plugin-hub.md) explains the marker file, the commit pin and the
submission steps.
