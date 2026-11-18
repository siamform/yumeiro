# Eighth handover report (Yumeiro r15-stability)

Kept current after every job. The repo is the truth; this file and "Resume here" in docs/R15-STATUS.md must agree.

## State

- Head at start (session 1, 2026-11-17): 2cbb2ae (docs only after 912093b). No run after 44094439021 then.
- Head at start (session 2, 2026-11-18): 063b552 — 47 commits after 912093b, all pushed by session 1; the master
  prompt's "last state 912093b" was out of date (repo wins). Newest run at start: 44162062169 (EXT-T1), done.
- Session 2 differences from the prompt: checklist and eighth report already existed (checklist lacked batch two:
  NBUG-5, NBUG-6, NF-1…NF-3 → added; columns restructured to ID/description/group/competitor/status/evidence);
  report renamed to the required 2026-11-18 name; raw report file docs/NEW-BUGS-FEATURES-2026-11-18.md created.
- Skills (session 2): cloned to /home/user/workspace/agent-skills (sandbox only). Findings: kotlin-agent-skills holds
  only tooling/migration skills (Gradle, AGP 9, Java→Kotlin), no coroutine/Compose guide — used for build-side
  checks only; morluto/rea is a reverse-engineering MCP toolkit, not a general problem-solving method — its
  "observe, hypothesise, prove" workflow is applied, the tools need Node/Frida and are not run here;
  architecture-designer SKILL.md read (ADR template used); ui-ux-pro-max present.
- Current group: 4.1. Done for agent scope: N6-2, BUG-1, BUG-2, BUG-4, TV-3. DL-11 p2 nearly met (option F).
  X2-1 emulator part done (profile 743b1c7, jank no gain, startup measured). 4.3 register done or Blocked: owner.
  ST-17 campaign 44145301927 read (20/20, 0 spins, stays open). Group 13 closed for agent work. 4.4 proposals
  F-01…F-12 written (ADR-017, ADR-018 proposed); gate not passed, nothing built.
  Next: EXT-T1 (≥20 runs on api33-lowram), DL-11 next sample, then the 4.4 gate check.
- Open runs: CI 44180839496 (unit, 6ec4868), device 44180844505 (DownloadThroughputDeviceTest a+d ×3, both phones, 6ec4868).

## Findings (eighth cycle)

| ID | Root cause | Fix | Test (fail-before → pass-after) | Label |
|---|---|---|---|---|
| N6-2 | Android 10 PIN screen is a separate activity; returning re-locked | N6-4 guard 3f3fbd8 | 44114305239 → 44112738603 (emulator api29) | verified (emulator) |
| DL-11 p2 | Pace restarts on our own pauses (H2) and link dips (H1) | 4fa126e (ADR-016 D+E) | 44103206509 → 44106540469 (CI unit); device 44110174701 | unit verified; device budget not met |
| DL-11 H3 | Byte ranges rode HTTP/2: segments were streams on one TCP; restarts cancelled in-flight bytes | 7a85df6 HTTP/1.1 for ranges (option F) | same-run A/B 44118957078; pass-after 44121429260 (medians 1.057/1.042×, one 1.1004×); CI 44123412795 green | emulator; budget not claimed (one sample 0.04 % over) |
| TV-3 | Right at a Home Spaces row end jumped to the YouTube Music card | 45a15ea spacesRowFocus() | 44116803828 → 44118950490 (tv-api34), 44121151867 (tv-api24) | verified (emulator) |
| BUG-1 | follow-through: Jump In, YumeMix banner, Continue Watching after a kill | fixes 42f1a1b, 97c99df | 44116513171 BUG1Y_RESULT PASS both phones; Trakt/Simkl carry no resume | verified (emulator) |
| BUG-2 | follow-through: channel list, scroll, video, back | E1–E6 | 44116513171 CHANNEL_UI_RESULT PASS both phones | verified (emulator) |
| X2-1 | Profile journey's am kill dropped the process that had run Home (Home rules 684 → 99) | 52155bf restore first, browse after; profile 743b1c7 | 44128769774 → 44133217014 (rules); jank 44138627579 no gain; startup 44142300486 0.62/0.71 | emulator; no gain claimed |
| BUG-4 | applied track per resolution on TV | — | 44116803828 tv-api34 QUALITY_RESULT PASS 1080…184 | verified (emulator); OMT-15 owner |
| G5-PC1 | Recently played / counts recorded only by the screen's MusicPlayerManager listener; a swipe from recents clears the ViewModel while PlaybackService plays on | 227c203 shared-player watch records while no screen listens (one writer at a time) | CI 44200206502 + device 44200234105 (ab/pc1-before) → device 44201835648 both phones; CI 44203248895 running | verified (emulator) |
| G5-PC2 | Home Continue saved only by MainViewModel; same root cause | a96db17 watch saves Continue on the same steps, full track from the process queue | as G5-PC1 (Continue = B → C) | verified (emulator) |
| NBUG-5 (D) | One Dub list mixed language runs; `episodesOf` number sort interleaved them: count inflated, Next Dub 1 → Spanish 1, Sub → Dub could land on Spanish | 546cdbe labelledEpisodes (runs kept together, version-named run first) + caption + count; 957e4b2 pattern moved for RegexWarmup | 44206044268 fail-before → 44211141941 green; device 44209853612 | verified (emulator, api33-lowram) |
| G5-LL | Library Jump back in waits for the cold scan (no cached track list) | none needed | 44211556974: 5,000 songs, 99–180 ms / 162–238 ms | measured (emulator); no fix |

