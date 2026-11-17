# Checklist: every owner bug, every feature, remaining agent work

Created 2026-11-17, restructured 2026-11-18 (eighth cycle) to the owner's column set. One row per item; a
duplicate keeps one row with both IDs. Detail lives in docs/DONE-LEDGER.md (same IDs); this file is the index.
Owner's raw text: docs/NEW-BUGS-FEATURES-2026-11-18.md.

Status words: Not started · In progress · Implemented—unverified · Verified (unit | CI | emulator | hardware) ·
Cannot reproduce · Blocked: owner · Rejected (reason). A Verified row has a fail-before and a pass-after unless
it says "unit only" with the reason. Evidence is blank only for Not started. Every result names its label;
"emulator" means the CI emulators api33-lowram (pixel_3a, 2 GB), api36, tv-api34, tv-api24 (Android 7 TV),
api29 where named.

Competitor column: the version read is recorded in the linked proposal (repo + commit). "—" = no competitor
study needed (test-only or our own defect with an obvious cause).

## A. Owner bugs (BUG-1…7, NBUG register, batch one and two)

| ID | Description | Group | Competitor sources | Status | Evidence |
|---|---|---|---|---|---|
| BUG-1 | Watch progress not restored across sessions (Jump In, YumeMix Continue Watching, banners) | G5 (touches G3) | CloudStream, NewPipe (FEATURE-BUG-1) | Verified (emulator, both phones) | `ResumePointsTest` 7/7 (CI 43851470283, unit); `ResumeAcrossProcessDeviceTest` PASS both phones 43849088080, 44116513171; `Bug1YumixRestoreDeviceTest` BUG1Y_RESULT PASS both phones 44116513171 (artifact logcat); fixes 42f1a1b, 97c99df. Trakt/Simkl carry no resume position (nothing to cross-check) |
| BUG-2 | YouTube channels cannot be opened | G3 (touches G1) | NewPipe, Hikari, CloudStream (FEATURE-BUG-2) | Verified (emulator, both phones) | `YtChannelLinkTest` 6/6 (CI 43851470283); `YtChannelUiDeviceTest` CHANNEL_UI_RESULT PASS ×4 in 44116513171 (re-read 2026-11-18); public pages only, no login |
| BUG-3 = NBUG-1 | Download speed drops at ~50–70 % | G8 | CloudStream, Nuvio, IDM-style split (FEATURE-BUG-3) | Blocked: owner (real phone, OMT-20). Emulator case d green | Fix 40ea96e, 3c85a72; case d 6/6 PASS 44043535616, 44110174701, 44118957078, 44121429260 (total ≤ 10 s, last 10 % ≤ 3.5 s) |
| BUG-4 | Upscaling and quality selection: applied track differs from the chosen one | G3 | CloudStream, Hikari (FEATURE-BUG-4) | Verified (emulator, phones + tv-api34); real YouTube = OMT-15 Blocked: owner | `Bug4QualityListTest` 7/7, `RebufferWindowTest` 6/6 (CI 43861926740); `QualityListDeviceTest` 43861039865 phones; tv-api34 44116803828 QUALITY_RESULT PASS applied 184…1080 (re-read 2026-11-18) |
| BUG-5 | TV UI unstable (umbrella of NBUG-2a…e) | G1/G3 | Kodi, SmartTube, Jellyfin (FEATURE-BUG-5) | Agent part Verified (emulator, TV); TV-4 cross-source scope Blocked: owner (OMT-21) | `TvBug5ReproDeviceTest` tv-api34 43908926085, 43912693329, 43935810414; tv-api24 43949571344, 43957029647 |
| BUG-5 / TV-4 | Cross-source search on TV (G7 proposal 3) | G7 | Kodi, SmartTube | Blocked: owner (decision; options A/B/C written, recommend B) | OMT-21; docs/groups/GROUP-7 proposal 3 |
| NBUG-2a | TV cannot reach fullscreen from the top row (TV-2) | G3/G1 | as BUG-5 | Verified (emulator, TV) | fail-before 43908926085 b → pass 43912693329, 43914880052 (tv-api34), 43949571344 (tv-api24) |
| NBUG-2b | TV horizontal scroll on Home opens YouTube Music (TV-3) | G1 | as BUG-5 | Verified (emulator, tv-api34 + tv-api24) | fix 45a15ea `spacesRowFocus()`; fail-before 44116803828 e → pass 44118950490 (OK 5, re-read 2026-11-18), 44121151867 |
| NBUG-2c | TV search returns nothing (TV-4 focus + answer) | G1 | as BUG-5 | Verified (emulator, TV) for focus + answer; cross-source scope → BUG-5/TV-4 row | 43914880052 d, 43935810414 d, 43957029647 d |
| NBUG-2d | TV player opens the phone layout, landscape fails (TV-5) | G3 | as BUG-5 | Verified (emulator, TV) | fail-before 43902905156 a (608×1080) → pass 43908926085 a (1920×1080); fix 4e5ff31 |
| NBUG-2e | TV landscape does not load | G3 | as BUG-5 | Verified (emulator, TV), same root cause as NBUG-2d (`USER_PORTRAIT`) | as NBUG-2d |
| NBUG-3 = BUG-6 | Music full screen: taps reach elements underneath | G6 | — (own defect) | Verified (emulator, both phones) | fail-before 43912650347 → pass 43914724044 (leaked=0); fix c8ac2ae; `MusicTapThroughDeviceTest` |
| NBUG-4a = BUG-7a | Web URL playback: no download button | G3/G8 | — | Verified (emulator, both phones) | 43935805199 `StreamWatchDownloadDeviceTest`; `StreamDownloadTest` |
| NBUG-4b = BUG-7b | IPTV Play Link download hidden from gallery | G8 | — | Verified (emulator, MediaStore row); gallery app view = OMT-17 Blocked: owner | `PlayLinkPublishTest`; 43935805199 |
| NBUG-4c = BUG-7c | Freeze on stream-format mismatch (DASH vs HLS) | G3 | Media3 samples | Verified (emulator, both phones) | fail-before 43935805199 → pass 43946324687; fix 5d1119e; `StreamFormatMismatchDeviceTest` |
| NBUG-4d = BUG-7d | Freeze with upscaling on | G3 | — | Cannot reproduce (emulator) → Blocked: owner (OMT-16, real GPU) | `UpscaleFreezeDeviceTest` 3/3 PASS both phones 43946324687 (injected decoder failure recovers) |
| NBUG-5 | Dub filter shows the Sub count (7 on Dub) and Episode 7 silently plays Episode 1 | G3 (source metadata used by F-01) | to read: CloudStream, Hikari, Aniyomi | Not started | — |
| NBUG-6 | ISP blocking: per-feature DNS routing with DoH, sequential fallback, owner gate "no side effect" | G12 (touches G11) | to read: OkHttp DoH, Mihon/Tachiyomi DoH, Intra, RethinkDNS | Not started (research note with the six answers comes before any code) | — (guards to keep: SmartDnsResilienceTest, DnsWireClientTest, DataCacheBoundsTest, CI 44067269319) |

