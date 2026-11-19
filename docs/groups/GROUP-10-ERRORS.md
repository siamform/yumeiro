# Group 10 audit: dialogs, errors, permissions (R17)

Scope (master handover, Group 10): error taxonomy and shared mapper; every message accurate with a
recovery action; permission flows (denied, permanently denied, revoked); no/slow network, VPN, low
storage; dialog/toast consistency, Back, accessibility. List every message from code, trigger each
on device.

Labels: unit = JVM/Robolectric, emulator = CI device-check. Status words as in `docs/DONE-LEDGER.md`.

## Shared pieces

- `online/OnlineRuntime.kt` `OnlineErrors.friendly(e)`: the shared mapper from a Throwable to a
  sentence (walks the cause chain: offline vs blocked DNS, refused/reset, timeout, TLS, HTTP codes,
  bad JSON, extension not implemented, broken links). `PlaylistMarket.friendlyError` keeps two
  market cases and delegates the rest.
- `ui/components/ErrorHelp.kt` `suggestionsFor(text)` / `forExtension(text)`: recovery tips under
  error cards (`StateViews.ErrorState`), the video player's error dialog, Now Playing, the watch
  page and the online player.
- `ui/components/StateViews.kt` `AppMessages`: the one in-app message pill (info / success / error
  / warning, optional action, live region for TalkBack, falls back to a system Toast with no host).
- Separate player-side mappers: `player/StreamRecovery.kt` (retry/fail decisions for streams) and
  `stream/StreamResolver.kt` (`Resolution.Failed` titles for playlist links). Not merged into the
  shared mapper: they decide recovery, not only wording; their texts are checked below.

## Message inventory (from code, 2026-11-16)

`AppMessages.error` 61, `.success` 61, `.show` 33, `.info` 3, direct `Toast.makeText` 3 (the
AppMessages fallback, and two outside the Compose host). Generated list: `/tmp/msgs.tsv` method,
rows copied into the table below as they are checked.

## Problems found and fixed

| # | Problem | Root cause | Fix | Test |
|---|---|---|---|---|
| ER-10 | The tips under an error did not match the shared mapper's own texts: "Your network refused the connection…" got the HTTP 403 tips (refresh the link, add a Referer), "Secure connection failed" got the offline tips, "The server took too long to answer" and "The server sent unreadable data" only the generic ones, and an update "certificate mismatch" got the TLS date/time tip. HTTP 401/403/410/429/451 were all shown as "Server error (HTTP …)" | `ErrorHelp` matches keywords in order and was written for raw texts, not for the mapper's output; the mapper labelled every non-404 HTTP code a server error | Mapper names 4xx answers; ErrorHelp gets branches for the mapper's phrases and keeps signature errors out of the TLS branch | `r17/ErrorTaxonomyTest` (4; 13 causes: text and tips) |
| ER-11 | Every "Don't allow" in a permission dialog (notifications, music & videos) sent the user straight to Android settings, even the first time, when Android would still ask again. With notifications granted but turned off for the app, the Notifications row did nothing | The result callbacks treated every denial as permanent and had no branch for "granted but off" | `PermissionCenter.denial` reads `shouldShowRequestPermissionRationale`: once → "not allowed. Tap again when you want to allow it" (stay in app); blocked → error "…blocked for Yumeiro. Android won't ask again; allow it in Android settings" with "Open settings". `afterNotificationRequest` opens the notification page when granted but off. Limit: closing the dialog without a choice also reads as blocked | `PermissionFlowDeviceTest` |
| ER-12 | Inbox refresh failure: "Couldn't check for news. Try again later" for every cause. Repository / add-on URL and home-server failures had no Retry (the dialog with the typed URL had closed) | Messages written before the shared mapper; Retry added only to the extension market | Reason from `OnlineErrors.friendly`; "Retry" repeats the call (`InboxScreen` sync, `SourcesHubScreen` addRepoUrl ×2, addUrl, save) | compile (CI) |
| ER-13 | Player "Unsupported video format" had no next step | Written before the decoder choice existed | Tip to set Video decoder → Software (Settings › Audio quality & sound › Quality and audio); "Try another quality or source" when Software is already on | `PlaybackRecoveryTest` |
| ER-14 | A full disk outside downloads showed the raw "write failed: ENOSPC (No space left on device)" | Only the download worker knew ENOSPC (DiskFull) | Shared mapper branch on the cause chain: "Not enough storage. Free up some space, then try again" | `ErrorTaxonomyTest` |
| ER-15 | Pin limit and "Channel not known yet" were raw system Toasts (other look, no kind); pin limit had no next step | Written before AppMessages | `AppMessages` WARNING "…Unpin one first" / INFO; only the host fallback still uses Toast | code scan |

