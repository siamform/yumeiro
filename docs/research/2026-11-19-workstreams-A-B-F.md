# Competitor research, 2026-11-19 (F-16, NF-1, F-17, TV-4, NB-batch 1)

Ideas and contracts only. No code was copied; licences respected (CloudStream GPL-3.0, Mihon/Aniyomi Apache-2.0).

| Item | Project, commit | Files read | What we took (idea only) |
|---|---|---|---|
| F-16 sub/dub tags | recloudstream/cloudstream 2dcc25c3945c61dfa95d1710ced7c74acfe84baa | `app/src/main/java/com/lagradost/cloudstream3/ui/search/SearchResultBuilder.kt:58-83, 271-273` | poster shows separate Sub / Dub marks from `dubStatus`, each switchable; Yumeiro adds Dual audio and quality from its own ReleaseTags |
| NF-1 episode status | cloudstream 2dcc25c | `ui/result/EpisodeAdapter.kt:207-231` (`VideoWatchState.Watched`, `getDisplayPosition()` progress) | Watched state is separate from a position bar; Yumeiro adds "N min left" text and a Fresh state |
| F-17 statistics | mihonapp/mihon f64d82b94218b42410c36e6c85da818574865a28 | `app/src/main/java/eu/kanade/presentation/more/stats/data/StatsData.kt` (Overview, Titles, Chapters, Trackers), `StatsScreenContent.kt` | an on-device totals page grouped in sections; Yumeiro adds a day map, streaks and milestones and keeps everything on the device |
| F-12, F-09, F-08, F-10 | see their proposals in docs/proposals/ | | |
| TV-4 | Kodi, SmartTube (behaviour, from GROUP-7 proposal 3) | | one box reaches online sources |
| NB14-OB1 | coil-kt/coil 2.7.0 `coil-svg` (Apache-2.0, a dependency, not copied) | | SVG decoder module |
