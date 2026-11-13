/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, createVerify } from "node:crypto";
import {
  _resetTokenCache, accessToken, buildMessage, parseNotice, parseServiceAccount, parseTopics, safeLink, sameSecret,
  send, sendOutcome, signAssertion, topicFor, validFid, type Notice,
} from "../src/fcm.ts";

const id = () => "fixedid123";

test("notice validation: kinds, sizes, links, versions", () => {
  const ok = parseNotice({ kind: "announcement", title: "Hi", body: "New look" }, id) as Notice;
  assert.equal(ok.kind, "announcement");
  assert.equal(ok.id, "fixedid123");
  assert.equal(ok.channel, "release");
  assert.equal(ok.ttl, 3 * 86_400);
  assert.equal(ok.dry_run, false);
  const bad: [Record<string, unknown> | null, string][] = [
    [null, "bad_json"],
    [{ kind: "spam", title: "x" }, "bad_kind"],
    [{ kind: "update", title: "" }, "bad_title"],
    [{ kind: "update", title: "x".repeat(81) }, "bad_title"],
    [{ kind: "update", title: "x", body: "y".repeat(601) }, "bad_body"],
    [{ kind: "update", title: "x", link: "http://evil.example" }, "bad_link"],
    [{ kind: "update", title: "x", link: "javascript:alert(1)" }, "bad_link"],
    [{ kind: "update", title: "x", channel: "prod" }, "bad_channel"],
    [{ kind: "direct", title: "x" }, "bad_device"],
    [{ kind: "update", title: "x", id: "has space" }, "bad_id"],
    [{ kind: "update", title: "x", min_version: 9, max_version: 3 }, "bad_versions"],
  ];
  for (const [b, e] of bad) assert.deepEqual(parseNotice(b, id), { error: e }, JSON.stringify(b));
  const d = parseNotice({ kind: "direct", title: "x", device_id: "abcdEFGH1234", ttl: 120, dry_run: true, link: "update" }, id) as Notice;
  assert.equal(d.device_id, "abcdEFGH1234");
  assert.equal(d.ttl, 120);
  assert.equal(d.dry_run, true);
  assert.equal(d.link, "update");
});

test("links: https or known in-app actions only", () => {
  assert.equal(safeLink("https://yumeiro.click/news"), "https://yumeiro.click/news");
  assert.equal(safeLink("extensions"), "extensions");
  for (const b of ["http://x.y", "https://u:p@x.y", "intent://x", "file:///sdcard", "", 5, "settings-hack"]) assert.equal(safeLink(b), null, String(b));
});

test("topics: release plain, preview/debug prefixed, unknown dropped", () => {
  assert.equal(topicFor("announcement", "release"), "announcements");
  assert.equal(topicFor("maintenance", "preview"), "preview-maintenance");
  assert.deepEqual(parseTopics(["updates", "announcements", "evil", "updates", "debug-updates", 3]), ["announcements", "debug-updates", "updates"]);
  assert.deepEqual(parseTopics("x"), []);
});

test("fid guard", () => {
  assert.ok(validFid("cR_3k2-abcdefghijklmnop"));
  assert.equal(validFid("short"), null);
  assert.equal(validFid("has spaces in it but long enough"), null);
  assert.equal(validFid(undefined), null);
});

test("message: data-only, high priority, ttl, collapse per kind, version + expiry fields", () => {
  const n = parseNotice({ kind: "maintenance", title: "Down at 02:00", body: "10 min", ttl: 7200, min_version: 50, link: "https://status.yumeiro.click" }, id) as Notice;
  const m = buildMessage(n, { topic: "maintenance" }, 1000) as { topic: string; data: Record<string, string>; android: Record<string, unknown>; notification?: unknown };
  assert.equal(m.topic, "maintenance");
  assert.equal(m.notification, undefined, "no notification block: the app renders it");
  assert.deepEqual(m.data, { y: "1", kind: "maintenance", id: "fixedid123", title: "Down at 02:00", body: "10 min", sent: "1000", link: "https://status.yumeiro.click", min_v: "50", exp: "8200" });
  assert.equal(m.android.priority, "high");
  assert.equal(m.android.ttl, "7200s");
  assert.equal(m.android.collapse_key, "yumeiro-maintenance");
  for (const v of Object.values(m.data)) assert.equal(typeof v, "string", "FCM data values must be strings");
  const dm = buildMessage(parseNotice({ kind: "direct", title: "x", device_id: "abcdEFGH1234" }, id) as Notice, { fid: "f".repeat(30) }, 1) as Record<string, unknown>;
  assert.equal(dm.fid, "f".repeat(30));
});

