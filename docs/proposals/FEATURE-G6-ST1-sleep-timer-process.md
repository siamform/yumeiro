# G6-ST1 — Music sleep timer stops working when the screen goes away

Status: **Verified (emulator, both phones)** — fail-before 44215701198, pass-after 44217196977 (paused at 60.35 s, api33-lowram and api36). Group L2 G6 (Music).
Found by code read, 2026-11-18.

## 1. Code read (head 0a47b48)

| Step | Code | What it does |
|---|---|---|
| Timer | `ui/viewmodel/MainViewModel.kt` ~459 | `SleepTimer(scope = viewModelScope, …, pause = playerManager::pause)` |
| Screen gone | `MainViewModel.onCleared` → `playerManager.release()` | `viewModelScope` cancelled → the countdown job is cancelled |
| Music | `service/PlaybackService` | the shared ExoPlayer keeps playing (onTaskRemoved stops only when idle) |
| Video timer | `player/VideoSleepTimer` | already a process object with its own scope (precedent) |

Same root cause as G5-PC1/PC2: work that must follow playback lived with the screen.

## 2. Competitors (ideas only, nothing copied)

- **InnerTune v0.5.10** `playback/SleepTimer.kt`: created in `MusicService.onCreate` with the service scope and the player.
- **Metrolist v14.0.0** `androidApp/.../playback/MusicService.kt`: `SleepTimerController` owned by the service.

Idea taken: the timer belongs with the player, not the UI.

## 3. Options

| Option | Pros | Cons |
|---|---|---|
| **A process object (`player/MusicSleepTimer`) on a main-thread scope, reading `PlaybackService.sharedPlayer`** | one instance for every screen; the existing `SleepTimer` class and UI unchanged; mirrors `VideoSleepTimer` | process death still ends it (the music ends too) |
| B move into `PlaybackService` | service lifetime | the service is created later than the UI asks; UI needs a binder or static |
| C keep in ViewModel, cancel on clear | none | the bug |

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Set 30 min, swipe the app from recents | music fades over the last 20 s and pauses at 30 min |
| Set "end of song", Back-exit | pauses at the end of that song |
| Reopen the app while the timer runs | the sheet shows the same countdown (same instance) |
| Timer fires while the stream is buffering | playback is stopped (pause no longer skipped when `isPlaying` is false) |

## 5. Tests

Device `SleepTimerWithoutScreenDeviceTest` (one song on repeat, 1-minute timer, screen closed; paused between 58
and 100 s). Fail-before 44215701198 at 0a47b48: still playing after 100 s on api33-lowram and api36.