## Test faults

| ID | Cause | Fix | Evidence |
|---|---|---|---|
| EXT-T1-C | Campaign 44162062169 ran plain E11, not the owner's reproduction condition | next campaign seeds five failing drama feeds before opening Source status | 44162062169 test code e11 (no feed setup) |
| EXT-T1-D | e13 counted the ADR-014-held phisher98 donation dialog (hidden, not focusable) as a window over Source status | c6ff72c: WindowSpy.liveUnheld() counts only attached, unheld windows; held ones logged | 44185209665 20/20 fail with focus = MainActivity and Back closing Source status every run; re-run 44189135829 |
| NBUG6-T2 | realDohWithUnreachableBootstrap: DnsOverHttps needs OkHttp.initialize under Robolectric | c6f06ff | CI 44188521804 error text. Observation (no change): a non-I/O exception from a DoH engine leaves SmartDns.encryptedLookup without trying the next provider; in the app the OkHttp startup initializer runs (manifest keeps InitializationProvider), so no evidence of this in production |
| TV-H5-T | h5 on TV api34: the Home rail item holds focus from start-up and the test's semantics tap changes the tab without moving it (a remote can't), so Left stayed in the rail on Home | 9582843: test presses Right into the page first when focus starts on the rail | 44192958569 (head), 44195239132 (77d387a^, 5/5) and 44196925739 (3/3, before LEFT focused=[tv_nav_tab_home] touch=false selected=[music]) fail; api24 44196930392 3/3 PASS (onEnter → Music). Not an ST-18 regression. Second step: 9582843 still failed on api34 (44198639260: Right from the rail Home item while Music was selected by the tap → page 0, a state a remote cannot make); 581026d chooses Music with D-pad Down + centre when focus starts on the rail → tv-api34 44200018532 and tv-api24 44201869204 OK 11 |
## Runs