ID note: these rows were first written as ER-1/ER-2, which clash with the older G10 A1 ledger rows; renamed ER-10/ER-11.

## Verification

| Item | Test | Label | Status |
|---|---|---|---|
| ER-10 mapper ↔ tips | `ErrorTaxonomyTest` | unit | 4/4 CI 43941919340 |
| ER-13 decoder tip | `PlaybackRecoveryTest` | unit | 6/6 CI 43941919340 |
| StreamRecovery / StreamResolver texts | code review: cause + next step in each (DNS, timeout, TLS, 401/403/404/429/451/5xx, DRM, parsing, empty/too-large playlist, web page, TorrServer) | review | done (no change) |
| ER-12 Retry on failed adds / Inbox | `RetryActionsDeviceTest` a–d (local server, real tap on Retry) | emulator | PASS both phones 43987451529 (JS repo, Stremio, Mihon repo, home server); Inbox: same call, fixed feed URL → code review |
| ER-19 Reason hidden behind an open form (home server, Kitsu) | `RetryActionsDeviceTest` d, e | emulator | fail-before 43985787392; PASS both phones 43987451529 (reason inside the form, Retry) |
| Every message shown on device | `scripts/messages-inventory.py` → docs/evidence/round3/messages-inventory.md (162 call sites, 100 literal); `MessagesDeviceTest` shows each literal text through the real host: shown, whole (no ellipsis at 3 lines), action button, ATF. Real-flow triggers: NetworkState, PermissionFlow, LowStorage, Dialogs, BackdropImport tests | emulator | PASS 100/100 both phones, 43904330191 (api33 failed 1 in 43891331959 → ER-18 fixed) |
| Dialogs: Back closes without acting, page kept, ATF clean (Privacy "Clear …" confirms, TsukiDialog) | `DialogsDeviceTest` | emulator | PASS checked=5 both phones, 43891331959 |
| Dialog Back by construction | code scan: every `*Dialog/*Sheet/*Prompt/*Host` composable uses Dialog/AlertDialog/ModalBottomSheet/BackHandler or delegates (ReminderCard → Dialog); the 6 without are non-modal overlays (gestures, info overlay, error overlay under the player's BackHandler, lights, message host) | review | done |
| Left as is after review: per-row Install failures (the Install button stays on the row, so tapping it again is the retry); "Couldn't start casting" (CastSupport.play returns false without a reason; the cast session is already connected when it runs) — noted, not changed | code review | — | noted |
| ER-11 permission denied once / blocked / Open settings | `PermissionFlowDeviceTest` | emulator | PASS both phones, 43904330191 |
| Permission revoked while running | Code: every check is live (`AppNotifier.systemAllowed` = checkSelfPermission + areNotificationsEnabled; Settings cards re-read on `LifecycleResumeEffect`; UpdateNotifier catches SecurityException mid-post). Device: script step `PERM_RUNNING` (in the PermissionFlowDeviceTest branch) launches, revokes POST_NOTIFICATIONS / READ_MEDIA_VIDEO, checks the process was killed, relaunches and counts crash-buffer lines | emulator | PASS both phones, 43904330191: POST_NOTIFICATIONS and READ_MEDIA_VIDEO revoked → process killed, relaunch clean, crashLines=0 |
| No network (Wi-Fi + data off → signal, offline pill, "No internet connection", back on) | `NetworkStateDeviceTest.a` | emulator | PASS both phones, 43904330191 (api36 failed in 43891331959 → ER-17 fixed) |
| Slow server (25 s > 20 s read timeout → "took too long"; 32 KB/s steady body completes) | `NetworkStateDeviceTest.b` | emulator | PASS both phones, 43891331959 and 43904330191 |
| Refused port → "refused the connection" | `NetworkStateDeviceTest.c` | emulator | PASS both phones, 43891331959 and 43904330191 |
| VPN | `NetworkStateDeviceTest.d` logs `vpnActive`; an emulator cannot raise a VPN without a VPN app and the consent dialog → owner test after the run | emulator + owner | emulator logs vpnActive=false (no VPN on CI); real VPN → OMT-18 |
| Low storage | ER-14 mapper (unit); downloads DiskFull (G8); device: `LowStorageDeviceTest` a) size > free → up-front "Storage full — needs about … MB free", no retry; b) chunked body, disk filled by `fallocate` to ~16 MB → ENOSPC shown as "Storage full", app alive | unit + emulator | PASS a, b both phones, 43904330191 (b skipped in 43891331959, test fixed 523af6f) |

