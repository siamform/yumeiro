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

- Gradle 9.x, AGP with `compileSdk 37`, `minSdk 24`, Kotlin + Compose (BOM in `gradle/libs.versions.toml`).
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
   (LRCLIB online lyrics landed in §22; it was not wired in before despite this note), offline downloads.
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

## 17. Paste-cookie YouTube sign-in and free UnifiedPush alerts (branch `feat/settings-redesign`)

Pushed for review; **no PR opened and nothing merged** (owner's instruction).

### Paste cookie (fallback when Google blocks the in-app sign-in page)
- Modelled on ArchiveTune's "Login with token" (`TokenEditorDialog`, `***INNERTUBE COOKIE*** =…` lines,
  checked with `hasYouTubeLoginCookie`). ArchiveTune has no "how to get it" guide; Yumeiro adds one.
- `youtube/YtCookiePaste.kt` (pure, unit-tested) accepts: raw `cookie` header value (with/without `Cookie:`),
  DevTools "Copy as cURL" (`-b` or `-H 'cookie:'`), Netscape `cookies.txt`, JSON exports (Cookie-Editor etc.)
  and ArchiveTune/Metrolist token text. Only `youtube.com` cookies are kept. Needs SAPISID + SID.
- `YtAccount.verify` loads `https://www.youtube.com/account` with the cookie (fresh client without the session
  interceptors): redirect to accounts.google.com = signed out, `"LOGGED_IN":true` = OK, else unknown (user may
  save anyway). Then the normal `YtAccount.save` (Keystore-sealed, no backup).
- UI: "Paste cookie (advanced)" on Settings → YouTube account → `PasteCookiePage` (warning, monospace field,
  paste-from-clipboard, Check and save). Guide `SetupGuides.youtubeCookie` follows yt-dlp's advice (private window,
  `youtube.com/robots.txt`, close the window so the cookie is never rotated), DevTools steps, the MIT
  "Get cookies.txt LOCALLY" extension, Cookie-Editor on Firefox for Android.

