# Changelog

All notable changes to Yumeiro.

## v2.0.0: the 1.9.0 features, without the crash

Replaces 1.9.0. Update from 1.9.0 or 1.8.x directly; nothing is lost (profiles, history, downloads and settings carry
over). Everything listed under v1.9.0 below is in this release.

### Fixed
- Opening any video crashed 1.9.0 on release builds ("VerifyError: Verifier rejected class ok6"). The video player
  had grown into one very large compiled method and the code shrinker (R8) produced code Android refuses to load. The
  player screen is now split so each part stays small, and every release build is checked automatically: it fails if
  any of our screens gets close to that size again (FINAL-49).
- 1.9.0 wrote a crash report "Closed to free memory" when Android closed Yumeiro while it sat unused in the
  background. That is normal Android behaviour, not a crash, and is no longer reported as one. Reports are still
  written when memory runs out while you are using the app, playing or downloading (FINAL-50).
- When Android asked the app to free memory, it logged the same request many times a second; now once per kind of
  request every few seconds (FINAL-50).
- The "where to find things" note in the Inbox is also shown to people updating straight from 1.8.x to 2.0.0.

## v1.9.0: yours, and fast

Released 2026-11-20 (planned as v1.8.2; it adds features, so it ships as 1.9.0). The final test ran every feature
once on a fresh install (the warm-start second pass was dropped on the owner's decision of 2026-11-20) on a low-RAM
phone, Android TV 14, Android TV 7 and a phone on Android 16 emulators; results per feature are in
docs/MASTER-CHECKLIST-FINAL.md. What still needs real phones is listed at the end (full list: docs/UNVERIFIED-QUEUE.md
and docs/OWNER-MANUAL-TESTS.md, which starts with the six mandatory tests for every tester).

Where things are now: Settings → You → Profiles · Settings → You → Your statistics · Settings → Sources & accounts →
Telegram & web sites · Settings → Downloads & library → Downloads (Private downloads) · Settings → Video → Video
quality & playback · Settings → Music → Music & sound.

### Profiles and privacy
- Settings reorganised by subject (FINAL-47): You (Profiles, Your statistics, Look & feel, Navigation bar), Video
  (Video player, Video quality & playback, Subtitles & intros), Music (Music & sound), Downloads & library, Sources &
  accounts (incl. Telegram & web sites), System, Help & about. Profiles, the 18+ switch (per profile) and Private
  downloads are no longer hidden inside Privacy; video quality is no longer inside the audio page. Settings search
  finds profiles, statistics, Telegram, Vega and script repos. After updating, one Inbox note says where everything is.
- Profiles (up to 8) with "Who's watching?". Each profile has its own watch history, lists, YouTube history and 18+
  setting. A profile can be locked with a 4–8 digit PIN and/or the phone's fingerprint / screen lock. Upgrading
  changes nothing: your data becomes the first profile.
- Nothing of the app can be reached with the remote behind "Who's watching?". Wrong PINs lock out for longer each
  time, also after the app is closed. A child's profile needs the main profile's lock to add profiles or turn 18+ on.
  "Forgot?" uses the phone's screen lock; without one, the PIN goes together with that profile's data.
- Private downloads: hide a finished video (eye button). It leaves every list and never appears in the gallery. Open
  Private (the lock at the top of Downloads) with the PIN or phone lock; it closes when you leave the app and can't be screenshotted. Stealth mode
  hides the entry (long-press the storage card).
- Honest limits: files are kept in Yumeiro's private storage, not encrypted; a rooted phone can read them.

### Look and player
- Accent colours: Gold, Ao (blue), Sakura, Matcha, Fuji.
- Hold the video for 2x; long-press the speed button to switch 1x / 2x.
- Shorts player, volume boost to 200 %, external players, Kasumi glass theme, statistics (on the device only).

### Faster
- Setup, feed, extensions and catalog show at once; extensions load behind, pinned ones first. An extension update
  no longer throws away the saved catalog. Starter extensions install side by side.

### Also new
- Watch Later and Favourite on posters; Upcoming movies and series; Yumix sections and Sub / Dub / dual-audio tags;
  your own website list; TV search online; episode watch status.
- Offline manga chapters; Live TV switches to the channel's other links when one fails; Now / Next from the
  playlist's guide.
- Website videos can be downloaded on TV and in landscape.

### Fixes
- Notices reach phones whose notification subscription failed (server side: live since the Yumeiro Cloud update of
  2026-11-20).
- Android TV: sheets with a search box (Yumix "Show videos from", extension and market filters) no longer open the
  full-screen keyboard by themselves; press OK on the box to type, one Back closes the sheet.
- Android TV boxes without an AC-3 / E-AC-3 decoder play those audio tracks through FFmpeg.
- "Show videos from" search shows every match; YouTube on TV shows a grid; M3U logos in single quotes and relative
  channel links; "1 track"; last-backup time kept.

### Security
- Last.fm: the app no longer carries Yumeiro's Last.fm shared secret. Yumeiro Cloud signs the built-in key's calls
  (SEC-LFM, ADR-048). Until the cloud update is live, built-in Last.fm sign-in waits and keeps your plays; your own
  Last.fm API key and secret (Settings → Last.fm) work without it.

### Sources and network (tenth cycle)
- Telegram: public channels play without a login (F-02). Private channels say they can't be opened.
- TeraBox and DiskWala share links that the site lets anyone open play or download (F-03); nothing behind a login
  is bypassed.
- Manga auto-translation in the reader, on the device (F-11).
- Script repos: add a Vega provider repository you trust and install its providers; they show in Home, Search and
  Source status, run in a time- and memory-limited sandbox with no access to your home network, and streams that need
  DRM keys are not played. Nuvio and SkyStream repositories are recognised but can't run yet.
- Your own HTTP or SOCKS5 proxy for the app and its web pages (NBUG-13).
- Automatic DNS fallback (Settings → Network, off by default): when your network's DNS fails, Yumeiro tries
  1.1.1.1, then 9.9.9.9, one at a time (NBUG-6).
- Airing alerts can reach phones that use Firebase push, not only UnifiedPush (F-AIR). The app side is in this build;
  the Yumeiro Cloud update with it went live on 2026-11-20. UnifiedPush alerts work as before.

### Not in this release
- Nuvio and SkyStream runtimes, per-feature DNS (rejected for its cost), Telegram login and private channels.

### Still to be proven on real phones (community tests)
- Profiles and Private with your remote and phone (OMT-44, OMT-45), hold for 2x (OMT-46), external players,
  volume boost, Shorts on a 2–3 GB phone, glass theme battery, statistics, Upcoming time zone, SVG logos,
  your extensions after the library update (OMT-35…OMT-43), and the earlier network / GPU cases (OMT-14…34).
- Tenth-cycle items are emulator verified, owner verification pending: DNS fallback on a dead-DNS network (OMT-47),
  a real Vega repository on phone and TV (OMT-48), Telegram (OMT-49), TeraBox / DiskWala (OMT-50), manga
  translation (OMT-51), airing alerts on a Firebase phone after the server update (OMT-52), your own proxy (OMT-53).
- Final-test items the emulators can't do: skip-intro times after the TV was offline (OMT-54), app lock with a PIN
  on a TV (OMT-55), last-backup time after a restore restarts the app (OMT-56), memory on an Android 7 TV after long
  drama browsing (OMT-57), the TV notification question after two "Don't allow" (OMT-58), smoothness on an
  Android 7–8 TV (OMT-59), Yumix "See all" loading a second page (OMT-60), a video right after installing over
  1.8.x and the TV keeping its data (OMT-61).

### Known imperfect in this release
- YouTube can answer "Sign in to confirm you're not a bot" and refuse to play; the emulators in CI always get this,
  so YouTube playback, quality list and endless Music Home were not checked there (OMT-29).
- On Android TV the system keeps asking for the notification permission after every "Don't allow"; the "Open
  settings" step after a block was checked on phones only (OMT-58).
- On the Android 7 TV emulator the feed-position test taps the phone bar, which a TV does not have (a test fault,
  TV-FP-1), and the TV emulators can't turn their network off, so offline checks on TV ran on phones only (OMT-54).
- A phone set to Layout = TV in portrait was not verified (FINAL-31).
- NOTICE does not yet name the Google SDKs the app uses (LIC-1).
- Built-in Last.fm sign-in stays off until the Last.fm shared secret is rotated and set on Yumeiro Cloud (SEC-LFM);
  your own Last.fm key works.
- Once, on the Android 7 TV emulator, memory rose from 61 to 479 MB during drama paging; two later runs peaked at
  94 and 95 MB with no freeze, and no heap dump was taken (FINAL-35, OMT-57).

## v1.8.1: every title plays

A correction release. Every item was re-tested instead of trusting earlier notes, and four
things that 1.8.0 described as working were not. They are named below.

### Corrections to earlier notes
- Yumeiro Drama did not open any title in 1.8.0 ("Unknown drama catalog"). CloudStream rewrote the internal drama id into a web address, so the studio could not be found.
- "Streams without quality options get Yumeiro's own ladder" (1.8.0) was not true: the YouTube and Yumix players still showed one quality for single-quality streams.
- Custom theme pictures were refused with "picture couldn't be opened" since 1.6: the size check always failed.
- Library showed demo playlists and an estimated play time, not your own data.

### Yumeiro Drama
- Every title opens and plays, tap to finish, for both new and saved (1.8.0) ids.
- Studio health check: a studio whose video server refuses or hangs is hidden (and re-checked later) instead of showing titles that cannot play. If one studio fails, the same title plays from another studio.
- 10 studios in the catalog; the ones that answer today are shown.

### Quality
- One source of truth: the player and the download sheet use the same quality list.
- Streams with a single quality get Yumeiro's own levels: lower levels scaled on the GPU, one upscaled level with Anime4K. Works for local videos too.

### Loading
- A neutral loader ("Getting it ready") is used unless the next screen is certain. Shaped skeletons appear only for direct video links, or when the same source opened the same kind of screen twice in a row.

### Yumix
- No extension header between feed sections; the extension name is on each shelf.
- The "Show videos from" list always has a search field.

### Home and Library
- Jump back in is filled when the app opens (it used to wait for Yumix to load).
- Library shows only your likes, your own playlists and play time measured from real listening.

### Heat and battery
- New heat guard: follows the phone's thermal status and battery saver. Position tickers slow down in the background and when the phone is warm, the video ticker runs fast only while the seek bar is visible, extension calls at once drop from 6 to 4 (warm) or 2 (hot), Anime4K pauses while the phone is hot, and backup sources stop being collected once enough are ready.

### Crash reports
- Crashes, freezes (ANR) and native crashes are saved on the phone. Settings, Crash reports: read, share or delete them. Nothing is sent anywhere.

### Custom theme picture
- Pictures open again (JPEG, PNG, WebP, GIF first frame, BMP, HEIC/HEIF, AVIF on Android 12+), with the accepted types and sizes shown, and a clear reason when a file is refused.

### Notifications
- "Keep notifications on time" guide (Settings, Background): checks notifications, background checks, battery optimisation, restricted background use and Android's standby restriction, each with a one-tap fix, then step-by-step settings for Xiaomi, Samsung, Huawei/Honor, OPPO/realme/OnePlus, vivo/iQOO, Tecno/Infinix/itel, ASUS, Nokia and Meizu with a button that opens the right page.
- The "notifications are held back" prompt now also appears on phones whose maker stops apps even with battery optimisation off.

### Tests
- 755 unit tests pass. New: drama health and live tap-to-play, quality engine, loader, library, heat guard, crash log, picture import (both decoders), notification guide.

## v1.8.0: player first

1.5.0 never showed "Yumeiro is not responding". 1.8.0 audits the player against it, moves heavy work off the main thread and rebuilds source handling, then fixes performance, Yumix, Music, Manga, backup and notifications.

### Player
- No more freezes or "not responding": source gathering runs on one background thread, the list publishes at most every 0.7 s, audio language detection is computed once per link (it ran thousands of regexes per re-sort on the main thread) and sorting uses precomputed keys.
- Video with no audio fixed: on phones the audio path is always decoded to PCM (some devices accepted a passthrough format and played silence) and FFmpeg is tried first for surround formats (AC-3, E-AC-3, DTS, TrueHD).
- The captcha page is blocked: a site check is solved silently in the background and never opens the blank interactive page.
- Automatic source rotation: one quick retry, then the next source. A source that shows no first frame (12 s, 10 s on fast links) or stalls for 25 s moves on by itself.
- Picking a language only changes the audio track of the video that is playing; it never switches to another source.
- Every video has a quality selector, YouTube included. Streams without quality options get Yumeiro's own ladder (snapped to 144p to 4320p) from the real track heights.
- Missing episode thumbnails are filled in from ani.zip and TVmaze.

### Sources
- Never all at once: 10 are shown, Load more fetches 10 more each time.
- Gathered silently in the background, staggered, never simultaneous. Playback starts the moment the first good source is ready; discovery never blocks it.

### Loading skeletons
- The skeleton matches what is opening: direct videos (only the player is coming), movies, series, and titles that open their own page first. Yumeiro remembers which sources open a page.

### Settings
- Settings → Player → Player info: a "Stats for nerds" overlay shown with the controls. Pick the fields: resolution, source, provider, quality, video codec, audio codec, frame rate, video bitrate, buffer, dropped frames, decoder, speed, audio track, subtitle track and audio boost.
- Settings → Audio → Quality and audio: default quality (Auto), default audio (Sub/Dub, Sub by default), default speed, video decoder (Hardware+ recommended), video renderer (Auto recommended), Anime4K enhancement (sharpen for clean 1080p sources), Anime4K GPU tier (mid-range by default), default player, player controls (reorder or hide buttons) and resume playback.

### Performance
- Smoother scrolling, especially on low-end phones: stable list keys, low-end devices (3 GB or less, 4 cores or less) skip crossfades and decode posters in 16-bit.
- The extension market no longer hangs: filtering and the source list are worked out off the main thread.
- Jump back in shows the real last-played item again, video or audio, at the correct position, as in 1.5.0.

### Yumix, Music and Manga
- Yumix: the extension bar hides when you scroll down and comes back on a small scroll up. Search inside "Show videos from" a section (it loads more pages as you search, up to 2000 titles). Yumix Drama is on by default.
- Music: Online by default when online, On-device when offline, switching automatically (a mode you pick yourself is kept while it works). The online feed is endless: after the home shelves end, "Similar to ..." shelves keep coming from your history and the songs on the page, spread over artists (concept from ArchiveTune).
- Manga: redesigned like Yumix, with a source strip, a source sheet with search and language filters, live search, Popular/Latest/Saved, and a bar that hides on scroll.

### Data and system
- Restore is at the top of the backup center: restore the latest backup, pick a file, or find the backup folder (Download/Yumeiro/Backups).
- Notifications work when the app is closed and arrive one at a time at their proper times (a background clock every 3 hours, at least 12 minutes between notifications, re-armed after reboot and update).
- A gallery photo theme now applies across the whole app (correct rotation, lighter scrim, shared decoded image).

### Build
- One universal APK per edition, with native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64. `release-Yumeiro-v<version>.apk` for phones and tablets (Android 9 and newer) and `Yumeiro-TV-v<version>.tv.apk` for TVs (Android 7.0 and newer). No more per-processor files.
- Note: phones on Android 7 and 8 stay on 1.7.0 (the phone baseline is now Android 9).

## v1.7.0: bring back the legacy

1.5.0 was the release people loved; 1.6.0 felt slow, reloaded everything, lost data on restore and mixed the phone and TV experience. 1.7.0 goes back to what made Yumeiro good (fast, reliable, yours) and fixes those problems at the root.

### 1.6.0 vs 1.7.0

| Area | v1.6.0 | v1.7.0 |
|---|---|---|
| Loading | Home, search and details loaded from the network every time | Saved pages: opened pages show instantly from storage and refresh in the background; posters keep a large disk cache (2 % of free space, 64 to 512 MB) |
| Cache and cookies | Grew without limits, only manual clearing | Time limits like a browser: pages refresh after N hours, are deleted after N days, a size cap, and website cookies clear every N days (all adjustable) |
| Backup | One file, partial restore, often broke the running app | Backup v2: pick sections, saved on the phone (Download/Yumeiro/Backups), automatic backups, staged restore that applies on a clean restart, a safety backup before every restore |
| Reset | Clear data from Android settings | Reset center: clear cache, cookies, history, extensions or settings, each with a backup first; erase everything |
| Extensions after restore | Had to be found and added again | Restored with the backup, missing files downloaded again automatically |
| TV | One app for phone and TV | Two apps: **Yumeiro** for phones and tablets, **Yumeiro TV** for Android TV, Google TV and Fire TV, each tuned separately |
| Sources | Name only | Quality, release type, format, size and audio on every source; only the playing group opens; Show more paging so long lists never freeze |
| Audio and subtitle tracks | One long list | Grouped by language, only the playing language open, Show more paging |
| Video details | Missing size, duration and audio for streams | Real size, duration and audio |
| Yumix | Extension filter without search | Search box in the extension filter |

### Two apps: Yumeiro and Yumeiro TV
- Built separately (`mobile` and `tv` product flavors). Yumeiro TV always uses the ten-foot layout and is tuned for TV sticks: smaller memory image cache, 16-bit posters, no crossfades, a leaner video buffer, landscape only and no picture in picture.
- The phone app has no TV launcher entry any more. Installed on a TV, it offers Yumeiro TV through the updater; it installs over the phone app and keeps everything.
- Release files: `Yumeiro-v<version>-<abi>.apk` and `release-Yumeiro-v<version>.apk` for phones, `Yumeiro-TV-v<version>-<abi>.tv.apk` and `Yumeiro-TV-v<version>-universal.tv.apk` for TVs. Each app's updater takes only its own files (1.6.0 updaters never pick TV files).
- The TV home has a single Settings entry (at the bottom of the rail), with the Yumeiro logo at the top.

### Backup, storage and reset
- Settings, Backup, storage & reset: back up library, history, progress, extensions and repositories, settings, trackers and downloads list; choose a folder; restore any backup with counts per section.
- Restore is staged: it is written first and applied on a clean restart, so nothing is half restored. A safety backup is taken before every restore and reset.
- Automatic backups every few days (daily by default), skipped when nothing changed, keeping only the newest few.

### Speed
- Saved pages for home rows, searches (up to 1 hour) and details. A refresh still happens in the background.
- Larger poster disk cache sized from free space.

### Music player
- Codec details under the artist in Now Playing, live from the decoder: codec (Opus, AAC-LC, HE-AAC, MP3, FLAC, ALAC, Dolby...), bitrate, sample rate, bit depth and channels, with a Lossless badge for FLAC, ALAC and PCM. Song info adds the codec string and whether the decoder is hardware or software. Works for songs on the device and online songs.
- Online music search and song lists no longer show a download icon on every row. Long-press a song (or use the song options) to download it.

### Sources and tracks
- Each source shows quality, release (WEB-DL, BluRay, CAM...), format (HLS, DASH, MP4, MKV), size and audio when known; low quality copies are marked.
- Only the group that is playing is expanded; 10 at a time with Show more.
- Audio and subtitle choices grouped by language with Show more.
- The small "i" info icons were replaced with clearer icons everywhere.

### Video and Yumix
- Video details show the real size, duration and audio for streams (1.6.0 left them empty for most online videos).
- Yumix: a search box in the extension filter sheet, so long extension lists are quick to filter.

### Cache and cookies
- Saved pages work like a browser cache: each kind has a refresh age and a delete age, the whole cache has a size cap, and old entries clear themselves. Settings, Backup, storage & reset shows the size and lets you change the limits.
- Website cookies (used by sign-ins and site checks) clear every N days, or never.

### Security and privacy
- Extension install: the extension's manifest is read in memory, no temporary file in the shared temp folder.
- Private DNS (DNS-over-TLS): the resolver's certificate name is checked again after the handshake.
- Android cloud backup is off in both apps (`allowBackup="false"`). Yumeiro's own backup replaces it, keeps sign-in tokens out of Google Drive and still lets Android 12+ phone-to-phone transfer follow the rules in `data_extraction_rules.xml`.
- Saved page file names use SHA-256.
- CodeQL: the full scan of this release reports 0 open alerts (1.6.0 had 32). Accepted results are listed rule by rule, with reasons, in `docs/security/AUDIT.md`.
- No open Dependabot or secret-scanning alerts.

### Fixed
- Restore no longer breaks the running app: it is applied on a clean restart, and a backup that fails twice is dropped instead of looping.
- Extensions missing after a restore are downloaded again.
- Long source, audio and subtitle lists no longer freeze the watch page.
- Yumeiro TV no longer shows the phone/TV layout chooser or the extra Settings card on its home.

### Tests
- 640+ JVM / Robolectric tests, 0 failures. New: Backup v2 round trip, staged restore, resets, prune and saved pages (run on Android 7, 10, 14 and 16), both app editions, updater edition picking, source facts, language track groups, codec details and the music rows.
- CI builds, tests and lints the phone app and the TV app separately.

### For developers
- Gradle tasks: `assembleMobileRelease`, `assembleTvRelease` (and `Preview` / `Debug`). Edition switches live in `Edition.kt`.
- Handover: `docs/HANDOVER.md` §29 and `docs/TV-AND-PHONE.md`.

## v1.6.0: redesign (R1 to R11)

The redesign was built in a separate working repository on top of v1.5.0 (`af8349e`) and lands here in one pull request.

### Current (v1.5.0) vs new

| Area | Current: v1.5.0 | New: this release |
|---|---|---|
| Adding extensions | A separate add flow per format (CloudStream repo, Aniyomi/Mihon repo, Stremio, JavaScript, servers) | **Add anything**: one field, file picker or "Open with" link for every format; the app detects the kind and installs it in the right place |
| CloudStream compatibility | Plugins that need Jetpack Compose (their own settings UI) or extractors removed in CloudStream 4.8 failed to load | 2,247 of the 2,248 market plugins link against the app (audit in `docs/EXTENSION-COMPAT.md`); the one left drives CloudStream's own screens |
| Incoming links | Only media files and playlists | Also `cloudstreamrepo://`, `stremio://`, `aniyomi://`, `tachiyomi://`, `mihon://`, `mangayomi://`, `.cs3` and extension `.apk` files |
| Android versions | Android 9+ | Android 7.0+ |
| APKs | One universal APK | `arm64-v8a`, `armeabi-v7a` and universal |
| TV | Phone layout on a TV; some options unreachable with a remote (#25) | TV mode: left rail, visible focus, remote-first player |
| Home | Previous Home | Continue watching, colour shortcuts, Quick picks, endless For you feed |
| Yumix (online video) | Feed with extension rows | Hybrid feed, YouTube-style category bar (All, Anime, Movies, Series), filter sheet, resume banner, latest episodes, Sub/Dub per language |
| Player | Fixed picture size; captions could sit under the seek bar | Pinch to zoom 100 to 400 %, subtitle style editor, captions move above the seek bar while the controls show, playback facts |
| Watch page | Previous layout | Watch page under a docked player, source and subtitle pickers, download popup with keep options |
| First run | Extensions loaded at once | Welcome choice, explore tour, setup checklist; extensions wait until the guide is done |
| Background | Original gradient | Same original gradient and glow (painted scene experiments removed), or your own photo |
| Library | Tabs | No tabs: colour shortcuts, Videos and Reading pages |
| Notices | Pull only | Optional instant notices through Firebase Cloud Messaging |
| Antivirus | Flagged 2 / 67 on VirusTotal | 0 / 66 (preview) and 0 / 68 (release) |
| Security | See the fixes below | Login CSRF, private-file access, backup restore, stream finder and server abuse guards fixed |

### New
- **Add anything (auto-detect)**: Extensions menu and the Sources hub header take any link, short code (as in CloudStream) or file, and "Open with" from other apps lands there too. Detected: CloudStream plugin, repository or plugin list; Aniyomi or Mihon repository or extension APK; Stremio add-on; Mangayomi JavaScript repository; Jellyfin, Emby or Plex server (sign-in follows). A confirm dialog shows what was found before anything installs.
- **Extension compatibility**: restored the extractors CloudStream 4.8 removed (`GMPlayer`, `Okrulink`, `VidSrcExtractor`, `VidSrcTo`, `Vidguardto` 1 to 3, `Vidplay` and subclasses) and a JVM locale shim, and R8 now keeps the public Compose API that plugins with their own settings use. `scripts/extension_link_audit.py` re-checks every plugin against a built APK.
- **Yumix category bar**: All, Anime, Movies and Series as small chips under the header, like YouTube.
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
- **Captions no longer hide under the seek bar**: they move up while the player controls show and go back down when they hide.
- **Backdrop**: the original Yumeiro gradient and glow again; the painted scenes (moon, sun, shapes) are gone. Older "Calm" settings map to it.
- **TV**: a single Settings entry (on the rail), the Yumeiro logo at the top of the rail, no stray glyphs.
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
- `UniversalAddTest` (detection without a network), the extension link audit over 2,248 market plugins, a live load probe of the plugins that needed the restored extractors, and `RealAppShotsTest` tours with real SoraAnime and CineStream feeds.
- 590+ JVM / Robolectric tests (TV layout and mode, navigation crash paths, playback recovery, zoom maths, subtitle style, onboarding gate, intent and backup guards) and 20 server tests, all passing.
