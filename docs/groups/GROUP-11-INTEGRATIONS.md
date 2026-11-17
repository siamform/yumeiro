# Group 11 audit: Integrations (R17, seventh handover)

Scope (INVENTORY G11): I1 Trakt, I2 Simkl, I3 Kitsu + AniList (+ MyAnimeList), I4 TMDB metadata,
I5 OpenSubtitles / SubDL / SubSource, I6 LRCLIB, I7 Last.fm, I8 Firebase (FCM, cloud alerts),
I9 Cloudflare Worker endpoints, I10 YouTube account, I11 GitHub releases (self-update).

Method (master handover 7.7): map, read every file line by line, action–reaction table with unhappy
paths, findings register with root cause, regression test that fails before and passes after,
labelled verification, competitor notes. Results are labelled unit, emulator, CI or hardware.

## Status

In progress. Started 2026-11-17 with the lead left by the sixth agent (Trakt/Simkl `awaitToken`).

## Findings register

| ID | Item | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|---|
| IG-1 | I1, I2 | Trakt and Simkl sign-in can never finish in a release build; a debug build freezes on each poll | `PrivacyTrackingBackup.kt` starts sign-in with `scope.launch` (main) and called `Trakt/Simkl.awaitToken` directly; both poll with blocking `execute()` on the caller's thread. Release main thread = death on network → NetworkOnMainThreadException at the first poll (5 s after the code is shown). Confirmed by reading: `deviceCode` was wrapped in `withContext(IO)` at the call site but `awaitToken` was not (lines 194–216); Last.fm's `awaitSession` is wrapped | `awaitToken` runs its whole loop in `withContext(Dispatchers.IO)` (main-safe at the source, so any caller is safe) | `TrackerSignInDeviceTest` (emulator) | verified (emulator): 44049036117 → 44049031398 |
| IG-2 | I1–I3 | When Kitsu (or MAL lookup) failed, Trakt and Simkl got nothing, and the whole update repeated every ~5 s progress tick | `AnimeSync.onProgress` ran the anime trackers unguarded before Trakt/Simkl in one `runCatching`; `Kitsu.get` throws on non-2xx; the failure handler released the single episode key | Own key and own `runCatching` per tracker; a thrower is retried at most 3 times | `AnimeTrackersDeviceTest` b, c (emulator) | fix in; runs pending |
| IG-3 | I3 | Kitsu and MAL stop tracking ~30 days after sign-in while still showing the account | Refresh tokens stored, never used; no expiry stored | `validToken` per tracker (refresh a day ahead / once on 401; refused refresh signs out; MAL rotated refresh token saved) | `AnimeTrackersDeviceTest` a, b (emulator); `KitsuLoginTest` (unit) | fix in; runs pending |
| IG-4 | I7 | Listens waiting at Last.fm sign-in were uploaded on the main thread (release: thrown, swallowed, not sent; debug: freeze) | `onSuccess { …; Scrobbler.flush(context) }` runs on the screen scope (main); `flush` posts with blocking OkHttp | `flush` wraps itself in `withContext(Dispatchers.IO)` | `ScrobbleFlushDeviceTest` (emulator) | fix in; runs pending |
| IG-5 | I4 | Episode thumbnails missing for the rest of the session after backing out mid-lookup or one offline lookup | Title marked tried before the lookup; lookup ran in the screen's LaunchedEffect, result dropped on cancellation; failures kept the mark | Own scope, result always stored, failure clears the mark | `EpisodeStillsDeviceTest` (emulator) | fix in; runs pending |
| IG-6 | I8, I9 | A show whose AniList lookup failed once (offline, 429, 5xx) never got airing alerts again | `aniListIdForTitle` returns null for failure and for not-found; CloudAlerts cached null as 0 = not anime, no expiry | `aniListIdOrThrow`; failures not cached; old zeros re-checked once | `CloudAlertsLookupDeviceTest` (emulator) | fix in; runs pending |
| IG-7 | I11 | Subtitle search showed an error and lost the exact hash match when the title search hit HTTP 429 | title-search exception not caught after the hash search | failure only surfaces when nothing was found; 4 MB reply cap | `OnlineSubtitlesFallbackDeviceTest` (emulator) | fix in; runs pending |
| IG-8 | I1, I2 | After a failed re-sign-in the app said "Signed in to AniList as <old user>" | message chosen from "any token saved" | success only when a new token was saved | `AnimeSignInMessageDeviceTest` (emulator) | fix in; runs pending |

## Code read

