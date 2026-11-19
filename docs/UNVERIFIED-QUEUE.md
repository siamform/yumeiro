# Unverified queue

The honest list of what v1.8.2 ships without full proof. An item leaves this list only with a
fail-before run and a pass-after run (or, for an owner test, the owner's recorded result).
Created 2026-11-18 at cf1691b (master prompt "hard rules, group completion, v1.8.2 release").

Targets: **stability-v2 deep** (agent work after the v1.8.2 merge) · **user-assisted** (owner or
recruited users, `docs/OWNER-MANUAL-TESTS.md`, brief in `docs/USER-TEST-PROGRAM.md`) ·
**v1.8.2 soft** (agent, before the release gate, if time allows; otherwise moves to stability-v2).

Labels: unit (JVM/Robolectric in CI), CI, emulator (CI phones api33-lowram / api36, TV tv-api34 /
tv-api24), real hardware.

## Agent findings and gaps

| ID | Description | Group | Verified | Not verified | Why queued | Target | Status |
|---|---|---|---|---|---|---|---|
| G6-PG2 | Endless Music Home: offline scrolls no longer close the feed; 80-shelf cap | G6 | unit (CI 44235994633 → 44238631828) | emulator end-to-end: skipped in 44242208629, 44243818628 (YouTube bot wall on CI IPs) | the "Similar to" call cannot be answered from CI | user-assisted (OMT-29) | Queued |
| G7-GAP-1 | Live TV buffering / stall behaviour on a real channel | G7 | unit: G3 P14 recovery tests (`PlaybackRecoveryTest`, `StarvationLoadControlTest`) | no device run of a Live TV channel buffering | no reachable test channel from CI; a local HLS stall test is possible | stability-v2 deep (local HLS server test); user-assisted (OMT-11) | Queued |
| G7-GAP-2 | Live TV and Manga screens with the TV remote (D-pad focus) | G7 | emulator TV for Home/Search/player (`TvBug5ReproDeviceTest` 43908926085, 43912693329, 43935810414, 43949571344, 43957029647) | Live TV and Manga screens never opened on the TV images | not covered by the BUG-5 test | stability-v2 deep (TV device test); user-assisted (OMT-4, OMT-11) | Queued |
| G7-L5-A | M3U parser ignores single-quoted attributes (`tvg-logo='…'`): logo/group lost, channel plays | G7 | code read (`M3uParser.ATTR`) | no test | minor, no user report | stability-v2 deep (L5) | Queued |
| G7-L5-B | M3U channel lines without a scheme (relative URLs) are skipped | G7 | code read (`M3uParser.URL_LINE`) | no test | behaviour change; needs a proposal | stability-v2 deep (L5) | Queued |
| G7-L5-C | Manga home and reader read two tiny SharedPreferences on the main thread | G7 | code read (`MangaScreens.kt` 142, 520) | no StrictMode run | few bytes; not user-visible | stability-v2 deep (L5) | Queued |
| G7-L5-D | Music track count says "1 tracks" (no singular) | G6 | code read; seen in SP1 work | no test | cosmetic | stability-v2 deep (L5) | Queued |
| G7-AU-PIP | GROUP-7 audit row said "PiP, buffering, TV focus covered by G3 and BUG-5": overstated (PiP proven with a local clip only) | G7 | PiP in the shared player: emulator 43708220122 (`LocalPlayerDeviceTest c`), 43747147539, 43748560619 | PiP from a Live TV channel | wording corrected in GROUP-7; gaps = G7-GAP-1/2 | stability-v2 deep | Queued |
| G8-CQ-1 | `OnlineDownloads.deleteFolder` is unused (dead code) | G8 | code read (G8 audit) | — | cleanup, no user effect | stability-v2 deep | Queued |
| G8-CQ-2 | Download ids are 32-bit `hashCode`s (a collision would merge two items) | G8 | code read | no collision test | no report; needs a migration | stability-v2 deep | Queued |
| G8-CQ-3 | `DashGrab.bytes` holds a whole segment in memory | G8 | code read; TV/low-RAM regression passed | memory peak on large segments | no OOM seen | stability-v2 deep | Queued |
| G8-CQ-4 | Only the first `EXT-X-MAP` of an HLS playlist is written | G8 | code read | playlists that switch init segments | rare format | stability-v2 deep | Queued |
| G8-TV7A | Website-video Download action on TV: the watch page (Download) shows only when the player is docked, never on a landscape TV (`VideoPlayerModal.kt:1582`); `StreamWatchDownloadDeviceTest` 7a fails on TV (44314925557) | G8 (TV) | phones PASS (44346504202) | TV has no website-video download | adding it on TV is a new feature | owner decision | Queued |
| G8-WEBM7 | WebM video + audio join on Android 7–8.1 uses the platform muxer (ADR-022 keeps it) | G8 (TV) | MP4 join verified on tv-api24 (44339775707) | WebM join on API 24–27 | not exercised by the TV tests | stability-v2 deep | Queued |
| G9-CQ-1 | "Last backup" time can be lost: `Restart.now` exits right after `recordBackup` uses `apply()` | G9 | code read | no deterministic test | cosmetic | stability-v2 deep | Queued |
| NB11-OB1 | 18+ on but private: an NSFW-typed title of a mixed extension can show in that extension's feed rows (`OnlineRepository.toItem` drops NSFW only when 18+ is off) | GROUP-14 | code read | no test | not in the owner's report; "private" is defined for activity, not browsing | owner reading of "private", then stability-v2 | Queued |
| NB12-5b | NBUG-12: a version switch or Retry after the refresh and page step gets its own refresh and page step again (3651816 resets `refreshed`/`captured` in `switchVersion` and `retry`) | GROUP-14 | code read | no device test drives a version switch on a refused source | the NB12-5 test covers the next episode only | stability-v2 deep | Queued |
| NB16-6 | NBUG-16: a background update refused by the safety check is reported ("held back … tap Update to review") | GROUP-14 | unit `BackgroundUpdatesTest` (message) | no test drives a DANGER-flagged update through the old sync | no fixture extension the scanner flags as DANGER and that also loads | stability-v2 deep | Queued |
| NB16-7 | NBUG-16: update checks run on every return to the app (6 h gates kept) | GROUP-14 | unit `BackgroundUpdatesTest` (gate) | no old-code test: there is no hook to make "6 h later" happen on a device | owner can see it on a TV left open for a day (OMT) | stability-v2 deep | Queued |
| NB14-OB1 | NBUG-14: SVG channel logos never show (Coil has no SVG decoder in the app; 18 curated logos) | GROUP-14 | code read | no test | needs a new dependency (owner approval) | owner decision, then stability-v2 | Queued |
| NB15D-OB1 | NBUG-15 (d): stream recents are kept while incognito is on, and "Clear watch history" leaves them | GROUP-14 | code read (`addToHistory`, `SettingsControls` 313) | no test | not in the owner's report; the setting's text does not promise it | owner decision | Queued |
| NB15D-B | NBUG-15 (d): `Nbug15RecentsDeviceTest` case b (a Play Link stream stays out of the Live TV row) passes on old code, which had no Live TV row | GROUP-14 | pass-after on phones and both TVs | fail-before is impossible by design | it guards the new row only | — | Queued (by design) |
| NB15-OB2 | TV: every bottom sheet (Live TV Tools, Yumix filter) opens with TV focus on the drag handle, which shows a "Drag handle" label | GROUP-14 (TV) | tv-api24 screenshot nb15r_c_tools (44435753383) | the Yumix sheet on TV (same handle, code read) | not in the owner's report; affects every sheet (shared `MoonSheetHandle` in a Material sheet) | G10 (ER-28, 2026-11-19 re-audit) | Resolved: G10 ER-28 (2564ef48; fail-before api33 44445734996, pass-after 44449003874, tv-api24 44460663805) |
| TV-FP-1 | `FeedPositionDeviceTest` liveTv and youtube fail on tv-api24 on new and old code alike: the test taps the phone's `nav_tab_home`, so the page never opens on TV | GROUP-14 | old code 44438504785 and new code 44438500496 fail the same way; on tv-api34 (44440614210) the test reaches each feed and liveTv passes, youtube fails on TV-YT-1 | feed position on TV | phone-only test; TV needs its own navigation in the test | stability-v2 deep | Queued |
| TV-YT-1 | YouTube For You on a 1080p TV is one column of full-width 16:9 cards (`YouTubeScreens.kt:477`, `YtVideoCard(wide = true)`): under 2 cards per screen; `FeedPositionDeviceTest#youtube` needs 3 | GROUP-14 regression (TV) | tv-api34 44440614210 and 44443748315, screenshot feed_youtube (cards=2) | a TV grid / row layout for the YouTube feed | layout predates GROUP-14 (file untouched since 033bd58); a redesign needs the UI/UX skill first and the owner's look-and-feel call | stability-v2 TV pass | Queued |
| ER-28-TV34 | The sheet-handle focus bug (ER-28) did not reproduce on tv-api34 with old code (44445713091 PASS), so on that image the fix is shown working (44461533827) but not shown needed | G10 | unit `SheetHandleScanTest`; api33 fail-before 44445734996; tv-api24/tv-api34 pass-after | a TV image where old code fails the test | the Android 14 TV image moves the first focus elsewhere | stability-v2 TV pass; user-assisted (OMT-4) | Queued |
| TV-A11Y-1 | tv-api34: Espresso's TouchTargetSizeCheck flags one 44 dp view at [484,780][756,867] behind the message pill (MessagesDeviceTest problems=11) | G10 regression (TV) | old code 6dbfde03 fails the same way (44462757164, problems=13); new code 44461533827 | which view it is and whether a touch-size rule matters on a remote-only TV | pre-existing and not a message; needs a TV layout pass | stability-v2 TV pass | Queued |

## Owner bugs and earlier items

| ID | Description | Group | Verified | Not verified | Why queued | Target | Status |
|---|---|---|---|---|---|---|---|
| BUG-3 / NBUG-1 | Download speed drops at ~50–70 % | G8 | emulator case d 6/6 (44043535616); DL-11 part 2 budget met (877413b, 44286280482, medians 1.050 / 1.030) | real phone per 10 % band | real Wi-Fi/phone | user-assisted (OMT-14, OMT-20) | Queued |
| BUG-5 / TV-4 | Cross-source search on TV | G7 | focus + answer on TV emulators (NBUG-2c) | scope decision | owner decision (options A/B/C) | user-assisted decision (OMT-21) | Queued |
| NBUG-4d | Freeze with upscaling on | G3 | injected decoder failure recovers, both phones (43946324687) | real GPU | cannot reproduce on emulator GL | user-assisted (OMT-16) | Queued |
| NBUG-6 | ISP blocking: per-feature DNS | G12 | side effects measured; guards SmartDnsResilience / DnsWire / DataCacheBounds green | not built | owner gate "no side effect" not passed | user-assisted decision (OMT-27), then stability-v2 | Queued |
| NBUG-7 | Restore after "Clear all data": extensions listed but not installed/usable; duplicate names across repositories and extension types | G9 (touches G2) | CI unit + emulator both phones: G9-1/2/5/7 fail-before 44312331848, 44315631356, 44318262848 → pass-after 44318294335, 44319576018 | Stremio and Aniyomi restore on a real account; the owner's own backup | fixed and verified on emulator; owner's real backup not tried | user-assisted (owner retries the restore) | Verified (emulator) |
| NBUG-8 | One extension's content loads forever or very slowly | G10 (touches G2) | CI unit as G10 ER-30 (fail-before 44457201367, pass-after 44457297312) | a real blocking extension on a device | the owner's extension and network | user-assisted (OMT-34) | Fixed (unit); OMT-34 pending |
| NBUG-9 | Fresh install / cleared data: nothing online loads until DNS is set to Cloudflare by hand | G9 (touches G11/G12, NBUG-6) | probe 44312163084 (both phones): not reproduced (auto bypass loaded 3/3 hosts) | the owner's ISP; H1 (TLS handshake hang on a block-page IP not counted as blocking) | needs the owner's network | user-assisted (OMT-31) | Blocked: owner |
| NBUG-10 | Yumix "Show videos from" search shows only 2 of the matching installed extensions | G4 (touches G2) | none (owner report 2026-11-18) | everything | reported during G6-QP; G4 is closed and only G6-QP is built this cycle | stability-v2 deep (G4 reopen), unless the owner moves it into this cycle | Open (G9-8 fixed the half-height sheet on TV; the phone search case is not reproduced yet) |
| NBUG-11 | Adult toggle off, adult titles still in Home → Most Played | GROUP-14 (touches G1) | code read: NB11-1, NB11-2 (GROUP-14) | everything | not run yet | GROUP-14, this cycle (ADR-023) | Verified (GROUP-14; NB11-OB1 queued) |
| NBUG-12 | "Access denied" on some feed streams (e.g. PORN4FANS); all three player fallbacks fail | GROUP-14 (touches G3, G2) | site probe: the extension's link is 403 for everyone, the page's KVS links 206 | everything in the app | not run yet | GROUP-14, this cycle (ADR-024); real site on the owner network: OMT-32 | Verified in the app (GROUP-14); OMT-32 pending |
| NBUG-13 | Adult streams need a VPN instead of working through the app's network setup | GROUP-14 (touches G11/G12, NBUG-6) | code read: SmartDns auto-DoH covers OkHttp paths; WebView has no DNS hook; IP/SNI blocks need a tunnel | the owner's network | CI network is not blocked; a proxy is a new feature | owner decision on ADR-024 options, then OMT-33 | Blocked: owner |
| NBUG-14 | IPTV channel logos missing | G7 (GROUP-14) | CI, emulator, TV emulator (NB14-1…3; GROUP-14) | the live market until the owner merges (scheduled builds run from main); SVG logos (NB14-OB1) | market fix acts after merge | after merge: the next market build | Verified (GROUP-14) |
| NBUG-15 | Live TV redesign + logic (filter/search/pins, category grouping across playlists, 50-channel paging, Tools modal with Playlists page, separate Play Link / Live TV recents) | G7 (GROUP-14, touches G4) | CI unit, emulator, TV emulator (NB15-A…C2, NB15D-1/2; GROUP-14) | real hardware and the owner's own playlists; NB15D-OB1, NB15-OB2 | built per the owner spec; look and feel is the owner's call | owner review on real hardware (OMT) | Verified (GROUP-14) |
| NBUG-16 | Extensions and M3U8 playlists never auto-update; re-sync flags them as needing an update again | G2 (GROUP-14, touches G7, G11) | CI, CI unit, emulator, TV emulator (NB16-1…5; GROUP-14) | NB16-6/7 old-code proof; the data workflows on main | workflow fixes act after merge | after merge: the next scheduled data builds | Verified (GROUP-14; NB16-6/7 queued) |
| ST-17 | Compose spin stress, cause-naming logging | G12 | 44145301927 (20/20, 0 spins) | stays open by order | standing order | stability-v2 deep | Queued |
| ST-7 | Jank on software-rendered emulators | G12 | 44002112195, 44138627579 | real phone | emulators render in software | user-assisted (OMT-12) | Queued |
| X2-1 | Baseline profile gain | G12 | emulator startup 44142300486 (no gain claimed) | real hardware gain | software rendering on CI | user-assisted (OMT-12) | Queued |
| EXT-T1 | Back closes a pop-up instead of Source status (NF-3 / ADR-014 part) | G2/G12 | NF-3 blocker emulator-verified (43825449454, 43866791994) | owner's phone reproduction | cannot reproduce on emulator | user-assisted (OMT-26) | Queued |

## Pending owner tests (user-assisted)

Each is described step by step in `docs/OWNER-MANUAL-TESTS.md`.

| ID | Description | Target | Status |
|---|---|---|---|
| OMT-1 | YouTube playback and quality switching | user-assisted | Pending |
| OMT-2 | Chromecast casting | user-assisted | Pending |
| OMT-3 | Low-RAM phone run | user-assisted | Pending |
| OMT-4 | Android TV with the remote | user-assisted | Pending |
| OMT-5 | Yumix offline on a real phone | user-assisted | Pending |
| OMT-6 | Heat, battery and long playback | user-assisted | Pending |
| OMT-7 | Predictive back animation | user-assisted | Pending |
| OMT-8 | Launcher widget | user-assisted | Pending |
| OMT-9 | Usability feedback | user-assisted | Pending |
| OMT-10 | Last.fm with your own account | user-assisted | Pending |
| OMT-11 | Manga and Live TV with your own sources | user-assisted | Pending |
| OMT-12 | Cold start with the baseline profile | user-assisted | Pending |
| OMT-13 | Restrict the Firebase API key (console) | owner | Pending |
| OMT-14 | Download speed with Wi-Fi power saving | user-assisted | Pending |
| OMT-15 | Each YouTube quality really plays | user-assisted | Pending |
| OMT-16 | Play link streams and upscaling on a real GPU | user-assisted | Pending |
| OMT-17 | Downloads in the phone's gallery | user-assisted | Pending |
| OMT-18 | Yumeiro behind a VPN | user-assisted | Pending |
| OMT-19 | A real YouTube download | user-assisted | Pending |
| OMT-20 | BUG-3 speed per 10 % band | user-assisted | Blocked: owner |
| OMT-21 | TV search scope decision | owner decision | Blocked: owner |
| OMT-22 | Firebase Test Lab role (console) | owner | Blocked: owner |
| OMT-23 | Phone Jackson version decision | owner decision | Blocked: owner |
| OMT-24 | "Vega" meaning | owner question | Blocked: owner |
| OMT-25 | Telegram / Terabox / Diskwala / manga cloud path | owner decision | Blocked: owner |
| OMT-26 | EXT-T1 on the owner's phone | user-assisted | Blocked: owner |
| OMT-27 | NBUG-6 decision | owner decision | Blocked: owner |
| OMT-28 | Headphones unplugged pauses music | user-assisted | Blocked: owner (hardware) |
| OMT-29 | Endless Music Home on a home connection | user-assisted | Pending |
| OMT-30 | A real HLS site with separate audio: the saved file plays with sound (G8-2) | user-assisted | Pending |
| OMT-31 | NBUG-9 on the owner's network: fresh install, default DNS | user-assisted | Blocked: owner |
| OMT-32 | NBUG-12: a Porn4fans title plays on your network (after the GROUP-14 fix); tell the extension author the link is dead | user-assisted | Pending |
| OMT-33 | NBUG-13: pick a network option (ADR-024) and retest adult hosts without a VPN | user-assisted decision | Blocked: owner |
| OMT-34 | NBUG-8: the extension that loaded forever now stops with a message within ~35 s, and Back leaves at once | user-assisted | Pending |

## Features (all queued, nothing built in this cycle except G6-QP)

| ID | Description | Group | Proposal | Target | Status |
|---|---|---|---|---|---|
| F-01 | Sub/Dub badges and preselect | G3/G4 | FEATURE-F-01 | stability-v2 | Queued |
| F-02 | Telegram option | new | FEATURE-F-02 (OMT-25) | stability-v2 | Queued |
| F-03 | Terabox / Diskwala links | G3 | FEATURE-F-03 (OMT-25) | stability-v2 | Queued |
| F-04 | User-added WebView site list | G2 | FEATURE-F-04 | stability-v2 | Queued |
| F-05 | Nuvio / SkyStream / Vega repositories | G2 | FEATURE-F-05 (OMT-24) | stability-v2 | Queued |
| F-06 | 200 % volume booster | G3 | FEATURE-F-06 | stability-v2 | Queued |
| F-07 | External video player | G3 | FEATURE-F-07 | stability-v2 | Queued |
| F-08 | Upcoming Movies & Series | G4 | FEATURE-F-08 | stability-v2 | Queued |
| F-09 | Watch Later + Favorite on posters | G5 | FEATURE-F-09 | stability-v2 | Queued |
| F-10 | Glassmorphism theme (ADR-017 proposed) | G9 | FEATURE-F-10 | stability-v2 | Queued |
| F-11 | Manga auto-translation | G7 | FEATURE-F-11 (OMT-25) | stability-v2 | Queued |
| F-12 | Vertical video player (ADR-018 proposed) | G3 | FEATURE-F-12 | stability-v2 | Queued |
| F-13 | Offline manga chapters | G7 | to write | stability-v2 | Queued |
| F-14 | Automatic Live TV failover | G7 | to write | stability-v2 | Queued |
| F-15 | Live TV EPG / TV guide | G7 | to write | stability-v2 | Queued |
| NF-1 | Episode watch status (Watched / In progress / Fresh) | G5 | checklist C | stability-v2 | Queued |
| NF-2a | Download storage labels (Offline cache vs Permanent) | G8 | checklist C | stability-v2 | Queued |
| NF-2b | Universal stream download (HLS/DASH without a provider button) | G8 | checklist C | stability-v2 | Queued |
| NF-3 | Extension pop-up blocker (ADR-014): already implemented earlier; only EXT-T1 open | G2/G12 | ADR-014 | stability-v2 (no work this cycle) | Queued |

## Release-scope gaps (named in the v1.8.2 notes)

| ID | Description | Target | Status |
|---|---|---|---|
| L4 | §13a re-audit (line by line, findings register, fail-before/pass-after, group regression) of Groups 1–5, 12 and 13 (closed by earlier-cycle tests only) | stability-v2 deep | Queued |
| L5 | Final sweep | stability-v2 deep | Queued |

Count: 27 agent findings/gaps (NB15-OB2 resolved, kept for the record; ER-28-TV34 and TV-A11Y-1 added 2026-11-19) + 18 earlier items (NBUG-8 fixed in unit, OMT-34 pending) + 34 owner tests + 19 features + 2 release-scope = 100 rows.
