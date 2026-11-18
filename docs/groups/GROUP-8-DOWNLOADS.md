# Group 8 audit: Downloads (R17)

Scope (master handover, Group 8): queue and concurrency, worker vs foreground service, notifications;
HTTP ranges, resume after a kill, network change, disk full, duplicates, cancellation; HLS/DASH
merge, torrent, forced downloads, external downloaders; scoped storage / MediaStore, list integrity.
Method: local server that cuts connections, throttles and returns bad ranges; real host; kill
mid-download. BUG-3 (speed) is in docs/proposals/FEATURE-BUG-3-download-throughput.md.

Branch `r15-stability`. Earlier rounds: R16 DL-2..DL-5 (ledger "R16: Groups 8–10"), R15 DL-1
(forced downloads), BUG-3 (RequestPace 3c85a72, split 40ea96e, ProgressGate 24ee21b).

## Code read (R17)

`online/download/`: DownloadWorker (+ DownloadGate, DiskFull, HttpStatus), OnlineDownloads (list,
enqueue, start/pause/delete, save), ForceGrab (Segmented, DashGrab, side files), HlsDownloader,
YtDownloads (8 MiB range fetch, MediaStore publish), ExternalDownloader, DownloadSettings,
DownloadNaming, MediaMux.

## Problems found and fixed (R17)

| # | Problem | Root cause | Fix | Test |
|---|---|---|---|---|
| DL-6 | A finished download could read back as running after a restart and start again | `OnlineDownloads.write()` ran outside any lock from every worker; a slower writer could rename an older list last; all shared one `.tmp` | Saves serialized, list read inside the lock (3aa1623) | `DownloadListSaveRaceTest` (8 threads × 40 rounds) |
| DL-7 | Deleting a split video/audio or segmented download left its half-done tracks on the phone | Side files are named after the `.part` file (`.part.v`, `.part.a`, `.part.chunks`, `.part.idx`, `.part.len`); delete removed `x`, `x.part`, `x.v`, `x.a` only. A piece mid-read at cancel could also write after delete; a finish right after delete left an untracked file | `OnlineDownloads.removeFiles` (prefix `x.`), cleanup in the worker on cancel and before the rename (0c27614) | `DownloadDeleteFilesTest`; device `DownloadKillDeviceTest#c` |
| DL-8 | Settings → Downloads → Quality did nothing for HLS sources | `HlsDownloader.bestVariant` always took the top bandwidth | Best at/under the limit, then unknown, then smallest above, like links and DASH (7436a94) | `OnlineExtrasTest#hlsVariantFollowsTheDownloadQualitySetting` |
| DL-9 | A connection that drops now and then failed the download (and waited for WorkManager backoff) | Segmented counted every drop towards 4 tries even when bytes came in; YtDownloads.fetch had no retry | Only drops with no bytes count; YouTube resumes from the file end (commit "a dropped connection resumes…") | `DownloadThroughputTest#droppedConnectionsResumeInsideOneRun` (server drops every connection after 700 KB) |
| DL-12 | The Downloads page read its list on the main thread when it opened, and every tap (keep, pause, resume, delete) saved the whole list with an fsync on the main thread; deleting a folder did it once per episode and also removed the files and the MediaStore row there | `DownloadsScreen` loaded the list in a `LaunchedEffect` without a dispatcher (LB-7 fixed only the library tabs); `OnlineDownloads.set/update` call `write()` (SafeJsonFile: write + fsync + rename) on the caller's thread; `delete` removed files and called `ContentResolver.delete` (binder) on it; `play` ran `mkdirs` + `exists` | Page load and `play` on `Dispatchers.IO`; a save from the main thread goes to one background thread (queued saves folded, each takes the newest list inside `writeLock`, so DL-6 holds); `delete` removes files off the main thread after the list update; workers still save before they return (a501d8e) | `DownloadsScreenDeviceTest` (StrictMode listener; open, play, keep, delete folder; then the list is read back from disk) |