| File | Lines | Read | Notes |
|---|---|---|---|
| tracking/Trakt.kt | 178 | yes | IG-1. Refresh serialized (`refreshLock`), single-use refresh token stored at once; 401 retry once |
| tracking/Simkl.kt | 188 | partly (sign-in, refresh) | IG-1 |
| ui/screens/settings/PrivacyTrackingBackup.kt (Tracking card) | 150–260 | yes | IG-1 call sites |
| online/anime/AnimeSync.kt | 110 | yes | IG-2. AniSkip `load` keyed per episode, stale result dropped by key check |
| tracking/Kitsu.kt | 100 | yes | IG-3, IG-2 (throws on non-2xx). Login: password grant, own client pair optional (ADR-013) |
| online/anime/AniList.kt (AniList, MyAnimeList, OAuthGuard) | 146 | yes | IG-3 (MAL). AniList implicit token: 1 year, no refresh; 401 → false. OAuthGuard: 10-min single-use window against login CSRF (fine) |
| tracking/Simkl.kt (rest) | 188 | yes | Matches the AUTH V2 docs (device flow, `media:write` check, 7-day token, sliding non-rotating refresh): https://api.simkl.org/api-reference/oauth2-device . No finding |
| scrobble/LastFm.kt, Scrobbler.kt | 124, 121 | yes | IG-4. Queue: 500 cap, 14-day cutoff, bad entries skipped (G6), code 9 signs out and keeps the queue. Observation: one network error during the sign-in poll ends the sign-in (Trakt/Simkl same); not a defect, user retries |
| lyrics/LrcLib.kt | 142 | yes | Called on IO (TrackDetailsRepository). 512 KB body cap, week-long miss marks pruned (G6). Observation: 32-bit cache key; a collision would show another song's lyrics (about 1% chance of one collision in a 10 000-song library); cache only, left as is |
| online/subtitles/OpenSubtitles.kt | 82 | yes | Sorted lowercase params (no 301). Lead checked and dismissed: show IMDb id sent as `imdb_id` with season/episode returns the same 41 S01E01 results as `parent_imdb_id` (live API from the sandbox, Game of Thrones, 2026-11-17). Docs: https://opensubtitles.stoplight.io/docs/opensubtitles-api/a172317bd5ccc-search-for-subtitles |
| online/subtitles/SubDL.kt, SubSource.kt, SubtitleHub.kt, SubtitleArchive.kt | 82, 112, 129, 118 | yes | Key headers scoped to the site on redirects; ZIP capped 12 MB, entry 4 MB, total 24 MB, 400 entries; sites searched in parallel on IO, per-site errors shown. Observation: a hostile ZIP with a huge non-subtitle entry is still inflated while skipped (CPU only, memory capped) |
| online/SourceFacts.kt | 76 | yes | Pure text parsing, no network; no finding |
| online/EpisodeStills.kt | 103 | yes | IG-5 |
| notify/FcmPush.kt | 357 | yes | FID registration, topic diff, weekly check-in with back-off, 401/403 clears device creds, data-only messages, links limited to https + known actions, version/expiry gate. No finding |
| notify/CloudAlerts.kt | 152 | yes | IG-6. Stale-token lead dismissed (server re-issues on endpoint proof) |
| notify/AppFeed.kt | 102 | yes | Fetched only from the official feed URL through TrustedSources, 512 KB cap, links limited to https or known actions, first run quiet except items from the last 3 days, mutex around sync. No finding |
| update/UpdateConfig.kt | 38 | yes | The token is only for a private repository; release/preview builds refuse to build with MT_UPDATER_TOKEN set (build.gradle.kts:55, ADR-013). No finding |
| update/GitHubReleaseClient.kt | 275 | yes | APK must be on the official repository, SHA-256 required (digest and SHA256SUMS must agree), redirects pinned to GitHub hosts, 2 MB/64 KB caps, falls back to the public API. No finding |
| youtube/YtAccount.kt | 222 | yes | Cookie sealed with Keystore, sent only to youtube.com hosts (never googlevideo/ytimg), CR/LF stripped, retry with the account scoped by a thread-local to one retry (NewPipe extraction is synchronous), verify() off the main thread (YouTubeAccountScreen.kt:300). No finding |
| youtube/YtCookiePaste.kt | 128 | yes | Pure parser, 200 K char cap, youtube.com cookies only, needs SAPISID+SID. No finding |
| youtube/YtAuthHash.kt | 21 | yes | SHA-1 SAPISIDHASH is YouTube's fixed format. No finding |
| subtitles/OnlineSubtitles.kt | 204 | yes | IG-7 (raised from an observation: the hash results were lost when the title search failed). Search reply now capped at 4 MB. Legacy rest.opensubtitles.org checked live 2026-11-17: HTTP 200, 108 KB for "inception" |
| subtitles/SubtitleSitesProvider.kt | 63 | yes | Search only with a saved key; download through SubtitleHub (already read). No finding |
| cast/CastSubtitleServer.kt | 136 | yes | Private IPv4 only, 128-bit random path, GET/HEAD/OPTIONS for known tracks only, 8 tracks × 2 MB, two workers, 10 s timeout, 8 KB head; stopped when the session ends. No finding |
| online/anime/AnimeIds.kt | 70 | yes | Both ids present → no network; lookups cached per title |

## Verification

| Kind | Run | Result |
|---|---|---|
| emulator, IG-1 fail-before | 44049036117 (`scratch/ig1-fail-before`, api33-lowram) | FAIL as expected: NetworkOnMainThreadException at the first poll, on main |
| emulator, IG-1 after | 44049031398 (5a46d98, both phones) | 4/4 PASS, every request on a worker thread |
| emulator, IG-2…IG-5 after | 44050521996 (14a367f, api33-lowram) | in progress |
| emulator, IG-2…IG-5 fail-before | 44050574003 (`scratch/ig2-fail-before`, api33-lowram) | in progress |
