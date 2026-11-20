# Yumeiro 2.0.0: bug and fix list (read this first)

Written 2026-11-20 after the owner reported that the published **v1.9.0 crashes**. 2.0.0 replaces 1.9.0. This file
is the single list of what broke, why, what is fixed, what is verified and what the next agent must still do.
Trust the repo and run IDs over this text; every "Verified" needs a run ID.

## Status at a glance

| ID | Bug | Severity | Fix commit | Status |
|---|---|---|---|---|
| B-1 / FINAL-49 | v1.9.0 crashes when a video opens: `VerifyError: Verifier rejected class ok6` | Critical, every user | eef90a2 | Fixed. CI 44703556988 green (player 262 → 202 registers); phone upgrade check 44709239365 PASS verify=0 fatal=0; TV 44709249640 verify=0 fatal=0 |
| B-2 / FINAL-50 | "Closed to free memory" crash report for a normal background kill | Medium (looks like a crash) | 574fdc8 | Fixed; unit tests green in CI 44703556988 |
| B-3 / FINAL-50 | Same memory-trim event logged ~15 times a second | Low | 574fdc8 | Fixed; unit tests green in CI 44703556988 |
| B-4 | Dependabot PR #36 failing CI 6/7 (moves Jackson past the owner's 2.18.11 pin) | Low | 2026-11-20 config commit | Closed PR #36; Dependabot ignores Jackson 2.19+ |
| B-5 | README "What's new" images badly placed (two sizes, old screenshots) | Low | README commit | Fixed: one 3-column grid of the real social images |
| B-6 | README / docs changes were on r15-stability, not on main | Low | push with 2.0.0 | Fixed when 2.0.0 is pushed to main |
| B-7 | Upgrade check from v1.8.1 FAIL (run 44700731576): fatal=1, verify=3, resumed=0 | Critical (same as B-1) | eef90a2 | Phone PASS 44709239365. Deferred by owner: TV re-run with the fixed script, 1.9.0→2.0.0 upgrade check |
| B-8 | Thread spike: 171 threads 30 s after start (mali 30, Dispatcher 24, OkHttp 20) | Watch | none | Open: measure on a real phone in 2.0.x |
| B-9 | Built-in Last.fm sign-in off; shared secret must be rotated before it is enabled | Owner action | none | Open (owner): rotate, then `wrangler secret put LASTFM_SHARED_SECRET` |
| B-10 / FREEZE-1 | 2.0.0 freeze on touch ("Input dispatching timed out"), Home doing N² 18+ checks on the main thread | Critical (community "crash on touch") | d072e4d8 | Fail-before CI 44764157290 (AdultApiCostTest failed on old code). Pass-after CI 44764168235 (Build, test & lint success). Not released (goes into 2.1.0) |
| B-11 / PROF-1 | Continue reading / saved manga shared across profiles | High (privacy) | see git log PROF-1 | Fail-before CI 44771329328 (ab/prof1-before), pass-after CI 44771338763: pending |
| B-12 / T-1 | Raw torrent info-hash rejected (Play Link) or dropped (Stremio) | Medium | see git log T-1 | Fail-before CI 44771332825 (ab/t1-before, helper missing), pass-after CI 44771338763: pending |

## B-1: video player VerifyError (the 1.9.0 crash)

Owner report, 2026-11-20 14:29 +06, Pixel 6a, Android 17, app 1.9.0 (17) mobile release:

```
java.lang.VerifyError: Verifier rejected class ok6: void ok6.c(zg6, Function0, Function3, Modifier, zg6, boolean,
Function0, Function0, ComposableLambda, Function1, ComposableLambda, boolean, Function0, Function0, Function0,
Composer, int) failed to verify: [0x22F9] register v0 has type Reference: kotlin.jvm.functions.Function0 but
expected Integer
	at vr2.invoke(SourceFile:1853) ... ComposableLambdaImpl.invoke ... MaterialTheme ... Recomposer
```

Same crash on CI: upgrade check 44700731576 (v1.8.1 → 1.9.0 review APK) logged dex2oat "Verification error" in
`ok6.c` at install (08:18:24 UTC) and `FATAL EXCEPTION: main ... VerifyError` when the clip opened (08:19:24).

