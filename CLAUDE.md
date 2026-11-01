# CLAUDE.md — context for AI agents working on this repo

MinecraftRPG: a Paper plugin that turns a Minecraft server into a "Skyrim-like" RPG where the **admin configures
everything in-game through chest menus** and players just play. It must work for **Java and Bedrock** players
(Bedrock joins through GeyserMC + Floodgate).

The owner (GitHub `Dathis`) writes in **Russian**. Reply in Russian; in-game texts, README and `docs/` are in Russian.
Code identifiers and comments: comments are in Russian, keep that style.

## Read first
- `docs/ARCHITECTURE.md` — how the code is organized (modules, menus, storage, combat pipeline).
- `docs/ADMIN-GUIDE.md` — what each feature does for the admin (useful to understand expected behaviour).
- `docs/ROADMAP.md` — what is done and what is planned next.

## Stack
- Paper API **1.21.8** (`paper-api:1.21.8-R0.1-SNAPSHOT`), **Java 21**, Gradle 8.14.3 (Kotlin DSL, wrapper committed).
- Plugins: `com.gradleup.shadow`, `xyz.jpenilla.run-paper` **3.0.2** (3.1+ needs Gradle 9), foojay toolchain resolver.
  `gradle/gradle-daemon-jvm.properties` forces the Gradle daemon onto Java 21 (the owner's PC defaults to Java 8).
- Floodgate API is `compileOnly` + `softdepend`; the plugin must work without it.
- `plugin.yml` (not `paper-plugin.yml`), main class `com.dathis.rpg.RpgPlugin`, commands declared there.

## Build and test
- `./gradlew build` → `build/libs/minecraft-rpg-<version>.jar`. CI: `.github/workflows/build.yml` (build on push to main and PRs).
- **Cloud agents cannot build locally**: `repo.papermc.io` and `repo.opencollab.dev` are blocked by the sandbox network
  policy. Push a branch and rely on the GitHub Actions run to verify compilation. Optionally sanity-check syntax with
  `javac` against hand-written stubs.
- Owner tests on Windows with `test-server.bat` (→ `scripts/test-server.ps1`): finds/installs Java 21, builds the plugin,
  downloads Paper 1.21.8 (Fill v3 API, needs a User-Agent), Geyser and Floodgate into `test-server/` (git-ignored),
  starts the server. Java client must be 1.21.8 (or add ViaVersion).

## Hard rules (Bedrock compatibility)
1. All UI is **chest menus** (`core/gui/Menu`), chat, titles, action bar, boss bars, real entities. No resource packs,
   custom fonts, Display entities (Geyser doesn't render them), books with clickable text as the only path, client mods.
2. Menu buttons react to a **single plain click** only. No right-click / shift-click / number-key semantics on buttons
   (awkward on Bedrock touch). Exception: "input slots" accept an item placed from the cursor, and shift-click from the
   player's own inventory is a shortcut, never the only way.
3. Text and numbers are entered **through chat** with `ChatInput.ask(...)` ("отмена" cancels).
4. Avoid unusual unicode glyphs in item names/lore (Bedrock font may lack them); colours: prefer the 16 named colours.

## Conventions
- New feature = a module in `modules/<name>/` extending `AbstractModule`, registered in `RpgPlugin.onEnable`,
  toggle `modules.<id>` in `config.yml`. Use `listen(...)` so listeners are unregistered on disable; cancel tasks in `onDisable`.
- Templates (items, mobs) live in YAML via `YamlStore` (`plugins/MinecraftRPG/data/<name>.yml`). Spawned entities and
  given ItemStacks carry **only the template id** in the PersistentDataContainer, and behaviour is looked up live,
  so editing a template affects existing items/mobs.
- New config keys: add them with comments to `src/main/resources/config.yml` and read with a default value
  (`getConfig().getX(path, default)`), because old servers keep their old `config.yml`.
- Commands: admin permission `rpg.admin`. Add new commands to `plugin.yml`.
- Update `README.md` and `docs/` when behaviour changes.

## Git / PR workflow
- Repo: `Dathis/minecraft-rpg`, default branch `main`. Work on a feature branch, open a PR, wait for green CI.
- The owner tests in-game and says "мерджи" to approve; squash-merge after that.
- Commit messages are in Russian, short subject + body.
