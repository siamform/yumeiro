# NBUG-5 — Dub list shows the Sub count, Episode 7 on Dub plays Episode 1

Status: **Verified (emulator, api33-lowram) — signal D fix (section 5b), evidence in section 8**. Owner report 2026-11-18 (docs/NEW-BUGS-FEATURES-2026-11-18.md).
Group G3 (source metadata; F-01 uses the same path).

## 1. Report

A title has 7 Sub episodes. The Dub filter also shows 7. Tapping Episode 7 on Dub plays Episode 1 with no
message. Wanted: the Dub list holds only the episodes that really have a dub; a missing dub says so, with a way
back (Sub), and never plays a different episode.

## 2. Code read (head a48e099)

| Step | Code | What it does |
|---|---|---|
| Lists per version | `online/OnlineRepository.kt` `map()` ~389–392 | `AnimeLoadResponse.episodes` (DubStatus → list) → `"Sub"` / `"Dub"` / `"Original"`, empty lists dropped, each list copied as is |
| Disk cache | `cache/OnlineCodec.kt` `detail()` | writes and reads each version's list under its own key (order kept by org.json) |
| Detail screen | `ui/screens/online/OnlineDetailScreen.kt` 174–181, 325, 333 | `ordered = episodesOf(d, version)`; a tap sends `(version, index in ordered)` |
| Watch page | `ui/screens/online/OnlinePlayerUi.kt` ~480–483, 605–634 | `shown` comes from `state.episodes` (the playing version) and is re-remembered when it changes |
| Play | `online/OnlineSession.kt` `play()` 162, `playEpisode()` 384 | index into `episodesOf(detail, version)`; loads links for that episode's `data` |
| Sub ↔ Dub switch | `OnlineSession.switchVersion()` 401, `EpisodeMatcher` | matches by season + number, then name; never by position; a miss shows "Dub isn't available for episode N yet" and keeps playing |

Finding: no Yumeiro step changes the number of Dub episodes or swaps the episode that is loaded. If the Dub list
has 7 entries, the extension sent 7; if Dub Episode 7 plays Episode 1, the extension's `data` (or the site
behind it) answered Episode 1 for it. This is a reading, not a proof — section 4 checks it on a device.

## 3. Competitors (ideas only, nothing copied)

- **CloudStream v4.9.1** (`ui/result/ResultViewModel2.kt`): episodes are indexed by `(DubStatus, season)` and
  shown exactly as the extension sends them; no check that a Dub entry is real. Same trust model as Yumeiro.
- **Aniyomi v0.18.2.1**: no Sub/Dub split in the app; extensions expose dub as separate sources or episode
  names, so a wrong count is the extension's list. Idea: the app never invents per-version lists.
- **Hikari**: no maintained public repository found under that name (GitHub search 2026-11-18: only
  geekobueno/HikariFlix, 6 stars) → not used as a source.

Idea taken: the card the extension sends with search results already carries Sub/Dub counts
(`AnimeSearchResponse.episodes`); when the load answer disagrees, that is evidence, not a guess.

## 4. Reproduction plan (emulator first)

`DubCountProbeDeviceTest` (androidTest): installs up to eight English anime extensions from the market, searches
titles whose dub usually lags (One Piece, Frieren, Solo Leveling, Dandadan, Kaiju No. 8, Blue Lock), and for each
card with a dub logs `NBUG5_PROBE`: card counts vs raw Sub/Dub list sizes vs what Yumeiro maps; distinct `data`
among Dub entries; Dub entries reusing Sub `data`; Dub episode number range; and whether the last Dub episode's
links equal Dub Episode 1's links. It fails nothing; no reachable extension → skipped (never passed).

Run: `device-check.yml`, test `DubCountProbeDeviceTest`, phone api33-lowram, repeat 1.

## 4b. Probe results (emulator api33-lowram)

- Probe 1 and probe 2 (run 44203720877, head 962fdbf): signals A, B and C absent — Dub entries have distinct
  `data`, never reuse Sub `data`, and the last Dub episode's links differ from Dub 1's.
- **Signal D (new):** KickassAnime puts several languages in one `DubStatus.Dubbed` list, one run per language,
  marked only in the episode name ("… (Dub)", "… (Spanish)") and `?lang=`: Frieren {Dub=28, Spanish=56}, Solo
  Leveling {Spanish=24, Dub=12}, Dandadan {Dub=12, Spanish=12}, Kaiju No. 8 {Dub=12, Spanish=6}, Blue Lock
  {Dub=24, Spanish=35}; One Piece is clean {Dub=1064}. Raw order is run by run; **Yumeiro's `episodesOf` sorted
  the whole list by number** and interleaved the runs (shown: e1 Spanish, e1 Spanish, e1 Dub, e2 …).