- **What `ok6.c` is:** `VideoPlayerModalContent` (Compose trace key "dev.asfakul.yumeiro.ui.screens.video.
  VideoPlayerModalContent (VideoPlayerModal.kt:262)" inside the method). It was one ~2150-line Compose function;
  the player surface and controls sat in inline `Box`/`Column` blocks, so all of it compiled into one dex method of
  **262 registers, 14,911 code units**.
- **Root cause:** R8 (AGP 9.5.1) register allocation in methods above 255 registers. At 0x22F7 it stores the
  remembered `Function0` (BackHandler's lambda) in v0, then at 0x22F9 does `xor-int/lit8 v4, v0, 1` (the
  `!minimized` of `BackHandler(enabled = !minimized)`) on the same register. ART refuses the whole class, so the
  player cannot open. Debug builds (D8, no R8) are fine, which is why every device test on debug builds passed.
- **Released build affected:** yes. The six `classes*.dex` of `release-Yumeiro-v1.9.0.apk` are byte-identical
  (sha256) to the review APK of CI 44691804753 (commit 4c3cda3).
- **History, why it slipped:** the same defect showed up in FINAL-46 (upgrade check 44681019595, class `lk6`,
  255 registers). It was put down to missing build values in that one build because the exact review APK of the time
  passed (44683540047, verify=0). The method then grew with FINAL-47 (Settings reorganisation) and crossed the line
  again. The upgrade check that would have caught it ran **after** the release was published.
- **Fix (eef90a2):** the player surface and its controls are a composable lambda `playerSurface` invoked from
  `VideoPlayerModalContent`, so they compile into their own method. New gate `scripts/dex_register_budget.py`
  runs in `review-apk.yml` and `release.yml` and fails the build if any of our Compose methods needs more than
  224 registers. On the 1.9.0 APK it fails: `262 regs Lok6;->c VideoPlayerModalContent`.
- **Rule for the next agent:** never publish a release before the upgrade check (phone and TV) passed on the exact
  review APK with `verify=0 fatal=0`. When a Compose function grows, split it; do not raise the 224 budget.

## B-2 and B-3: background kill shown as a crash; repeated trim samples

Owner report, 2026-11-20 14:08 +06: "Kind: Closed to free memory · Importance at exit: background / cached · PSS 0 MB
· RSS 54 MB · status 0 · Android kept no trace", sysFree ~1.1 GB, low=false. That is Android closing an app nobody
was using, not a crash.

- **Cause:** `CrashLog.exitKind` wrote a report for every `REASON_LOW_MEMORY` exit and only changed the label for
  cached ones. `MemoryWatch.onTrim` took a full sample (walks `/proc/self/task`) for every `onTrimMemory`; Android 17
  sent `TRIM_MEMORY_BACKGROUND` ~15 times within one second.
- **Fix (574fdc8):** a low-memory kill is a report ("Out of memory") only when the app was foreground, in a
  foreground service (playing / downloading) or visible (picture-in-picture). Trim samples are taken once per level
  per 5 s; repeats are counted on the next sample.
- **Tests:** `KanaAndCachesTest.backgroundMemoryKillIsNotReportedAsACrash`,
  `KanaAndCachesTest.repeatedTrimCallbacksAreSampledOnce`.

## B-8: thread spike (open)

The VerifyError report's trail: 14:28:48 `threads=20`, 14:29:18 `heap=122/256M native=54M threads=171 (mali 30,
Dispatcher 24, OkHttp 20, Binder 10)`. 30 GPU (mali) threads and 24 dispatcher threads 30 s after start is high.
Not a crash cause on its own (the crash is B-1). Next agent: capture `threads=` on a real phone with 2.0.0 after
opening Home, Yumix and one video; if it stays above ~120, find who starts the Dispatcher work (feed / extension
warm-up) and the image decodes (Coil parallelism is 4/8, decoders 2/3 per docs/STABILITY-R15.md).

## Verification required before 2.0.0 is published

1. CI `ci.yml -f review_apk=true` on the 2.0.0 head: unit (phone + TV), lint, gitleaks, review APK, and the new
   "Dex register budget" step all green.
2. Upgrade check phone: `device-check.yml -f upgrade_from=v1.8.1 -f upgrade_apk_run=<that CI run>` →
   `UPGRADE_RESULT PASS ... resumed=1 ... fatal=0 verify=0`.
3. Upgrade check TV: same with `-f tv=true -f tv_api=34` → install Success, `verify=0`, `FATAL 0`, the clip plays.
4. Only then push r15-stability to main (release.yml builds, runs the register budget again and publishes).
5. Owner, on the Pixel 6a: install 2.0.0 over 1.9.0, open one video from Home, one from Downloads, one online
   episode. Mandatory test OMT-61.

## Lessons (process)

- Run the upgrade check on the exact review APK **before** pushing to main, not after.
- A Pass on debug builds says nothing about R8 output; the review / release APK must be exercised on a device.
- Report what the user sees: a crash list entry is a crash to the user, whatever the label says.
