# Group 6 audit: Music (R16)

Scope (INVENTORY M1–M9): on-device library, online mode (YouTube Music), the endless online feed,
lyrics (embedded and LRCLIB), now playing, queue and media session, equalizer, Last.fm
scrobbling, song downloads and Music mix.

Commits: ee9e246, d183e65, a119186 (branch `r15-stability`).

## Tested

- Code read: `MusicPlayerManager`, `service/PlaybackService`, `scrobble/*`, `lyrics/LrcLib`,
  `youtube/music/YmEndless`, `online/download/SongDownloads`, `player/equalizer/EqualizerManager`.
- Failure paths checked on purpose: music still playing after the screen is gone (Back-exit), the
  app reopened while music plays, a bad entry in the saved scrobble queue, a large library with
  many songs that have no lyrics.
- Environment: JVM unit tests (Robolectric, including a real ExoPlayer for the queue test) and the
  emulator device test `LibraryDeviceTest` (shared player with no activity).

## Problems found and fixed

| # | Problem | Root cause | Fix (files) | Verified by |
|---|---|---|---|---|
| a | Songs played after leaving the app with Back were never scrobbled to Last.fm | The scrobble tracker watched `MusicPlayerManager.playbackState`; the manager belongs to the screen's ViewModel and is released on Back-exit, while the shared player in `PlaybackService` keeps playing | New `player/BackgroundPlaybackWatch.kt`, attached once to the shared player in `PlaybackService.getOrCreatePlayer`. It builds the same snapshot from the player (1 s ticks only while playing; the tracker needs ticks ≤ 2 s) and feeds `Scrobbler.watch`. `MusicPlayerManager` no longer watches, so nothing is fed twice | `ScrobbleTrackerTest` (rules unchanged); device `LibraryDeviceTest#a` part b; real account: OMT-10 |
| b | One bad entry in the saved scrobble queue dropped every listen waiting to be sent | `readQueue` parsed the whole array in one `runCatching` and returned an empty list; the next enqueue saved it | `Scrobbler.decodeQueue` skips bad entries | `ScrobbleQueueTest` |
| c | The LRCLIB prefs file grew forever | Every song without lyrics added a `miss:<key>` entry that was never removed; prefs files are read whole into memory | `LrcLib.pruneMisses` drops expired marks once there are more than 300 (runs on the IO thread inside `find`) | `LrcLibMissesTest` |
| d | Reopening the app while music played showed no now-playing bar, and "Play next" replaced the playing queue | The queue lived on the screen-scoped manager; the new manager started with an empty queue and `restoreExistingState` did not rebuild it | The queue is kept for the process (`processQueue`); when it does not match the player, it is rebuilt from the player's media items (`trackFrom`); `restoreExistingState` now sets track, queue and index | `QueueSurvivesScreenTest` |

Checked and not a bug:

- `YmEndless` is pure and capped at 80 extra shelves; it skips songs already on the page.
- `SongDownloads` holds one request at a time and ignores device songs.
- `EqualizerManager` re-attaches only when the audio session changes and releases the old effects.
- Last.fm keys: the user's own key is never signed with the built-in secret (`LastFm.secret()`).

Group 5 fixes that also touch music: listening time is counted by the shared player (LB-6), and
song/video sort orders are remembered and sorted off the main thread (LB-7).

## Verification

| Kind | Run / command | Result |
|---|---|---|
| unit (local) | `--tests '*Scrobble*' '*LrcLib*' '*QueueSurvivesScreenTest'` | pass |
| unit (local) | full suite at the Group 6 tip | see "Verification" update below |
| CI device | 43738288853 Drama + LibraryDeviceTest on a119186 | pass on api33-lowram and api36; `LIB_B watch playing=true` after Back-exit (MU-1 watch state). Real Last.fm send: OMT-10 |
| device (pending) | `MiniPlayerDeviceTest` (cbe2e47): H11 taken over from Group 1 in the 2026-11-17 re-audit (it had never been added here) | run pending |

Cancelled runs in this group: none.

## Research

- Media3 `MediaSessionService`: the player and session outlive activities; anything that must
  follow playback (scrobbling, play time) belongs with the player, not the UI
  ([Media3 background playback](https://developer.android.com/media/media3/session/background-playback)).
- Last.fm scrobbling rules: track longer than 30 s, scrobble after half its length or 4 minutes
  ([Last.fm API](https://www.last.fm/api/scrobbling)); the existing tracker already follows them.
- Open-source players (Metrolist / ArchiveTune / InnerTune family) keep the queue in the service
  or a database, so a new screen reads it back; Yumeiro now does the same in memory for the process.

## Eighth cycle (2026-11-18)

| ID | Finding | Status | Evidence |
|---|---|---|---|
| G6-ST1 | Sleep timer lived in MainViewModel scope; closing the screen cancelled it while music played on | Verified (emulator, both phones) | 44215701198 fail-before, fix 3b6e2be, 44217196977 pass-after |
| G6-WL | Streams ran with WAKE_MODE_LOCAL (no Wi-Fi lock) | Implemented—unverified | 44217196977 fail-before, fix fb359cf |

## Proposals (not implemented; need owner approval)

1. Save the queue and position to disk so it survives process death (InnerTune-style
   "persistent queue"). Today, after the system kills the app, Home → Continue restores only the
   last song (LastPlayed), not the whole queue.
2. A "Scrobbles waiting" line in Settings → Last.fm with a "Send now" button (the count already exists).

## Limits and owner tests

- OMT-10: Last.fm with your own account (needs your login).
- Equalizer audibility on real speakers/headphones is part of OMT-6 (long playback) feedback.
