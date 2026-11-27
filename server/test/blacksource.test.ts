/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// F14 BlackSource (Group U): input checks, answer shaping and the handler with a fake AniList.
import { test } from "node:test";
import assert from "node:assert/strict";
import { plan, shape, tagList, title, SEARCH_QUERY } from "../src/blacksource.ts";
import worker, { type Env } from "../src/index.ts";

const frieren = {
  id: 154587, idMal: 52991, title: { romaji: "Sousou no Frieren", english: "Frieren: Beyond Journey's End", native: "葬送のフリーレン" },
  synonyms: ["Frieren"], format: "TV", seasonYear: 2023, episodes: 28, coverImage: { large: "https://s4.anilist.co/c.jpg" },
  genres: ["Adventure", "Drama", "Fantasy"],
  tags: [
    { name: "Shounen", rank: 80, category: "Demographic", isAdult: false, isMediaSpoiler: false },
    { name: "Elf", rank: 95, category: "Cast-Traits", isAdult: false, isMediaSpoiler: false },
    { name: "Time Skip", rank: 70, category: "Setting-Time", isAdult: false, isMediaSpoiler: true },
    { name: "Nudity", rank: 60, category: "Sexual Content", isAdult: true, isMediaSpoiler: false },
  ],
};

test("plan validates values and never takes query text from the app", () => {
  const p = plan(new URLSearchParams("genres=Fantasy,Drama&tags=Elf&demographic=Shounen&sort=score&page=99"));
  assert.ok(!("error" in p));
  if (!("error" in p)) {
    assert.deepEqual(p.variables, { page: 10, perPage: 30, sort: ["SCORE_DESC"], genres: ["Fantasy", "Drama"], tags: ["Elf", "Shounen"] });
    assert.equal(p.ttl, 6 * 3600);
  }
  const w = plan(new URLSearchParams("q=frieren&genres=Fantasy"));
  assert.ok(!("error" in w) && w.variables.search === "frieren" && (w.variables.sort as string[])[0] === "SEARCH_MATCH" && w.ttl === 3600);
  assert.deepEqual(plan(new URLSearchParams("")), { error: "no_filter" });
  assert.deepEqual(plan(new URLSearchParams("genres=Hentai")), { error: "bad_genre" });
  assert.deepEqual(plan(new URLSearchParams("genres=Action&demographic=Adults")), { error: "bad_demographic" });
  assert.deepEqual(plan(new URLSearchParams("tags=}){x}")), { error: "bad_tag" });
  assert.deepEqual(plan(new URLSearchParams("tags=a,b,c,d,e,f")), { error: "bad_tag" });
  assert.deepEqual(plan(new URLSearchParams("genres=Action&sort=DROP")), { error: "bad_sort" });
  assert.deepEqual(plan(new URLSearchParams("q=" + "x".repeat(81))), { error: "bad_query" });
  assert.ok(SEARCH_QUERY.includes("isAdult:false"));
});

test("title keeps safe fields, drops adult and spoiler tags, reads the demographic", () => {
  const t = title(frieren, { genres: ["Fantasy"], tags: ["Elf"], demographic: "Shounen" });
  assert.deepEqual(t, {
    id: 154587, mal: 52991, title: "Sousou no Frieren", english: "Frieren: Beyond Journey's End", native: "葬送のフリーレン",
    synonyms: ["Frieren"], format: "TV", year: 2023, episodes: 28, cover: "https://s4.anilist.co/c.jpg",
    genres: ["Adventure", "Drama", "Fantasy"], tags: ["Elf"], demographic: "Shounen",
  });
  // Filters are checked again here.
  assert.equal(title(frieren, { genres: ["Horror"], tags: [] }), null);
  assert.equal(title(frieren, { genres: [], tags: ["Mecha Pilot"] }), null);
  assert.equal(title(frieren, { genres: [], tags: [], demographic: "Seinen" }), null);
  // Spoiler tags still match a requested tag, but are not shown.
  assert.ok(title(frieren, { genres: [], tags: ["Time Skip"] }));
  assert.equal(title({ ...frieren, genres: ["Hentai"] }, { genres: [], tags: [] }), null);
  assert.equal(title({ id: "1", title: { romaji: "x" } }, { genres: [], tags: [] }), null);
  assert.equal(title({ ...frieren, coverImage: { large: "javascript:alert(1)" } }, { genres: [], tags: [] })?.cover, undefined);
  assert.equal(title({ ...frieren, title: { romaji: "a\u0000b" } }, { genres: [], tags: [] })?.title, "a b");
});

