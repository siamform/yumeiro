# Yumeiro redesign proposal — "Tsuki no Michi" (月の道)

## Vision
Yumeiro is a night-time media app: music, videos, Hues (extensions), YouTube, Live TV, reading.
Its identity is the warm moon-gold on ink, Japanese overlines and kana. The redesign keeps all of
that and makes the experience **calmer, faster to read and more consistent**. The guiding image
is the moon's path on water: one thin gold line that shows you where you are.

This is not a copy of any product. The research in `RESEARCH.md` informed *principles* (48dp
targets, recognition over recall, progressive disclosure, never hiding power features). No layout,
iconography or interaction is lifted from another app.

## Problems found in the audit (see before screenshots)
1. **Offline pill covered the tab title** (Home, Music and Settings "Good morning", "Music").
2. **Home was a wall of shortcuts.** Resume cards (228dp) and a 5-size bento (~600dp) pushed the For-you feed below two screens.
3. **Header actions were three separate glass circles**, which competed with the title.
4. **Mini player progress sat on the bottom edge** next to the dock, where it is hard to see. No gesture to skip. 40dp buttons.
5. **Now Playing hid Sleep and EQ** as unlabelled header icons, while Lyrics/Up next/Info were labelled pills. Two different patterns for the same kind of tool.
6. **Selected filter pill was white text on gold**, which has low contrast.
7. **Many radii** (6–36dp, 393 hard-coded shapes) and many card styles.

## Proposal (implemented)
| Area | Change |
|---|---|
| Tokens | `Yume` spacing / radius / touch / motion scale (`ui/theme/Tokens.kt`). Colours untouched |
| Kit | `MoonPath`, `MoonSectionHeader`, `YumePanel`, `YumeIconButton`, `ActionCapsule`, `MoonTag`, `MoonSheetHandle`, `moonPathMark()` |
| Shell | ActionCapsule header; OfflineTag; a 68dp dock with a gliding moon-path indicator; a mini player with top-edge progress and swipe-to-skip |
| Home | Tonight panel → Spaces (kana keys) → For you |
| Now Playing | Simplified header; a labelled five-tool rail; moon seek bar |
| Lists | Playing row = moon-path mark |
| Sheets | One sheet shape plus the moon handle for all 5 bottom sheets |
| Dialogs | TsukiDialog gets the moon stroke at the top edge (all ~50 dialogs) |
| States | StateMessage halo + stroke; SubPageHeader 48dp back; skeleton radius |
| Settings | GroupLabel moon mark, calmer IconTile, 64dp rows |

## Navigation
See `NAVIGATION.md`. The information architecture is unchanged on purpose (5 tabs + sub-pages).
Users keep their muscle memory, which is the Plex lesson. The changes are in wayfinding: the
indicator, labels, the header capsule, and Home becoming a real launcher for every space.
