# Yumeiro Cloud (server/)

One small, free Cloudflare Worker. Its only job is to say **"an episode you follow just aired, check now"** to devices that turned on push. That's the whole server.

## Why so small

- **No content, no accounts.** The server stores a device's UnifiedPush endpoint and the AniList ids of shows it follows. It doesn't store titles, history, IPs or names.
- **The app does the work.** A push is a 1-byte nudge. The app ignores the payload by design (`PushWake.onMessage` → `BackgroundWork.wake`) and runs its usual feed and new-episode checks through its own sources.
- **Optional.** Without the server, push still works with ntfy's scheduled wake-ups (`PushPlan`, about 9 a day). The server only makes alerts arrive about 30 minutes after an episode airs, not hours later.

## How it works

```
App (push on) ──POST /v1/devices {endpoint, anilist:[ids]}──► Worker ──► D1 (devices, follows)
                                                                │
Cron every 10 min ── AniList airingSchedules (last run → now] ──┤
                                                                ▼
                     followers of those shows ──► Queue (30 min delay, 40 endpoints/message)
                                                                ▼
                     POST "w" to each endpoint (ntfy / any UnifiedPush server) ─► app wakes
                     404 / 410 → device deleted
```

| Endpoint | What it does |
|---|---|
| `GET /v1/health` | `{ok:true}` |
| `POST /v1/devices` | Register or refresh. Body `{endpoint, anilist}`. Returns `{id, token}`. The token is only returned on first registration. Later calls send `Authorization: Bearer <id>.<token>`. Only the token's SHA-256 is stored. |
| `DELETE /v1/devices` | Remove this device (Bearer). |

Housekeeping:
- Follow lists are only rewritten when they change.
- `seen` is updated at most once a day.
- Devices silent for 45 days are pruned at 03:00 UTC.
- Endpoints are SSRF-guarded: https only, default port, no IP literals, no localhost, `.local` or `.internal`, at most 1024 chars.

App side: `notify/CloudAlerts.kt`.
- `CloudAlerts.sync` runs from `DiscoveryWorker` and from `PushWakeWorker` when a new endpoint arrives. It's a no-op unless the follows or endpoint changed, or 3 days have passed.
- Follows are the same set the new-episode check uses. AniList ids are resolved by title and cached, with at most 10 lookups per run.
- Turning push off drops the local credentials. The server deletes the row when the dead endpoint answers 404/410.
- The build field `CLOUD_HOST` (gradle property `cloudHost` or env `YUMEIRO_CLOUD_HOST`, default `api.yumeiro.click`) is the server host. Set it blank to build without the feature.

## Free-tier budget (30,000 monthly active devices)

| Resource (free plan) | Limit | Expected use |
|---|---|---|
| Worker requests | 100,000/day | ~10,000 refreshes (every 3 days) + 144 cron runs |
| Subrequests | 50 per invocation | ≤ 40 pushes per queue message, max batch 1 |
| D1 rows written | 100,000/day | Refreshes touch 1 row. Follow lists are rewritten only on change. |
| D1 storage | 500 MB per DB | ~30k devices × ~10 follows ≈ a few MB |
| Queue operations | 10,000/day | Each message is about 3 ops (write, read, delete). Allowing for about 1 airing wake per device per day, that's ~750 messages, or about 2,250 ops. |

Limits come from Cloudflare's docs: [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/), [D1 limits](https://developers.cloudflare.com/d1/platform/limits/) and [Queues pricing](https://developers.cloudflare.com/queues/platform/pricing/).

If queue ops ever get close to the limit, raise the cron interval or the per-message batch. Nothing else needs to change.

## One-time setup (owner)

1. Create a free Cloudflare account. Add `yumeiro.click` and switch the domain's nameservers to the two Cloudflare gives you.
2. In `server/`:
   ```bash
   npm ci
   npx wrangler login
   npx wrangler d1 create yumeiro          # copy the database_id into wrangler.toml
   npx wrangler queues create yumeiro-wakes
   npm run db:init                          # creates the tables
   ```
3. In `wrangler.toml`, uncomment the `routes` line (`api.yumeiro.click`). Then run `npm run deploy`.
4. Check `https://api.yumeiro.click/v1/health`.

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

```bash
cd server && npm ci
npx wrangler d1 execute yumeiro --local --file=schema.sql
npx wrangler dev --test-scheduled         # then: curl "localhost:8787/__scheduled?cron=*/10+*+*+*+*"
npm test && npm run typecheck
```

## Not planned (on purpose)

There are no user accounts, comments, chat, analytics or content hosting. Watch-party rooms may come later as a separate small Durable Object, but only if the community asks for it.
