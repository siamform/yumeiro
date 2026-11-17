# F-03 TeraBox / DiskWala links (proposal only; gated; owner decision OMT-25)

Status: **proposal, not built further.** Part 8 gate not passed (4.4). Terms are an owner call (OMT-25).

## 1. Current code (read 2026-11-18, head 52f88de)

Already supported as a link route, not as a source:

- `stream/LinkHosts.kt:56-62` host lists (TeraBox mirrors incl. `1024terabox.com`, `teraboxapp.com`,
  `dubox.com`, `nephobox.com`, `4funbox.com`, …; DiskWala `diskwala.com/.in/.app`).
- `LinkHosts.classify` (`:136-140`) returns `Browser(…, auto = true)`: the page opens in Yumeiro's link
  finder (hardened WebView, `HeadlessCapture`), its player is started automatically and the media request it
  makes is played in Yumeiro; the page is only shown for sign-in or a check.
- Tests: `LinkHostsTest.teraboxAndDiskwalaOpenInPageBrowser`, `:76` sharing link. BEHAVIOR-SPEC "Cloud
  links": unit-tested, **live TeraBox: Device/unverified** (needs a real link, owner).
- Used by `StreamResolver`, `StreamScreen`, `DownloadWorker`.

Gaps: no folder (multi-file share) listing, no quality choice, captured URLs expire (download resume),
no live verification.

## 2. Competitors (ideas and contracts only)

- Hikari 8aa1652b2af151aca85be316b926cbabee742e63: tree has no TeraBox/DiskWala code (`git tree` search for
  "terabox|diskwala" returns nothing).
- CloudStream 2dcc25c3945c61dfa95d1710ced7c74acfe84baa and Nuvio 511ebd5: no built-in TeraBox handler; such
  hosts live in third-party extensions (not read; not copied).

Yumeiro is already ahead of the read competitors on this point; the work is verification and robustness.

## 3. Design (least risk)

- Keep the WebView capture (no private API, no sign-in bypass; respects the host's own player).
- **Folder shares**: when the page lists several files, show Yumeiro's list of the page's file names (read
  from the page DOM after load) and capture the one the user picks.
- **Expiry**: a captured URL is stored with its page URL; on HTTP 403/410 during download or playback, re-run
  capture once (no loop) and resume from the saved offset (BUG-3 case d stays green).
- **Live check**: a CI-safe fixture page that mimics a "player requests HLS after JS" flow, plus the owner's
  real link in OMT (TeraBox live stays owner-only).
- Flag `cloud_capture_v2` with kill switch.

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Paste a single-file link | Page runs hidden, stream plays (as today) |
| Paste a folder link | File list; pick one → plays |
| Site wants sign-in / captcha | Page shown with the reason; after it, capture continues |
| Captured URL expires mid-download | One re-capture, resume at offset; second failure → clear error |
| No stream within 22 s | Gives up, WebView disposed, message (as today) |
| Back during capture | Capture cancelled, WebView disposed |
| Low memory | One capture at a time (as today) |

## 5. Pros and cons

- WebView capture: + no reverse-engineered API, survives API changes; − heavier than a direct API, needs
  the page to work.
- Re-capture on expiry: + downloads survive; − one extra page load.

## 6. Tests (when approved)

Unit (pure): folder-list parse from fixture HTML; expiry → recapture state machine (fail-before: no
recapture). Device: fixture host on api33-lowram (capture, expiry 403, resume offset, case d).

## 7. Owner decision (OMT-25)

Plan folder support and re-capture? Provide one real TeraBox and one DiskWala link for the live check.
