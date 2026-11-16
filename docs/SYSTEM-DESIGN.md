# Yumeiro system design (R15)

Written with the architecture-designer skill (jeffallan/claude-skills). Kotlin/Android specifics follow
the Kotlin agent skills. This document is the contract for how the app is built. Code that breaks a
rule here is a bug, and every rule has a test or a runtime guard named next to it.

Status markers: **[done]** implemented and covered by a test; **[in progress]** being moved now;
**[planned]** designed, not built yet.

## 1. Requirements

### Functional (what the app must do)

| Area | Requirement |
|---|---|
| Playback | One player for every kind of media: local files, YouTube, YouTube Music, IPTV/links, extension sources (CloudStream, Aniyomi, Stremio, JS, home servers). Quality, audio, subtitle, speed and source switching while playing. Full screen, docked watch page, in-app floating player, system picture-in-picture, background audio. |
| Sources | Extensions are installed by the user and run in-process. A dead link moves to the next one automatically, including after a manual pick. |
| Feeds | Home, YouTube, online music, Yumix (extensions) and live TV are endless and live: they never stop by themselves and are never replaced by an old saved copy while the user scrolls. |
| Downloads | Download any playable stream (direct, HLS, DASH, YouTube) during playback, even when the source page doesn't offer a download button. |
| Reliability | Crash, ANR and out-of-memory reports are written on the device and exported to a file automatically. |

### Non-functional (how well)

| Attribute | Target | How it is checked |
|---|---|---|
| Scale | 200,000 monthly users, every kind of device | Crash-free sessions ≥ 99.5 %; ANR rate < 0.47 % (Play "bad behaviour" line) |
| Devices | Android 7 TV boxes, 2 GB Android Go phones, flagships, Android TV | Device tiers in §5; emulator matrix in `.github/workflows/device-check.yml` |
| Main thread | No frame task ≥ 700 ms; no input event ≥ 5 s (ANR) | `MainThreadWatchdog` (debug), `StabilityStressDeviceTest` stall meter |
| Memory | No OOM on a 192 MB heap device under the stress test | `MemoryWatch` samples, stress test on the low-RAM emulator |
| Start-up | First frame without network, disk scans or extension loading on the main thread | `DevStrictMode`, start-up thread `yu-startup` |
| Privacy | No credential in code, logs, crash reports or backups | `CrashLog` scrubbing, secret scan before every commit |

### Constraints

Single Gradle module, Kotlin + Compose, minSdk 24. Extensions are third-party code that can block, throw,
leak or allocate without limit; the app must survive all of that. There is no app server: everything runs on
the device (GitHub Actions only builds the market and feeds).

## 2. Architecture at a glance

```mermaid
graph TD
    subgraph UI["UI layer (Compose): draws state, sends intents, owns no long-lived work"]
        Shell["MainActivity + MTPlayerApp\n(pager tabs, navigation, TV focus)"]
        Screens["Screens: Home, YouTube, Music,\nYumix, Live TV, Search, Details, Downloads, Settings"]
        Surfaces["Player surfaces:\nfull screen · watch page · floating · PiP"]
    end

    subgraph State["State holders (process scope, survive pages being disposed)"]
        Feeds["Feed stores\n(append-only, stale-while-revalidate)"]
        Session["PlaybackSession\n(owns the one ExoPlayer)"]
        Rotation["SourceRotation\n(OnlineSession: link states, failover)"]
        DL["DownloadEngine"]
    end

    subgraph Domain["Services"]
        Resolve["Stream resolving\n(StreamResolver, YouTubeStreams,\nOnlinePlayback, AccessLadder)"]
        Quality["Quality ladder\n(QualityEngine, VideoCodecSupport)"]
        Repo["OnlineRepository / YouTube / Music"]
        Ext["ExtensionManager + bridges\n(guarded dispatcher, timeouts)"]
    end

    subgraph Data["Data and platform"]
        Net["NetworkClient\n(one OkHttp, bounded dispatcher)"]
        Cache["DataCache (disk + LRU),\nCoil (bounded)"]
        Store["Prefs / files / MediaStore"]
        Media3["Media3 ExoPlayer + nextlib ffmpeg"]
        Diag["Diagnostics: CrashLog, MemoryWatch,\nCrashExport, StrictMode"]
    end

    Shell --> Screens
    Screens -->|intents| Feeds
    Screens -->|play / switch| Session
    Surfaces -->|attach surface| Session
    Feeds --> Repo
    Session --> Resolve
    Session --> Quality
    Session --> Rotation
    Rotation --> Resolve
    DL --> Resolve
    Repo --> Ext
    Repo --> Cache
    Resolve --> Net
    Ext --> Net
    Session --> Media3
    Media3 --> Net
    Diag -.observes.-> Session
    Diag -.observes.-> Shell
```