- Anichi: card counts = list counts, distinct data, clean.

Effects of D in Yumeiro (the owner's symptoms): the header counts entries (Blue Lock Dub "59 episodes" for 24);
the grid reads 1, 1, 1, 2, 2, 2 so a tile's position is not its episode; **"Next" from Dub 1 plays Spanish 1**
(index + 1); a Sub → Dub switch can land on a Spanish copy (first exact number match).

## 5. Candidate fixes (chosen after the probe, least risk first)

| Id | Signal | Change | Risk |
|---|---|---|---|
| A | Dub entries share one `data` (or reuse Sub `data`) | keep one entry per distinct `data`; the rest are not listed | low; pure list filter, unit-testable |
| B | card says Dub = N, load sends more | list only Dub numbers ≤ N; a tap on a higher one is impossible | medium; card counts can be stale |
| C | links for Dub ep K equal Dub ep 1 | no list change; on play, a K ≠ 1 episode whose links equal ep 1's shows "No dub for episode K yet — play Sub?" | higher; needs a second link load |
| — | probe shows Yumeiro side drops/merges | fix that step instead | — |

| **D** | one list mixes language runs (repeated numbers + ≥ 2 name tags) | keep each run together, sorted on its own; run named like the version first; tile caption + count of different episodes | low; ordering only, nothing dropped; lists without the mix unchanged |

Never: substitute another episode silently, or hide Dub entirely.

## 5b. Chosen: D (A–C have no evidence)

| Option | Pros | Cons |
|---|---|---|
| D1 split into new versions ("Dub", "Dub · Spanish") | clean chips | new version keys touch history, downloads, matcher, default version; overlaps gated F-01 |
| **D2 keep runs together in one list + label** | one function (`OnlineSession.labelledEpisodes`), every caller (grid, watch page, next, switch) gets it; nothing hidden | Spanish runs still in the Dub list (labelled) |
| D3 drop non-matching languages | short list | hides real content (data loss of choice) — rejected |

Run key = (name tag, how many times this number was already seen under the tag), so two Spanish runs become
"Spanish" and "Spanish 2". Applied only when numbers repeat AND ≥ 2 different tags exist; otherwise the old
number sort is used as is. Tag = trailing "(Word…)" of the name (2–24 letters/space/.-), generic, no source name.

Competitors (ideas only): CloudStream v4.9.1 shows the list as the extension sends it (no re-sort across runs);
Aniyomi v0.18.2.1 keeps the source's order and shows names. Idea taken: do not reorder across what the source
grouped.

Not changed: `NewEpisodesWorker.episodeCount` (largest list size) — consistent between checks, so a new
Spanish episode is still a "new episode"; recorded, not in scope.

## 6. Action–reaction (target behaviour)

| Action | Reaction |
|---|---|
| Open Dub on a title whose dub lags | only dubbed episodes listed; header count matches |
| Open Dub on a list with Dub + Spanish runs | English run first (1..N), then "Spanish", "Spanish 2"; tiles captioned; header "24 episodes · 3 versions" |
| Next on Dub 3 | Dub 4 (same run), never Spanish 3 |
| Sub 3 → Dub | English Dub 3 |
| Clean list (One Piece, Anichi) | unchanged: number order, no captions, plain count |
| Tap a Dub episode that has no dub at the source | message "No dub for episode N yet" with "Play Sub" |
| Switch Sub → Dub on an episode with no dub | existing message (EpisodeMatcher), keeps playing |

## 7. Tests (to add with the fix)

Unit (fail before / pass after) on the chosen signal with a fake `AnimeLoadResponse`; device: probe re-run shows
the mapped Dub count equal to the card's count for the probed titles.

## 8. Verification

- Unit `online/MixedLanguageEpisodesTest` (Robolectric): fail-before CI 44206044268 on temp branch ab/nb5-before
  (3 of the 4 tests; the 4th needs the new API): 1144 tests, only nextEpisodeStaysInTheSameLanguage and
  runNamedLikeTheVersionComesFirst failed; the clean-list guard passed. Pass-after: CI 44211141941 green at 957e4b2.
- Device (emulator api33-lowram) 44209853612: `shown12` (from `episodesOf`) starts with the English run e1..e12
  "(Dub)" for Frieren, Solo Leveling (raw list starts with Spanish), Solo Leveling S2, Dandadan S2, Kaiju No. 8,
  Blue Lock; One Piece unchanged.
- Not verified: the owner's exact title (not named). Real hardware: not run.
