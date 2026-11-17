# Checklist: owner bugs, remaining agent work, gated features

Created 2026-11-17 (eighth cycle). One row per item. Status words: Not started · In progress · Fixed (unverified) ·
Verified (label: unit / CI / emulator / real hardware) · Blocked: owner. A Verified row needs a fail-before and a
pass-after unless it says "unit only" with the reason. Every non-"Not started" row has evidence. Detail lives in
docs/DONE-LEDGER.md (same IDs); this file is the index.

## A. Owner bugs (BUG-1…7, NBUG register)

| ID | Bug | Same as | Status | Evidence |
|---|---|---|---|---|
| BUG-1 | Watch progress not restored across sessions | — | Verified (emulator, both phones; D1–D5 fixed; follow-through done 2026-11-17: after a process kill Home Jump In, YumeMix Continue Watching and YumeMix banner restore; Trakt/Simkl carry no resume position, nothing to cross-check) | Unit `ResumePointsTest` 7/7 (CI 43851470283); `ResumeAcrossProcessDeviceTest` PASS both phones (43849088080; 44116513171 pos 9346/8441); `Bug1YumixRestoreDeviceTest` BUG1Y_RESULT PASS both phones (44116513171, logcat artifact: history 240000/600000 in the new pid, jump-in, banner, continue PASS); fixes 42f1a1b, 97c99df |
| BUG-2 | YouTube channels cannot be opened | — | Verified (emulator, both phones; E1–E6; follow-through done 2026-11-17: open channel, list, scroll, open video, channel row, back) | `YtChannelLinkTest` 6/6 (CI 43851470283, unit); `YtChannelUiDeviceTest` PASS both phones (43851896291; re-run at head 44116513171 CHANNEL_UI_RESULT PASS); NewPipeExtractor public pages only, no login, terms kept |
| BUG-3 = NBUG-1 | Download speed drops at ~50–70 % | NBUG-1 | Blocked: owner (real phone, ISP). Emulator case d green | Fix 40ea96e, 3c85a72; case d 6/6 PASS (emulator 44043535616: total ≤ 10 s, last 10 % ≤ 3.5 s); OMT entry |
| BUG-4 | Playback quality list differs from the download list | — | Verified (emulator: applied track asserted per resolution on both phones and tv-api34); real YouTube = OMT-15 Blocked: owner | `Bug4QualityListTest` 7/7, `RebufferWindowTest` 6/6 (CI 43861926740, unit); `QualityListDeviceTest` 184–1080p applied by decoder size (emulator 43861039865 phones; tv-api34 44116803828 on e011c77, quality code identical to head: 1080/720/480/288/184 each applied, back to Auto); real YouTube = OMT-15 (bot wall on CI IPs) |
| BUG-5 | TV UI unstable (umbrella of NBUG-2a…e) | NBUG-2 | Agent part verified (emulator TV: TV-2, TV-3, TV-4 focus + answer, TV-5); remainder Blocked: owner (TV-4 cross-source search, G7 proposal 3 / OMT-21) | `TvBug5ReproDeviceTest` tv-api34 43908926085, 43912693329, 43935810414; tv-api24 43949571344, 43957029647 (emulator) |
| NBUG-2a | TV: cannot reach fullscreen from the top row | TV-2 | Verified (emulator, TV) — fail-before 43908926085 b (focus left the player), pass 43912693329, 43914880052 (tv-api34), 43949571344 (tv-api24) | ledger BUG-5 rows 2026-11-16/17 |
| NBUG-2b | TV: horizontal scroll on Home opens YouTube Music | TV-3 | Verified (emulator tv-api34 + tv-api24): cause = Right at the end of a Spaces grid row left the row and landed on `quick_card_ytmusic` (row above), so Center opened YouTube Music. Fix 45a15ea `spacesRowFocus()`. Filled online feed rows (LazyRow) not reproduced on CI (no seeded content); real TV = owner optional | `TvBug5ReproDeviceTest#e` fail-before 44116803828 (e011c77: R1→quick_card_ytmusic), pass-after 44118950490 tv-api34 and 44121151867 tv-api24 (R1–R3 stay on quick_card_downloads; a–e OK 5) |
| NBUG-2c | TV: search returns nothing | TV-4 | Focus + answer verified (emulator, TV 43914880052 d, 43935810414 d, 43957029647 d); cross-source scope Blocked: owner (G7 proposal 3) | ledger BUG-5 |
| NBUG-2d | TV: player opens the phone layout, landscape fails | TV-5 | Verified (emulator, TV): fail-before 43902905156 a (608×1080 portrait box), pass 43908926085 a (1920×1080), fix 4e5ff31 | ledger BUG-5 |
| NBUG-2e | TV: landscape does not load | TV-5 | Verified with NBUG-2d (same root cause: `USER_PORTRAIT` request) | as NBUG-2d |
| NBUG-3 | Music full screen: taps reach elements underneath | BUG-6 | Verified (emulator, both phones): fail-before 43912650347, pass 43914724044 (leaked=0), fix c8ac2ae | `MusicTapThroughDeviceTest` |
| NBUG-4a | Web URL playback: no download button | BUG-7a | Verified (emulator, both phones 43935805199) | `StreamWatchDownloadDeviceTest`, `StreamDownloadTest` |
| NBUG-4b | IPTV Play Link download hidden from gallery | BUG-7b | Verified (emulator, MediaStore, 43935805199); gallery app view = OMT-17 | `PlayLinkPublishTest`, ledger BUG-7 |
| NBUG-4c | Freeze on stream-format mismatch (DASH vs HLS) | BUG-7c | Verified (emulator, both phones): fail-before 43935805199, pass 43946324687, fix 5d1119e | `StreamFormatMismatchDeviceTest`, `PlayerBackRuleTest` |
| NBUG-4d | Freeze with upscaling on | BUG-7d | Blocked: owner (OMT-16 real GPU). Not reproduced on emulator: `UpscaleFreezeDeviceTest` 3/3 PASS both phones 43946324687 | ledger BUG-7 |

