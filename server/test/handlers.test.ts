/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// G12 X16-1: the HTTP handlers, the cron job and the queue consumer run against a real SQLite
// database built from schema.sql (node:sqlite), with in-memory Queue, RateLimit and fetch fakes.
// Before this, only the pure helpers in logic.ts and fcm.ts had tests.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import worker, { type Env } from "../src/index.ts";
import { sha256 } from "../src/logic.ts";

class Stmt {
  args: unknown[] = [];
  constructor(private db: DatabaseSync, readonly sql: string) {}
  bind(...a: unknown[]) { const s = new Stmt(this.db, this.sql); s.args = a; return s; }
  async first<T>() { return (this.db.prepare(this.sql).get(...(this.args as never[])) ?? null) as T | null; }
  async all<T>() { return { results: this.db.prepare(this.sql).all(...(this.args as never[])) as T[] }; }
  async run() { this.db.prepare(this.sql).run(...(this.args as never[])); return { success: true }; }
  runSync() { this.db.prepare(this.sql).run(...(this.args as never[])); }
}

function fakeEnv(opts: { newDevicesOk?: () => boolean; syncOk?: () => boolean; admin?: string } = {}) {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
  const sent: { body: { endpoints: string[] }; delaySeconds?: number }[] = [];
  const env = {
    DB: {
      prepare: (sql: string) => new Stmt(db, sql),
      batch: async (stmts: Stmt[]) => { // D1 runs a batch as one transaction
        db.exec("BEGIN");
        try { for (const s of stmts) s.runSync(); db.exec("COMMIT"); } catch (e) { db.exec("ROLLBACK"); throw e; }
        return stmts.map(() => ({ success: true }));
      },
    },
    WAKES: { sendBatch: async (m: typeof sent) => { sent.push(...m); } },
    NEW_DEVICES: { limit: async () => ({ success: opts.newDevicesOk?.() ?? true }) },
    DEVICE_SYNC: { limit: async () => ({ success: opts.syncOk?.() ?? true }) },
    ADMIN_TOKEN: opts.admin,
  } as unknown as Env;
  return { env, db, sent };
}

const EP = "https://ntfy.sh/upAbCdEf123?up=1";
const req = (method: string, path: string, body?: unknown, auth?: string) =>
  new Request("https://api.yumeiro.click" + path, {
    method,
    headers: { "content-type": "application/json", "cf-connecting-ip": "203.0.113.9", ...(auth ? { authorization: auth } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
const call = async (env: Env, r: Request) => { const res = await worker.fetch(r, env); return { status: res.status, body: await res.json() as any }; };

test("register: new device gets id + token; only the token's hash is stored", async () => {
  const { env, db } = fakeEnv();
  const r = await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [21, 5] }));
  assert.equal(r.status, 200);
  assert.match(r.body.id, /^[A-Za-z0-9_-]{8,}$/);
  assert.ok(r.body.token.length >= 16);
  const row = db.prepare("SELECT token_hash, endpoint FROM devices WHERE id = ?").get(r.body.id) as any;
  assert.equal(row.endpoint, EP);
  assert.equal(row.token_hash, await sha256(r.body.token));
  assert.notEqual(row.token_hash, r.body.token);
  const f = db.prepare("SELECT media_id FROM follows WHERE device_id = ? ORDER BY media_id").all(r.body.id) as any[];
  assert.deepEqual(f.map((x) => x.media_id), [5, 21]);
});

test("register: bad input is refused with a reason", async () => {
  const { env } = fakeEnv();
  assert.deepEqual(await call(env, new Request("https://x.y/v1/devices", { method: "POST", body: "{" })), { status: 400, body: { error: "bad_json" } });
  assert.equal((await call(env, req("POST", "/v1/devices", { endpoint: "https://127.0.0.1/x" }))).body.error, "bad_endpoint");
  assert.equal((await call(env, req("GET", "/v1/nope"))).status, 404);
});

test("register: check-in with the token keeps the id, issues no new token, follows change once per cooldown", async () => {
  const { env, db } = fakeEnv();
  const a = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [1] }))).body;
  const auth = `Bearer ${a.id}.${a.token}`;
  const b = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [1, 2] }, auth))).body;
  assert.equal(b.id, a.id);
  assert.equal(b.token, null);
  // Registration wrote the list (and the cooldown row), so a change 0 s later waits.
  assert.equal(b.follows_pending, true);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM follows WHERE device_id = ?").get(a.id) as any).n, 1);
  // Move the cooldown into the past: the list is applied.
  db.prepare("UPDATE follow_changes SET at = at - 3600").run();
  const c = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [1, 2] }, auth))).body;
  assert.equal(c.follows_pending, false);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM follows WHERE device_id = ?").get(a.id) as any).n, 2);
});

