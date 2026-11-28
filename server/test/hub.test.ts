/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// E1-A: Yumeiro Hub catalog (pure helpers + the handler with a fake TMDB).
import { test } from "node:test";
import assert from "node:assert/strict";
import { item, plan, shape, title, providers, plusDays, SHELVES } from "../src/hub.ts";
import worker, { type Env } from "../src/index.ts";

test("item keeps movies and series, drops adult, people and broken rows", () => {
  assert.deepEqual(item({ media_type: "movie", id: 603, title: "The Matrix", release_date: "1999-03-30", poster_path: "/p.jpg", original_language: "en" }), {
    id: "movie:603", type: "movie", title: "The Matrix", year: 1999, poster: "https://image.tmdb.org/t/p/w342/p.jpg", lang: "en", date: "1999-03-30",
  });
  assert.equal(item({ media_type: "person", id: 1, name: "X" }), null);
  assert.equal(item({ media_type: "movie", id: 2, title: "A", adult: true }), null);
  assert.equal(item({ media_type: "movie", id: 3, title: "XXX Party" }), null);
  assert.equal(item({ media_type: "movie", title: "No id" }), null);
  const a = item({ id: 9, name: "Frieren", original_name: "葬送のフリーレン", first_air_date: "2023-09-29", genre_ids: [16], original_language: "ja" }, "tv");
  assert.equal(a?.id, "tv:9"); assert.equal(a?.anime, true); assert.equal(a?.original, "葬送のフリーレン");
});

test("title maps details, imdb id and real seasons only", () => {
  const t = title({ id: 1396, name: "Breaking Bad", first_air_date: "2008-01-20", overview: "o", genres: [{ id: 18, name: "Drama" }],
    external_ids: { imdb_id: "tt0903747" }, vote_average: 8.91, vote_count: 15000, episode_run_time: [47],
    seasons: [{ season_number: 0, episode_count: 9 }, { season_number: 1, episode_count: 7, name: "Season 1" }, { season_number: 2, episode_count: 0 }] }, "tv");
  assert.equal(t?.imdb, "tt0903747"); assert.equal(t?.tmdb, 1396); assert.equal(t?.rating, 8.9); assert.equal(t?.runtime, 47);
  assert.deepEqual(t?.genres, ["Drama"]);
  assert.deepEqual(t?.seasons, [{ n: 1, count: 7, name: "Season 1" }]);
});

test("plan validates input and always asks TMDB without adult titles", () => {
  const p = plan("/v1/hub/catalog", new URLSearchParams("shelf=kdrama&page=99"));
  assert.ok(!("error" in p));
  if (!("error" in p)) { assert.match(p.tmdb, /^\/discover\/tv\?/); assert.match(p.tmdb, /include_adult=false/); assert.match(p.tmdb, /page=20/); assert.equal(p.kind, "tv"); }
  assert.deepEqual(plan("/v1/hub/catalog", new URLSearchParams("shelf=nope")), { error: "bad_shelf" });
  assert.deepEqual(plan("/v1/hub/search", new URLSearchParams("q=")), { error: "bad_query" });
  const s = plan("/v1/hub/search", new URLSearchParams("q=naruto"));
  assert.ok(!("error" in s) && s.tmdb.includes("include_adult=false"));
  assert.deepEqual(plan("/v1/hub/title", new URLSearchParams("type=person&id=1")), { error: "bad_title" });
  assert.deepEqual(plan("/v1/hub/title", new URLSearchParams("type=movie&id=-1")), { error: "bad_title" });
  for (const s of Object.values(SHELVES)) assert.ok(s.path.startsWith("/"));
});

test("shape pages lists and stops at the page cap", () => {
  const r = shape("/v1/hub/catalog", { page: 1, total_pages: 500, results: [{ id: 1, title: "A" }, { id: 2, title: "B", adult: true }] }, "movie") as { items: unknown[]; hasNext: boolean };
  assert.equal(r.items.length, 1); assert.equal(r.hasNext, true);
  const last = shape("/v1/hub/catalog", { page: 20, total_pages: 500, results: [] }, "movie") as { hasNext: boolean };
  assert.equal(last.hasNext, false);
});