| Run | What | Result (label) |
|---|---|---|
| 44103206509 | DL-11 p2 fail-before (dl11-fail-before) | ownPauses FAIL as expected (7 restarts, 1.487x); link-dips passed on old code (guard only) (CI unit) |
| 44106540469 | CI on head 226c9c5 (DL-11 fix 4fa126e) | PASS: Dl11PaceTest 6/6, DownloadThroughputTest 6/6, ownPauses 0 restarts, capped MB/s no drop (CI unit) |
| 44112738603 | N6-2 api29 head | AppLockDeviceTest OK 2 (emulator) |
| 44112747705 | N6-2 api29, pre-734e9f6 Privacy | a+b FAIL (old baseline) |
| 44114305239 | N6-2 api29, head minus 3f3fbd8 | b FAIL as expected (emulator) |
| 44110174701 | DL-11 device a + d ×3, both phones | all PASS; real host 1.031–1.120× (median 1.112 / 1.101): ADR-016 budget not met; case d 6/6 (emulator) |
| 44116513171 | head 9262793 both phones: DL a, Bug1Yumix, ResumeAcrossProcess, YtChannelUi | all PASS; protocol=h2 (emulator) |
| 44116803828 | tv-api34 on scratch/tv3-fail-before: TV-3 e + QualityList | e FAIL as expected; QualityList PASS (emulator) |
| 44118950490 / 44121151867 | TvBug5ReproDeviceTest a–e, tv-api34 / tv-api24 | OK 5 both (emulator) |
| 44118957078 | DL a (h2) vs f (HTTP/1.1) vs d ×3 both phones | h2 1.08–1.15×, HTTP/1.1 1.03–1.07×; d 6/6 (emulator) |
| 44121429260 | DL a (F) vs f (h2 control) vs d ×3 both phones, 7a85df6 | F medians 1.057/1.042, max 1.1004; d 6/6 (emulator) |
| 44123412795 | CI caaf148 | green incl. gitleaks (unit) |
| 44128769774 | bench=profile 864cb16 | profile lost Home rules; not committed (finding X2-1) |
| 44133217014 | bench=profile 52155bf | 47,941 rules, committed 743b1c7 |
| 44138627579 | bench=jank 743b1c7 | no gain (software rendering) |
| 44142300486 | bench=startup 743b1c7 | TTID ratio 0.62 api36, 0.71 lowram |
| 44145301927 | ST-17 StabilityStressDeviceTest ×10 both phones, a7b03a0 | 20/20 PASS, compose spins 0, FATAL 0, ANR 0; ~5–6 distinct main-thread stalls 702–1070 ms (each logged twice) (emulator) |
| 44162062169 | EXT-T1 ExtensionsDeviceTest ×10 api33-lowram (5f7f333) | 10/10 PASS, 0 ANR, EXT_T1 line every run: only MainActivity focused, no added window → not reproduced; the owner's condition (five failing drama feeds while Source status is open) was not set up, so this does not count toward the ≥ 20 (emulator) |
| 44067269319 (re-read) | X11/X12 confirmation from the run's JUnit XML | SmartDnsResilienceTest 4/4, SmartDnsTest 13/13, DnsWireClientTest 4/4, DataCacheBoundsTest 4/4; 0 failures, 0 skips; INVENTORY rows already "verified (CI unit)" — confirmed (CI unit) |
| 44180839496 | CI unit at 6ec4868 (DL-11 trip logging + paceTripsAreLoggedOncePerRestart) | green: 1155 tests, 0 failures, 11 skipped; DownloadThroughputTest 7/7 incl. paceTripsAreLoggedOncePerRestart (CI unit) |
| 44185781422 | CI unit at 0bdbb74 (ST-18 AfterFirstFrameTest, NBUG-6 Nbug6ExhaustionTimingTest) | 1 failure = test fault: the Nbug6 real-DoH case used a `.example` host, which DnsOverHttps rejects as private (fixed 4d95c04, re-run 44188521804). AfterFirstFrameTest 2/2, SmartDnsResilienceTest 4/4, DnsWireClientTest 4/4, DataCacheBoundsTest 4/4 (CI unit) |
| 44180844505 | DownloadThroughputDeviceTest a_realHostScreenOn + d_localUnitCollapseProfile ×3, both phones (6ec4868) | PASS 12/12. Real host received ÷ file api33 1.056/1.035/1.090 (median 1.056), api36 1.043/1.039/1.030 (median 1.039): all ≤ 1.10 in this sample; case d 3.8–7.0 s, last 10 % ≤ 1.8 s. Trips: J/K/L all rejected by data (note §8.4); no code change (emulator) |
| 44185180596 | ST-18 after: HomeTraceDeviceTest api33-lowram at 46480b6 (cbad735 deferral) | first attempt: build failed, Maven Central 429 (infrastructure); re-run PASS. Structural: VerifyClass of the deferred hosts in frame 1 = 0 / 0 / 0 ms (moved to frame 3: 216 / 124 / 196 ms). Frame 1 total 4548 / 4386 / 4179 ms, noisier than before (3613 / 1768): no timing gain claimed (emulator api33-lowram, software rendering) |
| 44185209665 | EXT-T1 e13 ×20 api33-lowram (46480b6) | 20/20 FAIL = test fault. The only added window, every run, was `com.phisher98.donation.DonationDialogFragment` (HDhub4u), concealed by the ADR-014 blocker ("held a promotional window … card shown", flags 0x1800018 not focusable/touchable, absent from the screenshot). Focus was MainActivity and Back closed Source status 20/20, so the original symptom did not reproduce. Assertion counted every added window; fixed c6ff72c (count only attached, unheld windows), re-run 44189135829 |
| 44189135829 | EXT-T1 e13 ×20 api33-lowram (c6ff72c) | 20/20 PASS: E13_RESULT PASS every run, live=[] and held=[HDhub4u donation] every run, seeded timeouts 5–7 per run, exit-info 0 ANR (16 force-stops by the harness). Not reproduced → Cannot reproduce, OMT-26 (emulator api33-lowram) |
| 44188152810 | ST-18 step 2: HomeTraceDeviceTest api33-lowram at 77d387a (Home after first frame under cold splash) | PASS. Home VerifyClass slices in frame 1: 5 / 5 / 3 (before ~95 each); 92 now in frame 3. Frame 1 2779 / 1688 / 1868 ms, recompose 920 / 324 / 394 ms, measure 422 / 191 / 206 ms (step 1: 4548 / 4386 / 4179; 749–899; 499–942). Structural pass (emulator api33-lowram); timing not claimed, before-run variance 1768–3613 ms |
| 44188521804 | CI unit at 4d95c04 | 1137 tests, 1 failure = test fault (Nbug6 real DoH: OkHttp not initialised under Robolectric → PublicSuffixDatabase IllegalStateException; fixed c6f06ff). AfterFirstFrameTest 3/3, PlayerBuiltAfterFirstFrameTest 4/4, SmartDnsResilienceTest 4/4, DnsWireClientTest 4/4, DataCacheBoundsTest 4/4; NBUG6_EXHAUST model firstMs=1202 secondMs=0 (CI unit) |
| 44190759388 | ST-18 regression: HomeNavigation, BackNavigation, HeroCarouselLifecycle both phones (f79d9b3) | running |
| 44192309663 | CI unit at c6f06ff | green: 1160 tests, 0 failures, 11 skipped. Nbug6ExhaustionTimingTest 2/2: model firstMs=1201 secondMs=0; real DoH bootstrap 10.255.255.1 firstMs=15091 (5057/5004/5006 per provider, connect timeout) secondMs=0. AfterFirstFrameTest 3/3, SmartDnsResilience 4/4, DnsWire 4/4, DataCacheBounds 4/4 (CI unit) |
| 44192612391 | NBUG-6 Nbug6EngineCostDeviceTest + NBUG-5 DubCountProbeDeviceTest api33-lowram (615bdeb) | PASS 2/2. NBUG6_COST: native 6174 → 9048 / 9331 / 9465 KiB for 1 / 2 / 3 engines, threads 23 → 31 / 36 / 41, 27 after 65 s idle; pool 1 connection for one client, 2 with a second Dns → gate not passed (note §2). NBUG5_PROBE 15 titles: Anichi Dub = card counts; KickassAnime Dub longer than Sub with the same numbers (Blue Lock 24 / 59, other langs such as lang=Spanish in Dub) → second probe (emulator api33-lowram) |
| 44192958569 | TV api34: HomeNavigation, BackNavigation, TvBug5Repro (ded970c) | BackNavigation PASS, TvBug5Repro PASS, HomeNavigation 10/11: h5 "rail entered on [tv_nav_tab_home], not on the selected tab" (page 1 reached). A/B h5 ×5: head 44195234882, 77d387a^ branch ab/st18-before-home 44195239132 |
| 44195239132 | A/B: h5 ×5 TV api34 at 77d387a^ (branch ab/st18-before-home, deleted after) | 5/5 FAIL, same message → failure predates the Home deferral |
| 44195234882 | A/B: h5 ×5 TV api34 at head | build failed, Maven Central 429 (infrastructure); re-run cancelled, replaced by 44196925739 |
| 44196925739 | h5 ×3 TV api34 with focus logging (b00a740) | 3/3 FAIL: before any key focus on tv_nav_tab_home, touch=false, selected Music → test fault TV-H5-T |
| 44196930392 | h5 ×3 TV api24 with focus logging (b00a740) | 3/3 PASS: focus none → LEFT #1 music_source_switch → LEFT #2 tv_nav_tab_music; Down + Centre → page 2 |
| 44198639260 | HomeNavigation (11) TV api34 after 9582843 | h5 FAIL: Right from the rail Home item (Music selected by the tap) → page 0; test fault, fixed 581026d |
| 44198643189 | HomeNavigation (11) TV api24 after 9582843 | OK 11 (emulator) |
| 44200018532 / 44201869204 | HomeNavigation (11) TV api34 / api24 at 581026d / f390272 | OK 11 both; api34 remote path: 1 Down → Music, centre, Right → youtube_song, Left → rail on Music, Down + centre → page 2 (emulator) |
| 44200206502 | CI on ab/pc1-before (G5 tests, no fix) | 1141 tests, 2 failed = the two target tests (PlaysCountedWithoutScreenTest, ContinueSavedWithoutScreenTest) as expected (CI unit) |
| 44200234105 | LibraryEmptyProfileDeviceTest api33-lowram on ab/pc1-before | FAIL as expected: C count 0, Recently played [B, A], Continue B; play time 31.9 s, likes and playlist order correct (emulator) |
| 44201835648 | LibraryEmptyProfileDeviceTest both phones at f390272 | EP_RESULT PASS both: counts 1/1/1, Recently played C,B,A, Continue C, Most played 3; play time 31.6 / 31.5 s (emulator api33-lowram, api36) |
| 44203248895 | CI at f390272 (pass-after G5-PC1/PC2) | green (build, unit, lint, gitleaks) |
| 44203529014 | LibraryEmptyProfileDeviceTest both phones, reopen timing split | PASS both; EP_B open=6693 / continue=6693 / library=6695 ms (api33-lowram), 3667 / 3667 / 3668 ms (api36); 3 songs only |
| 44203720877 | DubCountProbeDeviceTest probe 2 (api33-lowram) | success; signal D found (KickassAnime mixes Dub + Spanish runs in one list; Yumeiro sort interleaved them) |
| 44206044268 | CI on ab/nb5-before (NBUG-5 fail-before, MixedLanguageEpisodesTest) | red as expected: 1144 tests, 2 failed (nextEpisodeStaysInTheSameLanguage, runNamedLikeTheVersionComesFirst); clean-list guard passed |
| 44208688578 | DubCountProbeDeviceTest re-run | my input fault: short class name → ClassNotFoundException; re-run with full name |
| 44208655473 | CI at 546cdbe (NBUG-5 fix) | red: MixedLanguageEpisodesTest passed; RegexGuardTest.everyPatternHolderIsWarmedOffMain failed (new pattern in object OnlineSession) → 957e4b2 |
| 44209853612 | DubCountProbeDeviceTest api33-lowram at f7da100 | success; shown12 starts with the English Dub run for Frieren, Solo Leveling (raw Spanish first), S2, Dandadan, Kaiju, Blue Lock |
| 44211141941 | CI at 957e4b2 | green (build, unit, lint, gitleaks) |
| 44214275251 | Upgrade v1.8.0 → db52f4c (api33-lowram) | UPGRADE_RESULT PASS compare=0 played=1 resumed=1 fatal=0; 0 lost, 1 changed (updater last_check) |
| 44214312408 | RtlLayoutDeviceTest + WidgetUpdatesDeviceTest both phones at db52f4c | OK: RTL 1 test RTL_RESULT PASS, Widget 5 tests, both phones |
| 44215701198 | G6-ST1 fail-before SleepTimerWithoutScreenDeviceTest at 0a47b48 | ST_RESULT FAIL both phones: still playing at 100 s after a 1-minute timer, screen closed |
| 44217196977 | G6-ST1 pass-after + G6-WL fail-before at 3b6e2be, both phones | ST_RESULT PASS 60354 / 60357 ms; WL_RESULT FAIL http=false (no ExoPlayer:WifiLockManager in dumpsys wifi) |
| 44218535243 | G6-WL pass-after at fb359cf, both phones | api36 PASS; api33-lowram FILE2 wifiLock=true with empty "Locks held:" → test check too wide, tightened c4abe23 |
| 44218538378 | CI at fb359cf | green (build, unit incl. MusicWakeModeTest 2/2, lint, gitleaks) |
| 44219681816 | MusicWifiLock + MusicAudioFocus + MusicNotificationControls at c4abe23, both phones | all OK; WL_RESULT PASS, AF_RESULT PASS (noisy not delivered: protected → OMT-28), NC_RESULT PASS |
| 44220997354 | G6-PG1 fail-before CI on ab/pg1-before | red as intended: YmDetailPagingTest 2 of 1149, nothing else |
| 44223304640 | CI at f8564b8 (G6-PG1 fix) | green; YmDetailPagingTest 2/2; SmartDnsResilience, DnsWire, DataCacheBounds 4/4 each |
| 44225572082 | G6-MO1 fail-before CI on ab/mo1-before | red as intended: MusicModeReconnectTest 1 of 1150 ("On device" after reconnect), nothing else |
| 44225724090 | YmPagingNetworkDeviceTest both phones at 3816573 | OK both; PG_RESULT PASS page1=100, offline more failed (UnknownHostException), back → retries=1, 5 pages / 499 rows |
| 44227662448 | CI at 7d1486a (MO1 fix + LY/LF mocked tests) | red only in LrcLibMockedTest 3 of 8 (test isolation: LrcLib.prefs kept across Robolectric apps → 00c4ded); 1166 tests; MusicModeReconnectTest 1/1, Round13LogicTest 13/13, ScrobbleFlushMockedTest 8/8, SmartDns/DnsWire/DataCacheBounds 4/4 each |
| 44211556974 | LargeLibraryScanDeviceTest both phones | OK both; 5,000 songs; scan 180/111/99 ms api33-lowram, 238/181/162 ms api36; insert 135 s / 128 s; cleanup removed 5,000 |
| 44182727961 | ST-18 before: HomeTraceDeviceTest api33-lowram at 03fb2e4 (no deferral) | PASS. Frame 1 (cold) 3613 / 1768 ms; VerifyClass of the hosts deferred in cbad735 inside frame 1: 64 / 15 ms (OnlinePlayerUiKt 28/7, WatchScreensKt 15/3, ReminderHostKt 12/2, BrowserCheckHostKt 7/2); trace 2 missed the cold start (frame 1 299 ms, 23 verifies). Older 43863246577: 53/74/122 ms (emulator) |