Dependency rule: arrows only point down. The UI never builds players, parses pages, compiles regexes or
talks to the network. State holders outlive any single composable.

### Code structure as measured (G12 X1, 2026-11-15)

| Package | Files | Lines | Role |
|---|---|---|---|
| `ui/` | 141 | 44,897 | Compose screens, components, theme, the one `MainViewModel` |
| `online/` | 61 | 8,779 | Extensions, online repository, Yumix, trackers' APIs |
| `sources/` | 30 | 5,151 | Drama, Tachiyomi/JS (Mangayomi) bridges, Stremio |
| `player/` | 32 | 3,550 | PlaybackSession, quality, heat guard, listening time |
| `stream/` | 20 | 3,250 | Resolving and forced downloading |
| others (21 packages) | 96 | 14,098 | update, youtube, notify, data, backup, network, lyrics, … |
| **total** | **380** | **79,725** | |

- State is held in 275 top-level `object`s (service-locator style; no DI framework) and one
  `MainViewModel`. This matches ADR-001/006 (process-scoped holders) and keeps start-up cheap, but
  makes unit tests reach into globals (`resetForTest` hooks) and hides dependencies. Proposal, not
  done: constructor-injected interfaces at the seams that tests fake most (network, prefs, clock).
- Largest files (review cost, recomposition scope): `VideoPlayerModal.kt` 2,388, `MediaComponents.kt`
  1,693, `MTPlayerApp.kt` 1,687, `HomeScreen.kt` 1,218 lines.
- Coroutines: 64 ad-hoc `CoroutineScope(...)`; those without a `SupervisorJob` are all
  `rememberCoroutineScope` (UI-bound, cancelled with the composable). `GlobalScope`: none (one comment).
  `runBlocking`: two, `JsBridge.extractor` (now bounded, X1-3) and `BrowserCheck` (guarded off main).
- `runCatching` around suspend calls catches cancellation; the shared `online/runCancellable` keeps
  timeouts as failures and rethrows real cancellation (X1-4). Other suspend `runCatching` sites were
  read: they check `isActive` afterwards or wrap `withTimeout` on purpose.
- Synchronous `SharedPreferences.commit()`: 10 calls, all deliberate (launch-time restore before
  anything loads, reset, crash export while the process may die).

## 3. Threading model **[done for regex/start-up/YouTube; enforced]**

| Work | Where it runs | Guard |
|---|---|---|
| Drawing, input, focus, small state updates | Main | `MainThreadWatchdog` logs `YuStall` (debug); stress test fails on a stall ≥ 4 s |
| Regex compilation | Never at run time on main; patterns are file-level constants or `RegexCache`. A file-level constant compiles in the class's static initialiser on whichever thread touches the class first, so `RegexWarmup` initialises all 91 holder classes on `yu-startup` | `RegexGuardTest` (no regex built inside a function; every holder class is in the warm-up list); stress test samples main for `Pattern.compile` |
| Focus and motion | Auto-advancing carousels (Home banner, Yumix hero) pause while focus is inside, on touch, with reduced motion and under `HeatGuard`, so a pager never moves a focused card away from bring-into-view. The tab pager takes no nested scroll from its pages: Compose's default page connection consumes `UserInput` pre-scroll while between pages, and bring-into-view is `UserInput`. An idle tab pager is always settled on a page | `dpadFocusSettles`, `swipeThenDpadSettles` (frames per 2 s must fall to 0); the stress test fails on any "Compose never idle" |
| Parsing (HTML, JSON, manifests), feed merging, sorting | `Dispatchers.Default` | code review + StrictMode |
| Network and disk | `Dispatchers.IO` through `NetworkClient` (16/32 requests, 4/6 per host) | StrictMode disk/network on main (debug) |
| Extension calls | `ExtensionGuard.dispatcher` with timeouts | crash guard, per-call timeout |
| Image fetch / decode | Coil, 4/8 fetchers, 2/3 decoders | `MTApplication` image loader |
| Start-up chores (workers, FCM, downloads resume, backups, janitor, regex warm-up) | `yu-startup` thread | — |
| YouTube extractor init | `yu-yt-init` thread | — |
| YouTube stream request (manifests, OTF) | `Dispatchers.IO` | `YouTubeStreams.videoRequest` |

