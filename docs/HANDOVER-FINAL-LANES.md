# Handover: final test lanes and release preparation (1.9.0 release candidate)

For the next agent. Written 2026-11-20 during the owner's 3-hour release window. Trust the repo over this
file: `git log`, `docs/evidence/final/*.txt` and `docs/MASTER-CHECKLIST-FINAL.md` are the truth.

## Rules that still hold

- The master prompts in force: tenth-cycle handover and "THE FINAL TEST AND RELEASE PREPARATION". Owner's
  2026-11-20 decisions: P1 only (one pass, fresh install state), REPEAT=1, run as many device lanes in parallel as
  needed to finish in time. Fast does not mean partial: every feature row must end Pass, or Fail/Blocked/skip
  with a written reason and a finding ID. Never weaken an assertion to go green.
- No merge to `main`, no store publish, no Worker deploy, no push to siamform/yumeiro-web, no branch deletion
  until the owner says "go" (one go covers all of them, §12).
- Secrets only from the git-ignored `.env` and `secrets/`. Never print them, never commit them.
- No local JDK/SDK, no local emulator: compile and test through CI (`ci.yml`, `device-check.yml`).
- Every fix: root cause, regression test with fail-before run and pass-after run, commit body with Cause / Fix /
  Tests / Evidence / Timestamp / Ledger, then push. Commits end with
  `Co-authored-by: Perplexity <noreply@perplexity.ai>`; the owner stays author.
- Call the build "the release candidate for 1.9.0", never "stable".

## How the device lanes work

- Chunks: `docs/evidence/final/PLAN.md` lists 20 chunks (`chunk_00` … `chunk_19`) of androidTest classes.
  Kinds: `phone` (api33-lowram + api36 in one run), `tv34` (tv-api34), `tv24` (tv-api24).
- Worker: `scripts/final_lanes/worker.sh <lane> <kind> [adopt_run kind chunk]`. It takes the first `kind:chunk`
  line from the queue file `/tmp/queue.txt` (lock `/tmp/queue.lock`), dispatches
  `device-check.yml --ref r15-stability -f test=<classes> -f passes=true -f once=true -f lane=<lane>` plus the
  target flags (dispatch lock `/tmp/dispatch.lock`), waits for the run, then runs
  `scripts/final_collect.sh <run>` which writes `docs/evidence/final/<run>.txt` and appends a line to
  `/tmp/lanes.log` (`<run> <kind> <chunk> done <conclusion> bad=<n> <time>`).
- Start a lane: `(set -m; nohup bash scripts/final_lanes/worker.sh P1 phone > /tmp/P1.out 2>&1 &)`.
  Lanes used: P1, P2 (phone), T1–T3 (tv34), V1–V4 (tv24). `once=true` means one pass (P1);
  `lane` only separates the concurrency groups.
- The `gh` token in the sandbox can expire after 1–2 h: a worker then logs an empty run id. Restart that lane.
- Queue snapshot at writing time: `scripts/final_lanes/queue-snapshot.txt`; lane log copy:
  `docs/evidence/final/lanes.log`.

## After a run finishes

1. `grep -v " PASS .*skipped=0" docs/evidence/final/<run>.txt` lists everything that is not a clean pass.
2. `gh run download <run> -R siamform/yumeiro -D /tmp/x<run>`; failures are in
   `*/stress/inst-dev.asfakul.yumeiro.<Class>.txt` (`stack=` lines; the `test=` line before `stack=` names the
   failing method). App logs: `*/logcat.txt` (tags `YuGesture`, `YuLocalPlayer`, `YuExtTest`, `YuPermScript`,
   `YuStall`, `TestRunner`).
3. Fix, commit, push, then put the chunk back at the top of the queue:
   `flock /tmp/queue.lock bash -c 'sed -i "1i phone:08" /tmp/queue.txt'`.
4. `python3 scripts/final_checklist.py` regenerates docs/MASTER-CHECKLIST-FINAL.md from all evidence files
   (latest run per class/device wins). Commit evidence + checklist together (ledger FINAL-RUNS).

## Findings of this window (all pushed on r15-stability)

