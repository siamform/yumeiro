# F-AIR Episode-airing alerts through push (ADR-043)

Status: **Built, not tested** on devices (batch B). Server code tested locally. **Not deployed**: the
Worker deploy waits for the owner's "go".

## 1. Root cause (evidence)

- Production D1, read-only count on 2026-11-19: 406 `fcm_devices`, 1 UnifiedPush device, 0 follows and
  0 airing rows. The deployed Worker dates from 2026-11-12, and migrations 0003/0004 aren't applied.
- In code: `airingWakes` selected only UnifiedPush `devices`/`follows`, and Firebase phones never sent their
  shows (`FcmPush.registerWithCloud` had no `anilist`/`airing`). So 405 of 406 users could never get a
  server-timed episode alert. They only got the phone's own periodic check, which Doze delays.

## 2. Competitors (ideas only; nothing copied)

| Project | Commit | Files read | How alerts arrive |
|---|---|---|---|
| [mihonapp/mihon](https://github.com/mihonapp/mihon) (Apache-2.0) | f64d82b94218b42410c36e6c85da818574865a28 | `data/library/LibraryUpdateWorker.kt` (`setupTask`) | Periodic WorkManager job, user interval, optional unmetered/charging; no push |
| [KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu) (GPL-3.0, archived) | 34f6e5232bf7f0bcdf8f3fbddb986697518e82a4 | `tracker/work/TrackWorker.kt` (`schedule`, batches) | Periodic job ≥ 2 h, batches of tracks, linear backoff; no push |

Both only poll, so an alert can come hours late. Yumeiro keeps its own check and adds a wake that the
server times to the actual airing: 30 minutes after the episode airs, so sources have it by then.

## 3. Design (built)

- Server:
  - migration `0005_fcm_follows.sql`;
  - shared `storeFollows` / `storeAiring` for both kinds of device;
  - `airingWakes` queues `{wake:{fids}}`;
  - `fcm.ts buildWake` sends a data-only, high-priority message (TTL 6 h, collapse `yumeiro-wake`);
  - a gone token deletes its row and follows;
  - prunes keep the Firebase follows.
- App:
  - `FcmPush.registerWithCloud` sends follows and next airing times from `CloudAlerts`;
  - `syncFollows` runs from `DiscoveryWorker`;
  - `handle(kind=wake)` → `BackgroundWork.wake` → unique work `push_catch_up_episodes`.

## 4. Feature profile

| Field | Value |
|---|---|
| Purpose | Episode alerts close to airing time for Firebase phones (most users) |
| Entry points | None new: Settings → Notifications → Episodes (existing) |
| Storage / migration | Server: `fcm_follows` table + `fcm_devices.follows_v` (migration 0005, applies cleanly on the old schema). App: pref `push_wake_at` in `background_work` |
| Permissions | None new (POST_NOTIFICATIONS existing) |
| Settings | Episodes / Background toggles: off → empty follow list → no wakes |
| Backup / restore | Nothing new to back up (follows come from the library) |
| Reset | Turning push off / reset removes the FCM row; the server deletes follows with it |
| Errors / offline | Phone offline → FCM keeps the wake up to 6 h (collapsed); server failure → the phone's own periodic check still runs |
| Accessibility | Same notifications as before |
| TV | Same code in the TV build; `FcmAiringWakeDeviceTest` runs on both TV images (the wake handler; Google's delivery itself is the owner test) |
| Low-end | One small data message per airing (at most one per 30 min per phone through cooldowns) |
| Performance budget | Worker: one D1 join per cron and 1 queue message per 40 phones; app: one unique work per wake |
| Inventory | INVENTORY rows F-AIR server wake, F-AIR app wake |

## 5. Side effects

- Free tier: 406 phones × a few shows → tiny. The queue and FCM calls scale with followers of airing
  shows, not with all devices.
- Battery: one high-priority data message per new episode of a followed show. That's the same work the
  periodic check already does.
- UnifiedPush path unchanged (server tests cover both).

## 6. Service terms

FCM data messages under the Firebase terms. AniList is read only by phones, as before (Workers are
blocked by AniList, see SERVER.md). There's no new data about users: show ids only, and no IPs are stored.

## 7. Tests

- Server `npm test` (old code 33 pass / 2 fail, new 35/35), typecheck. Also a local `wrangler dev
  --test-scheduled` run: register with follows → `/__scheduled` → the airing row rolls.
- Unit `FcmAiringWakeTest`; device `FcmAiringWakeDeviceTest` (both phones), `PushAlertsEffectDeviceTest`
  as regression.
- Owner/community test: a real alert on a phone after the deploy.

## 8. Deploy (only after "go")

```
cd server && npm run db:migrate:guards && npm run db:migrate:want && npm run db:migrate:fcm-follows && npx wrangler deploy
```