Root cause fixed in R15: `Kana.forTitle()` compiled about 60 regexes per call and ran inside composition for
every card the pager prefetched, which matches the Xiaomi ANR stack (`Pattern.compile` under
`LazyLayoutPinnableItem` / `PrefetchHandleProvider`). See `docs/STABILITY-R15.md`.

## 4. Memory model **[done]**

| Budget | Low-end (≤192 MB heap, ≤3 GB RAM, ≤4 cores, Go) | Normal | TV |
|---|---|---|---|
| Image memory cache | 15 % of heap | Coil default (25 %) | Coil default |
| Image fetch / decode parallelism | 4 / 2 | 8 / 3 | 8 / 3 |
| Player buffer | `bufferBudgetBytes(memoryClass)` | same formula | same formula |
| `ReleaseTags` / `Kana` LRU | 1024 / 256 entries | same | same |
| Data cache in memory | 48 pages | same | same |
| Feed stores | rows × `MAX_ROW_ITEMS`, explore grows only as the user scrolls | same | same |

Pressure handling: `MemoryWatch` samples every 30 s (heap, native, free system memory, thread families),
clears the image cache at ≥ 85 % heap, and reacts to `onTrimMemory` / `onLowMemory`. `CrashLog` keeps a
512 KB reserve that is released when an OOM is thrown so the report can still be written. Background
low-memory kills reported by `ApplicationExitInfo` are labelled "Closed to free memory", not "Out of memory".

## 5. Device tiers

`DeviceClass.lowEnd` (pure function `isLowEnd`, unit tested) selects the low-end budgets. TV is detected
from `UI_MODE_TYPE_TELEVISION` and gets D-pad focus paths (see `docs/TV-AND-PHONE.md`). The emulator matrix
runs a low-RAM Android 13 device (2 GB RAM, 192 MB heap → low-end path) and a current Android image.

## 6. Feeds **[done for Yumix, Home, YouTube, online music, Live TV]**

Rules for every endless feed:

1. **Process-scoped store.** Feed state lives in an object that outlives the pager page. The main pager uses
   `beyondViewportPageCount = 0`, so a tab's composables are disposed when you leave it; state held in
   `remember` was thrown away and reloaded from the disk copy (up to 6 h old). That was the "suddenly shows
   saved cache" bug. Yumix: `YumixMemory` in `OnlineHomeScreen.kt`.
2. **Stale-while-revalidate.** A saved copy is shown at once; when it's older than the memory window
   (15 min) the live page is fetched in the background and merged with `mergeRows` (existing rows keep
   their order and items, new titles are appended, new rows go at the end).
3. **Append-only.** Cards already on screen never move, vanish or get replaced (`YumixFeed.appendNew`).
   Before R15 the explore feed was rebuilt and re-shuffled from scratch on each page, and round-robin put a
   short row's next page in the middle of what was already shown.
4. **A failed page is not the end.** `HueRowFeed.afterPage(null)` keeps the row open and retries with a
   back-off (2 s, 4 s, … up to 8 s, 3 tries in a row). Only an empty page or 3 failures in a row end it.
5. **Fetches survive navigation.** They run in the store's scope, so leaving a tab doesn't cancel them half
   way (a row stuck "loading").