## L1 G12 order: items verified in earlier cycles

| Item | Ledger evidence | This cycle |
|---|---|---|
| Upgrade from v1.8.0 (X10-5) | 43891389012 PASS at c306e7e (emulator api33-lowram) | re-run at db52f4c: 44214275251 PASS (0 lost, played, resumed, fatal=0; emulator api33-lowram) |
| MASVS write-up (X7-5) | 460b840 docs/security/MASVS.md | no redo: document review, no security-relevant code change this cycle (NBUG-6 not built) |
| Widget updates (G12-WD) | 7569065 WidgetUpdatesDeviceTest | re-run at db52f4c: 44214312408 OK 5 tests both phones |
| Decoder fault tests (G12-DF) | 24bcc65 DecoderFaultDeviceTest | no redo: player code untouched this cycle |
| Anime4K (G12-A4K) | 7787fde Anime4kGlDeviceTest | no redo: shader/player code untouched |
| RTL (G12-RTL) | ec0c7ae RtlLayoutDeviceTest | re-run at db52f4c: 44214312408 RTL_RESULT PASS both phones |
| X11–X16 | INVENTORY rows 186–189; X11/X12 re-read in CI 44067269319 | no redo; SmartDnsResilience 4/4, DnsWire 4/4, DataCacheBounds 4/4 again green in CI 44192309663 |
| ST-17 | 44145301927 | stays open by order (cause-naming logging per campaign) |