**Spot-check 2026-11-18 (eighth cycle, this session):** BUG-2 (44116513171 CHANNEL_UI_RESULT PASS ×4), NBUG-2b
(44118950490 OK 5 tests), N6-2 (44112738603 OK 2 tests), BUG-4 TV (44116803828 QUALITY_RESULT PASS applied
184…1080), DL-11 F (44121429260 rxOverSize 1.030–1.100 matches the note). All hold; no status changed.

## B. Remaining agent work and owner items

| ID | Description | Group | Competitor sources | Status | Evidence |
|---|---|---|---|---|---|
| DL-11 part 2 | Segmented downloads read more than the file | G8 | CloudStream, Nuvio, aria2 (FEATURE-DL-11) | In progress: D+E (4fa126e), F HTTP/1.1 for ranges (7a85df6). Medians met (1.057/1.042×), one download 1.1004× > 1.10 → budget not claimed. Option G rejected by reading (an HTTP/1.1 range can't be shortened; waste only moves), H withdrawn; cause logging 6ec4868 (trips=[…]) to choose J/K/L, note §8.3 | before 44036060683, 44037559115; CI 44103206509 → 44106540469; device 44110174701; A/B 44118957078; pass-after 44121429260; CI 44123412795; analysis note §8.2; CI 44180839496 + device 44180844505 (trip logs, running) |
| X2-1 | Baseline profile gain on real hardware | G12 | — | Emulator part done (no gain claimed: software rendering); hardware Blocked: owner (OMT-12) | startup 44013773673, 44142300486 (TTID 0.62/0.71); jank 44138627579 no gain |
| N6-2 | Android 10 credential screen re-lock | G12 | — | Verified (emulator api29) | fail-before 44114305239 → pass 44112738603 (OK 2, re-read 2026-11-18); guard 3f3fbd8 |
| ST-17 | Stress-test compose spins, cause-naming logging | G12 | — | In progress (recorded per campaign; stays open by order) | 43988958313, 44145301927 (20/20, 0 spins, FATAL 0, ANR 0) |
| ST-7 | Jank on software-rendered emulators | G12 | — | Blocked: owner (OMT-12 real phone) | 44002112195, 44138627579 |
| EXT-T1 | E11 cleanup Back closed a pop-up instead of Source status (api33-lowram) | G2 / ADR-014 | — (test infra) | In progress: 10 plain runs, 0 reproductions; the owner's condition (five failing drama feeds while Source status is open) now seeded by e13_sourceStatusWhileFiveDramaFeedsFail (five studio shelves hang 10 s then time out, health + shelf cache cleared, probes re-run while Source status is open); live logcat of 44162062169 showed only 1–2 failing feeds per E11 window | first seen 43744809503; 44162062169: ExtensionsDeviceTest ×10 api33-lowram, 10/10 PASS, 0 ANR, EXT_T1 log shows only MainActivity focused, no added window |
| PL-16 | Screenshots of 43747147539 / 43748560619 into docs/evidence/pl16/ | G12 | — | Verified (evidence committed) | docs/evidence/pl16/README.md |
| X3-TL | Firebase Test Lab role for the service account | G12 | — | Blocked: owner (console role, OMT-22) | OMT-22 |
| X13-6 | Phone Jackson 2.19.4 → 2.18.11 | G12 | — | Blocked: owner (decision, OMT-23) | OMT-23 |
| OMT-1…19, OMT-12 | Owner tests (hardware, accounts, home network, judgement) | — | — | Blocked: owner | docs/OWNER-MANUAL-TESTS.md |
| Vega | "Vega" = Fire TV Vega OS or Vega app providers? | F-05 | — | Blocked: owner (question, OMT-24) | OMT-24 |
| Telegram / Terabox / Diskwala / manga cloud | Terms, cost, privacy proposal | F-02/F-03/F-11 | Hikari | Blocked: owner (decision, OMT-25) | OMT-25 |

## C. Gated features (Part 8): proposals only until the gate passes and the owner approves

Gate state: **not passed** (DL-11 budget not claimed; EXT-T1 open). Nothing below is built.

| ID | Description | Group | Competitor sources | Status | Evidence |
|---|---|---|---|---|---|
| F-01 = "Sub/Dub badges" | Language / Sub / Dub badges on search posters; tap preselects | G3/G4 | CloudStream, Hikari | Not started (build); proposal written. Badges already ship (ReleaseTags + PosterTags, ff7cb19); gaps: detail write-back, JS/Aniyomi, device test | docs/proposals/FEATURE-F-01-language-sub-dub-badges.md |
| F-02 | Telegram option | new | Hikari 8aa1652 | Not started; proposal written; Blocked: owner (OMT-25) | docs/proposals/FEATURE-F-02-telegram.md |
| F-03 | Terabox / Diskwala links | G3 | — | Not started; proposal written; live check Blocked: owner (OMT-25) | docs/proposals/FEATURE-F-03-terabox-diskwala.md |
| F-04 | User-added WebView site list | G2 | Hikari 8aa1652 | Not started; proposal written | docs/proposals/FEATURE-F-04-webview-site-list.md |
| F-05 | Nuvio / SkyStream / Vega repositories | G2 | Nuvio 511ebd5, SkyStream f5523ae | Not started; proposal written; Vega Blocked: owner (OMT-24) | docs/proposals/FEATURE-F-05-nuvio-skystream-repos.md |
| F-06 | 200 % volume booster | G3 | Next Player 08845f8 | Not started; proposal written | docs/proposals/FEATURE-F-06-volume-booster.md |
| F-07 | External video player | G3 | CloudStream 2dcc25c | Not started; proposal written (base hand-off exists) | docs/proposals/FEATURE-F-07-external-player.md |
| F-08 | Upcoming Movies & Series | G4 | Hikari 8aa1652 | Not started; proposal written; data source = owner call | docs/proposals/FEATURE-F-08-upcoming-movies-series.md |
| F-09 | Watch Later + Favorite on posters | G5 | CloudStream 2dcc25c | Not started; proposal written | docs/proposals/FEATURE-F-09-watch-later-favorite-posters.md |
| F-10 | Glassmorphism theme | G9 | haze 30528aa, PrismalAGSL 0da7d9c, ui-ux-pro-max e59ff37 | Not started; proposal + ADR-017 (proposed) | docs/proposals/FEATURE-F-10-glassmorphism-theme.md |
| F-11 | Manga auto-translation | G7 | jedzqer c980b45 (AGPL), Kotlin9527/Kala9527 54867de | Not started; proposal written; cloud path Blocked: owner (OMT-25) | docs/proposals/FEATURE-F-11-manga-translation.md |
| F-12 | Dedicated vertical video player | G3 | RN-VideoFeed ee2394f, compose-reels be0373c | Not started; proposal + ADR-018 (proposed) | docs/proposals/FEATURE-F-12-vertical-video-player.md |
| NF-1 | Episode watch status: Watched, In Progress bar with position, Fresh | G5 | to read: CloudStream, Hikari | Not started (as a feature). Existing: episode list shows a Watched check when `finished` (OnlinePlayerUi.kt:246, 661) from the BUG-1 resume store; no progress bar, no Fresh state | code read 2026-11-18 |
| NF-2a | Download manager storage labels: Offline cache vs Permanent | G8 | — | Not started (as a feature). Existing: Offline / Permanent labels and filter already on the Downloads page (DownloadsScreen.kt:95, 184, 298; DownloadPrompt.kt:121); gap to confirm: "public gallery" wording and per-row location | code read 2026-11-18 |
| NF-2b | Universal stream download (HLS/DASH muxing without a provider button) | G8 | to read: Seal, ytdlnis, 1DM | Not started. Existing: HlsDownloader, stream download from Web URL (NBUG-4a) | code read 2026-11-18 |
| NF-3 = ADR-014 | Extension pop-up / alert blocker (CNCVerse, phisher98) | G2/G12 | — (owner decision ADR-014) | Implemented and Verified (emulator, phones + TV) except EXT-T1 | ledger ADR-014 row: 43825449454 ExtensionPopups 8/8 + Extensions 7/7 both phones; TV 43866791994 8/8; ST-20 guard engaged 43828509434 |