6. **One store type for every feed (ADR-008).** `LiveFeed` / `LivePaged` hold the rows, the arrival order and
   a generation number. A load started for an older generation can't overwrite a newer one, and a failed
   refresh never wipes rows that are on screen. Home uses `HomeMemory` (rows in arrival order, a shuffle
   seed kept for the process), Live TV uses `LiveTvMemory` (per-playlist loads and stamps).

Tests: `LiveFeedTest` (append-only, retry, merge), `LivePagedTest`, `YumixFeedTest`.

## 7. Playback **[done: player ownership and recovery state machines; verified on device]**

### Before R15

The `ExoPlayer` was created inside `VideoPlayerModal` (`remember(video.id)`), so the player's life is tied
to one composable. Resolving, quality switching, failover timers and PiP handling also live in that
2,300-line composable. Consequences: a new video recreates the player; work that should survive a
navigation (download → downloads page with the video still playing) depends on the composable staying
alive; and blocking work started from the UI (the YouTube quality request) froze the screen.

### Target

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Resolving: play(request)
    Resolving --> Preparing: stream resolved (IO)
    Resolving --> Failed: no stream
    Preparing --> Playing: first frame
    Preparing --> Rotating: error / no picture in 12 s
    Playing --> Switching: quality / audio / source change
    Switching --> Playing: first frame at same position
    Switching --> Rotating: new rendition fails
    Playing --> Rotating: fatal error / stalled 25 s
    Rotating --> Preparing: next link (900 ms gap)
    Rotating --> Resolving: all links failed once → fresh links
    Rotating --> Failed: nothing left
    Playing --> Idle: close
