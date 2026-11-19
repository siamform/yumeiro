# FEATURE F-09 — Watch Later and Favorite on posters

Status: approved by root 2026-11-19 (option A), built on `feat/yumix`. ADR-033.

Build notes (changes from the design below, with reasons):
- Downgrade: `Status.NONE` is never written. A Favorite-only title is written as `"status":"PLAN"` plus
  `"nostatus":true` and `"fav":true`, so an older build reads it as Watch later instead of losing it (4.6).
  An older build that then edits the file drops the two extra keys; the title stays as Watch later.
- TV uses the same full-height sheet as phones (ADR-031, `sheetDragHandle()` gives no handle on TV), with focus
  moved to "Watch later", instead of a separate dialog. `SheetFocusTvDeviceTest` already covers handle focus.
- The sheet has Watch later, Favorite and Details. "Play" was left out: Details already leads to Play, and a
  play action would need the episode list, which the sheet must not load.
- The hero banner gets long press / Menu instead of a fourth round button: on a 360 dp phone the text column next
  to the poster has about 154 dp, and Play plus two round buttons already fill it.
- The status `PLAN` is now labelled "Watch later" everywhere (it was "Plan to watch").
- New-episode checks skip a Favorite outside My List unless its alerts are on (`NewEpisodesWorker.due`).

Stream: A-yumix. Date: 2026-11-19. Repo read at branch `r15-stability`, head `6c9ee28c`.
Paths are relative to `app/src/main/java/dev/asfakul/yumeiro/` unless they start with
`app/`, `docs/` or `/tmp/`. Builds on `docs/proposals/FEATURE-F-09-watch-later-favorite-posters.md`.

## 1. Goal

From any poster (Home, Yumix, Search, rows, grids, hero), the user can add a title to Watch
Later or mark it Favorite without opening the detail page. Posters show a small mark when a
title is saved. My List can filter Favorites and Watch Later. Works by touch on phones and by
remote on TV. No network call.

## 2. Current code

| Area | File:line | Today |
|---|---|---|
| Status | `online/OnlineBookmarks.kt:26` | WATCHING, PLAN ("Plan to watch"), ON_HOLD, COMPLETED, DROPPED. |
| Bookmark | `online/OnlineBookmarks.kt:28-45` | api, url, name, poster, type, status, addedAt, episodes, notify, newEpisodes. No favorite. Key `api|url`. |
| Save / toggle | `online/OnlineBookmarks.kt:67`, `:79` | `save(status = PLAN)`, `toggle()` add/remove. |
| File | `online/OnlineBookmarks.kt:128`, `:137` | JSON `version 1`; `from()` reads with defaults. Max 1000. `SafeJsonFile`. |
| Backup | `backup/Backup.kt:190` | `online_bookmarks.json` is in the backup. |
| Alerts | `notify/CloudAlerts.kt` (`CloudAlertsPlan.watched`) | Uses `notify` and status (not COMPLETED/DROPPED, not movies). |
| Poster | `ui/screens/online/OnlineComponents.kt:155-187` | `PosterCard`: one `.clickable`, testTag `online_poster`, score top-right, tags top-left and bottom-left, progress bar bottom. No long press, no saved mark. |
| Call sites | 14 `PosterCard` uses (Home, Search, RowScreen, Hero, YumixFilter, HuePage, PlayerUi, `ui/screens/home/HomeScreen.kt`) | All would get the feature through one change. |
| Hero | `ui/screens/online/OnlineHomeHero.kt:182-184` | Round button toggles My List (PLAN). |
| Detail | `ui/screens/online/OnlineDetailScreen.kt:428` | `MyListButton` with status menu. |
| My List | `ui/screens/library/VideoLibraryScreen.kt:169`, `:200-221` | Status filter chips; card menu: status, alerts, remove. |
| Focus | `ui/theme/Theme.kt:109-119` | Global soft press, focus ring, 1.04 scale on TV. |
| Long press elsewhere | `ui/components/MediaComponents.kt:236`, `ui/screens/sites/WebSitesScreen.kt:201` | `combinedClickable` already used. |
| Menu key | none (`rg Key.Menu` gives 0 hits) | No TV options key yet. |
| Sheets | ADR-031 (`docs/SYSTEM-DESIGN.md`), G15-1 | Sheets open fully on every device; TV sheet focus checked by `app/src/androidTest/.../SheetFocusTvDeviceTest.kt`. |

## 3. How others do it

