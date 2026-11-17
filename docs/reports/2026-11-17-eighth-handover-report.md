# Eighth handover report (Yumeiro r15-stability)

Kept current after every job. The repo is the truth; this file and "Resume here" in docs/R15-STATUS.md must agree.

## State

- Head at start: 2cbb2ae (docs only after 912093b). No run after 44094439021 at start (checked 2026-11-17).
- Current group: 4.1. Done for agent scope: N6-2, BUG-1, BUG-2, BUG-4, TV-3. DL-11 p2 nearly met (option F).
  X2-1 emulator part done (profile 743b1c7, jank no gain, startup measured). 4.3 register done or Blocked: owner.
  ST-17 campaign 44145301927 read (20/20, 0 spins, stays open). Group 13 closed for agent work. 4.4 proposals
  F-01…F-12 written (ADR-017, ADR-018 proposed); gate not passed, nothing built.
  Next: EXT-T1 (≥20 runs on api33-lowram), DL-11 next sample, then the 4.4 gate check.
- Open runs: 44162062169 (EXT-T1, ExtensionsDeviceTest ×10, api33-lowram).

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
| 44162062169 | EXT-T1 ExtensionsDeviceTest ×10 api33-lowram | in progress |

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
