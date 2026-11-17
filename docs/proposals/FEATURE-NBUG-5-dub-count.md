# NBUG-5 — Dub list shows the Sub count, Episode 7 on Dub plays Episode 1

Status: **In progress — investigation (no fix yet)**. Owner report 2026-11-18 (docs/NEW-BUGS-FEATURES-2026-11-18.md).
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

## 5. Candidate fixes (chosen after the probe, least risk first)

| Id | Signal | Change | Risk |
|---|---|---|---|
| A | Dub entries share one `data` (or reuse Sub `data`) | keep one entry per distinct `data`; the rest are not listed | low; pure list filter, unit-testable |
| B | card says Dub = N, load sends more | list only Dub numbers ≤ N; a tap on a higher one is impossible | medium; card counts can be stale |
| C | links for Dub ep K equal Dub ep 1 | no list change; on play, a K ≠ 1 episode whose links equal ep 1's shows "No dub for episode K yet — play Sub?" | higher; needs a second link load |
| — | probe shows Yumeiro side drops/merges | fix that step instead | — |

Never: substitute another episode silently, or hide Dub entirely.

## 6. Action–reaction (target behaviour)

| Action | Reaction |
|---|---|
| Open Dub on a title whose dub lags | only dubbed episodes listed; header count matches |
| Tap a Dub episode that has no dub at the source | message "No dub for episode N yet" with "Play Sub" |
| Switch Sub → Dub on an episode with no dub | existing message (EpisodeMatcher), keeps playing |

## 7. Tests (to add with the fix)

Unit (fail before / pass after) on the chosen signal with a fake `AnimeLoadResponse`; device: probe re-run shows
the mapped Dub count equal to the card's count for the probed titles.
