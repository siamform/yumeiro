# Server test, final (1.9.0 release candidate)

End-to-end checks of every server-backed feature (master prompt section 7). One row per request: what was
sent, what came back, what was expected, the result and where (local Worker test, staging, production read-only,
emulator). The Worker fix is not deployed until the owner's go; tests before the go run against the local Worker
(Miniflare / wrangler dev) and the production endpoints only read.

| ID | Service | Case | Request | Response | Expected | Result | Where |
|---|---|---|---|---|---|---|---|
| S-01 | Worker | all handlers, local | `server/test/*.test.ts` (node:test, SQLite D1 stand-in) at 2e68bf9 | 40 tests: 40 pass, 0 fail; `tsc --noEmit` clean | all pass | Pass | local Worker test, 2026-11-19 19:25 UTC |
| S-02 | Worker | health, production | `GET https://api.yumeiro.click/v1/health` | 200 `{"ok":true,"v":2,"cron_s":275}` in 0.33 s | 200, cron recent | Pass | production read-only |
| S-03 | Worker | 401 admin | `GET /v1/admin/fcm/stats` without token | 401 `{"error":"unauthorized"}` | 401 | Pass | production read-only; also local "admin: off without a strong token, refuses a wrong one" |
| S-04 | Worker | malformed body | `POST /v1/fcm/devices` body `nope` | 400 | 400, nothing stored | Pass | production (refused before any write); local "register: bad input is refused with a reason" |
| S-05 | Worker | 429 | per-IP new devices, per-device check-ins, Last.fm sign 30/60 s | 429 with reason | 429 | Pass | local tests "per-IP limit…", "POST /v1/lastfm/sign errors" |
| S-06 | Worker | gone push endpoint | queue send to a 404/410 endpoint | device forgotten; other failures keep it | as described | Pass | local test "queue: a gone endpoint (404/410)…" |
| S-07 | Worker | SSRF guard | push URLs to private / odd hosts | refused | refused | Pass | local "endpoint guard rejects SSRF shapes" |
| S-08 | Worker | airing cron | aired episode wakes followers once, next week planned | one wake per follower, health green | once, no duplicates | Pass | local "cron: …", "air-1: …" |
| S-09 | Worker | Last.fm sign (SEC-LFM) | `POST /v1/lastfm/sign` | local: signature equals the app's vectors, secret never returned; off → 503; wrong key → 403; other method → 403; bad JSON → 400; rate → 429. Production: 404 (route not deployed) | after deploy: 200 `{api_sig}` | Pass local; production pending the owner's go | local test; production read-only |
| S-10 | App ↔ Worker | down / slow / error | Last.fm sign answering 503, 500, 502 HTML, 403, short signature, 429 | transient error, plays kept, no loop | transient | Pass (unit, run ID pending) | `LastFmCloudSignTest` (CI) |
| S-11 | FCM | app closed / killed / duplicates | data-only wake to a stopped app; same wake twice | one notification | one notification | Not run yet | `FcmAiringWakeDeviceTest`, final chunk_00 (44593053433 / 44593085810) |
| S-12 | Production D1 | schema | migrations 0003–0005 | missing in production | applied by `scripts/deploy-worker.sh --go` | Blocked (owner's go, §12) | dry run 2026-11-19 19:32 UTC: 409 FCM phones, 1 Web Push device; needs 0003, 0004, 0005; Worker secrets present ADMIN_TOKEN, FCM_SERVICE_ACCOUNT (Last.fm secrets go in with `--go`) |
| I-01 | Trakt | key valid / invalid | `GET /shows/trending?limit=1` with app key / `bad` | 200 / 403 | 200 / refusal | Pass | live, read-only |
| I-02 | SIMKL | device sign-in | `POST /oauth2/device` with the app's params | 200 with device_code, user_code, verification_uri | 200 | Pass | live (creates an unused code that expires) |
| I-03 | SIMKL | call without user token | `GET /search/anime` | 401 `user_token_required`; bad id 412 | the app only calls Simkl after sign-in | Pass | live, read-only |
| I-04 | TMDB | token | `GET /3/movie/550` as Bearer / as `api_key` | 200 / 401 | the configured value is a read token (Bearer) | Pass | live, read-only; not in the APK (ApkSecretsTest) |
| I-05 | OpenSubtitles | key valid / invalid | `GET /api/v1/subtitles?imdb_id=137523` | 200 / 200 | search is open; downloads need the key | Pass (note: an invalid key is not detected on search) | live, read-only |
| I-06 | Last.fm | key valid / invalid | `artist.getinfo` | 200 / 403 error 10 | as described | Pass | live, read-only |
| I-07 | LRCLIB | found / not found | `/api/get` | 200 / 404 | as described | Pass | live, read-only |
| I-08 | AniList | valid / malformed query | GraphQL | 200 / 400 | as described | Pass | live, read-only |