| ID | What | Fix | Fail-before | Pass-after |
|---|---|---|---|---|
| FINAL-16c | Hold-for-2x test froze the Compose test clock (SystemClock.sleep), so the long-press timeout never fired in the test | 59a4fcf `stepComposeClock()` | 44659095131 | phone 08 re-run |
| FINAL-23 | PermissionFlow left notifications off; next class saw Android's permission dialog | 5474bf3 device-check.sh restores permissions | 44659067541 | phone 12 re-run |
| FINAL-24 | Library tests inserted songs without DATA on Android 7 (MediaProvider NPE); no WRITE_EXTERNAL_STORAGE after pm clear | 5cf0679, 5474bf3 | 44659196962 | tv24 07 re-run |
| FINAL-25 | PiP tests expected a PiP button on Android 7; the app offers PiP from Android 8 by design | FINAL-25 commit | 44659224203 | tv24 08 re-run |
| e13 tv24 | `ExtensionsDeviceTest.e13` got 4 of 5 seeded drama-feed failures on tv-api24 (4 requests, then the check ended) | under investigation; re-run queued (tv24 05) | 44658520448 | — |
| FINAL-24c | Music tests inserted songs without DATA on Android 7 (four more classes) | 92e03d7 LegacyMedia.path() | 44660785480 | 44662157055 |
| FINAL-27 | Player-settings tests tapped the clip in Home's "Jump back in" instead of Videos → Device | 03df604 | 44660692917 | 44663931586 (phone 13) |
| FINAL-28 / 28b | Resume test: an extension's "has a message" card took the tap; closing it showed the next waiting card | d1567eb, FINAL-28b commit | 44660840151, 44663619588 | phone 14 re-run |
| FINAL-23b / 29 | PlaybackSession started before the tabs (permission dialog after pm clear); notification wait too short on a busy low-RAM boot | 8007648 | 44661962064 | phone 12 re-run |
| FINAL-30 | App: tv-api24 AC-3 rendition DECODER_INIT_FAILED (HLS audio group mapped to the platform renderer). ADR-050 | 9d339b8 | 44662563096 | 44664839512 (tv24 13) |
| FINAL-31 | TvBug5Repro forced Layout=TV on portrait phones (first run on phones); now assumes a landscape window there | b975bb4 | 44662669318 | phone 17 re-run |
| FINAL-32 | TV: Continue row below Spaces not composed (scroll to it); rail Home tag; QualityList greyed (unplayable) rows on tv-api24 | 87ea3a2 | 44662863280, 44662890080 | tv24 14 / tv34 14 re-runs |
| FINAL-33 | YmPagingNetwork network drop impossible with TV Ethernet (written reason, as NetworkState) | adb0526 | 44664324623, 44664431589 | — (skip) |
| FINAL-34 | TV tab taps hit off-screen phone-bar twins; SurfaceMode start race; Messages pill race; TV full-screen keyboard over a form | 1a22844 | 44660329194, 44662525331, 44663118888, 44663218206 | tv24 09/15/16, phone 16 re-runs |
| FINAL-35 (open, not reproduced) | StabilityStress on tv-api24: Java heap 61 → 479 MB in 90 s after "tab back: page 2 → Home", Compose never idle, ANR at the next broadcast; in the same window onshort.net answered 503 to drama feed paging (dramawave page 2, netshort) | no root cause (no heap dump); 44666637483 on 1a22844 passed 3/3, peak heap 94 MB, 0 ANR; second re-run 44670841174 on d91f052; Known imperfect + OMT-57 | 44663218206 | 44666637483 |
| YT-WALL | YtChannelUi (tv-api24) watch page: YouTube "Sign in to confirm you're not a bot" (SignInConfirmNotBotException) on the CI network | Blocked: external (OMT-29) | 44664527460 | — |
| FINAL-36 | FinalCoverage a_h9 expected EXPLORED after Back; the app records SKIPPED (EXPLORED only on finishing the tour) | 2d19cc1 | 44665104133, 44665616397 | phone 19 re-run |
| FINAL-37 | RtlLayout on TV: the rail is mirrored to the right half in RTL | ffbfb90 | 44663558900 | tv34 15 re-run |
| FINAL-38 | RetryActions e: the Kitsu card is below the fold of the landscape TV window; the unscrolled click hit nothing | aeb37d5 | 44666731015, 44667327410 | tv24 15 / tv34 15 re-runs |
| FINAL-39 / 39b | App (TV): the filter sheet's search field took first focus and focus alone opened the full-screen keyboard; Back closed only the keyboard. 39 (099689d) set showKeyboardOnFocus on the legacy field, which ignores it; 39b (d91f052) uses the TextFieldState field. ADR-051. Also PermissionFlow on TV: Android keeps asking after every deny, the app's "Tap again" is right; the test follows shouldShowRequestPermissionRationale | 099689d, d91f052 | 44662694767, 44666813073 | tv34 12 / tv24 12 re-runs |
| FINAL-40 | PlaybackSession waited for phone-bar tags on TV | a5bc4da | 44662694767, 44666813073, 44667417908 | tv34 12 / tv24 12 re-runs |
| FINAL-41 | NotifySettingsEffect: the app's own DiscoveryWorker took the notification pacer's slot during the case (pacer right); the class now cancels that work first | a5bc4da | 44666813073 | tv34 12 re-run |
| INFRA | tv34 19 run 44667182892: Gradle configuration-cache write error in "Build APKs" after 22 s (no test ran); re-dispatched as 44668014172 (pass) | — | — | 44668014172 |
| FINAL-42 | App: RetryActions c (tv-api34, 44669673771) crashed the process: the Tachiyomi "add repository" job posted its error from a worker thread; the message host collected on that thread, a Compose frame ran off main (CalledFromWrongThreadException), the slot table broke and destroy crashed (SlotWriter.moveSlotGapTo). AppMessages now changes state on the main thread only | df4ba9d | 44669673771; unit fail-before on tmp/final42-fail-before | head CI 44672545816; tv34 re-run |
| FINAL-43 (closed: not reproduced; Blocked step) | PermissionFlow b on tv-api34: no "Notifications" message within 10 s after the second "Don't allow" in 3/3 runs (result=6, rationale true). On 4d125b3 (YuPerm/PERM_SHOWN logs, and the FINAL-42 main-thread fix) the message "Notifications not allowed. Tap again when you want to allow it" showed after every one of 5 denials; the system dialog kept coming back, so the "Open settings" step cannot occur on this TV (Blocked, OMT-58). Cause of the 3 earlier misses not proven | diagnostics 4d125b3 | 44662694767, 44666813073, 44669616027; after 44672818277 | owner OMT-58 |
| FINAL-44 (fixed, test fault) | tv-api34 chunk_09 (first run on that device): MiniPlayer e_swipe_right stayed on mini_t3 (the right swipe starts at 10 % of the row, under the TV rail that opens to 248 dp over the page; the left swipe starting at 90 % worked); MusicAccess b/c "no Music tab" (the test waited for the phone bar tag nav_tab_music; TV has tv_nav_tab_music). App behaviour not involved | test-only: swipe kept right of the rail edge, nav via FirstRun.navTag/clickTag | fail-before 44671880872 | tv34 [MiniPlayer, MusicAccess] re-run |
| FINAL-35 (3rd run) | StabilityStress on tv-api24, d91f052: peak Java heap 95/512 MB, 0 ANR (44670841174). Still not reproduced; OMT-57 stays | — | — | 44670841174 |
| TV-FP-1 | `FeedPositionDeviceTest` live/youtube on tv-api24 taps the phone tab (known test fault, same on old code 44438504785) | test rewrite queued (stability-v2) | 44658520448 | — |

