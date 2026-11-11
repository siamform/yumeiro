# Screen inventory & coverage checklist

Audited at baseline `af8349e` (siamform/yumeiro `main`). Paths are relative to
`app/src/main/java/dev/asfakul/yumeiro/ui/`. Status legend:

- **R**: redesigned directly in this branch.
- **K**: inherits the redesign through shared kit components (header, dock, sheets, dialogs, states, pills, track rows).
- **—**: unchanged visually, but audited, and its functionality is verified untouched.

Every route in `navigation/Screen.kt` and every testTag listed in `HANDOVER-REDESIGN.md` still exists.

## Shell & navigation
| Screen / part | File | Purpose & entry | Key states | Problems found (before) | Direction / status |
|---|---|---|---|---|---|
| App shell, tab pager | `screens/MTPlayerApp.kt` | 5 tabs + sub-routes, swipe between tabs | splash, offline, PiP, video open | Offline pill floated over the tab title (seen in the before shots) | R: offline is now a header tag on tabs and a bottom pill on sub-pages |
| Top bar | `components/MTTopBar.kt` | title, kana overline, search/inbox/settings | search expanded, suggestions, badge | 3 separate glass circles = visual noise; overline clipped | R: one ActionCapsule, 48dp YumeIconButtons, a moon-path overline with ellipsis |
| Bottom dock | `components/MTBottomBar.kt` | 5 destinations | selected, joined with mini player | 76dp tall; selection shown only by a tonal pill | R: 68dp dock, a gliding gold moon-path indicator (spring), labels kept |
| Mini player | `components/MiniPlayerBar.kt` | resume / control playback | playing, paused, hidden | progress hidden at the bottom edge; 40dp buttons; no gestures | R: progress on the top edge, swipe left/right = next/previous, 44/48dp buttons, a11y custom actions |
| Search suggestions | `components/SearchSuggest.kt` | typeahead | recent, results, empty | — | K |
| In-app messages | `components/StateViews.kt` `AppMessageHost` | toasts with action | info/success/warn/error | — | — (already on palette) |
| Splash | `screens/SplashScreen*` | start-up | — | — | — |

## Home
| Part | File | Before | After |
|---|---|---|---|
| Resume | `screens/home/HomeScreen.kt` | two tall 228dp cards side by side | R: "Tonight" (つづき) single panel, music row + video row, 48dp play, moon-path progress |
| Shortcuts | same | ~600dp bento wall of 10 tiles in 5 sizes | R: "Spaces" (すべて): 10 kana keys in 2 rows (5 cols, 10 on wide). All 10 destinations, the same `quick_card_*` tags, live status (song count, Hues live, downloads active) |
| For-you feed | same | endless FeedGenerator, shuffle 40dp | R: MoonSectionHeader for every shelf, 48dp shuffle. Feed logic untouched |
| States | | empty library, loading skeleton, end | K (StateMessage / skeleton redesigned) |

## Music
| Screen | File | Status |
|---|---|---|
| Music tab (Device / YouTube Music, Tracks/Albums/Artists/Folders/Favourites) | `screens/music/*` | K: FilterPill contrast fixed (onAccent ink on gold), track rows get the moon-path mark |
| Track row, song action sheet, add-to-playlist, song details, batch bar | `components/MediaComponents.kt` | R: TrackItemRow selection = moon-path mark + light wash; song sheet = moon handle + sheet shape |
| Now Playing (artwork, lyrics, queue, info) | `screens/player/NowPlayingScreen.kt` | R: header (collapse · 再生中 + album · more); a labelled five-tool rail (Lyrics · Up next · Info · Sleep · EQ); radius-28 artwork; gradient moon seek |
| Sleep timer sheet | `screens/player/SleepTimerSheet.kt` | K: moon handle and sheet shape |
| Equalizer | `screens/equalizer/*` | K (headers) |
| YouTube Music | `screens/youtube/*` | K |

