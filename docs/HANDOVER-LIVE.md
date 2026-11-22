# Live handover (updated with every push; read this first after AGENT-START-HERE.md)

Owner rule 2026-11-21 16:30 +06: agents can run out of limit at any time, so this file is rewritten with every push:
what was just done, what runs are pending, and the exact next step. Every bug and feature on the lists must be done
AND verified. Mandatory focus now: feed loading, extension loading, load speed, search speed.
Owner 2026-11-22: every doc by past agents must be read. Done: docs/DOCS-READ-DIGEST.md (read it; D-1..D-13).

## Last updated
2026-11-22 10:55 +0600, branch `r15-stability`. This push: F-05-N-ON (scripts_nuvio + f05_stream_addons default on)
after device 44985807462: NuvioProviderDeviceTest 4/4 + VegaProviderDeviceTest 3/3 on api36 and api33-lowram.
Tests changed on purpose for the flip (NuvioAdapterTest defaults, EcosystemRegistryTest Nuvio repos added). OMT-73.
GATE-1-QUIET (cd0b256a) closed on the emulator: lowram Pass twice, 44984838999 worst 664 ms, 44985820433 worst 559 ms,
app stall 0 ms. New finding GATE-1-PREPARE-COLD: the first start after install (prepare step, before the settle)
stalls 4-6 s on lowram while the CPU is 90 % busy; not counted by the gate, open.
- Prod 2026-11-22: /v1/health ok; Last.fm built-in signing off (Worker has no LASTFM_SHARED_SECRET; owner B-9).
- Test Lab still Blocked on the owner (enable 2 APIs + Editor role, OMT-22 has the links).

## Pending runs (check each, then update the ledger rows that say PENDING)
- r15 CI for F-05-N-ON (dispatched after this push, review_apk=false): pass-after for the flip.
- r15 CI 44983288674 (@2ab4f33f): Build, test & lint Pass, gitleaks Pass; review APK result: see ledger F-05-N-ERR.
- Fail-befores done: MUSIC-START-2 44979435737, F-05-N 44977804955, F-05-N-ERR 44983223105 (branches deleted).
- CI gitleaks scans every live branch: never leave an ab/ branch with a secret-looking literal alive during other runs.
- Old leftover remote branches from past agents: ab/b27, b29, b30, freeze1, freeze2, gate1, prof1, t1 (-before). They do
  not trip gitleaks; check their ledger rows before deleting.

## Next step
1. Close F-05-N-ON pass-after (ledger row, MAJOR F-05).
2. GATE-1-PREPARE-COLD: run gate_quiet before GateOnePrepareDeviceTest too, measure; trace if it repeats quietly.
3. GATE-1-VIDEOS-COMPOSE: Perfetto trace of the Videos tap on lowram to name the slow list item.
4. GATE-1 on real phones: when the owner turns Test Lab on, `gh workflow run ftl-gate.yml --ref r15-stability`.
   Never raise the 700 ms limit.
5. F-05 next: SkyStream adapter on the same seam (plugin.js globals getHome/search/load/loadStreams(cb), .sky zip,
   manifest.baseUrl, loadExtractor; v2.9.0 ignores the manifest plugins list), then Stremio stream-only add-ons.
6. B31-FRAME device test (real player first frame + seek on the test hash), FEED-ENGINE leftovers, C5, C6, other rows,
   then UNVERIFIED-QUEUE / INVENTORY (also EDGE-CASE-AUDIT P2-4, P2-5). E1 only on the owner's go.

