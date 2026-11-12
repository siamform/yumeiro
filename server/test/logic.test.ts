/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { AIRING_MAX_AHEAD_S, airingWindow, chunk, parseAiring, parseFollows, pushOutcome, randomToken, same, sha256, validEndpoint } from "../src/logic.ts";

test("endpoint guard accepts public https push URLs", () => {
  assert.equal(validEndpoint("https://ntfy.sh/upAbC123?up=1"), "https://ntfy.sh/upAbC123?up=1");
  assert.equal(validEndpoint("https://push.example.org/x#frag"), "https://push.example.org/x");
});

test("endpoint guard rejects SSRF shapes", () => {
  for (const bad of [
    "http://ntfy.sh/x", "https://127.0.0.1/x", "https://[::1]/x", "https://localhost/x",
    "https://box.local/x", "https://intranet/x", "https://a.internal/x", "https://ntfy.sh:8443/x",
    "https://u:p@ntfy.sh/x", "ftp://ntfy.sh/x", "", 42, null, "https://ntfy.sh/" + "a".repeat(1100),
  ]) assert.equal(validEndpoint(bad), null, String(bad));
});

test("follows are clean, unique, sorted and capped", () => {
  assert.deepEqual(parseFollows([5, 3, 5, -1, 0, 2.5, "7", 1e9, 21]), [3, 5, 21]);
  assert.equal(parseFollows(Array.from({ length: 500 }, (_, i) => i + 1)).length, 300);
  assert.deepEqual(parseFollows("nope"), []);
});

test("airing window never gaps, never reaches far back", () => {
  assert.deepEqual(airingWindow(1000, 1600), [1000, 1600]);
  assert.deepEqual(airingWindow(null, 100_000), [99_400, 100_000]);
  assert.deepEqual(airingWindow(1, 100_000), [99_400, 100_000]); // after long downtime
  assert.deepEqual(airingWindow(100_000, 100_000), [99_999, 100_000]);
});

test("push status mapping", () => {
  assert.equal(pushOutcome(200), "ok");
  assert.equal(pushOutcome(201), "ok");
  assert.equal(pushOutcome(404), "gone");
  assert.equal(pushOutcome(410), "gone");
  assert.equal(pushOutcome(429), "retry");
  assert.equal(pushOutcome(507), "retry");
});

test("tokens, hashing and helpers", async () => {
  const t = randomToken();
  assert.match(t, /^[A-Za-z0-9_-]{32}$/);
  assert.notEqual(t, randomToken());
  assert.equal(await sha256("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  assert.ok(same("abc", "abc"));
  assert.ok(!same("abc", "abd"));
  assert.ok(!same("abc", "ab"));
  assert.deepEqual(chunk([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
});

test("airing reports keep followed ids and sane times only", () => {
  const now = 1_800_000_000;
  const got = parseAiring([
    [5, now + 3600], [5, now + 7200], // last one wins
    [9, now + 100],                   // not followed
    [3, now - 7200],                  // too old
    [21, now + AIRING_MAX_AHEAD_S + 1], // too far ahead
    [21, now + 60.5], ["3", now], [3], "x", null,
    [3, now - 60],
  ], [3, 5, 21], now);
  assert.deepEqual(got, [[3, now - 60], [5, now + 7200]]);
  assert.deepEqual(parseAiring("nope", [1], now), []);
});


// ---- R11 abuse guards ----
import { AIRING_VOTER_MIN_AGE_S, followDiff, planAiring } from "../src/logic.ts";

test("followDiff writes only what changed", () => {
  assert.deepEqual(followDiff([1, 2, 3], [2, 3, 4]), { add: [4], remove: [1] });
  assert.deepEqual(followDiff([5, 6], [5, 6]), { add: [], remove: [] });
});

test("planAiring: first report and rolled guesses are taken", () => {
  const ops = planAiring([[1, 1000], [2, 2000]], [{ media_id: 2, at: 1500, rolls: 1 }], [], "dev-a", 0);
  assert.deepEqual(ops, [{ op: "set", media_id: 1, at: 1000 }, { op: "set", media_id: 2, at: 2000 }]);
});

test("planAiring: one device cannot move a confirmed time", () => {
  const rows = [{ media_id: 7, at: 5000, rolls: 0 }];
  const old = AIRING_VOTER_MIN_AGE_S + 1;
  assert.deepEqual(planAiring([[7, 9000]], rows, [], "bad", 10), []); // young device: ignored
  assert.deepEqual(planAiring([[7, 9000]], rows, [], "bad", old), [{ op: "pending", media_id: 7, at: 9000 }]);
  // The same device voting again does not confirm it.
  assert.deepEqual(planAiring([[7, 9000]], rows, [{ media_id: 7, at: 9000, device_id: "bad" }], "bad", old), []);
  // A second, different, aged device agreeing confirms it.
  assert.deepEqual(planAiring([[7, 9000]], rows, [{ media_id: 7, at: 9000, device_id: "bad" }], "good", old), [{ op: "set", media_id: 7, at: 9000 }]);
  // Same as stored: no write at all.
  assert.deepEqual(planAiring([[7, 5000]], rows, [], "x", old), []);
});
