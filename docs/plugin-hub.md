# Publishing on the Plugin Hub

The plugin is a separate project now: **Translator Enhanced**, with its own config group
(`translator-enhanced`) so it does not share settings with the original Translator.

## 1. Rename the repository

GitHub -> Settings -> Repository name: `translator-enhanced`. GitHub keeps redirects from
the old URL, but update the references anyway:

* `General -> Dictionary URL` (already points at `Romte1/translator-enhanced`);
* `.github/workflows/translate.yml` has no repository name;
* `README.md` badges and links.

## 2. Push and check the build

```bash
./gradlew build
./gradlew run
```

In the client: the plugin appears as **Translator Enhanced**, its settings are empty (new
config group) and the journal/diaries translate.

## 3. Meet the hub requirements

The hub reviews source, so everything is built from this repository:

* no reflection, no bytecode tricks, no `java.awt.Robot`;
* no third-party dependencies beyond RuneLite and Lombok;
* network access is opt-in and carries the `warning` text (the capture upload and the
  dictionary download);
* every config group and key is namespaced (`translator-enhanced`);
* a license file is present (BSD-2-Clause inherited from the original project);
* the plugin does not send anything while `Enable text capture` is off (default).

## 4. Open the submission

1. Fork `https://github.com/runelite/plugin-hub`.
2. Add `plugins/translator-enhanced` (no extension) with the commit you want reviewed:

   ```properties
   repository=https://github.com/Romte1/translator-enhanced.git
   commit=<40-character commit SHA>
   ```

3. Open a pull request. The bot builds the plugin and a reviewer checks the code.

When the PR is merged the plugin appears in the hub and users install it like any other. The
hub keeps a pinned commit, so **translation data still updates without a release** through
`General -> Dictionary URL`; the pin only affects code.

## 5. After the first release

* Keep the `master` branch buildable: the hub rebuilds from the pinned commit.
* Bump `version` in `build.gradle` for code changes.
* Open a new hub PR with the new commit to update the pin.
