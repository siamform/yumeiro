# F-09 Watch Later + Favorite on posters (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head 86a2f8d)

- `online/OnlineBookmarks.kt`: "My List", stored in `filesDir/online_bookmarks.json` (in backups,
  `backup/Backup.kt:186`). `Status` = Watching, Plan to watch, On hold, Completed, Dropped (line 26).
- `ui/screens/online/OnlineDetailScreen.kt:415` `MyListButton`: add/remove and status menu, **only on the
  detail page**.
- `ui/screens/online/OnlineComponents.kt:155` `PosterCard(item, onClick, …)`: one click target, no long press,
  no saved marker.
- Home quick card "My List" and `NavPrefs.Shortcut.MY_LIST` open `Screen.OnlineLibrary`.
- Local media has its own `isFavorite` (`data/model/MediaModels.kt:38, 88`), unrelated to online titles.

So "Watch Later" already exists as status Plan to watch, and "Favorite" does not exist for online titles.
The request is mainly about reaching both from a poster without opening the detail page.

## 2. Competitor (ideas and contracts only; no code copied)

CloudStream `recloudstream/cloudstream` at 2dcc25c3945c61dfa95d1710ced7c74acfe84baa:
`app/src/main/java/com/lagradost/cloudstream3/ui/WatchType.kt` has the same five list states plus NONE;
favourites are a separate flag stored in `utils/DataStoreHelper.kt` and toggled from
`ui/result/ResultViewModel2.kt`; home rows show a long-press menu on a poster.

## 3. Design (least risk, Yumeiro's own look)

- Reuse `OnlineBookmarks`: Watch Later = `Status.PLAN` (no second list, no data migration). Favorite = a new
  optional `favorite: Boolean` field on `Bookmark` (JSON default false, old files load unchanged; backup
  format version unchanged because the field is additive).
- Poster: long press (phone) / Menu or long Center (TV) opens a small sheet: "Watch Later", "Favorite",
  "Open". A tiny corner mark (bookmark / heart glyph in the Yumeiro accent) on saved posters.
- My List screen: filter chips "Favorites" and "Watch Later" next to the existing status filter.
- Reads from the in-memory `StateFlow` already used by `MyListButton`; writes stay off main as today.

## 4. Action–reaction

| Action | Reaction |
|---|---|
| Long press a poster → Watch Later | Added with status Plan to watch; corner mark; toast |
| Long press a saved poster → Watch Later again | Removed (toggle); mark gone |
| Favorite | Added (if missing) and flagged; heart mark |
| TV: Menu key on a focused poster | Same sheet, D-pad navigable, focus returns to the poster |
| Restore a backup from before F-09 | Loads; no favorites, statuses kept |

## 5. Pros and cons

- Pros: no new store, additive JSON field, works on every poster row (search, Home, YumeMix).
- Cons: long press can clash with row drag on TV (use Menu key there); one more sheet to keep accessible.

## 6. Tests (when approved)

- Unit: `Bookmark` JSON round trip with and without `favorite`; toggle logic.
- Device: long press → sheet → Watch Later → mark visible; TV Menu key path; focus returns (fail-before: no
  sheet).

## 7. Owner decision

Confirm Watch Later = Plan to watch (one list) and the long-press / Menu-key trigger.