## Done this session (2026-11-21 evening / 2026-11-22), newest first
- 2026-11-22 10:55: F-05-N-ON (this push); GATE-1-QUIET closed (664 / 559 ms Pass).
- 2026-11-22 10:30: GATE-1-QUIET cd0b256a.
- 2026-11-22 10:05: F-05-N-ERR 2ab4f33f.
- 2026-11-22 09:15: GATE-1-MUSIC-START-2 f46c3f22.
- 2026-11-22 09:00: F-05-N Nuvio 06200fc3, docs digest 45aac76a.
- 2026-11-22 morning: RENDER-5b 0270f069, FF-LOAD 8cb89e9b, GATE-1-AB b847caa0, GATE-1-FTL 76a6e521, MUSIC-START 9da67d0c.
- GATE-1 (newest first): RENDER-4b f30cd6c5 poster phase fix; TRACE-4 1ee95cc3; T11 345d2572; RENDER-5 935875ed;
  RENDER-4 posters; TRACE-3/3b; T10 a4cc1379; RENDER-3 4b303e0d; RENDER-2 62102ae5; RENDER a8f33c0e. Gate runs
  44951712024, 44953724011, 44955115811, 44956362034, 44957621376 (swiftshader), 44958850831 (swangle): all Fail.
- GATE-1-RENDER (17a02248) debug-only Compose composition tracing; GATE-1-TRACE-2 (8c8eec69) atrace-only gate trace
  (52 s, whole walk); CI-MAVEN-404 (865bce1e) review APK retries once on a Maven Central miss; GATE-1-ANR-T8
  (768a5698) the harness fails a class on an input ANR under instrumentation (AMS ends the run, no exit record).
- GATE-1-ANR T2..T7 walk fixes (popup window, pill label, landed checks, live accessibility tree): walk completes.
- PIN-1-DUP, F-19-EXT, F-19-EXT-2, CI-DNS-RACE-3, OL-6-T: pass-after CI 44944975133 (unit 1753 / 0 failed).
- FREEZE-4 guard test 7e31dcfe Pass 44937485728; AIR-T1 1e358ea8 Pass 44942929078; OL-13 device Pass.
- OL-13 (1ce6d8ca): About › Community shows Discord, Facebook, Instagram, X, TikTok, Reddit (@officialyumeiro) as an
  icon row; About › Developer adds Facebook siam1p, Instagram asfakul_siam, Telegram @siamorwho. Simple Icons (CC0)
  marks; README Community section. Flag ol13_social_links. Manual: OMT-71.
- OL-6-REVEAL (27bdae38, not enough) + OL-6-REVEAL-2 (ddfb93a9): "Where it goes and tips" scrolls into view when
  opened. OL-5-T2 (b2e0e3a3): subtitle test scrolls first. Both from r15 CI 44929819988.
- CI-DNS-RACE-2 (c1fb5652): a NetworkSettings.init from before a test reset no longer writes after it; fail-before
  44930913875 valid, pass-after 44932398355. CI-DNS-RACE flake closed.
- E2-DRY (0f5f9fd1): stream-sync dry_run input; run 44932539992 Pass (23 countries, nothing published).
- E3 fail-before 44879928117 confirmed valid. F-02 login lowram device Pass 44933818039.
- Device fail-befores valid: OL-4 44931006317, OL-9 44931008815, OL-10 44931866056, OL-6 44932939164, OL-5-HEIGHT
  44933186744 (redone on current code; 44932248504 on the old branch failed for another reason). All ab/ol* branches
  deleted except ab/ol13-before.
- Device Pass @71efd02d: phones 44929260054, TV 44929262746 (PopupQuality, F02Split, F18MangaChapters, LiveCountry,
  StreamWatchDownload) -> NF-2b, F-02 split, F-18, Country filter device Verified.
- r15 CI 44929819988 @197d8df1: 1712 tests, 2 fails (OL-6-REVEAL, OL-5-T2) -> OL-5, OL-7-EDGE, OL-11, OL-12 Verified.

## Owner rule 2026-11-22 (session 3)
Owner added OL-13 (social links) 2026-11-22 and repeated: partial or fake success is not acceptable.

## Owner rule 2026-11-21 (session 3)
"Continue, fix and feature all; partial or fake success is not acceptable; if a bug or feature needs a manual test,
list it, I will do it later." Manual tests go to docs/OWNER-MANUAL-TESTS.md (OMT-64..OMT-71 for OL items, F-12).
Secrets: never in code; only the gitignored secrets/ folder. SEC-1 (Last.fm shared secret) and UPG-1 rotation are the
owner's, he said not to worry about them.

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

