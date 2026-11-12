/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// Firebase Cloud Messaging (HTTP v1) for the existing Yumeiro Cloud Worker. Free: FCM itself costs
// nothing, the OAuth token is minted here with WebCrypto (no Admin SDK), and broadcasts use topics so
// one request reaches every subscribed phone. See docs/PUSH-FCM.md.

export type NoticeKind = "announcement" | "update" | "maintenance" | "direct";
export type Channel = "release" | "preview" | "debug";

export interface Notice {
  kind: NoticeKind;
  id: string;
  title: string;
  body: string;
  link?: string;
  channel: Channel;
  /** Direct messages only: the Worker's device id (never an FCM id from the caller). */
  device_id?: string;
  min_version?: number;
  max_version?: number;
  /** Seconds FCM keeps the message for offline phones (also the client-side expiry for maintenance). */
  ttl: number;
  dry_run: boolean;
}

export const TOPIC_OF: Record<Exclude<NoticeKind, "direct">, string> = {
  announcement: "announcements",
  update: "updates",
  maintenance: "maintenance",
};
/** Same rule as the app's FcmPlan.topic: preview/debug builds listen on prefixed topics. */
export const topicFor = (kind: Exclude<NoticeKind, "direct">, channel: Channel): string =>
  channel === "release" ? TOPIC_OF[kind] : `${channel}-${TOPIC_OF[kind]}`;

export const MAX_TITLE = 80;
export const MAX_BODY = 600;
export const ACTIONS = new Set(["update", "extensions", "market", "streams", "support", "downloads", "inbox"]);
const ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
/** FCM ids (FIDs / legacy tokens) are URL-safe base64-ish; cap length defensively. */
const FID_RE = /^[A-Za-z0-9_:\-]{20,400}$/;

export function validFid(v: unknown): string | null {
  return typeof v === "string" && FID_RE.test(v) ? v : null;
}

export function safeLink(v: unknown): string | null {
  if (typeof v !== "string") return null;
  const l = v.trim();
  if (!l || l.length > 500) return null;
  if (ACTIONS.has(l)) return l;
  try {
    const u = new URL(l);
    return u.protocol === "https:" && u.hostname && !u.username && !u.password ? l : null;
  } catch {
    return null;
  }
}

/** Topics a device may report; anything else is ignored (counts only, never used to send). */
export function parseTopics(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  const ok = new Set<string>();
  for (const ch of ["release", "preview", "debug"] as Channel[]) {
    for (const k of Object.keys(TOPIC_OF) as Exclude<NoticeKind, "direct">[]) ok.add(topicFor(k, ch));
  }
  return [...new Set(v.filter((t): t is string => typeof t === "string" && ok.has(t)))].sort();
}

const intOrUndef = (v: unknown, lo: number, hi: number): number | undefined =>
  typeof v === "number" && Number.isInteger(v) && v >= lo && v <= hi ? v : undefined;

/** Validates an admin request body. Returns the notice or an error code. */
export function parseNotice(b: Record<string, unknown> | null, makeId: () => string): Notice | { error: string } {
  if (!b) return { error: "bad_json" };
  const kind = b.kind;
  if (kind !== "announcement" && kind !== "update" && kind !== "maintenance" && kind !== "direct") return { error: "bad_kind" };
  const title = typeof b.title === "string" ? b.title.trim() : "";
  const body = typeof b.body === "string" ? b.body.trim() : "";
  if (!title || title.length > MAX_TITLE) return { error: "bad_title" };
  if (body.length > MAX_BODY) return { error: "bad_body" };
  let link: string | undefined;
  if (b.link != null && b.link !== "") {
    const l = safeLink(b.link);
    if (!l) return { error: "bad_link" };
    link = l;
  }
  const channel = b.channel == null ? "release" : b.channel;
  if (channel !== "release" && channel !== "preview" && channel !== "debug") return { error: "bad_channel" };
  let device_id: string | undefined;
  if (kind === "direct") {
    if (typeof b.device_id !== "string" || !/^[A-Za-z0-9_-]{8,64}$/.test(b.device_id)) return { error: "bad_device" };
    device_id = b.device_id;
  }
  const id = b.id == null ? makeId() : typeof b.id === "string" && ID_RE.test(b.id) ? b.id : null;
  if (!id) return { error: "bad_id" };
  const min_version = intOrUndef(b.min_version, 1, 2_000_000_000);
  const max_version = intOrUndef(b.max_version, 1, 2_000_000_000);
  if (min_version != null && max_version != null && min_version > max_version) return { error: "bad_versions" };
  // FCM keeps messages up to 28 days; default 3 days (maintenance notices should set their own).
  const ttl = intOrUndef(b.ttl, 60, 28 * 86_400) ?? 3 * 86_400;
  return { kind, id, title, body, link, channel, device_id, min_version, max_version, ttl, dry_run: b.dry_run === true };
}

/**
 * HTTP v1 message. Data-only with high priority: the app builds the notification itself (inbox,
 * channel, chime, deep link, user switches). `fid` targets a device registered in FID mode.
 */
