# R15 status: every item in the master prompt, with its evidence

## Resume here (updated 2026-11-16 16:50 UTC, sixth handover; orders: master handover 6)

- **Orders in force:** the sixth master handover. Order: build green → G10/G9 device batch → close G9, G10 →
  Group 12 → G5–G11 → re-audit G1–4 → final sweep → gated feature phase. No PR/merge/release without the owner.
- **Sandbox:** no JDK/Android SDK; every compile and unit test goes to CI. Secrets live only in the git-ignored
  `.env` and `secrets/` of the agent's checkout.
- **State found (re-verified):** head was 3ff6bd3, not 27f1c2b. Device 43884803041 (fe643e2) failed in Build APKs
  on both phones (not only api36): LowStorage/NetworkState tests used the OkHttp 4 MockWebServer API → fixed
  7929bf9 (ER-16). CI never compiled androidTest → 677cefa adds the compile step. CI 43884797613: 1086 tests /
  0 failures, lint 0 errors, DL-11a requests=1 / 1.000×, ER-15 compiles. Upgrade 43881788009: data 0 lost, but
  three harness errors (empty logcat, played detection, impossible "listed" check) → fa3bb3b; X10-5 open.
  BackgroundSettingsEffectDeviceTest (3ff6bd3) reviewed: every symbol it calls exists; compile owed.
- **New owner bug:** BUG-5 Yumeiro TV UI (TV-1…TV-5, `docs/proposals/FEATURE-BUG-5-tv-ui.md`), open, not reproduced
  yet; it is worked in G12 X9 after the G9/G10 batch, starting with a TV emulator reproduction.
- **Current group:** G10 Errors + G9 verification. Step: CI 43894661940 (523af6f) compiling the fixes and new tests.
- **Batch 43891331959 read** (docs/evidence/round3/device-runs.md): Dialogs, BackdropImport (SE-4 proven),
  BackgroundSettingsEffect a–e PASS both. App defects found and fixed: ER-17 offline signal stuck online (api36,
  8038761 + ConnectivityLostTest), ER-18 long message cut on api33 (f6881aa). Test errors fixed: PermissionFlow
  (37711ec), LowStorage fill (523af6f).
- **New tests, unverified:** MoreSettingsEffect, PlayerExtrasSettingsEffect, OnlineSwitchesEffect (G9, 9 switches),
  HeroCarouselLifecycle (Y6/H1). yt_to_phone and push_alerts still without a test.
- **Running:** CI 43894661940; upgrade 43891389012 (v1.8.0 → c306e7e).
- **Next three actions:** (1) CI 43894661940 green → dispatch device rerun on both phones: NetworkState,
  PermissionFlow, LowStorage, Messages + the four new classes; (2) read upgrade 43891389012 → X10-5;
  (3) while the device run goes: yt_to_phone test, then G12 open work / BUG-5 TV reproduction plan.

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
