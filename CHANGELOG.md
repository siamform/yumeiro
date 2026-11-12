# Changelog

All notable changes to Yumeiro. Version numbers are set by the release workflow from the commit types
(Conventional Commits), so the first release that contains these notes gets the next version after v1.5.0.

## Redesign (R1–R11) — next release after v1.5.0

The redesign was built in a separate working repository on top of v1.5.0 (`af8349e`) and lands here in one pull request.

### New
- **Video pinch to zoom**: two fingers zoom the picture 100–400 % and pan it without opening black edges; it snaps back near 100 %, shows the zoom level, and a *Reset zoom* pill appears with the controls. Only the picture scales, so subtitles and controls stay sharp. A second finger cancels a seek drag instead of jumping.
- **Subtitle style**: size (75–200 %), colour, outline / shadow / box / none, bold, height above the bottom, and *keep the file's own colours*. Live preview. In the player's subtitle menu and in Settings → Video player. The default still follows Android's caption settings.
- **Android TV, Google TV and Fire TV**: TV mode with a left navigation rail, visible focus on every control, and a remote-first player (seek, play/pause, controls, media keys, Back). Addresses "can't click all the options with the Firestick remote" (#25).
- **Android 7.0+** (was 9) and **one APK per processor**: `arm64-v8a`, `armeabi-v7a` and the universal APK.
- **First-run guide**: a welcome choice (music, video, anime, live TV), a tutor-style explore tour and a setup checklist.
- **Extensions wait for the guide**: on a first run nothing extension-related loads, installs, syncs or shows messages until the guide is finished or skipped. Returning users are not affected.
- **New Home**: *Continue watching*, colour shortcuts, Quick picks and an endless *For you* feed. **Library** without tabs.
- **Yumix** (the online video hub): hybrid feed, filter sheet, resume banner, categories, latest episodes, per-language Sub / Dub groups with an audio filter, and a built-in short-drama source.
- **New watch page** under a docked player; **Live TV hub**; smooth page transitions; *See more* rows.
- **YouTube / YouTube Music**: dynamic discovery, quick picks without a download button in the way, song downloads.
- **Link player auto-capture**: pages that hide their stream are opened in the background and the real stream is played.
- **Playback facts**: size, duration and audio format of what's playing.
- **Instant notices (optional)**: Firebase Cloud Messaging for announcements, updates and maintenance through the existing Yumeiro Cloud Worker. Preview and debug builds use separate topics.
- **Themed dialogs** with severity tones (info, warning, error), theme-legible warning / star / category tints, kana accents (optional).

### Fixed
- Seek bar jumps and scrubbing glitches; endless Yumix feed loading; smooth navigation between tabs.
- Manga extension install crash.
- A clean clone failed to build (bundled ISRG root certificate was ignored by git).
- Many edge cases from the R10 edge-case audit (`docs/EDGE-CASE-AUDIT.md`), including dock sizing and navigation crash paths (`AppNavigationCrashTest`).

### Security and privacy
- **Antivirus false positives** (v1.5.0 was flagged 2 / 67) fixed at the cause, without removing features: the preview build scans 0 / 66 and the release build 0 / 68 on VirusTotal. See `docs/security/VT-REPORT-r11.md`.
- **Tracker sign-in (login CSRF)**: any web page or app could open `yumeiro://anilist-login#access_token=…` and swap in another AniList account or sign the user out. Redirects are now accepted only within 10 minutes of tapping *Sign in* in the app, only once, and only if AniList accepts the token. MyAnimeList's PKCE verifier is single-use.
- **"Open with" private-file access**: other apps can no longer make Yumeiro open its own private files (`file:///data/data/…`).
- **Backup restore**: only writes to the known data folders; extension code is restored only when the user ticks it (a shared backup could carry code); 768 MB / 50 000-file limits stop zip bombs.
- **Stream finder**: a hostile page can no longer grow the capture list without limit through the JS bridge.
- **Yumeiro Cloud (server)**: a single phone can no longer move the shared airing timetable for everyone (changes to a confirmed time need two devices, each at least 3 days old); follow lists are written as diffs and at most once per 10 minutes per device, so nobody can burn the free plan's daily database write budget and stop push for all users. Needs migration `server/migrations/0003_abuse_guards.sql`.

### Tests
- 590+ JVM / Robolectric tests (TV layout and mode, navigation crash paths, playback recovery, zoom maths, subtitle style, onboarding gate, intent and backup guards) and 20 server tests, all passing.
