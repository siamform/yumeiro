# Round 3 device evidence (GitHub Actions emulators)

Two emulators run on every device check:

| Name | Image | RAM / heap |
|---|---|---|
| api33-lowram | Android 13, pixel_3a | 2 GB / 192 MB |
| api36 | Android 16, pixel_6 | 4 GB / 512 MB |

These are real emulators running the real debug APK; the tests are not simulated. YouTube tests skip on CI because YouTube answers the GitHub runners with "Sign in to confirm you're not a bot".

## Runs

| Run | Commit | api33-lowram | api36 | Notes |
|---|---|---|---|---|
| [43626650174](https://github.com/siamform/yumeiro/actions/runs/43626650174) | 5a1c9ec | FAIL | FAIL | AC3 StuckPlayerException (api33). ForegroundServiceDidNotStartInTime crash (both). ForceDownload initializationError. 8.7 s start-up stall on api36. |
| [43629395323](https://github.com/siamform/yumeiro/actions/runs/43629395323) | b0e596d | FAIL | FAIL | Stress, PlayerSwitch 5/5 (AC3 included), SurfaceMode and PlaybackSession pass. OnlinePlayback c_ (UI flow) fails: the source chips moved into a menu. The crash export file is listed with size 0. |
| [43632313418](https://github.com/siamform/yumeiro/actions/runs/43632313418) | 0575711 | PASS | PASS | All six classes pass. Crash export: 192,627 bytes on api33 and 192,638 on api36, matching the report size. No FATAL or ANR. Max stall 1,340 ms (api33) and 1,043 ms (api36). |
| [43641330758](https://github.com/siamform/yumeiro/actions/runs/43641330758) | 1eb1b65 | PASS | FAIL | api33: all pass, with 3 navigation recoveries and a max stall of 1,472 ms. api36: every class passes except Stress. Its new recovery saw ComposeNotIdleException and relaunched instead of reporting it; now under investigation. |
| [43643387375](https://github.com/siamform/yumeiro/actions/runs/43643387375) | 7f528fe | FAIL | FAIL | Stress only. Endless recomposition after D-pad focus traversal (ComposeNotIdle: pending recompositions, hadSnapshotChanges=false; samples in PagerBringIntoViewSpec, FocusableNode, LazyLayoutBeyondBounds). api33 also caught regex compiled on main in the YumixFeed$Category and YumixFilterKt static initialisers. |
| [43645273428](https://github.com/siamform/yumeiro/actions/runs/43645273428) | warm-up commit | PASS | PASS | Stress only. Patterns in 91 classes are warmed off main. No app regex on main. Max stall 1,115 ms (api33) and 1,542 ms (api36). The spin did not reproduce. |
| [43646522316](https://github.com/siamform/yumeiro/actions/runs/43646522316) | dpadFocusSettles | PASS | PASS | Stress only, 2 tests. Max stall 732 ms (api33) and 796 ms (api36), 0 navigation recoveries. The new focus check counted 0 snapshot applies in every window, which shows it could not see a frame-clock loop. It was replaced by a rendered-frame count. |
| [43647993548](https://github.com/siamform/yumeiro/actions/runs/43647993548) | 48bcfb4 | — | — | Invalid run: the class filter needs the full name `dev.asfakul.yumeiro.StabilityStressDeviceTest`. |
| [43648850998](https://github.com/siamform/yumeiro/actions/runs/43648850998) | 48bcfb4 | PASS | PASS | Stress only, with the carousel focus pause and the rendered-frame check. 0 frames rendered in every 2 s window after each D-pad pattern on Home and Yumix, so the screen settles. Max stall 806 ms (api33) and 529 ms (api36). api36 had 1 recovery: a system permission dialog covered the app. A detail page showed the plugin's own "Error loading: Invalid JSON response". InternetArchiveProvider raises that for any `load()` failure, network included. The error screen with retry handled it. |
| [43650046635](https://github.com/siamform/yumeiro/actions/runs/43650046635) | 4540f1f | PASS | PASS | All six classes pass. No FATAL, ANR or OOM. Crash export 192,627 / 192,638 bytes. PlaybackSession `created=1 reused=2 resumed=1`, released after the grace period. Max stall 809 ms (api33) and 461 ms (api36). But the stress test hid a real spin: Compose never idle at round 2 on both devices. 1,717 snapshot applies in 3 s; main in `DefaultPagerNestedScrollConnection.onPreScroll → PagerState.dispatchRawDelta`; a pager offset stuck at −0.48; 99.4 % janky frames. Back cleared it, and the test carried on. |
| [43652020450](https://github.com/siamform/yumeiro/actions/runs/43652020450) | 82a5996 | PASS | PASS | Stress and OnlinePlayback, before the pager fix. `d_manualPickOfADeadSourceRotatesAgain`: the dead pick was tried, then playback rotated to `his_girl_friday.mp4`, and the dead link was marked failed (both devices). Time labels while opening: `[00:00, 1:31:44]`, never "--:--". `swipeThenDpadSettles`: 0 frames in all 8 attempts, so the spin did not reproduce on demand. |
| [43653623319](https://github.com/siamform/yumeiro/actions/runs/43653623319) | 2ba28e1 | PASS | PASS | Stress only, with the tab-pager fix and the stricter test (any "Compose never idle" fails it). 3/3 tests. Compose spins 0. Max stall 594 ms (api33) and 579 ms (api36). |

## What each class proves

- **StabilityStressDeviceTest**
  - Covers tab, pager and feed flings, D-pad focus traversal, and the Yumix, YouTube and device video feeds.
  - Fails on any app regex compile on main or a main-thread stall of 4 s or more.
  - Since 1eb1b65 it also fails when a tab does not respond.
- **PlayerSwitchDeviceTest**
  - Engine quality levels switch without timeout.
  - Every DASH rendition has picture and sound.
  - Every audio rendition (AAC, AC3) is audible.
  - Separate audio is merged.
- **SurfaceModeDeviceTest:** the surface and texture rules.
- **PlaybackSessionDeviceTest**
  - One player for every video.
  - Release after close.
  - Run 43641330758: `created=1 reused=2 resumed=1`, released after the grace period.
- **OnlinePlaybackDeviceTest**
  - A direct MP4 plays.
  - An extension link plays.
  - The full UI flow plays.
- **ForceDownloadDeviceTest**
  - A DASH stream saves as one MP4 with video/avc and audio/mp4a: 3,944,838 bytes, 6 s clip.
  - A segmented DASH stream saves too: 36,632,975 bytes, 634 s.
  - "View downloads" moves the video to the floating player, and a new video expands it.
  - The crash report is exported as a file right away.

## Runs after the tab-pager fix (R15)

| Run | Commit | Scope | API 33 | API 36 | Notes |
|---|---|---|---|---|---|
| 43654905931 | 2ba28e1 | full | cancelled | cancelled | Cancelled by a later dispatch (shared concurrency group); group now includes the test filter (0cb815a) |
| 43656137755 | 61bf92b | drama + stress | drama FAIL, stress 3/3 | drama FAIL, stress 3/3 | Test waited on sources held by the first-run gate; test bug, fixed in 0cb815a |
| 43657727593 | 0cb815a | drama | 1/1 | 1/1 | 4 rows x 18 dramas; episode first frame 1080x1920 |
| 43657729675 | 0cb815a | full, 7 classes | 18/18 | 18/18 | never idle 0, ComposeNotIdle 0, FATAL/ANR/OOM 0, max stall 477 ms / 542 ms; dead pick rotated; labels [00:00, 1:31:44]; YouTube test skipped (CI IP bot-checked) |

## R16 Group 1 and ST-7 runs (2026-11-15)

| Run | Commit | Scope | API 33 | API 36 | Notes |
|---|---|---|---|---|---|
| 43683535994 | 10bbd6e | full | pass | pass | |
| 43683528288 | 10bbd6e | stress 2 × 5 | 10/10 | 10/10 | FATAL 0; steady app stalls ≥700 ms 0.5 / 0.3 per run |
| 43683520404 | 10bbd6e | FeedPosition × 3 | 3/3 | 2/3 | api36 miss = ANR "No response to onStartJob" in an 8.2 s main stall 1 min after boot |
| 43685183725, 43686104737 | 6df7c1f | HomeNavigation | 5/5 | 5/5 | h7 singleTop onNewIntent |
| 43687396407 | a5b8f60 | stress 2 × 5 | 10/10 | 10/10 | FATAL 0; steady app stalls 0.4 / 0.1 per run; cold 0.5 / 0.6; 9 of 15 app stalls "framework only" |
| 43687398312 | a5b8f60 | FeedPosition × 3 | 3/3 | 3/3 | |
| 43687458863, 43688381102 | 4098177, ac6e412 | BackNavigation | fail | fail | Test clock: Thread.sleep does not recompose |
| 43688049790 | 77fc8ca | HomeNavigation × 2 | 2/2 | 1/2 | h10 lost adb keystrokes (test) |
| 43689402737 | 3479bf8 | BackNavigation × 2 | 2/2 | 0/2 | Back after swipe cancelled (sendCancelIfRunning): app, fixed in 89f7553 |
| 43689481257 | 2c109ac | HomeNavigation × 3 | 3/3 | 3/3 | h10 field text 'yumei', suggestions shown |
| 43690918679 | 89f7553 | BackNavigation × 3 | 3/3 | 3/3 | first back arms, no callback left enabled, second back → STARTED |
| 43691032951 | ec035ac | HomeNavigation × 2 | 0/2 | 1/2 | new h2 (api33), h5 (D-pad in touch mode) |
| 43692234004 | 14c5998 | HomeNav + BackNav × 2 | Back 2/2, Home 0/2 | Back 1/2, Home 0/2 | reliability sheet took a back (test); h5 focus |
| 43693532355 | e9c281c | HomeNav + BackNav × 2 | Back 2/2, Home 0/2 (h5 only) | Back 2/2, Home 0/2 (h5 only) | h5: rail entered on Home (app bug) |
| 43694972929 | ec6ebd8 | HomeNavigation × 2 | 0/2 (h5 only) | 0/2 (h5 only) | h7b SEND passes; h5 focus `tv_nav_tab_home`, once `home_setup_card` |
| 43696246671 | a53effb | HomeNavigation × 2 | 2/2 | 2/2 | rail Left → selected tab, Down → Videos, centre → page 2 |
| 43697438813 | 3342106 | full suite | fail (h2, FeedPosition precondition) | fail (h2) | h2 fresh-data case (test); stress 0 compose spins |
| 43697447466 | 3342106 | stress 2 × 10 | 10/10 | 9/10 | ST-17 bring-into-view spin once |
| 43697545350 | 3342106 | HomeNavigation h13b | pass | pass | |
| 43701056608 | 5b1263e | full suite | pass | pass | every class |
| 43701058752 | 5b1263e | Extensions + Pinned | fail | fail | Back in selection left the screen (app, fixed bfde23d); e8 back (test) |
| 43702633505 | bfde23d | Extensions + Pinned | fail | fail | raw "Unable to resolve host" (app, fixed 9be5afb); e6 long-press on chips (test) |
| 43704211410 | 9be5afb | Extensions + Pinned | 6/6, 1/1 | 6/6, 1/1 | |
| 43705847970 | 97bb255 | Extensions + Pinned | 7/7, 1/1 | 7/7, 1/1 | e9 starters |
| 43706162128 | 2744a23 | LocalPlayer | cancelled | cancelled | superseded by 43707028426 (workflow concurrency) |
| 43707028426 | 4c885fe | LocalPlayer | 1/5 | 1/5 | e Cast ready in 192 / 138 ms (app fix b5d2d5a); a–d test errors (off-screen controls, clip end, floating timing, saver while charging) |
| 43705867413 | 97bb255 | stress 2 × 5 | 10/10 | 10/10 | ST-7 steady 0.4 / 0.4, cold 0.8 / 0.3; ST-17 0 spins |
| 43708220122 | 5bfa07c | LocalPlayer | 4/5 | 4/5 | b: stepped Back (test); a, c, d, e pass |
| 43709093938 | 3dc3914 | full suite (adds LocalPlayer, HeaderStream) | every class | every class except LocalPlayer b | b: 12 s clip passed 95 % before Close (test timing) |
| 43712469920 | 9877afb | LocalPlayer × 2 | 5/5, 5/5 | 5/5, 5/5 | resume within 0.2–0.6 s, pill shown |

## R16 Group 4 (Yumix) and Group 5 runs (2026-11-15)

| id | sha | what | api33 | api36 | note |
|---|---|---|---|---|---|
| 43714635144 | 6e57d9e | full suite (rerun after the Group 3 timing fix) | every class | every class except LocalPlayer d | d: battery saver precondition (test, fixed 65e50e0) |
| 43715245923 | 2a79dbe | YumixOffline + FeedPosition#yumix | FeedPos pass; offline skipped | pass, 8/8 kept | skip: 4 cards < old threshold 6 |
| 43716649966 | 9e10315 | YumixDeviceTest | see all 78 → 694; offline skipped | see all 26 → 468; offline skipped | |
| 43717950021 | 65e50e0 | YumixDeviceTest + LocalPlayer#d | see all 78 → 592; d pass | see all 26 → 593; d pass | offline skipped on both |
| 43719277646 | 678903d | Yumix | fail | fail | cards vanished: Internet Archive removed by the legacy migration (app, fixed e2a1114 / 52a8c42) |
| 43720589678 | — | diagnostic (feed logging) | — | — | diagnostic only |
| 43721853978 | 6267099 | diagnostic (unload log) | pass | pass | diagnostic only |
| 43723079397 | 52a8c42 | Extensions + Yumix | 7/7; 2/2 | 7/7; Yumix fail | api36: old "kept" check, the offline notice shrinks the viewport (test) |
| 43724760853 | 8769ef8 | full suite | fail: Drama, HomeNav h2, Yumix | same | Drama: DramaBox CDN NXDOMAIN (app n + test q); h2 test p; Yumix test o |
| 43732060154 | 3c29282 | Drama + Yumix + HomeNav | Drama pass; HomeNav 11/11; Yumix fail | Drama slow (55 s); HomeNav 11/11; Yumix 2/2 | api33 Yumix: section still loading at hold time (test, fixed bc68326) |
| 43733844682 | bc68326 | G4 milestone full suite | 15/16 classes; Drama fail | 16/16 classes | api33 Drama: every onshort.net call timed out at 20 s in that window (upstream) |
| 43738288853 | a119186 | Drama + LibraryDeviceTest | 2/2 pass (listening +36.8 s) | 2/2 pass (listening +36.6 s) | rerun of api33 Drama; first run of the Group 5/6 device test |

## 2026-11-16 (fourth handover): Group 3 design items

| Run | Commit | Filter | api33-lowram | api36 | Notes |
|---|---|---|---|---|---|
| 43747147539 | 236910c | LocalPlayerDeviceTest (9 tests) | 7/9 | 7/9 | New f (More menu, sleep sheet, 1-min timer paused after 60.1 s, volume 1.0), g (one Back → floating), h (refused-PiP message), i (SRT served as WebVTT from 10.0.2.15 with CORS) pass on both. a and c failed on both: the test helper tapped the player centre (Pause) when the button was in the More menu (PiP actions showed "Play"). Test fixed after this run. Screenshots `pl16_*`, `pl17_*`, `pl18_*` in the artifacts |

## 43744809503 — full suite on 22f7c30 (both emulators)

- api36 (Pixel 6): all 20 classes PASS.
- api33-lowram (Pixel 3a, 2 GB): 19 classes PASS; `ExtensionsDeviceTest` FAIL, 1 test: `e11` printed `E11_RESULT PASS`, then the cleanup Back (`input keyevent 4`) at 18:41:26 removed window 195fe20, a second window that opened over Source status at 18:41:14 while five drama feeds were timing out. Source status stayed on screen, so `waitGone("source_health_run")` failed after 10 s. Logged as EXT-T1 (open).
- YouTube and onshort.net checks were skipped as unreachable from the CI network (assumption, not pass).

## 43748560619 — LocalPlayer, AniSkip, PlaybackSession on 01e0719 (both emulators)

- api36 (Pixel 6) and api33-lowram (Pixel 3a): LocalPlayerDeviceTest OK (9 tests), AniSkipDeviceTest OK, PlaybackSessionDeviceTest OK.
- LocalPlayer d: battery saver → WARM and thermal 3 → HOT on both.
- ST-7, `YuSession player release held main`: api36 1, 69, 48, 23, 75, 36 ms; api33-lowram 301, 100, 103, 45 ms.

## 43749337517 — Extensions, Accessibility on fbbe7b9 (both emulators)

- ExtensionsDeviceTest OK on both (EXT-T1 not reproduced).
- AccessibilityDeviceTest: api33-lowram PASS; api36 FAIL: TouchTargetSize 46 dp on the top-bar search and bell, SpeakableTextPresent on 7 Music cards with titles off screen (fixed 3019605, a898010).

## 43749978183 — HeatGuard, ClosedAppAlert on a5fa49b (both emulators)

- HeatGuardDeviceTest PASS on both (HEAT_THERMAL_RESULT).
- ClosedAppAlertDeviceTest: api36 PASS (posted 122,960 ms after queueing, app killed, deep IDLE, new pid). api33-lowram: alert delivered (76 s) but the test asserted an empty queue while the app had queued its own alert (test error, fixed 8dd11e0).

## 43754318750 — CrashUi, ClosedAppAlert, Accessibility on f0df775 (both emulators)

- CrashUiDeviceTest PASS on both: real crash on thread yu-crash-test, report read, shared (system Chooser on top), Keep, Delete → empty.
- ClosedAppAlertDeviceTest: api36 PASS (122,979 ms, pid 4903 → 5508). api33-lowram FAIL: posted 30.8 s after queueing; the app was started by a pacer alarm 29 s in. App bug: deliverNext ignored the gap (fixed 9b46045, X5-2).
- AccessibilityDeviceTest: api33-lowram PASS; api36: top bar and Music clean now; Videos: Yumix category pills 46 dp tall (fixed b0fbea9).

## CI 43754316254 — first TV lint on f0df775

- Build, unit tests, gitleaks: success. Lint mobile 117 errors, TV 121: NewApi readNBytes (Cast subtitles, API 33), NewApi API 28 calls in ReliabilityGuide (TV), RememberReturnType ×2, TV manifest ×2, 113 UnsafeOptInUsage (fixed ff00d11, 6651bc9, 7486997, c7772ce, 72394dc).

## 43757544993 — ClosedAppAlert, Accessibility on b0fbea9 (both phones)

- ClosedAppAlertDeviceTest PASS on both: api33-lowram posted 123,113 ms after queueing (pid 4109 → 6877), api36 123,013 ms (pid 3458 → 4791). The api33 early delivery (30.8 s) is gone: X5-2 verified.
- AccessibilityDeviceTest A11Y_RESULT PASS on both (X8-1 verified).
- StrictMode: DiskReadViolation in `ThemeSettings.init` (getSharedPreferences) inside the first composition; the whole message took ~5.9 s on api33-lowram and ~4.0 s on api36 (debug build). Logged as startup finding X2-1.

## 43757547383 — first TV run (tv-api24, android-tv x86) on b0fbea9

- The TV app installed and launched on API 24 with no crash.
- HomeNavigation 11/11, BackNavigation 1/1, StabilityStress 3/3 failed, all from one test-helper cause: the API 24 storage permission dialog (packageinstaller GrantPermissionsActivity) stayed on top. FirstRun granted only Android 13+ permissions and matched only API 29+ dumpsys wording. Fixed in FirstRun.kt; rerun 43759794246.

## CI 43757542521 — lint recheck on b0fbea9

- Build, full phone unit suite, TV unit subset, gitleaks: success.
- lintMobileDebug 0 errors (367 warnings), lintTvDebug 0 errors (335 warnings), down from 117 / 121 in 43754316254. Lint made blocking in `ci.yml` (X10-1..4 verified).

