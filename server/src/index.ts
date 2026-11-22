/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// Yumeiro Cloud: a tiny wake-up server. It never sends content, only "check now" pings to a
// device's own UnifiedPush endpoint; the app then fetches what's new itself. See docs/SERVER.md.
import {
  AIRING_WAKE_DELAYS_S, FOLLOW_CHANGE_COOLDOWN_S, MAX_ROLLS, ROLL_S, SILENT_DAYS, WAKES_PER_MESSAGE, airingWindow, chunk,
  followDiff, parseFollows, parseAiring, planAiring, pushOutcome, randomToken, same, sha256, validEndpoint,
  type AiringRow, type PendingRow,
} from "./logic.ts";
import {
  FANOUT_PER_MESSAGE, buildMessage, buildWake, needsFanout, parseNotice, parseServiceAccount, parseTopics, send, sameSecret, topicFor, validFid, type Notice,
} from "./fcm.ts";
import { apiSig, signRequest } from "./lastfm.ts";
import { plan as hubPlan, shape as hubShape, shelves as hubShelves } from "./hub.ts";

export interface Env {
  DB: D1Database;
  WAKES: Queue<WakeJob>;
  /**
   * Free per-IP limit on registrations that create a new device row, so nobody can burn the
   * free plan's 100k D1 row writes/day with fake devices. Refreshes with a valid token skip it.
   * Counted at the edge; IPs are never stored.
   */
  NEW_DEVICES?: RateLimit;
  /** Per-device limit on check-ins, so one device can't loop writes. Legit apps check in a few times a day. */
  DEVICE_SYNC?: RateLimit;
  /** Secret (wrangler secret put ADMIN_TOKEN): bearer for POST /v1/admin/notify. Unset = admin off. */
  ADMIN_TOKEN?: string;
  /** Secret (wrangler secret put FCM_SERVICE_ACCOUNT): the Firebase service-account JSON. Never in the app. */
  FCM_SERVICE_ACCOUNT?: string;
  /** SEC-LFM (ADR-048): Yumeiro's Last.fm API key (public; the app has the same one) and its shared secret (wrangler secret put). */
  LASTFM_API_KEY?: string;
  LASTFM_SHARED_SECRET?: string;
  /** Per-IP limit on Last.fm signatures (a phone needs a few a minute). */
  LASTFM_SIGN?: RateLimit;
  /** E1-A: TMDB API read token (wrangler secret put TMDB_TOKEN). Unset = hub catalog off (503). */
  TMDB_TOKEN?: string;
  /** E1-A: per-IP limit on hub catalog calls (cache hits are not counted). */
  HUB?: RateLimit;
}

/** Airing nudges (UnifiedPush endpoints) or NOTIFY-1 direct copies of a broadcast (FCM ids). */
// endpoints: UnifiedPush wake-ups. fcm: a broadcast copied to phones the topic misses (NOTIFY-1).
// wake: F-AIR (ADR-043) data-only "check now" to Firebase phones that follow a show that just aired.
interface WakeJob { endpoints?: string[]; fcm?: { fids: string[]; notice: Notice; sent: number }; wake?: { fids: string[] } }

const DAY = 86_400;
const now = () => Math.floor(Date.now() / 1000);

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store", "x-content-type-options": "nosniff" },
  });
}
const fail = (status: number, error: string) => json({ error }, status);