## Close-out (2026-11-17)

Every row above has device (emulator) or CI evidence except VPN, which needs a real VPN app and its
consent dialog: owner test OMT-18. New findings in the close-out: ER-19 (fixed, verified) and ST-21
(message over the floating video window, fixed, verified). Group 10 is closed for agent work.


## Re-audit under the group method (§13a), 2026-11-19

The 2026-11-17 close-out above predates the 10-step group method; the handover asks for G10 to be
redone in full. This section is that pass. Earlier rows (ER-10 … ER-20) stay as they are.

### Step 1: files read line by line

`online/OnlineRuntime.kt` 1–130 (`OnlineErrors.friendly`), `ui/components/ErrorHelp.kt` 1–179,
`ui/components/StateViews.kt` 1–383 (`AppMessages`, `AppMessageHost`), `ui/screens/extras/PermissionCenter.kt`
1–171, `network/Connectivity.kt` 1–72, `ui/components/TsukiDialog.kt` 1–259, `ui/components/OfflineNotice.kt`
1–109, `ui/screens/online/ExtensionReviewHost.kt` 1–130, `online/ExtensionReview.kt` 1–70,
`update/ui/UpdateDialog.kt` (all), `update/ui/UpdateHost.kt` (all), `update/UpdateManager.kt` 128–145 and
340–430, `update/UpdateReceivers.kt` 25–60, `ui/screens/online/BrowserCheckHost.kt` (all),
`player/StreamRecovery.kt` (all), `player/RecoveryRouter.kt` (all), `ui/screens/video/VideoPlayerModal.kt`
440–470 and 730–830 (router and recovery call sites), `stream/StreamResolver.kt` 240–287 (`failureFor`),
`stream/StreamProbe.kt` 19–113, `ui/components/YumeKit.kt` 270–283 (`MoonSheetHandle`), every
`ModalBottomSheet(` call (10: YumixFilter, OnlinePlayerUi, DownloadSheets ×2, MangaScreens, LiveTvChrome,
HomeScreen, SleepTimerSheet, YuSearch, MediaComponents), `ui/tv/TvMode.kt`, `res/xml/network_security_config.xml`.
Library code read for behaviour: material3 `BottomSheet.kt` 359–399 and `SheetDefaults.kt` 603–640
(androidx-main, fetched 2026-11-19): a non-null `dragHandle` is wrapped in `clickable` + a "Drag handle"
tooltip; on an expanded sheet the click calls `animateToDismiss()`.

### Step 2: audit (side effects, action–reaction, competitors)

