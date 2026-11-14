# Instant notices (Firebase Cloud Messaging)

Yumeiro can deliver **announcements, app updates, maintenance alerts and messages for one device** within seconds, using Firebase Cloud Messaging (FCM) and the existing Yumeiro Cloud Worker (`server/`, `api.yumeiro.click`). Everything stays on free plans.

- **No new infrastructure.** Same Worker (`yumeiro-cloud`), same D1 database (`yumeiro`, one new table), same rate-limit bindings.
- **Only `firebase-messaging`.** No Analytics, Auth, Firestore, Crashlytics or the google-services Gradle plugin.
- **No secrets in the APK.** The app only carries the public Firebase *client* config (project id, app id, API key, sender id). The service account lives only in the Worker as a secret.
- **Optional for forks.** A build without FCM config runs normally with push off (`Settings → Notifications` hides the Instant alerts switch).

## Status (round 11, 2026-11-13)

| Piece | State |
|---|---|
| Firebase project | `yumeiro-ab29e`, one Android client `dev.asfakul.yumeiro`. Config in `app/google-services.json` (gitignored), never committed |
| Preview / debug builds | No own client registered, so `fcmClient()` in `app/build.gradle.kts` falls back to the `dev.asfakul.yumeiro` client (same project and key). Their topics stay `preview-…` / `debug-…`, so a test build never reaches real users. A dedicated client, if added later, wins automatically |
| D1 migration `0002_fcm_devices` | Applied to the remote `yumeiro` database |
| `ADMIN_TOKEN` Worker secret | Set (kept outside the repo by the owner) |
| Worker `yumeiro-cloud` | Deployed with the FCM routes. Live checks passed: health, register, refresh, wrong token 403, bad FID 400, admin 401 without token, stats, unregister |
| `FCM_SERVICE_ACCOUNT` Worker secret | Set 2026-11-13, replaced with the owner's current key 2026-11-14 (`firebase-adminsdk-fbsvc`); a `validate_only` send to `preview-announcements` with that key returned 200. `/v1/admin/fcm/stats` reports `fcm: true`; a `dry_run` announcement on `preview-announcements` was accepted by FCM v1 |
| Firebase API key | Currently unrestricted (Firebase Installations accepted it for the `.preview` package too). Restrict it in Google Cloud → APIs & Services → Credentials to Android apps `dev.asfakul.yumeiro` (+ `.preview`, `.debug`) with their signing SHA-1, and to the Firebase Installations + FCM Registration APIs |

### Load test

`server/test/load/fcm-load.mjs` simulates 2000 phones against a **local** `wrangler dev` (never production; it deliberately trips rate limits and would spend D1 quota). Round 11 result: 2000 registers, 2000 refreshes and 2000 unregisters all 200; per-device limit returned 429 for 27 of 30 burst calls, per-IP limit 35 of 40; malformed bodies, bad FIDs and wrong tokens gave 400/403, never 500.

## How it works

```
App start / switch changed ──► FcmSyncWorker (network constraint, back-off)
   │  FirebaseMessaging.register()           (FID mode, firebase-messaging 25.1+; no legacy tokens)
   │  subscribe/unsubscribe topics from the user's switches
   └─ POST /v1/fcm/devices {fid, topics, app_version, channel} ──► Worker ──► D1 fcm_devices
                                                                   (weekly check-in, for direct messages + counts)

Owner ── POST /v1/admin/notify (Bearer ADMIN_TOKEN) ──► Worker
            ├─ announcement | update | maintenance ──► FCM topic  (one request reaches everyone subscribed)
            └─ direct {device_id}  ──► D1 lookup ──► FCM fid       (404 UNREGISTERED → row deleted)
                                                       │
                       data-only, high priority, TTL   ▼
YumeiroMessagingService.onMessageReceived ──► FcmPlan.parse (validates, version window, expiry)
                                          ──► AppNotifier.post → inbox + notification on its channel
Tap ──► MainActivity: https link → browser · in-app action (update, extensions, market, streams,
        downloads, support, inbox) → runs in the app · nothing → inbox
```

### Topics

| Notice kind | Topic (release build) | Preview / debug builds | User switch | Android channel |
|---|---|---|---|---|
| `announcement` | `announcements` | `preview-announcements`, `debug-…` | Yumeiro news | What's new |
| `update` | `updates` | `preview-updates` | always on | What's new |
| `maintenance` | `maintenance` | `preview-maintenance` | Service alerts | Service alerts (high importance) |
| `direct` | — (device FID) | — | Service alerts | Service alerts |

