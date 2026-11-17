# Seventh handover: report, results and handover prompt (2026-11-17)

Branch `r15-stability`. No PR, merge, release or publish was made. `archive/online-rooms` was not touched.
Every result is labelled: **unit** (JVM/Robolectric), **CI** (GitHub Actions build + unit + lint),
**emulator** (instrumented test on the CI emulators: api33-lowram = Pixel 3a, 2 GB, 192 MB heap;
api36 = Pixel 6; api28 = minSdk phone image), **real hardware** (a person's phone; none was available).
"Verified" means a test failed on the old code (fail-before run) and passes on the fix.

## Summary

- Order followed: G8 → G11 → G9–G11 leftovers → re-audit G1–4 → final sweep (new Group 13 for
  the rows no group had audited: N1–N9, M1/M2/M6/M8/M9).
- 26 app findings fixed in this handover, each with a root cause, a regression test, a fail-before
  run and an after-fix run (table below). 9 test faults found and fixed (our tests, not the app).
- Milestone: the default device suite passed on both phones (api36 24/24, api33-lowram 24/24,
  emulator), after one emulator crash on the CI runner (FS-INFRA-1) and one live-site timeout (YX-T1).
- Nothing changed in how data is stored except to make it safer (N4-1/N5-1: damaged files are kept,
  never read as "empty" and overwritten). No migration was added; no user data path was removed.
- Part 8 features were not started (gate not passed, owner approval needed).

## App findings fixed (this handover)

| ID | What the user saw | Root cause | Fix | Evidence |
|---|---|---|---|---|
| DL-12 | Downloads page stuttered: disk work on the main thread | list load, save + fsync per tap, folder delete on main | a501d8e | emulator both phones: 44054365230 → 44059862262, 44073375083 |
| IG-1 | Trakt/Simkl sign-in froze the screen | token poll on main | 5a46d98 | emulator: 44049036117 → 44049031398 |
| IG-2/IG-3 | One failing tracker stopped the others; Kitsu/MAL stopped after a month | no isolation; no token refresh | 2747726 | emulator both: 44050574003 → 44050521996, 44051808801 |
| IG-4 | Last.fm upload on main at sign-in | flush on main | 282ce61 | emulator both (same runs) |
| IG-5 | Episode thumbnails lost after a back-out or one offline lookup | lookup tied to the screen | 14a367f | emulator both (same runs) |
| IG-6 | A show vanished from airing alerts for good | a failed lookup cached as "not anime" | 9ecd280 | emulator both: 44052308373 → 44054360131, 44051808801 |
| IG-7 | Subtitle hash match lost | title-search failure dropped the hash hit | dc00672 | emulator both (same runs) |
| IG-8 | A failed re-sign-in said "Signed in" | result not checked | 0997e45 | emulator both (same runs) |
| ER-20 | A site with a failed browser check reopened a hidden WebView for every request | failure never remembered | 065f455 | emulator both: 44057033821 → 44054360131, 44073375083; unit |
| ST-22 | Adult switch and ~15 actions froze while up to 128 MB was deleted | cache clear on main | daab2ab | emulator: 44060561160 → 44062811776, api36 44073375083 |
| N6-1 | App lock shut the user out after the phone's screen lock was removed | prompt can never pass | 734e9f6 | emulator: 44062815556 → 44059862262; api36, api28 |
| N6-3 | Android 9: locked for good with no fingerprint left; PIN-only phones could not turn the lock on | Android 9 BiometricPrompt has no PIN option | 734e9f6 | emulator api28: 44069107057 → 44071192093 |
| N6-4 | Android 7–9: right PIN entered, app stayed locked | return from our own PIN screen re-locked in `onStart` | 3f3fbd8 | emulator api28: 44069102667 → 44071192093 |
| N3-1 | Self-update download froze the UI | folder + DownloadManager work on main | 13e8136 | emulator both: 44062815556 → 44059862262, 44073375083 |
| N4-1/N5-1 | Saved home servers, Stremio add-ons, JS sources, repos could be wiped | damaged file read as empty, then written in place | 595305f | CI unit: 44070875412 → 44062718477 |
| N4-2 | Stremio/Plex/Jellyfin/file-server/Aniyomi/JS items lost their place/history | CloudStream `fixUrl` rewrote bridge ids | 7c69091 | emulator + CI unit |
| N2-1 | Site sign-out left the user signed in | Domain=/Secure cookies kept | 83d7818 | emulator both + CI unit |
| N7-1 | Inbox taps froze; two saves could race | write + fsync on main, no lock | ac13d92 | emulator both: 44062815556 → 44062811776, 44073375083 |
| N8-1 | A link did nothing (common on TV, no browser) | no handler, no message | 5be755e | CI unit: 44070875412 → 44062718477 |
| N1-1/N1-2 | Watch Together host stopped publishing; wrong room code said nothing | player already bound; error dropped | 0040d98 | CI unit (same runs) |
| M9-1 | Music Mix search typed slowly | device-song match on main per keystroke | 0dce4ae | CI unit: 44070875412 → 44067269319 |
| M2-1 | After switching YouTube account, Music Home still showed the old account | API cache + on-screen store not keyed by account | cf4d650, f3d1d38 | CI unit (same runs) |
| M1-1 | Cold start ran 2–3 full library scans in parallel | no join of a running scan | 841b085 | CI unit (same runs); LibraryPersist emulator both |

Also verified earlier in this handover (see ledger): G8 "Downloads fixes", BUG-3 case d 6/6
(emulator; BUG-3 stays open for the owner's phone), D3/D5/D6, SE-1, LB/MU/LT rows.

## Test faults fixed (no app change)

MINI-T1 (three swipe/tap target faults), LP-T1 (AppLock keys left the display out of touch mode, so a
later touch Back was a remote Back), YX-T1 (a real site timeout counted as the old race; now retried,
and "every page timed out" is reported as skipped, never as a pass), AppLock api28 (read the lock
state, not the gate node, while Android 9's PIN screen is on top), ST-22 helper (StrictMode restored in
the same message: the old fail-before was void), two androidTest compile errors I introduced
(4eb6245, b345cab; the runs they broke are recorded as void), DownloadsScreen/UniversalAdd scroll
faults (3e6b7de, e71a041).

## Runs (this segment)

| Run | Kind | Result |
|---|---|---|
| 44062718477, 44067269319 | CI | success (unit, lint, gitleaks) |
| 44070875412 | CI fail-before (`scratch/unit-fail-before`) | failed exactly on the expected tests; controls pass |
| 44073375083 | emulator api36, 11 new classes | 10/11 PASS; LocalPlayer#g → LP-T1 |
| 44077732900 | emulator api36, AppLock → LocalPlayer | 12/12 PASS (LP-T1 verified) |
| 44079680572 | milestone suite, both phones | api36 23/24 (Yumix → YX-T1); api33-lowram 14/14 ran, then emulator offline (FS-INFRA-1) |
| 44089151495 | emulator api33-lowram, the 10 that never ran | 10/10 PASS |
| 44089166780 | emulator api36 Yumix | inconclusive: onshort.net timed out on every page |
| 44091851873 | emulator api36 Yumix | 3/3 PASS (See all 77 → 435 titles, 0 errors) |
| 44088190688, 44088205006 | — | void: did not build (my missing import) |
| 44094439021 | H8 bench=restore | 1/1 PASS api36 (25.3 s) and api33-lowram (30.6 s), emulator; H8 verified for the open tab |

## Still open (not claimed fixed)

- **BUG-3** download speed collapse: stays open until the owner confirms on a phone (case d PASS on emulator).
- **BUG-5 / TV-4** cross-source search on TV: owner decision (G7 proposal 3).
- **ST-7** jank on software-rendered emulators; real-phone check OMT-12. **ST-17** stays open (no upstream fix, no symptom fix).
- **N6-2** suspected (Android 10 credential screen re-lock): no API 29 CI image; same guard as N6-4 would apply once reproduced.
- **DL-11** part 2 (2–36 % over-read) measured, ADR-016 proposed; **X2-1** open; X11/X12 rows say "CI pending" in INVENTORY: CI has been green since, but I did not read their XML by name, so they are not upgraded.
- **Owner tests** (real hardware, accounts, home network): `docs/OWNER-MANUAL-TESTS.md` OMT-1…OMT-19, X3-TL, X13-6.

## Gated feature phase (needs the owner's approval; nothing started)

Telegram, Terabox/Diskwala, WebView sites, Nuvio/Skystream/Vega, volume booster, external player,
Upcoming, poster Watch Later/Favorite, glassmorphism, manga translation, Language/Sub/Dub badges,
vertical player. ui-ux-pro-max and an ADR come first for any redesign item.

## Handover prompt for the next agent

```
You are continuing Yumeiro on branch r15-stability (repo siamform/yumeiro). Read, in order:
docs/R15-STATUS.md "Resume here", docs/DONE-LEDGER.md (last 40 rows), this report, then
docs/groups/GROUP-13-NEW-GROUPS.md. The master resume handover (seventh) is the order in force.
Rules: no PR/merge/release without the owner; one heavy run at a time, at most two device
workflows; smallest test on one phone first, full suite on both only at a milestone; every compile
goes to CI (no JDK in the sandbox); commit + push after each job with cause/fix/tests/evidence/
ledger ID and the trailer; update the ledger and Resume here with every push; secrets only in the
git-ignored .env/secrets/, never printed. Label every result unit / CI / emulator / real hardware.
Next: H8 is done (44094439021 PASS on both phones, emulator). The gated feature list goes to
the owner for approval (Part 8 stays closed until then). Open items are listed in the report's
"Still open" section; do not mark BUG-1…4 closed, BUG-3 needs the owner's phone.
Known test hazards: key-sending tests must restore touch mode (FirstRun.touchMode, LP-T1);
onshort.net often times out from CI runners (tests skip, never pass, when it does).
```

## Addendum (after the report was first pushed)

- H8: 44094439021 (912093b) ProcessDeathRestoreTest 1/1 PASS on api36 and api33-lowram (emulator). The open tab survives a real process death (new PID). Scroll position across a process death is not asserted.
- X11/X12: unit XML read by name in CI 44067269319: SmartDnsResilienceTest 4/4, DnsWireClientTest 4/4, DataCacheBoundsTest 4/4 (CI unit). INVENTORY set to verified (CI unit).
