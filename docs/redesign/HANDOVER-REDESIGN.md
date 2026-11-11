# Handover: Yumeiro redesign "Tsuki no Michi" (月の道)

For the next agent or developer. Read this first, then [REPORT.md](REPORT.md). The project-wide
handover from the previous agent is [../HANDOVER.md](../HANDOVER.md). It still describes the app,
its modules and its release process, and nothing in it was changed.

## 1. Where things are

| Item | Value |
|---|---|
| Original repo | `https://github.com/siamform/yumeiro`, `main`. **Untouched.** Nothing was pushed or opened there |
| Baseline commit | `af8349e92d6e1c86c17dac4974ca04185b6497c2` ("feat(mihon): extension lib 1.6 and the new store index (Keiyoushi) (#23)") |
| Redesign repo | `https://github.com/siamform/yumeiro-redesign` (private). It is a full copy of the original history plus the redesign branch |
| Branch | `redesign/yume-experience` |
| Remotes in the working copy | `origin` = yumeiro-redesign. `upstream` = the original, with its push URL set to `DISABLED_NO_PUSH` on purpose |
| Merge status | **Not merged, and there is no PR.** The owner must approve the visual redesign first (Stage 5). After that comes CI (Stage 6), then a PR (Stage 7), then a merge only with explicit authorisation |

## 2. What the brief asked for, and the rules that still apply

- Redesign the whole experience, but **keep every feature, route, integration, setting and saved data**.
- **Keep the colours** (every palette in `ui/theme/Color.kt`), the fonts (Unbounded/Manrope) and the Japanese night vibe. No other brand's look. No copying of other apps.
- No fake functionality, placeholders or screenshot-only screens.
- Research is mandatory. It is in [RESEARCH.md](RESEARCH.md).
- Never expose credentials. `local.properties` holds only `sdk.dir` and is git-ignored. No secrets were added anywhere.

## 3. What was done

### Design system (new)
- `app/src/main/java/dev/asfakul/yumeiro/ui/theme/Tokens.kt`: the `Yume` object. It holds a 4dp spacing scale, `gutter` 20, `sectionGap` 28, `touch` 48dp, radii 6/10/16/22/28 plus shapes, `shapeSheet`, two elevations, `moonStroke` 3dp, and the springs `settle()` and `quick()`. **No colours live here.** Components read `LocalMtPalette`.
- `app/src/main/java/dev/asfakul/yumeiro/ui/components/YumeKit.kt` contains:
  - `MoonPath`: the gold stroke motif.
  - `MoonSectionHeader`: overline, title, count and a 48dp "See all".
  - `rememberMoonHairline()`, `YumePanel`, `YumeIconButton` (48dp, with a badge and badge tag), `ActionCapsule`, `MoonTag`, `RowSubtle` and `MoonSheetHandle`.
- The rules are in [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md).

### Shell and shared components (these change every screen)

| File | Change |
|---|---|
| `ui/components/MTTopBar.kt` | Actions are grouped in one `ActionCapsule` of `YumeIconButton`s. The overline has a moon path and an ellipsis. There is a new `offline` param that shows `OfflineTag()` (tag `offline_banner`) inside the header, so offline status no longer covers the title. That was a bug in the before shots |
| `ui/components/MTBottomBar.kt` | Rewritten visually. A 68dp dock with a gliding gold moon-path indicator (`animateDpAsState`), a tonal pill and labels that are always shown. Uses `Role.Tab` and selected semantics. All `nav_tab_*` tags are kept |
| `ui/components/MiniPlayerBar.kt` | Progress moved to the top edge. Swiping the art or title skips tracks (64dp threshold: left = next, right = previous). Adds accessibility custom actions, a 48dp play button and 44dp buttons. Tags are kept |
| `ui/components/Pills.kt` | The selected `FilterPill` uses `onAccent` ink, because white on gold had low contrast. Adds selected semantics |
| `ui/components/StateViews.kt` | `StateMessage` gets a halo ring and moon stroke, plus 48dp buttons. `SubPageHeader` uses a 48dp back button. Skeleton radius updated. `OfflineBanner` moved to the bottom (sub-pages only) |
| `ui/components/TsukiDialog.kt` | Moon stroke on the top edge. Every dialog built on it (about 50) inherits this |
| `ui/components/MediaComponents.kt` | The playing `TrackItemRow` gets a light gold wash plus `Modifier.moonPathMark()`. Duration uses tertiary `labelMedium`. `SongActionBottomSheet` uses `Yume.shapeSheet` and `MoonSheetHandle`. New test tag `song_action_sheet_content` |
| `screens/online/OnlinePlayerUi.kt`, `screens/online/DownloadSheets.kt`, `screens/player/SleepTimerSheet.kt` | All bottom sheets use one shape and the moon handle |
| `ui/screens/MTPlayerApp.kt` | Passes `offline = !isOnline` to the top bar. The bottom `OfflineBanner` shows only on sub-pages |

