/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// E1-A (owner 2026-11-22): the Yumeiro Hub catalog. Shelves, search and title pages for the one built-in
// "Yumeiro" source, built from TMDB (https://developer.themoviedb.org). The TMDB token is a Worker secret,
// never in the app. Only metadata is served (titles, years, posters, seasons); no video, no links.
// No 18+: include_adult=false on every call, and adult titles are dropped again here.

export type Kind = "movie" | "tv";

export interface HubItem {
  id: string; // "movie:603" / "tv:1396"
  type: Kind;
  title: string;
  original?: string;
  year?: number;
  poster?: string;
  lang?: string;
  anime?: boolean;
  /** Group O (B14, F8, F9): release or first-air day, TMDB's own score (with its vote count) and genre names. */
  date?: string;
  rating?: number;
  votes?: number;
  genres?: string[];
}

export interface HubTitle extends HubItem {
  overview?: string;
  backdrop?: string;
  genres: string[];
  /** Group O (F5): where it streams in the asked region (TMDB / JustWatch data), names only. */
  streaming?: string[];
  imdb?: string;
  tmdb: number;
  runtime?: number;
  rating?: number;
  seasons?: { n: number; count: number; name?: string }[];
}

/** Home shelves: key -> [TMDB path, query]. The app shows them in this order. */
export const SHELVES: Record<string, { name: string; path: string; q?: Record<string, string> }> = {
  // Group O (F6): "Hot right now" (today's trending films and series together) and all-time top films.
  hot_today: { name: "Hot right now", path: "/trending/all/day" },
  top_movies: { name: "Top rated movies", path: "/movie/top_rated" },
  trending_movies: { name: "Trending movies", path: "/trending/movie/week" },
  trending_tv: { name: "Trending series", path: "/trending/tv/week" },
  popular_movies: { name: "Popular movies", path: "/movie/popular" },
  top_tv: { name: "Top rated series", path: "/tv/top_rated" },
  anime: { name: "Anime series", path: "/discover/tv", q: { with_genres: "16", with_original_language: "ja", sort_by: "popularity.desc" } },
  anime_movies: { name: "Anime movies", path: "/discover/movie", q: { with_genres: "16", with_original_language: "ja", sort_by: "popularity.desc" } },
  kdrama: { name: "Korean drama", path: "/discover/tv", q: { with_original_language: "ko", with_genres: "18", sort_by: "popularity.desc" } },
  cdrama: { name: "Chinese drama", path: "/discover/tv", q: { with_original_language: "zh", with_genres: "18", sort_by: "popularity.desc" } },
  jdrama: { name: "Japanese drama", path: "/discover/tv", q: { with_original_language: "ja", with_genres: "18", without_genres: "16", sort_by: "popularity.desc" } },
  thai: { name: "Thai series", path: "/discover/tv", q: { with_original_language: "th", sort_by: "popularity.desc" } },
  turkish: { name: "Turkish series", path: "/discover/tv", q: { with_original_language: "tr", sort_by: "popularity.desc" } },
  hindi: { name: "Hindi movies", path: "/discover/movie", q: { with_original_language: "hi", sort_by: "popularity.desc" } },
  telugu: { name: "Telugu movies", path: "/discover/movie", q: { with_original_language: "te", sort_by: "popularity.desc" } },
  tamil: { name: "Tamil movies", path: "/discover/movie", q: { with_original_language: "ta", sort_by: "popularity.desc" } },
  bangla: { name: "Bangla movies", path: "/discover/movie", q: { with_original_language: "bn", sort_by: "popularity.desc" } },
  spanish: { name: "Spanish-language series", path: "/discover/tv", q: { with_original_language: "es", sort_by: "popularity.desc" } },
  documentaries: { name: "Documentaries", path: "/discover/movie", q: { with_genres: "99", sort_by: "popularity.desc" } },
  kids: { name: "Kids and family", path: "/discover/tv", q: { with_genres: "10762", sort_by: "popularity.desc" } },
};

const IMG = "https://image.tmdb.org/t/p";

