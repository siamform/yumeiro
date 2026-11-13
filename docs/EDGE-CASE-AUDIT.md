# Yumeiro — edge-case and micro-interaction audit (Round 10)

Scope: the whole app on `redesign/v7-dream` (16 audit areas and the 10 conflict scenarios from the
Round 10 brief). Method: the source was read area by area, the risky paths were traced, fixes were
kept minimal, and everything that could be exercised was exercised in unit and Robolectric tests
(simulated Android runtime, simulated touch and remote keys, offline JVM). There was no real
phone, TV, SIM, Bluetooth headset or live third-party account in the sandbox; anything that needs
one is marked **Unverified** instead of assumed.

Documents in this set:

1. Audit report — this file, section 1.
2. System behaviour specification — [BEHAVIOR-SPEC.md](BEHAVIOR-SPEC.md).
3. Prioritised edge-case checklist — section 2.
4. Feature interaction and conflict matrix — section 3.
5. Implementation fixes — section 1 (status column) and the Round 10 commits listed in section 5.
6. Verification report — section 4.

Priorities: **P0** critical (broken core behaviour or data loss), **P1** high (common failure the
user notices), **P2** UX, **P3** optimisation.

---

## 1. Audit report — issues found

| # | Pri | Area | Issue | Root cause | Status |
|---|---|---|---|---|---|
| P0-1 | P0 | Network & offline | Offline detection never worked on real devices. The offline banner, offline search/page states, the player's wait-for-connection and WorkManager's network constraint all believed the device was always online. | The manifest stripped `ACCESS_NETWORK_STATE` (`tools:node="remove"`, from the initial commit) "to keep permissions minimal". Without it `ConnectivityManager` throws `SecurityException`, which `Connectivity` turned into "online" (`runCatching { … }.getOrDefault(true)`). | **Fixed.** Permission restored (normal install-time permission, no prompt). Checked in the merged release manifest. |
| P0-2 | P0 | Build | Every clean clone failed to build (`resource raw/isrg_root_x1 not found`), so CI and any new developer were blocked; only the original machine built. Found in round 11. | `.gitignore` has `*.pem`, which silently excluded `app/src/main/res/raw/isrg_root_x1.pem` (the root bundled for P1-7). | **Fixed.** `!app/src/main/res/raw/isrg_root_x1.pem` exception, file re-added from letsencrypt.org (SHA-256 `96BCEC06…DF08C6` verified, valid to 2035). `BundledRootTest` checks the resource exists, parses as ISRG Root X1, matches the pinned fingerprint and is unexpired. |
| P1-1 | P1 | Network / playback | A network drop during video playback used up the player's retry budget within seconds and ended on an error card even though the network came back. | Retries were time-based, not network-aware. | **Fixed.** Waits up to 90 s for a validated network, lets routes settle 0.8 s after a Wi-Fi ↔ mobile switch, then re-prepares at the same position. |
| P1-2 | P1 | Downloads | Downloads started offline (or that lost the network) burned all their WorkManager retries and failed. | No network constraint (it could not work without P0-1). | **Fixed.** `NetworkType.CONNECTED` constraint; resumes from the `.part` file with a Range request. |
| P1-3 | P1 | Downloads | A full disk made downloads retry forever with a generic error. | `ENOSPC` was treated as a transient IO error. | **Fixed.** Up-front free-space check plus `ENOSPC` detection; stops with "Storage full — needs about N MB more, M MB free". `DiskFullTest`. |
| P1-4 | P1 | History / lifecycle | The last up to 5 s of progress were lost when the system killed the app in the background (low memory, battery saver, force-stop after leaving). | Progress was saved only on the 5 s tick, pause and close. | **Fixed.** Saved immediately on `ON_STOP`. |
| P1-5 | P1 | Lifecycle / TV | Connecting a Bluetooth keyboard or a remote during a video restarted the activity and the video. | `configChanges` did not include `keyboard|keyboardHidden|navigation`. | **Fixed.** |
| P1-6 | P1 | Compatibility | The app could not be installed on Android 7–8 (most TVs and older phones). | minSdk was 28. | **Fixed.** minSdk 24 with core-library desugaring; ~70 API call sites in 16 files guarded (`OsCompat.kt`). |
| P1-7 | P1 | Security / compatibility | On Android 7.0 every Let's Encrypt HTTPS site failed. | Android 7.0 predates ISRG Root X1. | **Fixed for the app's own networking** (root bundled, fingerprint checked). **Unresolved for WebView** on 7.0, which ignores the network security config. |
| P1-8 | P1 | Security / compatibility | App lock could not work on Android 7–8. | `BiometricPrompt` doesn't exist there. | **Fixed.** Falls back to the device PIN/pattern screen. |
| P1-9 | P1 | TV | With a remote, focus was invisible, so users could not see what they were on. | Phone ripple indication only. | **Fixed.** Focus-visible indication across the app, TV rail, remote keys in the player. |
| P1-10 | P1 | TV | On the playback error card the remote focus stayed on hidden controls, so Retry was unreachable; faded transport buttons were drawn through the card. | Focus not moved; chrome not hidden. | **Fixed.** Focus goes to Retry; chrome hidden. Caught by a screenshot in `TvLayoutTest`. |
| P1-11 | P1 | Updates | The updater always downloaded one APK; with ABI splits it must match the device. | Single-asset logic. | **Fixed.** Picks the device's preferred ABI, else universal. `UpdaterLogicTest`. |
| P1-12 | P1 | Playback | Video size, duration and audio language were often missing under the player, with no explanation (owner report). | Facts were read only from the container metadata. | **Fixed.** Duration from the player, size from content length or estimated from bitrate × duration (marked "≈"), audio language from tracks; a single notice names what the source doesn't share. `PlaybackFactsTest` 5/5. |
| P1-13 | P1 | Link playback | TeraBox / DiskWala links didn't play; Pixeldrain, OneDrive and MediaFire needed manual steps. | No host handling. | **Fixed.** Hidden in-app capture for TeraBox/DiskWala (page shown if it asks for login or captcha); direct URLs for the other three. `LinkHostsTest`, `StreamDetectionTest`. Live TeraBox **unverified**. |
| P2-1 | P2 | UI | Yumix felt like a list of extension groups rather than a streaming home (owner report). | — | **Done.** Hybrid feed: resume banner, spotlight and latest-episode slides, source filter sheet, categories, latest episodes, shuffled discovery. `YumixFeedTest` 7/7. |
| P2-2 | P2 | UI | Library tabs hid Videos and Reading behind a second row of tabs. | — | **Done.** Eight colour shortcuts; Videos and Reading open their own pages. `LibraryShortcutsTest`. |
| P2-3 | P2 | UI | Quick picks rows each had a download button, noisy and easy to tap by mistake. | — | **Done.** No button; long-press offers Download; the playing song is highlighted. |
| P2-4 | P2 | History / sync | A tracker update (Trakt, SIMKL, AniList/MAL) that fails while offline may not be retried later. | Trackers run after the local save, isolated per tracker; no retry queue was found. | **Unresolved / unverified.** Local history is never lost; only the remote tracker can lag. |
| P2-5 | P2 | Playback | When a site's own web player is shown (login/captcha fallback), its audio does not take audio focus, so music can keep playing under it. | Android WebView doesn't request audio focus for page media. | **Unresolved** (platform limitation). Workaround: the captured stream plays in Yumeiro's own player, which does take focus. |
| P2-6 | P2 | UI | The bottom nav pill (62 dp) looked thin. | — | **Done.** 68 dp, matching the mini player's 68 dp; icons 26/28 dp. |
| P3-1 | P3 | Performance | Saving progress on every 250 ms tick recomposed the app. | — | Already handled before Round 10 (saved every 20th tick). Kept. |
| P3-2 | P3 | Search | Risk of an older, slower response overwriting a newer query. | — | Already safe: `LaunchedEffect(query)` cancels the previous search; debounced 200–350 ms. |

