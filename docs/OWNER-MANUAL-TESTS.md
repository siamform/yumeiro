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
| OMT-11 | Manga reading and Live TV channels on your phone and TV, with your own sources and playlists (MG-1, LT-1..3) | Pending |
| OMT-12 | Cold start on your phone with the baseline profile (G12 X2-1) | See section |
| OMT-13 | Restrict the Firebase API key (G12 X7-3, ADR-013) | See section |
| OMT-14 | Download speed with Wi-Fi power saving (BUG-3 T5) | See section |
| OMT-15 | Each YouTube quality really plays (BUG-4) | Pending |
| OMT-16 | Your own Play link streams and Yumeiro upscaling on your phone's GPU (BUG-7 7c, 7d) | Pending |
| OMT-17 | Website and Play link downloads in your phone's gallery (BUG-7 7a, 7b) | Pending |
| OMT-18 | Yumeiro behind a VPN on your phone (G10 network states) | Pending |
| OMT-19 | A real YouTube download (video + audio) on your home network (G8 D6) | Pending |
| OMT-20 | BUG-3 download speed per 10 % band on your phone (eighth cycle rule) | Blocked: owner |
| OMT-21 | BUG-5 / TV-4: choose how TV search should work (G7 proposal 3) | Blocked: owner (decision) |
| OMT-22 | X3-TL: give the service account the Firebase Test Lab role | Blocked: owner (console) |
| OMT-23 | X13-6: phone Jackson 2.19.4 → 2.18.11 | Blocked: owner (decision) |
| OMT-24 | Vega: what do you mean by "Vega"? | Blocked: owner (question) |
| OMT-25 | Telegram, Terabox, Diskwala and manga cloud path | Blocked: owner (decision) |
| OMT-26 | EXT-T1: Back closes a pop-up instead of Source status | Blocked: owner (phone) |
| OMT-27 | NBUG-6: per-feature DNS has measured side effects — build anyway, narrow it, or drop | Blocked: owner (decision) |
| OMT-28 | G6-AF: unplug wired headphones / turn off Bluetooth headphones while music plays | Blocked: owner (hardware) |
| OMT-29 | G6-PG2: endless Music Home keeps adding "Similar to" shelves, also after the connection drops | Pending |

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

### OMT-11 — Manga reading and Live TV with your own sources

- **Why the agent can't do it:** the manga sources and IPTV playlists you use are your choice; many
  channels are region-locked, so a CI emulator in a US data centre can't open them. The agent covers
  the code with unit tests (`StreamPlaylistTest`, `PlaylistMarketTest`, `HlsAdFilterTest`) and a code read.
- **Hardware / environment:** your phone and Android TV, home Wi-Fi, one installed manga source, one IPTV playlist you use.
- **Instructions:** Manga: search a title, open it, open a chapter, read 10 pages, tap Retry on the title
  page while it loads, go back and forward between chapters. Live TV: add or open your playlist, play 3
  channels, close the app from recents, reopen Live TV.
- **Expected result:** no "failed" message after Retry (only real network errors); reading position kept;
  your playlist and channels still listed after reopening; channels start in under 10 s on Wi-Fi.
- **Evidence to send:** a short screen recording or screenshots, the app version, and the playlist size (number of channels).
- **Status:** Pending

---

### OMT-12 — Cold start on your phone with the baseline profile (G12 X2-1)

- **Feature:** app start-up speed (baseline profile `app/src/main/baseline-prof.txt`, commit fef01c6).
- **Why the agent can't do it:** CI only has emulators with software graphics; their cold-start numbers
  swing by more than the profile's effect between runs (api36 "no AOT" was 3899 ms in one run and
  2509 ms in the next). Only a real phone gives a number that means something for users.
- **Hardware / environment:** your phone, with a preview build installed from the branch (the agent
  can attach one on request), Developer options → USB debugging, a computer with `adb`.
