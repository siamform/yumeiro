# Yumeiro — system behaviour specification (Round 10)

What every major feature does in the normal case, when it fails, when it is interrupted, across the
Android lifecycle, and how it recovers. Written from the code on `redesign/v7-dream` (Round 10),
not from intent. Each row names the code that implements it. Evidence levels used below:

- **Tested**: covered by a unit or Robolectric test that ran and passed in this round.
- **Code**: read in the source and traced, not executed.
- **Device**: needs real hardware or a live service; not verified here.

Companion documents: [EDGE-CASE-AUDIT.md](EDGE-CASE-AUDIT.md) (issues, checklist, conflict matrix,
verification report) and [TV.md](TV.md).

---

## 1. Playback (music and video)

There are two players. Music runs in `service/PlaybackService.kt` (Media3 `MediaSessionService`).
Video runs in the full-screen player `ui/screens/video/VideoPlayerModal.kt`, built by
`stream/StreamMediaFactory.createPlayer`, and is published to the system through
`service/VideoPlaybackService.kt` / `VideoSessionBridge` while it plays in the background.

| Rule | Behaviour | Where | Evidence |
|---|---|---|---|
| One audible session | Both players request audio focus (`setAudioAttributes(…, handleAudioFocus = true)`, usage MEDIA). Starting one makes Android take focus from the other, which pauses. They never play together. | `PlaybackService.kt:47`, `StreamMediaFactory.kt:104` | Code (Media3 focus handling); Device for real overlap |
| One video at a time | The video player is a single modal. Opening another video replaces the current item in the same player; the previous position is saved first. | `VideoPlayerModal.kt` | Tested (`OnlinePlaybackTest`, `AppNavigationCrashTest`) |
| Headphones unplugged / BT disconnect | Pause (`setHandleAudioBecomingNoisy(true)`). | `StreamMediaFactory.kt` | Code |
| Phone call / other app takes focus | Transient loss pauses and resumes when focus returns; permanent loss pauses and stays paused. Ducking lowers volume for notification sounds. | Media3 `AudioFocusManager` | Device |
| Seeking | Seek steps are clamped to `[0, duration]`; unknown duration (live) only rewinds within the window. | `player/SeekMath.kt` | Tested (`SeekMathTest`) |
| Audio track switch | Switch, and if the new track is still buffering after 10 s, revert to the previous track with a message. | `VideoPlayerModal.kt` audio switch job | Tested (`AudioSplitTest`, `SubtitleAudioTest`) |
| Source switch | Keeps position; failed source shows the error card with Retry and "Try another source". | `PlaybackRecovery` | Tested (`PlaybackRecoveryTest`) |
| Black frames with audio | Watchdog detects a stuck video surface and re-prepares. | `BlackFrameWatchdog` | Tested (`BlackFrameWatchdogTest`) |
| Resume | Video progress is saved every 5 s, on pause, on close and immediately on `ON_STOP` (the process may be killed afterwards). Reopening offers resume from that position. | `VideoPlayerModal.kt:1078` | Code + Tested for history (`WatchLogicTest`) |
| Background | Video keeps playing in the background only if the user enabled background play or is in PiP; otherwise `ON_STOP` pauses it. Music keeps playing (foreground service). | `VideoPlayerModal.kt:1084` | Code |
| Playback facts | Under the player: size, duration and audio language. Size is the real content length when the server reports it, otherwise "≈ N MB" estimated from bitrate × duration with a note. Missing facts get a single notice ("This source doesn't share its …"). | `player/PlaybackFacts.kt`, `WatchScreens.kt` `PlaybackFactsRow` | Tested (`PlaybackFactsTest` 5/5, `YumixShowcaseTest`) |

## 2. Picture-in-picture

| Rule | Behaviour | Evidence |
|---|---|---|
| Availability | Only Android 8.0+ and only when the device declares `FEATURE_PICTURE_IN_PICTURE`. Otherwise PiP is never offered. | Code (`PipController.isSupported`) |
| Enter | Auto on Home while playing if auto-PiP is on (Android 12+ natively via `setAutoEnterEnabled`, 8–11 via `onUserLeaveHint`), or from the PiP button. Controls hide on entry. | Code; Device |
| Actions | Play/pause and ±10 s remote actions in the PiP window. | Code |
| Close PiP | Closing the window stops the activity; `ON_STOP` saves the position and pauses. | Code; Device |
| Another video while in PiP | There is one player; expanding PiP and picking another title replaces the item (previous position saved). Music started while in PiP takes audio focus and the PiP video pauses. | Code; Device |
| Rotation / resize | The activity handles configuration changes itself (`configChanges` includes orientation, screen size, smallest screen size, screen layout), so the player is not recreated. | Code |
| Process death in PiP | Position saved on `ON_STOP`; on next launch the title shows in Continue watching. | Code |

## 3. Network and offline

