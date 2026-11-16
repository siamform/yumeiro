# BUG-1: watch progress not restored across sessions (Step A.5 note)

Status: investigation note, written before any fix. Ledger row: BUG-1. Date: 2026-11-16.

## 1. Implementations read

| Project | Version read | Files |
|---|---|---|
| CloudStream (recloudstream/cloudstream) | master @ `5d66bbeaf3b35c0c18ad2e0b9ce6b0aea4f5241c` | `app/src/main/java/com/lagradost/cloudstream3/utils/DataStoreHelper.kt` (`setViewPos`, `getViewPos`, `setViewPosAndResume`, `setLastWatched`, `migrateResumeWatching`, `PosDur.fixVisual`) |
| NewPipe (TeamNewPipe/NewPipe) | dev @ `1e61c9adacabe9ea59c06eee581d81350c7dcf8e` | `app/src/main/java/org/schabi/newpipe/player/Player.java` (`saveStreamProgressState` both overloads, `saveStreamProgressStateCompleted`, all call sites). `HistoryRecordManager.java` could not be fetched (fetch failed); not relied on |
| Yumeiro (this repo) | `r15-stability` @ 1515f60 | see section 3 |

Licences: CloudStream GPL-3.0, NewPipe GPL-3.0, Yumeiro GPL-3.0. Only ideas are taken; no code copied.

## 2. What each one does, and its weakness

CloudStream
- Position store: one key per episode id (`$account/video_pos_dur/<id>` → `PosDur(position, duration)`).
  "Resume watching" is a separate key per parent title (`result_resume_watching_2/<parentId>`) holding
  the episode id, season, episode number and time.
- Guard: `setViewPos` returns without writing when `dur < 30_000`. An unprepared player (duration
  unset or 0) can therefore never overwrite or delete a saved position.
- Near the end (`NEXT_WATCH_EPISODE_PERCENTAGE`) the resume pointer moves to the next episode, or is
  removed after the last one.
- Display only: `fixVisual` snaps ≤ 1 % to 0, ≤ 5 % to 5 %, ≥ 95 % to done. The stored value is exact.
- Migration: the resume folder was renamed when ids changed (`_2`), with a one-time copy from the old
  folder.
- Weakness: ids are `hashCode()`s of URLs. When a provider changes domain, the ids change and the
  resume is lost (that is why they needed `_2`). Two stores (position and resume) can disagree.

NewPipe
- Saves on every state change, not only on close: `play()`, `pause()`, `onPositionDiscontinuity`
  (seek, auto-advance), `onPlayerError`, quality and audio-track changes, previous/next, queue
  selection and `destroy()`. The progress loop does not save.
- Guard: `saveStreamProgressState()` returns when the player is null, no metadata is loaded, or the
  queue index differs from the player's media-item index. A save never lands on the wrong stream or
  happens before the stream is known.
- Finished: saves `duration + 1 s` so the item counts as watched.
- Weakness: no periodic save. If the process dies during playback with no pause or seek, everything
  since the last event is lost.

## 3. Yumeiro today (traced end to end)

Two stores:
- Device and YouTube videos: `MediaRepository.updateVideoProgress(id, pos, dur)` writes `pos_<id>` and
  `watched_<id>` in SharedPreferences. It deletes `pos_<id>` when `pos <= 0` or `pos ≥ 95 %`.
- Yumix (extensions): `OnlineHistory` (`online_history.json`). Positions are keyed by `api|episodeData`.
  "Continue watching" entries are keyed by `api|titleUrl`.

Save points (`VideoPlayerModal`): a tick every ~5 s while playing; ON_STOP (when position > 0); STATE_ENDED;
and `onDispose` with `lease.positionMs` / `lease.durationMs`.

Entry points:
1. Home → Jump back in (`MTPlayerApp` `homeResumeVideo`): device → `viewModel.playVideo(device)`, which
   resumes from `pos_<id>`. YouTube → `YouTubePlayback.playVideo(context, yt)`. Yumix → `openOnline(item)`
   → `OnlineSession.open` → `play(detail)` → `progressOf(api, playData)`.
