# Yumeiro — Developer Handover

Read this first. It is the fastest way for the next developer or agent to understand the
codebase, what changed in the `feat/ultimate-revamp` branch, and what to do next.

## 0. Repository move (2026-11-07)

- The project lives at **`siamform/yumeiro`**. App catalog URLs, the
  `TrustedSources` allowlist, the updater default, scripts and docs all point to the new repo.
- **Renamed package.** `dev.asfakul.mtrevanced` → `dev.asfakul.yumeiro`, OAuth redirects
  `yumeiro://anilist-login` and `yumeiro://mal-login`.
- **New release signing key.** Old installs (signed with the previous key, updating from the old repo)
  must uninstall once and install the first `siamform` release. The fingerprint is in
  `.github/release-signing-cert.sha256`; the keystore is backed up offline by the owner.
- **Actions budget.** Data workflows (extension market, playlist market, stream sync, app feed) run
  every 3 days and skip publishing when nothing changed. CI and CodeQL run on pull requests,
  device check is manual, release runs only for app changes, Dependabot is monthly and grouped.
  Keep it that way: the previous account was restricted for Actions usage.

## 1. Product in one paragraph

**Name:** Yumeiro (夢色, "dream-colored"). Formerly "MT ReVanced" / "MT Player"; renamed in the
`feat/yumeiro-rebrand` branch. Repo: `siamform/yumeiro`. The Kotlin package, `applicationId`
and OAuth scheme were renamed from `mtrevanced` to `yumeiro` (`dev.asfakul.yumeiro`, `yumeiro://`) on
2026-11-07 together with the new signing key, so Android treats it as a new app: users of the old build
install it fresh.

Yumeiro (`dev.asfakul.yumeiro`) started as a local audio/video player and is becoming an
"ultimate" streaming hub: local music + video, IPTV / live streams, CloudStream-compatible online
extensions (`.cs3` plugins act as channels), and — new in this branch — login-free YouTube video
and YouTube Music. No account system anywhere; everything works signed-out.

## 2. Stage

| Area | Before this branch | After | Notes |
|---|---|---|---|
| Local music / video player | Done | Done | Media3/ExoPlayer, MediaSession service, lyrics, EQ, sleep timer |
| Streams (IPTV, M3U, pasted links) | Done | Done + YouTube links play in-app | `stream/` |
| Online extensions (CloudStream) | Works, crashes with many extensions | Crash-isolated, YouTube-style mixed feed | `online/`, `ui/screens/online/` |
| HTTP 403 on extension links | Failed after one blind retry | 5-step header ladder → fresh links → headless page capture | see §6 |
| YouTube video | Handed off to YouTube app | In-app: home shelves, search, suggestions, history, DASH/HLS playback | `youtube/` |
| YouTube Music | — | In-app: quick picks, mood shelves, song search, background playback | `youtube/` + Music tab |
| Design system | Generic dark | "Tsuki" ink + moonlight-gold theme, artwork-driven ambient colour, joined glass dock | `ui/theme/` |
| Overall | ~40 % | ~55 % | see roadmap §9 |

## 3. Build

```bash
# JDK 21, Android SDK with platforms;android-37.0 + build-tools;37.0.0
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug          # ~5–6 min cold
./gradlew testDebugUnitTest      # Robolectric unit + Compose UI tests
```

- Gradle 9.x, AGP with `compileSdk 37`, `minSdk 28`, Kotlin + Compose (BOM in `gradle/libs.versions.toml`).
- CloudStream library `com.lagradost:cloudstream3` runs the `.cs3` extensions.
- NewPipeExtractor `v0.26.5` (JitPack) powers YouTube. The CloudStream library pulls an old
  `com.github.teamnewpipe:NewPipeExtractor`; `app/build.gradle.kts` substitutes it with the
  TeamNewPipe artifact so only one copy is on the classpath.
- `main` is protected: work on a branch and open a PR.

## 4. Architecture map

```
dev.asfakul.yumeiro
├── MTApplication        installs ExtensionGuard (first!), YouTube, update + extension engines
├── MainActivity         single activity, Compose host
├── ui/
│   ├── theme/           palette-driven tokens (MtPalette), Tsuki Night / Washi Day, Ambient colour, Unbounded type
│   ├── components/      MTBottomBar (floating glass capsule), MiniPlayerBar, MTTopBar, Pills, StateViews(AppMessages)
│   ├── screens/MTPlayerApp.kt  NavHost + Scaffold; tabs Home / Music / Videos / Stream / Library
│   ├── screens/online/  OnlineHomeScreen (For you), OnlineFeed (mix feed + channels), detail, search, extensions, market
│   ├── screens/youtube/ YouTubeVideoScreen + YouTubeMusicScreen (shared YtBrowse)
│   ├── screens/video/   VideoPlayerModal (player UI + error recovery), VideoScreen (device videos)
│   └── screens/music/   MusicScreen (+ MusicSourceSwitch), NowPlaying, lyrics
├── player/              MusicPlayerManager (direct ExoPlayer), StreamRecovery (pure retry policy)
├── service/             PlaybackService (MediaSessionService; ExoPlayer w/ YouTubeAudioDataSource)
├── stream/              StreamRequest model, StreamRepository, StreamResolver/Sniffer, PlatformRules,
│                        RequestAwareHttpFactory, StreamInterceptors, StreamRefreshers (new)
├── online/              ExtensionManager (.cs3 loading), OnlineRepository (home/search/load/links),
│                        OnlineSession (episode → links → failover), OnlinePlayback, ExtensionGuard (new),
│                        AccessLadder (new), web/HeadlessCapture (new), web/BrowserCheck (Cloudflare)
├── youtube/             NewPipeDownloader, YouTube (search/shelves/related/info), YouTubeStreams
│                        (DASH/HLS request building), YouTubeAudioDataSource (mtyt://), YouTubePlayback, YtHistory
├── network/ data/ lyrics/ subtitles/ cast/ update/
```

