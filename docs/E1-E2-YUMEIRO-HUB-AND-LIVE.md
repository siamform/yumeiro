# E1 + E2: Yumeiro's own all-in-one extension ("Yumeiro Hub") and all-in-one Live TV ("Yumeiro Live")

Owner order 2026-11-22 (Dhaka), recorded as written: Yumeiro's own extension and Live TV playlist must be all in one,
"like Vega, SkyStream, CloudStream, Hikari and all other mega extensions (leave 18+)". It must cover everything for
a user: movies, anime, TV series, drama, country-based movies and series, and everything else. It runs only in
Yumeiro's engine, updates itself when the root (the sites) changes, comes installed by default, and gives a
ready experience on first open. Same for Live TV: auto update, country based, stream checker, dead streams removed
automatically. Use GitHub Actions and the Cloudflare server, stay inside the free limits, and never get banned or
blocked.

The base design is docs/E1-MASTER-EXTENSION.md (2026-11-21). This file adds the owner's answers, E2 (Live TV),
the free-limit budget and the order of work. Status today: **not built**. This work does not go into the
2.1.0 production release. The owner gave the 2.1.0 preview one hour; E1 and E2 are multi-step features, and each
step needs its own fail-before, pass-after and device runs. Target: 2.2.0.

## Owner decisions taken from the 2026-11-22 order (these answer E1 section 6)

| Question in E1 section 6 | Answer |
|---|---|
| Scheduled jobs allowed? | Yes, for the Hub / Live builders (owner: "we can use github action and ur cloudflare server"), within the budget below |
| Default install | Yes: Yumeiro Hub and Yumeiro Live ship installed and turned on, with a kill-switch flag each |
| Scope | Movies, anime, TV series, drama, country-based movies and series, everything else; no 18+ |
| Engine | Runs only in Yumeiro's engine (own recipe format, not a .cs3 / APK / Stremio package other apps can load) |
| Updates | Automatic when the root changes (site domain moves, recipe fixes, new channels), no app update needed |
| Repo name / visibility for `yumeiro-sources` | Still open: suggested `siamform/yumeiro-sources`, public (free Actions minutes), recipes signed |

## What "all in one like Vega, SkyStream, CloudStream, Hikari" means here (rules kept)

- Yumeiro already runs those formats side by side: CloudStream .cs3, Aniyomi / Mihon APKs, Stremio add-ons, Nuvio,
  SkyStream and Vega. "All in one" therefore means **one Hub source that the user sees as one extension**, which
  (a) shows one catalog of every type and country, and (b) asks every installed source for a title and merges the
  links (resolver fan-out, E1 layer B), plus (c) our own recipes written from the sites' public pages (layer C).
- We do **not** copy or bundle other extensions' code (GPL / unlicensed / closed): the owner rule "never copy GPL /
  AGPL or closed code" stays. Other extensions keep coming from their own repos at runtime.
- No DRM, no login bypass, no paywall bypass; no 18+ (adult flags in the catalog, the recipe list and Live TV
  groups are filtered out at build time and again in the app).

## E1: Yumeiro Hub, steps (each complete and verified before the next)

| Step | What | Flag | Verification |
|---|---|---|---|
| E1-A | Hub Catalog: Worker endpoint `/hub/catalog/{type}/{id}.json?country=&genre=&page=` built from TMDB discover (country, language), AniList (anime), TVmaze (series schedule, already used by F-22-C). App: Hub home with type tabs (Movies, Series, Anime, Drama, Country) and country chips | `hub_catalog` | unit fail-before / pass-after; device api33-lowram, api36, tv-api34 |
| E1-B | Resolver fan-out over installed sources (every format), match by ids or title + year + type; best link first, the rest as fallbacks | `hub_resolver` | unit (matching); device: one title plays from 2 sources |
| E1-C1 | Recipe engine (JSON recipes, own extractors) + first recipes written by us | `hub_recipes` | unit per recipe on saved pages; device plays each |
| E1-C2 | `yumeiro-sources` repo: probe job, domain-move PRs, signed bundle (sha256 + signature) pulled by the app | `hub_recipes_ota` | probe run IDs; bad signature rejected |
| E1-D | Ranking and health (speed, success, quality, language) fed into ExtensionSpeed | `hub_ranking` | unit + device |
| E1-E | Default install + first-open ready experience (Hub preinstalled, Home rows filled from the Hub catalog) | `hub_default` | fresh install device test, no extension needed for a full Home |

## E2: Yumeiro Live, all-in-one Live TV

Yumeiro already has: the Playlist Market builder (`.github/workflows/playlist-market.yml`, every 3 days: repairs
headers, #EXTINF lines, duplicates and bad URLs, validates and health-samples each list), the default playlist
mirror (`stream-sync.yml`, Mrgify BDIX) and the in-app Sync button. E2 joins them into one default playlist.

| Step | What | Flag | Verification |
|---|---|---|---|
| E2-A | Builder job merges the market lists and iptv-org's public index (https://github.com/iptv-org/iptv, main list `https://iptv-org.github.io/iptv/index.m3u`) into one "Yumeiro Live" list, grouped by country and category, adult groups dropped, duplicates merged by tvg-id / normalized name | `live_all_in_one` | builder unit tests on fixtures; run ID |
| E2-B | Stream checker in the job: HEAD / first-segment probe per stream with a short timeout, low concurrency; a stream that fails 3 runs in a row is removed (dead-stream remover); it comes back if it passes again | `live_checker` | job log with pass / fail counts; fixture tests |
| E2-C | App: Yumeiro Live preinstalled, auto-update by sha256 (the market's update path), country picker on Live TV, "checked N hours ago" line | `live_default` | device api33-lowram, api36, tv-api24 / 34 |
| E2-D | On-device fallback: a channel that fails to play is reported once (count only, no user data) to the Worker so the next build re-checks it first | `live_report` | unit + Worker test |

## Free-limit budget (so nothing hits a limit, is banned or is blocked)

- GitHub Actions: usage is free for standard GitHub-hosted runners in public repositories
  ([GitHub Docs](https://docs.github.com/en/actions/administering-github-actions/usage-limits-billing-and-administration)).
  Plan: Hub probe once a day, Live builder every 12 h, each under 20 min, low concurrency, polite User-Agent,
  backoff on 429. Scheduled workflows in a public repo are disabled after 60 days without activity
  ([GitHub Docs](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-workflow-runs/disabling-and-enabling-a-workflow)):
  each build commits its output to its data branch, which counts as activity; a monthly keep-alive check is added.
- Cloudflare Workers free plan: 100,000 requests per day, 10 ms CPU per request, 50 subrequests per invocation,
  5 Cron Triggers per account ([Cloudflare docs](https://developers.cloudflare.com/workers/platform/limits/)).
  Plan: the app reads big files (catalog pages, the Live list, the recipe bundle) from GitHub raw / Pages with a
  long cache, not through the Worker; the Worker only signs, serves small JSON with `Cache-Control` so Cloudflare's
  edge cache answers repeats, and keeps API keys (TMDB) off the APK. The app caches each page for 6 h (as F-22-C
  does), so one user makes a few Worker calls a day.
- Sites: the probe touches each site at most once a day per recipe step, so it looks like one visitor, not a bot farm.

## Order of work after 2.1.0 ships

E2-A, E2-B (Live first: it reuses the existing market builder, smallest risk), then E1-A, E1-B, E1-E, then E1-C1,
E1-C2, E1-D. Each step follows the rules: research with cited URLs, flag, fail-before branch, pass-after CI, device
runs, ledger row, OMT entry.
