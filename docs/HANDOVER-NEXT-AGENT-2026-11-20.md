# Handover for the next agent (2026-11-20, after v2.0.0)

Read this first, then `docs/MAJOR-BUGS-AND-FEATURES-2.x.md` (the must-do list) and `docs/BUGS-AND-FIXES-2.0.0.md`.
Trust the repo and run IDs over any summary, including this one.

## Owner decision 2026-11-20 (latest)

No 2.0.1. Fix all the other bugs first, then release **2.1.0**. Work list: MAJOR-BUGS-AND-FEATURES-2.x.md.
Done so far for 2.1.0: FREEZE-1 (d072e4d8, CI 44764168235 green), PROF-1 (B-11), T-1 (B-12); CI for the last two in
BUGS-AND-FIXES-2.0.0.md.

## Where things stand

- **v2.0.0 is published** (release.yml run 44710765718, tag v2.0.0). main and r15-stability were both at e47f450f
  at release time. Release dex = tested review dex (CI 44703556988).
- **Open P0: FREEZE-1** (2.0.0 freeze on touch, Xiaomi API 33, report on branch `crash-log`,
  file `freeze-1795185111722.txt`). Fix commit **d072e4d8** on r15-stability (not on main, not released).
  - Fail-before: branch `ab/freeze1-before` (afcaadf7 = e47f450f + test only), CI **44764157290** = failure,
    `AdultApiCostTest > manyProvidersAreCheckedInOnePass FAILED` (AdultApiCostTest.kt:49), 1484 tests, 1 failed.
  - Pass-after: CI **44764168235** on d072e4d8 with `review_apk=true` was still running when this was written.
    Check: `gh run view 44764168235 --json conclusion`. If green, record it in BUGS-AND-FIXES-2.0.0.md B-10 and
    MASTER ledger, then real-phone check, then release 2.0.1 (bump version.properties to 2.0.1 / 19, CHANGELOG).
  - If red: read `gh run view 44764168235 --log-failed`. Likely suspects: compile of `providerStamp()` (uses
    `SourceHub.state`, `APIHolder.allProviders`, `plugins`), or `adultLoaded` assignment in HomeScreen.
- Deferred by owner (not run): TV upgrade re-check with fixed scripts/upgrade-check.sh; 1.9.0 → 2.0.0 upgrade check.
- Owner action: rotate Last.fm shared secret before enabling built-in Last.fm sign-in (B-9).
- New Dependabot branches exist (actions, gradle, npm server). Gradle one CI 44711769570 completed; check it
  does not move Jackson past 2.18.11 (phone) / 2.13.5 (TV). `.github/dependabot.yml` ignores jackson 2.19+.

## FREEZE-1: what it was and how it was found (method to reuse)

1. Report: "Input dispatching timed out … Waited 8001ms for MotionEvent(action=DOWN)" = ANR, not crash. Main
   thread `utm=96515` (~965 s CPU) stuck in `bg1.h ← yx7.d ← s22.f` (obfuscated). 171 threads (same as B-8).
2. No mapping.txt is uploaded by release.yml. Map names from the release APK itself with androguard:
   download `release-Yumeiro-v2.0.0.apk` (`gh release download v2.0.0`), load each `classes*.dex` with
   `androguard.core.dex.DEX`, find the classes/methods from the stack, print their `const-string`s (Compose
   trace strings contain the source file + line, e.g. "HomeScreen (HomeScreen.kt:268)") and invoked methods.
   Result: s22.f = HomeScreen, yx7.d = AdultContent.loadedAdultApis, bg1.h = ExtensionManager.extensionOf.
3. Root cause: `AdultContent.isAdultApi(name)` called `ExtensionManager.provider()` → rebuilds the full provider
   list (`providers()`: filter installed, plugin files, APIHolder, SourceHub, distinctBy, ProviderOverrides.apply)
   per call; screens call it once per provider inside `remember` on the main thread → N² per recomposition, and
   Home's Most-played `remember` called `loadedAdultApis()` (N × plugins) on the main thread whenever its keys
   changed (PlayCounts, recentVideos, engine.generation).
4. Fix (d072e4d8): `online/StampMemo.kt` (value kept until stamp changes); `ExtensionManager.providerStamp()`
   (engine state + SourceHub state by identity, APIHolder size, plugins size) and `extensionLookup()` (one pass);
   `AdultContent.isAdultApi` = cached map lookup; HomeScreen computes the map on IO into `adultLoaded` state
   (unknown plays stay hidden = fail closed, NBUG-11 rule kept).
5. Test: `app/src/test/java/dev/asfakul/yumeiro/online/AdultApiCostTest.kt` (Robolectric): 200 fake providers
   via `SourceHub.publishVideo`, counts `name` reads, must be < 10N (old code ~1.5N² = 60 000); and a new provider
   published later must be seen (stamp invalidation).

Still to do around it (see MAJOR list): FREEZE-2 (one provider StateFlow built off the main thread, used by
OnlineHomeScreen / OnlineSearchScreen / OnlineHomePicks / HomeOnline), FREEZE-3 (thread cap, B-8),
FREEZE-4 (find what keeps Home recomposing during Live TV playback), GATE-1 (ANR device test).

Update 2026-11-20 (later): FREEZE-2 fixed (B-13). FREEZE-4 now has a draw counter (B-21,
`diagnostics/DrawCounter.kt`): each 30 s memory sample says `draws home=N yumix=N live=N`, and Freeze
(ANR) reports carry that trail. Ask the owner for one freeze report from a 2.1.0 build, read the
`draws` lines just before the freeze; the screen with hundreds of draws per 30 s is the one to fix.
Note: PlaybackState position ticks are already split out of the app-wide state (MainViewModel), so
music position is not the cause. F-19 security questions (B-19) and per-profile pins (B-20) are done;
extensions / repositories per profile are still shared (Epic).

