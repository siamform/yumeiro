# Live handover (updated with every push; read this first after AGENT-START-HERE.md)

Owner rule 2026-11-21 16:30 +06: agents can run out of limit at any time, so this file is rewritten with every push:
what was just done, what runs are pending, and the exact next step. Every bug and feature on the lists must be done
AND verified. Mandatory focus now: feed loading, extension loading, load speed, search speed.

## Last updated
2026-11-21 19:17 +06, branch `r15-stability`, commit "TV + phone reruns Pass".

## Owner top priority 2026-11-21 19:00 +06 (read docs/FEED-ENGINE-2.1.md)
Feed engine: Yumeiro loads all extensions like YouTube channels; must handle 1000+; priority (playing > picked >
on screen > background); reserve cache; instant open; feed ~10 min after install/update must be fixed; check whether
extensions reinstall after an update; YouTube and online music slow too. Plan FE-1..FE-7 in that doc.
Owner rule 19:15: after every bug/feature, mark its status in the docs, verified (run ID) or not.

## Owner rule 2026-11-21 19:01 +06
Partial is not acceptable: every bug and feature must be done AND verified (run IDs). Write the docs every push so the next agent resumes exactly. E1 master extension: research and design in docs/E1-MASTER-EXTENSION.md (owner brief quoted there).

## Owner rule 2026-11-21 17:15 +06
No hurry, nothing unverified. Before each fix: research how competitors handle the case (cite URLs in the ledger),
then build Yumeiro's own better version. All bugs mandatory; features one by one. 2.1.0 must be the most stable build.

