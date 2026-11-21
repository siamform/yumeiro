# Yumeiro feed engine (2.1.0): owner requirement, root causes, design, plan

Status: IN PROGRESS. Every row below is Pass (run ID) / Fail (finding) / Blocked (reason) / Not run (reason).
Read this before touching Yumix, Home, YouTube, online music, search or stream-source code.

## 1. Owner requirement (2026-11-21 ~19:00 +06, recorded in plain words; top priority, mandatory)

- Other apps load one extension at a time. Yumeiro loads all of them at once, like YouTube: Yumeiro = YouTube,
  each extension = one YouTube channel. The Yumix feed mixes them: two cards next to each other can come from two
  extensions ("Latest", "Trending", "Fresh from your sources" are multi-source). Default "Show videos from" = all.
- It must scale: a user with 1000+ extensions must not hang the app. Same for search (same title from many
  extensions) and stream sources (same video, many sources while playing).
- "Show from your source" does not need to load everything up front. Pick one extension → it is prioritized and
  loads first, fully. Otherwise extensions load in a queue in the engine, serving content from each over time.
- Priority everywhere: what the user is playing is first; then what they picked; then what is on screen; then
  background. Same rules for Home, Yumix, YouTube and online music.
- A reserve engine: content loaded ahead (scroll triggers more) and kept in memory / on disk so it is served
  without a spinner. Learn from Instagram / TikTok reels (instant feed, prefetch), but build Yumeiro's own.
- No loading spinners everywhere. Tap a stream → it opens in milliseconds (already fast; make it faster).
- Biggest problem now: after a fresh install and after an app update, the feed takes about 10 minutes with
  several extensions installed. Open the app → content must show instantly. Check whether installed extensions
  are reinstalled after every update, and fix it if so.
- YouTube and online music also take long to load: find out why and fix.
- Keep this doc up to date for the next agent.

## 2. How it works today (read from the code, r15-stability, 2026-11-21)

- Extension engine: `online/ExtensionManager.ensureLoaded` loads EVERY enabled extension at start
  (`StartupSpeed.loadProgressively`: files opened 2 / 4 ahead, `load()` strictly one at a time because provider
  ownership is read from the global `APIHolder.allProviders` + `PluginManagerHook.currentlyLoading`).
  Order: pinned, then fastest measured (E4), unknown, slow, quarantined.
- Yumix feed (`ui/screens/online/OnlineHomeScreen.kt`): `loadAll` starts one job per extension that has a home
  page; `gate = Semaphore(3)` lets 3 extension homes fetch at once. One extension home (`OnlineRepository.home` →
  `homeLive`) asks up to 4 rows per batch, up to 3 batches (`ROWS_PER_PROVIDER` 4, `MAX_PAGE_BATCHES` 3), each row
  call bounded by `HOME_TIMEOUT` 25 s, on `ExtensionGuard.dispatcher` (8 threads) + `parallel` permits.
- Saved pages: `cachedHome` → `cachedHomeNow` reads memory (15 min) or disk `DataCache.read("home", key)` with the
  default max age `Settings.freshMs` = 6 h. `savedHome` (any age up to keepDays 7) exists but nothing calls it.
- Sections: "Fresh from your sources" = round-robin mix of every Ready extension (`buildMixFeed`); per-extension
  groups appear 4 at a time as you scroll (`groupLimit`); "More to explore" pages 3 rows at a time.
- Extension updates: `sync()` (at most every `CHECK_INTERVAL_MS`) reinstalls only when the market version is
  higher, or for curated/user repos when sha256 differs (both lowercase hex).

## 3. Root causes found so far (code reading; each needs a device number before "Pass")

| ID | Cause | Effect |
|---|---|---|
| FE-RC1 | Whole extension homes are queued 3 at a time; one home = up to 12 row calls × 25 s timeout. Dead or slow sites hold a slot for up to minutes. | N extensions × ~30 s / 3 → 50 extensions ≈ 8 min of "Loading N more extensions". Matches the owner's ~10 min. |
| FE-RC2 | Saved home pages count only when younger than 6 h (`cachedHomeNow` uses `DataCache.read` default `freshMs`). `savedHome` (any age) is unused. | After > 6 h away, or an update installed later, every extension goes live again through the queue: no instant feed although pages are on disk. |
| FE-RC3 | Every enabled extension's code is loaded at start, one `load()` at a time, before its home can be asked. | Start-up cost grows with the number installed; 1000+ = minutes and memory pressure. |
| FE-RC4 | Fresh install: nothing saved yet, so the first feed is only as fast as the queue (FE-RC1). | Cold feed waits for whole homes, not first rows. |
| FE-Q1 | Reinstall after update: code reading shows no loop (version / sha rules above); `repairMissing` only downloads when a file is missing, and `cleanupFiles` deletes plain files only (`File.delete` fails on the non-empty `oat` folder, so compiled code is kept). | To verify on a device across a real app update (upgrade-check.yml). |
| FE-Q2 | After an app update ART may re-verify / recompile every extension's code (CloudStream deletes oat folders after an update on purpose). | To measure: extension load time before vs after an update. |
| FE-Q3 | YouTube shelves and YouTube Music Home were cached in memory only (20 min): every cold start = skeletons + every shelf live (YouTube 2 at a time). | Fixed by FE-7a / FE-7b (saved on the phone, shown at once). |

## 4. Design: Yumeiro feed engine (own build)

