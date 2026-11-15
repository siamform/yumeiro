# Manual tests on real hardware (Round 4, steps added in R16)

The automated device suite runs on GitHub's emulators (API 33 low-RAM Pixel 3a with 2 GB, and
API 36 Pixel 6). Three things cannot be proven there and need your phone and TV:

1. **YouTube playback and quality switching.** YouTube asks GitHub's data-centre IPs to sign in
   ("confirm you're not a bot"), so `OnlinePlaybackDeviceTest`'s YouTube case skips itself on CI.
   It must be run on a home network.
2. **Real GPU, real thermal limits, real OEM memory killer.** The emulators draw with SwiftShader
   (the CPU), so frame times there are worse than on a phone, but heat and OEM task killers are
   not modelled at all.
3. **TV remote (D-pad) on a real TV launcher.**

## Which build to install

Use the **debug APK** from the latest green `ci.yml` run on `r15-stability`
(Actions → CI → the run → artifact `Yumeiro-debug-apk` → `app-mobile-debug.apk`). The debug build
installs beside the release one (`dev.asfakul.yumeiro.debug`) and keeps the timing logs (`YuStart`,
`YuStall`, `YuMemory`, `YuGate`). The release build strips them (R8 `-assumenosideeffects` on `Log.i`).

For logs, keep the phone on USB with USB debugging on and run, before each script:

```
adb logcat -c
adb logcat -v time -s YuStart YuStall YuMemory YuGate YuCache AndroidRuntime ActivityManager > yumeiro-<device>-<script>.txt
```

Stop it with Ctrl+C at the end of the script. If you have no PC, use the screen recording plus
Settings → Crash reports (About group) → Share (only if a crash happened).

## Devices to use

| Role | Device (exact model) | Why this one |
|---|---|---|
| Low-RAM phone, Android 13 | **Xiaomi Redmi A2, 2 GB RAM** (Android 13 Go edition, Helio G36, PowerVR GE8320) | Go edition sets `isLowRamDevice`, so this is the strictest real memory budget Yumeiro supports; MIUI also kills background apps hard. Any Android 13 (Go) phone with 2–3 GB works if you cannot get this one. |
| TV | **Chromecast with Google TV (HD)** (1.5 GB RAM, Amlogic S805X2, Android TV 12, later updated) | The lowest-memory mainstream Google TV device; it has a hardware AV1 decoder and a D-pad remote. A Xiaomi Mi Box S / TV Stick is a fine second choice. |
| Optional reference | Any recent mid-range phone (Android 14–16, 6–8 GB) | Separates "Yumeiro is slow" from "the phone is slow". |