## Done today (2026-11-21), newest first
- TV Pass 44906652590 (F-04 / F-09 Menu key / F-13 / F-14 / F-15 / F-17 / NF-1 now Verified phones + TV). Phones re-check Pass 44906655104.
- Device Pass on this head (phones): F-07 ExternalPlayerDeviceTest 44904481596 (MX + chooser, Watched(66000, 60000)); ReleaseFeatures / PosterActions / FinalCoverage / MangaOffline 44904447255 (F-04, F-09 phone part, F-13, F-14, F-15, F-17; F-10 glass part; F-12 retry part). CI 44903723798 @b8381ce0 Pass (357 / 1675 / 0). E1 research + design written.
- NF-1 device Pass on all 3 targets (44903495726 / 44903498117). r15 CI 44901991356 @6196d665 Pass (357 classes, 1675 cases, 0 failures): pass-after NBUG10-IME, third green for CI-DNS-RACE. F-07 lowram MX round trip Pass; F07-T4 test fix (media permission dialog).
- NBUG-10 device Pass 44901993315 (both phones, with the NBUG10-IME fix). Fail-befores valid: NBUG10-IME 44901948952, NF-2b 44901952882, F-02 split 44901941945, Country 44901945428, F-18 44899238590 (branches deleted). NF1-T3 and F07-T3 test faults fixed (app rows were right; F-07 result blocked by the permission dialog in the test).
- r15 CI 44899160113 @ff056fb7 Pass (356 classes, 1673 cases, 0 failures): second green for CI-DNS-RACE. DEV-CHK-21-C2: Nf1 rewrite did not compile; the runs on it are invalid; fixed and re-run.
- NBUG10-IME root cause found and fixed (flag EXT_HOLD_WHILE_TYPING): AnimePahe's Cloudflare-check window opened on its own and took the keyboard focus; now held behind a card while the user types. Fail-before 44900885102. F-18 fail-before 44899238590 is valid (main compiled). Fail-before redos from HEAD: 44900731236 (NF-2b), 44900735170 (F-02 split), 44900738482 (Country).
- 17:48 Recorded 44899145489 / 44899147205: Nf1 phones Pass, Speed both Pass (FE-S4 device Pass, live 1453..25101 ms), Nbug10 api36 Pass / lowram Fail (NBUG10-IME open), TvBug5 TV Pass, Nf1 TV Fail (NF1-T2, test rewritten to read rows by label), ExternalPlayer both Fail (F07-T2, logging added). Fail-befores 44899230268 / 44899234331 / 44899243747 / 44899238590 invalid (AB-INVALID-1).
- DEV-CHK-21-T: device tests fixed after run 44896550636 / 44896552485 (Java stub player; Nf1 scroll + on-screen rows; Nbug10 match check + keyboard time). SpeedDeviceTest logs 3 more live searches for FE-S4.
- CI-DNS-RACE: Nbug6 flake traced to NetworkSettings.init overwriting a setting saved during load; fixed with a write lock and test hook.
- FE-T1: 2 GB phone feed half-shown 56.8 s (was 4.4 s): startup trims with 800 MB free throttled the feed to one lane. Fixed (FeedThrottle.confirmed). Rerun SpeedDeviceTest lowram after the r15 CI.
- F-07-RES: "Choose each time" hand-off now asks for the result (MX / Just return the position only when asked). Device round trip ExternalPlayerDeviceTest + StubPlayerActivity (androidTest manifest).
- DEV-CHK-21-C: CI 44892857421 failed at device-test compile (wrong YumeiroDrama import in Nf1 test); fixed.
- F-07-VIS: external players (VLC, mpv, MX, MX Pro, Just Player) were invisible on Android 11+ (manifest `<queries>` missing them); declared. Fail-before 44890380227 verified.
- DEV-CHK-21: new device tests for rows with none: Nbug10FilterSearch, VolumeBoost (F-06), Nf1EpisodeStatus, Upcoming (F-08), F01DubTap.
- CI-429: 44889594971 Blocked by Maven Central 429 before tests; superseded by 44890073919 Pass.
- E4-T: background feed work 1 at a time when hot or short of memory, one less when warm (FeedThrottle, flag e4_throttle).
- E4-GUIDE: no change (OnboardingGate holds only a first run with nothing installed; it is the extension pop-up blocker).
- B31-SEEK5-T: TorrentBlocksTest.primePutsTheStartBeforeTheEnd updated to the B31-SEEK5 contract (tail held until the start has bytes); the only failure of 44887743406.
- FE-Q-CHK Pass 44888340063: an update does not reinstall extensions (26/26 files identical) and does not slow their load.
- FE-4b: Yumix working set: only picked + pinned + fastest 24 (12 on 2 GB) fetch live; rest saved pages, join at the feed end (flag fe4_working_set).
- FE-Q-CHK: UpdateFeedDeviceTest (install extensions, relaunch, `adb install -r` update, compare files / timings / dexopt).
- FE-P-T: PauseWhilePlayingTest expectation fixed (gate has 3 permits; it asserted 4).
- FE-S4: a site's search timeout follows its measured speed (8..25 s), quarantined 8 s, Retry 25 s (flag fe_s4_adaptive_timeout).
- FE-4a: Yumix rebuilds its mix at most every 300 ms (first update at once), not on every row answer (flag fe4_feed_frames).
- FE-S3: a site that failed a search in the last 2 min answers with that failure at once; Retry asks again (flag fe_s3_fail_memory).
- B31-SEEK5: the piece the player waits for downloads alone until it has bytes, then window + tail (flag b31_target_first).
- B31-SEEK4: a seek drops the tail deadlines set at open (TV seek 10370 ms, run 44884971496: tail served before the target).
- FE-S2: search ranking and typeahead (regex) moved off the main thread (Online search, YouTube, YT Music).
- FE-5: FeedWarmWorker refreshes the working set's saved homes every 6 h on Wi-Fi (reserve cache, flag fe5_feed_warm).
- FE-2c: HeatGuard's shared limit of 6 extension calls is a PriorityGate: search, scroll pages, picked before background rows.
- FE-P: Yumix background fetches run one at a time while something plays (PlayingNow + PriorityGate backgroundLimit).
- SPEED-T2: SpeedDeviceTest measures the Yumix pattern (20 extensions, 3 slots) and YouTube / YT Music cold start.
- FE-3: Yumix shows an extension at its first home row (`home(api, force, onRows)`, flag fe3_first_row).
- FE-S1: empty search answers kept (memory 10 min / phone 1 h), second disk read on a miss removed.
- FE-Q1 code reading: no reinstall loop after update; compiled `oat` folder is not deleted (device check still Not run).
- B31-SEEK3: torrent engine downloads only the playback window (flag b31_stream_window); B31-SEEK-T insideWindow at file end.
- FE-2: Yumix PriorityGate (picked extension first, flag fe2_priority_gate); dead site asked one batch (25 s, was 75 s).
- FE-7a/b: YouTube shelves and YouTube Music Home saved on the phone and shown at once (were memory-only).
- FE-1 `f37b4224`: `OnlineRepository.feedHome` shows any-age saved homes at once in Yumix and Home, live refresh behind.
- FEED-ENGINE doc docs/FEED-ENGINE-2.1.md (requirement, root causes FE-RC1..4, design, plan).
- SPEED-T: device test `SpeedDeviceTest` (extension relaunch load, Home first row, search first result; tag SPEED).
- B31-SEEK2: request_queue_time 1, seek target alone at 0 ms, read-ahead from 500 ms (research: libtorrent_flutter, VortX).
- SPEED-3/4: extensions load fastest-first; search starts with loaded extensions and late ones join (no restart).
- SPEED-1/2: cached homes and searches skip the permit queue; Home and search fastest-first (ledger row SPEED-1 / SPEED-2).
- E4 part 1 `b640f68d`: ExtensionSpeed (fastest-first Yumix home order, quarantine 3 fails / 6 h), flag `e4_speed_order`.
- B-31 seek fix `e451b783`: abandoned window's deadlines dropped on seek (finding B31-SEEK from device 44879268223).
- E2 `18917728`: `.github/scripts/curate_live.py` curated probed Live TV (ADR-055), switch repo var `YUMEIRO_CURATED_LIVE`.
- E3 `23fad75d`: ABI splits behind `-Pyumeiro.abiSplits=true` / repo var `YUMEIRO_ABI_SPLITS`, one versionCode (ADR-054).
- B-31 privacy notice `787a7901`: engine plays only after the Settings notice is accepted.
- F-22 `706dee41`, F-02 round trip `e0c4e7b8`, B-31 speed `2b286d6c`, F-21 `60e8d6df`, B-32 earlier.

