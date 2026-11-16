# BUG-6: taps on the full-screen music player reach the screen underneath (owner report)

Status: **open, recorded 2026-11-16.** Not reproduced yet. Ledger row: BUG-6.
Groups: G2 (Music / Now Playing), touches G1 (Home / tabs under the overlay) and G12 (cross-cutting input).
Rule (same as BUG-1 to BUG-5): no fix before the Step A.5 note; not closed until reproduced, fixed at
the root, and proven on both phone emulators with a regression test that fails before and passes after.

## Owner report (verbatim)

> Bug Report: In the music player screen, when media is set to full screen, tapping on the screen
> accidentally triggers clicks on elements positioned underneath the player interface.

## First lead (code reading only, unverified)

- The full Now Playing screen is an `AnimatedVisibility` overlay drawn over the tab shell
  (`MTPlayerApp.kt`, "Full Now Playing Screen Overlay"). Its root `Box` in `NowPlayingScreen.kt`
  (`now_playing_screen_root`) has a background but no pointer handling. In Compose a background does
  not consume touches, so a tap on an empty part of the player (between artwork, title and controls)
  is not consumed and is delivered to the Home/tab content laid out underneath.
- To check as well: the online music (YouTube Music) player and its video/full-screen mode, in case
  "full screen" means that one; and the floating mini player expand path.

## Plan

1. Reproduce on both phone emulators: open a song, expand Now Playing, tap empty areas over where a
   tab card sits underneath; assert nothing under the overlay received the click (route, tab and
   pressed-state probes, screenshot). Repeat for the online music player.
2. Step A.5 note: how other players block touches under full-screen sheets (ideas only).
3. Root-cause fix, regression device test (fail before / pass after), phone suite on both emulators.