- **Instructions:** close the app from recents. Run 5 times:
  `adb shell am force-stop dev.asfakul.yumeiro.preview && adb shell am start -W -n dev.asfakul.yumeiro.preview/dev.asfakul.yumeiro.MainActivity`
  and note the `TotalTime` line each time. Then install the previous preview build (without the
  profile) and repeat.
- **Expected result:** the median `TotalTime` with the profile is the same or lower than without it.
- **Pass / fail rule:** pass if the median with the profile is ≤ +5 % of the median without it; fail otherwise.
- **What to record:** phone model, Android version, all 10 `TotalTime` numbers (5 with, 5 without), both build versions.
- **Attempts by the agent:** macrobenchmark on CI emulators: before 43784533563, profile generated in
  43788916880, after 43790371337 (ledger X2-1). Numbers recorded, but emulator noise is too large to
  judge the gain.
- **Status:** Pending

---

### OMT-13 — Restrict the Firebase API key in your Google Cloud console (G12 X7-3, ADR-013)

- **Feature:** instant notices (FCM). The Firebase API key is in every APK by design (class 2,
  "restricted public"); Firebase asks that it be restricted.
- **Why the agent can't do it:** key restrictions live in your Google Cloud console
  (project 767447175720), which only your account can open. The agent has no credentials for it,
  and must not have them.
- **Hardware / environment:** a browser signed in to the Google account that owns the Firebase
  project.
- **Instructions:** Google Cloud console → APIs & Services → Credentials → the "Android key" (or
  auto-created) key used by the app. (1) Application restrictions: Android apps; add
  `dev.asfakul.yumeiro`, `dev.asfakul.yumeiro.preview`, `dev.asfakul.yumeiro.debug`, each with the
  SHA-1 of the certificate that signs it (release key; debug key for preview/debug). (2) API
  restrictions: restrict the key to Firebase Installations API and Firebase Cloud Messaging API
  (plus any other Firebase API the console lists as in use). Save. Then tell the agent; it will
  re-run the probe below and the push device tests.
- **Expected result:** a request with the key and no Android app headers is refused with
  `API_KEY_ANDROID_APP_BLOCKED`; push registration on the emulator still works.
- **Pass / fail rule:** pass when the agent's probe is refused and the FCM device test passes on
  both emulators; fail if either is not true.
- **What to record:** the date, the packages and SHA-1s you entered, the APIs you allowed.
- **Attempts by the agent:** 2026-11-16, probe of the key from the public v1.8.1 APK without
  Android headers: Firebase Installations passed the key check and reached argument validation
  (HTTP 400 `INVALID_ARGUMENT`), so no Android-app restriction is active; YouTube Data answered
  `SERVICE_DISABLED` (service off in the project, not a key restriction). The console itself
  cannot be reached without your account.
- **Status:** Pending

---

### OMT-14 — Download speed on your phone with Wi-Fi power saving (BUG-3 T5)

- **Feature:** online downloads keep their speed to 100 % with the screen off (BUG-3; fixes 40ea96e
  split of idle work, 3c85a72 `RequestPace`, 24ee21b progress throttle).
- **Why the agent can't do it:** the emulators' network is not Wi-Fi; Android's Wi-Fi power save and the
  phone maker's battery rules (which slow Wi-Fi with the screen off) exist only on a real phone.
- **Hardware / environment:** your phone on your home Wi-Fi, a preview build from the branch (the agent
  can attach one on request), battery saver off, charger unplugged.
- **Instructions:** add a large download (a film or episode of 1 GB or more) from a source that was slow
  for you before. Watch the percentage once at 10 %, 50 %, 70 % and 90 % with a stopwatch (screen on).
  Then add a second one, turn the screen off at 30 %, and check the time when it finishes.
- **Expected result:** no slowdown after 50–70 %: the time from 90 % to 100 % is about the same as
  from 10 % to 20 %; the screen-off download finishes.