Key flows:

- Play anything video → `IncomingMedia.open(context, StreamRequest, …)` → `VideoPlayerModal`.
  `StreamMediaFactory` builds the MediaSource (HLS/DASH/progressive, merges separate audio tracks).
- Online title → `OnlineSession.start` → `OnlineRepository.loadLinks` (streams links as the
  extension finds them) → `OnlinePlayback.request(link)` → player. Player fatal errors call
  `OnlineSession.failover`.
- Music → `MainViewModel.playTrack(track, queue)` → `MusicPlayerManager` → shared ExoPlayer in
  `PlaybackService`. YouTube songs are `AudioTrack(uri = "mtyt://v/<id>")`, resolved lazily by
  `YouTubeAudioDataSource` (so a 50-song queue costs nothing until each song plays).

## 5. "For you" crash with many extensions — root cause and fix

Causes found:
1. Extensions throw from their own threads/coroutines (OkHttp callbacks, `GlobalScope`, Jsoup on
   the main thread). One bad extension's uncaught exception killed the whole process.
2. Every enabled extension loaded its home at once, each fanning out over all of its rows →
   dozens of parallel requests + parsing on the default dispatcher, starving the UI.
3. Lazy lists keyed by `item.key`; two rows (or the hero/pager after a reload) containing the same
   URL threw `IllegalArgumentException: Key was already used`, and the hero pager indexed
   `items[page]` after the list shrank.

Fixes:
- `online/ExtensionGuard.kt`: default uncaught-exception handler that swallows crashes whose
  stack is from extension code (anything outside trusted prefixes) on background threads, plus a
  guarded main-looper loop; a bounded `Dispatchers.IO.limitedParallelism(8)` dispatcher and a
  coroutine exception handler for all extension work. Skipped under Robolectric.
- `OnlineRepository.home`: supervisor scope, per-row guards, row/item caps (12 rows × 40 items).
- `OnlineHomeScreen`: staged loading (`Semaphore(3)` across extensions, selected channel first),
  positional unique keys everywhere, `getOrNull` in the hero pager.
- New YouTube-style UX (`OnlineFeed.kt`): extensions are **channels** (avatar strip, "Mix" first),
  their rows are **playlists** (shelves). Mix = hero + continue watching + My List + an infinite
  round-robin feed (`buildMixFeed`, unit-tested) of big cards tagged with the channel, with a
  playlist shelf every 4 cards. Tapping a channel opens its channel page (header, Top 10, playlists).

Known limit: `ExtensionGuard.frameIsExtension` treats `com.lagradost.*` as the bundled library, so
crashes from very old extensions that still use that package are not swallowed. A classloader
check (extension classes come from a `PathClassLoader` per plugin) would be the precise fix.

## 6. HTTP 403 "Access denied" — root cause and fix

Old path (`VideoPlayerModal.recoverFrom`): 403 → `StreamRecovery` → "Refresh" → `StreamResolver`
re-probed the **same** URL with the **same** headers (burning the token), then failed over to the
next link with the same headers, then showed the error. Links extracted at the start could expire
before failover reached them.

Why hosts refuse (same reasons CloudStream / Aniyomi / NewPipe users hit):
1. Hot-link protection checks `Origin` + `Sec-Fetch-*` that browsers/hls.js send but extensions don't declare.
2. CDN wants the site (provider `mainUrl`) or its own origin as `Referer`, not the embed URL.
3. CDN rejects browser UAs on media requests (expects a native player / okhttp).
4. Signed tokens expire or are bound to the extraction session/IP.
5. Token bound to the embed page's cookies/JS (Cloudflare, page-generated tokens).

New recovery ladder (no extension is removed or bypassed):
1. **YouTube**: `StreamRefreshers` re-extracts fresh signed URLs (twice max), keeps position.
2. **`online/AccessLadder`** (per URL, each step once): BROWSER fetch metadata → SITE referer →
   SELF (host) referer → NO referer → PLAYER UA → VLC UA → SELF_BROWSER (browser UA + host's own
   Referer/Origin). Pure + unit-tested. Network streams (Stream tab) use the same ladder: the
   resolver probes variants with `AccessLadder.firstWorking` before reporting a 403, and the
   player walks it again on a mid-playback 403.
3. **Failover** to the next link (`OnlineSession.failover`).
4. **Fresh `loadLinks`** once when every link failed (new tokens), resume position.
5. **`online/web/HeadlessCapture`**: loads the embed/episode page in an off-screen WebView
   (real Chromium TLS, JS, cookies, Cloudflare clearance), auto-starts the page player muted,
   captures the manifest the page requests with its exact UA/Referer/Origin/cookies, plays it
   natively. Reuses `PageStreamCapture` from the Stream → "Find streams in page" feature.
