# Remaining work, bugs and owner decisions, 2026-11-19 (cross-check before the release gate)

One list of everything that is not `Verified` yet. It was checked against CHECKLIST-FEATURES-BUGS, UNVERIFIED-QUEUE,
OWNER-MANUAL-TESTS, PROGRAM-2026-11-19, docs/groups/ and ADR-037…ADR-041. Every owner decision that was waiting
has been taken by the agent with the evidence written down (owner order in PROGRAM-2026-11-19: "do not ask the owner").
Status words: Not started · Built, not tested · Tested, failing · Verified (emulator / TV emulator / CI) ·
Owner/community test (pending). "Not in this release" means the flag is off or nothing ships, with its reason.

## 1. Built in this cycle, waiting for device pass-after or the gate

| ID | What | Status | Evidence so far | Still needed |
|---|---|---|---|---|
| P-2X | Hold for 2x, long-press speed button | Built, not tested | unit HoldSpeedTest; fail-before CI 44506927082 (compile) | head CI; owner OMT-46 |
| TH-1 | Accent tones (Ao blue, Sakura, Matcha, Fuji, Gold) | Built, not tested | unit AccentToneTest; fail-before 44506927082 | head CI; ProfilesDeviceTest on phones + TVs |
| PR-1 + PR-1-SEC | Profiles with locks; 10 review fixes (ADR-041) | Built, not tested | unit ProfilesTest, ProfilesGuardTest; fail-before 44506927082, 44506931345 | head CI; ProfilesDeviceTest; OMT-44 |
| DL-H | Private downloads vault | Built, not tested | unit PrivateDownloadsTest; fail-before 44506927082 | head CI; ProfilesDeviceTest; OMT-45 |
| G8-TV7A | Website-video Download on TV / landscape | Built, not tested | fail-before tv-api34 44504287444 (FAIL as expected) | pass-after tv-api34 StreamWatchDownloadDeviceTest |
| F-09 | Watch Later / Favorite on posters (TV focus fix) | Built, not tested | phones api36 44502098420 PASS, api33-lowram 44504293129 PASS, tv-api24 44502103283 PASS | tv-api34 pass-after |
| F-04, F-10, F-12, F-17, TV-4 | ReleaseFeaturesDeviceTest | Built, not tested | phones 44500142144 PASS (5/5 both), tv-api34 44500146795 PASS, tv-api24 44502103283 PASS | gate run on the final commit |
| SRC-1, NF-2b | JS time limit, stream downloads | Built, not tested | phones 44500142144: JsTimeLimit, StreamWatchDownload, DownloadG8 PASS | gate run |
| F-13, F-14, F-15 | Offline manga, Live TV failover, Now/Next guide | Built, not tested | unit; fail-before 44501139483 (F-13), 44500775830 (F-14/F-15) | head CI; gate; OMT-11 |
| G7-L5-A/B/D, G8-CQ-1, G9-CQ-1 | Small queue fixes (ADR-040) | Built, not tested | fail-before 44501616817 (L5); G9-CQ-1 has no JVM fail-before (process death) | head CI; gate |
| SPD-1…4 | Fast setup / feed / extensions / catalog | Built, not tested | fail-before 44499065629 | gate; OMT-3 on a 2 GB phone |
| NOTIFY-1 | Notices reach phones whose topic subscription failed | Server Verified (CI 44499921888); app Built, not tested | fail-before 44499918126 | **deploy after the owner's "go"** (section 4) |
| X13-6 | Phone Jackson 2.18.11 | Built, not tested | fail-before 44494085935 | extension suite on both phones; OMT-43. Conflicts with the owner's condition 3 (kept: patched line, ADR-035) |
| F-01/F-16, F-05 step 1, F-06, F-07, F-08, NF-1, NB11-OB1, NB14-OB1, NB15D-OB1, SRC-2 | earlier batches | Built, not tested | fail-before runs in CHECKLIST | gate run; owner tests OMT-35…OMT-42 |

## 2. Done since the last queue update (moved out of UNVERIFIED-QUEUE)

| ID | Result | Evidence |
|---|---|---|
| NBUG-10 / G15-1 | Verified (emulator) | phones 44487432724 (Yumix, PinnedExtensions PASS) |
| TV-YT-1 / G15-2 | Verified (TV emulator) | fail-before tv-api34 44477069801 (cards=2) → pass-after 44480266808 PASS |
| TV-A11Y-1 / G15-3 | Verified (TV emulator) | fail-before 44477069801 (problems=11) → 44480266808 PASS |
| G7-L5-A, G7-L5-B, G7-L5-D, G8-CQ-1, G9-CQ-1, F-13, F-14, F-15 | Built, not tested (see section 1) | fixed; queue rows closed as "built" |

