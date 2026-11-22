# Owner manual tests

These are the only checks left for you (the owner) and for Yumeiro users. Everything else was tested by the agent
with unit tests, CI and four emulators (Android 13 Pixel 3a with 2 GB RAM, Android 16 Pixel 6, Android TV 7 and
Android TV 14). Each test here needs real hardware, your own accounts, your home network or a person looking at the
screen. When you send one back, the agent records it here and in `docs/DONE-LEDGER.md`.

The file has two sections: **Yumeiro users checklist** (anyone can run these on their own phone or TV) and **Owner
(developer) tests** (need the owner's accounts, consoles or adb). The detailed step-by-step scripts (Y, P, T, F) are
in [`MANUAL-TESTS.md`](MANUAL-TESTS.md).

Which build: the newest **review APK of `r15-stability`** (2.x), release-signed (artifact `yumeiro-review-apk` of the
latest green `ci.yml` run named in [`HANDOVER-LIVE.md`](HANDOVER-LIVE.md); check its SHA-256 against `SHA256SUMS.txt`).
Older tests below that name the 1.9.0 release candidate were written for that build; run them on the newest one.
Back up 1.8.x first (Settings → Backup & restore → Back up now), then install over it; your data stays. TV: `adb install -r` with the
TV APK (`yumeiro-tv-1.9.0-…-review.apk`). How to record logs: see "Which build to install" in `MANUAL-TESTS.md`
(`adb logcat ... -s YuStart YuStall YuMemory YuGate YuCache AndroidRuntime`). How to report: Settings → Crash
reports → Share, plus a GitHub issue with the test ID.

The final test ran one pass per feature from a fresh install (the warm-start second pass was dropped on the owner's
decision of 2026-11-20); the rows below are what the emulators could not prove.

## Day-by-day test plan (owner order 2026-11-19)

The owner and users run these day by day on real phones and TVs; development never waits for them. Send back
the result per test ID (pass / fail + what you saw, a screen recording or `adb logcat` helps). Use the 1.9.0
release candidate review APK (above).

| Day | Theme (what you need) | Tests |
|---|---|---|
| 1 | First run on your phone, home Wi-Fi | OMT-31 fresh install loads online content · OMT-5 Yumix offline · OMT-34 the slow extension now stops · OMT-26 Back closes a pop-up · OMT-3 low-RAM phone run (if you have a 2–3 GB phone) |
| 2 | YouTube and music | OMT-1 playback + quality switching · OMT-15 each YouTube quality plays · OMT-19 a real YouTube download · OMT-29 endless Music Home after the connection drops · OMT-10 Last.fm scrobbles · OMT-28 unplug headphones while music plays |
| 3 | Downloads | OMT-14 speed with Wi-Fi power saving · OMT-20 BUG-3 speed per 10 % band · OMT-17 website and Play link downloads in the gallery · OMT-30 HLS download with separate audio has sound |
| 4 | Player and hardware | OMT-16 your Play links + upscaling on your GPU · OMT-6 heat, battery, long playback · OMT-7 predictive back animation · OMT-12 cold start with the baseline profile · OMT-2 Chromecast |
| 5 | Android TV with the remote | OMT-4 TV with the remote · OMT-11 manga and Live TV on phone and TV · OMT-8 launcher widget |
| 6 | Your network | OMT-18 behind a VPN · OMT-32 the Porn4fans title on your network · OMT-33 adult hosts without a VPN (after the agent's network option ships) |
| 7 | New in this release (phone) | OMT-35 external players · OMT-36 volume boost · OMT-37 Shorts swipes on a 2–3 GB phone · OMT-38 Kasumi glass theme · OMT-39 statistics over three days · OMT-40 Upcoming in your time zone · OMT-43 extensions after the Jackson change |
| 8 | New in this release (TV, playlists) | OMT-41 Home search → Search online with the remote · OMT-42 SVG channel logos in your playlists |
| any | Your judgement | OMT-9 usability feedback (anything that feels slow, confusing or off-vibe) |

Owner-only console actions (not tests, only the account owner can do them): OMT-13 restrict the Firebase API key,
OMT-22 give the service account the Firebase Test Lab role.

Decision items OMT-21 (TV search), OMT-23 (Jackson), OMT-24 (Vega), OMT-25 (Telegram / Terabox / Diskwala / manga
cloud), OMT-27 (NBUG-6 DNS) and the network option of OMT-33: per the owner order of 2026-11-19 the agent researches
competitors, decides with the evidence written down, and builds; the owner only tests the result.

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
| OMT-21 | BUG-5 / TV-4: choose how TV search should work (G7 proposal 3) | Decided (agent) |
| OMT-22 | X3-TL: enable the Cloud Testing API and Cloud Tool Results API, and give the service account the Editor role (FTL answered 403, 2026-11-22) | Blocked: owner (console) |
| OMT-23 | X13-6: phone Jackson 2.19.4 → 2.18.11 | Decided (agent) |
| OMT-24 | Vega: what do you mean by "Vega"? | Decided (agent) |
| OMT-25 | Telegram, Terabox, Diskwala and manga cloud path | Decided (agent) |
| OMT-26 | EXT-T1: Back closes a pop-up instead of Source status | Blocked: owner (phone) |
| OMT-27 | NBUG-6: per-feature DNS has measured side effects — build anyway, narrow it, or drop | Decided (agent) |
| OMT-28 | G6-AF: unplug wired headphones / turn off Bluetooth headphones while music plays | Blocked: owner (hardware) |
| OMT-29 | G6-PG2: endless Music Home keeps adding "Similar to" shelves, also after the connection drops | Pending |
| OMT-30 | G8a: a download from a real HLS site with separate audio plays with sound in your gallery app | Pending |
| OMT-31 | NBUG-9: fresh install on your own network loads online content without changing DNS | Pending |
| OMT-32 | NBUG-12: a Porn4fans title plays on your network; tell the extension author | Pending |
| OMT-33 | NBUG-13: choose a network option, then adult hosts without a VPN | Decided (agent) |
| OMT-34 | NBUG-8: the extension that kept loading now stops with a message, and Back leaves at once | Pending |
| OMT-35 | F-07: external players with headers, subtitles and position | Owner/community test (pending) |
| OMT-36 | F-06: volume boost to 200 % | Owner/community test (pending) |
| OMT-37 | F-12: Shorts swipes on a 2–3 GB phone | Owner/community test (pending) |
| OMT-38 | F-10: Kasumi glass theme looks and battery | Owner/community test (pending) |
| OMT-39 | F-17: statistics over three days | Owner/community test (pending) |
| OMT-40 | F-08: Upcoming in your time zone | Owner/community test (pending) |
| OMT-41 | TV-4: TV search online with a real remote | Owner/community test (pending) |
| OMT-42 | NB14-OB1: SVG channel logos in your playlists | Owner/community test (pending) |
| OMT-43 | X13-6: your extensions after the Jackson change | Owner/community test (pending) |
| OMT-44 | PR-1: profiles and the profile lock (also with the TV remote) | Owner/community test (pending) |
| OMT-45 | DL-H: private downloads stay out of the gallery and lists | Owner/community test (pending) |
| OMT-46 | P-2X hold for 2x; TH-1 accent tones | Owner/community test (pending) |
| OMT-47 | NBUG-6: Automatic DNS fallback on a network whose DNS is dead | Owner/community test (pending) |
| OMT-48 | Vega step 4: a real Vega provider repository on a phone and a TV | Owner/community test (pending) |
| OMT-49 | F-02: a public Telegram channel (no login) plays | Owner/community test (pending) |
| OMT-50 | F-03: a TeraBox / DiskWala share link | Owner/community test (pending) |
| OMT-51 | F-11: manga auto-translation on a real chapter | Owner/community test (pending) |
| OMT-52 | F-AIR: airing alert on a Firebase phone (after the Worker deploy) | Owner/community test (pending) |
| OMT-63 | F-02 login: your own Telegram account, private chats, video + audio play and download | Owner/community test (pending; flag f02_telegram_login off until it passes). This is the only full sign-in check: Telegram switched off its public test accounts (tdlib/td#3083), so CI can only test up to Telegram checking the code |
| OMT-53 | NBUG-13: your own HTTP / SOCKS5 proxy for adult hosts | Owner/community test (pending) |
| OMT-54 | Skip-intro times after the TV was offline (final test: AniSkip skipped on TV emulators) | Owner/community test (pending) |
| OMT-55 | App lock with a PIN on a TV (final test: AppLock b_ skipped on TV emulators) | Owner/community test (pending) |
| OMT-56 | G9-CQ-1: "Last backup" time survives a restore that restarts the app | Owner/community test (pending) |
| OMT-57 | FINAL-35: memory on an old Android TV while scrolling drama feeds for 10 minutes | Owner/community test (pending) |
| OMT-58 | FINAL-43: notification permission on an Android 13+ TV after two "Don't allow" | Owner/community test (pending) |
| OMT-59 | FT-B1: smoothness on an Android 7–8 TV (main-thread checks need Android 9 on emulators) | Owner/community test (pending) |
| OMT-60 | FT-B3: Yumix "See all" keeps paging while searching | Owner/community test (pending) |
| OMT-61 | FINAL-46: video plays right after the upgrade; TV data kept | Owner test (pending) |
| OMT-62 | FINAL-47/47b: Settings organised by subject (Profiles, Statistics, Telegram own pages) | Owner/community test (pending) |
| OMT-64 | OL-1 / OL-3 / OL-8: website tab stays open, only the current stream, banner hides | Owner test (pending) |
| OMT-65 | OL-2 / OL-4 / OL-5: website stream panel and download popup with tracks | Owner test (pending) |
| OMT-66 | OL-7: player top bar on a real phone and TV | Owner test (pending) |
| OMT-67 | OL-11 / OL-5: episode range and Sub / Dub download on a real extension | Owner test (pending) |
| OMT-68 | OL-12: a whole YT Music playlist downloads | Owner test (pending) |
| OMT-69 | F-12: vertical Shorts on a real phone | Owner test (pending) |
| OMT-70 | OL-6: the new download popup on a real phone and TV | Owner test (pending) |
| OMT-71 | OL-13: every social button opens the right account in its app | Owner test (pending) |
| OMT-72 | GATE-1: no freeze when switching sources on a real (low-RAM) phone | Owner test (pending) |
| OMT-73 | F-05-N-ON: Nuvio sources on a title | Owner test (pending) |
| OMT-74 | F-05-S: SkyStream plugins | Owner test (pending) |
| OMT-75 | F-05-STREMIO: a Stremio stream add-on gives links | Owner test (pending) |
| OMT-76 | F-22-C: Upcoming by country and genre | Owner test (pending) |
| OMT-77 | F-09-FEED: Watch later / Favorite from Yumix, Top 10, Continue watching | Owner test (pending) |
| OMT-78 | TV-FP-1: Home → Live TV card on an older Android TV | Owner test (pending) |
| OMT-79 | B31-FRAME: built-in torrent first frame and seek on a real phone | Owner test (pending, needs a test build with the engine on) |
| OMT-80 | DL-ONE: the same download popup everywhere | Owner test (pending) |

Status words: Pending (not run yet) · Passed · Failed (send the evidence; the agent fixes it) ·
Blocked (you couldn't run it; say why).

**For every test (owner and users):** capture a screenshot or short screen recording when something looks
wrong, plus a note, **plus the app version** (Settings → About) and the phone/TV model and Android version.
Report with the GitHub issue form "Test report" (Issues → New issue; `.github/ISSUE_TEMPLATE/test-report.yml`) or send results to the owner (developer); recruited users send them to the person who invited them (see
[USER-TEST-PROGRAM.md](USER-TEST-PROGRAM.md)). The owner forwards them to the agent, who records them here,
in `docs/DONE-LEDGER.md` and in `docs/UNVERIFIED-QUEUE.md`.

The tests are in two tracks: the **Yumeiro users** checklist just below (short, no developer tools), and the
**owner (developer)** section after it (OMT-1…OMT-61, full detail; some need consoles, logs or decisions).

## Yumeiro users checklist

Short tasks for people helping to test. No computer or developer tools needed. Install the test build the
owner sends you, then pick any task you can do.

### Mandatory for every tester (do these first)

These seven cover what changed most in 2.0.0 (and the 1.9.0 crash it fixes). Please do all of them before
anything else, and report each one as Passed or Failed:

1. **U-30** Videos open without a crash (YouTube, Live TV, downloads, online episodes).
2. **U-29** Settings are easy to find (new layout).
3. **U-26** Profiles and locks.
4. **U-3** YouTube videos play and change quality.
5. **U-27** Private downloads.
6. **U-24** Telegram channel (no login).
7. **U-20** Your statistics (one day is enough for the first report).

TV owners also: **U-7** TV remote. Updating from 1.8.1: also check that your history, favourites and
downloads are still there after the update (Settings → Downloads & library). Updating from 1.9.0: install 2.0.0 over it; nothing is lost.

| # | Task (what you check) | You need | Steps | It passes if | Same as |
|---|---|---|---|---|---|
| U-1 | Music pauses when headphones are unplugged | phone + wired or Bluetooth headphones | 1. Play a song in Music with headphones on. 2. Unplug them (or switch the Bluetooth headphones off). | the music pauses and does not play from the speaker | OMT-28 |
| U-2 | Music Home keeps loading | phone, your home Wi-Fi | 1. Music → tap the source pill → Online → Home. 2. Scroll to the bottom again and again until shelves called "Similar to …" appear. 3. Turn Wi-Fi off for 10 seconds, then on. 4. Go back to Online Home and keep scrolling. | new "Similar to …" shelves keep appearing after Wi-Fi comes back | OMT-29 |
| U-3 | YouTube videos play and change quality | phone, home Wi-Fi | 1. Open any YouTube video in Yumeiro. 2. Tap Quality and pick each height, one after another. | each pick plays without stopping, and the picture gets sharper or softer | OMT-1, OMT-15 |
| U-4 | Downloads appear in your gallery | phone | 1. Download a video in Yumeiro. 2. When it finishes, open your phone's Gallery or Photos app. | the video is there and plays | OMT-17 |
| U-5 | Downloads keep a good speed | phone, home Wi-Fi | 1. Start a large download (a full episode or film). 2. Watch the speed shown at 10 %, 50 %, 70 %, 90 %. | the speed does not drop sharply after half way | OMT-14, OMT-20 |
| U-6 | Yumeiro with a VPN | phone + any VPN app | 1. Turn the VPN on. 2. Open Home, a video and Music Online. | everything loads, or a clear message says what's wrong | OMT-18 |
| U-7 | TV remote | Android TV + its remote | 1. Move around Home, Search, Live TV, Manga and the player with only the remote's arrows, OK and Back. | you can reach every button, see where you are, and Back always works | OMT-4 |
| U-8 | Low-memory phone | a phone with 2–3 GB RAM | 1. Use Yumeiro for 15 minutes: Home, a video, Music, Manga. 2. Switch to another app and back a few times. | no crash, no long freeze | OMT-3 |
| U-9 | Long playback and heat | phone | 1. Play videos or music for 45 minutes. | the phone does not get very hot and battery use looks normal | OMT-6 |
| U-10 | Manga and Live TV with your own sources | phone or TV, your own playlist / source | 1. Add your playlist or manga source. 2. Open 5 channels or 2 chapters. | channels play and pages load | OMT-11 |
| U-11 | Home screen widget | phone | 1. Add the Yumeiro widget to your home screen. 2. Play music. | the widget shows the song and its buttons work | OMT-8 |
| U-12 | Back gesture animation | phone with Android 14 or newer | 1. Swipe back slowly from a screen inside Yumeiro. | you see the previous screen peek in, and letting go returns there | OMT-7 |
| U-13 | Casting to a TV | phone + Chromecast or Google TV | 1. Play a video and tap Cast. | the video plays on the TV | OMT-2 |
| U-14 | Yumix offline | phone | 1. Open Yumix once online. 2. Turn on airplane mode and open Yumix again. | what you saw before is still there, with a clear "offline" note | OMT-5 |
| U-16 | Open a video in another player | phone with VLC or MX Player installed | 1. Open any Yumix title. 2. Tap ⋮ → Open in… → VLC (or MX). 3. Watch a minute, go back to Yumeiro. | it plays in the other app, and Yumeiro remembers where you stopped | OMT-35 |
| U-17 | Volume boost | phone + headphones | 1. Play a video. 2. Swipe up on the right side past 100 %. | a warning appears once; sound gets louder up to 200 % without crackling | OMT-36 |
| U-18 | Shorts | phone | 1. Videos → YouTube → open a Short. 2. Swipe up through 20 Shorts quickly. 3. Press Back. | each Short starts within about 2 s, no freeze, Back closes the Shorts player | OMT-37 |
| U-19 | Kasumi theme | phone | 1. Settings → Appearance → Theme → Kasumi. 2. Use Home, Yumix and the player for 10 minutes. | bars look see-through, text is easy to read, nothing feels slower | OMT-38 |
| U-20 | Your statistics | phone, three days of normal use | 1. Settings → You → Your statistics after each day. | each day you used Yumeiro is filled in; the streak counts the days | OMT-39 |
| U-21 | Upcoming | phone | 1. Yumix → Upcoming. 2. Open the Anime, Movies and Series tabs. | titles are grouped by day in your own time zone (Today, Tomorrow…) | OMT-40 |
| U-22 | Search online on TV | Android TV + remote | 1. Home → search icon. 2. Type a show name, press Search. 3. Press "Search online for …". | the online search opens with your words and shows results | OMT-41 |
| U-23 | Script repos (Vega) | phone and TV | 1. Sources → Script repos → Add. 2. Paste a Vega manifest.json link you trust. 3. Install one provider. 4. Open it from Home, play a title. | the provider's rows show on Home, a title opens and plays; Source status lists it as Vega | OMT-48 |
| U-24 | Telegram channel | phone | 1. Settings → Sources & accounts → Telegram & web sites. 2. Type a public channel (for example @durov) or paste its t.me link, tap Add. 3. Open the channel, then a video. | it plays without signing in | OMT-49 |
| U-25 | Manga translation | phone | 1. Open a manga chapter in another language. 2. Reader menu → Translate. | text bubbles show in your language; turning it off shows the original | OMT-51 |
| U-26 | Profiles and locks | phone or TV | 1. Settings → You → Profiles → add a second profile and give it a PIN. 2. Close Yumeiro and open it again. 3. Pick that profile, type a wrong PIN once, then the right one. | "Who's watching?" shows first; the wrong PIN is refused; the right one opens that profile with its own history | OMT-44 |
| U-27 | Private downloads | phone | 1. Downloads → tap the eye on a finished video. 2. Open your Gallery. 3. Downloads → the lock at the top → unlock. | the video is gone from lists and the Gallery, and shows inside Private after unlocking | OMT-45 |
| U-28 | Hold for 2x | phone | 1. Play any video. 2. Press and hold on the picture, then let go. | it plays at 2x while you hold and goes back to normal when you let go | OMT-46 |
| U-30 | Videos open without a crash | phone or TV with 2.0.0 | 1. Open a YouTube video, touch the picture, change quality. 2. Live TV → open 3 channels. 3. Downloads → play a finished video. 4. Open an online episode from Yumix. 5. Open the player, minimise it, open it again. | every video opens and plays; the app never closes by itself; no crash report appears in Settings → Help & about | OMT-61 |
| U-29 | Settings are easy to find | phone or TV | 1. Open Settings. 2. Find Profiles, Your statistics, Telegram, Downloads, Video quality and Music & sound without searching. 3. Search "telegram", "profile", "subtitle". | each subject has its own page in the group you expect (You, Video, Music, Downloads & library, Sources & accounts, System, About); search finds each one | OMT-62 |
| U-15 | General feedback | phone or TV | Use Yumeiro as you normally would for a day. | tell us anything confusing, slow or broken | OMT-9 |

Report for each task: task number, Passed/Failed, one sentence, a screenshot if something looked wrong,
the app version and your device. Don't send passwords or account details.

## Owner (developer) tests

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
- **Hardware / environment:** your phone with internet, a Last.fm account. Since SEC-LFM (ADR-048) the built-in key
  is signed by Yumeiro Cloud, so run this after the Worker deploy (§12); with your own Last.fm API key and secret it
  works before the deploy too. Before the deploy, the built-in sign-in shows a "try again later" message and the
  plays stay queued.
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
- **Instructions:** install the 1.9.0 release candidate review APK. Add one download of 300–600 MB from a source that
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
- **Status:** Decided by the agent 2026-11-19: option B, built (ADR-036, TV-4); retest = OMT-41.

---

### OMT-22 — X3-TL: Firebase Test Lab role (console)

- **Why:** real-device cloud runs would turn some owner tests into agent tests. The service account mints a
  token, but Test Lab answers 403 (ledger X3-TL, 2026-11-16).
- **Instructions:** Google Cloud console → IAM → the service account → add role "Firebase Test Lab Admin"
  (`roles/cloudtestservice.testAdmin`) or "Editor"; APIs → enable "Cloud Testing API" and "Cloud Tool Results API".
  Links (project yumeiro-ab29e): [Cloud Testing API](https://console.cloud.google.com/apis/library/testing.googleapis.com?project=yumeiro-ab29e),
  [Cloud Tool Results API](https://console.cloud.google.com/apis/library/toolresults.googleapis.com?project=yumeiro-ab29e),
  [IAM](https://console.cloud.google.com/iam-admin/iam?project=yumeiro-ab29e) for
  firebase-adminsdk-fbsvc@yumeiro-ab29e.iam.gserviceaccount.com. Re-checked 2026-11-22: still 403. After it, the
  agent runs `gh workflow run ftl-gate.yml --ref r15-stability` (GATE-1 on real phones).
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
- **Status:** Decided by the agent (F-05 proposal §2): Vega = the Vega app's provider repositories, not Amazon Vega OS. Running them is not in this release (ADR-037).

---

### OMT-25 — Telegram, Terabox, Diskwala and manga cloud path (decision)

- **Now:** Terabox and Diskwala links open a hidden WebView that catches the real media request
  (BEHAVIOR-SPEC "Cloud links"). Telegram and manga translation are not built (F-02, F-03, F-11).
- **Proposal:** each comes as its own gated feature note (docs/proposals/FEATURE-*.md) with the site terms,
  what data leaves the phone, and an off-by-default switch. Telegram needs your own API id (user login
  through TDLib), so it would be "bring your own account"; manga translation would run on-device first.
- **What to send back:** which of the four to plan, and whether a user login (Telegram) is acceptable.
- **Status:** Decided by the agent 2026-11-19: F-02, F-03 login path and F-11 are not in this release (ADR-037); the public-link WebView path for TeraBox/DiskWala stays.

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
- **Status:** Decided by the agent 2026-11-19: not built (side effects measured; ADR-037).

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

### OMT-30 — A real HLS download with separate audio (G8a)

- **Why you:** G8-2 (separate audio track merged into the saved file) is verified on the emulators with a local
  HLS fixture (DownloadG8DeviceTest a, device 44278624792 and regression 44309363764). Real sites (for example
  a multi-language anime source) are not reachable from CI the same way, and the gallery app is yours.
- **Steps:** on your phone, open a video from a source that offers more than one audio language, download it,
  wait until it says Done, then open it in your phone's Gallery / Photos app.
- **Pass:** it plays with picture and sound in the gallery app. **Fail:** no sound, no picture, or it does not
  open (send the source name, the title and a screen recording).
- **Record:** source, title, audio language picked, app version, phone model and Android version.
- **Status:** Pending

### OMT-31 — Fresh install on your own network (NBUG-9)

- **Why you:** NBUG-9 was not reproduced on the emulators. Probe 44312163084 broke the emulator's resolver
  (every system lookup fails): with the fresh-install setting (System DNS + auto bypass) music.youtube.com,
  gstatic and api.github.com all loaded through the encrypted-DNS bypass, ~4.5 s extra for the first request
  to each site. Your provider's resolver may answer differently (a block page, or no answer for a long time),
  which only your network shows.
- **Steps:** 1. On your home network (Wi-Fi or mobile data, the one where it happened), Settings → Apps →
  Yumeiro → Storage → Clear all data. 2. Open Yumeiro, finish the welcome screens, leave Settings → Network →
  DNS as it is (it reads "System DNS · auto bypass for blocked sites"). 3. Open Music → Online, then Yumix Home. Wait up to 30 s.
  4. If nothing loads: Settings → Network → DNS → Cloudflare, try again and note the difference.
- **Pass:** music and Yumix load within 30 s without touching DNS. **Fail:** nothing loads until Cloudflare is
  picked.
- **Record:** provider name, Wi-Fi or mobile data, whether Android's Private DNS is on (Settings → Network →
  Private DNS), how long you waited, and a screen recording of steps 3 and 4.
- **Failed attempts (agent):** none failed; the emulator only produces the "resolver says no" shape (above).
- **Status:** Pending

### OMT-32 — Porn4fans on your network (NBUG-12)

- **Why you:** the fix is verified against a local copy of a KVS site on the emulators; the real site, your
  network and your installed extension version only show on your phone.
- **Steps:** 1. Install the build with the GROUP-14 fix. 2. Settings → 18+ on. 3. Open any Porn4fans title and
  tap Play. 4. Wait up to 60 s (the app first tries the extension's link, then the title page).
- **Pass:** the video plays. **Fail:** "Access denied" or "Every source refused playback".
- **Also:** the extension (phisher98/CXXX, Porn4fans) reads a link the site now refuses for everyone (NB12-2);
  please tell its author, so the extension works without Yumeiro's fallback.
- **Record:** with and without your VPN, and a screen recording of a failure.
- **Status:** Pending

### OMT-33 — Adult hosts without a VPN (NBUG-13)

- **Why you:** the CI network blocks nothing. Yumeiro already bypasses DNS blocking on its own connections
  (Settings → Network → DNS, auto bypass); blocking by address or by site name (common in Bangladesh) can
  only be passed by a proxy or a tunnel, which is a new feature.
- **Decision needed:** (1) a proxy setting in Yumeiro (HTTP/SOCKS5, also for the in-app web pages); (2) DNS
  bypass also for in-app web pages (helps only DNS blocking); (3) keep using a system VPN or Private DNS.
  See docs/SYSTEM-DESIGN.md ADR-024.
- **Then:** retest an adult title without the VPN on your network.
- **Status:** Decided by the agent 2026-11-19: option 3 for this release (system VPN or Private DNS); a proxy is not in this release (ADR-037). The retest still applies.

### OMT-34 — The extension that kept loading (NBUG-8)

- **Why you:** you saw it with one extension on your phone; the fix (G10 ER-30) is proven with a fake extension
  that blocks (unit test), but not with yours.
- **Steps:** 1. Install the build with the G10 fix. 2. Open the same extension's page and the same title as before.
  3. If it is slow, wait, then press Back once.
- **Pass:** within about 25 s (home) or 35 s (a title) you see either the content or a message with Retry; Back
  leaves at once. **Fail:** the spinner stays longer, or Back does nothing.
- **Record:** the extension name and version, the title, and a screen recording.
- **Status:** Pending

### OMT-35 — External players (F-07)

- **Why you:** CI emulators have no VLC, mpv, MX or Just Player installed. **Hardware:** phone + one of them.
- **Steps:** 1. Open a Yumix title with a header-protected source. 2. ⋮ → Open in… → the player. 3. Watch 2 minutes, go back.
- **Pass:** it plays with subtitles; Yumeiro resumes at the position the player returned. **Fail:** error, no
  subtitles, or the position is lost. **Record:** player name and version, source name. **Status:** Owner/community test (pending)

### OMT-36 — Volume boost (F-06)

- **Why you:** loudness and distortion can only be judged with real speakers and ears. **Hardware:** phone + headphones.
- **Steps:** swipe volume past 100 % to 200 %, then back down. **Pass:** one hearing warning, louder without
  crackling, boost drops first on the way down. **Status:** Owner/community test (pending)

### OMT-37 — Shorts on a low-RAM phone (F-12)

- **Why you:** swipe feel and real decoders on a 2–3 GB phone. **Steps:** U-18 with 50 Shorts.
- **Pass:** no freeze or crash; memory warning absent in `adb logcat -s YuMemory`. **Status:** Owner/community test (pending)

### OMT-38 — Kasumi glass theme (F-10)

- **Why you:** looks and battery on a real screen. **Steps:** U-19 for one hour with battery saver off and on.
- **Pass:** readable text, solid bars with battery saver on, no extra heat. **Status:** Owner/community test (pending)

### OMT-39 — Statistics over days (F-17)

- **Why you:** streaks need real days. **Steps:** U-20. **Pass:** days and streak correct; incognito days not
  counted; "Clear statistics" empties it. **Status:** Owner/community test (pending)

### OMT-40 — Upcoming in your time zone (F-08)

- **Steps:** U-21 on your phone in Asia/Dhaka. **Pass:** day groups match your local dates. **Status:** Owner/community test (pending)

### OMT-41 — TV search online (TV-4)

- **Hardware:** a real Android TV and remote. **Steps:** U-22. **Pass:** the button is reachable with the remote
  and opens the online search. **Status:** Owner/community test (pending)

### OMT-42 — SVG channel logos (NB14-OB1)

- **Steps:** open Live TV with your own playlists. **Pass:** channels whose logo is an SVG show the logo, not a
  blank tile. **Status:** Owner/community test (pending)

### OMT-43 — Extensions after the Jackson change (X13-6)

- **Steps:** open each extension you use (home, a title, Play). **Pass:** they all work as before.
  **Record:** any extension that fails, with its version. **Status:** Owner/community test (pending)

### OMT-44 — Profiles and profile lock (PR-1)

- **Steps:** Settings → Privacy → Profiles: add "Kid", give your own profile a PIN, restart the app. On "Who's
  watching?" try the remote's Play / OK and arrows: nothing behind the screen may react. Open Kid, watch something,
  switch back with the PIN. Wrong PIN 5 times: wait message. In Kid, try to add a profile or turn 18+ on: your PIN is
  asked. **Pass:** each profile shows only its own history, lists and 18+ setting; the lock can't be skipped.
  **Status:** Owner/community test (pending)

### OMT-45 — Private downloads (DL-H)

- **Steps:** Downloads → eye button on a finished video → it leaves the list; open your phone's gallery: it is not
  there. Open Private with the PIN or phone lock, play it, press Home and come back: Private is closed. Turn stealth on
  (Settings → Privacy) and open Private with a long press on the storage card. **Pass:** never in the gallery, never in
  any list, screenshots of Private are black. **Status:** Owner/community test (pending)

### OMT-46 — Hold for 2x and accent tones (P-2X, TH-1)

- **Steps:** hold a finger on a playing video: 2x while held, back to your speed on release; long-press the speed
  button: 1x / 2x. Settings → Appearance → Accent: try Ao (blue) and the others. **Pass:** smooth on a 2–3 GB phone,
  readable text in every accent. **Status:** Owner/community test (pending)

### OMT-47 — Automatic DNS fallback (NBUG-6 F1)

- **Steps:** Settings → Network → turn "Automatic DNS fallback" on. On a network whose DNS fails (for example a
  router with a dead DNS server, or Private DNS set to a name that doesn't exist), open Home and a source. Then turn it
  off and repeat. **Pass:** On: pages load after one short wait (the first lookup takes at most your phone's own DNS
  timeout plus about 7 s), later pages without waiting. Off: same as before this release. **Status:** Owner/community
  test (pending)

### OMT-48 — A real Vega repository (Vega step 4)

- **Steps:** Sources → Script repos → Add → paste a Vega `manifest.json` you trust. Install two providers. Use Home,
  Search, a movie and a series episode; on the TV, do the same with the remote only. Uninstall one. **Pass:** rows and
  search work, episodes play, a provider that is slow stops with a message after about 25 s, Source status lists them
  as Vega, uninstall removes the source. **Status:** Owner/community test (pending)

### OMT-49 — Public Telegram channel (F-02)

- **Steps:** Universal add → a public `t.me/<channel>` link with videos → open and play one on a phone and a TV.
  **Pass:** plays without any Telegram login; private channels say they can't be opened. **Status:** Owner/community
  test (pending)

### OMT-50 — TeraBox / DiskWala link (F-03)

- **Steps:** paste a public TeraBox or DiskWala share link. **Pass:** a link the site lets anyone open plays or
  downloads; a link that needs the site's login or app says so and nothing is bypassed. **Status:** Owner/community
  test (pending)

### OMT-51 — Manga auto-translation (F-11)

- **Steps:** open a chapter in Japanese, Korean or Chinese → Translate. Read ten pages, then turn it off. **Pass:**
  bubbles are readable and placed over the right text; no freeze on a 2–3 GB phone. **Status:** Owner/community test
  (pending)

### OMT-52 — Airing alert on a Firebase phone (F-AIR)

- **Steps (after the owner's go and the Worker deploy):** follow an airing show on a phone that uses Firebase push
  (not UnifiedPush), close the app, wait for the next episode. **Pass:** the alert arrives within about an hour of
  release. **Status:** Owner/community test (pending)

### OMT-53 — Your own proxy (NBUG-13)

- **Steps:** Settings → Network → Proxy: enter your HTTP or SOCKS5 proxy (with login if it needs one), open an adult
  host that your network blocks, then turn the proxy off. **Pass:** with the proxy the site loads in the app and in its
  web pages; off, the app goes direct again. **Status:** Owner/community test (pending)

### OMT-54 — Skip-intro times after the TV was offline (final test, AniSkip on TV)

- **Steps:** on your Android TV, unplug the network cable or turn Wi-Fi off, open an anime episode, then reconnect
  and open the same episode again. **Pass:** offline the episode plays without skip buttons; once online again the
  "Skip intro" button appears (the offline failure was not remembered). Why you: both TV emulators use emulated
  Ethernet that a test cannot switch off (MASTER-CHECKLIST-FINAL, skips). **Status:** Owner/community test (pending)

### OMT-55 — App lock with a PIN on a TV (final test, AppLock on TV)

- **Steps:** only if your TV has a screen lock (PIN): Settings → Privacy → App lock on, leave the app, open it again.
  **Pass:** the app asks for the TV's PIN and opens after it; without a screen lock the switch explains why it is off.
  Why you: the TV emulators cannot set a PIN from a test. **Status:** Owner/community test (pending)

### OMT-56 — "Last backup" time after a restore restarts the app (G9-CQ-1)

- **Steps:** Settings → Backup → back up now, then restore that backup (the app restarts on its own). Open Settings → Backup again.
  **Pass:** "Last backup" shows the time of the backup you just made, not an older time or "never".
  Why you: the restart kills the process at a moment a test cannot reproduce the same way every time. **Status:** Owner/community test (pending)

### OMT-57 — Memory on an old Android TV while scrolling drama feeds (FINAL-35)

- **Steps:** On an Android 7–9 TV box, add the drama source, open Drama and switch tabs and pages with the remote for
  about 10 minutes (go to page 2 and further, back to the rail, other tabs). **Pass:** the app stays responsive, no
  "app isn't responding" dialog, no crash. If it slows down or freezes, send the "Send logs" report from Settings
  → About right after. Why you: seen once on the tv-api24 emulator while the source's server answered 503, not
  reproduced since. **Status:** Owner/community test (pending)

### OMT-58 — Notification permission on an Android TV, said "no" twice (FINAL-43)

- **Steps:** on an Android 13+ TV with a fresh install: when Yumeiro asks to send notifications, choose "Don't
  allow". Open Settings → Notifications in Yumeiro and turn an alert on again; choose "Don't allow" a second time.
  **Pass:** after each "Don't allow" Yumeiro shows a short message that notifications are off and how to turn them
  on; no crash, and the app keeps working. Why you: on the TV emulator the message after the second "Don't allow"
  was not seen within 10 seconds (cause not yet found). **Status:** Owner/community test (pending)

### OMT-59 — Smoothness on an Android 7–8 TV (final test, main-thread checks)

- **Steps:** on an Android 7 or 8 TV box: open Library (all tabs), Manga library pages, Inbox, Settings → Storage and
  Settings → About → Check for updates. **Pass:** nothing freezes or stutters for more than a moment. Why you: the
  automatic main-thread checker needs Android 9, so it could not run on the Android 7 TV emulator. **Status:**
  Owner/community test (pending)

### OMT-60 — Yumix "See all" keeps paging while you search (final test)

- **Steps:** Yumix → open a row with many titles → See all. Type a word in the search box and scroll to the
  bottom of the results, twice. **Pass:** more results keep loading at the bottom; nothing repeats or jumps.
  Why you: on the CI machines every row had a single page, so the second page never loaded. **Status:**
  Owner/community test (pending)

### OMT-61 — 1.9.0 opens a video right after installing over 1.8.x, TV data kept (final test, FINAL-46)

- **Steps:** on the phone and on the TV, after installing the review APK over 1.8.x: open one video you had
  half-watched (Continue watching or History) and one new video. On the TV also check that your library,
  extensions and settings are still there. **Pass:** both videos play (the half-watched one from where you
  stopped) and nothing is missing. Why you: a test build made with different inputs crashed in the player
  (FINAL-46); the exact review APKs passed on the emulators, but the TV emulator gives no root, so the TV data
  comparison could not run. **Status:** Owner test (pending)

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

### OMT-62 — Settings organised by subject (FINAL-47, FINAL-47b)

On a phone and on the TV, after updating from 1.8.1:

1. Open Settings. The groups read You, Video, Music, Downloads & library, Sources & accounts, System, About.
2. You → Profiles opens the profiles list (add, rename, PIN, 18+ per profile). It is no longer inside Privacy.
3. You → Your statistics opens the week, streak and activity map.
4. Sources & accounts → Telegram & web sites: type @durov, tap Add, open it; posts load without signing in.
5. Video → Video quality & playback, Video → Subtitles & intros, Music → Music & sound each hold only their subject.
6. Downloads & library → Downloads holds the Private downloads rows.
7. The Inbox shows one "What's new in 1.9" note that names these pages (only after an update, not a fresh install).

Passes if every step shows what it says, Back returns to Settings each time, and search finds each page.

### OMT-63 — Your own Telegram account (F-02 login)

On a phone with a debug build that has the Telegram keys, and the flag `f02_telegram_login` turned on (no menu for flags: `adb shell run-as dev.asfakul.yumeiro` and set `f02_telegram_login` to true in shared_prefs/feature_flags.xml, or ask the agent for a build with it on):

1. Sources & accounts → Telegram & web sites → "Your Telegram account". The first time, a bar shows "Downloading Telegram support" and reaches 100 %.
2. Type your number (paste works), tap Next. Type the code Telegram sends to your other device. If you have a cloud password, type it.
3. Your chats appear: a private channel you joined, a group, and a public channel. The search box filters them.
4. Open each one. Videos and audio files (music, voice notes) are listed; search finds a file by name.
5. Tap a video: it plays in Yumeiro's player and seeking forward works. Tap an audio file: it plays.
6. Tap Download on one video and one audio file. Both appear in Downloads and play with the phone in airplane mode.
7. Close and reopen the app: you are still signed in. Log out from the account screen: you are back at the phone step.

Passes if every step does what it says. Note: a download started from Telegram can't resume after the app is closed; start it again from the chat.

### OMT-64 — Website tab stays open (OL-1, OL-3, OL-8)

On a phone and on the TV, on a real site you are signed in to:

1. Websites → open the site, sign in, open a video page. The helper banner hides by itself after about 3 s.
2. Play the video in Yumeiro's player, then close the player: the same page is back, still signed in, same scroll.
3. Start a download from the page, tap "View downloads" in the message, then Back: the page is still there.
4. Tap the down arrow (Minimize). The Websites page shows an "Open tab" card; Resume brings the page back as it was.
5. TV: Back on the first page hides the tab (it does not close it); the "Open tab" card resumes it.
6. Play a second video on the same page: the stream list shows only the new one, named "Now playing: <its title>".

Passes if every step does what it says.

### OMT-65 — Website stream panel and download popup (OL-2, OL-4, OL-5)

On a real site that plays HLS with more than one audio track or subtitles (for example an anime site with Sub / Dub):

1. The stream panel has Rescan, a down arrow (collapse) and Close. Close leaves a small "Streams · n" pill that reopens it.
2. Tap Download on a stream: the popup asks Offline copy or Permanent, every time.
3. The popup shows Quality chips with the stream's real sizes, an Audio track row and a Subtitles row.
4. Pick a non-default audio track and one subtitle, download. Open it from Downloads in airplane mode: that audio plays and the subtitle is in the captions menu.

Passes if every step does what it says. A site with only one track shows no Audio row (nothing to choose).

### OMT-66 — Player top bar (OL-7)

On a real phone (portrait and landscape) and the TV, play any video: Quality, Audio and the 3-dot buttons sit at the right edge, the title fills the rest, no big empty gap.

### OMT-67 — Episode range and Sub/Dub download (OL-11, OL-5)

On a real extension title with 80+ episodes and Sub / Dub versions:

1. Download → the sheet shows "Version" chips (Sub / Dub); pick Dub, the lists below follow it.
2. "Episode range": From 50 To 80 → "Download 31 episodes" (fewer if some are saved). Start, pick Permanent.
3. Downloads shows 50..80 queued in order, in the Dub folder; the quality picked applies to all.
4. "This episode only" saves just the episode Play / Continue opens.
5. With Subtitles "Save all the source has", a finished episode plays offline with its subtitles in the captions menu.

### OMT-68 — Download a whole YT Music playlist (OL-12)

On a real YouTube Music playlist with 100+ songs:

1. Online Music → open the playlist. A round Download button sits next to Play / Shuffle (not on artist pages).
2. Tap it, pick Offline copy. Every song is queued (not only the rows on screen); the ring and "Downloading · n of N done" move.
3. Tap again when done: nothing is downloaded twice. Repeat with Permanent on another playlist.

Passes if all songs land in Downloads and play in airplane mode.

### OMT-69 — Vertical Shorts on a real phone (F-12)

Play a 9:16 clip that is not from YouTube on a real phone: it goes full screen portrait; swipe up / down changes clip; Back returns to the normal player and the clip keeps playing. (Same as OMT-37, now with the flag on by default.)

### OMT-70 — New download popup (OL-6)

On a real phone (small screen if you have one) and the TV, start any download that asks first (an episode, a website video, a YouTube video):

1. Phone: a bottom sheet slides up; TV: a centred dialog the remote can move through.
2. One line at the top says the quality, the size and the free space. Offline copy / Permanent is one switch with one line under it.
3. "Where it goes and tips" is folded; tap it: the text opens and the sheet scrolls so you can read it.
4. The Download button stays on screen the whole time, even with many quality / audio / subtitle chips.

Passes if every step does what it says and nothing is cut off.

### OMT-71 — Social links (OL-13)

Settings → About on a real phone with Facebook, Instagram, X, TikTok, Reddit, Discord and Telegram installed (any you have):

1. Community: Telegram channel, Telegram group, then a row Discord · Facebook · Instagram · X · TikTok · Reddit, then Website and GitHub.
2. Each button opens @officialyumeiro (Discord: the Yumeiro server invite) in its app, or in the browser if the app is missing.
3. Developer: Website, GitHub, Facebook (siam1p), Instagram (asfakul_siam), Telegram (@siamorwho); each opens the right profile.

Passes if every button lands on the right account.

### OMT-72 — No freeze when switching sources (GATE-1)

On a real phone (a low-RAM one if you have it), with many extensions installed:

1. Open the app, tap Videos, then Source → Yumix → Source → YouTube → Source → Live TV → Home.
2. Do it twice: once right after installing / updating, once after a normal start.

Passes if no tap leaves the screen stuck for about a second or more, and Android never shows "Yumeiro isn't responding".

### OMT-73 — Nuvio sources on a title (F-05-N-ON)

1. Sources → Add (or Script repos), paste a Nuvio repository manifest URL you trust (one that lists scrapers).
2. Open a movie, then a TV episode, and press Play / Sources.

Passes if the repo is added (not "recognised only"), the source list shows extra rows named "Nuvio · <scraper name>",
one of them plays, and a broken scraper only shows its own error while the other sources still appear.

### OMT-74 — SkyStream plugins (F-05-S)

Only after the agent turns `scripts_skystream` on (it says so in the handover).

1. Sources → Add, paste a SkyStream repository URL you trust (a repo.json with pluginLists).
2. Sources → Script repos: install one plugin, then open its source on Home.
3. Search something, open a title, press Play.

Passes if the repo is added with its plugin count, the source shows rows (Trending first), search finds titles, and a title plays. A plugin that needs a captcha shows that message instead of hanging.

### OMT-75 — Stremio stream add-on (F-05-STREMIO)

1. Sources → Add, paste the manifest URL of a Stremio add-on you trust that gives streams (its manifest lists
   "stream" in resources), for example a public HTTP-link add-on.
2. Open any movie or series episode that has an IMDb id (most TMDB/IMDb titles) and open the sources list.
3. Press one link marked "Stremio · <add-on name>".

Passes if the add-on's links appear with quality, a direct link plays, and a torrent-only result shows as
"Torrent" (skipped) instead of failing. Yumeiro never logs in or unlocks paid content for an add-on.

### OMT-76 — Upcoming by country and genre (F-22-C)

1. Online → Upcoming → Series.
2. Tap JP, then KR, then Streaming; on each, tap a genre chip (e.g. Drama, Anime).
3. Close the app, open Upcoming again.

Passes if each country lists only its shows with a "JP • Anime, Fantasy"-style badge and local air times, the
genre chip narrows the list, the credit line says TVmaze, and the last country and genre are kept after reopening.

### OMT-77 — Watch later / Favorite from every poster row (F-09-FEED)

1. Phone: Online home. Long-press a Yumix feed card, a Top 10 poster and a Continue watching card.
2. Each time pick Favorite (or Watch later). Then tap the same card once.
3. TV: focus each of those cards with the D-pad and press Menu.

Passes if every long press (phone) and Menu press (TV) opens the Watch later / Favorite sheet, the choice shows
the small mark on the poster and in Library, and a normal tap still opens the title (no sheet).

### OMT-78 — Home → Live TV on an older Android TV (TV-FP-1)

1. On an Android TV box (Android 7 to 9 if you have one, else any TV), open Home.
2. Move to the "Live TV" card with the D-pad and press OK.

Passes if the Live TV channel grid opens (not the Music page). If Music opens, note the TV model and Android version.

### OMT-79 — Built-in torrent: first frame and seek on a real phone (B31-FRAME)

The emulator cannot decode the test file (HEVC 10-bit), so this needs a real phone. Needs a test build with the
built-in torrent engine on (flag b31_builtin_torrent; it is off in the release).

1. Play magnet:?xt=urn:btih:BF2063A5E9A69F14716288FCA7C2D6078BDA7369 with a stopwatch.
2. When the picture shows, drag the seek bar to the middle.

Passes if the first picture shows within 15 s and the picture plays again within 5 s after the seek.

### OMT-80 — The same download popup everywhere (DL-ONE)

1. Download from the player (Download button), from an episode list, from a website link, from Telegram, from a YouTube video, from a YouTube song and from a YT Music playlist.
2. Each time the same popup opens (Quality, Audio track, folder) and the file lands in Downloads.
3. YouTube: no separate quality list appears first; "Audio only" saves just the sound.
4. Note: a plain file link inside an in-app web page is not caught yet (DL-WEB).
