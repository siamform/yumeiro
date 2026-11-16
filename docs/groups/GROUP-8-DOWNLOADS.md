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

## Checked and not a bug

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
| unit | CI on the r15-stability head after DL-9 | pending |
| unit, fail-before | CI 43867540147 (fail-before branch: DL-6 race test and DL-9 drop test without the fixes) | pending |
| emulator | device 43867796913 `DownloadKillDeviceTest` (kill at 25 %, continue, delete at 10 %) | pending |
| emulator | 43742375420 `DownloadResumeDeviceTest` (R16) | pass both |
| emulator | 43861039865 `DownloadThroughputDeviceTest` (BUG-3, real host, Doze) | pass both |

## Limits and owner tests

- Wi-Fi power save with the screen off: OMT-14 (emulator has no Wi-Fi power save).
