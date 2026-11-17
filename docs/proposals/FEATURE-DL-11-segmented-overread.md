# DL-11 part 2: segmented downloads read more than the file (Step A.5 note)

Ledger row: DL-11. ADR: ADR-016 (docs/SYSTEM-DESIGN.md). Date: 2026-11-17 (eighth cycle). Group G8, touches BUG-3.

## 1. Current code (read line by line, head 2cbb2ae)

`online/download/ForceGrab.kt`:
- `Segmented.once`: files of 16 MB or more are split into 4 MiB ranges and fetched over 4 connections
  (2 when `DeviceClass.lowEnd`). An idle connection splits the busiest piece (40ea96e).
- `fetchPiece`: one `Range: bytes=from-to` request per piece. Each read goes into `RequestPace.slowed`.
  A `true` result closes the response and asks for the rest of the piece in a new request
  (3c85a72, BUG-3; up to `MAX_RESTARTS` = 32 per piece, only with at least 512 KiB left).
- `RequestPace`: the baseline is the request's speed over its first MiB. After that, each 256 KiB window
  is timed by the **wall clock** between reads, and a window under a third of the baseline trips.

Everything between two `read()` calls counts as host time. That includes the disk write, the progress
callback, GC and CPU scheduling on a 2 GB phone. When the response is closed, the bytes already in the
socket buffers and on the wire are lost: about 2 MB per restart on the real host.

## 2. Evidence before the fix (emulator, real host cdn.kernel.org, 134.7 MB, screen on)

| Run | Phone | Pace restarts | Received / file | MB/s |
|---|---|---|---|---|
| 44036060683 | api33-lowram / api36 | 7 / 1 | 1.129 / 1.053 | — |
| 44037559115 | api33-lowram | 29 / 1 / 6 | 1.440 / 1.039 / 1.130 | 3.58 / 7.82 / 5.86 |
| 44037559115 | api36 | 11 / 3 / 1 | 1.163 / 1.060 / 1.051 | 7.28 / 10.55 / 11.23 |
| 44041714032 | api33-lowram | 13 / 7 / 0 | 1.243 / 1.136 / 1.031 | 6.1–15.7 |
| 44041714032 | api36 | 15 / 0 / 0 | 1.186 / 1.031 / 1.031 | 5.4–17.5 |

With 0 restarts the app received 1.031×, which is TLS/TCP overhead in `TrafficStats` uid rx.
cdn.kernel.org does not cap requests, so every restart here was a false trip. The low-RAM phone tripped
more often, and the run with the most restarts was the slowest. Both fit hypothesis H2 (our own pauses)
as well as H1 (the link dips for every connection at once). The fix covers both, and the new counters tell
them apart on the device.

## 3. Competitors (source read at the exact version; ideas only, no code copied)

| Project | Version | Files | What it does with a slow connection | Weakness for Yumeiro |
|---|---|---|---|---|
| aria2 (GPL-2.0) | master `9e7273583f83e881e3ec067b523ba88724088d2f` | `src/FileEntry.cc` `findFasterRequest`, `src/DownloadCommand.cc` `checkLowestDownloadSpeed`, `src/SocketCore.cc` (SO_RCVBUF) | Replaces a connection only when the fastest **other** connection's average × 0.8 beats it, after a 10 s start-up idle and at most once per 10 s (`lastFasterReplace_`). `--lowest-speed-limit` aborts. The receive buffer is an option (0 = OS). | Comparing against peers ignores link-wide dips (good), but when every request is capped at once (BUG-3's per-request cap) nobody is faster, so it never restarts. That is the owner's slow tail. |
| CloudStream (GPL-3.0) | `5d66bbeaf3b35c0c18ad2e0b9ce6b0aea4f5241c` | `utils/downloader/DownloadManager.kt` | Open-ended `bytes=start-` ranges; the reader stops at the chunk end and closes the response. | Every chunk end throws away whatever the server sent ahead (the same waste, on every chunk). There are no speed restarts at all. |
| Nuvio | cmp-rewrite `04cfe4af95261873412c33791546a53e2b37e786` | `AndroidDownloadTransfer.kt` | One request, `If-Range`; never restarts. | No waste, but it shows the BUG-3 curve on capped hosts. |

## 4. Options and choice (ADR-016)

