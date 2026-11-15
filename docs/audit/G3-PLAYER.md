# Group 3 audit: Player (R16)

Branch `r15-stability`, commits `2744a23` … (see Fixes). Devices: CI emulators only:
**api33-lowram** (Android 13, Pixel 3a profile, 2 GB RAM, 192 MB heap) and **api36** (Android 16,
Pixel 6 profile), both `google_apis` images (Play Services present). These are emulators, not
real phones or TVs. Casting to a real TV, real heat and real low-RAM phones are in
`docs/MANUAL-TESTS.md` (scripts P and T).

## Tested

| Inventory | What | How | Result |
|---|---|---|---|
| P1 | Local video playback | New `LocalPlayerDeviceTest`: a 12 s test clip (320×240 H.264, two AAC tracks eng/jpn, one mov_text subtitle) is put in Movies/YumeiroTest through MediaStore, found by the app's own scan and opened from Videos → Device | first frame at 320×240 on both emulators (43707028426, 43708220122) |
| P2 | Online playback session | `OnlinePlaybackDeviceTest` a–d (direct MP4, extension link, UI flow, dead manual pick); `HeaderStreamDeviceTest` (host refuses every request without the right Referer, playlist, segments and subtitle) | full suite 43701056608 pass; HeaderStream added to the default suite in this group (run in Verification) |
| P3 | YouTube playback | `PlayerSwitchDeviceTest c_` (every height in the quality list opens) | 43701056608 pass. Real YouTube steps on a phone: script Y |
| P4 | Drama playback | `DramaPlaybackDeviceTest`, `DramaResilienceTest` 16, `DramaHealthTest` 4, `YumeiroDramaTest` 9 | 43701056608 pass; unit pass |
| P5 | Quality list and switching | `QualityListDeviceTest`, `PlayerSwitchDeviceTest a_ b_`, `QualityParityTest` 3, `QualityRound2Test` 5 | 43701056608 pass |
| P6 | Audio tracks and languages | `LocalPlayerDeviceTest a`: the audio dialog lists exactly two tracks; picking the second switches the decoder's selected track (log `P6 audio: en → ja`); `PlayerSwitchDeviceTest d_ e_`; `AudioLanguagesTest` 6, `SubtitleAudioTest` 7 | 43708220122 pass on both |
| P7 | Subtitles (embedded, delay, off) | `LocalPlayerDeviceTest a`: embedded track listed and selected, +250 ms delay tapped, turned off (the text track is deselected in the player); `SubtitleSitesTest` 7, `OpenSubtitlesParamsTest` 2, `SubtitleStyleSettingsTest` 4, `SubtitleKeyScopeTest` 3 | 43708220122 pass on both. Online subtitle search is Group 11 (OpenSubtitles) |
| P8 | Speed, zoom, gestures, volume | `LocalPlayerDeviceTest a`: speed 1.5× and back to 1.0× read from `playbackParameters`; `VideoZoomTest` 4, `VolumeAccumulatorTest` 5 | 43708220122 pass; swipe gestures for volume and brightness are unit only |
| P9 | Source rotation, dead manual pick | `OnlinePlaybackDeviceTest d_`, `RecoveryStateTest` 5 | 43701056608 pass |
| P10 | PiP, floating window, lock | `LocalPlayerDeviceTest c` (PiP button → `isInPictureInPictureMode`, still playing), `a` (lock hides the controls, unlock returns them), `SurfaceModeDeviceTest` (View downloads → floating, new video → full screen) | 43708220122 c pass on both; 43701056608 SurfaceMode pass |
| P11 | Resume position | `LocalPlayerDeviceTest b`: seek to 7 s, close through the floating window, wait past the session grace, reopen; must resume near the left position or show the resume pill | see Verification |
| P12 | Controls, HUD, time labels | `LocalPlayerDeviceTest a` (play/pause through the button, read from `playWhenReady`); `DurationLabelTest` 3 | 43708220122 pass |
| P13 | Player settings | Unit: `ScreenWakeTest` 6, `SubtitleStyleSettingsTest` 4; control order and hidden controls through `VideoPrefs` (read in the code) | unit only |
| P14 | Recovery (black frame, stall, starvation) | `BlackFrameWatchdogTest` 5, `PlaybackRecoveryTest` 6, `RecoveryStateTest` 5, `StarvationLoadControlTest` 3 | unit only; a real decoder fault cannot be forced on the emulators |
| P15 | AniSkip intro skip, AniList | `OnlineExtrasTest.aniSkipParse`, new `AniSkipCacheTest` 2 | unit only (live API) |
| P16 | Anime4K effect | `Round13LogicTest.anime4k_tiers_grow`; engine levels in `PlayerSwitchDeviceTest a_` | unit; the shader output itself is not checked on device |
| P17 | Casting | New `LocalPlayerDeviceTest e`: the Cast module loads on the main thread without blocking; new `CastSubtitlesTest` | 43707028426 and 43708220122: `ready()` returned in 34–132 ms, Cast ready after 90–327 ms on both. Casting to a TV: script P/T |
| P18 | Screen wake (sleep timer: Group 6) | `LocalPlayerDeviceTest a`: a view keeps the screen on while playing, not while paused, on again after play; `ScreenWakeTest` 6 | 43708220122 pass on both. The sleep timer is only in the music player (`NowPlayingScreen`), so it is tested in Group 6 |
| P19 | Heat guard | New `LocalPlayerDeviceTest d`: battery saver on → WARM, off → COOL, `cmd thermalservice override-status 3` → HOT, reset → COOL; `HeatGuardTest` 5 | 43708220122 pass on both (`isPowerSaveMode=true`, `reached HOT: true`) |

