# F-12 Dedicated vertical video player (proposal only; gated; ADR-018 proposed)

Status: **proposal, not built.** Part 8 gate not passed (4.4). ADR-018 (docs/SYSTEM-DESIGN.md) is
"Proposed". The owner approves before any code.

## 1. Current code (read 2026-11-18, head 76daea1)

- Shorts exist as data and a shelf: `youtube/YouTube.kt:357` `YtItem.isShort()` (≤ 60 s or `/shorts/`),
  `ui/screens/youtube/YouTubeScreens.kt:185-187, 462, 490-494` (`youtube_shorts_shelf`, `YtShortCard`).
  Tapping a short calls `onPlay(s, shorts)`, i.e. the **normal** player (`VideoPlayerModal`) with a queue,
  portrait layout, `RESIZE_MODE_FIT` (`VideoPlayerModal.kt:299, 311`).
- One player per playback: `stream/StreamMediaFactory.kt:113` (`YumeiroRenderersFactory`),
  `service/PlaybackService.kt:50`. Media3 1.11.1 (`gradle/libs.versions.toml:12`). No preload manager.

## 2. Sources read (ideas and contracts only)

- venky145/RN-VideoFeed at ee2394f0e27a94f5bf8f78f9499b8a59c6fb2b58 (MIT):
  `rn-videofeed/android/.../VideoFeedPlayerPool.kt`: LRU pool of ExoPlayer instances keyed by video id,
  `maxSize = 8`, each `prepare()`d, positions kept; `releaseAll` on leave.
- manjees/compose-reels at be0373c090b8ebaff89ce08ee90f7b8e38d2aae7 (Apache-2.0): `ReelsConfig.kt`
  `preloadCount = 2`, `playerPoolSize = preloadCount*2 + 3` (7), long-press 2× speed, pinch zoom, infinite
  scroll; `player/PlayerPool.kt` acquire/release; `player/VideoCache.kt` 150 MB `SimpleCache` LRU;
  `ui/ReelsPager.kt` vertical pager.

Weaknesses for Yumeiro: 7–8 live ExoPlayers each hold decoders and buffers; low-RAM phones have 1–2
hardware decoder instances and get `MediaCodec` failures or OOM; a 150 MB cache competes with downloads.

## 3. Design

- New `ShortsPlayerScreen` (vertical pager, full-bleed, `RESIZE_MODE_ZOOM` for 9:16, `FIT` for others),
  opened from the Shorts shelf and from any queue whose items are vertical.
- **One active ExoPlayer**, reused across pages; next/previous items **preloaded as media sources** with
  Media3 `DefaultPreloadManager` (buffers only, no decoder), 2 ahead / 1 behind on normal devices, 1 ahead
  on `DeviceClass.isLowEnd`. Positions kept per item in memory.
- Disk cache: the existing player cache, cap 64 MB for shorts (owner may raise), not a second cache.
- Gestures: swipe up/down next/previous, tap pause, double-tap like is **not** added (no account action),
  long-press 2× (same as the normal player's speed control), volume/brightness swipes off here.
- Exits to the normal player with "Full player" for subtitles, quality and download.
- TV: not offered (D-pad up/down already moves rows); the normal player is used.
- Flag `shorts_player` with kill switch → normal player as today.

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Tap a short on the shelf | Vertical player opens at that short; next is preloaded |
| Swipe up fast several times | Each page shows its poster at once; only the settled page plays |
| Swipe back | Previous resumes at its position |
| Network slow | Poster + spinner; preload skipped; no stacked requests |
| Low memory / trim | Preloads dropped, active player kept |
| Background / screen off | Paused (no background play for shorts unless the user picks Full player) |
| Rotate | Stays portrait for 9:16; landscape items use the normal player |
| Kill and restore | Reopens at the same short and position |
| Decoder error | Existing fault ladder (software fallback), then skip with a message |
| Kill switch off | Normal player with queue (current behaviour) |

## 5. Pros and cons

- One player + preload manager: + one decoder, fits low-RAM; − first frame on swipe slightly later than a
  pool of hot players (measured target below).
- Reuse existing cache: + no extra disk pressure; − shorts may evict other cached media (cap).
- Separate screen: + focused UI; − another player surface to test (parity tests below).

## 6. Tests (when approved)

Unit (pure): preload window choice by device class; page settle logic. Device: `ShortsPlayerDeviceTest` on
api33-lowram and api36: 30 rapid swipes, one decoder in use (`dumpsys media.codec` count), no ANR, first
frame after settle ≤ 600 ms on api36 / ≤ 1200 ms on api33-lowram with the local fixture server; kill and
restore; parity: `PlayerSwitchDeviceTest`, `OnlinePlaybackDeviceTest` unchanged. Fail-before: no
`ShortsPlayerScreen` (test cannot open it).

## 7. Owner decision

Approve ADR-018 (one player + preload), entry points (YouTube Shorts shelf, vertical queues), cache cap.
