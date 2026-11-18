# Group 14: owner bug list of 2026-11-18 (NBUG-11 … NBUG-16)

Why this group exists: the owner reported six bugs and one redesign on 2026-11-18, during the G9 TV
regression. Owner order of 2026-11-19: close G9, then this list with the full method (research,
analysis, side effects, reverse engineering of competitors, architecture and system design, tests that
fail on the old code, fixes, pass-after, regression), then G10 and G11.

Rules: the same as every group (master prompt §13a, ten steps). NBUG-15 (a)–(c) are a redesign: a
ui-ux-pro-max proposal goes to the owner first and nothing is built before the owner approves it;
NBUG-15 (d) is a logic bug and follows the normal method.

Order (user risk first): NBUG-11 (18+ shown with 18+ off: a privacy leak) → NBUG-12/13 (adult
streams: "Access denied", VPN needed; one network investigation) → NBUG-16 (updates never run, false
"update" flags) → NBUG-14 (IPTV logos) → NBUG-15 (d) → NBUG-15 (a)–(c) proposal.

## NBUG-11: 18+ off, adult titles still in Home → "Most played"

### Files and lines read

- `data/PlayCounts.kt` (all): the play-count store behind Home's "Most played" videos.
- `ui/screens/home/HomeScreen.kt` 186–193 (`Played`), 290–330 (state, the Most played filter),
  624–660 (the row), 1189–1200 (`SquareCard`, test tags).
- `online/AdultContent.kt` (all), `online/OnlineSettings.kt` 20–72 (the two switches).
- `online/OnlineHistory.kt` 140–200 (Continue watching: `adult` stored per entry, `forFeed`, `clearAdult`).
- `online/OnlineSession.kt` 200–220 (where a play is counted).
- `online/ExtensionManager.kt` 70–90, 205–255 (`ready`, `generation`, `providers()`, `provider()`,
  `extensionOf()`).
- `ui/screens/home/HomeOnline.kt` 70–110, `ui/screens/online/OnlineHomeScreen.kt` 185–200, 260–270,
  `OnlineHomePicks.kt` 45–70, `OnlineSearchScreen.kt` 100–145, `ui/screens/library/VideoLibraryScreen.kt`
  115–120, `MTPlayerApp.kt` 660–666, `online/OnlineRepository.kt` 500–515: every other place that
  filters 18+, to compare.

### Data flow

Play an online title → `OnlineSession` 214 `PlayCounts.record(kind = ONLINE, id = "api|url", …)`;
no type, no 18+ mark → prefs `play_counts` → Home `PlayCounts.entries` → `played` (HomeScreen 321)
drops an ONLINE entry only if `AdultContent.isAdultApi(entry.api)` is true right now →
`isAdultApi` asks `ExtensionManager.provider(api)`, which is null until the extensions have loaded
(`ready = false`), and always null for an extension that is turned off or removed → false.

### Action → reaction

| Action | Expected | Code | Result |
|---|---|---|---|
| 18+ off, cold start, open Home | adult titles absent | `played` is computed once at first composition, before `ExtensionManager.ready`; its `remember` keys (`mostPlayed, videoPlays, recentVideos, adultOn, adultPrivate`) do not include the extension state, so it is never computed again when the extensions load | **shown: NB11-1** |
| 18+ off, the adult extension turned off or removed | absent | `provider(api)` null → `isAdultApi` false | **shown: NB11-1** |
| 18+ off, adult title from a mixed extension (Movie + NSFW types, item `TvType.NSFW`) | absent | PlayCounts keeps no type or mark; `isAdultApi` is false for a mixed extension | **shown: NB11-2** |
| Same title in Continue watching | absent | `OnlineHistory.Entry.adult` is stored at record time (`isAdult(api, type)`), `forFeed` filters it | OK (the model to follow) |
| 18+ on and not private | shown | filter off | OK |
| Toggling 18+ while Home is open | row updates | `adultOn` is a key | OK (why the owner may see it come and go) |
| Feed rows, Yumix home, picks | adult extensions absent | `remember(engine.generation, engine.ready, …)` | OK (keys include the extension state) |

### Competitors (reverse engineering, read 2026-11-19)

