# G6-QP — The music queue did not survive a process death

Status: **In progress** (built; device fail-before run 44251573024 on the old code; pass-after pending).
Group L2 G6 (Music). Option 1 approved by the owner (master prompt v1.8.2, §5). Owner-approved scope: queue
persistence only. Shuffle order, repeat mode and playback speed are not restored (unchanged from today).

## 1. Problem (code read, head 25b3bc9)

- `player/LastPlayed.kt` kept one song for Home → Continue ("Jump back in"): prefs `yu_last_played`, key
  `audio` = JSON of one `AudioTrack` plus position, duration and time. It is written every 5 s of play, on
  pause and on a track change (`MainViewModel` with a screen, `BackgroundPlaybackWatch.saveContinue` without one).
- `MusicPlayerManager.currentQueue` lives only in memory (companion `processQueue`).
- Home Continue (`HomeScreen.resumeTrack`, `MTPlayerApp` `onResumeTrack`) rebuilt the queue from Recently
  played (or the library, or the song alone). After a process death, an album or playlist queue came back as
  "the songs already played, newest first". The order and every song not yet played were lost.

## 2. Competitors (exact versions, source read line by line; ideas only, nothing copied)

| Project | Tag | Files read | How it keeps the queue |
|---|---|---|---|
| InnerTune (z-huang/InnerTune) | v0.5.10 | `app/src/main/java/com/zionhuang/music/models/PersistQueue.kt`; `playback/MusicService.kt` lines 338–367 (restore in `onCreate`, 30 s save loop), 758–778 (`saveQueueToDisk`), 780–793 (`onDestroy`) | `PersistQueue(title, items, mediaItemIndex, position)`: Java `Serializable` to `filesDir/persistent_queue.data` via `ObjectOutputStream`, written straight over the file. Saved every 30 s and in `onDestroy`; deleted when the player is idle. Restored when the service starts, with `playWhenReady=false`. A switch (`PersistentQueueKey`, default on). |
| Metrolist (MetrolistGroup/Metrolist) | v14.0.0 | `androidApp/src/main/kotlin/com/metrolist/android/models/PersistPlayerState.kt` (whole file, 164 lines); `androidApp/.../playback/MusicService.kt` lines 2414–2560 (`startPlaybackPersistence`, `savePlaybackState`, `restorePersistedPlayback`) | kotlinx JSON `PersistPlayerState` (`ignoreUnknownKeys`). The queue structure is written only when it changes; a small position checkpoint linked by a revision id is written often. Writes go through a conflated channel on IO (`PlaybackPersistenceWriter`, stale revisions dropped). Saves on seek, pause, queue change and when the app goes to the background. Restore runs on IO and catches failures (rethrows `CancellationException`). |

Lessons taken (ideas): from Metrolist, split "structure" (rare) from "position" (often) and conflate writes
off the main thread. From InnerTune's weak points: Java serialization breaks on class changes and the
direct overwrite can leave a truncated file on a kill. A 30 s save interval loses up to 30 s.

## 3. Options

| | Option | Pros | Cons | Risk | Effort |
|---|---|---|---|---|---|
| **1** | **Extend `LastPlayed` with a queue file through `SafeJsonFile` (approved)** | Reuses the proven atomic write and `.bad` recovery (G5); the position stays in the existing 5 s `audio` save; old data needs no migration step | Restores only on Home Continue (no auto-restore of the player at start); the queue file and the song are two writes matched by id | low | medium |
| 2 | Room table for the queue | Queries, partial updates | New schema plus migrations for one list; more code on the startup path | medium | high |
| 3 | Restore the queue into the player at service start (InnerTune) | Notification and lock screen show the queue after reboot | Changes startup and the player (ADR-002 single player); a prepared player at start costs memory; auto-restore is a behaviour change the owner did not ask for | high | medium |

## 4. Design (Option 1, ADR-019)

- File `filesDir/last_queue.json`, format 1: `{"v":1,"cur":id,"at":ms,"q":[track…]}`. Track keys are the
  same as the `audio` entry.
- Written by `MusicPlayerManager.persistQueue()` after every queue change made in the app: play
  (`playTrack`), play next, add to queue, remove. `LastPlayed.saveQueue` returns at once. One daemon
  thread builds the JSON and writes it through `SafeJsonFile.write` (synced temp file, then rename).
  Requests are conflated; the latest wins.
