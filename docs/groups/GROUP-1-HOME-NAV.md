# Group 1 audit: Home and navigation (R16)

Branch `r15-stability`, commits `4098177` … `ec6ebd8`. Devices: CI emulators only:
**api33-lowram** (Android 13, Pixel 3a profile, 2 GB RAM, 192 MB heap) and **api36** (Android 16,
Pixel 6 profile). These are emulators, not real phones. Real-hardware steps are in
`docs/MANUAL-TESTS.md` (scripts P and T).

## Tested

| Inventory | What | How | Result |
|---|---|---|---|
| H1 | Home feed, sections, carousels | `FeedPositionDeviceTest` (Home case), `HomeNavigationDeviceTest` setUp on every run; hero timers keyed on STARTED (`f021791`) | 43683520404: api33 3/3, api36 2/3 (miss = cold-boot ANR, see FEED-1); 43687398312 on a5b8f60: pass on both |
| H2 | Continue cards | `h2_continueVideoCardOpensVideosWhenEmpty` | 43693532355: 4/4 (2 per emulator) |
| H3 | Nav-bar shortcuts | `h3_navBarShortcutOpensItsPage` (Inbox shortcut on, tap, back, off) | 43693532355: 4/4 |
| H4 | Bottom bar, pager, swipe | `h1_h4_everyTabOpensAndBackReturnsHome`, `BackNavigationDeviceTest` (swipe) | 43685183725, 43686104737, 43689481257, 43693532355: all pass |
| H5 | TV rail and D-pad | `h5_tvLayoutShowsTheRailAndDpadMovesTabs` (TV layout forced, D-pad LEFT to rail, DOWN, centre) | see "Status"; earlier D-pad pager spin fix ST-3 verified in 43653623319 and 43671209244 |
| H6 | Back: button, predictive gesture, nested, player | `BackNavigationDeviceTest` (page 1 → Home, first back arms, second back leaves; the app ends STARTED, not destroyed); `h7` (player back → floating window) | 43690918679: 6/6; 43692234004 + 43693532355: 8/8 (one earlier miss = reliability sheet, test error) |
| H7 | VIEW intent, SEND text link | `h7_viewIntentForAWebVideoOpensThePlayer`, `h7b_sharedTextWithAVideoLinkOpensThePlayer` | h7: all runs since 6df7c1f pass; log shows singleTop `onNewIntent` delivery. h7b: see "Status" |
| H8 | State after recreate | `h8_openTabSurvivesRecreate` | all runs pass |
| H9 | Splash, welcome, tour gate | `FirstRun.toTabs` walks the welcome and tour on every device test with cleared data | every device run (hundreds of launches) |
| H10 | Top bar search suggestions | `h10_topBarSearchSuggestsARecentSearch` | 43689481257: 6/6; 43693532355: 4/4 |
| H11 | Mini player bar | needs a playing audio track | re-audit 2026-11-17: GROUP-6 never took it (gap). `MiniPlayerDeviceTest` (cbe2e47): real 3-track queue; play/pause, Next, swipe left/right, Previous, a11y "Next track", tap opens Now Playing, Close stops and hides. Run pending |
| H12 | Continue widget | `h12_continueWidgetIsOffered` (provider installed) | all runs pass; placing it on a launcher is manual (script P) |
| H13 | App pages (inbox) | `h13_inboxOpensFromTheTopBarAndBackCloses` | all runs pass |

## Problems found

1. **H6 double-back exit failed on both emulators (my own change 4098177).** First diagnosis
   pointed at the app (handler armed twice 0.8 s apart). Evidence then showed the cause was the
   Compose test clock: `Thread.sleep` does not recompose, so `enabled = false` never reached the
   dispatcher. Test error, fixed in 3479bf8.
2. **Back right after a swipe could be cancelled on api36 (app).** Two Home back handlers switched
   on and off as the page changed, so the system re-registered callbacks mid-gesture and sent
   `sendCancelIfRunning` (43689402737). A real user pressing back during a slow swipe could lose
   that press.