## 3. Still open, with the decision taken

| ID | What | Decision and reason (pros / cons) | Status |
|---|---|---|---|
| G7-L5-C | Manga home / reader read a 1-key prefs file on the main thread | Keep. Moving it off main makes the page show "no source" for a frame and then switch, and can start a feed load for the wrong source (a wasted network call on 2 GB phones). The read is under 1 ms after the first open. Pro: no visible flash. Con: one StrictMode note stays. | Not in this release (stability-v2) |
| G8-CQ-2 | 32-bit download ids could collide | Needs a data migration of every user's download list; no report. Risk of a migration bug is larger than the risk of a collision (about 1 in 4 billion per pair). | Not in this release |
| G8-CQ-3 / CQ-4 / WEBM7 | DASH segment in memory; only the first EXT-X-MAP; WebM join on Android 7–8 | No OOM or report; rare formats; ADR-022 keeps the platform muxer. | Not in this release |
| TV-FP-1, TV-PIN-1, TV-YTC-1 | Three device tests send phone gestures on the tv-api24 image | Test faults, not app faults (same failure on old code, 44438504785). The TV paths are covered by HomeNavigation / SheetFocusTv and OMT-4. | Not in this release (test rewrite in stability-v2) |
| ER-28-TV34, NB15D-B | Fix shown working, not shown needed, on one image | fail-before impossible on that image by design | Verified on the other images; kept for the record |
| NB12-5b, NB16-6, NB16-7 | Version switch retry; held-back update message; 6 h update gate | Unit-tested; no device hook for "6 h later" or a dangerous fixture extension | Owner/community test (pending) |
| G6-PG2, G7-GAP-1, G7-GAP-2, G7-AU-PIP | Music offline scroll, Live TV stall, Live TV / Manga with remote, PiP from Live TV | YouTube bot wall and no test channel on CI IPs | Owner/community test (pending): OMT-4, OMT-11, OMT-29 |
| ST-7, ST-17, X2-1 | Jank, spin stress, baseline profile gain | Emulators render in software | Owner/community test (pending): OMT-12 |
| EXT-T1 | Back closes a pop-up instead of Source status | Can't reproduce on emulator | Owner/community test (pending): OMT-26 |
| BUG-3, NBUG-4d, NBUG-8, NBUG-9, NBUG-12 | Real network / GPU / extension cases | Fixed or guarded in code with tests; need the owner's network or phone | Owner/community test (pending): OMT-14/20, 16, 34, 31, 32 |
| L4, L5 | Line-by-line re-audit of Groups 1–5, 12, 13; final sweep | Release-scope gap named in the notes | stability-v2 |

## 4. Owner decisions: all taken (agent, with evidence)

| Was waiting on the owner | Decision | Where |
|---|---|---|
| OMT-21 TV search scope | Option B: "Search online for …" one explicit step; no per-keystroke network | ADR-036 |
| OMT-23 Jackson version | Pinned 2.18.11 (patched line). Note: conflicts with the owner's earlier condition 3; the owner can revert with one commit | ADR-035 |
| OMT-24 "Vega" | The Vega app's provider repositories (not Amazon Vega OS); step 4 not in this release | ADR-037, F-05 |
| OMT-25 Telegram / TeraBox / DiskWala / manga cloud | Not in this release: TDLib size and per-user login (F-02); logged-in path needs account cookies (F-03); OCR models (F-11) | ADR-037 |
| OMT-27 NBUG-6 per-feature DNS | Not in this release: measured side effects; automatic DNS bypass already ships | ADR-037 |
| OMT-33 NBUG-13 network option | Option 3: system VPN / Private DNS help text + DNS bypass for Yumeiro's own connections | ADR-037 |
| Privacy & Stealth request | Built: PR-1, DL-H, TH-1, P-2X with the limits written down | ADR-041 |
| Episode airing through FCM | Not in this release: needs follows uploaded to the server (privacy); the app's own check stays | ADR-039 |
| Server deploy (NOTIFY-1, guards) | Ready; runs only after the owner's "go": `cd server && npm run db:migrate:guards && npm run db:migrate:want && npx wrangler deploy` | ADR-039 |
| OMT-13, OMT-22 consoles | Only the owner can do these (Firebase key restriction, Test Lab role) | OWNER-MANUAL-TESTS |

## 5. Not in this release (flag off or nothing shipped)

F-02 Telegram, F-03 login path, F-11 manga translation, NBUG-6 per-feature DNS, NBUG-13 proxy, Vega step 4,
FCM episode airing, G7-L5-C, G8-CQ-2…4, G8-WEBM7. Reasons in section 3 and ADR-037.