## How work is done in this repo (rules from the owner)

- Author: `Asfakul Siam <71690568+siamform@users.noreply.github.com>`; every commit ends with
  `Co-authored-by: Perplexity <noreply@perplexity.ai>` once. Commit body: Cause / Fix / Tests / Evidence /
  Timestamp / Ledger. One logical change per commit. Push after every job. No force-push.
- No JDK locally: compile and test only through CI: `gh workflow run ci.yml --ref <branch> [-f review_apk=true]`.
  Unit tests ~10 min, full run ~40 min.
- Fail-before proof: push the test alone on a branch from the old commit (`ab/<id>-before`), run CI, record the
  failing run ID; then the fix on r15-stability with the pass run ID.
- Device checks: `device-check.yml` / `upgrade-check.yml`; at most two device workflows at once. Cancelled runs
  go in docs/evidence/final/CANCELLED-RUNS.md. No scheduled workflows, no API polling loops, no secrets in logs.
- Ledger cells: Pass needs a run ID; otherwise Fail (finding ID) / Blocked (reason) / Not run (reason). No "Partial".
- Owner decisions not to reopen: P1 only; REPEAT=1; Jackson 2.18.11 phone / 2.13.5 TV; NBUG-6 sequential DNS
  fallback; Vega/Nuvio/SkyStream one registry; Telegram has no login (owner's new list asks for login: ask first).
- Release: release.yml runs on pushes to main touching app/**; docs-only pushes to main do not release.
  Gates in release: `scripts/dex_register_budget.py` (limit 224 registers; FINAL-49 VerifyError guard).
- Credentials: /tmp/sec.env (re-extract from the session's turn_0001.md if lost). Never print or commit them;
  gitleaks must stay green.
- README must keep the "made in Bangladesh" line. Do not make images for features that are queued or broken.

## Files to know

| File | What |
|---|---|
| docs/MAJOR-BUGS-AND-FEATURES-2.x.md | Item-by-item audit of the owner's master list + Now / Next / Later |
| docs/BUGS-AND-FIXES-2.0.0.md | B-1…B-10 bug and fix list |
| docs/MASTER-CHECKLIST-FINAL.md | Feature ledger with run IDs |
| docs/CHECKLIST-FEATURES-BUGS.md | Older per-ID ledger (BUG-, NBUG-, F-, NF-) with evidence |
| docs/OWNER-MANUAL-TESTS.md | Real-phone tests for the owner (mandatory list first) |
| docs/R15-STATUS.md | "Resume here" |
| scripts/upgrade-check.sh, scripts/dex_register_budget.py | Upgrade harness, register gate |

## Next steps in order

1. Confirm CI 44764168235; write B-10 run IDs; owner real-phone check (Live TV playing, touch Home, YouTube).
2. Keep fixing the MAJOR list; release 2.1.0 (version.properties 2.1.0 / 19, CHANGELOG, WhatsNew.shouldPost must accept 2.1.x) when the owner says.
3. FREEZE-2/3/4, GATE-1, then PROF-1 (manga progress per profile) and S-1 (search debounce).
4. Then the Next list in MAJOR-BUGS-AND-FEATURES-2.x.md.

## Update 2026-11-21: GATE-1 (main-thread stall gate)

What the runs showed (rows B-23, B-25, B-26 in docs/BUGS-AND-FIXES-2.0.0.md):

- Compose-rule gate `MainThreadStallDeviceTest` (300 fake providers): 44782628451 2742/2884 ms (api36/api33),
  2.0.0 code (ab/gate1-before) 44782633791 4347/4602 ms, 44784827103 2707/1712 ms, 44786829781 1651/1741 ms,
  44789029823 3414/804 ms. Control walk without providers (`MainThreadStallControlDeviceTest`): 638/1160 ms
  (44786829781) and 860/829 ms (44789029823). Limit 700 ms, never raised.
- Stack samples (log tag YuGate1, `STALL_SAMPLE`): the long waits are inside
  `androidx.compose.ui.test.AbstractMainTestClock.advanceTimeByFrame`: the Compose test rule runs the app's frames
  from the test thread, several in one main-thread message. That is the harness, not what a user sees.
- Real finding B-26: a starter extension (com.phisher98, `EnmaDecryptor.startInit`) creates a WebView on the main
  thread when it loads (1.3 s on api33-lowram) and opens `DonationDialogFragment`. Starters install on every first
  run, so every new user pays it. Owner decision: leave it out of the starter picks, or delay extension loading.
  Gate tests now mark starters done so third-party code is not measured.
- Real finding B-25 (fixed, YumixSearchOnlyTest): search-only extensions counted as "loading" for ever and the
  search-only card measured every name in one unbounded Text.
- New gate on the real frame clock: `GateOnePrepareDeviceTest` then `MainThreadStallRealClockDeviceTest`
  (am start, no Compose rule, real `input tap`, same 700 ms limit). Run:
  `gh workflow run device-check.yml --ref r15-stability -f test=dev.asfakul.yumeiro.GateOnePrepareDeviceTest,dev.asfakul.yumeiro.MainThreadStallRealClockDeviceTest`
  Data is kept between classes when REPEAT=1 (scripts/device-check.sh clears only for N>1), which the pair relies on.
