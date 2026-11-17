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

## Test faults

| ID | Cause | Fix | Evidence |
|---|---|---|---|
| EXT-T1-C | Campaign 44162062169 ran plain E11, not the owner's reproduction condition | next campaign seeds five failing drama feeds before opening Source status | 44162062169 test code e11 (no feed setup) |
| EXT-T1-D | e13 counted the ADR-014-held phisher98 donation dialog (hidden, not focusable) as a window over Source status | c6ff72c: WindowSpy.liveUnheld() counts only attached, unheld windows; held ones logged | 44185209665 20/20 fail with focus = MainActivity and Back closing Source status every run; re-run 44189135829 |
| NBUG6-T2 | realDohWithUnreachableBootstrap: DnsOverHttps needs OkHttp.initialize under Robolectric | c6f06ff | CI 44188521804 error text. Observation (no change): a non-I/O exception from a DoH engine leaves SmartDns.encryptedLookup without trying the next provider; in the app the OkHttp startup initializer runs (manifest keeps InitializationProvider), so no evidence of this in production |

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
| 44192309663 | CI unit at c6f06ff | running |
| 44182727961 | ST-18 before: HomeTraceDeviceTest api33-lowram at 03fb2e4 (no deferral) | PASS. Frame 1 (cold) 3613 / 1768 ms; VerifyClass of the hosts deferred in cbad735 inside frame 1: 64 / 15 ms (OnlinePlayerUiKt 28/7, WatchScreensKt 15/3, ReminderHostKt 12/2, BrowserCheckHostKt 7/2); trace 2 missed the cold start (frame 1 299 ms, 23 verifies). Older 43863246577: 53/74/122 ms (emulator) |

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
