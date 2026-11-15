# Group 12 audit: cross-cutting systems (Level 1)

Branch `r15-stability`. Method: master operating document, Part 3 (feature map, files read,
action-reaction, advantages/disadvantages, bug hunt, root causes, regression tests, ADRs,
verification, owner tests / research / proposals / CI usage).

Each result is labelled as one of:
- **unit**: JVM or Robolectric test
- **emulator**: an instrumented test on the CI emulators. **api33-lowram** is Android 13 on a
  Pixel 3a profile (2 GB RAM, 192 MB heap). **api36** is Android 16 on a Pixel 6 profile.
  **tv-api24** is Android 7 TV (the TV flavour's minSdk).
- **CI**: a green workflow step (build, lint, scan)
- **real hardware**: a phone or TV in the owner's hands (`docs/OWNER-MANUAL-TESTS.md`)

## Status

**In progress.** It is not complete; the gate is every finding fixed and verified, or in OMT with
attempts. Open items are listed under "Open work". Last update: see the commit that last touched
this file.

## 1. Feature map (inventory G12)

| ID | System | Main code | Audit state |
|---|---|---|---|
| X1 | Architecture and layering | whole app, `docs/SYSTEM-DESIGN.md` §2 | measured (§2 "Code structure"); findings X1-3, X1-4 fixed |
| X2 | Start-up, memory / OOM, low-RAM class | `MTApplication`, `startup/PrefsWarmup`, `diagnostics/MemoryWatch`, `DeviceClass` | X2-1 open (measure), X2-2 fixed; benchmark module built; heap profile not done |
| X3 | Main-thread stalls / ANR, StrictMode | `diagnostics/DevStrictMode`, `MainThreadWatchdog` (YuStall), `RegexWarmup` | ST-13/14/15 unit only; ST-16 emulator; ST-17 open |
| X4 | Battery and heat | `player/HeatGuard`, `notify/NotifyPacer` | X4-1 emulator verified |
| X5 | Notifications while closed | `notify/*`, `PushWakeService`, alarms, workers | X5-1, X5-2 emulator verified |
| X6 | Crash logs and export | `diagnostics/CrashLog`, `CrashExport`, `CrashReportsCard` | X6-1 emulator verified |
| X7 | Security | `security/*`, `IntegrityGuard`, `network_security_config.xml`, BuildConfig | X7-1 unit, X7-2 CI, X7-3 owner decision (ADR-013) |
| X8 | Accessibility | all UI, `AccessibilityDeviceTest` | X8-1 verified; X8-2, X8-3 device recheck running |
| X9 | TV and low-end | `ui/tv`, `DeviceClass` | X9-1 in progress (tv-api24 run) |
| X10 | Build, CI, release, signing | `.github/workflows`, Gradle | X10-1..4 CI verified; R8/ABI/signing review not done |
| X11 | Network stack (smart DNS, DoH, bypass) | `network/*` | audited: X11-1..5 fixed (a299f60, f7e490c, 98f2dbf) |
| X12 | Caching (data, image, codec) | `cache/*` | audited: X12-1, X12-2 fixed (c63bc66) |
| X13 | Android 7–16 compatibility | `OsCompat`, `MTApplication`, dependency SDK floors | X13-1 found (Jackson on Android 7); dependency scan of other libraries for SDK 26+ classes still to do; minSdk phone run pending |
| X14 | Design system and shared components | `ui/components/*` | not started (A11yLabel added in X8-2) |
| X15 | Shared live-feed component | `ui/feed/*` | not started |
| X16 | Cloud Worker and its abuse guards | `server/src/*` | not started |

Items owner-tests deferred to Group 12 (`OWNER-MANUAL-TESTS.md`, "Not owner tests"): widget data
updates, decoder fault recovery on device (fault-injection hook), Anime4K output on emulator GL,
upgrade from a 1.8.0 install. None done yet; listed under "Open work".

## 2. Files read

| Area | Files (lines) |
|---|---|
| X1 coroutines and blocking | every `CoroutineScope(`, `runBlocking`, `GlobalScope`, `while (true)`, `.commit()` site (grep over 380 files, 79,725 lines); `sources/js/JsBridge.kt`; `online/SuspendCatching.kt`; `ui/screens/settings/PrivacyTrackingBackup.kt` |
| X2 start-up | `MTApplication.kt`, `MainActivity.kt`, `startup/PrefsWarmup.kt`, `ui/theme/Theme.kt`, `ui/screens/extras/SetupGuide.kt`, `PermissionCenter.kt`; YuStall/StrictMode logcat of 43757544993 |
| X7 | `app/build.gradle.kts` (BuildConfig fields), `update/UpdateConfig.kt`, `res/xml/network_security_config.xml`; unzipped debug APKs from 43757542521 |
| X8 | `AccessibilityDeviceTest.kt`, `ui/components/A11yLabel.kt`, `HomeScreen.kt` ContinueCard, `YtMusicBrowse.kt`, `MTTopBar.kt`; `cloudstream3/MainActivity.kt`, `online/ExtensionManager.kt` (loading) |
| X9 | `scripts/device-check.sh`, `FirstRun.kt`, `.github/workflows/device-check.yml` |
| X10 | `.github/workflows/ci.yml`, `macrobenchmark.yml`, `benchmark/build.gradle.kts` |

## 3. Action → reaction (checked so far)

| Action | Expected | Seen | Label |
|---|---|---|---|
| Change font size / rotate / dark mode after an extension showed a dialog | app recreates, the dialog is gone or kept | **crash at start** (X8-3) → fixed: dialog dropped, app opens | unit; emulator recheck 43763558754 |
| Open the app on a low-RAM phone | first frame without app work on main | 2 s binder stall in Home setup card (X2-2) → fixed | unit; emulator recheck 43763558754 |
| Leave Settings while a tracker sign-in polls | silent cancel | error toast (X1-4) → fixed | unit |
| JS source extractor never calls back | play fails after a bound | thread blocked forever (X1-3) → 30 s bound | unit |
| Kill app, phone in Doze, alert due | alert posted, paced | posted 123 s after queueing on both phones | emulator |
| Heat level changes | interval, parallel calls, GPU work change | matches the table | emulator |
| Crash reports → Delete all | confirm, exact wording | fixed (X6-1) | emulator |
| TalkBack on tabs, Settings, search, player, 130 % font | no unnamed/small targets | clean except Continue cards (fixed) | emulator |
| Install on Android 7 TV | starts, no crash | starts; tests blocked by storage dialog (fixed by pm grant, rerun) | emulator (tv-api24) |

## 4. Advantages and disadvantages

| Design | Advantage | Disadvantage | Better design |
|---|---|---|---|
| 275 `object` singletons, no DI | cheap start-up, simple calls, survives page disposal (ADR-001/006) | hidden dependencies, tests reach into globals | constructor-injected interfaces at network/prefs/clock seams (proposal) |
| Warm prefs on `yu-prefs` thread | most files ready before first draw | a race: main may still get there first (X2-1) | measure with Macrobenchmark first (ADR-012) |
| Extensions use the app's activity for their UI | CloudStream-compatible dialogs work | their fragments outlive their class loader on recreate (X8-3) | factory with loader lookup and drop (done) |
| Secrets in BuildConfig | works offline | extractable from the APK (X7-3) | ADR-013 option (b) |

## 5. Findings register

| ID | Severity | File:line | Root cause | Repro | Fix | State |
|---|---|---|---|---|---|---|
| X1-3 | high | `sources/js/JsBridge.kt` extractor | `runBlocking` with no limit; plain list across threads | extractor that never calls back | `withTimeoutOrNull(30 s)`, synchronized list (565d39b) | unit |
| X1-4 | medium | `PrivacyTrackingBackup.kt` (6 sign-in calls) | `runCatching` catches the job's own cancellation | leave Settings mid-poll | `runCancellable` (b0a62eb) | unit |
| X2-1 | medium | `MainActivity.kt:50` → `Theme.kt:68` | prefs read in first composition; most of the 5.4 s stall is the emulator's render thread | cold start api33-lowram | measure (ADR-012) | open |
| X2-2 | medium | `SetupGuide.kt` `rememberSetupStatus` | uncached binder permission checks in composition | cold start api33-lowram (2,018 ms YuStall) | off main + warm-up (208ba48) | emulator (43763558754) |
| X2-3 | medium | `cloudstream3/MainActivity.kt` | per-Context prefs path map; warm-up used the app context | StrictMode in player (43763558754) | route to app context (15a29ca) | unit; emulator pending |
| X6-2 | low (diagnostics) | `diagnostics/DevStrictMode.kt` crumb | first `dev.asfakul` frame = guarded looper on framework-only stacks | 43763558754 crumbs `ExtensionGuard.kt:83` | `MainThreadWatchdog.appFrames` (99a4e68) | unit |
| X15-1 | low (perf/battery) | `ui/feed/LiveFeed.kt` reset | old request not cancelled | switch chips fast | cancel Job (d626e28) | unit |
| X16-1 | medium (test gap) | `server/src/index.ts` | handlers untested | — | handlers.test.ts, 11 tests (23ab446) | CI 43765850022 |
| X5-2 | medium | `NotifyPacer.kt` deliverNext | every tick delivered | ticks from boot/updates | gap check (9b46045) | emulator |
| X6-1 | medium | `CrashReportsCard.kt` | Delete all with no confirm, over-promised text | tap | confirm + wording (2e5f9d6) | emulator |
| X7-1 | high | `network_security_config.xml` | cleartext app-wide; HTTPS-only list incomplete | config read | domains added (149ef42) | unit |
| X7-3 | medium | `app/build.gradle.kts:51-59,122-146`, `UpdateConfig.kt:22` | secrets compiled into the APK | unzip APK | ADR-013 (proposed) | owner decision |
| X8-1 | medium | `MTTopBar.kt`, `YtMusicBrowse.kt` | small gap, unnamed cards | a11y check | 3019605, a898010 | emulator |
| X8-2 | medium | `HomeScreen.kt` ContinueCard | clickable without a label | a11y check on search (api36) | `a11yLabel` (5e685a5) | emulator recheck |
| X8-3 | **critical** | `cloudstream3/MainActivity.kt` onCreate | extension fragments restored with the app class loader | font scale change after an extension dialog (43762090588, both phones) | `ExtensionFragmentFactory` (7cb8c2e) | emulator (43763558754, both phones) |
| X11-1 | medium | `network/SmartDns.kt` resolver loop | dead resolver retried on every lookup | resolver unreachable | 2-min skip, cleared on network change (a299f60) | unit |
| X11-2 | medium | `NetworkSettings` / `MTApplication` | first requests ran before settings loaded | request at cold start | `attach()` + `ensureLoaded()` (f7e490c) | unit |
| X11-3 | high | `SmartDns.kt` error mapping | OkHttp wraps network failure in UnknownHostException, read as NXDOMAIN | Cloudflare blocked | `unreachableOrAnswer` (a299f60) | unit |
| X11-4 | medium | `network/DnsMessage.kt` DnsWireClient | socket not closed on failed connect/TLS | TLS failure | close in finally (98f2dbf) | unit |
| X11-5 | medium (security) | DnsWireClient UDP | reply sender not checked (spoofing) | packet from another port | source check (98f2dbf) | unit |
| X12-1 | medium (memory) | `cache/DataCache.kt` remember | LRU capped by count only | 16 large pages = 48M chars | char budget, big pages disk-only (c63bc66) | unit |
| X12-2 | medium (data) | `DataCache.purge` | deleted in-flight `.tmp` | purge during write | only `.tmp` older than 60 s (c63bc66) | unit |
| X13-1 | **high** | `gradle/libs.versions.toml` jackson, `app/build.gradle.kts` | TV (minSdk 24) shipped Jackson 2.19.4; since 2.15 `ObjectMapper` init calls `ExceptionUtil.isFatal` → `BootstrapMethodError` (SDK 26) → NoClassDefFoundError; every CloudStream source dead on Android 7 | TV api24 emulator, 43765162241 (`home Yumeiro Drama/ReelShort … MainAPIKt`) | tv* classpaths → Jackson 2.13.5 (2226756), phone keeps 2.19.4 | unit; CI 43768099126; TV emulator 43768054941 (no NoClassDefFoundError, Drama home loads) |
| X9-2 | medium (test) | `androidTest/FirstRun.kt` shell | keys injected while the previous test's activity window still held input focus; taps reach Compose directly, keys do not | 43765039266 api33-lowram Settings, 43765162241 TV Inbox ("input event receiver has already been disposed") | wait for our window focus before `input keyevent` (1a577e5, eb1775c) | emulator (phones 43767144680, TV 43768054941) |
| X9-3 | low (test) | `HomeNavigationDeviceTest`, `StabilityStressDeviceTest` | looked for phone bar tags on TV (`nav_tab_*`, `nav_shortcut_*`) | 43765162241 | `FirstRun.navTag` / `hasNav` (1a577e5) | emulator (TV 43768054941) |
| X9-4 | medium (TV) | `ui/screens/MTPlayerApp.kt` tab pager | after a tab change that does not move focus (touch tap on the rail, Continue video card → Videos, deep link), focus stays on the old page's item: the pager composes only the shown page but the lazy list pins the focused item, so focus sits off screen with nothing to its left and D-pad Left does nothing | TV 43768054941 h5 (focus `[home_setup_card]` after 4 × Left on Music) | page focus tracking + `TvPageFocus.isStranded` → rail's selected tab (0931a3f) | unit; TV emulator 43769985665 pending |
| X13-2 | medium (test) | `androidTest` LocalPlayer, Accessibility, HomeNavigation | API 28: MediaStore insert without a path → invented `/sdcard/video/<millis>`; no thermal service below 29; guide timer raced Back | minSdk 43768670627 | `TestClip` (scanner below 29), thermal n/a below 29, guide marked shown (77d8d44) | emulator 43771214803 pending |
| X10-1..4 | medium | `ci.yml`, several | TV never linted; NewApi calls; opt-in noise | lint | f0df775, ff00d11, 7486997, c7772ce, 72394dc | CI |
| ST-17 | low (1 in 40 runs) | Compose `ContentInViewNode` | bring-into-view never settles once | stress api36 43697447466 | focus-chain logging (3342106) | open |

## 6–7. Regression tests (fail before, pass after)

| Finding | Test | Before | After |
|---|---|---|---|
| X1-3 | `JsBridgeExtractorGuardTest` (2) | hangs/fails | pass (unit) |
| X1-4 | `SuspendCatchingTest` (5) | fails | pass (unit) |
| X2-2 | `SetupPermsOffMainTest` | "a permission check ran on the main thread: [true]" | pass (unit) |
| X8-2 | `A11yLabelTest` (2), `AccessibilityDeviceTest` | api36 flagged Continue cards | recheck 43763558754 |
| X8-3 | `ExtensionFragmentFactoryTest` (3) | default factory throws on the saved state (the crash) | restores with no fragment (unit) |
| X2-3 | `ActivityPrefsRouteTest` | "did not reach the application context: []" | pass (unit) |
| X6-2 | `StrictCrumbTest` (2) | framework-only stack blamed `ExtensionGuard.kt:83` | pass (unit) |
| X15-1 | `LivePagedTest.resetCancelsTheLoadInFlight` | "old request is still running after reset" | pass (unit) |
| X16-1 | `server/test/handlers.test.ts` (11) | n/a (gap); guards removed → 3 fail | 31/31 (CI) |
| X11-1, X11-3 | `SmartDnsResilienceTest` (4) | 3 fail | pass (unit) |
| X11-4, X11-5 | `DnsWireClientTest` (4) | 3 fail | pass (unit) |
| X9-4 | `TvPageFocusTest` (4) + device h5 | h5: "D-pad LEFT never reached the rail; focus [home_setup_card]" (43768054941) | 4/4 (unit); device rerun 43769985665 |
| X13-1 | `JacksonTvCompatTest` (2, testTv; in CI) | 2 fail: "jackson-databind 2.19.4 needs Android 8", `ExceptionUtil.class` references `BootstrapMethodError` | pass (unit) |
| X12-1, X12-2 | `DataCacheBoundsTest` (3) | 3 fail (48000000 chars held; fresh .tmp deleted; 400000-char page kept) | pass (unit) |

## 8. Decision records

ADR-012 (start-up measured on the R8 preview build, accepted) and ADR-013 (no long-lived secrets in
the APK, **proposed, owner decision**) are in `docs/SYSTEM-DESIGN.md` §10.

### Swallowed catches reviewed (X1, error handling)

| Place | What is swallowed | Fallback | Verdict |
|---|---|---|---|
| `EqualizerManager.kt` releaseNow (eq, bass, virtualizer) | `AudioEffect.release()` after the audio server died | each released separately, field still cleared | correct, no change |
| `MediaRepository.kt` loadVideoPositions / loadVideoLastWatched | `prefs.all` read | empty map; types checked with `when` | correct, no change |
| `VideoPlayerModal.kt` subtitle picker | `takePersistableUriPermission` refused by the provider | subtitle still loads for this session | correct, no change |
| `UpdateManager.kt` onUserActionRequired | `startActivity(confirm)` refused | falls through to the "update ready" notification | correct, no change |
| `DevStrictMode` breadcrumb | — | named the looper for framework-only stacks | fixed X6-2 (99a4e68) |

## 9. Verification

See `docs/DONE-LEDGER.md` rows X1-3 … X10-4, ST-13 … ST-17 for the run IDs per item.

## 10. Owner-only tests, research, proposals, CI usage

- Owner tests: real-phone start-up time (after ADR-012 numbers exist), widget look (OMT-8), TV
  remote feel (OMT series). Credentials: rotate the Cloudflare token and Firebase service account
  before any public release; decide on ADR-013.
- Proposal (X10 battery): `HeatGuard` only knows thermal status on API 29+. On Android 9 (phone
  minSdk 28) and the TV app's Android 7–9 boxes it falls back to battery saver alone. A fallback
  on the battery temperature from `ACTION_BATTERY_CHANGED` (`EXTRA_TEMPERATURE`, e.g. WARM at
  41 °C, HOT at 45 °C) would cover them. Not built: it needs an ADR and real-hardware numbers.
- Research: AOSP `ContentInViewNode` already cancels requests that can never be satisfied (code as
  of June 2025), so ST-17 is a different case; the logged focus chain is needed.
- CI usage (this group): CI 43754316254, 43757542521, 43762845168; device 43757544993, 43760044391,
  43762090588, 43763558754; TV 43757547383, 43759794246, 43762847904.

## Open work (Group 12 is not done until all are closed)

1. X9-4: TV rerun 43769985665 (h5, h2). X13-2: minSdk rerun 43771214803 (api28). (X8-2, X2-3,
   X9-2, X9-3, X13-1 done.)
2. X2: Macrobenchmark startup (before), baseline profile, startup (after); heap profile on api33-lowram.
3. ST-13/14/15: StrictMode device proof; ST-17 root cause.
4. X7: MASVS write-up.
5. X10: R8, ABI split and signing review; upgrade test from the v1.8.0 release on an emulator.
6. X11–X16: full audit (network, caching, compatibility, design system, live feed, Worker).
7. Deferred-to-G12 items: widget data updates, decoder fault injection, Anime4K on emulator GL.
8. RTL layout check.