Principles (from Instagram / TikTok / YouTube clients: cache-first render, precomputed feed, bounded prefetch,
priority with cancellation, single-flight; sources: https://droidly.io/sysdesign_instafeed,
https://www.binarybox.org/p/system-design-how-instagram-delivers,
https://iliazlobin.com/designs/system-design-tiktok-reels/):

1. Cache-first, always (FE-1). On open, every extension's last saved home (any age within keepDays) shows at once;
   live refresh runs behind it and merges in (stale-while-revalidate). Never a blank skeleton when anything is saved.
2. Row-level work, not extension-level (FE-2). The unit of work is one row call. A scheduler with lanes:
   P0 playing / stream sources of the open title, P1 the extension the user picked, P2 rows on screen and the next
   screen, P3 background warm-up. Global concurrency cap sized by DeviceClass (2 GB phone / TV lower); per-host cap;
   a slow row (> first-row budget) yields its slot; a quarantined extension gets one probe, not a full home.
3. First rows first (FE-3). An extension shows as soon as its first row answers; the rest of its rows fill later.
4. Working set, not all (FE-4, for 1000+). Only a working set of extensions is loaded and asked at start
   (pinned + most used + fastest + what the feed shows), the rest load lazily when needed (picked, scrolled to,
   searched). Code load moves off the critical path.
5. Reserve engine (FE-5). After the feed is shown, the engine prefetches the next page of rows near the scroll
   position and keeps it in memory/disk; a background warm-up (WorkManager, unmetered + charging or idle, existing
   budgets) refreshes saved homes so the next open is fresh and instant, including right after an app update.
6. Search and stream sources (FE-6). Same scheduler: results from the fastest extensions show first, duplicates
   of the same title merge, a cap on in-flight extensions; stream sources of the playing title are P0.
7. YouTube and online music (FE-7): same cache-first + priority rules after measuring their slow path.

Budgets (proposed; set from the SpeedDeviceTest baseline, then never raised): first content on Yumix after open
≤ 1 s when anything is saved; first live row on a fresh install ≤ 5 s on api33-lowram; no hang / ANR with 50
installed (device), 1000+ simulated (unit).

## 5. Plan (one by one, each: fail-before test, fix, pass-after CI, device number, ledger row)

| Step | What | Status |
|---|---|---|
| SPEED-T | Device speed test (extension load, Home first row live/cached, search) | Pass: baseline run 44883372732 (numbers in GOAL-TOP-OF-CLASS.md) |
| SPEED-T2 | SpeedDeviceTest grows: 20 extensions (was 6), the Yumix pattern (every home through 3 priority slots with FE-3 first rows: first / half / all shown), YouTube shelf and YouTube Music Home cold start (live, then saved) | Pass: device run 44884969625 (both phones; numbers in GOAL-TOP-OF-CLASS.md run 2). Finding FE-S3: lowram cached search 15.2 s (a timed-out extension is asked again) |
| FE-1 | Show any-age saved homes at once + background refresh (fixes FE-RC2) | Pass: fail-before 44883755874 verified; pass-after CI 44884554686; device home_first_row_cached 1 ms (44884969625) |
| FE-2 / FE-2b | Priority gate (picked jumps the queue, flag fe2_priority_gate) + dead-site budget (one batch when rows time out) | Built; fail-before 44884208915 verified; pass-after CI 44884554686 Pass. Row-level lanes done in FE-2c (scroll pages = VISIBLE) |
| FE-3 | First row shows the extension | Built (flag fe3_first_row); fail-before 44884810399 verified; pass-after pending |
| FE-P | Background feed steps back to one job while a video or song plays (flag fe_pause_while_playing) | Built; fail-before 44885100184 verified; pass-after pending |
| FE-2c | Row-level lanes: the shared extension-call limit (6) serves search / scroll pages / picked before background rows (flag fe2c_call_lanes) | Built; fail-before 44885309121 verified; pass-after pending |
| FE-4 | Working set + lazy extension code load (fixes FE-RC3, 1000+) | FE-4a built: Yumix mix rebuilt per 300 ms frame, not per row answer (flag fe4_feed_frames), fail-before 44886560580 pending. Working set (live fetch only for pinned / fastest / on screen) and lazy code load: Not run |
| FE-5 | Reserve engine: scroll prefetch + background warm-up | Background warm-up built (FeedWarmWorker, flag fe5_feed_warm); fail-before 44885480520 verified; pass-after pending; device Not run. Scroll prefetch: Yumix already pages Explore near the end (nearEnd, 6 items), now on the VISIBLE lane (FE-2c) |
| FE-6 | Search / stream sources on the scheduler; FE-S1: save empty search answers too (cached search took 15.1 s, run 44883372732); FE-S2: ranking and typeahead off the main thread | FE-S1 built, fail-before 44884613831 verified; FE-S2 built, fail-before 44885736016 verified; FE-S3 (recent failure answered at once, Retry asks again) built, fail-before 44886384973 verified; FE-S4 (search timeout from the site's own speed, 8..25 s) built, fail-before 44886794506 pending; pass-after pending. Search already on the PICKED lane (FE-2c); dedupe exists (SearchRanker groupKey / alsoOn). Early finish: FE-S4 (measured sites 8..25 s; unknown sites still 25 s) |
| FE-7a/b | YouTube shelves + YouTube Music Home saved and shown at once | Built; fail-before 44883994031 verified; pass-after CI 44884554686 Pass |
| FE-Q1/Q2 | Device check across a real app update: reinstall? recompile? feed time | Not run |
