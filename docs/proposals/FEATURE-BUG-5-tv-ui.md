# BUG-5: Yumeiro TV UI unstable, several TV features broken (owner report)

Status: **open.** Reproduced on the Android TV 14 emulator 2026-11-16 (step 1 below); TV-5 fixed in code, not yet
proven on the TV emulator. Ledger row: BUG-5.
Groups: G12 X9 (TV and low-end), touches G1 (Home / navigation), G3 (Player), G7 (Search).
Rule (same as BUG-1 to BUG-4): no fix before the Step A.5 note; not closed until each item is
reproduced, fixed at the root, and proven on a TV emulator with a regression test that fails before
and passes after. Real-TV confirmation goes to OMT-4.

## Owner report (verbatim)

> Bug Report Yumeiro TV UI and features Issues:
>  * The TV UI is unstable with multiple bugs.
>  * Cannot navigate all the way up to the full-screen button from the top.
>  * Horizontal scrolling on the home screen does not work properly, and scrolling accidentally triggers YouTube Music instead.
>  * Searching yields no results.
>  * Playing media opens the mobile UI format instead of a proper TV layout, and landscape orientation fails to load.

## Items

| ID | Symptom (owner's words, split) | Area | Status |
|---|---|---|---|
| TV-1 | TV UI is unstable with multiple bugs (umbrella: every crash, focus loss or stall found while working TV-2…TV-5 is filed here as TV-1a, TV-1b, …) | X9, G12 | open |
| TV-2 | D-pad Up from the top of a screen cannot reach the full-screen button | G3 player controls / TV focus (`ui/tv/TvPageFocus.kt`) | open |
| TV-3 | Home horizontal rows do not scroll properly with the remote; scrolling opens YouTube Music by accident | G1 Home, TV rail (`ui/tv/TvNavRail.kt`), tab pager | verified (emulator tv-api34/24, fix 45a15ea) |
| TV-4 | Search returns no results on TV | G7 Search | open |
| TV-5 | Playing media shows the phone player layout instead of a TV layout; landscape does not load | G3 Player, `ui/tv/TvMode.kt` | open |

## Plan (to be done in order, each step recorded here)

1. Reproduce on the Android TV emulator myself before anything else: the existing `tv-api24` job
   (`device-check.yml` input `tv: true`, Yumeiro TV flavour, minSdk) plus a current Android TV image,
   driven with `adb shell input keyevent` (DPAD_UP/DOWN/LEFT/RIGHT/CENTER, BACK), UI dumps and
   screenshots. Note which items reproduce on which image.
2. Step A.5 note: read how Android TV apps that work handle the same things (focus restoration and
   `focusProperties` exits, horizontal rows inside a pager with D-pad, TV search input, a 10-foot
   player layout) and compare with Yumeiro's code. Ideas only; no code copied.
3. Root-cause each item; fix; regression device test on the TV emulator (fail before / pass after).
4. Check every fix does not change the phone app (phone device tests on both phone emulators).
5. Real TV remote check stays in OMT-4 until the owner runs it.

## Step 1: reproduction (emulator, tv-api34 = Android TV 14 image, tv_1080p, Yumeiro TV flavour)

Test `TvBug5ReproDeviceTest` (D-pad keys only, focused node logged after every key), device run
43902905156 at 1707ced. Each test asserts what a working TV app does, so FAIL = reproduced.

| Item | Result | What the log and screenshot show |
|---|---|---|
| TV-5 | **reproduced** (a FAIL) | Opening a video requested `USER_PORTRAIT`; the TV turned the whole activity into a 608 × 1080 px portrait box (pillarboxed, `configOrientation=1`, sw 304 dp) with the phone watch-page player in it (`tvb_a_player.png`). Root cause in code: `VideoPlayerModal` treats every screen with smallestScreenWidthDp < 600 as a phone and asks for portrait when not full screen; a 1080p TV is 540 dp, and `isFullscreen` starts false. No TV check existed in the player. |
| TV-2 | not reproduced as reported on API 34 (b PASS) | In that portrait layout the full-screen button was reachable: Up × 5 stayed on `video_quality_btn`, Right ×4, Down → `video_fullscreen_btn`. The owner's "cannot navigate up to the full-screen button" is consistent with the portrait watch page of TV-5; re-checked after the TV-5 fix (the TV player has no toggle any more). |
| TV-3 | not reproduced on an empty install (c PASS) | Right ×14 along the Spaces grid stayed on Home (pager page 0); focus stopped at `quick_card_live`. One oddity: Right from `quick_card_downloads` (second row) jumped to `quick_card_ytmusic` (first row), so Center there opens YouTube Music — a likely cause of "scrolling triggers YouTube Music". Feed rows need online content; next run seeds them. |
| TV-4 | **reproduced in part** (d FAIL) | The Home top-bar search button is on screen (`topbar_search_action`) but D-pad Up from the first card focuses nothing tagged and never reaches it; Left falls back to the rail. Search also only filters the local library on Home ("Search your library"), so on a TV with no local media any query is empty. |

## Step A.5: how working TV apps do it (ideas only, nothing copied)

- Android's TV quality checklist: the app runs in landscape without pillarboxing (TV-LO), Back from
  the top level returns to the TV home (TV-DB), every function is reachable with the five-way D-pad
  (TV-DP), and during playback Center pauses/resumes and Left/Right seek (TV-PC)
  ([TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality)). Android
  TV screens are always landscape; layouts reused from phones are an anti-pattern
  ([Build adaptive apps for TV](https://developer.android.google.cn/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv)).
- TV players (Leanback `PlaybackSupportFragment`, and the ten-foot players of Kodi, Jellyfin for
  Android TV, SmartTube) are always full screen; there is no full-screen toggle and no watch page
  under the video. Back hides the controls, a second Back leaves the player.
- Compared with Yumeiro: the player had no TV branch at all, so TV took the phone path. Fix
  direction for TV-5: in TV layout never request an orientation and pin the player full screen
  (no toggle); Back steps controls → leave. Phones and tablets unchanged.
- TV-4 direction (to confirm with the next run's focus bounds before any fix): the top bar is drawn
  over the page; the page is a focus group whose Up exit must be routed to the top bar explicitly,
  as TV apps route Up from the first row to the top navigation; and Home search on TV should search
  online sources, not only the local library.

## Step 3: fixes

| Item | Fix | Unit | Device |
|---|---|---|---|
| TV-5 | `PlayerOrientationRule`: TV layout → `SCREEN_ORIENTATION_UNSPECIFIED` always, player pinned full screen unless floating, no full-screen button, Back does not step out of full screen. `VideoPlayerModal` and `VideoPlayerBottomBar` use it. | `PlayerOrientationRuleTest` | pending: TvBug5ReproDeviceTest a/b on tv-api34 and tv-api24 |

## TV-3 result (2026-11-17)

- Cause: the Spaces grid rows on Home had no exit rule for Right at the row end, so the default 2D search picked
  the nearest card to the right in any row: from `quick_card_downloads` (row 2, last item) it landed on
  `quick_card_ytmusic` (row 1). One Center then opened YouTube Music.
- Fix 45a15ea (`HomeScreen.kt` `spacesRowFocus()` on `home_spaces` and `home_spaces_more_row`): Right or Left
  past the row end stays on the current card; Up/Down unchanged.
- Fail-before 44116803828 (scratch/tv3-fail-before e011c77 = head without the fix): e FAIL, R1→quick_card_ytmusic.
  Pass-after 44118950490 (tv-api34) and 44121151867 (tv-api24): R1, R2, R3 stay on quick_card_downloads; a–e OK 5.
- Not covered: online feed LazyRows with real content (CI seeds none); they use LazyRow's own end handling.

