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

## Round 2 changes
- **Type**: display styles use Manrope ExtraBold/Bold (`Type.kt`, `DisplayStyle` 28sp). The top-bar title is plain `headlineLarge`, with no gradient, and drops to `headlineSmall` below 380dp width. Overlines are 12sp SemiBold, not upper-case.
- **Background** (`DesignSystem.kt` `pageBackground`): the glow is softer (0.09/0.06), with no sparkles and no echo.
- **Bottom bar**: 4 labelled tabs. One solid `accent` capsule (58×30dp) glides behind the active icon (`onAccent` icon, ExtraBold label). The bar shadow is 6dp and the border is a plain hairline.
- **ModeSwitcher** (`ui/components/ModeSwitcher.kt`): use it wherever a page has 2–4 sources of content. Cards are equal width and at least 76dp tall, with an icon disc, a title and a live status line. The selected card gets an accent tonal fill and a 1.5dp accent border.
- **ExtensionBanner** (`ui/screens/online/ExtensionBanner.kt`): one per extension. A poster backdrop, a horizontal ink scrim, a "Yumix source" chip and a gold action pill.
- **Home Explore tiles**: a 3-column grid (6 columns at ≥600dp), with rows of equal height and titles of up to 2 lines.
- **Language**: user-facing text is English. "Yumix" replaces "Hues". Japanese words are no longer used as labels.
- **Colours**: unchanged.

## Round 3 changes
Colours and fonts are **unchanged**. Round 3 adds interaction components and motion rules.

**Principles added**
- One clear source per page, which the user changes from the title. No permanent source cards.
- Show the important things first (context line, new episodes). Everything else folds, and folding is remembered.
- The brand signature ("夢色 · YUMEIRO" + greeting, no glyph) appears **only on Home**.
- Every new animation has a reduced-motion version.
- Never fake data. If the app doesn't know something (a release date), say what it does know (the found date).

| Component | File | Notes |
|---|---|---|
| `YuMotion` | `ui/components/Motion.kt` | M3 easings (Emphasized, EmphasizedDecelerate/Accelerate, Standard). Durations SHORT 150, MEDIUM 300, ENTER 400, EXIT 200 ms. Helpers return `snap()` when reduced. `LocalReducedMotion`, `reducedMotion()`, `isReducedMotion(context)` (reads `ANIMATOR_DURATION_SCALE`) |
| `SourceOption` / `SourceMenu` | `ui/components/SourceSelector.kt` | In-window panel under the top bar: scrim (`<tag>_scrim`) and a heading ("Listen from"/"Watch from"). Rows are at least 64dp tall, with an icon, a title, a live status and a check on the selected row. Rows are `Role.RadioButton`, enter with a 30ms stagger, and Back closes the menu |
| `SourceTitle` | `ui/components/MTTopBar.kt` | Top-bar title + rotating chevron, `Role.DropdownList`, AnimatedContent on change. Enabled by `onTitleClick` / `titleExpanded` / `titleTag` |
| `FoldHeader` | `ui/screens/home/HomeSections.kt` | Section title + optional "See all" + fold arrow (`home_fold_<key>`). State in `HomeLayout` (prefs `home_layout`) |
| `Space` / `SpaceGroup` | same | Named group of shortcut tiles, 4 columns, foldable (`home_space_<key>`, `home_space_toggle_<key>`) |
| `EpisodeCarousel` / `EpisodesEmpty` | same | M3 hero carousel: 212dp cards, 28dp corners, 44dp peek, page dots. The cover is the series' own poster at 30% (dark) or 18% (light) under an ink gradient. Gold "Watch now"/"Episodes" pill |
| `ContextLine` | same | One sentence from real state (`home_context_line`) |
| `OfflineNotice` | `ui/components/OfflineNotice.kt` | Tonal card: what needs the internet + chips for what still works (`offline_exit_*`). Exits come from `LocalOfflineExits` |
| `ExploreTourOverlay` | `ui/screens/extras/ExploreTour.kt` | Canvas scrim with a `BlendMode.Clear` hole, 2dp accent ring, pulse. Four blocker boxes around the hole keep the target live. The card is placed above or below the hole, with a step counter, dots and Back/Next/Skip |

**Motion map**
- Tabs: slide by ⅓ + fade, 300ms emphasized (fade only when reduced).
- Source menu: expand from top + fade, rows staggered.
- Title: cross-fade + slide. Chevron rotates 180°.
- Carousel: neighbouring pages scale down 6% and fade 35% per page of distance (off when reduced).
- Fold: the arrow rotates and the content expands/shrinks.
- Tour: the spotlight moves between targets and the ring pulses (off when reduced).
