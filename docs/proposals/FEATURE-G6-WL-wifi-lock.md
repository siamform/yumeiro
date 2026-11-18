# G6-WL — Music streams ran without a Wi-Fi lock

Status: **Verified (emulator, both phones)** — fail-before 44217196977 (http wifiLock=false), pass-after 44219681816 (lock held only while the stream plays), CI 44218538378 green. Real stall prevention under Wi-Fi power save: hardware, not claimed. Group L2 G6 (Music). Found by code read, 2026-11-18.

## 1. Code read (head 3b6e2be)

- `service/PlaybackService.getOrCreatePlayer`: `setWakeMode(C.WAKE_MODE_LOCAL)` for every item, YouTube Music
  (`mtyt:` resolved to googlevideo HTTPS) included.
- Media3: `WAKE_MODE_LOCAL` holds only a CPU wake lock; `WAKE_MODE_NETWORK` also holds a Wi-Fi lock so Wi-Fi does not
  enter power save with the screen off while streaming
  ([C.WAKE_MODE_NETWORK](https://developer.android.com/reference/androidx/media3/common/C#WAKE_MODE_NETWORK())).
- `stream/StreamMediaFactory` line ~128 already picks NETWORK for network URIs on the video/stream player.

## 2. Competitors (ideas only)

- **InnerTune v0.5.10** `MusicService.onCreate`: `.setWakeMode(C.WAKE_MODE_NETWORK)` for all items.
- **Yumeiro's own video player** (StreamMediaFactory): per-URI choice.

## 3. Options

| Option | Pros | Cons |
|---|---|---|
| A NETWORK always (InnerTune) | one line | Wi-Fi lock while playing device files: battery for nothing |
| **B per item on `onMediaItemTransition`** (`PlaybackService.wakeModeFor`) | lock only while a stream plays; same rule as video | one listener |

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Play a YouTube Music song, screen off on Wi-Fi | Wi-Fi lock held while it plays; released on pause |
| Play a device song | CPU wake lock only (no Wi-Fi lock) |
| Queue mixes device and online songs | mode follows the current item |

## 5. Tests

Unit `service/MusicWakeModeTest` (rule). Device `MusicWifiLockDeviceTest`: file → local HTTP → file; `dumpsys wifi`
must list `ExoPlayer:WifiLockManager` only while streaming. Real stall prevention with the screen off needs real
Wi-Fi power save → hardware only (owner item if not reproducible on the emulator).