export default {
  async fetch(req: Request, env: Env, ctx?: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    try {
      if (url.pathname.startsWith("/v1/hub/") && req.method === "GET") return await hub(req, env, url, ctx);
      if (url.pathname === "/v1/health" && req.method === "GET") return await health(env);
      if (url.pathname === "/v1/devices" && req.method === "POST") return await register(req, env);
      if (url.pathname === "/v1/devices" && req.method === "DELETE") return await unregister(req, env);
      if (url.pathname === "/v1/fcm/devices" && req.method === "POST") return await fcmRegister(req, env);
      if (url.pathname === "/v1/fcm/devices" && req.method === "DELETE") return await fcmUnregister(req, env);
      if (url.pathname === "/v1/lastfm/sign" && req.method === "POST") return await lastfmSign(req, env);
      if (url.pathname === "/v1/admin/notify" && req.method === "POST") return await adminNotify(req, env);
      if (url.pathname === "/v1/admin/fcm/stats" && req.method === "GET") return await adminStats(req, env);
      return fail(404, "not_found");
    } catch (e) {
      console.error("fetch", url.pathname, e);
      return fail(500, "server_error");
    }
  },

  async scheduled(ev: ScheduledController, env: Env): Promise<void> {
    await airingWakes(env);
    const d = new Date(ev.scheduledTime);
    if (d.getUTCHours() === 3 && d.getUTCMinutes() < 10) await prune(env);
  },

  async queue(batch: MessageBatch<WakeJob>, env: Env): Promise<void> {
    for (const msg of batch.messages) {
      if (msg.body.fcm) { await fanoutSend(env, msg.body.fcm); msg.ack(); continue; }
      if (msg.body.wake) { await wakeSend(env, msg.body.wake.fids); msg.ack(); continue; }
      const results = await Promise.all((msg.body.endpoints ?? []).map(async (ep) => [ep, await wake(ep)] as const));
      const gone = results.filter(([, r]) => r === "gone").map(([ep]) => ep);
      if (gone.length) await forget(env, gone);
      msg.ack(); // no retries: a missed nudge is fine, the app also checks on its own schedule
    }
  },
} satisfies ExportedHandler<Env, WakeJob>;

/**
 * For uptime monitors: 200 only if D1 answers and the 10-minute job ran in the last 30 minutes.
 * `cron_s` = seconds since the job last finished. One D1 read per call.
 */
/**
 * E1-A: Yumeiro Hub catalog (shelves, search, title pages) from TMDB, cached at the edge
 * (6 h shelves, 1 h search, 24 h titles). Metadata only; the token never leaves the Worker.
 */
async function hub(req: Request, env: Env, url: URL, ctx?: ExecutionContext): Promise<Response> {
  const p = hubPlan(url.pathname, url.searchParams);
  if ("error" in p) return fail(p.error === "not_found" ? 404 : 400, p.error);
  if (url.pathname === "/v1/hub/shelves") return cached(json(hubShelves()), p.ttl);
  if (!env.TMDB_TOKEN) return fail(503, "hub_off");
  const key = new Request(`https://hub.cache${url.pathname}?${[...url.searchParams].sort().map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&")}`);
  const cache = (globalThis as { caches?: { default?: Cache } }).caches?.default;
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) return hit;
  if (env.HUB && !(await env.HUB.limit({ key: `hub:${req.headers.get("cf-connecting-ip") ?? "?"}` })).success) return fail(429, "slow_down");
  const r = await fetch(`https://api.themoviedb.org/3${p.tmdb}`, { headers: { authorization: `Bearer ${env.TMDB_TOKEN}`, accept: "application/json" } });
  if (r.status === 404) return fail(404, "not_found");
  if (!r.ok) return fail(502, "upstream");
  const body = hubShape(url.pathname, await r.json<Record<string, unknown>>(), p.kind);
  if (body && typeof body === "object" && "error" in body) return fail(404, "not_found");
  const res = cached(json(body), p.ttl);
  if (cache) { const put = cache.put(key, res.clone()); if (ctx) ctx.waitUntil(put); else await put; }
  return res;
}

function cached(r: Response, ttl: number): Response {
  const h = new Headers(r.headers);
  h.set("cache-control", `public, max-age=${ttl}`);
  return new Response(r.body, { status: r.status, headers: h });
}

/** SEC-LFM (ADR-048): signs one of the app's four Last.fm calls for the built-in key. Never logs parameters. */
async function lastfmSign(req: Request, env: Env): Promise<Response> {
  if (!env.LASTFM_SHARED_SECRET || !env.LASTFM_API_KEY) return fail(503, "lastfm_off");
  if (env.LASTFM_SIGN && !(await env.LASTFM_SIGN.limit({ key: `lfm:${req.headers.get("cf-connecting-ip") ?? "?"}` })).success) return fail(429, "slow_down");
  if (Number(req.headers.get("content-length") ?? "0") > 40_000) return fail(413, "too_large");
  let body: unknown;
  try { body = await req.json(); } catch { return fail(400, "bad_json"); }
  const r = signRequest(body, env.LASTFM_API_KEY);
  if ("error" in r) return fail(r.error === "too_large" ? 413 : r.error === "bad_params" ? 400 : 403, r.error);
  return json({ api_sig: apiSig(r.params, env.LASTFM_SHARED_SECRET) });
}