test("FCM error mapping", () => {
  assert.equal(sendOutcome(200, "{}"), "ok");
  assert.equal(sendOutcome(404, '{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}'), "gone");
  assert.equal(sendOutcome(400, '{"error":{"status":"INVALID_ARGUMENT","message":"The registration token is not a valid FCM registration token"}}'), "gone");
  assert.equal(sendOutcome(429, ""), "retry");
  assert.equal(sendOutcome(503, ""), "retry");
  assert.equal(sendOutcome(403, '{"error":{"status":"PERMISSION_DENIED"}}'), "fail");
});

test("service account parsing rejects anything that isn't one", () => {
  assert.equal(parseServiceAccount(undefined), null);
  assert.equal(parseServiceAccount("not json"), null);
  assert.equal(parseServiceAccount(JSON.stringify({ type: "authorized_user", project_id: "p", client_email: "e", private_key: "k" })), null);
  assert.deepEqual(parseServiceAccount(JSON.stringify({ type: "service_account", project_id: "p", client_email: "e@x", private_key: "k", extra: 1 })),
    { project_id: "p", client_email: "e@x", private_key: "k", token_uri: undefined });
});

function testAccount() {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const pem = privateKey.export({ type: "pkcs8", format: "pem" }).toString();
  return { sa: { project_id: "demo-proj", client_email: "svc@demo-proj.iam.gserviceaccount.com", private_key: pem }, publicKey };
}

test("JWT assertion is a valid RS256 signature with the FCM scope", async () => {
  const { sa, publicKey } = testAccount();
  const jwt = await signAssertion(sa, 1_700_000_000);
  const [h, c, s] = jwt.split(".");
  const claims = JSON.parse(Buffer.from(c, "base64url").toString());
  assert.deepEqual(JSON.parse(Buffer.from(h, "base64url").toString()), { alg: "RS256", typ: "JWT" });
  assert.equal(claims.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assert.equal(claims.aud, "https://oauth2.googleapis.com/token");
  assert.equal(claims.exp - claims.iat, 3600);
  const v = createVerify("RSA-SHA256");
  v.update(`${h}.${c}`);
  assert.ok(v.verify(publicKey, Buffer.from(s, "base64url")));
});

test("send: token is minted once and cached; v1 endpoint and body shape", async () => {
  _resetTokenCache();
  const { sa } = testAccount();
  const calls: { url: string; init: RequestInit }[] = [];
  const fake = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    if (url.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }));
    return new Response(JSON.stringify({ name: "projects/demo-proj/messages/123" }));
  }) as unknown as typeof fetch;
  const msg = { topic: "announcements", data: { y: "1" } };
  const r1 = await send(sa, msg, true, 1000, fake);
  const r2 = await send(sa, msg, false, 1100, fake);
  assert.equal(r1.outcome, "ok");
  assert.equal(r2.name, "projects/demo-proj/messages/123");
  assert.equal(calls.filter((x) => x.url.includes("oauth2")).length, 1, "token cached");
  const sendCall = calls.find((x) => x.url.includes("fcm.googleapis.com"))!;
  assert.equal(sendCall.url, "https://fcm.googleapis.com/v1/projects/demo-proj/messages:send");
  assert.equal((sendCall.init.headers as Record<string, string>).authorization, "Bearer ya29.test");
  assert.deepEqual(JSON.parse(String(sendCall.init.body)), { validate_only: true, message: msg });
});

test("admin secret compare", async () => {
  assert.equal(await sameSecret("a".repeat(40), "a".repeat(40)), true);
  assert.equal(await sameSecret("a".repeat(40), "b".repeat(40)), false);
  assert.equal(await sameSecret("", ""), false);
});
