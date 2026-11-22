# Yumeiro 2.1.0 manual test list (owner and users)

Who: the owner and any Yumeiro user with a real phone or TV. Why: automatic tests ran on emulators; these checks need a real
device, a real network, your own accounts or a person looking at the screen.

Build: the 2.1.0 soft-test APK shared by the developer (or the newest `yumeiro-review-apk` artifact of a green `ci.yml`
run on `r15-stability`). Install over your current Yumeiro; your data stays (back up first: Settings → Backup & restore).

How to report: for each test ID say Pass or Fail and what you saw (a screen recording helps). Send it in the Telegram
group or open a GitHub issue titled with the test ID. On a freeze or crash: Settings → Crash reports → Share.

Full step lists for older tests: docs/OWNER-MANUAL-TESTS.md (same IDs).

## Checklist

| Test | What | Result |
|---|---|---|
| OMT-31 | Fresh install on your own network (NBUG-9) | |
| OMT-3 | Low-RAM phone | |
| OMT-5 | Yumix offline on a real phone | |
| OMT-72 | No freeze when switching sources (GATE-1) | |
| OMT-34 | The extension that kept loading (NBUG-8) | |
| OMT-60 | Yumix "See all" keeps paging while you search (final test) | |
| OMT-73 | Nuvio sources on a title (F-05-N-ON) | |
| OMT-74 | SkyStream plugins (F-05-S) | |
| OMT-70 | New download popup (OL-6) | |
| OMT-65 | Website stream panel and download popup (OL-2, OL-4, OL-5) | |
| OMT-67 | Episode range and Sub/Dub download (OL-11, OL-5) | |
| OMT-68 | Download a whole YT Music playlist (OL-12) | |
| OMT-14 | Download speed on your phone with Wi-Fi power saving (BUG-3 T5) | |
| OMT-64 | Website tab stays open (OL-1, OL-3, OL-8) | |
| OMT-50 | TeraBox / DiskWala link (F-03) | |
| OMT-49 | Public Telegram channel (F-02) | |
| OMT-63 | Your own Telegram account (F-02 login) | |
| OMT-66 | Player top bar (OL-7) | |
| OMT-69 | Vertical Shorts on a real phone (F-12) | |
| OMT-37 | Shorts on a low-RAM phone (F-12) | |
| OMT-35 | External players (F-07) | |
| OMT-36 | Volume boost (F-06) | |
| OMT-44 | Profiles and profile lock (PR-1) | |
| OMT-45 | Private downloads (DL-H) | |
| OMT-47 | Automatic DNS fallback (NBUG-6 F1) | |
| OMT-53 | Your own proxy (NBUG-13) | |
| OMT-18 | Yumeiro behind a VPN (G10 network states) | |
| OMT-4 | Android TV with the remote | |
| OMT-59 | Smoothness on an Android 7–8 TV (final test, main-thread checks) | |
| OMT-71 | Social links (OL-13) | |
| OMT-75 | F-05-STREMIO: a Stremio stream add-on gives links | |
| OMT-76 | F-22-C: Upcoming by country and genre | |
| OMT-77 | F-09-FEED: Watch later / Favorite from Yumix, Top 10, Continue watching | |
| OMT-78 | TV-FP-1: Home → Live TV card on an older Android TV | |
| OMT-79 | B31-FRAME: built-in torrent first frame and seek on a real phone | |
| OMT-80 | DL-ONE: the same download popup everywhere | |
| OMT-81 | DL-WEB: a file link inside a website opens the download popup | |
| OMT-82 | ST-17-BACK: one Back leaves a vertical video | |
| OMT-83 | OWN-2 / OWN-7: fresh install, skip the tour, online works; Cloudflare DNS by default | |
| OMT-84 | OWN-10: TeraBox link signed in plays; a human check opens the TeraBox page | |
| OMT-85 | OWN-1: screen recording of a page that closes by itself (My List / Reading / Yumix -> player) | |
| OMT-86 | OWN-8 / OWN-4 / OWN-5: download choices, Vega link in the Extension market box, script sources on Extensions | |
| OMT-87 | OWN-11: YouTube video -> Download offers "Save as: Video / Audio only", also with "Ask before download" off | |
| OMT-88 | OWN-12: profile PIN: first PIN leads to security questions; Change / Remove PIN asks the current PIN | |
| OMT-89 | OWN-1 / OWN-6: double taps open one page; Yumix -> details opens fast, no feed flash | |
| OMT-90 | OWN-3: Telegram sign-in with your own API ID / hash | |
| OMT-91 | OWN-9: fresh install shows Yumeiro Classics; a classic film plays | |
| OMT-92 | OWN-10: TeraBox share from 1024terabox / terabox.app plays when signed in | |
| OMT-9 | Usability feedback | |

