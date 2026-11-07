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

Kept out on purpose (the product rule is "no login"): subscriptions feed, account playlists, likes
sync, age-restricted / members-only videos, YouTube Music library/uploads. If ever added, do it
for both audio and video via an optional cookie import screen, never mandatory.

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