### UnifiedPush alerts (optional, free)
- Library `org.unifiedpush.android:connector:3.3.5` (Apache-2.0, Maven Central). Tink's `protobuf-java` is
  excluded (clashes with protobuf-lite; Web Push decryption doesn't need it).
- No server of ours: `notify/PushWake.kt` publishes **scheduled** "wake" messages to the app's own endpoint using
  ntfy's free scheduled delivery (`In:` header, 10 s – 3 days). `PushPlan` keeps one wake-up every 3 h for 24 h
  plus a 71 h backstop; each wake-up tops it up (~9 messages/day, ntfy.sh's free limit is 250). The query
  (`?up=1`) is stripped for publishing: in a test against ntfy.sh (2026-11-09) `?up=1` publishes were refused
  ("cannot publish to UnifiedPush topic without previously active subscriber"), while plain publishes with `In:`
  to the same topic were accepted and delivered on time to a live subscriber.
  If the server's echo has no future `time`, the server can't schedule (non-ntfy distributor) and the UI says so.
- `PushWakeService` (connector `PushService`, not exported) → `PushWake.onMessage` (payload ignored by design) →
  `BackgroundWork.wake`: feed + new-episode checks as **expedited** work (20 min throttle), plus a refill.
  Both workers implement `getForegroundInfo` (only used on Android 9–11, channel `yumeiro_sync`, IMPORTANCE_MIN).
- The connector's receiver drops broadcasts whose token doesn't match the registration, so other apps can't
  fake a wake-up. Its `RaiseToForegroundService` / `MessagingReceiverImpl` are exported by the UnifiedPush spec.
- UI: Settings → Notifications → On-time alerts (`PushAlertsCard`: toggle, push-app picker, status with last
  wake-up / planned count, Send test) + guide `SetupGuides.unifiedPush` (ntfy F-Droid / Play, battery). WorkManager
  stays the default; turning push off unregisters and deletes the endpoint (Keystore secret `push_endpoint`).

Tests: `YtCookiePasteTest` (8), `PushPlanTest` (8). 296 unit tests pass; lint clean of new issues. Not tested on a
physical device (needs ntfy installed and a real Google account).


## 18. Playback and extension fixes (branch `fix/playback-extensions`)

**"Expected URL scheme 'http' or 'https'…"**: extensions sometimes hand back relative (`/embed/1`), protocol-relative
(`//cdn…`), JSON-escaped (`https:\/\/…`) or typo'd (`https:/…`) links. `online/UrlFix.normalize` repairs them;
`online/SafeRequests` (a NiceHttp `Requests` subclass installed as CloudStream's global `app`) normalizes every
extension request against the referer or the provider's `mainUrl` (`ProviderBase` coroutine element). Links,
audio tracks and subtitles from `OnlineRepository` are normalized too. These errors no longer tell users to update
the extension (`OnlineErrors.isBadLink`, `ErrorHelp.forExtension`).

**Updater + safety bridge**: every install path goes through `ExtensionManager.gate()` →
`ExtensionScanner` (reads the `.cs3`'s DEX: strings, types, method refs; flags shell commands, hidden code loading,
native code, SMS/contacts/location/camera, installers/accessibility, account reads, miners, storage deletes as
DANGER; trackers, device IDs, clipboard, app lists, JS bridges as CAUTION) → `ExtensionReview` dialog
(`ExtensionReviewHost`). Modes: ALWAYS (manual installs), IF_RISKY (Hues market, updates), AUTO (starter set, sync —
DANGER is still refused). DANGER needs "Install anyway" twice. Installed cards have Check for update / Update to vX
(`checkUpdate` re-reads the repo with `force=true`; manual installs re-download and compare SHA-256) and a Safety pill.
The scanner is a heuristic: it can't see code downloaded at run time (that itself is flagged).

**Firewall / captcha / sign-in**: `BrowserCheck.openSite(url, purpose)` opens the site in a manual WebView
(no auto-close, Done button). Cookies and the WebView user agent are reused for that host by both the extension
and media interceptors. `SiteLogins` remembers signed-in hosts (Sign out clears their cookies). LOGIN mode shows a
short sign-up guide (`SignupSteps`). Firewall errors show "Open site".

**Audio**: NextLib's FFmpeg audio renderer (`nextlib-media3ext`, GPL-3.0, ARM only — x86 uses platform decoders)
is added after the platform decoders (`YumeiroRenderersFactory`), so AC-3/E-AC-3/DTS/TrueHD/Opus/Vorbis/FLAC play
on phones without hardware support — that was the "video without sound" case. The HLS ad filter is off for streams
with separate audio playlists (it cut segments differently in audio and video, causing desync and the black frozen
picture after switching audio). Audio switching (`switchAudio` in `VideoPlayerModal`) refuses unplayable tracks,
seeks to flush decoders, and reverts to the previous track on error or after 10 s stuck buffering. Duplicate
per-variant audio entries are merged (`TrackPicks.dedupeAudio`).

**Subtitles / CC**: the CC button state now matches the "captions by default" setting (its first tap used to do
nothing). Turning captions on picks a track explicitly (`TrackPicks.bestText`: phone language → English → default).
`SniffingSubtitleParserFactory` picks the parser by content (SRT served as VTT, header-less VTT, UTF-16), and
`SafeSubtitleDataSource` turns a failed subtitle download into an empty track instead of a playback error. Subtitle
delay is applied at parse time (`SubtitleTiming`) and reloads the subtitle files. Subtitles an extension finds
after playback starts appear under "More from this source" in the captions menu.

Not yet verified on a physical device. Tests: `UrlFixTest`, `ExtensionScannerTest`, `SiteAccessTest`, `SubtitleAudioTest`.

## 19. Subtitle sites, external download managers, softer chime, ad shield (branch `feat/subtitles-downloaders-adshield`)

**Subtitles**: `online/subtitles/SubDL` (api.subdl.com, key in the query string) and `SubSource` (api.subsource.net v1,
`X-API-Key` header) join OpenSubtitles.com. Keys are the user's own free keys, stored encrypted like the
OpenSubtitles key (`OnlineSettings.subdlKey` / `subsourceKey`, `SecretBox`), never logged; error texts never contain
URLs. `SubtitleHub` searches every site with a key in parallel and interleaves results; one site failing shows its
error without hiding the rest. SubDL / SubSource send ZIPs: `SubtitleArchive.pick` unpacks with size caps, picks the
episode from season packs (SxxEyy, 1x02, "Show - 07"), decodes UTF-8 / UTF-16 / Windows-1252, and the file goes to
`cacheDir/subtitles` as a `file://` URI (plays through `DefaultDataSource`). Language mapping: `SubtitleLanguages`
(SubDL "EN"/"BR_PT", SubSource "english"/"farsi_persian", matching Bazarr's converters). The local video player's
dialog adds them via `subtitles/SubtitleSitesProvider`. Limits: SubDL free keys ~2,000 searches/day; SubSource
60 requests/min.

**External download managers**: Settings → Downloads → "Download with" (`DownloadSettings.downloader`): Yumeiro,
any installed 1DM / 1DM+ / 1DM Lite / ADM / ADM Pro, or "Ask each time". `ExternalDownloader.send` uses 1DM's
documented intent (`idm.internet.download.manager.Downloader`, extras `extra_headers`, `extra_referer`,
`extra_useragent`, `extra_cookies`, `extra_filename`) plus `Browser.EXTRA_HEADERS` for everyone. Only "Download this
video" in the player is handed off (`OnlineDownloads.sendCurrentToExternal`); season batches and YouTube stay
in-app. DASH never, HLS only to 1DM; otherwise it falls back to the built-in downloader with a message. The manifest
`<queries>` lists just those packages (no all-apps visibility).

**Notification sound**: new original chime `res/raw/yumeiro_tsuki.ogg` (synthesised: soft E5 → B5 bell with a short
room tail, 1.5 s, peak −9 dBFS, about 5 dB quieter on average than the old one). Android can't change a channel's
sound, so channel ids moved to `*_v3` (`AppNotifier.Channel`, `UpdateNotifier`) and the v2 ids are deleted. The sound
URI is name-based (`android.resource://pkg/raw/yumeiro_tsuki`, kept by `res/raw/keep.xml`) so it survives
resource-id changes. Users who changed a channel's sound or importance get the defaults again once.

**Captcha / site page (black screen + ads after every tap)**: cause was `setSupportMultipleWindows(false)`: ad
`window.open` / `target=_blank` replaced the page itself. Now `SafeWebView.harden` turns multiple windows on (the
default `onCreateWindow` drops pop-ups), `SafeWebView.addAdShield` injects `AdShield.DOCUMENT_START_JS` (no
`window.open`, removes empty full-screen click-catcher overlays) where `DOCUMENT_START_SCRIPT` is supported, and
`BrowserCheckHost` routes navigations through `AdShield.decideNavigation` (same site, captcha / sign-in hosts and
server redirects pass; ad hosts, `intent:`/`market:` and cross-site jumps are held back), pop-ups through
`decidePopup` (only sign-in pages, opened in the same view), and sub-requests through `blockRequest`
(`AdFilter` list). A slim bar shows "Blocked N ads & pop-ups" with "Open anyway" for the last held-back page; a
loading cover replaces the black first paint. The headless check uses the same filters. `BrowserCheckPolicy`:
after the check page was shown for a host it isn't shown again automatically for 3 minutes; a single "wants another
quick check · Open" message is shown instead.

**Torrent**: proposal only, see `docs/proposals/TORRENT.md`.

**README**: player (landscape), Hues home, a Hue and the watch page added from real UI renders (artwork is original
sample art).

Not yet verified on a physical device. Tests: `SubtitleSitesTest`, `AdShieldTest`, `ExternalDownloaderTest`.


## 20. PR #17 CI fix: CodeQL gate (branch `feat/subtitles-downloaders-adshield`)

**Symptom.** Build, unit tests, lint, gitleaks, dependency review and both CodeQL analysis jobs passed, but the
`CodeQL` code-scanning check failed: "7 new alerts including 1 high severity" (alerts 50–56 on `refs/pull/17/merge`).

**Why it appeared now.** The CodeQL action is diff-informed on pull requests: it reports only alerts on changed lines.
PR #17 wrapped the captcha WebView in a `Box` (re-indenting it), added a pop-up probe WebView and two subtitle
clients, so old and new findings on those lines counted as new. Same `javaScriptEnabled = true` line passed in PR #16
because it wasn't in that diff.

| Alert | Where | Decision |
|---|---|---|
| `websettings-javascript-enabled` (high) | `BrowserCheckHost.kt` captcha / sign-in page | Needed (Cloudflare, Turnstile, logins). Reviewed suppression (rule + exact path) |
| `websettings-allow-content-access` x4 | `BrowserCheckHost.kt`: the page, the pop-up probe, the `webRef` state | **Fixed.** CodeQL can't see the settings set inside `SafeWebView.harden()`; set explicitly on both views, on a local variable (`val page = WebView(ctx); page.settings...`), and configured through `with(page) { }` instead of `apply { }`: the query treats `WebView(..).apply { }`'s return value as another WebView source and doesn't credit settings made inside it (two fix pushes left 2, then 4, medium alerts for exactly that reason; the probe and `HeadlessCapture`, which never used `apply`, were clean). The probe (new in #17) had default settings; now JavaScript, file and content access are off and it is destroyed after 5 s if the pop-up never navigates (it leaked before). The `WebView?` Compose state is replaced by a loader lambda |
| `missing-certificate-pinning` x2 | `SubDL.kt`, `SubSource.kt` (+ `OpenSubtitles.kt`, touched here) | Reviewed suppression, same reasoning as Kitsu/Trakt: third-party CDNs rotate certs; HTTPS-only already enforced |

**Real bug found during the audit (not reported by CodeQL).** OkHttp strips only `Authorization` on a cross-host
redirect, so the user's OpenSubtitles `Api-Key` and SubSource `X-API-Key` would follow a redirect to any host,
including plain HTTP. `SubtitleNet.keyScoped(client, header, domain)` adds a network interceptor that removes the key
on any hop that isn't HTTPS on the site's domain or a subdomain (OpenSubtitles VIP stays working).
Tests: `SubtitleKeyScopeTest` (host rules, suffix tricks, HTTPS downgrade, cross-host redirect end-to-end with two
MockWebServers, same-site redirect keeps the key).

**Same pattern, everywhere (pre-emptive).** The other four WebViews (`BrowserCheck.kt` headless solver,
`HeadlessCapture.kt`, `YouTubeAccountScreen.kt`, `PageStreamFinderDialog.kt`) now also set file / content access off on a local in the creating function (configured via `with(page) { }`, not `apply { }`), so the next PR that touches any of them doesn't trip the same gate. `harden()` still sets
them too (defence in depth). New WebViews: copy this pattern.

**SubDL v2 (deliberately not migrated yet).** SubDL's v2 API wants the key in `Authorization: Bearer` / `X-API-Key`
instead of `?api_key=`, but v2 downloads count against 50/day on free keys (today's `dl.subdl.com` ZIPs are anonymous)
and the v2 subtitle-search response isn't documented. Migrate only with a real key to test against; until then the key
stays in the query string to `api.subdl.com` only (HTTPS-only, never logged, a redirect doesn't carry the query).

Docs: `docs/security/AUDIT.md` (suppressions table, redirect note, diff-informed note), `.github/codeql/suppressions.jq`,
`docs/INSPIRATION.md` (competitor study). Tip: after a big refactor run the CodeQL workflow by hand on `main`
(`workflow_dispatch`) to get a full, non-diff scan. Not tested on a physical device (logic is unit-tested).

## 21. Trakt without a client secret (branch `fix/trakt-secretless`)

**Why.** Since 2026-10-01 Trakt issues no client secret to new API apps ("Not issued. This app signs users in with
PKCE"); `client_secret` is optional and deprecated on every `/oauth` body (trakt/trakt-api#974). Yumeiro required
a secret (`Trakt.configured`) and always sent one, so a new app could never sign in.

**What changed (`tracking/Trakt.kt`).**
- Only the Client ID is required. `client_secret` is sent only when the app has one (pre-October apps).
- A user's own Client ID is never paired with the built-in secret (it was before: mismatched pair, sign-in failed).
- Token refresh: access tokens last 7 days and refresh tokens are single-use. `validToken()` refreshes within a day
  of expiry (serialized, new pair stored at once); a `401` on `/sync/history` forces one refresh and retries; a
  rejected refresh signs out so the user signs in again. Before this, Trakt sync silently stopped after 7 days.
- Device-code sign-in is unchanged (PKCE applies only to the authorization-code flow).
- Settings dialog and setup guide: secret marked optional; redirect URI `urn:ietf:wg:oauth:2.0:oob` (Trakt may warn,
  it still saves; it's sent on refresh, so it must match the app).
- Release: only the `TRAKT_CLIENT_ID` repo secret is needed. `TRAKT_CLIENT_SECRET` stays optional in `release.yml`.

Tests: `TraktAuthTest` (secret omitted/sent, refresh body, refresh window). Not tested against a live Trakt account.

### 21a. CI audit and the base64url client-ID bug (same branch)

**CI audit (all checks of PR #18 green; earlier reds explained).**
- PR #17's red `CodeQL` check: code scanning compares a PR with the latest analysis of `main`. `codeql.yml` ran only on
  pull requests and once a month, so the `main` baseline was ~10 merges old and alerts already on `main` (or fixed
  there) were counted as "new" whenever a PR touched those lines. The action also warned "Please specify an on.push
  hook". Fix: `push` on `main` with a paths filter (one run per merged code change, docs-only merges skipped).
- PR #14 had one red gitleaks run on an earlier push; its final head was green on every check before it merged, so
  nothing is outstanding there.
- Lint is still `continue-on-error`, so lint findings never turn a PR red; read the report artifact.

**Real bug: client IDs were mangled at build time.** `app/build.gradle.kts` filtered every OAuth client id with
`isLetterOrDigit()`. Trakt's new IDs (and LRCLIB app IDs) are base64url and contain `-` / `_`; stripping them gave an
ID Trakt rejects with `401` (checked live: the real ID gets a device code, the stripped one 401s). `buildConfigSafe()`
keeps letters, digits, `-`, `_`, `.` (JWT-style tokens) and still drops quotes, backslashes and spaces, so nothing can
break out of the generated Java string literal.

**Secrets.** Repo secrets now include `TRAKT_CLIENT_ID` (secret-less app), plus `SIMKL_CLIENT_ID`, `LASTFM_API_KEY`,
`LASTFM_SHARED_SECRET`, `LRCLIB_APP_ID`, `OPENSUBTITLES_API_KEY`, `TMDB_API_KEY` used by the next update (§22).
The repo is public, so anything embedded in an APK is extractable; these are free-tier app identifiers, all
user-overridable in Settings, and none grants access to a user's account on its own.

## 22. Simkl, Last.fm scrobbling, LRCLIB lyrics, keyless OpenSubtitles (branch `feat/lyrics-scrobble-simkl`)

Stacked on `fix/trakt-secretless` (PR #18): it needs `buildConfigSafe()` for the base64url LRCLIB id. Merge #18 first.
Competitor and Sozo study: `docs/INSPIRATION.md` (last section). Next steps: `docs/ROADMAP.md` §0.8 and §2.5.

**Build-time keys** (`app/build.gradle.kts`, `release.yml`, `.env.example`; all optional, blank = feature asks for the
user's own key or hides): `SIMKL_CLIENT_ID`, `LASTFM_API_KEY`, `LASTFM_SHARED_SECRET`, `LRCLIB_APP_ID`,
`OPENSUBTITLES_API_KEY`, `TMDB_API_KEY` (read token, reserved for the trailer feed). Repo secrets are set.

**Simkl (`tracking/Simkl.kt`).** The built-in app is an AUTH V2 app: the old `/oauth/pin` flow is rejected, so sign-in
uses the OAuth 2 device flow (`POST /oauth2/device`, scope `media:read media:write`, poll `/oauth2/token` without a
secret). Every call carries `client_id`, `app-name`, `app-version` and a User-Agent (Simkl requires them). Access
tokens last 7 days, refresh tokens 180 days (not rotated); `validToken()` refreshes a day early, a `401` forces one
refresh. A read-only scope grant is detected and reported. `AnimeSync` posts finished episodes / movies to
`/sync/history` with MAL / AniList / IMDb / TMDB ids; each tracker (Trakt, Simkl) is isolated so one failing
doesn't stop the other. UI: "Movies & shows" card, device-code dialog, optional own Client ID (`simkl_client`).

**Last.fm (`scrobble/`).** `LastFm.kt`: desktop auth (signed `auth.getToken` → user approves on last.fm →
poll `auth.getSession`, error 14 = not yet), `track.updateNowPlaying`, `track.scrobble` (batches of 50). MD5 `api_sig`
lives alone in `LastFmSignature.kt` (Last.fm's format, CodeQL-suppressed by exact path, see AUDIT.md).
`ScrobbleRules.kt`: `TrackNames` (cleans "Artist - Topic", VEVO, "(Official Video)", "[MV]", "| 4K", "Artist - "
prefixes) and the pure `ScrobbleTracker` (tracks > 30 s; scrobble at min(50 %, 4 min) of wall-clock listening, capped
2 s per tick so seeks don't count; restart = new listen; unknown artist = skipped). `Scrobbler.kt`: hooked in
`MusicPlayerManager.init`, offline queue in prefs (≤ 500, drops > 14 days old, Last.fm's limit), error 9 signs out,
8/11/16/29 retry later; nothing while incognito or switched off. Limitation: it runs while `MusicPlayerManager`
exists (the UI side); background-only sessions after the activity is gone aren't scrobbled yet (ROADMAP §0.8).

**LRCLIB (`lyrics/LrcLib.kt`).** Fourth lyrics step after imported / sidecar / embedded: `/api/get` with title, artist,
album, length, then `/api/search` with `pick()` (±2 s match first, synced over plain, > 10 s off rejected as another
version). Sends `User-Agent: Yumeiro v<ver> (repo URL)` and `Lrclib-App-Id`. Hits cached in `cacheDir/lyrics_online`,
misses remembered 7 days. Settings toggle "Find lyrics online" (Tracking → Music). Source label `LRCLIB`.

**OpenSubtitles.** Uses the built-in key when the user has none (user key wins; Settings shows "Your key" / "Built-in").
Query parameters are sent sorted and lowercased (the API 301-redirects otherwise, checked live). UA carries the version.

**Security.** `network_security_config.xml`: HTTPS-only for simkl.com, audioscrobbler.com, last.fm, lrclib.net.
Tokens / session keys sealed with `SecretBox` via `OnlineSettings.setSecret`, not in backups. CodeQL suppressions
(rule + exact path) for the MD5 signature file and certificate pinning on the three new clients, in
`.github/codeql/suppressions.jq` and `docs/security/AUDIT.md`.

Tests: `SimklTest`, `LastFmTest` (signature vectors incl. UTF-8), `ScrobbleTrackerTest`, `TrackNamesTest`, `LrcLibTest`,
`OpenSubtitlesParamsTest`. Live-checked during development: Simkl device code + pending poll, Last.fm key, LRCLIB app
id, OpenSubtitles key, TMDB token. Not tested on a phone or with real Simkl / Last.fm accounts.

## 23. Yumeiro Cloud: airing-time push nudges (branch `feat/cloud-alerts`)

Stacked on `feat/lyrics-scrobble-simkl` (PR #19). Owner asked for a server "smaller but more effective", free tier,
30k monthly users, and later: "only what is necessary, not a large system". So the server does one thing.

**What.** `server/`: one Cloudflare Worker (TypeScript, no framework) + D1 + one Queue + a 10-minute cron. Devices with
push on register `{endpoint, anilist ids}`. The cron asks AniList which episodes aired since the last run, finds
followers and enqueues a 1-byte push to their UnifiedPush endpoint 30 minutes later (sources lag the airing). The app
ignores push contents anyway: a push → `BackgroundWork.wake` → feed + new-episode checks. Full design, endpoints,
free-tier budget and owner setup steps: `docs/SERVER.md`. Privacy: `PRIVACY.md` "Push alerts".

**Server safety.** Endpoint SSRF guard (`validEndpoint`: https, default port, no IP literals / localhost / .local /
.internal, ≤ 1024 chars). Device token stored as SHA-256, compared in constant time. Follow ids validated, ≤ 300.
404/410 from a push server deletes the device; 45 silent days prunes it. No retries on the queue (a missed nudge is
harmless; ntfy-scheduled wake-ups still run). Deployed 2026-11-09, see §24. Checked locally with `wrangler dev`: register, follow update,
bad endpoint, wrong token, unregister, scheduled run against live AniList.

**App.** `notify/CloudAlerts.kt`: `sync()` from `DiscoveryWorker` and `PushWakeWorker` (new endpoint). No-op unless
endpoint / follows changed or 3 days passed. Follows = the new-episode check's set (`CloudAlertsPlan.watched`), AniList
ids resolved by title via `AnimeIds.aniListIdForTitle`, cached in prefs (0 = not found), ≤ 10 lookups per run.
Credentials `<id>.<token>` sealed via `OnlineSettings.setSecret("cloud_device")`; `PushWake.disable` /
`onUnregistered` call `CloudAlerts.forget`. Build field `CLOUD_HOST` (`cloudHost` / `YUMEIRO_CLOUD_HOST`, default
`api.yumeiro.click`; blank = off). HTTPS-only for `yumeiro.click`. CodeQL pinning suppression for
`notify/CloudAlerts.kt` (Cloudflare rotates edge certs), justified in AUDIT.md.

**CI.** `.github/workflows/server.yml`: `npm test` (node:test via tsx) + `tsc`, only on `server/**`; no secrets, no deploy.
Tests: `server/test/logic.test.ts` (6), `CloudAlertsPlanTest` (4).

## 24. Scope review and deployment (branch `feat/cloud-alerts`, 2026-11-09)

Owner's rules for the server: "we will build only a tiny server … only better streaming experience", 50k monthly users,
Cloudflare free plan only (the domain is the only paid thing), "if the server is break, it should work smoothly as it
is", and "use the server only for the most necessary functionality that a user actually need and depend".

**Decision.** The server does airing alerts only. Online watch-together rooms (Durable Object relay, end-to-end
encrypted, live-tested) and the cloud backup / device-link ideas were dropped from `main`'s path. The rooms work is kept,
unmerged, on branch `archive/online-rooms` (commit `688226d`) in case the community asks for it and funding exists.
Reasons with free-plan numbers: `docs/SERVER.md` "Not planned".

**Hardening for 50k users.**
- App back-off: a failed register waits 6 h → 12 h → 24 h → 48 h → 3 days (`CloudAlertsPlan.retryDelay`), 20 s call
  timeout. Before this, a broken server meant every push device retried every 2 h (up to ~180k requests/day, over the
  100k free limit, so one outage could keep itself going).
- Server: free rate-limit binding `NEW_DEVICES` (5/min per IP, only for registrations that create a row) so fake
  devices can't burn the 100k D1 writes/day. Refreshes with a valid token are not limited. Best-effort by design
  (per Cloudflare location).
- "When the server breaks" table in `docs/SERVER.md`.

**Deployed.** Worker `yumeiro-cloud` (version `fcbe5753`), custom domain `api.yumeiro.click`, cron `*/10`, Queue
`yumeiro-wakes` producer + consumer, D1 `yumeiro` (`beb118e1-ba0e-4b6b-84d8-d76b42037d7c`, tables `devices`,
`follows`, `state`). The earlier test deploy with the rooms Durable Object was deleted and redeployed clean (the
consumer had to be removed first: `wrangler queues consumer remove yumeiro-wakes yumeiro-cloud`). Live checks:
`/v1/health` → `{ok:true,v:1}`, `/v1/rooms` → 404, register → 200, burst → 429; test rows deleted (devices = 0).
The D1 id is not a secret. The API token is kept outside the repo; the owner should rotate it because it was pasted in
chat. Tests: server suite + `tsc`, app 389 unit tests pass.

### 24a. Server dependency alerts (branch `chore/server-deps`)

After `server/` reached `main`, Dependabot opened alerts for `undici` (several high) and `sharp` (high) and its two
security-update jobs failed: both are transitive (`wrangler` → `miniflare`), so they can't be bumped alone. Fixed by
updating `wrangler` to 4.159 and `@cloudflare/workers-types`; `npm audit` reports 0. These are dev/deploy tools only;
the deployed Worker bundle (10 KiB) never contained them, so production was never exposed. Wrangler now needs Node
≥ 22.12 (`engines` set; CI already uses 22). `dependabot.yml` gains a monthly grouped npm entry for `/server`.

## 25. Alerts never fired: AniList blocks Cloudflare Workers (branch `fix/alerts-airing-from-devices`)

**Found by** checking the live server (owner asked "how do I ensure my server is working"). `/v1/health` said ok but
`state` was empty. Cloudflare GraphQL analytics: 61 `scriptThrewException` with 1 subrequest each, every cron run since
deploy. `wrangler tail`: `anilist 403`. A throwaway probe Worker (deleted after) got `403 "You have been manually
blocked. Please come to the principal's office."` with plain, custom-UA, Referer and browser headers; the same
query from a normal machine returns 200. AniList blocks Cloudflare Workers egress deliberately; not worked around.

**Fix.** The server makes no outside calls now. Phones (not blocked) look up `nextAiringEpisode` for their follows
(`CloudAlerts.nextAiring`, `id_in`, 50 per request) and send `airing: [[id, at]]` on check-in. Server: shared table
`airing (media_id PK, at, rolls)`; `parseAiring` keeps followed ids and times in [now − 1 h, now + 15 d]; upsert
writes only changed rows. Cron selects rows with `at` in the window, wakes followers, rolls `at` +7 d (≤ 2 guesses,
then deletes), records `aired_to` in the same batch. Daily prune drops airing rows nobody follows. New per-device
check-in limit `DEVICE_SYNC` (3/min). `/v1/health` is now a real check: 200 only if D1 answers and the job ran within
30 min, else 503 (`v: 2`, `cron_s`). App fingerprint prefix `a2|` makes updated phones re-sync once at once.
Monitoring how-to: `docs/SERVER.md` "Is it working?". PRIVACY.md updated (airing times, shared, not linked to you).

**Deployed** 2026-11-10 (version `f1bb9926`) before merge; it accepts v1.3.0 check-ins (no `airing`, no alerts for
them until they update). Tests: server 7 + `tsc`, local `wrangler dev` end to end (register with airing, per-device
429, cron fires and rolls +7 d, unfollowed id rejected, health 503 → 200), app 390 unit tests.

## 26. Markets for Aniyomi, Mihon, Mangayomi and Stremio; yumeiro.click links (branch `fix/alerts-airing-from-devices`)

**Asked:** "users manually need to add extension … there should be their individual extension market … auto search
by github star or rating … and also the manual setups." Also: README and in-app links should use `yumeiro.click`.

**Builder.** `.github/scripts/build_source_market.py` runs in the `Extension market` workflow after the CloudStream
step (best effort, `timeout 25m … || warning`; a failure keeps the last `sources.json`). Per format: GitHub repo
search (topics + keywords, by stars, ≤160 candidates), README add-repo links (`aniyomi:// tachiyomi:// mihon://
mangayomi://`, raw index URLs), usual publish paths (`repo` / default branch). Every index is downloaded and parsed;
kind comes from the contents (`.animeextension.` packages = Aniyomi), placeholder entries ("Outdated App", "Update to
…") are dropped, repos not pushed for `STALE_DAYS` are left out, forks/mirrors with an identical index are merged.
Score = 25·log10(stars+1) + 30·e^(−days/180) + 6·log2(count+1). 18+ entries are counted (`nsfw`) so the app can
hide them. Stremio: Stremio's featured collection + stremio-addons.net community catalog, each manifest fetched;
non-answering ones are left out, `adult` flagged. Only links are listed; nothing is mirrored. `blocked` and
`sourceSeeds` come from `.github/extensions/sources.json`. Local test: `USE_GH_CLI=1 python3 … /tmp/out` (uses the
gh CLI for GitHub calls).

**App.** `sources/SourceMarket.kt` reads `${ExtensionMarket.BASE}/sources.json` through
`TrustedSources.fetchCatalogText` (official branch only), caches it in `files/extensions/sources.json`, refreshes
every 6 h, works offline from the cache. Pure `parse` / `filterRepos` / `filterAddons` / `ago` are unit-tested
(`SourceMarketTest`). UI `ui/screens/sources/SourceMarketSection.kt`: a "Discover" block in the Aniyomi, Mihon,
JavaScript and Stremio tabs — search (name, owner, language, sample source names), sort chips (Top / Recently
updated / Most extensions; Stremio: Featured / A–Z + Movie/Series/Anime/TV), rows with stars, last update, count,
languages and sample names, one-tap Add through the existing `addRepo` / `StremioAddons.add` paths, "Added" when
present. Adult-only repos and adult add-ons are hidden unless the 18+ setting is on. Expanded by default until the
user has a repository. The manual paste dialog is unchanged ("Your repositories" → Add). Setup guides updated.

**Known gap (closed in §27).** Keiyoushi (the main Mihon repo) now publishes `index.pb` / `index.json` v2 with lib 1.6
and leaves only placeholders in `index.min.json`, so it wasn't listed. §27 adds that format.

**Links.** Every `yumeiro-phi.vercel.app` link (README, docs, fastlane description, Settings → website, Source
status) now points at `https://yumeiro.click` (308 → www, served by Vercel; `/download` and `/status` work).

## 27. Mihon extensions-lib 1.6 and the v2 store index — Keiyoushi works (branch `feat/mihon-lib-1-6`)

**Why.** Keiyoushi moved every extension (1554) to extensions-lib 1.6 (`keiyoushi.source.KeiSource`) and a new
store index: `repo.json` → `index_v2` (`index.pb`, protobuf; `index.json` is the same in JSON). `index.min.json`
now only holds "Outdated App" stubs. Yumeiro read neither, so Keiyoushi was unreachable.

**Store index** (`sources/tachiyomi/TachiyomiIndex.kt`, used by `TachiyomiExtensions.fetchRepoIndex`).
One request to `<base>/repo.json` decides: a v2 store points at `index_v2`, which is decoded (gzip or not,
protobuf or JSON — sniffed, not trusted from the URL; `extensionListUrl` indexes follow the second file).
Otherwise `index.min.json` is read as before; if it only has update stubs, `index.pb` then `index.json` next
to it are tried. Protobuf schema = mihonapp/extensions-lib `index.proto` (kotlinx-serialization-protobuf).
v2 entries carry absolute APK/icon URLs (GitHub releases), so `IndexEntry` gained `apkUrl`/`iconUrl`/`lib`.
Content warning: only NSFW waits behind the 18+ switch; SAFE and MIXED are listed like before.
`normalizeRepo` also accepts `index.pb`, `repo.json`, `github.com/<o>/<r>/raw|blob/<branch>/…` and
URL-encoded `mihon://add-repo?url=` links.

**Auto-trust by repo key.** The store declares its signing-certificate SHA-256 (`repo.json`
`meta.signingKeyFingerprint` / v2 `signingKey`). An APK installed from that store and signed with exactly that
certificate is trusted at once (like Mihon); anything else still needs the manual trust tap. Keiyoushi:
`9add655a…5a75d4da2`, checked with `apksigner` on the real MangaDex APK.

**Host ABI (extensions-lib 1.6).** Checked against the class files of all 1554 Keiyoushi jars — every external
class, method and field they reference (1560 refs) is asserted to exist with the same JVM signature by
`KeiyoushiLinkageTest` (fixture `app/src/test/resources/keiyoushi-lib16-refs.json`). Changes:
- `source/MangaSource.kt` → `Source.kt`: the interface is `eu.kanade.tachiyomi.source.Source` (the old name made
  every extension that implements `Source`/`SourceFactory` fail to link). It now declares `supportsLatest`,
  `getFilterList`, the suspend catalogue calls and `getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)`
  (default bridges older extensions to getMangaDetails/getChapterList). New `SMangaUpdate`.
- `CatalogueSource`: related-manga members (`supportsRelatedMangas`, `fetchRelatedMangaList`, …).
- `SManga.memo` / `SChapter.memo` (`JsonObject`), copied in `copy()` / `copyFrom()`.
- `HttpSource.getHomeUrl()` (default `baseUrl`; used for the provider's site link). `headers` stays `by lazy`
  — KeiSource replaces the `headers$delegate` field reflectively.
- `ChallengeInterceptor` → `CloudflareInterceptor`: KeiSource refuses to start unless the default client has
  interceptors named `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, `CloudflareInterceptor` and no
  Brotli / IgnoreGzip network interceptor. Don't rename them.
- New deps: `okhttp-brotli`, `okhttp-zstd` (KeiSource adds `CompressionInterceptor(Gzip, Deflate, Brotli, Zstd)`),
  `kotlinx-serialization-protobuf`. `app.cash.quickjs.QuickJs` (25 extensions) is a small layer over the QuickJS
  Yumeiro already ships (`app/src/main/java/app/cash/quickjs/QuickJs.kt`) — two QuickJS libraries can't coexist
  (same `libquickjs.so`). R8 keeps added for zstd / brotli / quickjs / webkit boundary.

**Bridge** (`MihonMangaProvider`). Details and chapters use `getMangaUpdate`: details asks for both and keeps the
chapters for 60 s, so opening a title costs one source call. Calls per manga are serialised with a `Mutex`
(KeiSource throws on concurrent calls for the same manga). Memo data is persisted per source
(`files/tachiyomi/memo/<id>.json`, LRU 5000, `MemoStore`) and handed back on details/chapters/pages — some
multisrc themes (e.g. Iken: `chapter.memo["seriesSlug"]!!`) need it. Empty memos are never written.
Loader: `MAX_MANGA_LIB` 1.6; reads `tachiyomix.name`, `tachiyomix.extensionLib` (stored as a float) and
`tachiyomix.contentWarning` (0 safe, 1 mixed, 2 NSFW).

**Market.** `build_source_market.py` reads v2 `index.json` (and probes `<repo>/repo/index.json`), so Keiyoushi is
listed in Discover again; the stored URL is accepted by the app's add-repo path.

**Captcha page top bar.** The "Verify you're human" dialog (and the page stream finder) is its own window, which
kept platform bar defaults — a white status bar over the dark header. `FullScreenDialogBars()` (in
`BrowserCheckHost.kt`) makes that window draw edge-to-edge with transparent bars, the app background and
icon colours from the current theme.

**Known gap.** `ca.mpreg.imagedecoder` (4 extensions: webtoonlib, randomscan, toonlook, procomic) isn't bundled:
it adds ~12 MB of native code for 4 sources. Those load but fail on images that need it.

**Tests.** `TachiyomiIndexTest` (v1, stubs, repo.json, v2 JSON/gzip/proto, the real Keiyoushi `index.pb`
fixture, normalizeRepo, lib versions, MemoStore) and `KeiyoushiLinkageTest`.

