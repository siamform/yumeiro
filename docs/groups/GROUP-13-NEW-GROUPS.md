# Group 13 audit: rows outside the original group list (final sweep, 2026-11-17)

Why this group exists: the final-sweep coverage check over `docs/INVENTORY.md` (seventh handover,
7.3) found rows that no group ever audited: M2 (YouTube Music online mode), M9 (Music mix),
N1–N9 (Watch Together, Web sites tab, self-update, home servers, other ecosystems, app lock and
privacy, inbox/reminders, support/guide pages, edition/integrity), and M1/M6/M8 with a code read
only. They are audited here in order of user risk: N6 (a lock that can shut the user out), N3
(self-update installs code), N4 (home servers), N2 (WebView sites), then the rest.

Rules: same as every group (map, line-by-line read, action–reaction table, findings with root
cause, regression test failing before and passing after, labelled verification, competitor check).
Parked on purpose: online Watch Together rooms (`archive/online-rooms`, not touched).

## N6 App lock and privacy

Files read line by line: `security/Privacy.kt`, `security/LockKey.kt`, `ui/screens/extras/AppLockGate.kt`,
`MainActivity.kt` (onStart/onStop, gate placement), `ui/screens/settings/PrivacyTrackingBackup.kt`
(lock switch, delay pills), `widget/ContinueWidget.kt` (hidden while locked or incognito).

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Turn lock on | asks for auth first; on success saved | `setLockEnabled` | OK (cannot lock yourself out by a mis-tap) |
| Turn lock on with no screen lock | refused, message | `canAuthenticate` false → "App lock wasn't turned on" | OK |
| App opened after process death | locked | `load` sets locked = lockEnabled | OK |
| Back on the gate | app goes to background, not past the gate | `BackHandler { moveTaskToBack }` | OK |
| Taps on the gate | swallowed | full-screen clickable | OK |
| Return within the delay | not locked | `onStart` away < lockAfter | OK |
| New fingerprint enrolled (API 30+) | old key dropped, new key made | `KeyPermanentlyInvalidatedException` → delete + init | OK |
| Hooked "success" callback (API 30+) | does not open | cipher must encrypt after auth (`LockKey.verify`) | OK |
| **Screen lock removed after turning the lock on** | user can still get in | prompt fails at once, gate stays forever | **N6-1** |
| PIN screen on Android 7–8 | not treated as leaving the app | `pendingCredential` guard in `onStop` | OK |
| PIN screen on Android 9–10 is an activity; delay "every time" | no re-lock behind our own prompt | the guard covers only Android 7–8 | **N6-2 suspected** (code read; no API 29 emulator profile in CI, see below) |
| Hide in recents | FLAG_SECURE only while the lock is on | `applyWindow` | OK |
| Incognito | no history; widget hidden | `ContinueWidget` checks `incognito`/`lockEnabled` | OK |

