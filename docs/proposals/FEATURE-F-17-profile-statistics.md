# F-17 Profile statistics (on-device only)

Status: **Built, not tested** (b6c7868c test, 7703c050 feat). Fail-before: pending CI on `ab/wsB-before`.

- **Ask:** activity map of watching and listening, streaks, totals per kind, milestones; nothing leaves the device.
- **Competitors:** Mihon stats page (docs/research/2026-11-19-workstreams-A-B-F.md); GitHub contribution map idea.
- **UI/UX:** ui-ux-pro-max skill 2de1134 (chart + Jetpack Compose rules): at most 365 day cells, a legend, the
  selected day in text and a screen-reader summary (never colour alone), 4.5:1 contrast, LazyColumn with stable keys.
- **Design:** `stats/StatsMath` (pure), `stats/ActivityLog` (prefs `activity_log`, 371 days max, batched writes, off
  in incognito, in the Library backup and the history reset) fed by ListeningTime, PlaybackSession (video time),
  PlayCounts and recordTrackPlayed. `StatsScreen` (route `library/stats`) from a Library entry card.
- **Performance budget:** one Canvas for the map (no 365 composables); under 20 KB of prefs; no network.
- **Flag:** `f17_profile_stats` (off = nothing recorded, no entry card). **Tests:** F17StatsTest.