async function health(env: Env): Promise<Response> {
  const last = Number(await getState(env, "aired_to").catch(() => null));
  const ago = last > 0 ? now() - last : null;
  const ok = ago != null && ago < 1800;
  return json({ ok, v: 2, cron_s: ago }, ok ? 200 : 503);
}

// ---- devices ----

async function register(req: Request, env: Env): Promise<Response> {
  const body = await req.json<Record<string, unknown>>().catch(() => null);
  if (!body) return fail(400, "bad_json");
  const endpoint = validEndpoint(body.endpoint);
  if (!endpoint) return fail(400, "bad_endpoint");
  const follows = parseFollows(body.anilist);
  const version = (await sha256(follows.join(","))).slice(0, 16);
  const t = now();

  // An existing device proves itself with its token; knowing the endpoint is also proof
  // (it's the device's private push address), so a reinstall can take its row back.
  let row: { id: string; follows_v: string; seen: number; created: number } | null = null;
  const auth = bearer(req);
  if (auth) {
    const r = await env.DB.prepare("SELECT id, token_hash, follows_v, seen, created FROM devices WHERE id = ?")
      .bind(auth.id).first<{ id: string; token_hash: string; follows_v: string; seen: number; created: number }>();
    if (r && same(r.token_hash, await sha256(auth.token))) row = r;
  }
  if (row && env.DEVICE_SYNC && !(await env.DEVICE_SYNC.limit({ key: row.id })).success) return fail(429, "slow_down");
  let token: string | null = null;
  if (row) {
    // Endpoint may have changed (distributor switch). Drop any other row that owned the new one.
    await env.DB.batch([
      env.DB.prepare("DELETE FROM follows WHERE device_id IN (SELECT id FROM devices WHERE endpoint = ? AND id <> ?)").bind(endpoint, row.id),
      env.DB.prepare("DELETE FROM devices WHERE endpoint = ? AND id <> ?").bind(endpoint, row.id),
      env.DB.prepare("UPDATE devices SET endpoint = ?, seen = ? WHERE id = ? AND (endpoint <> ? OR seen < ?)")
        .bind(endpoint, t, row.id, endpoint, t - DAY),
    ]);
  } else {
    if (env.NEW_DEVICES && !(await env.NEW_DEVICES.limit({ key: req.headers.get("cf-connecting-ip") ?? "?" })).success) {
      return fail(429, "slow_down");
    }
    const old = await env.DB.prepare("SELECT id, created FROM devices WHERE endpoint = ?").bind(endpoint).first<{ id: string; created: number }>();
    const id = old?.id ?? randomToken(12);
    token = randomToken();
    const th = await sha256(token);
    if (old) {
      await env.DB.prepare("UPDATE devices SET token_hash = ?, seen = ? WHERE id = ?").bind(th, t, id).run();
      row = { id, follows_v: "?", seen: t, created: old.created };
    } else {
      await env.DB.prepare("INSERT INTO devices (id, token_hash, endpoint, follows_v, created, seen) VALUES (?, ?, ?, '', ?, ?)")
        .bind(id, th, endpoint, t, t).run();
      row = { id, follows_v: "", seen: t, created: t };
    }
  }

  const followsPending = await storeFollows(env, UP, row.id, row.follows_v, version, follows, t);
  const airingWrites = await storeAiring(env, row.id, t - row.created, parseAiring(body.airing, follows, t), t);
  return json({ id: row.id, token, follows: follows.length, airing: airingWrites, follows_pending: followsPending });
}

// ---- follow lists and the shared airing timetable (UnifiedPush and Firebase phones alike) ----

interface FollowStore { devices: string; follows: string; col: string; cooldownKey: (id: string) => string }
const UP: FollowStore = { devices: "devices", follows: "follows", col: "device_id", cooldownKey: (id) => id };
// F-AIR: Firebase phones keep their own list; their cooldown/vote key is "fcm:<id>" so it never collides.
const FCM: FollowStore = { devices: "fcm_devices", follows: "fcm_follows", col: "fcm_id", cooldownKey: (id) => `fcm:${id}` };

/**
 * Follow list: only changed rows are written, and at most once per 10 minutes per device, so one
 * token can't burn the free plan's daily D1 write budget (that would stop push for everyone).
 * Returns true when the change was held back (the app sends the list again on its next check-in).
 */
