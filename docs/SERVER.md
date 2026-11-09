# Yumeiro Cloud (server/)

One small Cloudflare Worker on the **free plan** (the domain is the only paid thing). Two jobs, both about watching,
both optional, no accounts:

1. **Airing alerts:** say "an episode you follow just aired, check now" to devices that turned on push.
2. **Online watch-together rooms:** relay end-to-end encrypted sync messages between a host and friends anywhere
   (one hibernating Durable Object per room, nothing stored per message).

Deployed at `https://api.yumeiro.click` since 2026-11-09.

**Rule: the app never depends on it.** If the Worker is down, misconfigured, or Cloudflare's free daily quota is used
up, everything else works exactly as before: playback, sources, downloads, Wi-Fi rooms, local new-episode checks and
ntfy-scheduled push wake-ups. See "When the server breaks" below.

Considered and dropped on purpose (2026-11-09 scope review for 50k monthly users on the free plan): encrypted cloud
backup and "link a device". Each backup is hundreds of KB; D1 allows 500 MB per database, so a few thousand users would
fill the database the alerts also use and break them. Backups stay as files the user owns (Backup & restore).

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
| `GET /v1/health` | `{ok:true, v:2, features:["alerts","rooms"]}` |
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

## End-to-end encryption (rooms)

All crypto is in the app (`cloud/CloudCrypto.kt`, tested in `CloudCryptoTest` incl. the RFC 5869 vector): HKDF-SHA256 for
keys, AES-256-GCM with a random 12-byte IV, and associated data that binds every frame to its room and kind (`state` or `msg`).
Frames are `v1.` + base64url(iv ‖ ciphertext ‖ tag). The server only checks that shape.

| Secret (stays on devices) | Derived | Server sees |
|---|---|---|
| Room secret, 16 random bytes, in the invite link's `#fragment` (browsers never send it) | key = HKDF(secret, "yumeiro-room", roomId); AAD `roomId|state` or `roomId|msg` | room id, sealed frames |

The room secret is RFC 4648 base32 (26 characters); typing forgives case, spaces, dashes and 0/1/8 for O/I/B.

## Room endpoints

| Endpoint | What it does |
|---|---|
| `POST /v1/rooms` | Create a room. Returns `{id, host}` (8-char id; host token, only its SHA-256 is stored). Rate-limited per IP. |
| `GET /v1/rooms/<id>/ws` | WebSocket. `Authorization: Bearer <host>` joins as host. Server sends `hello {you, role, now, peers, host}`. Host sends `state {c}` (relayed, never stored); anyone sends `msg {c}` (relayed with `from`); host sends `close`. Server also sends `peers {n, host, joined?}` and `closed`. A newcomer gets the state because the host's app re-sends it when `peers` arrives with `joined`. 24 peers, 4 KB frames, 40 frames / 10 s per socket. Ends when the host closes it, when empty at two alarms in a row (5–15 minutes), or after 24 hours. |
| `GET /r/<id>` | Invite page for people without the app open: hands `#fragment` to `yumeiro://room/<id>#…`, never sends it anywhere. Strict CSP. |

App side:
- `watch/WatchTogether.kt` (`hostOnline`, `joinOnline`, `say`; pure rules in `RoomPlan`). The host sends state only on
  a real change (title, episode, play/pause, a seek over 2 s) plus a 30-second heartbeat; `at` is converted to the server
  clock so every guest extrapolates the same position. Guests reuse the Wi-Fi rooms' `follow()`. OkHttp pings every 25 s
  (Cloudflare answers them without waking the room), reconnects with backoff up to 12 times.
- `cloud/CloudApi.kt`: short timeouts (8 s connect, 15 s total) and `CloudGate`, a circuit breaker: after a server
  error or quota error the app stops trying for 2 → 4 → … 60 minutes and shows "Online rooms are unavailable right now.
  Rooms on the same Wi-Fi still work." A network blip only triggers the normal reconnect.
- `ui/screens/watch/WatchTogetherScreen.kt`.
- Deep link `yumeiro://room/<id>#<key>` → `RoomNav` → Watch together asks before joining.

## Free-plan budget (50,000 monthly active users)

Assumptions: 30% turn on push (15k devices, refresh every 3 days), and on a busy day 1,000 online rooms of ~4 people
for ~2 hours. Free limits reset at 00:00 UTC; when one is used up, only calls of that kind fail.

| Resource (free plan) | Limit | Expected use |
|---|---|---|
| Worker requests | 100,000/day | ~5,000 alert refreshes + 144 cron runs + ~7,000 room creates / joins / reconnects + invite pages ≈ 15,000 |
| Subrequests | 50 per invocation | ≤ 40 pushes per queue message, max batch 1 |
| D1 rows written | 100,000/day | ~5,000 (a refresh touches 1 row; follow lists are rewritten only on change). Rooms write nothing to D1. |
| D1 storage | 500 MB per DB | ~15k devices × ~10 follows ≈ a few MB |
| Queue operations | 10,000/day | ~3 ops per message; ~750 messages ≈ 2,250 ops |
| Durable Object requests | 100,000/day (incoming WebSocket messages bill 20:1; protocol pings free) | per room ≈ 4 connects + ~500 frames / 20 + 12 alarms ≈ 40 → ~40,000 at 1,000 rooms; room for ~2,000 rooms a day |
| Durable Object row writes | 100,000/day (each `setAlarm` = 1 row) | per room ≈ 2 at creation + 12 alarms + a few on empty / delete ≈ 18 → ~18,000 |
| Durable Object duration | free while hibernating | rooms hibernate between frames |
| Rate limit binding | free | 20 room creations / 60 s per IP |

Limits come from Cloudflare's docs: [Durable Objects pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/), [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/), [D1 limits](https://developers.cloudflare.com/d1/platform/limits/) and [Queues pricing](https://developers.cloudflare.com/queues/platform/pricing/).

If queue ops ever get close to the limit, raise the cron interval or the per-message batch. If Durable Object requests
get close (check **Workers & Pages → yumeiro-cloud → Metrics**), raise `RoomPlan.HEARTBEAT_MS` or lower `MAX_PEERS`.

## When the server breaks

| What fails | What users notice |
|---|---|
| Worker down / deploy broken / Worker request quota used up (Error 1027) | Alerts arrive later (ntfy-scheduled wake-ups, ~9 a day, and the normal background checks still run). "Create online room" shows the busy message; Wi-Fi rooms work. |
| Durable Object quota used up | Same busy message for new online rooms. Rooms already open keep relaying until their next request fails, then guests keep playing on their own. |
| D1 or Queue quota used up | Alerts arrive later, as above. Rooms are unaffected (they don't use D1 or Queues). |
| Server unreachable mid-room | Each phone keeps playing; guests reconnect with backoff (up to 12 tries) and re-sync when the host answers. |

Nothing on the playback path, start-up or any screen waits on the server. `CLOUD_HOST` blank builds the app without it.

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

There are no user accounts, cloud backups, public comments, profiles, analytics, stream proxying or content hosting. Room chat exists, but it is end-to-end encrypted, never stored by the server, and disappears from each phone when the room ends.