- **Pass / fail rule:** pass if the last 10 % takes at most 2× the average 10 %; fail otherwise.
- **What to record:** phone model, Android version, source name, file size, the stopwatch times.
- **Attempts by the agent:** unit, local server with a per-request cap (CI 43851470283 before: last 10 %
  4.9×; CI 43861926740 after: 1.67× and 4× faster). Emulator, real host cdn.kernel.org, screen on and
  screen off with Doze forced (`dumpsys deviceidle force-idle`) at 30 %: last/mean 0.60–1.02, all DONE
  (device run 43861039865). Wi-Fi power save cannot be produced on an emulator.
- **Status:** Pending

---

### OMT-15 — Each YouTube quality really plays (BUG-4)

- **Feature:** the player's Quality list for YouTube equals the download sheet, and each height is
  applied (BUG-4 F1, `QualityEngine.requestForList`).
- **Why the agent can't do it:** YouTube refuses anonymous playback from GitHub's CI addresses
  ("Sign in to confirm you're not a bot", device runs 43849088080 and 43851896291).
- **Hardware / environment:** your phone with a preview build, your normal internet.
- **Instructions:** open any YouTube video in Yumeiro, open Quality, note the list. Pick every height,
  one after another, and after each pick open Video details → the resolution line.
- **Expected result:** the list has the same heights as the download sheet of that video; after each
  pick the resolution line shows that height and the video keeps playing.
- **Pass / fail rule:** pass if every height is listed and applied; fail on any missing or wrong height.
- **What to record:** the video link, the list, the resolution after each pick.
- **Attempts by the agent:** offline YouTube test without the network (`YtQualityOfflineDeviceTest`,
  run 43851896291, emulator): list = download heights [1080…240]. Generic HLS ladder, every height picked
  and applied on both emulators (`QualityListDeviceTest`, run 43861039865). The online YouTube test skips
  on the bot wall.
- **Status:** Pending

---

### OMT-16 — Your own Play link streams and Yumeiro upscaling (BUG-7 7c, 7d)

- **Feature:** a wrong format in Play link ends in a readable error, and Back closes the player;
  Anime4K and the "Upscaled by Yumeiro" level keep playing without a freeze.
- **Why the agent can't do it:** your exact stream links are yours, and the CI emulators draw with a
  software GPU, so Anime4K shaders don't load the way they do on your phone.
- **Hardware / environment:** your phone with a preview build, your stream links.
- **Instructions:** (1) Play link → paste a link that froze before → pick a wrong format (for example
  HLS for an MP4) → Play. Wait 10 s, then press Back once. (2) Play the link with the right format,
  turn on Anime4K (Player settings → Upscaling), then pick "Upscaled by Yumeiro" in Quality. Watch 2
  minutes; tap the screen, open More, press Back.
- **Expected result:** (1) an error card such as "Unsupported stream format" within a few seconds; one
  Back closes the player. (2) the time keeps moving, taps answer, More opens, Back leaves.
- **Pass / fail rule:** fail on any freeze over 5 s, an "app isn't responding" dialog, or Back that does
  nothing.
- **What to record:** the link type (not the link if private), the formats picked, phone model, and
  `adb logcat -s YuStall YuBack AndroidRuntime` if it fails.
- **Attempts by the agent:** emulator, both phones: 5 mismatch cases end in a readable error, main thread
  free (maxLag < 1.8 s). Back was ignored on the error card (43935805199) → fixed (PlayerBackRule.leave),
  one Back closes (43946324687). Upscaling: all 3 cases play on, More opens, Back leaves (43946324687).
- **Status:** Pending

---

### OMT-17 — Website and Play link downloads in your gallery (BUG-7 7a, 7b)

- **Feature:** the website watch page has a Download button; Play link downloads land in
  Movies/Yumeiro and show in your gallery app.
- **Why the agent can't do it:** gallery apps differ by phone maker; the emulator only proves the file is
  in MediaStore.
- **Hardware / environment:** your phone, the gallery app you use.
- **Instructions:** open a site video from Stream → Download. Then Play link → a direct MP4 → Download.
  Settings → Downloads → "Save YouTube and link downloads to phone" on. Open your gallery.
