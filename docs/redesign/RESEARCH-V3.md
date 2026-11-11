# Round 3 research: what was studied, and what Yumeiro took from it

Research was done per feature area before any code was written. Sources are guidelines,
usability studies and the public documentation or reporting of other players. **Nothing was copied.**
For each source, the table says what was learned and how Yumeiro applies it in its own way. The
round 1 and 2 references (Material 3 Expressive, NN/g progressive disclosure, recognition over recall,
Mihon, Stremio and CloudStream docs) are in [RESEARCH.md](RESEARCH.md) and [ROUND-2.md](ROUND-2.md) §B.

## Quick Setup and the Explore tour

| Source | What it says | What Yumeiro does |
|---|---|---|
| [Material Design: Feature discovery](https://m1.material.io/growth-communications/feature-discovery.html) | Highlight one real UI element at a time, with a short explanation, and let the user tap the element itself | Each tour stop cuts a hole in a dim scrim over a **live** control (top-bar title, a tab, the extension buttons). The hole has no blocker, so the tap reaches the real control |
| [NN/g: Instructional overlays](https://www.nngroup.com/articles/mobile-instructional-overlay/) | Overlays that cover the whole screen with arrows are hard to remember and are often dismissed | One element per stop, never a full-screen cheat sheet. The tooltip sits next to the element, above or below it |
| [NN/g: Mobile-app onboarding](https://www.nngroup.com/articles/mobile-app-onboarding/) and [NN/g: Onboarding tutorials vs contextual help](https://www.nngroup.com/articles/onboarding-tutorials/) | Onboarding should be optional and easy to skip, and help should be easy to find again | Two equal choices on the welcome screen: **Explore Yumeiro** or **Skip for now**. Skip is on every stop. Replay from Settings → Setup guide or from the Home setup card |
| [NN/g: Mobile tutorials](https://www.nngroup.com/articles/mobile-tutorials/) | Tutorials that only show screens do not improve task success. Doing the task helps | Stops ask the user to **do** the action ("Tap Music", "Tap the title… Try it now"). The tour moves on when the user does it |
| [UI Patterns: Coachmarks](https://ui-patterns.com/patterns/coachmarks), [Chameleon: Coachmarks](https://www.chameleon.io/patterns/coachmarks), [Livefront: Put me in, coach marks](https://livefront.com/writing/put-me-in-coach-marks/) | Keep coach marks few and short (Chameleon suggests 3–4); show progress; never block the user | The brief listed six areas, so the tour has 9 stops. Each card is one sentence or two, with a "3 of 9" counter and dots. Any stop can end the tour. This is longer than Chameleon's advice; see the limitations in ROUND-3 §E |

## Home and the New episodes carousel

| Source | What it says | What Yumeiro does |
|---|---|---|
| [Material 3: Carousel guidelines](https://m3.material.io/components/carousel/guidelines) | A "hero" layout shows one large item and a peek of the next one; offer a "Show all" action | `EpisodeCarousel` is a hero pager: one large card plus a 44dp peek of the next, with a "See all" link that opens Yumix |
| [Material 3: Carousel specs](https://m3.material.io/components/carousel/specs) | 28dp item corners and 16dp padding | 28dp card corners, 16dp page margin, 10dp between pages |
| [Netflix Help: Reminders](https://help.netflix.com/en/node/101522) | Users ask to be reminded and see the reminders in one place | New episodes come from the user's own My List and the real new-episode alerts (`NewEpisodesWorker`); nothing is suggested from outside |
| [Jellyfin discussion: Next Up / Continue Watching](https://github.com/jellyfin/jellyfin-web/discussions/8079) | People expect "next up" to be separate from "continue" | Continue (resume where you stopped) and New episodes (series with new releases) stay two separate sections |
| [NN/g: Progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/) | Show the important parts first, details on request | Home groups the 12 shortcuts into three **spaces** (Watch, Listen, Read & keep). Each space and each row folds, and the choice is remembered |

## Music and Videos source selectors

| Source | What it says | What Yumeiro does |
|---|---|---|
| [Android Central: YouTube Music library redesign](https://www.androidcentral.com/apps-software/youtube-music-library-tab-redesign), [9to5Google](https://9to5google.com/2023/01/10/youtube-music-library-redesign-final/), [YouTube Music Help](https://support.google.com/youtubemusic/answer/6313542?hl=en) | Library content is switched from a menu next to the title instead of permanent tabs | The **page title is the selector**: "Music · On device ▾" and "Videos · Yumix ▾". Tapping it opens Yumeiro's own menu panel |
| [Apple Support: Downloaded music filter](https://support.apple.com/en-mk/guide/iphone/iphbddea0e5e/15.0/ios/15.0) | A filter of "what is on this device" is one choice among sources | "On device" is one of the menu options with its live song count |
| [Material 3: Menus accessibility](https://m3.material.io/components/menus/accessibility), [Menus guidelines](https://m3.material.io/components/menus/guidelines) | Show the selected item with a checkmark, not colour alone; 48dp rows | The selected row has an accent tint **and** a check icon. Rows are radio buttons for TalkBack, at least 64dp tall |
| [StremioK user guide](https://github.com/Kepler75/StremioK/blob/main/docs/USER-GUIDE.md) | Type and catalog pickers sit at the top of browse screens | Videos keeps its four real sources (Yumix, YouTube, Device, Live TV) in one picker; each source keeps its own page and search |
| [Plex: Customizing the apps](https://support.plex.tv/articles/customizing-the-apps/), [Plex forum: new mobile experience](https://forums.plex.tv/t/the-new-experience-is-coming-to-mobile-what-to-expect/909623) | Users want to choose and reorder what Home shows; the 2024 redesign showed that taking control away causes backlash | Home sections fold instead of disappearing, and nothing is removed |
| [VLC redesign wiki: Library](https://github.com/KuchiSofts/vlc-redesign/wiki/Library) | Local media libraries need clear "this device" grouping | Device music and device videos stay first-class choices in both menus and in the offline notice |

## Motion

| Source | What it says | What Yumeiro does |
|---|---|---|
| [Material 3: Easing and duration tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs) | Emphasized easing for big moves, standard for small ones; durations around 150–500ms | `YuMotion` (`ui/components/Motion.kt`): Emphasized, EmphasizedDecelerate/Accelerate and Standard curves; 150/300/400/200ms |
| [Material 3: Applying transitions](https://m3.material.io/styles/motion/transitions/applying-transitions) | With reduced motion, replace movement with fades | When reduced motion is on, tab changes only fade, menus and the tour snap, and the pulse ring and carousel scaling are off |
| [Eevis: Android animations and reduced motion](https://eevis.codes/blog/2022-12-12/android-animations-and-reduced-motion/) | Read `ANIMATOR_DURATION_SCALE`; 0 means the user turned animations off | `isReducedMotion(context)` reads that setting; `LocalReducedMotion` passes it down |

## Offline and setup-required states

| Source | What it says | What Yumeiro does |
|---|---|---|
| [Google Design: Offline design](https://design.google/library/offline-design) | Tell people what still works offline, and don't show empty or broken content | `OfflineNotice` says which feature needs the internet, then offers what still works: Device music, Device videos, Downloads and Library |
| [Material (MDUI mirror): Offline states](https://www.mdui.org/en/design/1/patterns/offline-states.html) | Keep cached content visible and mark it, rather than blanking the screen | The notice sits above the screen; history and cached lists stay visible below it |
