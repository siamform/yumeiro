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
| N6-1 | With app lock on, removing the phone's PIN/pattern left the app locked for good; only "Clear data" (losing every library, download and setting) got the user back in | `unlock()` always showed the system prompt; with no secure lock screen it fails at once (`onAuthenticationError` or a null cipher), and nothing ever turned the lock off | `Privacy.releaseIfNoScreenLock`: in `onStart` and `unlock`, when `KeyguardManager.isDeviceSecure` is false the lock is turned off (saved), the gate goes and the user is told. Only the keyguard state is used, not a biometric error that can be passing | `AppLockDeviceTest#a` (fail-before branch `scratch/n6-fail-before`); `#b` checks a real PIN still opens and keeps the lock | device run pending |
| N6-2 | Suspected: on Android 10 with "Lock after: every time", the device-credential screen is a separate activity, so `onStop` records a leave and `onStart` can lock again behind our own prompt | `onStop` skips only `pendingCredential` (the confirm-credential path) | not changed: no reproduction; CI has no API 29 profile (TV 24/34, phones 28/33/36). Recorded as an owner/real-phone check, not fixed blind | — | open (suspected) |
| N6-3 | Android 9: lock turned on with a fingerprint, fingerprints later removed but PIN kept → locked for good. Also: on Android 9 a PIN-only phone could never turn the lock on | Android 9's BiometricPrompt has no device-credential option (Cancel button only); with no fingerprint it fails at once (`BIOMETRIC_ERROR_NO_BIOMETRICS`); `isDeviceSecure` is still true, so N6-1 does not apply | on Android 9 only, "no biometric" errors (HW_UNAVAILABLE, NO_BIOMETRICS, HW_NOT_PRESENT) fall back to the system PIN screen (`confirmCredential`, the same path as Android 7–8) | `AppLockDeviceTest#b` on the api28 emulator (PIN, no fingerprint) | device run pending (fail-before `scratch/n6-fail-before`) |

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
| N3-1 | Update download did disk and provider work on the main thread: the updates folder was created, listed and emptied on Download; DownloadManager `enqueue` and the progress `query` (every 400 ms for the whole download) and the restore `query` in `MainActivity.onCreate` were provider calls on main | `UpdateManager.scope` is `Dispatchers.Main.immediate` and these calls were made directly in it | folder work, `enqueue`, every `query` and the fallback file/delete moved to `Dispatchers.IO`; restore runs in a coroutine (the auto check follows it, as before) | `UpdateMainThreadDeviceTest` (StrictMode on main; also checks an off-GitHub link is refused and a stale APK is cleared); fail-before `scratch/n3-fail-before` | device run pending. The provider calls are not visible to StrictMode: that part is structural (code), not measured |

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
| N4-1 | A damaged or half-written `servers/servers.json` became "no servers" and the next save deleted every server; add and remove could overwrite each other; remove ran on main | read `runCatching → emptyList`, `writeText` in place, no lock | `SafeJsonFile` read/write (atomic, `.tmp` recovery, damaged copy kept as `.bad`); all changes under one lock; remove on IO | `SourceStoresSafetyTest` (homeServers cases); fail-before `scratch/n45-fail-before` | fixed 595305f, unit pending (CI) |
| N5-1 | Stremio: every add-on unreachable at start was deleted at the next add/remove; a start-up load overwrote an add made meanwhile. JS: one damaged file emptied both lists; changes before the start-up read dropped old repos/sources. Tachiyomi: add/remove before `reload()` saved only the new list; damaged `repos.json` read as empty | same read/write pattern; Stremio saved the *loaded* list, not the saved one; JS parsed both files in one `runCatching`; changes built on in-memory state | `SafeJsonFile` everywhere; Stremio saves its link list apart from loaded manifests and merges the start-up load; JS reads each file alone and every change waits for the saved lists; Tachiyomi builds on the saved file inside its mutex; removes on IO | `SourceStoresSafetyTest` (6 cases); same fail-before branch | fixed 595305f, unit pending (CI) |

Competitor check (source tree read 2026-11-17): Mihon `main` keeps extension stores in its SQLDelight
database (`data/.../extension/repository/ExtensionStoreRepositoryImpl.kt`, `tachiyomi.data.Database`), so a
write is a transaction and a failed read never turns into "save an empty list". Yumeiro keeps its JSON files
and gets the same guarantee from `SafeJsonFile` (no schema move; nothing to migrate). Stremio's own client was
not checked in source; no claim is made about it.