export function buildMessage(n: Notice, target: { topic: string } | { fid: string }, nowS: number): Record<string, unknown> {
  const data: Record<string, string> = {
    y: "1", kind: n.kind, id: n.id, title: n.title, body: n.body, sent: String(nowS),
  };
  if (n.link) data.link = n.link;
  if (n.min_version != null) data.min_v = String(n.min_version);
  if (n.max_version != null) data.max_v = String(n.max_version);
  if (n.kind === "maintenance") data.exp = String(nowS + n.ttl);
  return {
    ...target,
    data,
    android: { priority: "high", ttl: `${n.ttl}s`, collapse_key: n.kind === "direct" ? undefined : `yumeiro-${n.kind}` },
  };
}

export type SendOutcome = "ok" | "gone" | "retry" | "fail";
/** Maps an FCM v1 error to what the Worker should do with the target. */
export function sendOutcome(status: number, body: string): SendOutcome {
  if (status >= 200 && status < 300) return "ok";
  if (status === 404 || /UNREGISTERED/.test(body)) return "gone";
  if (status === 400 && /registration token|INVALID_ARGUMENT.*(fid|token)/i.test(body)) return "gone";
  if (status === 429 || status >= 500) return "retry";
  return "fail";
}

// ---- OAuth (service account → access token), WebCrypto only ----

export interface ServiceAccount { project_id: string; client_email: string; private_key: string; token_uri?: string }

export function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
  if (!raw) return null;
  try {
    const j = JSON.parse(raw) as Partial<ServiceAccount> & { type?: string };
    if (j.type !== "service_account" || !j.project_id || !j.client_email || !j.private_key) return null;
    return { project_id: j.project_id, client_email: j.client_email, private_key: j.private_key, token_uri: j.token_uri };
  } catch {
    return null;
  }
}

const b64url = (bytes: ArrayBuffer | Uint8Array): string => {
  const u = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (const x of u) s += String.fromCharCode(x);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const b64urlJson = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));

function pemToPkcs8(pem: string): ArrayBuffer {
  const b = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const bin = atob(b);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

/** RS256 JWT assertion for the OAuth 2.0 JWT-bearer grant. */
export async function signAssertion(sa: ServiceAccount, nowS: number): Promise<string> {
  const header = b64urlJson({ alg: "RS256", typ: "JWT" });
  const claims = b64urlJson({ iss: sa.client_email, scope: FCM_SCOPE, aud: sa.token_uri ?? TOKEN_URL, iat: nowS, exp: nowS + 3600 });
  const key = await crypto.subtle.importKey("pkcs8", pemToPkcs8(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  return `${header}.${claims}.${b64url(sig)}`;
}

let cached: { token: string; exp: number; email: string } | null = null;

/** Access token, cached in the isolate until 5 minutes before it expires. */
export async function accessToken(sa: ServiceAccount, nowS: number, f: typeof fetch = fetch): Promise<string> {
  if (cached && cached.email === sa.client_email && cached.exp - 300 > nowS) return cached.token;
  const assertion = await signAssertion(sa, nowS);
  const r = await f(sa.token_uri ?? TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${assertion}`,
  });
  if (!r.ok) throw new Error(`oauth_${r.status}`);
  const j = (await r.json()) as { access_token?: string; expires_in?: number };
  if (!j.access_token) throw new Error("oauth_no_token");
  cached = { token: j.access_token, exp: nowS + (j.expires_in ?? 3600), email: sa.client_email };
  return j.access_token;
}
export const _resetTokenCache = () => { cached = null; };

/** One HTTP v1 send. Returns the outcome and FCM's message name (or error text, trimmed). */
export async function send(
  sa: ServiceAccount, message: Record<string, unknown>, validateOnly: boolean, nowS: number, f: typeof fetch = fetch,
): Promise<{ outcome: SendOutcome; status: number; name?: string; error?: string }> {
  const token = await accessToken(sa, nowS, f);
  const r = await f(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(sa.project_id)}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify({ validate_only: validateOnly, message }),
  });
  const text = await r.text();
  const outcome = sendOutcome(r.status, text);
  if (outcome === "ok") {
    let name: string | undefined;
    try { name = (JSON.parse(text) as { name?: string }).name; } catch { /* ignore */ }
    return { outcome, status: r.status, name };
  }
  let error = text.slice(0, 300);
  try { error = (JSON.parse(text) as { error?: { status?: string; message?: string } }).error?.status ?? error; } catch { /* ignore */ }
  return { outcome, status: r.status, error };
}

/** Constant-time comparison of two secrets via their SHA-256 digests (lengths never leak). */
export async function sameSecret(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [x, y] = await Promise.all([crypto.subtle.digest("SHA-256", enc.encode(a)), crypto.subtle.digest("SHA-256", enc.encode(b))]);
  const u = new Uint8Array(x), v = new Uint8Array(y);
  let d = 0;
  for (let i = 0; i < u.length; i++) d |= u[i] ^ v[i];
  return d === 0 && a.length > 0;
}
