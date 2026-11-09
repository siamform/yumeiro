/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// Pure rules for online watch-together rooms (no Cloudflare APIs).
// Unit-tested in test/cloud.test.ts. See docs/SERVER.md.
//
// The server never sees a key: the room key lives in the invite link's #fragment, and clients
// send only AES-GCM ciphertext. The server checks shapes and sizes and relays. It stores nothing
// per message, so a busy evening costs no storage writes (free plan: 100k DO row writes/day).

/** Room ids: 8 chars from an alphabet without look-alikes (no 0/O, 1/I/L). */
export const ROOM_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const ROOM_ID_LEN = 8;
export const MAX_PEERS = 24;
/** One WebSocket text frame, JSON envelope included. Room state is ~300 bytes before encryption. */
export const MAX_FRAME = 4096;
/** Messages a socket may send per window before it is closed (hosts send on change + every 30 s). */
export const FRAME_BUDGET = 40;
export const FRAME_WINDOW_MS = 10_000;
/** A room nobody is in is deleted after this; an active room lives at most ROOM_MAX_MS. */
export const ROOM_IDLE_MS = 10 * 60_000;
export const ROOM_MAX_MS = 24 * 3600_000;


const B64URL = /^[A-Za-z0-9_-]+$/;
/** Our sealed format: "v1." + base64url(iv ‖ ciphertext ‖ tag). */
const SEALED = /^v1\.[A-Za-z0-9_-]{38,}$/;

export function newRoomId(rand: (n: number) => Uint8Array = (n) => crypto.getRandomValues(new Uint8Array(n))): string {
  // Rejection sampling keeps every character equally likely (256 % 31 != 0).
  const out: string[] = [];
  const limit = 256 - (256 % ROOM_ALPHABET.length);
  while (out.length < ROOM_ID_LEN) {
    for (const b of rand(16)) {
      if (b < limit) out.push(ROOM_ALPHABET[b % ROOM_ALPHABET.length]);
      if (out.length === ROOM_ID_LEN) break;
    }
  }
  return out.join("");
}

export function validRoomId(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const id = raw.trim().toUpperCase();
  if (id.length !== ROOM_ID_LEN) return null;
  for (const c of id) if (!ROOM_ALPHABET.includes(c)) return null;
  return id;
}

/** A sealed blob from a client: our envelope, base64url, bounded. */
export function validSealed(raw: unknown, max = MAX_FRAME): string | null {
  return typeof raw === "string" && raw.length <= max && SEALED.test(raw) ? raw : null;
}

/** Bearer tokens we hand out or that clients derive: base64url, 22–128 chars. */
export function validToken(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const m = /^Bearer (\S+)$/.exec(raw);
  const t = m ? m[1] : null;
  return t && t.length >= 22 && t.length <= 128 && B64URL.test(t) ? t : null;
}

export type Frame =
  | { t: "ping" }
  | { t: "state"; c: string }
  | { t: "msg"; c: string }
  | { t: "close" };

/** Parses one client frame; anything unexpected is null (and the frame is dropped). */
export function parseFrame(raw: unknown): Frame | null {
  if (typeof raw !== "string" || raw.length > MAX_FRAME) return null;
  let o: unknown;
  try { o = JSON.parse(raw); } catch { return null; }
  if (!o || typeof o !== "object") return null;
  const f = o as Record<string, unknown>;
  switch (f.t) {
    case "ping": return { t: "ping" };
    case "close": return { t: "close" };
    case "state":
    case "msg": {
      const c = validSealed(f.c);
      return c ? { t: f.t, c } : null;
    }
    default: return null;
  }
}

/** Sliding budget per socket: returns the new window and whether this frame is allowed. */
export function spend(w: { start: number; n: number }, now: number): { start: number; n: number; ok: boolean } {
  if (now - w.start >= FRAME_WINDOW_MS || now < w.start) return { start: now, n: 1, ok: true };
  const n = w.n + 1;
  return { start: w.start, n, ok: n <= FRAME_BUDGET };
}

/** When the room's alarm should next fire: every 10 minutes (5 once empty), never past 24 h. */
export function nextAlarm(now: number, created: number, sockets: number): number {
  const hardStop = created + ROOM_MAX_MS;
  return Math.min(hardStop, now + (sockets > 0 ? ROOM_IDLE_MS : ROOM_IDLE_MS / 2));
}

/**
 * What the alarm does. A room is deleted after 24 h, or when it was empty at two alarms in a row
 * (5–15 minutes), so a phone that is just reconnecting doesn't lose the room. "mark" and "unmark"
 * are the only storage writes a running room makes besides the alarm itself.
 */
export function alarmVerdict(now: number, created: number, sockets: number, wasEmpty: boolean): "keep" | "mark" | "unmark" | "delete" {
  if (now >= created + ROOM_MAX_MS) return "delete";
  if (sockets === 0) return wasEmpty ? "delete" : "mark";
  return wasEmpty ? "unmark" : "keep";
}

/** The invite landing page. The secret lives in the URL fragment, which browsers never send. */
export function invitePage(id: string): string {
  const safe = validRoomId(id) ?? "";
  return `<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>Join a Yumeiro room</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0E0E11;color:#F2F0EA;font:16px/1.5 system-ui,sans-serif}
main{max-width:22rem;padding:2rem;text-align:center}h1{font-size:1.4rem;margin:0 0 .5rem}a{color:#F5C46B}
.b{display:inline-block;margin-top:1.25rem;padding:.8rem 1.4rem;border-radius:999px;background:#F5C46B;color:#1C1407;text-decoration:none;font-weight:700}
small{display:block;margin-top:1.5rem;color:#9A978F}</style>
<main><h1>Watch together on Yumeiro</h1><p>Room <b>${safe}</b>. Opening the app…</p>
<a class="b" id="open" href="#">Open in Yumeiro</a>
<small>No app yet? <a href="https://yumeiro-phi.vercel.app/download">Download Yumeiro</a>, then tap this link again.</small></main>
<script>(function(){var u="yumeiro://room/${safe}"+location.hash;document.getElementById("open").href=u;location.replace(u);})();</script></html>`;
}

/** Short per-socket id so guests can tell chat senders apart. Not a secret. */
export function randomPeer(): string {
  const a = crypto.getRandomValues(new Uint8Array(4));
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}
