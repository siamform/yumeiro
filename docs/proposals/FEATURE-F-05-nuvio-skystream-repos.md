# F-05 Nuvio, SkyStream and Vega repositories

Stream: D-sources. Status: decided by agent (was waiting on OMT-25).

Decisions:
1. "Vega" means the Vega streaming app by Zenda-Cross and its provider repository format
   (`github.com/Zenda-Cross/vega-providers`, `manifest.json`). It does not mean Amazon's Vega OS.
2. Step 1 now: detect all three repository formats in Universal add and fix a real bug (a
   SkyStream repository is detected as a CloudStream repository today). Show a clear "supported
   later" message instead of a broken install.
3. Step 2: build one hardened JS sandbox (time limit, memory limit, private-network guard, no
   file access) and use it first in Yumeiro's existing JS engine.
4. Step 3: run Nuvio scrapers (stream-only, keyed by the TMDB id Yumeiro already has).
5. Step 4: run Vega providers (catalog, search, meta, streams) on the same sandbox.
6. SkyStream `.sky` plugins last, only if users ask; detection message until then.
7. No default repositories shipped. User-added only, with the existing consent sheet. Streams
   that carry provider-supplied DRM keys are not played.

## 1. Current code

Paths relative to `app/src/main/java/dev/asfakul/yumeiro`.

- `sources/UniversalAdd.kt`: `Kind` enum `:49-62` (CloudStream, Aniyomi/Mihon, Stremio,
  Mangayomi JS, Jellyfin, Emby, Plex); `sniff` `:132`. JSON object rule `o.has("pluginLists") ->
  Kind.CS_REPO` at `:141`. JSON array rules `:152-160` (CloudStream list needs `internalName` +
  `url`, Mangayomi needs `sourceCodeUrl` or `baseUrl` + `itemType`). `zipKind` `:167`.
  - Bug: a SkyStream repository also has `pluginLists` (plus `id` or `packageName`), so it is
    sniffed as `CS_REPO` and then fails inside the CloudStream installer.
  - A Nuvio manifest (`{"name","version","scrapers":[...]}`) and a Vega manifest (bare array of
    `{display_name, value, version, type}`) return null, so the user sees "not recognised".
  - A `.sky` zip (plugin.json + plugin.js) has no `.dex`/apk, so `zipKind` returns null.
- `sources/js/JsEngine.kt`: one QuickJS per source, `memoryLimit = 64 MB` `:80`,
  `maxStackSize = 2 MB` `:81`, `call` `:98`, promise pump of 50 spins `:104-105`.
  - Gap: no evaluation time limit. A `while(true)` in a Mangayomi source blocks its thread
    forever. quickjs-kt 1.0.18 (Yumeiro's version, `gradle/libs.versions.toml:147`,
    `app/build.gradle.kts:374`) supports `evaluationTimeoutMillis`
    (dokar3/quickjs-kt v1.0.18 `quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt:285`).
- `sources/js/JsBridge.kt`: `http` `:120-140` executes any URL the script asks for (`newCall`
  `:136`). Gap: no private-network guard, so a script can call `http://192.168.1.1/` or
  `http://127.0.0.1:8090/` (TorrServer) from the user's phone. The only private-address check in
  the app is `network/SmartDns.kt:159`. `EXTRACTOR_TIMEOUT_MS = 30 s` `:244`.
- Trust and crash handling: `online/ExtensionGuard.kt` (catches extension crashes,
  `limitedParallelism(8)`), `online/ExtensionReview.kt` (consent modes ALWAYS / IF_RISKY /
  AUTO), `online/ExtensionScanner.kt` `scan :173` (DEX rules, not JS).
- TMDB ids exist already: `online/OnlineRepository.kt:121,415` (from CloudStream
  `LoadResponse.getTMDbId()`), `cache/OnlineCodec.kt:59`, `tracking/Simkl.kt:165`. Nuvio
  scrapers need only tmdbId + type + season + episode, so no TMDB key is needed in Yumeiro.
