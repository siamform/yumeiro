/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// F14 BlackSource (Group U): advanced anime search by genre, tags and demographic. The Worker asks AniList's public
// GraphQL API with ONE fixed query (users choose values, never query text), checks every answer field as untrusted
// data, filters again itself, and caches at the edge. It runs no third-party code at all: provider (extension) code
// stays on the phone, where the user installed it. See yumeiro-docs/app-docs/BLACKSOURCE.md.

export const ANILIST = "https://graphql.anilist.co";

/** AniList's fixed genre list (GenreCollection). Hentai is never offered. */
export const GENRES = [
  "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music",
  "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
] as const;

/** AniList tags in the "Demographic" category. */
export const DEMOGRAPHICS = ["Shounen", "Shoujo", "Seinen", "Josei", "Kids"] as const;

export const SORTS = { popular: "POPULARITY_DESC", score: "SCORE_DESC", trending: "TRENDING_DESC", newest: "START_DATE_DESC" } as const;

/** B9: AniList media formats and statuses the app may ask for (allow-lists, as for genres). */
export const FORMATS = ["TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA"] as const;
export const STATUSES = ["RELEASING", "FINISHED", "NOT_YET_RELEASED", "HIATUS"] as const;
export const YEAR_MIN = 1940;
export const YEAR_MAX = 2100;

const MAX_TAGS = 5;
const PER_PAGE = 30;

export type BsTitle = {
  id: number; mal?: number; title: string; english?: string; native?: string; synonyms: string[];
  format?: string; year?: number; episodes?: number; cover?: string; genres: string[]; tags: string[]; demographic?: string;
  status?: string; score?: number;
};

export type BsWant = {
  genres: string[]; tags: string[]; demographic?: string;
  without?: string[]; formats?: string[]; statuses?: string[]; yearFrom?: number; yearTo?: number; minScore?: number;
};
export type BsPlan = BsWant & { variables: Record<string, unknown>; ttl: number };

export const SEARCH_QUERY = `query($page:Int,$perPage:Int,$search:String,$genres:[String],$tags:[String],$sort:[MediaSort],\
$without:[String],$formats:[MediaFormat],$statuses:[MediaStatus],$after:FuzzyDateInt,$before:FuzzyDateInt,$score:Int){
Page(page:$page,perPage:$perPage){pageInfo{hasNextPage}
media(type:ANIME,isAdult:false,search:$search,genre_in:$genres,tag_in:$tags,sort:$sort,genre_not_in:$without,\
format_in:$formats,status_in:$statuses,startDate_greater:$after,startDate_lesser:$before,averageScore_greater:$score){
id idMal title{romaji english native} synonyms format status seasonYear startDate{year} averageScore episodes coverImage{large}
genres tags{name rank category isAdult isMediaSpoiler}}}}`;

export const TAGS_QUERY = `query{MediaTagCollection{name category isAdult}}`;

