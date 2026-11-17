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

Competitor check: Mihon's `AppUpdateDownloadJob` downloads in a WorkManager job off the main thread;
Seal/NewPipe use their own downloader threads. None run storage or provider work on the UI thread.
