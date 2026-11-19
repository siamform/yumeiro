/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// SEC-LFM (ADR-048): Last.fm's api_sig for Yumeiro's built-in API key is made here, so the shared secret
// never ships in the APK. Only the four calls the app makes can be signed, only for Yumeiro's key, with a
// size cap; the format is Last.fm's (https://www.last.fm/api/authspec): MD5 over every parameter except
// format/callback/api_sig, sorted by name, "<name><value>", then the secret, UTF-8, lowercase hex.

export const SIGNABLE = new Set(["auth.getToken", "auth.getSession", "track.updateNowPlaying", "track.scrobble"]);
export const MAX_PARAMS = 260; // track.scrobble: 50 plays × 5 fields + method, api_key, sk
export const MAX_BYTES = 32_768;
const UNSIGNED = new Set(["format", "callback", "api_sig"]);

/** The parameters to sign, or an error code. Rejects anything the app would never send. */
export function signRequest(raw: unknown, apiKey: string): { params: Record<string, string> } | { error: string } {
  const p = (raw as { params?: unknown } | null)?.params;
  if (!p || typeof p !== "object" || Array.isArray(p)) return { error: "bad_params" };
  const entries = Object.entries(p as Record<string, unknown>);
  if (entries.length === 0 || entries.length > MAX_PARAMS) return { error: "bad_params" };
  const out: Record<string, string> = {};
  let bytes = 0;
  for (const [k, v] of entries) {
    if (typeof v !== "string" || !/^[a-z_]+(\[\d{1,2}\])?$/.test(k)) return { error: "bad_params" };
    bytes += k.length + v.length;
    out[k] = v;
  }
  if (bytes > MAX_BYTES) return { error: "too_large" };
  if (!SIGNABLE.has(out.method ?? "")) return { error: "method_not_allowed" };
  if (!apiKey || out.api_key !== apiKey) return { error: "wrong_key" };
  return { params: out };
}

export function signatureBase(params: Record<string, string>, secret: string): string {
  return Object.keys(params).filter((k) => !UNSIGNED.has(k)).sort().map((k) => k + params[k]).join("") + secret;
}

export function apiSig(params: Record<string, string>, secret: string): string {
  return md5Hex(new TextEncoder().encode(signatureBase(params, secret)));
}

// RFC 1321 MD5 (Last.fm's format needs it; Workers' subtle MD5 isn't in Node, so tests and the Worker share this).
const S = [7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21];
const K = Array.from({ length: 64 }, (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32) >>> 0);
export function md5Hex(msg: Uint8Array): string {
  const len = msg.length;
  const total = (((len + 8) >> 6) + 1) << 6;
  const buf = new Uint8Array(total);
  buf.set(msg); buf[len] = 0x80;
  const dv = new DataView(buf.buffer);
  dv.setUint32(total - 8, (len * 8) >>> 0, true);
  dv.setUint32(total - 4, Math.floor(len / 0x20000000), true);
  let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
  for (let off = 0; off < total; off += 64) {
    let a = a0, b = b0, c = c0, d = d0;
    for (let i = 0; i < 64; i++) {
      let f: number, g: number;
      if (i < 16) { f = (b & c) | (~b & d); g = i; }
      else if (i < 32) { f = (d & b) | (~d & c); g = (5 * i + 1) % 16; }
      else if (i < 48) { f = b ^ c ^ d; g = (3 * i + 5) % 16; }
      else { f = c ^ (b | ~d); g = (7 * i) % 16; }
      const t = (a + f + K[i] + dv.getUint32(off + g * 4, true)) >>> 0;
      a = d; d = c; c = b;
      b = (b + ((t << S[i]) | (t >>> (32 - S[i])))) >>> 0;
    }
    a0 = (a0 + a) >>> 0; b0 = (b0 + b) >>> 0; c0 = (c0 + c) >>> 0; d0 = (d0 + d) >>> 0;
  }
  return [a0, b0, c0, d0].map((w) => [0, 8, 16, 24].map((s) => ((w >>> s) & 0xff).toString(16).padStart(2, "0")).join("")).join("");
}