const TAG_OK = /^[A-Za-z0-9][A-Za-z0-9 '&\-.]{0,39}$/;

function list(v: string | null): string[] {
  return (v ?? "").split(",").map((s) => s.trim()).filter((s) => s.length > 0);
}

/** Validates the app's filters. Unknown genres / demographics are errors; tags must look like AniList tag names. */
export function plan(params: URLSearchParams): BsPlan | { error: string } {
  const q = (params.get("q") ?? "").trim();
  if (q.length > 80) return { error: "bad_query" };
  const genres = list(params.get("genres"));
  if (genres.length > 4 || genres.some((g) => !(GENRES as readonly string[]).includes(g))) return { error: "bad_genre" };
  const tags = list(params.get("tags"));
  if (tags.length > MAX_TAGS || tags.some((t) => !TAG_OK.test(t))) return { error: "bad_tag" };
  const demoRaw = params.get("demographic") ?? "";
  const demographic = demoRaw === "" ? undefined : (DEMOGRAPHICS as readonly string[]).find((d) => d === demoRaw);
  if (demoRaw !== "" && !demographic) return { error: "bad_demographic" };
  const without = list(params.get("without"));
  if (without.length > 4 || without.some((g) => !(GENRES as readonly string[]).includes(g)) || without.some((g) => genres.includes(g))) return { error: "bad_without" };
  const formats = list(params.get("formats"));
  if (formats.length > FORMATS.length || formats.some((x) => !(FORMATS as readonly string[]).includes(x))) return { error: "bad_format" };
  const statuses = list(params.get("statuses"));
  if (statuses.length > STATUSES.length || statuses.some((x) => !(STATUSES as readonly string[]).includes(x))) return { error: "bad_status" };
  const year = (k: string): number | undefined | null => {
    const raw = params.get(k);
    if (raw === null || raw === "") return undefined;
    const n = /^\d{4}$/.test(raw) ? Number(raw) : NaN;
    return n >= YEAR_MIN && n <= YEAR_MAX ? n : null;
  };
  const yearFrom = year("from"); const yearTo = year("to");
  if (yearFrom === null || yearTo === null || (yearFrom && yearTo && yearFrom > yearTo)) return { error: "bad_year" };
  const scoreRaw = params.get("score");
  const minScore = scoreRaw === null || scoreRaw === "" ? undefined : (/^\d{1,2}$/.test(scoreRaw) && Number(scoreRaw) % 10 === 0 && Number(scoreRaw) > 0 ? Number(scoreRaw) : null);
  if (minScore === null) return { error: "bad_score" };
  const narrows = without.length > 0 || formats.length > 0 || statuses.length > 0 || !!yearFrom || !!yearTo || !!minScore;
  if (!q && genres.length === 0 && tags.length === 0 && !demographic && !narrows) return { error: "no_filter" };
  const sortKey = params.get("sort") ?? "popular";
  const sort = (SORTS as Record<string, string>)[sortKey];
  if (!sort) return { error: "bad_sort" };
  const page = Math.min(Math.max(Number.parseInt(params.get("page") ?? "1", 10) || 1, 1), 10);
  const allTags = demographic ? [...tags, demographic] : tags;
  const variables: Record<string, unknown> = {
    page, perPage: PER_PAGE,
    sort: q ? ["SEARCH_MATCH", sort] : [sort],
  };
  if (q) variables.search = q;
  if (genres.length) variables.genres = genres;
  if (allTags.length) variables.tags = allTags;
  if (without.length) variables.without = without;
  if (formats.length) variables.formats = formats;
  if (statuses.length) variables.statuses = statuses;
  // FuzzyDateInt is YYYYMMDD and both bounds are strict: "from 2020" is after 2019-12-31, "to 2022" before 2023-01-01.
  if (yearFrom) variables.after = (yearFrom - 1) * 10000 + 1231;
  if (yearTo) variables.before = (yearTo + 1) * 10000 + 101;
  if (minScore) variables.score = minScore - 1;
  const out: BsPlan = { variables, genres, tags, demographic, ttl: q ? 3600 : 6 * 3600 };
  if (without.length) out.without = without;
  if (formats.length) out.formats = formats;
  if (statuses.length) out.statuses = statuses;
  if (yearFrom) out.yearFrom = yearFrom;
  if (yearTo) out.yearTo = yearTo;
  if (minScore) out.minScore = minScore;
  return out;
}

function str(v: unknown, max = 200): string | undefined {
  if (typeof v !== "string") return undefined;
  const t = v.replace(/[\u0000-\u001f\u007f]/g, " ").trim();
  return t ? t.slice(0, max) : undefined;
}
function int(v: unknown, min = 0, max = 1e9): number | undefined {
  return typeof v === "number" && Number.isInteger(v) && v >= min && v <= max ? v : undefined;
}
const lower = (s: string) => s.toLowerCase();

/** One AniList media row → a BlackSource title, or null when it is broken, adult, or misses a requested filter. */
export function title(m: unknown, want: BsWant): BsTitle | null {
  if (!m || typeof m !== "object") return null;
  const r = m as Record<string, unknown>;
  const id = int(r.id, 1);
  const t = (r.title && typeof r.title === "object" ? r.title : {}) as Record<string, unknown>;
  const name = str(t.romaji) ?? str(t.english) ?? str(t.native);
  if (!id || !name) return null;
  const genres = Array.isArray(r.genres) ? r.genres.map((g) => str(g, 40)).filter((g): g is string => !!g && g !== "Hentai") : [];
  if (Array.isArray(r.genres) && r.genres.includes("Hentai")) return null;
  const rawTags = Array.isArray(r.tags) ? r.tags : [];
  let demographic: string | undefined;
  const tags: string[] = [];
  const allTagNames = new Set<string>();
  for (const x of rawTags) {
    if (!x || typeof x !== "object") continue;
    const tg = x as Record<string, unknown>;
    const n = str(tg.name, 40);
    if (!n || tg.isAdult === true) continue;
    allTagNames.add(lower(n));
    if (tg.category === "Demographic") { demographic ??= (DEMOGRAPHICS as readonly string[]).find((d) => d === n); continue; }
    if (tg.isMediaSpoiler === true) continue;
    if ((int(tg.rank, 0, 100) ?? 0) >= 50 && tags.length < 10) tags.push(n);
  }
  // AniList filters already; checking again keeps the answer honest even if the upstream query semantics change.
  const gl = new Set(genres.map(lower));
  if (want.genres.some((g) => !gl.has(lower(g)))) return null;
  if (want.tags.some((x) => !allTagNames.has(lower(x)))) return null;
  if (want.demographic && demographic !== want.demographic) return null;
  if (want.without?.some((g) => gl.has(lower(g)))) return null;
  const fmt = str(r.format, 20);
  if (want.formats?.length && !(fmt && want.formats.includes(fmt))) return null;
  const status = str(r.status, 20);
  if (want.statuses?.length && !(status && want.statuses.includes(status))) return null;
  const start = int(((r.startDate ?? {}) as Record<string, unknown>).year, YEAR_MIN, YEAR_MAX);
  const yr = int(r.seasonYear, 1900, 2100) ?? start;
  // The year range is checked on the start date, the same field AniList filters on (a winter season can start in
  // December of the year before its season year).
  const began = start ?? yr;
  if ((want.yearFrom || want.yearTo) && !began) return null;
  if (want.yearFrom && began! < want.yearFrom) return null;
  if (want.yearTo && began! > want.yearTo) return null;
  const score = int(r.averageScore, 1, 100);
  if (want.minScore && !(score && score >= want.minScore)) return null;
  const cover = str((r.coverImage as Record<string, unknown> | undefined)?.large, 400);
  const out: BsTitle = {
    id, title: name, synonyms: Array.isArray(r.synonyms) ? r.synonyms.map((s) => str(s, 120)).filter((s): s is string => !!s).slice(0, 8) : [],
    genres, tags,
  };
  const mal = int(r.idMal, 1); if (mal) out.mal = mal;
  const en = str(t.english); if (en && en !== name) out.english = en;
  const na = str(t.native); if (na && na !== name) out.native = na;
  if (fmt) out.format = fmt;
  if (yr) out.year = yr;
  if (status) out.status = status;
  if (score) out.score = score;
  const ep = int(r.episodes, 1, 100000); if (ep) out.episodes = ep;
  if (cover && cover.startsWith("https://")) out.cover = cover;
  if (demographic) out.demographic = demographic;
  return out;
}

export function shape(body: unknown, p: BsWant): { items: BsTitle[]; more: boolean } | null {
  const page = ((body as Record<string, unknown> | null)?.data as Record<string, unknown> | undefined)?.Page as Record<string, unknown> | undefined;
  if (!page || !Array.isArray(page.media)) return null;
  const items = page.media.map((m) => title(m, p)).filter((x): x is BsTitle => x !== null);
  const more = (page.pageInfo as Record<string, unknown> | undefined)?.hasNextPage === true;
  return { items, more };
}

/** Tag names for the picker (non-adult; demographics separate). */
export function tagList(body: unknown): { tags: { name: string; category: string }[] } | null {
  const c = ((body as Record<string, unknown> | null)?.data as Record<string, unknown> | undefined)?.MediaTagCollection;
  if (!Array.isArray(c)) return null;
  const tags = c.flatMap((x) => {
    if (!x || typeof x !== "object") return [];
    const r = x as Record<string, unknown>;
    const name = str(r.name, 40); const category = str(r.category, 60) ?? "";
    if (!name || r.isAdult === true || category === "Demographic" || !TAG_OK.test(name)) return [];
    return [{ name, category }];
  });
  return { tags: tags.slice(0, 600) };
}
