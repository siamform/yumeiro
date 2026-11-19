# Tenth handover report: seven items, Worker fix, feature smoke (2026-11-19 → 2026-11-20)

Branch `r15-stability`. No PR, merge, release or Worker deploy has been done; they wait for the owner's "go".
Labels: `unit` (JVM/Robolectric in CI), `CI`, `emulator` (GitHub-hosted Android emulator, API named),
`TV emulator` (Android TV image, API named), `real hardware`. Nothing in this report was run on real hardware.

## 1. Correction to the prompt

The prompt said the seven items were to be built "before building" anything else, as if none existed. The repo
showed 21 commits already on the branch (071da2d … 899c195) that built F-02, F-03, F-11, F-AIR and NBUG-13 with
tests. Per "trust the repo", those were kept, checked and completed rather than rebuilt. NBUG-6 and Vega step 4
were new this cycle.

## 2. The seven items

| Item | Decision record | Code | Tests (fail-before → pass-after) | Device | Owner test |
|---|---|---|---|---|---|
| F-02 Telegram, no login | ADR-042 | 071da2d, 4cbe3cd, 4788a1c | batch A fail-before 44566567570 | see section 4 | OMT-49 |
| F-03 TeraBox / DiskWala | ADR-042 | a0c7b3b | batch A 44566567570 | see section 4 | OMT-50 |
| F-11 Manga translation | ADR-044 | 2f93b1d, 67b3dee, 95e3f91, 7afefba, 4788a1c | batch B 44572968675 / 44575868202 | see section 4 | OMT-51 |
| F-AIR airing push + Worker | ADR-043 | 45abcd2 (Worker), 07e475f | Worker 33/35 before → 35/35 after; app batch B 44572961369 / 44575863230 | see section 4 | OMT-52 |
| NBUG-13 proxy | ADR-045 | 89f4d1d, c815e99, 6bb420c | batch C 44573626244 | see section 4 | OMT-53 |
| NBUG-6 DNS fallback (F1 sequential, F2 rejected) | ADR-046 | 61e6eb9 | test 0fba73b; fail-before branch tmp/batch-c2-fail-before | see section 4 | OMT-47 |
| Vega step 4 | ADR-047 | 9193694 | test 8f652ee; fail-before branch tmp/batch-c3-fail-before | see section 4 | OMT-48 |

Competitors, feature profiles, action-reaction tables, side-effect analysis and terms checks are in each ADR and
in docs/proposals/. Hard limits held: nothing bypasses DRM, logins, paywalls or access controls (private Telegram
channels and login-only cloud links say so and stop; Vega streams that need DRM keys are refused).

## 3. Cloudflare Worker (missed notifications, ADR-043)

Root cause: the airing cron only sent wakes to UnifiedPush endpoints; Firebase phones never got one, and their
follows were never cleaned up. Fix 45abcd2; Worker tests 35/35 (33/35 before the fix, the two new cases failing).
Production D1 lacks migrations 0003–0005. `scripts/deploy-worker.sh` is a dry run by default; with `--go` it
applies the migrations and deploys. Not deployed.

Deploy steps after the owner's go:
1. `scripts/deploy-worker.sh` (dry run, read the plan).
2. `scripts/deploy-worker.sh --go` (credentials from the git-ignored `.env`).
3. Check `/health` and one test wake to a Firebase phone (OMT-52).

## 4. Runs

CI and device runs, in order (one heavy run at a time, at most two device workflows):

| Run | What | Result |
|---|---|---|
| 44573633005 | head CI | failed (RegexWarmup on main thread) → fix 4788a1c |
| 44578908005 | head CI 4788a1c | cancelled: "Lint (TV app)" hung 10 h after unit tests passed |
| 44583808297 | head CI 9193694 | (pending) |

## 5. Feature smoke (section 10)

Covered by existing device classes plus the new `FeatureSmokeDeviceTest` (4ed327b: a Live TV channel from a
playlist and a Web URL's video reach their first frame on a local server). Results in section 4.

## 6. Skills (section 13)

| Skill | Version | Applied to | Note |
|---|---|---|---|
| ui-ux-pro-max-skill | 2de1134 | ScriptReposTab | labels on icon buttons, progress feedback |
| kotlin-agent-skills | 087f568 | — | only tooling-migration skills; not applicable to these items |
| reverse-engineer-anything (rea) | 2837de5 | Vega format reading | read real repositories (Zenda-Cross/vega-providers 24054d4484) as evidence, not guesses |
| claude-skills architecture-designer | 67c1961 | ADR-047 | diagram, trade-offs, failure modes |
| claude-skills kotlin-specialist | 67c1961 | Vega and NBUG-6 code | `!!` uses justified; no runBlocking in production code |

No applicable skill was skipped.

## 7. For the owner

The single "go" covers: merge to main and publish signed 1.9.0, deploy the Worker, and any rollout beyond the
agent's own smoke test. Owner tests OMT-35…OMT-53 stay Pending and do not block development.
