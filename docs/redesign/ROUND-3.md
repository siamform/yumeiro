# Round 3: UX, interaction and visual redesign (report)

Branch **`redesign/v3-experience`** in the separate private repository
[`siamform/yumeiro-redesign`](https://github.com/siamform/yumeiro-redesign/tree/redesign/v3-experience).
It starts from round 2 (`7011396`). The original `siamform/yumeiro` `main` (`af8349e`) was not touched.
**Status: waiting for the owner's approval. Not merged. No PR open.**

Companion documents:
- [V3-SCOPE.md](V3-SCOPE.md): screen-by-screen audit and states matrix
- [RESEARCH-V3.md](RESEARCH-V3.md): research with links
- [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) and [NAVIGATION.md](NAVIGATION.md): updated for round 3
- [HANDOVER-REDESIGN.md](HANDOVER-REDESIGN.md) §R3: for the next developer

Screenshots are in [screenshots/v3/](screenshots/v3/). They were rendered from the real app code with Robolectric, not mocked.

---

## A. Implementation: what was redesigned

### 1. Identity, used with restraint
- **Colours: unchanged.** Dark accent #F5C46B and light accent #8A5A00. Manrope and Unbounded stay.
- The "◆ 夢色 YUMEIRO" overline and the greeting now appear **on Home only**. Other screens show a plain title.
- No new Japanese labels were added. 夢色 appears only in that one signature line.

### 2. Quick Setup and the Explore tour (new)
- **Welcome.** On first launch the user sees the brief's exact text: "New to Yumeiro? Explore and discover what you can do here. Already familiar with Yumeiro? You can skip and start using the app."
  - It has two equal buttons, **Explore Yumeiro** and **Skip for now**, plus a link to the quick-setup checklist.
  - The checklist is the round 2 guide's 8 real steps, with live ticks.
- **Explore tour.** It runs on the **real interface**, not on pictures:
  - A dim scrim with a cut-out spotlight and a gold ring, plus a pulse that is off under reduced motion.
  - The highlighted control stays **live**: the user taps the real Music tab, opens the real source menu, and so on.
  - 9 stops: Home → Music tab → music source menu → Videos tab → video source menu → extensions/sources → Library → alerts & settings → "You're ready".
  - Every feature mentioned exists in the app.
- **Remembered.** The choice is stored as `Onboarding.EXPLORED` or `SKIPPED` (prefs `onboarding`).
- **Reopen it** from Settings → Setup guide ("Explore Yumeiro again, or open the quick-setup checklist") or from "Take the 1-minute tour" on the Home setup card.

### 3. Home
- **Context line.** One short sentence built from real data, such as "2 new episodes in your list", "You're offline — your downloads still play", or what's playing.
- **New episodes carousel.** See §6 below.
- **Your spaces.** The 12 shortcuts are **all kept**, with their `quick_card_*` IDs. They are grouped into three foldable spaces:
  - Watch: Yumix, YouTube, Live TV, Device videos.
  - Listen: Music, YouTube Music, Mix, Audio engine.
  - Read & keep: Manga, Library, Downloads, Get sources.
  - Listen moves first while music is loaded.
- **Folding rows.** Every content row (Moods, New arrivals, Most played, Watched recently, Random, YouTube) has a fold header and "See all" where a destination exists. Folded state is remembered (prefs `home_layout`).
- **The signature intro stays.**

### 4. Music source selector
- The large round 2 mode cards are gone. The **title is the selector**: "Music · On device ▾".
- Tapping it opens `SourceMenu` with "Listen from": On device (live song count), Online (YouTube Music) and Mix.
- The menu has a check mark on the current source and staggered row entry. Back or a scrim tap closes it.
- All device tabs, sort, selection, permission handling and the online/mix pages are unchanged.

### 5. Videos source selector
- The same title menu ("Watch from") covers Yumix (extension count), YouTube, Device (video count) and Live TV.
- Each source keeps its own real layout. They are not forced into one template.

### 6. New episodes carousel
- **Data comes only from the user's own My List** (bookmarks) plus the real new-episode alerts that `NewEpisodesWorker` writes to the Inbox.
- **Each card shows:**
  - the series' own cover (from the extension, via `PosterImage`)
  - title and episode line
  - status ("New" / Watching / Planned …)
  - source name
  - the date **the new episode was found**
- **Honest limit:** extensions do not provide release dates. The card says "Found 3 Nov" rather than inventing a release date.
- **Layout:** a hero pager with a peek of the next card and page dots. Tapping opens the series' existing detail page. "Watch now" / "Episodes" lead to the same place.
- **Empty states:**
  - "Add a Yumix source first" when no extension is installed.
  - "Follow a series" when nothing is followed yet.
  - Both have a working action button.

### 7. States
- **Offline.** `OfflineNotice` appears on Music Online, Yumix, YouTube, Live TV and Manga.
  - It says which part needs the internet.
  - It offers working exits: Device music, Device videos, Downloads and Library.
  - Device sources never show it.
  - Home's context line and Moods also say what still works offline.
- **Missing configuration.** These existing states are kept: Yumix "Get started", "No manga sources yet" and the Live TV market link. The Home carousel adds its own setup-required state.
- **Empty.** Manga's bare "Nothing here." became a real empty state with guidance.
- **Kept:** the existing loading, permission (music permission card) and error/retry states. See the matrix in V3-SCOPE.md.

### 8. Motion
- `YuMotion` tokens follow M3: emphasized easing; 150/300/400/200ms.
- Tab changes slide by a third and fade. The menu expands and fades. The title cross-fades and the chevron rotates.
- The carousel scales and fades. Fold arrows rotate. The tour spotlight pulses.
- **Reduced motion:** with the system "Remove animations" setting on (`ANIMATOR_DURATION_SCALE` = 0), everything becomes a snap or a fade.

### 9. Code footprint
- 18 files, about 1,690 lines added and 255 removed (`7011396..50d1c55`), plus these docs.
- New files:
  - `Motion.kt`
  - `SourceSelector.kt`
  - `OfflineNotice.kt`
  - `HomeSections.kt`
  - `ExploreTour.kt`
  - `EpisodeCarouselTest.kt`
- No new dependencies and no architectural changes. State lives in small `SharedPreferences` objects, the same way as the existing `SetupGuide`.

---

## B. Screenshots (real, from the implemented app)

| Sheet | Shows |
|---|---|
| [v3/01_quick_setup.jpg](screenshots/v3/01_quick_setup.jpg) | Welcome (dark, light, small screen), checklist, Settings → Setup guide |
| [v3/02a_explore_tour.jpg](screenshots/v3/02a_explore_tour.jpg), [v3/02b_explore_tour.jpg](screenshots/v3/02b_explore_tour.jpg) | All 10 tour frames on the live interface (including the menu opened by the user) |
| [v3/03_home.jpg](screenshots/v3/03_home.jpg) | Home top to bottom, folded, and light |
| [v3/04_selectors.jpg](screenshots/v3/04_selectors.jpg) | Music and Videos source menus (dark/light) and each mode |
| [v3/05_states.jpg](screenshots/v3/05_states.jpg) | Offline notices, episodes empty state, Yumix get-started, manga |
| [v3/06_carousel.jpg](screenshots/v3/06_carousel.jpg) | The carousel with **test fixture data**. This is a layout check only: the screenshot device has no real My List |
| [v3/07_features.jpg](screenshots/v3/07_features.jpg) | Now playing, library, downloads, Live TV, sources hub, equalizer, search, inbox |
| [v3/08_settings.jpg](screenshots/v3/08_settings.jpg) | Settings categories |
| [v3/09_accessibility.jpg](screenshots/v3/09_accessibility.jpg) | Small screen and large text |
| [v3/10_before_after.jpg](screenshots/v3/10_before_after.jpg) | Original app vs round 3 (Home, Music, Videos) |

Why the screenshots show sample media: the test device has no internet, extensions or real files. Content rows show what the tests create (sample local tracks and videos). Nothing was faked to make screens look busy. Where a section has no data it shows its real empty state.

---

## C. Before / after: UX problems and fixes

| Problem (before) | Why it hurt | Round 3 change |
|---|---|---|
| New users landed in a full app with no explanation; the round 2 guide was a static checklist | 800+ users; people did not know where music sources, extensions or Live TV were | Welcome with Explore / Skip; a live tour that makes the user tap the real controls; replay from Settings and Home |
| Music and Videos opened with big mode cards taking ⅓ of the screen | Content pushed down; the switch looked like content | Title-as-selector: one line, clear current source, menu on demand |
| Home was a long dashboard of 12 tiles plus many rows, all always open | Felt like a control panel, not personal | Context line + new episodes first; tiles grouped into 3 named spaces; every row foldable and remembered |
| Next episodes were a plain row with no status or date | Hard to see what is new | Hero carousel with cover, episode, status, source and the real found date |
| Branding overline repeated on every tab | Noise, and many Japanese-looking marks | Signature only on Home |
| Offline showed empty or failing online pages | Looked broken; users didn't know what still works | OfflineNotice with working exits; device sources untouched |
| Animations were inconsistent and ignored the system setting | Motion sickness and accessibility risk | One token set; reduced motion respected everywhere new |
| Manga "Nothing here." | No guidance | A real empty state |

---

## D. Design documentation
- Principles, colour/type, components and motion: [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) ("Round 3 changes").
- Navigation, spaces map, source menus, tour stops and setup flow: [NAVIGATION.md](NAVIGATION.md) ("Round 3").
- Screen-by-screen changes and what was preserved: [V3-SCOPE.md](V3-SCOPE.md).
- Research: [RESEARCH-V3.md](RESEARCH-V3.md).

### Preservation
- **No feature, route, setting or shortcut was removed.**
- The old `MusicSourceSwitch` and `VideoSourceSwitch` composables still exist; tests use them.
- `Screen.Stream` and every deep link remain.
- Every existing test still runs. Navigation crash tests now open the source menu before choosing a mode.

---

## E. Final report

### What works (verified by executed tests)
- The full unit and Robolectric suite: **412 tests in 72 classes, 0 failures, 0 skipped** (run locally on 11 Nov 2026, `./gradlew :app:testDebugUnitTest`).
- `RedesignTourTest` walks the whole app in 4 configurations (dark, light, small screen, large text) and saves screenshots. It covers:
  - the welcome choice being remembered (SKIPPED/EXPLORED)
  - the full Explore tour, tapping the real tabs and menus
  - both source menus
  - Home folding
  - offline notices
- `EpisodeCarouselTest` (3 tests): shows the real found date and never invents one; the empty state's action works; swiping changes the page.
- `AppNavigationCrashTest` opens every screen through the new menus without crashing.

### Not redesigned in round 3 (audited only; design from rounds 1–2)
- Now Playing and the video player.
- Search, Library, Downloads and playlists.
- Settings categories, Equalizer, Watch together, tracking, backup/restore and source health.
- Dialogs and sheets other than the new source menu.
- These all work and use the shared kit and colours, but did not get a round 3 rework. This is the next step if the direction is approved.

### Limitations, stated honestly
- **No physical device, emulator or TalkBack testing.** Everything ran on Robolectric (JVM). Real-device checks of gestures, performance and screen readers are still needed.
- **No release dates.** Extensions do not expose them; the carousel shows when Yumeiro found the episode.
- **The tour has 9 stops.** The brief asked for six areas. Research suggests 3–4 stops, so each card is very short and Skip is on every stop.
- **The light-theme tour scrim** is fairly light; it may need more contrast after device review.
- **Carousel screenshots use fixture data**, labelled as such. With a real My List the same code shows real covers.
- **Performance** was not profiled on hardware.
- GitHub Actions are disabled on the redesign repo, so no CI run was made. All tests ran locally.

### Next step
**Waiting for your explicit approval.** Nothing has been merged, no PR has been opened, and the original repository's `main` was not touched.