2. Yumix → Continue watching (`ContinueRow`) → `onOpenTitle` → title page → `play(version, index)`. The
   episode is found by `resume.episodeData == episode.data`, and the position by `progressOf(api, data)`.
3. Yumix banners: the resume slide → `onOpenTitle` (same as 2); "Play" → `OnlineSession.open` (same as 1).

Defects found by reading the code (each still needs a failing test before it counts as a cause):
- D1 (YouTube, certain from the code): `YouTubePlayback.playVideo` calls `IncomingMedia.open(..., id =
  "yt:<id>")` without `positionMs`, so the item opens at 0. The position is saved (`pos_yt:<id>`) but
  never read back. `MainViewModel.playVideo` does not look it up either. YouTube videos never resume.
- D2 (all non-Yumix): the `onDispose` save passes `lease.positionMs` even when the player never
  prepared (position 0, duration TIME_UNSET). `updateVideoProgress` treats `pos <= 0` as "delete", so
  opening an item and leaving before it loads (for example on a slow network or when the stream fails)
  wipes its resume point. CloudStream's `dur < 30 s` guard and NewPipe's metadata guard both prevent this.
- D3 (all): there is no save on seek or pause. The test case "play, seek, kill" loses the seek unless
  5 s of playback follows it. NewPipe saves on `onPositionDiscontinuity` and `pause()`.
- D4 (Yumix): on close, `closePlayer` runs `OnlineSession.end()` (state = null) before the modal's
  `onDispose`, and `OnlineSession.onProgress` returns when the state is null. The last seconds are
  dropped, and so is a seek made just before closing.
- D5 (Yumix, to be proven): the resume lookups are exact-string matches on extension-supplied data
  (`episodeData`, `playData`, the title URL). Extensions often embed the site domain, which rotates, or
  per-session tokens in these strings. When the string changes, both the episode pointer and the
  position are lost. CloudStream has the same weakness through URL hash ids.
  Fix (2026-11-16): `OnlineHistory.hostFree` drops every `http(s)://host` (plain or JSON-escaped)
  and `progressOf` / `entry` fall back to (api, host-free data) when the exact key misses; a lazily
  rebuilt index keeps the lookup O(1) in episode lists. Per-session query tokens are NOT covered.

## 4. Yumeiro's target (better than both)

- One resume API: a `ResumePoints` facade used by every entry point. It reads `pos_<id>` for device and
  YouTube, and `OnlineHistory` for Yumix. No entry point passes 0 by default.
- Write guards (from both): no write when the duration is unknown or < 30 s, or when the position is ≤ 0
  because nothing loaded. Delete only when the item finishes or the user clears it.
- Save on events and on a timer: seek (discontinuity), pause, error, close, ON_STOP, plus the 5 s tick.
  This is stronger than NewPipe (no timer) and CloudStream (no seek/pause saves).
- Stable Yumix identity: fall back to (api, host-free title path, season, episode number) when the
  exact data string misses. This fixes domain rotation, which CloudStream ships with.
- Migration: the existing `pos_*` keys and `online_history.json` are read as they are. No format
  change, so no data loss. A parity test checks that an old file still resumes.

## 5. Tests that prove it (fail before, pass after)

- Unit: `ResumePointsTest`. A YouTube item opens at its saved position (D1). An unprepared
  dispose save leaves the old position (D2). Domain-rotated episode data still finds the position (D5).
- Unit: Yumix close-time save lands after `end()` (D4).
- Device: `ResumeAcrossProcessDeviceTest`. Play a local test clip, seek to 60 %, `am kill` the process
  (after it goes to the background), reopen through Home → Jump back in, and check the position is
  ≥ 55 %. For Yumix, a local fake extension served from a test HTTP server, through all three entry
  points.
