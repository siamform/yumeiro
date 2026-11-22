# E1: Yumeiro Master Extension ("Yumeiro Hub")

Status: **Not built**. This is the research and design record, written 2026-11-21 while CI ran.
Owner brief, 2026-11-21: "One extension that contains everything: anime, drama, web series, movies, all in categories with filters like Bangladesh, India, that looks like an API. Find out how extensions are built from the root, mix many into one and serve it as one. No copy, no clone, build our own, updated from the root time to time. Research deep and find the right way."

Owner rules that apply here: no partial or unverified work, every feature behind a flag with a kill switch, a perf budget for a 2 GB phone and for TV, a regression test with a fail-before run, no DRM / login / paywall bypass, and no copying of closed or GPL-incompatible code. Reverse engineering is OK for interoperability.

## 1. How the other apps' extensions are built (findings)

| Ecosystem | What an extension is | How it is distributed | What it implements |
|---|---|---|---|
| CloudStream | `.cs3` = zip with `classes.dex` + `manifest.json` (`pluginClassName`, `version`, `requiresResources`), loaded with a class loader ([DeepWiki: creating plugins](https://deepwiki.com/recloudstream/cloudstream/9.2-creating-plugins), [loader audit](https://github.com/sostk/cloudstream-webos-native/blob/main/docs/research/cloudstream-loader-audit.md)) | `repo.json` → `pluginLists` → `plugins.json` (url, version, status, tvTypes, language, fileHash) ([RepositoryManager.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt)) | A `MainAPI` per site: `getMainPage`, `search`, `load`, `loadLinks`, plus `ExtractorApi` for video hosts ([CloudStream docs](https://recloudstream.github.io/csdocs/devs/create-your-own-providers/)) |
| Aniyomi / Mihon | An APK whose manifest names the source class; the class extends `AnimeHttpSource` ([Aniyomi docs](https://extension-docs.aniyomi.org/extensions-lib/eu.kanade.tachiyomi.animesource.online/-anime-http-source/index.html)) | `index.min.json` repository | popular / latest / search / details / episodes / hosters / videos; shared "themes" for sites that use the same CMS ([yuzono CONTRIBUTING](https://github.com/yuzono/anime-extensions/blob/master/CONTRIBUTING.md)) |
| Vega | JS files (`catalog`, `posts`, `meta`, `episodes`, `stream`) that run in a sandbox with an injected `providerContext` (axios, cheerio) ([providers-template](https://github.com/vega-org/providers-template/blob/main/README.md), [DeepWiki: Vega providers](https://deepwiki.com/vega-org/vega-app/4-content-provider-system)) | `manifest.json` in a GitHub repo, fetched raw | catalog + filters, posts, search, meta, stream |
| SkyStream | `.sky` / `.js` plugins in its own JS runtime; one plugin can carry several providers and a `domains` list for site moves ([skystream-tools DEVELOPER.md](https://github.com/akashdh11/skystream-tools/blob/main/DEVELOPER.md)) | `repo.json`, built by GitHub Actions | scraper logic plus a shared `skystream-extractors` package |
| Stremio | An HTTP service: `/manifest.json` and `/{resource}/{type}/{id}.json` for `catalog`, `meta`, `stream`, `subtitles` ([Stremio protocol](https://stremio.github.io/stremio-addon-sdk/protocol.html)) | A URL | Stream add-ons answer for shared IDs (`tt…`, and `tt…:season:episode` for series) ([Stremio advanced](https://stremio.github.io/stremio-addon-sdk/advanced.html)) |

What every format has in common, the "root":
1. A **site recipe**: base URL (it moves often), how to list a catalog page, how to search, how to read a title page (seasons, episodes, sub/dub), and how to turn an episode into a playable link.
2. An **extractor layer**: the same video hosts (players embedded by many sites) are shared across sites. CloudStream has `ExtractorApi`, SkyStream has `skystream-extractors`, and Aniyomi has `lib/` extractors.
3. A **catalog model**: CloudStream has `TvType` and home rows, Vega has `catalog` and `genres`, and Stremio has catalogs with `extra` filters.

Why extensions break and go stale: (a) the site changes domain, (b) the site changes HTML or its JSON API, (c) a video host changes its player, (d) the maintainer stops. Yumeiro already sees this daily (ExtensionSpeed quarantine, FE-S3 fail memory).

## 2. What Yumeiro already has that E1 builds on

- It runs all five formats side by side: CloudStream `.cs3`, Aniyomi / Mihon APKs, Stremio add-ons and Mangayomi-style JS ([README](https://github.com/siamform/yumeiro/blob/main/README.md)).
- A merged search: `SearchJoin`, `SearchRanker` (groupKey / alsoOn) and per-site speed (`ExtensionSpeed`, FE-S4).
- An extension market, a playlist market and a Worker at api.yumeiro.click; GitHub Actions builds the catalogs.
- Built-in providers: `YumeiroDrama` and the Internet Archive.

## 3. The design: "Yumeiro Hub", one source with three layers

Stremio's split is the right model for "one extension that looks like an API": **metadata is separate from streams**. One shared catalog decides what a title is, and many stream resolvers answer "where can I play this ID".

```
             +-------------------------------------------+
 Layer A     |  Hub Catalog (our API)                      |
 metadata    |  type: anime / drama / series / movie / web |
             |  filters: country (BD, IN, KR, JP, ...),    |
             |  language, genre, year, status              |
             |  ids: tmdb, imdb, anilist, mal (cross-map)  |
             +---------------------+---------------------+
                                   | title id (+ season, episode)
             +---------------------v---------------------+
 Layer B     |  Resolver fan-out                           |
 matching    |  ask every enabled source (any format)      |
             |  for this id, match by ids or by            |
             |  title + year + type (EpisodeMatcher)       |
             +---------------------+---------------------+
                                   | candidate links
             +---------------------v---------------------+
 Layer C     |  Stream ranking + health                    |
 playback    |  speed, success rate, quality, sub/dub,     |
             |  language; the best link plays first and    |
             |  the rest stay as fallbacks                 |
             +-------------------------------------------+
```

### Layer A: Hub Catalog (our own API, no scraping needed)
- The source of truth is public metadata APIs. TMDB `discover` filters by `with_origin_country` (e.g. `BD`, `IN`) and `with_original_language` for both movies and TV ([TMDB discover movie](https://developer.themoviedb.org/reference/discover-movie), [discover TV params](https://tmdb.lorenzopant.dev/docs/api-reference/discover/tv)). Anime comes from AniList (Yumeiro already tracks with it).
- Categories become our own catalog IDs: `anime`, `kdrama`, `cdrama`, `bd-drama`, `in-webseries`, `movies-bd`, `movies-in`, and so on, each with filters.
- It is served by the Worker as `/hub/catalog/{type}/{id}.json?country=BD&genre=..&page=N` (Stremio-shaped, so the format is open and documented). The app caches it like a feed frame (FE-4).
- Keys stay on the Worker; the app never ships a TMDB key in the APK.

### Layer B: Resolver fan-out (the "mix many extensions into one")
- For a Hub title, the app asks every enabled installed source, in any format, through the paths it already has (search by title on PICKED lanes, FE-2c), then matches results to the Hub ID by ids or by normalized title + year + type.
- No extension code is copied or bundled; each extension still comes from its own repo, at runtime, as the user installed it. This is interoperability, which the owner rule allows.
- A match table is learned per device: (hub id → source, source url). Optionally a shared one comes from the Worker, built by our own CI from public catalogs, never from user data.

### Layer C: Our own recipes ("built from the root")
- A separate repo, `yumeiro-sources`, holds **declarative recipes** that we write ourselves by reading each site's public pages and APIs (interoperability reverse engineering of the site, not of someone else's extension code). A recipe is JSON: `baseUrls[]` (all known domains), `catalog`, `search`, `title`, `episodes`, `links`, and selectors or JSON paths.
- Our own extractor set covers the common video hosts, written from their public player responses. Where a site or host needs DRM, a login or a paywall bypass, it is out of scope.
- **Root updates**: a CI job in `yumeiro-sources` probes each recipe (catalog page, one search, one title, one playable HEAD) and records pass/fail per domain. When a site moves, the probe tries the next domain or the site's redirect, opens a PR updating `baseUrls`, and the app pulls the recipe bundle OTA (signed, sha256, like the extension market).
- An **upstream watcher**: the same job reads public `repo.json` / `plugins.json` / `manifest.json` of the big repos only for metadata (version bumps, domain lists, status flags). A version bump on a site we also cover is a hint to re-probe our recipe. We never copy their code.

### The recipe engine in the app
- One Kotlin engine runs recipes, using the OkHttp and HTML parser the app already uses (no new big dependency).
- Its output is the same `OnlineDetail` / `OnlineEpisode` / link model the app already plays, so player, downloads, history and NF-1 status all work unchanged.
- Regexes live in private vals and go into RegexWarmup.CLASSES; all work runs off the main thread.

## 4. Licence and legal guardrails

- CloudStream's app and library are GPL-3.0. Extension repos have mixed licences, and many have none, which means all rights reserved. We **read** them only to learn which sites and hosts exist; recipes and extractors are written from the sites' own responses.
- We take no DRM content, need no account, and bypass no paywall. Sites that serve OTT catalogs by circumventing DRM (e.g. "Netflix / Prime mirror" providers) are excluded from Hub recipes.
- Every recipe records its source site, the date it was written, and who wrote it (us), so an audit is possible.

## 5. Delivery plan, each step complete and verified before the next

| Step | Scope | Flag (kill switch) | Perf budget (2 GB phone / TV) | Verification |
|---|---|---|---|---|
| E1-A | Hub Catalog on the Worker + app catalog screen with type and country filters | `hub_catalog` | first page under 1.5 s from cache, under 3 s cold; under 6 MB heap | unit (catalog parse, filter URL) with fail-before; device test on api33-lowram, api36, tv-api34 |
| E1-B | Resolver fan-out: Hub title → matched results from installed sources | `hub_resolver` | first playable link under 8 s on lowram; at most 6 parallel calls (shared limit) | unit (matching: id, title + year, sub/dub); device: one Hub title plays from at least 2 sources |
| E1-C1 | Recipe engine + 3 own recipes + 3 own extractors | `hub_recipes` | recipe run under 2 s per step on lowram; no main-thread I/O (StrictMode) | unit per recipe on saved fixtures; live probe job; device plays each |
| E1-C2 | `yumeiro-sources` repo, probe job, signed OTA bundle, domain-move PRs | `hub_recipes_ota` | bundle under 200 KB | probe run IDs; OTA install test with a bad signature rejected |
| E1-D | Ranking + health (speed, success, quality, language) feeding the existing ExtensionSpeed | `hub_ranking` | none extra | unit + device |

The owner must approve two things before E1-C2: the new repo, and whether its probe job may run on a schedule (the agent rule is "no scheduled workflows").

## 6. Open questions for the owner

1. Repo name and visibility for `yumeiro-sources` (suggested: private until the first audited recipes).
2. Schedule for the probe job (needs your OK because of the no-schedule rule).
3. First countries and categories for the Hub Catalog (suggested: BD, IN, KR, JP; anime, drama, web series, movies).

## 7. Resume notes for the next agent

- Nothing is built yet. Start at E1-A only after the open release items in HANDOVER-LIVE.md are done, or when the owner says to start E1.
- Reuse: `SearchJoin`, `SearchRanker`, `EpisodeMatcher`, `ExtensionSpeed`, `FeedFrames`, the Worker at api.yumeiro.click, and the extension-market build workflow pattern.
