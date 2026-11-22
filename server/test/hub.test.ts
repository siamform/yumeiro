/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// E1-A: Yumeiro Hub catalog (pure helpers + the handler with a fake TMDB).
import { test } from "node:test";
import assert from "node:assert/strict";
import { item, plan, shape, title, SHELVES } from "../src/hub.ts";
import worker, { type Env } from "../src/index.ts";

test("item keeps movies and series, drops adult, people and broken rows", () => {
  assert.deepEqual(item({ media_type: "movie", id: 603, title: "The Matrix", release_date: "1999-03-30", poster_path: "/p.jpg", original_language: "en" }), {
    id: "movie:603", type: "movie", title: "The Matrix", year: 1999, poster: "https://image.tmdb.org/t/p/w342/p.jpg", lang: "en",
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
    external_ids: { imdb_id: "tt0903747" }, vote_average: 8.91, episode_run_time: [47],
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