test("register: a reinstall that knows the endpoint takes its row back with a new token", async () => {
  const { env, db } = fakeEnv();
  const a = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [1] }))).body;
  const b = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [1] }))).body;
  assert.equal(b.id, a.id);
  assert.notEqual(b.token, a.token);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM devices").get() as any).n, 1);
  // The old token no longer works.
  assert.equal((await call(env, req("DELETE", "/v1/devices", undefined, `Bearer ${a.id}.${a.token}`))).status, 403);
});

test("register: per-IP limit on new devices and per-device limit on check-ins", async () => {
  const blocked = fakeEnv({ newDevicesOk: () => false });
  assert.deepEqual(await call(blocked.env, req("POST", "/v1/devices", { endpoint: EP })), { status: 429, body: { error: "slow_down" } });
  assert.equal((blocked.db.prepare("SELECT COUNT(*) AS n FROM devices").get() as any).n, 0);
  let sync = true;
  const s = fakeEnv({ syncOk: () => sync });
  const a = (await call(s.env, req("POST", "/v1/devices", { endpoint: EP }))).body;
  sync = false;
  assert.equal((await call(s.env, req("POST", "/v1/devices", { endpoint: EP }, `Bearer ${a.id}.${a.token}`))).status, 429);
});

test("unregister: needs the right token and removes the device, its follows and its cooldown row", async () => {
  const { env, db } = fakeEnv();
  const a = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [7, 8] }))).body;
  assert.equal((await call(env, req("DELETE", "/v1/devices"))).status, 401);
  assert.equal((await call(env, req("DELETE", "/v1/devices", undefined, `Bearer ${a.id}.${"x".repeat(32)}`))).status, 403);
  assert.deepEqual(await call(env, req("DELETE", "/v1/devices", undefined, `Bearer ${a.id}.${a.token}`)), { status: 200, body: { ok: true } });
  for (const t of ["devices", "follows", "follow_changes"]) assert.equal((db.prepare(`SELECT COUNT(*) AS n FROM ${t}`).get() as any).n, 0, t);
});

test("fcm register: token flow, wrong token refused, reinstall with the same FID reclaims the row", async () => {
  const { env, db } = fakeEnv();
  const fid = "c" + "A".repeat(21);
  const a = await call(env, req("POST", "/v1/fcm/devices", { fid, topics: ["announcements"], app_version: 190, channel: "preview" }));
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.ok(a.body.token);
  const bad = await call(env, req("POST", "/v1/fcm/devices", { fid }, `Bearer ${a.body.id}.${"y".repeat(32)}`));
  assert.equal(bad.status, 403);
  const ok = await call(env, req("POST", "/v1/fcm/devices", { fid, channel: "release" }, `Bearer ${a.body.id}.${a.body.token}`));
  assert.deepEqual(ok.body, { id: a.body.id, token: null });
  assert.equal((db.prepare("SELECT channel FROM fcm_devices WHERE id = ?").get(a.body.id) as any).channel, "release");
  const again = await call(env, req("POST", "/v1/fcm/devices", { fid }));
  assert.equal(again.body.id, a.body.id);
  assert.notEqual(again.body.token, a.body.token);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_devices").get() as any).n, 1);
  assert.equal((await call(env, req("DELETE", "/v1/fcm/devices", undefined, `Bearer ${a.body.id}.${a.body.token}`))).status, 403);
});

test("admin: off without a strong token, refuses a wrong one", async () => {
  assert.equal((await call(fakeEnv().env, req("GET", "/v1/admin/fcm/stats"))).status, 503);
  assert.equal((await call(fakeEnv({ admin: "short" }).env, req("GET", "/v1/admin/fcm/stats"))).status, 503);
  const strong = "z".repeat(40);
  const { env } = fakeEnv({ admin: strong });
  assert.equal((await call(env, req("GET", "/v1/admin/fcm/stats", undefined, "Bearer " + "q".repeat(40)))).status, 401);
  const s = await call(env, req("GET", "/v1/admin/fcm/stats", undefined, "Bearer " + strong));
  assert.deepEqual(s, { status: 200, body: { devices: 0, topics: {}, fcm: false } });
  // Notify without a Firebase key says so instead of failing later.
  assert.equal((await call(env, req("POST", "/v1/admin/notify", { kind: "announcement", title: "t", body: "b" }, "Bearer " + strong))).body.error, "fcm_not_configured");
});

