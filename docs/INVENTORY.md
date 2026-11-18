# Yumeiro feature inventory (Round 4 audit, checked against the code in R16)

Built from the code on `r15-stability` (481 Kotlin files under `app/src/main/java`), the
manifest, the navigation routes (`ui/navigation/Screen.kt`) and the Settings catalog
(`ui/screens/settings/SettingsCatalog.kt`, 22 categories in 5 groups). Every item has an ID; the
"Covered by" column is filled as each group's audit is reported, and anything not covered at the
end is listed in §Not covered with the reason.

R16 check (2026-11-15): every one of the 368 Kotlin files under `app/src/main/java` was matched to
an item by file or package name. Files with no item became Y8, L10, D8 and X13–X16, or were added to
the code list of P1 and M2.

Handover-6 check (2026-11-17, master handover 7.3): 393 Kotlin files under `app/src/main/java`; each
matched to an item by file name, package or a `pkg/*` entry (the three `update/ui` files are under N3).
Reverse check: every class, object or function named in a Code column still exists in `app/src/main`
(only DESIGN/SYSTEM, a doc link, and the YuStall log tag are not code names). No item added or removed.

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
| H8 | State restoration after process death | MainViewModel, ScrollMemory, NavPrefs | verified on device (emulator, both phones; open tab only) | `ProcessDeathRestoreTest.openTabSurvivesProcessDeath` (macrobenchmark, `am kill` + relaunch, new PID): 44094439021 api36 1/1 PASS 25.3 s, api33-lowram 1/1 PASS 30.6 s, 0 skipped. Coverage only, no bug found, so no fail-before. Scroll position after process death is not asserted |
| H9 | Splash, first-run welcome, explore tour, onboarding gate | ui/screens/splash, extras/SetupGuide, ExploreTour, online/OnboardingGate | done | `FirstRun.toTabs` on every device run (welcome + tour gate) |
| H10 | Top bar, global search entry, search suggestions | ui/components/MTTopBar, YuSearch, SearchSuggest, TabSearch | done | `h10` 43689481257 6/6, 43696246671 |
| H11 | Mini player bar | ui/components/MiniPlayerBar | verified (emulator, both phones): MiniPlayerDeviceTest 44067244782 (api33-lowram), 44073375083 (api36); MINI-T1 test faults fixed | `MiniPlayerDeviceTest` (re-audit: G6 had never taken it) |
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
| L10 | Music history and Home Continue with no screen (swiped from recents) | data/MusicPlayHistory, player/BackgroundPlaybackWatch | done (emulator, both phones) | G5-PC1 227c203, G5-PC2 a96db17; `PlaysCountedWithoutScreenTest`, `ContinueSavedWithoutScreenTest`; `LibraryEmptyProfileDeviceTest` 44200234105 fail → 44201835648 pass |
| L11 | Empty-profile flow (fresh install → like/add/play → kill → reopen) | all G5 stores | done (emulator, both phones) | G5-EP `LibraryEmptyProfileDeviceTest` 44201835648 |
| L8 | Reading library (manga) | ReadingLibrary route, MangaLibrary | done (unit only) | LB-1, LB-2 |
| L9 | Only real data, no placeholder or fake items | all of the above | code read, no bug | no sample items; demo playlists removed unless filled (`LibraryRound2Test`) |
| L10 | (R16) Device videos tab: folders, sorting, thumbnails | ui/screens/video/VideoScreen, ui/image/VideoThumbnailFetcher, data/model/MediaModels | done (unit only) | LB-7 sort remembered, sorted off main; thumbnails already on IO |