6. Only then the error card, with an accurate message.

Not done / ideas: per-host learned header profile (remember which ladder step worked for a CDN
host and apply it first next time); Cronet engine for hosts that fingerprint OkHttp TLS (JA3).

## 7. YouTube + YouTube Music (login-free)

- Library: NewPipeExtractor (same engine as NewPipe/LibreTube-style apps). ArchiveTune / AuraMusic
  / InnerTune talk to the InnerTube API directly with multiple clients; NewPipe already does the
  client rotation, deobfuscation (n-param / signature) and PoToken-less clients, and is
  maintained weekly — update `newpipeExtractor` in `libs.versions.toml` when YouTube breaks.
- `YouTube.shelf(YtShelf)` — kiosks (trending) + curated searches (Anime, AMV, J-Pop, Lo-fi…).
- `YouTubeStreams.videoRequest` — live → HLS; otherwise builds a local DASH manifest (video-only +
  audio tracks) from progressive/OTF streams; falls back to muxed.
- `YouTubeAudioDataSource` (`mtyt://v/<id>`) — resolves the best audio lazily, retries once on
  401/403/404/410 with a fresh extraction.
- Stream tab: pasted YouTube links now play in-app (falls back to the old YouTube-app hand-off).
- History is local only (`YtHistory`, SharedPreferences JSON).

### Login-only features

Still login-free by default. Since §16 there is an **optional** YouTube account (Settings → YouTube
account) used only to get past "confirm you're not a bot" and age walls. Subscriptions feed,
account playlists, likes sync and the YouTube Music library are still not built.

Terms note: YouTube's terms disallow third-party stream access; previous versions handed off to
the YouTube app for that reason. The owner explicitly requested in-app YouTube like ArchiveTune.

Sandbox caveat: from a datacenter IP Google answered stream requests with its "Sorry/unusual
traffic" page, so stream extraction was not verified end-to-end in CI; search, suggestions and
kiosks were verified. On a phone (residential IP) this is the normal NewPipe path.

## 8. Design system ("Tsuki") — replaces Sakura Night

The old multi-hue pink/violet/cyan aurora read as noisy. The Tsuki system follows how Spotify and
Apple Music do it: neutral surfaces, one accent, and colour that comes from the content.
- Palette (`ui/theme/Color.kt`): **Tsuki Night** — ink neutrals (bg `#0E0E11`, surface `#16161A`),
  moonlight-gold accent `#F5C46B` / `#FFDB94` / `#D9963F`, `onAccent` `#1C1407`. **Washi Day** — paper
  `#F6F5F2`, accent `#8A5A00`. Legacy `Brand*` vals are remapped to the gold family.
- **Ambient colour** (`ui/theme/Ambient.kt`): `AmbientColor.extract` downsamples the current artwork
  (Coil, 32 px), takes a saturation-weighted circular hue mean, clamps lightness/saturation and caches
  it; `rememberAmbientColor` animates it (900 ms) and falls back to the accent. Provided app-wide via
  `LocalAmbientColor` in `MTPlayerApp`. Setting: `ThemeSettings.ambient` (Settings tag `toggle_ambient`).
- `pageBackground` (`DesignSystem.kt`): neutral vertical gradient + one ambient halo (top right) + a
  faint echo + a few faint sparkles. Glass borders are neutral white hairlines.
- **Dock** (`MTBottomBar.kt`, `MiniPlayerBar.kt`): mini player and nav are one capsule. Constants
  `DockHorizontalPadding 12`, `DockBottomPadding 10`, `DockMaxWidth 460`, nav 68 dp, mini 66 dp, gap 3 dp,
  corners 30 (standalone) / 26 outer / 10 at the junction. `MiniPlayerBar(attachedToNav)`,
  `MTBottomBar(joinedWithMiniPlayer)`.
- Spacing (home): gutter 16, section gap 28, header gap 12, tile gap 10, list rows 72 dp / 56 dp art.
- Type: Unbounded (display/headline/titleLarge) + Manrope body. Japanese overlines stay.
- Logo / launcher / TV banner recoloured to gold on ink (`res/drawable/ic_*`, `docs/logo.svg|png`).

### Home (rewritten)
1. **Continue listening & watching** — `ResumeMusicCard` (tags `home_continue_card`, `home_continue_play`)
   and `ResumeVideoCard` (`home_continue_video`). The video is the most recent of device video
   (`lastWatchedTimestamp`), online history (`OnlineHistory.updatedAt`) or YouTube history, computed in
   `MTPlayerApp` as `ResumeVideo`.
2. **Shortcuts** — bento tiles (`quick_card_*`); search and settings were removed (they are in the header).
3. **For you** — endless feed built by `FeedGenerator`: blocks of tracks, video rows
   (`home_video_grid_row`), albums, YouTube related (from `YtHistory`) and online picks, cycling and
   reshuffling; a loader item (`home_feed_loading`) appends 4 blocks when it becomes visible, capped at
   `MAX_FEED_BLOCKS`. Shuffle button `home_feed_shuffle`.
4. **Dream deck** (`DreamDeckCard`, tags `storage_stat_banner`, `banner_rescan_button`) moved to the top
   of Library.

### Protection
- Licence **GPL-3.0** (required anyway: the app bundles GPL CloudStream library code and NewPipe Extractor).
  `NOTICE` adds GPL §7 terms; `TRADEMARKS.md` reserves the name/logo (forks must rename + change
  `applicationId`). All own Kotlin files carry a "Do not remove or alter this notice" header.
