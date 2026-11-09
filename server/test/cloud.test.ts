/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  FRAME_BUDGET, FRAME_WINDOW_MS, ROOM_ALPHABET, ROOM_IDLE_MS, ROOM_MAX_MS, alarmVerdict, invitePage, newRoomId,
  nextAlarm, parseFrame, spend, validRoomId, validSealed, validToken,
} from "../src/cloud.ts";

const sealed = "v1." + "A".repeat(40);

test("room ids use the look-alike-free alphabet and are uniform-ish", () => {
  const counts = new Map<string, number>();
  for (let i = 0; i < 2000; i++) {
    const id = newRoomId();
    assert.equal(id.length, 8);
    for (const c of id) { assert.ok(ROOM_ALPHABET.includes(c), c); counts.set(c, (counts.get(c) ?? 0) + 1); }
  }
  assert.equal(counts.size, ROOM_ALPHABET.length);
  for (const c of "0O1IL") assert.ok(!counts.has(c));
  // Rejection sampling: bytes >= 248 are skipped, never mapped.
  const id = newRoomId(() => new Uint8Array([255, 249, 248, 0, 1, 2, 3, 4, 5, 6, 7, 30, 31, 0, 0, 0]));
  assert.equal(id, "ABCDEFGH");
});

test("room id validation is strict and case-insensitive", () => {
  assert.equal(validRoomId("abcdefgh"), "ABCDEFGH");
  for (const bad of ["ABCDEFG", "ABCDEFGHJ", "ABCDEFG0", "ABCDEFGI", "", null, undefined, "ABCD-EFG"]) assert.equal(validRoomId(bad), null, String(bad));
});

test("sealed blobs and bearer tokens", () => {
  assert.equal(validSealed(sealed), sealed);
  for (const bad of ["v2." + "A".repeat(40), "v1.short", "v1." + "A".repeat(40) + "=", 5, null, "v1." + "A".repeat(5000)]) assert.equal(validSealed(bad), null);
  assert.equal(validSealed("v1." + "A".repeat(3000), 2048), null);
  assert.equal(validToken("Bearer " + "a".repeat(32)), "a".repeat(32));
  for (const bad of ["Bearer short", "Basic " + "a".repeat(32), "Bearer " + "a".repeat(31) + "!", null, "Bearer " + "a".repeat(200)]) assert.equal(validToken(bad), null);
});

test("frames: only known shapes pass", () => {
  assert.deepEqual(parseFrame('{"t":"ping"}'), { t: "ping" });
  assert.deepEqual(parseFrame(JSON.stringify({ t: "state", c: sealed, extra: 1 })), { t: "state", c: sealed });
  assert.deepEqual(parseFrame(JSON.stringify({ t: "msg", c: sealed })), { t: "msg", c: sealed });
  assert.deepEqual(parseFrame('{"t":"close"}'), { t: "close" });
  for (const bad of ['{"t":"state","c":"plain text"}', '{"t":"msg"}', '{"t":"hello"}', "not json", "[]", "null", JSON.stringify({ t: "msg", c: "v1." + "A".repeat(5000) })]) {
    assert.equal(parseFrame(bad), null, bad);
  }
  assert.equal(parseFrame(new ArrayBuffer(4)), null);
});

test("per-socket budget resets each window", () => {
  let w = { start: 0, n: 0 };
  for (let i = 0; i < FRAME_BUDGET; i++) { const r = spend(w, 100); assert.ok(r.ok); w = r; }
  assert.equal(spend(w, 100).ok, false);
  assert.deepEqual(spend(w, FRAME_WINDOW_MS), { start: FRAME_WINDOW_MS, n: 1, ok: true });
  assert.ok(spend(w, -5).ok, "clock going back starts a new window");
});

test("rooms expire when empty twice in a row and after 24 h", () => {
  assert.equal(alarmVerdict(1000, 0, 2, false), "keep");
  assert.equal(alarmVerdict(1000, 0, 0, false), "mark");
  assert.equal(alarmVerdict(1000, 0, 0, true), "delete");
  assert.equal(alarmVerdict(1000, 0, 1, true), "unmark");
  assert.equal(alarmVerdict(ROOM_MAX_MS, 0, 5, false), "delete");
  assert.equal(nextAlarm(ROOM_MAX_MS - 10, 0, 3), ROOM_MAX_MS);
  assert.equal(nextAlarm(0, 0, 1), ROOM_IDLE_MS);
});

test("invite page keeps the secret client-side and escapes nothing user-controlled", () => {
  const html = invitePage("ABCDEFGH");
  assert.ok(html.includes('"yumeiro://room/ABCDEFGH"+location.hash'));
  assert.ok(!invitePage("<script>").includes("<script>alert"));
  assert.ok(invitePage("<img onerror=x>").includes("Room <b></b>"));
});
