# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v2.0.0] - 2026-11-01

First release as an independent plugin, **Translator Enhanced**, based on Translator 1.4.0
by iolave.

### Added
- Reviewed Spanish dictionary for quest journals, achievement diaries and combat
  achievements (nearly a thousand entries), bundled with the plugin and downloadable at
  runtime.
- Per-language journals dictionary and queue: every supported language grows with the same
  collection server, and the translated state is tracked per language.
- Diary requirements are preserved and the journal list is re-laid out: long tasks wrap, the
  empty requirement widgets collapse and the scroll reaches the last tier.
- The dictionary is downloaded on start from the project repository, so translation updates
  no longer need a plugin release.
- scripts/translate-worker.mjs: LLM first pass with the project rules, reviewed examples
  and mechanical validation of tags, numbers and placeholders.
- scripts/merge-translations.mjs and server/: a collection service that aggregates the
  texts every client sees and marks them as translated once handled.
- .github/workflows/translate.yml: scheduled or manual run that opens a pull request.
- Tests for the dictionary, the parsers and the menu target.

### Changed
- Menus are translated at render time (MenuOpened / BeforeMenuRender) instead of
  MenuEntryAdded, so Menu Entry Swapper keeps its ordering.
- Journal texts are matched ignoring colour tags and line breaks, so a task keeps its
  translation when the game rewraps it.
- Config group is 	ranslator-enhanced; Java 11; logging follows the RuneLite guidelines.

### Removed
- Real-time machine translation (pi.datpmt.com, Google fallback), chat message
  translation and the options that enabled them.
- The interface inspector and the debug options that relied on reflection.

## [v1.3.0] - 2026-01-20

### Added
- Dialogue translation can now be disabled.
- Translation files are now loaded dynamically from [iolave/osrs-translations](https://github.com/iolave/osrs-translations).
- Added real time translation for chat box messages and quest guide journal.

### Changed
- Renamed `SelectLanguage` enum to `Language`.
- TranslatorAPI now uses the `/api/v1/data-collection` endpoint to collect dialogues.
- Translation maps were consolidated into a single map to allow easier implementation of new translation maps.
- Menu entries are now translated when added to the stack rather than when the menu is opened.
- Plugin confiuration now have sections.

### Fixed
- Dialogues after the first one are now collected correctly.
- Collected dialogues with player name are now collected with the name repalced by `[PLAYER_NAME]`.

## [v1.2.0] - 2026-01-14

### Added
- Added Portuguese support.
- Portuguese translation for dialogues, items, NPCs, and objects.

### Changed
- Reduced Finnish translation file sizes.

### Fixed
- Incorrect schedule time on plugin change.

### Removed
- Removed unused resource files.

## [v1.1.0] - 2026-01-07

### Added
- Added an option to enable/disable dialogue options translation.
- Added opt-in data collection via runelite-translator-api.
- Added spanish support.
- Data collection is now opt-in.

### Removed
- Removed sys outs.

## [v1.0.0] - 2025-12-03

### Added

- dialouge translation with support for finnish and spanish.
