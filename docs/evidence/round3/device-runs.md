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
