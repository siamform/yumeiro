# Tsuki no Michi — Yumeiro design system

月の道 ("the moon's path"): the strip of moonlight that lies on night water. The redesign takes
that one image as its motif: a **thin gold stroke** that marks where you are (the selected tab,
a section, progress). Gold is used on purpose, not as decoration.

## What did NOT change (non-negotiable)
- **Colours**: every palette in `ui/theme/Color.kt` is untouched. That covers Tsuki Night (accent `#F5C46B`, bright `#FFDB94`, accent2 `#D9963F`, onAccent `#1C1407`, bg `#0E0E11`), Washi Day (accent `#8A5A00`, bg `#F6F5F2`), Afterglow, Morning/Noon/Afternoon/Evening/Midnight, Dynamic and AMOLED. No hex value was added to any component; new components read `LocalMtPalette`.
- **Fonts**: Unbounded (display) and Manrope (body), from `ui/theme/Type.kt`.
- **Vibe**: the ambient halo and sparkles (`pageBackground()`), Japanese overlines (ホーム, つづき, すべて, おすすめ, 再生中) and the kana glyphs (音 歌 響 彩 店 映 動 生 棚 保).

## Tokens — `ui/theme/Tokens.kt` (`Yume`)
| Token | Value | Use |
|---|---|---|
| `space4…space32`, `gutter` 20, `sectionGap` 28 | 4dp grid | rhythm |
| `touch` | 48dp | minimum target |
| `radiusXs/Sm/Md/Lg/Xl` | 6/10/16/22/28 | badges / list art / cards & keys / dock, panels / dialogs & artwork |
| `moonStroke` | 3dp | selection and progress stroke |
| `elevationDock` 8, `elevationDialog` 14 | | the only shadows |
| `settle()`, `quick()` | springs | indicator glide, panels / presses |

## Kit — `ui/components/YumeKit.kt`
| Component | Purpose |
|---|---|
| `MoonPath` | the vertical gold stroke (section mark, overline mark) |
| `MoonSectionHeader` | the one section header: stroke, optional kana overline, title, count, "See all" (48dp) |
| `YumePanel` | the one lifted container: tonal fill and a gold hairline |
| `YumeIconButton` | flat 48dp icon button with an optional badge |
| `ActionCapsule` | groups a page's header actions into one capsule |
| `MoonTag` | small tonal status tag |
| `rememberMoonHairline()` | the hairline brush used by panels, the dock and capsules |

## Rules
1. **One focal point per screen.** One gold-filled element (the Music key on Home, play on Now Playing, the selected pill).
2. **Gold = intent.** Selection, progress, primary action. Not borders everywhere.
3. **Labels stay visible** in navigation and tool rails.
4. **Fewer nested cards.** Prefer one panel with hairline dividers (the Home "Tonight" panel holds both resume rows).
5. **Readable on gold.** Text on gold uses `onAccent` ink (this fixes the white-on-gold FilterPill).
6. **No new hues, no extra blur/glass** beyond the existing `GlassFill`.
7. **Status never covers content.** Offline is now a tag in the tab header, or a bottom pill on sub-pages.
