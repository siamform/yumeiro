# Navigation breakdown

## Structure (unchanged routes)
```
Dock (bottomNavItems, navigation/Screen.kt)
├── Home      ホーム   → Tonight · Spaces (10 kana keys) · For you
├── Music     ミュージック → Device | YouTube Music · Tracks/Albums/Artists/Folders/Favourites
├── Videos    ビデオ   → Hues | YouTube | Device
├── Stream    ストリーム → Network stream · Playlists · Playlist market
└── Library   ライブラリ → Music | Videos | Reading
Header capsule (every tab): Search (not on Videos/Stream/online Music) · Inbox (badge) · Settings
Sub-routes (no dock): Settings(+19 categories), Equalizer, PlaylistDetail, Market, OnlineSearch,
  OnlineTitle, OnlineRow, OnlineHistory, OnlineLibrary, Downloads, Extensions, ExtensionMarket,
  SourcesHub?tab, Manga, MangaTitle, MangaReader, Privacy, Tracking, YouTubeAccount, Backup,
  WatchTogether, SourceHealth
Overlays: Now Playing (expand from mini player), video player, Inbox/Support pages, dialogs, sheets
```

## Home "Spaces" → destination map (all kept, same tags)
| Key | Kana | Tag | Destination |
|---|---|---|---|
| Music | 音 | `quick_card_music` | Music tab |
| YT Music | 歌 | `quick_card_ytmusic` | Music tab → YouTube Music |
| Equalizer | 響 | `quick_card_audio_engine` | Equalizer route |
| Hues | 彩 | `quick_card_hues` | Videos → Hues |
| Store | 店 | `quick_card_store` | Hue store / extension market |
| Videos | 映 | `quick_card_videos` | Videos tab (device) |
| YouTube | 動 | `quick_card_youtube` | Videos → YouTube |
| Live TV | 生 | `quick_card_live` | Stream tab |
| Library | 棚 | `quick_card_library` | Library tab |
| Downloads | 保 | `quick_card_downloads` | Downloads route |

## Gestures
- Swipe between tabs (pager): unchanged.
- Mini player: tap opens Now Playing; **new** swipe left = next, swipe right = previous (also as accessibility custom actions); buttons unchanged.
- Back: unchanged (sub-page → tab → Home → double-back exit).