## Checked and not a bug

- D6 YouTube downloads (YtDownloads, YtDownloadOptions) read: `download` always takes fresh links (`YouTube.info(force=true)`), 8 MiB ranged fetch with drop resume (DL-9), MediaMuxer join of video-only + audio-only, `publish` to Movies/Yumeiro or Music/Yumeiro with `IS_PENDING`. No finding. The pipeline after extraction is checked on a device by `YtDownloadPipelineDeviceTest`; extraction itself cannot run on CI (YouTube asks data-centre IPs to sign in, see `YouTube.seedForTests`), so a real YouTube download is owner test OMT-D6 (real phone, home network).
- Enqueue (`enqueue`, `enqueueEpisodes`, `enqueueYouTube`) checks `isSaved` (one `exists`) for an item already in the list, from the main thread. One stat per already-saved episode; left as is (not a stall), recorded here.

- Queue/concurrency: `DownloadGate` limits running downloads to the setting and one per extension;
  YouTube has no per-site key. A waiting worker holds no foreground notification.
- Worker vs service: downloads are WorkManager `CoroutineWorker`s with `setForeground` (type
  dataSync); network loss stops the worker (constraint CONNECTED) and DL-2 puts it back to QUEUED.
- Duplicates: `enqueue` / `enqueueYouTube` / `enqueueEpisodes` refuse an item that is saved or active
  ("Already downloaded" / "Already downloading"); a failed one is replaced.
- Ranges: `RangeResume.decide` (DL-4); Segmented demands 206 for every piece.
- Disk full: up-front `checkSpace` with a 64 MB margin and ENOSPC mapping (`DiskFullTest`); no retry
  on a full disk; the `.part` stays for a later resume.
- MediaStore publish (YouTube): `IS_PENDING` until copied; on failure the row is deleted and the app
  copy kept.
- Torrent: only through the user's TorrServer (HTTP); without it a clear message.

## Observations (not changed)

- The YouTube engine uses 8 MiB requests without RequestPace (BUG-3 note §4.3).
- A 32-bit `hashCode` item id could in theory collide for two titles; not seen.

## Verification

| Kind | Run | Result |
|---|---|---|
| unit | CI 43871066269 (DL-6..DL-9 tests) | pass |
| unit, fail-before | CI 43867540147 (fail-before branch: DL-6 race test and DL-9 drop test without the fixes) | fail, as expected |
| emulator | device 43867796913 `DownloadKillDeviceTest` (kill at 25 %, continue, delete at 10 %) | resume + delete pass both; data-used rule failed on api36 → DL-11; kept-chunks rule pass 44039053465, 44041714032 |
| emulator | 44043535616 `DownloadCleanupDeviceTest` (series folder, auto-delete timer, Permanent vs Offline, daily schedule), repeat 3 | pass 3/3 both |
| emulator | 44043535616 `DownloadThroughputDeviceTest#d` (BUG-3 case d, 24 MiB, 3 MiB burst then 150 KiB/s), repeat 3 | pass 6/6: total 3.4–7.2 s, last 10 % 0.1–1.7 s (old code 44037723917: 19.99 s, last 10 % 6.3–6.5 s) |
| emulator | 43742375420 `DownloadResumeDeviceTest` (R16) | pass both |
| emulator | 43861039865 `DownloadThroughputDeviceTest` (BUG-3, real host, Doze) | pass both |

## Limits and owner tests

- Wi-Fi power save with the screen off: OMT-14 (emulator has no Wi-Fi power save).

## v1.8.2 full audit (2026-11-18, master prompt §7, owner rule §13a)

Head at the read: 6a7d8fb (download code unchanged since 7a85df6). Method: every file below read line by line;
competitors read at exact versions (ideas only, no code copied); one regression test per finding, all in one
commit; one fail-before run for the group; one fix commit per finding; one pass-after; group regression.
Skills: architecture-designer (ADR-020 before code), kotlin-specialist (every Kotlin change). UI: no UI change.