| Option | Gain | Risk | Verdict |
|---|---|---|---|
| A. Today: one slow window by the wall clock | Fast on capped hosts | False trips from our pauses and link dips; 1.04–1.44× received | replaced |
| B. Two slow windows in a row (6e40982) | 1.04× served | Capped unit profile 67.6 → 17.8 MB/s (CI 44037788044) | rejected (BUG-3 regression) |
| C. Small SO_RCVBUF (1 MiB) | Less in flight per close | Caps a connection at buffer / RTT (10 MB/s at 100 ms) on flagships | rejected (speed loss on good links) |
| D. PaceGate: judge each restart by the new request's first MiB; stop after 2 that did not help and more than helped | Bounds H1 waste to ~2 restarts per download | A capped host whose restarts do not help would lose restarts (never seen: a fresh request on a capped host starts at burst speed) | **chosen** |
| E. Time each window by the wait inside `read()` (wall clock kept for the baseline) | Removes H2 trips; a capped host still trips because the read waits on the host | None measured; wait time ≤ wall time, so it can only lower trips | **chosen** |
| aria2-style peer compare | Ignores link dips | Never trips when all requests are capped (BUG-3) | rejected |

## 5. Action–reaction

| Event | Before | After |
|---|---|---|
| Disk write or GC pause of 200 ms inside a window, host steady | Request closed, ~2 MB lost | No trip (`ownPauses`+1) |
| Host caps each request after its burst (BUG-3, case d) | Restart; the new request is fast | Same; `paceHelped`+1 |
| Whole link dips for seconds | Each connection restarts on each dip | ≤ 2 useless restarts, then `paceOff=1` for this download |
| Link dips, then recovers inside the new request's first MiB | Restart | Counted as helped (no harm: the waste already happened) |
| Process killed mid-download | Chunks resume from `.chunks` | Unchanged (the gate is per download, in memory) |
| Piece under 512 KiB left / 32 restarts | No restart | Unchanged |
| Cancel / delete mid-download | `ensureActive` | Unchanged |

## 6. Budget (pass rule) and parity tests

- Unit (CI): `Dl11PaceTest` (pure). `DownloadThroughputTest.ownPausesAreNotAHostSlowdown`: 0 pace restarts.
  `linkWideDipsStopUselessRestarts`: ≤ 3 restarts. `burstThenCapPerRequest` and `steadyPerConnectionCap`
  keep the BUG-3 tail rule, and segmented MB/s may not drop more than 10 % against the same CI's old numbers.
- Device (emulator, both phones, `DownloadThroughputDeviceTest` a + d, repeat 3), budget on the
  real host: received ≤ 1.10× in each download, median ≤ 1.06× (1.031× is pure protocol overhead);
  case d keeps total ≤ 10 s and last 10 % ≤ 3.5 s (BUG-3 parity).
- Data safety: no storage format change. The `.chunks` side file and the resume path are untouched.

## 7. After the fix (4fa126e), measured

- CI unit pass-after 44106540469 (head 226c9c5): `Dl11PaceTest` 6/6; `DownloadThroughputTest` 6/6.
  `ownPausesAreNotAHostSlowdown`: paceRestarts=0 with ownPauses=10 (segmented2) and 5 (segmented4); served
  1.000× / 1.046× (fail-before 1.487×). `linkWideDipsStopUselessRestarts`: 2 restarts, paceWasted=2, paceOff=1,
  1.005×. Capped parity: segmented4 106.67 MB/s, segmented2 73.39 MB/s (old 81.63 / 68.77: no drop).
- Device 44110174701 (emulator, both phones, a + d, repeat 3), real host cdn.kernel.org:

| Phone | Received / file (3 runs) | Pace restarts | paceHelped / paceWasted / ownPauses | Median |
|---|---|---|---|---|
| api33-lowram | 1.117 / 1.112 / 1.051 | 7 / 5 / 1 | 6/0/2 · 4/0/8 · 1/0/6 | 1.112 |
| api36 | 1.101 / 1.120 / 1.031 | 5 / 6 / 0 | 5/0/3 · 4/1/3 · 0/0/0 | 1.101 |

  Case d (local, per-request cap) passed every run: total 3.6–5.3 s, last 10 % ≤ 1.63 s.
- **Budget not met** (each ≤ 1.10×, median ≤ 1.06×). Worst case fell from 1.440× to 1.120×; api33 median from
  1.130× to 1.112×; api36 median did not improve (1.053× before, 1.101× after). E works (ownPauses 0–8 trips
  no longer restart), but the remaining trips are counted as helped (the new request's first MiB was ≥ 1.5×
  faster), so D does not stop them.
