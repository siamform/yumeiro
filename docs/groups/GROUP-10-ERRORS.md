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
| ER-1 | The tips under an error did not match the shared mapper's own texts: "Your network refused the connection…" got the HTTP 403 tips (refresh the link, add a Referer), "Secure connection failed" got the offline tips, "The server took too long to answer" and "The server sent unreadable data" only the generic ones, and an update "certificate mismatch" got the TLS date/time tip. HTTP 401/403/410/429/451 were all shown as "Server error (HTTP …)" | `ErrorHelp` matches keywords in order and was written for raw texts, not for the mapper's output; the mapper labelled every non-404 HTTP code a server error | Mapper names 4xx answers; ErrorHelp gets branches for the mapper's phrases and keeps signature errors out of the TLS branch | `r17/ErrorTaxonomyTest` (4; 13 causes: text and tips) |

## Verification

| Item | Test | Label | Status |
|---|---|---|---|
| ER-1 mapper ↔ tips | `ErrorTaxonomyTest` | unit | CI run owed |
| Every message triggered on device | — | emulator | open |
| Permission flows denied / permanently denied / revoked | — | emulator | open |
| No / slow network, VPN, low storage | — | emulator | open |
