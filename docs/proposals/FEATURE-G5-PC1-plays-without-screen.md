# G5-PC1 — Songs played after the app is swiped away are not counted

Ledger ID: G5-PC1 (L2 G5 Library, play-time and play-count accounting). Found by code read, 2026-11-18.

## 1. Finding

Music "Recently played", "Most played" and per-song play counts stop updating when the app is
swiped away from recents while music plays. The music keeps playing the queue, but every song
after the swipe is missing from the library.

## 2. Code read (head 01fbd28)

| Step | Code | Effect |
|---|---|---|
| Swipe from recents while playing | `PlaybackService.onTaskRemoved` stops only when nothing plays | Service and shared ExoPlayer keep playing the queue |
| Activity destroyed | `MainViewModel.onCleared` → `MusicPlayerManager.release()` | `playerListener` removed from the shared player |
| Next song starts | `MusicPlayerManager.playerListener.onMediaItemTransition` → `onTrackChanged` → `repository.recordTrackPlayed` | Not called any more: no count, no Recently played entry |
| Back at root | `MTPlayerApp` BackHandler, no `finish()` | Activity kept, so plain Back is not affected |

Listening time (R16, `ListeningTime` fed by `PlaybackService`) and scrobbling
(`BackgroundPlaybackWatch`) were already moved to the shared player for the same reason.
Play counts were not.

State: `OfflineMediaRepository` keeps Recently played and counts in memory per instance and
writes them to `mt_player_music_prefs`. A second live writer would overwrite the first one's
changes, so the fix must keep exactly one writer at a time.

## 3. Competitors (ideas only, nothing copied)

- InnerTune v0.5.10, `playback/MusicService.kt`: play events and total play time are written from
  the service (`onPlaybackStatsReady`, 30 s minimum), not from a screen.
- Metrolist v14.0.0, `playback/AndroidListeningHistory.kt`, owned by `MusicService`: the listening
  session is finished and recorded in the service, with a configurable minimum play time.

Both put play history in the service lifetime. The idea taken: record where the player lives.
The minimum play time is not taken, because it would change what counts as a play (that's a product
decision, recorded as an option below).

## 4. Options

| Option | Pros | Cons / risk |
|---|---|---|
| A. Shared-player watch records only while no screen listener is attached (chosen) | Smallest change. The screen path is unchanged. One writer at a time. No double counts | Two call sites share one rule (`MusicPlayHistory.next`) |
| B. Move all recording to the watch, remove the screen path | One path | Changes the timing of every count. The first song of a new queue (`PLAYLIST_CHANGED`) needs new handling. The screen's in-memory flows would go stale without a shared store |
| C. Process-wide repository singleton | Removes per-instance state | Large refactor of `OfflineMediaRepository` lifetime, with data-loss risk during migration |
| D. Minimum play time (30 s) like competitors | Fewer accidental counts | Changes the meaning of counts. Owner decision, not part of this fix |

## 5. Fix (option A)

- `data/MusicPlayHistory.kt`: one rule `next(recent, counts, id)` (front of the list, max 50,
  count + 1). It's used by `OfflineMediaRepository.recordTrackPlayed` (same behaviour as before) and by
  `recordWithoutScreen`, which writes straight to the same preferences. Incognito is respected.
- `MusicPlayerManager`: `screenRecorders` is incremented when a manager that records
  (`onTrackChanged != null`) attaches its listener, and decremented once on `release()`.
- `BackgroundPlaybackWatch.onMediaItemTransition`: if no screen listener is attached, it records the
  song (not for `PLAYLIST_CHANGED`, which `playTrack` records, the same as the screen path).
- No race on reopen: when music is playing, the manager attaches inside its constructor on the
  main thread, right after the new repository has read the preferences.

## 6. Action–reaction

| Action | Before | After |
|---|---|---|
| Play an album, keep the app open, songs change | Counted | Counted once (the watch skips) |
| Play an album, swipe the app away, 3 songs play | Not counted | 3 counts, Recently played in order |
| Reopen the app after that | Library shows the stale history | Library shows the 3 songs |
| Incognito on, app swiped away | Not counted | Not counted |
| Repeat one, no screen | Not counted | Counted per loop (same as with a screen) |

## 7. Tests

- Unit (Robolectric) `PlaysCountedWithoutScreenTest`. It uses only APIs that existed before the fix:
  - `songsAfterTheScreenIsGoneAreCounted`: must fail before the fix and pass after.
  - `nothingIsCountedTwiceWhileAScreenListens`: passes before and after.
- Device: the G5 empty-profile flow test (play → swipe-equivalent finish → next song → reopen →
  Recently played and counts), on the emulator first.

## 8. G5-PC2 (same root cause): Home Continue

`LastPlayed.saveAudio` (Home → Continue, "Jump back in") was called only from `MainViewModel`
(every 5 s of playback, on pause, on song change). After a swipe, the queue plays on with nothing
saving Continue. If the process then dies (low-RAM phones, or the system stopping the service
after the queue ends), Home shows the song from before the swipe at that old position.

Fix: `BackgroundPlaybackWatch.saveContinue` saves on the same steps (song, playing, 5 s bucket)
while no screen listener is attached. The full track (album art) comes from the process queue
(`MusicPlayerManager.queuedTrack`), and the player's metadata is the fallback. Test:
`ContinueSavedWithoutScreenTest` (unit, existing APIs only) and the phase b check in
`LibraryEmptyProfileDeviceTest`.
