# Owner manual tests

These are the only checks left for you (the owner). Everything else is tested by the agent with
unit tests, CI and the two emulators (Android 13 Pixel 3a with 2 GB RAM, and Android 16 Pixel 6).
Each test here needs real hardware, your own accounts, your home network or a person looking at
the screen. Development does not wait for these results; when you send one back, the agent
records it here and in `docs/DONE-LEDGER.md`.

The detailed step-by-step scripts (Y, P, T, F) are in [`MANUAL-TESTS.md`](MANUAL-TESTS.md); this
file says which ones are needed, why, and what to send back.

Which build: the debug APK from the latest green `ci.yml` run on `r15-stability` (Actions → CI →
artifact `Yumeiro-debug-apk`). How to record logs: see "Which build to install" in
`MANUAL-TESTS.md` (`adb logcat ... -s YuStart YuStall YuMemory YuGate YuCache AndroidRuntime`).

## Status summary

| ID | Feature | Status |
|---|---|---|
| OMT-1 | YouTube playback and quality switching (PL-3, PL-4, Y8) | Pending |
| OMT-2 | Chromecast casting (P17) | Pending |
| OMT-3 | Low-RAM phone run (H5, P3, ST) | Pending |
| OMT-4 | Android TV with the remote (T, TV focus) | Pending |
| OMT-5 | Yumix offline on a real phone (Y1, Group 4) | Pending |
| OMT-6 | Heat, battery and long playback on a real phone (P9, HeatGuard) | Pending |
| OMT-7 | Predictive back animation on hardware (H6) | Pending |
| OMT-8 | Launcher widget on a real home screen (H12) | Pending |
| OMT-9 | Usability feedback | Pending |
| OMT-10 | Last.fm scrobbles with your own account, including after leaving the app with Back (MU-1) | Pending |

Status words: Pending (not run yet) · Passed · Failed (send the evidence; the agent fixes it) ·
Blocked (you couldn't run it; say why).

---

### OMT-1 — YouTube playback and quality switching

- **Why the agent can't do it:** YouTube asks GitHub's data-centre addresses to sign in ("confirm
  you're not a bot"), so the YouTube cases skip themselves on CI. Only a home connection gets the
  normal answer.
- **Hardware / environment:** any Android phone, home Wi-Fi; then mobile data; then a VPN (Y14).
- **Instructions:** Script Y, steps Y1–Y14, in `MANUAL-TESTS.md`. Turn on Settings → Player →
  Player info overlay first.
- **Expected result:** cards in ≤ 5 s; play in ≤ 5 s; the quality list holds real heights once
  each; switching quality keeps the position (±1 s); progress is kept after closing.
- **Evidence to send:** screen recording, the `adb logcat` file, screenshots of the Quality list
  and of the overlay after picking 360p and 1080p, the step numbers that failed.
- **Status:** Pending

### OMT-2 — Chromecast casting

- **Why the agent can't do it:** the emulators have no Cast receiver on their network, and Google
  Cast needs a real receiver to discover. The code paths (Cast context warm-up off the main
  thread, which subtitles can be sent) are unit-tested (`CastSubtitlesTest`), and the player
  opens without a Cast device on both emulators.
- **Hardware / environment:** a Chromecast or Google TV on the same Wi-Fi as the phone.
- **Instructions:** 1) Play a Yumix title. 2) Tap the Cast button in the player and pick the TV.
  3) Pause, seek ±10 s, change subtitles. 4) Stop casting. 5) Repeat with a video from Device
  (local file) and note whether it casts or says it can't.
- **Expected result:** the TV starts the same title at the same position in ≤ 10 s; pause and
  seek work from the phone; WebVTT/TTML subtitles show on the TV (SRT ones are not sent yet, a
  known limit); stopping returns playback to the phone. A local file shows a clear message if it
  can't be cast.
- **Evidence to send:** a phone video of both screens, the `adb logcat` file, the TV model.
- **Status:** Pending

### OMT-3 — Low-RAM phone

- **Why the agent can't do it:** the emulator models the memory limit (2 GB, 192 MB heap) but not a
  real phone's GPU, heat or the maker's background killer (MIUI, One UI).
- **Hardware / environment:** an Android 13 Go phone with 2–3 GB RAM (for example a Redmi A2),
  home Wi-Fi.
- **Instructions:** Script P, steps P1–P13, in `MANUAL-TESTS.md`.
- **Expected result:** no ANR or crash; cards never swap under your finger; drama plays in ≤ 5 s;
  downloads survive other heavy apps.
- **Evidence to send:** recording, `adb logcat` file, `adb shell dumpsys meminfo dev.asfakul.yumeiro.debug`
  after P4, failed step numbers.
- **Status:** Pending

### OMT-4 — Android TV with the remote

- **Why the agent can't do it:** no TV emulator with a launcher runs in CI, and judging whether focus
  is visible and Back feels right needs a person with a remote.
- **Hardware / environment:** Chromecast with Google TV (HD) or another Android TV box, its remote.
- **Instructions:** Script T, steps T1–T8, in `MANUAL-TESTS.md`. Also in Yumix: scroll down with the
  D-pad until the extension bar hides, then press Up until it shows; open the filter and pick an
  extension; press Back.
- **Expected result:** focus always visible; Back goes up one level; the extension bar comes back on
  Up; every player menu is reachable.
- **Evidence to send:** phone video of the TV screen, `adb logcat` file, failed step numbers.
- **Status:** Pending

