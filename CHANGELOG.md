# Changelog

All notable changes to Yumeiro.

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
- Phone and TV settings no longer leak into each other (TV-only layout options are gone from the phone app and the other way round).

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
