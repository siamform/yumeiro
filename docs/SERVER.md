# Yumeiro Cloud (server/)

One small, free Cloudflare Worker. Its only job is to say **"an episode you follow just aired, check now"** to devices that turned on push. That's the whole server.

**Live** since 2026-11-09 at `https://api.yumeiro.click` (Worker `yumeiro-cloud`, D1 `yumeiro`, Queue `yumeiro-wakes`, all on the free plan).

## Why so small

- **No content, no accounts.** The server stores a device's UnifiedPush endpoint and the AniList ids of shows it follows. It doesn't store titles, history, IPs or names.
- **The app does the work.** A push is a 1-byte nudge. The app ignores the payload by design (`PushWake.onMessage` → `BackgroundWork.wake`) and runs its usual feed and new-episode checks through its own sources.
- **Optional.** Without the server, push still works with ntfy's scheduled wake-ups (`PushPlan`, about 9 a day). The server only makes alerts arrive about 30 minutes after an episode airs, not hours later.

## How it works

```
App (push on) ── asks AniList for next airing times of its shows (phones aren't blocked)
      └─POST /v1/devices {endpoint, anilist:[ids], airing:[[id, at]]}──► Worker ──► D1 (devices, follows, airing)
                                                                │
Cron every 10 min ── airing rows with at in (last run → now] ───┤  (no outside calls)
                                                                ▼
                     followers of those shows ──► Queue (30 min delay, 40 endpoints/message)
                                                                ▼
                     POST "w" to each endpoint (ntfy / any UnifiedPush server) ─► app wakes
                     404 / 410 → device deleted
```

| Endpoint | What it does |
|---|---|
| `GET /v1/health` | `200 {ok:true, v:2, cron_s}` when D1 answers and the 10-minute job ran in the last 30 min; `503 {ok:false}` otherwise. `cron_s` = seconds since the last run. Point an uptime monitor here. |
| `POST /v1/devices` | Register or refresh. Optional `airing: [[anilistId, unixSeconds]]`: next-episode times the phone got from AniList; kept only for followed ids and times from 1 h ago to 15 days ahead. Check-ins limited to 3 per minute per device. New devices are limited to 5 per minute per IP (free rate-limit binding, best-effort, IPs not stored); `429 slow_down` otherwise. Body `{endpoint, anilist}`. Returns `{id, token}`. The token is only returned on first registration. Later calls send `Authorization: Bearer <id>.<token>`. Only the token's SHA-256 is stored. |
| `DELETE /v1/devices` | Remove this device (Bearer). |
| `POST /v1/fcm/devices` | Instant notices (FCM): register or refresh `{fid, topics, app_version, channel}`, weekly. Same `{id, token}` / Bearer scheme and limits as above. See [PUSH-FCM.md](PUSH-FCM.md). |
| `DELETE /v1/fcm/devices` | Remove this phone's FCM row (Bearer). |
| `POST /v1/admin/notify` | Owner only (`Authorization: Bearer $ADMIN_TOKEN`): send an announcement, update, maintenance or direct notice through FCM. |
| `GET /v1/admin/fcm/stats` | Owner only: device counts per topic. |

Why phones send airing times: AniList blocks requests from Cloudflare Workers ("You have been manually blocked", tested
2026-11-10 from a probe Worker with plain, custom, Referer and browser headers: all 403). We don't work around a
deliberate block. Phones are not blocked, already talk to AniList, and one `id_in` query per 50 shows is cheap.
The `airing` table is shared (one row per show, not per device), so 50k phones following the same 150 airing shows
write ~150 rows, and only when a time changes. After an episode fires the server guesses next week (+7 days), at
most 2 guesses in a row without a fresh report, then drops the row. A bad report can at worst cause one harmless extra
"check now" or one missed nudge until the next phone reports.

Housekeeping:
- Follow lists are only rewritten when they change.
- `seen` is updated at most once a day.
- Devices silent for 45 days are pruned at 03:00 UTC.
- Endpoints are SSRF-guarded: https only, default port, no IP literals, no localhost, `.local` or `.internal`, at most 1024 chars.

App side: `notify/CloudAlerts.kt`.
- `CloudAlerts.sync` runs from `DiscoveryWorker` and from `PushWakeWorker` when a new endpoint arrives. It's a no-op unless the follows or endpoint changed, or 3 days have passed.
- Follows are the same set the new-episode check uses. AniList ids are resolved by title and cached, with at most 10 lookups per run.
- After a failed call (server down, 429, 5xx, timeout) the app waits 6 h, then 12 h, 24 h, 48 h, then 3 days before trying again. Calls time out after 20 s and always run in the background.
- Turning push off drops the local credentials. The server deletes the row when the dead endpoint answers 404/410.
- The build field `CLOUD_HOST` (gradle property `cloudHost` or env `YUMEIRO_CLOUD_HOST`, default `api.yumeiro.click`) is the server host. Set it blank to build without the feature.

## Free-tier budget (50,000 monthly users)

Worst case assumes every user turns push on; in practice it's a fraction.

| Resource (free plan) | Limit | Expected use at 50k |
|---|---|---|
| Worker requests | 100,000/day | ≤ ~17,000 refreshes (each device every 3 days) + 144 cron runs |
| Worker CPU | 10 ms per request | Short D1 queries and a SHA-256; waiting on the network doesn't count |
| Subrequests | 50 per invocation | ≤ 40 pushes per queue message, max batch 1 |
| D1 rows written | 100,000/day | A refresh touches 1 row; follow lists are rewritten only when they change. New-device writes are rate-limited |
| D1 rows read | 5,000,000/day | Indexed lookups; one full `devices` scan a day for pruning |
| D1 storage | 500 MB per DB | 50k devices × ~10 follows ≈ a few MB |
| Queue operations | 10,000/day | ~3 ops per message; ~1 wake per device per day → ≤ 1,250 messages ≈ 3,750 ops |