### Feature map and data flow

```
Download tap ─ DownloadPrompt.ask (where, Permanent/Offline) ─ OnlineDownloads.enqueue / enqueueEpisodes /
  enqueueYouTube / downloadCurrent / StreamDownload.enqueue ─ list (online_downloads.json, SafeJsonFile) ─
  WorkManager unique work (CONNECTED) ─ DownloadWorker.doWork (IO) ─ DownloadGate (parallel setting, one per
  extension) ─ run(): setForeground(dataSync) ─ [EPISODE: resolveEpisode → link + file name] ─ engine:
    YT_*      → YtDownloads.download (8 MiB ranges, MediaMux join, MediaStore publish)
    isDash    → DashGrab (plan → per-track segments or Segmented → MediaMux)
    audioUrl  → video (Segmented or HlsDownloader) ∥ audio (Segmented) → MediaMux
    isHls     → HlsDownloader (variant → segments, AES-128, resume record)
    torrent   → TorrServer HTTP → Segmented
    else      → Segmented (probe, 4 MiB chunks, splits, RequestPace/PaceGate, .chunks record)
  ─ `.part` → rename → afterDownload (Play link / YouTube publish) → finished → notification
Delete / pause → WorkManager cancel → CancellationException → stopped / removeFiles (DL-7)
Cleanup worker (Offline items) · DownloadsScreen (list, play, keep, delete, folders) · ExternalDownloader
```

### Files and lines reviewed

| File | Lines | Notes |
|---|---|---|
| online/download/DownloadWorker.kt | 1–334 | doWork, run, resolveEpisode, forced, notifications, DownloadGate |
| online/download/ForceGrab.kt | 1–714 | chunks/readDone, Piece, RequestPace, PaceGate, Segmented (probe, whole, once, fetchPiece, single), DashGrab (plan, download, saveTrack, fetchRetry) |
| online/download/HlsDownloader.kt | 1–181 | download, bestVariant, parseMedia, AES-128, HlsResume |
| online/download/OnlineDownloads.kt | 1–427 | dir/fileOf/isSaved, enqueue*, start/pause/delete, removeFiles, save queue (DL-12) |
| online/download/YtDownloads.kt, YtDownloadOptions.kt | 1–207, 1–98 | fetch, publish, afterDownload, linkMime |
| online/download/MediaMux.kt | 1–68 | merge |
| online/download/DownloadNaming.kt, DownloadSettings.kt, DownloadGroups.kt, ExternalDownloader.kt, SongDownloads.kt | all | naming/extension, settings, folders, chooser |
| ui/screens/downloads/DownloadPrompt.kt, DownloadsScreen.kt, StorageState.kt | all | prompt, list UI |
| ui/screens/online/DownloadSheets.kt, ui/screens/settings/DownloadSettingsCard.kt, stream/StreamDownload.kt | all | entry points, settings UI (G9 audits the setting effects) |

### Action → reaction

| Action | Expected reaction | Checked by |
|---|---|---|
| Download a plain file | 4 connections (2 low-RAM), exact bytes, resumable | DownloadThroughputTest, DownloadKillDeviceTest |
| Kill the app mid-download | resumes from the record, no repeated bytes | DownloadKillDeviceTest, DownloadResumeDeviceTest |
| Split v/a or DASH, one track fails, retry | finished track not fetched again | **G8-3** (was: fetched again) |
| HLS with #EXT-X-BYTERANGE | each range once, file = source | **G8-1** (was: N× the file) |
| HLS with a separate audio rendition | saved with audio | **G8-2** (was: silent) |
| HLS fMP4 (EXT-X-MAP) | saved/published as .mp4 | **G8-6** (was: .ts, video/mp2t) |
| Pause during an HLS segment retry | stops at once | **G8-5** (was: up to 7.5 s more) |
| Kill between the DASH side-file writes | no repeated segment | **G8-4** (was: segment appended twice) |
| Not enough space for tracks + join | "Storage full" up front | **G8-7** (was: both tracks fetched, join fails/retries) |
| One read stalls on a healthy host | request kept (no wasted bytes) | **DL-11 part 2** (was: restart, bytes thrown away) |
| Host caps each request (BUG-3) | request restarted | DownloadThroughputTest burst/steady, DownloadThroughputDeviceTest#d |
| Disk full | "Storage full …", no retry | LowStorageDeviceTest, DiskFullTest |
| Delete mid-download | files and side files gone | DownloadKillDeviceTest#c, DownloadDeleteFilesTest |
| Network lost | QUEUED, resumes when back | DownloadResumeTest (DL-2) |

