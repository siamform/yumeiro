# Ninth handover report: full run stopped, seven items added, Worker fix (2026-11-19)

Order: master prompt "STOP THE SLOW RUN, ADD THE MISSING ITEMS, FIX THE SERVER, THEN ONE FINAL RUN"
(received 16:00 UTC). It replaces the release-gate plan of the eighth report where they conflict.

## 1. Full-suite run: stopped at 16:05 UTC

- Hourly check-in automation deleted. Queue driver `/tmp/gate/run.sh` stopped. The queued rerun 44548234760
  (chunk_01 tv-api24, not yet started) was cancelled.
- One group was mid-run: chunk_02 on tv-api24 (44544937378, started 14:33 UTC). It is allowed to finish, so the
  log is clean. Its result goes into device-runs.md when it ends.
- App code is unchanged since e53ba21d. Every later commit touches only `app/src/androidTest`
  (`git diff --stat e53ba21d..e444ac90`: 8 test files).

Groups that passed in full on gate commits 35333952 / 77d01696 (kept, not re-run):

| Group | api33-lowram + api36 | tv-api24 | tv-api34 |
|---|---|---|---|
| chunk_00 (10 classes) | 44533443975 PASS 20/20 | 44533481697 PASS 10/10 | 44535500399 PASS 10/10 |
| chunk_01 (10 classes) | 44535791174 PASS 20/20 | open: see fault 6 | 44542099349 PASS 10/10 |
| chunk_02 (10 classes) | 44540083087 PASS 20/20 | 44544937378 running | 44545354920 PASS 10/10 |

Chunks 03–12 were not started. They run once, in the final run (order section 7).

## 2. TV problems found by the run: test faults, not app bugs

None of these needed an app change. Each is the test meeting a TV case that it did not handle.

| # | Test | What happened | Fix (androidTest only) | Recheck |
|---|---|---|---|---|
| 1 | AniSkipDeviceTest | tv-api34 is on emulated Ethernet; `svc wifi/data` cannot take it offline (44523673246) | f354883d: the skip covers every TV, only when the network stayed up | 44526618412 (skipped as designed); phones assert, 44530260936 OK |
| 2 | AccessibilityDeviceTest | TouchTargetSizeCheck measured chips under the open TV rail (3 dp / 43 dp); the TV app has no touch input | 87d80b99 + 42359aae: only that check is off, only in the TV edition; every other check still runs | tv-api34 44530205063 OK (10 tests); phones 44530260936 OK (full checks) |
| 3 | Player tests (Accessibility, LocalPlayer, DecoderFault, FloatingMessage, ResumeAcrossProcess) | The clip was opened from the off-screen Library copy, and a touch landed on the TV rail ("first frame never showed") | 87d80b99: `FirstRun.openClip` picks the Videos grid; TV clicks through the D-pad path, phones keep the real touch | 44530205063 videoPlayer PASS |
| 4 | LocalPlayerDeviceTest | The TV player has no fullscreen button (by design), and the TV image has no Cast sender (the app hides Cast) | 35333952: fullscreen case is phone-only; on TV the Cast case still checks that loading does not block, and skips only the load | 44530205063 OK (5 tests, 2 assumption skips logged) |
| 5 | CrashUiDeviceTest | Waited for a pinned Settings shortcut that the TV rail drops on purpose (44537810500, 44539228724) | 77d01696: on TV it maps to the rail's own Settings | tv-api34 44542099349 PASS 10/10 |
| 6 | CrashUiDeviceTest | On tv-api24 one Back did not close the system share sheet (44542850496) | e444ac90: Back up to three times, then the same assert | Built, not tested: the rerun was cancelled by this order; it runs in the final run |

## 3. Jackson: which condition, what it costs, recommendation

- **Contradicted condition.** It is the owner's condition 3 on X13-1 (2026-11-16): when the TV build was moved to
  Jackson 2.13.5, the phone build was to keep its own version (2.19.4). X13-6 moved the phone to 2.18.11 instead.
- **Two builds, two versions.** `app/build.gradle.kts` forces 2.13.5 on every `tv*` classpath and 2.18.11 only on
  the phone classpaths. The Android 7 TV never receives 2.18.11. `JacksonTvCompatTest` fails CI if the TV leaves
  the SDK-24 line (green in CI 44530200549).
- **Cost of 2.18.11 compared with 2.13.5:**
  - API floor: 2.18 needs Android 8 (SDK 26), because 2.14 and later call `ExceptionUtil.isFatal`. The phone's
    minSdk is 28, so this costs nothing. 2.13.5 runs on Android 7.
  - Known vulnerabilities (OSV.dev, 2026-11-16, ADR-015 and ADR-035): 2.18.11 has 0; 2.13.5 has 14
    (3 jackson-core, 11 jackson-databind); 2.19.4 (the old phone version) has 16.
  - Android 7 TV behaviour: not affected. The TV keeps 2.13.5 (X13-1, proven on tv-api24 in run 43768054941: 0 `NoClassDefFoundError`, Drama home loads). Today's
    ExtensionCompatDeviceTest on tv-api24 is in chunk_02 (44544937378, still running); its result is added here.
- **Recommendation: keep 2.18.11.** It removes 16 known advisories from phones, stays below the 2.20
  `invokeExact` limit and does not touch the TV. Per the owner's instruction it stays as it is, and nothing was
  reverted. ExtensionCompatDeviceTest (real extensions) passed today on 2.18.11 on both phones (chunk_02 44540083087,
  20/20). X13-6 moves to Verified (emulator) in the ledger; the owner check stays OMT-43.

## 4. New plan

1. Full run stopped (done).
2. Add the seven deferred items in three batches, each under the full protocol:
   - A: F-02 Telegram and F-03 TeraBox / DiskWala (network and link resolution).
   - B: F-11 manga translation and episode-airing push (media and background work), together with the Worker fix.
   - C: NBUG-6 per-feature DNS over HTTPS, the proxy option and Vega step 4 (network and source providers).
     For Vega, the owner is asked what "Vega" means before anything is designed.
3. Cloudflare Worker: find the cause of the missed airing alerts with evidence, fix and test locally, prepare the
   deploy. No deploy without the owner's go.
4. One final run: 13 groups on both phones and both TVs, once, after every item has its own evidence.
5. Release gate 1.9.0, then the owner's go (merge and publish, Worker deploy, rollout).

Queue size at this report: UNVERIFIED-QUEUE has 113 rows (36 Queued, 23 Pending, 12 Blocked: owner,
11 Built, not tested, 4 Not in this release, the rest Verified or headers).