## G6 Music
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| M1 | On-device library (scan, albums, artists) | data/repository/MediaRepository | GROUP-13 2026-11-17: M1-1 (2–3 parallel library scans per cold start) fixed 841b085, verified (CI unit: fail-before 44070875412 → 44067269319); LibraryPersist device PASS both phones | `LibraryScanOnceTest` (unit), `LocalPlayerDeviceTest`, `LibraryPersistDeviceTest` |
| M2 | Online mode (YouTube Music), automatic online/offline switch | youtube/music/*, ui/screens/music/MusicScreen, ModeSwitcher, ui/screens/youtube/YtMusicBrowse, youtube/YouTubeAudioDataSource | GROUP-13 2026-11-17: audited; M2-1 (account change kept the old account's YouTube Music Home) fixed cf4d650 + f3d1d38, verified (CI unit: 44070875412 → 44067269319) | `YtAccountMusicCacheTest` (unit), `YmParserTest`, `Round13LogicTest` |
| M3 | Infinite online feed | youtube/music/YmEndless | verified on device (FEED-1, append-only feed); `YmEndless` code read, no bug (GROUP-6) | `Round13LogicTest`, `LivePagedTest`, `LiveFeedTest`; ledger FEED-1 |
| M4 | Lyrics (embedded, LRCLIB) | lyrics/* | MU-3 fixed (unit only); embedded lyrics code read | `LrcLibMissesTest`; ledger MU-3 |
| M5 | Now playing, queue, media session/notification | ui/screens/player/NowPlayingScreen, service/PlaybackService, MusicPlayerManager | MU-4 verified on device (emulator); BUG-6 verified (emulator); mini player H11 verified (emulator, both phones) | `QueueSurvivesScreenTest`, `MusicQueueDeviceTest`, `MusicTapThroughDeviceTest`, `MiniPlayerDeviceTest`; ledger MU-4, BUG-6 |
| M6 | Equalizer | player/equalizer, ui/screens/equalizer | code read in GROUP-6 and GROUP-13 2026-11-17: no finding (observation: presets cover 5 bands) | settings search coverage only |
| M7 | Last.fm scrobbling | scrobble/* | MU-1 verified on device (emulator, 43738288853), MU-2 unit only; real Last.fm send OMT-10 (owner) | `ScrobbleTrackerTest`, `ScrobbleQueueTest`, `ScrobbleFlushDeviceTest`, `LibraryDeviceTest`; ledger MU-1, MU-2 |
| M8 | Song downloads | online/download/SongDownloads | code read in GROUP-6 and GROUP-13 2026-11-17: no finding | `R7LogicTest` (partial) |
| M9 | Music mix | ui/screens/music/MusicMix | GROUP-13 2026-11-17: audited; M9-1 (search matched on main per keystroke) fixed 0dce4ae, verified (CI unit: 44070875412 → 44067269319) | `MusicMixSearchTest` (unit), `R7LogicTest` |

## G7 Manga and live TV
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| T1 | Manga search, filter, source selection | ui/screens/manga/MangaScreens, sources/manga | code read; MG-1 fixed (unit/compile) | `TachiyomiIndexTest`, `SourceMarketTest`; no manga source on CI |
| T2 | Manga title page and reader | MangaTitle, MangaReader routes | code read; MG-1 fixed (compile) | owner reading test OMT-11 |
| T3 | Live TV hub, playlists, playlist market | ui/screens/stream/*, stream/PlaylistMarket, PlaylistStore | LT-1, LT-2 verified on device (emulator); market code read, no bug | `StreamPlaylistTest`, `PlaylistIndexRecoveryDeviceTest`, `PlaylistRetryDeviceTest`; ledger LT-1, LT-2, FEED-1 |
| T4 | M3U parsing, stream resolving, sniffing, page stream finder | stream/M3uParser, StreamResolver, StreamSniffer, PageStreamFinderDialog | LT-3 fixed (unit); code read | `StreamPlaylistTest`, `StreamDetectionTest`, `RequestHeadersTest` |
| T5 | HLS ad filter for streams | stream/HlsAdFilter, AdFilterDataSource | unit only, no bug found | `HlsAdFilterTest` |

## G8 Downloads
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| D1 | Download manager (queue, worker, groups, naming) | online/download/* | verified (emulator): DL-2, DL-5, DL-7, DL-10, series folders + cleanup (44043535616); DL-6 unit (CI) | `DownloadResumeDeviceTest`, `DownloadCleanupDeviceTest`, `DownloadListSaveRaceTest` (ledger DL-2…DL-10) |
| D2 | Forced (IDM-style) downloads, segmented HLS/DASH, mux | ForceGrab, HlsDownloader, MediaMux | DL-1 verified (device), DL-9 unit (CI), DL-10 verified (emulator); DL-11 part 2 open (4fa126e D+E, 7a85df6 F HTTP/1.1 ranges: medians 1.057/1.042×, max 1.1004×, 44121429260; budget not claimed) | ledger DL-1, DL-9, DL-10, DL-11 |
| D3 | Torrent / magnet | eu/kanade/tachiyomi/torrentutils, OnlineDownloads | device test e12705f: TORRENT_RESULT PASS a, b (emulator api33-lowram 44054360131) | `TorrentDownloadDeviceTest` |
| D4 | Pause, resume, failure, retry, cleanup worker | DownloadWorker, DownloadCleanupWorker | verified (emulator): DL-2, DL-3, DL-4, DL-5, cleanup worker (44043535616) | ledger DL-2…DL-5, `DownloadCleanupDeviceTest` |
| D5 | External downloaders (ADM, IDM) | online/download/ExternalDownloader | device test e12705f: EXTDL_RESULT PASS a–d (emulator api33-lowram 44054360131) | `ExternalDownloaderDeviceTest` |
| D6 | YouTube downloads and options | YtDownloads, YtDownloadOptions | unit (CI); local pipeline device test (resume, merge, publish) YTDL_RESULT PASS a, b (emulator api33-lowram 44054360131); real YouTube on CI hits the bot wall → OMT-19 | `YtDownloadPipelineDeviceTest`, OMT-19 |
| D7 | "View downloads" toast → PiP | DownloadPrompt | verified on device (PL-9, 43657729675) | ledger PL-9 |
| D8 | (R16) Downloads screen: list, free space, actions | ui/screens/downloads/DownloadsScreen, online/DownloadSheets | DL-12 fixed a501d8e (disk work off main), verified (emulator, both phones): fail-before 44054365230 → 44059862262 (api33-lowram), 44073375083 (api36) | `DownloadsScreenDeviceTest` (ledger DL-12) |

## G9 Settings
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| S1–S22 | The 22 categories: Look & feel; Navigation bar; Player; Audio quality & sound; Online videos; Downloads; Library & storage; Sources; Source status; Tracking; YouTube account; Watch together; Notifications; Background & battery; Network; Privacy & security; Backup & restore; App updates; Setup guide; User guide; Crash reports; About & developer | ui/screens/settings/* | open | |
| S23 | Settings search | SettingsCatalog keywords + per-page setting names | verified (unit, CI): SE-3 | `SettingsSearchCoverageTest`, `SettingsCatalogTest` |
| S24 | Every reset action | backup/ResetCenter | SE-1 unit PASS (CI 43877618176); device reset walk RESET_RESULT PASS a, b (emulator api33-lowram 44054360131) | `SettingsResetTest`, `SettingsResetDeviceTest` |
| S25 | Backup, restore, auto backup worker, restart | backup/* | BK-1 verified on device; SE-1 restore-by-section unit PASS (CI 43877618176); auto backup period verified on device (SE-6 f) | `SettingsResetTest`, `BackupV2Test`, `BackupDeviceTest`, `ChoiceSettingsEffectDeviceTest` |
| S26 | Theme, backdrop custom image (accepted types) | ui/theme/*, BackdropSettings | verified on device (emulator): SE-2, SE-4 | `BackdropImportDeviceTest`, `BackdropImportTest` |
| S27 | Each toggle's real effect (~42 switches) | settings cards | verified: SE-5 switches (emulator), SE-6 choice rows (unit + emulator, 43974089878); qa_gpu_tier → OMT-16 | `SettingsWalkDeviceTest`, `ChoiceSettingsEffectDeviceTest`, `ChoiceSettingsEffectTest` |

## G10 Alerts, toasts, dialogs and errors
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| A1 | App messages / toasts | ui/components/AppMessages (MediaComponents) | verified on device (emulator): ER-15, ER-18, ST-21; ER-1/ER-2 wording unit only | `MessagesDeviceTest`, `FloatingMessageDeviceTest` (ledger ER-1, ER-2, ER-15, ER-18, ST-21) |
| A2 | Dialogs (Tsuki dialog, update, review, permission) | TsukiDialog, UpdateDialog, ExtensionReviewHost | verified on device (emulator): ER-11, ER-19 | `DialogsDeviceTest`, `RetryActionsDeviceTest` (ledger ER-11, ER-19) |
| A3 | Error views with recovery actions | ErrorHelp, StateViews, OfflineNotice | verified: ER-10, ER-13 (unit, CI); ER-12 Retry (emulator) | `ErrorTaxonomyTest`, `PlaybackRecoveryTest`, `RetryActionsDeviceTest` |
| A4 | No network, slow network, VPN, low storage | network/Connectivity, OnlineErrors | verified on device (emulator): ER-14, ER-17; VPN → OMT-18 | ledger ER-14, ER-17; OMT-18 |
| A5 | Browser check / Cloudflare challenge host | online/web/BrowserCheck, BrowserCheckHost | ER-20 fixed 065f455 (failed check remembered 60 s), verified (emulator, both phones): fail-before 44057033821 → 44054360131 (api33-lowram), 44073375083 (api36) | `BrowserCheckTest`, `BrowserCheckCooldownDeviceTest` (ledger ER-20) |

## G11 Integrations
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| I1 | Trakt | tracking/Trakt | verified (emulator): IG-1, IG-2 | `TrackerSignInDeviceTest`, `AnimeTrackersDeviceTest` |
| I2 | Simkl | tracking/Simkl | verified (emulator): IG-1, IG-2 | `TrackerSignInDeviceTest`, `AnimeTrackersDeviceTest` |
| I3 | Kitsu, AniList | tracking/Kitsu, online/anime/AniList | IG-2, IG-3 verified (emulator); IG-8 verified (emulator, both phones: 44051808801, 44054360131) | `AnimeTrackersDeviceTest`, `KitsuLoginTest`, `AnimeSignInMessageDeviceTest` |
| I4 | TMDB (metadata) | online/SourceFacts / EpisodeStills | verified (emulator): IG-5 | `EpisodeStillsDeviceTest` |
| I5 | OpenSubtitles, SubDL, SubSource | online/subtitles/* | code read, IG-7 verified (emulator, both phones: 44051808801, 44054360131) | `OnlineSubtitlesFallbackDeviceTest` |
| I6 | LRCLIB | lyrics/LrcLib | code read, no finding (on IO, 512 KB cap, miss marks pruned); unit | `LrcLibTest`, `LrcLibMissesTest`, `LyricsParsingTest` |
| I7 | Last.fm | scrobble/LastFm | verified (emulator): IG-4; real account send → OMT-10 | `ScrobbleFlushDeviceTest` |
| I8 | Firebase (FCM push, cloud alerts) | notify/FcmPush, YumeiroMessagingService, CloudAlerts | code read; IG-6 verified (emulator, both phones: 44051808801, 44054360131); push effect verified (SE-5 push_alerts) | `CloudAlertsLookupDeviceTest`, `FcmDeliveryTest`, `FcmPlanTest`, `PushAlertsEffectDeviceTest` |
| I9 | Cloudflare (worker / feed endpoints) | notify/AppFeed, update/UpdateConfig | code read (app + server/), no new finding; server logic 31/31 (CI 43765850022), X16-2 local unit | `AppFeedTest`, server `logic.test.ts` |
| I10 | YouTube account (cookie paste, auth hash) | youtube/YtAccount, YtCookiePaste, YtAuthHash | code read, no finding; unit | `YtAccountTest`, `YtCookiePasteTest`, `PasteCookieAndPushScreenshotTest` |
| I11 | GitHub releases (self-update) | update/GitHubReleaseClient | code read, no finding (official repo, SHA-256 required, pinned redirects); unit; upgrade path X10-5 verified (emulator) | `ReleaseTagsTest`, ledger X10-5 |

## G12 Cross-cutting systems
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| X1 | Architecture and layering | whole app, docs/SYSTEM-DESIGN.md | audited (GROUP-12): X1-3, X1-4 fixed (unit); PL-1 verified on device; DOC-1 document | ledger X1-3, X1-4, PL-1, DOC-1 |
| X2 | Memory / OOM, low-RAM device class | MemoryWatch, DeviceClass, ImageCacheSize | ST-2 verified on device; X2-2, X2-3 done; heap X2-3h (emulator); X2-4 emulator; ST-19, ST-20 emulator; **X2-1 open; ST-18 verified (emulator) for the structural change (cbad735, 77d387a; regression phones 44190759388, TV 44200018532 / 44201869204), timing not claimed** | ledger ST-2, X2-1…4, ST-18…20 |
| X3 | Main-thread stalls / ANR, StrictMode | diagnostics/DevStrictMode, YuStall watchdog, text/RegexWarmup, RegexCache, startup/PrefsWarmup | ST-1, ST-3, ST-6, ST-16 verified on device; ST-13…15 unit + emulator; ST-22 verified (emulator: 44060561160 → 44062811776, api36 44073375083); **ST-7, ST-17 open** | `StabilityStressDeviceTest`, `StorageMainThreadDeviceTest`; ledger ST-* |
| X4 | Battery and heat | HeatGuard, NotifyPacer | X4-1 verified on device (emulator); O6 unit only | `HeatGuardDeviceTest`; ledger X4-1, O6 |
| X5 | Notifications while closed (FCM, UnifiedPush, alarms, workers, boot) | notify/*, PushWakeService, NotifyAlarmReceiver | X5-1, X5-2, ST-4 verified on device (emulator); NT-1, O9 unit only | `ClosedAppAlertDeviceTest`; ledger X5-1, X5-2, NT-1 |
| X6 | Crash logs and export | diagnostics/CrashLog, CrashExport | X6-1, CR-1, O7 verified on device (emulator); X6-2 done | `CrashUiDeviceTest`; ledger X6-1, X6-2, CR-1 |
| X7 | Security: secrets in APK, storage encryption, network, integrity | security/*, IntegrityGuard, SecretBox | X7-1, X7-4 unit; X7-2 CI; X7-3 done (OMT-13 owner); X7-5 document; SEC-1 verified | ledger X7-1…5, SEC-1 |
| X8 | Accessibility (TalkBack, contrast, touch targets, font scale) | all UI | X8-1 verified on device; X8-2 emulator 5/5 both phones; X8-3 done | `AccessibilityDeviceTest`; ledger X8-1…3 |
| X9 | TV and low-end behaviour | ui/tv, DeviceClass | X9-1 verified (emulator tv-api24); X9-2…6, X9-8 done; X9-7 product part owner (ADR-014 done); **BUG-5 open** (TV-2/3/5 and TV-4 focus verified, TV-3 fix 45a15ea 44118950490/44121151867; TV-4 cross-source search owner) | `HomeNavigationDeviceTest`, `TvBug5ReproDeviceTest`; ledger X9-* |
| X10 | Build, CI, release, signing | .github/workflows, gradle | X10-1…4 verified on CI; X10-5 upgrade verified (emulator); R8/ABI/signing review unit; CI-1, CI-2, DEP-1 verified | ledger X10-*, CI-1, CI-2, DEP-1 |
| X11 | Network stack: smart DNS, DoH, auto-bypass, settings | network/* | verified (CI unit) | X11-1..5: CI 44067269319 (d702c8c) XML read by name: SmartDnsResilienceTest 4/4, DnsWireClientTest 4/4, 0 skipped/failures/errors; fail-before recorded in GROUP-12. Device-level DNS on a blocked network stays owner (home network) |
| X12 | Caching: data cache, image cache, online codec | cache/* | verified (CI unit) | X12-1, X12-2: CI 44067269319 (d702c8c) XML read by name: DataCacheBoundsTest 4/4, 0 skipped/failures/errors; fail-before recorded in GROUP-12 |
| X13 | (R16) Android 7–16 compatibility (minSdk 24), OS version guards | OsCompat, MTApplication | X13-1 done (ADR), X13-2…5 done (emulator api28); X13-6 owner decision | ledger X13-1…6 |
| X14 | (R16) Design system and shared components | ui/components/YumeKit, Motion, Pills, Artwork, YuEditorial, Kana, YumixLogo | audited (code, GROUP-12): palette use, tone contrast ≥ 5.26:1, ATF incl. 130 % font, RTL (G12-RTL emulator) | `AccessibilityDeviceTest`, `RtlLayoutDeviceTest` |
| X15 | (R16) Shared live-feed component (append-only cards) | ui/feed/LiveFeed, FeedCard, OnlineHuePage | audited; X15-1 fixed (unit) | |
| X16 | (R16) Cloud Worker (alerts, FCM fan-out, market index) and its abuse guards | server/src/index.ts, fcm.ts, logic.ts | audited; handler tests added (CI 43765850022), no defect | |

## New groups (not in the prompt's list)
| ID | Item | Code | Status | Covered by |
|---|---|---|---|---|
| N1 | Watch Together | watch/WatchTogether, ui/screens/watch/* | audited (GROUP-13, local parts): N1-1, N1-2 fixed 0040d98, verified (CI unit: 44070875412 → 44062718477); two-phone follow = real hardware | `WatchTogetherHostTest` |
| N2 | Web sites tab, saved sites, site logins, safe WebView, ad shield, headless capture | ui/screens/sites, online/web/* | audited (GROUP-13): N2-1 fixed 83d7818, verified (emulator, both phones: fail-before 44062815556 → 44062811776, 44073375083; CI unit) | `SiteSignOutDeviceTest`, `SiteAccessTest`, `BrowserCheckCooldownDeviceTest` |
| N3 | Self-update (check, download, install, package replaced) | update/* | audited (GROUP-13): N3-1 fixed 13e8136, verified (emulator, both phones: 44062815556 → 44059862262, 44073375083); verify/install path code read, no finding | `UpdateMainThreadDeviceTest`, `UpdaterLogicTest`, `ReleaseTagsTest`; upgrade X10-5 |
| N4 | Home servers: Jellyfin, Plex, SMB, WebDAV, DLNA | sources/servers/* | audited (GROUP-13): N4-1 fixed 595305f, verified (CI unit: 44070875412 → 44062718477); N4-2 fixed 7c69091, verified (emulator + CI unit) | `SourceStoresSafetyTest` |
| N5 | Other ecosystems: Stremio add-ons, JS sources (QuickJS), Aniyomi/Mihon runtime | sources/stremio, sources/js, sources/tachiyomi | audited (GROUP-13): N5-1 fixed 595305f, verified (CI unit: 44070875412 → 44062718477); JS loop timeout OK by code + library source | `SourceStoresSafetyTest`, `UniversalAddEcosystemsDeviceTest`, `UniversalSourcesLogicTest` |
| N6 | App lock and privacy (lock key, private mode) | security/LockKey, Privacy, extras/AppLockGate | audited (GROUP-13): N6-1 verified (emulator api33-lowram, api36 44073375083), N6-3 + N6-4 verified (emulator api28); N6-2 verified (emulator api29: 44114305239 → 44112738603) | `AppLockDeviceTest` |
| N7 | Inbox / app feed / reminders | notify/Inbox, AppFeed, Reminders, InboxScreen | audited (GROUP-13): N7-1 fixed ac13d92, verified (emulator, both phones: 44062815556 → 44062811776, 44073375083) | `InboxMainThreadDeviceTest`, `RemindersTest`, `AppFeedTest` |
| N8 | Support and user guide pages | extras/SupportScreen, settings/UserGuide | audited (GROUP-13): N8-1 fixed 5be755e, verified (CI unit: 44070875412 → 44062718477) | `LinksOpenTest` |
| N9 | Edition and integrity (build flavours, trademark guard) | Edition, security/IntegrityGuard | audited (GROUP-13): code read, no finding | build guard (ADR-013) |

## Proposed features (4.4, gated, not built)

Proposals only (eighth cycle, 2026-11-18). No code exists for these; each needs the 4.4 gate and the owner.

| ID | Feature | Note | Existing code it would touch |
|---|---|---|---|
| F-01 | Language / Sub / Dub badges on search posters | docs/proposals/FEATURE-F-01-language-sub-dub-badges.md | online search results, SourceFacts |
| F-02 | Telegram option | docs/proposals/FEATURE-F-02-telegram.md | stream/LinkHosts (no Telegram code today) |
| F-03 | TeraBox / DiskWala links | docs/proposals/FEATURE-F-03-terabox-diskwala.md | stream/LinkHosts (route exists, unit-tested) |
| F-04 | WebView site list | docs/proposals/FEATURE-F-04-webview-site-list.md | N2 (online/web/SavedSites, ui/screens/sites) |
| F-05 | Nuvio / SkyStream repositories (Vega: owner question OMT-24) | docs/proposals/FEATURE-F-05-nuvio-skystream-repos.md | sources/UniversalAdd, sources/js |
| F-06 | 200 % volume booster | docs/proposals/FEATURE-F-06-volume-booster.md | player |
| F-07 | External player support | docs/proposals/FEATURE-F-07-external-player.md | player |
| F-08 | Upcoming movies and series | docs/proposals/FEATURE-F-08-upcoming-movies-series.md | online/anime/AniList, notify |
| F-09 | Watch Later and Favorite on posters | docs/proposals/FEATURE-F-09-watch-later-favorite-posters.md | library lists |
| F-10 | Glass theme (ADR-017 proposed) | docs/proposals/FEATURE-F-10-glassmorphism-theme.md | ui/theme |
| F-11 | Manga auto-translation | docs/proposals/FEATURE-F-11-manga-translation.md | ui/screens/manga reader |
| F-12 | Vertical shorts player (ADR-018 proposed) | docs/proposals/FEATURE-F-12-vertical-video-player.md | youtube Shorts shelf, StreamMediaFactory |
| NBUG-5 | Dub filter count and silent episode fallback (bug, signal D fixed: mixed language runs) | docs/proposals/FEATURE-NBUG-5-dub-count.md; androidTest DubCountProbeDeviceTest; test online/MixedLanguageEpisodesTest; online/OnlineSession.labelledEpisodes; ui/screens/online/OnlineDetailScreen episodeCountText | online/OnlineRepository versions (391, 420), ui/screens/online/OnlinePlayerUi (552) |
| NBUG-6 | Per-feature DNS with DoH, sequential fallback (owner gate: no side effect) | docs/proposals/FEATURE-NBUG-6-per-feature-dns.md | network/SmartDns, network/DnsMessage, eu/kanade/tachiyomi/network/DohProviders |
| NF-1 | Episode watch status (Watched / In progress / Fresh) | — | OnlinePlayerUi episode rows (Watched check exists), BUG-1 resume store |
| NF-2 | Download storage labels; universal stream download | — | ui/screens/downloads (Offline/Permanent labels exist), online/download/HlsDownloader |
| NF-3 | Extension pop-up blocker = ADR-014 (built, verified emulator; EXT-T1 cannot reproduce on emulator, OMT-26) | ledger ADR-014 row | online/ExtensionPopups, online/ExtensionWindowWatch |

## Not covered
(Filled at the end of the audit.)
| G5-LL | Cold MediaStore scan cost with a large library (Library Jump back in / Most played wait) | androidTest LargeLibraryScanDeviceTest (5,000 songs) | 44211556974: 99–180 ms api33-lowram, 162–238 ms api36 |
| G6-ST1 | Music sleep timer with no screen (process object MusicSleepTimer) | androidTest SleepTimerWithoutScreenDeviceTest | 44217196977 PASS both phones |
| G6-WL | Wi-Fi lock while a music stream plays (PlaybackService.wakeModeFor) | unit MusicWakeModeTest; androidTest MusicWifiLockDeviceTest | 44219681816 PASS both phones |
| G6-AF | Audio focus: transient / duck / permanent | androidTest MusicAudioFocusDeviceTest | 44219681816 PASS both phones; unplug OMT-28 |
| G6-NC | Media notification + outside MediaController | androidTest MusicNotificationControlsDeviceTest | 44219681816 PASS both phones |
| G6-PG1 | Detail page "load more" keeps the continuation (YmState.afterDetailMoreFailed) | unit YmDetailPagingTest; androidTest YmPagingNetworkDeviceTest | CI 44223304640; device 44225724090 PASS both phones |
| G6-MO1 | Music mode follows the connection, keeps the user's pick (MusicMode.follow) | unit Round13LogicTest; Robolectric UI MusicModeReconnectTest | CI 44227662448 |
| G6-LF | Last.fm queue flush rules (Scrobbler.flush) | unit ScrobbleFlushMockedTest (server mocked) | CI 44227662448 8/8 |
| G6-LY | LRCLIB find path (LrcLib.find) | unit LrcLibMockedTest (server mocked) | pending re-run |