async function storeFollows(env: Env, k: FollowStore, id: string, storedV: string, version: string, follows: number[], t: number): Promise<boolean> {
  if (storedV === version) return false;
  const key = k.cooldownKey(id);
  const last = await env.DB.prepare("SELECT at FROM follow_changes WHERE device_id = ?").bind(key).first<{ at: number }>();
  if (last && t - last.at < FOLLOW_CHANGE_COOLDOWN_S && storedV !== "") return true;
  const cur = (await env.DB.prepare(`SELECT media_id FROM ${k.follows} WHERE ${k.col} = ?`).bind(id).all<{ media_id: number }>())
    .results.map((r) => r.media_id);
  const { add, remove } = followDiff(cur, follows);
  const stmts: D1PreparedStatement[] = [];
  for (const ids of chunk(remove, 90)) {
    stmts.push(env.DB.prepare(`DELETE FROM ${k.follows} WHERE ${k.col} = ? AND media_id IN (${ids.map(() => "?").join(",")})`).bind(id, ...ids));
  }
  for (const ids of chunk(add, 45)) {
    stmts.push(env.DB.prepare(
      `INSERT OR IGNORE INTO ${k.follows} (media_id, ${k.col}) VALUES ${ids.map(() => "(?, ?)").join(",")}`,
    ).bind(...ids.flatMap((m) => [m, id])));
  }
  stmts.push(env.DB.prepare(`UPDATE ${k.devices} SET follows_v = ? WHERE id = ?`).bind(version, id));
  stmts.push(env.DB.prepare("INSERT INTO follow_changes (device_id, at) VALUES (?, ?) ON CONFLICT(device_id) DO UPDATE SET at = excluded.at").bind(key, t));
  await env.DB.batch(stmts);
  return false;
}

/** Shared timetable: one phone can't move a confirmed time for everyone (see planAiring). */
async function storeAiring(env: Env, voter: string, ageS: number, reports: [number, number][], t: number): Promise<number> {
  if (!reports.length) return 0;
  const rows: AiringRow[] = [];
  const pending: PendingRow[] = [];
  for (const ids of chunk(reports.map(([m]) => m), 90)) {
    const q = ids.map(() => "?").join(",");
    rows.push(...(await env.DB.prepare(`SELECT media_id, at, rolls FROM airing WHERE media_id IN (${q})`).bind(...ids).all<AiringRow>()).results);
    pending.push(...(await env.DB.prepare(`SELECT media_id, at, device_id FROM airing_pending WHERE media_id IN (${q})`).bind(...ids).all<PendingRow>()).results);
  }
  const ops = planAiring(reports, rows, pending, voter, ageS);
  const stmts: D1PreparedStatement[] = [];
  for (const o of ops) {
    if (o.op === "set") {
      stmts.push(env.DB.prepare(
        "INSERT INTO airing (media_id, at, rolls) VALUES (?, ?, 0) ON CONFLICT(media_id) DO UPDATE SET at = excluded.at, rolls = 0",
      ).bind(o.media_id, o.at));
      if (pending.some((p) => p.media_id === o.media_id)) stmts.push(env.DB.prepare("DELETE FROM airing_pending WHERE media_id = ?").bind(o.media_id));
    } else {
      stmts.push(env.DB.prepare(
        `INSERT INTO airing_pending (media_id, at, device_id, reported) VALUES (?, ?, ?, ?)
         ON CONFLICT(media_id) DO UPDATE SET at = excluded.at, device_id = excluded.device_id, reported = excluded.reported`,
      ).bind(o.media_id, o.at, voter, t));
    }
  }
  for (const b of chunk(stmts, 50)) await env.DB.batch(b);
  return ops.length;
}

async function unregister(req: Request, env: Env): Promise<Response> {
  const auth = bearer(req);
  if (!auth) return fail(401, "no_token");
  const r = await env.DB.prepare("SELECT token_hash FROM devices WHERE id = ?").bind(auth.id).first<{ token_hash: string }>();
  if (!r) return json({ ok: true }); // already gone
  if (!same(r.token_hash, await sha256(auth.token))) return fail(403, "bad_token");
  await env.DB.batch([
    env.DB.prepare("DELETE FROM follows WHERE device_id = ?").bind(auth.id),
    env.DB.prepare("DELETE FROM follow_changes WHERE device_id = ?").bind(auth.id),
    env.DB.prepare("DELETE FROM devices WHERE id = ?").bind(auth.id),
  ]);
  return json({ ok: true });
}