- `security/IntegrityGuard.kt`: compares the running APK's signing cert SHA-256 with the official one
  (from `.github/release-signing-cert.sha256`, XOR-obfuscated into `BuildConfig.INTEGRITY_BYTES`).
  Unofficial → auto-update off and a notice in Settings → About (`unofficial_build_notice`). Debug builds
  are never flagged.
- R8 strips `Log.v/d/i` in release. `SECURITY.md`, `CODEOWNERS`, Dependabot, read-only workflow
  permissions; GitHub secret scanning + push protection, Dependabot alerts/fixes and private
  vulnerability reporting are enabled on the repo.

## 8b. Yumeiro release notes (rebrand, bento home, watch page, seamless updater)

- **Name**: Yumeiro (夢色, "dream-colored"). Repo `siamform/yumeiro`. `applicationId` is
  `dev.asfakul.yumeiro` (renamed from `dev.asfakul.mtrevanced` on 2026-11-07).
- **Naming**: online extension channels are **Hues**; the combined feed is **Spectrum**.
- **Home** (`ui/screens/home/HomeScreen.kt`): hero `VaultSummary` + bento grid of shortcuts
  (`BentoTile`, tags `quick_card_*`) covering every feature, then personal shelves. List tag `home_list`.
- **Hue page** (`ui/screens/online/OnlineHuePage.kt`): playlist tabs + round-robin infinite feed paged with
  `OnlineRepository.page`, YouTube-like `FeedCard`s with skeletons. `ChannelAvatar` loads `HueLogo`
  candidates (extension `iconUrl`, Google s2 favicon, `/favicon.ico`) and falls back to initials.
- **Watch page**: `VideoPlayerModal(watchPage = …)` docks the player 16:9 at the top in portrait
  (`LocalPlayerDocked` = true) and renders the page below; rotating / fullscreen gives the full player.
  - Online: `OnlineWatchPanel` (`OnlinePlayerUi.kt`) — meta chips, next episode, Sub/Dub, sources &
    subtitles sheet, plot, seasons, episode list with progress, "More like this".
  - YouTube: `youtube/YtWatch.kt` state + `YouTubeWatchPanel`/`YouTubeWatchLayer` (`ui/screens/watch/WatchScreens.kt`).
  - Shared building blocks + skeletons: `WatchScaffold`, `WatchDetailsSkeleton`, `WatchRowSkeleton`.
  - Device videos keep the full-screen player.
- **Skeletons** replace spinners on search, row ("see more") pages, Hue feed and the watch page.
- **Seamless updater** (`update/`): `AppVisibility` tracks foreground; `UpdateManager` downloads silently
  and installs when the app is backgrounded ≥15 s and no audio plays (rechecks every 60 s).
  `ApkInstaller` sets `USER_ACTION_NOT_REQUIRED`, `PACKAGE_SOURCE_OTHER`; `PackageReplacedReceiver` posts the
  "updated" notification. Toggle: `UpdatePreferences.seamlessUpdates` (Settings tag `toggle_seamless_update`).
  Play Protect prompts are caused by an unregistered signing key — fix via Android developer verification.

## 9. Roadmap (next steps, in order)

1. Device QA of the new feed, YouTube video/music and 403 ladder on real extensions; fix spacing.
2. Per-host learned 403 profile + optional Cronet transport.
3. ExtensionGuard classloader-based detection (see §5).
4. YouTube: related-video "Up next" + autoplay, chapters, SponsorBlock, comments (all login-free).
5. YouTube Music: radio from a song (`YouTube.related`), albums/artists pages, synced lyrics
   (LRCLIB is already used by `lyrics/`), offline downloads.
6. Unified search across channels + YouTube with source chips.
7. Mixed Videos tab option ("Mix" = online channels + YouTube in one feed).
8. Redesign pass on the remaining legacy screens (Library, Settings, Stream lists).

## 9b. Player, music and help additions

- **Floating player**: `VideoPlayerModal(minimized, onMinimize, onExpand, minimizedBottomInset)`;
  back (setting `PlayerSettings.backAction`: MINIMIZE / PIP / CLOSE) or `DragMode.SWIPE_DOWN` in
  `VideoPlayerGestures` shrinks it into a draggable window (`video_floating_player`). State lives in
  `MTPlayerApp.videoMinimized`.
- **HUD**: `VerticalLevelBar` in `VideoPlayerHUD.kt` replaces the boxy brightness/volume panels.
- **Sleep timer**: `player/SleepTimer.kt` (countdown with 20 s fade, end of track), owned by
  `MainViewModel.sleepTimer`, UI in `ui/screens/player/SleepTimerSheet.kt`.
- **Error suggestions**: `ui/components/ErrorHelp.kt` (`parse`, `suggestionsFor`, `ErrorSuggestions`),
  used by the playback error overlay, watch page, online player, `StateMessage` and Now Playing.
- **User guide**: `ui/screens/settings/UserGuide.kt` (drawn diagrams, opened from Settings).
- **Stream watch page**: `ui/screens/watch/StreamWatchPanel.kt` (`StreamWatch.ordered/neighbour`).
- **Touch feedback**: `ui/theme/PressIndication.kt` (soft shrink + rounded overlay as `LocalIndication`).
- **Dock**: `MTBottomBar` slimmer, inset further from the edges; mini player matches the nav height.