test("cron: an episode that aired wakes its followers once, moves the time a week, health turns green", async () => {
  const { env, db, sent } = fakeEnv();
  const a = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [42] }))).body;
  assert.ok(a.id);
  assert.equal((await call(env, req("GET", "/v1/health"))).status, 503); // cron never ran
  const t = Math.floor(Date.now() / 1000);
  db.prepare("INSERT INTO state (k, v) VALUES ('aired_to', ?)").run(String(t - 600));
  db.prepare("INSERT INTO airing (media_id, at, rolls) VALUES (42, ?, 0)").run(t - 60);
  await worker.scheduled({ scheduledTime: Date.now(), cron: "*/10 * * * *", noRetry() {} } as ScheduledController, env, {} as ExecutionContext);
  assert.equal(sent.length, 1);
  assert.deepEqual(sent[0].body.endpoints, [EP]);
  const row = db.prepare("SELECT at, rolls FROM airing WHERE media_id = 42").get() as any;
  assert.equal(row.rolls, 1);
  assert.equal(row.at, t - 60 + 7 * 86_400);
  assert.equal((await call(env, req("GET", "/v1/health"))).status, 200);
  // Same window again: nothing new.
  await worker.scheduled({ scheduledTime: Date.now(), cron: "*/10 * * * *", noRetry() {} } as ScheduledController, env, {} as ExecutionContext);
  assert.equal(sent.length, 1);
});

test("queue: a gone endpoint (404/410) forgets the device; other failures keep it", async () => {
  const { env, db } = fakeEnv();
  const gone = "https://ntfy.sh/goneGoneGone1?up=1";
  const flaky = "https://ntfy.sh/flakyFlaky123?up=1";
  for (const ep of [gone, flaky]) await call(env, req("POST", "/v1/devices", { endpoint: ep, anilist: [1] }));
  const real = globalThis.fetch;
  const posted: string[] = [];
  globalThis.fetch = (async (u: string | URL | Request, init?: RequestInit) => {
    posted.push(String(u));
    assert.equal(init?.redirect, "manual"); // never follow a push server's redirect
    return new Response(null, { status: String(u) === gone ? 410 : 500 });
  }) as typeof fetch;
  let acked = 0;
  try {
    await worker.queue({ messages: [{ body: { endpoints: [gone, flaky] }, ack: () => { acked++; } }] } as never, env, {} as ExecutionContext);
  } finally { globalThis.fetch = real; }
  assert.deepEqual(posted.sort(), [flaky, gone].sort());
  assert.equal(acked, 1);
  const left = db.prepare("SELECT endpoint FROM devices").all() as any[];
  assert.deepEqual(left.map((r) => r.endpoint), [flaky]);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM follows").get() as any).n, 1);
});

test("prune: silent devices and their rows go at 03:00 UTC", async () => {
  const { env, db } = fakeEnv();
  const a = (await call(env, req("POST", "/v1/devices", { endpoint: EP, anilist: [9] }))).body;
  db.prepare("UPDATE devices SET seen = seen - 46 * 86400").run();
  db.prepare("INSERT INTO airing (media_id, at, rolls) VALUES (9, ?, 0)").run(Math.floor(Date.now() / 1000) + 86_400);
  const at3 = Date.UTC(2026, 10, 16, 3, 5);
  await worker.scheduled({ scheduledTime: at3, cron: "*/10 * * * *", noRetry() {} } as ScheduledController, env, {} as ExecutionContext);
  for (const t of ["devices", "follows", "airing", "follow_changes"]) assert.equal((db.prepare(`SELECT COUNT(*) AS n FROM ${t}`).get() as any).n, 0, t);
  assert.ok(a.id);
});

// ---- NOTIFY-1 (ADR-039): broadcasts also reach phones whose topic subscription failed ----

test("notify-1: needsFanout keeps the user's switches", async () => {
  const { needsFanout } = await import("../src/fcm.ts");
  assert.equal(needsFanout("update", "release", "announcements,maintenance,updates", null), false); // topic reaches it
  assert.equal(needsFanout("update", "release", "", null), true); // older app, "updates" is always on
  assert.equal(needsFanout("announcement", "release", "", null), false); // older app: its news switch is unknown
  assert.equal(needsFanout("announcement", "release", "", "announcements,updates"), true);
  assert.equal(needsFanout("maintenance", "release", "", "announcements,updates"), false); // switched off
  assert.equal(needsFanout("update", "preview", "updates", "preview-updates"), true); // release topic ≠ preview
});