- Backups: `backup/PrefSections.kt` source files set (tachiyomi_bridge, provider_overrides,
  pinned_extensions, home_servers, JS prefix `js_source_`).
- `docs/proposals/OPEN-SOURCE-COMPARISON.md:106,109,130,160` notes Hikari added Nuvio and
  SkyStream in v0.9.8 and "Vega" in v0.10.57.

## 2. What "Vega" means (evidence)

- Hikari v0.12.51 `app/src/main/java/com/hikari/app/providers/vega/VegaPluginManager.kt:31,36`
  default repos are `raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json`
  and `DHR-Store/vega-providers` (a Vega-Next fork). `FILE_NAMES` `:26` = catalog, posts, meta,
  stream, episodes, settings. `MAX_BYTES = 2 MB` `:23`.
- Zenda-Cross/vega-app v5.3.3 (6f2d7579) is a React Native app for phone and TV that loads these
  providers. vega-providers HEAD 24054d44 `manifest.json` is a bare array of 68 entries such as
  `{"display_name":"VMovies","value":"vega","version":"2.52","type":"global","hasSettings":true}`;
  code per provider is `dist/<value>/<file>.js`. Types in `providers/types.ts`; `ProviderContext`
  `:275-286` = axios, commonHeaders, cheerio, openWebView, kvStore, getCookies.