## Videos & streaming
| Screen | File | Status |
|---|---|---|
| Videos tab (Hues / YouTube / Device; All, Folders, Continue, Recent) | `screens/video/VideoScreen.kt`, `VideoLibraryScreen.kt` | K |
| Video player + dialogs (speed, aspect, audio track, subtitles, info, quality), HUD, gestures | `screens/video/*Player*` | — (player behaviour untouched; dialogs inherit TsukiDialog) |
| Online home (Hero, Picks, Feed, GetStarted, HuePage), search, detail, row, history, library, resolve layer, watch panel | `screens/online/*` | K (dialogs, sheets, states); per-screen polish is listed in the handover |
| YouTube watch page / layer | `screens/watch/*` | K |
| Download sheets | `screens/online/DownloadSheets.kt` | K: moon handle |
| Online subtitle dialog | `screens/online/OnlineSubtitleDialog.kt` | K (TsukiDialog) |

## Extensions & sources
| Screen | File | Status |
|---|---|---|
| Extensions, input dialog, review host | `screens/online/ExtensionsScreen.kt`, `ExtensionReviewHost` | K (SubPageHeader, dialogs) |
| Extension market | `screens/online/ExtensionMarketScreen.kt` | K |
| Sources hub (+URL dialog, market section) | `screens/sources/*` | K |
| Source health | `screens/sources/SourceHealth*` | K |
| Browser check host | `screens/online/BrowserCheckHost*` | — |

## Live TV
| Screen | File | Status |
|---|---|---|
| Stream tab (network stream, playlists, market entry) | `screens/stream/*` | K (header, dock, pills) |
| Playlist detail / market / edit / add / channel edit / confirm delete / page stream finder dialogs | `screens/stream/*` | K (TsukiDialog moon stroke) |
| Stream watch panel | `screens/stream/StreamWatchPanel*` | — |

## Library, downloads, reading
| Screen | File | Status |
|---|---|---|
| Library (Music / Videos / Reading tabs, hero, quick tiles, playlists) | `screens/library/LibraryDesign.kt`, `LibraryScreen.kt` | K |
| Playlist detail | `screens/library/*` | K |
| Downloads page | `screens/downloads/DownloadsScreen.kt` | K |
| Manga home / detail / reader | `screens/manga/*` | K (reader untouched) |

## Settings (19 categories, each screenshotted by `RedesignTourTest`)
Look & feel · Player · Audio & equalizer · Online videos · Downloads · Library & storage · Sources ·
Source status · Tracking · YouTube account · Watch together · Notifications · Background & battery ·
Network · Privacy & security · Backup & restore · App updates · User guide · About & developer.
File: `screens/settings/*`. Status **R** for the shared primitives in `SettingsDesign.kt`:
GroupLabel (moon-path mark), GlassGroup (radius 22, moon hairline), CategoryRow (64dp minimum, calmer
tonal IconTile; the category tints are kept), RowDivider and CategoryHero (display type). Cards
(DownloadSettings, NetworkSettings, OnlineSettings, PushAlerts, SetupGuides, SettingsExtras) are K.

## Overlays & hosts
Inbox, Support, ReminderHost, NotificationPromptHost, PermissionCenter, AppLockGate, DownloadPromptHost,
UpdateDialog, ExtensionReviewHost: K through TsukiDialog (moon stroke) and StateMessage.

## States checklist
| State | Where it is handled | Status |
|---|---|---|
| Loading / skeleton | `SkeletonRow`, `shimmer`, `FeedLoadingSkeleton` | K |
| Empty / no results / error / not found | `StateMessage` (26 uses) | R: halo ring, moon stroke, display title, 48dp actions |
| Offline | MTTopBar `OfflineTag`, `OfflineBanner` | R |
| Permission denied | `PermissionPromptCard`, PermissionCenter | K |
| Selected | dock, pills, track rows, tool rail | R |
| Disabled | Material defaults | — |
| Small screen (360×640) | `RedesignTourTest.smallScreenTour` | tested |
| Large text (1.3×) | `RedesignTourTest.largeTextTour` | tested |
| Light / dark | `lightTour` / `darkTour` | tested |
