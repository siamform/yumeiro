# Proposal: built-in torrent streaming

Status: **proposal, not built**. Everything here is free and open source; nothing needs a paid service.

## Why

Many extension ecosystems (Stremio-style addons, some CloudStream providers) return `magnet:` links or `.torrent`
files. Today Yumeiro can't play them. Built-in streaming would let a user start watching a torrent in seconds,
with the same player, subtitles, Skip intro and Continue watching as every other source.

## Options checked (November 2026)

| Project | What it is | License | Fit for Yumeiro |
|---|---|---|---|
| [libtorrent4j](https://github.com/aldenml/libtorrent4j) | Java/SWIG binding of libtorrent (C++). Android builds for armeabi-v7a, arm64-v8a, x86, x86_64. Sequential download, piece priorities, DHT, magnet links. | MIT | **Recommended engine.** In-process, small API, no extra app. |
| [LibreTorrent](https://github.com/proninyaroslav/libretorrent) | Full Android torrent client built on libtorrent4j, with streaming. | GPL-3.0 | Reference for service, notification and streaming code (license compatible with Yumeiro's GPL-3.0). Not a dependency. |
| [TorrServer](https://github.com/YouROK/TorrServer) | Go server that streams torrents over HTTP; Android binaries per ABI. | GPL-3.0 | Works, but each binary is about 64 MB per ABI (release MatriX.150), far too big to bundle. Could be supported as an **optional external server** (user installs [TorrServe](https://github.com/YouROK/TorrServe) and Yumeiro talks to its HTTP API). |
| [WebTorrent](https://github.com/webtorrent/webtorrent) | JavaScript, WebRTC peers. | MIT | Needs a WebView or Node runtime and only reaches WebRTC peers; poor fit for a native player. |

## Recommended design

1. **Engine**: `libtorrent4j` behind a `TorrentEngine` interface (so TorrServer can be a second backend later).
   Ship only arm64-v8a and armeabi-v7a native libs in the release APK (ABI splits keep each APK smaller;
   expect several MB per ABI — measure before deciding).
2. **Local stream server**: a tiny loopback HTTP server (`127.0.0.1`, random port, random path token) serves the
   chosen file with `Range` support. ExoPlayer plays `http://127.0.0.1:port/token/file.mkv` like any other link, so
   seeking, subtitles and the existing data sources keep working.
3. **Smooth start**: sequential download, with the first ~5 % and the last pieces at top priority (containers keep
   their index at the end); on seek, re-prioritise the pieces around the new position (deadline API).
4. **Foreground service** (`dataSync` type) with a progress notification while a torrent is active; it stops when
   the player closes unless the user chose "Keep downloading".
5. **Limits and safety**: cache in `cacheDir/torrents` with a size cap (default 2 GB) and LRU cleanup; Wi-Fi only by
   default; upload ratio cap; never seed in the background after the player closes unless the user asks.
6. **Picking the file**: in a multi-file torrent, pick the largest video file, or the one matching
   S01E02 / episode number (same matcher as the subtitle archive picker, `SubtitleArchive.score`).
7. **Opt-in**: off by default, with a one-time notice that the user is responsible for what they download and
   that their IP is visible to peers. No torrent search is built in; links only come from extensions the user
   installed.

## Network: DNS and "VPN rotation"

- **DNS**: Yumeiro already has `SmartDns` (DNS-over-HTTPS with several free providers and automatic fallback). Tracker
  and DHT bootstrap lookups can use it, which gets around ISP DNS blocks of tracker hosts.
- **VPN rotation is not feasible for free**: rotating VPN exits needs VPN servers, which cost money, and Android
  allows only one VPN app at a time. What we can do for free instead:
  - DHT + PEX + many public trackers, so blocked trackers don't matter much;
  - an optional SOCKS5/HTTP proxy setting for the engine (libtorrent supports it), for users who already have a
    proxy or VPN app;
  - "Only over VPN" toggle: pause torrents when no VPN network is active (`NetworkCapabilities.TRANSPORT_VPN`).

## Work estimate

| Step | Size |
|---|---|
| `TorrentEngine` + libtorrent4j integration, magnet → metadata → file list | M |
| Loopback HTTP server with Range + piece priorities | M |
| Foreground service, notification, cache limits, settings | S |
| Player / extension plumbing (`magnet:` and `.torrent` links in `OnlineRepository`) | S |
| Tests (piece prioritisation, Range math, file picker) and device testing | M |

Before building: confirm the native library size per ABI and test on a low-end phone (memory use of libtorrent).
