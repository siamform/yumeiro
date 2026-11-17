# Eighth handover report (Yumeiro r15-stability)

Kept current after every job. The repo is the truth; this file and "Resume here" in docs/R15-STATUS.md must agree.

## State

- Head at start: 2cbb2ae (docs only after 912093b). No run after 44094439021 at start (checked 2026-11-17).
- Current group: 4.1 row 1, DL-11 part 2 (G8, ADR-016).
- Open runs: 44106540469 (CI, DL-11 fix head).

## Findings (eighth cycle)

| ID | Root cause | Fix | Test (fail-before → pass-after) | Label |
|---|---|---|---|---|

## Test faults

| ID | Cause | Fix | Evidence |
|---|---|---|---|

## Runs

| Run | What | Result (label) |
|---|---|---|
| 44103206509 | DL-11 p2 fail-before (dl11-fail-before) | ownPauses FAIL as expected (7 restarts, 1.487x); link-dips passed on old code (guard only) (CI unit) |
| 44106540469 | CI on head 226c9c5 (DL-11 fix 4fa126e) | in progress |

## Still open

See docs/CHECKLIST-FEATURES-BUGS.md sections A and B.

## Owner-blocked

BUG-3 (= NBUG-1) real phone; BUG-5 TV-4 cross-source search (G7 proposal 3); OMT-1…19; X3-TL; X13-6; OMT-12
cold start; NBUG-4d real GPU (OMT-16); Vega meaning; Telegram/Terabox/Diskwala/manga-translation cloud path.

## Feature phase

Gate not passed. Part 8 not started; proposals only (checklist section C).

## Handover prompt for the next agent

Continue the eighth-cycle master prompt on siamform/yumeiro r15-stability. Read "Resume here" in
docs/R15-STATUS.md, this report and docs/CHECKLIST-FEATURES-BUGS.md first; the repo is the truth. No PR, merge
or release without the owner. Every compile goes to CI; emulators only on CI runners; one heavy run at a time.
Commit and push after every job with the trailer; update the ledger, INVENTORY, checklist and Resume here in
the same push. Hazards: key-sending tests restore touch mode (FirstRun.touchMode); onshort.net times out
from CI (tests skip, never pass).