/** "Authorization: Bearer <id>.<token>" */
function bearer(req: Request): { id: string; token: string } | null {
  const m = /^Bearer ([A-Za-z0-9_-]{8,64})\.([A-Za-z0-9_-]{16,128})$/.exec(req.headers.get("authorization") ?? "");
  return m ? { id: m[1], token: m[2] } : null;
}

async function forget(env: Env, endpoints: string[]): Promise<void> {
  const q = endpoints.map(() => "?").join(",");
  await env.DB.batch([
    env.DB.prepare(`DELETE FROM follows WHERE device_id IN (SELECT id FROM devices WHERE endpoint IN (${q}))`).bind(...endpoints),
    env.DB.prepare(`DELETE FROM devices WHERE endpoint IN (${q})`).bind(...endpoints),
  ]);
}

async function prune(env: Env): Promise<void> {
  const cut = now() - SILENT_DAYS * DAY;
  await env.DB.batch([
    env.DB.prepare("DELETE FROM follows WHERE device_id IN (SELECT id FROM devices WHERE seen < ?)").bind(cut),
    env.DB.prepare("DELETE FROM devices WHERE seen < ?").bind(cut),
    env.DB.prepare("DELETE FROM fcm_follows WHERE fcm_id IN (SELECT id FROM fcm_devices WHERE seen < ?)").bind(cut),
    env.DB.prepare("DELETE FROM fcm_devices WHERE seen < ?").bind(cut),
    env.DB.prepare("DELETE FROM fcm_follows WHERE fcm_id NOT IN (SELECT id FROM fcm_devices)"), // rows of a moved FID
    env.DB.prepare("DELETE FROM airing WHERE media_id NOT IN (SELECT media_id FROM follows UNION SELECT media_id FROM fcm_follows)"),
    env.DB.prepare(
      "DELETE FROM follow_changes WHERE device_id NOT IN (SELECT id FROM devices) AND device_id NOT IN (SELECT 'fcm:' || id FROM fcm_devices)",
    ),
    env.DB.prepare("DELETE FROM airing_pending WHERE reported < ? OR media_id NOT IN (SELECT media_id FROM airing)").bind(now() - 15 * DAY),
  ]);
}

// ---- FCM devices (instant notices) ----
// Same credential scheme as /v1/devices: the server hands out "<id>.<token>", stores only the
// token's SHA-256. Knowing the FID (private to the phone and Firebase) also lets a reinstall reclaim its row.

