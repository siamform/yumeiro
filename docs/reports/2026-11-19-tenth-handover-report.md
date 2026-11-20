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
| 44583808297 | head CI 9193694 | failed: SettingsSearchCoverageTest "switches missing from search table: [Automatic DNS fallback]" → fix 9dd0213 (NBUG-6) |
| 44587248595 | head CI 9dd0213 | success: unit (1455 tests), lint, TV lint, gitleaks |
| 44592881571 | fail-before c2 (NBUG-6, 4a8f59c) | failed as expected: Nbug6FallbackTest does not compile (no fallback API); cancelled after the unit step to free the runner |
| 44595319629 | fail-before c3 (Vega, 8f652ee) | failed as expected: VegaProviderDeviceTest does not compile against the old code; cancelled after the step |
| 44593053433 / 44593085810 | device chunk_00, phones / tv-api34, passes P1+P2 | 6 of 10 classes pass both passes on api36, api33-lowram, tv-api34. Failures FINAL-1..4 (player touched off the main thread after a refresh; tests waited on the first transient 403; `SavedSites.add("@name")` refused; a non-void JUnit method) → fix 1158c01 |
| 44595993499 / 44595998571 | re-run of the 4 failing classes, phones / tv-api34 | CloudRecapture and TelegramPlayback pass on all three; TelegramPublic fails on tv-api34 (FINAL-5, rows off-screen in the lazy list); MangaTranslate fails (FINAL-6, the model answered "Good afternoon") → fix in the next commit |
| 44597459511 | fail-before SEC-LFM + AUD-ZIP + FINAL-3 (`tmp/sec-lfm-fail-before`) | failed as expected: LastFmCloudSignTest "Unresolved reference 'cloudSig'"; gitleaks passed with the scoped allowlist; cancelled after the unit step |
| 44599561077 | fail-before part 2 (`tmp/fail-before-part2`: same without LastFmCloudSignTest, so ApkSecretsTest, AUD-ZIP and FINAL-3 tests run on the old code) | failed as expected: 1458 tests, 4 failed — SavedSitesTelegramTest.aChannelHandleOrLinkIsSavedAsItsPublicPage, ApkSecretsTest.everySecretLikeFieldIsClassified, ApkSecretsTest.removedTrueSecretsStayOut, UniversalAddFormatsTest.anOversizedManifestInAnArchiveIsNotInflated; cancelled after the unit step |
| 44603218553 | head CI 1b3e644 | cancelled: superseded by FINAL-7 / AUD-UI-1 fixes before the unit step |
| 44600322143 | chunk_00 tv-api24 | 9/10 pass P1/P2; CloudRecapture fails (FINAL-7: Chromium 53 edits the read-only header map) → fix d35614c |
| 44600328162 | chunk_01 phones | 7/7 pass P1/P2 on api33-lowram and api36 |
| 44603655334 | CloudRecapture + chunk_01 tv-api24 | CloudRecapture passes P1/P2 (FINAL-7 pass-after); AnimeSignInMessage P1 fails (FINAL-8 init race) → fix 82c5b93; 3 platform skips (FINAL-9 found they were counted as passes → 1c44b93) |
| 44603660590 | chunk_02 phones | 7/7 pass P1/P2 on api33-lowram and api36 |
| 44604147006 | fail-before FINAL-7 guard + AUD-UI-1 guard (`tmp/final7-fail-before` dd78a56) | failed as expected: 1463 tests, 2 failed — WebViewLoadUrlGuardTest.noDirectLoadUrlWithHeadersInAppSources, TouchTargetTest.smallClickablesReserveFortyEightDp; cancelled after the unit step |
| 44607714314 | fail-before FINAL-8 (`tmp/final8-fail-before` b7cdc1b: old init + test hook only) | failed as expected: 1466 tests, 1 failed — OnlineSettingsInitRaceTest.aTokenWrittenWhileStartUpInitRunsIsKept; cancelled after the unit step |
| 44610265729 | head CI 6f11509 | success: build, unit tests, lint, gitleaks (Dependency review skipped: push event) |
| 44607298962 / 44607305833 | chunk_01 tv-api34 / chunk_02 + AnimeSignInMessage tv-api24 | pass P1/P2 except written platform skips (AniSkip, AppLock b_); AnimeSignInMessage passes on tv-api24 (FINAL-8 pass-after) |
| 44610610454 / 44610615764 | chunk_03 phones / chunk_02 tv-api34 | 7/7 pass P1/P2 on api33-lowram and api36; 7/7 pass P1/P2 on tv-api34 |
| 44598214395 / 44598220085 | re-run TelegramPublic + MangaTranslate, phones / tv-api34 | pass P1 and P2 on api33-lowram, api36, tv-api34 |
| 44613835799 | chunk_04 phones | pass P1/P2 on api33-lowram and api36 |
| 44613955120 | chunk_03 tv-api34 | DialogsDeviceTest P1 fails (FINAL-10: Privacy page not open yet) → fix 1199d74 (compile 44618876153) |
| 44616946466 | chunk_03 tv-api24 | infrastructure failure (Maven download); re-run 44630204932 |
| 44617190495 | chunk_04 tv-api34 | DramaPlaybackDeviceTest P1 timing 10615 ms (FINAL-11: frame-callback wait) → fix 65a88d2 (compile 44621887018) |
| 44617327290 | chunk_05 phones | ExtensionPopups/Extensions fail on api33-lowram: launcher ANR held focus (FINAL-12 → e9a7e89); promo card lost after a displacing message (FINAL-13 → 022a95f) |
| 44623909085 | fail-before FINAL-13 (`tmp/final13-fail-before`) | failed as expected: 1468 tests, 1 failed — PromoCardComesBackTest |
| 44626358644 | head CI 022a95f | success |
| 44623469339 | chunk_06 phones | pass P1/P2 on api33-lowram and api36 |
| 44627424706 | chunk_07 phones | all pass except LargeLibraryScan P1 api33-lowram 4999/5000 (FINAL-14 → 99f9288, compile 44630435344); re-run queued |
| 44620669094 | chunk_04 tv-api24 | adb link hung after DownloadThroughput, cancelled at 110 min with 4 P1 passes (FINAL-15 → 3a965a3: adb_alive checks, bounded pulls); re-run queued |
| 44630204932 | chunk_03 tv-api24 re-run | 7/7 pass P1/P2 (FINAL-10 pass-after on tv-api24) |
| 44640073858 | chunk_08 phones | LocalPlayer k_ hold-for-2x stays 1.0 (FINAL-16 → e74f9f3); LowStorage lowram P1 SQLiteFullException in androidx.work (FINAL-17 → b84b934, DiskFullGuard swallows only SQLiteFullException from androidx.work off the main thread); MangaRetry disk reads on main (FINAL-18 → f22e462); re-run queued |
| 44640840216 | chunk_03 tv-api34 re-run | Dialogs pass P1/P2 (FINAL-10 pass-after); CrashUi P1 tap during scroll (FINAL-19 → 12a0617); re-run queued |
| 44643496869 | check branch `tmp/final16-18-check` | build + device-test compile success; unit 1471 tests, 1 failed: DramaResilienceTest.probeBudgetIsInconclusive DOWN≠UNKNOWN (FINAL-20 → 85ac068) |
| 44646120274 | check branch `tmp/final16-20-check` | success: build, 1472 unit tests incl. probeBudgetIsInconclusiveUnderLoad, lint, gitleaks |
| 44643028088 | chunk_09 phones | MiniPlayer timing assumptions (FINAL-21 → faa431d); MusicEndlessNetwork skipped on CI: YouTube sign-in wall (OMT-29, Blocked with reason); re-run queued |
| 44643868312 | chunk_04 tv-api34 re-run | DramaPlayback P1/P2: player page never composed because FINAL-11's plain poll stopped the Compose test clock (FINAL-22 → 155bbdb); re-run queued |
| 44646789738 | chunk_10 phones | 7/7 pass P1/P2 on api33-lowram and api36 |

Final-test cycle findings so far: SEC-LFM (Last.fm secret in the APK, ADR-048, b57e155), AUD-ZIP (archive manifest read
uncapped, 2e68bf9), FINAL-1..4 (1158c01), FINAL-5/6 (test fixes, device-verified in 44598214395/44598220085). Commit-body timestamps of 2026-11-19 19:15–19:45 were estimated ahead of the clock; the git
commit times are the true ones.

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