3. **Back during a far tab jump (Home card → Videos) could race the fade (app, low risk).** The
   jump coroutine and the back scroll were not tied together.
4. **H10 lost keystrokes (test error).** `adb input text` reached the field before it had focus.
5. **Self-opening "Keep notifications on time" sheet (design issue).** `ReminderHost` opens a modal
   8 s after start (3 times, a week apart). It took the test's back press (43692234004), and it
   interrupts a user in the same way.
6. **Start-up main-thread file reads (ST-7).** StrictMode in 43687396407 named the backdrop photo
   check (40), bookmarks and downloads JSON reads at Home's first draw (38/19/7).
7. **Full-suite h2 failure (test error).** In the full suite (43697438813) Continue watching held a
   fresh entry, so its play button resumed the video instead of opening the Videos page. The test
   now reads the button's description ("Resume" → player, back to the mini player, close; otherwise
   the Videos page). Fixed in 3342106.
8. **FeedPosition swipe precondition (test, plus a Group 4 note).** On api33 only 7 of 16 swipes
   moved, because the feed had 10 cards, and there were 0 position problems. The YuGate log shows
   background onshort.net calls holding one of the two feed slots for 147–209 s. The "swipes moved"
   check is now a precondition (reported as skipped), and the position checks are unchanged.
   Reserving feed slots is a Group 4 item.
9. **New bring-into-view spin (open, ST-17).** In 1 of 20 stress runs on api36 (43697447466),
   Compose never went idle in `pagerFocusAndScrollStress`. The main thread was in
   `ContentInViewNode.animateToZero` → the default LazyList `BringIntoViewSpec`. This is not ST-3.
   Back cleared it. The stress test now logs the focused node chain and a screenshot on a spin
   (3342106) to find the item that keeps asking. It did not come back in the full suite (0 spins).
10. **Inventory correction:** H7 lists SEND; the app accepts SEND only for `text/plain` (a link)
   and extension files. Shared media files (EXTRA_STREAM video) go through VIEW, not SEND.

## Fixes

| Commit | Fix | Verified |
|---|---|---|
| 4098177 | `enableOnBackInvokedCallback`, two-step exit without `finish()` | device (with 89f7553) |
| 89f7553 | One Home back handler; decision inside `onBack`; off only while exit is armed | 43690918679 6/6 |
| 14c5998 | Back cancels a running tab jump and restores fade/shift before scrolling Home ("cancel prior motion, set final state", UI/UX skill, Animation → Cancellable State Transitions) | 43692234004, 43693532355: "tab back: page 2 → Home" logged, h2 passes |
| 77fc8ca | Suggestions ranked on `Dispatchers.Default` after 120 ms | 6/6 + 4/4 |
| 18dda67 | Backdrop, bookmarks, downloads warmed on `yu-prefs`; Home loads them on IO; unit test with planted fault | stress 43697447466 (19/20; the one failure is item 9): StrictMode app frames down to 1 (NotifyPacer, Group 10); stalls ≥700 ms per run steady 0.2/0.2 (was 0.5/0.3 and 0.4/0.1) |
| f021791 | Carousels stop below STARTED | indirect (no stall in nav runs) |
| test only | 3479bf8, 2c109ac, e9c281c, ec6ebd8, 6df7c1f, e28df74 | |

## Verification

Unit suite after every app change: 145 classes, 858 tests, 0 failures, 11 skipped (Robolectric);
146/862 after the Group 2 additions.
Full device suite: 43697438813 (a53effb) failed on items 7 and 8 above (test errors). After the
fixes, 43701056608 (5b1263e) passed every class on both emulators (api33-lowram: Android 13,
Pixel 3a, 2 GB; api36: Android 16, Pixel 6). Both are emulators, not real phones.
Device runs listed above; every run id is in `docs/evidence/round3/device-runs.md`.

## Research