### Screens redesigned directly

| File | Change |
|---|---|
| `screens/home/HomeScreen.kt` | **Tonight** panel (つづき): one `YumePanel` holding the music resume row (`home_continue_card`, `home_continue_play`) and the video resume row (`home_continue_video`). **Spaces** (すべて): 10 kana keys (音 歌 響 彩 店 映 動 生 棚 保) in 5 columns (10 on wide screens). It covers the same 10 destinations with the same `quick_card_*` tags, plus live status from the new `HomeShortcut.meta` field. **For you**: `MoonSectionHeader` for every shelf and a 48dp shuffle (`home_feed_shuffle`). The feed logic (`FeedGenerator`) is untouched. The old private helpers `BentoTile`, `IconChip`, `Badge`, `DisplayTitle` and `ResumeCardFrame` were removed, and nothing else used them |
| `screens/player/NowPlayingScreen.kt` | The header is collapse · animated EqualizerBars + "再生中 · NOW PLAYING" (or "おやすみ · SLEEP mm:ss") + album · more. A labelled **tool rail** holds Lyrics, Up next, Info, Sleep (`now_playing_sleep_button`, with the dot `sleep_timer_active_dot`) and EQ (`now_playing_equalizer_button`). Sleep and EQ used to be unlabelled header icons. Artwork uses `Yume.shapeXl`. The seek bar uses an accent2→accent gradient. The lyrics-import `PillButton` is kept |
| `screens/settings/SettingsDesign.kt` | Tonal `IconTile`, `GroupLabel` with a moon path, `GlassGroup` with `shapeLg` and a hairline, 64dp `CategoryRow`, 64dp divider inset, `headlineSmall` hero. All 19 settings categories inherit this |

Everything else (Music lists, Videos, Stream, Library, Downloads, Hue store, Sources/Extensions,
online pages, the manga reader and the video player) **inherits** the redesign through the kit:
the header, dock, mini player, pills, rows, sheets, dialogs and states. Those screens were **not**
reworked one by one. See section 7.

### Functionality: what was explicitly preserved
- All routes in `navigation/Screen.kt` are unchanged. The tab order and `bottomNavItems` are unchanged.
- The test tags are unchanged. A tag diff of `app/src/main` against the baseline shows 472 vs 473 literal tags. The one "missing" literal is `sleep_timer_active_dot`, which is now passed as a parameter (`dot = …`). `topbar_inbox_badge` is passed the same way (`badgeTag = …`). Only one tag was added: `song_action_sheet_content`.
- There are no changes to ViewModels, repositories, data, DataStore keys, services, playback, extensions, network or the build. **No new dependencies.** `app/build.gradle.kts` is unchanged.

## 4. Build, test, screenshots

The toolchain used here is JDK 17 in `/home/user/tools/jdk` and the Android SDK in `/home/user/tools/sdk`.
On a new machine, follow [../DEVELOPMENT.md](../DEVELOPMENT.md).

```bash
export JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties          # git-ignored
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --no-configuration-cache  # all JVM/Robolectric tests
./gradlew :app:testDebugUnitTest --no-configuration-cache --tests '*RedesignTourTest*'
```

- Tour screenshots go to `app/build/screenshots/tour_<light|dark|small|largetext>_*.png`. If a stop fails, it is listed in `tour_<theme>_missing.txt`. The file is empty when every stop passed.
- `app/src/test/java/dev/asfakul/yumeiro/ui/RedesignTourTest.kt` drives the **real app** (`MainActivity`, with a fake MediaStore provider) through Home, Search, Inbox, Music tabs, the song sheet, Now Playing (error, lyrics, up next, info, sleep sheet, sleep active, more), the mini player and swipe, the Equalizer, Videos tabs, Hues, YouTube, YouTube Music, Stream, Playlist market, Library tabs, Downloads, the Hue store, Settings plus all 19 categories, and the Sources hub tabs. It runs in four configurations: light 411×891, dark, small 360×640 and font scale 1.3.
- Known Robolectric quirks, already handled in the test:
  - Sheet windows capture as blank. The sheet's content node is drawn over a surface-coloured backing, and the real drag handle is not drawn.
  - The "New playlist" dialog's blinking cursor never lets Robolectric go idle, so it is left out of the tour.
