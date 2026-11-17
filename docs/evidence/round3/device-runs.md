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

## 43880703410 (d4b657f, api33-lowram + api36, emulator)

- SettingsWalkDeviceTest PASS both: `SW_RESULT PASS verified=31` (S27 closed).
- PlayerSettingsEffectDeviceTest PASS both (PFX a/b/c). NotifySettingsEffectDeviceTest PASS both (NFX a/b/c).
- BackdropImportDeviceTest FAIL both: every type as expected except corrupt.jpg (a.jpg cut at 9000 bytes) saved
  as a backdrop → SE-4 (BitmapFactory fallback after ImageDecoder SOURCE_INCOMPLETE). Fixed; rerun owed.
- PermissionFlowDeviceTest FAIL both, test error: phase b waited for the tabs, but the app's start-up permission
  request put Android's dialog on top (GrantPermissionsActivity resumed). Test now closes that dialog with Back
  and logs it; rerun owed.

## 43884803041 (fe643e2, api33-lowram + api36, emulator) — build failure, no test ran

Both jobs failed in "Build APKs" (`assembleMobileDebugAndroidTest`): 82 `Unresolved reference` errors in
`LowStorageDeviceTest.kt` (62) and `NetworkStateDeviceTest.kt` (20). Cause: both imported the OkHttp 4 package
`okhttp3.mockwebserver`, but the androidTest dependency is `com.squareup.okhttp3:mockwebserver3:5.5.1`
(package `mockwebserver3`, `MockResponse.Builder`). Fixed in 7929bf9 (API checked against the published
5.5.1 sources). Root cause of the late discovery: CI never compiled androidTest; 677cefa adds
`compileMobileDebugAndroidTestKotlin` to CI. No G10 or G9 result comes from this run.

## CI 43884797613 (fe643e2, CI)

Build, unit tests, lint, secret scan all green. Unit: **1086 tests, 0 failures, 0 errors, 11 skipped**
(read from the `reports` artifact). Lint: 0 errors on mobileDebug and tvDebug. DL-11a:
`THROUGHPUT engine=single-no-ranges … requests=1 served=25165824 servedOverSize=1.000` on both profiles
(before, CI 43877618176: requests=2, 1.132×). ER-15 compiles. gitleaks: pass.

## Upgrade check 43881788009 (v1.8.0 → f214c7c, emulator api33) — INCOMPLETE, harness errors found

`UPGRADE_RESULT INCOMPLETE_NOT_PLAYED compare=0 pre_ok=1 played=0 guide_again=0 clip_seen=0 fatal=0`.
Read from the `upgrade-check` artifact:
- Data: 36 → 40 files, **0 lost**, 1 changed (`mt_updater last_check`). The old app saved
  `pos_external:file:///sdcard/Movies/upgrade-clip.mp4 = 48147`, its `watched_` time and a `play_counts`
  entry; all three are byte-identical after the upgrade.
- `played=0` was wrong: `shot-old-player.png` shows the clip playing (test-pattern counter at 35 s); the
  check looked for the clip name in a UI dump of a player with hidden controls (no text).
- `fatal=0` was blind: `logcat.txt` had **0 lines** — logcat was started before `adb root`, whose adbd
  restart killed it. The same was true of every earlier upgrade run's crash check.
- `clip_seen=0` could never pass: a clip opened from another app is stored under an `external:` id and
  Home/History list library videos only (same in v1.8.0).
- Fix fa3bb3b: logcat after root (verdict `ERROR_NO_LOGCAT` under 100 lines); played = saved position in
  the old snapshot; new resume check (reopen in the new app; position kept by the upgrade and the reopened
  position past it, else `FAIL_NOT_RESUMED`); clip 60 → 180 s. Rerun owed. X10-5 stays open.

## 43891331959 (c306e7e, api33-lowram + api36, emulator)