## What is left for the release (in order)

1. Let the queue drain; fix every failure as above.
2. Head CI with the review APK on the final commit:
   `gh workflow run ci.yml -R siamform/yumeiro --ref r15-stability -f review_apk=true`; artifact
   `yumeiro-review-apk` holds `yumeiro-1.9.0-<hash>-review.apk`, the TV APK, `SHA256SUMS.txt`, `INTEGRITY.txt`
   (signature against `.github/release-signing-cert.sha256`, not debuggable, 4 ABIs, R8 mapping).
3. APK secret scan: unzip the APK and search its bytes for each secret value from `.env` and the Firebase
   private key id, printing only "found / not found" per key name.
4. Virus scan (VirusTotal key, if any, from `.env`) and record the result in RELEASE-SUMMARY §9.
5. Fill docs/RELEASE-SUMMARY-FINAL.md (what changed / tested / queued / imperfect, §9 table, review APK run,
   hash, SHA-256), docs/R15-STATUS.md "Resume here", CHANGELOG.
6. Ask the owner for "go". Then: merge to main, release, Worker deploy (needed for SEC-LFM Last.fm signing and
   F-AIR), website push (`docs/website/yumeiro-web-v1.9.0.patch`), delete the 41 scratch branches listed in
   `docs/evidence/final/branches-to-delete-2026-11-20.txt` (fail-before evidence branches; their CI runs stay).

## Done in this window

- gitleaks 8.28 over the full history (`--log-opts=--all`, 1154 commits): no leaks. Last.fm shared secret,
  Cloudflare token and Firebase private key id: in no commit (`git log --all -S`, values not printed).
- Images: docs/assets/final/ (14 screenshots + changelog card, README maps each to its test and run).
- CODE-AUDIT-FINAL: performance and stability rows filled.