If the server fails for a whole day, back-off keeps retries to roughly 3 per due device that day (≈ 50k requests worst case), still under the limit, so a failure can't snowball into a used-up quota.

Limits come from Cloudflare's docs: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/), [D1 limits](https://developers.cloudflare.com/d1/platform/limits/), [Queues pricing](https://developers.cloudflare.com/queues/platform/pricing/) and [Workers rate limiting](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/).

If queue ops ever get close to the limit, raise the cron interval or the per-message batch. Nothing else needs to change.

## When the server breaks

Nothing in the app waits on the server. Every call is in a background worker and fails soft.

| Failure | What users notice |
|---|---|
| Worker down, errors, or quota used up for the day | Alerts arrive on the normal schedule instead of ~30 min after airing: ntfy-scheduled wake-ups (~9/day) plus the regular 2 h / 4 h background checks. Playback, search, library, tracking all unaffected. |
| AniList down or blocking | The server never calls it. Phones that can't reach it send no times; the server keeps the last ones and guesses weekly. |
| A push server (ntfy) down | That nudge is skipped (no retries). The app still checks on its own schedule. |
| Domain or Cloudflare account lost | Same as "Worker down". Build with `cloudHost=` blank to remove the feature. |

## One-time setup (owner)

1. Create a free Cloudflare account. Add `yumeiro.click` and switch the domain's nameservers to the two Cloudflare gives you.
2. In `server/` (forks: replace `database_id` in `wrangler.toml` with your own):
   ```bash
   npm ci
   npx wrangler login
   npx wrangler d1 create yumeiro          # copy the database_id into wrangler.toml
   npx wrangler queues create yumeiro-wakes
   npm run db:init                          # creates the tables
   ```
3. Change the `routes` host in `wrangler.toml` if you use another domain. Then run `npm run deploy`.
4. Check `https://api.yumeiro.click/v1/health` (503 until the first 10-minute run, then 200).

## Is it working? (monitoring, all free)

- **Uptime monitor:** add `https://api.yumeiro.click/v1/health` to a free monitor (UptimeRobot, Better Stack or
  similar), every 5–10 minutes, alert on any non-200. A 503 means the database or the 10-minute job is broken, not
  only the Worker. 144–288 checks a day is nothing against the 100k request limit.
- **Dashboard:** Workers & Pages → `yumeiro-cloud` → Metrics: requests, errors, CPU. Errors should be ~0.
  D1 → `yumeiro` → Metrics: rows read / written per day. Queues → `yumeiro-wakes`: messages per day.
- **Live logs:** `npx wrangler tail yumeiro-cloud` shows every request and cron run with exceptions (`--format json`
  for details). This is how the AniList block was found: 61 cron runs in a row ended in `anilist 403`.
- **Database peek:** `npx wrangler d1 execute yumeiro --remote --command "SELECT count(*) FROM devices"`, same for
  `airing`. `SELECT * FROM state` shows `aired_to` (last run).
- **End to end:** push on in the app (ntfy), follow an airing anime, wait for ~30 min after its airing time.

### Deploying with an API token instead of `wrangler login`

Create the token under **My Profile → API Tokens → Create Token → Custom token** with these permissions:

| Type | Permission | Access |
|---|---|---|
| Account | Workers Scripts | Edit |
| Account | D1 | Edit |
| Account | Queues | Edit |
| Account | Account Settings | Read |
| Zone (`yumeiro.click` only) | Workers Routes | Edit |
| Zone (`yumeiro.click` only) | Zone | Read |

Set Account Resources to your account and Zone Resources to **Specific zone → yumeiro.click**. Then run Wrangler with
`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` in the environment. Never commit the token.

If `wrangler deploy` refuses to register the cron, open **Workers & Pages** once in the dashboard and choose a `workers.dev`
subdomain. The Worker doesn't need to serve on it.

No secrets are needed in the repo. CI (`.github/workflows/server.yml`) only runs tests and the type check, and only when `server/**` changes.

## Local development

Needs Node.js 22.12 or newer (Wrangler's minimum).

```bash
cd server && npm ci
npx wrangler d1 execute yumeiro --local --file=schema.sql
npx wrangler dev --test-scheduled         # then: curl "localhost:8787/__scheduled?cron=*/10+*+*+*+*"
npm test && npm run typecheck
```

## Not planned (on purpose)

The server only does what users depend on and nothing that could use up the free plan and break alerts for everyone.

| Idea (some from Sozo's backend) | Why not |
|---|---|
| Accounts, login, profiles | Privacy-first project; the owner asked for no account system. |
| Cloud backup / history sync | Would grow D1 toward its 500 MB-per-database limit and share the 100k writes/day with alerts. Local export/import already covers it. |
| Device linking / TV pairing | Needs a short-lived relay on every link; rarely used, not worth the shared quota. |
| Online watch-together rooms | Built, live-tested, then archived on branch `archive/online-rooms`. Durable Objects on the free plan share [100k requests and 100k row writes a day](https://developers.cloudflare.com/durable-objects/platform/pricing/); a few busy rooms could eat it. Local watch-together still works. |
| Comments, chat, friends, streaks, banners, analytics | Moderation load, storage growth, and not needed for streaming. |
| Content hosting or proxying | Legal risk and bandwidth; the app talks to sources directly. |