test("notify-1: a broadcast is copied to each phone the topic misses; a gone FID drops its row", async () => {
  const { generateKeyPairSync } = await import("node:crypto");
  const { _resetTokenCache } = await import("../src/fcm.ts");
  _resetTokenCache();
  const pem = generateKeyPairSync("rsa", { modulusLength: 2048 }).privateKey.export({ type: "pkcs8", format: "pem" }).toString();
  const strong = "z".repeat(40);
  const { env, db, sent } = fakeEnv({ admin: strong });
  (env as any).FCM_SERVICE_ACCOUNT = JSON.stringify({ type: "service_account", project_id: "demo", client_email: "svc@demo.iam.gserviceaccount.com", private_key: pem });
  const A = "c" + "A".repeat(21), B = "c" + "B".repeat(21), C = "c" + "C".repeat(21);
  await call(env, req("POST", "/v1/fcm/devices", { fid: A, topics: ["announcements", "maintenance", "updates"], want: ["announcements", "maintenance", "updates"] }));
  await call(env, req("POST", "/v1/fcm/devices", { fid: B, topics: [], want: ["announcements", "updates"] }));
  await call(env, req("POST", "/v1/fcm/devices", { fid: C, topics: ["announcements"] })); // older app: no want
  assert.equal((db.prepare("SELECT want FROM fcm_devices WHERE fid = ?").get(C) as any).want, null);
  const real = globalThis.fetch;
  const fcmTargets: string[] = [];
  globalThis.fetch = (async (u: string | URL | Request, init?: RequestInit) => {
    const url = String(u);
    if (url.includes("oauth2") || url.includes("token")) return new Response(JSON.stringify({ access_token: "at", expires_in: 3600 }), { status: 200 });
    const m = JSON.parse(String(init?.body)).message;
    fcmTargets.push(m.topic ?? m.fid);
    if (m.fid === C) return new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 });
    return new Response('{"name":"projects/demo/messages/1"}', { status: 200 });
  }) as typeof fetch;
  try {
    const up = await call(env, req("POST", "/v1/admin/notify", { kind: "update", title: "New version", body: "b" }, "Bearer " + strong));
    assert.equal(up.status, 200, JSON.stringify(up.body));
    assert.equal(up.body.fanout, 2); // B (wants updates, no topic) and C (older app, no updates topic)
    const job = sent.at(-1)!.body as any;
    assert.deepEqual([...job.fcm.fids].sort(), [B, C].sort());
    const news = await call(env, req("POST", "/v1/admin/notify", { kind: "announcement", title: "News", body: "b" }, "Bearer " + strong));
    assert.equal(news.body.fanout, 1); // B only: C has the topic, A has the topic
    fcmTargets.length = 0;
    let acked = 0;
    await worker.queue({ messages: [{ body: job, ack: () => { acked++; } }] } as never, env, {} as ExecutionContext);
    assert.equal(acked, 1);
    assert.deepEqual([...fcmTargets].sort(), [B, C].sort());
  } finally { globalThis.fetch = real; _resetTokenCache(); }
  const left = (db.prepare("SELECT fid FROM fcm_devices ORDER BY fid").all() as any[]).map((r) => r.fid);
  assert.deepEqual(left, [A, B]); // C was UNREGISTERED
});

// ---- F-AIR (ADR-043): airing alerts reach Firebase phones too ----
// Production 2026-11-19: 406 fcm_devices, 1 UnifiedPush device, 0 follows, 0 airing rows. Airing wakes went
// only to UnifiedPush endpoints, so a Firebase phone never got one.

async function fakeFcm() {
  const { generateKeyPairSync } = await import("node:crypto");
  const { _resetTokenCache } = await import("../src/fcm.ts");
  _resetTokenCache();
  const pem = generateKeyPairSync("rsa", { modulusLength: 2048 }).privateKey.export({ type: "pkcs8", format: "pem" }).toString();
  return { sa: JSON.stringify({ type: "service_account", project_id: "demo", client_email: "svc@demo.iam.gserviceaccount.com", private_key: pem }), reset: _resetTokenCache };
}

