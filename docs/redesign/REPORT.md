# Yumeiro redesign "Tsuki no Michi": review report

Branch `redesign/yume-experience` · repo `siamform/yumeiro-redesign` · baseline `af8349e`.
**For approval only. Not merged, and no PR is open.**
All screenshots are rendered from the real application code under Robolectric (JVM). They are
not mockups. Each image has the baseline on the left and the redesign on the right.

## A. Executive summary
Yumeiro keeps its identity: moon-gold on ink (Tsuki Night) and on paper (Washi Day), Unbounded
and Manrope, Japanese overlines and kana glyphs. **No colour value was changed.** The redesign
adds one motif, *the moon's path*. It is a thin gold stroke that marks where you are: the
selected tab, the playing song, a section, progress. It also gives the app a token and kit layer
(`Yume`, `YumeKit`), so screens look like one product.

The shell (header, dock, mini player, sheets, dialogs, states, pills, list rows), Home, Now
Playing and Settings were redesigned directly. Every other screen inherits the shell and kit
changes. Several of those screens still need their own pass (see H). No feature, route, setting
or test tag was removed, and no dependency was added.

## B. Research findings (full board: [RESEARCH.md](RESEARCH.md))
- **48dp minimum targets** ([Android](https://support.google.com/accessibility/android/answer/7101858?hl=en)): header actions, "See all", mini-player play, resume buttons and Now Playing tools now meet it.
- **Recognition over recall** ([NN/g](https://www.nngroup.com/articles/recognition-and-recall/)): labels stay visible in the dock and the Now Playing rail, and every destination is a visible key on Home.
- **Progressive disclosure** ([NN/g](https://www.nngroup.com/articles/progressive-disclosure/)): one primary action per screen. Secondary actions stay in sheets.
- **Don't break muscle memory**: the Plex 2024 redesign moved familiar things and got strong pushback ([TechCrunch](https://techcrunch.com/2024/11/22/plex-redesigns-its-app-to-look-more-like-a-streaming-service/), [r/PleX](https://www.reddit.com/r/PleX/comments/1j4zl9z/how_we_feeling_about_the_new_ui/)). So Yumeiro's routes and tab order did not move.
- **Expressive motion with restraint** ([M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), [motion](https://m3.material.io/styles/motion/overview/how-it-works)): spring motion is used only for the dock indicator and presses.
- **Now Playing tools** ([YouTube Music 2025](https://9to5google.com/2025/09/11/youtube-now-playing-2025-redesign), [Apple Music](https://support.apple.com/guide/iphone/use-the-music-player-controls-iph676daac9b/ios)): tools belong together, labelled, under the controls. Yumeiro does this with its own rail. It does not copy their tabs or layout.
- **Extensions** ([Mihon](https://mihon.app/docs/faq/browse/extensions), [Stremio](https://docs.stremio-addons.net/addons/install), [CloudStream](https://recloudstream.github.io/csdocs/)): trust and repo flows must stay explicit. The Sources hub flows were kept exactly as they were.

## C. Old vs new (key changes)
| Area | Before | After |
|---|---|---|
| Offline status | Floating pill **covering the tab title** | A tag in the header, or a bottom pill on sub-pages |
| Header | Three separate glass circles | One action capsule with 48dp icon buttons |
| Dock | 76dp with a tonal pill only | 68dp with a gliding gold moon-path indicator and labels |
| Mini player | Progress on the bottom edge, 40dp buttons, no gestures | Progress on the top edge, swipe to skip, 48/44dp buttons, accessibility actions |
| Home | Two 228dp resume cards and a ~600dp bento of 10 tiles in 5 sizes | A "Tonight" panel, 10 kana "Spaces" keys in two rows, then For you |
| Now Playing | Sleep and EQ as unlabelled header icons; three labelled pills | One labelled five-tool rail; a calm header with a live EQ glyph and the sleep countdown |
| Lists | Playing row tinted | Light gold wash plus a moon-path mark |
| Pills | White text on gold | `onAccent` ink on gold (readable) |
| Sheets and dialogs | Mixed shapes and handles | One sheet shape, moon handle, moon stroke on dialogs |
| Settings | Bordered icon tiles, tight rows | Tonal tiles, 64dp rows, moon-marked group labels |

## D. Screen by screen
| Screen | Before / after |
|---|---|
| Home, dark | ![](screenshots/compare_home_dark.jpg) |
| Home, light | ![](screenshots/compare_home_light.jpg) |
| Search (the before image is an earlier static shot) | ![](screenshots/compare_search.jpg) |
| Inbox | ![](screenshots/compare_inbox.jpg) |
| Music | ![](screenshots/compare_music.jpg) |
| Song actions sheet (the before image is the Music tab; composite, see H) | ![](screenshots/compare_song_actions.jpg) |
| Now Playing, dark | ![](screenshots/compare_now_playing_dark.jpg) |
| Now Playing, light | ![](screenshots/compare_now_playing_light.jpg) |
| Up next | ![](screenshots/compare_up_next.jpg) |
| Lyrics | ![](screenshots/compare_lyrics.jpg) |
| Song info | ![](screenshots/compare_song_info.jpg) |
| Sleep timer sheet | ![](screenshots/compare_sleep_sheet.jpg) |
| Sleep timer active | ![](screenshots/compare_sleep_active.jpg) |
| Mini player | ![](screenshots/compare_mini_player.jpg) |
| Equalizer | ![](screenshots/compare_equalizer.jpg) |
| Videos | ![](screenshots/compare_videos.jpg) |
| Stream | ![](screenshots/compare_stream.jpg) |
| Library (kit only) | ![](screenshots/compare_library.jpg) |
| Downloads (kit only) | ![](screenshots/compare_downloads.jpg) |
| Hue store (kit only) | ![](screenshots/compare_hue_store.jpg) |
| Settings | ![](screenshots/compare_settings.jpg) |
| Settings → Look & feel | ![](screenshots/compare_settings_appearance.jpg) |
| Sources hub (kit only) | ![](screenshots/compare_sources_hub.jpg) |
| Small screen 360×640: Home | ![](screenshots/compare_small_home.jpg) |
| Small screen: Now Playing | ![](screenshots/compare_small_now_playing.jpg) |
| Font scale 1.3: Now Playing | ![](screenshots/compare_largetext_now_playing.jpg) |

The full set (134 PNGs in light, dark, small and large text, covering every Settings category,
the Music, Videos, Library and Sources tabs, and every Now Playing state) is produced by
`RedesignTourTest` (see [HANDOVER-REDESIGN.md §4](HANDOVER-REDESIGN.md#4-build-test-screenshots)).

## E. Navigation breakdown
See [NAVIGATION.md](NAVIGATION.md). In short:
- The **routes are unchanged** (5 dock tabs plus all sub-routes).
- Wayfinding is better: the gliding indicator, always-visible labels, the header capsule, and Home "Spaces" as a launcher for all 10 areas.
- One gesture was added: swipe the mini player to skip. Every existing gesture is kept.

## F. Functionality preservation
- **Routes**: `navigation/Screen.kt` is untouched.
- **Data, state and storage**: no ViewModel, repository, DataStore, service, playback, extension or network code was changed. User preferences and saved data are unaffected.
- **Test tags**: every baseline tag still exists. Two are now passed as parameters, and one was added (`song_action_sheet_content`).
- **Colours**: `ui/theme/Color.kt` is untouched. All moods, AMOLED and Dynamic still apply.
- **Tests actually run** (JVM/Robolectric, on this branch): see the counts in [HANDOVER-REDESIGN.md §8](HANDOVER-REDESIGN.md#8-verification-log). **No emulator or device tests were run.**

## G. Implementation summary
- **New files**:
  - `ui/theme/Tokens.kt`
  - `ui/components/YumeKit.kt`
  - `app/src/test/.../ui/RedesignTourTest.kt`
  - `docs/redesign/*`
- **Changed files (14)**:
  - `MTTopBar.kt`, `MTBottomBar.kt`, `MiniPlayerBar.kt`, `Pills.kt`, `StateViews.kt`, `TsukiDialog.kt`, `MediaComponents.kt`
  - `MTPlayerApp.kt`, `HomeScreen.kt`, `NowPlayingScreen.kt`, `SettingsDesign.kt`
  - `OnlinePlayerUi.kt`, `DownloadSheets.kt`, `SleepTimerSheet.kt`
- **Components introduced**: `MoonPath`, `MoonSectionHeader`, `YumePanel`, `YumeIconButton`, `ActionCapsule`, `MoonTag`, `RowSubtle`, `MoonSheetHandle`, `rememberMoonHairline`, `Modifier.moonPathMark`, `OfflineTag`, the Home `KanaKey` and `ResumeRow`, and the Now Playing `RailTool`.
- **Components replaced or removed**: the Home `BentoTile`, `IconChip`, `Badge`, `DisplayTitle` and `ResumeCardFrame` (private, with no other users).
- **Architecture**: unchanged. Compose plus the existing palette (`LocalMtPalette`). No new modules.
- **Dependencies**: **none added**.

## H. Known limitations (honest)
1. **Not every screen got its own pass.** Library, the Videos grid, Stream and the Playlist market, the online title, row and search pages, the Sources hub and Extensions, Downloads, the Hue store, the manga reader, the video player chrome and Live TV only inherit the shell and kit changes. The before/after pairs for Library, Downloads, the Hue store and the Sources hub look almost the same for this reason. The brief asked for all of them. This is the main open work.
2. **Research depth**: Jellyfin, VLC, MX Player, Disney+ and Aniyomi were not studied in depth.
3. **No device or emulator testing.** Swipe gestures, TalkBack, performance and real font rendering are not verified on hardware.
4. **The sheet screenshots are composites**. Robolectric renders sheet windows blank, so the sheet content was drawn over a surface-coloured backing, without the real drag handle. The "New playlist" dialog could not be captured.
5. **CI was not run.** Actions are disabled on the private repo, as planned until approval (Stage 6).
