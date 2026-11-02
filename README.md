# Translator Enhanced

Translator Enhanced is a community-maintained continuation of the **Translator** plugin by
[iolave](https://github.com/iolave/runelite-the-translator). It translates Old School
RuneScape dialogues, right-click menus and the contents of the quest journal, achievement
diaries and combat achievements.

## Why this project exists

Translator Enhanced started as a fork and became an independent plugin. The reasons are
practical:

* the original plugin has not received code changes since January 2026;
* its translation pipeline depended on an external machine translation API that now answers
  with `400`s, so the automatic translations stopped arriving;
* the quest journal, achievement diaries and combat achievements were never part of that
  pipeline: their texts stayed in English;
* translated journal tasks broke the layout: the requirement disappeared, long tasks
  overlapped the next one, and the scroll did not reach the last tier (Elite).

The fixes and the new content outgrew a fork: a different scope, a different data pipeline
and a different maintenance model. Rather than keep patching a branch that nobody can
review, the project continues here under its own name, with the same license and a clear
credit to the original author.

What we keep from the original: the BSD-2-Clause license, the supported menu/dialogue
languages, and the file format of `iolave/osrs-translations`, so the existing data keeps
working.

## What changed

**Journals are translated with a reviewed dictionary.** The quest journal, achievement
diaries and combat achievements are not machine translated. Their texts are full of proper
nouns, and automatic translation mangles them (`Duke Horacio` -> `Duque Horacio`,
`Zulrah` -> `Desierto`). The plugin ships a curated dictionary of nearly a thousand reviewed
Spanish entries and translates only what is in it; everything else stays in English.

**Requirements are never lost.** The game renders the requirement of a diary task in a
separate widget, and stops creating it once the task is translated. The plugin now appends
the requirement to the translated text, keeps it visible, and reflows the list accordingly.

**The layout holds.** Translated tasks that are longer than the original are wrapped to the
panel width, the whole list is re-laid out from the top, empty widgets are collapsed and the
scroll height is recalculated, so the last tier is reachable and nothing overlaps.

**Menus are translated at render time.** `MenuEntryAdded` is too early: Menu Entry Swapper
decides what to swap by reading the English option and target. Translating at render time
(`MenuOpened` / `BeforeMenuRender`) keeps its ordering intact and still translates what the
player sees.

**No runtime machine translation.** The real-time engine, the Google fallback and the
"translate missing texts" options are gone. Machine output was not precise enough to be
shipped, and it is not needed when the dictionary is good: the plugin only applies reviewed
text.

**A first pass that can be trusted.** Translations are proposed by an LLM with the project
rules and eight reviewed examples as the prompt, then validated (tags, numbers, placeholders,
line breaks) before a maintainer reviews the pull request. Nothing is merged automatically.

**Maintenance tooling.** A worker, a merge script, a test suite that rejects broken entries,
a GitHub Action that opens a pull request, and a collection service that aggregates the
texts players actually see.

## Features

* **Dialogue**: NPC and player lines in the chatbox, plus dialogue options (off by default).
* **Right-click menus**: options such as `Talk-to`, `Examine`, `Bank` (on); NPC, item and
  object names (off, each one independent).
* **Journals**: quest journal, achievement diaries and combat achievements, with
  requirements and difficulty preserved.
* **Languages**: Dutch, Finnish, French, German, Italian, Portuguese, Spanish and Swedish.
  Menus and dialogues use the upstream files; journals use one dictionary per language,
  grown by the same shared queue so every language reaches the same coverage.
* **Local corrections**: fix a bad entry without waiting for a release.
* **Dynamic loading**: translation files are downloaded only for the feature groups you
  enable, so the plugin does not keep 180.000 unused entries in memory.

### Why names are off by default

Translating an NPC, item or object name changes what you read, but not what the rest of the
world calls it: other players, the wiki and every guide still use the English name. That is
confusing while trading, following a quest guide or asking for help. The default is:
translate the **text**, keep the **names**. Enable the ones you care about in `Menus`.

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
* **Data and code are separate.** The dictionary is bundled as a fallback and also
  downloaded from the project repository (a fixed address, not a user setting), so
  translation changes do not need a plugin release; only code changes do.

Details: [docs/translations.md](docs/translations.md),
[docs/translation-rules.md](docs/translation-rules.md) and
[server/README.md](server/README.md).

## Configuration

| Section | Option | Default |
| --- | --- | --- |
| General | Language | Spanish |
| General | Enable text capture (opt-in) | off |
| Menus | Menu options | **on** |
| Menus | NPC names | off |
| Menus | Item names | off |
| Menus | Object names | off |
| Text | Chatbox: dialogues | **on** |
| Text | Chatbox: dialogue options | off |
| Journals | Translate journals | **on** |
| Journals | Capture untranslated | off |
| Journals | Download translations | **on** |
| Translation quality | Use corrections | **on** |
| Translation quality | Reload translations | - |

## Integration with other plugins

Menu entries are modified **right before the menu is drawn**, never when they are added, so
Menu Entry Swapper and Quest Helper keep working.

Two limits, both by design:

* **Dialogue options** are off by default: Quest Helper picks the answer by matching the
  English text, so translating it breaks its auto-answer.
* Some plugins compare the clicked option text (`MenuOptionClicked`) and will not react to a
  translated option. Turn `Menus -> Menu options` off if one misbehaves.

## Privacy

The plugin works offline with the bundled data. It only contacts the network for the
features you enable:

* downloading the translation files you selected;
* downloading the dictionary (can be turned off);
* uploading untranslated texts, only when `Enable text capture` is on.

The upload sends the text, its type and the IP used for rate limiting. Nothing else. The
plugin is a RuneLite plugin: it has no telemetry.

## Development

Requires JDK 11 or newer.

```bash
./gradlew compileJava   # compile
./gradlew test          # unit tests
./gradlew run           # development client with the plugin loaded
```

The `run` task loads the plugin in a development client, so it can be tested without
installing anything.

### Finding a text that is not translated

1. Open that screen in game.
2. Enable `Journals -> Capture untranslated` (local file) or `General -> Enable text capture`
   (shared queue).
3. Run `./scripts/merge-captures.ps1 -Language spanish` or the worker, and translate what it
   reports.
4. If the whole interface is ignored, add its root to `WidgetTranslator.QUEST_INTERFACES`.

### Architecture

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

## Contributing

* **Translations**: follow [docs/translation-rules.md](docs/translation-rules.md). The rules
  are enforced by `InterfaceDictionaryTest`, so a malformed entry fails the build.
* **Texts**: enable `Enable text capture` and play the content; it will appear in the queue.
* **Corrections**: put them in `~/.runelite/translator/overrides/<language>.txt` and open a
  pull request if they are generally useful.
* **Code**: [docs/plugin-hub.md](docs/plugin-hub.md) explains how the plugin is published.

## Credits

* [iolave](https://github.com/iolave) for the original Translator plugin, the translation
  file format and the `osrs-translations` data repository.
* Every player who enabled text capture or reported a bad translation.

## License

BSD-2-Clause. See [LICENSE](LICENSE).
