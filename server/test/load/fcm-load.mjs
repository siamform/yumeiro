// Load + abuse test for the FCM device endpoints against a LOCAL `wrangler dev` (never production:
// it would spend the free D1 write quota and trip the per-IP limits on purpose).
//   npx wrangler dev --local --port 8787   (with server/.dev.vars holding ADMIN_TOKEN)
//   node test/load/fcm-load.mjs [base=http://localhost:8787] [phones=2000]
import { randomBytes } from "node:crypto";

const BASE = process.argv[2] ?? "http://localhost:8787";
const N = Number(process.argv[3] ?? 2000);
const CONC = 200;

const fid = () => { const b = randomBytes(17); b[0] = (b[0] & 0x0f) | 0x70; return b.toString("base64url").slice(0, 22); };
const ip = (i) => `10.${(i >> 16) & 255}.${(i >> 8) & 255}.${i & 255}`;

async function pool(items, fn) {
  const out = new Array(items.length); let next = 0;
  await Promise.all(Array.from({ length: CONC }, async () => {
    while (next < items.length) { const i = next++; out[i] = await fn(items[i], i); }
  }));
  return out;
}

async function call(method, path, { body, auth, from } = {}) {
  const t = performance.now();
  const r = await fetch(BASE + path, {
    method,
    headers: { "content-type": "application/json", ...(auth ? { authorization: `Bearer ${auth}` } : {}), ...(from ? { "cf-connecting-ip": from } : {}) },
    body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
  });
  const text = await r.text();
  let j = null; try { j = JSON.parse(text); } catch {}
  return { status: r.status, j, ms: performance.now() - t };
}

function report(name, rs) {
  const by = {}; for (const r of rs) by[r.status] = (by[r.status] ?? 0) + 1;
  const ms = rs.map((r) => r.ms).sort((a, b) => a - b);
  const p = (q) => ms[Math.min(ms.length - 1, Math.floor(q * ms.length))].toFixed(0);
  console.log(`${name.padEnd(34)} n=${rs.length} status=${JSON.stringify(by)} p50=${p(0.5)}ms p95=${p(0.95)}ms p99=${p(0.99)}ms`);
  return by;
}

let failures = 0;
const expect = (ok, msg) => { if (!ok) { failures++; console.log("  FAIL:", msg); } };

const phones = Array.from({ length: N }, (_, i) => ({ fid: fid(), ip: ip(i + 1) }));
const topics = ["updates", "announcements", "maintenance"];

// 1. N phones register at once, each from its own IP.
const reg = await pool(phones, (p) => call("POST", "/v1/fcm/devices", { from: p.ip, body: { fid: p.fid, topics, app_version: 20, channel: "release" } }));
const r1 = report("1 register (new phones)", reg);
expect(r1[200] === N, "every new phone gets an id + token");
reg.forEach((r, i) => { phones[i].cred = r.j?.id && r.j?.token ? `${r.j.id}.${r.j.token}` : null; });
expect(new Set(phones.map((p) => p.cred?.split(".")[0])).size === N, "ids are unique");

// 2. Same phones check in again (weekly refresh, topic change).
const ref = await pool(phones, (p) => call("POST", "/v1/fcm/devices", { auth: p.cred, from: p.ip, body: { fid: p.fid, topics: ["updates"], app_version: 21, channel: "release" } }));
const r2 = report("2 refresh with credentials", ref);
expect(r2[200] === N, "every refresh accepted");
expect(ref.every((r) => r.j?.token === null), "refresh never hands out a new token");

// 3. Reinstall: same FID, no credentials -> same row, new token (old token stops working).
const re = phones.slice(0, 50);
const rr = await pool(re, (p, i) => call("POST", "/v1/fcm/devices", { from: ip(900000 + i), body: { fid: p.fid, topics, app_version: 21 } }));
report("3 reinstall reclaims row", rr);
expect(rr.every((r, i) => r.j?.id === re[i].cred.split(".")[0]), "reinstall keeps the same id");
const stale = await call("POST", "/v1/fcm/devices", { auth: re[0].cred, body: { fid: re[0].fid } });
expect(stale.status === 403, `old token rejected after reinstall (got ${stale.status})`);
rr.forEach((r, i) => { re[i].cred = `${r.j.id}.${r.j.token}`; });

// 4. One phone loops check-ins: per-device limit (3/min) must kick in.
const loop = await pool(Array.from({ length: 30 }), () => call("POST", "/v1/fcm/devices", { auth: phones[100].cred, body: { fid: phones[100].fid } }));
const r4 = report("4 one device hammering", loop);
expect((r4[429] ?? 0) >= 20, "per-device limit stops a write loop");

// 5. One IP creating many fake devices: per-IP new-device limit (5/min).
const flood = await pool(Array.from({ length: 40 }), () => call("POST", "/v1/fcm/devices", { from: "203.0.113.9", body: { fid: fid() } }));
const r5 = report("5 one IP creating fake devices", flood);
expect((r5[429] ?? 0) >= 30, "per-IP new-device limit");

// 6. Garbage from every angle: never a 500, never accepted.
const junk = [
  "not json", "{}", '{"fid":""}', '{"fid":"../../etc"}', `{"fid":"${"A".repeat(400)}"}`, '{"fid":123}', "[]", "null",
  `{"fid":"${fid()}","topics":"updates"}`, `{"fid":"${fid()}","app_version":-1}`, `{"fid":"${fid()}","channel":"'; DROP TABLE fcm_devices;--"}`,
];
const bad = await pool(junk, (b, i) => call("POST", "/v1/fcm/devices", { from: ip(800000 + i), body: b }));
const r6 = report("6 malformed bodies", bad);
expect(!r6[500], "no 500 on malformed input");
const badAuth = await pool(["x", "a.b", "..", `${"A".repeat(300)}.x`, "id.token"], (a) => call("DELETE", "/v1/fcm/devices", { auth: a }));
const r6b = report("6b bad credentials on delete", badAuth);
expect(!r6b[500], "no 500 on bad credentials");

// 7. Admin endpoints without / with a wrong token.
const adm = await pool([undefined, "short", "W".repeat(45)], (a) => call("GET", "/v1/admin/fcm/stats", { auth: a, from: "198.51.100.7" }));
report("7 admin without valid token", adm);
expect(adm.every((r) => r.status === 401 || r.status === 429), "admin refuses bad tokens");

// 8. Every phone turns Instant alerts off.
const un = await pool(phones, (p) => call("DELETE", "/v1/fcm/devices", { auth: p.cred, from: p.ip }));
const r8 = report("8 unregister", un);
expect(r8[200] === N, "every phone can delete its row");

console.log(failures ? `\n${failures} check(s) FAILED` : "\nAll load / abuse checks passed");
process.exit(failures ? 1 : 0);