test("handler: off without the token, then serves TMDB with the token as a bearer", async () => {
  const off = await worker.fetch(new Request("https://api.yumeiro.click/v1/hub/catalog?shelf=anime"), {} as Env);
  assert.equal(off.status, 503);
  const calls: { url: string; auth: string | null }[] = [];
  const real = globalThis.fetch;
  globalThis.fetch = (async (u: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(u), auth: new Headers(init?.headers).get("authorization") });
    return new Response(JSON.stringify({ page: 1, total_pages: 2, results: [{ id: 5, name: "Show", first_air_date: "2020-01-01" }] }), { status: 200 });
  }) as typeof fetch;
  try {
    const r = await worker.fetch(new Request("https://api.yumeiro.click/v1/hub/catalog?shelf=anime"), { TMDB_TOKEN: "tok" } as Env);
    assert.equal(r.status, 200);
    const body = await r.json() as { items: { id: string }[]; hasNext: boolean };
    assert.deepEqual(body.items.map((i) => i.id), ["tv:5"]);
    assert.equal(calls.length, 1); assert.equal(calls[0].auth, "Bearer tok");
    assert.ok(calls[0].url.startsWith("https://api.themoviedb.org/3/discover/tv?"));
    assert.match(r.headers.get("cache-control") ?? "", /max-age=21600/);
    const bad = await worker.fetch(new Request("https://api.yumeiro.click/v1/hub/catalog?shelf=zzz"), { TMDB_TOKEN: "tok" } as Env);
    assert.equal(bad.status, 400);
    const sh = await worker.fetch(new Request("https://api.yumeiro.click/v1/hub/shelves"), {} as Env);
    assert.ok(((await sh.json()) as { shelves: unknown[] }).shelves.length >= 10);
  } finally {
    globalThis.fetch = real;
  }
});

// ---- Group O (B14, F5, F6, F8, F9) ----

test("Group O: discover whitelists every parameter and builds the TMDB call", () => {
  const today = "2026-11-27";
  const up = plan("/v1/hub/discover", new URLSearchParams("type=tv&sort=upcoming&origin=IN"), today);
  assert.ok(!("error" in up));
  if (!("error" in up)) {
    const q = new URLSearchParams(up.tmdb.split("?")[1]);
    assert.match(up.tmdb, /^\/discover\/tv\?/);
    assert.equal(q.get("with_origin_country"), "IN");
    assert.equal(q.get("first_air_date.gte"), today);
    assert.equal(q.get("first_air_date.lte"), "2027-01-26");
    assert.equal(q.get("include_adult"), "false");
    assert.equal(up.kind, "tv");
  }
  const mv = plan("/v1/hub/discover", new URLSearchParams("type=movie&sort=upcoming&origin=IN"), today);
  assert.ok(!("error" in mv) && mv.tmdb.includes("primary_release_date.gte=2026-11-27"));
  const top = plan("/v1/hub/discover", new URLSearchParams("type=movie&sort=top&genre=28,12,35,18"), today);
  assert.ok(!("error" in top));
  if (!("error" in top)) {
    const q = new URLSearchParams(top.tmdb.split("?")[1]);
    assert.equal(q.get("with_genres"), "28,12,35", "at most 3 genres");
    assert.equal(q.get("sort_by"), "vote_average.desc");
    assert.equal(q.get("vote_count.gte"), "300");
  }
  const sv = plan("/v1/hub/discover", new URLSearchParams("type=movie&provider=8&region=IN"), today);
  assert.ok(!("error" in sv));
  if (!("error" in sv)) { const q = new URLSearchParams(sv.tmdb.split("?")[1]); assert.equal(q.get("with_watch_providers"), "8"); assert.equal(q.get("watch_region"), "IN"); }
  const an = plan("/v1/hub/discover", new URLSearchParams("type=tv&anime=1"), today);
  assert.ok(!("error" in an) && an.tmdb.includes("with_genres=16") && an.tmdb.includes("with_original_language=ja"));
  // Junk never reaches TMDB.
  const junk = plan("/v1/hub/discover", new URLSearchParams("type=movie&origin=india&region=x&lang=EN1&genre=abc&with_cast=1"), today);
  assert.ok(!("error" in junk));
  if (!("error" in junk)) for (const k of ["with_origin_country", "watch_region", "with_original_language", "with_genres", "with_cast"]) assert.ok(!junk.tmdb.includes(k + "="), k);
  assert.deepEqual(plan("/v1/hub/discover", new URLSearchParams("type=person")), { error: "bad_type" });
  assert.deepEqual(plan("/v1/hub/discover", new URLSearchParams("type=movie&sort=random")), { error: "bad_sort" });
  assert.equal(plusDays("2026-12-31", 1), "2027-01-01");
});