## Owner rule 2026-11-21 19:52 +06
Commits: owner is author and committer, one `Co-authored-by: Perplexity` trailer (re-approved). Credentials: never in
code, commits or docs; only in the gitignored `secrets/` folder on the working machine.

## Done today (2026-11-21), newest first
- OL-5 (91667cdd): download popup chips for real HLS heights, audio track and subtitles (website HLS master read off
  the main thread, 6 s call timeout); episodes: "Save all the source has" + Sub/Dub chips in the sheet; picked audio
  joined; subtitles saved as "<video>.<label>.vtt|srt" (SubtitleSaver) and loaded by the player from Downloads.
  Flag ol5_download_tracks. Fail-before CI 44927927381 (ab/ol5-before, empty stubs) running.
- OL-12 (5357dce2) Download playlist on Online Music; OL-11 (fc474be6) This episode only + Episode range. Fail-befores
  44927039015 / 44926724501 running.
- OL-8 02dbbfbb, OL-2 1346cedb, OL-3 4fe266a4 + 2d27529a, OL-1 ae938183 + 95108c0c (build fix), OL-4 f08c8c38,
  OL-7 acf7f590, OL-9 123de693, OL-10 91ee246c. Valid fail-befores: OL-8 44922989207, OL-2 44923147788, OL-3
  44923308227, OL-1 44923702849 + device 44926511600 ("no kept WebView"). Device api36 Pass 44926513936 @95108c0c
  (WebsiteTab, StreamWatchDownload with the popup, MoreDestinations OL-9 / OL-10); lowram job of that run failed
  before tests (emulator package zip corrupt on the runner, infra) and was re-dispatched.
- F-12-FLAG (7e93879f): f12_vertical_shorts on. Fail-before 44922624531 valid, CI 44922673093, phones 44922675456,
  TV 44922677658 all Pass: F-12 Verified. NBUG-13-PORT Pass (44917972192 valid, 44918012619 green).
- NBUG-13-PORT (8a3e74a4): CI 44914717596 failed AppProxyTest (the random free port held the digits 407, and the proxy
  error text checked "407" before the error type, so an unreachable proxy read as "wants a login"). Fixed, flag
  nbug13_reach_first, new fixed-port test. Fail-before CI 44917972192 and pass-after CI 44918012619 running.
- F12-MODE + F12-CHROME device Pass: phones 44916676685 (vertical on, queue swipe, Back to the normal player with the
  same clip playing; chrome hides at 3.5 s, tap shows, tap pauses), TV 44916693769 (never enters / skips). Fail-before
  device 44913945589 valid (branch ab/f12mode-before deleted). First pass-after 44913948025 failed on the tests, fixed:
  F12-TEST-CLING (bf534934, Android's one-time full-screen notice took Back) and F12-TEST-CLOCK (c3ec754d, the hide
  timer follows the compose test clock). Diagnosis run 44915399473 on diag/f12back (deleted).
- F12-MODE (a2222bf5): vertical mode in the normal player for 9:16 non-YouTube videos (ADR-056): full screen portrait,
  centre swipe up / down through the queue, Back to the normal layout. F12-CHROME (215fb53d): Shorts top bar + title
  hide after 3.5 s of play, a tap brings them back. F12-LINT (7eb17931): Media3 opt-in on ShortsWindow.resizeFor (lint
  errors in CI 44911548689 and 44912685131). New asset yumeiro_portrait_clip.mp4 (180x320). Built, not tested.
- F12-HIST + F12-VIEW Pass: unit CI 44912685131 @8c304d4f (failed only on lint), device phones 44912687672 (swipe +
  ReleaseFeatures Pass, api36 inside budget now), TV 44912689981 (swipe skips by design). Fail-befores valid
  44911548689 (unit) and 44911557702 (device, views 14 vs 1); branch ab/f12view-before deleted.
- F12-HIST (3d4d780c): YouTube history written on one IO lane (was main thread on every Shorts settle / Music tap).
  F12-VIEW (93c73933): one PlayerView for the whole Shorts pager. Both from pass-after 44910278641 (api36 Fail
  1384 / 1372 ms > 1200; lowram Pass). F-12 fail-befores valid: 44910420105 (unit), 44910276470 (device). Built, not tested.
