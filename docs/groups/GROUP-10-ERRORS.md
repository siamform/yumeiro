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
| ER-12 Retry on failed adds / Inbox | code review | unit (compile) | compiled CI 43941919340; not device-tested |
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