Preview and debug builds listen on prefixed topics, so you can test with `"channel": "preview"` without reaching real users. Turning **Instant alerts** off leaves every topic, unregisters the FID and deletes the device row (`DELETE /v1/fcm/devices`).

### Why data-only messages with `android.priority: high`

The app builds the notification itself, so notices respect the user's switches, land in the in-app inbox, use Yumeiro's channels and chime, dedupe by id and open the right screen. Every message we send results in a visible notification, which is what FCM requires for high priority. Messages typed in the Firebase console also work (shown as announcements).

FCM's new `AndroidConfigV2` (`android_v2.remote_notification`, client callback `onNotificationReceived`) is still an early-access SDK (`firebase-messaging:26.1.0-eap01`, not on Google Maven) as of 2026-11. FCM will keep supporting the v1 `android` block for years; switch `buildMessage()` in `server/src/fcm.ts` to `android_v2` with `mutable_content: true` and `use_as_v1_data_message: true` once the client SDK is public. See [Migrate from AndroidConfig to AndroidConfigV2](https://firebase.google.com/docs/cloud-messaging/send/v2-api).

## What you need to provide (owner)

Nothing below is in the repo. Never commit any of it.

1. **A Firebase project** (free Spark plan). Console → Add project. Analytics not needed.
2. **Android apps** in that project, one per package you ship:
   - `dev.asfakul.yumeiro` (release)
   - `dev.asfakul.yumeiro.preview` (preview builds, optional)
   - `dev.asfakul.yumeiro.debug` (debug builds, optional)
3. **Client config** for the app build, either way:
   - download `google-services.json` and put it at `app/google-services.json` (already in `.gitignore`), or
   - set environment variables / Gradle properties (CI-friendly):

     | Env var | Gradle property | Value from `google-services.json` |
     |---|---|---|
     | `YUMEIRO_FCM_PROJECT_ID` | `fcmProjectId` | `project_info.project_id` |
     | `YUMEIRO_FCM_SENDER_ID` | `fcmSenderId` | `project_info.project_number` |
     | `YUMEIRO_FCM_API_KEY` | `fcmApiKey` | `client[].api_key[0].current_key` |
     | `YUMEIRO_FCM_APP_ID` | `fcmAppId` | `mobilesdk_app_id` of `dev.asfakul.yumeiro` |
     | `YUMEIRO_FCM_APP_ID_PREVIEW` | `fcmAppIdPreview` | … of `dev.asfakul.yumeiro.preview` |
     | `YUMEIRO_FCM_APP_ID_DEBUG` | `fcmAppIdDebug` | … of `dev.asfakul.yumeiro.debug` |

   These values are not secret (they ship inside every Firebase app), but keep them out of git anyway. In Google Cloud console you can restrict the API key to the Firebase Installations and FCM registration APIs and to your package names + signing certificate.
4. **A service account key for the Worker**: Firebase console → Project settings → Service accounts → Generate new private key. Keep the JSON file private; it only goes into the Worker secret below. The default `firebase-adminsdk` account already has the FCM send permission.
5. **An admin token**: any random string of 32+ characters, e.g. `openssl rand -base64 36`.

## Deploy (owner, one time)

From `server/` with Node ≥ 22.12 (`wrangler` reads `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID` from the environment):

```bash
npm ci
npm test && npm run typecheck
npm run db:migrate:fcm                               # adds the fcm_devices table (idempotent)
npx wrangler secret put ADMIN_TOKEN                  # paste the admin token
npx wrangler secret put FCM_SERVICE_ACCOUNT < /path/to/service-account.json
npx wrangler deploy
```

Rotate either secret any time with the same `secret put` command; the Worker picks it up on the next request. Delete the downloaded key file once it's stored, and revoke old keys in Google Cloud → IAM → Service accounts.

## Sending notices