## 10. Tests

- `app/src/test/.../revamp/RevampLogicTest.kt` — mix feed (interleave, dedupe, unique keys),
  AccessLadder steps/headers, ExtensionGuard frame classification, YtHistory codec, formatting.
- `app/src/test/.../polish/PolishLogicTest.kt` — sleep timer (fade, cancel, extend, end of track),
  error suggestions, `AccessLadder.firstWorking`, stream watch ordering/neighbours.
- Existing suites: stream detection, online pages (Compose UI), player recovery, etc.
- `app/src/androidTest` needs a device (`OnlinePlaybackDeviceTest`).

## 11. Similar open-source projects (for reference)

- CloudStream (extension model this app uses) — https://github.com/recloudstream/cloudstream
- ArchiveTune (design + YouTube Music) — https://github.com/rukamori/ArchiveTune
- AuraMusic (YouTube Music, multi-client fallback) — https://github.com/TeamAuraMusic/AuraMusic
- NewPipe / NewPipeExtractor — https://github.com/TeamNewPipe/NewPipeExtractor
- LibreTube, InnerTune / Metrolist (YouTube Music clients), Aniyomi, Kotatsu, Saikou/Dantotsu —
  similar extension-driven streaming apps worth studying for 403/Cloudflare handling.

## 12. Stability pass (2026-11-08): black screen, force close, tab swipe, captcha loop

Branch `fix/stream-blackscreen-captcha-swipe`.

### "Verify you're human" that never shows, then "you have been blocked"
- Root cause: `BrowserCheck.isChallenge` matched `challenge-platform` / `turnstile` in the body of any
  Cloudflare 403/429/503. Cloudflare injects `/cdn-cgi/challenge-platform/scripts/jsd/main.js` into
  ordinary pages and into its block pages, so plain 403s (hot-link, WAF, API) opened the check page.
  The page then loaded the exact API / media URL, which the WAF answers with "Sorry, you have been
  blocked" (also because WebView adds `X-Requested-With: <package>`).
- Fix (`online/web/BrowserCheck.kt`): `classify()` returns `Verdict.CHALLENGE / BLOCKED / NONE`, using the
  documented `cf-mitigated: challenge` header first, then only real challenge markup. Blocks never open
  the dialog; the user gets one calm message per host every 5 min. `challengePage()` solves POST, API and
  media requests on the site root. The dialog (`BrowserCheckHost`) detects a block page, retries the root
  once, then closes with the message. `SafeWebView.harden` drops `X-Requested-With` where supported.
  Same approach as Mihon (PR mihonapp/mihon#2200, commits 7ae858e, 649fc83).
- Tests: `app/src/test/.../online/web/BrowserCheckTest.kt`.

### Stuck after "finding best source", black screen, back removes it
- The interactive interceptor was on player media requests (`OnlinePlayback.registerInterceptors`), so a
  segment 403 could hold the loader thread for 20 s headless plus a 120 s dialog over the player.
  Media / downloads now use `BrowserCheck.MediaInterceptor` (headless only, never a dialog).
- `PlayerView` is inflated from `res/layout/yumeiro_player_view.xml` with `surface_type="texture_view"`:
  a SurfaceView ignores Compose scale / translate / clip (swipe-down, floating, docked player) and can
  stay black after a dialog closes over it.
- `OnlineSession.GATHER_MS` 3500 → 2000 ms (shorter wait after the first working link).

### Force close / freeze (ANR)
- `solveBlocking` (runBlocking + Main) is skipped on the main thread; `userAgent()` no longer waits on the
  main thread from a worker for more than 3 s (fallback UA).
- Every WebView (headless check, dialog, `HeadlessCapture`, `PageStreamFinderDialog`) uses
  `SafeWebViewClient`: `onRenderProcessGone` destroys the view instead of letting Android kill the app.
- `DdosGuardKiller` only solves on a real challenge, not on every 403.

### Tab swipe
- The release-only `Modifier.tabSwipe` (needed empty space, did not follow the finger) is gone. The five
  tabs are pages of one `HorizontalPager` inside the Home destination (`tab_pager`). `currentRoute` is the
  pager's tab on the root; `navigateTop` pops to Home and scrolls the pager (slide for neighbours, jump
  for far tabs). Back on another tab returns to Home, then double back exits. Rows and carousels keep
  their own drags through nested scrolling; swipes are disabled under the full-screen video and Now
  Playing. The old tab routes still exist and redirect to the pager page.
- `OnlineLibrary` is now a sub-route (no top/bottom bar on that page).

### Not verified on a device
No emulator in the build sandbox: run `OnlinePlaybackDeviceTest`, try miruro.to (challenge + block),
a single-video movie source, floating / docked player and tab swipes on a real phone before release.

### Ideas from similar apps (next)
- Mihon: per-source "Open in WebView" action and a settings toggle to clear cookies / WebView data.
- CloudStream: per-extension "Test" screen and source ranking by previous success.
- NewPipe / LibreTube: ExoPlayer load-control tuning for faster start, background play queue.
- Aniyomi: player "skip intro" chapters via AniSkip.

## 13. Offline downloads (anime, movies, YouTube video and music)

**Where:** `online/download/` (logic), `ui/screens/online/DownloadSheets.kt` (sheets), Library → Videos (folders),
Settings → Downloads.

