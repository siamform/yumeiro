# F-03 TeraBox / DiskWala: expired streams found again (ADR-042)

Status: **Built, not tested** (batch A). Flag `cloud_capture_v2` (kill switch). The folder-share file list is
**queued with a reason** (section 3); the owner decides.

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

## 2. Competitors (ideas and contracts only; no code copied)

| Project | Commit | Files read | Approach |
|---|---|---|---|
| Hikari codegeasse1/hikari | b186ab9b02531c287373d67637f31795671fb0bc | tree search "terabox\|diskwala" | none |
| [saahiyo/terabox-gateway](https://github.com/saahiyo/terabox-gateway) | 063f61fc61bb173fb936fc487f4a18e40436caab | `src/terabox_gateway/terabox_client.py` | Calls TeraBox's share/list API with a `jsToken` read from the page and a copied account cookie (`ndus`) |
| [Damantha126/TeraboxDL](https://github.com/Damantha126/TeraboxDL) | dbf7f2ce4be527c529134b368f9dd2882e0c11a3 | `TeraboxDL/teraboxdl.py` | Same API, needs the user's `ndus` cookie |
| CloudStream 2dcc25c3, Nuvio 511ebd5 | — | tree | No built-in handler (third-party extensions only) |

Both API tools work around the site's own player and depend on an account cookie that someone copied
out of a browser. That is an access-control and terms risk, so it's **rejected**. Yumeiro uses only the
site's own page in a WebView, as a browser would.

## 3. Design (built) and what is queued

- `stream/CloudRecapture.kt` is a `StreamRefreshers` refresher.
  - It owns requests whose `pageUrl` is on a TeraBox / DiskWala host (`LinkHosts.cloudHost`).
  - On 403/410 or a network error, `RecoveryRouter.RefreshLink` asks it for a fresh stream. It opens the share page off screen in a hardened WebView (ad filter, no file access) and runs the link finder's hook and autoplay scripts.
  - It returns the stream that the page itself requests, and the player resumes at the saved position.
  - Budget: 2 tries per page per 10 minutes, each at most 25 s, and the WebView is destroyed afterwards.
  - A page that wants sign-in or a check returns nothing, so the player shows its error card.
- **Queued (owner decides): folder-share file list.** There's no verifiable public folder fixture, and
  the only competitor route is the rejected API. Today the page opens, and the user picks the file in the
  site's own list (existing behaviour, unchanged).
- Live TeraBox / DiskWala check: owner/community test (needs real links; they expire).

Feature profile:

| Field | Value |
|---|---|
| Purpose | An expired cloud-share stream resumes instead of failing |
| Entry points | Playback of a TeraBox / DiskWala link (Stream, share intent, Web sites) |
| Storage / migration | none |
| Permissions / settings | none new; flag `cloud_capture_v2` |
| Backup / restore / reset | nothing stored |
| Errors / offline | page needs the user, or no stream in 25 s → the existing player error card |
| Accessibility / TV | no new UI; the TV player path is the same |
| Low-end | one off-screen WebView only after a failure, destroyed after ≤ 25 s; at most 2 per page per 10 min |
| Performance budget | 0 ms at startup; ≤ 25 s per refresh |

Side effects: nothing runs unless a cloud stream fails. Memory goes up only while one off-screen
WebView exists, and the budget prevents loops. Other refreshers (YouTube, Telegram) own different
requests.

Terms: the site's own page and player run as in a browser. Sign-in, checks and premium limits stay
with the site, and no API is called and no cookie is copied.

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Paste a single-file link | Page runs hidden, stream plays (as today) |
| Paste a folder link | The site's own page opens; the user picks the file there (Yumeiro list queued) |
| Site wants sign-in / captcha | Page shown with the reason; after it, capture continues |
| Captured URL expires during playback | Page re-captured off screen (≤ 2 per 10 min), resumes at the saved position; else the player error card |
| No stream within 22 s | Gives up, WebView disposed, message (as today) |
| Back during capture | Capture cancelled, WebView disposed |
| Low memory | One capture at a time (as today) |

## 5. Pros and cons

- WebView capture: + no reverse-engineered API, survives API changes; − heavier than a direct API, needs
  the page to work.
- Re-capture on expiry: + downloads survive; − one extra page load.

## 6. Tests

- Unit tests:
  - `TelegramAndCloudRoutesTest.cloudHostsAreKnown` (fail-before: `cloudHost` / `CloudRecapture` are missing).
  - `recaptureBudgetIsTwoPerPagePerTenMinutes`.
- Device test `CloudRecaptureDeviceTest`: a local share page whose player asks for a fresh signed address. The stale address gets a 403, the page is re-captured, and the fresh stream plays from 2000 ms. It runs on both phones and both TVs.

## 7. Owner decision

The folder-share file list is queued (section 3). It needs a public folder link the owner can share for a
fixture, or it stays as the site's own list.
