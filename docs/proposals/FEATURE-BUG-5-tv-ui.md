# BUG-5: Yumeiro TV UI unstable, several TV features broken (owner report)

Status: **open, recorded 2026-11-16.** Nothing below is investigated or fixed yet. Ledger row: BUG-5.
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
| TV-3 | Home horizontal rows do not scroll properly with the remote; scrolling opens YouTube Music by accident | G1 Home, TV rail (`ui/tv/TvNavRail.kt`), tab pager | open |
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
