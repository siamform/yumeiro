# Stability round 15 (OOM / ANR / player)

Status: branch `r15-stability`. A row counts as fixed only when a device run in
`docs/evidence/round3/device-runs.md` shows it. Earlier "done" reports (STATUS-v1.8.1) were
checked with Robolectric only. This round re-tested each of them on two emulators: a low-RAM
API 33 (2 GB, 192 MB heap) and an API 36 Pixel 6.

## Root causes found in the code

| Report | Root cause | Fix |
|---|---|---|
| ANR 8 s on FocusEvent, Xiaomi 220333QAG / Android 13. Stack: `Pattern.compile` inside `LazyLayoutPinnableItem` / `PagerLazyLayoutItemProvider` / `PrefetchHandleProvider` | `Kana.forTitle()` built ~60 `Regex` objects per call and was called straight from composition (HomeScreen, MTPlayerApp) for every card the pager prefetched. Around 70 more `Regex("…")` literals were built inside functions on hot paths (ReleaseTags, YumixFeed, LinkHosts, etc.). | `Kana.forTitle` is now regex-free (`containsWord`) with an LRU of 256 and is wrapped in `remember`. Every runtime regex literal is hoisted to a file-level `private val RX_…` or goes through `text/RegexCache` (LRU 128). `RegexWarmup` touches the heavy objects on a background thread at startup. `ReleaseTags.parse` has an LRU of 1024 and item tags are computed once when the item is built. |
| "Out of memory" reports (Pixel 6a / Android 17) | Part of the reports were not OOMs at all: `ApplicationExitInfo` background low-memory kills were labelled "Out of memory". Real pressure came from unbounded Coil fetch/decoder parallelism, separate OkHttp clients (dozens of `OkHttp` threads), and a large image memory cache on low-end phones. | Exit import now labels LMK kills "Closed to free memory" and records importance / PSS / RSS. Coil uses the shared `NetworkClient` OkHttp, fetcher parallelism 4/8, decoder 2/3, memory share 0.15 on low-end phones. Dispatcher limits 16/32 requests, 4/6 per host; one connection pool. `MemoryWatch` clears the image cache at ≥85 % heap and responds to `onTrimMemory`. |
| Slow cold start / main-thread work | `MTApplication.onCreate` scheduled workers, FCM, download resume, backups, janitor on the main thread; `YouTube.init` ran on main. | Moved to the `yu-startup` and `yu-yt-init` threads. |
| YouTube quality switch freezes 5–10 s, then "This video couldn't play (timeout)" | `YouTubeStreams.videoRequest` (OTF manifest building + network) ran on the main thread from the quality dialog coroutine. The chosen stream ignored whether the device can decode the codec (e.g. AV1 / VP9 4K with no hardware decoder). | `videoRequest` runs on `Dispatchers.IO`. `pickVideo` is codec-aware via `VideoCodecSupport` (MediaCodecList HW/SW/none) and the quality list explains when a level plays as a different codec. Start position is passed into `setMedia` instead of prepare-then-seek. |

## Root causes found on device (runs 43612391254 to 43648850998)