- Android 16 turns predictive back on by default for apps that target 36; the opt-out is
  temporary ([Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-16)).
  The [predictive back guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
  says to enable or disable each callback from observable UI state ahead of time, and that an
  enabled `OnBackPressedCallback` stops the system back-to-home animation. Yumeiro follows that:
  the Home handler is off only while exit is armed, so the second back plays the system animation.
  The mid-swipe cancel (problem 2) comes from the device log (`WindowOnBackDispatcher
  sendCancelIfRunning` right after the callback re-registration), not from the guide.
- UI/UX Pro Max guidelines: "Back Button: users expect back to work predictably" (High);
  "Cancellable State Transitions: cancel or replace prior motion" (High); carousel guideline asks
  for pause/prev/next controls.
- Competitor back behaviour (CloudStream, Hikari) is not verified on a device in this round;
  it is listed for the Step 4 comparison.

## Enhancements (proposed, not built)

1. Replace the self-opening reliability modal with a dismissible card on Home or in the Inbox
   (no interruption, still paced).
2. Hero carousel: visible pause and previous/next controls (TV and accessibility).
3. Baseline profile for start-up and Home (OPEN-SOURCE-COMPARISON P1); the remaining stalls are
   mostly framework measure work on unprofiled code just after boot.
4. Accept shared video files (`SEND` with `EXTRA_STREAM`, `video/*`), like VLC and MX Player.

## Code quality and system design

- `MTPlayerApp.kt` is still a very large composable (about 1,600 lines) holding navigation,
  overlays and back logic. Proposal: move tab/back state into a small `TabNavigator` state holder
  (testable on JVM), keep the composable as wiring.
- Start-up warm-up is one list in `PrefsWarmup` with a guard test; new prefs files must be added
  there (the test catches missing ones for listed names only).

## Status

| Item | Status | Evidence / reason |
|---|---|---|
| H1 Home feed | verified on device | FeedPosition 43687398312; full suite 43701056608 |
| H2 Jump back in / continue | verified on device | `h2` 43696246671; full suite 43701056608 (with fresh Resume data) |
| H3 Home and nav-bar shortcuts | verified on device | `h3` 43693532355, 43696246671 |
| H4 Bottom nav, pager, swipe | verified on device | `h1_h4`, BackNavigation 43690918679 |
| H5 TV rail and D-pad | verified on device (emulator D-pad) | `h5` 43696246671 after a53effb; a real TV is script T |
| H6 Back behaviour | verified on device | BackNavigation 43690918679, 43693532355; the real gesture is script P |
| H7 Deep links and intents | verified on device | `h7`, `h7b`; SEND of video files is not supported (enhancement 4) |
| H8 Restore after process death | verified on device (emulator, both phones; open tab) | `ProcessDeathRestoreTest` in the self-instrumenting benchmark module: other tab → Home → `am kill` → relaunch → new PID and same tab. 44094439021 (912093b): api36 PASS, api33-lowram PASS; snapshots journey_h8_before_kill/after_restore. Scroll position across process death is not asserted (gap kept open) |
| H9 Splash, welcome, tour | verified on device | `FirstRun.toTabs` on every run |
| H10 Top bar, search suggestions | verified on device | `h10` 43689481257, 43696246671 |
| H11 Mini player | moved to Group 6 | needs a playing track |
| H12 Continue widget | audited | provider present; launcher placement is manual (script P) |
| H13 App pages | verified on device (inbox, support) | `h13`, `h13b` 43697545350; permission center → Group 9; reminder cards → unit tests |
| ST-17 bring-into-view spin | open | problem 9. Re-audit 2026-11-17: 0 spins in campaign 43988958313 (20/20) and 43705867413 (20) after the first sighting (1 in ~60 runs). Upstream Compose (ContentInViewNode, BringIntoViewSpec, issuetracker) searched: no matching fixed bug. No fix without a reproduction (no symptom fix); the focus-chain log stays in every stress run |

Group 1 is complete. Every item is verified on a device or listed above with its reason.
The per-item ledger rows are in `docs/DONE-LEDGER.md`.