/** TMDB genre ids (stable, from /genre/movie/list and /genre/tv/list). Names only, no call needed. */
export const GENRES: Record<number, string> = {
  28: "Action", 12: "Adventure", 16: "Animation", 35: "Comedy", 80: "Crime", 99: "Documentary", 18: "Drama",
  10751: "Family", 14: "Fantasy", 36: "History", 27: "Horror", 10402: "Music", 9648: "Mystery", 10749: "Romance",
  878: "Science Fiction", 10770: "TV Movie", 53: "Thriller", 10752: "War", 37: "Western",
  10759: "Action & Adventure", 10762: "Kids", 10763: "News", 10764: "Reality", 10765: "Sci-Fi & Fantasy",
  10766: "Soap", 10767: "Talk", 10768: "War & Politics",
};
const ADULT = /\b(xxx|porn\w*|erotic\w*|hentai|nude|nudity|18\+)\b/i;
export const MAX_PAGE = 20;
export const MAX_QUERY = 80;

type Raw = Record<string, unknown>;
const str = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim() : undefined);
const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : undefined);

export function year(date: unknown): number | undefined {
  const y = Number(String(date ?? "").slice(0, 4));
  return y > 1800 && y < 3000 ? y : undefined;
}

/** One TMDB list row -> HubItem, or null for adult / person / broken rows (pure; unit-tested). */
export function item(r: Raw, fallback?: Kind): HubItem | null {
  const t = (str(r.media_type) as Kind | undefined) ?? fallback;
  if (t !== "movie" && t !== "tv") return null;
  if (r.adult === true) return null;
  const id = num(r.id);
  const title = str(t === "movie" ? r.title : r.name) ?? str(r.title) ?? str(r.name);
  if (!id || !title) return null;
  const original = str(t === "movie" ? r.original_title : r.original_name);
  if (ADULT.test(title) || (original && ADULT.test(original))) return null;
  const genres = Array.isArray(r.genre_ids) ? (r.genre_ids as unknown[]) : [];
  const lang = str(r.original_language);
  const out: HubItem = { id: `${t}:${id}`, type: t, title };
  if (original && original !== title) out.original = original;
  const y = year(t === "movie" ? r.release_date : r.first_air_date);
  if (y) out.year = y;
  const p = str(r.poster_path);
  if (p) out.poster = `${IMG}/w342${p}`;
  if (lang) out.lang = lang;
  if (genres.includes(16) && lang === "ja") out.anime = true;
  const d = str(t === "movie" ? r.release_date : r.first_air_date);
  if (d && /^\d{4}-\d{2}-\d{2}$/.test(d)) out.date = d;
  const votes = num(r.vote_count) ?? 0;
  const v = num(r.vote_average);
  // A score from a handful of votes is noise: only real ones (10 votes or more) are passed on.
  if (v && v > 0 && votes >= 10) { out.rating = Math.round(v * 10) / 10; out.votes = votes; }
  const names = genres.map((g) => GENRES[Number(g)]).filter((g): g is string => !!g);
  if (names.length) out.genres = names.slice(0, 4);
  return out;
}

/** TMDB title details -> HubTitle (pure; unit-tested). */
export function title(r: Raw, t: Kind): HubTitle | null {
  const base = item({ ...r, media_type: t, genre_ids: Array.isArray(r.genres) ? (r.genres as Raw[]).map((g) => g.id) : [] });
  if (!base) return null;
  const ext = (r.external_ids ?? {}) as Raw;
  const out: HubTitle = { ...base, tmdb: num(r.id)!, genres: Array.isArray(r.genres) ? (r.genres as Raw[]).map((g) => str(g.name)).filter((g): g is string => !!g) : [] };
  const ov = str(r.overview); if (ov) out.overview = ov.slice(0, 1500);
  const b = str(r.backdrop_path); if (b) out.backdrop = `${IMG}/w780${b}`;
  const imdb = str(r.imdb_id) ?? str(ext.imdb_id); if (imdb && imdb.startsWith("tt")) out.imdb = imdb;
  const rt = num(r.runtime) ?? (Array.isArray(r.episode_run_time) ? num((r.episode_run_time as unknown[])[0]) : undefined); if (rt) out.runtime = rt;
  // rating / votes come from item() (only real scores, 10 votes or more).
  const wp = ((r["watch/providers"] ?? {}) as Raw).results as Raw | undefined;
  const reg = typeof r._region === "string" ? r._region : "US";
  const here = (wp?.[reg] ?? {}) as Raw;
  const flat = Array.isArray(here.flatrate) ? (here.flatrate as Raw[]) : [];
  const streaming = [...new Set(flat.map((x) => str(x.provider_name)).filter((x): x is string => !!x))].slice(0, 8);
  if (streaming.length) out.streaming = streaming;
  if (t === "tv" && Array.isArray(r.seasons)) {
    out.seasons = (r.seasons as Raw[])
      .map((s) => ({ n: num(s.season_number) ?? -1, count: num(s.episode_count) ?? 0, name: str(s.name) }))
      .filter((s) => s.n >= 1 && s.count > 0)
      .slice(0, 60);
  }
  return out;
}

