# F-05 Nuvio / SkyStream / Vega repositories (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.
**Vega is not designed here**: the owner was asked what "Vega" means (OMT-24, Blocked: owner question).

## 1. Current code (read 2026-11-18, head d485b4f)

- `sources/UniversalAdd.kt`: one "Add anything" field detects CloudStream `.cs3` / `repo.json` /
  `plugins.json` / `cloudstreamrepo://`, Aniyomi/Mihon repos and APKs, Stremio `manifest.json` /
  `stremio://`, Mangayomi JS `index.json` / `mangayomi://`, Jellyfin/Emby/Plex. Detection is pure and
  unit-tested (`hint`, `sniff`, `candidates`).
- `sources/js/` (813 lines): Mangayomi-format JavaScript on QuickJS (`quickjs-kt-android` 1.0.18,
  `gradle/libs.versions.toml:144`) with `JsBridge`, `JsCrypto`, `JsProviders`.
- No Nuvio scraper format, no SkyStream `.sky` plugin format. No TMDB key in the app (see F-08).
- Extension safety: `online/ExtensionGuard.kt`, `ExtensionScanner.kt`, `ExtensionReview.kt`.

## 2. Competitors (ideas and contracts only; both GPL-3.0, no code copied)

Nuvio `tapframe/NuvioStreaming` at 511ebd5f175dd39e31b9cc0e7b259259e887cf6f:

- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/plugins/PluginModels.kt` (218 lines): repository
  = `manifest.json` `{name, version, description, author, scrapers[]}`; scraper = `{id, name, version,
  filename, supportedTypes [movie, tv], enabled, hasSettings, logo, contentLanguage, formats,
  supportsExternalPlayer, limited, supportedPlatforms, disabledPlatforms}`; refresh every 6 h; result =
  `{title, name, url, quality, size, language, provider, type, seeders, peers, infoHash, headers,
  subtitles}`.
- `composeApp/src/androidFull/.../plugins/runtime/PluginRuntime.android.kt`: the same QuickJS binding
  Yumeiro uses (`com.dokar.quickjs.QuickJs`). Plugins are a "full" flavour only (Play flavour has none).

SkyStream `akashdh11/skystream` at f5523ae07d729b579be30976df8783a0b98c4559 (Flutter):

- `lib/core/extensions/models/extension_repository.dart`: SkyStream repo = `{name, description, iconUrl,
  manifestVersion, pluginLists[], repos[] (included repos), packageName}` plus short links; plugins are
  `.sky` / `.js` files; `extension_plugin.dart`: per-plugin settings (`key, title, type, defaultValue,
  options, reloadOnChange, isBaseUrl`).
- `lib/core/nuvio/data/nuvio_runtime.dart`: runs Nuvio scrapers by calling
  `getStreams(tmdbId, mediaType, season, episode)` in isolated workers, 60 s default timeout (20 s for
  lighter calls), with Cheerio/DOM polyfills (`nuvio_polyfill.dart`, `nuvio_dom.dart`).
- `lib/core/nuvio/data/nuvio_tmdb.dart`: Nuvio scrapers need TMDB ids; a user-entered TMDB key
  (`nuvio_tmdb_api_key`, falls back to the app key).

Weaknesses: Nuvio scrapers get full network and a 60 s budget per call; SkyStream runs every plugin with
broad polyfills and stores the key in preferences; both depend on TMDB ids.

## 3. Design

- **Detection only first** (lowest risk): `UniversalAdd` learns Nuvio `manifest.json` (has `scrapers[]`
  with `filename`) and SkyStream repo JSON (`pluginLists` with `.sky`/`.js` entries, `manifestVersion`), so
  pasting either shows the right name instead of "not recognised". Pure, unit-testable.
- **Runtime** (second step, ADR required: touches extensions): Nuvio scrapers run on the existing QuickJS
  engine in a separate runtime per call, inside `ExtensionGuard` limits: 20 s per scraper, 4 at a time
  (2 on low-RAM), network through Yumeiro's OkHttp with the same safe-request rules, no file access. A
  small Cheerio-compatible bridge only if the owner approves the size (APK budget).
- **TMDB ids**: the scrapers need them; Yumeiro would map a title to a TMDB id through the server proxy
  (F-08 option C, owner key) or through a key the user enters (stored encrypted, never logged). Without an
  id the Nuvio sources stay hidden, not broken.
- **SkyStream `.sky` plugins**: format and API surface to be read in full before any runtime; proposal for
  now is detection plus a clear "not supported yet" message.
- Results merge into the existing source list with provider name, quality and language (feeds F-01
  badges). Feature flag `nuvio_scrapers` with kill switch. Backup covers repositories (existing SOURCES
  section).

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Paste a Nuvio manifest URL | Shown as "Nuvio repository (N scrapers)"; install asks for review like other kinds |
| Paste a SkyStream repo | Recognised; "SkyStream plugins are not supported yet" until the runtime is approved |
| Scraper slow / hangs | Stopped at 20 s; other sources still shown |
| Scraper throws | Logged with the extension name (no URLs with tokens), source skipped |
| No TMDB id | Nuvio sources hidden for that title, a note in the source sheet |
| Repo unreachable | Last cached list kept; retry after 6 h or on pull to refresh |
| Low memory | Fewer scrapers at once; runtime closed after each call |
| Kill switch off | Nuvio sources disappear, data kept |

## 5. Pros and cons

- Detection first: + no new runtime risk, users get a clear message; − no new sources yet.
- QuickJS reuse: + no new engine, same sandbox; − Nuvio scrapers written for Node-like polyfills may fail.
- TMDB dependency: + matches the scraper contract; − needs an owner key or a user key.
- Risk at 200k users: scraper code is third-party; Yumeiro ships none and repositories are user-added.

## 6. Tests (when approved)

Unit (pure logic, stated): `UniversalAdd.sniff` for Nuvio manifest and SkyStream repo JSON, fail-before on
the current code (returns unknown), pass-after. Device: install a local test manifest from the CI fixture
server, run a fixture scraper, timeout and throw cases on api33-lowram.

## 7. Owner decision

Approve detection step; decide on the runtime step and the TMDB key path; answer OMT-24 (Vega).
