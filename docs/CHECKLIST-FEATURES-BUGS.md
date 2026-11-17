# Checklist: owner bugs, remaining agent work, gated features

Created 2026-11-17 (eighth cycle). One row per item. Status words: Not started · In progress · Fixed (unverified) ·
Verified (label: unit / CI / emulator / real hardware) · Blocked: owner. A Verified row needs a fail-before and a
pass-after unless it says "unit only" with the reason. Every non-"Not started" row has evidence. Detail lives in
docs/DONE-LEDGER.md (same IDs); this file is the index.

## A. Owner bugs (BUG-1…7, NBUG register)

| ID | Bug | Same as | Status | Evidence |
|---|---|---|---|---|
| BUG-1 | Watch progress not restored across sessions | — | In progress (D1–D5 fixed; follow-through 4.1 owed: Home Jump In, YumeMix Continue Watching, YumeMix banner after a kill; Trakt/Simkl resume) | Unit `ResumePointsTest` 7/7 (CI 43851470283); `ResumeAcrossProcessDeviceTest` PASS both phones (emulator 43849088080); fixes 42f1a1b, 97c99df |
| BUG-2 | YouTube channels cannot be opened | — | In progress (E1–E6 verified emulator; follow-through 4.1 owed: list, scroll, open video, back) | `YtChannelLinkTest` 6/6 (CI 43851470283, unit); `YtChannelUiDeviceTest` PASS both phones (emulator 43851896291, shots channel_1..5) |
| BUG-3 = NBUG-1 | Download speed drops at ~50–70 % | NBUG-1 | Blocked: owner (real phone, ISP). Emulator case d green | Fix 40ea96e, 3c85a72; case d 6/6 PASS (emulator 44043535616: total ≤ 10 s, last 10 % ≤ 3.5 s); OMT entry |
| BUG-4 | Playback quality list differs from the download list | — | In progress (phones: every height applied; TV emulator per-resolution assertion owed) | `Bug4QualityListTest` 7/7, `RebufferWindowTest` 6/6 (CI 43861926740, unit); `QualityListDeviceTest` 184–1080p applied by decoder size (emulator 43861039865); real YouTube = OMT-15 (bot wall on CI IPs) |
| BUG-5 | TV UI unstable (umbrella of NBUG-2a…e) | NBUG-2 | In progress (TV-2/3/4 focus/5 verified; TV-4 cross-source search Blocked: owner, G7 proposal 3) | `TvBug5ReproDeviceTest` tv-api34 43908926085, 43912693329, 43935810414; tv-api24 43949571344, 43957029647 (emulator) |
| NBUG-2a | TV: cannot reach fullscreen from the top row | TV-2 | Verified (emulator, TV) — fail-before 43908926085 b (focus left the player), pass 43912693329, 43914880052 (tv-api34), 43949571344 (tv-api24) | ledger BUG-5 rows 2026-11-16/17 |
| NBUG-2b | TV: horizontal scroll on Home opens YouTube Music | TV-3 | In progress: not reproduced on an empty install (tv-api34 43902905156 c PASS, tv-api24 43949571344 PASS); one oddity (Right from `quick_card_downloads` jumped to `quick_card_ytmusic`) not yet explained; reproduction with filled rows owed | ledger BUG-5, FEATURE-BUG-5-tv-ui.md |
| NBUG-2c | TV: search returns nothing | TV-4 | Focus + answer verified (emulator, TV 43914880052 d, 43935810414 d, 43957029647 d); cross-source scope Blocked: owner (G7 proposal 3) | ledger BUG-5 |
| NBUG-2d | TV: player opens the phone layout, landscape fails | TV-5 | Verified (emulator, TV): fail-before 43902905156 a (608×1080 portrait box), pass 43908926085 a (1920×1080), fix 4e5ff31 | ledger BUG-5 |
| NBUG-2e | TV: landscape does not load | TV-5 | Verified with NBUG-2d (same root cause: `USER_PORTRAIT` request) | as NBUG-2d |
| NBUG-3 | Music full screen: taps reach elements underneath | BUG-6 | Verified (emulator, both phones): fail-before 43912650347, pass 43914724044 (leaked=0), fix c8ac2ae | `MusicTapThroughDeviceTest` |
| NBUG-4a | Web URL playback: no download button | BUG-7a | Verified (emulator, both phones 43935805199) | `StreamWatchDownloadDeviceTest`, `StreamDownloadTest` |
| NBUG-4b | IPTV Play Link download hidden from gallery | BUG-7b | Verified (emulator, MediaStore, 43935805199); gallery app view = OMT-17 | `PlayLinkPublishTest`, ledger BUG-7 |
| NBUG-4c | Freeze on stream-format mismatch (DASH vs HLS) | BUG-7c | Verified (emulator, both phones) | `StreamFormatMismatchDeviceTest`, `PlayerBackRuleTest` |
| NBUG-4d | Freeze with upscaling on | BUG-7d | Not reproduced on emulator (`UpscaleFreezeDeviceTest` PASS); real GPU = OMT-16 | ledger BUG-7 |