test("air-1: a Firebase phone's follows and airing times are stored; an aired episode sends it a data-only wake", async () => {
  const { sa, reset } = await fakeFcm();
  const { env, db, sent } = fakeEnv();
  (env as any).FCM_SERVICE_ACCOUNT = sa;
  const A = "d" + "A".repeat(21);
  const t = Math.floor(Date.now() / 1000);
  const r = await call(env, req("POST", "/v1/fcm/devices", { fid: A, topics: ["updates"], want: ["updates"], anilist: [42, 7], airing: [[42, t + 3600]] }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.follows, 2);
  const f = (db.prepare("SELECT media_id FROM fcm_follows WHERE fcm_id = ? ORDER BY media_id").all(r.body.id) as any[]).map((x) => x.media_id);
  assert.deepEqual(f, [7, 42]);
  assert.equal((db.prepare("SELECT at FROM airing WHERE media_id = 42").get() as any).at, t + 3600);
  // The episode airs.
  db.prepare("UPDATE airing SET at = ? WHERE media_id = 42").run(t - 60);
  db.prepare("INSERT INTO state (k, v) VALUES ('aired_to', ?)").run(String(t - 600));
  await worker.scheduled({ scheduledTime: Date.now(), cron: "*/10 * * * *", noRetry() {} } as ScheduledController, env, {} as ExecutionContext);
  const jobs = sent.filter((m: any) => m.body.wake);
  assert.equal(jobs.length, 1, "no Firebase wake was queued (old code: UnifiedPush only)");
  assert.deepEqual((jobs[0].body as any).wake.fids, [A]);
  assert.equal(jobs[0].delaySeconds, 30 * 60);
  // The queue sends it: data-only, normal priority (PUSH-1), collapsible, nothing about the show in it.
  const real = globalThis.fetch;
  const msgs: any[] = [];
  globalThis.fetch = (async (u: string | URL | Request, init?: RequestInit) => {
    if (String(u).includes("oauth2")) return new Response(JSON.stringify({ access_token: "at", expires_in: 3600 }), { status: 200 });
    msgs.push(JSON.parse(String(init?.body)).message);
    return new Response('{"name":"projects/demo/messages/2"}', { status: 200 });
  }) as typeof fetch;
  let acked = 0;
  try {
    await worker.queue({ messages: [{ body: jobs[0].body, ack: () => { acked++; } }] } as never, env, {} as ExecutionContext);
  } finally { globalThis.fetch = real; reset(); }
  assert.equal(acked, 1);
  assert.equal(msgs.length, 1);
  assert.equal(msgs[0].fid, A);
  assert.deepEqual(msgs[0].data, { y: "1", kind: "wake" });
  assert.equal(msgs[0].notification, undefined);
  assert.equal(msgs[0].android.priority, "normal");
  assert.equal(msgs[0].android.collapse_key, "yumeiro-wake");
});

test("air-1: an older app's check-in (no anilist) keeps the follows; sign-off, a gone FID and prune remove them", async () => {
  const { sa, reset } = await fakeFcm();
  const { env, db } = fakeEnv();
  (env as any).FCM_SERVICE_ACCOUNT = sa;
  const A = "e" + "A".repeat(21), B = "e" + "B".repeat(21), C = "e" + "C".repeat(21);
  const a = (await call(env, req("POST", "/v1/fcm/devices", { fid: A, topics: [], anilist: [1, 2] }))).body;
  await call(env, req("POST", "/v1/fcm/devices", { fid: A, topics: [] }, `Bearer ${a.id}.${a.token}`));
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_follows WHERE fcm_id = ?").get(a.id) as any).n, 2, "a check-in without anilist must not wipe the list");
  await call(env, req("DELETE", "/v1/fcm/devices", undefined, `Bearer ${a.id}.${a.token}`));
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_follows").get() as any).n, 0, "sign-off leaves follows behind");
  // A gone FID during a wake drops the row and its follows.
  const b = (await call(env, req("POST", "/v1/fcm/devices", { fid: B, topics: [], anilist: [3] }))).body;
  const real = globalThis.fetch;
  globalThis.fetch = (async (u: string | URL | Request) => String(u).includes("oauth2")
    ? new Response(JSON.stringify({ access_token: "at", expires_in: 3600 }), { status: 200 })
    : new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 })) as typeof fetch;
  try {
    await worker.queue({ messages: [{ body: { wake: { fids: [B] } }, ack: () => {} }] } as never, env, {} as ExecutionContext);
  } finally { globalThis.fetch = real; reset(); }
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_devices WHERE id = ?").get(b.id) as any).n, 0);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_follows").get() as any).n, 0);
  // Prune: a silent phone's follows go with it.
  await call(env, req("POST", "/v1/fcm/devices", { fid: C, topics: [], anilist: [4] }));
  db.prepare("UPDATE fcm_devices SET seen = seen - 46 * 86400").run();
  await worker.scheduled({ scheduledTime: Date.UTC(2026, 10, 16, 3, 5), cron: "*/10 * * * *", noRetry() {} } as ScheduledController, env, {} as ExecutionContext);
  assert.equal((db.prepare("SELECT COUNT(*) AS n FROM fcm_follows").get() as any).n, 0);
});
