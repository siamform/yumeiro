# Branches of siamform/yumeiro: obsolete list (not deleted)

Listed 2026-11-20 from `git ls-remote --heads`. Nothing here was deleted; deletion waits for the owner's explicit go.
"ahead N" = commits on that branch that are not in `r15-stability`.

## Obsolete: fail-before and check branches (42)

Each holds a regression test without its fix (or a one-off compile/check), pushed only to show the test fails before the fix. The run is recorded in the ledger (docs/DONE-LEDGER.md / docs/HANDOVER-FINAL-LANES.md) and the test is in `r15-stability` with its fix, so the branch has no further use.

| Branch | State vs r15-stability | Last commit |
|---|---|---|
| `bug3-fail-before` | ahead 3 | 2026-11-17 |
| `dl11-fail-before` | ahead 1 | 2026-11-17 |
| `dl4-fail-before` | ahead 1 | 2026-11-17 |
| `dl6-fail-before` | ahead 1 | 2026-11-17 |
| `dl8-fail-before` | ahead 1 | 2026-11-17 |
| `g8-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/a5-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/d8-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/device-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/ig1-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/ig2-fail-before` | ahead 6 | 2026-11-17 |
| `scratch/l1-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/lb7-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/lb7tabs-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/lt2-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/lt4-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/mg1-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/mu4-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/n2-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/n3-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/n45-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/n6-fail-before` | ahead 4 | 2026-11-17 |
| `scratch/n62-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/n7-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/st22-fail-before` | ahead 2 | 2026-11-17 |
| `scratch/tv3-fail-before` | ahead 1 | 2026-11-17 |
| `scratch/unit-fail-before` | ahead 5 | 2026-11-17 |
| `scratch/x15-2-fail-before` | ahead 1 | 2026-11-16 |
| `tmp/batch-b-fail-before` | ahead 2 | 2026-11-19 |
| `tmp/batch-c-fail-before` | ahead 2 | 2026-11-19 |
| `tmp/batch-c2-fail-before` | ahead 1 | 2026-11-19 |
| `tmp/batch-c3-fail-before` | contained | 2026-11-19 |
| `tmp/fail-before-part2` | ahead 5 | 2026-11-19 |
| `tmp/final-coverage-compile` | ahead 1 | 2026-11-19 |
| `tmp/final13-fail-before` | ahead 1 | 2026-11-19 |
| `tmp/final16-18-check` | ahead 1 | 2026-11-20 |
| `tmp/final16-20-check` | ahead 1 | 2026-11-20 |
| `tmp/final17-fail-before` | ahead 1 | 2026-11-20 |
| `tmp/final42-fail-before` | ahead 1 | 2026-11-20 |
| `tmp/final7-fail-before` | ahead 1 | 2026-11-19 |
| `tmp/final8-fail-before` | ahead 1 | 2026-11-19 |
| `tmp/sec-lfm-fail-before` | ahead 4 | 2026-11-19 |

`tmp/final42-fail-before` is obsolete: its CI run 44680944481 (fail-before) is recorded in the FINAL-42 row.

## Obsolete: fully contained in r15-stability (2)

Every commit is already in `r15-stability`.

| Branch | State | Last commit |
|---|---|---|
| `chore/hardening` | contained | 2026-11-07 |
| `r12-features` | contained | 2026-11-14 |

## Keep (not obsolete without the owner's review)

These have commits that are not in `r15-stability`, are data branches (`extensions`, `feed`, `streams` are updated by the owner's workflows), are open Dependabot updates, or are `main`.

| Branch | State vs r15-stability | Last commit |
|---|---|---|
| `archive/online-rooms` | ahead 6 | 2026-11-09 |
| `dependabot/gradle/gradle-94f3b546e9` | ahead 2 | 2026-11-08 |
| `dependabot/npm_and_yarn/server/server-5a5353f8a5` | ahead 1 | 2026-11-13 |
| `docs/imagedecoder-wording` | ahead 1 | 2026-11-10 |
| `extensions` | ahead 1 | 2026-11-20 |
| `feat/cloud-alerts` | ahead 6 | 2026-11-09 |
| `feat/lyrics-scrobble-simkl` | ahead 7 | 2026-11-10 |
| `feat/player` | ahead 6 | 2026-11-19 |
| `feat/sources` | ahead 2 | 2026-11-19 |
| `feat/subtitles-downloaders-adshield` | ahead 4 | 2026-11-09 |
| `feed` | ahead 1 | 2026-11-20 |
| `fix/playback-flow-and-ux` | ahead 1 | 2026-11-08 |
| `fix/trakt-secretless` | ahead 2 | 2026-11-09 |
| `market` | ahead 1 | 2026-11-08 |
| `r11-final` | ahead 59 | 2026-11-13 |
| `r13-mockups` | ahead 1 | 2026-11-14 |
| `r13-player` | ahead 16 | 2026-11-14 |
| `r14-round2` | ahead 23 | 2026-11-14 |
| `redesign-final` | ahead 59 | 2026-11-13 |
| `redesign/v6-polish` | ahead 15 | 2026-11-12 |
| `redesign/v7-dream` | ahead 20 | 2026-11-12 |
| `safety/r11b-2026-11-13` | ahead 59 | 2026-11-13 |
| `streams` | ahead 1 | 2026-11-20 |
| `wip/r10-safety` | ahead 29 | 2026-11-12 |