| ID | Action → reaction today (unhappy path) | Competitor / reference (exact tag, file) | Fix chosen (pros / cons) |
|---|---|---|---|
| ER-21 | Network blocked for the app (MIUI/ColorOS per-app data switch, NetGuard, no INTERNET in a work profile) → `socket failed: EPERM`, `connect failed: EACCES`, `getaddrinfo EACCES` → the storage rule matches "EPERM"/"Permission denied" → "The app couldn't use its storage on this device. Restart the app" (wrong cause, useless step) | Chromium 130.0.6723.58 `net/base/net_errors_posix.cc` lines 31, 104: EACCES and EPERM → ERR_ACCESS_DENIED (network) | A branch for network exceptions with EACCES/EPERM before the storage rule: "Android or a firewall app is blocking Yumeiro's internet…", tip to allow Wi-Fi and mobile data in Settings › Apps. Pro: right cause; con: none (storage rule unchanged for file errors) |
| ER-22 | Message with Retry/Open/Undo → closes after 4.5 s whatever the user's "Time to take action" setting | material3 androidx-main `SnackbarHost.kt` line 311; material-components-android 1.12.0 `Snackbar.java` line 375 | Ask `LocalAccessibilityManager` for the recommended timeout (icon, text, controls). Pro: platform setting honoured; con: none (default unchanged) |
| ER-23 | Update available on a 1080p TV (540 dp) or phone landscape (~400 dp) → card ~620 dp, no scroll → Download / Remind me later clipped; on TV the update can't be started | material3 androidx-main `AlertDialog.kt` line 358 (`weight(1f, fill = false)` for the text slot); Mihon v0.19.1 `NewUpdateScreen.kt` + `InfoScreen.kt` (buttons in the bottom bar, notes scroll) | Notes take the remaining height (`weight(1f, fill = false)`), header/meta scroll with them on very short screens; buttons always placed. Pro: no layout change on tall phones; con: fewer note lines visible on TV |
| ER-24 | "Installing…" and the installer never reports (Home on the system confirm, installer killed) → Back and outside do nothing → the modal covers the app until restart | Android `PackageInstaller.Session.commit(IntentSender)`: status arrives asynchronously, possibly never if the confirm is abandoned; Yumeiro's material3 `Dialog` already lets Back leave every other state | Back hides the dialog (state kept; success/failure still arrive through `UpdateReceivers` and re-open it). Pro: never trapped; con: none |
| ER-25 | Audio output fails (Bluetooth switch, `AUDIO_TRACK_INIT_FAILED`), player timeout, runtime check, unspecified, or a stream's file-not-found / no-permission / cleartext / 416 → "Unsupported video format … hardware decoders … set Video decoder to Software" | NewPipe v0.28.0 `Player.java` 1450–1480 (these I/O codes = stream failure, TIMEOUT/UNSPECIFIED = reload); media3 1.8.0 demo `PlayerActivity.java` 529–559 (decoder text only for DecoderInitializationException) | Decoder text only for 4xxx; 5xxx retry once then an audio message; the four I/O codes and 1xxx get their own wording with Retry. Pro: right next step; con: more strings |
| ER-26 | Play Link / Live TV channel / playlist refresh while offline → probe UnknownHost → "Can't find the server … turn on an alternative DNS" + DNS button | Yumeiro's own `OnlineErrors.friendly` (offline → "No internet connection"); NewPipe v0.28.0 `ErrorInfo.kt` line 96 (network-related first) | Offline check first in `failureFor` for DNS / timeout / connection. Pro: consistent with the shared mapper; con: none |
| ER-27 | Danger extension → "Install anyway" twice → a double tap or two quick OK presses (key repeat) installs with the warning line never seen | AOSP android-14.0.0_r1 `PackageInstallerActivity.java` lines 158–162, 445–450 (OK enabled only once the confirm text is shown, disabled on pause, touches filtered when obscured) | Second tap counts only 1 s after arming; label reads "Tap again to install" when armed. Pro: deliberate; con: 1 s wait |
| ER-28 | TV: open any sheet → first remote key focuses the drag handle, "Drag handle" tooltip, OK closes the sheet without acting (NB15-OB2) | material3 androidx-main `BottomSheet.kt` 359–399 (clickable handle → dismiss); androidx tv-material (no bottom sheet at all for TV) | `sheetDragHandle()` = null on TV, moon handle elsewhere, used by all 10 sheets. Pro: first key lands in content; con: no visual handle on TV (nothing to drag there) |
| ER-29 | (found in step 9) An extension's promotional window is held → "<extension> has a message · Show" card posted with `AppMessages.show` → replaces the message on screen, an error with Retry included, though the user did nothing (MessagesDeviceTest failed on both phones, 44452173594, 44454531660, right after "card shown") | material3 androidx-main `SnackbarHost.kt` (`showSnackbar` holds a mutex: messages queue, never replace) | `AppMessages.showQuiet`: an app-posted message waits behind the one on screen and shows when it is dismissed; user-caused messages still replace. Pro: no lost error; con: the card can come a few seconds later |
| ER-30 | (owner report NBUG-8, missed in steps 1–3, added 2026-11-19 at step 8) Open one extension's page or title → the extension blocks its thread (blocking Jsoup/OkHttp, `runBlocking`, `Thread.sleep`) → `withTimeout` (25 s home, 35 s details, 75 s links) never fires because the code never suspends → spinner for as long as the extension blocks, maybe forever; leaving the page waits too, and the shared parallel permit stays held | kotlinx.coroutines `Timeout.kt` KDoc (timeouts are cooperative); CloudStream master 2dcc25c `ui/APIRepository.kt` lines 29, 84–155 (plain `withTimeout`, same gap) | One helper `ExtensionCall.bounded`: the extension runs as a detached job; the caller only awaits it under the timeout, so a timeout or Back returns at once and the job is cancelled. Used by home, search, details and links. Pro: no stuck spinner, permit released; con: a blocked thread lives on until the extension returns (Dispatchers.IO has 64) |

