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
  buildMessage, parseNotice, parseServiceAccount, parseTopics, send, sameSecret, topicFor, validFid, type Notice,
} from "./fcm.ts";

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
}

interface WakeJob { endpoints: string[] }

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
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    try {
      if (url.pathname === "/v1/health" && req.method === "GET") return await health(env);
      if (url.pathname === "/v1/devices" && req.method === "POST") return await register(req, env);
      if (url.pathname === "/v1/devices" && req.method === "DELETE") return await unregister(req, env);
      if (url.pathname === "/v1/fcm/devices" && req.method === "POST") return await fcmRegister(req, env);
      if (url.pathname === "/v1/fcm/devices" && req.method === "DELETE") return await fcmUnregister(req, env);
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
      const results = await Promise.all(msg.body.endpoints.map(async (ep) => [ep, await wake(ep)] as const));
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

  // Follow list: only changed rows are written, and at most once per 10 minutes per device, so one
  // token can't burn the free plan's daily D1 write budget (that would stop push for everyone).
  let followsPending = false;
  if (row.follows_v !== version) {
    const last = await env.DB.prepare("SELECT at FROM follow_changes WHERE device_id = ?").bind(row.id).first<{ at: number }>();
    if (last && t - last.at < FOLLOW_CHANGE_COOLDOWN_S && row.follows_v !== "") {
      followsPending = true; // the app sends the list again on its next check-in
    } else {
      const cur = (await env.DB.prepare("SELECT media_id FROM follows WHERE device_id = ?").bind(row.id).all<{ media_id: number }>())
        .results.map((r) => r.media_id);
      const { add, remove } = followDiff(cur, follows);
      const stmts: D1PreparedStatement[] = [];
      for (const ids of chunk(remove, 90)) {
        stmts.push(env.DB.prepare(`DELETE FROM follows WHERE device_id = ? AND media_id IN (${ids.map(() => "?").join(",")})`).bind(row.id, ...ids));
      }
      for (const ids of chunk(add, 45)) {
        stmts.push(env.DB.prepare(
          `INSERT OR IGNORE INTO follows (media_id, device_id) VALUES ${ids.map(() => "(?, ?)").join(",")}`,
        ).bind(...ids.flatMap((m) => [m, row!.id])));
      }
      stmts.push(env.DB.prepare("UPDATE devices SET follows_v = ? WHERE id = ?").bind(version, row.id));
      stmts.push(env.DB.prepare("INSERT INTO follow_changes (device_id, at) VALUES (?, ?) ON CONFLICT(device_id) DO UPDATE SET at = excluded.at").bind(row.id, t));
      await env.DB.batch(stmts);
    }
  }

  // Shared timetable: one phone can't move a confirmed time for everyone (see planAiring).
  const reports = parseAiring(body.airing, follows, t);
  let airingWrites = 0;
  if (reports.length) {
    const rows: AiringRow[] = [];
    const pending: PendingRow[] = [];
    for (const ids of chunk(reports.map(([m]) => m), 90)) {
      const q = ids.map(() => "?").join(",");
      rows.push(...(await env.DB.prepare(`SELECT media_id, at, rolls FROM airing WHERE media_id IN (${q})`).bind(...ids).all<AiringRow>()).results);
      pending.push(...(await env.DB.prepare(`SELECT media_id, at, device_id FROM airing_pending WHERE media_id IN (${q})`).bind(...ids).all<PendingRow>()).results);
    }
    const ops = planAiring(reports, rows, pending, row.id, t - row.created);
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
        ).bind(o.media_id, o.at, row.id, t));
      }
    }
    for (const b of chunk(stmts, 50)) await env.DB.batch(b);
    airingWrites = ops.length;
  }
  return json({ id: row.id, token, follows: follows.length, airing: airingWrites, follows_pending: followsPending });
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
    env.DB.prepare("DELETE FROM airing WHERE media_id NOT IN (SELECT media_id FROM follows)"),
    env.DB.prepare("DELETE FROM fcm_devices WHERE seen < ?").bind(cut),
    env.DB.prepare("DELETE FROM follow_changes WHERE device_id NOT IN (SELECT id FROM devices)"),
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
  const version = typeof body.app_version === "number" && Number.isInteger(body.app_version) && body.app_version > 0 && body.app_version < 2e9
    ? body.app_version : 0;
  const channel = body.channel === "preview" || body.channel === "debug" ? body.channel : "release";
  const t = now();

  let id: string | null = null;
  const auth = bearer(req);
  if (auth) {
    const r = await env.DB.prepare("SELECT id, token_hash FROM fcm_devices WHERE id = ?").bind(auth.id)
      .first<{ id: string; token_hash: string }>();
    if (r && same(r.token_hash, await sha256(auth.token))) id = r.id;
    else if (r) return fail(403, "bad_token");
  }
  if (id) {
    if (env.DEVICE_SYNC && !(await env.DEVICE_SYNC.limit({ key: `fcm:${id}` })).success) return fail(429, "slow_down");
    await env.DB.batch([
      env.DB.prepare("DELETE FROM fcm_devices WHERE fid = ? AND id <> ?").bind(fid, id), // FID moved to this row
      env.DB.prepare("UPDATE fcm_devices SET fid = ?, topics = ?, app_version = ?, channel = ?, seen = ? WHERE id = ?")
        .bind(fid, topics, version, channel, t, id),
    ]);
    return json({ id, token: null });
  }
  if (env.NEW_DEVICES && !(await env.NEW_DEVICES.limit({ key: req.headers.get("cf-connecting-ip") ?? "?" })).success) {
    return fail(429, "slow_down");
  }
  const old = await env.DB.prepare("SELECT id FROM fcm_devices WHERE fid = ?").bind(fid).first<{ id: string }>();
  const newId = old?.id ?? randomToken(12);
  const token = randomToken();
  const th = await sha256(token);
  await env.DB.prepare(
    `INSERT INTO fcm_devices (id, token_hash, fid, topics, app_version, channel, created, seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET token_hash = excluded.token_hash, fid = excluded.fid, topics = excluded.topics,
       app_version = excluded.app_version, channel = excluded.channel, seen = excluded.seen`,
  ).bind(newId, th, fid, topics, version, channel, t, t).run();
  return json({ id: newId, token });
}

async function fcmUnregister(req: Request, env: Env): Promise<Response> {
  const auth = bearer(req);
  if (!auth) return fail(401, "no_token");
  const r = await env.DB.prepare("SELECT token_hash FROM fcm_devices WHERE id = ?").bind(auth.id).first<{ token_hash: string }>();
  if (!r) return json({ ok: true });
  if (!same(r.token_hash, await sha256(auth.token))) return fail(403, "bad_token");
  await env.DB.prepare("DELETE FROM fcm_devices WHERE id = ?").bind(auth.id).run();
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
  return json({ ok: true, id: n.id, dry_run: n.dry_run, target: "topic" in target ? target.topic : "device", name: r.name ?? null });
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

async function getState(env: Env, k: string): Promise<string | null> {
  return (await env.DB.prepare("SELECT v FROM state WHERE k = ?").bind(k).first<{ v: string }>())?.v ?? null;
}
