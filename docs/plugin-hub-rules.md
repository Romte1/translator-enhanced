# RuneLite Plugin Hub rules

Source: [elzerma/farm-run-autopilot AGENTS.md](https://github.com/elzerma/farm-run-autopilot/blob/master/AGENTS.md),
based on Jagex's Third-Party Client Guidelines and RuneLite's rejected/rolled-back features.
This checklist is mandatory for plugin changes.

## Logging

- Use `log.debug()` for diagnostics.
- `log.info()` only for one-time startup/shutdown or infrequent events; never per frame or
  per event.
- `log.warn()`/`log.error()` for failures.

## Threading and concurrency

- No `Thread.sleep()`.
- Do not block `startUp()`/`shutDown()`; use `shutdownNow()`, no `awaitTermination()`.
- No blocking network or disk I/O on the client thread.
- Use the OkHttp thread pool for blocking HTTP; prefer `enqueue()`.
- Call back into `client` from background threads through `clientThread.invoke()`.
- Cancel scheduled tasks (`ScheduledFuture`) on shutdown.
- Use `CompletableFuture.allOf()` for batching, not `CountDownLatch`.
- Any `Process.waitFor()` needs a timeout.

## Performance

- Do not scan the scene every tick/frame; use object/NPC spawn events and maintain state.
- Keep per-frame overlay work minimal.
- Avoid rebuilding large maps/sets on every render or tick.

## API usage

- Use `net.runelite.api.gameval` constants (`ItemID`, `InterfaceID`, `ObjectID`, ...); no magic
  game IDs.
- Use `LinkBrowser` for URLs, not `java.awt.Desktop`.
- Look up widgets through gameval component IDs, not manually combined group/child IDs.
- Java reflection is forbidden.

## HTTP and JSON

- Use injected `OkHttpClient`; no `HttpURLConnection`, `java.net.http.HttpClient` or Apache
  HttpClient.
- Use injected `Gson`; derive with `.newBuilder()` if needed, never create a new base Gson.
- Do not add transitive `runelite-client` dependencies (gson, guice, okhttp) to `build.gradle`.
- Never execute OkHttp calls on the client thread; prefer `enqueue()`.

## File I/O

- All file I/O uses `Filepath`; set `PluginDescriptor.internalName`.
- Use `Filepath.Chooser`, not `JFileChooser`.
- Set `PluginDescriptor.legacyDataDirectory` when migrating a legacy data folder.

## Config

- Config group names must be specific.
- Never rename a config key or group without a migration.
- Any `@ConfigItem` that enables a feature involving a third-party server must be disabled by
  default and use the exact warning: `This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers`.

## Plugin setup and packaging

- Rename template package, classes, config group, Gradle group, settings project and
  `runelite-plugin.properties`.
- No `META-INF/services/net.runelite.client.plugins.Plugin`.
- Do not commit build artifacts.
- `build.gradle` targets Java 11 and follows the example-plugin structure.
- Keep a permissive license (BSD-2).

## Resources

- Optimize icon PNGs; Java loads full resolution.
- Verify PNGs are real PNGs.

## Cleanup

- Remove unused config/classes/imports.
- Cancel subscriptions, listeners, overlays and scheduled work in `shutDown()`.
- Do not mix reformatting with functional changes.

## Testing

- Agents cannot verify in-game behaviour and must not automate RuneScape input.
- Offer `./gradlew run`, point the user to the Jagex Accounts development-client guide, state
  exactly what to test, and wait for confirmation.
- A clean JVM start is not a passing test.

## Submitting

- Append an attribution footer to the Plugin Hub PR description, for example:
  `Generated-by: Claude Code`.

## Forbidden or restricted features

- Java 11 only; no reflection, JNI/JNA, Unsafe/LWJGL native memory, external processes,
  dynamic/classloading, runtime code generation or Java serialization.
- No boss/combat helpers: attack prediction, projectile/landing indicators, prayer switching,
  attack counters, automatic tile indicators, extra boss-mechanic indicators, future-hazard
  warnings, flinch helpers, prayer recommendations, NPC focus, content simulation.
- No PvP helpers: target scouting, freeze timers, clan/opponent identification, loot previews,
  attackable/level indicators, spell-target simplification, removal/deprioritising of
  attack/cast options.
- No menu actions sent to the server; no conditional menu-entry removal based on NPC/friend
  status.
- No interface click-zone changes for combat, inventory, equipment, spellbook or prayer; no
  unhiding hidden components; no detached-camera world interaction.
- No input injection or autotyping; no modifying outgoing chat.
- No exposing player information over HTTP; no crowdsourcing other players' data; no
  credential storage.
- No adult/sexual content; no plugins whose entire functionality depends on player-provided
  IDs.