Lower findings (kept, not changed this pass, reason): Connectivity ignores captive portals (no
NET_CAPABILITY_VALIDATED; a portal shows as online and every request fails as "can't be reached" — behaviour
change for all users, proposal for stability-v2); the Toast fallback drops a message's action (only before the
host attaches, e.g. start-up); BrowserCheckHost Back cancels the whole check instead of going back in the
WebView (by design: the check page has no history worth keeping); 4xx other than 401/403/404/410/429/451 are
labelled "Server error" (rare codes; next pass).

### Step 3: findings register

| ID | Finding | Severity | Layer | Test (fails on old code) | Status |
|---|---|---|---|---|---|
| ER-21 | Blocked network shown as a storage problem | Medium | mapper | `g10/NetworkBlockedErrorTest` (unit) | Fixed 829073bb |
| ER-22 | Message pill ignores the accessibility timeout | Medium (a11y) | UI host | `g10/AppMessageTimeoutTest` (unit, Robolectric UI) | Fixed 4268f89d |
| ER-23 | Update dialog buttons off-screen on TV and phone landscape | High (TV) | dialog | `g10/UpdateDialogFitTest` tv1080p, phoneLandscape (unit, Robolectric UI) | Fixed 5af20d35 |
| ER-24 | "Installing…" dialog can't be left | Medium | dialog | `g10/UpdateDialogFitTest` installingCanBeLeftWithBack | Fixed 05263e28 |
| ER-25 | Player calls non-decoder errors a decoder failure | Medium | player | `g10/PlaybackErrorWordingTest` (unit) | Fixed 627501bb |
| ER-26 | Stream link offline gets DNS advice | Medium | stream | `g10/StreamOfflineTest` (unit) | Fixed 0804bbf2 |
| ER-27 | Double tap installs a Danger extension | Low (security) | dialog | `g10/ExtensionReviewConfirmTest` (unit, Robolectric UI) | Fixed 7ee7bdab |
| ER-28 | TV sheets: handle takes the first key, OK dismisses (NB15-OB2) | Medium (TV) | sheets | `g10/SheetHandleScanTest` (unit), `SheetFocusTvDeviceTest` (emulator, TV emulator) | Fixed 2564ef48 |
| ER-29 | Extension promo card replaces the message on screen | Medium | UI host | `g10/PromoCardWaitsTest` (unit, Robolectric), `MessagesDeviceTest` (emulator) | Fixed dad92181 + 6bb180ef |
| ER-30 | A blocked extension keeps its page loading past every timeout (NBUG-8) | High | online repository | `g10/BlockedExtensionTimeoutTest` (unit, Robolectric) | Fixed 6bce3dbf |

ADR-029 (docs/SYSTEM-DESIGN.md) holds the rules. 10 findings (ER-29 found in step 9, ER-30 = NBUG-8 added at step 8 after a scope check against the checklist): one batch. Both get their own fail-before and pass-after before the group CI rerun.

### Steps 4–9: evidence

Labels: unit = JVM/Robolectric in CI; emulator = phones api33-lowram and api36; TV emulator = tv-api24 and tv-api34.

