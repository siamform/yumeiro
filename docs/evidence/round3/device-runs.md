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

## 43760044391 — AccessibilityDeviceTest with the X8-2 screens on 5648a9a (both phones)

- Clean on both: main tabs, video player with controls up, the four tabs at font_scale 1.3.
- search (api36): SpeakableTextPresentCheck on [42,1926][524,2126] and [556,1926][1038,2126] = the
  two Home → "Jump back in" cards cut off by the bottom bar (shot a11y_search.png). Fixed 5e685a5.
- settings / large-font Settings part: test used `nav_shortcut_settings` (only when pinned); fixed 84e151c.

## 43759794246 — TV rerun on cc7dbff

- FirstRun now detects the API 24 dialog, but Back does not close it and the grant call is API 28+;
  all 15 tests blocked at "tab bar never showed". Fixed in test infra 6fa9ce6 (pm grant before each class).


## 43763558754 — AccessibilityDeviceTest, both phones, 208ba48 (2026-11-15)

- api33-lowram: 5/5 pass (tabs, Settings, search, large font 1.3, video player).
- api36: 4/5. `largeFontPassesAccessibilityChecks` passes on both: X8-3 (extension fragment restore crash) verified.
- api36 `settings_downloads`: four 38 dp touch targets. Screenshot `a11y_settings_downloads.png`: the Episode quality
  chips are full size; the floating "Ready to watch: … installed" app message covers their lower edge (reported
  bounds end at y=2104 px = the banner's top). Test now clears app messages first (399ccdb).
- No PermissionCenter binder stall on either phone: X2-2 verified.
- StrictMode (api33-lowram): DiskReadViolation `ContextImpl.getDataDir` ← `SubtitleStyleSettings.init` ←
  `VideoPlayerModalContent` → X2-3 (15a29ca). The 1.0–1.5 s XML-parse stalls are inside the ATF library's own
  string bundle (`AndroidXMLResourceBundle`), test-only.
- StrictMode breadcrumbs named `ExtensionGuard.kt:83` (the outer `Looper.loop()` frame), not the real caller.

## 43762847904 — TV tv-api24 after the pm grant fix (6fa9ce6)

- Storage dialog gone; the app reaches Home with the TV side rail. All tests failed in `FirstRun.toTabs`, which waited
  for the phone tag `nav_tab_home`. Test infra fixed 1a80183; rerun 43765162241.

## 43765039266 — AccessibilityDeviceTest, both phones (399ccdb)

- api36 5/5 (X8-2 Downloads chips clean once the app message is dismissed). api33-lowram: settings failed, "settings
  home after downloads never showed". Logcat 21:51:12.64: Back injected; 21:51:12.678 the previous test's
  MainActivity window `21f0c2` "was disposed without first being removed" — the key went to that stale window (X9-2).
- YuStall 3.7 s on api33-lowram with `ours=HomeOnlineKt$rememberExtensionFeed$2$1.invokeSuspend:88` (includes GC):
  open lead under X2.

## 43765162241 — TV tv-api24 (1a80183)

- HomeNavigation 5/11 pass, BackNavigation 1/1 pass, StabilityStress fail. Failures: phone bar tags (`nav_tab_*`,
  `nav_shortcut_*`) looked up on TV (X9-3); Back on Inbox lost to the previous test's window, 21:51:23.33 "input event
  receiver has already been disposed" (X9-2); h5 rail tap left page 0 with no app log (X9-4, evidence added).
- **Product bug X13-1:** every CloudStream source failed on Android 7: `NoClassDefFoundError MainAPIKt` ←
  `ObjectMapper.<clinit>` ← Jackson `ExceptionUtil.isFatal` → `java.lang.BootstrapMethodError` (21:51:22.300).

## 43767144680 — AccessibilityDeviceTest, both phones (1a577e5)

- **Pass, both phones 5/5** (search, settings incl. Downloads, 130 % font, player, tabs). X8-2 done (emulator).
- X2-3: player opened, no `SubtitleStyleSettings` StrictMode violation on either phone (done, emulator).
- api36: one Back meant for the system permission dialog waited 10 s for focus (`YuKeyFocus`); helper narrowed in eb1775c.

## Running at 22:20 UTC

- TV 43768054941 (2226756: Jackson 2.13.5 + key focus + rail tags), minSdk phone 43768670627 (eb1775c: HomeNavigation,
  BackNavigation, LocalPlayer, Accessibility on API 28), CI 43768099126 (c01bd5c).

## 43768054941 / 43769985665 / 43772037996 — TV tv-api24 (2226756 / 0931a3f / b5854c3)

- 43768054941: X13-1 proven (0 `NoClassDefFoundError`/`BootstrapMethodError`; Drama home loads). HomeNavigation
  9/11: h2 (Continue row not composed on 540 dp: test) and h5 (focus stranded on off-screen `home_setup_card`: X9-4).
  BackNavigation and StabilityStress pass.
- 43769985665 (X9-4 fix): HomeNavigation 11/11, StabilityStress 3/3; `YuTvFocus` moved stranded focus to the rail.
  BackNavigation failed: reliability guide raced Back (X9-5).
- 43772037996 (X9-5): HomeNavigation 11/11, StabilityStress 3/3; BackNavigation failed: Back waited 10 s on a dialog
  of ours and closed it (X9-6).

## 43768670627 — minSdk api28 pixel_3a (eb1775c)

- BackNavigation pass; HomeNavigation 10/11 (h1_h4: guide race); LocalPlayer 2/9 and Accessibility 4/5: the clip
  was inserted without a path and listed as "1794781938560" (X13-2); heat guard thermal half absent below API 29.

## 43771214803 — minSdk api28 pixel_3a (77d8d44, full suite)

- X13-2 classes pass: HomeNavigation 11/11, BackNavigation, LocalPlayer 9/9 (`P19 THERMAL_NA`), Accessibility 5/5.
- StabilityStress "Process crashed": `NoSuchMethodError URLDecoder.decode(String, Charset)` in NewPipeExtractor on
  `ExoPlayer:Playback`, 7 times (X13-3). FeedPosition 3/6 (feed never showed 3 cards), Yumix offline refresh message,
  CrashUi share sheet (empty top activity on API 28): open.

## TV 43774198053 (0eb1635), 43775191888 (fd19a72), 43775889375 (5344e44)

- 43774198053 BackNavigation only: one Compose root, key went to a dialog window (X9-6).
- 43775191888: capture names it: `Window{… MainActivity}` ty=2 840×wrap = phisher98 "Support Phisher Repo" (X9-7).
- 43775889375: HomeNavigation 11/11, BackNavigation pass, StabilityStress 3/3; `YuExtDialog` closed the dialog once.

## 43776085150 — minSdk api28, throwaway branch tmp/x13-3-before (test without fix)

- NewPipeCompatDeviceTest 0/2: `NoSuchMethodError` (fail-before proof for X13-3; branch deleted).

## 43777260888 — minSdk api28 (81ba4d3, X13-3 after)

- 0 `NoSuchMethodError`, no FATAL. StabilityStress 3/3, OnlinePlayback 4/4. NewPipeCompat 1/2 (link test used a
  playlist URL; fixed d603322). FeedPosition 4/6: every swipe moved 0/16 on API 28 (X13-5).

## 43777524987 — minSdk api28 (b445e2c): CrashUi pass (X13-4); Yumix offline refresh "1 answered" offline (X9-8 cause)

## 43779350057 — minSdk api28 (84ebf2f): Yumix 2/2, offline message shown (X9-8)

## 43780218270 — minSdk api28 (ed3d6b8): NewPipeCompat 2/2; FeedPosition 6/6, every feed 16/16 moves (X13-3, X13-5)

## 43780466709 — api36 + api33-lowram (ed3d6b8): NewPipeCompat, Yumix, OnlinePlayback, StabilityStress, CrashUi all pass

## 43782724021 — TV api24 (07eaa76): NewPipeCompat 2/2, StabilityStress 3/3, HomeNavigation 11/11, 0 NoSuchMethodError

## Runs 43784533563 … 43812114134 (recorded 2026-11-16 by the fifth agent, from the run logs)

| Run | Commit | Devices | Result |
|---|---|---|---|
| 43784533563 | 166a258 | bench | Macrobenchmark wiring (X2-1); no device classes |
| 43784768652 | 8bcc494 | api36, api33-lowram | Accessibility 5/5 pass both (ST-18 YuStall naming) |
| 43785978028 | 145eddb | api36, api33-lowram | StorageMainThread + StabilityStress pass both; ST-19 after = 0 violations |
| 43787473722 | 70066aa | bench | startup bench (X2-1 before) |
| 43788916880 | a60980b | bench | baseline-profile generation (X2-1) |
| 43789071263 | 3225554 | api36, api33-lowram | StabilityStress + OnlinePlayback pass both; heap dumps (X2-3h) |
| 43790371337 | fef01c6 | bench | startup bench with the profile (X2-1 after) |
| 43801874469 | 22f2640 | api36, api33-lowram | LocalPlayer#z_pl16Before pass (PL-16 before images) |
| 43806307608 | 3a33f4c | api36, api33-lowram | ExtensionPopups FAIL both (first ADR-014 build) |
| 43807629702 | c382674 | api36, api33-lowram | ExtensionPopups FAIL both |
| 43809135501 | 4d58adb | api36, api33-lowram | ExtensionPopups 7/7 pass both |
| 43810679345 | 1518c9e | api36, api33-lowram | ExtensionPopups 7/7 pass both |
| 43810683270 | 1518c9e | tv-api24 | ExtensionPopups 7/7 pass |
| 43812110468 | 7439ef4 | api36, api33-lowram | ExtensionPopups 7/8 both: p8 "Dismiss added a window 1→2". Test error: baseline taken on Home, before the Extensions screen opened. Fixed in 3f37da8 |
| 43812114134 | 7439ef4 | api36, api33-lowram | BackNavigation pass both; Extensions pass api33-lowram, api36 e8 crashed: "Already connected" from `WindowSpy.pollWindows` → `getUiAutomation()` on a second thread. Test error, fixed in 3f37da8 |
| 43823291873 | 3f37da8 | api36, api33-lowram | Extensions 7/7 pass both; ExtensionPopups 7/8 both: p8 real bug, quiet card covered Dismiss (fixed 523d960) |
| 43825449454 | d3abed6 | api36, api33-lowram | ExtensionPopups 8/8 + Extensions 7/7 pass both. api33-lowram logcat: DonationManager.recordShown commit() still on main, ST-20 guard not engaged (fixed 41820ee) |
| 43828509434 | 3caf272 | api36, api33-lowram | ExtensionPopups 8/8 + Extensions 7/7 both; ST-20 guard engaged both (no DonationManager main commit); HomeTrace: 3 Perfetto cold starts each, first frame 2.36–2.43 s lowram / 2.64–2.89 s api36 (shader compile 1.23–1.43 s, VerifyClass 0.42–0.53 s, ExoPlayer build ~95 ms) |
| 43831097217 | 2f86897 | api36, api33-lowram | HomeTrace after the player-build change: media3 verified in first frame 251–258 → 3; lowram main Running 835–846 → 509/545 ms; api36 noisy (Maps crash loop, GMS start); 2/6 traces missed the app start |

- 43847976226 (device-check, 0ceb971, api33-lowram + api36): FAILURE before any test ran. The
  main app (with the BUG-1 code) compiled; compileMobileDebugAndroidTestKotlin failed:
  ResumeAcrossProcessDeviceTest.kt:127 "Missing '}'" (a `//` comment swallowed the closing brace
  of the companion object). No device result from this run. Fixed in the next commit and rerun.
- 43849088080 (device-check, 3076b25): rerun of 43847976226 plus YtChannelProbeDeviceTest and
  YtChannelUiDeviceTest (BUG-2). Dispatched 2026-11-16 10:28 UTC. Finished ~10:40 UTC: FAILURE
  (only DecoderFault failed). Emulator results, both api33-lowram and api36 (repeat-results.txt):
  - WidgetUpdatesDeviceTest 5/5 PASS (w1–w5 ok).
  - DecoderFaultDeviceTest 0/3: test error, not an app result. `TestClip.put(ctx, "yumeiro_fault_clip.mp4")`
    opened an asset that does not exist (only `yumeiro_test_clip.mp4` is in androidTest/assets):
    FileNotFoundException in setUp. Fixed: `TestClip.put(..., asset =)`; rerun needed.
  - Anime4kGlDeviceTest PASS: ANIME4K_GL_RESULT PASS {LOW=8, MID=8, HIGH=8} (SwiftShader/ANGLE GL).
  - RtlLayoutDeviceTest PASS: locale [ar,en_US], layoutDirection=1, tab bar mirrored (Home on the
    right), Settings back at the right edge. Shots rtl_home/settings/videos/library looked at: mirrored,
    no clipped text found; Library stat tile shows "Unkn…" (ellipsis, the same in LTR, not RTL-specific).
  - ResumeAcrossProcessDeviceTest PASS (BUG-1): RESUME_A left=8000 then process killed; RESUME_B
    reopened at 8593 ms (api33-lowram) / 8215 ms (api36) from Home → Jump back in; RESUME_RESULT PASS.
    Phase a reports "Process crashed" by design (the test kills the process).
  - YtChannelProbeDeviceTest PASS (BUG-2 E5): header ok 'Google for Developers', subs 2680000, tabs
    [videos, shorts, livestreams], 30 items, next page, channel search 4 hits. NewPipeExtractor v0.26.5.
  - YtChannelUiDeviceTest reported PASS, but the shots do NOT support it: channel_4_from_row and
    channel_5_back still show the YouTube watch page (video failed: LOGIN_REQUIRED, YouTube blocks
    anonymous playback from the CI IP) on top of the channel. The check only looked for the
    `youtube_channel` node, which exists behind the page. Root cause found by reading the code
    (E6): the loading / failed watch page (`YouTubeWatchLayer`) built `YouTubeWatchPanel` without
    `onOpenChannel`, so the channel row did nothing until the player opened. Fixed, and the test now
    requires the channel with no watch page (`yt_watch_panel`, `yt_watch_loading`) on screen and logs
    the state after each Back. Status of this test: NOT a pass; rerun needed.
- Timestamp correction: the commit bodies d2923fa, 9128b10, 0ccc2f6, 8d48335 and 3076b25 give
  ~10:30–10:55 UTC; they were all made between 10:24 and 10:28 UTC (real clock, `date -u`).

- Timestamp correction (2026-11-16 10:39 UTC): commit f47c639 says 10:41 UTC in its body; it was made at 10:37 UTC.

## CI 43851470283 — ci.yml on 191dedf (unit, 2026-11-16 10:44–11:03 UTC)

- 1016 tests, 2 failed, 11 skipped. The 2 failures are `DownloadThroughputTest` (both profiles), a test
  error: the HLS fake's 40 × 600 KiB segments left 576 KiB of the 24 MiB file out. Fixed in 40ea96e.
- Pass: `ResumePointsTest` 7/7 (BUG-1 incl. D5), `YtChannelLinkTest` 6/6 (BUG-2), `Bug4QualityListTest`
  5/5 (BUG-4 F1, F5), `LivePagedTest` 8/8 (X15-2).
- BUG-3 THROUGHPUT numbers: table in `docs/proposals/FEATURE-BUG-3-download-throughput.md` §4.1.

## Device run 43851896291 — device-check on 640e984 (emulator: api36 + api33-lowram, 2026-11-16 ~11:10–11:20 UTC)

- DecoderFaultDeviceTest (G12-DF): a PASS, b PASS on both emulators (b: ERROR_CODE_DECODER_INIT_FAILED
  from the injected `yumeiro.fault.decoder`, error card shown, app alive). c FAILED on both: "player
  closed (after 15000ms)". Shot `decoder_fault_player_closed.png`: the error card still full screen.
  Cause (test error): `input keyevent 4` is a hardware key, Compose switches to keyboard input mode and
  `PlayerBackRule` steps Back like a remote (fullscreen → leave fullscreen first, R17 design). Fixed in
  7c0045b: the test taps the card's own Close (`error_close_btn`). Rerun pending.
- YtChannelUiDeviceTest (BUG-2 E6): CHANNEL_UI_RESULT PASS on both. Shots looked at (api36 + api33):
  channel_1_listed (Videos tab of "Google for Developers"), channel_2_scrolled, channel_3_video (watch
  page; playback blocked by YouTube on the CI IP, LOGIN_REQUIRED), channel_5_back: the channel page
  is on top with no watch page. The flow open channel → list → scroll → open video → Back to channel is
  proven on emulator. Remark: the "YouTube couldn't play this video" snackbar stays visible on the
  channel page after Back (cosmetic, logged for G3).
- YtQualityDeviceTest: YT_QUALITY_RESULT SKIP (SignInConfirmNotBotException, YouTube bot wall on the CI IP).
  The repeat runner counts it as pass; it is NOT a pass.
- YtQualityOfflineDeviceTest (BUG-4 F1 without YouTube network): YT_OFFLINE_RESULT PASS on both,
  download sheet heights [1080,720,480,360,240] = player rows [1080p,720p,480p,360p,240p], no engine-only
  rows. Shot `ytqo_dialog.png` looked at: five rows, 240p "Playing now".

## Device run 43855202294 — device-check on 93070ae (emulator, both, 2026-11-16 11:09–11:27 UTC)

- DownloadThroughputDeviceTest: no measurement. Case screen-on: the probe reached proof.ovh.net
  (ash-speed.hetzner.com did not answer), then the app's download failed before the first byte:
  IPv6 ENETUNREACH (the emulator has no IPv6) and IPv4 141.95.207.211:443 timeout after 15 s. Case b:
  ~10 s later the plain probe reached neither host → SKIP. Runner network, not an app result. The test
  now tries five hosts and fails only when the app cannot connect while a plain client can (445156c).

## CI 43854742733 — stuck, replaced by CI 43857949982 (2026-11-16 ~11:35 UTC)

- ci.yml on 40ea96e stayed "queued" from 11:06 with 0 jobs created (GitHub status: Actions operational).
  `gh run cancel` (also `--force`) answered "Cannot cancel a workflow run that is completed", so it is a
  stale record. Replaced by CI 43857949982 on the current head (contains 40ea96e plus test-only and docs
  commits). Not a duplicate: the stuck run will never execute.
- Correction (11:35 UTC): the "stuck" run 43854742733 got its jobs at ~11:30 and started; through the
  ci-<ref> concurrency group it cancelled CI 43857949982 (Build cancelled after 3 min, gitleaks passed).
  43854742733 (40ea96e) is now the CI run of record; ProgressGateTest (24ee21b) goes in the next CI.

## Device run 43857809658 — device-check on 445156c (emulator, both, 2026-11-16 11:36–11:47 UTC)

- DecoderFaultDeviceTest: a, b, c PASS on both emulators (G12-DF complete on emulator).
- QualityListDeviceTest (now every height): manifest [1080,720,480,288,184].
  - api33-lowram: 184p (decoder 320x184) and 288p (512x288) applied, frame shots; then Back did not
    close the dialog in 5 s (cause unknown; a8b4d27 logs misses with shots).
  - api36: 184p, 288p applied; 480p reached 848x480, then snapped back to Auto and 1280x720. **App bug**:
    PlaybackHealthMonitor counted the three pick-induced buffers as network rebuffers and cleared the
    override. Fixed in 10be03d (RebufferWindow, picks/seeks excluded).
- DownloadThroughputDeviceTest: no measurement again (Hetzner: "unexpected end of stream" to a plain
  client; OVH IPv4 timeouts). kernel.org CDN hosts added in a8b4d27.

## CI 43854742733 — ci.yml on 40ea96e (unit, ran 11:30–11:48 UTC)

- 1022 tests, 1 failed, 11 skipped. Passed: Bug3PieceTest 4/4, Bug4QualityListTest 7/7 (incl. F4 x2),
  DownloadThroughputTest steadyHost (all engines ≤ 1.0x).
- Failed: DownloadThroughputTest.burstThenCapPerRequest — segmented4 4.82x [43,11,5,5,4,367,256,6,5,654],
  segmented2-lowend 3.30x. The 40ea96e split fix alone does not meet the rule. Cause and fix: 3c85a72
  (RequestPace). Rerun: CI 43861926740 (head; also ProgressGateTest, RebufferWindowTest).

## Device run 43861039865 — device-check on 10be03d (emulator, both, 2026-11-16 11:52–12:02 UTC) — success

- QualityListDeviceTest (BUG-4, every height): PASS on both, backMisses=0.
  - api36: 184p→320x184, 288p→512x288, 480p→848x480, 720p→1280x720, 1080p→1920x1080, playback
    position advancing between picks (13.8 s → 43.0 s); the 480p revert seen in 43857809658 is gone (10be03d).
  - api33-lowram: the same five decoder sizes, but the position stayed at 9786 ms for 480/720/1080 and the
    shots show the play button (not playing / buffering): the format was applied, smooth playback at
    720p/1080p on this 2 GB emulator is NOT shown. Open observation for G3 (low-RAM high-res playback).
  - Shots quality_applied_{184,288,480,720,1080}p.png looked at (badge and toast match each pick).
- DownloadThroughputDeviceTest (BUG-3, real host cdn.kernel.org, 134.7 MB, real enqueue → DownloadWorker
  → Segmented, before RequestPace 3c85a72):
  - api36 screen-on 8.13 MB/s, per10 % ms [1813,1608,1506,1508,1506,1508,1518,1711,1513,1615], last/mean 1.02.
  - api36 screen-off + forced Doze (IDLE) at 30 %: 8.61 MB/s, last/mean 0.88, DONE.
  - api33 screen-on 6.22 MB/s, last/mean 0.83; screen-off + Doze 5.88 MB/s, last/mean 0.60, DONE.
  - No end collapse on this CDN (it does not cap per request). The collapse is reproduced only by the
    burst-then-cap profile (unit); RequestPace targets that.
- Branch `tmp/bug3-before` (old ForceGrab) deleted without a device run: run 43861039865 showed the real
  CDN does not cap per request, so a before/after on it cannot show the collapse; the before/after
  evidence is the unit burst profile (CI 43851470283 before, CI 43861926740 after).
- Dispatched device 43863246577: HomeTraceDeviceTest with gpu=swangle_indirect.

## CI 43861926740 — ci.yml on 3c85a72 (unit, 2026-11-16 ~11:58–12:20 UTC) — success

- 1059 tests, 0 failed, 11 skipped. ProgressGateTest 4/4, RebufferWindowTest 6/6, Bug3PieceTest 8/8,
  Bug4QualityListTest 7/7, DownloadThroughputTest 2/2 (BUG-3 rule met: segmented4 burst 1.67×,
  low-end 0.09×; table in FEATURE-BUG-3 §4.3).

## Device 43863246577 — HomeTraceDeviceTest, gpu=swangle_indirect (3c85a72-era branch head) — success

Emulator (Perfetto, debug APK, 3 cold starts per phone, `scripts/home_trace_report.py`):

| Phone | bindApplication ms | first frame duration ms | main Running ms | shader_compile ms | VerifyClass(main) ms | media3 classes in frame |
|---|---|---|---|---|---|---|
| api33-lowram | 2096 / 1343 / 1391 | 2263 / 2263 / 2360 | 858 / 919 / 1000 | 145 / 132 / 97 | 993 / 1180 / 1187 | 3 / 3 / 3 |
| api36 | 1640 / 1771 / 1081 | 2198 / 2815 / 1152 | 709 / 1277 / 782 | 156 / 207 / 70 | 572 / 718 / 360 | 3 / 3 / 3 |

- With the ANGLE-on-SwiftShader (swangle) GPU, RenderThread shader compile falls from 1229–1427 ms
  (default GPU, run 43828509434) to 70–207 ms: the earlier 1.2–1.4 s was the emulator's GPU mode, not app code.
- The first-frame duration does not fall by the same amount: the frame now waits on the main thread,
  where debug-build class verification (`VerifyClass`, no AOT) is 360–1187 ms. That cost does not exist in
  a release build with the baseline profile; the real-phone number is OMT-12.
- The player build stays out of the first frame (3 media3 classes, ST-18 fix 2f86897 holds).

## Device 43865392890 — ExtensionPopupsDeviceTest on the TV emulator (tv-api24, 1080p) — failure (test error)

- 7/8 pass on TV (p1–p7 incl. Quiet card, Show, Block, login framed, dead-token window swallowed).
- p8 failed at line 242: it waited for `nav_shortcut_extensions`; the TV rail tags items `tv_` + the bar
  tag (`TvNavRail.kt`). Test error; fixed in the next commit, rerun dispatched (43866791994).
- Shots looked at: `adr014_quiet_card.png` is taken before the card's enter animation (no card yet);
  `adr014_quiet_card_late.png` shows "An extension has a message · Show". On TV the card sits over the
  second tile row, not at the bottom edge (observation, cosmetic; not changed).

## CI 43864957319 — ci.yml on fail-before/r15-proofs 408faea — failure (compile, not a proof)

- `compileMobileDebugUnitTestKotlin` failed: `ResumePointsTest` calls `savedVideoPosition`, which the
  revert commit removed with the D1 fix. A compile failure proves nothing about behaviour, so this run
  is not counted as fail-before evidence (my error: the accessor is API, not the fix).
- Fixed on the branch (accessor kept, its D1 use still reverted) and the DL-6 race, DL-9 drop and LB-8
  tests added without their fixes. Rerun: CI 43867540147.

## TV 43866791994: ADR-014 rerun after the p8 test fix (emulator, tv-api24)

- `ExtensionPopupsDeviceTest`: OK (8 tests), repeat-results PASS. ADR-014 on TV is now 8/8 on emulator.
- Shots: `adr014_extensions_setting.png` shows the quiet card ("An extension has a message · Show") at the
  bottom of Extensions. Observation (cosmetic, not changed): the refresh spinner sits on the "More sources" card text
  in that shot.

## Device 43867796913: DownloadKillDeviceTest (emulator, api36 + api33-lowram, real host kernel.org)

| Phone | a: kill at 25 % | b: continues in the new process | c: delete at 10 % |
|---|---|---|---|
| api33-lowram | killed at 43.1 MB of 134.7 MB, `.part` + `.part.chunks` on disk | PASS: DONE, 134 728 520 B, only the final file; read 110.7 MB after the kill for 91.6 MB left | PASS: files during = `.part`, `.part.chunks`; after = none; not listed |
| api36 | killed at 43.3 MB | FAIL on the byte rule only: DONE, right size, only the final file, resumed from 41.9 MB (= the done chunks), but read 126.2 MB for 92.8 MB left (rule: < rest + 24 MiB = 117.9 MB) | PASS (same as above) |

- The resume itself works on both (the first progress after the restart is 40 MiB = the 10 done 4 MiB chunks;
  WorkManager restarted the worker after its backoff, 12:41:19).
- The extra 19–33 MB is data read and thrown away. Open question (DL-11): RequestPace restarts (up to 32 per piece)
  and tail splits close a response with bytes in flight. The test rule stays; DownloadThroughputTest now reports
  the bytes the local server sent per engine to measure it.

## CI 43867540147: fail-before branch `fail-before/r15-proofs` (unit, CI)

The new tests run against the code without their fixes (accessor kept so it compiles).
1023 tests, 10 fail — exactly the expected ones, nothing else:

| Test | Fix it proves |
|---|---|
| `LivePagedTest.aTimeoutInsideTheFetchIsAFailureNotAStuckSpinner` | X15-2 |
| `ResumePointsTest` ×3 (unloaded player, domain rotation, online zero) | BUG-1 |
| `Bug4QualityListTest.youTubeManifestStillGetsItsRequest` | BUG-4 |
| `DownloadListSaveRaceTest.parallelFinishesAllReachTheFile` | DL-6 |
| `DownloadThroughputTest.droppedConnectionsResumeInsideOneRun` | DL-9 |
| `DownloadThroughputTest.burstThenCapPerRequest` | BUG-3 (no RequestPace there) |
| `LibraryCrudTest` ×2 (CRUD order across restart, liked order) | LB-8 |

Next on that branch: device `YtQualityOfflineDeviceTest` 43871061781 (expected FAIL), then the branch is deleted.

## Device 43871061781: YtQualityOfflineDeviceTest on `fail-before/r15-proofs` (emulator, both phones)

Expected FAIL, and it failed for the BUG-4 reason on both phones: "player list lacks download heights
[1080p, 720p, 480p] · rows=[360p, 240p, 144p]". The same test passes on r15-stability (BUG-4 fix).
The fail-before branch is deleted after this run (no other run uses it).

## CI 43871066269: r15-stability head 1059e…(after DL-9) (unit, CI)

1047 tests, 2 fail, both test errors (fixed in the following commit, rerun owed):
- `DownloadListSaveRaceTest`: FileNotFound — the store kept the files dir of the first Robolectric test.
  **Correction:** the same error made the fail-before run fail, so that "expected FAIL" for DL-6 was not proof.
- `DownloadThroughputTest.burstThenCapPerRequest`: segmented2 last 10 % = 107 ms of a 349 ms download (3.07×);
  a floor of 250 ms is added (fail-before tails were 659–660 ms, still caught).
Passed: LibraryCrudTest 7/7 (LB-8), DownloadDeleteFilesTest (DL-7), HLS quality (DL-8), dropped connections
(DL-9), ResumePointsTest, Bug4QualityListTest, LivePagedTest.
Data used (`servedOverSize`): segmented4 steady 1.026, burst 1.126; segmented2 burst 1.122; single-no-ranges
burst 1.132 (size probe answered with a full 200 body); YouTube and HLS 1.000 → DL-11.

## Device 43875418000: SettingsWalkDeviceTest first run (emulator, both phones) — test error

Phase a failed on both phones on the first page: every switch on Look & feel "did not move" (the tap
landed below the window; the page is a scrolled column) and close() waited for
`settings_cat_appearance`, which Settings home's remembered scroll keeps off the list. Phase b skipped
("phase a left no state"). Not a product result. Fixed in e7efd6e; rerun 43877623222.
CI 43876676611 cancelled: started on eb9b4b6, head moved to e7efd6e (ST-1 fix) before it finished.

## Upgrade check 43869944264 (v1.8.0 → HEAD, emulator)

- Data: `upgrade_compare.py` 19 → 21 files, **0 lost values**, 1 changed (`mt_updater.xml last_check`, the update check time). New files: `files/crash/breadcrumbs.log`, `yumeiro_crashlog.xml` (the new build's breadcrumb log, not a crash: FATAL/ANR lines 0).
- Verdict FAIL = harness error. Screens: old app start showed v1.8.0's "Update available" dialog (v1.8.0 → v1.8.1); the first-run loop's button list lacked "Remind me later", so after 45 s the welcome guide was still up (`ui-old-tabs`, `ui-old-player`), the clip never opened, and the new build correctly showed the still-unanswered guide.
- Fix: "Remind me later" added; the script now checks the precondition (old app past dialog and guide) and reports `ERROR_PRECONDITION` instead of a product FAIL; it logs whether the old app opened the clip. Rerun dispatched after this commit.

## Device 43877623222: SettingsWalkDeviceTest rerun (emulator, api33-lowram + api36) — FAIL, test reach

- Phase a flipped 28 switches on both phones; 3 "did not move": `tracking/lyrics_online_toggle`,
  `backup/cache_enabled`, `backup/backup_auto`. Log: "no visible '<tag>' after 10000ms" — Tracking and
  Backup are plain `verticalScroll` Columns; the walk only scrolled lists by index, so these three below the
  fold were never brought on screen. Test error, not a product result for those three.
- Phase b (new process after the kill): `SW_RESULT PASS verified=28` on both phones — the 28 flipped switches
  kept their new value across the process death and were put back.
- Fix: `flip()` calls `performScrollTo()` on the switch first (works for both parents). Rerun owed.

## CI 43877618176: r15-stability e7efd6e (unit, CI) — success

1074 tests, 0 fail, 11 skipped. `SettingsResetTest` 4/4 (SE-1), `DownloadListSaveRaceTest` 1/1 (DL-6),
`DownloadThroughputTest` 3/3 with the 250 ms tail floor: burst segmented4 per10pct last 67 ms of 370 ms,
segmented2-lowend last 110 ms of 461 ms; steady profiles flat (tailRatio 1.00). servedOverSize burst
segmented4 1.092, segmented2 1.037, single-no-ranges 1.132 (DL-11 data waste still open).

## Upgrade check 43878778019 (v1.8.0 → d978853, emulator) — reported PASS, not accepted

`UPGRADE_RESULT PASS compare=0 pre_ok=1 played=0 guide_again=0 clip_seen=0 fatal=0`. Data: 36 → 39 files,
**0 lost values**, 1 changed (`mt_updater last_check`), new files breadcrumbs.log, reliability_guide.xml,
yumeiro_crashlog.xml; FATAL/ANR 0. But the screens show v1.8.0's extension support pop-up ("Support Phisher
Repo … Maybe Later") over the tabs and over the clip (v1.8.0 has no Quiet mode), so the clip never played in the
old app (played=0) and history/resume parity was not exercised; after the upgrade the new app's "Keep
notifications on time" guide covered History (clip_seen=0). pre_ok=1 was wrong (the pop-up was not checked).
Harness fixed: "Maybe Later" answered (case), pop-ups answered after the clip starts and before History,
pre_ok counts the pop-up, and PASS now needs played=1 (else INCOMPLETE_NOT_PLAYED) and the clip listed after
the upgrade (else FAIL_CLIP_NOT_LISTED). Rerun owed.
