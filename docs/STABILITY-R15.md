# Stability round 15 (OOM / ANR / player)

Status: work in progress on branch `r15-stability`. Nothing here counts as fixed until the
device evidence in `docs/evidence/round3/` says so.

## Root causes found in the code

| Report | Root cause | Fix |
|---|---|---|
| ANR 8 s on FocusEvent, Xiaomi 220333QAG / Android 13. Stack: `Pattern.compile` inside `LazyLayoutPinnableItem` / `PagerLazyLayoutItemProvider` / `PrefetchHandleProvider` | `Kana.forTitle()` built ~60 `Regex` objects per call and was called straight from composition (HomeScreen, MTPlayerApp) for every card the pager prefetched. Around 70 more `Regex("…")` literals were built inside functions on hot paths (ReleaseTags, YumixFeed, LinkHosts, etc.). | `Kana.forTitle` is now regex-free (`containsWord`) with an LRU of 256 and is wrapped in `remember`. Every runtime regex literal is hoisted to a file-level `private val RX_…` or goes through `text/RegexCache` (LRU 128). `RegexWarmup` touches the heavy objects on a background thread at startup. `ReleaseTags.parse` has an LRU of 1024 and item tags are computed once when the item is built. |
| "Out of memory" reports (Pixel 6a / Android 17) | Part of the reports were not OOMs at all: `ApplicationExitInfo` background low-memory kills were labelled "Out of memory". Real pressure came from unbounded Coil fetch/decoder parallelism, separate OkHttp clients (dozens of `OkHttp` threads), and a large image memory cache on low-end phones. | Exit import now labels LMK kills "Closed to free memory" and records importance / PSS / RSS. Coil uses the shared `NetworkClient` OkHttp, fetcher parallelism 4/8, decoder 2/3, memory share 0.15 on low-end phones. Dispatcher limits 16/32 requests, 4/6 per host; one connection pool. `MemoryWatch` clears the image cache at ≥85 % heap and responds to `onTrimMemory`. |
| Slow cold start / main-thread work | `MTApplication.onCreate` scheduled workers, FCM, download resume, backups, janitor on the main thread; `YouTube.init` ran on main. | Moved to the `yu-startup` and `yu-yt-init` threads. |
| YouTube quality switch freezes 5–10 s, then "This video couldn't play (timeout)" | `YouTubeStreams.videoRequest` (OTF manifest building + network) ran on the main thread from the quality dialog coroutine. The chosen stream ignored whether the device can decode the codec (e.g. AV1 / VP9 4K with no hardware decoder). | `videoRequest` runs on `Dispatchers.IO`. `pickVideo` is codec-aware via `VideoCodecSupport` (MediaCodecList HW/SW/none) and the quality list explains when a level plays as a different codec. Start position is passed into `setMedia` instead of prepare-then-seek. |

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
