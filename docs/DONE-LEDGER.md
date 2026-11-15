# Done ledger

What is already done in Yumeiro, built from the commits. The commits are the source of truth:
summaries, status pages and chat reports were only used to find commits, never as evidence.

- Branch of record: `r15-stability` (89 commits, tip at the time of writing `50e734d`).
- Changed files for every commit: [`docs/evidence/ledger/COMMIT-FILES.md`](evidence/ledger/COMMIT-FILES.md)
  (generated from `git show --name-status`; images are counted, not listed).
- Device runs: GitHub Actions `device-check.yml`, two emulators: **api33-lowram** (Android 13,
  Pixel 3a, 2 GB RAM, 192 MB heap, low-end path) and **api36** (Android 16, Pixel 6, 4 GB).
  Run IDs link to `https://github.com/siamform/yumeiro/actions/runs/<id>`.
- Last re-check: 2026-11-15 (this file's first version). Local unit re-run of every test named
  below: **29 classes, 135 tests, 0 failures, 4 skipped** (the 4 are opt-in live-network tests,
  `-Dyumeiro.live=true`). Command: `./gradlew testMobileDebugUnitTest --tests 'dev.asfakul.yumeiro.r15.*'
  --tests '*.round2.*' --tests '*DramaHealthTest' --tests '*YumixShowcaseTest' --tests '*BackgroundWorkTest'
  --tests '*LiveFeedTest' --tests '*PinnedExtensionsUiTest' --tests '*ExternalDownloaderTest'`.

## Status words

| Status | Meaning |
|---|---|
| `verified on device` | An instrumented test passed on both CI emulators, and I read the run's log or report |
| `unit only` | Only JVM or Robolectric tests prove it |
| `unverified` | Code exists but no test proves the behaviour (or the test cannot run on CI) |
| `broken again` | It was reported done and later found broken; the row says what replaced it |
| `open` | Not done |

Emulators are not real phones. No item is verified on a real low-RAM phone or a real TV yet
(see `docs/MANUAL-TESTS.md`, scripts P and T).

## Mapping keys

- **Bug list**: the R15 master prompt phases (`R15-P1` OOM/ANR, `R15-P2` player, `R15-P3` live
  feeds, `R15-P4` downloads/PiP, `R15-P5` extensions/forced downloads/crash logs, `R15-P6`
  proposal, `R15-P7` audit/design), the R16 handover Step 2 items (`S2-…`), and the v1.8.1 owner
  orders (`O1`–`O9`, `docs/STATUS-v1.8.1.md`). Older rounds are `R1`–`R14`.
- **Audit group**: Step 3 groups G1–G12 and item IDs from `docs/INVENTORY.md` (H, E, P, Y, L, M,
  T, D, S, A, I, X, N).

## Re-check of the reported items (Step 1B, point 1)

Each claim from the handover, checked against the code and a test run.

| Claim | What I checked | Result |
|---|---|---|
| Drama playback, 4 rows x 18, 1080x1920 first frame | Run 43657727593 (0cb815a) log; repeat run 43676961108 (db40142), 5 runs per device, log lines `drama plays: Daddy, Save Me 1080x1920` | True. 10/10 runs pass |
| Drama start time reduced | Same runs, `drama timing` lines | True, but the earlier single samples (2.0 s / 2.6 s) were optimistic. 5-run medians: **3.8 s** api33 (2.8–7.1 s), **3.5 s** api36 (3.2–4.7 s). Before: 9.7 s / 34.0 s |
| Dead manual source pick rotates again | `OnlineSession.kt:290–295` (manual pick clears its failed mark; pending failover cancelled); `RecoveryStateTest` 5/5 locally; device `d_manualPickOfADeadSourceRotatesAgain` in run 43652020450 and in the 18/18 run 43657729675 | True |
| 00:00 label instead of `--:--` | `VideoPlayerControls.kt:502, 637`; `DurationLabelTest` 3/3 locally; device labels `[00:00, 1:31:44]` in run 43652020450 | True. Note: the first claim (3e2114e) was wrong; it showed a pulsing pill. Fixed in 2be78be |
| Forced download (DASH into one MP4) | `ForceGrabTest` 9/9 run, 3 live-network tests skipped; device `ForceDownloadDeviceTest` reports in 43657729675 | True (3,944,838 B, video/avc + audio/mp4a; segmented 36,632,975 B, 634 s) |
| View downloads moves the player to PiP; a new video expands it | `PlayerSurfaceRulesTest` 3/3; device `SurfaceModeDeviceTest` OK in 43657729675 | True |
| Crash report exported as a file | `CrashExport.kt:40,52` (IS_PENDING 1 → write → 0); device: 192,627 B / 192,638 B in 43632313418 | True |
| Full suite 7 classes / 18 tests at 0cb815a | Artifacts of 43657729675: `OK` 1+4+1+1+5+3+3 = 18 on each device | True |
| Unit 16 classes / 83 tests (r15 package) | Local re-run: the r15 package is now **16 classes / 86 tests** (GateLaneTest grew in c093f81), all pass | True, count updated |
| Pinned extensions | `YumixFilter.kt:107` pinned first; `PinnedExtensionsTest` 3/3, `PinnedExtensionsUiTest` 1/1; device `PIN_RESULT PASS`, order `[Internet Archive, Yumeiro Drama]` → `[Yumeiro Drama, Internet Archive]`, survives recreate, run 43671202859 both devices | True (`verified on device`) |
| Quality list parity | `QualityParityTest` 3/3, `QualityRound2Test` 4/4 (+1 live skip); device `QUALITY_RESULT PASS heights=[1080,720,480,288,184]`, dialog rows match the manifest, 184p → 320x184 and 1080p → 1920x1080 decoded, run 43668984393 both devices | True for HLS. YouTube list not proven on device (CI IPs get a bot check) |
| Feed scrolling never replaced by stale rows | `LivePagedTest` 6/6, `LiveFeedTest` 6/6; device `FeedPositionDeviceTest` | **Partial.** YouTube, live TV, online music and the video source menu pass on both devices (43673890077). Home and Yumix still fail; see row FEED-1 |
| CI concurrency fix | `device-check.yml` concurrency group includes the test filter (0cb815a); parallel runs 43676915487 and 43676961108 both ran | True |
| Earlier rounds (OOM/ANR, player, sources, downloads) | See the R15-P1 rows: re-proven on device in R15. Pre-R15 rows are `unit only` unless an R15 device test covers them | Mixed, per row |

## Ledger

Columns: **Item | Area (bug list → audit) | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits**.
File lists are shortened to the main files; the full list is in `COMMIT-FILES.md` under each hash.

### R15/R16: stability (OOM, ANR, stalls)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| ST-1 No regex compile on main | R15-P1 → G12 X3 | d13e6b8, 7f528fe, ec8ca95 (`text/RegexWarmup.kt`, `r15/RegexGuardTest.kt`), fd446ac | `Regex(...)` built in hot paths on main (pager prefetch, titles). Patterns moved to holder objects; all 91 holder classes warmed on `yu-startup`; a test fails on a pattern built inside a function | `RegexGuardTest` 2/2; stress runs from 43645273428 on; 40-run campaign 43671209244 40/40 | api33-lowram, api36 | verified on device | Third-party code (extensions) can still compile patterns; that is caught by ExtensionGuard, not prevented |
| ST-2 No OOM under stress on low RAM | R15-P1 → G12 X2 | d13e6b8, 1192f84 (`OnlineComponents.kt` SoftBackdrop replaces live blur) | Large bitmaps and GPU blur per card. Low-end image sizes, decoded tiny poster instead of blur | 40-run campaign 43671209244: app FATAL/OOM 0 | api33-lowram (192 MB heap), api36 | verified on device | Emulator only; not a real 2 GB phone |
| ST-3 Tab pager endless recomposition / spin after D-pad focus | R15-P1 → G1 H4/H5, G12 X3 | 82a5996 (test), 27a0e9d (`MTPlayerApp.kt`: `TabPagerNoNestedScroll`, never rest between pages), 2ba28e1 | The pager took a focused card's bring-into-view as nested scroll and never settled | Runs 43653623319, 43671209244 (never-idle 0, ComposeNotIdle 0) | both | verified on device | |
| ST-4 Foreground-service start crash | R15-P1 → G3 P1, G12 X5 | b0e596d (`service/VideoPlaybackService.kt`) | `ForegroundServiceDidNotStartInTime`: startForeground called late. Now called at once with a placeholder notification | Run 43626650174 failed, 43629395323 onward clean | both | verified on device | |
| ST-5 Audio-switch deadlock | R15-P1/P2 → G3 P6/P14 | b0e596d (`stream/StarvationSafeLoadControl.kt`, `StreamMediaFactory.kt`) | Load control waited for a buffer that a switched audio track never filled. Starvation-safe control lets playback start | `StarvationLoadControlTest` 3/3; device `d_everyAudioRenditionIsAudible` (AAC, AC3) | both | verified on device | |
| ST-6 Start-up stalls (prefs, WebView UA) | R15-P1 → G12 X3 | b0e596d (`startup/PrefsWarmup.kt`, `online/web/BrowserCheck.kt`), 50e734d (`yumeiro_tv_mode`, `play_counts` warmed; `PushAlertsCard.kt`, `HomeScreen.kt` loads on IO), a5b8f60 (13 more prefs files, parallel loads, filesDir and Inbox warm) | SharedPreferences first reads and WebView UA on main. Warm before first frame; UA deferred | `StartupGuardsTest` (now with an inbox test that catches a planted fault); full device suites 43680668799 (dfe9234) and 43683535994 (10bbd6e) pass on both; stress 43683528288: TvMode.init and PlayCounts no longer in the stall list | api33-lowram, api36 | verified on device (b0e596d, 50e734d); a5b8f60 unit only until stress 43687396407 is read | Cold start of unprofiled code still stalls just after boot (see ST-7) |
| ST-7 Main-thread pauses "a small fraction" | S2 → G12 X3 | 50e734d, dfe9234, d6ff111, 9bbd223, a5b8f60, 5af4f0d `PlaybackSession.releaseInTwoSteps` (all partial) | Named app stalls moved off main: history write in the player's dispose (dfe9234; the row used to say "player release", which was wrong: release() stayed on main), `pageExplore` (already on IO in `OnlineRepository.page`), `videoSourceOptions` memo (dfe9234); player release now stops first and releases 1 s later, with the main-thread time logged as `YuSession player release held main N ms` (5af4f0d), audio effects on `yu-audio-fx`, browser UA, YtHistory (d6ff111), catch-up on `yu-catch-up` (9bbd223), prefs warm-up (a5b8f60) | Steady-state app stalls ≥700 ms per run: api33 0.25 (43671209244) → 0.4 (43680725238) → 0.5 (43683528288); api36 0.1 → 0.1 → 0.3. Then 0.2 / 0.2 steady and 0.6 / 0.5 cold (43697447466, after 18dda67): steady is slightly better, cold is noise. Repeat campaign 43705867413 (97bb255, 10 runs per emulator, 20/20 pass): steady 0.4 / 0.4 per run (max 1.26 s / 1.28 s), cold 0.8 / 0.3 (max 8.5 s / 7.6 s). Steady went back up from 0.2 / 0.2, so the 18dda67 gain is noise too. No large improvement can be claimed. The large stalls cluster in the first minute after boot, mostly framework Compose measure under boot load | api33-lowram, api36 | **open** | Next: baseline profile (OPEN-SOURCE-COMPARISON P1), then a new campaign; re-measured in 43687396407 and 43697447466 |
| ST-8 Carousels pause while focused, touched or reduced motion | R15-P1 → G1 H1, Y6, X8 | 9a3e375 (`HomeSections.kt`, `OnlineHomeHero.kt`), 48bcfb4 | Auto-advance kept recomposing under focus | Run 43648850998: 0 frames rendered in 2 s windows while idle | both | verified on device | |
| ST-9 Yumix explore crash (ConcurrentModificationException) | found in R16 → G4 Y1 | c093f81 (`OnlineHomeScreen.kt`) | List mutated while composed; 3 of 40 runs crashed. Snapshot copy before iterate | 40-run campaign at 15d8e33: 40/40, 0 FATAL | both | verified on device | |
| ST-10 OnlineHistory written on main | found in R16 → G5 L | dfe9234 (`OnlineHistory` single writer) | Stress named a disk write on main. One background writer | Unit suite; stress 43683528288 no longer names it | api33-lowram, api36 | verified on device | |
| ST-11 Scrobbler title regex failed one stress run | found in R16 → G11 I | 10bbd6e (`TrackNames.stripArtistPrefix`) | 43680725238 run failed on a title pattern. Prefix stripped without a per-call regex | 43683528288: 20/20 | api33-lowram, api36 | verified on device | |
| ST-12 Source menu rebuilt every frame | found in R16 → G4 Y5 | dfe9234 (`videoSourceOptions` remembered) | Built inside composition. Memoised | 43683528288: no longer in stall list | both | verified on device | |
| ST-13 Audio effects, browser UA, YtHistory on main | found in R16 → G3, G12 X3 | d6ff111 (`EqualizerManager.kt`, `BrowserCheck.kt`, `HomeScreen.kt`) | Single `yu-audio-fx` executor; UA from cache or estimate on `yu-browser-ua`; YtHistory on IO | Unit suite 857/0 fail; stress pending (43687396407) | unit only | unit only | |
| ST-14 Catch-up work on main at app start | found in R16 → G12 X3 | 9bbd223 (`AppVisibility.kt`) | PushWake.reload took 1.1–1.3 s on main (43680725238). Runs on `yu-catch-up` | Unit suite; stress pending | unit only | unit only | |
| ST-15 More prefs files read on main | found in R16 → G12 X3 | a5b8f60 (`PrefsWarmup.kt`, `StartupGuardsTest`) | StrictMode list from 43683520404 named 13 files. Warmed in parallel | `StartupGuardsTest` incl. planted-fault inbox test; stress pending | unit only | unit only | |
| DEP-1 Dependabot branches | G11 I9, G12 X10 | a799e27 (Robolectric 4.18, not 4.17), bcc6178 (server TypeScript 7.0.2, wrangler 4.160) | 4.17 still failed 7/7 `OnlineFeaturesTest`; 4.18 passes. jsoup 1.24.1, guava 33.8.0, fragment 1.9.1 were already in dc4e469. Server: npm ci, tsc clean (planted error caught), 20/20 server tests, wrangler dry-run | Unit suite 145 classes, 857 tests, 0 failures | local, CI | verified (no device part) | Dev/test tools only |
| FR-1 Test helper dismisses system ANR dialog | test infra | dfe9234 (`FirstRun.kt`) | Boot-time "isn't responding" dialog blocked taps. Helper closes it and logs it | Full suites 43680668799, 43683535994 | both | verified on device | Hides nothing: the dialog is logged |
| X7-1 Account tokens could travel over plain HTTP | G12 X7 | 149ef42 (`res/xml/network_security_config.xml`, `TokenHostsHttpsOnlyTest`) | Cleartext is on app-wide for IPTV/LAN; the HTTPS-only list missed Trakt, AniList, MyAnimeList, Kitsu, Plex, Google sign-in, OneDrive, AniSkip. Added | `TokenHostsHttpsOnlyTest` 2 + `network.*` 17/17 (local JVM) | — | unit only (config file; a unit test is the right proof) | The platform enforces the rule; no device test can add more |
| X8-1 Automated accessibility checks on the main tabs | G12 X8 | (this commit) `AccessibilityDeviceTest.kt`, `ui-test-junit4-accessibility` | No automated check existed. ATF checks (touch target, contrast, labels, duplicate descriptions) run on Home, Videos, Music and Library through the real UI; ERROR findings fail the test and are logged as `YuA11y A11Y` lines | pending device run | — | unverified | Checks the first screen of each tab only; TalkBack reading order still needs an owner test |
| EXT-T1 E11 cleanup Back closed a pop-up instead of the screen (api33-lowram) | G2 E11, test infra | (this commit) `ExtensionsDeviceTest.kt` diagnostics | Run 43744809503 api33-lowram: E11 passed, but the cleanup Back closed a second window that had opened over Source status 18 s in (logcat: window 195fe20 removed on Back); Source status stayed. Test now logs every window and tag before Back and on failure, to name the pop-up | 43744809503 logcat | api33-lowram | open | Not reproduced on api36. Cause of the pop-up not yet known |

### R16: Group 1 (Home and navigation)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| H6 Predictive back on Home (double back exits, app stays warm) | G1 H6 | 4098177 (manifest `enableOnBackInvokedCallback`, exitArmed), 89f7553 (one handler, decision in `onBack`), 14c5998 (back cancels a running tab jump); tests e28df74, ac6e412, 3479bf8, e9c281c | Android 16 turns predictive back on for target 36. First back arms exit for 2 s and turns our handler off, so the second back goes to the system (animation, no finish()). Two handlers flipping during a swipe made the system cancel a back (sendCancelIfRunning, 43689402737); now one handler | 43687458863, 43688381102 failed (test clock, not the app); 43690918679: 6/6; 43692234004 + 43693532355: 8/8 BackNavigation (one miss before e9c281c = reliability sheet took the back); app ends STARTED, not destroyed | api33-lowram, api36 | verified on device | Real-hardware gesture (edge swipe, animation) still needs script P |
| H1–H4 Tabs, swipe, back to Home | G1 H1–H4 | 45abd1b, ebfb79d, 6df7c1f (`HomeNavigationDeviceTest`) | Device test for tabs and back | 43685183725, 43686104737: 5/5 on both | api33-lowram, api36 | verified on device | |
| H7 VIEW intent while running (singleTop) | G1 H7 | 6df7c1f | onNewIntent delivers to the running activity; back minimises the player to the floating window | 43686104737 log: "intent has been delivered to currently running top-most instance" | both | verified on device | |
| H8 Recreate keeps tab, H12 Continue widget, H13 Inbox | G1 H8/H12/H13 | 45abd1b | | 43685183725, 43686104737 | both | verified on device | Widget placement on a launcher is manual (script P) |
| H10 Top-bar search suggestions off main | G1 H10 | 77fc8ca (`MTPlayerApp.kt` produceState, 120 ms debounce on Default), 2c109ac (test) | Ranking ran in composition. Moved off main | 43688049790: 3/4 (lost `adb input text` keys, test error); 43689481257: 6/6 with field text logged; 43693532355, 43696246671: pass | api33-lowram, api36 | verified on device | |
| Y6/H1 Carousels stop when the app is not started | G1 H1, G4 Y6 | f021791 (`LifecycleState.kt`, `HomeSections.kt`, `OnlineHomeHero.kt`) | Timers kept running in background | Unit suite; HomeNavigation runs show no stall from them | both | verified on device (indirect) | No direct frame count while stopped yet |
| H2 Continue cards | G1 H2 | ec035ac (test) | Empty card opens Videos; back returns Home | 43693532355, 43696246671: 8/8 | api33-lowram, api36 | verified on device | With-history case is covered in G3/G5 |
| H3 Nav-bar shortcut | G1 H3 | ec035ac (test) | Inbox shortcut on, tap opens Inbox, back closes | 43693532355, 43696246671: 8/8 | both | verified on device | |
| H5 TV rail focus | G1 H5 | a53effb (`TvNavRail.kt` focus group with onEnter to the selected tab; `MTPlayerApp.kt` TV pages are focus groups: Left exit → rail, Right exit cancelled), tests ec035ac, e9c281c, ec6ebd8 | Left from Music entered the rail on Home, and once moved focus into the off-screen Home page (beyond-bounds focus search) | 43694972929 failed 4/4 (logged focus on `tv_nav_tab_home`, once `home_setup_card`); 43696246671 after a53effb: 4/4, Left → `tv_nav_tab_music`, Down → `tv_nav_tab_videos`, centre → page 2 | api33-lowram, api36 (TV layout forced on phone emulators) | verified on device | Not a real TV or remote; script T |
| H7b Shared text link opens the player | G1 H7 | ec6ebd8 (test) | SEND text/plain → `IncomingMedia.extractUrl` | 43694972929, 43696246671: 8/8 | both | verified on device | SEND of video files is not accepted (enhancement) |
| H2 fresh-data case | G1 H2 | 3342106 (`HomeNavigationDeviceTest` h2) | Full suite 43697438813 failed h2 on both: Continue held a fresh entry, so the play button resumed the video (test error). Test reads the button description and checks either path | Full suite 43701056608 pass on both | api33-lowram, api36 | verified on device | |
| FEED-1 precondition | G1 H1 | 3342106 (`FeedPositionDeviceTest`) | 43697438813 api33: only 7 of 16 swipes moved (10 cards, 0 position problems). "Swipes moved" is now an assumption (skipped, logged `SKIP(not exercised)`); position checks unchanged | 43701056608 pass | both | verified on device | Feed slot starvation by background onshort.net calls goes to Group 4 |
| ST-17 LazyList bring-into-view spin | G1 H5, G12 X3 | 3342106 (stress test logs the focus chain and a screenshot on a spin) | 1 of 20 stress runs, api36 (43697447466): Compose never idle in `pagerFocusAndScrollStress`; main thread in `ContentInViewNode.animateToZero` under the default LazyList `BringIntoViewSpec`. Back cleared it. Not ST-3 | Full suite 43701056608: 0 spins. Stress repeat 43705867413 with the focus-chain log: 0 of 20 runs spun (no "spin focus:" line) | api36 | **open** | Seen 1 time in 40 runs; cause not found. Kept open: the log is in every stress run now, so the next spin names the node |
| ST-16 Backdrop, bookmarks, downloads read on main | G12 X3 | 18dda67 (`PrefsWarmup.kt`, `HomeScreen.kt`, `StartupGuardsTest`) | StrictMode in 43687396407 named them at Home first draw. Warmed on `yu-prefs`, Home loads on IO | Unit 858/0; planted fault (warm lines removed) fails the new test; stress 43697447466 StrictMode: app frames down to one chain (`NotifyPacer.read` from `NotifyAlarmReceiver`, Group 10) | api33-lowram, api36 | verified on device | |

### R16: Group 2 (Extensions and market)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| E6 Bulk select, update, enable, remove | G2 E6 | 5b1263e (`online/ExtensionBulk.kt`, `ExtensionsScreen.kt` `SelectionBar`, `ExtensionBulkTest`, `ExtensionsDeviceTest`) | No bulk actions; "Update all" ran in parallel with one toast per item. Sequential runner, one summary, confirm before remove | `ExtensionBulkTest` 4/4; device 43704211410 6/6, 43705847970 7/7 | api33-lowram, api36 | verified on device | Long-press on the chip row is taken by the chips |
| E6 Back ends selection | G2 E6 | bfde23d (`ExtensionsScreen.kt`, `SubPageHeader`) | Back left the screen while selecting: the header's back callback was added last, so it ran first. Back goes through the header's handler | Found failing in 43701058752; passes 43704211410, 43705847970 | both | verified on device | |
| E8 Readable link errors (whole cause chain) | G2 E8, G10 | 9be5afb (`OnlineErrors.kt`, `OnlineErrorsChainTest`), bfde23d (`UniversalAdd`) | Raw "Unable to resolve host" shown: the root was a `GaiException`, so a root-only check missed it. Checks every cause | `OnlineErrorsChainTest` 3/3; device text in 43704211410 | both | verified on device | `PlaylistMarket.friendlyError` still checks the top only (G10) |
| E12 18+ off hides adult search results | G2 E12 | bfde23d (`OnlineSearchScreen.kt`) | Adult providers and NSFW items appeared in search with 18+ off. Filtered | Code review; `e12` covers the override part only | — | unit only | Needs a fake NSFW provider on CI |
| E11 Source status follows the engine | G2 E11 | bfde23d (`SourceHealthScreen.kt`) | Screen keyed on nothing, checks on main, cancellation swallowed. Keyed on engine state, IO, rethrow, `source_health_run` tag | Device `e11` 43704211410, 43705847970 | both | verified on device | |
| E9 Starter extensions retry | G2 E9 | 97bb255 (`StarterExtensions.kt`, `StarterExtensionsTest`, `e9`) | A partial first run was marked done and never retried. Installs only missing picks, up to 3 launches, rethrows cancellation | Unit 3/3; device 43705847970 7/7 | both | verified on device | |
| E12 Provider override defaults thread-safe | G2 E12 | bfde23d (`ProviderOverrides.kt`) | Plain map written from several threads. `ConcurrentHashMap` | Device `e12` 43704211410 | both | verified on device | |

### R16: Group 3 (Player)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| PL-11 Cast starts without blocking | G3 P17 | b5d2d5a (`cast/CastSupport.kt`, `OnlinePlayerUi.kt`), 4c885fe (`LocalPlayerDeviceTest e`) | `.result` read on an unfinished `getSharedInstance` Task threw, and the catch turned Cast off for the process. Task completes into a `StateFlow` the Cast buttons watch | `e`: `ready()` 34–132 ms, ready after 90–327 ms, 43707028426, 43708220122, 43709093938 | api33-lowram, api36 | verified on device | Casting to a TV needs a real Chromecast (script P/T) |
| PL-12 Cast sends only subtitles the TV can show | G3 P17 | b5d2d5a (`CastSupport.castableSubtitles`, `CastSubtitlesTest`) | SRT sent as `application/x-subrip`; receivers show TTML/WebVTT/CEA-608 only. SRT tracks dropped | `CastSubtitlesTest` 1/1 | — | unit only | SRT is now converted instead of dropped: PL-19 |
| PL-13 PiP refusal is reported | G3 P10 | 5bfa07c (`VideoPlayerModal.kt`), fd1a2f5 `LocalPlayerDeviceTest h` | `PipController.enter` result ignored: a refused PiP did nothing. Error message | PiP itself `c` 43708220122, 43709093938; refusal forced with `appops set … PICTURE_IN_PICTURE ignore`, device `h` pending | both | unverified | Waiting for device `h` |
| PL-14 AniSkip failures are not cached | G3 P15 | e1714fe (`online/anime/AniSkip.kt`, `AniSkipCacheTest`), e9a9c35 `AniSkipDeviceTest` | A timeout cached "no segments" for the process. Only success or 404 cached | `AniSkipCacheTest` 2/2; device `AniSkipDeviceTest` (real offline, live API, counted requests) pending | — | unit only | Live API: a 5xx online answer is not cached by design and would fail the "cached" step |
| PL-15 Local player through the UI | G3 P1, P6–P8, P10–P12, P18, P19 | 2744a23, b5d2d5a, 5bfa07c, 3dc3914 (`LocalPlayerDeviceTest`, test clip asset) | New device test: device clip, audio and subtitle tracks, speed, play/pause, screen wake, lock, PiP, resume, heat guard | 43709093938: api33 5/5, api36 4/5 (b: test timing, fixed in 9877afb); 43712469920 ×2: 5/5 on both | api33-lowram, api36 | verified on device | Video sleep timer does not exist (proposed) |
| PL-16 Top bar fits; the rest goes to More | G3 proposal (portrait "More") | 3ce721d `ui/screens/video/TopBarFit.kt`, `VideoPlayerControls.kt`, `TopBarFitTest`, `LocalPlayerDeviceTest f` | Portrait top bar was a sideways-scrolling row: later buttons were off-screen with no hint. Now the buttons that fit stay, in the user's order, and the rest are labelled items in a More menu (always present: it holds the sleep timer) | `TopBarFitTest` 5/5 (local); device `f` pending | — | unit only | Before/after screenshots for the owner come from `f` (`pl16_*`) |
| PL-17 Video sleep timer | G3 proposal | 3ce721d `player/VideoSleepTimer.kt`, `SleepTimerSheet.kt`, `VideoPlayerModal.kt`, `LocalPlayerDeviceTest f` | Missing feature (music had one). Process-wide timer over the music `SleepTimer`: countdown with 20 s fade, or "end of this video"; spans episode changes; volume given back on detach | Device `f` pending (1 min timer must pause and restore volume) | — | unit only | |
| PL-18 One Back leaves the player on phones | G3 proposal | c832bc2 `ui/screens/video/PlayerBackRule.kt`, `VideoPlayerModal.kt`, `PlayerBackRuleTest`, `LocalPlayerDeviceTest g` | Touch Back in full screen only left full screen; a second Back was needed. Now a touch Back leaves (floating window / PiP / close) in one press, except full screen on a watch page, which returns to the page (YouTube behaviour). A remote keeps the steps: hide controls → leave full screen → leave | `PlayerBackRuleTest` 8/8 (local); device `g` pending | — | unit only | |
| PL-19 SRT subtitles can be cast (converted to WebVTT) | G3 proposal | 23390e6 `cast/SrtToVtt.kt`, `cast/CastSubtitleServer.kt`, `CastSupport.prepareSubtitles`, `OnlinePlayerUi.castCurrent`, `SrtToVttTest`, `CastSubtitleServerTest`, `LocalPlayerDeviceTest i` | Receivers show WebVTT/TTML only, so PL-12 dropped SRT (most subtitle sites give SRT). Now the phone fetches the SRT (IO), converts it, and serves it on its private LAN IPv4 under a random 128-bit path with CORS; at most 8 tracks × 2 MB; server stops when the cast session ends | `SrtToVttTest` 3/3, `CastSubtitleServerTest` 4/4 (Robolectric, real loopback sockets); device `i` pending | — | unit only | Shown on a real TV: script P/T (needs a Chromecast) |

### R16: Group 4 (Yumix)

Report: `docs/groups/GROUP-4-YUMIX.md`. Device runs are on api33-lowram and api36 (CI emulators).

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| YX-1 Offline refresh keeps the feed | G4 Y1 | 2a79dbe, 6267099 (`OnlineHomeScreen.kt`, `YumixFeed.homeAfterFetch`, `YumixFeed.broughtNew`) | An empty answer (every row failed) was taken as success and wiped the sections; offline the drama shelf cache "answered" and reset More to explore. Old rows kept; only new titles count as an answer; offline message | `YumixRefreshTest` 5; device `YumixDeviceTest#offline…`: 43723079397 api33 4/4 kept + message; G4 milestone full suite 43733844682 Yumix 2/2 on both | api33-lowram, api36 | verified on device | Captive Wi-Fi: OMT-5 |
| YX-2 More to explore starts with an empty extension | G4 Y1 | 2a79dbe (`YumixFeed.sectionsDone`) | `groups.size >= ready.size` never true when one ready extension had no rows | `YumixRefreshTest` | — | unit only | |
| YX-3 A cancelled section load writes nothing | G4 Y1 | 2a79dbe (`loadOne` `isActive`, `Deferred<Boolean>`) | The cancelled fetch's catch put "Couldn't load" over the newer one | code + unit suite | — | unit only | Race not forced on device |
| YX-4 Stale explore page after refresh | G4 Y1 | 2a79dbe (`YumixMemory.generation`) | A page fetched before the reset was appended to the new list | `YumixRefreshTest` | — | unit only | |
| YX-5 See all keeps paging | G4 Y6 | 2a79dbe (`OnlineRowScreen.kt`) | Fetch in a keyed `LaunchedEffect` was cancelled by a second request → false "Couldn't load more". One job in the screen scope | `YumixDeviceTest#seeAll…`: 43716649966 (26→468, 78→694), 43723079397 (76→644, 52→973), 0 errors | api33-lowram, api36 | verified on device | |
| YX-6 Feed calls don't wait behind probes | G4 Y1/Y4 | 2a79dbe (`OnShortCatalog.kt` `fits()`) | Background probes counted against FEED/INTERACTIVE slots: 20 s waits | `GateLaneTest#feedLoadsDontWaitForARunningProbe` | — | unit only | 20 s waits still logged on device while several feed calls run at once (feed lane full, not probes) |
| YX-7 Turned-off / 18+ extension titles leave explore | G4 Y1 | 678903d (`YumixFeed.goneFrom`, `syncExplore`) | Titles stayed until refresh | `YumixGoneTest` | — | unit only | Brief engine gap may drop and re-add |
| YX-8 Detail page keeps content on failed refresh | G4 Y7 | 678903d (`OnlineDetailScreen.kt`) | Failed refresh replaced the page; cancelled load flashed an error | code + unit suite | — | unit only | |
| YX-9 Internet Archive the user added is kept | G4 Y1, G2 | 52a8c42 (`ExtensionManager.legacyDefaultMigrationDue`, `LegacyDefaultMigrationTest`) | The 1.8.0 one-time clean-up ran at first sync and removed an Internet Archive just added by the user. Decided from saved state at load | `LegacyDefaultMigrationTest` 3; device: 43723079397 no unload from `uninstall` while Yumix open (was 43721853978: "Unloaded … from uninstall") | api33-lowram, api36 | verified on device | Real upgrade from a 1.8.0 install: G12 |
| YX-10 Partial refresh keeps rows in place | G4 Y1 | 6267099 (`YumixFeed.homeAfterFetch`) | Missing old rows moved below the fresh ones | `YumixRefreshTest#refreshWithFewerRowsKeepsEveryRowInItsPlace` | — | unit only | |
| YX-T Yumix device tests | G4 | 9e10315 … bc68326 (`YumixDeviceTest`, `HomeNavigationDeviceTest`, `DramaPlaybackDeviceTest`, `scripts/device-check.sh`) | New class in the default suite; counts only Yumix-list cards; checks held titles, not composed cards; scrolls rows into view; leaves out sections still loading at hold time (bc68326); h2 accepts the online detail page for an online Resume (by design) | 43732060154 api36 2/2; milestone 43733844682 Yumix 2/2 and HomeNav 11/11 on both | api33-lowram, api36 | verified on device | |
| YX-11 Studio whose video host is gone is hidden | G4 Y4 | 3c29282 (`DramaHealth.kt` `StreamHostMissing`), bc68326 (`OnShortCatalog.kt` slow-answer log) | DramaBox moved its CDN to `hwztakavideoto.dramaboxdb.com`; the old host gave NXDOMAIN and every episode failed. An UnknownHost while the phone is online now marks the studio down for 5 min, so the mirror search runs | `DramaHealthTest` +2; device Drama 43732060154 api33 pass, 43733844682 api36 pass | api33-lowram, api36 | verified on device | onshort.net itself can time out (43733844682 api33): upstream, not fixed by this |

### R16: Group 5 (Library) and Group 6 (Music)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| LB-1 Personal lists survive a crash while saving | G5 L3/L4/L5/L8 | 53420b2 (`data/SafeJsonFile.kt`, `OnlineBookmarks`, `OnlineHistory`, `OnlineDownloads`, `notify/Inbox`, `MangaLibrary`, `backup/Backup.kt`) | Unreadable file → empty list marked loaded → next save overwrote the only copy; write fallback could leave only `.tmp`. Now read falls back to `.tmp`, keeps a `.bad` copy, write is temp + fsync + rename | `SafeJsonFileTest` 7 | — | unit only | Real power loss: not reproducible on CI |
| LB-2 Manga library saves off the main thread | G5 L8 | 53420b2 (`MangaLibrary.kt`) | `writeText` on every page turn on main. Coalesced background writer, flushed before backup | `SafeJsonFileTest`, `BackupV2Test` 56/56 | — | unit only | |
| LB-3 One bad playlist no longer resets all playlists | G5 L6 | 53420b2 (`MediaRepository.loadPlaylists`) | Whole-array `runCatching` fell back to defaults; next save made it permanent. Per-entry skip | `LibraryRound2Test#oneBadPlaylistKeepsTheOthers` | — | unit only | |
| LB-4 No false "new episodes" after a short answer | G5 L2 | 53420b2 (`OnlineBookmarks.seen/checked`, `NewEpisodesWorker`) | A short list lowered the saved count; the next full list looked new. Count never drops; 0-episode answers skipped | `OnlineExtrasTest#aShortAnswerNeverLooksLikeNewEpisodes` | — | unit only | |
| LB-5 YouTube history keeps good entries | G5 L4 | 1c6b946 (`YtHistory.decode`) | One bad entry emptied the list; next play saved it | `RevampLogicTest#ytHistoryRoundTrips` | — | unit only | |
| LB-6 Listening time counts with no screen open | G5 L7 | 1c6b946 (`PlaybackService`, `ListeningTime.playerIsPlaying`, `MainViewModel`) | Clock ran on the ViewModel scope; Back-exit stopped it while music played. Shared-player listener | `LibraryRound2Test#clockRunsOnlyWhilePlaying`; device `LibraryDeviceTest#a` (run 43738288853) | api33-lowram, api36 | verified on device (43738288853: listening time +36.8 s / +36.6 s, LIB_A_RESULT PASS on both) | |
| LB-7 Library tabs read files off main; sort orders remembered; playlist kept on rotation | G5 L1/L5/L10 | 1c6b946, 535d763 (`VideoLibraryScreen`, `LibraryDesign`, `LibraryScreen`, `MainViewModel`, `PrefsWarmup`) | Main-thread loads; sort in memory only; filter + sort on main per keystroke | compile + unit suite | — | unit only | |
| MU-1 Scrobbling works after Back-exit | G6 M7 | ee9e246 (`player/BackgroundPlaybackWatch.kt`, `PlaybackService`, `MusicPlayerManager`) | Tracker watched the screen-scoped manager, released on Back-exit. Now fed by the shared player | `ScrobbleTrackerTest`; device `LibraryDeviceTest#a` part b (43738288853) | api33-lowram, api36 | verified on device (43738288853: BackgroundPlaybackWatch sees playing=true after Back-exit on both); real scrobble not sent | Real Last.fm account: OMT-10 |
| MU-2 Scrobble queue keeps good entries | G6 M7 | ee9e246 (`Scrobbler.decodeQueue`) | One bad entry emptied the queue of unsent listens | `ScrobbleQueueTest` | — | unit only | |
| MU-3 LRCLIB no-lyrics marks expire | G6 M4 | d183e65 (`LrcLib.pruneMisses`) | One prefs key per song without lyrics, forever | `LrcLibMissesTest` | — | unit only | |
| MU-4 Playing queue survives the screen | G6 M5 | a119186 (`MusicPlayerManager.processQueue`, `restoreExistingState`, `trackFrom`) | Queue lived on the screen-scoped manager: reopening while music played showed no now-playing bar and "Play next" replaced the queue | `QueueSurvivesScreenTest` | — | unit only | |

### R16: Group 7 (Manga and Live TV)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| LT-1 Saved playlists survive a bad file | G7 T3 | 4cee17d (`PlaylistStore`) | Index read and write went straight over the file; a kill mid-write or one bad byte lost every saved playlist. Now SafeJsonFile (temp + rename, .bad kept) | `StreamPlaylistTest#aBrokenIndexIsNotOverwrittenWithDefaults` | — | unit only | |
| LT-2 Playlist page shows a store error with Retry | G7 T3 | 4cee17d (`PlaylistDetailScreen`) | A failed store load left an empty page with no message | compile + code read | — | unit only (compile) | UI look: none on device |
| LT-3 Large playlists parsed line by line | G7 T4 | c9a4929 `M3uParser.parseLines`, `PlaylistStore.channels/refresh` | A saved playlist (cap 40 MB) was read into one String before parsing: heap spike on a 192 MB-heap phone | `StreamPlaylistTest` (both paths give the same channels) | — | unit only | Memory gain not measured on device |
| MG-1 Manga detail/reader: Retry no longer shows a false error; library read off main | G7 T1/T2 | 4cee17d, c9a4929 (`MangaScreens`) | runCatching caught the cancelled old load and showed it as an error; MangaLibrary file read on main at first open | compile + code read | — | unit only (compile) | No manga source on CI |

### R16: Groups 8–10 (Downloads, Settings, Errors), started before Group 12

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| DL-2 A system stop no longer leaves a download paused | G8 D4 | d9cdd73 (`DownloadWorker.kt`) | A stop by the system (network lost, quota) set PAUSED, so the WorkManager rerun quit and the item was stuck until tapped. Only a running item goes back to QUEUED; user pause/delete keep their state | `DownloadResumeTest`; device `DownloadResumeDeviceTest` 43742375420 (DL_A, DL_B PASS) | api33-lowram, api36 | verified on device | Real network drop on a phone: owner script P |
| DL-3 HLS resume cuts back to the saved point | G8 D4 | d9cdd73 (`HlsDownloader.kt`, `ForceGrab.kt`) | The side file was written every 5 segments, so after a pause up to 4 segments were appended twice (repeated video). Side file stores `segments:bytes`; the file is cut back to the recorded length | `DownloadResumeTest`; device 43742375420 | api33-lowram, api36 | verified on device | |
| DL-4 Range answers checked on resume | G8 D4 | d9cdd73 (`DownloadWorker.kt`) | A 416 or a range at another offset was trusted. Append only when the range starts at the `.part` end; 416 is complete only when the server size matches | `DownloadResumeTest` | — | unit only | |
| DL-5 A late progress tick keeps the user's pause | G8 D4 | 22f7c30 (`OnlineDownloads.progress`) | Found by 43741091128 (both emulators failed): the last segment's tick turned PAUSED back to RUNNING | `DownloadResumeTest`; device 43742375420 pass on both | api33-lowram, api36 | verified on device | |
| BK-1 Backup round trip, secrets never exported | G9 S25 | fda0870 (`BackupDeviceTest`) | Test only: settings survive export → wipe → import; tokens are not in the file | Device 43742375420 `BK_A entries=2 leaked=false applied=true` on both | api33-lowram, api36 | verified on device | Test only, no app change |
| ER-1 Playlist market errors are readable | G10 A1 | eb91756 (`PlaylistMarket.friendlyError`, `PlaylistMarketTest`) | Raw SSL text or URLs were shown. Market cases kept, the rest through `OnlineErrors.friendly` (cause chain) | `PlaylistMarketTest` | — | unit only | |
| NT-1 Notification alarm work off the main thread | G10, G12 X5 | eb91756 (`NotifyPacer.kt` `NotifyAlarmReceiver`) | StrictMode (43697447466) named prefs, WorkManager and the notification post in `onReceive` on main. `goAsync()` + a short `yu-notify-alarm` thread | Compile + unit suite | — | unit only | Next stress StrictMode list must not name `NotifyPacer` |
| ER-2 YouTube play and update failures readable | G10 A1 | f81357f (`YouTubePlayback.kt`, `UpdateManager.kt`) | Raw exception text in the toast and update dialog. Shared mapper | Compile | — | unit only (compile) | |

### R15/R16: player

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| PL-1 One player per video (PlaybackSession) | R15-P2 → G3 P1/P2, X1 | 5b05512 (ADR-006, `PlaybackSession`), `r15/PlaybackSessionTest.kt` | Several ExoPlayers alive at once. One session, reused, released after a grace period | `PlaybackSessionTest` 5/5; device `created=1 reused=2 resumed=1` (43641330758) | both | verified on device | |
| PL-2 Engine quality levels for single-rendition streams | R15-P2, O2 → G3 P5 | 4b9c665 (v1.8.1 in 073041d), 5a1c9ec (`VideoPlayerModal.kt`, `StreamWatchPanel.kt`: switch stall) | Switching stalled on the engine path. Fixed | Device `a_engineLevelsSwitchWithoutTimeout` | both | verified on device | |
| PL-3 Quality list equals the downloader's list | R15-P2, O2, S2 → G3 P5 | 4b9c665, c37efbf, 474f7df (`r15/QualityParityTest.kt`), 2f7e9b2 (`QualityListDeviceTest`) | Player and downloader built lists separately. One source of truth | Unit 3/3 + 4/4; device QUALITY_RESULT PASS (43668984393) | both (HLS) | verified on device (HLS); YouTube unverified | YouTube needs a home network; manual script Y in `MANUAL-TESTS.md` |
| PL-4 YouTube quality switch uses the downloader's data, codec-aware | R15-P2 → G3 P3/P5 | d13e6b8, 8259ff3 (`YouTubeStreams.videoRequest`, `pickVideo`) | Switch used a different stream request. Same request, IO, codec check | `YouTubePickVideoTest` 5/5 | — | unit only | Device test skips on CI (Google bot check on data-centre IPs) |
| PL-5 Dead manual pick rotates again | R15-P2 → G3 P9 | d99d623 (`OnlineSession.kt`, `OnlinePlaybackDeviceTest`) | A waiting failover fired after a manual pick; a failed link was never retried. Manual pick wins and gets a fresh chance | `RecoveryStateTest`; device 43652020450, 43657729675 | both | verified on device | |
| PL-6 00:00 until the length is known | R15-P2 → G3 P12 | 3e2114e (claimed, **broken**), 2be78be (`VideoPlayerControls.kt`, `r15/DurationLabelTest.kt`) | 3e2114e still showed a pulsing pill. 2be78be shows `00:00` | `DurationLabelTest` 3/3; device labels `[00:00, 1:31:44]` | both | verified on device (3e2114e claim was `broken again`, replaced by 2be78be) | |
| PL-7 Drama banner plays, dramas in the feed | R15-P2, O1 → G3 P4, G4 Y4 | e8c3f3c, ec1dbe4, 5ad3e60, d3f5b3b, bda5a75 (v1.8.1, `sources/drama/*`), 474f7df (`r15/DramaResilienceTest.kt`), 61bf92b, 0cb815a (`DramaPlaybackDeviceTest`) | CloudStream `fixUrl` rewrote drama ids; dead studios shown. Ids kept, health check hides dead studios, cross-studio fallback | `DramaResilienceTest` 16/16, `DramaHealthTest` 4/4; device 43657727593 (4 rows x 18) and 43676961108 (5/5 per device) | both | verified on device | Depends on third-party drama hosts |
| PL-8 Drama start time | S2 → G3 P4 | b231133, 8a16df5, 0fc4acc, c093f81 (`OnShortCatalog.kt` GateLane priority lanes, `r15/GateLaneTest.kt`) | Taps queued behind health probes in one FIFO gate. Interactive / feed / background lanes; a tap promotes a shared request | Before 34,044 ms api36 / 9,650 ms api33 (43661715830). After, 5 runs (43676961108): median 3.5 s / 3.8 s, worst 4.7 s / 7.1 s | both | verified on device | Network-bound; first cold run on low RAM is about 7 s |
| PL-9 View downloads → floating player; new video → full screen | R15-P4 → G8 D7, G3 P10 | 8259ff3 (`PlayerSurfaceRules.kt`, `MTPlayerApp.kt`, `DownloadPrompt.kt`), 0afd322 (`SurfaceModeDeviceTest`) | Downloads page opened over a full-screen player | Unit 3/3; device OK in 43657729675 | both | verified on device | |
| PL-10 Every DASH rendition has picture and sound; separate audio merged | R15-P2 → G3 P5/P6 | b0e596d, 5a1c9ec (`PlayerSwitchDeviceTest`) | | Device `b_`, `d_`, `e_` tests, 43657729675 | both | verified on device | |

### R15/R16: feeds, downloads, extensions, crash logs

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| FEED-1 Live feeds append-only, never stale | R15-P3, S2 → G1 H1, G4 Y1, G6 M3, G7 T3, P3 | 3e2114e, 474f7df (feed logic); test work da1a3a9, 205a4c8, e1410e7, c1c555d, 15d8e33, 374b3c1, 5016dbc, db40142, 10bbd6e (`FeedSlotKey`, `lostTrack`, motion-event swipe) | Feeds reloaded and replaced rows. Append-only store, generation guard, failed page keeps the cursor | `LivePagedTest` 6/6, `LiveFeedTest` 6/6. Device 43683520404 (10bbd6e) repeat 3: api33 3/3, api36 2/3. The api36 miss is an ANR ("No response to onStartJob") during an 8.2 s main stall 1 min after boot, at 92% CPU pressure: environment, not a feed bug. Earlier Home failures (43680730905) were a test error (same title in two shelves) | api33-lowram, api36 | verified on device | Cold-boot ANR belongs to ST-7; 43687398312 (a5b8f60) pending |
| DL-1 Forced (IDM-style) downloads | R15-P5 → G8 D2 | 47ba0a0 (`online/download/ForceGrab.kt`, `OnlineDownloads.kt`, `r15/ForceGrabTest.kt`) | Sources that block downloads | Device `ForceDownloadDeviceTest` 43657729675 | both | verified on device | DRM (DASH key id) is refused by design |
| EX-1 Pinned extensions in "show videos from" | R15-P5, S2 → G2 E5, G4 Y5 | 47ba0a0 (`YumixFilter.kt`, `PinnedExtensions`), 2f7e9b2 (`PinnedExtensionsDeviceTest`) | | Unit 3/3 + 1/1; device PIN_RESULT PASS 43671202859 | both | verified on device | |
| CR-1 Crash log exported as a file | R15-P5, O7 → G12 X6 | 5a5bd3b (v1.8.1 crash store), 5a1c9ec (`CrashReportsCard.kt`, `shared_files.xml`, `CrashShareTest`), 0575711 (`diagnostics/CrashExport.kt`) | Downloads showed 0 bytes. Publish with IS_PENDING | `CrashLogTest` 3/3, `CrashShareTest` 2/2; device 43632313418 | both | verified on device | |

### R15: tests, CI, docs

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| CI-1 Device workflow concurrency per test filter | R15-P7 → G12 X10 | 0cb815a (`.github/workflows/device-check.yml`) | A later dispatch cancelled an earlier one | Parallel runs 43676915487 / 43676961108 | CI | verified | |
| CI-2 Repeat and shard inputs | S2 → X10 | 8971a7b (`device-check.yml`, `scripts/device-check.sh`) | Needed to prove flaky tests | Campaign 43671209244 (5 shards x 2 devices x 4 repeats) | CI | verified | |
| SEC-1 `secrets/` and service-account files ignored; history scan | handover Rule 2 → X7 | befbd13 (`.gitignore`) | | `git check-ignore`; scan of all branches and history for every shared credential fragment and common key patterns: 0 hits | — | verified | Shared keys must still be rotated before release (owner action) |
| DOC-1 System design, ADR-001..011 | R15-P7 → X1 | 8259ff3 (`docs/SYSTEM-DESIGN.md`) and later | | File | — | done (document) | |
| DOC-2 Manual scripts (YouTube, low-RAM phone, TV) | S2 → G3 P3, X9 | 2f7e9b2 (`docs/MANUAL-TESTS.md`) | | File | — | done (document); results `open` until the owner runs them | |
| DOC-3 Open-source comparison (cs-repos, hikari) | R15-P6 / Step 4 → — | `docs/proposals/OPEN-SOURCE-COMPARISON.md` | Proposal only | File | — | done as a draft; Step 4 needs a release-by-release re-check | |

### v1.8.1 owner orders (squash `073041d`, branch `r14-round2`)

The branch tip is identical to `073041d` in `app/src`, `server` and `.github` (checked with
`git diff`). Before R15 these were proven only with Robolectric.

| Item | Area | Commits | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| O1 Drama plays | O1 → P4 | e8c3f3c, ec1dbe4, 5ad3e60, d3f5b3b, bda5a75 | See PL-7 | PL-7 | both | verified on device (in R15) | |
| O2 Quality single source of truth | O2 → P5 | 4b9c665, c37efbf | See PL-3 | PL-3 | both | verified on device (HLS) | |
| O3 Neutral loader unless certain | O3 → G10 A3, Y1 | f9c9ef4 | Wrong skeleton shape flashed | `LoaderRound2Test` 1/1 | — | unit only | |
| O4 Yumix: no section headers, search always shown | O4 → Y1/Y2 | 9bdec1a | | `YumixShowcaseTest` 4/4 | — | unit only | |
| O5 History on open; real likes, playlists, play time | O5 → G5 L4/L6/L7/L9 | 898884c | Placeholder data | `LibraryRound2Test` 4/4 | — | unit only | |
| O6 HeatGuard | O6 → P19, X4 | eec2938 | Phones got hot | `HeatGuardTest` 5/5 | — | unit only | Needs a real phone |
| O7 Crash logs on the phone | O7 → X6 | 5a5bd3b | | See CR-1 | both | verified on device (export path) | Reading/deleting UI not device-tested |
| O8 Theme image picker | O8 → G9 S26 | 465688f, 18accbd, e23a126 | Bounds probe refused every picture since PR #27 | `BackdropImportTest` 3/3 | — | unit only | |
| O9 Notification reliability guide | O9 → X5, H13 | f2cfaa5, 36a556d | Notifications stopped on some makers | `ReliabilityGuideTest` 5/5, `BackgroundWorkTest` 3/3 | — | unit only | Needs Xiaomi/Samsung phones |

### Earlier rounds (merged into `r15-stability` through `main`)

Status is `unit only` unless an R15 device test covers the same path. Details per commit are in
`COMMIT-FILES.md` and `CHANGELOG.md`.

| Item | Area | Commits | Status | Notes |
|---|---|---|---|---|
| Black screen during source failover | R7 → P9/P14 | bcf3dc8 → 1ad118e (PR #8) | verified on device (covered by PL-5 rotation tests) | |
| Captcha/blocked loop, WebView crashes, tab pager | → A5, N2, H4 | fb718c7, 6dcde57 | unit only | Pager part superseded by ST-3 |
| Downloads fixes | → G8 D1/D4 | 562c6e3, 0e5cc1a | unit only | |
| Universal sources, v1.0.5 stability | → E10 | 352dc28, 743f1ca, 43bf18d | unit only | |
| Settings redesign, YouTube cookie, UnifiedPush | → G9, I10, X5 | 3018a36 | unit only | |
| Extension links, updater, scanner, FFmpeg audio, subtitles | → E2/E3, P6/P7 | ef54fff, 0d21c50 (AV heuristics, Injekt ProGuard) | unit only | VirusTotal reports in `docs/security/` |
| SubDL/SubSource, external downloaders, ad shield, CodeQL WebView hardening | → I5, D5, N2, X7 | 8021f45, e3d9bb0, bcf1eaf, e3d90b9 → f7182be | unit only | |
| Trakt secret-less + client-id chars | → I1 | e7f768b, dbe310d/904608c → 7587b90 | unit only | TRAKT_CLIENT_SECRET not provided |
| Simkl, Last.fm, LRCLIB, OpenSubtitles key | → I2, I7, I6, I5 | 5c973b9 → 7587b90 | unit only | |
| Cloud airing alerts (Cloudflare Worker, D1), back-off | → I9, X5 | 0628b6a, 6f8cc1e, 672aa00 → 7587b90 | unit only (server tests) | Server not exercised by device tests |
| FCM push via Worker | → I8 | ba065a4, 9a7a822, ca56afc | unit only | Dry run accepted by FCM (per ca56afc) |
| Markets, Mihon lib 1.6 | → E1, T1 | 3b7d1c7, af8349e | unit only | |
| Redesign R1–R11b (shell, Home, Yumix, Now Playing, dialogs, TV rail, captions) | → G1, G4, G10 | 72fa3da … f73c2eb → ff7cb19 (PR #27) | unit only (Robolectric screenshots) | |
| Pinch zoom, subtitle style, onboarding gate | → P8, P7, H9 | 236ffac | unit only | |
| Red-team fixes (tracker sign-in, Open with, backup limits) | → X7, S25 | 3f4b244 | unit only | |
| TV flavour, Android 7, one universal APK | → X9, X10 | f94611b, 1188650, ebc70b7, ba00c43 | unit only | No real TV run |
| Backup v2 | → S25 | 93074cb, 79f6d96, 429288f | unit only | |
| Sources redesign | → E11 | c87aa2d, 265c907 | unit only | |
| Security hardening | → X7 | ce6e640, 18cd213, b2ae9e6 | unit only | |
| Music codec | → M5 | 1b29918 | unit only | |
| Round 13 player (gather off main, rotate stalled sources, silent audio, quality selector) | → P2/P5/P6/P9 | a7a3bc2, 6e678d5, 24df44a, 60cffac, 8636445 → 366a5f6 (PR #31) | partly verified on device through PL-2/PL-5/PL-10 | |
| Repo move and rename | → X10 | 99278fa, dfd32ab | done | |

## Branches (Step 1B: every branch read)

| Branch | State | Proof |
|---|---|---|
| `r15-stability` | Active | 89 commits |
| `main` | Up to v1.8.1 | Ancestor of `r15-stability` |
| `fix/playback-flow-and-ux` | Merged (PR #8, 1ad118e) | No diff in `app/src` vs 1ad118e |
| `feat/subtitles-downloaders-adshield` | Merged (f7182be) | No diff |
| `feat/lyrics-scrobble-simkl`, `feat/cloud-alerts` | Merged (7587b90) | No diff |
| `fix/trakt-secretless` | Content merged in 7587b90 (PR #19 title: "Trakt without secret") | The only `tracking/` difference to 7587b90 is `Simkl.kt`, which #19 added |
| `r11-final` = `redesign-final` = `safety/r11b-2026-11-13` | Merged (PR #27, ff7cb19) | No diff |
| `wip/r10-safety`, `redesign/v6-polish`, `redesign/v7-dream` | Earlier states of PR #27 | `git merge-base --is-ancestor` of `r11-final`: yes |
| `r12-features` | Merged (d028db3) | No diff |
| `r13-player` | Merged (PR #31, 366a5f6) | No diff |
| `r13-mockups` | Merged into r14-round2 | Ancestor of `r14-round2`: yes |
| `r14-round2` | Merged (PR #32, 073041d) | No diff |
| `docs/imagedecoder-wording` | Merged (text in `docs/HANDOVER.md`) | `grep` finds the sentence |
| `archive/online-rooms` | **Not merged, on purpose** (watch-together rooms parked 2026-11-09) | 688226d message |
| `dependabot/gradle/…` | **Resolved** (Robolectric 4.18 instead of 4.17; others already in dc4e469) | a799e27; DEP-1 |
| `dependabot/npm_and_yarn/server/…` | **Resolved** (TypeScript 7.0.2, wrangler 4.160 taken) | bcc6178; DEP-1 |
| `market`, `extensions`, `streams`, `feed` | Data branches written by workflows | Not code |

## Changes to items marked done

| Date | Item | Why it changed |
|---|---|---|
| 2026-11-15 | ST-6 | 50e734d adds more start-up moves after the 40-run campaign named three new main-thread waits; the b0e596d fix is unchanged |
| 2026-11-15 | PL-8 | Numbers replaced by 5-run medians; the earlier single samples were the best runs |
| 2026-11-15 | FEED-1 | Test only (db40142): slot keys added to feed cards so the test can tell two copies of one title apart; no feed logic changed |
| 2026-11-15 | ST-6 | Moved to verified on device for b0e596d and 50e734d: full suites 43680668799 and 43683535994 pass; TvMode and PlayCounts gone from the stall list (43683528288) |
| 2026-11-15 | FEED-1 | Moved from unverified to verified on device: 43683520404 api33 3/3, api36 2/3 with the miss proven to be a cold-boot ANR, not a stale row |
| 2026-11-15 | FEED-1 precondition | Its limit "feed slot starvation goes to Group 4" is handled by YX-6 (unit only; see that row's limit) |
| 2026-11-15 | PL-15 | Test only (65e50e0): HeatGuard test `d` retries the battery-saver precondition; full suite 43714635144 api36 failed because the system kept `isPowerSaveMode=false`. 43717950021: d passes on both |
| 2026-11-15 | FEED-1 | Yumix part extended by YX-1, YX-10 (refresh never replaces or reorders rows); FeedPosition#yumix 43715245923 pass on both |
| 2026-11-15 | ST-7 | Stays open. New campaigns show no measurable drop in steady-state stalls; the claim of progress is withdrawn until the baseline profile campaign |
| 2026-11-15 | H6 | 4098177 failed on device 2/2 (43687458863, 43688381102); kept unverified until the cause was found |
| 2026-11-15 | H6 | Moved to verified on device after 89f7553 (43690918679 6/6). The earlier failures were the Compose test clock, then a real mid-swipe cancel that 89f7553 fixed |
| 2026-11-15 | ST-6 | a5b8f60 stays unit only for its own effect: stress 43687396407 20/20 PASS, steady app stalls api33 0.4/run, api36 0.1/run (was 0.5 and 0.3): small, within run-to-run noise |
| 2026-11-15 | ST-16 | unit only → verified on device: stress 43697447466 StrictMode shows only the NotifyPacer chain |
| 2026-11-15 | ST-7 | Stays open. Repeat campaign 43705867413: steady 0.4 / 0.4, so the small 18dda67 drop (0.2 / 0.2) was noise |
| 2026-11-15 | YX-1 | Verified on: api33-lowram → api33-lowram and api36 (milestone 43733844682 Yumix 2/2 on both); status unchanged |
| 2026-11-15 | YX-T | Test fixes 3c29282 and bc68326 added; evidence moved to the milestone 43733844682 |
| 2026-11-15 | LB-6 | unit only → verified on device: 43738288853 LibraryDeviceTest pass on api33-lowram and api36 |
| 2026-11-15 | MU-1 | unit only → verified on device (watch state only): 43738288853 part b pass on both; Last.fm send stays OMT-10 |
| 2026-11-15 | Y4 / YX-11 | api33-lowram Drama now passes too: 43738288853 DramaPlaybackDeviceTest pass on both (43733844682 api33 failure was onshort.net timeouts) |
| 2026-11-16 | ledger layout | Group 5–7 rows had been written inside the Group 4 table; moved to their own sections (no status changed). Rows DL-2..5, BK-1, ER-1, ER-2, NT-1 added for commits d9cdd73, fda0870, eb91756, f81357f, 22f7c30, which had no row |
| 2026-11-16 | PL-13 | Limit "cannot be forced on the emulators" withdrawn: the app op forces the refusal (fd1a2f5, test `h`); stays unverified until `h` passes |
| 2026-11-16 | PL-12 | Limit changed: SRT is converted (PL-19) instead of dropped |
| 2026-11-16 | ST-7 | Re-checked in code: dfe9234 moved the history write, not ExoPlayer.release(); row corrected. Two-step release added, unit only until a stall campaign measures it |
| 2026-11-16 | PL-15 | Run 43747147539 (f9 tests on both): new f, g, h, i pass on both; a and c failed on both because the test helper tapped the player centre (the Pause button) when a control was in the More menu. Helper fixed; rerun pending |
| 2026-11-16 | DEP-1 | Both Dependabot branches re-checked: every bump is equalled or exceeded on r15-stability; nothing to merge. Deleting the two branches is the owner's decision |
| 2026-11-16 | full suite 43744809503 (22f7c30) | api36: all 20 classes pass. api33-lowram: 19 pass, ExtensionsDeviceTest fails in E11 cleanup only (EXT-T1). E11 itself passed on both |