### Findings

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N6-1 | With app lock on, removing the phone's PIN/pattern left the app locked for good; only "Clear data" (losing every library, download and setting) got the user back in | `unlock()` always showed the system prompt; with no secure lock screen it fails at once (`onAuthenticationError` or a null cipher), and nothing ever turned the lock off | `Privacy.releaseIfNoScreenLock`: in `onStart` and `unlock`, when `KeyguardManager.isDeviceSecure` is false the lock is turned off (saved), the gate goes and the user is told. Only the keyguard state is used, not a biometric error that can be passing | `AppLockDeviceTest#a` (fail-before branch `scratch/n6-fail-before`); `#b` checks a real PIN still opens and keeps the lock | verified (emulator api33-lowram): fail-before 44062815556 → 44059862262; api28 44071192093 |
| N6-2 | Suspected: on Android 10 with "Lock after: every time", the device-credential screen is a separate activity, so `onStop` records a leave and `onStart` can lock again behind our own prompt | `onStop` skips only `pendingCredential` (the confirm-credential path) | not changed: no reproduction; CI has no API 29 profile (TV 24/34, phones 28/33/36). Recorded as an owner/real-phone check, not fixed blind | — | verified (emulator api29): fail-before 44114305239 (N6-4 guard reverted) → 44112738603; fix = N6-4 guard 3f3fbd8 (eighth cycle) |
| N6-3 | Android 9: lock turned on with a fingerprint, fingerprints later removed but PIN kept → locked for good. Also: on Android 9 a PIN-only phone could never turn the lock on | Android 9's BiometricPrompt has no device-credential option (Cancel button only); with no fingerprint it fails at once (`BIOMETRIC_ERROR_NO_BIOMETRICS`); `isDeviceSecure` is still true, so N6-1 does not apply | on Android 9 only, "no biometric" errors (HW_UNAVAILABLE, NO_BIOMETRICS, HW_NOT_PRESENT) fall back to the system PIN screen (`confirmCredential`, the same path as Android 7–8) | `AppLockDeviceTest#b` on the api28 emulator (PIN, no fingerprint) | verified (emulator api28): fail-before 44069107057 → 44071192093 |
| N6-4 | Android 7–9: after the right PIN on the fallback screen the app stayed locked, with no new prompt (found by `AppLockDeviceTest#b`, api28 run 44069102667: PIN accepted at 11:02:13, app resumed, `Privacy.locked` still true) | The PIN screen is a separate activity; returning runs `onStart`, which re-locked when `backgroundAt` was 0 (cold start) or older than lock_after. The result arriving before that `onStart` was undone | 3f3fbd8: no re-lock while our own credential flow is pending or when the app was not left since the last unlock; a good unlock resets `backgroundAt` (Mihon `isAuthenticating` rule) | `AppLockDeviceTest#b` (fail-before 44069102667, api28) | verified (emulator api28): 44069102667 → 44071192093 |

Competitor check: Mihon `SecureActivityDelegate.setAppLock` turns the lock setting off when
`isAuthenticationSupported()` is false, and keeps an `isAuthenticating` flag so its own prompt is
never counted as leaving the app (source read 2026-11-17). N6-1 follows the first rule; N6-2 would
follow the second once reproduced.

## N3 Self-update

Files read line by line: `update/UpdateManager.kt`, `ApkInstaller.kt`, `GitHubReleaseClient.kt` (also I11),
`UpdatePreferences.kt`, `UpdateReceivers.kt`, `UpdateNotifier.kt`, `AppVisibility.kt`, `UpdateConfig.kt`.

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Auto check at start | silent on failure, popup only for a newer, non-snoozed version | `checkForUpdates(false)` | OK |
| "Remind me later" | hidden 24 h for that version | `snooze` | OK |
| Download | system DownloadManager (survives process death, visible notification) | `startDownload` | OK; **N3-1** folder work on main |
| Link not on GitHub / redirect off GitHub | refused before anything is queued | `TrustedSources.isGitHubDownloadHost`, `signedAssetUrl` | OK (device test checks it) |
| Download progress | polled every 400 ms | `pollDownload` | **N3-1** provider query on main every 400 ms |
| App killed during download | restored at next start | `restoreDownload` | OK; **N3-1** provider query on main during `onCreate` |
| Finished file | SHA-256 required (fail closed), same package, higher version, same signer | `ApkInstaller.verify` (IO) | OK |
| Install | PackageInstaller session, user confirms (USER_ACTION_REQUIRED) | `ApkInstaller.install` (IO) | OK |
| Install cancelled / failed | back to "ready", message | `onInstallCancelled`, `onInstallFailed` | OK |
| Stale APKs | removed at start when no download is active | `cleanupStaleApks` (IO) | OK |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N3-1 | Update download did disk and provider work on the main thread: the updates folder was created, listed and emptied on Download; DownloadManager `enqueue` and the progress `query` (every 400 ms for the whole download) and the restore `query` in `MainActivity.onCreate` were provider calls on main | `UpdateManager.scope` is `Dispatchers.Main.immediate` and these calls were made directly in it | folder work, `enqueue`, every `query` and the fallback file/delete moved to `Dispatchers.IO`; restore runs in a coroutine (the auto check follows it, as before) | `UpdateMainThreadDeviceTest` (StrictMode on main; also checks an off-GitHub link is refused and a stale APK is cleared); fail-before `scratch/n3-fail-before` | verified (emulator api33-lowram): fail-before 44062815556 → 44059862262. The provider calls are not visible to StrictMode: that part is structural (code), not measured |