async function fcmRegister(req: Request, env: Env): Promise<Response> {
  const body = await req.json<Record<string, unknown>>().catch(() => null);
  if (!body) return fail(400, "bad_json");
  const fid = validFid(body.fid);
  if (!fid) return fail(400, "bad_fid");
  const topics = parseTopics(body.topics).join(",");
  // NOTIFY-1: what the switches ask for (new apps). null = an older app that does not say.
  const want = Array.isArray(body.want) ? parseTopics(body.want).join(",") : null;
  const version = typeof body.app_version === "number" && Number.isInteger(body.app_version) && body.app_version > 0 && body.app_version < 2e9
    ? body.app_version : 0;
  const channel = body.channel === "preview" || body.channel === "debug" ? body.channel : "release";
  // F-AIR (ADR-043): new apps also send the shows they follow and their airing times. An older app
  // sends no `anilist`, and then its stored list is left alone (never wiped by a check-in).
  const follows = Array.isArray(body.anilist) ? parseFollows(body.anilist) : null;
  const t = now();
  const withFollows = async (id: string, storedV: string, created: number, token: string | null) => {
    if (!follows) return json({ id, token });
    const version = (await sha256(follows.join(","))).slice(0, 16);
    const pending = await storeFollows(env, FCM, id, storedV, version, follows, t);
    const airing = await storeAiring(env, FCM.cooldownKey(id), t - created, parseAiring(body.airing, follows, t), t);
    return json({ id, token, follows: follows.length, airing, follows_pending: pending });
  };

  let id: string | null = null;
  let stored = { v: "", created: t };
  const auth = bearer(req);
  if (auth) {
    const r = await env.DB.prepare("SELECT id, token_hash, follows_v, created FROM fcm_devices WHERE id = ?").bind(auth.id)
      .first<{ id: string; token_hash: string; follows_v: string; created: number }>();
    if (r && same(r.token_hash, await sha256(auth.token))) { id = r.id; stored = { v: r.follows_v, created: r.created }; }
    else if (r) return fail(403, "bad_token");
  }
  if (id) {
    if (env.DEVICE_SYNC && !(await env.DEVICE_SYNC.limit({ key: `fcm:${id}` })).success) return fail(429, "slow_down");
    await env.DB.batch([
      env.DB.prepare("DELETE FROM fcm_follows WHERE fcm_id IN (SELECT id FROM fcm_devices WHERE fid = ? AND id <> ?)").bind(fid, id),
      env.DB.prepare("DELETE FROM fcm_devices WHERE fid = ? AND id <> ?").bind(fid, id), // FID moved to this row
      env.DB.prepare("UPDATE fcm_devices SET fid = ?, topics = ?, want = ?, app_version = ?, channel = ?, seen = ? WHERE id = ?")
        .bind(fid, topics, want, version, channel, t, id),
    ]);
    return withFollows(id, stored.v, stored.created, null);
  }
  if (env.NEW_DEVICES && !(await env.NEW_DEVICES.limit({ key: req.headers.get("cf-connecting-ip") ?? "?" })).success) {
    return fail(429, "slow_down");
  }
  const old = await env.DB.prepare("SELECT id, follows_v, created FROM fcm_devices WHERE fid = ?").bind(fid)
    .first<{ id: string; follows_v: string; created: number }>();
  const newId = old?.id ?? randomToken(12);
  const token = randomToken();
  const th = await sha256(token);
  await env.DB.prepare(
    `INSERT INTO fcm_devices (id, token_hash, fid, topics, want, app_version, channel, created, seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET token_hash = excluded.token_hash, fid = excluded.fid, topics = excluded.topics, want = excluded.want,
       app_version = excluded.app_version, channel = excluded.channel, seen = excluded.seen`,
  ).bind(newId, th, fid, topics, want, version, channel, t, t).run();
  return withFollows(newId, old ? "?" : "", old?.created ?? t, token);
}

async function fcmUnregister(req: Request, env: Env): Promise<Response> {
  const auth = bearer(req);
  if (!auth) return fail(401, "no_token");
  const r = await env.DB.prepare("SELECT token_hash FROM fcm_devices WHERE id = ?").bind(auth.id).first<{ token_hash: string }>();
  if (!r) return json({ ok: true });
  if (!same(r.token_hash, await sha256(auth.token))) return fail(403, "bad_token");
  await env.DB.batch([
    env.DB.prepare("DELETE FROM fcm_follows WHERE fcm_id = ?").bind(auth.id),
    env.DB.prepare("DELETE FROM follow_changes WHERE device_id = ?").bind(FCM.cooldownKey(auth.id)),
    env.DB.prepare("DELETE FROM fcm_devices WHERE id = ?").bind(auth.id),
  ]);
  return json({ ok: true });
}

// ---- admin: send notices ----

/** "Authorization: Bearer <ADMIN_TOKEN>", rate-limited per IP so the token can't be guessed. */
async function admin(req: Request, env: Env): Promise<Response | null> {
  if (!env.ADMIN_TOKEN || env.ADMIN_TOKEN.length < 32) return fail(503, "admin_not_configured");
  const ip = req.headers.get("cf-connecting-ip") ?? "?";
  if (env.NEW_DEVICES && !(await env.NEW_DEVICES.limit({ key: `admin:${ip}` })).success) return fail(429, "slow_down");
  const m = /^Bearer (\S{32,256})$/.exec(req.headers.get("authorization") ?? "");
  if (!m || !(await sameSecret(m[1], env.ADMIN_TOKEN))) return fail(401, "unauthorized");
  return null;
}