test("shape and tagList refuse broken answers", () => {
  assert.equal(shape({ errors: [{ message: "x" }] }, { genres: [], tags: [] }), null);
  const s = shape({ data: { Page: { pageInfo: { hasNextPage: true }, media: [frieren, null, { id: 2 }] } } }, { genres: [], tags: [] });
  assert.equal(s?.items.length, 1); assert.equal(s?.more, true);
  const t = tagList({ data: { MediaTagCollection: [{ name: "Elf", category: "Cast-Traits" }, { name: "Shounen", category: "Demographic" }, { name: "Nudity", category: "Sexual Content", isAdult: true }] } });
  assert.deepEqual(t, { tags: [{ name: "Elf", category: "Cast-Traits" }] });
});

test("handler: fixed query to AniList, values as variables, errors mapped", async () => {
  const real = globalThis.fetch;
  const seen: { url: string; body: { query: string; variables?: Record<string, unknown> } }[] = [];
  let mode: "ok" | "busy" | "throw" = "ok";
  globalThis.fetch = (async (u: string | URL | Request, init?: RequestInit) => {
    if (mode === "throw") throw new Error("timeout");
    seen.push({ url: String(u), body: JSON.parse(String(init?.body)) });
    if (mode === "busy") return new Response("{}", { status: 429 });
    const q = JSON.parse(String(init?.body)).query as string;
    if (q.includes("MediaTagCollection")) return Response.json({ data: { MediaTagCollection: [{ name: "Elf", category: "Cast-Traits" }] } });
    return Response.json({ data: { Page: { pageInfo: { hasNextPage: false }, media: [frieren] } } });
  }) as typeof fetch;
  try {
    const r = await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/search?genres=Fantasy&demographic=Shounen"), {} as Env);
    assert.equal(r.status, 200);
    assert.match(r.headers.get("cache-control") ?? "", /max-age=21600/);
    const b = await r.json() as { items: { id: number }[] };
    assert.deepEqual(b.items.map((x) => x.id), [154587]);
    assert.equal(seen[0].url, "https://graphql.anilist.co");
    assert.equal(seen[0].body.query, SEARCH_QUERY);
    assert.deepEqual(seen[0].body.variables?.tags, ["Shounen"]);
    const m = await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/meta"), {} as Env);
    const mb = await m.json() as { genres: string[]; demographics: string[]; tags: { name: string }[] };
    assert.ok(mb.genres.includes("Fantasy") && !mb.genres.includes("Hentai"));
    assert.deepEqual(mb.tags.map((x) => x.name), ["Elf"]);
    assert.equal((await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/search?genres=Nope"), {} as Env)).status, 400);
    assert.equal((await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/other"), {} as Env)).status, 404);
    mode = "busy";
    assert.equal((await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/search?genres=Action"), {} as Env)).status, 503);
    mode = "throw";
    assert.equal((await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/search?genres=Action"), {} as Env)).status, 504);
    mode = "ok";
    const limited = { BLACKSOURCE: { limit: async () => ({ success: false }) } } as unknown as Env;
    assert.equal((await worker.fetch(new Request("https://api.yumeiro.click/v1/blacksource/search?genres=Action"), limited)).status, 429);
  } finally {
    globalThis.fetch = real;
  }
});