Competitor check (source tree read 2026-11-17): Mihon `main` has only `data/updater/AppUpdateChecker.kt`;
it checks GitHub and hands the download to the browser, with no in-app downloader to compare. Our design
(system DownloadManager, required SHA-256, signer and version checks) is kept; only the threading changes.

## N4 Home servers and N5 other ecosystems (Stremio, JavaScript sources, Aniyomi/Mihon)

Files read line by line: `sources/servers/HomeServers.kt`, `JellyfinProvider.kt`, `PlexProvider.kt`, `SmbTree.kt`
(+ `SmbProxy`), `WebDavTree.kt`, `DlnaTree.kt`, `TreeProvider.kt`; `sources/stremio/StremioAddons.kt`,
`StremioProvider.kt`; `sources/js/JsSources.kt`, `JsEngine.kt`, `JsBridge.kt` (http part), `JsProviders.kt`;
`sources/tachiyomi/TachiyomiExtensions.kt` (repos, install, trust, reload); `ui/screens/sources/SourcesHubScreen.kt`.

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Add Jellyfin/Emby | sign in; only the token is stored, sealed | `JellyfinClient.login`, `HomeServers.seal` | OK (password never stored) |
| Wrong password | "Wrong username or password" | 401 branch | OK |
| Token revoked on the server | clear message | provider `get` 401 → "Signed out of …" | OK |
| SMB file played | loopback proxy with a per-process token; other apps refused | `SmbProxy` binds 127.0.0.1, checks token | OK |
| Folder search on a big share | bounded | `TreeProvider.search`: 150 folders, depth 4, 80 hits | OK |
| Remove a server / add-on / JS repo / JS source | gone, saved, no disk on main | Sources screen | **fixed (N4-1/N5-1)**: was a main-thread write |
| servers.json, addons.json, js/*.json, tachiyomi/repos.json damaged or half-written | data kept or recoverable | read → `runCatching` → empty, write in place | **N4-1 / N5-1** |
| Phone offline when the app starts, then any Stremio change | offline add-ons kept | start kept only loaded manifests; save wrote that list | **N5-1** |
| Add during start-up (Stremio, JS, Tachiyomi) | old entries kept | built on memory not yet loaded | **N5-1** |
| A JS source loops forever | call times out, app keeps working | callers use `withTimeout`; quickjs-kt 1.0.18 interrupts a running evaluation when its coroutine is cancelled (library source read, `QuickJs.kt` v1.0.18) | OK by code + library source, not tested |
| JS source memory | capped | `memoryLimit` 64 MB, stack 2 MB | OK |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N4-1 | A damaged or half-written `servers/servers.json` became "no servers" and the next save deleted every server; add and remove could overwrite each other; remove ran on main | read `runCatching → emptyList`, `writeText` in place, no lock | `SafeJsonFile` read/write (atomic, `.tmp` recovery, damaged copy kept as `.bad`); all changes under one lock; remove on IO | `SourceStoresSafetyTest` (homeServers cases); fail-before `scratch/n45-fail-before` | verified (CI unit): fail-before 44070875412 → 44062718477 |
| N5-1 | Stremio: every add-on unreachable at start was deleted at the next add/remove; a start-up load overwrote an add made meanwhile. JS: one damaged file emptied both lists; changes before the start-up read dropped old repos/sources. Tachiyomi: add/remove before `reload()` saved only the new list; damaged `repos.json` read as empty | same read/write pattern; Stremio saved the *loaded* list, not the saved one; JS parsed both files in one `runCatching`; changes built on in-memory state | `SafeJsonFile` everywhere; Stremio saves its link list apart from loaded manifests and merges the start-up load; JS reads each file alone and every change waits for the saved lists; Tachiyomi builds on the saved file inside its mutex; removes on IO | `SourceStoresSafetyTest` (6 cases); same fail-before branch | verified (CI unit): fail-before 44070875412 → 44062718477 |

Competitor check (source tree read 2026-11-17): Mihon `main` keeps extension stores in its SQLDelight
database (`data/.../extension/repository/ExtensionStoreRepositoryImpl.kt`, `tachiyomi.data.Database`), so a
write is a transaction and a failed read never turns into "save an empty list". Yumeiro keeps its JSON files
and gets the same guarantee from `SafeJsonFile` (no schema move; nothing to migrate). Stremio's own client was
not checked in source; no claim is made about it.

## N2 Web sites tab

Files read line by line: `ui/screens/sites/WebSitesScreen.kt`, `online/web/SavedSites.kt`, `SiteLogins.kt`,
`SafeWebView.kt`, `HeadlessCapture.kt`, `AdShield.kt`, `AdFilter.kt`, `BrowserCheck.kt` (re-read for ER-20).

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Add a site ("example.com") | https link saved, readable name | `SavedSites.normalize/defaultName` (unit-tested) | OK |
| Add the same site again | renamed, not doubled | key = host + path | OK |
| Saved list storage | atomic | SharedPreferences `apply` (whole value) | OK |
| Page wants files / content:// | refused | `allowFileAccess/ContentAccess = false`, `SafeWebView.harden` | OK |
| Page renderer crashes | app survives | `SafeWebViewClient(onGone)` | OK |
| Headless capture never finds a stream | gives up after 22 s, WebView disposed | `withTimeoutOrNull`, `finally dispose` | OK |
| Ads requested by the page | blocked, never captured as "the stream" | `AdFilter.isAd` in `shouldInterceptRequest` and `onMedia` | OK |
| **Sign out of a site** | its sign-in cookies gone | expired only host-only, non-Secure cookies | **N2-1** |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N2-1 | "Signed out of …'s site" while `Domain=` and `__Secure-`/`__Host-` sign-in cookies stayed in the in-app browser (suspected from the code read; the device test decides) | a cookie is only replaced by one with the same name, domain and path; the expiry line had no `Domain=` and no `Secure` | `SiteLogins.expiryLines`: host-only + `Domain=` lines, `Secure` on https, for https/http and www | unit `SiteAccessTest#signOutExpiresDomainAndSecureCookies`; device `SiteSignOutDeviceTest`; fail-before `scratch/n2-fail-before` | verified (emulator api33-lowram 44062815556 → 44062811776; CI unit 44070875412 → 44062718477) |

Limitation kept on record: cookies set on a sub-path cannot be listed by `CookieManager` and are not reached.

## N7 Inbox, app feed, reminders

Files read line by line: `notify/Inbox.kt`, `AppFeed.kt`, `Reminders.kt`, `ui/screens/extras/InboxScreen.kt`,
`ReminderHost.kt`, `SupportMessage.kt`.

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Notification arrives | one inbox row (same id never twice), kept on disk | `Inbox.add` dedupe, SafeJsonFile | OK |
| Damaged inbox.json | kept as `.bad`, `.tmp` used | `SafeJsonFile.read` (R16) | OK |
| Tap a row / mark all read / leave the page / remove / clear | saved, no disk on main | `edit → save` on the caller's thread | **N7-1** |
| Reminders cadence | at most one per hour, one per day first open, 30 min grace | `Reminders.next` (unit `RemindersTest`) | OK |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N7-1 | Every inbox tap wrote and fsynced `inbox.json` on the main thread (also on leaving the page); two saves from different threads could write the same `.tmp` at once | `save()` ran on the caller's thread, outside any lock | writes under one lock, always the newest list; main-thread saves on one background writer; worker saves stay synchronous (on disk before the worker ends) | `InboxMainThreadDeviceTest`; fail-before `scratch/n7-fail-before` | verified (emulator api33-lowram): fail-before 44062815556 → 44062811776 |

## N8 Support and user guide pages

Files read: `ui/screens/extras/SupportScreen.kt`, `AppPages.kt` (`Links`), `settings/UserGuide.kt` (static text).

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N8-1 | With no browser (common on Android TV), Support links, reminder cards, inbox links and the ntfy install buttons did nothing on tap | `Links.open` returned false and most callers ignored it | `Links.open` tells the user; `tell = false` for the notification tap (falls back to the inbox) | unit `LinksOpenTest` (Robolectric `checkActivities`); fail-before `scratch/unit-fail-before` | verified (CI unit): fail-before 44070875412 → 44062718477 |

Payment details on the Support page are the owner's content and were not changed.

## N9 Edition and integrity

Files read: `Edition.kt`, `security/IntegrityGuard.kt`, `app/build.gradle.kts` (token/integrity fields).
No finding: the signer digest check is computed once and cached; debug builds are never flagged; a build with
`MT_UPDATER_TOKEN` set fails (ADR-013), so no GitHub token ships.

## N1 Watch Together (local parts; online rooms stay parked)

Files read line by line: `watch/WatchTogether.kt`, `ui/screens/watch/WatchTogetherScreen.kt`, the bind site in
`ui/screens/video/VideoPlayerModal.kt` (DisposableEffect(lease)).

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Host a room | 4-digit code, address, NSD advert | `host()`: NanoHTTPD(0), SecureRandom code, `registerService` in runCatching | OK |
| Guest with the right code | follows title, episode, play/pause, seek (±2.5 s) | 1 s poll, clock offset from the request midpoint | OK |
| **Guest with a wrong code** | told, back to the start page | `leave()` reset the status → card and message gone, nothing said | **N1-1** |
| Room closes | "The room closed" after 30 failed polls | `failures > 30` | OK |
| **Host while a video is already up (floating window)** | guests follow pause/seek | listener and 3 s tick only attached in `bind()`, which ran before the room existed | **N1-2** |
| Next video reuses the player (R15) | one listener per player | `bind()` removes the old one | OK |
| Close the room | server stopped, NSD unregistered, tick stops | `leave()`; tick checks the role | OK |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| N1-1 | Wrong room code: the guest card vanished with no message | `leave()` resets `Status`, which held the message | leave, then `AppMessages.error` | unit `WatchTogetherHostTest#wrongRoomCodeTellsTheGuest` | verified (CI unit): fail-before 44070875412 → 44062718477 |
| N1-2 | Hosting while a video plays in the floating window: guests never saw pause/seek/position | `attach` lived only in `bind()` | `attachIfHosting()` from `bind()` and `host()`, once per player; `leave()` detaches | unit `WatchTogetherHostTest#hostingWithAPlayerAlreadyBoundKeepsPublishing` | verified (CI unit): fail-before 44070875412 → 44062718477 |

Two phones on one Wi-Fi (NSD discovery, real follow) needs real hardware: OMT entry, not blocking.

## N4-2 Bridge ids and CloudStream fixUrl (found by device run 44059862262)

`UniversalAddEcosystemsDeviceTest` failed on head: "Stremio stream not offered: []"; logcat showed the search result url
`http://localhost:50525/stremio/movie/yu1` and a title page named `/localhost:50525/stremio/movie/yu1`.

Root cause (library source, cloudstream v4.8.0 `MainAPI.kt`): `newMovieSearchResponse`, `newAnimeSearchResponse` and
`newEpisode` default to `fix = true`; `fixUrl()` turns any id that does not start with "http" into `"$mainUrl/$id"`.
The built-in drama source had met the same bug (Round 2) and uses `fix = false` + `stripId`; the bridges did not.

| Bridge | Id shape | Broken before | Fix |
|---|---|---|---|
| Stremio | `<type>/<id>` | every title from search/home (load split a URL) | `BridgeIds.unfix` in load/loadLinks |
| Plex | `plex:<key>`, episodes `rk:<key>` | series episodes; any server named "plex" | unfix in load/loadLinks |
| Jellyfin | `jf:<id>`, episodes `item:<id>` | series episodes | unfix in loadLinks |
| File servers (SMB, WebDAV, DLNA) | `d:`/`f:` + path; episode = path | every folder/file from home/search; SMB/DLNA episodes | unfix in load; loadLinks unfix except WebDAV (paths are absolute URLs) |
| Aniyomi | source-relative url | requests are `baseUrl + url` (aniyomi `AnimeHttpSource`) → baseUrl twice | `fix = false` |
| Mangayomi JS | the script's own link | script got a url it never gave | `fix = false` |

Data safety: Stremio/Plex/Jellyfin/Tree ids keep the shape already saved (history, progress, favourites keyed by them);
readers accept both. For Aniyomi/JS the new ids are relative: `OnlineHistory.progressOf/entry` find spots saved under the
old absolute shape through the host-free index (path-like ids only; plain ids never borrow).
Tests: unit `BridgeIdsTest` (real fixUrl), `ResumePointsTest#bridgeIdWithoutTheOldSitePrefixKeepsThePosition`;
device `UniversalAddEcosystemsDeviceTest` (fail-before = 44059862262). Plex/Jellyfin/SMB/DLNA end to end needs a real
server: covered by unit round trip; owner check optional.

## Test fault MINI-T1 (MiniPlayerDeviceTest)

44059862262 `e_swipe_right` stayed on track 3: the swipe started at 3 % of the bar, in its start padding (outside the
draggable row) and in the system back edge (logcat: a back reached the app at 10:04:07.503). Swipes moved inside the
row (c09cdc8). App code unchanged.

Second fault (44062811776, `d_swipe_left` stayed on track 2): the c09cdc8 swipe used fractions of the whole bar, but the
Previous/Play/Next/Close buttons take the right ~186 dp, so 60 % sat on the Previous button. The draggable row now has
the tag `mini_player_swipe_area` and the test swipes 0.90 → 0.10 of it (aa3489e). App behaviour unchanged.

## M9 Music Mix

Files read: `ui/screens/music/MusicMix.kt` (device part, online picks, search), `R7LogicTest` (matching rules).

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Open Mix offline | device songs only, online part says it needs internet | `musicSourceOptions`, online block skipped | OK |
| Online picks | Home quick picks + songs by the user's top artists, failures silent | `runCatching` per call | OK |
| **Type in Mix search** | smooth typing on a 2 GB phone with thousands of songs | `remember(query, tracks) { mixDeviceMatches(...) }` in composition: NFD + regex over title/artist/album of every song on main, per keystroke | **M9-1** |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| M9-1 | Mix search dropped frames while typing with a large library | device match ran on the main thread in composition | `LaunchedEffect(query, tracks)` + `Dispatchers.Default`; newer keystroke cancels; blank query does nothing | unit `MusicMixSearchTest` (Robolectric Compose, probe: no call on main; "Sóng 157" found accent-folded) | verified (CI unit): fail-before 44070875412 → 44067269319 |

## M2 Online music (YouTube Music) and the automatic online/offline switch

Files read: `youtube/music/YtMusicApi.kt`, `YmEndless.kt`, `youtube/YouTubeAudioDataSource.kt`, `youtube/YtAccount.kt`,
`ui/screens/youtube/YtMusicBrowse.kt` (Home, endless, search, store), `MusicMode` + the follow loop in `MTPlayerApp.kt`.

| Action | Expected reaction | Code | Result |
|---|---|---|---|
| Open Music with / without internet | Online / On device | `MusicMode.defaultFor` | OK (`Round13LogicTest`) |
| Connection drops while Online | switches to On device; a picked mode sticks otherwise | `MusicMode.follow` | OK |
| Play an online song, link expired | re-extract once on 401/403/404/410 | `YouTubeAudioDataSource.open` | OK |
| Home runs out of pages | "Similar to …" shelves, max 80, stops after 6 misses | `YmEndless`, `endlessOpen` | OK |
| A Home page fails | kept, retried with backoff | `moreHome` onFailure | OK |
| **Sign out of YouTube (mode Always) / sign in / change mode** | Home becomes the anonymous / new account's Home | YtMusicApi cache (10 min) and visitor id never cleared (`invalidate()` had no caller); `YmStore.home` kept the page for the tab | **M2-1** |

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| M2-1 | After sign-out the previous account's own YouTube Music Home (and opened account pages) stayed for up to 10 min, sent with that account's visitor id | account changes cleared only `YouTube.infoCache` | `YtMusicApi.invalidate()` also clears the visitor id and runs on save/signOut/setMode; `YmStore.homeAccount` + `ymHomeStale` reload Home and clear the page stack on an account change | unit `YtAccountMusicCacheTest` (3 cases) | verified (CI unit): fail-before 44070875412 → 44067269319 |

Real account check (personal Home before/after sign-out) needs a person's Google account: optional owner check, not blocking.

## M1 On-device library, M6 Equalizer, M8 Song downloads

M1 files read: `data/repository/MediaRepository.kt` (scan, grouping), `MainViewModel` scan callers, the startup
permission block in `MTPlayerApp.kt`.

| ID | Problem | Root cause | Fix | Test | Status |
|---|---|---|---|---|---|
| M1-1 | Every cold start with media access ran two (three on Android 13+ while the notification prompt answers) full MediaStore scans at once | `MainViewModel.init` → `scanMedia()` and the startup check → `onPermissionResult(true)` → `scanMedia()`; each scan on its own IO coroutine | requests with the same access state join the running scan; a changed state (first grant) runs its own scan after it (Mutex) | unit `LibraryScanOnceTest` (2 cases) | verified (CI unit): fail-before 44070875412 → 44067269319 |

M6 (`player/equalizer/EqualizerManager.kt`): every Equalizer/BassBoost/Virtualizer call on the single `yu-audio-fx`
thread (ST-7), prefs warmed in `PrefsWarmup`, re-attach only on a new session, the process-wide player keeps its
session. No finding. Observation: presets hold 5 gains; a device reporting more than 5 bands gets 0 on the rest (sound
quality, not stability; no change without a decision).

M8 (`online/download/SongDownloads.kt`, `YouTubeDownloadSheet`, `OnlineDownloads.enqueueYouTube`): one request at a
time, device songs return null, the sheet's state is keyed by the song id, the list load is `@Synchronized`, saves
from main go to the downloads thread (G8 D8). No finding.

**N6-2 update 2026-11-17 (eighth cycle):** reproduced on an API 29 emulator. With only the N6-4 guard reverted
`AppLockDeviceTest` b fails (gate stays after the PIN, 44114305239); head passes 2/2 (44112738603). Verified (emulator).

## Group 13 close-out (2026-11-18, eighth cycle)

Every finding row is verified (emulator or CI unit, labelled per row); N6-2 moved from open (suspected) to verified
on API 29 (44114305239 → 44112738603). No row is open. Online Watch Together rooms stay parked
(`archive/online-rooms`). Group 13 is **closed for agent work**; owner-only checks stay in docs/OWNER-MANUAL-TESTS.md.