async function adminNotify(req: Request, env: Env): Promise<Response> {
  const denied = await admin(req, env);
  if (denied) return denied;
  const sa = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  if (!sa) return fail(503, "fcm_not_configured");
  const n = parseNotice(await req.json<Record<string, unknown>>().catch(() => null), () => randomToken(12));
  if ("error" in n) return fail(400, n.error);
  const t = now();

  let target: { topic: string } | { fid: string };
  if (n.kind === "direct") {
    const row = await env.DB.prepare("SELECT fid FROM fcm_devices WHERE id = ?").bind(n.device_id!).first<{ fid: string }>();
    if (!row) return fail(404, "unknown_device");
    target = { fid: row.fid };
  } else {
    target = { topic: topicFor(n.kind, n.channel) };
  }
  let r: Awaited<ReturnType<typeof send>>;
  try {
    r = await send(sa, buildMessage(n as Notice, target, t), n.dry_run, t);
  } catch (e) {
    // OAuth refused (wrong / revoked key, clock) or FCM unreachable: say so instead of a bare 500.
    console.error("fcm send", e instanceof Error ? e.message : "error");
    return json({ ok: false, error: e instanceof Error && e.message.startsWith("oauth_") ? e.message : "fcm_unreachable", id: n.id }, 502);
  }
  if (r.outcome === "gone" && n.kind === "direct") {
    await env.DB.prepare("DELETE FROM fcm_devices WHERE id = ?").bind(n.device_id!).run();
    return json({ ok: false, error: "device_gone", id: n.id }, 410);
  }
  if (r.outcome !== "ok") return json({ ok: false, error: r.error ?? "send_failed", fcm_status: r.status, id: n.id }, r.outcome === "retry" ? 503 : 502);
  // NOTIFY-1: phones the topic does not reach get their own copy (same id: the app shows it once).
  let fanout = 0;
  if (n.kind !== "direct") {
    const kind = n.kind;
    const rows = await env.DB.prepare("SELECT fid, topics, want FROM fcm_devices WHERE channel = ?").bind(n.channel)
      .all<{ fid: string; topics: string; want: string | null }>();
    const fids = rows.results.filter((d) => needsFanout(kind, n.channel, d.topics, d.want)).map((d) => d.fid);
    fanout = fids.length;
    if (fids.length && !n.dry_run) {
      const msgs = chunk(fids, FANOUT_PER_MESSAGE).map((g) => ({ body: { fcm: { fids: g, notice: n as Notice, sent: t } } }));
      for (const b of chunk(msgs, 100)) await env.WAKES.sendBatch(b);
    }
  }
  return json({ ok: true, id: n.id, dry_run: n.dry_run, target: "topic" in target ? target.topic : "device", name: r.name ?? null, fanout });
}

/** Device counts per channel/topic, for the admin. No ids or FIDs leave the Worker. */
async function adminStats(req: Request, env: Env): Promise<Response> {
  const denied = await admin(req, env);
  if (denied) return denied;
  const rows = await env.DB.prepare("SELECT channel, topics, COUNT(*) AS n FROM fcm_devices GROUP BY channel, topics")
    .all<{ channel: string; topics: string; n: number }>();
  const topics: Record<string, number> = {};
  let devices = 0;
  for (const r of rows.results) {
    devices += r.n;
    for (const tp of r.topics.split(",").filter(Boolean)) topics[tp] = (topics[tp] ?? 0) + r.n;
  }
  return json({ devices, topics, fcm: parseServiceAccount(env.FCM_SERVICE_ACCOUNT) != null });
}

// ---- wake-ups ----

