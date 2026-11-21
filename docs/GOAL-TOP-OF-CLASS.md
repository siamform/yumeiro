# Goal: Yumeiro at the top of its class

Owner, 2026-11-21: "yumeiro will be placed top of them, have to achieve everything over all competitors. Set the goal,
and make it happen."

## The goal

By 2.1.0, Yumeiro does everything the leading open-source media apps do, on phone and TV, plus the things none of them
do, and it does it faster on a 2 GB phone. "Done" for a row means Pass with a run ID (AGENT-START-HERE rules); a row
that is only in code is "Built, not tested".

Three measurable bars on top of the feature rows:

| Bar | Target | How it is measured |
|---|---|---|
| Start to first frame (direct link, warm app) | ≤ 1.5 s on api33-lowram emulator | macrobenchmark / device-check log, run ID |
| Torrent start to first frame (test hash BF2063A5…, healthy swarm) | ≤ 15 s, seek ≤ 5 s | device test on the built-in engine, run ID |
| GATE-1 main-thread frame budget | unchanged (700 ms limit stays; never raised) | existing GATE-1 test |

## Competitors checked (2026-11-21)

- CloudStream 4.8.0: extension (repo) based, phone + TV, Chromecast, FCast, downloads with a queue, skip intro, preview
  seekbar, bookmarks/library, subscriptions, backup, external players, torrent streaming since 4.7.0
  ([GitHub](https://github.com/recloudstream/cloudstream), [Telegram changelog](https://t.me/s/Cloudstream3),
  [version table](https://cloudstreamdownload.com/)).
- Hikari (codegeasse1): Stremio addons, CloudStream .cs3, SkyStream, Vega, Nuvio, Aniyomi, Anymex and Sora extensions,
  JSON-rule scrapers, manga reader, trackers with login (AniList, Simkl, Kitsu), calendar, profile locks, hold-to-speed
  4x, dual-audio picker, Telegram videos, torrents in IPTV, silent verified extension auto-updates, desktop port on mpv
  ([GitHub](https://github.com/codegeasse1/hikari), [releases](https://github.com/codegeasse1/hikari/releases),
  [0.11.04 notes](https://github.com/codegeasse1/hikari/commit/9eee9a881a89afb32af328dc2d2c72ad98350721),
  [desktop](https://github.com/codegeasse1/hikari-desktop)).
- Aniyomi: anime + manga library, mpvKt player with filters and Lua buttons, seasons grouping, Mihon-compatible backups,
  torrent streaming, seek thumbnails and an HTTP server for extensions since September 2026
  ([guide](https://tachiyomiguide.com/aniyomi-apk/)).
- Nuvio: Stremio addons plus local QuickJS plugins, built-in debrid (TorBox, Premiumize; Real-Debrid on some ports),
  Trakt / Simkl / MDBList sync, synced profiles, ratings from several sites, upcoming calendar; no torrent engine
  ([Nuvio wiki](https://nuvio.wiki/integrations/addons), [ElfHosted](https://docs.elfhosted.com/nuvio/nuvio-vs-stremio/),
  [NuvioSync FAQ](https://nuviosync.com/faq)).

## Feature matrix (Yumeiro status from the code on r15-stability, 2026-11-21)

Status words: Have (has a Pass run in the ledger or MASTER-CHECKLIST), In code (present, check the ledger before
claiming it), Building (this cycle), Missing.

| Capability | Who has it | Yumeiro | Row / next step |
|---|---|---|---|
| Extensions: Stremio, CloudStream .cs3, Aniyomi, Vega/Nuvio/SkyStream | CloudStream, Hikari, Nuvio | In code | B-32 empty extensions next; E1 master extension |
| Anymex / Sora extensions | Hikari | Missing | G-01 (ADR first: format and licence) |
| Trackers AniList, MAL, Kitsu, Trakt, Simkl | Hikari, Nuvio | In code | Trakt secret still missing (owner) |
| MDBList lists / ratings, several rating sites on detail | Nuvio | Missing | G-02 |
| Built-in torrent engine, no TorrServer | CloudStream (4.7+), Aniyomi | Building | B-31 (always-ready engine, faster start/seek) |
| Debrid (TorBox, Premiumize, Real-Debrid) | Nuvio | Missing | G-03 (user's own API key; official APIs only) |
| Telegram: login, private + public channels and groups, video + audio, download | Hikari (videos) | Building | F-02 login (videos + audio in code, download next) |
| Chromecast | CloudStream | In code | check ledger |
| FCast | CloudStream | Missing | G-04 |
| External players | CloudStream, Hikari | In code | — |
| Skip intro / outro: anime (AniSkip) | CloudStream, Hikari | In code | — |
| Skip intro / recap / credits for films and series (introdb-style) | CloudStream, Nuvio ports | Missing | G-05 |
| Seek-bar thumbnail previews | CloudStream, Aniyomi | Missing | G-06 (budget: off on 2 GB phones unless cheap) |
| Hold to speed up | Hikari | In code | — |
| Dual-audio / audio-language picker | Hikari | check | G-07 audit |
| Manga reader | Hikari, Aniyomi | In code | check ledger |
| IPTV m3u with groups, search, pins | Hikari | Have (NBUG-15 rows) | — |
| Downloads with queue, any stream (HLS/DASH) | CloudStream, Hikari | In code (NF-2b) | — |
| Backup / restore of everything | all | In code | — |
| Profiles with locks | Hikari, Nuvio | In code | check ledger |
| Cross-device sync of profiles and progress | Nuvio | Missing (trackers only) | G-08 (Firebase is set up; design ADR first) |
| Upcoming / airing calendar | Hikari, Nuvio | In code | — |
| Silent, verified extension auto-update with rollback | Hikari | check | G-09 audit |
| Phone-as-remote setup page for the TV | Nuvio ports | Missing | G-10 |
| Desktop app | Hikari (beta) | Missing | Out of scope until owner says |

## What Yumeiro has that they don't (keep and prove)

- One app for local files, music, online, IPTV, websites (page stream finder), TeraBox / DiskWala, Telegram public
  channels without login, and (F-02) with login.
- Built-in DNS-over-HTTPS fallback and unblocking (NBUG-6), ad / pop-up shield in the page browser.
- Built-in torrent engine AND Telegram login AND every extension family in one app: no competitor has all three.

## Speed baseline and budgets (SpeedDeviceTest, device run 44883372732 @ r15-stability 2dba1982, 6 market extensions)

| Measure (tag SPEED) | api33-lowram (2 GB) | api36 | Budget (never raised) |
|---|---|---|---|
| ext_first_usable | 1754 ms | 701 ms | ≤ 2500 ms on 2 GB |
| ext_all_loaded (6 extensions, 10 providers) | 2109 ms | 827 ms | ≤ 3000 ms on 2 GB |
| home_first_row_live | 3480 ms | 776 ms | ≤ 5000 ms |
| home_first_row_cached | 5 ms | 2 ms | ≤ 100 ms |
| search_first_result_live | 270 ms | 361 ms | ≤ 3000 ms |
| search_all_answered_live | 25725 ms | 25364 ms | none yet (bounded by the 25 s search timeout of slow extensions; target: finish the fan-out early, FE-6) |
| search_first_result_cached | 4 ms | 2 ms | ≤ 100 ms |
| search_all_answered_cached | 15116 ms | 15111 ms | Fail finding FE-S1: extensions with an empty answer are not saved, so a "cached" search still waits for them live (FE-6) |

### Run 2: device run 44884969625 @ r15-stability 7d77a583 (SPEED-T2: 20 market extensions, 26 providers), both phones Pass

| Measure (tag SPEED) | api33-lowram (2 GB) | api36 | Budget (never raised) | Result |
|---|---|---|---|---|
| ext_first_usable | 946 ms | 1077 ms | ≤ 2500 ms on 2 GB | Pass (44884969625) |
| ext_all_loaded (20 extensions, 26 providers) | 2010 ms | 2446 ms | ≤ 3000 ms on 2 GB | Pass (44884969625) |
| home_first_row_live | 1162 ms | 1908 ms | ≤ 5000 ms | Pass (44884969625) |
| home_first_row_cached | 1 ms | 1 ms | ≤ 100 ms | Pass (44884969625) |
| search_first_result_live | 128 ms | 119 ms | ≤ 3000 ms | Pass (44884969625) |
| search_all_answered_live | 25105 ms | 25099 ms | none yet (25 s search timeout, FE-6 early finish) | Not run (no budget yet) |
| search_first_result_cached | 6 ms | 1 ms | ≤ 100 ms | Pass (44884969625) |
| search_all_answered_cached | 15161 ms | 2 ms | none yet | api36 FE-S1 effect shown; lowram: an extension that timed out live (errors are not saved) is asked again: finding FE-S3 |
| feed_first_extension_shown (Yumix pattern, 3 slots) | 192 ms | 59 ms | ≤ 3000 ms | Pass (44884969625) |
| feed_half_shown | 4423 ms | 5703 ms | none yet | measured |
| feed_all_done (22 of 26 shown) | 24559 ms | 22007 ms | none yet (bounded by the 25 s home timeout of dead sites) | measured |
| youtube_shelf_live / saved | 723 / 1 ms | 1277 / 1 ms | saved ≤ 100 ms | Pass (44884969625) |
| ytmusic_home_live / saved | 367 / 15 ms | 346 / 44 ms | saved ≤ 100 ms | Pass (44884969625) |

### Run 3: device run 44892710506 @ r15-stability e2b87521 (20 extensions, 26 providers), both phones Pass

| Measure (tag SPEED) | api33-lowram (2 GB) | api36 | Budget (never raised) | Result |
|---|---|---|---|---|
| ext_first_usable | 809 ms | 391 ms | ≤ 2500 ms on 2 GB | Pass (44892710506) |
| ext_all_loaded | 1254 ms | 836 ms | ≤ 3000 ms on 2 GB | Pass (44892710506) |
| home_first_row_live / cached | 3195 / 1 ms | 321 / 2 ms | ≤ 5000 / ≤ 100 ms | Pass (44892710506) |
| search_first_result_live / cached | 138 / 1 ms | 55 / 1 ms | ≤ 3000 / ≤ 100 ms | Pass (44892710506) |
| search_all_answered_cached | 2 ms | 3 ms | none yet | FE-S1 + FE-S3 shown on both phones (was 15161 ms lowram) |
| search_all_answered_live | 25018 ms | 25409 ms | none yet | first search, no measured speeds yet (FE-S4 needs a second search): Not run |
| feed_first_extension_shown | 20 ms | 42 ms | ≤ 3000 ms | Pass (44892710506) |
| feed_half_shown | 56847 ms | 5175 ms | none yet | lowram regression: Fail (finding FE-T1, fixed, rerun pending) |
| feed_all_done | 75557 ms | 26939 ms | none yet | lowram: Fail (finding FE-T1) |
| youtube_shelf_live / saved | 1194 / 1 ms | 1342 / 1 ms | saved ≤ 100 ms | Pass (44892710506) |
| ytmusic_home_live / saved | 304 / 5 ms | 303 / 36 ms | saved ≤ 100 ms | Pass (44892710506) |
| torrent first 64 KB / seek (BuiltinTorrentDeviceTest) | 2844 / 1129 ms | 3061 / 1776 ms | 15 s / 5 s | Pass (44892710506); TV api24 8696 / 3148 ms Pass (44892712537) |

Not yet measured: feed with 50+ installed, after an app update (FE-Q1/Q2).

## How it happens

The owner's order stays: F-02 → B-31 → B-32 → E1 → F-21 / F-22 → E2-E4 → Built-not-tested device checks →
UNVERIFIED-QUEUE / INVENTORY. The G rows above come after that, in this order: G-03 debrid, G-05 skip credits,
G-06 seek previews, G-08 sync, G-02 MDBList, G-04 FCast, G-07, G-09, G-10, G-01. Each G row gets an ADR (competitors'
code read for ideas only, never copied; GPL/AGPL respected; no DRM, login or paywall bypass), a flag with kill switch,
a 2 GB phone and TV budget, and a regression test, like every other feature.
