# Yumeiro 2.x: major bugs and features, must-do list

Written 2026-11-20 (Dhaka) against r15-stability after v2.0.0 was published. It checks every item of the owner's
"COMPLETE CONSOLIDATED MASTER LIST" one by one against the code, the ledger (MASTER-CHECKLIST-FINAL.md,
CHECKLIST-FEATURES-BUGS.md, REMAINING-2026-11-19.md) and the new crash report on the `crash-log` branch.
Trust the repo and run IDs over this text.

## How to read the status column

| Status | Meaning |
|---|---|
| Verified | A test passed on an emulator with a run ID. Not yet seen on a real phone unless said. |
| Built, not tested | Code is in 2.0.0, no device run proves it. Treat as "may not work". |
| Broken | A real report or a failing check shows it does not work. Must be fixed first. |
| Partial | Some of the requested behaviour exists; the rest is listed in "Missing". |
| Not built | No code in the app for it (checked with a code search on 2026-11-20). |
| Owner decision | Conflicts with a decision the owner already took. Not reopened here; the owner must say yes. |

"Verified" items still need the owner's real-phone check (OWNER-MANUAL-TESTS.md), because the community reports
problems that emulators did not show.

## P0: stop the freezes first (Now, before any feature)

The community report "app crashes even when I touch, YouTube and Live TV" matches a **freeze**, not a crash. The
owner's 2.0.0 report on the `crash-log` branch (freeze-1795185111722.txt, Xiaomi 220333QAG, Android 13, 2.0.0 (18)
release) is Android's "Input dispatching timed out … Waited 8001ms for MotionEvent(action=DOWN)": the screen did not
answer a touch for 8 seconds. Live TV was playing (ExoPlayer and tv.bdiptv.net threads in the dump), and the main
thread had used about 965 s of CPU (`utm=96515`).

| ID | Problem | Root cause | Fix | Status |
|---|---|---|---|---|
| FREEZE-1 | Touch freezes the app ("not responding") while Home is under the player | The release dex maps the main-thread stack to `HomeScreen` → `AdultContent.loadedAdultApis` → `ExtensionManager.extensionOf` (loop over the plugin map). `isAdultApi` rebuilt the whole provider list on every call, and Home / Yumix call it once per provider while drawing: about N² work on the main thread each time, repeated at every extension-load step and every Most-played change | One 18+ map per extension change (`StampMemo` + `ExtensionManager.providerStamp`), one pass over plugins (`extensionLookup`), Home works the map out off the main thread | Fixed in this push; regression test `AdultApiCostTest` (fail-before branch `ab/freeze1-before`). Run IDs in BUGS-AND-FIXES-2.0.0.md B-10 |
| FREEZE-2 | Other screens still do provider work while drawing | Same pattern in `OnlineHomeScreen`, `OnlineSearchScreen`, `OnlineHomePicks`, `HomeOnline` (`ExtensionManager.providers()` inside `remember`) | Now cheap (cached map), but `providers()` itself still rebuilds and re-applies overrides per call. Move provider lists to one `StateFlow` in `ExtensionManager`, built on a background thread; screens only collect it | Not built |
| FREEZE-3 | 171 threads in the freeze dump, same number as B-8 | Many OkHttp connections (Live TV failover probes, extension feeds) and dispatcher workers at once | Cap parallel feed loads per screen (see Epic 4), share one OkHttp pool, stop probing hidden screens | Open (B-8) |
| FREEZE-4 | Why Home recomputed so often while Live TV played | To confirm on a real phone: Most-played inputs (`PlayCounts`, MediaStore `recentVideos`) or extension generation bumps changing during playback | Add a debug counter of Home recompositions to the diagnostics screen; a device test that plays Live TV for 60 s and asserts Home recomposes ≤ N times | Not built |
| GATE-1 | Nothing in CI catches main-thread stalls | No ANR test | Device test: start with 300 fake providers, open Home, Live TV, YouTube; fail if any frame takes > 700 ms or an ANR appears in `ApplicationExitInfo` | Not built |
| UPG-1 | Deferred by owner: TV upgrade re-check and 1.9.0 → 2.0.0 upgrade check | — | Run when the owner allows (max two device workflows at once) | Not run (owner deferred) |
| SEC-1 | Last.fm shared secret must be rotated before built-in Last.fm sign-in is turned on | — | Owner rotates, then `wrangler secret put LASTFM_SHARED_SECRET` | Owner action |