## 1. Install and first start (everyone)

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

### OMT-72 — No freeze when switching sources (GATE-1)

On a real phone (a low-RAM one if you have it), with many extensions installed:

1. Open the app, tap Videos, then Source → Yumix → Source → YouTube → Source → Live TV → Home.
2. Do it twice: once right after installing / updating, once after a normal start.

Passes if no tap leaves the screen stuck for about a second or more, and Android never shows "Yumeiro isn't responding".

## 2. Feeds, extensions and search speed

### OMT-34 — The extension that kept loading (NBUG-8)

- **Why you:** you saw it with one extension on your phone; the fix (G10 ER-30) is proven with a fake extension
  that blocks (unit test), but not with yours.
- **Steps:** 1. Install the build with the G10 fix. 2. Open the same extension's page and the same title as before.
  3. If it is slow, wait, then press Back once.
- **Pass:** within about 25 s (home) or 35 s (a title) you see either the content or a message with Retry; Back
  leaves at once. **Fail:** the spinner stays longer, or Back does nothing.
- **Record:** the extension name and version, the title, and a screen recording.
- **Status:** Pending

### OMT-60 — Yumix "See all" keeps paging while you search (final test)

- **Steps:** Yumix → open a row with many titles → See all. Type a word in the search box and scroll to the
  bottom of the results, twice. **Pass:** more results keep loading at the bottom; nothing repeats or jumps.
  Why you: on the CI machines every row had a single page, so the second page never loaded. **Status:**
  Owner/community test (pending)

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

## 3. Downloads

### OMT-70 — New download popup (OL-6)

On a real phone (small screen if you have one) and the TV, start any download that asks first (an episode, a website video, a YouTube video):

1. Phone: a bottom sheet slides up; TV: a centred dialog the remote can move through.
2. One line at the top says the quality, the size and the free space. Offline copy / Permanent is one switch with one line under it.
3. "Where it goes and tips" is folded; tap it: the text opens and the sheet scrolls so you can read it.
4. The Download button stays on screen the whole time, even with many quality / audio / subtitle chips.

Passes if every step does what it says and nothing is cut off.

### OMT-65 — Website stream panel and download popup (OL-2, OL-4, OL-5)

On a real site that plays HLS with more than one audio track or subtitles (for example an anime site with Sub / Dub):

1. The stream panel has Rescan, a down arrow (collapse) and Close. Close leaves a small "Streams · n" pill that reopens it.
2. Tap Download on a stream: the popup asks Offline copy or Permanent, every time.
3. The popup shows Quality chips with the stream's real sizes, an Audio track row and a Subtitles row.
4. Pick a non-default audio track and one subtitle, download. Open it from Downloads in airplane mode: that audio plays and the subtitle is in the captions menu.

Passes if every step does what it says. A site with only one track shows no Audio row (nothing to choose).

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

## 4. Websites and cloud links

### OMT-64 — Website tab stays open (OL-1, OL-3, OL-8)

On a phone and on the TV, on a real site you are signed in to:

1. Websites → open the site, sign in, open a video page. The helper banner hides by itself after about 3 s.
2. Play the video in Yumeiro's player, then close the player: the same page is back, still signed in, same scroll.
3. Start a download from the page, tap "View downloads" in the message, then Back: the page is still there.
4. Tap the down arrow (Minimize). The Websites page shows an "Open tab" card; Resume brings the page back as it was.
5. TV: Back on the first page hides the tab (it does not close it); the "Open tab" card resumes it.
6. Play a second video on the same page: the stream list shows only the new one, named "Now playing: <its title>".

Passes if every step does what it says.

### OMT-50 — TeraBox / DiskWala link (F-03)

- **Steps:** paste a public TeraBox or DiskWala share link. **Pass:** a link the site lets anyone open plays or
  downloads; a link that needs the site's login or app says so and nothing is bypassed. **Status:** Owner/community
  test (pending)

### OMT-49 — Public Telegram channel (F-02)

- **Steps:** Universal add → a public `t.me/<channel>` link with videos → open and play one on a phone and a TV.
  **Pass:** plays without any Telegram login; private channels say they can't be opened. **Status:** Owner/community
  test (pending)

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

## 5. Player

### OMT-66 — Player top bar (OL-7)

On a real phone (portrait and landscape) and the TV, play any video: Quality, Audio and the 3-dot buttons sit at the right edge, the title fills the rest, no big empty gap.

### OMT-69 — Vertical Shorts on a real phone (F-12)

Play a 9:16 clip that is not from YouTube on a real phone: it goes full screen portrait; swipe up / down changes clip; Back returns to the normal player and the clip keeps playing. (Same as OMT-37, now with the flag on by default.)