/** Episodes that aired since the last run → wake the devices that follow those shows. No outside calls. */
async function airingWakes(env: Env): Promise<void> {
  const t = now();
  const last = await getState(env, "aired_to");
  const [from, to] = airingWindow(last == null ? null : Number(last), t);
  const due = await env.DB.prepare("SELECT media_id FROM airing WHERE at > ? AND at <= ?").bind(from, to).all<{ media_id: number }>();
  const media = due.results.map((r) => r.media_id);
  await env.DB.batch([
    // Guess next week's episode until a phone reports the real time; give up after MAX_ROLLS guesses.
    env.DB.prepare("DELETE FROM airing WHERE at > ? AND at <= ? AND rolls >= ?").bind(from, to, MAX_ROLLS),
    env.DB.prepare("UPDATE airing SET at = at + ?, rolls = rolls + 1 WHERE at > ? AND at <= ?").bind(ROLL_S, from, to),
    env.DB.prepare("INSERT INTO state (k, v) VALUES ('aired_to', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v").bind(String(to)),
  ]);
  if (media.length === 0) return;
  const endpoints = new Set<string>();
  for (const ids of chunk(media, 90)) { // D1 allows 100 bound parameters per query
    const r = await env.DB.prepare(
      `SELECT DISTINCT d.endpoint FROM follows f JOIN devices d ON d.id = f.device_id WHERE f.media_id IN (${ids.map(() => "?").join(",")})`,
    ).bind(...ids).all<{ endpoint: string }>();
    for (const x of r.results) endpoints.add(x.endpoint);
  }
  await enqueue(env, [...endpoints], AIRING_WAKE_DELAYS_S);
  // F-AIR (ADR-043): Firebase phones that follow these shows get the same nudge through FCM.
  const fids = new Set<string>();
  for (const ids of chunk(media, 90)) {
    const r = await env.DB.prepare(
      `SELECT DISTINCT d.fid FROM fcm_follows f JOIN fcm_devices d ON d.id = f.fcm_id WHERE f.media_id IN (${ids.map(() => "?").join(",")})`,
    ).bind(...ids).all<{ fid: string }>();
    for (const x of r.results) fids.add(x.fid);
  }
  const msgs: MessageSendRequest<WakeJob>[] = [];
  for (const g of chunk([...fids], FANOUT_PER_MESSAGE)) for (const d of AIRING_WAKE_DELAYS_S) msgs.push({ body: { wake: { fids: g } }, delaySeconds: d });
  for (const b of chunk(msgs, 100)) await env.WAKES.sendBatch(b);
}

async function enqueue(env: Env, endpoints: string[], delays: number[]): Promise<number> {
  const msgs: MessageSendRequest<WakeJob>[] = [];
  for (const group of chunk(endpoints, WAKES_PER_MESSAGE)) {
    for (const d of delays) msgs.push({ body: { endpoints: group }, delaySeconds: d });
  }
  for (const b of chunk(msgs, 100)) await env.WAKES.sendBatch(b); // 100 messages per sendBatch
  return msgs.length;
}

/** RFC 8030 push with a 1-byte body. The app ignores push contents by design; it's only a nudge. */
async function wake(endpoint: string): Promise<"ok" | "gone" | "retry"> {
  try {
    const r = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "text/plain", ttl: "21600", urgency: "normal" },
      body: "w",
      redirect: "manual",
    });
    return pushOutcome(r.status);
  } catch {
    return "retry";
  }
}

/** NOTIFY-1: one queue message = up to FANOUT_PER_MESSAGE direct sends. A gone FID drops its row. */
async function fanoutSend(env: Env, job: { fids: string[]; notice: Notice; sent: number }): Promise<void> {
  const sa = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  if (!sa) return;
  const gone: string[] = [];
  for (const fid of job.fids) {
    try {
      const r = await send(sa, buildMessage(job.notice, { fid }, job.sent), false, now());
      if (r.outcome === "gone") gone.push(fid);
    } catch (e) {
      console.error("fanout", e instanceof Error ? e.message : "error");
      return; // OAuth / FCM down: the rest of this group is skipped, the topic copy still went out
    }
  }
  for (const ids of chunk(gone, 90)) {
    await env.DB.prepare(`DELETE FROM fcm_devices WHERE fid IN (${ids.map(() => "?").join(",")})`).bind(...ids).run();
  }
}

/** F-AIR: data-only wake per FID (the app runs its own new-episode check). A gone FID drops its row and follows. */
async function wakeSend(env: Env, fids: string[]): Promise<void> {
  const sa = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  if (!sa) return;
  const gone: string[] = [];
  for (const fid of fids) {
    try {
      if ((await send(sa, buildWake({ fid }), false, now())).outcome === "gone") gone.push(fid);
    } catch (e) {
      console.error("wake", e instanceof Error ? e.message : "error");
      break; // OAuth / FCM down: the app's own 2-hour check still runs
    }
  }
  for (const ids of chunk(gone, 90)) {
    const q = ids.map(() => "?").join(",");
    await env.DB.batch([
      env.DB.prepare(`DELETE FROM fcm_follows WHERE fcm_id IN (SELECT id FROM fcm_devices WHERE fid IN (${q}))`).bind(...ids),
      env.DB.prepare(`DELETE FROM fcm_devices WHERE fid IN (${q})`).bind(...ids),
    ]);
  }
}

async function getState(env: Env, k: string): Promise<string | null> {
  return (await env.DB.prepare("SELECT v FROM state WHERE k = ?").bind(k).first<{ v: string }>())?.v ?? null;
}