```

- **One owner.** `PlaybackSession` (process scope) owns the single `ExoPlayer`, its media source factory and
  the state machine above. Surfaces (full screen, watch page, floating, PiP) attach and detach a
  `SurfaceView`; they never create or release the player.
- **Every expensive step runs off main.** Resolving and manifest building on IO, codec probing cached.
- **Quality ladder.** `QualityEngine` builds one list per source: the downloader's full list for
  YouTube/Video/Yumix; for single-rendition sources the engine adds scaled levels (GPU `Presentation`) and an
  upscaled level (Anime4K). `VideoCodecSupport` ranks codecs by hardware support so a 4K AV1 level on a
  phone without an AV1 decoder plays as VP9/H.264 instead of timing out.
- **Source rotation.** A link that fails, sends no picture in 12 s, or stalls 25 s hands over to the next
  link; a manual pick is just another start point for the same rotation.
- **Surface modes.** Starting a new video while the floating player or system PiP is showing expands to full
  screen. "View downloads" from the download toast moves the video to the floating player and opens
  Downloads.

### Player ownership (built in R15)

`player/PlaybackSession` owns the one `ExoPlayer` of the process:

- `acquire(key, uri)` returns a `Lease`. The next video reuses the same player (stopped, media cleared,
  speed, volume and track overrides back to their defaults) when the profile matches (network vs local
  file) and it never ran GPU effects. Otherwise the old player is released before a new one is built, so
  two players never exist together. Before R15 the next episode built a second player while the first
  was still alive: two sets of codecs and two media buffers on a 2 GB phone.
- Listeners are added through the lease. A handover revokes the old lease first: its listeners are
  removed, and its last position and duration are frozen for the old screen's final progress save. This
  stops the old screen from reacting to the new video's events, which ExoPlayer sends synchronously.
- On close the player pauses and is released after `GRACE_MS` (3 s). Reopening the same video inside the
  grace resumes it with no reload. `onTrimMemory(RUNNING_LOW)` releases a closed player at once.
- Header and DRM lookups read `PlaybackSession.currentUri`, so a reused player sends the new video's
  headers.

Tests: `PlaybackSessionTest` (reuse, fencing, grace, resume, local vs network) and
`PlaybackSessionDeviceTest` (three videos in a row through the real app = one player built, two reuses;
close → released after the grace; reopen inside the grace → resumed).

Migration (one step at a time, each with a device test):
1. **[done]** YouTube stream request off main; codec-aware rendition choice; start position passed to
   `setMedia`.
2. **[done]** Engine levels and DASH rendition switching, verified by `PlayerSwitchDeviceTest` on the
   emulator.
3. **[done]** Player creation, lifetime and listener ownership moved to `PlaybackSession`.
   **[done] 3b.** Recovery moved out of the composable: `StartWatch` (no picture: gives up after 12 s with
   10 s of no download, or 45 s in all; paused time does not count) and `StallWatch` (buffer frozen 25 s)
   in `player/SourceWatch.kt`, and `RecoveryRouter` (the order: GPU effect → local file → offline wait →
   link refresh → header ladder → quick retry → next source → generic bounded recovery). Retry and
   "stable playback" timers are lease-owned (`Lease.schedule`), so `revoke()`/`detach()` cancel them and a
   retry for one video can never fire on the next. Tests: `RecoveryStateTest`, `PlaybackSessionTest`
   (timer fence).
4. **[done]** Surface-mode rules (download → floating + Downloads; new video → full screen),
   `PlayerSurfaceRules` + `SurfaceModeDeviceTest`.

## 8. Failure modes

| Failure | Behaviour |
|---|---|
| Extension throws, hangs or returns junk | Guarded dispatcher, per-call timeout, row skipped; the rest of the feed continues |
| A feed page times out | Row stays open and retries with back-off (§6) |
| Network drops | `Connectivity.online` pauses stall timers; feeds keep what's on screen |
| Link dead / expired | Rotation (§7), then one fresh link extraction, then the site's own page |
| Decoder can't play a codec | Codec-aware choice; ffmpeg software decoders as fallback |
| Memory pressure | Image cache cleared, LRU bounded caches, OOM reserve for the report |
| Crash / ANR / LMK | `CrashLog` + `ApplicationExitInfo` import, breadcrumbs, auto export to `Download/Yumeiro/Crash reports` |

## 9. Security

Covered in `docs/ARCHITECTURE.md` (extension model, updates) and `docs/security/`. Additions in R15: crash
reports and breadcrumbs pass through `CrashLog` scrubbing (tokens, cookies, keys); the exported copy is the
scrubbed text. Credentials live only in CI secrets or the git-ignored `.secrets/` folder.

## 10. Decision records

### ADR-001: Process-scoped feed stores instead of composable state

- **Status:** Accepted, implemented for Yumix.
- **Context:** The main pager disposes tabs it isn't showing. Feed state held in `remember` was lost and
  reloaded from disk, which looked like the feed "stopped and showed old data".
- **Decision:** Keep feed state in process-scoped objects with bounded size; fetches run in the store's scope.
- **Alternatives:** `beyondViewportPageCount = 1+` (keeps more pages composed: more memory and prefetch work
  on low-end phones, the opposite of the ANR fix); Jetpack `ViewModel` per tab (the pager pages share one
  activity-scoped owner, so it would be the same lifetime with more plumbing); `rememberSaveable` (can't hold
  thousands of items in a Bundle).
- **Consequences:** Feeds survive tab switches and rotation; memory is held until the process dies, so every
  store must be bounded and cleared on "Clear caches".

### ADR-002: Append-only endless feeds

- **Status:** Accepted, implemented (`YumixFeed.appendNew`).
- **Context:** Rebuilding and re-shuffling the merged feed on every page moved and removed cards under the
  user's finger.
- **Decision:** A feed only appends entries it hasn't shown; each new batch is block-shuffled on its own.
- **Alternatives:** Stable sort by a per-item hash (still inserts new items between old ones).
- **Consequences:** Order is "arrival order", which is less evenly mixed than a full round-robin; acceptable
  for an endless feed.

### ADR-003: Stale-while-revalidate for saved pages

- **Status:** Accepted, implemented for extension home pages.
- **Decision:** Show the saved copy at once, refresh in the background when it's older than 15 min, merge.
- **Alternatives:** Always network first (blank screen on slow networks); always cache first with a 6 h
  freshness window (the old behaviour: old data until the window expired).
- **Consequences:** One extra request per stale extension per visit.

### ADR-004: No regex compilation at run time on hot paths

- **Status:** Accepted, implemented, enforced by `RegexGuardTest`.
- **Decision:** Patterns are file-level constants or come from `RegexCache` (LRU 128); heavy pattern
  objects are warmed on `yu-startup`.
- **Alternatives:** Only fixing `Kana` (the other ~70 call sites would come back as the next ANR).
- **Consequences:** Slightly more start-up work off main; a build-time rule developers must follow.

### ADR-005: One shared OkHttp client with bounded concurrency

- **Status:** Accepted, implemented.
- **Decision:** Coil, extensions, YouTube and the player share `NetworkClient` (dispatcher 16/32, 4/6 per
  host, one connection pool).
- **Alternatives:** Per-feature clients (dozens of `OkHttp` threads in the Pixel 6a report).
- **Consequences:** A slow host can't starve the others past its per-host limit.

### ADR-006: Player owned by a process-scoped PlaybackSession

- **Status:** Accepted. Ownership done (`PlaybackSession`, §7); recovery clocks and routing done (step 3b).
  The `PlaybackService` + `MediaController` move stays future work (see Consequences).
- **Context:** The player is tied to one composable, so player lifetime, surfaces and recovery depend on UI
  composition.
- **Decision:** One `PlaybackSession` owns the `ExoPlayer` and the playback state machine; UI surfaces attach.
- **Alternatives:** Keep the modal and patch each symptom (what earlier rounds did); move playback into
  `PlaybackService` + `MediaController` now (the long-term shape; bigger change, and the controller's async
  API would touch every player call at once).
- **Consequences:** Player survives navigation and mode changes; the modal shrinks to drawing and gestures.
  The `PlaybackService` move can follow without UI changes.

### ADR-007: Device evidence on emulators in CI

- **Status:** Accepted, implemented (`device-check.yml`).
- **Decision:** Instrumented tests on a low-RAM API 33 emulator and a current API image, uploading reports,
  logcat and optional heap dumps.
- **Alternatives:** Robolectric only (what v1.8.1 relied on; can't show ANRs, OOMs or decoder behaviour).
- **Consequences:** Each run costs about 30–45 minutes of CI time, so runs are manual.

### ADR-008: One live-feed store for every endless feed

- **Status:** Accepted, implemented (Yumix, Home, YouTube, online music, Live TV).
- **Context:** Every tab had its own copy of "load rows, keep them in `remember`, reload from disk when the
  page comes back". The disk copy could be hours old, so a live scroll was replaced by stale data.
- **Decision:** `LiveFeed` / `LivePaged` (process scope) with arrival order, append-only pages and a
  generation guard; loads run in `FeedScope`, never in the composable's scope.
- **Alternatives:** `beyondViewportPageCount` > 0 (more memory and prefetch work; the ANR cause); a
  `ViewModel` per tab (same lifetime as a process object here, more plumbing).
- **Consequences:** A feed survives tab switches; every store must be bounded and cleared on "Clear caches".

### ADR-009: Drama studio health as a background service

- **Status:** Accepted, implemented (`DramaHealth`, `YumeiroDramaProvider`).
- **Context:** Dramas often didn't appear and the banner showed episodes that never played: studios that
  refuse every episode were still shown, and each episode request could hang for 35 s.
- **Decision:** One `DramaHealth` checks each studio in the background and hides studios that refuse
  everything. Episode links run under one deadline (68 s): up to 3 tries on a hang, a refusal goes straight
  to mirrors, mirrors share what's left.
- **Consequences:** Studios come back by themselves when they recover. Evidence: live run b21, 24 of 24
  shown dramas played (`docs/evidence/round3/drama-live-b21.txt`).

### ADR-010: Forced downloading in the built-in engine (ForceGrab)

- **Status:** Accepted, implemented (`online/download/ForceGrab.kt`).
- **Context:** Some sources "don't allow downloads": they refuse a plain request, serve DASH, or keep the
  sound in a separate track.
- **Decision:** IDM-style engine: multi-connection byte ranges (2 connections on low-end phones, 4
  otherwise; 4 MB chunks, resumable with a `.chunks` file), header escalation on 401/403 (the player's
  headers, then browser UA + Origin + Sec-Fetch + WebView cookies, then without Referer), DASH saved as
  one file (one container family per pair, main or phone-language audio, joined with `MediaMuxer`), and
  torrents handed to TorrServer. DRM is never bypassed: protected tracks are skipped, and our own scan
  catches `<ContentProtection>` tags media3 drops.
- **Alternatives:** Hand everything to an external downloader (can't join DASH or split audio; such
  sources are now kept in the app).
- **Consequences:** More parallel requests per download, bounded by the shared client's per-host limit.
  Evidence: `ForceGrabTest` (live byte-identical multi-connection and resumed downloads),
  `ForceDownloadDeviceTest` (DASH save on the emulator).

### ADR-011: Pinned extensions

- **Status:** Accepted, implemented (`online/PinnedExtensions.kt`).
- **Decision:** Up to 8 pins kept in prefs, shown first in "Show videos from" and as a chip bar under
  the extension bar. 48 dp touch targets, labelled for TalkBack (UI/UX Pro Max rules).

### ADR-012: Start-up is measured on the R8 preview build (G12 X2)

- **Context:** debug builds are not optimised and JIT-only, so their start-up (X2-1: ~5.9 s first
  composition on the low-RAM emulator) says little about the release. A benchmark needs a
  non-debuggable, R8-optimised build that installs next to the store app.
- **Decision:** a `:benchmark` `com.android.test` module (Macrobenchmark 1.5.0) targets the existing
  `preview` build type; `src/preview` adds `<profileable shell=true>` for preview only. Cold start is
  measured with no AOT and with the baseline profile; the profile is generated by
  `BaselineProfileGenerator` and committed as `app/src/main/baseline-prof.txt`.
- **Consequences:** emulator numbers compare before/after on one image only; a real-phone number is
  an owner test. No release-manifest or signing change.
- **Status:** accepted (2026-11-15).

### ADR-013 (proposed): No long-lived secrets inside the APK (G12 X7-3)

- **Context:** `MT_UPDATER_TOKEN` (optional, private release repo) is XOR-obfuscated in BuildConfig;
  Trakt/Kitsu client secrets and the Last.fm shared secret are plain BuildConfig strings. Any value
  in an APK can be extracted.
- **Options:** (a) keep as is and treat them as public, rotate on leak; (b) public release repo so the
  updater needs no token, and the Worker signs/exchanges OAuth and Last.fm calls; (c) per-user keys
  only (the user enters their own).
- **Proposal:** (b), with (c) kept as the fallback the app already supports.
- **Status:** proposed; needs the owner's decision. Nothing changed in code.

### ADR-014 (proposed): Dialogs that third-party extensions open over the app (G12 X9-7)

- **Context:** CloudStream plugins run inside our process with the activity as context, so they can
  show their own windows. On the TV emulator (43775191888) the `com.phisher98` repo plugin opened a
  plain View dialog "Support Phisher Repo" (donation link, "Watch an Ad") over MainActivity while it
  loaded. It takes D-pad focus; the first Back only closes it. Evidence:
  `docs/evidence/round3/x9-7-extension-dialog-tv.png`. The same mechanism serves legitimate plugin
  UI (log-in pages, WebView captcha, settings), so a blanket block would break those.
- **Options:** (a) keep as is; the tests close it
  (`FirstRun.dismissForeignDialog`). (b) Block plugin dialogs during home loading only: show them
  later from a "Messages from extensions" entry in the extension's page. (c) Block every plugin
  dialog unless the user turns on "Allow extension pop-ups" per extension (default on for
  extensions that need log-in). (d) Remove or warn about the repo in the market list.
- **Proposal:** (b) for the start-up case and (c)'s per-extension switch as the user's control,
  after a design pass (UI/UX skill) and a TV focus test. Not built.
- **Status:** proposed; needs the owner's decision. Nothing changed in product code.

## 11. Risks

| Risk | Mitigation |
|---|---|
| Extension code still compiles regexes or blocks inside its own calls | Runs on the guarded dispatcher, never on main |
| Process-scoped stores grow with very long sessions | Bounded rows/items; cleared on "Clear caches"; `MemoryWatch` evidence |
| Emulators don't reproduce every vendor decoder | Codec-aware ladder + ffmpeg fallback; crash export from real users |
| Large playback refactor regresses a niche path | One migration step at a time, each with `PlayerSwitchDeviceTest` / `OnlinePlaybackDeviceTest` |
