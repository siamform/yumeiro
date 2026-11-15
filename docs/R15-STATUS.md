# R15 status: every item in the master prompt, with its evidence

## Resume here (updated 2026-11-15 22:20 UTC, fourth handover; orders: master operating document)

- **Owner orders in force:** master operating document (agent notes `notes/master-orders-3.md`):
  L0 (done) → **L1 Group 12 (current)** → L2 Groups 5–10 → L3 Group 11 → L4 re-audit Groups 1–4 with
  the full Part 3 method → L5 Step 4, Step 5. Heavy work on CI, ≤ 2 device workflows, no PR/merge.
- **Outage:** sandbox stopped answering 2026-11-15 ~19:05–19:45 UTC (local `lintTvDebug`). Lint runs on
  CI only since then.
- **Since 21:43 UTC:** X8-2 and X2-3 done on both phones (43767144680). Root cause of the lost Back
  presses in device tests: keys went to the previous test's window (X9-2, 1a577e5/eb1775c). **New
  product bug X13-1 (high):** online sources dead on the TV app on Android 7 (Jackson ≥ 2.15 needs SDK
  26); TV now on Jackson 2.13.5 (2226756), guard test in CI (b0be1a2, c01bd5c), unit fail-before shown.
- **Current step:** Level 1, Group 12: TV and minSdk device runs; then X2 startup benchmark.
- **Running:** TV 43768054941 (2226756), minSdk phone 43768670627 (eb1775c), CI 43768099126 (c01bd5c).
- **Next three actions:** (1) read TV 43768054941: online rows must load on api24 (no
  `NoClassDefFoundError`), X9-3 tags, X9-4 h5 evidence; fix what remains; (2) read minSdk 43768670627
  and CI 43768099126 (X15-1, X13-1 rows); (3) macrobenchmark startup → profile → commit
  `baseline-prof.txt` → startup again (X2-1).

Branch `r15-stability`. "Device" means an instrumented test on both CI emulators: a low-RAM
API 33 Pixel 3a (2 GB RAM, 192 MB heap, low-end path) and an API 36 Pixel 6. The run IDs are
in `docs/evidence/round3/device-runs.md`. "Unit" means a JVM or Robolectric test only. Items
marked **not device-verified** are not claimed as fixed on a phone.

Earlier "done" reports (STATUS-v1.8.1) were Robolectric-only. Each item below was re-checked.

## Phase 1: OOM and ANR

| Item | Status | Evidence |
|---|---|---|
| No regex compile on main | Fixed | `RegexGuardTest` (none inside functions; all 91 holder classes warmed on `yu-startup`). Device: `StabilityStressDeviceTest` samples main 42,000–44,500 times per run and fails on app-origin `Pattern.compile`. Clean since run 43645273428. The only left-over is the library cost of `androidx.navigation` `NavDeepLink` route patterns |
| No expensive work in pager prefetch | Fixed | `Kana.forTitle` has no regex and is LRU-cached and remembered; item tags are computed once. Device: pager and fling stress |
| No OOM under stress on low-RAM Android 13 | Fixed | Device: api33-lowram (192 MB heap) passes stress, playback, switching and downloads with no OutOfMemory or FATAL in logcat |
| No ANR on focus or pager | Fixed | Device: max main stall 461–1,542 ms across runs; the failure threshold is 4 s. The endless recomposition after focus moves came from the tab pager taking a focused card's bring-into-view as nested scroll (`docs/STABILITY-R15.md`). Fixed in MTPlayerApp. The stress test now fails on any "Compose never idle" and has passed with 0 spins since 43653623319. `dpadFocusSettles` and `swipeThenDpadSettles`: 0 frames per 2 s after D-pad moves |

## Phase 2: player

| Item | Status | Evidence |
|---|---|---|
| Quality switch uses the downloader's working stream data | Fixed in code | `YouTubeStreams.videoRequest` on IO, codec-aware `pickVideo` (`YouTubePickVideoTest`). Device `c_youTubeQualityListOpensEveryHeight` **skips on CI**: Google answers "Sign in to confirm you're not a bot" for data-centre IPs. **Not device-verified for YouTube** |
| Same full quality list as the downloader (Video, YouTube, Yumix) | Fixed in code | `QualityParityTest` (unit). Device: DASH rendition ladder switching (`b_everyDashRenditionHasPictureAndSound`) |
| Engine-generated levels for single-source streams | Fixed | Device `a_engineLevelsSwitchWithoutTimeout` |
| No audio with multiple sources | Fixed | Device `d_everyAudioRenditionIsAudible` (AAC, AC3), `e_separateAudioIsMerged`, `StarvationSafeLoadControl` |
| A manual pick of a dead source rotates again | Fixed | `RecoveryStateTest` (unit). Device `d_manualPickOfADeadSourceRotatesAgain`, run 43652020450, both devices: the dead pick was tried, then playback rotated to the working MP4, and the dead link was marked failed |
| Drama banner plays; dramas appear in the feed | Fixed in code | `DramaResilienceTest` (16, unit). Live network check b21 24/24 (`docs/evidence/round3/drama-live-b21.txt`); the b14 run was invalid (onshort.net outage). Device: `DramaPlaybackDeviceTest` PASS on API 33 and 36 (runs 43657727593, 43657729675): feed rows DramaBox/ReelShort/NetShort/DramaWave = 18 each; "Daddy, Save Me" reached first frame at 1080x1920. The first device attempt (43656137755) failed only because the test did not release the first-run gate (`OnboardingGate`), which holds every source until the welcome guide is skipped |
| 00:00 instead of "--:--" | Fixed (this round; before, the row showed a pulsing pill or nothing) | `DurationLabelTest` (Robolectric). Device `c_uiFlowPlays`, run 43652020450: labels while opening were `[00:00, 1:31:44]` on both devices |

