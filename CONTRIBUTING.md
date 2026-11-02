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
scripts/translate-worker.mjs    LLM first pass with rules and validation
scripts/merge-translations.mjs  appends the reviewed output to the dictionary
```

Everything that is not an event handler lives in its own class. Dictionaries are immutable
once published, so the client thread never sees a half-loaded map, and the hot path
(`MenuEntryAdded`) is a single field read when menu translation is disabled.

## How translations are maintained

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
* **No client has priority.** Every text is keyed by `sha256(type + text)` and counted, so
  the queue starts with what most players actually see, whoever sent it.
* **Nothing ships unreviewed.** The worker only proposes; the pull request is reviewed and
  CI validates tags, numbers, placeholders and duplicates.
* **Data and code are separate.** The dictionary is bundled as a fallback and also downloaded
  from the project repository at a fixed address, so translation changes do not need a plugin
  release; only code changes do.
* **Per-language queues.** The source texts are shared; the "translated" mark is per target
  language, and each language has its own dictionary file.

Details: [docs/translations.md](docs/translations.md),
[docs/translation-rules.md](docs/translation-rules.md) and
[server/README.md](server/README.md).

## Finding a text that is not translated

1. Open that screen in game.
2. Enable `Journals -> Capture untranslated` (local file) or `General -> Enable text capture`
   (shared queue).
3. Run `./scripts/merge-captures.ps1 -Language spanish` or the worker, and translate what it
   reports.
4. If the whole interface is ignored, add its root to `WidgetTranslator.QUEST_INTERFACES`.

## Translation rules

Every contribution follows [docs/translation-rules.md](docs/translation-rules.md). The rules
are enforced by `InterfaceDictionaryTest`: a malformed entry (wrong tags, numbers,
placeholders or duplicate keys) fails the build.

## Worker and server

```bash
# local first pass, no server
node scripts/translate-worker.mjs spanish

# from the shared queue
PENDING_URL=https://<server>/api/v1/pending \
PENDING_TOKEN=... LLM_API_KEY=... node scripts/translate-worker.mjs french

# review, then append to the dictionary
node scripts/merge-translations.mjs french
```

The worker defaults to Groq (`openai/gpt-oss-120b`). Put the key in `.env` at the repository
root (git ignored). Any OpenAI compatible endpoint works through `LLM_BASE_URL` and
`LLM_MODEL`; `--dry-run` shows the prompt and the batches without sending anything.

`SEED_FROM=spanish` adds the keys of the Spanish dictionary to the queue of another language,
which is how a new language reaches the same coverage without waiting for a player to open
every screen. `LIMIT` caps how many texts a single run handles.

`.github/workflows/translate.yml` runs the worker for every supported language on manual
dispatch and every Monday, and opens one rolling pull request per language. Marking texts as
translated happens only after the merge, in `mark-translated.yml`.

## Publishing on the Plugin Hub

[docs/plugin-hub.md](docs/plugin-hub.md) explains the marker file, the commit pin and the
submission steps.