- **Expected result:** both videos in the gallery (Movies/Yumeiro album) and in Yumeiro's Offline list.
- **Pass / fail rule:** fail if a file is missing from either place.
- **What to record:** phone model, gallery app, screenshots.
- **Attempts by the agent:** emulator, both phones (43935805199): Download queued and done; file in
  Movies/Yumeiro, inStore=true, app copy removed, entry kept.
- **Status:** Pending

---

### OMT-18 — Yumeiro behind a VPN (G10 network states)

- **Feature:** online pages, playback and downloads keep working with a VPN on, and turning the VPN
  on or off mid-use shows the network messages without a freeze.
- **Why the agent can't do it:** the CI emulator cannot start a VPN without a VPN app and Android's
  consent dialog; `NetworkStateDeviceTest.d` only logs `vpnActive=false` there.
- **Hardware / environment:** your phone, the VPN app you use.
- **Instructions:** VPN on → open Home, a YouTube video and an extension source; start a download.
  Turn the VPN off and on again while the video plays.
- **Expected result:** pages load, the video keeps playing or recovers with a readable message, the
  download continues or resumes; no "app isn't responding".
- **Pass / fail rule:** fail on a freeze over 5 s, a raw error text, or a download that never resumes.
- **What to record:** VPN app, phone model, what failed, `adb logcat -s YuStall AndroidRuntime`.
- **Attempts by the agent:** emulator: no network, slow server and refused port PASS on both phones
  (43904330191); VPN case logs only.
- **Status:** Pending

---

### OMT-19 — A real YouTube download on your home network (G8 D6)

- **Why the agent can't do it:** YouTube answers CI emulators (data-centre IPs) with "Sign in to confirm
  you're not a bot", so no real stream URL reaches the downloader there. Signing in would need your account.
- **Hardware / environment:** your phone on your home Wi-Fi; no VPN.
- **Instructions:** open a YouTube video of 5–15 minutes → Download → pick 720p (video + sound). Start a
  second download of the same video as audio only. Halfway through the first, turn Wi-Fi off for 20 s, then on.
- **Expected result:** both finish; the video file is in Movies/Yumeiro and plays with sound in your gallery;
  the audio file is in Music/Yumeiro; the Wi-Fi drop only pauses, it does not start over or fail.
- **Pass / fail rule:** fail on a file without sound or picture, a download that restarts from 0 %, a stuck
  "Merging", or a wrong folder.
- **What to record:** video link, quality, phone model, Android version, `adb logcat -s YuDownloads AndroidRuntime`.
- **Attempts by the agent:** CI emulator real YouTube: blocked by the bot wall (same as BUG-4, OMT-15);
  local pipeline (resume a half file, merge 1 video + 1 audio track, publish to Movies/Music) covered by
  `YtDownloadPipelineDeviceTest` on the emulator (run 44054360131); YtDownloads/option unit tests (CI).
- **Status:** Pending

---

### OMT-20 — BUG-3 download speed per 10 % band on your phone (eighth cycle)

- **Feature:** online downloads keep their speed to 100 % (BUG-3 = NBUG-1). Fixes so far: 40ea96e,
  3c85a72 `RequestPace`, 24ee21b, and DL-11 (4fa126e: a restart is only kept when it helps).
- **Why the agent can't do it:** the slowdown at 50–70 % was seen on your phone, host and Wi-Fi. The
  emulator case d (local host with a per-connection cap) stays green on every download change, but it
  cannot copy your Wi-Fi, phone maker battery rules or host.
- **Instructions:** install the latest green debug APK. Add one download of 300–600 MB from a source that
  was slow before, screen on. Each time the percentage passes 10, 20, … 100 note the stopwatch time.
  Optional: `adb logcat -s YuDownload` during the run and send the lines with `SEGMENTED_STATS`.
- **Pass / fail rule (owner order, eighth cycle):** work out the ten band times (10→20 %, …, 90→100 %)
  and their median. Pass if the last 10 % takes at most 3.5 s. Assumption by the agent: with a large
  file a 10 % band can take longer than 3.5 s at full speed; in that case the agent also reads the
  result as "last band ≤ median band + 3.5 s" and says so in the ledger. Fail otherwise.