test("Group O: recommend and providers plans", () => {
  const r = plan("/v1/hub/recommend", new URLSearchParams("type=tv&id=1396"));
  assert.ok(!("error" in r) && r.tmdb.startsWith("/tv/1396/recommendations?"));
  assert.deepEqual(plan("/v1/hub/recommend", new URLSearchParams("type=tv&id=0")), { error: "bad_title" });
  const p = plan("/v1/hub/providers", new URLSearchParams("region=IN"));
  assert.ok(!("error" in p) && p.tmdb.includes("watch_region=IN"));
  const d = plan("/v1/hub/providers", new URLSearchParams("region=../"));
  assert.ok(!("error" in d) && d.tmdb.includes("watch_region=US"));
});

test("Group O: providers ordered by the region's priority, logos sized", () => {
  const out = providers({ results: [
    { provider_id: 8, provider_name: "Netflix", logo_path: "/n.jpg", display_priorities: { IN: 2, US: 1 } },
    { provider_id: 2336, provider_name: "JioHotstar", logo_path: "/j.jpg", display_priorities: { IN: 1 } },
    { provider_id: 0, provider_name: "Broken" },
  ] }, "IN");
  assert.deepEqual(out.map((p) => p.name), ["JioHotstar", "Netflix"]);
  assert.equal(out[0].logo, "https://image.tmdb.org/t/p/w154/j.jpg");
});

test("Group O: items carry the release day, real scores only (10+ votes) and genre names", () => {
  const a = item({ id: 1, title: "A", release_date: "2026-12-04", vote_average: 7.46, vote_count: 120, genre_ids: [28, 18, 999] }, "movie");
  assert.equal(a?.date, "2026-12-04"); assert.equal(a?.rating, 7.5); assert.equal(a?.votes, 120);
  assert.deepEqual(a?.genres, ["Action", "Drama"]);
  const b = item({ id: 2, title: "B", vote_average: 10, vote_count: 2 }, "movie");
  assert.equal(b?.rating, undefined, "two votes is not a rating");
});

test("Group O: title lists where it streams in the asked region", () => {
  const body = { id: 1, title: "A", genres: [], "watch/providers": { results: { IN: { flatrate: [{ provider_name: "JioHotstar" }, { provider_name: "JioHotstar" }] }, US: { flatrate: [{ provider_name: "Max" }] } } } };
  assert.deepEqual((shape("/v1/hub/title", body, "movie", "IN") as { streaming?: string[] }).streaming, ["JioHotstar"]);
  assert.deepEqual((shape("/v1/hub/title", body, "movie") as { streaming?: string[] }).streaming, ["Max"]);
  assert.ok(SHELVES.hot_today && SHELVES.top_movies);
});