## 2. Prioritised edge-case checklist

`[x]` handled and tested · `[c]` handled, verified by code reading only · `[ ]` open · `[d]` needs a device

**P0**
- [x] App knows when the device is offline (P0-1)
- [x] Downloads wait for a network instead of failing (P1-2, depends on P0-1)

**P1 — playback**
- [c] Music and video never play together (audio focus on both players)
- [c] Headphones/Bluetooth disconnect pauses (`becomingNoisy`)
- [d] Phone call pauses and resumes after the call
- [x] Seek is clamped at start/end; live seeks within the window (`SeekMathTest`)
- [x] Audio-track switch that stalls reverts after 10 s
- [x] Source failure → error card with Retry and other sources (`PlaybackRecoveryTest`)
- [c] Network drop → waits, then resumes at the same position (P1-1)
- [c] Progress saved on `ON_STOP` (P1-4)
- [x] Size/duration/audio language shown or explained (P1-12)

**P1 — PiP**
- [c] Not offered on devices without the PiP feature or below Android 8
- [c] Closing PiP saves position and pauses
- [d] Auto-enter on Home (Android 8–11 via `onUserLeaveHint`, 12+ natively)
- [d] PiP play/pause from the notification shade

**P1 — downloads**
- [c] Same item twice → one job (`ExistingWorkPolicy.KEEP`)
- [x] Storage full stops with a clear message (`DiskFullTest`)
- [c] Resume from partial file after network loss or process death
- [d] OEM battery killers (Xiaomi, Oppo, Vivo) stopping the foreground job