**4.3 register state (2026-11-17):** every NBUG row is verified (emulator) or Blocked: owner (NBUG-1 real phone,
NBUG-2c cross-source scope, NBUG-4d real GPU). Spot-check of five verified rows against the runs (eighth cycle):
NBUG-2a/2d 43908926085 (a PASS code 0, b FAIL as fail-before), NBUG-3 43914724044 (MusicTapThrough PASS both
phones; the run's red is PushAlertsEffect), NBUG-4a 43935805199 (StreamWatchDownload PASS), NBUG-4c/4d
43946324687 (StreamFormatMismatch and UpscaleFreeze PASS both phones). All five hold; no status changed except
NBUG-4d wording (Not reproduced → Blocked: owner, reason: only a real GPU can show it).

## B. Remaining agent work (master prompt 4.1) and owner items (4.2)

| ID | What | Status | Evidence |
|---|---|---|---|
| DL-11 part 2 | Segmented over-read 1.04–1.44× | In progress: D+E (4fa126e) then F, HTTP/1.1 for byte ranges (7a85df6, H3 proven: protocol=h2). Medians 1.057/1.042× met; one download 1.1004× > 1.10, so the budget is not claimed | before 44036060683, 44037559115, 44041714032; CI 44103206509 → 44106540469; device 44110174701; H3 44116513171; A/B 44118957078; pass-after 44121429260 (case d 6/6); CI 44123412795 |
| X2-1 | Baseline profile gain on real hardware | Blocked: owner for real hardware (OMT-12); emulator extension done (restore journey, 743b1c7; startup and jank re-measured) | Startup 44013773673 (emulator): TTID api36 2116 vs 2957 ms, lowram 1756 vs 2465 ms; jank 44138627579 no gain (P50 327.6 vs 321.2 / 317.2 vs 312.5 ms, software rendering); startup 44142300486 TTID api36 1873 vs 3016 ms (0.62), lowram 2325 vs 3261 ms (0.71) |
| N6-2 | Android 10 credential screen re-lock | Verified (emulator api29) | fail-before 44114305239 (N6-4 guard reverted) → pass-after 44112738603; fix = N6-4 guard 3f3fbd8 |
| ST-17 | Stress-test spins, cause logging | In progress (recorded per campaign) | 43988958313 (20/20, 0 spins); 44145301927 (20/20, 0 spins, FATAL 0, ANR 0) |
| BUG-3 emulator side | Keep case d green | In progress (every download change) | 44043535616; 44110174701 (6/6 after DL-11 fix); 44118957078 and 44121429260 (6/6 each after option F) |
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
| F-06 | 200 % volume booster | Proposal written (gated; owner approval needed) | docs/proposals/FEATURE-F-06-volume-booster.md (Next Player 08845f8 LoudnessEnhancer contract) |
| F-07 | External player support | Proposal written (gated; owner approval needed). Base hand-off exists; gaps: result read-back, headers, subtitles | docs/proposals/FEATURE-F-07-external-player.md (CloudStream 2dcc25c contracts) |
| F-08 | Upcoming Movies & Series | Not started | — |
| F-09 | Watch Later + Favorite on posters | Proposal written (gated; owner approval needed) | docs/proposals/FEATURE-F-09-watch-later-favorite-posters.md (CloudStream 2dcc25c WatchType) |
| F-10 | Glassmorphism theme | Not started (ui-ux-pro-max + ADR first) | — |
| F-11 | Manga auto-translation | Not started (cloud path: owner call) | — |
| F-12 | Dedicated vertical video player | Not started (ui-ux-pro-max + ADR first) | — |