/** The TMDB request for a hub call, or an error code (pure; unit-tested). */
export function plan(path: string, params: URLSearchParams, today: string = new Date().toISOString().slice(0, 10)): { tmdb: string; kind?: Kind; ttl: number } | { error: string } {
  const page = Math.min(Math.max(Number(params.get("page") ?? "1") | 0, 1), MAX_PAGE);
  if (path === "/v1/hub/shelves") return { tmdb: "", ttl: 86_400 };
  if (path === "/v1/hub/catalog") {
    const s = SHELVES[params.get("shelf") ?? ""];
    if (!s) return { error: "bad_shelf" };
    const q = new URLSearchParams({ ...(s.q ?? {}), include_adult: "false", page: String(page) });
    const kind: Kind | undefined = s.path.includes("/tv") ? "tv" : s.path.includes("/movie") ? "movie" : undefined;
    return { tmdb: `${s.path}?${q}`, kind, ttl: 6 * 3600 };
  }
  if (path === "/v1/hub/search") {
    const text = (params.get("q") ?? "").trim().slice(0, MAX_QUERY);
    if (!text) return { error: "bad_query" };
    return { tmdb: `/search/multi?${new URLSearchParams({ query: text, include_adult: "false", page: String(page) })}`, ttl: 3600 };
  }
  if (path === "/v1/hub/title") {
    const t = params.get("type");
    const id = Number(params.get("id"));
    if ((t !== "movie" && t !== "tv") || !Number.isInteger(id) || id <= 0 || id > 1e9) return { error: "bad_title" };
    return { tmdb: `/${t}/${id}?append_to_response=external_ids,watch/providers`, kind: t, ttl: 24 * 3600 };
  }
  // Group O (F6 "If you like"): TMDB's recommendations for one title.
  if (path === "/v1/hub/recommend") {
    const t = params.get("type");
    const id = Number(params.get("id"));
    if ((t !== "movie" && t !== "tv") || !Number.isInteger(id) || id <= 0 || id > 1e9) return { error: "bad_title" };
    return { tmdb: `/${t}/${id}/recommendations?${new URLSearchParams({ page: String(page) })}`, kind: t, ttl: 24 * 3600 };
  }
  // Group O (F5): the streaming services TMDB knows in a region, most used first.
  if (path === "/v1/hub/providers") {
    const region = iso2(params.get("region")) ?? "US";
    return { tmdb: `/watch/providers/movie?${new URLSearchParams({ watch_region: region })}`, ttl: 24 * 3600 };
  }
  if (path === "/v1/hub/discover") return discover(params, page, today);
  return { error: "not_found" };
}

const iso2 = (v: string | null) => (v && /^[A-Z]{2}$/.test(v) ? v : undefined);
const ids = (v: string | null, max: number) => (v ?? "").split(/[,|]/).map(Number).filter((n) => Number.isInteger(n) && n > 0 && n < 1e6).slice(0, max);