**P1 — compatibility and TV**
- [x] Android 7.0+ build (compiles at minSdk 24 with every flagged API call guarded)
- [x] TV detection decision table (`TvModeTest`)
- [x] Rail, focus, remote keys (`TvLayoutTest`)
- [c] Keyboard/remote attach doesn't restart the video
- [d] Real Android TV / Fire TV hardware

**P1 — updates and security**
- [x] Updater picks the right ABI (`UpdaterLogicTest`)
- [c] Update APK refused unless its SHA-256 matches the published digest
- [x] Notification deep links validated (`FcmPlanTest`)
- [x] No secrets in the APK; service account only in the Worker (`OnlineSecretsTest`, `PUSH-FCM.md`)

**P2**
- [x] Back closes sheet → page → tab → app (`NavigationFlowTest`, `doubleBackPressExitsFromHome`)
- [x] Fast navigation doesn't crash (`quickNavigationThenHomeDoesNotCrash`)
- [x] Search results de-duplicated (`SearchRankerTest`)
- [c] Typing fast never shows stale results
- [x] Empty states for empty playlists and missing facts
- [ ] Tracker sync retry after offline (P2-4)
- [ ] WebView page audio vs music (P2-5)

**P3**
- [c] Progress persistence throttled to ~5 s
- [c] Extension market cached and usable offline

## 3. Feature interaction and conflict matrix

The ten scenarios from the brief. "Expected" is the rule; "Actual" is what the code does today.

| # | Scenario | Expected behaviour | Actual (mechanism) | Evidence |
|---|---|---|---|---|
| 1 | Music playing → user starts a video | Music pauses, video plays. When the video closes, music stays paused (the user chose video); one tap on the mini player resumes it. | The video player requests audio focus (`AUDIOFOCUS_GAIN`); the music player loses focus permanently and pauses. Media3 does not auto-resume after a permanent loss. | Code; **Device** for the real audio path |
| 2 | Video playing → user starts music | Video pauses, music plays. Video position is kept. | Music takes focus; the video player loses it and pauses. Position is saved on pause. | Code; **Device** |
| 3 | PiP playing → user opens another video | The PiP window switches to the new video (one player); the previous video's position is saved. | One video player; opening a title replaces the item after saving the position. In PiP the user has to expand the window or come back to the app to pick another title. | Code; **Device** |
| 4 | PiP playing → user starts music or opens another extension | Starting music pauses the PiP video (focus). Browsing another extension leaves the PiP video playing. | Same focus rule as #2; navigation does not touch the player. | Code; **Device** |
| 5 | Video buffering → internet disconnects | "No connection" state, wait without burning retries, resume at the same position when the network returns. | P1-1 fix: network-aware wait up to 90 s, then the error card with Retry. | Code (depends on P0-1, which is fixed and checked in the merged manifest) |
| 6 | Download running → app goes to background or the process is killed | Download continues in the background with a notification; after process death it resumes from the partial file. | Foreground data-sync WorkManager job; Range resume from `.part`; WorkManager reschedules after death/reboot. | Code; **Device** for OEM killers |
| 7 | Playback active → incoming call or audio interruption | Pause during the call; resume after it ends if it was playing. Short interruptions (navigation voice) duck. | Media3 audio-focus handling on both players (transient loss pauses and resumes; can-duck lowers volume). | **Device** (not testable on JVM) |
| 8 | Extension removed → its content is playing | Current playback continues; next episode / source switch looks only at installed sources. | The player holds a resolved URL, not the extension. | Code |
| 9 | Notification tapped → app already open on another screen | No second copy of the app; the target opens on top of the current screen; Back returns to where the user was. | `launchMode="singleTop"`; `onNewIntent` → `handleIntent` validates the link and opens it. | Code; deep-link parsing Tested (`FcmPlanTest`) |
| 10 | App crashes → user reopens with active playback and unsaved progress | At most a few seconds of progress lost; the title is in Continue watching at the saved position. | Progress saved every 5 s, on pause, close and `ON_STOP`. A crash in the foreground can lose up to 5 s. | Code; history logic Tested (`WatchLogicTest`) |

