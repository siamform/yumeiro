# Round 2 evidence, orders 2 to 9 (2026-11-14)

No physical phone or emulator is available in the build sandbox (no /dev/kvm, 2 cores).
Evidence is Robolectric unit/UI tests on the real app code, live network tests where they
apply, and ffprobe decoding. Every test named here passes on branch `r14-round2`.

## Order 2: one quality source of truth
- Root cause: the YouTube player built its quality list from the playing stream's own
  renditions, the download sheet from `YtDownloads.options`. Single-rendition streams showed
  one option. The round-13 note "streams without quality options get Yumeiro's own ladder" was
  not true in 1.8.0.
- Change: `QualityEngine.youTubeLadder(YtDownloads.options(info))` (same list as downloads);
  YouTube detected from the URL (`PlatformRules.isYouTube`) even when untagged; when fewer than
  two real heights exist, `engineLevels()` adds one upscaled level (Anime4K, at most 2x, max
  2160p), the source, and up to four scaled-down levels rendered on the GPU (media3
  `Presentation.createForHeight`). Also applies to local videos.
- Tests: `round2/QualityRound2Test`. The live YouTube case skips in the sandbox because
  YouTube answers "Sign in to confirm you're not a bot" for datacenter IPs.

## Order 3: loader
- Change: new neutral loader (`WatchKind.NEUTRAL`, "Getting it ready..."). A shaped skeleton is
  used only when it is certain: a media URL or live stream (player skeleton), or the same
  extension opened the same kind of screen twice in a row (page skeleton). Type-based guessing
  removed.
- Tests: `round2/LoaderRound2Test`, `Round13LogicTest` (updated).

## Order 4: Yumix
- Change: extension-name + View header removed between feed sections (the extension name is
  each shelf's subtitle); the extension search field always shows ("Search extensions").
- Tests: `YumixShowcaseTest`.

## Order 5: Home and Library
- Root cause: the online watch history loaded only when a Yumix page opened, so "Jump back in"
  was empty or stale on app open. Library seeded demo playlists ("Workout mix", "Chill vibes"),
  counted the favourites playlist as a user playlist, and "Play liked" with no likes played
  everything. Play time was not measured.
- Change: `MainViewModel` loads online + YouTube history on start (IO). `ListeningTime` measures
  real listening (saved every 30 s and on pause). Demo playlists removed (empty seeded ones
  deleted on upgrade); favourites excluded from playlists.
- Tests: `round2/LibraryRound2Test`.

## Order 6: heat
Audit (what runs while the app is used):
| Area | Before | After |
|---|---|---|
| Music position ticker | 300 ms forever while playing, also in background | 300 ms on screen, 2 s in background, x2 warm, x4 hot |
| Video progress ticker | 250 ms while playing, even with controls hidden | 250 ms only while the seek bar shows, else 1 s; scaled by heat |
| Source gathering ticker | 150 ms for up to 2+ minutes | 300 ms, scaled by heat |
| Backup link gathering | continued for up to 75 s after playback started | stops once 12 playable backups (8 warm, 5 hot) are in and subtitles arrived (or 20 s passed) |
| Extension calls at once | 6 always | 6 cool, 4 warm, 2 hot |
| Anime4K GPU pass | ran whenever enabled | skipped while thermal status is SEVERE or worse, dropped live during playback |
| Stats overlay | 1 s | scaled by heat |
| Hero carousel auto-advance | every 6 s | paused when warm or hot |
| Wake locks | ExoPlayer wake mode + keepScreenOn only while playing | unchanged (already correct) |

Heat level comes from `PowerManager.currentThermalStatus` + `addThermalStatusListener`
(Android 10+) and battery saver (`ACTION_POWER_SAVE_MODE_CHANGED`).
- Tests: `round2/HeatGuardTest` (levels, intervals, concurrency peak measured under 20
  parallel calls: at most 6 / 4 / 2, link-cap rules).
- Not measurable here: real device temperature. A device run with `adb shell dumpsys
  thermalservice` and `dumpsys batterystats` is the next step.

## Order 7: crash logs
- Change: `CrashLog` handler (installed first, chained), imports ANR / native crash / low-memory
  exits (ApplicationExitInfo), records extension crashes caught by ExtensionGuard. Saved under
  app storage (latest 25), scrubbed of query strings, e-mails and tokens. Settings, Crash reports:
  list, read, share, share all, delete all.
- Tests: `round2/CrashLogTest`.

## Order 8: custom theme image
- Root cause: since PR #27, `importPhoto` returned "couldn't open" for every picture because it
  took `decodeStream(..., inJustDecodeBounds)` returning null (which it always does in bounds
  mode) as a failure.
- Change: ImageDecoder (software, target size, HEIF/AVIF/WebP), BitmapFactory fallback with a
  correct bounds probe, EXIF rotation, specific messages (too big over 60 MB, smaller than
  200x200, video, cloud file not downloaded, out of memory). Accepted types and sizes shown in
  the picker: JPEG, PNG, WebP, GIF (first frame), BMP, HEIC/HEIF, AVIF (Android 12+).
- Tests: `round2/BackdropImportTest` (4000x3000 JPEG, PNG, WebP, 300x200, both decode paths,
  error cases).

## Order 9: notifications
Audit findings:
1. The "notifications are being held back" card only appeared while battery optimisation was
   on, so phones whose maker kills apps anyway (Xiaomi auto-start, Samsung sleeping apps,
   Oppo auto-launch) never saw it.
2. "Restricted" background use (Android 9+) and the RESTRICTED standby bucket (Android 11+)
   were never checked; both stop WorkManager, alarms and delay FCM.
3. One maker page per brand, several outdated, none for Tecno/Infinix/itel, Honor, Asus,
   Nokia, Meizu; no instructions on what to switch.
Already correct (verified in code): POST_NOTIFICATIONS request on Android 13+, boot and
update re-arming (NotifyAlarmReceiver), inexact Doze-friendly alarms (no exact-alarm
permission), expedited workers provide foreground info, FCM is data-only with high priority.

Change: `ReliabilityGuide` + "Keep notifications on time" guide (Settings, Background, and a
paced prompt: only when notifications and background checks are on and something is wrong,
at most 3 times a week apart). Live checklist with one-tap fixes, then the maker's own steps
with an "Open settings" button that tries every known page for that maker (only exported,
resolvable ones) and falls back to App info.
- Tests: `round2/ReliabilityGuideTest`, `notify/BackgroundWorkTest`.