- The before shots were made by running the same test file in a temporary worktree of `af8349e`. To redo them, run `git worktree add ../yumeiro-baseline af8349e`, copy the test in, run it, then `git worktree remove`. Never commit in the worktree. A few stops cannot run there because some tags did not exist yet (for example `search` and `song_action_sheet_content`).
- `docs/redesign/screenshots/compare_*.jpg` are side-by-side pairs built from those PNGs with Pillow. Some "before" images come from the earlier static shots, because the baseline tour could not reach those states.

## 5. Decisions (and why)
1. **The information architecture is unchanged** (5 tabs + sub-routes). It keeps users' muscle memory. The research lesson is the Plex 2024 backlash ([TechCrunch](https://techcrunch.com/2024/11/22/plex-redesigns-its-app-to-look-more-like-a-streaming-service/)).
2. **One motif, the moon path**, instead of more glass, gradients or shadows. The brief bans decorative excess.
3. **Labels are always visible** in the dock and the Now Playing rail ([NN/g recognition vs recall](https://www.nngroup.com/articles/recognition-and-recall/)).
4. **48dp targets** ([Android accessibility](https://support.google.com/accessibility/android/answer/7101858?hl=en)).
5. **Offline status never covers content.** It is a header tag on tabs and a bottom pill on sub-pages.
6. **No palette edits.** New components read `LocalMtPalette`, so all moods, AMOLED and Dynamic keep working.

## 6. Repo layout (relevant parts)
```
app/src/main/java/dev/asfakul/yumeiro/ui/
  theme/      Color.kt (palettes, untouched) · Type.kt · Theme.kt · Tokens.kt (NEW)
  components/ YumeKit.kt (NEW) · MTTopBar · MTBottomBar · MiniPlayerBar · Pills · StateViews · TsukiDialog · MediaComponents
  screens/    MTPlayerApp.kt · home/ · player/ · music/ · video/ · online/ · library/ · settings/ · sources/ · …
  navigation/ Screen.kt (routes, untouched)
app/src/test/java/dev/asfakul/yumeiro/ui/RedesignTourTest.kt (NEW)
docs/redesign/  README · HANDOVER-REDESIGN · REPORT · RESEARCH · PROPOSAL · DESIGN-SYSTEM · INVENTORY · NAVIGATION · screenshots/
```

## 7. What is NOT done (continue here)
1. **Per-screen redesign of the remaining screens.** These still use their own layouts and only inherit the kit:
   - Library (`screens/library/LibraryDesign.kt`: hero, tiles)
   - Videos grid and cards (`screens/video/`)
   - Stream and Playlist market
   - Online title, row and search pages (`screens/online/`)
   - Sources hub and Extensions (`screens/sources/`, long help cards)
   - Downloads page, Hue store, manga reader
   - Video player controls (`screens/video/` and `OnlinePlayerUi.kt`)
   - Live TV
   Suggested next order: Library hero → video cards → online title page → Sources hub (fold the long help text into a `YumePanel` with progressive disclosure) → video player chrome.
2. **Research depth.** Jellyfin, VLC, MX Player, Disney+ and Aniyomi were only touched lightly. The board in [RESEARCH.md](RESEARCH.md) covers M3 Expressive, Android accessibility, NN/g, Plex, Netflix, Spotify, Apple Music, YouTube Music, Mihon, Stremio, CloudStream and TiviMate.
3. **Device testing.** Only JVM/Robolectric was run. **No emulator or physical device.** Gestures (the mini-player swipe), TalkBack, real fonts and real performance still need a device check.
4. **CI.** GitHub Actions is disabled in the private redesign repo. CI (Stage 6) only starts after approval.
5. **Sheet screenshots** are composites (see section 4), not pixel-exact captures of the sheet window.

## 8. Verification log
All of this was actually run on this branch (Linux, JDK 17, Robolectric). Nothing here was run on a device.

| Command | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest --no-configuration-cache --continue` | **BUILD SUCCESSFUL: 71 test classes, 409 tests, 0 failures, 0 errors, 0 skipped.** This includes `RedesignTourTest` (4 tours) and every existing test |
| `./gradlew :app:testDebugUnitTest --tests '*RedesignTourTest*'` | 133 PNGs. Every `tour_*_missing.txt` is empty, so every tour stop ran |
| The same tour on a baseline `af8349e` worktree | 132 PNGs. Only the `search` stop failed, because that tag flow did not exist yet |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL. `app-debug.apk` was built (not installed on any device) |
| Test-tag diff of `app/src/main` (baseline vs branch) | No tag lost (see §3) |
| `git diff af8349e -- app/src/main/java/dev/asfakul/yumeiro/ui/theme/Color.kt app/build.gradle.kts` | Empty: colours and dependencies are unchanged |
