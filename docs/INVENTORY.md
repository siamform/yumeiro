# Yumeiro feature inventory (Round 4 audit, checked against the code in R16)

Built from the code on `r15-stability` (481 Kotlin files under `app/src/main/java`), the
manifest, the navigation routes (`ui/navigation/Screen.kt`) and the Settings catalog
(`ui/screens/settings/SettingsCatalog.kt`, 22 categories in 5 groups). Every item has an ID; the
"Covered by" column is filled as each group's audit is reported, and anything not covered at the
end is listed in §Not covered with the reason.

R16 check (2026-11-15): every one of the 368 Kotlin files under `app/src/main/java` was matched to
an item by file or package name. Files with no item became Y8, L10, D8 and X13–X16, or were added to
the code list of P1 and M2.

Status values: `open` (not audited yet), `audited` (tested, problems listed), `done` (fixed and
verified on a device, evidence linked), `not covered` (reason given).

## G1 Home and navigation
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| H1 | Home feed (sections, moods, online rows) | ui/screens/home/HomeScreen, HomeSections, HomeMoods, HomeOnline | done | `FeedPositionDeviceTest` 43683520404 / 43687398312; G1 report |
| H2 | "Jump back in" / continue row | HomeSections, player/LastPlayed, data/PlayCounts | done | `h2` 43693532355, 43696246671; G1 report |
| H3 | Home shortcuts and nav-bar shortcuts (up to two) | ui/navigation/NavPrefs, ui/components/MTBottomBar | done | `h3` 43693532355, 43696246671 |
| H4 | Bottom navigation + tab pager + swipe between tabs | MTPlayerApp, ui/components/TabSwipe, PageSwap | done | `h1_h4`, `BackNavigationDeviceTest` 43690918679, 43696246671 |
| H5 | TV navigation rail and D-pad focus | ui/tv/TvMode, TvNavRail | done | `h5` 43696246671 (rail focus fix a53effb); real TV in script T |
| H6 | Back behaviour (button, predictive gesture, nested pages, player) | MTPlayerApp, SubNav | done | `BackNavigationDeviceTest` 43690918679 6/6, 43693532355 4/4; gesture on hardware in script P |
| H7 | Deep links and incoming intents (VIEW, SEND, browsable links, shared media) | MainActivity, stream/IncomingMedia | done | `h7` VIEW, `h7b` SEND text 43694972929, 43696246671. SEND of video files is not supported (enhancement) |
| H8 | State restoration after process death | MainViewModel, ScrollMemory, NavPrefs | audited | `h8` recreate only; process death not tested on device (instrumentation dies with the process) |
| H9 | Splash, first-run welcome, explore tour, onboarding gate | ui/screens/splash, extras/SetupGuide, ExploreTour, online/OnboardingGate | done | `FirstRun.toTabs` on every device run (welcome + tour gate) |
| H10 | Top bar, global search entry, search suggestions | ui/components/MTTopBar, YuSearch, SearchSuggest, TabSearch | done | `h10` 43689481257 6/6, 43696246671 |
| H11 | Mini player bar | ui/components/MiniPlayerBar | open | Moved to G6 Music (needs a playing track) |
| H12 | Home-screen widget "Continue" | widget/ContinueWidget | audited | `h12` provider present; placing on a launcher is manual (script P) |
| H13 | App pages (inbox, support, permission center, reliability guide, reminders) | ui/screens/extras/* | done | `h13` inbox, all HomeNavigation runs |

## G2 Extensions and market
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| E1 | Extension market browse and search | online/ExtensionMarket, ui/screens/online/ExtensionMarketScreen | done | `ExtensionsDeviceTest` 43704211410, 43705847970; G2 report |
| E2 | Install (with review / scanner / guard) | ExtensionManager, ExtensionReview, ExtensionScanner, ExtensionGuard | done | 43704211410, 43705847970; G2 report |
| E3 | Update (auto and manual), repair missing files after restore | ExtensionManager.repairMissing | done | 43704211410, 43705847970; G2 report |
| E4 | Remove, enable/disable, per-extension settings | ExtensionsScreen | done | 43704211410, 43705847970 |
| E5 | Pin favourites | online/PinnedExtensions | done | `PinnedExtensionsDeviceTest` 43671202859, 43705847970 |
| E6 | Select all / individual selection, bulk actions | ExtensionsScreen | done | bulk selection 5b1263e, Back fix bfde23d; 43704211410, 43705847970 |
| E7 | Extension search | ExtensionsScreen | done | 43704211410, 43705847970 |
| E8 | Broken extension handling (crash, bad JSON, timeouts) | ExtensionGuard, OnlineErrors, SafeRequests | done | cause-chain errors 9be5afb; 43704211410 |
| E9 | Starter extensions after onboarding | online/StarterExtensions | done | retry fix 97bb255; `e9` 43705847970 |
| E10 | Repositories / Universal add (CloudStream, Aniyomi, Mihon, Stremio, JS) | sources/UniversalAdd, SourceMarket, ui/screens/sources/* | audited | link path on device (E8); Aniyomi, Mihon, Stremio, JS unit only (G2 report) |
| E11 | Sources hub and source health screen | ui/screens/sources/SourcesHubScreen, SourceHealthScreen | done | `e11` 43704211410, 43705847970 |
| E12 | Provider overrides, adult-content filter | online/ProviderOverrides, AdultContent | audited | override on device (`e12`); adult search filter code only (needs a fake NSFW provider) |

## G3 Player
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| P1 | Local video playback | ui/screens/video/VideoPlayerModal, VideoPlayerDialogs, OnlineSubtitleDialog, ui/screens/online/OnlinePlayerUi, ui/screens/player/SleepTimerSheet, service/VideoPlaybackService | done | `LocalPlayerDeviceTest` 43708220122; G3 report |
| P2 | Online (movies, series, anime) playback session | online/OnlineSession, OnlinePlayback | done | `OnlinePlaybackDeviceTest`, `HeaderStreamDeviceTest`; G3 report |
| P3 | YouTube playback | youtube/YouTubePlayback, YouTubeStreams, NewPipeDownloader | done | `PlayerSwitchDeviceTest c_` 43701056608; real phone script Y |
| P4 | Drama playback | sources/drama/* | done | `DramaPlaybackDeviceTest` 43701056608 |
| P5 | Quality list and switching (parity with downloader, generated levels) | player/QualityEngine, TrackGroups | done | `QualityListDeviceTest`, `PlayerSwitchDeviceTest` 43701056608 |
| P6 | Audio tracks and languages | online/AudioLanguages, player/TrackPicks | done | `LocalPlayerDeviceTest a` 43708220122 |
| P7 | Subtitles (embedded, online search, style) | subtitles/*, online/subtitles/*, SubtitleStyleDialog | done | `LocalPlayerDeviceTest a` 43708220122 (embedded); online search in G11 |
| P8 | Speed, zoom, gestures, volume/brightness | VideoPlayerGestures, VideoZoom, VolumeAccumulator | audited | speed on device 43708220122; gestures unit only |
| P9 | Source rotation, dead manual pick, "Load more" sources | OnlineSession failover, SourceSelector | done | `OnlinePlaybackDeviceTest d_` 43701056608 |
| P10 | PiP, fullscreen, orientation | player/PipController, PlayerSurfaceRules | done | PiP and lock `LocalPlayerDeviceTest a,c` 43708220122; floating `SurfaceModeDeviceTest` |
| P11 | Resume position | OnlineHistory, LastPlayed | done | `LocalPlayerDeviceTest b` 43712469920 ×2 both |
| P12 | Info overlay, HUD, controls, time labels | PlayerInfoOverlay, VideoPlayerHUD, VideoPlayerControls | done | `LocalPlayerDeviceTest a` 43708220122 |
| P13 | Player settings | PlayerSettings, ui/screens/settings | audited | unit only; settings screen in G9 |
| P14 | Recovery (black frame, stall, starvation, decoder fallback) | BlackFrameWatchdog, PlaybackHealthMonitor, RecoveryRouter, StreamRecovery, StarvationSafeLoadControl | audited | unit only (needs fault injection) |
| P15 | Anime extras: AniSkip intro skip, AniList | online/anime/* | audited | unit only; cache fix in G3 |
| P16 | Anime4K shader effect | player/Anime4kEffect | audited | unit only |
| P17 | Casting (Google Cast) | cast/CastSupport | audited | Cast start on device (`e`, fix b5d2d5a); casting needs a real Chromecast (script P/T) |
| P18 | Sleep timer, screen wake | player/SleepTimer, ScreenWake | done | screen wake `LocalPlayerDeviceTest a`; sleep timer is music-only → G6 |
| P19 | Heat guard | player/HeatGuard | done | `LocalPlayerDeviceTest d` 43708220122 |

## G4 Yumix
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| Y1 | Yumix feed and sections | ui/screens/online/YumixFeed, OnlineFeed, OnlineHomeScreen, OnlineComponents, OnlineGetStarted, ExtensionBanner | done (offline refresh: see GROUP-4) | `YumixRefreshTest`, `YumixGoneTest`, `YumixFeedTest`; `FeedPositionDeviceTest#yumix` 43715245923; `YumixDeviceTest#offlineRefreshKeepsTheFeed`; milestone 43733844682 Yumix 2/2 on both (api33-lowram, api36) |
| Y2 | Yumix search | OnlineSearchScreen, online/SearchRanker | unit only | `YumixFilterSearchTest`, `SearchRankerTest`, `SearchSuggestTest`; code read |
| Y3 | Extension bar hide/show on scroll | ui/components/HideOnScroll | code read, no bug | owner feel test OMT-9 |
| Y4 | Drama as default, built-in drama card | DramaBuiltinCard | done | `YumeiroDramaTest`, `PinnedExtensionsTest`; every Yumix device run opens on drama; YX-11 dead video host → studio hidden, mirror used (`DramaHealthTest`); Drama device 43732060154 api33, 43733844682 api36, 43738288853 both pass; onshort.net timeouts are upstream |
| Y5 | "Show videos from" filter | YumixFilter | done | `YumixFilterSearchTest`; `PinnedExtensionsDeviceTest` 43705847970 |
| Y6 | Hero carousel, picks, row pages | OnlineHomeHero, OnlineHomePicks, OnlineRowScreen | done | `YumixDeviceTest#seeAllKeepsPagingWhileSearching` 43716649966, 43717950021 |
| Y7 | Detail page (versions, episodes, stills, bookmarks) | OnlineDetailScreen, EpisodeStills, OnlineBookmarks | unit only + code read | `OnlineExtrasTest`, `EpisodeCarouselTest`; detail opened in `DramaPlaybackDeviceTest` |
| Y8 | (R16) YouTube in Videos: feed, search, channel pages, Shorts, live, discovery | ui/screens/youtube/YouTubeScreens, YtChannelPage, youtube/YtDiscovery, YtWatch | unit only | `YtDiscoveryTest`, `YtHomeOrderTest`, `YouTubePickVideoTest`; feed signed in: OMT-1 |

## G5 Library and personal lists
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| L1 | Library screen, shortcuts | ui/screens/library/* | done (G5 fixes, unit only) | LB-7; `GROUP-5-LIBRARY.md` |
| L2 | Episodes / new-episode tracking | online/NewEpisodesWorker | done (unit only) | LB-4 `OnlineExtrasTest#aShortAnswerNeverLooksLikeNewEpisodes` |
| L3 | My list, favourites, saved | OnlineBookmarks, LibraryScreen | done (unit only) | LB-1 `SafeJsonFileTest` |
| L4 | History (online, YouTube) | OnlineHistory, youtube/YtHistory | done (unit only) | LB-1, LB-5 |
| L5 | Downloads view inside library | VideoLibraryScreen | done (unit only) | LB-1, LB-7 |
| L6 | Liked songs, playlists | MediaRepository | done (unit only) | LB-3 `LibraryRound2Test#oneBadPlaylistKeepsTheOthers` |
| L7 | Play time / listening time | player/ListeningTime, data/PlayCounts | done | LB-6; `LibraryDeviceTest#a` 43738288853 pass on api33-lowram and api36 |
| L8 | Reading library (manga) | ReadingLibrary route, MangaLibrary | done (unit only) | LB-1, LB-2 |
| L9 | Only real data, no placeholder or fake items | all of the above | code read, no bug | no sample items; demo playlists removed unless filled (`LibraryRound2Test`) |
| L10 | (R16) Device videos tab: folders, sorting, thumbnails | ui/screens/video/VideoScreen, ui/image/VideoThumbnailFetcher, data/model/MediaModels | done (unit only) | LB-7 sort remembered, sorted off main; thumbnails already on IO |

## G6 Music
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| M1 | On-device library (scan, albums, artists) | data/repository/MediaRepository | open | |
| M2 | Online mode (YouTube Music), automatic online/offline switch | youtube/music/*, ui/screens/music/MusicScreen, ModeSwitcher, ui/screens/youtube/YtMusicBrowse, youtube/YouTubeAudioDataSource | open | |
| M3 | Infinite online feed | youtube/music/YmEndless | open | |
| M4 | Lyrics (embedded, LRCLIB) | lyrics/* | open | |
| M5 | Now playing, queue, media session/notification | ui/screens/player/NowPlayingScreen, service/PlaybackService, MusicPlayerManager | open | |
| M6 | Equalizer | player/equalizer, ui/screens/equalizer | open | |
| M7 | Last.fm scrobbling | scrobble/* | open | |
| M8 | Song downloads | online/download/SongDownloads | open | |
| M9 | Music mix | ui/screens/music/MusicMix | open | |

## G7 Manga and live TV
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| T1 | Manga search, filter, source selection | ui/screens/manga/MangaScreens, sources/manga | code read; MG-1 fixed (unit/compile) | `TachiyomiIndexTest`, `SourceMarketTest`; no manga source on CI |
| T2 | Manga title page and reader | MangaTitle, MangaReader routes | code read; MG-1 fixed (compile) | owner reading test OMT-11 |
| T3 | Live TV hub, playlists, playlist market | ui/screens/stream/*, stream/PlaylistMarket, PlaylistStore | open | |
| T4 | M3U parsing, stream resolving, sniffing, page stream finder | stream/M3uParser, StreamResolver, StreamSniffer, PageStreamFinderDialog | LT-3 fixed (unit); code read | `StreamPlaylistTest`, `StreamDetectionTest`, `RequestHeadersTest` |
| T5 | HLS ad filter for streams | stream/HlsAdFilter, AdFilterDataSource | unit only, no bug found | `HlsAdFilterTest` |

## G8 Downloads
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| D1 | Download manager (queue, worker, groups, naming) | online/download/* | open | |
| D2 | Forced (IDM-style) downloads, segmented HLS/DASH, mux | ForceGrab, HlsDownloader, MediaMux | open | |
| D3 | Torrent / magnet | eu/kanade/tachiyomi/torrentutils, OnlineDownloads | open | |
| D4 | Pause, resume, failure, retry, cleanup worker | DownloadWorker, DownloadCleanupWorker | open | |
| D5 | External downloaders (ADM, IDM) | online/download/ExternalDownloader | open | |
| D6 | YouTube downloads and options | YtDownloads, YtDownloadOptions | open | |
| D7 | "View downloads" toast → PiP | DownloadPrompt | open | |
| D8 | (R16) Downloads screen: list, free space, actions | ui/screens/downloads/DownloadsScreen, online/DownloadSheets | open | |

## G9 Settings
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| S1–S22 | The 22 categories: Look & feel; Navigation bar; Player; Audio quality & sound; Online videos; Downloads; Library & storage; Sources; Source status; Tracking; YouTube account; Watch together; Notifications; Background & battery; Network; Privacy & security; Backup & restore; App updates; Setup guide; User guide; Crash reports; About & developer | ui/screens/settings/* | open | |
| S23 | Settings search | SettingsCatalog keywords | open | |
| S24 | Every reset action | backup/ResetCenter | open | |
| S25 | Backup, restore, auto backup worker, restart | backup/* | open | |
| S26 | Theme, backdrop custom image (accepted types) | ui/theme/*, BackdropSettings | open | |
| S27 | Each toggle's real effect (~42 switches) | settings cards | open | |

## G10 Alerts, toasts, dialogs and errors
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| A1 | App messages / toasts | ui/components/AppMessages (MediaComponents) | open | |
| A2 | Dialogs (Tsuki dialog, update, review, permission) | TsukiDialog, UpdateDialog, ExtensionReviewHost | open | |
| A3 | Error views with recovery actions | ErrorHelp, StateViews, OfflineNotice | open | |
| A4 | No network, slow network, VPN, low storage | network/Connectivity, OnlineErrors | open | |
| A5 | Browser check / Cloudflare challenge host | online/web/BrowserCheck, BrowserCheckHost | open | |

## G11 Integrations
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| I1 | Trakt | tracking/Trakt | open | |
| I2 | Simkl | tracking/Simkl | open | |
| I3 | Kitsu, AniList | tracking/Kitsu, online/anime/AniList | open | |
| I4 | TMDB (metadata) | online/SourceFacts / EpisodeStills | open | |
| I5 | OpenSubtitles, SubDL, SubSource | online/subtitles/* | open | |
| I6 | LRCLIB | lyrics/LrcLib | open | |
| I7 | Last.fm | scrobble/LastFm | open | |
| I8 | Firebase (FCM push, cloud alerts) | notify/FcmPush, YumeiroMessagingService, CloudAlerts | open | |
| I9 | Cloudflare (worker / feed endpoints) | notify/AppFeed, update/UpdateConfig | open | |
| I10 | YouTube account (cookie paste, auth hash) | youtube/YtAccount, YtCookiePaste, YtAuthHash | open | |
| I11 | GitHub releases (self-update) | update/GitHubReleaseClient | open | |

## G12 Cross-cutting systems
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| X1 | Architecture and layering | whole app, docs/SYSTEM-DESIGN.md | open | |
| X2 | Memory / OOM, low-RAM device class | MemoryWatch, DeviceClass, ImageCacheSize | open | |
| X3 | Main-thread stalls / ANR, StrictMode | diagnostics/DevStrictMode, YuStall watchdog, text/RegexWarmup, RegexCache, startup/PrefsWarmup | open | |
| X4 | Battery and heat | HeatGuard, NotifyPacer | open | |
| X5 | Notifications while closed (FCM, UnifiedPush, alarms, workers, boot) | notify/*, PushWakeService, NotifyAlarmReceiver | open | |
| X6 | Crash logs and export | diagnostics/CrashLog, CrashExport | open | |
| X7 | Security: secrets in APK, storage encryption, network, integrity | security/*, IntegrityGuard, SecretBox | open | |
| X8 | Accessibility (TalkBack, contrast, touch targets, font scale) | all UI | open | |
| X9 | TV and low-end behaviour | ui/tv, DeviceClass | open | |
| X10 | Build, CI, release, signing | .github/workflows, gradle | open | |
| X11 | Network stack: smart DNS, DoH, auto-bypass, settings | network/* | open | |
| X12 | Caching: data cache, image cache, online codec | cache/* | open | |
| X13 | (R16) Android 7–16 compatibility (minSdk 24), OS version guards | OsCompat, MTApplication | open | |
| X14 | (R16) Design system and shared components | ui/components/YumeKit, Motion, Pills, Artwork, YuEditorial, Kana, YumixLogo | open | |
| X15 | (R16) Shared live-feed component (append-only cards) | ui/feed/LiveFeed, FeedCard, OnlineHuePage | open | |
| X16 | (R16) Cloud Worker (alerts, FCM fan-out, market index) and its abuse guards | server/src/index.ts, fcm.ts, logic.ts | open | |

## New groups (not in the prompt's list)
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| N1 | Watch Together | watch/WatchTogether, ui/screens/watch/* | open | |
| N2 | Web sites tab, saved sites, site logins, safe WebView, ad shield, headless capture | ui/screens/sites, online/web/* | open | |
| N3 | Self-update (check, download, install, package replaced) | update/* | open | |
| N4 | Home servers: Jellyfin, Plex, SMB, WebDAV, DLNA | sources/servers/* | open | |
| N5 | Other ecosystems: Stremio add-ons, JS sources (QuickJS), Aniyomi/Mihon runtime | sources/stremio, sources/js, sources/tachiyomi | open | |
| N6 | App lock and privacy (lock key, private mode) | security/LockKey, Privacy, extras/AppLockGate | open | |
| N7 | Inbox / app feed / reminders | notify/Inbox, AppFeed, Reminders, InboxScreen | open | |
| N8 | Support and user guide pages | extras/SupportScreen, settings/UserGuide | open | |
| N9 | Edition and integrity (build flavours, trademark guard) | Edition, security/IntegrityGuard | open | |

## Not covered
(Filled at the end of the audit.)
