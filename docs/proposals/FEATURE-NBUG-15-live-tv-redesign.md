# NBUG-15 (a)–(c): Live TV page redesign (proposal, ADR-028)

Owner spec, 2026-11-18 (verbatim summary): (1) a Yumix-style filter button with "Show videos from", search
and pins; (2) no M3U8 file names or "View" buttons, channels grouped by category across all installed
playlists, 50 channels first and more on scroll; (3) one "Tools / Menu" header button opening a modal with
every tool, including "Playlists", which opens a redesigned playlist management page. Part (d), separate
recents, is built and verified (NB15D-1, NB15D-2; the "Recently played" row stays at the top of the feed).

## What the page is today (read 2026-11-19, head 5008687)

`ui/screens/stream/StreamScreen.kt` 583–650 and `LiveTvHub.kt`:

1. Header "Live TV · N channels" with a "Play link" action.
2. Recently played row (NB15D-2).
3. Stories strip `LivePlaylistStrip`: "All" + one avatar per enabled playlist (`live_strip_*`).
4. "All": per playlist a section header with the playlist name and "View" (`LiveSectionHeader`) and a
   16-tile shelf with "+N more"; one playlist selected: its group chips (top 24 groups) and a paged grid
   (48 per page, `LIVE_PAGE`), then "Search, edit and save on the playlist page".
5. Tools section (8 tiles: Play link, M3U file, Market, Websites, Add URL, New list, Sync, Recent).
6. Playlists section: the opened temporary list, one `PlaylistCard` per playlist (on, then "Disabled").

## Research

- ui-ux-pro-max (cloned 2026-11-19, `.claude/skills/ui-ux-pro-max`): `--design-system "streaming tv channel
  guide dark"` → Dark Mode (OLED) style, entertainment; the app keeps its own Yumeiro theme tokens (the skill
  says to use semantic tokens, not new hex values). Rules applied: chip collections must wrap or offer "+n"
  (no clipped rows, ux "Chip Collection Reflow"); interactive chips are buttons with a selected state; focus
  visible inside modals; "No results" with a suggestion and a clear action; stable keys in lazy lists and
  LazyColumn over scrolling Column (jetpack-compose stack); touch targets ≥ 48 dp; no emoji icons.
  `--stack jetpack-compose` had no match for "modal bottom sheet" or "focus tv dpad": TV rules below come from
  the app's own verified TV work (G9-8 full-height sheets, BUG-5 focus), not from the database.