| Step | ER-21…ER-28 | ER-29 | ER-30 |
|---|---|---|---|
| 4 tests | 48bee9ad (+ 8336dbf3, ER-27 test fault) | 7fd2bf3b | deb6987e |
| 5 fail-before | CI [44445709090](https://github.com/siamform/yumeiro/actions/runs/44445709090) 9/10, rerun [44449000456](https://github.com/siamform/yumeiro/actions/runs/44449000456) 10/10 failed; device api33 [44445734996](https://github.com/siamform/yumeiro/actions/runs/44445734996) ER-28 reproduced; tv-api34 [44445713091](https://github.com/siamform/yumeiro/actions/runs/44445713091) not reproduced (queue ER-28-TV34) | CI [44456278746](https://github.com/siamform/yumeiro/actions/runs/44456278746) PromoCardWaitsTest failed (expected 2 messages, 3 shown); device: MessagesDeviceTest api36 failed right after "card shown" ([44452173594](https://github.com/siamform/yumeiro/actions/runs/44452173594), [44454531660](https://github.com/siamform/yumeiro/actions/runs/44454531660)) | CI [44457201367](https://github.com/siamform/yumeiro/actions/runs/44457201367): load() 8005 ms, home() 8132 ms (limit 4000 ms) |
| 6 fixes | one commit each (register) | dad92181 broke compilation (a new last parameter caught callers' trailing lambdas; CI 44456281899 and device 44456284891 failed to build); 6bb180ef keeps `show()` and adds `showQuiet()` | 6bce3dbf `ExtensionCall.bounded` (home, See all, search, details, links, Source Health) |
| 7 pass-after | CI [44448997376](https://github.com/siamform/yumeiro/actions/runs/44448997376) 1271/0; device api33 [44449003874](https://github.com/siamform/yumeiro/actions/runs/44449003874) | group CI (step 8) | group CI (step 8) |
| 8 group CI rerun | CI [44457297312](https://github.com/siamform/yumeiro/actions/runs/44457297312) at f504a934: 1274 tests, 0 failed, mobile and TV lint | same | same |

Step 9, group regression at f504a934 (app code unchanged since):

- Phones [44460656954](https://github.com/siamform/yumeiro/actions/runs/44460656954): 9 classes PASS on both (RetryActions 5, Messages 101/0, Dialogs,
  PermissionFlow, NetworkState 4, LowStorage 2, SheetFocusTv, Nbug15Redesign 3, Extensions 8). Both promo cards
  ("card shown") fired during MessagesDeviceTest on both phones with problems=0: ER-29 verified (emulator).
- tv-api24 [44460663805](https://github.com/siamform/yumeiro/actions/runs/44460663805): SheetFocusTv, Nbug15Redesign 3, Dialogs (checked=5), Messages 101/0 PASS.
- tv-api34 [44461533827](https://github.com/siamform/yumeiro/actions/runs/44461533827): SheetFocusTv, Nbug15Redesign 3, Dialogs (checked=5) PASS; Messages
  problems=11, every one the same 44 dp view [484,780][756,867] (TouchTargetSizeCheck). Old code (6dbfde03 with the
  same test, throwaway branch, deleted) [44462757164](https://github.com/siamform/yumeiro/actions/runs/44462757164): problems=13, the same view on 12 cases →
  pre-existing, not a G10 regression; queued TV-A11Y-1.

Runs on the way (test faults, fixed in the tests): tv-api24 44452176693 DialogsDeviceTest "no dialog was checked"
(rows below the fold on a 540 dp TV; 080a5bf8, then 44454630375 PASS); MessagesDeviceTest logs the whole
accessibility result since 74cc5b6e (details run 44455588206).

ER-30 is verified by unit tests only. A device run with a real extension that blocks needs the owner's extension and
network: OMT-34. Lower finding kept: the Manga providers (`MangaScreens.kt` 321) are Yumeiro's own suspend code, not
wrapped by `ExtensionCall` (no blocking call found in them; next pass).

Correction: the times written in the bodies of commits f1cc127b…f504a934 (07:05–08:08 UTC) are wrong; the git
commit dates (04:08–04:17 UTC, 2026-11-19) are right.

## Close-out (2026-11-19, §13a re-audit)

G10 closed. ER-21…ER-30 are fixed and verified: CI unit for all; emulator for ER-28, ER-29 and the regression; TV
emulator for ER-28 (tv-api24) and the regression. Queued: ER-28-TV34, TV-A11Y-1, OMT-34. NB15-OB2 resolved by ER-28.
NBUG-8 fixed as ER-30.
