# F-08 Upcoming Movies & Series (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head a6a1963)

- No upcoming / release-calendar screen. `online/anime/AniList.kt` only syncs progress (GraphQL `Media`,
  `SaveMediaListEntry`); `tracking/Simkl.kt` and `Trakt.kt` only mark watched (`/sync/history`).
- `notify/DiscoveryWorker.kt`, `online/NewEpisodesWorker.kt`: new-episode alerts for titles in My List (after
  release, from the sources themselves).
- No TMDB client and no TMDB key in the app.

## 2. Competitor (ideas and contracts only; no code copied)

Hikari `codegeasse1/hikari` at commit 8aa1652b2af151aca85be316b926cbabee742e63:

- `app/src/main/java/com/hikari/app/data/ReleaseCalendarRepository.kt`: Movies from TMDB `/movie/upcoming`
  per region, TV/episodes from TMDB TV paths through its `TmdbResolver.apiGet`; grouped by day, capped at 90,
  cached.
- `app/src/main/java/com/hikari/app/data/AnimeReleaseCalendarRepository.kt`: anime from Simkl's public
  calendar files `https://data.simkl.in/calendar/v2/anime.json` (legacy `calendar/anime.json`), cached.
- `ui/screens/AnimeCalendarScreen.kt`: a day-grouped list.

Not checked here (Nuvio 511ebd5 has no calendar; CloudStream 2dcc25c has none).

## 3. Options

| Option | Data | Key / terms | Notes |
|---|---|---|---|
| A. AniList GraphQL `airingSchedule` (anime) | free, public | no key | fits Yumeiro's anime focus; AniList client exists |
| B. Simkl public calendar JSON (anime, also TV/movies files) | free | Simkl client id already used for tracking; owner to confirm terms | one file per kind, easy to cache |
| C. TMDB `/movie/upcoming`, `/tv/on_the_air` | free tier | needs a TMDB API key (owner account, kept in CI secrets/server, never in the APK in clear) and attribution | best movie/series coverage |

Recommendation: **A for anime now, C for movies/series only if the owner provides a TMDB key through the
server proxy** (`server/`), so the key never ships in the app.

## 4. Design

- New "Upcoming" page reachable from Home (a Spaces card) and YumeMix; tabs Anime / Movies / Series; day
  groups ("Today", "Tomorrow", weekday dates in the user's locale and time zone).
- Each entry: poster, title, date/time, episode number; "Remind me" adds to My List with status Plan to watch
  and lets the existing new-episode alert fire when a source has it (no new notification channel).
- Cache 6 h on disk (`cache/`), loaded off main; empty and offline states use the existing `OfflineNotice`.
- TV: same page with D-pad grid; posters through the same image loader and size caps as Home.

## 5. Action–reaction

| Action | Reaction |
|---|---|
| Open Upcoming | Cached list at once, refresh in background |
| Offline | Cached list with "offline" note, or the offline card |
| Tap an entry | Search the user's sources for the title (existing search) |
| Remind me | In My List (Plan to watch); alert when a source lists the episode |
| Time zone change | Day groups recomputed |

## 6. Pros and cons

- Pros: a planning view users ask for; reuses My List and alerts; A needs no key.
- Cons: third-party data and terms (owner call for B/C); one more page to maintain; poster bandwidth on
  low-RAM phones (use small sizes).

## 7. Owner decision

Pick the data sources (A only, A+B, or A+C with a TMDB key behind the server) and the entry point.