| Rule | Behaviour | Evidence |
|---|---|---|
| Connectivity signal | `network/Connectivity.kt` uses `ConnectivityManager`. It needs `ACCESS_NETWORK_STATE`, which the manifest used to strip (see audit P0-1). Restored in Round 10. | Tested (`BackgroundWorkTest`), manifest merged check |
| Offline banner | Shown when no validated network; pages that need the network show an offline state instead of a spinner. | Code |
| Video loses network | The player does not burn its retry budget: it waits for a validated network (up to 90 s), lets DNS and routes settle for 0.8 s after a Wi-Fi ↔ mobile switch, then re-prepares at the same position. | Code (`VideoPlayerModal.kt:549`, `:674`) |
| Wi-Fi ↔ mobile switch | Media3 continues from the buffer and reconnects on the new network; HLS reloads the playlist. | Device |
| DNS failure | Optional Smart DNS (DoH) fallback for blocked hosts. | Tested (`SmartDnsTest`) |
| Timeouts / server errors | HTTP sources have timeouts; errors map to plain-language messages with Retry. | Tested (`PlaybackRecoveryTest`, `StreamDialogsTest`) |

## 4. Navigation

| Rule | Behaviour | Evidence |
|---|---|---|
| Back | Closes the top sheet/dialog first, then the sub-page, then returns to the previous tab, then leaves the app. | Tested (`NavigationFlowTest`, `AppNavigationCrashTest`) |
| Tabs | Tabs live in one pager; swiping or tapping moves between them and each keeps its own state while the app runs. | Tested (`swipeAnywhereMovesBetweenTabsAndBackReturnsHome`) |
| Rapid navigation | Fast tab and page switching followed by Home does not crash or leave a stray page. Double Back on Home exits. | Tested (`quickNavigationThenHomeDoesNotCrash`, `doubleBackPressExitsFromHome`) |
| Deep links / notifications | `MainActivity` is `singleTop`; a tap on a notification while the app is open arrives in `onNewIntent` and opens the target on top of the current screen. | Code |
| Bottom dock | Nav pill 68 dp, mini player 68 dp, joined with a 2 dp gap. Pages measure the dock and pad their content by its real height. TV uses a left rail instead. | Tested (`AppNavigationCrashTest` screenshot) |
| Library | No tabs. Eight colour shortcuts (Videos, Reading, Equalizer, New playlist, Rescan, History, Downloads, Together); Videos and Reading open their own pages. | Tested (`LibraryShortcutsTest`, `AppNavigationCrashTest`) |

## 5. Search and discovery

| Rule | Behaviour | Evidence |
|---|---|---|
| Empty query | Shows recent searches and suggestions; no request. | Tested (`SearchSuggestTest`) |
| Typing fast | Each search runs in `LaunchedEffect(query)` after a 200–350 ms debounce. A new keystroke cancels the previous coroutine, so an older, slower response can never overwrite a newer one. | Code (`YouTubeScreens.kt:246`, `YtMusicBrowse.kt:201`, `MusicMix.kt:139`) |
| Duplicates | Results from all sources are ranked and de-duplicated by `api|url`, so the grid never shows the same item twice. | Tested (`SearchRankerTest`) |
| Offline | Online search shows the offline state; local library search still works. | Code |
| Yumix filter | Source filter sheet with All or one source; categories need at least 4 titles to appear. | Tested (`YumixFeedTest` 7/7) |

## 6. Extensions

| Rule | Behaviour | Evidence |
|---|---|---|
| Install | Index is fetched over HTTPS, the APK/plugin signature/format is checked before install. | Tested (`ExtensionScannerTest`, `ExtensionFormatsTest`, `TachiyomiIndexTest`) |
| Incompatible version | Skipped with a reason in the market list instead of crashing. | Tested |
| Repository down / malformed JSON | The market is cached on the device and works offline from the last good copy; a bad response doesn't replace it. | Code (`ExtensionMarket.kt`); parsing Tested (`SourceMarketTest`) |
| Removed while its content plays | The current stream keeps playing (its URL is already resolved and the player holds no reference to the extension). The next episode or source lookup no longer finds that source and offers the remaining ones. | Code |
| Failure during playback | Treated like any source error: error card, Retry, other sources. | Tested (`PlaybackRecoveryTest`) |

## 7. Downloads

| Rule | Behaviour | Evidence |
|---|---|---|
| Duplicates | Unique WorkManager work per id (`ExistingWorkPolicy.KEEP`): tapping Download twice queues one job. | Code (`OnlineDownloads.kt`) |
| Network | Jobs require a connected network (Round 10) and resume from the `.part` file with an HTTP Range request. | Code; Tested (`DownloadNamingTest`) |
| Storage full | Checked up front against the expected size and on `ENOSPC` while writing. The job stops with "Storage full — needs about N MB more, M MB free" instead of retrying. | Tested (`DiskFullTest`) |
| Background / killed | Foreground data-sync job with a progress notification; WorkManager reschedules after process death or reboot. | Code; Device for OEM battery killers |
| Delete | Delete cancels the job and removes its files (`OnlineDownloads.delete`). | Code |