## Verified (run IDs)
- 44904481596 F-07 phones Pass; 44904447255 old-row tests phones Pass; CI 44903723798 Pass.
- CI 44901991356 @6196d665 Pass (357 / 1675 / 0). NF-1 Pass 44903495726 (phones) / 44903498117 (tv-api34).
- 44901993315 phones: Nbug10FilterSearchDeviceTest Pass (api33-lowram, api36).
- CI 44899160113 @ff056fb7: Pass, 356 classes, 1673 cases, 0 failures.
- 44899145489 phones: Nf1 Pass (both), SpeedDeviceTest Pass (both), Nbug10 Pass api36. 44899147205 tv-api34: TvBug5Repro Pass.
- CI 44896530640 @33c65f8d Pass (356 classes, 0 failures): pass-after FE-T1 and CI-DNS-RACE. CI 44894227826 @49c4c60b Pass: pass-after F-07-VIS, F-07-RES. Fail-before CI-DNS-RACE 44895869754 verified.
- Device 44896550636 / 44896552485: Pass VolumeBoost (F-06) and Upcoming (F-08) on lowram, api36, tv-api34; F01DubTap (F-01/F-16) both phones; SpeedDeviceTest both phones (FE-T1 fixed: lowram feed half 5151 ms).
- Fail-before F-07-RES 44894173529 (theGenericChooserGetsTheWidelyReadExtras failed as expected) and FE-T1 44894340441 (compile); both branches deleted.
- Phones 44892710506 @e2b87521: BuiltinTorrentDeviceTest Pass both (lowram first 2844 / seek 1129 ms: B31-SEEK5 Pass); SpeedDeviceTest Pass all budgets (GOAL run 3), lowram feed regression = FE-T1.
- Device TV api24 44892712537 @e2b87521: BuiltinTorrentDeviceTest Pass (first 64 KB 8696 ms, seek 3148 ms; was 10370 ms): B31-SEEK4 Pass.
- Pass-after CI 44890073919 @e2b87521 (abi_splits=true): success, 353 test classes, 0 failures: FE-S1, FE-S2, FE-S3, FE-S4, FE-3, FE-P (+FE-P-T), FE-2c, FE-4a, FE-4b, FE-5, E4-T, B31-SEEK4/5 (+B31-SEEK5-T) unit, SPEED-T2 compile; GateLaneTest (T-GATE) passed.
- Fail-before verified: E4-T 44889828561 (unresolved FeedThrottle), F-07-VIS 44890380227 (only failure ExternalPlayersVisibilityTest); branches deleted.
- Fail-before verified: FE-4b 44888534031 (unresolved FeedWorkingSet); branch deleted.
- Device 44888340063 @ad503cc2 phones: UpdateFeedDeviceTest Pass (FE-Q1 no reinstall, FE-Q2 load unchanged after update).
- Pass-after CI 44884554686 @346a581c (abi_splits=true): success, 338 test classes, 0 failures: F-22, E3, E2, E4, SPEED-1..4, SPEED-T1, B-31 notice / seek, B31-SEEK2, B31-SEEK3 / SEEK-T, FE-1, FE-2/2b, FE-7a/b. GateLaneTest passed there (T-GATE looks like runner timing; keep watching).
- Fail-before verified: FE-2c 44885309121 (unresolved PriorityGate.Lane); branch deleted.
- Device phones 44884969625 @7d77a583: SpeedDeviceTest Pass both (SPEED-T2 numbers in GOAL-TOP-OF-CLASS.md); BuiltinTorrentDeviceTest api36 Pass (first 64 KB 3668 ms, seek 3115 ms), api33-lowram Fail (first piece no block 16 s) -> B31-SEEK5.
- Device TV api24 44884971496 @7d77a583: BuiltinTorrentDeviceTest first 64 KB 3774 ms Pass, seek 10370 ms Fail -> B31-SEEK4 (fixed, unverified).
- Fail-before verified: B31-SEEK4+5 44886201772 (3 TorrentSourceTest assertions); branch deleted.
- Fail-before verified: FE-S4 44886794506 (unresolved searchTimeoutMs); branch deleted.
- Fail-before verified: FE-4a 44886560580 (unresolved FeedFrames / atMostEvery); branch deleted.
- Fail-before verified: FE-S2 44885736016 (SearchRankOffMainTest names OnlineSearchScreen / YtMusicBrowse / YouTubeScreens), FE-S3 44886384973 (no retry parameter); branches deleted. Finding FE-P-T: PauseWhilePlayingTest expected 4 on a 3-permit gate (test fixed).
- Fail-before verified: FE-S1 44884613831 (assertion "asked the extension again"), FE-5 44885480520 (unresolved FeedWarm); branches deleted. Same run 44884613831: GateLaneTest.tapStartsAtOnce failed (351 ms > 300): finding T-GATE, watch pass-after.
- Fail-before verified: FE-3 44884810399 (compile: home has no onRows), FE-P 44885100184 (unresolved PlayingNow); branches deleted.
- Fail-before verified: FE-1 44883755874 (unresolved feedHome), FE-7a/b 44883994031 (unresolved savedFeed / savedBrowse), FE-2 44884208915 (PriorityGate / homeTimeoutMs), B31-SEEK3 44884447258 (windowPriorities / streamWindow); branches deleted.
- Device 44883372732 (phones) / 44883375160 (TV) @2dba1982: SpeedDeviceTest Pass both phones (baseline in GOAL-TOP-OF-CLASS.md);
  BuiltinTorrentDeviceTest Fail: lowram first 64 KB 5022 ms Pass, seek 9956 ms Fail; api36 first 64 KB 15137 ms Fail;
  TV first 64 KB 6860 ms Pass, seek 19713 ms Fail -> B31-SEEK3 (diagnosed from TorrentTrace).