## Phase 3: live scroll never replaced by stale data

| Item | Status | Evidence |
|---|---|---|
| Home, YouTube, online music, Yumix and Live TV feeds | Fixed in code | `LivePagedTest` (append-only, generation guard, a failed page keeps the cursor). Device: fling stress on each feed with no stall or recovery. Yumix now has device assertions (R16 G4): `FeedPositionDeviceTest#yumix` and `YumixDeviceTest` (offline refresh keeps every held title; See all keeps paging), milestone 43733844682 pass on both. Other feeds: the guard is the store design in ADR-008, no device assertion |

## Phase 4: downloads and the floating player

| Item | Status | Evidence |
|---|---|---|
| "View downloads" moves the player to PiP and opens Downloads; a new video expands it | Fixed | Device `SurfaceModeDeviceTest`, `PlayerSurfaceRulesTest` |

## Phase 5: extensions, forced downloads, crash logs

| Item | Status | Evidence |
|---|---|---|
| Pin favourite extensions in "show videos from" | Fixed | `PinnedExtensionsTest` (unit) |
| Forced downloading (IDM-style) for sources that block downloads | Fixed | Device `ForceDownloadDeviceTest`: DASH into one MP4 (3,944,838 bytes, video/avc and audio/mp4a); segmented DASH (36,632,975 bytes, 634 s) |
| Crash log exported as a file automatically | Fixed | Device `crashReportIsExportedAsAFileRightAway`: 192,627 bytes (api33) and 192,638 bytes (api36) |

## Phase 6: proposal

`docs/proposals/OPEN-SOURCE-COMPARISON.md` (cs-repos and hikari). Nothing implemented, as asked.

## Phase 7: audit, plan and design

- System design with diagrams: `docs/SYSTEM-DESIGN.md`. Architecture, threading, memory, feeds,
  the playback state machine, failure modes and ADR-001 to ADR-011.
- Root causes found on device: `docs/STABILITY-R15.md`.
- Roadmap: `docs/ROADMAP.md` §R15.
- Skills used: ui-ux-pro-max (carousel rule `auto-rotation-controls` and the time-label states),
  kotlin-agent-skills, rea (reverse-engineering the spin and the crash-export size), and
  architecture-designer from jeffallan/claude-skills for the system design.

## R16 updates (2026-11-15)

- Done ledger: `docs/DONE-LEDGER.md` (every commit on every branch, re-checked claims, status per item).
- Drama start, play to first frame, 5 runs per device (run 43676961108, db40142, 10/10 pass):
  median 3.8 s api33-lowram (2.8–7.1 s) and 3.5 s api36 (3.2–4.7 s). Before (43661715830):
  9.7 s and 34.0 s. Cause: taps waited behind health probes in one FIFO gate; fixed with lanes.
- Quality list on device: `QUALITY_RESULT PASS heights=[1080,720,480,288,184]` (43668984393, both).
- Pinned extensions on device: `PIN_RESULT PASS`, order kept after recreate (43671202859, both).
- Main-thread pauses: **not yet a small fraction (ST-7 open).** 40-run campaign 43671209244: max
  per run 395–1,391 ms. Later campaigns after 50e734d, dfe9234, d6ff111, 9bbd223, a5b8f60, 18dda67:
  steady-state app stalls ≥700 ms per run 0.2–0.5 (api33-lowram) and 0.1–0.4 (api36); the latest
  repeat 43705867413 (97bb255, 20/20 pass) gives 0.4 / 0.4 steady, 0.8 / 0.3 cold. No measurable
  drop; next is a baseline profile.
- Feed position (Home, Yumix): fixed and verified on device (FEED-1): 43683520404 and 43687398312;
  the earlier failures were a test error (duplicate titles).
- Group audits (Step 3): `docs/groups/GROUP-1-HOME-NAV.md`, `GROUP-2-EXTENSIONS.md`, `GROUP-3-PLAYER.md`, `GROUP-4-YUMIX.md`. App bugs
  fixed and verified on device in R16 include stepped Back after a swipe (89f7553), TV rail focus
  (a53effb), Back in extension selection (bfde23d), readable network errors (9be5afb), starter
  extension retry (97bb255), Cast start (b5d2d5a), PiP refusal message (5bfa07c). Device runs are
  listed in `docs/evidence/round3/device-runs.md`.

- R16 group status: G1–G4 complete (agent work; owner tests pending). G5 Library and G6 Music:
  fixes in, `LibraryDeviceTest` 43738288853 pass on both emulators; full suite at next milestone.
  G7 in progress.

## Still open (not claimed)

- YouTube playback and quality switching on a device on a home network (CI IPs are bot-checked).
- A real Xiaomi with Android 13 and a TV device; the emulators cover API 33 low-RAM and API 36.
- Security: rotate the Firebase service-account key and the Cloudflare API token, since both were
  shared in chat.