test("Group W: discover takes themes, a year range and a lowest score; junk is dropped", () => {
  const today = "2026-11-27";
  const p = plan("/v1/hub/discover", new URLSearchParams("type=tv&anime=1&keyword=237451,207826&year_from=2015&year_to=2024&min_rating=7"), today);
  assert.ok(!("error" in p));
  if (!("error" in p)) {
    const q = new URLSearchParams(p.tmdb.split("?")[1]);
    assert.equal(q.get("with_keywords"), "237451,207826");
    assert.equal(q.get("first_air_date.gte"), "2015-01-01");
    assert.equal(q.get("first_air_date.lte"), "2024-12-31");
    assert.equal(q.get("vote_average.gte"), "7");
    assert.equal(q.get("with_genres"), "16");
  }
  const junk = plan("/v1/hub/discover", new URLSearchParams("type=movie&keyword=abc&year_from=1700&year_to=x&min_rating=42"), today);
  if (!("error" in junk)) {
    const q = new URLSearchParams(junk.tmdb.split("?")[1]);
    assert.equal(q.get("with_keywords"), null); assert.equal(q.get("primary_release_date.gte"), null); assert.equal(q.get("vote_average.gte"), null);
  }
  const fresh = plan("/v1/hub/discover", new URLSearchParams("type=movie&sort=new&year_to=2030"), today);
  if (!("error" in fresh)) assert.equal(new URLSearchParams(fresh.tmdb.split("?")[1]).get("primary_release_date.lte"), today);
  const r = plan("/v1/hub/discover", new URLSearchParams("type=movie&sort=rating"), today);
  assert.ok(!("error" in r));
});

test("Group W: providers merge films and series, drop stores and channels, majors first", () => {
  const movie = { results: [
    { provider_id: 2, provider_name: "Apple TV Store", display_priorities: { US: 1 } },
    { provider_id: 1825, provider_name: "HBO Max Amazon Channel", display_priorities: { US: 2 } },
    { provider_id: 99, provider_name: "Tiny Docs", display_priorities: { US: 3 } },
    { provider_id: 8, provider_name: "Netflix", logo_path: "/n.jpg", display_priorities: { US: 9 } },
  ] };
  const tv = { results: [
    { provider_id: 8, provider_name: "Netflix", display_priorities: { US: 9 } },
    { provider_id: 283, provider_name: "Crunchyroll", display_priorities: { US: 4 } },
    { provider_id: 1796, provider_name: "Netflix Standard with Ads", display_priorities: { US: 5 } },
  ] };
  const out = providers(movie, "US", tv);
  assert.deepEqual(out.map((p) => p.id), [283, 8, 99]);
  assert.equal(out[1].logo, "https://image.tmdb.org/t/p/w154/n.jpg");
});

test("Group W: list rows carry a backdrop for genre art", () => {
  const it = item({ id: 5, media_type: "tv", name: "Show", backdrop_path: "/b.jpg" });
  assert.equal(it?.backdrop, "https://image.tmdb.org/t/p/w780/b.jpg");
});

test("B20: title pages ask TMDB for keywords and pass their names on (films and series)", () => {
  const p = plan("/v1/hub/title", new URLSearchParams("type=tv&id=1399"));
  assert.ok(!("error" in p) && p.tmdb.includes("append_to_response=external_ids,watch/providers,keywords"));
  const film = title({ id: 1, title: "A", genres: [], keywords: { keywords: [{ id: 1, name: "time travel" }, { id: 2, name: "revenge" }, { id: 3, name: "revenge" }] } }, "movie");
  assert.deepEqual(film?.keywords, ["time travel", "revenge"]);
  const show = title({ id: 2, name: "B", genres: [], keywords: { results: [{ id: 9, name: "death game" }] } }, "tv");
  assert.deepEqual(show?.keywords, ["death game"]);
  assert.equal(title({ id: 3, title: "C", genres: [] }, "movie")?.keywords, undefined);
});
