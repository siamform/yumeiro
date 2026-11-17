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

## Code read

| File | Lines | Read | Notes |
|---|---|---|---|
| tracking/Trakt.kt | 178 | yes | IG-1. Refresh serialized (`refreshLock`), single-use refresh token stored at once; 401 retry once |
| tracking/Simkl.kt | 188 | partly (sign-in, refresh) | IG-1 |
| ui/screens/settings/PrivacyTrackingBackup.kt (Tracking card) | 150–260 | yes | IG-1 call sites |

## Verification

| Kind | Run | Result |
|---|---|---|
| emulator, fail-before | pending | |
| emulator, after | pending | |