## Spot-check (session 2)

BUG-2 44116513171, NBUG-2b 44118950490, N6-2 44112738603, BUG-4 TV 44116803828, DL-11 F 44121429260: all
hold against the run logs; no status changed.

## Still open

See docs/CHECKLIST-FEATURES-BUGS.md sections A and B.

## Owner-blocked

BUG-3 (= NBUG-1) real phone; BUG-5 TV-4 cross-source search (G7 proposal 3); OMT-1…19; X3-TL; X13-6; OMT-12
cold start; NBUG-4d real GPU (OMT-16); Vega meaning; Telegram/Terabox/Diskwala/manga-translation cloud path.

## Feature phase

Gate not passed. Part 8 not started. Proposals F-01…F-12 written (checklist section C; ADR-017 glass theme and
ADR-018 shorts player are proposed, not accepted). Owner decisions listed in each note §7/§8.

## Handover prompt for the next agent

Continue the eighth-cycle master prompt on siamform/yumeiro r15-stability. Read "Resume here" in
docs/R15-STATUS.md, this report and docs/CHECKLIST-FEATURES-BUGS.md first; the repo is the truth. No PR, merge
or release without the owner. Every compile goes to CI; emulators only on CI runners; one heavy run at a time.
Commit and push after every job with the trailer; update the ledger, INVENTORY, checklist and Resume here in
the same push. Hazards: key-sending tests restore touch mode (FirstRun.touchMode); onshort.net times out
from CI (tests skip, never pass).