- F-12 vertical Shorts: BUILT, NOT TESTED (0288e8d test, f8acd0c feat; flag f12_vertical_shorts default off). Swipe on
  one shared player + YouTube-marked Shorts up to 3 min open the Shorts player. Not built yet: 9:16 auto-open for
  non-YouTube content, swipe-up preload, idle-hide controls, Back to the normal player.
- F-10 Verified phones + TV (new F10BlueThemeDeviceTest; fail-before 44908264228, pass 44909154883 / 44909158248). F-07 TV Pass 44908157533. NBUG-10 TV: test skips by design (FINAL-39).
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
- 44916676685 phones Pass F12VerticalModeDeviceTest + F12ShortsSwipeDeviceTest (3 tests) @c3ec754d; 44916693769 TV Pass (entered=false, chrome test skips).
- 44912687672 phones Pass F12ShortsSwipeDeviceTest + ReleaseFeaturesDeviceTest @8c304d4f; 44912689981 TV skip / 5 Pass; unit 44912685131 Pass (lint Fail = F12-LINT).
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
- r15 unit pass-after CI 44926431107 @95108c0c (OL-8, OL-2, OL-3, OL-1, OL-4, OL-7, OL-9, OL-10 unit + compile + lint).
  When it ends: `gh workflow run ci.yml --ref r15-stability -f review_apk=true` for OL-11, OL-12, OL-5 (head 91667cdd+).
- Fail-before CIs (each must fail only on its new test): ab/ol4-before 44926496585 (StreamDownloadAskTest),
  ab/ol7-before 44926463969 (PlayerTopBarAlignTest), ab/ol9-before 44926469201 (HomeSpacesMoreTest), ab/ol10-before
  44926473786 (NavMoreDestinationsTest), ab/ol11-before 44926724501 (EpisodeRangeDownloadTest), ab/ol12-before
  44927039015 (PlaylistDownloadTest), ab/ol5-before 44927927381 (DownloadTracksTest). Read with
  `gh run download <id> -D /tmp/fb_<id>` + TEST-*.xml; no XML = compile error (`--log-failed | grep "e: "`).
- Device: r15 lowram 44927936416 and TV api34 44927947420 (WebsiteTabDeviceTest, StreamWatchDownloadDeviceTest,
  MoreDestinationsDeviceTest; logcat tags YuOlTab, YuWatchDownload, YuOlDest).
- Still to dispatch: device fail-befores ab/ol4-before (StreamWatchDownloadDeviceTest), ab/ol9-before + ab/ol10-before
  (MoreDestinationsDeviceTest), api36 is enough. Max 2 device workflows at once.
- Branch cleanup after each valid fail-before: `git worktree remove --force /tmp/fb_X; git push origin --delete
  ab/X-before; git branch -D ab/X-before` (ol8, ol2, ol3, ol1 are valid already).
- Ledger rows hold PENDING-UNIT / PENDING-FB / PENDING-DEV / PENDING-NEXT markers: replace each with the run result.

## Next steps, in order
1. Record the pending runs above (ledger + must-do Part 3b statuses). A failing test on new code = its own finding
   with its own fail-before; never relax an assertion or budget.
2. OL-6 downloader modal redesign (research other apps' download sheets first; build behind a flag; test + fail-before).
3. Part 8 step 2: device tests for NF-2b, F-02 split pages, F-18 and Country (fail-befores already valid; write the
   tests). Then step 3 F-02 Telegram TDLib login, step 4 E2 / E3 / E4 verification, step 5 F-03 / F-05 / F-19 / F-20 /
   airing alerts / X-1 / EXT-P / PIN-1 / GATE-1 ANR / FREEZE / C5 / C6, step 6 FEED-ENGINE-2.1.md leftovers.
4. E1 master extension: docs/E1-MASTER-EXTENSION.md; start E1-A only on the owner's go (open questions in section 6).
5. Release only on the owner's explicit go.