Competitor handling: Android's Compose guidance is to do as little calculation in a composable body as possible
and cache it with `remember`, and to move I/O and heavy work to `Dispatchers.IO`
([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[effects and threading](https://developer.android.google.cn/agents/skills/jetpack-compose/jetpack-compose-performance/references/effects-and-threading)).
FREEZE-1 broke exactly this rule.

## Part 1: epics

| # | Epic | Status | What exists | Missing (must do) |
|---|---|---|---|---|
| E1 | Flagship all-in-one super-extension with OTA updates (GitHub Actions + Cloudflare Worker) | Not built | Extension market, playlist market and feed workflows exist (`extension-market.yml`, `playlist-market.yml`, `app-feed.yml`); Worker api.yumeiro.click is live | A Yumeiro-owned provider (JS or CloudStream) with its own repo, a scheduled build that tests each source, and an OTA manifest the app polls. Legal check of each source first. Owner rule "no scheduled workflows" applies to this agent's runs, so the owner must approve a schedule |
| E2 | Curated, auto-validated Live TV M3U8 (~20 channels per country) | Partial | `stream-sync.yml`, `sync_streams.py`, market build with logos (NBUG-14 Verified) | A per-country curated list with a nightly probe that drops dead links, like iptv-org's `playlist:test --fix`, which checks every link and removes broken ones ([iptv-org scripts](https://github.com/iptv-org/iptv/blob/master/docs/scripts.md)). Respect iptv-org's blocklist (DMCA / NSFW) |
| E3 | ABI-split APKs + in-app ABI self-migrating updater + website ABI detector | Partial | The updater already understands per-ABI file names (`GitHubReleaseClient.ABI_NAMES`, "-<abi>.apk" and ".tv.apk") | Gradle `splits { abi { … } }` is not set, so releases are universal only. Add splits with unique `versionCode` per ABI and keep a universal APK with the lowest code ([Android: build multiple APKs](https://developer.android.com/build/configure-apk-splits)). Then: release.yml uploads 4 + universal, SHA256SUMS covers all, the website picks the ABI from the browser's user agent, and the updater moves a universal install to the matching split |
| E4 | High-speed background extension engine | Partial | SPD-1 (rows join as each extension loads), home memory cache, extension guard / timeouts (NBUG-8) | Stale-while-revalidate cache with < 50 ms first paint, a scheduler that loads only the visible rows plus two screens, pinned first, sort by measured latency, quarantine slow or failing extensions, throttle on low RAM or heat (MemoryWatch exists; thermal checks exist in 3 files). Remove the Explore guide delay but keep the pop-up blocker. Directly linked to FREEZE-2/3 |

## Part 2: features

| ID | Feature | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| F-01 / F-16 | Sub / Dub / language badges on search posters and Yumix; tap preselects; Yumix sections (Movies, Series, Web series, Dual audio, Sub, Dub) and quality tags | Built, not tested | 79532b29, 961fee20; `ReleaseTags.kt`, `AudioLanguages.kt` | Device test that a Dub tap opens the dub stream (ties to NBUG-5) |
| NF-1 | Episode watch status: Watched / In progress bar / Fresh | Built, not tested | 66c52247 | Device check |
| NF-2a | Downloads labels Offline vs Permanent | Done | DownloadsScreen Offline / Permanent label (present before this cycle) | — |
| NF-2b | Download any stream (HLS / DASH muxing) + quality picker | Partial | NF-2b-1..4: DASH + split audio, TS → MP4 (Built, not tested) | Quality picker before download, like Seal / ytdlnis format pickers |
| NF-3 | Extension pop-up / promo blocker | Verified (emulator) | ExtensionPopups 8/8, run 43825449454 | EXT-T1 open |
| NBUG-15a–d | Live TV redesign (filter button, no file names, channels by category, first 50, search, pins) | Verified (emulator, phones + TV) | ledger: fail-before 44411680293, phones 44413808456, 44421879508 | Real-phone check; tied to FREEZE-3 (Live TV probes) |
| F-02 split | Separate Telegram page and Websites page, with navigation shortcuts | Partial | Today one "Telegram & web sites" area (`TelegramPublic.kt`) | Two entries in the navigation |
| F-02 login | Telegram login (Hikari-style) | Owner decision | Owner decision "Telegram has no login" stands; public channels work without login (F-02 Pass 2/2, runs 44595993499, 44598214395) | If the owner changes the decision: TDLib on the device (Boost licence, [TDLib](https://github.com/tdlib/td)); the api_hash ends up in every APK ([Telegramedia](https://github.com/kernoeb/telegramedia)) or comes from a server at runtime ([Cablegram](https://github.com/seep-labs/cablegram-apps)); adds native libraries per ABI (needs E3) |
| F-20 | Vault: 3 security questions, `.nomedia`, app lock, incognito, discreet icon | Partial | `PrivateVault.kt`, `AppLockGate.kt`, incognito in 20 files | Not built: security questions, `.nomedia` in vault folders (0 hits), discreet launcher icon (no `activity-alias`) |
| F-19 | Profile security questions instead of the screen-lock bypass; strict per-profile isolation incl. extensions / repos and Continue Manga | Partial / Broken | Per-profile today: YouTube history, online settings, online history, bookmarks, vault, private downloads (`Profiles.prefsName` users) | Not per profile: manga progress ("Continue reading" leaks across profiles, confirmed in code), extensions and repositories, Live TV pins. No security questions |
| F-10 | Themes: Blue, glass | Built, not tested | 354082ba, 7396955f; ADR-017 tint only | Device check; Blue theme presence to confirm on device |
| 2x | Hold for 2× speed | Built (26 files mention it) | — | Device check |
| F-18 | Manga previous / next chapter and continuous scroll across chapters | Not built | Reader has vertical (webtoon) and paged modes only (`MangaScreens.kt`) | Prev / next buttons, auto-load next chapter at the end, as Mihon does |
| F-12 | Portrait drama swipe player | Built, not tested | df181804, 4f480803 (phones only) | Auto-open for 9:16 content not built |
| F-08 | Upcoming hub + Yumix carousel | Built, not tested | 97895d02 (Simkl data, kill switch `UPCOMING`) | Device check |
| Country filter | Country filter for Live TV / feeds | Partial | Country in PlaylistMarket only | Filter on Live TV page |
| F-17 | Profile statistics | Built, not tested | `StatsScreen.kt`, `StatsMath.kt`; ReleaseFeaturesDeviceTest PASS 44500142144 | Real-phone check |
| F-03 | TeraBox / DiskWala | Partial | Public share links (CloudRecapture, LinkHosts) | Login path needs account cookies (ADR-037); 30 s cut-off is a bug (see B-3 below) |
| F-04 | User WebView site list | Built, not tested | 49b28a38 | Device check |
| F-05 | Nuvio / SkyStream / Vega repositories | Partial | Step 1 detected (E14 Pass 2/2) | Owner decision "one registry"; Vega step 4 not built (import runs) |
| F-06 | Volume boost 200 % | Built, not tested | 84861229 | Device check |
| F-07 | External player | Built, not tested | 7713c6e8 | Return position from MX / VLC / mpv (`position` extra) not built |
| F-09 | Watch Later + Favourite on posters | Built, not tested | 26032196 | Owner reports taps not working; device check |
| F-11 | Manga OCR translation | Not built | ADR-037 | On-device OCR + translation models; size budget |
| F-13 | Offline manga | Built, not tested | 84987542 | Device check |
| F-14 | Live TV failover | Built, not tested | 6658998a | Limit parallel probes (FREEZE-3) |
| F-15 | EPG Now / Next | Built, not tested | af0a5a14 | Device check |
| Airing alerts | Push alerts for airing episodes | Partial | `airing` in 6 files | End-to-end notification check |

## Part 3: bugs

| ID | Bug | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| CL-1 | Audit the `crash-log` branch | Done for the only file there (freeze report) → FREEZE-1 | crash-log c12d27f4 | Owner: keep uploading reports there |
| X-1 | Nuvio / SkyStream / Vega import fails | Partial | Detection only (E14) | Import and run (F-05 step 2–4) |
| B-3 | TeraBox stops at 30 s; slow cloud-link detection | Not fixed | — | Find the 30 s cap in `CloudRecapture` / resolver; resolve links in the background with a progress state |
| T-1 | Raw torrent info-hash → magnet | Not built | Stremio skips torrent streams (`StremioProvider.kt:136`) | Build `magnet:?xt=urn:btih:<hash>` with trackers, hand to an external torrent app |
| S-1 | Search hangs, every keystroke fires, feed shuffles | Not fixed | No debounce in `OnlineSearchScreen` | 300 ms debounce, cancel the previous search, stable order |
| PIP-1 | Back on the stream page should go to PiP | To check | `PipController.kt` | Device test |
| PIN-1 | Pin cap 8, duplicate extension cap 2, remove the redundant pinned row | Not built | No pin limit in code | — |
| BUG-1 | Resume across entry points | Verified (emulator) | ResumePointsTest; device runs in CHECKLIST | Real-phone check |
| BUG-2 | YouTube channels + quality selector | Verified (emulator) for channels; quality = BUG-4 | 44116513171 | Real YouTube is behind YT-WALL (P3 / P5 / M3 Blocked) |
| BUG-3 | Download speed drops after 50 % | Blocked: owner (real phone, OMT-20) | 40ea96e, 3c85a72 | Real-phone run |
| BUG-4 | Upscaling and quality mismatch | Verified (emulator); real YouTube Blocked: owner | Bug4QualityListTest | — |
| EXT-P | External player integration + gallery downloads | Partial | F-07, NBUG-4b | Return position; gallery app view = OMT-17 |
| PROF-1 | Continue Manga leaks across profiles | Broken (confirmed in code) | manga progress not under `Profiles.prefsName` | Scope manga progress per profile + migration test |
| BUG-5 | TV UI | Verified (emulator) for NBUG-2a–e; TV-4 Built, not tested | runs in CHECKLIST | OMT-21 |
| BUG-6 | Music player touches pass through | Verified (emulator) | 43914724044 | — |
| BUG-7 | Player / IPTV freezes, missing downloads | Verified (emulator) except 7d (GPU, owner) | 43946324687 | Likely related to FREEZE-1 on real phones; re-test after the fix |
| NBUG-5 | Dub count | Verified (emulator) | ledger | — |
| NBUG-6 / 9 | DNS | Done per owner decision (sequential fallback 1.1.1.1 then 9.9.9.9); per-feature DoH deferred (ADR-037) | REMAINING-2026-11-19 | Real-network check OMT-31 |
| NBUG-7 | Backup restores extension list but not the binaries; composite keys for duplicate names | Verified (emulator, both phones) | G9-1/2/5/7, ADR-021; device 44315631356, 44318262848 | Real-phone restore after "Clear all data" |
| NBUG-8 | Infinite loading for one extension | Guarded in code (timeouts) | REMAINING row | OMT-34 |
| NBUG-10 | "Show videos from" lists only 2 | Built, not tested | 4c6345f8 | Device check |
| NBUG-11 | Adult titles in Most Played | Fixed (ADR-023); FREEZE-1 keeps it fail-closed | — | — |
| NBUG-12 / 13 | Access denied loop; proxy | Proxy Pass (NBUG-13 runs 44593053433, 44593085810, 44600322143); loop guarded | MASTER-CHECKLIST-FINAL | OMT-32 |
| NBUG-14 | IPTV logos | Verified | b212379 | — |
| NBUG-16 | Auto-update sync loop | Verified (CI + emulator + TV emulator) | NB16-1…7, ADR-025 | Real-phone check over a few days |
| C5 / C6 | Remaining audit items | Open | owner list | Track here as they are taken |

## Owner decisions needed (not reopened)

1. Telegram login: keep "no login" (decision) or allow TDLib login (size, api_hash, needs E3).
2. Super-extension (E1) and a scheduled validation workflow (E2): the "no scheduled workflows" rule is for agent runs;
   a product schedule needs the owner's yes.
3. Vega / Nuvio / SkyStream stay one registry; import (step 2–4) is a new release lane.

## Order of work (Now / Next / Later)

Now (2.0.1, stability only)
1. FREEZE-1 (this push), FREEZE-2, FREEZE-4 counter, GATE-1 ANR test.
2. PROF-1 manga progress per profile (privacy leak).
3. S-1 search debounce (fires work per keystroke on the main path).
4. Owner real-phone tests U-30 and the mandatory list, then release 2.0.1.

Next (2.1)
- E4 engine (scheduler, SWR, quarantine, throttling) and FREEZE-3 thread cap.
- E3 ABI splits + website detector.
- F-18 manga chapters, F-20 / F-19 security questions, `.nomedia`, discreet icon.
- PIN-1, T-1 magnet, B-3 TeraBox 30 s, NF-2b quality picker, F-07 position return.

Later (2.2+)
- E1 super-extension, E2 curated Live TV per country, F-11 OCR translation, F-05 import steps, Telegram login if
  the owner decides so.

## Acceptance for every item

Root cause written, regression test that fails before and passes after, commit body Cause / Fix / Tests / Evidence /
Timestamp / Ledger, run ID in the ledger, and an owner real-phone check for anything users touch.