## 8. YouTube

| Rule | Behaviour | Evidence |
|---|---|---|
| Audio ↔ video | Music mode plays audio-only streams; switching to video keeps position. | Tested (`YtDownloadOptionsTest`, `OnlinePlaybackTest`) |
| Expired stream URL | An HTTP error on the media URL makes `StreamRecovery` fetch a fresh manifest/token (re-open the link or re-resolve the page) and resume at the same position. | Code (`player/StreamRecovery.kt`); recovery plan Tested (`PlaybackRecoveryTest`) |
| Unavailable / age-restricted | Plain message, no crash. | Tested |
| Live streams | Treated as live (no duration, seek within window). | Tested (`PlaybackFactsTest` live case) |
| Quick picks | Rows have no download button; long-press offers Download. The playing song is highlighted with animated bars. | Tested (`LibraryShortcutsTest`) |

## 9. Torrent, IPTV and link playback

| Rule | Behaviour | Evidence |
|---|---|---|
| M3U/M3U8 parsing | Invalid lines skipped; empty playlist shows an empty state. | Tested (`StreamPlaylistTest`) |
| HLS ads | Ad segments filtered. | Tested (`HlsAdFilterTest`, `AdFilterTest`) |
| Cloud links | Pixeldrain, OneDrive, MediaFire turn into direct URLs; TeraBox and DiskWala open a hidden in-app WebView that captures the real media request; if the site asks for login or captcha the page is shown with the reason. | Tested (`LinkHostsTest`, `StreamDetectionTest`); TeraBox live: Device/unverified |
| Magnet / .torrent | Yumeiro has no torrent engine. Links stream through a TorrServer the user runs; without one they are handed to an installed torrent app. Seeders and peers are the server's concern. | Code (`LinkHosts.kt`) |

## 10. Watch history and lists

| Rule | Behaviour | Evidence |
|---|---|---|
| Progress | Saved every 5 s, on pause, close and `ON_STOP`. | Code |
| Completion | An episode counts as watched at 92 % (`OnlineHistory.DONE_FRACTION`) and Continue watching moves to the next episode. | Tested (`WatchLogicTest`, `EpisodeMatcherTest`) |
| Duplicates | One entry per title/episode id; re-watching updates the timestamp. | Tested (`OnlineHistoryFeedTest`) |
| Sync | Local history is always written first. Trakt, SIMKL and AniList/MAL updates run after it, each isolated, so one failing tracker (offline, revoked token) never blocks the others. Whether a tracker update that failed offline is retried later was not verified (audit P2-4). | Tested (`TraktAuthTest`, `SimklTest`, `ScrobbleTrackerTest`); Code (`AnimeSync.kt:86`) |

## 11. Android lifecycle

| Situation | Behaviour | Evidence |
|---|---|---|
| Background / screen lock | Music continues; video follows the background-play setting. | Code |
| Process death | Progress already saved on `ON_STOP`; WorkManager jobs resume. | Code |
| Reboot | Download and discovery workers are rescheduled (`RECEIVE_BOOT_COMPLETED`). | Code |
| Android 7–8 | minSdk 24 with desugaring; app lock falls back to device credential; ISRG root bundled. | Tested (compile + `TvModeTest`); Device |
| Keyboard / remote attached | No activity restart (`configChanges`). | Code |

## 12. Push notifications (FCM)

| Rule | Behaviour | Evidence |
|---|---|---|
| Delivery | Data-only high-priority messages; the app builds the notification itself. | Tested (`FcmPlanTest`, `FcmDeliveryTest`) |
| Duplicates | Notification id is the notice id's hash, so a repeated message replaces the existing one; the inbox de-duplicates by id. | Code (`AppNotifier.post`) |
| Permission denied | Notice goes to the in-app inbox only. | Tested (`PushPlanTest`) |
| Expired token | Worker deletes the row when FCM answers `UNREGISTERED`. | Tested (`server/test/fcm.test.ts`) |
| Tap while open | `onNewIntent` → validated link → browser or in-app action, else inbox. | Code |
| Not live yet | Needs the owner's Firebase client config and service account (see `PUSH-FCM.md`). | — |

## 13. App updates

| Rule | Behaviour | Evidence |
|---|---|---|
| Which APK | Picks the APK matching the device's ABI, else universal. | Tested (`UpdaterLogicTest`) |
| Interrupted download | The download can be retried from the update dialog; the install always asks the user (PackageInstaller with user action). | Code |
| Corrupt file | Fail closed: the APK's SHA-256 must match the digest published with the release, or it is not installed. | Code (`ApkInstaller.verify`) |