## B. Remaining agent work (master prompt 4.1) and owner items (4.2)

| ID | What | Status | Evidence |
|---|---|---|---|
| DL-11 part 2 | Segmented over-read 1.04–1.44× | In progress: fix 4fa126e cut worst case to 1.12×, device budget not met (median 1.10–1.11×); H3 HTTP/2 under test | before 44036060683, 44037559115, 44041714032; CI fail-before 44103206509 → pass-after 44106540469; device 44110174701 (emulator) |
| X2-1 | Baseline profile gain on real hardware | Blocked: owner for real hardware (OMT-12); emulator extension owed | Startup 44013773673 (emulator): TTID api36 2116 vs 2957 ms, lowram 1756 vs 2465 ms |
| N6-2 | Android 10 credential screen re-lock | Verified (emulator api29) | fail-before 44114305239 (N6-4 guard reverted) → pass-after 44112738603; fix = N6-4 guard 3f3fbd8 |
| ST-17 | Stress-test spins, cause logging | In progress (recorded per campaign) | 43988958313 (20/20, 0 spins) |
| BUG-3 emulator side | Keep case d green | In progress (every download change) | 44043535616; 44110174701 (6/6 after DL-11 fix) |
| EXT-T1 | Extension timing fault | Not started (≥20 runs on api33-lowram first) | — |
| PL-16 | Screenshots into docs/evidence/pl16/ | Done (evidence committed) | runs 43747147539, 43748560619; docs/evidence/pl16/README.md |
| OMT-1…19, X3-TL (OMT-22), X13-6 (OMT-23), OMT-12 | Owner tests | Blocked: owner | docs/OWNER-MANUAL-TESTS.md |
| BUG-3 owner rule | Median per 10 % band, last 10 % ≤ 3.5 s | Blocked: owner | OMT-20 |
| BUG-5 / TV-4 scope | G7 proposal 3, options A/B/C, recommend B | Blocked: owner (decision) | OMT-21 |
| Telegram / Terabox / Diskwala / manga cloud | Proposal, owner decides | Blocked: owner (decision) | OMT-25 |
| Vega | Fire TV Vega OS or Vega app providers? | Blocked: owner (question) | OMT-24 |

## C. Gated features (Part 8): research and proposals only until the gate passes and the owner approves

| ID | Feature | Status | Evidence |
|---|---|---|---|
| F-01 | Language / Sub / Dub badges on search posters | Not started | — |
| F-02 | Telegram option | Not started (owner call on terms/privacy) | — |
| F-03 | Terabox / Diskwala links | Not started (owner call on terms) | — |
| F-04 | WebView site list | Not started | — |
| F-05 | Nuvio / Skystream / Vega repositories | Not started (Vega question to owner) | — |
| F-06 | 200 % volume booster | Not started | — |
| F-07 | External player support | Not started | — |
| F-08 | Upcoming Movies & Series | Not started | — |
| F-09 | Watch Later + Favorite on posters | Not started | — |
| F-10 | Glassmorphism theme | Not started (ui-ux-pro-max + ADR first) | — |
| F-11 | Manga auto-translation | Not started (cloud path: owner call) | — |
| F-12 | Dedicated vertical video player | Not started (ui-ux-pro-max + ADR first) | — |