- CloudStream: 18+ extensions are hidden by the "Preferred media → NSFW" choice and NSFW items in
  mixed extensions by "Enable NSFW on supported extensions" ([recloudstream/cloudstream#1149](https://github.com/recloudstream/cloudstream/issues/1149));
  its home skips pinned NSFW-only providers when NSFW is not chosen
  ([HomeFragment.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt)).
  Its filter is per provider and per type, decided from loaded providers.
- Porn4fans, the owner's example, is an NSFW-only extension (CXXX list,
  [admknight/CloudstreamExtensions](https://github.com/admknight/CloudstreamExtensions/blob/master/README.md)),
  so `isAdultApi` is true for it once it is loaded: the owner's case is NB11-1 (timing), not NB11-2.
- Lesson: decide at record time and store the answer (as Yumeiro's own Continue watching does);
  a later check against loaded extensions is only a fallback.

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB11-1 | 18+ titles in Most played after a cold start, and for an extension that is off or removed | Most played asked the live extension list, once, before it loaded; unknown counted as "not 18+" | High (privacy: shown to anyone who opens Home) | Verified (9e12192) |
| NB11-2 | 18+ titles of a mixed extension in Most played | PlayCounts stores no 18+ mark or type | High | Verified (9e12192) |
| NB11-OB1 | Observation, other surface: with 18+ on but private, an NSFW-typed title of a mixed extension can still appear in that extension's feed rows (`OnlineRepository.toItem` 508 drops NSFW items only when 18+ is off; the private rule hides whole 18+ extensions) | the private rule is per extension, not per title | Medium (needs the owner's reading of "private") | Queued (UNVERIFIED-QUEUE NB11-OB1); not part of the owner's report, not built |

### Design (ADR-023)

See docs/SYSTEM-DESIGN.md ADR-023.

**Status: NBUG-11 closed** (NB11-1, NB11-2 Verified; NB11-OB1 queued, not part of the report).

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB11-1, NB11-2 | FAIL both phones [44394951861](https://github.com/siamform/yumeiro/actions/runs/44394951861) (ab/n11-before, deleted): case a showed A and B with 18+ off; case b (control) passed | CI [44392816697](https://github.com/siamform/yumeiro/actions/runs/44392816697) @9e12192 1209 tests 0 failures; phones [44396427898](https://github.com/siamform/yumeiro/actions/runs/44396427898) PASS (Nbug11, HomeNavigation, AdultSearchFilter, OnlineSwitchesEffect; test-only scroll fix fd7ef1b); tv-api34 [44403632079](https://github.com/siamform/yumeiro/actions/runs/44403632079) @2c552a0 Nbug11 + HomeNavigation PASS; tv-api24 [44406478289](https://github.com/siamform/yumeiro/actions/runs/44406478289) @beeda9c PASS | emulator, TV emulator, CI unit |

## NBUG-12 and NBUG-13: adult streams fail with "Access denied"; they need a VPN

One investigation (owner order): both are "an adult stream does not play", and NBUG-13's cause can
only be told apart once NBUG-12's is known.

### Files and lines read

- `online/AccessLadder.kt` (all): the header ladder behind "Retrying like a browser / as a media
  player / as VLC / like the host's own web player" — the cycle the owner saw.
- `online/OnlineSession.kt` 300–425 (`playCurrent`, `failover`, the fresh `loadLinks`, the headless
  capture, `pageFor`).
- `online/web/HeadlessCapture.kt` (all), `ui/screens/stream/PageStreamFinderDialog.kt` 110–245
  (`PageStreamCapture`: which requests count as a stream).
- `online/OnlineRepository.kt` 365–400 (`load`: `MovieLoadResponse.dataUrl` → `playData`).
- `stream/StreamMediaFactory.kt` 245–265 (player HTTP: `NetworkClient` OkHttp), `network/SmartDns.kt`
  1–60, `network/NetworkClient.kt`, `online/OnlineRuntime.kt` 50–56 (extension `app` HTTP uses the same
  OkHttp client), `res/xml/network_security_config.xml`.
- `stream/WebPageExtractor.kt` (all), `stream/StreamSniffer.kt` (extension tests), `stream/StreamResolver.kt`
  (`resolve`, candidate probing, at most 6).
- yt-dlp's KVS support (`_kvs_get_real_url`, `_kvs_get_license_token`,
  [generic.py](https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/extractor/generic.py), Unlicense):
  how KVS pages (kt_player) store and scramble their file links.
- The owner's extension: [phisher98/CXXX Porn4fans.kt](https://raw.githubusercontent.com/phisher98/CXXX/master/Porn4fans/src/main/kotlin/com/Porn4fans/Porn4fans.kt).

### Reverse engineering the site (probe from the sandbox, 2026-11-18 19:46 UTC, labelled "network probe")

| Request | Result |
|---|---|
| ld+json `contentUrl` (what the extension plays): `…/get_file/5/<hash>/12000/12934/12934_720p.mp4/`, with and without Referer | **403** `text/html` (Cloudflare) |
| Page player data (kt_player 2.7.5 flashvars) `video_url` / `video_alt_url`, which carry `?v-acctoken=…` | **206** `video/mp4` (redirect to `st2.…/remote_control.php`), with or without Referer, with a browser User-Agent |
| Same, no User-Agent header at all | 403 (any User-Agent works: Chrome, `okhttp/5.1`, Yumeiro's; rechecked 2026-11-19, the 2026-11-18 token still valid) |

So the extension is out of date: the site moved to tokenised links and the `contentUrl` it reads is
refused for everyone. No header variant can fix a refused URL, which is why the ladder cycles and
fails. The site's own page still plays.

### Data flow (old code)

Tap → `load()` → `playData = contentUrl` → `loadLinks()` → one link, `url = contentUrl`,
`referer = mainUrl` (bare origin) → player 403 → AccessLadder: 7 header variants, each 403 → fresh
`loadLinks()` → same URL, 403 → last resort `pageFor(s)`: (1) a referer with a path — none, the referer
is a bare origin; (2) **`s.data` — the refused `.mp4` itself**; (3) the title page — never reached →
`HeadlessCapture` loads the `.mp4` in a WebView, gets the same 403 page, finds no player → "Every
source refused playback, even from the site's own page."

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB12-1 | A title whose data is a direct media URL never reaches the site-page fallback | `pageFor` took the episode data as "the page" even when it was the refused stream file | High (every "direct file" source with expiring or moved links; the owner's case) | Verified (8e020b1) |
| NB12-3 | Even on the title page, Yumeiro cannot find a KVS (kt_player) stream | (a) KVS links end in `.mp4/` (trailing slash), so the page extractor's and the sniffer's extension tests miss them; (b) the links sit in the `flashvars` script object, not in `<video>`; (c) many KVS sites scramble them (`function/0/…`, de-scrambled with the page's `license_code`), which Yumeiro does not support. Live check: headless WebView capture of the real title page found no stream on either phone (diagnostic 44389256785, throwaway branch, deleted) (likely reason, not verified: kt_player requests the file only after a tap on its play button). | High (KVS is a common adult-site engine) | Verified (db723eb, 7e7329e) |
| NB12-4 | After "fresh loadLinks" returns the same refused link, the player never retries and the title sits on the error forever (the last resort is never reached) | `playCurrent` builds the same video id for the same link, so `IncomingMedia.open` publishes an equal `VideoItem`; `_pending` is a StateFlow and drops equal values, so no player reload, no second `onFatalError`, no `failover`. Found in fail-before 44394971017 (api36): phase PLAYING with the 403 for 240 s, the page never requested | High (every source whose links do not change on refresh) | Verified (0a37f0a) |
| NB12-5 | After one episode needed the refresh and the page step, the next episode skips both and stays on the player error | `playEpisode` copies the state and keeps `refreshed` and `captured` from the previous episode (code read while fixing NB12-4) | Medium (series from sources with refused links) | Verified for the next episode (b2cb13c); version switch and Retry (3651816) by code read: UNVERIFIED-QUEUE NB12-5b |
| NB12-2 | The extension's link is dead (site moved to `v-acctoken` links) | Third-party extension out of date | — (not ours) | Report to the extension author (owner, OMT) |
| NB13-1 | Adult hosts need a VPN on the owner's network | Bangladesh blocks adult sites at the gateway (BTRC; DNS tampering, IP blocking or gateway filtering, [OONI 2025](https://ooni.org/post/2025-bangladesh-report/); 22,000+ adult and gambling sites through the CTDR system, [bdnews24](https://bangla.bdnews24.com/tech/article1936753.bdnews)) | — | See below |

### NBUG-13 analysis

- What the app already does: every OkHttp path (extensions through `OnlineRuntime`, the player through
  `StreamMediaFactory`, images) resolves through `SmartDns`; with "auto" on, a host the network blocks
  by DNS (no answer, sinkhole address, reset after a system lookup) is resolved over DNS-over-HTTPS
  (NBUG-6, NBUG-9). So a **DNS-only** block is already bypassed in those paths.
- Gaps: (a) WebView paths (headless capture, the in-app browser, the host web player) use the system
  resolver: Android WebView has no DNS hook; (b) **IP or SNI blocking cannot be bypassed by DNS at
  all** — only a tunnel or proxy outside the country can, which is what the owner's VPN does.
- NBUG-12 also looked like "needs a VPN": the 403 is the same everywhere, so a VPN alone would not
  have fixed Porn4fans. After NB12-1 the owner must retest on the home network (OMT) to tell which
  remains.
- Design options (ADR-024, owner decision, nothing built without approval because it is a new network
  feature): (1) a user-set HTTP/SOCKS5 proxy in Settings → Network applied to OkHttp and, through
  `androidx.webkit.ProxyController`, to WebView; (2) WebView DNS through a local proxy that resolves with
  `SmartDns` (fixes DNS-only blocks in WebView, not IP/SNI); (3) leave it to a system VPN / Private DNS.

### Design (ADR-024)

Last resort for a refused title, in order: (1) the capture page is the title page whenever the episode
data is itself a media file (`CapturePage.pick`, NB12-1); (2) read the page HTML over OkHttp with the
site's referer and the CloudStream User-Agent and let `StreamResolver` find the stream, with KVS
`flashvars` support (best quality first, `realUrl` de-scrambling, links needing login skipped)
(NB12-3); (3) only then the headless WebView capture. The id suffix stays `:web` (resume and history
unchanged). No new dependency; the KVS code is a port of yt-dlp's (Unlicense, compatible with GPL-3).

**Status: NBUG-12 closed** (NB12-1, NB12-3, NB12-4, NB12-5 Verified; NB12-5b queued; NB12-2 third party, OMT-32). **NBUG-13: owner decision** (ADR-024, OMT-33).

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB12-1, NB12-3, NB12-4 | case a FAIL both phones [44394971017](https://github.com/siamform/yumeiro/actions/runs/44394971017) (attempt 2; ab/n12-before, deleted): PLAYING with the 403 for 240 s, the title page `/video/1/` never requested. NB12-4 stops the old code before the last resort, so NB12-1 and NB12-3 are not shown failing on their own (their unit tests `CapturePageTest`, `KvsPlayerTest` came with the fixes) | CI [44399173697](https://github.com/siamform/yumeiro/actions/runs/44399173697) @7e7329e 1221 tests 0 failures; phones [44401023896](https://github.com/siamform/yumeiro/actions/runs/44401023896) @7e7329e case a PASS (page requested, tokenised link played, `:web` id, first frame); regression phones [44403595281](https://github.com/siamform/yumeiro/actions/runs/44403595281) @2c552a0: Nbug12, OnlinePlayback, OnlineSubtitlesFallback, OnlineSwitchesEffect, HeaderStream, StreamFormatMismatch PASS; tv-api34 [44403632079](https://github.com/siamform/yumeiro/actions/runs/44403632079) Nbug12 PASS, OnlinePlayback [44406473476](https://github.com/siamform/yumeiro/actions/runs/44406473476) @beeda9c PASS; tv-api24 [44406478289](https://github.com/siamform/yumeiro/actions/runs/44406478289) @beeda9c PASS | emulator (local server), TV emulator, CI unit |
| NB12-5 | case b FAIL both phones [44398530263](https://github.com/siamform/yumeiro/actions/runs/44398530263) (ab/n12b-before @90b9e52, deleted): episode 2 on the 403, pageLoads 1→1 | case b PASS both phones [44401023896](https://github.com/siamform/yumeiro/actions/runs/44401023896) (pageLoads 1→2), both TVs (runs above) | emulator, TV emulator |
| Test-only fixes found in the regression | `OnlinePlaybackDeviceTest.c_uiFlowPlays`: the welcome guide covered the tabs (2c552a0, `FirstRun.toTabs`), and TV has a side rail (beeda9c, `FirstRun.navTag/clickTag`) | phones and both TVs PASS (runs above) | emulator, TV emulator |
| NB12-2 | — | — | OMT-32 (owner retest; tell the extension author) |
| NB13-1 | owner network only | — | OMT-33 (decision + retest) |

## NBUG-16: extensions and Live TV playlists never auto-update; after an update they are flagged again

Owner report (2026-11-18): "Extensions and Live TV M3U8 playlists never auto-update. Additionally, even
after manually updating them, syncing again repeatedly flags and shows them as needing an update again."

### Files and lines read

- Data workflows on `main` (the scheduled copies run from `main` only): `.github/workflows/playlist-market.yml`,
  `extension-market.yml`, `stream-sync.yml` (checkout, data-branch worktree, change check, publish);
  `.github/scripts/data-changed.sh` (all); `build_market.py` 420–470 (digest, keep the last good copy);
  `build_extension_market.py` 470–500 (curated mirror fallback); `sync_streams.py` 85–105.
- `online/ExtensionManager.kt`: `install` 261–291, `installManual` 317–335, `gate` 337–346, `checkUpdate`
  365–390, `updatesForTests` 512–521, `sync` 523–545, `readManifest` 649–681.
- `online/ExtensionMarket.kt` (`parse`, `load`, `ExtensionSources.repoPlugins`, `all` 186–206),
  `online/ExtensionModels.kt` (`parsePlugins`, `keyOf`), `online/ExtensionReview.kt` (`Mode`, `autoDecision`).
- `stream/PlaylistStore.kt`: `channels` 300–316, `refresh` 318–342, `installFromMarket` 460–486,
  `applyMarketUpdates` 495–509, `autoUpdateMarket` 512–516; `stream/PlaylistMarket.kt` (`load`, `updatesFor`).
- `ui/screens/MTPlayerApp.kt` 380–404 (the only extension `sync` call), `ui/screens/stream/StreamScreen.kt`
  148–152 (the only `autoUpdateMarket` call), 199 (Refresh), `PlaylistDetailScreen.kt` 152 ("A newer version").

### Evidence (labelled)

- Network probe 2026-11-19: the playlist market index (`market/index.json`) still says generated
  2026-11-08; the `market` branch head is 2026-11-08T00:55Z. Runs 42939091897 (11-11), 43542171517 (11-14),
  44015109388 (11-17) all "success", each "No changes since the last publish; skipped". The 11-17 log
  warns that 3 new playlists are outside the sparse-checkout definition.
- Local reproduction (sandbox git 2.4x): a sparse clone (`.github` only) + `git worktree add out
  origin/<data>` gives a sparse worktree; a new file in it makes `git add -A` exit 1 ("paths outside
  your sparse-checkout definition"), and `data-changed.sh` (`set -e`) then exits 1 = "no changes".
  `git add -A --sparse` exits 0.
- Network probe 2026-11-19, extension index generated 2026-11-17 (2836 entries: 2085 discovered,
  749 community, 2 curated): for the phisher98 repository, market listing / live listing / version in
  the file's own manifest: Cinefreak 23/24/24, StreamPlay 711/713/713, MovieBoxProvider 41/42/42; about
  80 such entries in the 40 largest repositories. Curated entries' sha256 match their mirrors.

### Data flow (old code)

Playlists: workflow builds `out/` → change check fails on a new file → publish skipped → catalog frozen
→ the app's `updatesFor` never sees a new sha → nothing to auto-update. A manual Refresh downloads the
newest mirror but keeps the old `marketSha` → "A newer version is available" and the market's "N updates"
stay. Extensions: install stores the listing's version, not the file's → when the market later lists the
version the user already has, it is flagged and downloaded again; the market copy also hides a newer copy
in the user's own repository; background sync runs once per activity start (TV: rarely), and refused
automatic updates are dropped without a word.

### Competitors (reverse engineering, read 2026-11-19)

- CloudStream `PluginManager.kt` ([source](https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)):
  after download, `loadPlugin` saves `manifest.version` from the file (`setPluginData(data.copy(version = version))`),
  not the listing's; `isOutdated = online.version > saved.version`; repositories merged with
  `distinctBy { it.plugin.url }`; updates run in `updateAllOnlinePluginsAndLoadThem` at app start.
- Tachiyomi/Mihon: the installed version comes from the installed package (`versionCode`), compared
  with the repo index; the same principle (the installed artefact is the truth).

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB16-1 | The playlist market (and any data branch that gains a new file) never publishes | Sparse checkout (`.github`) + `git worktree add` gives a sparse worktree; `git add -A` exits 1 on a new path outside the cone; `data-changed.sh` (`set -e`) reads that as "no changes" and the job reports success | High (catalog frozen since 2026-11-08: no market playlist can update) | Open |
| NB16-2 | "Keep the last good copy" never works in the data builds | The same sparse worktree has none of the previous files (`playlists/`, mirrors), so `os.path.exists(prev)` is always false: a source that fails once is dropped from the catalog (`build_market.py` ~462), the curated mirror fallback (`build_extension_market.py` ~489) and `sync_streams.py:97` never fire | High (catalog entries vanish on one bad day) | Open |
| NB16-3 | A market playlist stays "needs an update" after a manual Refresh | `refresh(id)` without a sha keeps the old `marketSha` although it downloaded the newest mirror | Medium (the owner's "flags again") | Open |
| NB16-4 | An extension is flagged for an update to the version it already has | `install` stores `remote.version` (the listing); community/discovered entries point at the author's live file, which is often newer than the market's listing | Medium (the owner's "flags again"; extra downloads) | Open |
| NB16-5 | A newer build in the user's own repository is hidden by the older market copy | `ExtensionSources.all` keeps the first copy per key (market first) | Low–Medium (auto-update waits up to 3 days for the market) | Open |
| NB16-6 | An automatic update refused by the safety check is dropped silently | `sync` installs with `Mode.AUTO`; `autoDecision` refuses DANGER/unreadable files; `failed` is never shown, so the user does not know why "auto-update" did nothing | Low (the Update button stays) | Open |
| NB16-7 | Background checks run too rarely | Extension `sync` runs only in `MTPlayerApp`'s first `LaunchedEffect` (once per activity creation; a TV keeps the activity for days); the playlist market check runs only while the Live TV screen is open | Medium (TV: never) | Open |

Not findings: the 6 h market interval and 30 min repository cache (by design, ADR-025 keeps them);
Tachiyomi has no update flags; curated sha256 match.

### Design (ADR-025)

See docs/SYSTEM-DESIGN.md ADR-025.

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB16-1, NB16-2 | pending (`.github/scripts/tests/data-branch-test.sh`) | pending | local shell + CI |
| NB16-3 | pending (`PlaylistMarketTest.manualRefreshClearsTheUpdateFlag`) | pending | CI unit (Robolectric) |
| NB16-4 | pending (`Nbug16ExtensionVersionDeviceTest`) | pending | emulator |
| NB16-5 | pending (`ExtensionFormatsTest.newerUserRepoCopyWinsOverMarket`) | pending | CI unit (Robolectric) |
| NB16-6, NB16-7 | no test can drive the old code (no hook for "6 h later" or a refused background update) | pending (unit, new code) | UNVERIFIED-QUEUE |

## NBUG-14: IPTV channel logos don't show

Owner report 2026-11-18: "Channel logos are not displaying for IPTV channels."

### Files and lines read

- `stream/M3uParser.kt` 53–150: `tvg-logo` → `StreamRequest.logoUrl` (quoted attributes, any case).
- `stream/StreamModels.kt` 70, 111, 128: the logo survives the channel cache (`logo` key).
- `ui/screens/stream/LiveTvHub.kt` 139–170 (`LiveChannelTile`), `StreamScreen.kt` 729, `watch/StreamWatchPanel.kt`
  320: every surface draws `logoUrl` with Coil `AsyncImage`; initials stay behind when it fails.
- `MTApplication.newImageLoader` 100–118: Coil uses `NetworkClient.get` (no User-Agent set → `okhttp/4.12.0`).
- `widget/ContinueWidget.kt` 132: its own `ImageLoader(context)` (Coil default client, same User-Agent).
- `.github/scripts/build_market.py` (all), `.github/market/sources.json`: `iptv-org/iptv` `streams/` per country.

### Evidence (network probe from the sandbox, 2026-11-19)

| Playlist (market copy) | Channels | With `tvg-logo` |
|---|---|---|
| IPTV Scraper Zilla | 14 478 | 13 139 |
| Free-TV | 2 192 | 2 101 |
| Mrgify BDIX | 338 | 288 |
| iptv-org Bangladesh / United States / India | 37 / 1 106 / 1 765 | 0 / 0 / 0 |

iptv-org's published `countries/bd.m3u` has 43 logos and `group-title`; its repository `streams/bd.m3u` has
`tvg-id` only. Logo hosts in the curated lists: 80 samples over the 40 commonest, all 200 with the app's
User-Agent except `upload.wikimedia.org` (403: "Please set a user-agent and respect our robot policy").

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB14-1 | Every iptv-org country playlist from the market shows no logos (340 of 400 entries) | The market copies the repository's raw `streams/` files, which carry `tvg-id` only; iptv-org adds logos when it publishes | High (most Live TV channels) | Open |
| NB14-2 | Logos on Wikimedia (and hosts with the same rule) never load | Image requests carry OkHttp's default User-Agent; Wikimedia refuses it (403) | Medium (457 curated logos, 3 117 in iptv-org's data) | Open |
| NB14-3 | iptv-org country playlists have no categories (every channel "Other") | Same as NB14-1: `group-title` is added only when iptv-org publishes | Medium (needed for NBUG-15 (b) grouping by category) | Open |
| NB14-OB1 | SVG logos never show | Coil has no SVG decoder in the app (new dependency) | Low (18 curated logos) | Queued |

Not findings: parser and cache keep `tvg-logo`; cleartext logos are allowed (`network_security_config`
base); `application/octet-stream` logos decode (Coil reads the bytes).

### Design (ADR-026)

See docs/SYSTEM-DESIGN.md ADR-026.

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB14-1, NB14-3 | pending (`.github/scripts/tests/test_build_market_logos.py`) | pending | CI (Python) |
| NB14-2 | pending (`Nbug14ChannelLogoDeviceTest`: local server answers 403 to library User-Agents) | pending | emulator, TV emulator |

## NBUG-15 (d): separate recents for Play Link and Live TV

Owner spec 2026-11-18: "Play Link Page Recents: restrict recent streams strictly to items launched via the Play
Link feature. Live TV Page Recents: route all general recently played channels into a dedicated 'Recently
Played' category row on the main Live TV feed." Parts (a)–(c) are the redesign (ui-ux-pro-max proposal, owner
approval before building); (d) is behaviour and is built now. The row is placed so the redesign keeps it.

### Files and lines read

- `stream/StreamRepository.kt` 19–70: one list `history` (prefs `mt_streams`, key `history`, 40 entries).
- `stream/IncomingMedia.kt` 27–37: `open(remember = true)` adds every stream to that list by default.
- Callers of `IncomingMedia.open` (all 14): Play Link page play / resolved play / page finder / its own recents
  (`StreamScreen` 287, 295, 391, 564); Live TV hub tiles (`StreamScreen` 366); playlist page
  (`PlaylistDetailScreen` 365); watch panel channel list and header modes (`StreamWatchPanel` 135, 140); websites
  (`WebSitesScreen` 103); the player's page finder (`VideoPlayerModal` 1498); links shared from other apps
  (`IncomingMedia.handle`); YouTube, online extensions (`remember = false`).
- `StreamScreen` 536–575: the Play Link page shows "Recent streams" = that one list; the Tools tile "Recent".
- `security/Privacy.kt`, `SettingsControls` 313: incognito and "Clear watch history" never touch stream recents.

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB15D-1 | Play Link "Recent streams" lists every channel played anywhere (Live TV, playlists, websites, the player's page finder) | one shared list, and `IncomingMedia.open` records by default | Medium (owner spec) | Open |
| NB15D-2 | Live TV has no recently played channels | no store and no row | Medium (owner spec) | Open |
| NB15D-OB1 | Stream recents are kept while incognito is on, and "Clear watch history" leaves them | `addToHistory` reads no privacy switch; the clear action names device, online and YouTube only | Low (not in the owner's report; the setting's text does not promise it) | Queued (owner decision) |

### Design (ADR-027)

See docs/SYSTEM-DESIGN.md ADR-027.

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB15D-1, NB15D-2 | pending (`StreamRecentsTest` unit; `Nbug15RecentsDeviceTest`: a channel played from Live TV must not be in Play Link recents and must be in the Live TV row; a link played from Play Link the other way round) | pending | CI unit, emulator, TV emulator |