```bash
API=https://api.yumeiro.click
AUTH="Authorization: Bearer $ADMIN_TOKEN"

# Check the payload with FCM without delivering it
curl -s -X POST $API/v1/admin/notify -H "$AUTH" -d '{"kind":"announcement","title":"Dream colours","body":"Every theme now has its own backdrop.","link":"https://yumeiro.click","dry_run":true}'

# Test on preview builds only
curl -s -X POST $API/v1/admin/notify -H "$AUTH" -d '{"kind":"announcement","channel":"preview","title":"Preview test","body":"Hello testers"}'

# App update (opens the in-app update dialog); only phones older than versionCode 7 show it
curl -s -X POST $API/v1/admin/notify -H "$AUTH" -d '{"kind":"update","title":"Yumeiro 1.1 is out","body":"Smoother player, sub/dub by language.","max_version":6}'

# Maintenance (expires with its TTL, so a late phone never shows a stale warning)
curl -s -X POST $API/v1/admin/notify -H "$AUTH" -d '{"kind":"maintenance","title":"Short maintenance tonight","body":"Alerts pause 02:00–02:15 UTC.","ttl":21600}'

# One device (device_id is the Worker id the phone got at registration; the user copies it from Settings → Notifications → "Copy support ID")
curl -s -X POST $API/v1/admin/notify -H "$AUTH" -d '{"kind":"direct","device_id":"AbCdEf123456","title":"Your report","body":"Fixed in 1.1, thanks."}'

# How many phones listen to each topic (no ids leave the Worker)
curl -s $API/v1/admin/fcm/stats -H "$AUTH"
```

Body fields: `kind` (required), `title` (≤ 80), `body` (≤ 600), `link` (https URL or `update`, `extensions`, `market`, `streams`, `downloads`, `support`, `inbox`), `channel` (`release` default, `preview`, `debug`), `device_id` (direct only), `min_version` / `max_version` (versionCode window, checked on the phone), `ttl` (60 s – 28 days, default 3 days), `id` (optional, for dedup; random otherwise), `dry_run`.

Responses: `200 {ok, id, target, name}` · `400 {error: bad_*}` · `401 unauthorized` · `404 unknown_device` · `410 device_gone` (row deleted) · `429 slow_down` (5 admin calls/min per IP) · `502/503` FCM or OAuth problem (`oauth_400` usually means a wrong or revoked key) · `503 admin_not_configured` / `fcm_not_configured` until the secrets are set.

## Security

- Service account only in the Worker secret; OAuth tokens are minted with WebCrypto (RS256 JWT, scope `firebase.messaging`) and cached in memory for under an hour.
- Admin endpoints fail closed without a 32+ character `ADMIN_TOKEN`, compare it in constant time and are rate-limited per IP.
- Device rows store only the SHA-256 of the device's own token. Knowing a FID (private to the phone and Firebase) is needed to reclaim a row after reinstall. FIDs and device ids never leave the Worker in responses except to their own phone.
- The phone validates every message (schema, id, sizes, link allow-list, version window, expiry) and drops anything malformed. Links are re-checked on tap.
- Messages carry no personal data. Nothing about what a user watches is sent for FCM.

## Free-tier budget (200,000 monthly users, all with Instant alerts on)

| Resource | Free limit | Use |
|---|---|---|
| FCM | free, no quota for topic sends at this scale | 1 request per broadcast |
| Worker requests | 100,000/day | ~29,000/day weekly check-ins + a few admin calls |
| D1 row writes | 100,000/day | ~29,000/day (1 write per check-in) |
| D1 storage | 5 GB | ~200 bytes × 200k rows ≈ 40 MB |

Broadcasts don't touch D1 at all. If the Worker is ever down, phones keep receiving topic broadcasts; only direct messages to newly installed phones wait for their next check-in.

## Files

- App: `notify/FcmPush.kt` (`FcmPush`, `FcmPlan`, `YumeiroMessagingService`, `FcmSyncWorker`), `notify/AppNotifier.kt` (Service alerts channel, deep-link extra), `notify/NotifySettings.kt` (`SERVICE`, `FCM`), `MainActivity.handleIntent`, `ui/screens/extras/AppPages.kt` (`runAction`), Settings → Notifications.
- Build: `app/build.gradle.kts` (`FCM_*`, `PUSH_CHANNEL` BuildConfig fields), `gradle/libs.versions.toml` (Firebase BoM 35.2.0 → messaging 26.0.2).
- Worker: `server/src/fcm.ts`, routes in `server/src/index.ts`, `server/schema.sql`, `server/migrations/0002_fcm_devices.sql`, tests in `server/test/fcm.test.ts`.
- Tests: `app/src/test/.../notify/FcmPlanTest.kt`, `FcmDeliveryTest.kt`.
