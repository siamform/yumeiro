# Group 14: owner bug list of 2026-11-18 (NBUG-11 … NBUG-16)

Why this group exists: the owner reported six bugs and one redesign on 2026-11-18, during the G9 TV
regression. Owner order of 2026-11-19: close G9, then this list with the full method (research,
analysis, side effects, reverse engineering of competitors, architecture and system design, tests that
fail on the old code, fixes, pass-after, regression), then G10 and G11.

Rules: the same as every group (master prompt §13a, ten steps). NBUG-15 (a)–(c) are a redesign: a
ui-ux-pro-max proposal goes to the owner first and nothing is built before the owner approves it;
NBUG-15 (d) is a logic bug and follows the normal method.

Order (user risk first): NBUG-11 (18+ shown with 18+ off: a privacy leak) → NBUG-12/13 (adult
streams: "Access denied", VPN needed; one network investigation) → NBUG-16 (updates never run, false
"update" flags) → NBUG-14 (IPTV logos) → NBUG-15 (d) → NBUG-15 (a)–(c) proposal.

## NBUG-11: 18+ off, adult titles still in Home → "Most played"

### Files and lines read

- `data/PlayCounts.kt` (all): the play-count store behind Home's "Most played" videos.
- `ui/screens/home/HomeScreen.kt` 186–193 (`Played`), 290–330 (state, the Most played filter),
  624–660 (the row), 1189–1200 (`SquareCard`, test tags).
- `online/AdultContent.kt` (all), `online/OnlineSettings.kt` 20–72 (the two switches).
- `online/OnlineHistory.kt` 140–200 (Continue watching: `adult` stored per entry, `forFeed`, `clearAdult`).
- `online/OnlineSession.kt` 200–220 (where a play is counted).
- `online/ExtensionManager.kt` 70–90, 205–255 (`ready`, `generation`, `providers()`, `provider()`,
  `extensionOf()`).
- `ui/screens/home/HomeOnline.kt` 70–110, `ui/screens/online/OnlineHomeScreen.kt` 185–200, 260–270,
  `OnlineHomePicks.kt` 45–70, `OnlineSearchScreen.kt` 100–145, `ui/screens/library/VideoLibraryScreen.kt`
  115–120, `MTPlayerApp.kt` 660–666, `online/OnlineRepository.kt` 500–515: every other place that
  filters 18+, to compare.

### Data flow

Play an online title → `OnlineSession` 214 `PlayCounts.record(kind = ONLINE, id = "api|url", …)`;
no type, no 18+ mark → prefs `play_counts` → Home `PlayCounts.entries` → `played` (HomeScreen 321)
drops an ONLINE entry only if `AdultContent.isAdultApi(entry.api)` is true right now →
`isAdultApi` asks `ExtensionManager.provider(api)`, which is null until the extensions have loaded
(`ready = false`), and always null for an extension that is turned off or removed → false.

### Action → reaction

| Action | Expected | Code | Result |
|---|---|---|---|
| 18+ off, cold start, open Home | adult titles absent | `played` is computed once at first composition, before `ExtensionManager.ready`; its `remember` keys (`mostPlayed, videoPlays, recentVideos, adultOn, adultPrivate`) do not include the extension state, so it is never computed again when the extensions load | **shown: NB11-1** |
| 18+ off, the adult extension turned off or removed | absent | `provider(api)` null → `isAdultApi` false | **shown: NB11-1** |
| 18+ off, adult title from a mixed extension (Movie + NSFW types, item `TvType.NSFW`) | absent | PlayCounts keeps no type or mark; `isAdultApi` is false for a mixed extension | **shown: NB11-2** |
| Same title in Continue watching | absent | `OnlineHistory.Entry.adult` is stored at record time (`isAdult(api, type)`), `forFeed` filters it | OK (the model to follow) |
| 18+ on and not private | shown | filter off | OK |
| Toggling 18+ while Home is open | row updates | `adultOn` is a key | OK (why the owner may see it come and go) |
| Feed rows, Yumix home, picks | adult extensions absent | `remember(engine.generation, engine.ready, …)` | OK (keys include the extension state) |

### Competitors (reverse engineering, read 2026-11-19)

- CloudStream: 18+ extensions are hidden by the "Preferred media → NSFW" choice and NSFW items in
  mixed extensions by "Enable NSFW on supported extensions" ([recloudstream/cloudstream#1149](https://github.com/recloudstream/cloudstream/issues/1149));
  its home skips pinned NSFW-only providers when NSFW is not chosen
  ([HomeFragment.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt)).
  Its filter is per provider and per type, decided from loaded providers.
- Porn4fans, the owner's example, is an NSFW-only extension (CXXX list,
  [admknight/CloudstreamExtensions](https://github.com/admknight/CloudstreamExtensions/blob/master/README.md)),
  so `isAdultApi` is true for it once it is loaded: the owner's case is NB11-1 (timing), not NB11-2.
- Lesson: decide at record time and store the answer (as Yumeiro's own Continue watching does);
  a later check against loaded extensions is only a fallback.

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB11-1 | 18+ titles in Most played after a cold start, and for an extension that is off or removed | Most played asked the live extension list, once, before it loaded; unknown counted as "not 18+" | High (privacy: shown to anyone who opens Home) | Open |
| NB11-2 | 18+ titles of a mixed extension in Most played | PlayCounts stores no 18+ mark or type | High | Open |
| NB11-OB1 | Observation, other surface: with 18+ on but private, an NSFW-typed title of a mixed extension can still appear in that extension's feed rows (`OnlineRepository.toItem` 508 drops NSFW items only when 18+ is off; the private rule hides whole 18+ extensions) | the private rule is per extension, not per title | Medium (needs the owner's reading of "private") | Queued (UNVERIFIED-QUEUE NB11-OB1); not part of the owner's report, not built |

### Design (ADR-023)

See docs/SYSTEM-DESIGN.md ADR-023.

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB11-1, NB11-2 | pending (`Nbug11MostPlayedAdultDeviceTest` case a; case b is the 18+-on control and must pass on both) | pending | emulator, TV emulator |
