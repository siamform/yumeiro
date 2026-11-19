# Group 15 audit: open agent bugs (owner order 2026-11-19, workstream F)

Scope: the bugs left open for the agent (PROGRAM-2026-11-19 workstream F): NBUG-10 (Yumix "Show videos from"
search shows only 2 extensions), TV-YT-1 (YouTube For You on TV is one column of full-width cards), TV-A11Y-1
(one 44 dp view flagged on tv-api34). Method: §13a (DEVELOPMENT.md). Results are labelled unit, CI, emulator,
TV emulator or real hardware.

Not in this group, with the reason: ER-28-TV34 is an evidence gap, not a bug (the Android 14 TV image never showed
the old fault; the fix is shown working there) and stays in the UNVERIFIED-QUEUE; ST-17 stays open by order
(recorded per stress campaign); TV-FP-1 is a test-navigation fault and is checked again by this group's TV
regression (FeedPosition on tv-api24).

## Status

Closed 2026-11-19: Verified (emulator + TV emulator). Fail-before tv-api34 44477069801; pass-after tv-api34 44480266808, phones 44487432724; tv-api24 44487437177 (only known phone-gesture test faults).

## Code read

| File | Lines | Read | Notes |
|---|---|---|---|
| ui/screens/online/YumixFilter.kt | 1–360 | yes | `SourceFilterSheet` 80–157: `skipPartiallyExpanded = Edition.isTv` (96) → phones open at half height; list `heightIn(max = 520.dp)`; search field 120; matching `filterExtensionNames` 193 (correct: every word, accent/case blind) |
| ui/screens/online/OnlineHomeScreen.kt | 185–330 | yes | names = every extension that is on (`providers`, 18+ hidden when off/private); sheet opened at 321 |
| ui/screens/stream/StreamScreen.kt | 770–790 | yes | Live TV reuses the same sheet (playlists): same fault |
| ui/components/YuSearch.kt | 180–190 | yes | Yumeiro's own search sheet already uses `skipPartiallyExpanded = true` |
| ui/screens/stream/LiveTvChrome.kt | 160–170 | yes | Tools sheet: no search field, half height is fine (left as is) |
| Material 3 ModalBottomSheet (androidx-main) | ModalBottomSheet.kt 83–108, SheetDefaults.kt 576–590 | yes | content insets = `safeDrawing` (includes IME): the content grows by the keyboard height but a half-height sheet does not move up |
| ui/screens/youtube/YouTubeScreens.kt | 440–520, 755–800 | yes | For You: `LazyColumn` of `YtVideoCard(wide = true)` (16:9, full width) at every width; Shorts shelf after card 2, "Watch again" after card 5 |
| ui/screens/online/OnlineComponents.kt | 226–231 | yes | `gridColumns()`: Yumix already scales posters by width (128 dp per column) |
| androidTest MessagesDeviceTest.kt | all | yes | a11y check runs on every 5th / long / action message, from the root view: the flagged view is on the screen under the pill, not the pill |
| androidTest FeedPositionDeviceTest.kt | 180–310 | yes | needs 3 cards on screen; youtube opened from Home's shortcut |

## Action–reaction table (with unhappy paths)

| Action | Expected | Before (observed) |
|---|---|---|
| Phone, 20+ extensions on, open "Show videos from" | full list, search box | opens at half height (rows below the fold) |
| Type "anime" (3 match) | the 3 matches above the keyboard | sheet stays at half height; the keyboard covers all but the first ~2 rows; they cannot be scrolled up because the list is not longer than its own box → NBUG-10 |
| Same with 2 extensions | list fits | fine (sheet small, no half state) — why tests with 2 sources never saw it |
| Clear search / Back | full list / close | fine |
| Live TV, many playlists, search | same as Yumix | same fault (same sheet) |
| TV, open sheet | full height (G9-8) | fine |
| TV 1080p (960×540 dp), YouTube For You | several cards per screen, D-pad grid | one column of 16:9 cards 936 dp wide: under 2 per screen → TV-YT-1 |
| Tablet / phone landscape (≥ 600 dp wide) | grid | same single column (not reported, same cause) |
| Phone portrait | one column (unchanged) | one column |
| tv-api34, a11y check during messages | no problem | one 44 dp touch target on the screen beneath → TV-A11Y-1 (view not yet named) |

## Competitor references

| Topic | Reference (tag + files) | What they do |
|---|---|---|
| NBUG-10 | CloudStream 2dcc25c `ui/home/HomeFragment.kt` 384–395 (`selectHomepage`), `ui/search/SearchFragment.kt` 285–296 | the provider picker (with its own search) is a BottomSheetDialog set to `STATE_EXPANDED` |
| NBUG-10 | Mihon v0.21.3 `presentation-core/.../components/AdaptiveSheet.kt` 114–141 | sheets have two anchors only (open, hidden): no half state |
| TV-YT-1 | NewPipe v0.28.0 `util/ThemeHelper.java` 336–415 (`getItemViewMode`, `getGridSpanCount`) | Auto mode uses a grid on large landscape screens; span = screen width / item min width |
| TV-YT-1 | SmartTube (yuliskov/SmartTube, behaviour) | TV home is rows of 16:9 cards, 4 per row on 1080p |
| TV-YT-1 | Yumeiro `OnlineComponents.kt` 228 `gridColumns()` | Yumix posters already scale columns with width |

## Findings register

| ID | Item | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|---|
| G15-1 | NBUG-10 | Search in "Show videos from" shows only ~2 of the matching extensions on phones with many extensions; same for Live TV playlists | the sheet opens at half height on phones and stays there when the keyboard opens; M3 adds the keyboard as content padding, so the matches sit under the keyboard | open the sheet fully on every device (`skipPartiallyExpanded = true`), as CloudStream, Mihon and YuSearch do | unit `g15/FilterSheetSearchHeightTest` (Robolectric, 30 extensions, phone size) | Verified (emulator / TV emulator) |
| G15-2 | TV-YT-1 | YouTube For You on TV / wide screens: one column of full-width cards (under 2 per 1080p screen) | `YtVideoCard(wide = true)` in a single `LazyColumn` at every width | wide screens (≥ 600 dp) lay the stream out in rows of N cards (N = width / 300 dp, 2–4); Shorts and Watch again shelves stay between rows; phones unchanged | device `FeedPositionDeviceTest#youtube` on tv-api34 (old code failed twice: 44440614210, 44443748315) | Verified (emulator / TV emulator) |
| G15-3 | TV-A11Y-1 | tv-api34: one 44 dp touch target [484,780][756,867] (px) | not yet named; the a11y check reads the whole screen under the pill | test now logs the node at the flagged bounds (`MSG_A11Y_NODE`); fix follows from that log | device `MessagesDeviceTest` tv-api34 | Verified (emulator / TV emulator) |

## Optimisation and architecture notes

- G15-1 costs nothing: one flag; the list is still lazy (only visible rows composed). Side effect: on phones the
  sheet opens taller (up to the list cap, 520 dp); Back closes it in one press instead of two (half-height step
  gone), which `PinnedExtensionsDeviceTest.closeSheet` handles (it only expects two steps after a drag).
- G15-2: rows are built from the append-only list (`remember` keyed by size and column count), keyed by their first
  card id, so cards on screen never move when pages are appended (R15 rule). No extra network or images: the same
  cards, smaller thumbnails (fewer pixels decoded per card). TV focus: each card stays focusable; D-pad moves across
  the row and down to the next row.
