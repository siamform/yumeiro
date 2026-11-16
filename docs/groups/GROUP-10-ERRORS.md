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

ID note: these rows were first written as ER-1/ER-2, which clash with the older G10 A1 ledger rows; renamed ER-10/ER-11.

## Verification

| Item | Test | Label | Status |
|---|---|---|---|
| ER-10 mapper ↔ tips | `ErrorTaxonomyTest` | unit | CI run owed |
| ER-12 Retry on failed adds / Inbox | code review | unit (compile) | CI run owed |
| Every message triggered on device | — | emulator | open |
| Left as is after review: per-row Install failures (the Install button stays on the row, so tapping it again is the retry); "Couldn't start casting" (CastSupport.play returns false without a reason; the cast session is already connected when it runs) — noted, not changed | code review | — | noted |
| ER-11 permission denied once / blocked / Open settings | `PermissionFlowDeviceTest` | emulator | run owed |
| Permission revoked while running | Android kills the app process on revoke; what the next start shows is not yet checked | — | open |
| No / slow network, VPN, low storage | — | emulator | open |
