# F-07 External player support (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head 5f7f333)

- `ui/screens/video/VideoPlayerModal.kt` `handOffExternal()` (~line 1107): Settings → Quality and audio →
  Default player = "Another app" or "Ask". Builds `ACTION_VIEW` with the current media item's URI and MIME,
  extras `title`, `position` (Int, MX Player), `from_start` (VLC), read-URI grant, shows a chooser
  ("Play with"), then pauses and closes our player.
- `ui/screens/watch/StreamWatchPanel.kt:260`: "Open with" chooser for web streams.
- `ui/components/ErrorHelp.kt:119`: error card suggests the ⋮ menu external player.
- CloudStream extension API kept for extensions: `VideoClickAction` / `Plugin.registerVideoClickAction`.

So the base feature exists. Gaps found in the read:

| Gap | Effect for the user |
|---|---|
| No result read-back | Watching in VLC/mpv does not update Continue Watching / resume (BUG-1 path) |
| Request headers (Referer, User-Agent, cookies) not handed over | Many source links play in Yumeiro but 403 in the other app |
| Subtitles not handed over | Picked subtitle lost in the other app |
| Generic chooser only | No remembered "always VLC", no per-app extras |

## 2. Competitor (ideas and contracts only; no code copied)

CloudStream `recloudstream/cloudstream` at commit 2dcc25c3945c61dfa95d1710ced7c74acfe84baa,
`app/src/main/java/com/lagradost/cloudstream3/actions/temp/`:

- `VlcPackage.kt`: package `org.videolan.vlc`, result action `org.videolan.vlc.player.result`; extras
  `position` (Long), `from_start`, `secure_uri`, `title`, `subtitles_location`; reads back `extra_position`,
  `extra_duration`.
- `MpvPackage.kt`: `is.xyz.mpv` / `MPVActivity` (plus mpvEx, mpv YTDL variants); extras `subs` (Uri array),
  `title`, `position` (Int), `secure_uri`; reads back `position`, `duration` (Int).
- Also: `JustPlayerPackage.kt` (`com.brouken.player`), `NextPlayerPackage.kt`, `MpvKtPackage.kt`,
  `WebVideoCastPackage.kt`, `KodiPackage.kt`; a temp M3U8 for multi-link results (`makeTempM3U8Intent`).

## 3. Design (least risk)

- A small `ExternalPlayers` table (package, activity, extras builder, result parser) for VLC, mpv family, MX
  Player, Just Player; anything else stays on the generic chooser path we have today.
- Launch with `ActivityResultContracts.StartActivityForResult`; on result, write the returned position and
  duration through the existing `ResumePoints` / history write (off main), so Jump In and YumeMix update.
- Headers: only for players with a documented header extra; otherwise keep today's behaviour and show the
  existing "may not play in other apps" hint. Never put cookies or tokens in logs.
- Subtitles: pass the selected external subtitle URL where the player has an extra for it.
- Setting: "Default player" gets "Ask / Yumeiro / VLC / mpv / Other app" (remembered), TV included (VLC exists
  on Android TV).

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Play with VLC, watch to 12:30, back | Yumeiro shows Continue Watching at 12:30 |
| Player not installed | Toast "VLC is not installed"; stays in Yumeiro |
| Player returns no result | Position kept from hand-off time (today's behaviour) |
| Source needs headers, player cannot take them | Hint shown; Yumeiro player offered |
| TV remote | Chooser and setting reachable with the D-pad |

## 5. Pros and cons

- Pros: closes the resume gap for external playback; fewer 403s; small code (table + result contract).
- Cons: extras are per-app contracts that can change; more test surface (each player); result Intents can be
  spoofed by any app, so values are bounded (0 ≤ position ≤ duration ≤ 24 h) and only written for the item we
  launched.

## 6. Tests (when approved)

- Unit: extras builder and result parser per player (pure).
- Device: a stub "player" APK-less activity in androidTest that returns a result; assert history row updated
  after the result (fail-before on today's code: no update).

## 7. Owner decision

Approve scope (VLC + mpv + MX + Just Player, result read-back, subtitles; headers only where documented) or
narrow it.