### Advantages and disadvantages of the current design

- Advantages: one worker per item with WorkManager constraints; per-extension gate (site bans); engines resume;
  RequestPace beats per-request caps (BUG-3); every list save serialized (DL-6) and off the main thread (DL-12).
- Disadvantages: four engines with four record formats; HLS parser hand-written (no renditions, no byte ranges,
  first MAP only); join happens after both tracks, so space and failures surface late; 32-bit item ids.

### Findings register

| ID | Finding | Root cause (file:line at 6a7d8fb) | Fix (ADR-020) | Test (fails on the old code) |
|---|---|---|---|---|
| G8-1 | HLS byte-range playlists saved the whole file once per range (N× size, corrupt) | `HlsDownloader.parseMedia` (≈120–137) ignores `#EXT-X-BYTERANGE` and MAP `BYTERANGE`; `fetchBytes` sends no Range | parse ranges (offset continuation), Range request, slice a 200 | unit `G8DownloadsBatchTest#g8_1_hlsByteRangesSaveEachRangeOnce` |
| G8-2 | HLS variant with separate audio rendition saved without sound | `bestVariant` (≈96–113) returns only the variant URI; EXT-X-MEDIA AUDIO is never read | fetch the rendition, MediaMux join, `.mp4` | unit `#g8_2_hlsSeparateAudioRenditionIsFetched`; device `DownloadG8DeviceTest#a_hlsAudioRenditionIsSavedWithTheVideo` |
| G8-3 | A finished track was downloaded again when the job ran again | `Segmented.once` deletes `.chunks` at the end (373); `HlsDownloader` deletes `.idx` (79); `DashGrab.saveTrack` deletes `.idx/.len` (683) | records kept; the worker removes `<part>.*` after the rename | unit `#g8_3_segmentedFinishedFileIsNotFetchedAgain`, `#g8_3_hlsFinishedTrackIsNotFetchedAgain`, `#g8_3_dashFinishedVideoTrackIsNotFetchedAgain` |
| G8-4 | DASH resume after a kill could append a segment twice | `saveTrack` writes `.len` then `.idx` in two calls (680); a kill between them resumes at the old index with the new length | one `count:bytes` record (HlsResume), old record restarts the track | unit `#g8_4_dashResumeAfterAKillBetweenTheTwoSideFilesRepeatsNoSegment` |
| G8-5 | Pause/delete during an HLS segment retry kept the worker running up to 7.5 s | `fetchBytesRetry` uses `Thread.sleep` (≈84–90), not cancellable | suspend + `delay` | unit `#g8_5_hlsRetryBackOffStopsWhenTheDownloadIsCancelled` |
| G8-6 | fMP4 HLS saved as `.ts` and published as video/mp2t | `DownloadNaming.extensionFor` (48) decides `ts` before the playlist is read | rename to `.mp4` when the stream has EXT-X-MAP (as the WebM rename) | device `DownloadG8DeviceTest#b_fmp4HlsIsSavedAsMp4` |
| G8-7 | Split v/a that fits per track but not with the join fetched both tracks, then failed at the join (wrong message, retries) | `DownloadWorker.forced` `space` (≈190) is called per track; the join output is not counted | one shared check: 2 × (both tracks' bytes to fetch) + margin | device `DownloadG8DeviceTest#c_splitTracksCountTheJoinInTheSpaceCheck` |
| DL-11 p2 | A single stalled read restarted a healthy request; the bytes in flight were thrown away (real host 1.1004× once) | `RequestPace.slowed` (≈211–232) judges a window by total wait, not by its shape | one window of grace when one read holds ≥ 60 % of the window's wait | unit `DownloadThroughputTest#aSingleStallIsRiddenOutNotRestarted`; device budget `DownloadThroughputDeviceTest` a + d |

Checked and not a finding (this audit): enqueue `isSaved` stat on the main thread (R17 decision kept: one
`exists` for an already-saved item); DownloadGate waits off the foreground; WorkManager 10-minute limit only
reschedules a waiting worker; torrent through TorrServer uses Segmented with ranges; MediaStore publish keeps
`IS_PENDING` until copied.

### ADRs

ADR-020 (this audit); ADR-016 (DL-11) amended by the stall grace.

### Verification (labels: unit = CI; emulator = device-check, both phones)

| Item | Fail-before | Pass-after | Label |
|---|---|---|---|
| G8-1…G8-5, DL-11 p2 (unit) | pending (one CI run, temp branch: old code + tests) | pending | unit |
| G8-2, G8-6, G8-7 (device) | pending (one device run, same branch) | pending | emulator |
| DL-11 p2 real-host budget | 44121429260 (one 1.1004×) | pending: `DownloadThroughputDeviceTest` a + d ×3 | emulator, real host |
| Group regression | — | pending: full download inventory, both phones + TV images | emulator |

### Owner-only tests

- OMT-14 Wi-Fi power save with the screen off (emulator has none). OMT-D6 real YouTube download (CI IPs blocked).
- New: OMT-G8a a real HLS site with separate audio (e.g. a multi-language anime source) on a real phone: the
  saved file plays with sound in a gallery app.

### Competitor research (exact versions, read line by line; ideas only)

| Topic | CloudStream 2dcc25c3945c | media3 1.11.1 (8c6678b657ed) | NewPipe 853917509100 | aria2 9e7273583f83 |
|---|---|---|---|---|
| Byte ranges | parser has them; downloader (`M3u8Helper`) does not use them | `HlsPlaylistParser` 207–212, 884–967 (offset continuation); `HlsDownloader` 302 DataSpec(offset, length) | — | — |
| Audio renditions | `M3u8Helper` 283–293: only `isPlayableStandalone` variants, else error | downloads variant + rendition playlists (`HlsDownloader` 223–229) | DASH/YT: separate audio muxed in post-processing | — |
| Container | output always mp4 (`DownloadManager` 1282) | — | mux to mp4/webm | — |
| Resume record lifetime | — | cache index | `DownloadMission` 380–394: metadata deleted only after post-processing | `.aria2` control file written to `__temp` then renamed (`DefaultBtProgressInfoFile` 209–221) |
| Slow connection | — | — | — | `DownloadCommand::checkLowestDownloadSpeed` 302–315: speed averaged over a window, not one read |

### Enhancements (proposals only, not built)

- Wi-Fi-only downloads setting (NewPipe has it). Ad segments in downloaded HLS are kept (the player's
  `HlsAdFilter` only filters playback). Confirm or undo for a single-row delete. Per-download speed limit.

### Code quality

- `OnlineDownloads.deleteFolder` is unused (dead code; removal is a cleanup for stability-v2). 32-bit `hashCode`
  ids. `DashGrab.bytes` holds a whole segment in memory. `MediaMux.merge` releases the first extractor only in
  `finally` after the muxer was created (a failing second `setDataSource` leaks the first until GC). Only the
  first EXT-X-MAP is written. These are recorded in UNVERIFIED-QUEUE.md (stability-v2), not changed in v1.8.2.

### CI usage

One CI run for the whole group fail-before, one for pass-after, one device run each for the device tests,
then one regression campaign (≤ 10 classes per run, ≤ 2 device workflows at a time).