- **New hypothesis H3 (to prove, not yet fixed):** the download client does not restrict protocols, so on an
  HTTP/2 host such as cdn.kernel.org the "connections" are streams on one TCP connection. A restart cancels a
  stream whose bytes are already in flight (counted in uid rx, thrown away), and a fresh stream on the warm
  connection always looks faster, so the gate calls it helped. The unit tests use MockWebServer over HTTP/1.1,
  which cannot show this. Cause logging added: `protocol=` in SEGMENTED_STATS. Next: one device run (a only) to
  read the protocol, then decide (for example HTTP/1.1 for segmented ranges, measured against case d and MB/s).
- Status: DL-11 part 2 stays **in progress**; ADR-016 stays accepted with the evidence not yet met.


## 8. H3 measured, option F (HTTP/1.1 for byte ranges)

- **Cause read:** 44116513171 logged `protocol=h2` on both phones (the job log cut the api33 field; the logcat
  artifact has `h2` on both). `Segmented` used the shared download client with no protocol limit, so both
  segments were streams on one TCP connection.
- **Experiment (56baf78, test-only switch, same run, same host, interleaved a/f/d, repeat 3), device 44118957078:**

| Phone | Run | h2 (old) received / file | h2 MB/s | h2 restarts | HTTP/1.1 received / file | HTTP/1.1 MB/s | HTTP/1.1 restarts |
|---|---|---|---|---|---|---|---|
| api33-lowram | 1 | 1.113 | 11.90 | 6 | 1.040 | 20.86 | 1 |
| api33-lowram | 2 | 1.154 | 14.96 | 8 | 1.030 | 20.18 | 0 |
| api33-lowram | 3 | 1.107 | 14.82 | 5 | 1.065 | 12.10 | 3 |
| api36 | 1 | 1.127 | 7.75 | 7 | 1.038 | 20.20 | 1 |
| api36 | 2 | 1.080 | 15.18 | 9 | 1.030 | 29.09 | 0 |
| api36 | 3 | 1.086 | 26.08 | 5 | 1.030 | 29.76 | 0 |

  Medians: h2 1.113 / 1.086 (budget missed); HTTP/1.1 1.040 / 1.030 (each ≤ 1.10, median ≤ 1.06: met). Speed
  median h2 14.82 / 15.18 MB/s, HTTP/1.1 20.18 / 29.09 MB/s. Case d (local server, already HTTP/1.1) PASS 6/6
  (4.2–5.2 s). The floor near 1.03× is TLS and header overhead in the uid counter (no restarts in those runs).
- **Why:** on one shared TCP connection the two streams split one congestion window; a stream that the server
  schedules later looks slow, the restart cancels bytes already in flight, and the new stream inherits a warm
  window, so the gate counts it as helped. With one TCP connection per segment a segment is slow only when its
  own path is slow, which is what the pace rule assumes.
- **Option F (decision):** `ForceGrab.rangeClient` limits byte-range downloads to HTTP/1.1. Every HTTPS host
  speaks HTTP/1.1; ALPN simply picks it. Connection pool, dispatcher, DNS and headers are shared with the base
  client (`newBuilder`), so there is no extra pool or thread on low-RAM phones.
- **Action–reaction:**

| Action | Reaction |
|---|---|
| Direct file ≥ 16 MB on an h2 host | Two TCP connections over HTTP/1.1, one per segment |
| A segment slows | Pace rule as before (D + E); fewer false trips |
| Host caps each request (BUG-3) | Restart still opens a new request; case d unchanged |
| Small file / no ranges | Same path on HTTP/1.1, one connection |
| Streaming, HLS/DASH, API calls | Unchanged (other clients) |

- **Pros:** budget met on both phones, faster, fewer requests. **Cons:** one more TLS handshake per download
  (about 1 RTT); a host that only allows one connection per IP would now see two (the old h2 path also opened
  two requests; the existing 1-connection fallback on refusal still applies).
- **Tests:** unit `Dl11PaceTest#rangeClientIsHttp1Only` (config, unit only, says so); device
  `DownloadThroughputDeviceTest` a (default = F), f (h2 control), d (BUG-3). Pass-after run on the fix commit.
