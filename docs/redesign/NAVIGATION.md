# Navigation breakdown (round 2)

## Structure
```
Bottom bar (bottomNavItems, navigation/Screen.kt): 4 tabs
├── Home     → Search · Finish setting up · Continue · Next episodes · Explore Yumeiro (12 tiles)
│              · Sources & extensions · Moods · New arrivals · Most played · Watched recently
│              · Random videos · Yumix picks (banner per extension) · On YouTube for you
├── Music    → modes: On device (Tracks/Albums/Artists/Folders/Favourites) | Online (YouTube Music) | Mix
├── Videos   → modes: Yumix (extensions) | YouTube | Device | Live TV (the former Stream tab)
└── Library  → Music | Videos | Reading
Header (every tab): Search (Home, Music on device) · Inbox (badge) · Settings
Screen.Stream route: kept, redirects to Videos → Live TV (deep links keep working)
Sub-routes (no bottom bar): Settings (+20 categories incl. Setup guide), Equalizer, PlaylistDetail,
  Market, OnlineSearch, OnlineTitle, OnlineRow, OnlineHistory, OnlineLibrary, Downloads, Extensions,
  ExtensionMarket, SourcesHub?tab, Manga, MangaTitle, MangaReader, Privacy, Tracking, YouTubeAccount,
  Backup, WatchTogether, SourceHealth
Overlays: Setup guide (first launch, or from Home/Settings), Now Playing, video player, Inbox/Support,
  dialogs, sheets
```

## Home "Explore Yumeiro" → destination map
| Tile | Tag | Destination |
|---|---|---|
| Yumix | `quick_card_hues` | Videos → Yumix |
| YouTube | `quick_card_youtube` | Videos → YouTube |
| Live TV | `quick_card_live` | Videos → Live TV |
| Online music | `quick_card_ytmusic` | Music → Online |
| Device music | `quick_card_music` | Music → On device |
| Device videos | `quick_card_videos` | Videos → Device |
| Manga | `quick_card_manga` | Manga route |
| Music mix | `quick_card_mix` | Music → Mix |
| Downloads | `quick_card_downloads` | Downloads route |
| Equalizer | `quick_card_audio_engine` | Equalizer route |
| Library | `quick_card_library` | Library tab |
| Get sources | `quick_card_store` | Yumix store (`Screen.Extensions`) |
| Sources & extensions banner | `home_sources_banner` | Sources hub |

## Setup guide steps → destination
| Step | Opens |
|---|---|
| Play your music and videos | System media permission prompt |
| Add Yumix sources | Extension market |
| Add manga sources | Sources hub, Manga tab |
| Search for a video | Online search |
| Set up DNS | Settings → Network (via `SettingsLaunch`) |
| Turn on notifications | System prompt (Android 13+), or Settings → Notifications |
| Watch Live TV | Videos → Live TV |
| Tune the sound | Equalizer |

## Gestures
- Swiping between tabs (the pager) works as before, now across 4 pages.
- Mini player: tap opens Now Playing, swipe left = next, swipe right = previous.
- Back: unchanged. In the setup guide, Back goes to the previous page and closes the guide from the first page.
