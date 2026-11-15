# Group 7 audit: Manga, search, sources and Live TV (R16)

Branch `r15-stability`. Inventory rows T1–T5 (and Y2 for Yumix search, audited in Group 4).
Devices: no Group 7 device test yet. CI emulators have no manga source and region-locked channels
can't open from the CI network, so the device side is owner test OMT-11.

Each result is labelled unit (JVM test), compile (builds, code read), device (named emulator) or
hardware (real phone/TV). Nothing in this group is device- or hardware-verified yet.

## Status

Agent-handleable fixes are in and unit-tested. No critical issue left open. Device check: a
Live TV device test is planned for the Group 12 milestone run (local playlist file → channel list →
process restart → list kept); until then T3/T4 stay unit only.

## Tested (code read + unit)

| Area | What was checked | Result |
|---|---|---|
| Manga browse (T1) | paging, keyed state per source/feed/query, duplicate filter, error text | OK; old answers write only to the old (discarded) state |
| Manga title and reader (T2) | load, Retry, cancellation, save progress per page | bug MG-1 fixed |
| Playlist store (T3) | index read/write, kill mid-write, bad file | bug LT-1 fixed |
| Playlist page (T3) | store error handling | bug LT-2 fixed |
| Playlist market (T3) | catalog size cap (8 MB), cache fallback, sha256 update check | OK; sha256 is used for updates, not as an integrity check (catalog comes from `TrustedSources`) |
| M3U parsing (T4) | CRLF, BOM, pipe headers, #EXTHTTP, KODIPROP DRM, garbage input | OK (`StreamPlaylistTest`); memory bug LT-3 fixed |
| HLS ad filter (T5) | segment and discontinuity removal | OK (`HlsAdFilterTest`), no change |

## Problems found and fixed

| ID | Problem | Root cause | Fix | Files | Evidence |
|---|---|---|---|---|---|
| LT-1 | One bad byte or a kill mid-save could lose every saved playlist | index read with plain JSON decode, written straight over the file | read and write through `SafeJsonFile` (temp + rename, bad copy kept as `.bad`) | `stream/PlaylistStore.kt` (4cee17d) | unit `StreamPlaylistTest#aBrokenIndexIsNotOverwrittenWithDefaults` |
| LT-2 | Playlist page empty with no message when the store failed | error not surfaced | error text + Retry re-runs the load | `ui/screens/stream/PlaylistDetailScreen.kt` (4cee17d) | compile |
| LT-3 | Opening a large saved playlist (cap 40 MB) built one String of the whole file, then the channel list: big heap spike on a 192 MB-heap phone | `file.readText()` before `M3uParser.parse` | `M3uParser.parseLines(Sequence<String>)`; the store reads with `bufferedReader().useLines` | `stream/M3uParser.kt`, `stream/PlaylistStore.kt` | unit: both paths give equal channels (`StreamPlaylistTest`) |
| MG-1 | Retry on a manga title or reader page could show a false error; first open read the library file on the main thread | `runCatching` caught the cancellation of the old load; `MangaLibrary.load` in a main-thread effect | rethrow `CancellationException`; load on `Dispatchers.IO` | `ui/screens/manga/MangaScreens.kt` (4cee17d, c9a4929) | compile |

## Verification

| Kind | Run / command | Result |
|---|---|---|
| unit (local) | full suite at 4cee17d + G7 edits | 156 classes, 893 tests, 0 failures, 11 skipped |
| unit (local) | `--tests '*StreamPlaylistTest' '*PlaylistMarketTest' '*StreamDetectionTest'` after LT-3 | 3 classes, 32 tests, 0 failures |
| CI device | none for this group yet | planned at the Group 12 milestone |

Cancelled runs in this group: none.

## Research

- Tachiyomi/Mihon readers load pages per item and keep failures per page with a tap-to-retry, as
  Yumeiro already does (`PageImage`).
- Kotlin coroutines: catching `CancellationException` (e.g. with `runCatching`) breaks cancellation;
  rethrow it ([Kotlin docs](https://kotlinlang.org/docs/cancellation-and-timeouts.html)).
- IPTV players (TiviMate-style, and open-source M3U players) parse playlists as a stream of lines;
  large public lists (iptv-org) are several MB with tens of thousands of channels.

## Proposals (not implemented; need owner approval)

1. Verify a market playlist's sha256 after download and reject a mismatch.
2. Store parsed channels in a small database so a 40 MB list doesn't need re-parsing on each open.
3. A global search across Yumix, manga and Live TV in one screen.

## Limits and owner tests

- No manga source or region-locked channel runs on CI; device test planned for Live TV only.
- `PlaylistMarket.friendlyError` wording is a Group 10 item.
- Owner test: OMT-11.