| App | Where | What | Lesson |
|---|---|---|---|
| CloudStream v4.9.1 (`c92e0cf0`) | `app/.../ui/WatchType.kt:7-18` | Watch state enum incl. PLANTOWATCH and NONE. | Watch Later = plan state. |
| CloudStream | `app/.../utils/DataStoreHelper.kt:679-700`, `:793`; `ui/result/ResultViewModel2.kt:935` | Favorites in a separate store from watch state; toggle on detail. | Favorite is independent of status. |
| CloudStream | `ui/search/SearchResultBuilder.kt:151-160`, `:182`, `:231`; `ui/search/SearchHelper.kt:58-64`; `MainActivity.kt:448-472` | Long press opens a quick popup, but it loads the full detail page first (network). | Good entry point, bad cost. Our sheet must be offline. |
| Aniyomi v0.18.2.1 (`97414446`) | `app/.../ui/browse/anime/source/browse/BrowseAnimeSourceScreen.kt:232-249`; `BrowseAnimeSourceComfortableGrid.kt:48`, `:77` | Long press adds to library at once with haptic; removing asks; "In library" badge on poster. | Fast, and the badge is the feedback. |
| Hikari (`5ff8b86c`) | `ui/components/PosterQuickMenu.kt:6`, `:14-57`, `:107+`; used `Components.kt:1063` | Hold 1000 ms, then slide to an option (Details, Play, Library). | Nice on touch, no remote path, hard to discover. |
| Hikari | `HomeScreen.kt:901-913`; `AppStore.kt:4043-4060` | TV hero bookmark toggle; favorites list. | Keep hero button. |

## 4. Design

### 4.1 Options

| | A. One store, `favorite` flag (recommended) | B. Separate favorites file | C. Direct toggle on long press (Aniyomi) |
|---|---|---|---|
| Model | Bookmark gets `favorite: Boolean`. Watch Later = status PLAN. | New `online_favorites.json`. | Same as A. |
| Entry | Long press / Menu key opens a small sheet: Watch Later, Favorite, Details, Play. | Same sheet. | Long press = toggle Watch Later. No Favorite on poster. |
| Pros | One list, one backup file, My List shows both, alerts keep working. | No change to bookmark semantics. | Fastest. |
| Cons | Need a "no status" state for favorite-only titles. | Two stores to keep in sync, new backup entry, title can be favorite but missing from My List. | Favorite needs detail page; misses half the ask. |
| Side effects | Older app reads unknown status as PLAN (see 4.6). | Backup format change. | Accidental adds. |

Recommend A with the sheet.

### 4.2 Data model

- `Bookmark.favorite: Boolean = false`, JSON key `"fav"` (written only when true). File
  version stays 1 (additive; `from()` already uses defaults).
- `Status.NONE("No status")` placed last. Used only when the user favorites a title that is
  not in My List. For NONE items, `notify` defaults to false, so favorites do not start
  alerts by surprise. `CloudAlertsPlan.watched` skips NONE unless `notify` is on.
- New API in `OnlineBookmarks`:
  - `isWatchLater(key)` = status PLAN.
  - `toggleWatchLater(context, item)`: not saved -> add PLAN; PLAN -> if favorite, set NONE,
    else remove; other status -> set PLAN (asks nothing; the sheet shows the current status).
  - `toggleFavorite(context, item)`: not saved -> add NONE + favorite; saved -> flip flag;
    if flag off and status NONE -> remove.
  - `marks: StateFlow<Map<String, Mark>>` with `Mark(watchLater, favorite, status)`,
    rebuilt on every list change (max 1000 entries, cheap, on the caller thread that
    already writes).
- Screens provide `marks` through `LocalSavedMarks` (one collector per screen). Each card
  reads `marks[item.key]`, so it does not collect a flow per card.

### 4.3 UI

Checked against ui-ux-pro-max-skill (`/tmp/uiux`, `2de1134d`): touch target at least 48 dp,
visible focus at least 2 px and 3:1, every gesture has a visible or keyboard path, icons
have labels.

- Poster mark: bottom-right, 20 dp dark round chip with a 14 dp glyph: bookmark for Watch
  Later, heart for Favorite, both if both (heart wins the space on compact cards, the a11y
  text says both). Sits above the progress bar. Score stays top-right, audio tag stays
  bottom-left, so nothing overlaps.
- Quick sheet (phone): opens full height per ADR-031 but with only 4 rows, so it is short:
  poster thumb + title, then "Watch Later" (check when on), "Favorite" (heart filled when
  on), "Details", "Play" (only if Continue exists). Tap acts and closes, short haptic,
  snackbar "Added to Watch Later · Undo".