| Symptom on device | Root cause | Fix (commit) |
|---|---|---|
| Two players for one video, audio after close | Each screen built its own ExoPlayer | `PlaybackSession`: one player per process, fenced leases, 3 s grace, release on memory pressure (5b05512, ADR-006) |
| Dramas missing from the feed, banner shows but episodes never play | No stale fallback for shelves, so one slow host emptied the feed. A hung episode had no overall deadline. `DramaHealth` failed with "Recursive update" | Stale-while-revalidate shelves; one 68 s `loadLinks` deadline inside the 75 s budget; `DramaHealth` uses lazy async with `putIfAbsent` (474f7df) |
| Live feeds stop mid-scroll and show cached rows | Screen-owned loaders restarted on recomposition and replaced live rows with saved ones | `LiveFeed` stores owned by the process, with generation guards, append-only updates and a cursor kept on failure (3e2114e, 474f7df, ADR-008) |
| Device tests hung for the full timeout | The extension review dialog waited for a tap | Install with `Mode.AUTO` in tests, `HangDumpRule` thread dumps (5a1c9ec) |
| Engine quality switch froze, then ERROR_CODE_TIMEOUT | `applyEngineLevel` re-selected the video track on a live effect graph | Re-select only when the graph is first attached (5a1c9ec) |
| Playback tests failed with HTTP 403 | Dead `gtv` sample bucket | Media3 test media (5a1c9ec) |
| ForegroundServiceDidNotStartInTimeException | A stale start intent, or a sticky restart without a player, called `stopSelf()` in `onCreate` | Always call `startForeground` then stop; never `START_STICKY` (b0e596d) |
| AAC to AC3 switch: StuckPlayerException | The heap byte budget refused loading while less than 2 s was buffered (a load-control starvation deadlock) | `StarvationSafeLoadControl` always loads below 2 s (b0e596d) |
| The same load-control fix had no effect | Kotlin `by` does not forward Java default methods | Every `LoadControl` method is forwarded explicitly (b0e596d, StarvationLoadControlTest) |
| 1.6 s main-thread stall at start-up | The WebView user agent was built on main, and workers started WebView | UA cached per WebView build and computed when idle (b0e596d) |
| ForceDownload class: initializationError | A `runBlocking` test body returned a non-Unit value | `runBlocking<Unit>` (b0e596d) |
| Exported crash file listed with size 0 | The MediaStore row was published before it was written | Insert with `IS_PENDING=1`, write, then set `IS_PENDING=0` and SIZE; delete the row on failure (0575711). Device: 192,627 and 192,638 bytes |
| Regex still compiled on main after hoisting | Hoisting moved the compile into static initialisers, which run on whichever thread touches the class first | `RegexWarmup` initialises all 91 pattern-holding classes on `yu-startup`. `RegexGuardTest.everyPatternHolderIsWarmedOffMain` enforces it (ec8ca95) |
| Stress test lost its place | Earlier steps left a detail page or a system dialog on screen | `backToTabs()` presses Back or relaunches, counts recoveries and asserts the tabs respond (1eb1b65) |
| Endless recomposition after D-pad focus (99–100 % janky frames) | Two parts. (1) The Yumix hero carousel kept auto-advancing while a card inside it had focus; the Home banner paused only for touch. (2) The main cause, caught in run 43650046635: Compose 1.12's default pager page connection consumes every `UserInput` nested pre-scroll while the pager sits between pages, and a focused card's bring-into-view is a `UserInput` scroll. The card scrolled the tab pager, the pager settled back, forever (1,717 state applies in 3 s, offset stuck at −0.48) | (1) Carousels pause under focus, touch, reduced motion and HeatGuard (UI/UX rule `auto-rotation-controls`, 9a3e375). (2) The tab pager has a no-op `pageNestedScrollConnection`, plus a guard that settles it to the nearest page when it is idle off a boundary (MTPlayerApp). The stress test now fails on any "Compose never idle". Before: spins in 3 of the 7 stress runs from 43641330758 to 43652020450. After: see runs 43653623319 and later in device-runs.md |

### Known remaining costs (not app bugs)

- `androidx.navigation` `NavDeepLink` compiles 14 small route patterns on main when the graph is
  built. This is a library cost. It was not measured on its own; every stress run since ec8ca95 stayed under
  1.6 s max stall with no app-origin regex on main. Removing it would mean dropping typed route arguments.
- `InternetArchiveProvider` (third-party default extension) turns any `load()` failure, network
  included, into "Error loading: Invalid JSON response". The detail error screen with retry
  handles it.
- YouTube device tests skip on CI emulators: Google answers "Sign in to confirm you're not a
  bot" for data-centre IPs.

## Diagnostics added

- `diagnostics/MemoryWatch`: every 30 s samples Java heap, native heap, free system memory and
  thread families; ring buffer of 160 entries written to `files/crash/breadcrumbs.log`
  (previous run kept as `breadcrumbs-previous.log`). Debug builds also run a main-thread watchdog
  that logs `YuStall`.
- `diagnostics/DevStrictMode`: StrictMode thread + VM policies in debug builds.
- `diagnostics/CrashExport`: each crash report is copied automatically to
  `Download/Yumeiro/Crash reports` (MediaStore on Android 10+, app external folder otherwise).
- `CrashLog`: keeps a 512 KB reserve that is freed on crash so the OOM report can be written.

## Guards and tests

- `app/src/test/.../r15/RegexGuardTest`: fails the build if a `Regex(…)`, `.toRegex()` or
  `Pattern.compile` appears inside a function body outside the allow-list.
- `app/src/test/.../r15/KanaAndCachesTest`, `YouTubePickVideoTest`.
- `app/src/androidTest/.../StabilityStressDeviceTest`: tab and pager swipes, feed flings and D-pad
  focus traversal while sampling the main thread for regex compilation and stalls; writes
  `files/stress/report.txt`. Fails on any app-origin regex compile on main or a stall ≥ 4 s.
- `app/src/androidTest/.../PlayerSwitchDeviceTest`: engine levels, DASH rendition switching with
  audio, YouTube ladder.
- `.github/workflows/device-check.yml`: runs these on a low-RAM API 33 emulator (2 GB RAM,
  192 MB heap) and on a current Android image.