**Anime / movies / series (extensions)**
- Detail screen: **Download** button (this season, all episodes, next 5, or the movie) and a per-episode icon.
- Each title + extension + audio version gets one folder: `Title-(Extension-(Sub|Dub))`, episodes inside as
  `S01E03 - Name.mp4` (`DownloadNaming`). Same idea as Aniyomi's `downloads/<source>/<title>/` layout, flattened to one
  readable folder per series.
- Links are resolved at download time (`DownloadWorker.resolveEpisode`, 90 s timeout) through the extension, so
  queued episodes don't use expired links. `DownloadNaming.pickLink` never picks DASH, respects the quality cap.
- `DownloadGate`: user-set parallelism (1–3) and only one job per extension at a time (sites rate-limit).
- Offline playback: Library → Videos → Downloads, grouped by series folder; episodes show a check on the detail screen (`OnlineDownloads.isSaved`).

**Auto-delete:** Settings → Downloads → timer (off, 1, 3, 7, 14, 30 days after finishing). Pin an item or a whole
folder to keep it permanently; "Keep new downloads" makes pinning the default. `OnlineDownloads.cleanup` runs at app start
and daily from `DownloadCleanupWorker`. Rule is pure and tested: `DownloadNaming.expired`.

**YouTube / YouTube Music:** Watch panel → Download. `YtDownloadOptions.build` turns NewPipe Extractor streams into one
choice per resolution/fps (H.264 first, then VP9, AV1 on Android 14+) plus every audio bitrate. HD video and audio are
fetched separately (8 MB range requests, like NewPipe, to avoid throttling) and joined with Android `MediaMuxer`
(`MediaMux`), MP4+AAC or WebM VP9+Opus. Optionally copied to the phone's Movies/Yumeiro or Music/Yumeiro (MediaStore).

**Design decision:** no yt-dlp/ffmpeg bundle (as in Seal / YTDLnis via youtubedl-android) — it adds ~30–60 MB and a
Python runtime. NewPipe Extractor is already in the app, and MediaMuxer covers the containers YouTube serves.