- TiviMate (guides read 2026-11-19): groups from every playlist sit in one list — Favorites, then All
  channels, then provider groups; Recent channels; multiple playlists merged into one channel list; groups
  can be hidden; playlists are managed in Settings → Playlists (enable, name, update, update interval, delete,
  manage groups) ([TiviMate guide](https://studylib.net/doc/28438891/tivimate-users-guide-4-7-0),
  [daily-use guide](https://store.grapevine.is/blogs/news/tivimate-daily-use-tips)).
- Kodi PVR IPTV Simple: groups come from `group-title`; a channel can list several groups separated by
  semicolons ([pvr.iptvsimple README](https://github.com/kodi-pvr/pvr.iptvsimple)). iptv-org data (NB14-3)
  uses the same `A;B` form.
- Yumeiro's Yumix filter (`ui/screens/online/YumixFilter.kt`): `SourceFilterSheet` (radio rows, search box,
  pins first, "Clear"), `PinnedExtensions` (≤ 8 pins, one-tap chips beside the filter). Verified on phones
  and TVs (G4 Y5, G9-8). Reused, not copied.

## Design

### Header (parts 1 and 3)

`Live TV · N channels` on the left; on the right two labelled buttons (≥ 48 dp, icon + text, D-pad
focusable): **Filter** (shows the chosen playlist's name, or "All", and a dot when a filter is on) and
**Tools**. The old "Play link" header action moves into Tools (the spec puts every secondary tool there).
Pinned playlists show as one-tap chips under the header, as in Yumix.

### Filter sheet (part 1)

`SourceFilterSheet`, generalised by parameters (title, subtitle, "All" row text, tag prefix, a label for
each id) so Yumix keeps its exact texts and tags. Rows: "All playlists" + one row per enabled playlist,
pinned first; subtitle per row "N channels" / "Loading…" / "Didn't load. Retry from Playlists". Search box
(accent and case blind, same matcher). Pin toggle per row (≤ 8, new store `PinnedPlaylists`, ids not names,
because two playlists can share a name). Single choice, like Yumix. TV: the sheet opens full height (G9-8).

### Feed (part 2)

- One merged channel list: every enabled playlist, pinned first, then the user's order. The same stream URL
  in two playlists shows once (the first copy wins). No playlist names, no "View", no "+N more".
- Categories: `group-title`, trimmed, case-insensitive merge, first part of an `A;B` list; empty → "Other".
  Order: most channels first, "Other" last.
- A chip row of categories ("All · N", then each category with its count) filters the feed. It scrolls
  sideways like Yumix's category bar (nothing is clipped; a wrapping block of 100+ provider groups would push
  the channels off the first screen).
- "All": category sections (title + count) with a channel grid under each. Paging is over the whole feed:
  the first 50 channels, then 50 more each time the list nears its end (`LIVE_PAGE` 48 → 50). "Near the
  end" is read from the keys of the rows on screen (the footer, or a row in the last two), not from the item
  count, so a layout from before the list grew cannot page twice. The header carries the state for screen
  readers ("Showing 50 of 120 channels"), and a footer says the same. A chosen category shows only its channels, same paging.
- Building the list runs off the main thread (`Dispatchers.Default`), keyed by the playlist load stamps; the
  grid gets an immutable list. Large lists (LargePlaylistMemoryDeviceTest, 20 000 channels) stay under the
  same memory budget because tiles are lazy and the list holds the existing `StreamRequest` objects.
- Playing a tile hands the player that channel's own playlist (channel up / down and "Sources for this
  channel" keep working, as `playLive` does today). States: loading skeletons, per-playlist errors as one
  line "N playlists didn't load · Retry", empty states with an action (Market, Add URL).

### Tools modal (part 3)

`Tools` opens a modal sheet (full height on TV) with a grid of: Playlists, Play link, M3U file, Market,
Websites, Add URL, New list, Update all, Recent links (Play Link recents). Each tile keeps today's action and
test tag. The Tools section and the Playlists section leave the feed.

### Playlists page (part 3)

A new page (`stream/PlaylistsScreen.kt`, opened from Tools → Playlists; Back returns to Live TV):

- Top actions: Add URL, New list, M3U file, Market, Update all.
- Search box from 6 playlists.
- Sections "On" and "Off"; the opened temporary list on top when there is one.
- Row: avatar, name, source (Market / Link / File / Your list), channel count, last updated, an
  "Update available" badge (market), pin, an on/off switch; tap opens the existing playlist page; the "⋮"
  menu keeps today's card actions (Refresh now, Edit, Export, Delete with confirm).
- Built from the existing `PlaylistCard` (same tags, actions and dialogs, no behaviour change), under the
  existing playlists header (Add URL, New, Sync latest, ⋮ Market / Restore defaults). It is a pane of the
  Live TV screen (like the Play Link page), so Back returns to Live TV.

## Side effects and migration

- Device tests that use the old hub tags (`live_strip_*`, `live_section_header`, `live_shelf`,
  `live_tools`, playlist cards on the Live TV page) are moved to the new tags in the same commit as the
  feature, with the reason in the commit; none is weakened. `Nbug15RecentsDeviceTest` and
  `Nbug14ChannelLogoDeviceTest` already work on any layout that shows `live_channel` tiles.
- `PlaylistDetailScreen` (search, edit, save) is unchanged.
- Rollback: one revert of the feature commits; stores added (`PinnedPlaylists`) are independent prefs.

## Tests (each must fail on the old code)

Unit: `LiveFeedTest` (merge + dedupe, category normalisation and order, `A;B`, paging window 50/100),
`PinnedPlaylistsTest`. Device (phones + both TVs): `Nbug15RedesignDeviceTest` — (a) Filter sheet lists
playlists, search narrows, pin puts a playlist first, choosing filters the feed; (b) no playlist name and no
"View" on the feed, category sections from two playlists merged, 50 tiles then more after scrolling; (c)
Tools opens a modal with every tool, Playlists opens the management page, the on/off switch works.

## Not built (would be new features; owner decision)

Channel search on the feed (the playlist page has it), hiding categories, favourites group, playlist
reordering by drag.
