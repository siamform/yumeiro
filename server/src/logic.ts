/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// Pure helpers (no Cloudflare APIs), unit-tested in test/logic.test.ts.

export const MAX_FOLLOWS = 300;
export const MAX_ENDPOINT = 1024;
/** Endpoints per queue message. Free Workers may make 50 outbound requests per invocation. */
export const WAKES_PER_MESSAGE = 40;
/** A device that hasn't checked in for this long is deleted. */
export const SILENT_DAYS = 45;
/** Accepted range for a reported airing time, relative to now. */
export const AIRING_MAX_AHEAD_S = 15 * 86_400;
/** Weekly guess when no phone has reported a new time yet, at most this many times in a row. */
export const ROLL_S = 7 * 86_400;
export const MAX_ROLLS = 2;
/** Wake-up delay after an episode airs: sources usually have it 10–40 min later. */
export const AIRING_WAKE_DELAYS_S = [30 * 60];

const PRIVATE_HOST = /^(localhost|.*\.local|.*\.internal|.*\.lan|.*\.home|.*\.arpa)$/i;
const IPV4 = /^\d{1,3}(\.\d{1,3}){3}$/;

/**
 * A UnifiedPush endpoint we're willing to POST to: https, default port, a public DNS name (no IP
 * literals, no localhost / .local / .internal), no credentials, bounded length. This is the server's
 * SSRF guard: the URL comes from the client.
 */
export function validEndpoint(raw: unknown): string | null {
  if (typeof raw !== "string" || raw.length === 0 || raw.length > MAX_ENDPOINT) return null;
  let u: URL;
  try { u = new URL(raw.trim()); } catch { return null; }
  if (u.protocol !== "https:" || u.port !== "" || u.username || u.password) return null;
  const host = u.hostname.toLowerCase();
  if (!host.includes(".") || host.startsWith("[") || IPV4.test(host) || PRIVATE_HOST.test(host)) return null;
  u.hash = "";
  return u.toString();
}

/** AniList ids: positive integers, de-duplicated, sorted, capped. Anything else is dropped. */
export function parseFollows(raw: unknown): number[] {
  if (!Array.isArray(raw)) return [];
  const set = new Set<number>();
  for (const v of raw) {
    if (typeof v === "number" && Number.isInteger(v) && v > 0 && v < 10_000_000) set.add(v);
    if (set.size >= MAX_FOLLOWS) break;
  }
  return [...set].sort((a, b) => a - b);
}

export async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function randomToken(bytes = 24): string {
  const a = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...a)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Constant-time string compare (tokens). */
export function same(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

export function chunk<T>(xs: T[], n: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < xs.length; i += n) out.push(xs.slice(i, i + n));
  return out;
}

/**
 * The airing window to check this run: from the last checked second (exclusive) to now, never
 * more than 2 hours back (after downtime, old episodes aren't worth a wake-up) and never empty.
 */
export function airingWindow(lastS: number | null, nowS: number): [number, number] {
  const floor = nowS - 2 * 3600;
  const from = lastS == null || lastS < floor ? Math.max(floor, nowS - 600) : lastS;
  return [Math.min(from, nowS - 1), nowS];
}

/** What a push result means for the device row. */
export function pushOutcome(status: number): "ok" | "gone" | "retry" {
  if (status >= 200 && status < 300) return "ok";
  if (status === 404 || status === 410) return "gone"; // endpoint unregistered (RFC 8030 / ntfy 41002)
  return "retry";
}

/**
 * Reported airing times: [[anilistId, unixSeconds], ...]. Kept only for followed ids and for times from
 * an hour ago to 15 days ahead (anything else is a bad clock or a bad actor). One entry per id.
 */
export function parseAiring(raw: unknown, follows: number[], nowS: number): [number, number][] {
  if (!Array.isArray(raw)) return [];
  const ok = new Set(follows);
  const out = new Map<number, number>();
  for (const p of raw) {
    if (!Array.isArray(p) || p.length !== 2) continue;
    const [id, at] = p;
    if (typeof id !== "number" || !ok.has(id) || typeof at !== "number" || !Number.isInteger(at)) continue;
    if (at < nowS - 3600 || at > nowS + AIRING_MAX_AHEAD_S) continue;
    out.set(id, at);
    if (out.size >= follows.length) break;
  }
  return [...out.entries()].sort((a, b) => a[0] - b[0]);
}


// ---- abuse guards (R11 red-team) ----

/** A device may replace its follow list at most this often; other check-ins keep the old list. */
export const FOLLOW_CHANGE_COOLDOWN_S = 10 * 60;
/** Only devices at least this old count toward overriding a confirmed airing time. */
export const AIRING_VOTER_MIN_AGE_S = 3 * 86_400;

/** Rows to add / remove so the stored follow list becomes [next]. Writes only what changed. */
export function followDiff(current: number[], next: number[]): { add: number[]; remove: number[] } {
  const cur = new Set(current);
  const nxt = new Set(next);
  return { add: next.filter((m) => !cur.has(m)), remove: current.filter((m) => !nxt.has(m)) };
}

export interface AiringRow { media_id: number; at: number; rolls: number }
export interface PendingRow { media_id: number; at: number; device_id: string }
export type AiringOp =
  | { op: "set"; media_id: number; at: number }        // write confirmed time, rolls = 0, clear pending
  | { op: "pending"; media_id: number; at: number };   // remember this device's vote

/**
 * Shared airing timetable, hardened against one bad phone poisoning it for everyone.
 *
 * - Unknown show → the first report is taken (nothing to protect yet).
 * - The stored time is only a guess (rolled forward a week) → any report replaces it.
 * - A confirmed time differs from the report → it changes only when two different devices, each at
 *   least 3 days old, report the same new time. A single device can never move a confirmed time.
 */
export function planAiring(
  reports: [number, number][], rows: AiringRow[], pending: PendingRow[], deviceId: string, deviceAgeS: number,
): AiringOp[] {
  const byId = new Map(rows.map((r) => [r.media_id, r]));
  const pend = new Map(pending.map((p) => [p.media_id, p]));
  const out: AiringOp[] = [];
  for (const [id, at] of reports) {
    const row = byId.get(id);
    if (!row || row.rolls > 0) { if (!row || row.at !== at || row.rolls !== 0) out.push({ op: "set", media_id: id, at }); continue; }
    if (row.at === at) continue;
    if (deviceAgeS < AIRING_VOTER_MIN_AGE_S) continue;
    const p = pend.get(id);
    if (p && p.at === at && p.device_id !== deviceId) out.push({ op: "set", media_id: id, at });
    else if (!p || p.at !== at) out.push({ op: "pending", media_id: id, at });
  }
  return out;
}