Specs: [Redmi A2 (Croma)](https://www.croma.com/redmi-a2-2gb-ram-32gb-sea-green-/p/272990),
[Chromecast HD (Google Help)](https://support.google.com/chromecast/answer/15273676?hl=en),
[Chromecast HD Android TV 12 (Android Police)](https://www.androidpolice.com/chromecast-with-google-tv-hd-review/).

Before every script: Wi-Fi at home (not mobile data, no VPN unless the script says so), battery
above 50 %, Yumeiro freshly installed or "Clear storage" done, screen recording on.

---

## Script Y — YouTube playback and quality switching (phone, home Wi-Fi)

Turn on Settings → Player → **Player info overlay** first (it shows Resolution, Quality, Video
codec, Video bitrate, Buffer and Dropped frames on top of the video).

| # | Step | Pass | Fail |
|---|---|---|---|
| Y1 | Videos tab → source switch at the top → **YouTube**. Wait. | Video cards appear within 5 s and keep coming as you scroll. | Empty screen, endless spinner, or an error with no Retry. |
| Y2 | Tap the first normal video (not a Short, not live). Note the time from tap to moving picture. | Moving picture with sound in **≤ 5 s**. | > 5 s, black screen with sound, or an error. |
| Y3 | Let it play 60 s. Watch the overlay. | No rebuffer spinner after the first second; Dropped frames stays under 1 % of frames. | Any rebuffer on home Wi-Fi, or dropped frames climbing steadily. |
| Y4 | Open **Quality**. Read the list. | Real heights only (e.g. 144p…1080p, or up to what the video has), each once, highest first, plus Auto. No "0p", no duplicates. | Missing heights the video has, duplicates, or entries that do nothing. |
| Y5 | Pick **360p**. | Picture changes within 3 s, the playback position does not jump back more than 1 s, overlay Resolution shows a 360-line stream. | Restarts from 0:00, freezes, or the overlay still shows the old height. |
| Y6 | Pick **1080p** (or the highest offered). | Same as Y5, at the new height. | Same as Y5. |
| Y7 | Pick **Auto**. | Plays on; overlay shows Quality "Auto". | Freezes or errors. |
| Y8 | Seek to the middle using the bar, then double-tap to skip ±10 s twice each. | Each seek shows a picture within 2 s. | Stuck spinner > 5 s. |
| Y9 | Press Home, wait 10 s, come back. | Same video, same position (±2 s), still at the chosen quality. | Restarted, lost, or crashed. |
| Y10 | Rotate to landscape and back; enter PiP (Home button while playing, if PiP is on). | No restart; PiP window keeps playing; tapping it returns to the full player. | Restart, black PiP, or crash. |
| Y11 | Play a **live** video and a **Short**. | Both play in ≤ 5 s. | Either fails. |
| Y12 | Repeat Y2–Y6 on **mobile data**. | Same results (may start at a lower Auto height). | Errors that did not happen on Wi-Fi. |
| Y13 | (R16) Play a video for 2 min, close the player with Back, then reopen the same video. | Continue watching shows it with the right progress; it resumes within ±5 s of where you closed it. | Starts from 0:00 or the progress bar is missing. |
| Y14 | (R16) Turn on a VPN (any free one), repeat Y1 and Y2. | Same as Y1–Y2, or a clear message that YouTube refused this network, with Retry. | Endless spinner or a blank screen. |

**Send back:** the screen recording (Y1–Y14), the `adb logcat` file, a screenshot of the
Quality list (Y4) and of the overlay after Y5 and Y6, and the row number of any step that failed.

---

## Script P — low-RAM phone (Redmi A2, Android 13 Go)

| # | Step | Pass | Fail |
|---|---|---|---|
| P1 | Fresh install, open, go through or skip the welcome guide. | Home appears in ≤ 3 s after the guide; no "isn't responding" dialog. | ANR dialog, crash, or > 5 s blank. |
| P2 | Swipe between all four tabs 20 times, quickly. | No freeze longer than a blink; no ANR. | A freeze you can feel (≈ ½ s) or an ANR. |
| P3 | Videos → Yumix: scroll down fast for 60 s, then back up to the top. | Cards never jump, swap or flash older ones; no out-of-memory crash. | A card changes under your finger, the list jumps, or the app closes. |
| P4 | Repeat P3 for YouTube, Music → Online, Live TV and Home. | Same. | Same. |
| P5 | Play a drama (Videos → Yumix → a drama row). Time tap → picture. | ≤ 5 s on home Wi-Fi. | > 8 s. |
| P6 | During playback, open Quality, Audio, Subtitles and Speed menus; change each once. | Each change takes effect without a restart. | Restart from 0:00 or a frozen picture. |
| P7 | Start a download, then open 3 other heavy apps (camera, browser, a game), come back after 5 min. | Download is still running or finished; the notification was shown the whole time. | Download silently stopped or vanished. |
| P8 | Settings → Crash reports (About group). | Says "No crashes recorded" — or, if a crash happened, Share works. | Share fails or the list is wrong. |
| P9 | Leave a video playing for 30 min with the screen on. | Phone warm, not hot; no stutter growth; battery drop roughly like YouTube's. | Hot to touch, or the picture stutters more over time. |
| P10 | (R16) Videos → Yumix, scroll a few rows down, turn on **Airplane mode**, keep scrolling, then turn it off. | A "No internet" notice appears once; cards already shown stay in place (no older cards replace them); after it comes back, new cards load without a restart. | Cards swap under your finger, the list jumps to the top, or nothing loads after the network is back. |
| P11 | (R16) Fill the phone until less than 500 MB is free (copy a large video), then start a download and play a video. | A clear "not enough space" message for the download; playback still works; no crash. | Crash, silent failure, or a half-written file left in Downloads. |
| P12 | (R16) Play a video for 2 min, press Back to close the player, swipe Yumeiro away from Recents at once, reopen. | Continue watching keeps the position (±5 s). | Position lost. |
| P13 | (R16) While a video plays, pull down the notification and use Pause / Play / Next; lock the screen and unlock. | Controls work; playback continues only if Background play is on. | Notification missing, controls do nothing, or a crash. |

**Send back:** the recording, the `adb logcat` file (it includes `YuMemory` heap lines and
`YuStall` main-thread pauses), and `adb shell dumpsys meminfo dev.asfakul.yumeiro.debug` taken
right after P4, and the step numbers that failed. For P10 also note the clock time you switched
Airplane mode on and off, so it can be matched to the log.

---

## Script T — TV (Chromecast with Google TV HD)

Install with `adb install` over the network (Settings → System → Developer options → Network
debugging), or with a sideload app. Use only the remote.

| # | Step | Pass | Fail |
|---|---|---|---|
| T1 | Open Yumeiro from the TV launcher. | Opens to the TV layout; focus ring visible on one item. | Phone layout, or no visible focus. |
| T2 | Move with the D-pad through every tab and the first three rows of each. | Focus always visible and never lost; Back goes up one level and never exits by surprise. | Focus disappears, gets stuck, or Back closes the app from a sub-screen. |
| T3 | Play a Yumix title, then a YouTube video, then a Live TV channel. | Each plays in ≤ 5 s; OK shows the controls; Left/Right seek. | Any one fails or the controls cannot be reached. |
| T4 | In the player, change Quality, Audio and Subtitles with the remote only. | Every menu is reachable and closes with Back. | A menu cannot be reached or closed. |
| T5 | Play 20 minutes, then press Home and reopen. | Resumes at the right position. | Restarts or crashes. |
| T6 | Leave the app on Home for 10 minutes. | No crash or "isn't responding". | Either one. |
| T7 | (R16) Open 2–3 other TV apps (YouTube, Netflix), then return to Yumeiro from Recents. | Comes back to the same screen, or restarts cleanly at Home with Continue watching intact. | Crash, blank screen, or lost progress. |
| T8 | (R16) Long-press OK on a card, and use the remote's Back/Home during a dialog. | Dialogs and menus get focus at once and close with Back. | Focus stuck behind a dialog. |

**Send back:** a phone video of the TV screen, the `adb logcat` file, and the step numbers that failed.

---

## Script F — the scroll flake (both devices, 20 runs)

On CI this runs automatically (`device-check.yml` with `repeat` and `shards`, see
`docs/evidence/round3/device-runs.md`). On a real phone, P2 + P3 repeated 5 times is enough;
report any freeze with the time it happened (the recording timestamp) so it can be matched to
the `YuStall` line in the log.