### OMT-37 — Shorts on a low-RAM phone (F-12)

- **Why you:** swipe feel and real decoders on a 2–3 GB phone. **Steps:** U-18 with 50 Shorts.
- **Pass:** no freeze or crash; memory warning absent in `adb logcat -s YuMemory`. **Status:** Owner/community test (pending)

### OMT-35 — External players (F-07)

- **Why you:** CI emulators have no VLC, mpv, MX or Just Player installed. **Hardware:** phone + one of them.
- **Steps:** 1. Open a Yumix title with a header-protected source. 2. ⋮ → Open in… → the player. 3. Watch 2 minutes, go back.
- **Pass:** it plays with subtitles; Yumeiro resumes at the position the player returned. **Fail:** error, no
  subtitles, or the position is lost. **Record:** player name and version, source name. **Status:** Owner/community test (pending)

### OMT-36 — Volume boost (F-06)

- **Why you:** loudness and distortion can only be judged with real speakers and ears. **Hardware:** phone + headphones.
- **Steps:** swipe volume past 100 % to 200 %, then back down. **Pass:** one hearing warning, louder without
  crackling, boost drops first on the way down. **Status:** Owner/community test (pending)

## 6. Profiles and privacy

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

## 7. Network

### OMT-47 — Automatic DNS fallback (NBUG-6 F1)