- **What to record:** phone, Android version, source, file size, the ten times, the median.
- **Agent evidence so far:** case d 44043535616 (both emulators); DL-11 CI unit fail-before 44103206509,
  pass-after 44106540469; device run in the ledger row DL-11.
- **Status:** Blocked: owner

---

### OMT-21 — BUG-5 / TV-4: how should TV search work? (decision, G7 proposal 3)

- **Now:** the TV search field is reachable and answers (verified 43914880052, 43935810414, 43957029647),
  but Home search only ranks your local library, so an empty library gives no results (TV-4 scope).
- **Options:**
  - A. Keep it local, and show "Nothing in your library yet — search Yumix" with a button. Smallest change.
  - B. TV search runs the same online Yumix search the phone uses (YouTube and installed sources), with
    local results first. Medium change, no new screens.
  - C. G7 proposal 3: one global screen across Yumix, manga and Live TV. Largest change; it is a new feature.
- **Recommendation:** B now (it is what users expect from a search box and reuses tested code), C later
  as a gated feature after the eighth-cycle gate.
- **What to send back:** A, B or C.
- **Status:** Blocked: owner (decision). Development does not wait for it.

---

### OMT-22 — X3-TL: Firebase Test Lab role (console)

- **Why:** real-device cloud runs would turn some owner tests into agent tests. The service account mints a
  token, but Test Lab answers 403 (ledger X3-TL, 2026-11-16).
- **Instructions:** Google Cloud console → IAM → the service account → add role "Firebase Test Lab Admin"
  (`roles/cloudtestservice.testAdmin`); APIs → enable "Cloud Testing API" and "Cloud Tool Results API".
- **What to send back:** "done" (no keys in chat). **Status:** Blocked: owner.

---

### OMT-23 — X13-6: phone Jackson version (decision)

- **Why:** OSV lists 16 advisories on 2.19.4 (no patched 2.19); 2.18.11 has none and stays below the 2.20
  `invokeExact` limit. Your earlier condition 3 keeps the phone version, so the agent did not change it.
- **Options:** A. move the phone to 2.18.11 (agent runs the full extension suite on both emulators);
  B. keep 2.19.4. **Recommendation:** A. **Status:** Blocked: owner (decision).

---

### OMT-24 — Vega: what do you mean? (question)

- "Vega" can mean Amazon's Vega OS (the new Fire TV system, not Android) or the Vega app's provider
  repositories (listed next to Nuvio and Skystream in F-05; hikari v0.10.57 calls them "Vega").
  These are very different jobs. **What to send back:** which one. No design starts before the answer.
- **Status:** Blocked: owner (question).

---

### OMT-25 — Telegram, Terabox, Diskwala and manga cloud path (decision)

- **Now:** Terabox and Diskwala links open a hidden WebView that catches the real media request
  (BEHAVIOR-SPEC "Cloud links"). Telegram and manga translation are not built (F-02, F-03, F-11).
- **Proposal:** each comes as its own gated feature note (docs/proposals/FEATURE-*.md) with the site terms,
  what data leaves the phone, and an off-by-default switch. Telegram needs your own API id (user login
  through TDLib), so it would be "bring your own account"; manga translation would run on-device first.
- **What to send back:** which of the four to plan, and whether a user login (Telegram) is acceptable.
- **Status:** Blocked: owner (decision).

### OMT-26 — EXT-T1: Back closes a pop-up instead of Source status (phone)

- **What the emulators showed:** 10 plain runs (44162062169) and 20 runs with five drama feeds timing out
  while Source status is open (44189135829, api33-lowram): focus stayed on Yumeiro and Back closed Source
  status every time. The only extra window was a phisher98 donation dialog that the pop-up blocker keeps
  hidden (you see the "Show" card instead).
- **Steps:** install HDhub4u (phisher98) and one CNCVerse extension, open Source status, tap "Check again"
  while on a slow or blocked network, wait for the result, press Back once.