**References studied:** Seal (github.com/JunkFood02/Seal), YTDLnis (github.com/deniscerri/YTDLnis),
youtubedl-android (github.com/yausername/youtubedl-android), NewPipe muxers (`Mp4FromDashWriter`, PR #2149),
Aniyomi download/storage docs (aniyomi.org/docs/faq/downloads, /storage), Android MediaMuxer reference.

**Tests:** `DownloadNamingTest`, `YtDownloadOptionsTest`.

### 13b. Download flow and Downloads page (follow-up)

- **Ask first:** every download (episode icon, title sheet, player button, YouTube) goes through
  `ui/screens/downloads/DownloadPrompt.kt`. The dialog shows quality, size and free space, where the file goes
  (Downloads folder + phone path, or Movies/Music/Yumeiro for YouTube), **Permanent vs Offline** (with the auto-delete
  timer chips inline) and tips. "Don't ask again" → `DownloadSettings.askFirst` (Settings → Downloads turns it back on).
  After starting, a message with **View** opens the Downloads page.
- **Downloads page** (`Screen.Downloads`, `DownloadsScreen.kt`): storage card, offline settings (same
  `DownloadSettingsCard`), dismissible tips, filters (All / Offline / Permanent / In progress) and sections
  In progress → Permanent → Offline, each grouped by series folder (`DownloadGroups`, unit-tested).
- **Entry points:** Home shortcut "Downloads", Library → Videos (same grouped sections + Manage), the download
  notification (tap opens the page via `DownloadWorker.ACTION_OPEN_DOWNLOADS` → `DownloadsNav`).
- **Tests:** `DownloadGroupsTest`, `DownloadUiScreenshotTest` (Robolectric; renders to `app/build/screenshots/dl_*.png`).

### 13c. Notifications that stayed in the inbox (fix)

**Bug:** feed items (new extensions, playlists, releases, news) appeared in the bell inbox but never in the
status bar. Causes:
1. `AppFeed.sync` marked **every item of the first sync** `inboxOnly`. The first periodic `DiscoveryWorker`
   run happens right after install, so a new user's whole first batch (the live feed has 4) was silent.
2. `AppNotifier.post` only checked the app-level switch, not a blocked **channel** (user swiped "Don't show
   these" or turned a category off) or the in-app "Show notifications" switch; the inbox banner only covered
   the permission case, so users saw no reason.
3. Only `SecurityException` was caught around `notify()`; any other failure would kill the worker run.

**Fix:** `AppFeed.shadePlan` (first sync: newest item from the last 3 days still notifies; later syncs: newest 2,
plus an "N more updates" summary), `AppNotifier.block()` returns the exact reason (permission, app off, in-app
switch, channel off) and is shown in the inbox banner and Settings, `Send a test notification` in Settings →
Notifications, all exceptions caught, explicit priority/category/timestamp. Items already filed silently by
older builds are not re-sent.

## 14. Universal sources update (v1.0.5, branch `feat/universal-sources`)

Full list for users: [UPDATE-universal-sources.md](UPDATE-universal-sources.md). Code map: [ARCHITECTURE.md](ARCHITECTURE.md).
Workflow: [DEVELOPMENT.md](DEVELOPMENT.md). Credits: [INSPIRATION.md](INSPIRATION.md) and `NOTICE`.

- **Bridges, not rewrites.** Aniyomi, Mihon, Stremio, JavaScript and home-server sources are converted to CloudStream
  `MainAPI` (or `MangaProvider` for manga) and published through `SourceHub`. Everything downstream works unchanged.
- **Aniyomi/Mihon** extension APKs are downloaded, checked for the same signer on update, and their DEX is loaded
  against the vendored source API in `eu/kanade/tachiyomi/**` (Apache-2.0). `TachiyomiRuntime` sets up the Injekt graph
  (`Application`, `NetworkHelper`, `Json`, `JavaScriptEngine`) the extensions ask for. ProGuard keeps these packages.
- **JavaScript** sources run in QuickJS (`com.dokar.quickjs`). `mangayomi_runtime.js` provides `MProvider`, `Client`,
  `Document`/`Element`; every native call goes through `JsBridge`.
- **SMB** files are served to the player by `SmbProxy` on 127.0.0.1 with a random token.
- **Free only.** No server was added. Watch together is LAN-only (NSD). The status page reads the `extensions` branch
  `index.json` that the existing workflow already writes.
- **Updater 504.** `github.com/.../releases/download/...` returned GitHub's own 504 page while the API was fine.
  `GitHubReleaseClient.resolveDownload` now asks the API asset URL (`Accept: application/octet-stream`, no redirect
  follow) for the signed `release-assets.githubusercontent.com` link and only falls back to the browser URL. The
  website has `/download` doing the same server-side. Old installs (≤1.0.4) may still hit the 504 until they update
  once from the website.
- **Library redesign** in `ui/screens/library/LibraryDesign.kt`: hero with stats, Music/Videos/Reading tabs.
- **Tests:** `sources/UniversalSourcesLogicTest` covers WebDAV/DLNA parsing, unpacker, URL normalising, natural
  sort, zip-slip, Trakt id pick, room clock and labels.
- **Not verified on a device** for every ecosystem. Self-signed HTTPS on home servers is rejected. No torrents.

## 15. Stability and polish pass (v1.0.5, branch `feat/v1.0.5-stability`, replaces PR #13)

PR #13 (`feat/universal-sources`) failed CI for two independent reasons; both are fixed here and its content is
carried over unchanged otherwise.

- **gitleaks (`generic-api-key`).** `tracking/Kitsu.kt` had the Kitsu OAuth client secret as a string literal. It now
  comes from `BuildConfig.KITSU_CLIENT_ID/SECRET` (Gradle property `kitsuClientId`/`kitsuClientSecret` or env
  `KITSU_CLIENT_ID`/`KITSU_CLIENT_SECRET`, set as repo secrets and passed in `release.yml`), or from a user-entered
  value in `OnlineSettings.secret("kitsu_client*")`. Builds without it show "Kitsu sign-in is not set up in this
  build". The branch history never contains the literal.
- **CodeQL (15 alerts, High gate).** Real fixes: `TachiyomiExtensions` trusted set is sealed with `SecretBox`
  (`trusted_v2`, legacy key migrated); app lock binds the prompt to a Keystore key (`security/LockKey`, API 30+,
  `CryptoObject`), so a hooked callback can't unlock. Reviewed suppressions (rule + path, see
  `docs/security/AUDIT.md` "CodeQL suppressions"): MD5/AES-CBC needed by the CryptoJS/Mangayomi ABI now live only in
  `sources/js/JsCrypto.kt`; `eu/kanade/**` is the vendored Tachiyomi API; certificate pinning for Kitsu/Trakt/Plex.
  `codeql.yml` analyses with `upload: never`, filters the SARIF with `.github/codeql/suppressions.jq`, then uploads.

User-reported bugs, root cause → fix (all with unit tests in `app/src/test`):

| Bug | Root cause | Fix |
|---|---|---|
| Phone sleeps while a stream plays | Compose player never set `keepScreenOn`; ExoPlayer had no wake mode for network streams | `player/ScreenWake` + `View.keepScreenOn` while playing/buffering; `setWakeMode(NETWORK)` for network URIs, `LOCAL` otherwise; buffer budget sized from the memory class |
| Black screen mid-stream ("like ads") | Hosts splice ad segments (other codec/resolution, `#EXT-X-DISCONTINUITY`) into HLS; decoder outputs no frames while audio continues | `stream/HlsAdFilter` (VOD playlists only: CUE-OUT/DATERANGE ad blocks, ad URLs, short foreign-host discontinuity groups, max 35% removed) via `AdFilterDataSource`; `online/web/AdFilter` blocks ad hosts/VAST in WebViews; `player/BlackFrameWatchdog` flushes the decoder after 4 s without frames, then fails over |
| Freeze / force close with extensions | Link/subtitle bursts re-sorted and re-published state per event; dead links failed over within milliseconds; results keyed by the extension-reported `apiName` collided in the grid | Publish throttled to 300 ms; failovers spaced ≥900 ms; result ownership forced to the extension that answered (`toItem(owner)`) and de-duplicated keys |
| Notifications only after opening the app | Periodic work had a battery-not-low constraint and a 6 h period; OEM battery managers kill it | 2 h feed / 4 h episodes, no battery constraint, exponential backoff, schedule version bump; catch-up chain when the app opens; `BackgroundHealth` heartbeat + "background checks stalled" card |
| Battery optimization button did nothing | Opened a generic list (or nothing) | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (manifest permission added) → OEM page (Xiaomi, Huawei, Oppo, Vivo, Samsung) → app details → list |
| Extensions need a VPN on some networks | ISP DNS filtering (no answer / sinkhole / reset); DoH was manual and opt-in | System DNS + **auto bypass** (default on): `SmartDns` detects NXDOMAIN-while-online and bogon answers, `AutoBypassInterceptor` marks hosts on connect/TLS reset and retries GET/HEAD once; DoH rotates Cloudflare → Quad9 → Google, per-host choice persisted. VPN detection + VPN settings shortcut; errors no longer say "No internet" when online |
| Sub → dub played another episode | `switchVersion` fell back to the same list index when numbers didn't match | `online/EpisodeMatcher` (season+episode, absolute number, title); no match → stays on the current version with a message, panel shows "Not for this episode" |

Features:
- Videos opens on **Hues** when online, **Device** when offline; follows connectivity until the user picks a source.
- Hues search: `SearchRanker` (exact > prefix > contains > all words > some words, stable by source order), per-query
  10 min cache, chips filter the ranked list.
- Alerts: `ui/components/TsukiDialog` is a drop-in for `AlertDialog` (same parameters: glass gradient, ambient glow,
  gold hairline, icon badge, spring entrance); all 38 dialogs use it. Reminder cards and in-app messages restyled;
  follow buttons use each network's brand colour.

Not done (needs money or a server): true push while the app is fully killed on OEMs that block WorkManager. The free
option is UnifiedPush/ntfy (roadmap). Not verified on a physical device in this pass.

## 16. Settings redesign, setup guides, optional YouTube account (branch `feat/settings-redesign`)

Pushed for review; **no PR opened and nothing merged** (owner's instruction).

### Settings
- `ui/screens/settings/SettingsCatalog.kt` is the single source of truth: 5 groups ("Make it yours", "Watch & listen",
  "Sources & accounts", "System", "Help & about") and 19 categories with title, summary, search keywords and an
  optional route to an existing screen. Search: every word must match; title > summary > keyword; unit-tested.
- `SettingsScreen.kt` home: header (version + user-guide button `open_user_guide`), search, a health strip
  (notifications / battery / network status), glass groups of category rows, the support card, "Made in Bangladesh".
  Each category opens its own page that reuses the existing cards. Design pieces live in `SettingsDesign.kt`
  (`GlassGroup`, `CategoryRow`, `StatusChip`, `CategoryHero`, `GuideCard`…), in the Tsuki gold/glass style.
- Removed three toggles that did nothing (not persisted, not read anywhere): Bass boost (the real one is in the
  equalizer), Gapless (ExoPlayer playlists are already gapless) and Scan on launch (the library is read from
  MediaStore on every launch anyway).
- Trackers moved out of "Online videos" into **Tracking**: one card per tracker (status chip, Set up / Sign in /
  Sign out) and the AniList / MyAnimeList client-ID dialogs, with a copy button for the redirect URL.

### Setup guides
- `SetupGuides.kt`: step-by-step guides with links, copy chips and tips for Aniyomi, Mihon, Stremio, JavaScript
  (Mangayomi) repos, home servers, CloudStream, AniList, MyAnimeList, Kitsu, Trakt and the YouTube account.
- Shown as collapsible `GuideCard`s in each Sources hub tab (expanded while that tab is empty), in Extensions,
  in Tracking and on the YouTube account page. The redirect values users copy are tested against
  `AniList.REDIRECT`, `MyAnimeList.REDIRECT` and the Trakt OOB URI. Yumeiro still ships no repository links.

### Optional YouTube account
- Research: ArchiveTune opens Google's sign-in page in a WebView and keeps the cookies; NewPipe refuses login
  (privacy, account bans); PipePipe uses a login cookie only for playback when blocked. See `INSPIRATION.md`.
- `youtube/YtAccount.kt`: cookie captured from a hardened WebView (`YouTubeAccountScreen.kt`: "; wv" stripped
  from the UA, non-http schemes blocked), sealed in `OnlineSettings` secret `yt_cookie` (excluded from backups).
  Mode **When YouTube blocks a video** (default: the extractor retries once with the account only after
  `SignInConfirmNotBotException` / `AgeRestrictedContentException`) or **Always**. The cookie and a
  `SAPISIDHASH` header go only to youtube.com / www / m / music, never googlevideo or ytimg (separate OkHttp client
  without the session-cookie interceptor). Sign out expires Google/YouTube WebView cookies only.
- `youtube/YtAuthHash.kt` holds the SHA-1 signature (YouTube's format); CodeQL suppression documented in
  `docs/security/AUDIT.md`.
- Risks to tell users (also in the guide): Google may refuse sign-in from embedded browsers; use a spare account.

### Support
- `ui/screens/extras/SupportMessage.kt`: one honest message (donating is optional, the app stays free with no ads,
  the developer and team keep it alive unfunded) used on the support page, About, the Settings home card and the
  reminder card. Coffee and star (until starred) reminders now return every 2 h (`Reminders.OFTEN_MS`); follow stays
  4 h; minimum gap between any two reminders 60 min. Users can still turn reminders off.

Tests: `SettingsCatalogTest`, `SetupGuidesTest`, `YtAccountTest`, `RemindersTest` (new 2 h case). Not tested on a
physical device.

