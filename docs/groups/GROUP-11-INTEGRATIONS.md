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
| IG-1 | I1, I2 | Trakt and Simkl sign-in can never finish in a release build; a debug build freezes on each poll | `PrivacyTrackingBackup.kt` starts sign-in with `scope.launch` (main) and called `Trakt/Simkl.awaitToken` directly; both poll with blocking `execute()` on the caller's thread. Release main thread = death on network → NetworkOnMainThreadException at the first poll (5 s after the code is shown). Confirmed by reading: `deviceCode` was wrapped in `withContext(IO)` at the call site but `awaitToken` was not (lines 194–216); Last.fm's `awaitSession` is wrapped | `awaitToken` runs its whole loop in `withContext(Dispatchers.IO)` (main-safe at the source, so any caller is safe) | `TrackerSignInDeviceTest` (emulator) | fix in; fail-before and after runs pending |
| IG-2 | I1–I3 | When Kitsu (or MAL lookup) failed, Trakt and Simkl got nothing, and the whole update repeated every ~5 s progress tick | `AnimeSync.onProgress` ran the anime trackers unguarded before Trakt/Simkl in one `runCatching`; `Kitsu.get` throws on non-2xx; the failure handler released the single episode key | Own key and own `runCatching` per tracker; a thrower is retried at most 3 times | `AnimeTrackersDeviceTest` b, c (emulator) | fix in; runs pending |
| IG-3 | I3 | Kitsu and MAL stop tracking ~30 days after sign-in while still showing the account | Refresh tokens stored, never used; no expiry stored | `validToken` per tracker (refresh a day ahead / once on 401; refused refresh signs out; MAL rotated refresh token saved) | `AnimeTrackersDeviceTest` a, b (emulator); `KitsuLoginTest` (unit) | fix in; runs pending |

## Code read

| File | Lines | Read | Notes |
|---|---|---|---|
| tracking/Trakt.kt | 178 | yes | IG-1. Refresh serialized (`refreshLock`), single-use refresh token stored at once; 401 retry once |
| tracking/Simkl.kt | 188 | partly (sign-in, refresh) | IG-1 |
| ui/screens/settings/PrivacyTrackingBackup.kt (Tracking card) | 150–260 | yes | IG-1 call sites |
| online/anime/AnimeSync.kt | 110 | yes | IG-2. AniSkip `load` keyed per episode, stale result dropped by key check |
| tracking/Kitsu.kt | 100 | yes | IG-3, IG-2 (throws on non-2xx). Login: password grant, own client pair optional (ADR-013) |
| online/anime/AniList.kt (AniList, MyAnimeList, OAuthGuard) | 146 | yes | IG-3 (MAL). AniList implicit token: 1 year, no refresh; 401 → false. OAuthGuard: 10-min single-use window against login CSRF (fine) |
| online/anime/AnimeIds.kt | 70 | yes | Both ids present → no network; lookups cached per title |

## Verification

| Kind | Run | Result |
|---|---|---|
| emulator, fail-before | pending | |
| emulator, after | pending | |