Read per class and phone from the job log (REPEAT_RESULT and the class's own *_RESULT lines).

| Class | api33-lowram | api36 | Notes |
|---|---|---|---|
| NetworkStateDeviceTest | PASS (NET a, b, c) | FAIL a | api36: `online signal stayed true 20 s after Wi-Fi and data went off` (NET_CASE offline signal=false after 20157 ms, error UnknownHostException). App defect → ER-17, fixed 8038761. b, c PASS; vpn case logged active=false (no VPN on CI) |
| PermissionFlowDeviceTest | FAIL | FAIL | Test errors: api33 shell `uiautomator dump` crashed (Bad file descriptor) while GrantPermissionsActivity was on top; api36 the Privacy category tap did not open the page. Fixed 37711ec |
| DialogsDeviceTest | PASS (checked=5) | PASS (checked=5) | |
| LowStorageDeviceTest | a PASS (requests=1), b skipped | a PASS (requests=1), b skipped | b: fill never ran (`; echo rc=$?` passed to fallocate, no shell). Fixed 523af6f |
| MessagesDeviceTest | FAIL (1 of 100) | PASS (0 of 100) | api33: PermissionCenter.kt:75 blocked message cut at 3 lines → ER-18, fixed f6881aa |
| BackdropImportDeviceTest | PASS (problems=0) | PASS (problems=0) | SE-4 proven |
| BackgroundSettingsEffectDeviceTest | PASS a–e | PASS a–e | e: Firebase status UNAVAILABLE in this build (no google-services.json), the test accepts that |

Rerun owed for the failed classes only, together with the new G9 classes, after CI 43894661940.

## Upgrade 43891389012 (v1.8.0 → c306e7e, api33-lowram, emulator)

`UPGRADE_RESULT PASS compare=0 pre_ok=1 played=1 guide_again=0 resumed=1 pos=46618/46618/54974 fatal=0 logcat=62948`.
Files 36 → 40 (new ones are profile/oat caches); every pref file has the same key count; JSON stores contained
byte-for-byte. The test clip's saved position was 46,618 ms before and after the upgrade, and reopening it in the
new build resumed and reached 54,974 ms (shot-new-player.png shows the clip playing). Logcat 62,948 lines, no
FATAL EXCEPTION or ANR. First run with the logcat fix, so the first non-blind crash check. X10-5 closed on emulator.

## 43902887573 (1707ced) — never started

"Concurrency group name must be less than 400 characters" (eight test classes in the group name). Fixed 35dc0c6;
the same batch re-dispatched as 43904330191.

## 43902905156 (1707ced, tv-api34 = Android TV 14 image, tv_1080p, Yumeiro TV flavour, emulator)

`TvBug5ReproDeviceTest` (BUG-5 reproduction, D-pad only): Tests run 4, failures 2.

| Case | Result | Log |
|---|---|---|
| a TV-5 player layout | FAIL (reproduced) | `requested=USER_PORTRAIT configOrientation=1 sw=304dp player=608x1080 window=608x1080`; tvb_a_player.png: pillarboxed portrait phone watch page |
| b TV-2 full-screen button | PASS | Up ×5 stays on `video_quality_btn`; Right ×4; Down → `video_fullscreen_btn` |
| c TV-3 Home row | PASS | Right ×14 stays on page 0; Downloads (row 2) → Right → `quick_card_ytmusic` (row 1) |
| d TV-4 search | FAIL (reproduced in part) | Up from `home_setup_card` focuses no tagged node, never `topbar_search_action`; Left → rail |

No FATAL EXCEPTION, no ANR. Details and fix plan: docs/proposals/FEATURE-BUG-5-tv-ui.md.

## 43904330191 (35dc0c6, api33-lowram + api36, emulator)

| Class | api33-lowram | api36 | Notes |
|---|---|---|---|
| NetworkStateDeviceTest | PASS a, b, c | PASS a, b, c | ER-17 fix proven (api36 a failed before) |
| PermissionFlowDeviceTest | PASS | PASS | deny1 INFO in app; deny2 "Open settings" → AppNotificationSettingsActivity (ER-11) |
| LowStorageDeviceTest | PASS a, b | PASS a, b | fill 4770 → 15 MB / 4450 → 15 MB free; b FAILED "Storage full", app alive, requests=1 (ER-14) |
| MessagesDeviceTest | PASS (0 of 100) | PASS (0 of 100) | ER-18 fix proven on api33 (failed before) |
| MoreSettingsEffectDeviceTest | PASS a–d | PASS a–d | backup_auto, keep_default, adult_private, yt_to_phone |
| PlayerExtrasSettingsEffectDeviceTest | PASS a, b1, b2, c1, c2 | PASS a, b1, b2, c1, c2 | cc_default, bg_play, auto_pip |
| OnlineSwitchesEffectDeviceTest | PASS a–c | PASS a–c | lyrics_online, skip_intro, auto_skip |
| HeroCarouselLifecycleDeviceTest | FAIL | FAIL | Test error: slide A after 9 s of mainClock time; delay() is real time in instrumented tests. Real-time test next |

## 43908926085 (c41f530, tv-api34, emulator) — TvBug5ReproDeviceTest

- a TV-5 PASS: requested UNSPECIFIED, config landscape, sw 540 dp, player 1920x1080@0,0 = window, no fullscreen
  button, focus on video_play_pause_btn. TV-5 fix 4e5ff31 proven on this emulator.
- b FAIL: Up reached video_audio_track_btn and the More button (top row), then Down moved focus to
  home_setup_card, quick_card_ytmusic … behind the player (screenshot tvb_b_walk: player still shown, no focus
  ring). Root cause: the player overlay was not a closed focus group. Fix c07bc70.
- c TV-3 PASS (14 × Right along "Next episodes", page stayed 0).
- d FAIL (test): Up from the page focused node1573 [1712,22,1792,102], inside topbar_search_action
  [1708,18,1796,106]; focused() read only the node's own tag. Test fixed to read ancestor tags.

## 43912650347 (8509d1d, api33-lowram + api36, emulator) — MusicTapThrough (before the fix) + HeroCarousel

- BUG-6 reproduced on both: TAP_CASE quick_card_hues → homeStill=false (Manga page opened under Now
  Playing); quick_card_youtube → page 2 (Stream). home_setup_* taps did not leak. TAP_RESULT FAIL.
- Y6/H1: CREATED 14 s → still on slide A (both PASS). RESUMED: api36 moved after 14,381 ms (PASS);
  api33-lowram moved=false after 15,016 ms (FAIL on the 15 s window).

## 43912693329 (8509d1d, tv-api34, emulator) — TvBug5ReproDeviceTest, all 4 PASS

- a TV-5 PASS (landscape, 1920x1080 full window). b TV-2 PASS: Up → video_audio_track_btn, Down →
  video_play_pause_btn → video_seekbar, focus never behind the player (fix c07bc70).
- c TV-3 PASS (Spaces grid, page 0). d: Up from home_setup_card → magnifier; box opened, but the
  screenshot shows an empty box and the rail expanded: the typed text never arrived (fix 18147da).


## 43914724044 (phones, both; emulator)

- MusicTapThroughDeviceTest PASS on api33-lowram and api36, leaked=0 (BUG-6 after c8ac2ae; failed in 43912650347).
- HeroCarouselDeviceTest PASS: api36 12,952 ms, api33-lowram 17,019 ms, heat COOL (Y6/H1).
- PushAlertsEffectDeviceTest: api33-lowram a/b/c PASS (ntfy schedule accepted, pending=9). api36 a FAIL: logcat
  "There was a distributor, but it isn't installed anymore" → onUnregistered cleared the endpoint. Test error, fixed 6551e71.

## 43914880052 (tv-api34; emulator)

- a TV-5, b TV-2, c TV-3, d TV-4 focus: all PASS. d: start quick_card_downloads, Up×3 → magnifier, inputFocused=true,
  typed='naruto'. After Enter Home is unchanged (library-only search, empty library) and focus moves to the rail.

## 43920651380 (phones, both; emulator; head 2507429)

- PushAlertsEffectDeviceTest a/b/c PASS on api33-lowram and api36 (SE-5 16/16).
- StreamFormatMismatchDeviceTest: all 5 cases on both phones ended in ERROR_CODE_PARSING_MANIFEST_MALFORMED shown as
  "Unsupported stream format"; maxLag api36 94–697 ms, api33 295–1,548 ms (one FMT_STALL: setRequestedOrientation
  binder, 1,512 ms). Failed only on closed=false: Back leaves to the floating window by design (test error).
- UpscaleFreezeDeviceTest: Anime4K on and the "Upscaled by Yumeiro" level both kept playing (pos +4.0 to +4.6 s),
  maxLag ≤ 846 ms. Shots showed 00:00 and an open Quality list because SystemClock.sleep stops the Compose test
  clock (no recomposition), not an app freeze. Tests rewritten with UiLiveness (pump, gfxinfo frames, real taps).

## 43935805199 (phones, both; emulator)

- StreamWatchDownloadDeviceTest a (7a) PASS on both: watch-page Download button, queued, done, saved.
  7b PASS on both: publicUri in Movies/Yumeiro, inStore=true, appCopy=false, keep=true.
- StreamFormatMismatchDeviceTest FAIL on both: all 5 cases end with "Unsupported stream format"
  (ERROR_CODE_PARSING_MANIFEST_MALFORMED), maxLag < 1.8 s, but 3 Back presses leave the player on screen
  (left=false, floating=false). Logcat: back callback cancelled and re-registered on each press. Cause: Back
  minimized, then LaunchedEffect(playerError, minimized) expanded it again. Fixed with PlayerBackRule.leave.
- UpscaleFreezeDeviceTest: upscaled_level and plain_control PASS on both. anime4k_on failed only on the More-tap
  check (api36 tapped=false, controls had auto-hidden; api33 tapped=true, no menu yet). Test timing, fixed.

## 43935810414 (tv-api34; emulator)

- TvBug5ReproDeviceTest a, b, c, d PASS. d: focusAfterSubmit=topbar_search_input, answered=true.

## 43946324687 (phones, both; emulator; head f3f8914)

- StreamFormatMismatchDeviceTest 5/5 PASS on both: readable error, backPresses=1 left=true closed=true,
  maxLag 196–1,361 ms. Fail before: 43935805199 (Back ignored). BUG-7 7c verified on device (emulator).
- UpscaleFreezeDeviceTest 3/3 PASS on both: anime4k_on, upscaled_level, plain_control; position and time
  label move, frames drawn, More opens on a real tap.

## 43949571344 (tv-api24 = Android TV 7, emulator; head c92870e)

- TvBug5ReproDeviceTest a TV-5 PASS (1920x1080 full window, no fullscreen button), b TV-2 PASS (top and bottom
  reached, focus stays in the player), c TV-3 PASS (Home pager stays on page 0, no YouTube Music opened).
- d TV-4: Up×3 reaches the magnifier, box opens focused, typed 'naruto'; after Enter answered=false. The Leanback
  keyboard was open and took the Enter key (shot tvb_d_search). Test now sends the IME search action (what the
  keyboard's SEARCH key sends) when Enter is eaten; rerun owed.
- 43946842943 (same inputs, 3be5e5f) sat "queued" with no jobs for 40 min and stayed queued after cancel: ghost,
  replaced by 43949571344.

## 43957029647 (tv-api24 = Android TV 7, emulator)

- TvBug5ReproDeviceTest a–d PASS 4/4. d: path=ime_search, answered=true, focus after submit stays in
  topbar_search_input. TV-4 focus+answer now verified on both TV images.

## 43957024165 (api33-lowram + api36, emulator; head 07c…)

- ChoiceSettingsEffectDeviceTest: a speed (1.5 → 1.5, normal → 1.0), c renderer (surface/texture), d decoder
  (c2.android.avc.decoder / c2.goldfish.h264.decoder), e default player ASK/Yumeiro, f backup period 3 d / 7 d: PASS
  on both phones.
- b quality FAIL on both phones in the close step: one tap on floating_close, the floating window stayed 15 s and the
  clip played to ENDED (shot cfx_fail_playerclosed). a and c closed with the same helper. Diagnostic run 43959524193.

## 43967676359 (api33-lowram + api36, emulator; head 8f69146, test only, fix not in)

- FloatingMessageDeviceTest FAIL on both phones, as expected (fail-before for ST-21): the message pill
  (api33 44,1565–1036,1741) lay over the floating window (442,1539–1047,1879) and its close (942,1550–1036,1644).

## 43974089878 (api33-lowram + api36, emulator; head 1cd184a, ST-21 fix)

- FloatingMessageDeviceTest PASS both: message now ends above the window (api33 bottom 1519 < top 1539; api36 1715 < 1734).
- ChoiceSettingsEffectDeviceTest a–f PASS 6/6 both. b: 480p → maxVideoHeight=480, Auto → 2147483647, closeTap#1 closed
  the player. 0 FATAL / ANR lines in logcat.

## 43984064564 (api33-lowram + api36, emulator; head 6bf399a)

- RetryActionsDeviceTest a (JS repo) and b (Stremio add-on) PASS both: 404 → "Not found (404)" + Retry → call repeated → added.

## 43985787392 (both phones, emulator; head af4f791)

- a, b, c (Mihon repo) PASS; d (WebDAV form) FAIL both: the reason pill sat behind the open dialog and the
  keyboard (shot rty_d_error); the real tap at Retry hit the keyboard. New finding ER-19 (fail-before).

## 43987237062 cancelled (superseded by 8011813: same class plus case e)

## 43987451529 (both phones, emulator; head 8011813)

- RetryActionsDeviceTest a–e PASS 5/5 both. d: "Not found (404)" inside the form, button Retry, real tap → connected.
  e: Kitsu "Wrong e-mail or password" inside the form, no bottom message. 0 FATAL lines.

## 43988958313 (stress 2 × 5 per phone, emulator, debug build; head 2e86f58)

- 10/10 jobs, 20/20 runs PASS. FATAL 0. App ANR 0 (api36 had 3 ANRs in com.google.android.gms[.persistent]).
- Test counter, stalls ≥700 ms: api33-lowram 13 in 10 runs (1.3), api36 19 in 10 (1.9); max per run 1.33 s / 1.52 s.
  Same counter on 43705867413: 0.7 / 1.0. Bursts during system load; stacks as before (HardwareRenderer).
- ST-17: 0 spin lines.

## 44002497794 (LibraryPersistDeviceTest, both phones, emulator; head 1502b1b)

- Phase a saves through OfflineMediaRepository, adds a broken entry, kills its own process; phase b runs in a new process.
- api33-lowram (pid 4576 → 5227) and api36 (3334 → 4070): `LIB_P_B playlists=[Favorite Tracks:3, YuLB repeats:2, YuLB keep:2]`,
  after one more save `[Favorite Tracks, YuLB repeats, YuLB keep 2]`, LIB_P_RESULT PASS, cleanup back to `[Favorite Tracks:0]`.

## 44002112195 (FrameTimingBenchmark, bench=jank, both phones, emulator; head 2e8d810)

- First valid journey: journey_after_scroll.png shows Home; 139–171 frames per iteration.
- frameDurationCpuMs P50/P90/P99, shipped profile vs no AOT: api36 400/510/952 vs 388/482/831; api33-lowram
  434/520/800 vs 412/476/702. No profile gain; the emulators draw in software (ST-7, OMT-12 for the phone).

## 44004098352 (LibraryRotationDeviceTest fail-before, scratch/lb7-fail-before, both phones, emulator)

- `remember` instead of `rememberSaveable`: FAILED on both, "open before=true after=false overview=true".

## 44005396278 (LibraryRotationDeviceTest, both phones, emulator; head c9c41d3)

- PASS on both: the open playlist survives recreation (LB-7 rotation part).

## 44007087901 (MangaLibraryMainThreadDeviceTest fail-before, both phones, emulator; head cb3593a)

- FAILED on both: 40 main-thread disk reads from MangaLibrary in 20 page turns (ContextImpl.ensurePrivateDirExists
  via filesDir, and File.mkdirs in MangaLibrary.file). LB-2.

## 44008161115 (MangaLibraryMainThreadDeviceTest after fix, both phones, emulator; head e059aed)

- PASS on both: `MANGA_MAIN violations from MangaLibrary: 0 (all: 0)`. LB-2.

## 44005414732 (BaselineProfileGenerator, bench=profile, api36, emulator; head c9c41d3)

- `startupAndTabs-startup-prof.txt`: 44,252 rules, 6,166 app rules; Home 470, Library 71, Online Home 296
  (shipped fef01c6: 27,827 / 3,499 / 295 / 0 / 6). Committed as app/src/main/baseline-prof.txt in 4989344 (X2-2).

## 44009339786 (LibraryTabsMainThreadDeviceTest fail-before, scratch/lb7tabs-fail-before, both phones, emulator)

- FAILED on both: `LIB_TABS violations from the stores: 10`. LB-7 tabs part.

## 44010510836 (LibraryTabsMainThreadDeviceTest, both phones, emulator; head 17358f9)

- PASS on both: `LIB_TABS violations from the stores: 0 (all: 0)`.

## 44011888158 (MusicQueueDeviceTest, scratch/mu4-fail-before) — void

- Failed on both at the test's own log line (Player accessed on the wrong thread). Test bug, fixed in b4d5033.

## 44013418634 (MusicQueueDeviceTest fail-before, scratch/mu4-fail-before, both phones, emulator)

- FAILED on both: `MUQ reopen track=null index=-1 queue=[] playing=true` (a119186 reverted). MU-4.

## 44014503279 (MusicQueueDeviceTest, both phones, emulator; head 7046dc0)

- PASS on both: reopen track=yuq2 index=1 queue=[yuq1, yuq2, yuq3]; Play next → [yuq1, yuq2, yuq4, yuq3], index 1, playing.

## 44010598355 (CI: build, unit, lint; head 4989344)

- Success. Covers e059aed (LB-2) and the new baseline profile.

## 44015384846 (LargePlaylistMemoryDeviceTest, both phones, emulator; head 75b2124)

- 16.3 MB playlist, 52,000 channels on both paths. Heap peak above start, whole-file vs line-by-line (lower of two):
  api33-lowram 183 vs 42 MB (heap 192 MB), api36 73 vs 29 MB. PASS. LT-3.

## 44016666847 (RemotePlaylistMemoryDeviceTest fail-before, both phones, emulator; head 4928d46)

- Same 16.3 MB list served by a local server. api33-lowram: add 114 MB, refresh 147 MB, read 56 MB; api36: add 105,
  refresh 71, read 41. FAILED both (limit half the heap, 96 MB). LT-4.

## 44017550181 (RemotePlaylistMemoryDeviceTest after fix, both phones, emulator; head 8b747ca)

- api36 add 31 MB, refresh 33 MB PASS. api33-lowram add 105, refresh 131 MB FAILED. The first step ran 4 s after
  process start, while the app's own start-up work allocates in the same process, so the peak counted it. The
  fail-before numbers of 44016666847 carry the same noise (its api36 105 MB too, see below). Test hardened c318e9a.

## 44018872516 (RemotePlaylistMemoryDeviceTest hardened, fail-before, both phones, emulator; scratch/lt4-fail-before b414e22)

- Old PlaylistStore/M3uParser (4928d46). 12 s settle, lower of two peaks. api33-lowram add 132/133 MB FAILED
  (limit 96), refresh 72/66; api36 add 54/56, refresh 61/61 (under the limit there). LT-4.

## 44018869252 (RemotePlaylistMemoryDeviceTest hardened, after fix, both phones, emulator; head c318e9a)

- api33-lowram add 42/76 → 42 MB, refresh 69/65 → 65 MB, read 68; api36 add 34/32 → 32, refresh 31/27 → 27. PASS
  both. LT-4.

## 44013773673 (bench=startup with the 4989344 profile, both phones, emulator)

- TTID median: api36 coldStartBaselineProfile 2116 ms (2018–2325) vs NoCompilation 2957 ms (1997–3929);
  api33-lowram (attempt 2, after a Maven resolution failure) 1756 ms (1659–1888) vs 2465 ms (1943–3570). X2-4.

## 44020227401 / 44020231250 (PlaylistRetryDeviceTest, both phones, emulator; heads fcb4be5 and scratch/lt2-fail-before)

- Case b (download 503 → Retry) PASS on both builds. Case a (store start fails) FAILED on both builds: "Playlist not
  found" shown instead of the error. The 4cee17d fix never worked (defaults loaded in memory, loadedDir set before
  the failed write). Fix bd18c0b. LT-2.

## 44021837402 (PlaylistRetryDeviceTest after bd18c0b, both phones, emulator)

- Both cases PASS. Screenshot: raw "/data/user/0/…/playlists.json.tmp: open failed: EACCES" and "Check your connection" shown.

## 44023164202 (PlaylistRetryDeviceTest after 1060ea7, both phones, emulator)

- Both cases PASS with the text checks; screenshot reads "The app couldn't use its storage on this device. Restart the
  app, then try again. Your saved playlists are not changed." LT-2.

## 44024309052 / 44024330486 (MangaRetryDeviceTest double Retry, both phones, emulator; scratch/mg1-fail-before and head)

- Skipped by assumption on both builds and both phones: Retry gone at frame 0 of the first retry, so no second tap can
  land. MG-1 Retry race not reachable from the screen.

## 44025493021 / 44025490015 (MangaRetryDeviceTest.libraryLoadIsOffMain, both phones, emulator)

- Fail-before (library loaded on main, as before 4cee17d): 5 MangaLibrary disk reads on main, both phones, FAILED.
  Head: 0, PASS both. MG-1.

## 44020023897 / 44023167122 (CI: build, unit, lint; heads c318e9a and 1060ea7)

- Success. RemotePlaylistStreamTest (LT-4); ErrorTaxonomyTest storage cases (LT-2).

## 44026779856 (PlaylistIndexRecoveryDeviceTest, both phones, emulator)

- a: torn index + complete .tmp → playlist recovered. b: torn index, no .tmp → .bad holds the torn bytes (309 = 309).
  PASS both. LT-1.

## 44026316670 / 44028308675 (CI unit; head 7eac485 and scratch/l1-fail-before)

- Head: success. Fail-before: 1096 tests, 1 failed (OnlineHomeFailureTest.everyPageFailingPassesTheReasonOn). G7-L1.


## Device 44030612565 / 44031868134: DownloadThroughputDeviceTest#c_localBurstThenCapHost (emulator, BUG-3)

Local server in the test process, 40 MiB, every request 1 MiB fast then 1 MiB/s; real WorkManager →
DownloadWorker → Segmented. Rule: last 10 % ≤ 2× the mean 10 %.

| Build | Phone | Total | MB/s | Last / mean | Requests | Result |
|---|---|---|---|---|---|---|
| head 44030612565 | api36 | 9481 ms | 4.22 | 0.74 | 24 | PASS |
| head 44030612565 | api33-lowram | 6645 ms | 6.02 | 1.08 | 31 | PASS |
| fail-before 44031868134 (`bug3-fail-before`, ForceGrab from 40ea96e^) | api36 | 14944 ms | 2.68 | 1.21 | 11 | PASS |
| fail-before 44031868134 | api33-lowram | 15053 ms | 2.66 | 1.27 | 11 | PASS |

Reading: this profile does not reproduce the tail collapse (the old code also meets the rule), so it is not
fail-before proof of the tail fix. It shows the RequestPace restarts on a device: 1.6–2.3× faster on a
per-request capped host. Case d (the unit collapse profile) added for the tail.

## Device 44033132233 / 44034718516 / 44036060683 / 44037723917: G8 downloads (emulator, api36 + api33-lowram)

- DL-4 `DownloadResumeDeviceTest` c (206 from byte 0 to a resume Range) and d (416 with another size):
  head PASS both phones (44033132233, 44034718516). Fail-before `g8-fail-before` 44037723917: c saved
  400 000 bytes for a 300 000-byte file, d finished DONE with the old 250 000 bytes (server file 200 000).
  On that scratch build api36 also timed out on b (QUEUED, 0 bytes, 60 s); b passed on head every time.
- R16 HLS resume (case a): failed on head 44033132233 (test waited for 8 segments in the list; ProgressGate
  showed 7 while segment 8 was held). Test fixed; PASS 44034718516.
- DL-8 `DownloadQualityDeviceTest`: head 44036060683 q720→720p, q480→480p, q360→480p, Best→1080p on both;
  fail-before 44037723917 → 1080p for every setting.
- DL-10 `DownloadKillDeviceTest`: 44033132233 api36 read 121.0 MB after the kill for 87.9 MB left (FAIL,
  rule rest + 24 MiB); 44034718516 PASS both (api36 93.5/87.3 MB with 1 pace restart; lowram 115.0/92.3 MB
  with 9). Both emulators run 2 connections (DeviceClass.lowEnd: 4 cores or fewer).
- DL-11 real host (cdn.kernel.org, 134.7 MB, screen on), before 6e40982: 44036060683 lowram 7 restarts
  rx 1.129×, api36 1 restart 1.053×; repeat 44037559115: lowram 29/1/6 restarts → rx 1.440/1.039/1.130×
  (3.58/7.82/5.86 MB/s), api36 11/3/1 → 1.163/1.060/1.051× (7.28/10.55/11.23 MB/s).
- BUG-3 case d old Segmented (44037723917, 24 MiB, 3 MiB burst then 150 KiB/s per request): 19.99 s
  (1.20 MB/s) on both, last/mean 3.24 and 3.14; the unit slowTail peak rule still passes it (last 6.5 s
  vs an earlier 5.5 s tenth). Head numbers in 44039053465.

## 2026-11-17: BUG-3 case d head, DL-10 kept-chunks rule, ADR-016 data, G8 cleanup (emulator)

- **44039053465** (head 0acf8c3, still with the two-window RequestPace 6e40982, reverted in f60d808), repeat 3:
  - Real host screen-on: lowram 0/1/5 restarts, rx 1.031-1.092×, 15.0-24.5 MB/s; api36 0/0/1 restarts, rx 1.031-1.035×, 9.8-14.2 MB/s.
  - Case d: 6.6-13.6 s, last/mean 2.37-4.93.
  - The unit regression (CI 44037788044) stands, so the revert stands. ADR-016 input only.
- **44041714032** (head 62b3a45, one-window RequestPace), repeat 3:
  - Real host: lowram 13/7/0 restarts, rx 1.243/1.136/1.031×, 6.1-15.7 MB/s; api36 15/0/0 restarts, rx 1.186/1.031/1.031×, 5.4-17.5 MB/s; last/mean 0.62-0.82.
  - Case d: lowram 3.4-5.0 s, api36 5.0-5.2 s, last 10 % 1.4-1.6 s, ratio 3.0-4.3. Old Segmented 44037723917 took 19.99 s, with the last 10 % at 6.3/6.5 s.
  - slowTail failed api36 run 1 at 3.07, so the case d rule changed (see the BUG-3 ledger row).
  - DownloadKillDeviceTest kept-chunks rule: 6/6 PASS (resumed 12-17 chunks).
- **44041881451** DownloadCleanupDeviceTest (G8 series folders, auto-delete, Permanent vs Offline):
  - Files are in the series folder on both phones; +1 h removes nothing; +2 days removes the Offline one (entry and file) and keeps the Permanent one byte-exact; the empty folder goes after the last delete.
  - lowram PASS. api36 FAIL only on "dl_cleanup scheduled": it was read 9 s after process start, and WorkManager came up 5 s after start on the low-priority yu-startup thread. The test now polls for up to 30 s; rerun 44043535616.
- **44043535616** (head 491b4e3, repeat 3) BUG-3 case d with the absolute rule + DownloadCleanupDeviceTest. Read 2026-11-17 by the seventh-handover agent:
  - `d_localUnitCollapseProfile` 6/6 PASS. api36 totalMs 7179 / 6390 / 5237, last 10 % 1206 / 507 / 100 ms; api33-lowram 5170 / 4041 / 3418, last 10 % 1203 / 201 / 1706 ms. Rule: total ≤ 10 s, last 10 % ≤ 3.5 s. Received 1.092–1.293× the file (DL-11 part 2).
  - `DownloadCleanupDeviceTest` 6/6 PASS (`dl_cleanup` ENQUEUED or RUNNING within the 30 s poll on both phones; empty folder removed).

## 2026-11-17: Group 11 integrations (emulator)

- **44049031398** (head 5a46d98, `TrackerSignInDeviceTest`, both phones): 4/4 PASS. Trakt and Simkl sign-in started on the main thread under penaltyDeathOnNetwork finish (`result=ig1-user`, 2 polls) and every request ran on `DefaultDispatcher-worker-*` (no `main:` entry). IG-1 after.
- **44049036117** (`scratch/ig1-fail-before` ab93f76, old code + the test, api33-lowram): FAIL as expected. Trakt and Simkl: `error=NetworkOnMainThreadException polls=0 threads=[main:main]`. IG-1 fail-before.
- **44050521996** (head 14a367f, api33-lowram): AnimeTrackers a/b/c, ScrobbleFlush, EpisodeStills a/b PASS (MAL and Kitsu refreshed; Trakt/Simkl once each; Simkl capped at 3 tries; flush on a worker thread).
- **44050574003** (`scratch/ig2-fail-before`, old code, api33-lowram): FAIL in every case (a: old tokens, trakt=0; b: trakt=0 simkl=0, Kitsu called 4 times; c: Simkl tried once and the update lost; flush on main, requests=0; stills null, no retry). IG-2…IG-5 fail-before.
- **44051808801** (head f0fa55d, api36): all six G11 tests PASS (ANIME_TRK a/b/c, SCROBBLE_FLUSH bg thread, STILLS a/b, CLOUD cached=4242 after a failed first sync with cached=null, SUBS fallback n=1 + titleOnly 429, SIGNIN rejected "AniList sign-in failed. You are still signed in as OldUser").
- **44052308373** (`scratch/ig2-fail-before` 41b8c0a, old code, api33-lowram): FAIL as expected. CLOUD first sync cached=0 (failure stored as "not anime"); SUBS fallback ok=false (hash hit lost on 429); SIGNIN rejected msg "Signed in to AniList as OldUser". Control cases (SIGNIN accepted, SUBS titleOnly) PASS on old code, as designed. IG-6…IG-8 fail-before.
- **44053272178** (head a501d8e, api33-lowram): build failed, no test ran. `compileMobileDebugAndroidTestKotlin`: "Unresolved reference 'and'" in DownloadsScreenDeviceTest.kt:13 (my import; `and` is a SemanticsMatcher member). Fixed ee1808c.
- **44053590474** (`scratch/d8-fail-before`): cancelled by the agent while building; the same file would fail the same compile. Re-dispatched after the fix.
- **44054360131** (head ee1808c, api33-lowram): 8/9 PASS. YTDL a/b, CHECK_RESULT PASS (BrowserCheckCooldown), RESET a/b, CLOUD, SUBS fallback/titleOnly, SIGNIN accepted/rejected, EXTDL a–d, TORRENT a/b. IG-6…IG-8 after-fix on api33-lowram (with 44051808801 api36: verified, both phones). DownloadsScreenDeviceTest: a_open `main-thread disk calls from downloads code: 0`, then ComposeTimeoutException at DownloadsScreenDeviceTest.kt:109 — after opening the folder the two episode rows were never composed (storage + tips cards push them below the fold of the 2 GB Pixel 3a; lazy grid). Test fault; fixed e71a041 (scroll + tips hidden).
- **44054365230** (`scratch/d8-fail-before`, old code, api33-lowram): FAIL as expected, `a_open: 6 main-thread disk calls from the downloads code`. DL-12 fail-before.
- **44056147165** (`scratch/a5-fail-before` dfb48ef): build failed, no test ran: the same `import androidx.compose.ui.test.and` (dfb48ef predates ee1808c). Fixed on the branch (import removed); re-dispatched as 44057033821.
- In progress: **44056138637** (head e71a041, api33-lowram: DownloadsScreen, UniversalAddEcosystems (new, G2 E10), LocalPlayer#j swipe gestures, Yumix#extensionBarHidesOnScrollAndComesBack) and **44057033821** (`scratch/a5-fail-before`, BrowserCheckCooldown on old code; expect ≈60 s > 5 s limit).

| 44056138637 | r15-stability e71a041 | api33-lowram | DownloadsScreen, UniversalAddEcosystems, LocalPlayer#j, Yumix#extensionBarHidesOnScrollAndComesBack | 2/4: LocalPlayer j PASS, Y3 PASS. DownloadsScreen: a_open 0 main-thread disk calls, open/play PASS, then test fault (delete button looked up as a sibling of the title; it is a child of the folder row). UniversalAdd: Aniyomi/Mihon/Stremio search PASS, then test fault (loadLinks given the search URL instead of load()'s dataUrl). Both fixed in the next commit |
| 44057033821 | scratch/a5-fail-before | api33-lowram | BrowserCheckCooldown (old code) | FAILED as expected: 3 next requests 60600 ms (ER-20 fail-before) |
| 44162062169 | r15-stability 5f7f333 | api33-lowram | ExtensionsDeviceTest repeat 10 (EXT-T1) | 10/10 PASS (OK 7 tests each), anr-list empty; EXT_T1 line: focused MainActivity, added=[] every run. Not reproduced; condition (five failing drama feeds) not seeded |