- Position and current song: unchanged, the `audio` entry (every 5 s, pause, track change). Home Continue
  uses the saved queue only when it contains the Continue song. It starts there at the saved position.
- Read once on IO (`MainViewModel` init → `LastPlayed.loadQueue`).
- Falls back to the behaviour before G6-QP when:
  - the file is missing (old installs, old format);
  - the file is unreadable (kept as `.bad`; `.tmp` recovery first);
  - the file has no tracks;
  - the queue does not contain the Continue song.
- Not saved:
  - in incognito (same rule as `saveAudio`);
  - an empty queue. Stop keeps the last queue, so Continue after Stop still plays the album.
- Bounded: a queue over 1000 songs is saved as a 1000-song window (200 before the current song).
- History reset (`ResetCenter`) deletes the file; `LastPlayed.clear` deletes it.
- Left out of backups: device-specific content URIs, refilled on the next play.

## 5. Feature profile

| Item | Value |
|---|---|
| Users | everyone who plays music (device, YouTube Music, servers) |
| Entry points | Home → Continue card and its Play button |
| Data | ≤ 1000 tracks × ~300 B ≈ 300 KB worst case; 5 songs ≈ 1.5 KB |
| Threads | write: `yu-last-queue` daemon thread; read: `Dispatchers.IO` |
| Failure modes | missing / broken / old file → old behaviour; write failure logged, never thrown |
| Privacy | incognito saves nothing; history reset deletes; not in backups; no network |
| Service terms | no third-party service involved (local storage only) |

## 6. Action–reaction

| Action | Reaction |
|---|---|
| Queue 5 songs, play song 2, kill the app, reopen, Continue | 5 songs in order, on song 2 at the saved position |
| Same with the screen swiped away (music played with no screen) | queue written when it was set; position by `BackgroundPlaybackWatch` |
| Back / swipe from recents, process stays | player still holds the queue (unchanged) |
| Play next / Add to queue / Remove, then kill | last change is in the file |
| Rapid changes (10 adds in 1 s) | conflated: at most one write in flight plus the latest |
| Incognito on | nothing written; Continue uses the queue from before incognito if it holds the song |
| Stop (clears the mini player) | file kept; Continue plays the last queue |
| Corrupt file | kept as `.bad`; Continue falls back to Recently played |
| Update from a version without the file | Continue falls back; next play writes the file |
| Reset history | file deleted |
| Online song (YouTube Music) in the queue | saved with its `mtyt:` URI, resolved again on play as today |
| Device song deleted since | the player skips it with the usual error, as with Recently played |
| Tap Continue in the first ~100 ms after start, before the read on IO | fallback queue (old behaviour) |

## 7. Pros / cons and better design later

- Pro: no startup cost on the main thread, no schema, no new dependency.
- Con: no auto-restore into the notification after a reboot (Option 3). It can be revisited in
  stability-v2 as a proposal with the owner.

## 8. Tests

- Unit (Robolectric, CI): `QueuePersistenceTest` — 5-song round trip across a "new process", old
  single-song data still loads (migration), broken file → fallback + `.bad`, other formats, queue without the
  song, last change wins with duplicates kept, empty queue and incognito save nothing, clear deletes, 1000-song
  window. New API, so pass-after only (pure logic, stated).
- Device: `QueuePersistenceDeviceTest` (two phases, `scripts/device-check.sh`), APIs that existed before
  G6-QP only, so the same file runs before and after. Fail-before: run 44251573024 on `ab/qp-before`
  (25b3bc9 + test). Pass-after: pending.
- Regression: BUG-1, G5-EP, G6-WL, G6-AF, G6-NC device tests on the fix commit.

## 9. Bug-hunt checklist (files touched)

- LastPlayed:
  - main-thread I/O: none on save; the read is on IO;
  - races: `AtomicReference` conflation; the executor is single-threaded;
  - leaks: daemon thread, no context kept (application context per call);
  - exceptions: write failures caught and logged without content.
- MusicPlayerManager: `persistQueue` wrapped in `runCatching`, so a save failure never reaches playback.
- HomeScreen / MTPlayerApp: an empty queue falls back to `[t]`.
- Backup / ResetCenter: the file is excluded from backups and deleted by the history reset.
