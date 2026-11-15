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