- CI 44882251682 (r15-stability): failure, only TorrentSourceTest.seekDropsTheOldWindowDeadlinesButKeepsTheNewOne -> B31-SEEK-T (fixed).
- Device 44882300017 / 44882301935 (B31-SEEK2): first 64 KB Pass 8724 / 4096 / 6106 ms; seek Fail 17353 / 7892 / 15941 ms
  (finding B31-SEEK3: libtorrent fetches a piece's blocks from block 0; 4 MiB pieces; diagnosing with TorrentTrace).
- Fail-before verified: E4 44880750191, SPEED-1/2 44881206020, SPEED-3/4 44881403329, B31-SEEK2 44881530280
  (branches deleted).
- CI 44881247581 (@4a58cb22): failure, finding SPEED-T1 (test used an ERROR-level deprecated constructor); fixed.
- Device 44880554878 (@e451b783, phones): notice gate Pass, first 64 KB Pass 8033 / 3192 ms, seek Fail 7566 / 10744 ms
  (B31-SEEK2). TV 44880575223 (api24): first 64 KB 5238 ms Pass, seek 12732 ms Fail (B31-SEEK2).
- Pass-after CI 44879288541 (@2b286d6c): B-32 EmptyHomeTest, F-21 ExtensionLabelsTest, B-31 TorrentBlocksTest.
- Fail-before verified: E2 44880384185, B-31 seek 44880511385 (branches deleted).
- Fail-before verified: F-21 44878962117, B-31 speed 44879233183, F-22 44879571787, E3 44879928117 (branches deleted).
- Device Pass: F-02 round trip 44879417411 (api36). B-31 first 64 KB Pass 44879268223 (2632 / 5783 ms), seek Fail B31-SEEK.

## Pending runs
None.

## Next steps, in order
1. Done: 44906652590 / 44906655104 recorded (BUGS DEV-CHK-21-T6).
2. Still open after that, in order: F-10 Blue theme device test (write it), F-12 swipe between shorts + 9:16 auto-open (build + test), NF-2b / F-02 split / F-18 / Country device tests (write them), F-02 login (flag off: owner TDLib keys), E2 / E3 / E4 verification, F-03 / F-05 / F-19 / F-20 / airing alerts / X-1 / EXT-P (make each complete + verified), PIN-1 test, GATE-1 ANR test, FREEZE rows, C5 / C6.
3. E1 master extension: docs/E1-MASTER-EXTENSION.md; start E1-A only on the owner's go (open questions in section 6).
4. Release only on the owner's explicit go.