- Amazon's Vega OS is a Linux-based Fire TV OS built on React Native that does not run Android
  apps or allow sideloading ([Dolby OptiView](https://optiview.dolby.com/resources/blog/playback/amazon-launches-vega-os-for-fire-tv/),
  [Amazon Vega 2.0 blog](https://developer.amazon.com/apps-and-games/blogs/2026/10/vega-2-0-sdk-launch)).
  It has no "repositories" and Yumeiro (an APK) cannot run on it, so it does not fit the owner's
  phrase "Nuvio / SkyStream / Vega repositories". Side note for the TV plan: new Fire TV sticks
  on Vega OS (for example Fire TV Stick 4K Select, HD 2026) cannot install Yumeiro at all
  ([Amazon Fire TV device list](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html)).

## 3. Competitors (read at tag)

| App | Tag / commit | Format and runtime | Evidence |
|---|---|---|---|
| Nuvio | 1.1.0-beta.2 / 81f836d4 (GPL-3.0) | Manifest `{name, version, scrapers[{id, name, filename, supportedTypes}]}`; scraper exports `getStreams(tmdbId, mediaType, season, episode)`; fresh QuickJS per call, `MAX_CONCURRENT_PLUGINS = 10`, `PLUGIN_TIMEOUT_MS = 60 s`; HTML parsing through a native Ksoup bridge instead of bundled cheerio; Play Store flavour has no plugins at all | `composeApp/src/commonMain/kotlin/com/nuvio/app/features/plugins/PluginModels.kt:15-30`; `composeApp/src/fullCommonMain/.../plugins/runtime/PluginRuntime.kt:40-41`, `:124-174`; `.../runtime/dom/DomBridge.kt:3-23`; `androidFull/.../PluginRuntime.android.kt:9-21` (background-priority pool, evaluation timeout); `composeApp/build.gradle.kts:268-291` (full vs playstore) |
| SkyStream | v2.9.0 / 287375b6 (GPL-3.0) | Repo `{name, id or packageName, pluginLists or repos, manifestVersion}`, validated (name and id required, not both lists) ; plugin entries need `packageName`; `.sky` = zip with `plugin.json` + `plugin.js`, path-escape checks and `installSha256`; JS via flutter_js_ng, 90 s invoke timeout, 15 s HTTP | `lib/core/extensions/models/extension_repository.dart`; `lib/core/extensions/services/repository_service.dart:248-268`; `models/extension_plugin.dart:268-306`; `services/plugin_storage_service.dart:77-181`; `engine/js_engine.dart:650`, `:864-870`; functions `providers/js_based_provider.dart:295-300` |
| Vega app | v5.3.3 / 6f2d7579 (PolyForm Noncommercial 1.0.0, so read only, no code reuse) | Providers run in a hidden WebView sandbox with CSP `connect-src 'none'` and a fake origin; all network goes through a host bridge with a URL guard that blocks private IPv4/IPv6, localhost and `.local`, rechecked on each redirect; rate limiter | `src/components/ProviderSandboxHost.tsx:12,21,68`; `src/lib/sandbox/urlGuard.ts` (129 lines); `providerFetch.ts` (381); `rateLimiter.ts` (245); `sandboxBridge.ts` (349) |
| vega-providers | HEAD 24054d44 (no licence file) | Manifest + dist files; `providerContext.ts` uses axios, cheerio and curl-cffi impersonation with TLS verify off | `manifest.json`, `providers/types.ts:275-286`. No licence, so nothing may be copied |
| Hikari | v0.12.51 / 5ff8b86c (GPL-3.0) | Runs all three. Vega: concurrency 8 / 4 perf / 2 TV, 45 s call, 50 s catalog, 256 MB engine memory, bundles cheerio.js (450 KB) + crypto-js (219 KB) + harness (about 782 KB total), patched quickjs-kt aar. Nuvio: rotates bundled TMDB keys. SkyStream: 3 files, 1860 lines | `providers/vega/VegaRuntime.kt:39-58`; `providers/vega/VegaPluginManager.kt:23-36`, install `:128-200`; `providers/nuvio/TmdbResolver.kt:27-30`; `app/build.gradle.kts:218` |
| CloudStream | v4.9.1 / c92e0cf0 | Repo `{name, manifestVersion, pluginLists}` (no id); plugins are DEX loaded with `PathClassLoader` into the app process, no sandbox | `app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt:34-40`; `plugins/PluginManager.kt:611-632` |

Detection rule that follows from the formats:
- CloudStream repo: `pluginLists` and no `id` / `packageName`; list items have `internalName`.
- SkyStream repo: `name` + (`id` or `packageName`) + (`pluginLists` or `repos`); list items
  have `packageName`.
- Nuvio: object with `scrapers` array whose items have `filename`.
- Vega: array whose items have `value` + `display_name` + `version`.

## 4. Legal and security

Legal:
- None of these formats ships content. Repositories are run by third parties, often for
  pirate sites; vega-providers shows a DMCA notice in its README. Yumeiro keeps its rule:
  nothing pre-installed, no default repo list, no "discover repos" feed. Hikari ships default
  repos; Yumeiro should not.
- Code reuse: Nuvio, SkyStream, Hikari are GPL-3.0; Vega app is PolyForm Noncommercial;
  vega-providers has no licence. Yumeiro implements the formats from its own reading (formats
  and function names are interfaces, not copied code). Do not bundle their cheerio builds or
  harness files.
- DRM: Vega's `Stream` type allows `drm` with ClearKey keys. Keys scraped from a pirate source
  are DRM circumvention. Yumeiro already says it plays ClearKey only with keys "you're
  authorised to use" (`player/StreamRecovery.kt:123`). So streams from scripts that carry `drm`
  are dropped with the note "Protected stream, not supported from extensions".
- TMDB: Yumeiro passes ids it already has; it does not ship or rotate TMDB keys like Hikari.

Security (untrusted code, runs for 200,000 users):
- Code runs in QuickJS, not in the app process class loader (better than CloudStream DEX).
  Rules for the shared sandbox:
  - `evaluationTimeoutMillis` per evaluate (5 s) and a whole-call budget (20 s stream, 25 s
    catalog), then the runtime is closed.
  - `memoryLimit` 48 MB (32 MB on low-end and TV), stack 1 MB.
  - Fresh runtime per call for Nuvio and Vega (like Nuvio), so no state leaks between titles.
  - Network only through the bridge: http and https only, block private, loopback, link-local,
    CGNAT and `.local` addresses after DNS resolution, recheck every redirect (Vega urlGuard
    model), max 5 redirects, max 5 MB per response, 30 requests per call, 15 s per request.
  - No file, no cookies of other hosts; cookies kept per provider in memory.
  - `openWebView` maps to the existing visible `SafeWebView` only after a user tap.
  - `kvStore` maps to a per-provider prefs file capped at 64 KB.
  - Script size cap 2 MB per file, SHA-256 recorded at install, update shows the consent sheet
    again when the hash changes (reuse `ExtensionReview`).
- These rules also close two gaps in the existing Mangayomi engine (no time limit, no private
  network guard), so step 2 hardens what is already shipped.

## 5. Design

### User flows
Phone:
1. User pastes a repo URL in Universal add. Yumeiro shows "Nuvio scrapers repository, 12
   scrapers" or "Vega providers, 68 providers" or "SkyStream repository". Before step 3/4 ship:
   "Detected. Yumeiro does not run this format yet." (no failed install).
2. After support ships: consent sheet (existing `ExtensionReview`) lists what the scripts can do
   ("Load web pages from any public site. Cannot reach your home network or files.").
3. Nuvio scrapers appear in the stream picker as extra sources for movies and episodes that have
   a TMDB id. Vega providers appear as normal sources (home rows, search, details).

TV:
- Same Universal add on TV (paste via phone or type). Consent sheet focusable, "Install" not
  focused by default (avoid accidental center press). Concurrency 2.

### UI fit
Reuse the extension list, consent sheet and stream picker. ui-ux-pro-max-skill rows #22, #28,
#35 (confirm repo removal), #79 (empty: "No scrapers returned a stream"). Each source shows a
small badge "Nuvio", "Vega" or "SkyStream" in the existing source chip style.

### Data model
- `ScriptRepo(id, url, format: NUVIO|VEGA|SKYSTREAM, name, addedAt)` and
  `ScriptPlugin(repoId, pluginId, name, version, files: Map<name, sha256>, enabled)`.
- Code files in `filesDir/scripts/<format>/<pluginId>/` (not cache, so they survive). Prefs file
  `script_sources`. Backup includes repo URLs and enabled flags only, never the code (re-download
  after restore). Add `script_sources` to the source files set in `backup/PrefSections.kt`.

### Files
Step 1:
- change `sources/UniversalAdd.kt` (new kinds `NUVIO_REPO`, `VEGA_REPO`, `SKYSTREAM_REPO`,
  `SKYSTREAM_PLUGIN`; fix `:141`; array rule; `.sky` in `zipKind`)
- change `app/src/test/java/dev/asfakul/yumeiro/sources/UniversalAddTest.kt`
Step 2:
- add `sources/js/ScriptSandbox.kt` (QuickJS wrapper with limits, about 200 lines)
- add `network/PublicUrlGuard.kt` (DNS-resolved private-range check + redirect hook, about 120 lines)
- change `sources/js/JsEngine.kt` (`evaluationTimeoutMillis`, call budget) and
  `sources/js/JsBridge.kt` (guard before `newCall` at `:136`)
Step 3:
- add `sources/script/NuvioRepo.kt`, `NuvioRunner.kt`, `HtmlBridge.kt` (jsoup-backed small
  cheerio-like API: load, find, attr, text, each), about 450 lines
- change stream picker to merge script streams
Step 4:
- add `sources/script/VegaRepo.kt`, `VegaSource.kt` (maps catalog/posts/meta/episodes/stream to
  Yumeiro's source interface), about 600 lines; axios-like shim over the bridge
Step 5 (optional): `SkyStreamRepo.kt`, `.sky` installer with path-escape checks.

### Options
| Option | What | Pros | Cons |
|---|---|---|---|
| A | Detection only | Tiny, fixes the misdetection bug, honest message | No new content |
| B | Shared hardened sandbox, then Nuvio, then Vega, SkyStream later | One runtime, one security model, also fixes the existing JS engine; Nuvio uses existing TMDB ids; sized for low-end and TV | Most work; scripts break often (third-party) |
| C | Hikari style: three runtimes, bundled cheerio + crypto-js, default repos, 256 MB engines | Most compatible fastest | About 782 KB assets, 256 MB engine limit is above the 192 MB low-end heap budget, default repos are a legal risk, three security models |
| D | Run Vega providers in a WebView sandbox like the Vega app | Strong isolation (CSP) | One WebView per call is heavy on 2 GB phones and TV; slower |

Recommended: A now, then B. C and D rejected for the reasons above.

### Performance budget
- Memory: one sandbox 48 MB max native (32 MB on low-end/TV); at most 4 concurrent on phones,
  2 on low-end and TV. Peak extra native memory 192 MB phone, 64 MB low-end. Nothing kept after
  the call.
- Threads: a dedicated `limitedParallelism(4)` dispatcher (2 low-end/TV), background priority.
- Network: per call max 30 requests and 5 MB per response; repo refresh at most once per 12 h,
  only while the app is open.
- Battery: no background runs. Repo update check on app start is deferred 30 s and skipped on
  metered networks.
- Startup: nothing loaded until a script source is used.
- Disk: about 50-300 KB per plugin; cap 50 MB total scripts.

### Failure modes
- Script throws or times out: source shows "Failed (timeout)" in the picker; others continue.
- Script tries a private address: blocked, logged as "blocked private address" (host only).
- Repo offline: keep installed code, show last update date.
- Hash changed on update: consent sheet again; user can keep the old version.
- Manifest invalid or too big (over 1 MB): "This repository file is not valid."
- Vega provider needs `openWebView` (captcha): show "Open page" button; never auto-open.
- DRM stream: dropped with a note.

### Accessibility
Source badges have text, not only color. Consent sheet is plain text, readable by TalkBack.

### Migration, backup, rollback
New prefs file and folder, nothing to migrate. Flags `scripts_nuvio`, `scripts_vega`,
`scripts_skystream`, each remote-killable. Rollback hides the sources; files stay until the user
removes the repo.

## 6. Test plan

Unit (JVM), fail before:
- `UniversalAddTest`: SkyStream repo JSON (with `id` and `pluginLists`) gives `SKYSTREAM_REPO`,
  not `CS_REPO`; a CloudStream repo (fixture from RepositoryManager format) still gives `CS_REPO`;
  Nuvio manifest gives `NUVIO_REPO`; Vega manifest array gives `VEGA_REPO`; `.sky` zip with
  `plugin.json` gives `SKYSTREAM_PLUGIN`; Mangayomi array unchanged.
- `PublicUrlGuardTest`: blocks 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, 100.64/10, ::1,
  fc00::/7, fe80::/10, `.local`, and a redirect from public to private; allows a public host.
- `ScriptSandboxTest` (Robolectric with QuickJS native for host, or androidTest if native is
  missing on JVM): `while(true){}` ends with timeout within 6 s; 100 MB allocation fails with
  memory error; second call does not see globals from the first.
- `JsBridgeTest`: request to `http://127.0.0.1` is refused.
- `NuvioRunnerTest`: fixture scraper returns 2 streams for a tmdb id; DRM stream dropped.
- `VegaSourceTest`: fixture provider maps posts and meta to Yumeiro models.

Device (api33-lowram, api36, tv-api24, tv-api34):
- `ScriptSourcesDeviceTest`: install fixture repo from a local server, consent sheet focus starts
  on Cancel on TV, streams appear in the picker, low-RAM run with 2 concurrent sandboxes stays
  within memory.

Real device only: live Nuvio community repo and Zenda-Cross vega-providers on a phone and a TV
box, timing of first stream.

## 7. Implementation plan

1. UniversalAdd detection + misdetection fix + tests (S, about 150 lines). Ship first.
2. `PublicUrlGuard` + JsBridge guard + tests (S-M).
3. `evaluationTimeoutMillis` and call budget in JsEngine + tests (S).
4. `ScriptSandbox` + tests (M).
5. Nuvio repo install, runner, HtmlBridge, stream picker merge + tests (M-L).
6. Vega repo and source mapping + tests (L).
7. Device tests (M).
8. SkyStream only on demand (M).
About 2,500 lines including tests for steps 1-7. No new dependency (QuickJS and jsoup already
present).