/** Adds [days] to an ISO day (UTC), pure. */
export function plusDays(day: string, days: number): string {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export const UPCOMING_DAYS = 60;

/**
 * Group O (B14, F5, F6, F9): one whitelisted TMDB Discover call. Only known parameters pass, each checked, so
 * the edge cache sees a small set of keys.
 *  type movie|tv · sort popular|top|new|upcoming · genre ids (up to 3, all must match) · provider ids (up to 3,
 *  any) with region · origin (country of origin) · lang (original language) · anime=1.
 * "upcoming" uses an explicit forward window (today .. +60 days) instead of TMDB's rolling /upcoming lists,
 * which are mostly already-released titles; for series it is the first air date.
 */
export function discover(params: URLSearchParams, page: number, today: string): { tmdb: string; kind: Kind; ttl: number } | { error: string } {
  const t = params.get("type");
  if (t !== "movie" && t !== "tv") return { error: "bad_type" };
  const sort = params.get("sort") ?? "popular";
  if (!["popular", "top", "new", "upcoming"].includes(sort)) return { error: "bad_sort" };
  const date = t === "movie" ? "primary_release_date" : "first_air_date";
  const q: Record<string, string> = { include_adult: "false", page: String(page) };
  const genres = ids(params.get("genre"), 3);
  const providers = ids(params.get("provider"), 3);
  const region = iso2(params.get("region"));
  const origin = iso2(params.get("origin"));
  const lang = params.get("lang");
  if (params.get("anime") === "1") { genres.push(16); q.with_original_language = "ja"; }
  if (genres.length) q.with_genres = [...new Set(genres)].join(",");
  if (providers.length) { q.with_watch_providers = providers.join("|"); q.watch_region = region ?? "US"; q.with_watch_monetization_types = "flatrate"; }
  if (origin) q.with_origin_country = origin;
  if (lang && /^[a-z]{2}$/.test(lang)) q.with_original_language = lang;
  switch (sort) {
    case "popular": q.sort_by = "popularity.desc"; break;
    case "top": q.sort_by = "vote_average.desc"; q["vote_count.gte"] = t === "movie" ? "300" : "150"; break;
    case "new": q.sort_by = `${date}.desc`; q[`${date}.lte`] = today; q["vote_count.gte"] = "5"; break;
    case "upcoming": q.sort_by = "popularity.desc"; q[`${date}.gte`] = today; q[`${date}.lte`] = plusDays(today, UPCOMING_DAYS); break;
  }
  return { tmdb: `/discover/${t}?${new URLSearchParams(q)}`, kind: t, ttl: sort === "upcoming" || sort === "new" ? 6 * 3600 : 12 * 3600 };
}

export function shelves() {
  return { shelves: Object.entries(SHELVES).map(([key, s]) => ({ key, name: s.name })) };
}

/** Group O (F5): TMDB watch providers -> the 24 most used in [region] (pure; unit-tested). */
export function providers(body: Raw, region: string): { id: number; name: string; logo?: string }[] {
  const rows = Array.isArray(body.results) ? (body.results as Raw[]) : [];
  const prio = (r: Raw) => num(((r.display_priorities ?? {}) as Raw)[region]) ?? num(r.display_priority) ?? 999;
  return rows
    .filter((r) => num(r.provider_id) && str(r.provider_name))
    .sort((a, b) => prio(a) - prio(b))
    .slice(0, 24)
    .map((r) => {
      const logo = str(r.logo_path);
      return { id: num(r.provider_id)!, name: str(r.provider_name)!, ...(logo ? { logo: `${IMG}/w154${logo}` } : {}) };
    });
}

/** Shapes a TMDB answer for the app (pure; unit-tested). */
export function shape(path: string, body: Raw, kind?: Kind, region?: string): unknown {
  if (path === "/v1/hub/title") return title({ ...body, _region: region ?? "US" }, kind!) ?? { error: "not_found" };
  if (path === "/v1/hub/providers") return { providers: providers(body, region ?? "US") };
  const rows = Array.isArray(body.results) ? (body.results as Raw[]) : [];
  const items = rows.map((r) => item(r, kind)).filter((x): x is HubItem => !!x);
  const page = num(body.page) ?? 1, pages = num(body.total_pages) ?? 1;
  return { items, hasNext: page < Math.min(pages, MAX_PAGE) };
}