### OMT-5 — Yumix offline on a real phone

- **Why the agent can't do it fully:** the emulator test `YumixDeviceTest#offlineRefreshKeepsTheFeed` switches the
  network off with `svc` and checks that pull to refresh keeps every title and shows the offline message. A real phone also has the
  "connected, no internet" state of a captive Wi-Fi or a weak signal, which the emulator can't produce.
- **Hardware / environment:** any phone; Airplane mode; ideally also a Wi-Fi without internet (a
  phone hotspot with its mobile data off).
- **Instructions:** Script P step P10. Then, still offline, pull down to refresh at the top of
  Yumix; open a title and pull down there too.
- **Expected result:** the cards stay; a short "Couldn't refresh: you're offline" message (on a Wi-Fi
  without internet it may say "Nothing new yet"); nothing jumps to the top;
  when the network is back, new cards load without restarting the app.
- **Evidence to send:** recording with the clock visible, `adb logcat` file.
- **Status:** Pending

### OMT-6 — Heat, battery and long playback

- **Why the agent can't do it:** emulators draw with the CPU and have no battery or thermal sensors
  to speak of; HeatGuard's decisions are unit-tested with simulated temperatures only.
- **Hardware / environment:** a real phone, screen on, home Wi-Fi.
- **Instructions:** Script P step P9 (30 minutes of playback). Note the battery % before and after.
- **Expected result:** warm, not hot; no growing stutter; battery drop close to the YouTube app's.
- **Evidence to send:** battery % before/after, `adb logcat` file (it has `YuMemory` and `YuStall`).
- **Status:** Pending

### OMT-7 — Predictive back animation

- **Why the agent can't do it:** the emulators run the back gesture and the tests check where Back
  lands (`BackNavigationDeviceTest`), but whether the system preview animation looks right is a
  visual judgement on a real gesture-navigation phone.
- **Hardware / environment:** an Android 14+ phone with gesture navigation.
- **Instructions:** from a title page, a row's "See all", Settings and the player, slowly swipe from
  the screen edge and let go halfway, then do it fully.
- **Expected result:** a smooth preview of the screen underneath; letting go halfway cancels; a full
  swipe goes back one level.
- **Evidence to send:** a short screen recording.
- **Status:** Pending

### OMT-8 — Launcher widget

- **Why the agent can't do it:** placing a widget and judging its look on the phone maker's launcher
  needs a person; the widget's data updates can be tested by the agent and are tracked in Group 12.
- **Hardware / environment:** your phone's home screen.
- **Instructions:** long-press the home screen → Widgets → Yumeiro; add it; play music and a video;
  check it after 10 minutes and after a reboot.
- **Expected result:** it shows what is playing or the last item, and taps open the right screen.
- **Evidence to send:** two screenshots and the launcher name.
- **Status:** Pending

### OMT-9 — Usability feedback

- **Why the agent can't do it:** whether something feels clear and pleasant is a human judgement.
- **Hardware / environment:** your phone, a few days of normal use.
- **Instructions:** note anything confusing, slow, ugly or surprising, with the screen name.
- **Expected result:** a short list; each item becomes a ledger entry or a proposal.
- **Evidence to send:** the list, screenshots where useful.
- **Status:** Pending

---

### OMT-10 — Last.fm scrobbling with your own account

- **Why the agent can't do it:** it needs your Last.fm login; the agent must not sign in to your accounts.
  The agent checks the scrobble rules and the background watch with unit and emulator tests
  (`ScrobbleTrackerTest`, `ScrobbleQueueTest`, `LibraryDeviceTest`), but not the real Last.fm server.
- **Hardware / environment:** your phone with internet, a Last.fm account.
- **Instructions:** Settings → Last.fm → sign in. Play two songs on the phone, each for more than half its
  length (or 4 minutes). For the second song, press Back until you leave the app and let it keep playing.
  Then open last.fm in a browser → your profile → Recent tracks.
- **Expected result:** both songs appear (allow up to a minute). Turn on airplane mode, play a third
  song the same way, turn airplane mode off and reopen the app: the third song appears too.
- **Evidence to send:** a screenshot of Recent tracks, and the app version (Settings → About).
- **Status:** Pending

---

## Not owner tests (handled by the agent)

Listed here so they are not asked of you again:

| Item | How the agent covers it | Group |
|---|---|---|
| Volume and brightness swipe gestures | Unit-tested; a device gesture test on the emulators is planned | G9 / G12 |
| Decoder fault recovery | Unit-tested; a fault-injection hook in the debug build is proposed so it can run on the emulators | G12 |
| Listening time and the scrobble watch with no screen open | `LibraryDeviceTest` on both emulators (no activity exists in the test) | G5 / G6 |
| Anime4K output | Shader compile and frame output can be checked on the emulators' GL | G12 |
| Player settings on a device | Emulator device tests | G9 |
| Yumix offline pull to refresh | `YumixDeviceTest#offlineRefreshKeepsTheFeed` on both emulators | G4 |
| Yumix "See all" paging while searching | `YumixDeviceTest#seeAllKeepsPagingWhileSearching` on both emulators | G4 |
| Internet Archive kept after the 1.8.0 clean-up | `LegacyDefaultMigrationTest` (unit); a real upgrade from a 1.8.0 install would need the 1.8.0 APK on the emulator (planned in G12 release checks) | G4 / G12 |
