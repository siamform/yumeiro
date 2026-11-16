# BUG-3: Download speed collapses near the end (Step A.5 note)

Status: investigation note, written before any fix. Ledger row: BUG-3. Date: 2026-11-16.
Owner report: fast start, a sharp drop after about 50–70 %, and the last part takes 4–5× longer.

## 1. Implementations read

| Project | Version read | Files |
|---|---|---|
| CloudStream (recloudstream/cloudstream) | master @ `5d66bbeaf3b35c0c18ad2e0b9ce6b0aea4f5241c` | `utils/downloader/DownloadManager.kt` (`streamLazy`, `LazyStreamDownloadData.resolve/resolveSafe`, `downloadThing`, `downloadHLS`), `services/VideoDownloadService.kt`, `services/DownloadQueueService.kt` |
| Nuvio (NuvioMedia/NuvioMobile) | cmp-rewrite @ `04cfe4af95261873412c33791546a53e2b37e786` | `composeApp/src/androidMain/.../downloads/AndroidDownloadTransfer.kt`, `DownloadsPlatformDownloader.android.kt`, `DownloadsTransferWorker.kt` |
| Yumeiro | `r15-stability` @ 9401a1c | `online/download/DownloadWorker.kt`, `ForceGrab.kt` (`Segmented`, `DashGrab`), `HlsDownloader.kt`, `YtDownloads.kt`, `OnlineDownloads.kt` (`progress`), `network/NetworkClient.kt` |

Licences: CloudStream GPL-3.0, Nuvio (ideas only), Yumeiro GPL-3.0. No code copied.

## 2. What each does, and its weakness

CloudStream (direct files)
- HEAD, then a `Range: bytes=0-1023` probe; 10 MiB chunks over 3 parallel connections
  (setting), but each chunk request is open-ended (`bytes=start-`) and the reader stops at the
  chunk end. Out-of-order chunks wait in RAM (cap 50 MB of not-yet-written data, then the
  workers sleep 500 ms) and are written in order to one stream.
- Weakness: an open-ended range makes the server send the rest of the file; closing it early
  wastes the connection (no keep-alive reuse) and can count against per-connection quotas. The
  RAM queue (up to 50 MB) is dangerous on 2 GB phones. A failed chunk retries 3× and then fails
  the whole download.
- HLS: segments in parallel (3), in-order write through a pending map.

Nuvio
- One connection only: `Range: bytes=offset-` with `If-Range` (ETag / Last-Modified validator),
  `Accept-Encoding: identity`, 64 KB buffer, `fd.sync()` at the end, 60 s timeouts.
- Weakness: one request for the whole file. A host that is fast only for the first bytes of a
  request (nginx `limit_rate_after`, googlevideo) gives exactly the owner's curve: fast, then a
  long slow tail. Strength worth copying: `If-Range`, so a resume never glues two different files.

Yumeiro (code read, not yet measured)
- `Segmented`: `bytes=0-0` probe; ≥ 16 MB files in 4 MB closed ranges over 4 connections (2 on
  low-end); finished chunks in a `.chunks` side file; retries 4× with backoff; a file is
  pre-sized with `RandomAccessFile.setLength` and written at offsets.
- `single`: one request when the host has no ranges or the file is < 16 MB.
- YouTube (`YtDownloads.fetch`): one connection, sequential 8 MB ranges; video first, then audio.
- HLS: one connection, segments one after another.
- DASH split tracks: video and audio at the same time, segment by segment.
- No Wi-Fi lock and no partial wake lock of its own (WorkManager holds a wake lock while the
  worker runs; nothing keeps the Wi-Fi radio out of power save when the screen goes off).
- No `If-Range` on resume.

## 3. Candidate causes (to be confirmed or ruled out by measurement)

| # | Candidate | Where | Fits the owner's curve? | How it is tested |
|---|---|---|---|---|
| T1 | One request for the whole body on a burst-then-cap host | `single` (no ranges or < 16 MB) | Yes: fast start, slow tail | Local server "burst" profile |
| T2 | Long YouTube ranges: each 8 MB request is fast at first, then capped | `YtDownloads.fetch` | Partly: saw-tooth, slower overall | Local server "burst" profile |
| T3 | Last wave: fewer chunks left than connections | `Segmented` | Only for small files | Local server "steady" profile (6 chunks, 4 connections) |
| T4 | HLS one segment at a time (latency per request) | `HlsDownloader` | No (steady), but slow overall | Both profiles |
| T5 | Screen off: Wi-Fi power save without a Wi-Fi lock | `DownloadWorker` | Yes, if the screen turns off mid-download | Not reproducible on an emulator (no Wi-Fi PSM); needs a phone |
| T6 | Progress updates on every read (64 KB) from every connection | `OnlineDownloads.progress` | No (cost is flat) | CPU profile in the same harness |

## 4. Measurement (first step, before any fix)

`app/src/test/.../online/download/DownloadThroughputTest.kt`: a local HTTP server with real
`Range` support and two profiles (steady per-request cap; burst then cap per request). Every
engine downloads the same 24 MB file; the test prints `THROUGHPUT engine=… per10pctMs=[…]
tailRatio=…` (time of each 10 % and the last-three-tenths / first-three-tenths ratio) and checks
every byte. Engines: `Segmented` 4 and 2 connections, YouTube 8 MB ranges, HLS sequential,
single connection (host without ranges).

Then a real host where allowed: a public test file, the same per-10 % log, on the CI emulator.

## 5. Target (better than both)

- Stable throughput to 100 %: closed ranges sized so each request stays in the host's fast
  part, work stealing at the end (split the last big chunk across idle connections), HLS
  segments over several connections with in-order write (bounded, unlike CloudStream's 50 MB).
- `If-Range` on every resume (Nuvio), so a changed file restarts instead of corrupting.
- A Wi-Fi lock (and the worker's wake lock) only while bytes are moving, released on pause,
  failure and completion (T5), if the phone test confirms it.
- Progress throttled to a few updates per second.

## 6. What could break, and the parity tests

- Resume of an old `.chunks` side file after a chunk-size change: `DownloadResumeTest`,
  `ForceGrabTest.liveSegmentedResumesFromTheChunkFile`.
- Byte-exact output: every engine in `DownloadThroughputTest` compares the full content.
- Low-end memory: no in-RAM queue larger than one chunk per connection.
