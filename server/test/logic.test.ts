/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { airingWindow, chunk, parseFollows, pushOutcome, randomToken, same, sha256, validEndpoint } from "../src/logic.ts";

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
