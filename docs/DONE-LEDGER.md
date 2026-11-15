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
| ST-7 Main-thread pauses "a small fraction" | S2 → G12 X3 | 50e734d, dfe9234, d6ff111, 9bbd223, a5b8f60 (all partial) | Named app stalls moved off main: player release, `pageExplore`, `videoSourceOptions` memo (dfe9234), audio effects on `yu-audio-fx`, browser UA, YtHistory (d6ff111), catch-up on `yu-catch-up` (9bbd223), prefs warm-up (a5b8f60) | Steady-state app stalls ≥700 ms per run: api33 0.25 (43671209244) → 0.4 (43680725238) → 0.5 (43683528288); api36 0.1 → 0.1 → 0.3. No improvement can be claimed. The large stalls cluster in the first minute after boot, mostly framework Compose measure under boot load | api33-lowram, api36 | **open** | Next: baseline profile (OPEN-SOURCE-COMPARISON P1), then a new campaign; stress 43687396407 on a5b8f60 pending |
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

### R16: Group 1 (Home and navigation)

| Item | Area | Commits and files | Cause and fix | Evidence | Verified on | Status | Limits |
|---|---|---|---|---|---|---|---|
| H6 Predictive back on Home (double back exits, app stays warm) | G1 H6 | 4098177 (manifest `enableOnBackInvokedCallback`, `MTPlayerApp.kt` exitArmed, `TabPagerProbe`, `BackNavigationDeviceTest`), e28df74, ac6e412 (log), 3479bf8 (test) | Android 16 turns predictive back on for target 36. First back arms exit for 2 s and turns our handler off, so the second back goes to the system (animation, no finish()) | 43687458863, 43688381102 failed: the handler armed twice 0.8 s apart because the test slept without syncing the Compose test clock, so `enabled=false` was never composed (test error). 3479bf8 advances one frame and asserts no callback stays enabled; run 43689402737 pending | — | unverified | Real-hardware gesture still needs script P |
| H1–H4 Tabs, swipe, back to Home | G1 H1–H4 | 45abd1b, ebfb79d, 6df7c1f (`HomeNavigationDeviceTest`) | Device test for tabs and back | 43685183725, 43686104737: 5/5 on both | api33-lowram, api36 | verified on device | |
| H7 VIEW intent while running (singleTop) | G1 H7 | 6df7c1f | onNewIntent delivers to the running activity; back minimises the player to the floating window | 43686104737 log: "intent has been delivered to currently running top-most instance" | both | verified on device | |
| H8 Recreate keeps tab, H12 Continue widget, H13 Inbox | G1 H8/H12/H13 | 45abd1b | | 43685183725, 43686104737 | both | verified on device | Widget placement on a launcher is manual (script P) |
| H10 Top-bar search suggestions off main | G1 H10 | 77fc8ca (`MTPlayerApp.kt` produceState, 120 ms debounce on Default), 2c109ac (test) | Ranking ran in composition. Moved off main | 43688049790: 3/4 (api36 run 2 lost `adb input text` keys; test error, 2c109ac types with Compose); 43689481257 pending | api33-lowram | unverified on api36 | |
| Y6/H1 Carousels stop when the app is not started | G1 H1, G4 Y6 | f021791 (`LifecycleState.kt`, `HomeSections.kt`, `OnlineHomeHero.kt`) | Timers kept running in background | Unit suite; device covered by HomeNavigation runs (no stall) | both | verified on device (indirect) | No direct frame count yet |

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
| 2026-11-15 | ST-7 | Stays open. New campaigns show no measurable drop in steady-state stalls; the claim of progress is withdrawn until the baseline profile campaign |
| 2026-11-15 | H6 | My own change 4098177 was reported to work in local reasoning but fails on device 2/2; failure traced to the test clock. Stays unverified until 43689402737 passes |