Rules that hold across all of them: only one audible player at a time (audio focus), one video
player instance, progress written locally before any remote sync, and navigation never starts or
stops playback by itself.

## 4. Verification report

### Automated tests

| Run | Result |
|---|---|
| Full Android unit + Robolectric suite (Round 10, all changes) | see section 4a |
| Worker (`server/`): `npm test` and `tsc --noEmit` | see section 4a |
| Targeted Round 10 suites | `PlaybackFactsTest` 5/5 · `YumixFeedTest` 7/7 · `YumixShowcaseTest` 3/3 · `LibraryShortcutsTest` 2/2 · `AppNavigationCrashTest` 11/11 · `TvLayoutTest` 2/2 · `TvModeTest` · `LinkHostsTest` · `StreamDetectionTest` · `DiskFullTest` · `UpdaterLogicTest` |

### 4a. Final run

Filled in at release time below.

### Tested (executed and passing)

Seek clamping; audio-track revert; source error recovery plan; black-frame watchdog; playback facts
(full, missing, estimated size, live); Yumix feed ordering, filters and categories; Library
shortcuts; Quick picks rows; navigation (Back, tabs, rapid switching, double-Back exit, video
portrait/fullscreen); TV detection, rail, focus and remote keys; link host classification; stream
detection; disk-full detection; updater ABI choice; FCM message parsing, deep-link validation and
delivery plan; Worker FCM routes; search ranking and de-duplication; M3U parsing; HLS ad filtering.

### Fixed this round

P0-1, P1-1 … P1-13, P2-1, P2-2, P2-3, P2-6 (section 1).

### Unresolved

- P2-4 tracker retry after offline (not verified either way).
- P2-5 WebView page audio doesn't take audio focus (platform).
- P1-7 WebView on Android 7.0 still rejects Let's Encrypt sites (platform).
- Push notifications: client config, D1 table, `ADMIN_TOKEN` and Worker deploy are done (round 11). Sending still needs the owner's `FCM_SERVICE_ACCOUNT` secret (`PUSH-FCM.md`).

### Unverified (needs a real device or live service)

- Phone calls, Bluetooth headsets, car audio, real audio-focus overlap.
- PiP auto-enter, PiP actions from the shade, PiP on OEM skins.
- TeraBox / DiskWala capture against live sites (sites change often).
- OEM battery killers stopping downloads.
- Real Android TV, Fire TV and 32-bit TV sticks (performance, remote quirks, voice keys).
- Android 7.0 and 8.x on hardware (compiles at minSdk 24; not run on a device).
- FCM delivery end to end (blocked on the service-account secret; registration endpoints verified live and under a 2000-phone local load test).

### Device-dependent limitations

- Robolectric only: no real decoder, surface, audio output or network stack.
- WebView on Android 7 ignores the network security config.
- PiP needs Android 8.0+ and the device's PiP feature.
- Android WebView doesn't request audio focus.
- Removing an extension mid-playback keeps the current stream until it ends.

## 5. Round 10 commits

`f94611b` TV mode, Android 7 + ABI splits, link player auto-capture, edge-case fixes ·
`31147cf`, `b8bfd96` playback facts, Yumix hybrid feed ·
`42d846c`, `26f1d9d` Library shortcuts, Quick picks rows ·
`64cdc07` … `74c2489` bottom nav height.