- **Steps:** Settings → Network → turn "Automatic DNS fallback" on. On a network whose DNS fails (for example a
  router with a dead DNS server, or Private DNS set to a name that doesn't exist), open Home and a source. Then turn it
  off and repeat. **Pass:** On: pages load after one short wait (the first lookup takes at most your phone's own DNS
  timeout plus about 7 s), later pages without waiting. Off: same as before this release. **Status:** Owner/community
  test (pending)

### OMT-53 — Your own proxy (NBUG-13)

- **Steps:** Settings → Network → Proxy: enter your HTTP or SOCKS5 proxy (with login if it needs one), open an adult
  host that your network blocks, then turn the proxy off. **Pass:** with the proxy the site loads in the app and in its
  web pages; off, the app goes direct again. **Status:** Owner/community test (pending)

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

## 8. Android TV

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

### OMT-59 — Smoothness on an Android 7–8 TV (final test, main-thread checks)

- **Steps:** on an Android 7 or 8 TV box: open Library (all tabs), Manga library pages, Inbox, Settings → Storage and
  Settings → About → Check for updates. **Pass:** nothing freezes or stutters for more than a moment. Why you: the
  automatic main-thread checker needs Android 9, so it could not run on the Android 7 TV emulator. **Status:**
  Owner/community test (pending)

## 9. About and community

### OMT-71 — Social links (OL-13)

Settings → About on a real phone with Facebook, Instagram, X, TikTok, Reddit, Discord and Telegram installed (any you have):

1. Community: Telegram channel, Telegram group, then a row Discord · Facebook · Instagram · X · TikTok · Reddit, then Website and GitHub.
2. Each button opens @officialyumeiro (Discord: the Yumeiro server invite) in its app, or in the browser if the app is missing.
3. Developer: Website, GitHub, Facebook (siam1p), Instagram (asfakul_siam), Telegram (@siamorwho); each opens the right profile.

Passes if every button lands on the right account.

## 10. Your opinion

### OMT-9 — Usability feedback

- **Why the agent can't do it:** whether something feels clear and pleasant is a human judgement.
- **Hardware / environment:** your phone, a few days of normal use.
- **Instructions:** note anything confusing, slow, ugly or surprising, with the screen name.
- **Expected result:** a short list; each item becomes a ledger entry or a proposal.
- **Evidence to send:** the list, screenshots where useful.
- **Status:** Pending

---

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
4. A plain file link inside an in-app web page: see OMT-81 (DL-WEB).

### OMT-81 — A file link inside a website opens the download popup (DL-WEB)

1. Open a website in Yumeiro (Sites) and tap a plain file link on the page (for example a .mp4, .mkv, .zip or .apk link).
2. The usual download popup opens with the file's real name; pick Offline or Permanent and the file lands in Downloads.
3. A site that signs you in (cookies) still downloads: the popup uses the page's cookies and address.
4. A link the page builds itself (blob:) shows "This page makes the file itself ..." instead of doing nothing.

### OMT-82 — One Back leaves a vertical video (ST-17-BACK)

1. Open a short drama or any portrait video (for example a ReelShort title from Yumix) so the player goes vertical.
2. Press Back once: the normal player layout comes back (the video keeps playing).
3. Press Back again: you leave the player as usual. Back never does nothing.
4. Repeat after swiping up to the next episode a few times.

### OMT-83 — Online works after skipping the tour; Cloudflare DNS by default (OWN-2, OWN-7)

1. Uninstall Yumeiro (or clear its data). Install the review APK. On the welcome screen tap "Skip for now".
2. Add one extension or repository (Extensions -> Add repository), open Yumix and Search: rows and results load.
3. Settings -> Network -> DNS shows Cloudflare (DNS over HTTPS), mode Always.
4. Repeat on your home Wi-Fi and on mobile data. Also update over 2.0.0 (install the APK over it): your old DNS
   choice stays as it was. If you never picked one in 2.0.0, it now shows Cloudflare.
5. A Jellyfin / Plex / SMB server on your Wi-Fi with a .local or one-word name still connects.

Passes if online works without the tour on both networks. If it still fails: Settings -> About -> Send feedback,
and say which network.

### OMT-84 — TeraBox (OWN-10)

1. Sign in to TeraBox in Yumeiro (the TeraBox sign-in page opens from a TeraBox link).
2. Paste a TeraBox share link (Telegram post or Sources -> Add anything): the full file plays.
3. If TeraBox asks for a human check, Yumeiro opens the TeraBox page in its browser: do the check there, then
   come back; the link plays.
4. DiskWala: the link opens the DiskWala app (its website itself does not play files).

### OMT-85 — Page closes by itself (OWN-1)

Not fixed in 2.1.0 (not reproduced). When it happens, record the screen (Android quick settings -> Screen record)
and send the clip: which page, what you tapped, how fast. That decides the fix for 2.1.1.

### OMT-86 — Download choices, Vega link, script sources (OWN-8, OWN-4, OWN-5)

1. Player -> Download on a website / HLS stream: the popup lists the real qualities, audio tracks and subtitles.
2. A title with Sub and Dub -> download icon on the episodes: pick Sub or Dub in the sheet, quality "up to", and
   subtitles None or "Save all the source has"; the files land with the picked version.
3. Extension market -> Add repository: paste a Vega providers link, a SkyStream repo link, or a Stremio manifest
   link: it is added (before 2.1.0 this box only took CloudStream repositories).
4. Extensions page: a card "Vega sources: N installed" (or Nuvio / SkyStream) lists them; Manage opens Sources ->
   Script repos.

### OMT-87 — YouTube audio-only download (OWN-11), phone and TV

1. Settings -> Downloads: turn "Ask before download" off.
2. YouTube -> any video -> Download: the popup still opens with "Save as": Video, Audio only · N kbps.
3. Pick Audio only -> Start: the Downloads list shows an audio file (m4a / webm) in the YouTube audio folder; it plays.
4. Pick Video -> Start: the video downloads as before. A song (YouTube Music) still saves audio without this row.

### OMT-88 — Profile PIN guard (OWN-12), phone and TV

1. Settings -> Privacy -> Profiles -> your profile -> Set a PIN: type it twice. The security questions open next
   (Cancel skips them).
2. Open the profile again -> Remove PIN: the PIN pad asks for the current PIN. A wrong PIN says "Wrong PIN" and
   keeps it; the right PIN removes it. "Forgot?" answers the security questions.
3. Change PIN and Remove security questions ask for the current PIN the same way. TV: all of it by D-pad / number keys.

### OMT-89 — Fast title open and double taps (OWN-1, OWN-6), phone and TV

1. Yumix -> tap a title: the title page slides in at once (or under the loading card); the feed does not flash.
2. Tap a poster twice fast, and press Back twice fast on My List / Reading: one page opens, one page closes.
3. Open the same title again: it opens at once (its source is remembered as one that has a title page).

### OMT-90 — Telegram sign-in with your own keys (OWN-3), phone

1. On a computer or phone browser, sign in at my.telegram.org -> API development tools -> create an app; copy
   App api_id and App api_hash.
2. Yumeiro -> Telegram -> Your Telegram account -> "Use your own API ID and hash" -> paste both -> Save and sign in.
3. Phone number -> code from Telegram -> (2FA password if set): your chats open; a video in a chat plays.
4. A wrong hash (not 32 letters / digits) shows the reason and saves nothing.

### OMT-91 — Official source on a fresh install (OWN-9), phone and TV

1. Fresh install, skip the tour. Extensions shows "Official · built in": Yumeiro Classics and Yumeiro Drama, both On.
2. Yumix shows Classics shelves (Classic feature films, Film noir, ...). Search "chaplin": results from Yumeiro Classics.
3. Open a film -> Play: it plays (Internet Archive MP4); Download works. Turn Classics off: its shelves go.

### OMT-92 — TeraBox (OWN-10), phone

1. Sign in to TeraBox once in Websites. Play a 1024terabox.com share and a terabox.app share: both play past 0:30.
2. If TeraBox asks for a human check, the share page opens; do the check, go back, play again: it plays.
