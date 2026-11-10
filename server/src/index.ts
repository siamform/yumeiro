/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// Yumeiro Cloud: a tiny wake-up server. It never sends content, only "check now" pings to a
// device's own UnifiedPush endpoint; the app then fetches what's new itself. See docs/SERVER.md.
import {
  AIRING_WAKE_DELAYS_S, MAX_ROLLS, ROLL_S, SILENT_DAYS, WAKES_PER_MESSAGE, airingWindow, chunk, parseFollows,
  parseAiring, pushOutcome, randomToken, same, sha256, validEndpoint,
} from "./logic.ts";

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
  let row: { id: string; follows_v: string; seen: number } | null = null;
  const auth = bearer(req);
  if (auth) {
    const r = await env.DB.prepare("SELECT id, token_hash, follows_v, seen FROM devices WHERE id = ?")
      .bind(auth.id).first<{ id: string; token_hash: string; follows_v: string; seen: number }>();
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
    const old = await env.DB.prepare("SELECT id FROM devices WHERE endpoint = ?").bind(endpoint).first<{ id: string }>();
    const id = old?.id ?? randomToken(12);
    token = randomToken();
    const th = await sha256(token);
    if (old) {
      await env.DB.prepare("UPDATE devices SET token_hash = ?, seen = ? WHERE id = ?").bind(th, t, id).run();
      row = { id, follows_v: "?", seen: t };
    } else {
      await env.DB.prepare("INSERT INTO devices (id, token_hash, endpoint, follows_v, created, seen) VALUES (?, ?, ?, '', ?, ?)")
        .bind(id, th, endpoint, t, t).run();
      row = { id, follows_v: "", seen: t };
    }
  }

  if (row.follows_v !== version) {
    const stmts = [env.DB.prepare("DELETE FROM follows WHERE device_id = ?").bind(row.id)];
    for (const ids of chunk(follows, 50)) {
      stmts.push(env.DB.prepare(
        `INSERT OR IGNORE INTO follows (media_id, device_id) VALUES ${ids.map(() => "(?, ?)").join(",")}`,
      ).bind(...ids.flatMap((m) => [m, row!.id])));
    }
    stmts.push(env.DB.prepare("UPDATE devices SET follows_v = ? WHERE id = ?").bind(version, row.id));
    await env.DB.batch(stmts);
  }

  // Shared timetable: only rows whose time actually changed are written; a report resets the weekly guess.
  const airing = parseAiring(body.airing, follows, t);
  if (airing.length) {
    await env.DB.batch(chunk(airing, 45).map((ps) => env.DB.prepare(
      `INSERT INTO airing (media_id, at, rolls) VALUES ${ps.map(() => "(?, ?, 0)").join(",")}
       ON CONFLICT(media_id) DO UPDATE SET at = excluded.at, rolls = 0 WHERE airing.at <> excluded.at OR airing.rolls <> 0`,
    ).bind(...ps.flat())));
  }
  return json({ id: row.id, token, follows: follows.length, airing: airing.length });
}

async function unregister(req: Request, env: Env): Promise<Response> {
  const auth = bearer(req);
  if (!auth) return fail(401, "no_token");
  const r = await env.DB.prepare("SELECT token_hash FROM devices WHERE id = ?").bind(auth.id).first<{ token_hash: string }>();
  if (!r) return json({ ok: true }); // already gone
  if (!same(r.token_hash, await sha256(auth.token))) return fail(403, "bad_token");
  await env.DB.batch([
    env.DB.prepare("DELETE FROM follows WHERE device_id = ?").bind(auth.id),
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
  ]);
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