- **Pass:** Back leaves Source status. **Fail:** Back closes something else first or nothing happens; then
  note which extensions are installed and send a screen recording.
- **Status:** Blocked: owner (phone).

### OMT-27 — NBUG-6 per-feature DNS: decision

- **Measured:** a feature with its own DNS can't share connections with the others for the same site (2 instead of
  1 on the emulator), each extra resolver keeps 5–6 threads for about a minute after use, the address cache must
  be split, and WebView, subtitles and app update don't go through it. Details:
  docs/proposals/FEATURE-NBUG-6-per-feature-dns.md §2, §4.
- **Your rule:** build only without side effects, so it is **not built**. The automatic bypass that exists today
  stays as is.
- **What to send back:** drop it, or name one narrow case (for example "drama always over DoH") to plan as a gated note.
- **Status:** Blocked: owner (decision).

### OMT-28 — Music pauses when headphones are unplugged (phone + headphones)

- **Why you:** "headphones unplugged" (AUDIO_BECOMING_NOISY) is a protected system broadcast; the emulator shell
  can send the command but the system does not deliver it (44219681816: music kept playing on both emulators).
  The code turns the behaviour on (`PlaybackService`: `setHandleAudioBecomingNoisy(true)`). Audio focus (calls,
  other apps, notification sounds) and the notification / lock-screen controls are verified on the emulators.
- **Steps:** play a song with wired or Bluetooth headphones, then unplug them (or switch the Bluetooth headphones off).
- **Pass:** the music pauses and does not come out of the speaker. **Fail:** it keeps playing on the speaker.
- **Status:** Blocked: owner (hardware).

### OMT-29 — Endless Music Home on your own connection (G6-PG2)

- **Why you:** the "Similar to …" shelves come from YouTube's watch page, which asks GitHub's CI addresses to
  sign in ("Sign in to confirm that you're not a bot"). On both emulators every call was refused
  (device runs 44242208629, 44243818628: Home showed only its own 2 shelves, then nothing). The rules
  behind the feed (80-shelf cap, offline seeds kept) are unit-tested (`YmEndlessCapTest`, `YmEndlessOfflineTest`,
  CI 44238631828), and the network drop / return is verified on the emulators (G6-MO1).
- **Steps:** Music → Online → Home. Scroll to the end until shelves called "Similar to …" appear and keep
  coming (YouTube's own Home stops after a few pages). Turn Wi-Fi and data off (Music shows On device), wait
  10 s, turn them on (Online again), go back to Home and keep scrolling.
- **Pass:** new "Similar to …" shelves keep appearing after the connection comes back.
  **Fail:** no new shelves, or Home ends with a blank space.
- **Automated test you can run:** `MusicEndlessNetworkDeviceTest` (log tag `YuEndless`, `EN_RESULT PASS`).
- **Status:** Pending

## Not owner tests (handled by the agent)

Listed here so they are not asked of you again:

| Item | How the agent covers it | Group |
|---|---|---|
| Volume and brightness swipe gestures | Unit-tested; a device gesture test on the emulators is planned | G9 / G12 |
| Decoder fault recovery | `DecoderFaultDeviceTest` a/b/c on both emulators with the debug fault hook (run 43857809658) | G12 |
| Listening time and the scrobble watch with no screen open | `LibraryDeviceTest` on both emulators (no activity exists in the test) | G5 / G6 |
| Anime4K output | Shader compile and frame output can be checked on the emulators' GL | G12 |
| Player settings on a device | Emulator device tests | G9 |
| Yumix offline pull to refresh | `YumixDeviceTest#offlineRefreshKeepsTheFeed` on both emulators | G4 |
| Yumix "See all" paging while searching | `YumixDeviceTest#seeAllKeepsPagingWhileSearching` on both emulators | G4 |
| Internet Archive kept after the 1.8.0 clean-up | `LegacyDefaultMigrationTest` (unit); a real upgrade from a 1.8.0 install would need the 1.8.0 APK on the emulator (planned in G12 release checks) | G4 / G12 |
