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

const MAX_TAGS = 5;
const PER_PAGE = 30;

export type BsTitle = {
  id: number; mal?: number; title: string; english?: string; native?: string; synonyms: string[];
  format?: string; year?: number; episodes?: number; cover?: string; genres: string[]; tags: string[]; demographic?: string;
};

export type BsPlan = { variables: Record<string, unknown>; genres: string[]; tags: string[]; demographic?: string; ttl: number };

export const SEARCH_QUERY = `query($page:Int,$perPage:Int,$search:String,$genres:[String],$tags:[String],$sort:[MediaSort]){
Page(page:$page,perPage:$perPage){pageInfo{hasNextPage}
media(type:ANIME,isAdult:false,search:$search,genre_in:$genres,tag_in:$tags,sort:$sort){
id idMal title{romaji english native} synonyms format seasonYear episodes coverImage{large}
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
  if (!q && genres.length === 0 && tags.length === 0 && !demographic) return { error: "no_filter" };
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
  return { variables, genres, tags, demographic, ttl: q ? 3600 : 6 * 3600 };
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
export function title(m: unknown, want: { genres: string[]; tags: string[]; demographic?: string }): BsTitle | null {
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
  const cover = str((r.coverImage as Record<string, unknown> | undefined)?.large, 400);
  const out: BsTitle = {
    id, title: name, synonyms: Array.isArray(r.synonyms) ? r.synonyms.map((s) => str(s, 120)).filter((s): s is string => !!s).slice(0, 8) : [],
    genres, tags,
  };
  const mal = int(r.idMal, 1); if (mal) out.mal = mal;
  const en = str(t.english); if (en && en !== name) out.english = en;
  const na = str(t.native); if (na && na !== name) out.native = na;
  const fmt = str(r.format, 20); if (fmt) out.format = fmt;
  const yr = int(r.seasonYear, 1900, 2100); if (yr) out.year = yr;
  const ep = int(r.episodes, 1, 100000); if (ep) out.episodes = ep;
  if (cover && cover.startsWith("https://")) out.cover = cover;
  if (demographic) out.demographic = demographic;
  return out;
}

export function shape(body: unknown, p: Pick<BsPlan, "genres" | "tags" | "demographic">): { items: BsTitle[]; more: boolean } | null {
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
