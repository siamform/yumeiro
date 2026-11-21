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
| Not built | No code in the app for it (checked with a code search on 2026-11-20; two rows were wrong in the first version and are corrected: S-1, PIN-1). Re-check with a wide search before building. |
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
| FREEZE-1 | Touch freezes the app ("not responding") while Home is under the player | The release dex maps the main-thread stack to `HomeScreen` → `AdultContent.loadedAdultApis` → `ExtensionManager.extensionOf` (loop over the plugin map). `isAdultApi` rebuilt the whole provider list on every call, and Home / Yumix call it once per provider while drawing: about N² work on the main thread each time, repeated at every extension-load step and every Most-played change | One 18+ map per extension change (`StampMemo` + `ExtensionManager.providerStamp`), one pass over plugins (`extensionLookup`), Home works the map out off the main thread | Fixed d072e4d8. Fail-before CI 44764157290 (only AdultApiCostTest failed), pass-after CI 44764168235 (Build, test & lint green) |
| FREEZE-2 | Other screens still do provider work while drawing | Same pattern in `OnlineHomeScreen`, `OnlineSearchScreen`, `OnlineHomePicks`, `HomeOnline` (`ExtensionManager.providers()` inside `remember`) | Now cheap (cached map), but `providers()` itself still rebuilds and re-applies overrides per call. Move provider lists to one `StateFlow` in `ExtensionManager`, built on a background thread; screens only collect it | Not built |
| FREEZE-3 | 171 threads in the freeze dump, same number as B-8 | Checked 2026-11-20: the dump lists only 77 of its 171 threads; 23 are OkHttp (Live TV and extension hosts), below OkHttp's own limit of 64 requests. Not shown to cause the freeze; FREEZE-1 (main-thread work) was the cause | Not a proven defect. Re-check with a full thread list from a real phone (Settings → Crash logs) before capping anything | Not reproduced (needs a full dump) |
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
| E1 | Flagship all-in-one super-extension with OTA updates (GitHub Actions + Cloudflare Worker) | Not built. Research + design done 2026-11-21 19:01 +06: docs/E1-MASTER-EXTENSION.md (Hub Catalog API with country / type filters, resolver fan-out over installed sources, own recipes repo with probe + OTA; steps E1-A..E1-D, each flagged and verified) | Extension market, playlist market and feed workflows exist (`extension-market.yml`, `playlist-market.yml`, `app-feed.yml`); Worker api.yumeiro.click is live | A Yumeiro-owned provider (JS or CloudStream) with its own repo, a scheduled build that tests each source, and an OTA manifest the app polls. Legal check of each source first. Owner rule "no scheduled workflows" applies to this agent's runs, so the owner must approve a schedule |
| E2 | Curated, auto-validated Live TV M3U8 (~20 channels per country) | Built, not tested (2026-11-21: curate_live.py + probe behind `YUMEIRO_CURATED_LIVE`, ADR-055; market entries after first publish) | `stream-sync.yml`, `sync_streams.py`, market build with logos (NBUG-14 Verified) | A per-country curated list with a nightly probe that drops dead links, like iptv-org's `playlist:test --fix`, which checks every link and removes broken ones ([iptv-org scripts](https://github.com/iptv-org/iptv/blob/master/docs/scripts.md)). Respect iptv-org's blocklist (DMCA / NSFW) |
| E3 | ABI-split APKs + in-app ABI self-migrating updater + website ABI detector | Built, not tested (2026-11-21: Gradle splits behind `yumeiro.abiSplits`, one versionCode per ADR-054, release.yml + check script; website detector not done) | The updater already understands per-ABI file names (`GitHubReleaseClient.ABI_NAMES`, "-<abi>.apk" and ".tv.apk") | Gradle `splits { abi { … } }` is not set, so releases are universal only. Add splits with unique `versionCode` per ABI and keep a universal APK with the lowest code ([Android: build multiple APKs](https://developer.android.com/build/configure-apk-splits)). Then: release.yml uploads 4 + universal, SHA256SUMS covers all, the website picks the ABI from the browser's user agent, and the updater moves a universal install to the matching split |
| E4 | High-speed background extension engine | Partial (2026-11-21: part 1 built, CI pending: fastest-first order + quarantine of failing extensions, flag `e4_speed_order`; part 2 2026-11-22: visible-rows scheduler = FE-2 picked-first gate + FE-2c call lanes + FE-4b working set (unit Pass 44890073919); low-RAM/heat throttle = E4-T FeedThrottle (flag e4_throttle, fail-before 44889828561 verified, unit Pass 44890073919); Explore guide delay = E4-GUIDE, no change needed (first-run hold only, nothing installed then; it is the pop-up blocker), Not run) | SPD-1 (rows join as each extension loads), home memory cache, extension guard / timeouts (NBUG-8) | Stale-while-revalidate cache with < 50 ms first paint, a scheduler that loads only the visible rows plus two screens, pinned first, sort by measured latency, quarantine slow or failing extensions, throttle on low RAM or heat (MemoryWatch exists; thermal checks exist in 3 files). Remove the Explore guide delay but keep the pop-up blocker. Directly linked to FREEZE-2/3 |

## Part 2: features

| ID | Feature | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| F-01 / F-16 | Sub / Dub / language badges on search posters and Yumix; tap preselects; Yumix sections (Movies, Series, Web series, Dual audio, Sub, Dub) and quality tags | Verified (emulator, phones): F01DubTapDeviceTest Pass on api33-lowram and api36 (44896550636); TV Not run | 79532b29, 961fee20; `ReleaseTags.kt`, `AudioLanguages.kt` | Device test that a Dub tap opens the dub stream (ties to NBUG-5) Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: F01DubTapDeviceTest Pass on api33-lowram and api36 (44896550636). Status Pass (44896550636) for the Dub tag tap; TV Not run (phone test). |
| NF-1 | Episode watch status: Watched / In progress bar / Fresh | Device Pass 44903495726 (api33-lowram, api36) / 44903498117 (tv-api34), 2026-11-21; TV rerun Pass 44906652590 @a7bbd80e: Watched / 6 min left / Fresh on the right rows (earlier fails were test faults NF1-T, NF1-T2, NF1-T3) | 66c52247 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: Nf1EpisodeStatusDeviceTest Fail on all 3 (finding NF1-T: test faults, labels were right: api36 read [Fresh, Watched, 6 min left, Fresh…] where the first 'Fresh' was a row kept by the lazy list off screen; tv-api34 never scrolled to the layout button below the fold; lowram waited for rows before list mode composed). Test fixed, rerun pending. |
| NF-2a | Downloads labels Offline vs Permanent | Done | DownloadsScreen Offline / Permanent label (present before this cycle) | — |
| NF-2b | Download any stream (HLS / DASH muxing) + quality picker | Fixed in code (quality picker); unit green in 44896530640; Unit Pass: fail-before 44901952882, pass-after 44896530640. Device: Not run | NF-2b-1..4: DASH + split audio, TS → MP4 (Built, not tested). 2.1.0: the download prompt offers Best / 1080p / 720p / 480p / 360p for episodes and movies (`DownloadPrompt.Request.startAt`, `Item.maxHeight`, test `DownloadQualityPickTest`) | Real-phone check |
| NF-3 | Extension pop-up / promo blocker | Verified (emulator) | ExtensionPopups 8/8, run 43825449454 | EXT-T1 open |
| NBUG-15a–d | Live TV redesign (filter button, no file names, channels by category, first 50, search, pins) | Verified (emulator, phones + TV) | ledger: fail-before 44411680293, phones 44413808456, 44421879508 | Real-phone check; tied to FREEZE-3 (Live TV probes) |
| F-02 split | Separate Telegram page and Websites page, with navigation shortcuts | Fixed in code; unit green in 44896530640; Unit Pass: fail-before 44901941945, pass-after 44896530640. Device: Not run | Route `telegram` (Screen.Telegram), Settings entries Telegram and Websites, Telegram tile in Live TV tools, Telegram nav-bar shortcut; each page refuses the other kind (`SavedSites.wrongPage`, test `SavedSitesSplitTest`) | Real-phone check |
| F-02 login | Telegram full login (TDLib): phone, code, 2FA password; private and public channels, groups and chats; search inside a chat; their videos AND audio play in Yumeiro's player and can be downloaded (owner add 2026-11-21) | Built, not tested (flag `f02_telegram_login` off, 2026-11-21) | Owner decision 2026-11-21: full login. Design ADR-052 (SYSTEM-DESIGN.md). api_id/api_hash are repo secrets YUMEIRO_TG_API_ID / YUMEIRO_TG_API_HASH. Public channels keep working without login (F-02 Pass 2/2, runs 44595993499, 44598214395) | Engine pins (tdlib-bin branch, TDLib build run 44871959357), CI on the app code, device test on Telegram's test servers, owner real-account check (OMT entry) |
| F-20 | Vault: 3 security questions, `.nomedia`, app lock, incognito (discreet icon dropped by the owner 2026-11-21) | Partial | `PrivateVault.kt`, `AppLockGate.kt`, incognito in 20 files; vault files are in app-private storage (no `.nomedia` needed, gallery apps can't see it) | Not built: security questions. Discreet launcher icon: dropped by the owner 2026-11-21, do not build |
| F-19 | Profile security questions instead of the screen-lock bypass; strict per-profile isolation incl. extensions / repos and Continue Manga | Partial / Broken | Per-profile today: YouTube history, online settings, online history, bookmarks, vault, private downloads (`Profiles.prefsName` users) | Not per profile: manga progress ("Continue reading" leaks across profiles, confirmed in code), extensions and repositories, Live TV pins. 2026-11-20: security questions built (B-19, ba87ce64), extension / Live TV pins per profile (B-20); manga progress per profile (PROF-1). Still shared: extensions and repositories |
| F-10 | Themes: Blue, glass | Verified (emulator, phones + TV): glass tier ReleaseFeaturesDeviceTest.glassTierIsSolidOnLowRamAndTv 44904447255 (phones) / 44906652590 (tv-api34); Blue: F10BlueThemeDeviceTest (tap Ao blue, drawn accent 0xFF7FB2FF on Tsuki Night and 0xFF1F5FBF on Washi Day) Pass 44909154883 (api33-lowram, api36) / 44909158248 (tv-api34) @9bf4c90b; fail-before 44908264228 (accent not applied: stayed gold), 2026-11-21 19:41 +06 | 354082ba, 7396955f; ADR-017 tint only | Device check; Blue theme presence to confirm on device |
| 2x | Hold for 2× speed | Built (26 files mention it) | — | Device check |
| F-18 | Manga previous / next chapter and continuous scroll across chapters | Fixed in code. Unit Pass: fail-before 44899238590 (main compiled; MangaChapterNavTest failed to compile: MangaChapterNav unresolved), pass-after 44896530640. Device: Not run | Reader top bar has Previous / Next chapter; a "Read next chapter" card ends each chapter (vertical) or an extra page (paged). `MangaChapterNav`, test `MangaChapterNavTest` | Real-phone check |
| F-12 | Portrait drama swipe player | Retry path Pass (44904447255). Swipe on one shared player: Pass phones 44912687672 (after F12-HIST + F12-VIEW; fail-befores 44910276470, 44911557702, 44911548689), TV skips by design 44912689981. YouTube 3-min Short mark + landscape fit: unit Pass 44912685131 (fail-before 44910420105). F12-MODE vertical mode (non-YouTube 9:16, swipe up / down through the queue, Back to the normal layout) + F12-CHROME idle-hide: device Pass phones 44916676685, TV 44916693769 (never enters / skips), fail-befores 44913232245 + 44913945589 valid; test fixes F12-TEST-CLING, F12-TEST-CLOCK. Unit + lint full green pending r15 CI 44918012619 (44914717596 failed only on NBUG-13-PORT, fixed). Preload = links resolved ahead (ADR-056). Next: flip f12_vertical_shorts on (F-12-FLAG) and re-run CI + phones + TV on that head | df181804, 4f480803, 0288e8d, f8acd0c, 3d4d780c, 93c73933, a2222bf5, 215fb53d, 7eb17931, bf534934, c3ec754d (phones only) | Device Pass; CI pending, flag still off |
| F-08 | Upcoming hub + Yumix carousel | Verified (emulator): UpcomingDeviceTest Pass on api33-lowram, api36, tv-api34 (44896550636, 44896552485) | 97895d02 (Simkl data, kill switch `UPCOMING`) | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: UpcomingDeviceTest Pass on api33-lowram, api36 and tv-api34 (44896550636, 44896552485). Status Pass (44896550636). |
| Country filter | Country filter for Live TV / feeds | Fixed in code (Live TV); unit green in 44896530640; Unit Pass: fail-before 44901945428, pass-after 44896530640. Device: Not run | Country from `tvg-country` or the iptv-org `tvg-id` suffix (`LiveCountry`), chip row on Live TV when 2+ countries, test `LiveCountryTest` | Real-phone check with a mixed playlist |
| F-17 | Profile statistics | Verified (emulator, phones + TV): ReleaseFeaturesDeviceTest.statsPageShowsTheMapAndTheSelectedDay Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | `StatsScreen.kt`, `StatsMath.kt`; ReleaseFeaturesDeviceTest PASS 44500142144 | Real-phone check |
| F-03 | TeraBox / DiskWala | Partial | Public share links (CloudRecapture, LinkHosts) | Login path needs account cookies (ADR-037); 30 s cut-off is a bug (see B-3 below) |
| F-04 | User WebView site list | Verified (emulator, phones + TV): ReleaseFeaturesDeviceTest.siteListSearchFilters Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 49b28a38 | Device check |
| F-05 | Nuvio / SkyStream / Vega repositories | Partial | Step 1 detected (E14 Pass 2/2) | Owner decision "one registry"; Vega step 4 not built (import runs) |
| F-06 | Volume boost 200 % | Verified (emulator): VolumeBoostDeviceTest Pass on api33-lowram, api36, tv-api34 (44896550636, 44896552485) | 84861229 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: VolumeBoostDeviceTest Pass on api33-lowram, api36 and tv-api34 (44896550636, 44896552485). Status Pass (44896550636). |
| F-07 | External player | Verified (emulator, phones + TV): ExternalPlayerDeviceTest Pass on api33-lowram and api36, run 44904481596 @a7bbd80e, 2026-11-21 19:00 +06: Watched(66000, 60000) for MX and for Choose each time (position and Referer header both made the round trip). Unit Pass (fail-befores 44890380227, 44894173529; pass-after 44894227826). Earlier fails were test faults F07-T..T4. TV Pass: tv-api34 44908157533 (2 tests), 2026-11-21 19:41 +06 | 7713c6e8; position return is built (`ExternalPlayers.result` reads `position` / `extra_position` with `return_result`). Correction: the first version said "not built" | Real-phone check with MX / VLC / mpv Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: ExternalPlayerDeviceTest Fail on both phones (finding F07-T: the stub player in the test APK crashed, NoClassDefFoundError kotlin/collections/ArraysKt, the test APK has no Kotlin runtime in its own process; Yumeiro did start it with the right extras). Stub rewritten in Java, rerun pending. Pass-after CI for F-07-VIS + F-07-RES: 44894227826 Pass (349 classes, 0 failures). |
| F-09 | Watch Later + Favourite on posters | Verified (emulator, phones + TV): PosterActionsDeviceTest (long-press Favourite) on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV (Menu key -> Watch Later) TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 26032196 | Owner reports taps not working; device check |
| F-11 | Manga OCR translation | Built behind a flag | `MangaTranslate`, `MangaTlPlan`, flag `MANGA_TRANSLATE`, reader Translate sheet (device test MangaTranslate). Correction: the first version said "not built" | Real-phone check, size budget |
| F-13 | Offline manga | Verified (emulator, phones + TV): MangaOfflineDeviceTest (half-failed save leaves nothing; saved chapter opens offline) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 84987542 | Device check |
| F-14 | Live TV failover | Verified (emulator, phones + TV): FinalCoverageDeviceTest.j_f14 (dead link fails over to the channel's other link) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 6658998a | Limit parallel probes (FREEZE-3) |
| F-15 | EPG Now / Next | Verified (emulator, phones + TV): FinalCoverageDeviceTest.k_f15 (Now / Next from XMLTV) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | af0a5a14 | Device check |
| Airing alerts | Push alerts for airing episodes | Partial | `airing` in 6 files | End-to-end notification check |
| F-21 | Rich extension metadata on every extension row (Hikari style), owner request 2026-11-21 | Built. Unit Pass (ExtensionLabelsTest: fail-before 44878962117, pass-after green in 44896530640); device screenshot check phone + TV Not run | F-21 commit on r15-stability | Flag `f21_extension_labels` (on, kill switch). Apply in three places: Extensions market / browser, Installed list, the "Show videos from" filter sheet. Each row: title + language code chip (`HDHub4u` `HI`); second line `12 sources • CloudStream • Nuvio • <repo name>` = exact source count, ecosystem tag (CloudStream, Nuvio, Vega, Stremio, Hikari/Yumeiro), repository name, one line with clean ellipsis. Purpose: tell apart same-name extensions from different repos or ecosystems. One shared composable used by all three lists; TV focus and font-scale 200 % checked; unit test for the label builder (count, tags, truncation); device screenshot test on phone + TV. |
| F-22 | Upcoming page: filters + date-grouped sections, owner request 2026-11-21 | Part built. Unit Pass (F22UpcomingSectionsTest: fail-before 44879571787, pass-after 44884554686, green in 44896530640); filters Blocked (Simkl calendar has no country / genre / source data) | F-08 97895d02 | Filter bar with three filters: Country (JP, US, KR, IN, BD, …), Category (Movies, Series, Anime, Web Series, Drama), Extension (installed extensions, same F-21 row selector). Group items by date with headers `Today • Sat, Nov 21`, `Tomorrow • Sun, Nov 22`, then `Sat, Nov 21, 2026` (category prefix when one category is filtered, e.g. `Anime • Sun, Nov 22, 2026`); dates in the user's time zone. Each card shows air time, episode number, genre and country badges. Filters persist per profile. Tests: grouping/labels around midnight and time zones (unit), filter combinations (unit), device check phone + TV. |

## Part 3: bugs

| ID | Bug | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| CL-1 | Audit the `crash-log` branch | Done for the only file there (freeze report) → FREEZE-1 | crash-log c12d27f4 | Owner: keep uploading reports there |
| X-1 | Nuvio / SkyStream / Vega import fails | Partial | Detection only (E14) | Import and run (F-05 step 2–4) |
| B-3 | TeraBox stops at 30 s; slow cloud-link detection | Not reproduced | `CloudRecapture` has no 30 s cap (refresh budget 2 per 10 min, capture timeouts from `PageStreamCapture`). Likely cause to confirm: the page served a preview stream (TeraBox gives short previews without login) | Needs a real share link that stops at 30 s, then a fail-before test on the captured URL |
| T-1 | Raw torrent info-hash → magnet | Fixed (B-12). Unit Pass (fail-before 44771332825, pass-after 44775798224; still green in 44896530640) | `LinkHosts.magnetFromHash`; Play Link accepts a bare hash; Stremio infoHash streams become MAGNET links with the add-on's trackers (download through TorrServer) | Real-phone check with TorrServer |
| S-1 | Search hangs, every keystroke fires, feed shuffles | Guarded in code; hang not reproduced | `OnlineSearchScreen` already waits 450 ms after typing, cancels the old search (LaunchedEffect key), `OnlineRepository.search` runs sources with a permit limit and a timeout, results ranked stably (`SearchRanker`). Correction: the first version of this list said "no debounce"; that was wrong | Reproduce on a real phone with many extensions; FREEZE-1 removed per-provider work from this screen too |
| PIP-1 | Back on the stream page should go to PiP | Built | `PlayerBackRule.leave` sends Back to PiP when playing and PiP is supported, following Settings → Player → Back button | Real-phone check |
| PIN-1 | Pin cap 8, duplicate extension cap 2, remove the redundant pinned row | Fixed in code (row removed), CI pending | Pin cap 8 exists (`PinnedExtensions.MAX = 8`). The pinned chip row on the Yumix feed is removed; pins order the filter sheet | Same-name extensions from two repos: see PIN-2 |
| PIN-2 | Same extension name from two repos shows once | Fixed (B-22, bd3b4ca6). Unit Pass (SameNameExtensionsTest: fail-before compile, see BUGS B-22; pass-after 44775798224; still green in 44896530640) | `ExtensionManager.uniqueNames`: per name the earliest installed keeps it (its history / bookmarks / pins stay), the others become "Name · Repo" | Real-phone check with two repos shipping the same name. Note: if the first one is removed, the other gets the plain name back after a restart and its "Name · Repo" history stays under the old label |
| BUG-1 | Resume across entry points | Verified (emulator) | ResumePointsTest; device runs in CHECKLIST | Real-phone check |
| BUG-2 | YouTube channels + quality selector | Verified (emulator) for channels; quality = BUG-4 | 44116513171 | Real YouTube is behind YT-WALL (P3 / P5 / M3 Blocked) |
| BUG-3 | Download speed drops after 50 % | Blocked: owner (real phone, OMT-20) | 40ea96e, 3c85a72 | Real-phone run |
| BUG-4 | Upscaling and quality mismatch | Verified (emulator); real YouTube Blocked: owner | Bug4QualityListTest | — |
| EXT-P | External player integration + gallery downloads | Partial | F-07, NBUG-4b | Return position; gallery app view = OMT-17 |
| PROF-1 | Continue Manga leaks across profiles | Fixed (B-11). Unit Pass (MangaLibraryProfileTest: fail-before 44771329328, pass-after 44775798224; still green in 44896530640) | `MangaLibrary` file now from `Profiles.file` (default profile keeps the old file) | Real-phone check with two profiles |
| BUG-5 | TV UI | Verified (emulator) for NBUG-2a–e; TV-4 Built, not tested | runs in CHECKLIST | OMT-21 |
| BUG-6 | Music player touches pass through | Verified (emulator) | 43914724044 | — |
| BUG-7 | Player / IPTV freezes, missing downloads | Verified (emulator) except 7d (GPU, owner) | 43946324687 | Likely related to FREEZE-1 on real phones; re-test after the fix |
| NBUG-5 | Dub count | Verified (emulator) | ledger | — |
| NBUG-6 / 9 | DNS | Done per owner decision (sequential fallback 1.1.1.1 then 9.9.9.9); per-feature DoH deferred (ADR-037) | REMAINING-2026-11-19 | Real-network check OMT-31 |
| NBUG-7 | Backup restores extension list but not the binaries; composite keys for duplicate names | Verified (emulator, both phones) | G9-1/2/5/7, ADR-021; device 44315631356, 44318262848 | Real-phone restore after "Clear all data" |
| NBUG-8 | Infinite loading for one extension | Guarded in code (timeouts) | REMAINING row | OMT-34 |
| NBUG-10 | "Show videos from" lists only 2 | Verified (emulator, phones): Nbug10FilterSearchDeviceTest Pass on api33-lowram and api36, run 44901993315, with the NBUG10-IME fix (fail-before 44901948952, pass-after 44901991356). TV: Not run by design (the test skips on TV: the keyboard opens only on OK, FINAL-39; 44908157533 assumption skip) | 4c6345f8 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: Nbug10FilterSearchDeviceTest Fail (finding NBUG10-T: api36 compared against only the rows drawn before the search, AnimePahe / Yumeiro Drama were below the fold and matched 'a'; lowram the system keyboard showed 14 s after the tap). Test fixed, rerun pending. |
| NBUG-11 | Adult titles in Most Played | Fixed (ADR-023); FREEZE-1 keeps it fail-closed | — | — |
| NBUG-12 / 13 | Access denied loop; proxy | Proxy Pass (NBUG-13 runs 44593053433, 44593085810, 44600322143); loop guarded | MASTER-CHECKLIST-FINAL | OMT-32 |
| NBUG-14 | IPTV logos | Verified | b212379 | — |
| NBUG-16 | Auto-update sync loop | Verified (CI + emulator + TV emulator) | NB16-1…7, ADR-025 | Real-phone check over a few days |
| C5 / C6 | Remaining audit items | Open | owner list | Track here as they are taken |

## Owner decisions needed (not reopened)

1. Telegram login: decided 2026-11-21, full login (TDLib). api_id / api_hash received 2026-11-21 and stored as repo secrets YUMEIRO_TG_API_ID / YUMEIRO_TG_API_HASH (BuildConfig TG_API_ID / TG_API_HASH). Never write the values in the repo.
4. Discreet launcher icon: dropped by the owner 2026-11-21. Do not build it.
2. Super-extension (E1) and a scheduled validation workflow (E2): the "no scheduled workflows" rule is for agent runs;
   a product schedule needs the owner's yes.
3. Vega / Nuvio / SkyStream stay one registry; import (step 2–4) is a new release lane.

## Order of work (Now / Next / Later)

Now (done; owner 2026-11-20 "no 2.0.1": these ship in 2.1.0)
1. FREEZE-1 (this push), FREEZE-2, FREEZE-4 counter, GATE-1 ANR test.
2. PROF-1 manga progress per profile (privacy leak).
3. S-1 search debounce (fires work per keystroke on the main path).
4. Owner real-phone tests U-30 and the mandatory list, then release 2.0.1.

Next (2.1, owner 2026-11-21: no release until all is fixed and tested)
- F-02 Telegram full login (TDLib, ADR-052): app code Built, not tested (flag off) 2026-11-21; engine pins and device tests next.
- B-29 TeraBox full video (done in code), B-30 DiskWala hand-off (done in code), B-31 built-in torrent engine.
- Owner goal 2026-11-21: Yumeiro at the top of its class (CloudStream, Hikari, Aniyomi, Nuvio). Matrix, bars and the G-01..G-10 gap rows (debrid, skip credits, seek previews, sync, MDBList, FCast, ...) in docs/GOAL-TOP-OF-CLASS.md; they follow the owner's order of work.
- B-31 owner add 2026-11-21: the built-in engine is always ready (one engine and one loopback server for the app's life, no per-link setup, no TorrServer); every magnet, info-hash or .torrent link plays; and it should speed up download, playback start and seeking where possible (sequential + priority pieces around the play position, DHT/PEX/LSD and extra public trackers, peer and connection limits sized for a 2 GB phone and TV, resume data so a replay starts from cached pieces). Test hash BF2063A5E9A69F14716288FCA7C2D6078BDA7369.
- B-31 status 2026-11-21: Built, not tested (ADR-053, flag `b31_builtin_torrent` off). Next: CI on head; device test with the test hash on api36, api33-lowram, TV api24 (first frame <= 15 s, seek <= 5 s, memory on 2 GB); then OMT for a real pack and a .torrent URL.
- B-31 seek 2026-11-21 17:20 +06: device seek 7.6 s / 10.7 s (phones), 12.7 s (TV) over the 5 s budget (B31-SEEK2); fix: request_queue_time 1 and the seek piece alone at the front; device re-run pending.
- B-32 extensions that load nothing ("Nothing came back from this extension", e.g. HindiSubAnime): find the real cause and say it. Status: Built, not tested. Cause for HindiSubAnime: its parent AnimeDekhoProvider reads `a.dgt-card`, which hindisubanime.co does not use (extension bug). Yumeiro now checks the site and says the real reason with a fix button.
- F-21 extension metadata rows, F-22 Upcoming filters + date sections.
- E1 Yumeiro master extension (replaces the removed starters, B-26).
- E4 engine (scheduler, SWR, quarantine, throttling) and FREEZE-3 thread cap.
- E3 ABI splits + website detector.
- F-18 manga chapters, F-20 / F-19 security questions, `.nomedia` (discreet icon dropped by the owner 2026-11-21).
- PIN-1, T-1 magnet, B-3 TeraBox 30 s, NF-2b quality picker, F-07 position return.

Later (2.2+)
- E2 curated Live TV per country, F-11 OCR translation, F-05 import steps. (Telegram login moved to Next: owner
  decided full login 2026-11-21. E1 moved to Next: owner 2026-11-21.)

## Acceptance for every item

Root cause written, regression test that fails before and passes after, commit body Cause / Fix / Tests / Evidence /
Timestamp / Ledger, run ID in the ledger, and an owner real-phone check for anything users touch.
