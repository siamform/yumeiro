# Code audit, final (1.9.0 release candidate)

Checks only, no rewrite (master prompt section 6). Scope: every module and major file touched in the eighth to
tenth cycles. Each row says what was checked, how, what was found, what was fixed (commit) and what was left
with a reason. Nothing is listed as checked unless the check was actually run.

| Area | How checked | Found | Fixed | Left (reason) |
|---|---|---|---|---|
| Lint | `rg` over app/src/main for `@SuppressLint`/`tools:ignore` and gradle `lint {}` blocks (2026-11-19); CI lint on the head (run ID pending) | No baseline file, no disabled or downgraded rule. 9 suppressions: 6 `SetJavaScriptEnabled` (WebView features that need JS: browser check, page stream finder, YouTube account, cloud recapture, headless capture), 2 `PackageManagerGetSignatures` (Tachiyomi extension signature check), 1 `AppLinkUrlError` (mixed-case `application/x-mpegURL` MIME, matching is case-sensitive). Each is a documented need, not a silenced warning | — | CI lint result on the final head: pending |
| Architecture | | | | |
| Threading | `rg` for `GlobalScope`, `runBlocking`, `Thread.sleep`, `allowMainThreadQueries` in app/src/main (2026-11-19) | `GlobalScope` 0 uses (1 hit is a comment). `runBlocking`: Tachiyomi/CloudStream API shims that are blocking by contract and run on extension threads; `BrowserCheck.solveBlocking` refuses the main thread; `JsBridge.extractor` is bounded by `withTimeoutOrNull`; `QuickJs` uses `Dispatchers.Unconfined` on the JS thread; `Inbox.awaitSavedForTests` is test-only. `Thread.sleep` only in `MemoryWatch` (its own diagnostics thread). StrictMode release policy (`penaltyDeathOnNetwork`) is exercised by `ScrobbleFlushDeviceTest` | — | — |
| Error handling | Read of every `while (true)` and archive read in the tenth-cycle files (UniversalAdd, DnsMessage, RepoFormats/EcosystemRegistry, LastFm) | AUD-ZIP: `UniversalAdd.zipKind` read an archive's `manifest.json` whole, so a zip bomb behind a repo link was inflated into memory. SEC-LFM (security, §9): the APK carried the Last.fm shared secret | AUD-ZIP 2e68bf9; SEC-LFM b57e155 (ADR-048) | fail-before CI for both on `tmp/sec-lfm-fail-before` (run ID pending) |
| Bounded retries, polls, timers | All 51 `while (true)` in app/src/main classified (2026-11-19) | Byte loops end on EOF or throw past a cap (`readNBytesCompat`, PlaylistStore, SubtitleArchive, MediaMux, AdFilterDataSource). `DnsMessage.skipName` always advances or returns and throws past the buffer; the UDP receive loop ends at the socket deadline. UI tickers (`ReminderHost` 60 s, `HomeSections` 6 s, player position 400 ms–1 s, `StreamWatchPanel` per minute, `DreamBackdrop`) live in `LaunchedEffect` and stop with the composition. Gesture loops are `awaitPointerEventScope` loops (end with the pointer scope). Download / extension loops have their own caps and cancellation (checked earlier: HlsDownloader cancellable backoff, ExtensionGuard) | — | — |
| Sync / async correctness | | | | |
| UI/UX (48 dp, focus, contrast, font scale, RTL) | | | | |
| Performance (start-up, memory, battery, APK size) | | | | |
| Stability (crashes, ANRs, stalls in the 6 runs) | | | | |