- Quick panel (TV): a centered dialog instead of a sheet (a sheet's drag handle took focus
  before, see `SheetFocusTvDeviceTest`). First focus on "Watch Later". Back closes and
  returns focus to the same poster.
- Hero: keep the round My List button (Watch Later). Add a heart button next to it.
- Detail: next to `MyListButton`, a heart button. The status menu gets "No status".
- My List (`VideoLibraryScreen.kt:169`): chips "Favorites" and "Watch Later" first, then
  the other statuses (NONE hidden as a chip). Card menu gets "Favorite".
- Home: a "Favorites" shelf (`YuSection` kicker "お気に入り") after My List when it has at
  least 1 item.

### 4.4 Input

- Phone: `PosterCard` takes `onLongClick: ((OnlineItem) -> Unit)?`; when set, uses
  `combinedClickable(onClick, onLongClick, onLongClickLabel = "Save options")`. TalkBack
  then offers the action in its menu.
- TV: Compose `combinedClickable` reacts to a long press of Center/Enter. Old remotes and
  API 24 may send repeat key-downs instead; this must be checked on tv-api24 and a real
  remote. Also add the Menu key (`Key.Menu`, KEYCODE_MENU) via `onPreviewKeyEvent` on the
  card. A one-line hint "Hold OK or press Menu for options" shows once on TV home.
- One place: a `posterActions` lambda provided by the screen, so all 14 call sites need no
  own code.

### 4.5 Flows

Phone: long press poster -> sheet -> Favorite -> heart appears on the poster, snackbar
with Undo. My List -> Favorites chip shows it.

TV: focus poster -> hold OK or press Menu -> panel -> "Watch Later" -> panel closes, focus
back on poster, bookmark glyph shows.

### 4.6 Migration, backup, rollback

- Additive JSON. Backups from the new build restore in the new build fully.
- Older build reading a new file: `"fav"` is ignored; status "NONE" is unknown, `from()`
  falls back to PLAN (`online/OnlineBookmarks.kt:144`, `getOrDefault(Status.PLAN)`). Favorite-only titles
  then show as "Plan to watch". Acceptable, documented in release notes.
- Rollback = revert; data stays readable as above.

### 4.7 Performance and privacy

- No network. Sheet holds only the item already in memory.
- `marks` map max 1000 entries. One recomposition of visible cards on a change (rare, user
  action).
- Write uses the existing `SafeJsonFile` path, off main.
- Data stays on the device and in the user's own backup.

### 4.8 Unhappy paths

- My List full (1000): sheet shows "My List is full" and no change.
- Same title from two extensions: different keys, two marks (same as My List today).
- Long press during scroll: `combinedClickable` cancels on drag.
- TV remote without Menu and without long press: Detail page buttons still work.
- File write fails: state reverts, snackbar "Could not save".

## 5. Test plan

### 5.1 JVM / Robolectric (fail before)

- `OnlineBookmarksFavoriteTest`: toggleFavorite on unsaved -> NONE + fav; flip off -> removed;
  toggleWatchLater on favorite PLAN -> NONE kept; JSON round trip with `"fav"`; old file
  without `"fav"` loads false; unknown status falls back to PLAN.
- `CloudAlertsPlanTest`: NONE with notify false is not watched.
- `MarksTest`: `marks` updates after save/remove.

### 5.2 Device tests

- `PosterActionsDeviceTest` (api33-lowram, api36): long press `online_poster` -> sheet ->
  Favorite -> mark exists (testTag `poster_mark`) -> My List Favorites chip shows the title.
- `PosterActionsTvDeviceTest` (tv-api24, tv-api34): focus poster, send KEYCODE_MENU ->
  panel focused on "Watch Later" -> Center -> focus back on the same poster; repeat with a
  long KEYCODE_DPAD_CENTER.
- Extend `SheetFocusTvDeviceTest`: the panel's first key lands on an action.

### 5.3 Real device only

- Long-press feel and haptic on cheap phones.
- Real remotes (Mi Box, Fire TV, Chromecast) for Menu and long OK.

## 6. Implementation plan

1. Model: `favorite`, `Status.NONE`, toggles, `marks`, tests. (~120 lines)
2. Alerts rule for NONE + test. (~15)
3. `PosterCard` `onLongClick` + Menu key + `poster_mark`. (~60)
4. `PosterActionsSheet` (phone) and TV panel; `LocalPosterActions` in screens. (~150)
5. Hero heart, Detail heart, status menu "No status". (~50)
6. My List chips + card menu Favorite. (~50)
7. Home Favorites shelf. (~40)
8. Device tests phone + TV. (~220)

About 700 lines with tests.
