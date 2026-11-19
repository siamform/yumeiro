/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// SEC-LFM (ADR-048): the Worker signs Last.fm calls so the APK has no shared secret. The signature must equal
// the app's own (LastFmSignature.kt: MD5 of sorted "<name><value>" + secret), and only the app's calls sign.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import worker, { type Env } from "../src/index.ts";
import { apiSig, md5Hex, signRequest, signatureBase } from "../src/lastfm.ts";

const nodeMd5 = (s: string) => createHash("md5").update(Buffer.from(s, "utf8")).digest("hex");
const KEY = "k".repeat(32), SECRET = "test-secret-not-real";

test("md5 matches RFC 1321 vectors and node for every length around the block edges", () => {
  assert.equal(md5Hex(new TextEncoder().encode("")), "d41d8cd98f00b204e9800998ecf8427e");
  assert.equal(md5Hex(new TextEncoder().encode("abc")), "900150983cd24fb0d6963f7d28e17f72");
  for (let n = 0; n < 200; n++) { const s = "aé日".repeat(n).slice(0, n); assert.equal(md5Hex(new TextEncoder().encode(s)), nodeMd5(s), `n=${n}`); }
});

test("signature equals the app's format: sorted names, format/callback/api_sig left out, UTF-8", () => {
  const p = { method: "track.scrobble", api_key: KEY, sk: "S", "artist[0]": "Yōko Kanno", "track[0]": "Tank!", "timestamp[0]": "1700000000", format: "json" };
  assert.equal(signatureBase(p, SECRET), `api_key${KEY}artist[0]Yōko Kannomethodtrack.scrobbleskStimestamp[0]1700000000track[0]Tank!${SECRET}`);
  assert.equal(apiSig(p, SECRET), nodeMd5(signatureBase(p, SECRET)));
  // The app's own vector (app/src/test/.../LastFmTest.kt): the Worker and the phone sign the same way.
  const app = { method: "track.scrobble", api_key: "k", sk: "SK", "artist[0]": "Björk", "track[0]": "Jóga", format: "json", callback: "x" };
  assert.equal(apiSig(app, "s"), "0886adad51e1f072f531d9f437427cd8");
  assert.equal(apiSig({ token: "t", method: "auth.getSession", api_key: "k" }, "s"), "4b1fe62eb056c03672443be528ff2633");
});

test("only the app's four calls, with Yumeiro's key, within the size cap, can be signed", () => {
  const ok = (m: string) => signRequest({ params: { method: m, api_key: KEY } }, KEY);
  for (const m of ["auth.getToken", "auth.getSession", "track.updateNowPlaying", "track.scrobble"]) assert.ok("params" in ok(m), m);
  for (const m of ["user.getInfo", "track.love", "", "auth.getMobileSession"]) assert.deepEqual(ok(m), { error: "method_not_allowed" }, m);
  assert.deepEqual(signRequest({ params: { method: "auth.getToken", api_key: "other" } }, KEY), { error: "wrong_key" });
  assert.deepEqual(signRequest({ params: { method: "auth.getToken", api_key: "" } }, ""), { error: "wrong_key" });
  assert.deepEqual(signRequest({ params: { method: "auth.getToken", api_key: KEY, "x y": "1" } }, KEY), { error: "bad_params" });
  assert.deepEqual(signRequest({ params: { method: "auth.getToken", api_key: KEY, n: 1 } }, KEY), { error: "bad_params" });
  assert.deepEqual(signRequest({ params: [] }, KEY), { error: "bad_params" });
  assert.deepEqual(signRequest(null, KEY), { error: "bad_params" });
  assert.deepEqual(signRequest({ params: { method: "track.scrobble", api_key: KEY, sk: "x".repeat(33_000) } }, KEY), { error: "too_large" });
  const many: Record<string, string> = { method: "track.scrobble", api_key: KEY, sk: "s" };
  for (let i = 0; i < 50; i++) for (const f of ["artist", "track", "timestamp", "album", "duration"]) many[`${f}[${i}]`] = "v";
  assert.ok("params" in signRequest({ params: many }, KEY), "a full 50-play batch signs");
});

const env = (o: Partial<Env> & { limitOk?: () => boolean } = {}) => ({
  LASTFM_API_KEY: KEY, LASTFM_SHARED_SECRET: SECRET, LASTFM_SIGN: { limit: async () => ({ success: o.limitOk?.() ?? true }) }, ...o,
}) as unknown as Env;
const sign = (e: Env, body: unknown) => worker.fetch(new Request("https://api.yumeiro.click/v1/lastfm/sign", {
  method: "POST", headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.9" }, body: typeof body === "string" ? body : JSON.stringify(body),
}), e).then(async (r) => ({ status: r.status, body: await r.json() as any }));

test("POST /v1/lastfm/sign returns the app's signature; never the secret", async () => {
  const params = { method: "auth.getToken", api_key: KEY };
  const r = await sign(env(), { params });
  assert.equal(r.status, 200);
  assert.equal(r.body.api_sig, nodeMd5(`api_key${KEY}methodauth.getToken${SECRET}`));
  assert.ok(!JSON.stringify(r.body).includes(SECRET));
});

test("POST /v1/lastfm/sign errors: off, rate limit, bad JSON, wrong key, other method", async () => {
  assert.deepEqual(await sign(env({ LASTFM_SHARED_SECRET: undefined }), { params: {} }), { status: 503, body: { error: "lastfm_off" } });
  assert.deepEqual(await sign(env({ limitOk: () => false }), { params: {} }), { status: 429, body: { error: "slow_down" } });
  assert.deepEqual(await sign(env(), "{"), { status: 400, body: { error: "bad_json" } });
  assert.deepEqual(await sign(env(), { params: { method: "auth.getToken", api_key: "x" } }), { status: 403, body: { error: "wrong_key" } });
  assert.deepEqual(await sign(env(), { params: { method: "user.getInfo", api_key: KEY } }), { status: 403, body: { error: "method_not_allowed" } });
});
