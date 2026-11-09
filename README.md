# Translator Enhanced

Translates Old School RuneScape in-game text: NPC and player dialogue, right-click menus and
the contents of the quest journal, achievement diaries and combat achievements.

## Why this plugin exists

Translator Enhanced continues the [Translator](https://github.com/iolave/runelite-the-translator)
plugin by iolave. The original has not received code changes since January 2026 and its
automatic translation service stopped responding, so its translations gradually went stale.
The quest journal, achievement diaries and combat achievements were never translated at all:
those screens stayed in English.

This project keeps the same idea and the same menu and dialogue data, and adds what was
missing: a reviewed dictionary for the journals, requirements that stay visible, a layout
that holds when a task is longer than the original, and translation updates that arrive
without a plugin release.

## Features

* **Dialogue**: NPC and player lines in the chatbox, plus dialogue options (off by default).
* **Right-click menus**: options such as `Talk-to`, `Examine`, `Bank` (on); NPC, item and
  object names (off, each one independent).
* **Journals**: quest journal, achievement diaries and combat achievements, with
  requirements and difficulty preserved.
* **Languages**: Dutch, Finnish, French, German, Italian, Portuguese, Spanish and Swedish.
* **Local corrections**: change any translation yourself without waiting for an update.
* **Dynamic loading**: only the translation files for the features you enable are downloaded,
  so the plugin does not keep 180.000 unused entries in memory. The interface dictionary also
  has a bundled fallback.

### Why names are off by default

Translating an NPC, item or object name changes what you read, but not what the rest of the
world calls it: other players, the wiki and every guide still use the English name. That is
confusing while trading, following a quest guide or asking for help.

The default is: translate the **text**, keep the **names**. Enable the ones you care about
one by one in `Menus`.

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
| Text | Quest Helper integration | off |
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
  English text, so translating it breaks its auto-answer. If you use Quest Helper, enable
  `Text -> Quest Helper integration`: the conversation is translated but the answer options
  stay in English while Quest Helper is active. It is a compromise, not a full integration;
  leave it off if you prefer translated options and answer manually.
* Some plugins compare the clicked option text (`MenuOptionClicked`). The plugin restores the
  original English option before those handlers run, so this is normally not a problem.

## Improving translations

You do not have to wait for an update to fix a bad translation:

1. Create `~/.runelite/plugin-data/translator-enhanced/overrides/<language>.txt`, for example
   `~/.runelite/plugin-data/translator-enhanced/overrides/spanish.txt`.
2. Add one correction per line, in the same format as the translation files:

   ```
   # original;translation
   Wield;Equipar
   Bank;Abrir banco
   ```

3. Enable `Translation quality -> Reload translations`.

Priority, from lowest to highest: the downloaded file, the corrections bundled with the
plugin, and your own corrections. If a correction is generally useful, send it as a pull
request to the
[translation repository](https://github.com/Romte1/translator-enhanced-translations) so
everybody gets it.

## Translation data

The reviewed menu and interface dictionaries are maintained in
[translator-enhanced-translations](https://github.com/Romte1/translator-enhanced-translations).
The plugin downloads them, validates the SHA-256 from the repository manifest, caches the
last valid copy and uses the bundled interface dictionary as a fallback.

## Privacy

The plugin can start offline: the interface dictionary has a bundled fallback and downloaded
dictionaries are cached. It contacts the network for the features you enable:

* downloading the menu and interface dictionaries from the translation repository;
* uploading untranslated texts, only when `General -> Enable text capture` is on.

The upload sends the text, its type and the IP used for rate limiting. Nothing else: no
account, no player name, no telemetry.

## Contributing

Bug reports, code and translation corrections are welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the development setup, the translation rules and how
the data pipeline works.

## Credits

* [iolave](https://github.com/iolave) for the original Translator plugin, the translation
  file format and the [osrs-translations](https://github.com/iolave/osrs-translations) data.
* Every player who enabled text capture or reported a bad translation.

## License

BSD-2-Clause. See [LICENSE](LICENSE).
