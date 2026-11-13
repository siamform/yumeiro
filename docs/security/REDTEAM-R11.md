# R11 red-team review (pre-production)

Goal: think like someone who wants to hurt Yumeiro's users or take the service down for the whole community,
find what actually works, and fix the cause. Scope: the Android app (R11 redesign), the Yumeiro Cloud Worker
(`api.yumeiro.click`, D1, Queues, FCM) and the release pipeline.

## Fixed

| # | Severity | Attack | Impact | Fix |
|---|---|---|---|---|
| S1 | High (community) | **D1 write-budget burn.** One registered device sends check-ins (3 / min allowed) that alternate between two 300-show follow lists. Each check-in deleted and re-inserted every follow row (~600 writes). | ~2.6 M row writes / day from one phone; the free plan allows 100 k, after which D1 refuses writes and registration, check-ins and push stop for **everyone**. | Follow lists are written as a **diff** (only added / removed rows), and a device may replace its list at most **once per 10 minutes** (`follow_changes` table). Unchanged lists still cost no writes. |
| S2 | High (community) | **Shared airing-timetable poisoning.** A device follows popular shows and reports fake next-episode times. Any single report overwrote the shared time. | Wake-ups at fake times for every follower (battery, data, and a burst of syncs against third-party sites), or real wake-ups pushed weeks away. | `planAiring`: the first report for an unknown show and replacements for rolled guesses are taken; a **confirmed** time changes only when **two different devices, each at least 3 days old**, report the same new time (`airing_pending`). One device can never move it. |
| A1 | High (privacy) | **Login CSRF on tracker sign-in.** Any web page or app can open `yumeiro://anilist-login#access_token=<attacker>`. | The victim's watch progress goes to the attacker's AniList account (the attacker sees what they watch), or a junk token signs them out. | Redirects are only accepted within **10 minutes** of tapping *Sign in* in the app, **once** (`OAuthGuard`), and the token is stored only after AniList accepts it. A bad link never replaces a working login. MAL's PKCE verifier is single-use. |
| A2 | Medium | **Private-file open via "Open with".** A malicious app sends `ACTION_VIEW file:///data/data/dev.asfakul.yumeiro/…` or our own `content://` authority. | The player (which can save / share what it opens) is pointed at our private storage. | `IncomingMedia.pointsIntoPrivateStorage` rejects those URIs before anything opens. |
| A3 | Medium | **Poisoned shared backup.** A "backup with all my sources" sent in a chat group contains `files/extensions/*.cs3`, `files/tachiyomi/*.ext` or `files/js/*` (runnable code) or a 50 GB zip bomb. The manifest can claim it has no extensions. | Silent code install on restore; storage exhaustion. | `inspect` works out `hasCode` from the real entries. Code is restored only when the user ticks *Also restore installed extensions* (off by default, with a warning). Restore writes only to known data folders, and stops at 768 MB / 50 000 files. |
| A4 | Low | **Stream-finder JS bridge flood.** A page calls `MTFinder.onMedia()` in a loop with endless unique URLs. | Unbounded memory → crash. | At most 64 captures and 8 KB per URL; DRM labels are trimmed and filtered. |

Tests: `server/test/logic.test.ts` (followDiff, planAiring), `IncomingMediaGuardTest` (private URIs, OAuth window,
backup targets), plus the existing zip-slip and SSRF tests.

## Checked and found sound

- **Admin API**: constant-time token compare, per-IP rate limit, 32+ char token, admin disabled when unset.
- **Device tokens**: only SHA-256 stored, constant-time compare, 401 / 403 split, FIDs and IDs never returned by stats.
- **SSRF**: push endpoints must be https on the default port with a public DNS name (no IP literals, localhost, `.local`, `.internal`, credentials); redirects are not followed.
- **FCM notices**: links are https or a fixed set of in-app actions, checked on the server **and** in the app.
- **Updater**: signing certificate and SHA-256 are verified before install; release keys never leave GitHub secrets.
- **WebViews**: file access off, non-http(s) navigations blocked, ad shield, render-process crashes contained.
- **Catalog downloads**: hard size limits on market indexes, playlists (40 MB) and subtitles (8 MB).
- **Backups**: zip-slip rejected; account tokens (Keystore-bound) are never exported.
- **CI**: actions pinned to SHAs, read-only default permissions, data branch published with a deploy key.

## Residual risk and recommendations

1. **Free-plan limits are the real ceiling.** Per-IP limits can't stop a botnet creating new devices (each first
   check-in writes up to ~300 follow rows). For 200 k monthly users, move the Worker to **Workers Paid** (US$5 / month:
   50 M D1 writes / month) and keep the guards above. Until then a determined attacker with many IPs can still hit
   the daily write cap; push then pauses until the next UTC day, and the app keeps checking on its own schedule.
2. **Reflection through push endpoints.** A UnifiedPush endpoint can be any public https URL, so registered devices
   could point wake-ups at a victim site. After S2 this is bounded to real airing times, and endpoints that answer
   404 / 410 are dropped. Restricting to known distributors would break self-hosted ntfy, so it is left open.
3. **Media session.** As on every Android media app, other apps can see what's playing through the exported
   MediaSession (needed for Bluetooth, Android Auto and the system media controls).
4. **Rotate the keys pasted in chat** during development (Firebase service account, API keys) and restrict the
   Firebase Android API key to the app's package and signing certificate in Google Cloud Console.

## Deploying the server fixes

```bash
cd server
npm run db:migrate:guards   # creates airing_pending + follow_changes (safe to run twice)
npm run deploy
```

Run the migration first: the new code reads both tables.