## Problems found

1. **Cast was off for the whole process after the first check (app, P17).**
   `CastSupport.get()` read `.result` on the Task from
   `CastContext.getSharedInstance(context, executor)` right away. That Task completes later on the
   executor, so `.result` throws "Task is not yet complete" while the module loads, and the catch
   then marked Cast unavailable for good. The Cast button could stay hidden all session.
2. **Subtitles never reached the TV for SRT sources (app, P17).** SRT tracks were sent as
   `application/x-subrip`. Cast receivers show TTML, WebVTT and CEA-608/708 only
   ([Supported Media for Google Cast](https://developers.google.com/cast/docs/media)).
3. **A refused picture-in-picture request did nothing (app, P10).** The button ignored the result
   of `PipController.enter`, so the user saw no change and no message.
4. **AniSkip cached a failed lookup as "no segments" (app, P15).** A timeout or no network
   stored an empty list for the rest of the process, so intro skip stayed off for that episode
   until the app restarted.
5. **Hidden controls on a portrait phone (design, P8/P10).** The top control row scrolls
   sideways. On a portrait phone Speed and PiP are off-screen, and nothing shows that the row
   scrolls. This was found because the test's tap missed them.
6. **Two or three Back presses to leave the player (design, P10).** A key Back first hides
   the controls (remote behaviour, also after any key event on a phone), then leaves full screen,
   then moves to the floating window. A gesture Back skips the first step. This is how the code
   is meant to work, but on a phone it is more steps than YouTube (one Back to leave full screen).
7. **HeaderStreamDeviceTest was not in the default device suite (test gap, P2).** Added.
8. **Test errors along the way (43707028426):** the 12 s clip ended mid-test (now looped); the
   floating window was checked before it appeared; battery saver cannot turn on while the
   emulator reports it is charging (`dumpsys battery unplug` first); off-screen controls (now
   scrolled into view as a person would).
9. Checked, not a bug: casting a source that needs a Referer or cookies (Chromecast does not
   send them) already shows a warning before casting (`OnlinePlayerUi.castCurrent`). The PiP
   aspect ratio is clamped to 0.42–2.38, inside Android's 1:2.39–2.39:1 limit
   ([PictureInPictureParams.Builder](https://developer.android.com/reference/android/app/PictureInPictureParams.Builder)).

## Fixes

| Commit | Fix | Verified |
|---|---|---|
| b5d2d5a | `CastSupport`: one non-blocking start (`warm`), the Task completes into a `StateFlow` the Cast buttons watch; `unavailable` only on a real failure or missing Play Services. Only WebVTT/TTML subtitle tracks are sent (`castableSubtitles`, `CastSubtitlesTest`). `LocalPlayerDeviceTest d` (heat guard) | `e` on device (4c885fe, 5bfa07c) |
| 4c885fe | `LocalPlayerDeviceTest e` (Cast loads without blocking) | 43707028426, 43708220122 both emulators |
| 5bfa07c | PiP button shows "Picture-in-picture isn't available right now" when refused; test fixes | 43708220122: a, c, d, e pass on both |
| 3dc3914 | Test: Back pressed until the floating window shows, each step logged | see Verification |
| (this group) | `AniSkip.segments` caches only a success or a 404; `AniSkipCacheTest` 2 | unit 2/2 |
| (this group) | `scripts/device-check.sh` default suite adds `HeaderStreamDeviceTest` | see Verification |

## Verification

- Unit suite after the Cast fix: 148 classes, 866 tests, 0 failures, 11 skipped. `AniSkipCacheTest`
  2/2 and `OnlineExtrasTest` 11/11 after the AniSkip fix.
- 43707028426 (4c885fe): LocalPlayer 1/5 on both (e passes, a–d test errors listed above).
- 43708220122 (5bfa07c): LocalPlayer 4/5 on both; b failed at the stepped Back (problem 6).
- Full device suite on 3dc3914: 43709093938 (result below).

## Research

- Google Cast: `getSharedInstance(Context, Executor)` returns a Task
  ([CastContext reference](https://developers.google.com/android/reference/com/google/android/gms/cast/framework/CastContext));
  reading the result before it completes throws "Task is not yet complete"
  ([Stack Overflow](https://stackoverflow.com/questions/74866218/how-can-i-resolve-task-is-not-yet-complete-exception-when-implementing-google-ca)).
  Subtitle formats and CORS: [Supported Media for Google Cast](https://developers.google.com/cast/docs/media).
- Android PiP: aspect ratio limits ([PictureInPictureParams.Builder](https://developer.android.com/reference/android/app/PictureInPictureParams.Builder));
  do not enter PiP when paused or on another page
  ([Enter PiP at correct times](https://developer.android.com/develop/ui/compose/system/pip-enter)).
- UI/UX Pro Max (ux domain): "Error Feedback: users need to know when something fails … Don't:
  silent failures with no feedback" (problem 3); "Overflow Hidden: hidden overflow can clip
  important content" (problem 5).
- Competitors: CloudStream and Hikari player features are compared release by release in Step 4.

## Enhancements (proposed, not built)

1. Controls row: an overflow ("More") button or a fade at the row's edge, so later controls
   are found on portrait phones.
2. Phone Back from full screen: one gesture or key Back leaves full screen and minimises in one
   step (keep the stepped Back for TV remotes only, `tvLayout`).
3. A video sleep timer ("end of episode", 15/30/60 min). VLC for Android has one under More
   Options ([VLC video player docs](https://docs.videolan.me/vlc-user/android/3.X/en/video/video_player.html));
   keep the resume position when it stops (a VLC bug:
   [vlc-android #2941](https://code.videolan.org/videolan/vlc-android/-/issues/2941)).
4. Cast: convert SRT to WebVTT on the phone and serve it from a small local HTTP server with
   CORS, so SRT subtitles reach the TV; same server can add the Referer for protected links.
5. PiP: call `setPictureInPictureParams` early with `setAutoEnterEnabled(playing)`, as the
   Android guide recommends, for a smoother gesture-home transition.

## Code quality and system design

- `VideoPlayerModal.kt` is very large (over 2,300 lines). Proposal for Group 12: split the Back
  logic, PiP and dialogs into their own files and test the Back steps as a pure function.
- Cast now has one start path and an observable state; the UI no longer calls into Play
  Services during composition.
- Recovery is pure logic with JVM tests (`RecoveryState`, `BlackFrameWatchdog`,
  `StarvationSafeLoadControl`); a decoder fault injection hook in a test build would let it run on
  device.

## Status

| ID | Status | Reason / next step |
|---|---|---|
| P1 | verified on device | 43708220122 |
| P2 | verified on device | 43701056608; HeaderStream in 43709093938 |
| P3 | verified on device (emulator) | 43701056608; real phone: script Y |
| P4 | verified on device | 43701056608 |
| P5 | verified on device | 43701056608 |
| P6 | verified on device | 43708220122 |
| P7 | verified on device | 43708220122 (embedded); online search in G11 |
| P8 | speed verified on device; gestures unit only | swipe volume/brightness need a gesture test |
| P9 | verified on device | 43701056608 |
| P10 | verified on device | PiP and lock 43708220122; floating 43701056608 |
| P11 | see Verification | `b` in 43709093938 |
| P12 | verified on device | 43708220122 |
| P13 | unit only | settings screen is audited in Group 9 |
| P14 | unit only | needs a fault-injection hook |
| P15 | unit only | live API |
| P16 | unit only | shader output not checked |
| P17 | Cast start verified on device; casting unverified | needs a real Chromecast: script P/T |
| P18 | screen wake verified on device; sleep timer → G6 | |
| P19 | verified on device | 43708220122 |
