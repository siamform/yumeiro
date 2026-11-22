# Yumeiro 2.x: major bugs and features, must-do list

Written 2026-11-20 (Dhaka) against r15-stability after v2.0.0 was published. It checks every item of the owner's
"COMPLETE CONSOLIDATED MASTER LIST" one by one against the code, the ledger (MASTER-CHECKLIST-FINAL.md,
CHECKLIST-FEATURES-BUGS.md, REMAINING-2026-11-19.md) and the new crash report on the `crash-log` branch.
Trust the repo and run IDs over this text.

## How to read the status column

| Status | Meaning |
|---|---|
| Verified | A test passed on an emulator with a run ID. Not yet seen on a real phone unless said. |
| Built, not tested | Code is in 2.0.0, no device run proves it. Treat as "may not work". |
| Broken | A real report or a failing check shows it does not work. Must be fixed first. |
| Partial | Some of the requested behaviour exists; the rest is listed in "Missing". |
| Not built | No code in the app for it (checked with a code search on 2026-11-20; two rows were wrong in the first version and are corrected: S-1, PIN-1). Re-check with a wide search before building. |
| Owner decision | Conflicts with a decision the owner already took. Not reopened here; the owner must say yes. |

"Verified" items still need the owner's real-phone check (OWNER-MANUAL-TESTS.md), because the community reports
problems that emulators did not show.

## P0: stop the freezes first (Now, before any feature)

The community report "app crashes even when I touch, YouTube and Live TV" matches a **freeze**, not a crash. The
owner's 2.0.0 report on the `crash-log` branch (freeze-1795185111722.txt, Xiaomi 220333QAG, Android 13, 2.0.0 (18)
release) is Android's "Input dispatching timed out … Waited 8001ms for MotionEvent(action=DOWN)": the screen did not
answer a touch for 8 seconds. Live TV was playing (ExoPlayer and tv.bdiptv.net threads in the dump), and the main
thread had used about 965 s of CPU (`utm=96515`).

| ID | Problem | Root cause | Fix | Status |
|---|---|---|---|---|
| FREEZE-1 | Touch freezes the app ("not responding") while Home is under the player | The release dex maps the main-thread stack to `HomeScreen` → `AdultContent.loadedAdultApis` → `ExtensionManager.extensionOf` (loop over the plugin map). `isAdultApi` rebuilt the whole provider list on every call, and Home / Yumix call it once per provider while drawing: about N² work on the main thread each time, repeated at every extension-load step and every Most-played change | One 18+ map per extension change (`StampMemo` + `ExtensionManager.providerStamp`), one pass over plugins (`extensionLookup`), Home works the map out off the main thread | Fixed d072e4d8. Fail-before CI 44764157290 (only AdultApiCostTest failed), pass-after CI 44764168235 (Build, test & lint green) |
| FREEZE-2 | Other screens still do provider work while drawing | Same pattern in `OnlineHomeScreen`, `OnlineSearchScreen`, `OnlineHomePicks`, `HomeOnline` (`ExtensionManager.providers()` inside `remember`) | Now cheap (cached map), but `providers()` itself still rebuilds and re-applies overrides per call. Move provider lists to one `StateFlow` in `ExtensionManager`, built on a background thread; screens only collect it | Verified: B-13 b59bb41b (fail-before 44771577361, pass-after 44775798224) |
| FREEZE-3 | 171 threads in the freeze dump, same number as B-8 | Checked 2026-11-20: the dump lists only 77 of its 171 threads; 23 are OkHttp (Live TV and extension hosts), below OkHttp's own limit of 64 requests. Not shown to cause the freeze; FREEZE-1 (main-thread work) was the cause | Not a proven defect. Re-check with a full thread list from a real phone (Settings → Crash logs) before capping anything | Not reproduced (no full dump); real-phone thread list = OMT |
| FREEZE-4 | Why Home recomputed so often while Live TV played | To confirm on a real phone: Most-played inputs (`PlayCounts`, MediaStore `recentVideos`) or extension generation bumps changing during playback | Add a debug counter of Home recompositions to the diagnostics screen; a device test that plays Live TV for 60 s and asserts Home recomposes ≤ N times | Built: B-21 0fa711ce DrawCounter + guard FreezeFourDeviceTest Pass 44937485728 (home=0); v2.0.0 also home=0 in 44945904021, so not reproduced on the emulator; real-phone check = OMT |
| GATE-1 | Nothing in CI catches main-thread stalls | No ANR test | Device test: start with 300 fake providers, open Home, Live TV, YouTube; fail if any frame takes > 700 ms or an ANR appears in `ApplicationExitInfo` | Built: B-23 + GATE-1-ANR (T2..T11) + RENDER-1..5, 4b (prewarm, staged Yumix reveal, plain posters, background-priority extension threads). Fail on emulator: 44957621376 lowram 1133 ms counted (YuStall 1714), api36 432 ms counted (YuStall 995 render). Finding GATE-1-EMU: the emulator's software GPU costs about 190 ms per frame in eglSwapBuffers (swangle worse, 44958850831). Limit unchanged at 700 ms. A/B on the same emulator (GATE-1-AB, 2 pairs): fixes on pass 3 of 4 phone runs (counted 449-557 ms), fixes off 1 of 4 (614-1861 ms). Start-up music player build moved to first use (GATE-1-MUSIC-START; MUSIC-START-2 fixes the first song tap after a cold start; pass-after 44979533703; gate 44982381315: no player stall, api36 Pass, lowram 3055 ms while Google apps starved the CPU, harness settle GATE-1-QUIET: lowram Pass twice, 44984838999 worst 664 ms and 44985820433 worst 559 ms, app stall 0 ms both). Real phones: Test Lab (GATE-1-FTL) blocked on owner access, then OMT-72 (owner) |
| UPG-1 | Deferred by owner: TV upgrade re-check and 1.9.0 → 2.0.0 upgrade check | — | Run when the owner allows (max two device workflows at once) | Not run (owner deferred) |
| SEC-1 | Last.fm shared secret must be rotated before built-in Last.fm sign-in is turned on | — | Owner rotates, then `wrangler secret put LASTFM_SHARED_SECRET` | Owner action |

Competitor handling: Android's Compose guidance is to do as little calculation in a composable body as possible
and cache it with `remember`, and to move I/O and heavy work to `Dispatchers.IO`
([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[effects and threading](https://developer.android.google.cn/agents/skills/jetpack-compose/jetpack-compose-performance/references/effects-and-threading)).
FREEZE-1 broke exactly this rule.

## Part 1: epics

| # | Epic | Status | What exists | Missing (must do) |
|---|---|---|---|---|
| E1 | Yumeiro Hub: Yumeiro's own all-in-one extension (movies, anime, series, drama, country based; no 18+), preinstalled, Yumeiro engine only, auto-updates from the root | Not built. Owner go 2026-11-22 14:00 +06 with scope; plan docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md (steps E1-A..E1-E, flags, free-limit budget) on top of docs/E1-MASTER-EXTENSION.md. Target 2.2.0, not in 2.1.0 | Extension market, playlist market and feed workflows; Worker api.yumeiro.click | Owner allowed schedules for the Hub / Live builders. Open: yumeiro-sources repo name and visibility |
| E2 | Yumeiro Live: one all-in-one Live TV playlist, preinstalled, auto-update, country based, stream checker, dead streams removed | Not built. Plan docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md (E2-A..E2-D). Target 2.2.0, first after 2.1.0 | playlist-market.yml (repair, validate, health-sample), stream-sync.yml, Sync button | Uses GitHub Actions (free for public repos) + Worker (100k req/day) |
| E2 | Curated, auto-validated Live TV M3U8 (~20 channels per country) | Built; CI Pass: dry run 44932539992 (curate + probe in GitHub Actions, 23 countries, publish skipped, E2-DRY), unit tests in CI 44884554686, fail-before 44880384185 valid. In-app Playlist Market entries: Blocked (needs the first publish to the streams branch, owner go) | `stream-sync.yml`, `sync_streams.py`, market build with logos (NBUG-14 Verified) | A per-country curated list with a nightly probe that drops dead links, like iptv-org's `playlist:test --fix`, which checks every link and removes broken ones ([iptv-org scripts](https://github.com/iptv-org/iptv/blob/master/docs/scripts.md)). Respect iptv-org's blocklist (DMCA / NSFW) |
| E3 | ABI-split APKs + in-app ABI self-migrating updater + website ABI detector | Built; CI Pass: 44884554686 with abi_splits=true (5 APKs per edition, each split only its own lib/), fail-before 44879928117 valid. In-app move to the split: the updater already picks the device ABI first (GitHubReleaseClient.parseRelease), so a universal install updates to its split on the next release. Website ABI detector: Blocked (siamform/yumeiro-web, a live site: owner go) | The updater already understands per-ABI file names (`GitHubReleaseClient.ABI_NAMES`, "-<abi>.apk" and ".tv.apk") | Gradle `splits { abi { … } }` is not set, so releases are universal only. Add splits with unique `versionCode` per ABI and keep a universal APK with the lowest code ([Android: build multiple APKs](https://developer.android.com/build/configure-apk-splits)). Then: release.yml uploads 4 + universal, SHA256SUMS covers all, the website picks the ABI from the browser's user agent, and the updater moves a universal install to the matching split |
| E4 | High-speed background extension engine | Verified: part 1 fastest-first order + quarantine (flag e4_speed_order) unit Pass 44884554686; part 2 visible-rows scheduler (FE-2 / FE-2c / FE-4b) + low-RAM / heat throttle E4-T (flag e4_throttle, fail-before 44889828561 valid) unit Pass 44890073919, regression FE-T1 fixed (fail-before 44894340441, unit 44896530640). Device: SpeedDeviceTest Pass on api33-lowram + api36 in 44896550636 (lowram feed half shown 5151 ms, was 56847) and again in 44899145489. Explore guide delay E4-GUIDE: no code change (first-run hold only; nothing is installed then) | SPD-1 (rows join as each extension loads), home memory cache, extension guard / timeouts (NBUG-8) | Stale-while-revalidate cache with < 50 ms first paint, a scheduler that loads only the visible rows plus two screens, pinned first, sort by measured latency, quarantine slow or failing extensions, throttle on low RAM or heat (MemoryWatch exists; thermal checks exist in 3 files). Remove the Explore guide delay but keep the pop-up blocker. Directly linked to FREEZE-2/3 |

## Part 2: features

| ID | Feature | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| F-01 / F-16 | Sub / Dub / language badges on search posters and Yumix; tap preselects; Yumix sections (Movies, Series, Web series, Dual audio, Sub, Dub) and quality tags | Verified (emulator, phones): F01DubTapDeviceTest Pass on api33-lowram and api36 (44896550636); TV Not run | 79532b29, 961fee20; `ReleaseTags.kt`, `AudioLanguages.kt` | Device test that a Dub tap opens the dub stream (ties to NBUG-5) Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: F01DubTapDeviceTest Pass on api33-lowram and api36 (44896550636). Status Pass (44896550636) for the Dub tag tap; TV Not run (phone test). |
| NF-1 | Episode watch status: Watched / In progress bar / Fresh | Device Pass 44903495726 (api33-lowram, api36) / 44903498117 (tv-api34), 2026-11-21; TV rerun Pass 44906652590 @a7bbd80e: Watched / 6 min left / Fresh on the right rows (earlier fails were test faults NF1-T, NF1-T2, NF1-T3) | 66c52247 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: Nf1EpisodeStatusDeviceTest Fail on all 3 (finding NF1-T: test faults, labels were right: api36 read [Fresh, Watched, 6 min left, Fresh…] where the first 'Fresh' was a row kept by the lazy list off screen; tv-api34 never scrolled to the layout button below the fold; lowram waited for rows before list mode composed). Test fixed, rerun pending. |
| NF-2a | Downloads labels Offline vs Permanent | Done | DownloadsScreen Offline / Permanent label (present before this cycle) | — |
| NF-2b | Download any stream (HLS / DASH muxing) + quality picker | Fixed in code (quality picker); unit green in 44896530640; Unit Pass: fail-before 44901952882, pass-after 44896530640. Device: Pass, PopupQualityDeviceTest OK on api36 + api33-lowram 44929260054 and TV api34 44929262746 (2026-11-21). Verified | NF-2b-1..4: DASH + split audio, TS → MP4 (Built, not tested). 2.1.0: the download prompt offers Best / 1080p / 720p / 480p / 360p for episodes and movies (`DownloadPrompt.Request.startAt`, `Item.maxHeight`, test `DownloadQualityPickTest`) | Real-phone check |
| NF-3 | Extension pop-up / promo blocker | Verified (emulator) | ExtensionPopups 8/8, run 43825449454 | EXT-T1 open |
| NBUG-15a–d | Live TV redesign (filter button, no file names, channels by category, first 50, search, pins) | Verified (emulator, phones + TV) | ledger: fail-before 44411680293, phones 44413808456, 44421879508 | Real-phone check; tied to FREEZE-3 (Live TV probes) |
| F-02 split | Separate Telegram page and Websites page, with navigation shortcuts | Fixed in code; unit green in 44896530640; Unit Pass: fail-before 44901941945, pass-after 44896530640. Device: Pass, F02SplitDeviceTest OK on api36 + api33-lowram 44929260054 and TV api34 44929262746 (2026-11-21). Verified | Route `telegram` (Screen.Telegram), Settings entries Telegram and Websites, Telegram tile in Live TV tools, Telegram nav-bar shortcut; each page refuses the other kind (`SavedSites.wrongPage`, test `SavedSitesSplitTest`) | Real-phone check |
| F-02 login | Telegram full login (TDLib): phone, code, 2FA password; private and public channels, groups and chats; search inside a chat; their videos AND audio play in Yumeiro's player and can be downloaded (owner add 2026-11-21) | Built (flag f02_telegram_login off until OMT-63). Device Pass: api36 44879417411, api33-lowram 44933818039 (engine download + load, TDLib 1.8.70; phone -> code screen, Telegram answers the code); TV api34 pending. Full sign-in with a real account: Blocked in CI (Telegram switched off public test accounts, tdlib/td#3083) = owner OMT-63 | Owner decision 2026-11-21: full login. Design ADR-052 (SYSTEM-DESIGN.md). api_id/api_hash are repo secrets YUMEIRO_TG_API_ID / YUMEIRO_TG_API_HASH. Public channels keep working without login (F-02 Pass 2/2, runs 44595993499, 44598214395) | Engine pins (tdlib-bin branch, TDLib build run 44871959357), CI on the app code, device test on Telegram's test servers, owner real-account check (OMT entry) |
| F-20 | Vault: 3 security questions, `.nomedia`, app lock, incognito (discreet icon dropped by the owner 2026-11-21; all of it is built) | Verified (B-19 security questions + vault); `.nomedia` not needed (vault is app-private storage); discreet icon dropped by the owner | `PrivateVault.kt`, `AppLockGate.kt`, incognito in 20 files; vault files are in app-private storage (no `.nomedia` needed, gallery apps can't see it) | Nothing left to build (security questions done in B-19). Discreet launcher icon: dropped by the owner 2026-11-21, do not build |
| F-19 | Profile security questions instead of the screen-lock bypass; strict per-profile isolation incl. extensions / repos and Continue Manga | Verified: B-19, B-20, PROF-1, F-19-EXT (fail-before 44937265964) + F-19-EXT-2 (fail-before 44938538930), pass-after CI 44944975133 | Per-profile today: YouTube history, online settings, online history, bookmarks, vault, private downloads (`Profiles.prefsName` users) | Not per profile: manga progress ("Continue reading" leaks across profiles, confirmed in code), extensions and repositories, Live TV pins. 2026-11-20: security questions built (B-19, ba87ce64), extension / Live TV pins per profile (B-20); manga progress per profile (PROF-1). Still shared: extensions and repositories |
| F-10 | Themes: Blue, glass | Verified (emulator, phones + TV): glass tier ReleaseFeaturesDeviceTest.glassTierIsSolidOnLowRamAndTv 44904447255 (phones) / 44906652590 (tv-api34); Blue: F10BlueThemeDeviceTest (tap Ao blue, drawn accent 0xFF7FB2FF on Tsuki Night and 0xFF1F5FBF on Washi Day) Pass 44909154883 (api33-lowram, api36) / 44909158248 (tv-api34) @9bf4c90b; fail-before 44908264228 (accent not applied: stayed gold), 2026-11-21 19:41 +06 | 354082ba, 7396955f; ADR-017 tint only | Device check; Blue theme presence to confirm on device |
| 2x | Hold for 2× speed | Built (26 files mention it) | — | Device check |
| F-18 | Manga previous / next chapter and continuous scroll across chapters | Fixed in code. Unit Pass: fail-before 44899238590 (main compiled; MangaChapterNavTest failed to compile: MangaChapterNav unresolved), pass-after 44896530640. Device: Pass, F18MangaChaptersDeviceTest OK on api36 + api33-lowram 44929260054 and TV api34 44929262746 (2026-11-21). Verified | Reader top bar has Previous / Next chapter; a "Read next chapter" card ends each chapter (vertical) or an extra page (paged). `MangaChapterNav`, test `MangaChapterNavTest` | Real-phone check |
| F-12 | Portrait drama swipe player | Retry path Pass (44904447255). Swipe on one shared player: Pass phones 44912687672 (after F12-HIST + F12-VIEW; fail-befores 44910276470, 44911557702, 44911548689), TV skips by design 44912689981. YouTube 3-min Short mark + landscape fit: unit Pass 44912685131 (fail-before 44910420105). F12-MODE vertical mode (non-YouTube 9:16, swipe up / down through the queue, Back to the normal layout) + F12-CHROME idle-hide: device Pass phones 44916676685, TV 44916693769 (never enters / skips), fail-befores 44913232245 + 44913945589 valid; test fixes F12-TEST-CLING, F12-TEST-CLOCK. Unit + lint full green r15 CI 44918012619 (44914717596 failed only on NBUG-13-PORT, fixed). Preload = links resolved ahead (ADR-056). F-12-FLAG (7e93879f, f12_vertical_shorts on): fail-before 44922624531 valid, CI 44922673093 Pass, phones 44922675456 Pass, TV 44922677658 Pass. Real-phone feel stays OMT-37 | df181804, 4f480803, 0288e8d, f8acd0c, 3d4d780c, 93c73933, a2222bf5, 215fb53d, 7eb17931, bf534934, c3ec754d (phones only), 7e93879f | Verified (flag on) |
| F-08 | Upcoming hub + Yumix carousel | Verified (emulator): UpcomingDeviceTest Pass on api33-lowram, api36, tv-api34 (44896550636, 44896552485) | 97895d02 (Simkl data, kill switch `UPCOMING`) | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: UpcomingDeviceTest Pass on api33-lowram, api36 and tv-api34 (44896550636, 44896552485). Status Pass (44896550636). |
| Country filter | Country filter for Live TV / feeds | Fixed in code (Live TV); unit green in 44896530640; Unit Pass: fail-before 44901945428, pass-after 44896530640. Device: Pass, LiveCountryDeviceTest OK on api36 + api33-lowram 44929260054 and TV api34 44929262746 (2026-11-21). Verified | Country from `tvg-country` or the iptv-org `tvg-id` suffix (`LiveCountry`), chip row on Live TV when 2+ countries, test `LiveCountryTest` | Real-phone check with a mixed playlist |
| F-17 | Profile statistics | Verified (emulator, phones + TV): ReleaseFeaturesDeviceTest.statsPageShowsTheMapAndTheSelectedDay Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | `StatsScreen.kt`, `StatsMath.kt`; ReleaseFeaturesDeviceTest PASS 44500142144 | Real-phone check |
| F-03 | TeraBox / DiskWala | Verified (unit): TeraBox B-29 44820338741, DiskWala B-30 44827815442; signed-in TeraBox = OMT | Public share links (CloudRecapture, LinkHosts) | Login path needs account cookies (ADR-037); 30 s cut-off is a bug (fixed as B-29) |
| F-04 | User WebView site list | Verified (emulator, phones + TV): ReleaseFeaturesDeviceTest.siteListSearchFilters Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 49b28a38 | Device check |
| F-05 | Nuvio / SkyStream / Vega repositories | Partial | Step 1 detected (E14 Pass 2/2); Vega runs (ADR-047, device 44593053433 / 44593085810 / 44600322143); Nuvio runs (F-05-N, ADR-057): device 44982374518 3 of 4, error-text bug F-05-N-ERR fixed, device 44985807462 Nuvio 4/4 + Vega 3/3 on api36 + api33-lowram; flags scripts_nuvio + f05_stream_addons on (F-05-N-ON, CI pending); manual OMT-73; SkyStream (F-05-S, ADR-058): fail-before 44988073278, pass-after r15 CI 44992235866, device 44992834769 SkyStream 3/3 on both phones, flag scripts_skystream on (F-05-S-ON b5c61f85); OMT-74 | Stremio stream-only add-ons built (F-05-STREMIO 5f60ccea, flag f05_stremio_streams on): fail-before 44993805121 valid; pass-after r15 CI pending; OMT-75 |
| F-06 | Volume boost 200 % | Verified (emulator): VolumeBoostDeviceTest Pass on api33-lowram, api36, tv-api34 (44896550636, 44896552485) | 84861229 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: VolumeBoostDeviceTest Pass on api33-lowram, api36 and tv-api34 (44896550636, 44896552485). Status Pass (44896550636). |
| F-07 | External player | Verified (emulator, phones + TV): ExternalPlayerDeviceTest Pass on api33-lowram and api36, run 44904481596 @a7bbd80e, 2026-11-21 19:00 +06: Watched(66000, 60000) for MX and for Choose each time (position and Referer header both made the round trip). Unit Pass (fail-befores 44890380227, 44894173529; pass-after 44894227826). Earlier fails were test faults F07-T..T4. TV Pass: tv-api34 44908157533 (2 tests), 2026-11-21 19:41 +06 | 7713c6e8; position return is built (`ExternalPlayers.result` reads `position` / `extra_position` with `return_result`). Correction: the first version said "not built" | Real-phone check with MX / VLC / mpv Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: ExternalPlayerDeviceTest Fail on both phones (finding F07-T: the stub player in the test APK crashed, NoClassDefFoundError kotlin/collections/ArraysKt, the test APK has no Kotlin runtime in its own process; Yumeiro did start it with the right extras). Stub rewritten in Java, rerun pending. Pass-after CI for F-07-VIS + F-07-RES: 44894227826 Pass (349 classes, 0 failures). |
| F-09 | Watch Later + Favourite on posters | Verified (emulator, phones + TV): PosterActionsDeviceTest (long-press Favourite) on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV (Menu key -> Watch Later) TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 26032196 | Owner reports taps not working: cause found (Yumix feed, Top 10, Continue watching had no long press); fix F-09-FEED: device Pass 44996395630 (phones, 5/5) and TV api34 44997163709 (5/5); CI Pass 44997677870 (Build, test & lint) |
| F-11 | Manga OCR translation | Built behind a flag | `MangaTranslate`, `MangaTlPlan`, flag `MANGA_TRANSLATE`, reader Translate sheet (device test MangaTranslate). Correction: the first version said "not built" | Real-phone check, size budget |
| F-13 | Offline manga | Verified (emulator, phones + TV): MangaOfflineDeviceTest (half-failed save leaves nothing; saved chapter opens offline) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 84987542 | Device check |
| F-14 | Live TV failover | Verified (emulator, phones + TV): FinalCoverageDeviceTest.j_f14 (dead link fails over to the channel's other link) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | 6658998a | Limit parallel probes (FREEZE-3) |
| F-15 | EPG Now / Next | Verified (emulator, phones + TV): FinalCoverageDeviceTest.k_f15 (Now / Next from XMLTV) Pass on api33-lowram and api36, run 44904447255 @a7bbd80e, 2026-11-21 19:00 +06. TV Pass: tv-api34 run 44906652590 @a7bbd80e, 2026-11-21 19:17 +06 | af0a5a14 | Device check |
| Airing alerts | Push alerts for airing episodes | Client Verified: unit + device 44939449545 / 44942929078 (AIR-T1). Worker deployed 2026-11-20 (SERVER.md, migrations 0003-0005; /v1/health ok 2026-11-22). End to end on a real phone: owner test OMT-52 | `airing` in 6 files | End-to-end notification check |
| F-21 | Rich extension metadata on every extension row (Hikari style), owner request 2026-11-21 | Built. Unit Pass (ExtensionLabelsTest: fail-before 44878962117, pass-after green in 44896530640); device check Pass 44994337536 @7c1ffce3 (F21ExtensionLabelDeviceTest on api36 + api33-lowram, 2026-11-22); TV api34 Pass 44997163709 @58c90cdc | F-21 commit on r15-stability | Flag `f21_extension_labels` (on, kill switch). Apply in three places: Extensions market / browser, Installed list, the "Show videos from" filter sheet. Each row: title + language code chip (`HDHub4u` `HI`); second line `12 sources • CloudStream • Nuvio • <repo name>` = exact source count, ecosystem tag (CloudStream, Nuvio, Vega, Stremio, Hikari/Yumeiro), repository name, one line with clean ellipsis. Purpose: tell apart same-name extensions from different repos or ecosystems. One shared composable used by all three lists; TV focus and font-scale 200 % checked; unit test for the label builder (count, tags, truncation); device screenshot test on phone + TV. |
| F-22 | Upcoming page: filters + date-grouped sections, owner request 2026-11-21 | Part built. Unit Pass (F22UpcomingSectionsTest: fail-before 44879571787, pass-after 44884554686, green in 44896530640); filters: Category = Series / Movies / Anime chips (built); Country + Genre built as F-22-C from TVmaze (Simkl has neither, re-checked 2026-11-22), fail-before 44994603153 + pass-after PENDING, OMT-76; Extension filter Blocked (a calendar entry is not tied to an installed extension) | F-08 97895d02 | Filter bar with three filters: Country (JP, US, KR, IN, BD, …), Category (Movies, Series, Anime, Web Series, Drama), Extension (installed extensions, same F-21 row selector). Group items by date with headers `Today • Sat, Nov 21`, `Tomorrow • Sun, Nov 22`, then `Sat, Nov 21, 2026` (category prefix when one category is filtered, e.g. `Anime • Sun, Nov 22, 2026`); dates in the user's time zone. Each card shows air time, episode number, genre and country badges. Filters persist per profile. Tests: grouping/labels around midnight and time zones (unit), filter combinations (unit), device check phone + TV. |

## Part 3: bugs

| ID | Bug | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| CL-1 | Audit the `crash-log` branch | Done for the only file there (freeze report) → FREEZE-1 | crash-log c12d27f4 | Owner: keep uploading reports there |
| X-1 | Nuvio / SkyStream / Vega import fails | Verified (emulator, phones); real-repo check OMT-73 / OMT-74 | Vega, Nuvio and SkyStream import and run; Nuvio on (F-05-N-ON, device 44985807462); SkyStream on (F-05-S-ON, device 44992834769) | — (real repositories: OMT-73, OMT-74) |
| B-3 | TeraBox stops at 30 s; slow cloud-link detection | Not reproduced | `CloudRecapture` has no 30 s cap (refresh budget 2 per 10 min, capture timeouts from `PageStreamCapture`). Likely cause to confirm: the page served a preview stream (TeraBox gives short previews without login) | Needs a real share link that stops at 30 s, then a fail-before test on the captured URL |
| T-1 | Raw torrent info-hash → magnet | Fixed (B-12). Unit Pass (fail-before 44771332825, pass-after 44775798224; still green in 44896530640) | `LinkHosts.magnetFromHash`; Play Link accepts a bare hash; Stremio infoHash streams become MAGNET links with the add-on's trackers (download through TorrServer) | Real-phone check with TorrServer |
| S-1 | Search hangs, every keystroke fires, feed shuffles | Guarded in code; hang not reproduced | `OnlineSearchScreen` already waits 450 ms after typing, cancels the old search (LaunchedEffect key), `OnlineRepository.search` runs sources with a permit limit and a timeout, results ranked stably (`SearchRanker`). Correction: the first version of this list said "no debounce"; that was wrong | Reproduce on a real phone with many extensions; FREEZE-1 removed per-provider work from this screen too |
| PIP-1 | Back on the stream page should go to PiP | Built | `PlayerBackRule.leave` sends Back to PiP when playing and PiP is supported, following Settings → Player → Back button | Real-phone check |
| PIN-1 | Pin cap 8, duplicate extension cap 2, remove the redundant pinned row | Verified: B-18 74d608bd (44775798224), cap 8, PIN-1-DUP be70b5a1 (fail-before 44936722531, pass-after 44944975133) | Pin cap 8 exists (`PinnedExtensions.MAX = 8`). The pinned chip row on the Yumix feed is removed; pins order the filter sheet | Same-name extensions from two repos: see PIN-2 |
| PIN-2 | Same extension name from two repos shows once | Fixed (B-22, bd3b4ca6). Unit Pass (SameNameExtensionsTest: fail-before compile, see BUGS B-22; pass-after 44775798224; still green in 44896530640) | `ExtensionManager.uniqueNames`: per name the earliest installed keeps it (its history / bookmarks / pins stay), the others become "Name · Repo" | Real-phone check with two repos shipping the same name. Note: if the first one is removed, the other gets the plain name back after a restart and its "Name · Repo" history stays under the old label |
| BUG-1 | Resume across entry points | Verified (emulator) | ResumePointsTest; device runs in CHECKLIST | Real-phone check |
| BUG-2 | YouTube channels + quality selector | Verified (emulator) for channels; quality = BUG-4 | 44116513171 | Real YouTube is behind YT-WALL (P3 / P5 / M3 Blocked) |
| BUG-3 | Download speed drops after 50 % | Blocked: owner (real phone, OMT-20) | 40ea96e, 3c85a72 | Real-phone run |
| BUG-4 | Upscaling and quality mismatch | Verified (emulator); real YouTube Blocked: owner | Bug4QualityListTest | — |
| EXT-P | External player integration + gallery downloads | Verified: F-07 phones 44904481596, TV 44908157533; gallery app view = OMT-17 | F-07, NBUG-4b | Return position; gallery app view = OMT-17 |
| PROF-1 | Continue Manga leaks across profiles | Fixed (B-11). Unit Pass (MangaLibraryProfileTest: fail-before 44771329328, pass-after 44775798224; still green in 44896530640) | `MangaLibrary` file now from `Profiles.file` (default profile keeps the old file) | Real-phone check with two profiles |
| BUG-5 | TV UI | Verified (emulator) for NBUG-2a–e; TV-4 Built, not tested | runs in CHECKLIST | OMT-21 |
| BUG-6 | Music player touches pass through | Verified (emulator) | 43914724044 | — |
| BUG-7 | Player / IPTV freezes, missing downloads | Verified (emulator) except 7d (GPU, owner) | 43946324687 | Likely related to FREEZE-1 on real phones; re-test after the fix |
| NBUG-5 | Dub count | Verified (emulator) | ledger | — |
| NBUG-6 / 9 | DNS | Done per owner decision (sequential fallback 1.1.1.1 then 9.9.9.9); per-feature DoH deferred (ADR-037) | REMAINING-2026-11-19 | Real-network check OMT-31 |
| NBUG-7 | Backup restores extension list but not the binaries; composite keys for duplicate names | Verified (emulator, both phones) | G9-1/2/5/7, ADR-021; device 44315631356, 44318262848 | Real-phone restore after "Clear all data" |
| NBUG-8 | Infinite loading for one extension | Guarded in code (timeouts) | REMAINING row | OMT-34 |
| NBUG-10 | "Show videos from" lists only 2 | Verified (emulator, phones): Nbug10FilterSearchDeviceTest Pass on api33-lowram and api36, run 44901993315, with the NBUG10-IME fix (fail-before 44901948952, pass-after 44901991356). TV: Not run by design (the test skips on TV: the keyboard opens only on OK, FINAL-39; 44908157533 assumption skip) | 4c6345f8 | Device check Device run 44896550636 @33c65f8d (phones) / 44896552485 (tv-api34), 2026-11-21 17:26 +06: Nbug10FilterSearchDeviceTest Fail (finding NBUG10-T: api36 compared against only the rows drawn before the search, AnimePahe / Yumeiro Drama were below the fold and matched 'a'; lowram the system keyboard showed 14 s after the tap). Test fixed, rerun pending. |
| NBUG-11 | Adult titles in Most Played | Fixed (ADR-023); FREEZE-1 keeps it fail-closed | — | — |
| NBUG-12 / 13 | Access denied loop; proxy | Proxy Pass (NBUG-13 runs 44593053433, 44593085810, 44600322143); loop guarded | MASTER-CHECKLIST-FINAL | OMT-32 |
| NBUG-14 | IPTV logos | Verified | b212379 | — |
| NBUG-16 | Auto-update sync loop | Verified (CI + emulator + TV emulator) | NB16-1…7, ADR-025 | Real-phone check over a few days |
| C5 / C6 | Remaining audit items (C5) and original rounds 1-3 (C6) | Defined by the owner 2026-11-22: see section "C5 / C6 (owner definition 2026-11-22)" below. Each item: map to a ledger row, then Pass (run ID) / fix / reason | owner prompt 2026-11-22 | C5 and C6 mapped (sections "C5 mapping", "C6 mapping"); open: ST-17, TV-FP-1, skeleton loader check; owner: X2-1, OMT-9, OMT-12; current-code re-check Pass 44994337536 / 44994339669 |

## Part 3b: owner list 2026-11-21 21:44 +06 (Website, downloader, player, Home, navigation)

Owner's words kept short. Every row is mandatory, follows the fix procedure (research with URLs in the ledger,
fail-before on `ab/<id>-before`, pass-after with run IDs, a flag in FeatureFlags.kt) and is "done" only when
Verified. Status "Not checked" = not yet compared with the code; re-check with a wide code search first.

| ID | Bug / feature | Status | Evidence | Missing (must do) |
|---|---|---|---|---|
| OL-1 | Website tab state and auto-close: the Website WebView is not a persistent tab (cannot be minimized); playing a page's video in Yumeiro's player, or downloading and tapping "View Download", closes the website page | Verified: fail-before 44923702849 + device fail-before 44926511600 valid; pass-after unit r15 CI 44926431107, device api36 44926513936, lowram 44927936416, TV api34 44927947420 (ledger OL-1, OL-1-BUILD, OL-3-HOOK) | owner 2026-11-21 | Keep the website tab open and kept in the background (page, scroll, history); let the user minimize it and return to it |
| OL-2 | Website detected-stream bar has no controls | Verified: fail-before 44923147788 valid; pass-after unit r15 CI 44926431107 (ledger OL-2) | owner 2026-11-21 | Close, Reload and a down arrow to collapse / minimize the panel |
| OL-3 | Website detected streams do not follow the page: playing a second video keeps the first video's stream listed next to the new one | Verified: fail-before 44923308227 valid; pass-after unit r15 CI 44926431107, device api36 44926513936, lowram 44927936416, TV api34 44927947420 (ledger OL-3, OL-3-HOOK) | owner 2026-11-21 | Show only the stream of the video playing now, with its title |
| OL-4 | Website downloads start at once, without the Offline vs Permanent storage popup | Verified: fail-before 44926496585 + device fail-before 44931006317 valid; pass-after unit r15 CI 44926431107, device api36 44926513936, lowram 44927936416, TV api34 44927947420, phones 44929260054 / TV 44929262746 (ledger OL-4) | owner 2026-11-21 | Same popup as every other download, every time |
| OL-5 | Downloader popup shows quality only for YouTube | Verified: fail-before 44927927381 valid; pass-after r15 CI 44929819988 and 44932398355 (OL-5-T2), device PopupQualityDeviceTest phones 44929260054 / TV 44929262746; OL-5-HEIGHT fail-before 44928486611 + device 44933186744 valid (ledger OL-5, OL-5-HEIGHT, OL-5-T, OL-5-T2) | owner 2026-11-21 (relates NF-2b) | Quality whenever the source has more than one; audio / sub / dub track choice and subtitle options whenever available |
| OL-6 | Downloader modal is not user-friendly (repeated user reports) | Built b28af942 + ddfb93a9 (flag ol6_download_sheet). Fail-before 44928728716 + device 44932939164 valid; 4 of 5 unit cases and device phones 44929260054 / TV 44929262746 Pass; ol6_whereAndTipsAreFoldedAway Fail twice (OL-6-REVEAL, fixed ddfb93a9), test fix OL-6-T: Pass r15 CI 44944975133 (Build, test & lint job) | owner 2026-11-21 | Redesign the downloader modal (research other apps' download sheets first) |
| OL-7 | Player top-right Quality, Audio and 3-dot buttons misaligned, large empty space on the left | Verified: fail-before 44926463969 valid; OL-7-EDGE fail-before 44928288893 valid; pass-after r15 CI 44929819988 (ledger OL-7, OL-7-EDGE) | owner 2026-11-21 | Reposition with a UI retouch (phone and TV, portrait and landscape) |
| OL-8 | Website helper banner ("Browsing (site). Press play on any video ... Tap to hide") never closes by itself | Verified: fail-before 44922989207 valid; pass-after unit r15 CI 44926431107 (ledger OL-8) | owner 2026-11-21 | Auto-dismiss after 3 s; tap to hide stays |
| OL-9 | Home "Space" section has few shortcuts | Verified: fail-before 44926469201 + device fail-before 44931008815 valid; pass-after unit r15 CI 44926431107, device api36 44926513936, lowram 44927936416, TV api34 44927947420 (ledger OL-9) | owner 2026-11-21 | More shortcuts: new features, Telegram (TG), Profile and other key sections |
| OL-10 | Navigation customization lacks newer destinations | Verified: fail-before 44926473786 + device fail-before 44931866056 valid; pass-after unit r15 CI 44926431107, device api36 44926513936, lowram 44927936416, TV api34 44927947420 (ledger OL-10) | owner 2026-11-21 | Add Telegram (TG) and the other new / core sections to the navigation settings |
| OL-11 | Bulk episode download has no range or single-episode choice in the download sheet: today it offers This season, All episodes and Next 5 only (DownloadSheets.EpisodeDownloadSheet); one episode is only from its row icon | Verified: fail-before 44926724501 valid; pass-after r15 CI 44929819988 (OL-11-T test fix c3486a97) (ledger OL-11, OL-11-T) | owner 2026-11-21 | Scope in the downloader: This episode only, All episodes, Episode range (from N to M, e.g. 50 to 80); quality, audio / sub / dub and subtitle choices (OL-5) apply to every episode of the job; Offline / Permanent popup as today |
| OL-12 | Online Music playlist / album page has Play and Shuffle but no Download (YtMusicBrowse.DetailHeader) | Verified: fail-before 44927039015 valid; pass-after r15 CI 44929819988 (OL-12-TOUCH 197d8df1) (ledger OL-12, OL-12-TOUCH) | owner 2026-11-21 | "Download playlist" button in the header next to Play / Shuffle: queues every track, same Offline vault / Permanent choice as other downloads, live progress |
| OL-13 | Social update: About lacks Yumeiro's Facebook / Instagram / X / TikTok / Reddit (@officialyumeiro), Discord, and the developer's Facebook / Instagram / Telegram | Built 1ce6d8ca (flag ol13_social_links). Fail-before 44936197438, r15 CI 44936285767, Pass: device phones 44936349725 / TV 44936347387 | owner 2026-11-22 | Telegram https://t.me/yumeiroofficial, Discord https://discord.gg/eqkNymUAMJ, website yumeiro.click; developer facebook.com/siam1p, instagram asfakul_siam, Telegram @siamorwho |

## Queue: new findings (owner rule 7: not started until the queued items are done)

| ID | Finding | Status |
|---|---|---|
| DL-WEB | In-app web pages have no WebView download listener, so a plain file link there does not open the download popup | Queued (found 2026-11-22 14:05 +06 during the DL-ONE audit) |
| DEX-REG-1 | Preview build failed the 224-register check (VideoPlayerModalContent 225) | Fixed as a blocker of item D (preview APK); pass-after 45002048828 pending |

## Owner decisions needed (not reopened)

1. Telegram login: decided 2026-11-21, full login (TDLib). api_id / api_hash received 2026-11-21 and stored as repo secrets YUMEIRO_TG_API_ID / YUMEIRO_TG_API_HASH (BuildConfig TG_API_ID / TG_API_HASH). Never write the values in the repo.
4. Discreet launcher icon: dropped by the owner 2026-11-21. Do not build it.
2. Super-extension (E1) and a scheduled validation workflow (E2): the "no scheduled workflows" rule is for agent runs;
   a product schedule needs the owner's yes.
3. Vega / Nuvio / SkyStream stay one registry; import (step 2–4) is a new release lane.

## Order of work (Now / Next / Later)

Now (done; owner 2026-11-20 "no 2.0.1": these ship in 2.1.0)
1. FREEZE-1 (this push), FREEZE-2, FREEZE-4 counter, GATE-1 ANR test.
2. PROF-1 manga progress per profile (privacy leak).
3. S-1 search debounce (fires work per keystroke on the main path).
4. Owner real-phone tests U-30 and the mandatory list, then release 2.0.1.

Next (2.1, owner 2026-11-21: no release until all is fixed and tested)
- Owner list OL-1..OL-12 (Part 3b; OL-1..10 added 2026-11-21 21:44 +06, OL-11..12 added 2026-11-21 22:42 +06): taken right after F-12 is closed (flag flip + its runs), before Part 8 step 2.
- F-02 Telegram full login (TDLib, ADR-052): app code Built, not tested (flag off) 2026-11-21; engine pins and device tests next.
- B-29 TeraBox full video (done in code), B-30 DiskWala hand-off (done in code), B-31 built-in torrent engine.
- Owner goal 2026-11-21: Yumeiro at the top of its class (CloudStream, Hikari, Aniyomi, Nuvio). Matrix, bars and the G-01..G-10 gap rows (debrid, skip credits, seek previews, sync, MDBList, FCast, ...) in docs/GOAL-TOP-OF-CLASS.md; they follow the owner's order of work.
- B-31 owner add 2026-11-21: the built-in engine is always ready (one engine and one loopback server for the app's life, no per-link setup, no TorrServer); every magnet, info-hash or .torrent link plays; and it should speed up download, playback start and seeking where possible (sequential + priority pieces around the play position, DHT/PEX/LSD and extra public trackers, peer and connection limits sized for a 2 GB phone and TV, resume data so a replay starts from cached pieces). Test hash BF2063A5E9A69F14716288FCA7C2D6078BDA7369.
- B-31 status 2026-11-21: Built, not tested (ADR-053, flag `b31_builtin_torrent` off). Next: CI on head; device test with the test hash on api36, api33-lowram, TV api24 (first frame <= 15 s, seek <= 5 s, memory on 2 GB); then OMT for a real pack and a .torrent URL.
- B31-FRAME (found 2026-11-22 while reading every doc): the goal bar is torrent FIRST FRAME <= 15 s and seek <= 5 s on the test hash, but BuiltinTorrentDeviceTest measures the first 64 KB over the loopback HTTP server, not a decoded frame in the player. Status: Blocked (emulator): B31FrameDeviceTest written; device 44997879586: the emulator HEVC decoder cannot decode the 10-bit test file (c2.goldfish.hevc.decoder). Real phone: OMT-79. To do: a device test that plays the test hash in the real player and logs onRenderedFirstFrame and the seek-to-frame time; the flag `b31_builtin_torrent` stays off until it passes.
- B-31 seek 2026-11-21 17:20 +06: device seek 7.6 s / 10.7 s (phones), 12.7 s (TV) over the 5 s budget (B31-SEEK2); fix: request_queue_time 1 and the seek piece alone at the front; device re-run pending.
- B-32 extensions that load nothing ("Nothing came back from this extension", e.g. HindiSubAnime): find the real cause and say it. Status: Built, not tested. Cause for HindiSubAnime: its parent AnimeDekhoProvider reads `a.dgt-card`, which hindisubanime.co does not use (extension bug). Yumeiro now checks the site and says the real reason with a fix button.
- F-21 extension metadata rows, F-22 Upcoming filters + date sections.
- E1 Yumeiro master extension (replaces the removed starters, B-26).
- E4 engine (scheduler, SWR, quarantine, throttling) and FREEZE-3 thread cap.
- E3 ABI splits + website detector.
- F-18 manga chapters, F-20 / F-19 security questions, `.nomedia` (discreet icon dropped by the owner 2026-11-21).
- PIN-1, T-1 magnet, B-3 TeraBox 30 s, NF-2b quality picker, F-07 position return.

Later (2.2+)
- E2 curated Live TV per country, F-11 OCR translation, F-05 import steps. (Telegram login moved to Next: owner
  decided full login 2026-11-21. E1 moved to Next: owner 2026-11-21.)

## Acceptance for every item

Root cause written, regression test that fails before and passes after, commit body Cause / Fix / Tests / Evidence /
Timestamp / Ledger, run ID in the ledger, and an owner real-phone check for anything users touch.

## C5 / C6 (owner definition 2026-11-22)

Owner, 2026-11-22: earlier agents could not find C5 / C6; they are defined here. Each item is mapped to a
ledger row and verified, fixed, or listed with a reason. A row already Pass only gets its run ID linked.

### C5: open items from the audits
ER-28-TV34, X13-6, NB15-OB2 (the "Drag handle" label read out on TV sheets), NB11-OB1, NB12-5b, NB14-OB1,
NB15D-OB1, NB16-6, NB16-7, TV-FP-1, EXT-T1, ST-7, ST-17. Player design items: a video sleep timer; a "More"
button for the portrait player's top control row; one Back press leaves the player on phones (the steps stay
on TV); SRT to WebVTT conversion for Cast.

#### C5 mapping (2026-11-22)

Earlier evidence is in docs/DONE-LEDGER.md (line numbers as of today). Current-code re-check: device phones
44994337536 (LocalPlayer, Nbug11/12/14/15, F21) and TV api34 44994339669 (SheetFocusTv), both at 7c1ffce3: both Pass (2026-11-22).

| Item | Ledger | Earlier evidence | Status |
|---|---|---|---|
| PL-16 portrait More | DONE-LEDGER 203, 435 | 3ce721d; device 43747147539, 43748560619 (LocalPlayer 9/9, both phones) | Pass (43748560619); re-check Pass 44994337536 (current code 7c1ffce3) |
| PL-17 video sleep timer | DONE-LEDGER 204, 435 | 3ce721d; device 43748560619 | Pass (43748560619); re-check Pass 44994337536 (current code 7c1ffce3) |
| PL-18 one Back leaves the player on phones | DONE-LEDGER 205, 435 | c832bc2; device 43748560619 | Pass (43748560619); re-check Pass 44994337536 (current code 7c1ffce3) |
| PL-19 SRT to WebVTT for Cast | DONE-LEDGER 199, 435 | 23390e6, SrtToVttTest; device 43748560619 | Pass (43748560619; SrtToVttTest in r15 CI 44992235866) |
| NB15-OB2 / ER-28 ("Drag handle" read on TV sheets) | DONE-LEDGER 660 | fix 2564ef48; fail-before 44445709090 / 44445734996; pass-after 44448997376 / 44449003874 | Pass (44449003874); re-check TV Pass 44994339669 (current code 7c1ffce3) |
| ER-28-TV34 | DONE-LEDGER 660 | old code did not show the bug on tv-api34 (44445713091) | Not run: cannot reproduce on old code by design; fix covers it (TV re-check Pass 44994339669) |
| X13-6 phone Jackson 2.19.4 | DONE-LEDGER 131, 583 | owner decision (OMT-23, ADR-035): phone keeps the version | Blocked: owner decision (OMT-23) |
| NB11-OB1 | CHECKLIST 42 | 8c6ca928, WsFBugBatch1Test, unit pass-after 44610265729 | Pass (44610265729); re-check Pass 44994337536 (current code 7c1ffce3) |
| NB14-OB1 | CHECKLIST 45 | 4c8076b0, OMT-42, unit pass-after 44610265729 | Pass (44610265729); real logos OMT-42 |
| NB15D-OB1 | CHECKLIST 46 | 414f6c8c, unit pass-after 44610265729 | Pass (44610265729); re-check Pass 44994337536 (current code 7c1ffce3) |
| NB12-5b | DONE-LEDGER 646 | unit-tested (BackgroundUpdatesTest family); no device hook | Pass unit (r15 CI 44992235866); real site: owner |
| NB16-6, NB16-7 | DONE-LEDGER 647 | 2b06fb2, dc44432; BackgroundUpdatesTest | Pass unit (r15 CI 44992235866); background timing on a real phone: owner |
| TV-FP-1 | DONE-LEDGER 669 | FeedPosition on TV: youtube now passes on tv-api24 (44995889578); live still ends on Music on tv-api24 (touch and semantic click, 44997306578) | Fail (TV-FP-1: 5 of 6; owner check OMT-78) |
| EXT-T1 | CHECKLIST 93 | 30 emulator runs, cannot reproduce | Blocked: owner phone test OMT-26 |
| ST-7 | CHECKLIST 92 | jank only on software-rendered emulators | Blocked: owner real phone OMT-12 |
| ST-17 | CHECKLIST 73, DONE-LEDGER 179 | 1 spin in 40 stress runs; 44145301927 20/20 clean | Fail (ST-17 open, logged per campaign) |

### C6: original rounds 1 to 3
- Player: some videos with no audio; source rotation, including rotating again when a manually chosen source
  is dead; no blank captcha page; missing episode thumbnails; 10 sources at a time with "Load more"; silent
  background source gathering so playback starts at once; choosing an audio language only changes the audio
  track; a quality selector on every video (own engine if the stream has none); no freeze or "not
  responding"; the YouTube resolution switcher does not hang; time shows 00:00, not "--:--"; Player info
  overlay; "Quality and audio" settings (default quality, default audio, default speed, decoder, renderer,
  Anime4K, GPU tier, default player, reorderable controls, resume playback); PiP rules; skeleton or universal
  loader by video type; Yumeiro Drama.
- Home and Yumix: "Jump back in" syncs on app open; the extension bar hides on scroll down; search inside
  "show videos from"; no extension header between sections; drama on by default; pin favourite extensions;
  the feed is never replaced by cached rows while scrolling.
- Library, music, manga: no fake liked songs, playlists or play time; music defaults to Online when online and
  On-device when offline; infinite online music feed; redesigned manga page.
- System: backup and restore; notifications work with the app closed and not all at once; custom theme image
  from the gallery; smooth scrolling on low-end devices and a non-hanging extension market; device heating;
  crash log saved as a file; universal APK with all ABIs and one app for TV; no OOM and no ANR; forced
  download for sources that block downloads.

#### C6 mapping (2026-11-22)

Mapped to docs/INVENTORY.md rows (its Covered-by column holds the run IDs) and docs/DONE-LEDGER.md. "Pass" = a
device or CI run proves it; "Unit" = unit tests only; "owner" = needs a real phone or TV (OMT).

| C6 item | Inventory / ledger | Evidence | Status |
|---|---|---|---|
| No audio on some videos | P6, Round 13 (60cffac) | LocalPlayerDeviceTest a 43708220122 | Pass (43708220122) |
| Source rotation, a dead manual pick rotates again, Load more (10 at a time) | P9 | OnlinePlaybackDeviceTest d_ 43701056608 | Pass (43701056608) |
| No blank captcha page | NBUG-12 (DONE-LEDGER 646), BrowserCheck | NB12 fixes; device fail-before 44394971017, pass-after in the ledger row | Pass (ledger NBUG-12); real sites: owner |
| Missing episode thumbnails | Y7, EpisodeStills | OnlineExtrasTest, EpisodeCarouselTest | Unit |
| Silent background source gathering, playback starts at once | P2, Round 13 (a7a3bc2) | OnlinePlaybackDeviceTest 43701056608 | Pass (43701056608) |
| Audio language changes only the audio track | P6 | LocalPlayerDeviceTest a 43708220122 | Pass (43708220122) |
| Quality selector on every video (own engine when none) | P5 | QualityListDeviceTest, PlayerSwitchDeviceTest 43701056608; OL-5 popup 44929260054 | Pass (43701056608) |
| No freeze or "not responding" | X3, GATE-1 | gate trio 44992836781 (lowram) | Pass (44992836781); real phone OMT-72 |
| YouTube resolution switcher does not hang | P3, P5 | PlayerSwitchDeviceTest c_ 43701056608 | Pass (43701056608) |
| Time shows 00:00, not --:-- | P12 | LocalPlayerDeviceTest a 43708220122 | Pass (43708220122) |
| Player info overlay | P12 | LocalPlayerDeviceTest a 43708220122 | Pass (43708220122) |
| Quality and audio settings (defaults, decoder, renderer, Anime4K, GPU tier, default player, reorderable controls, resume) | P13, S27, P16 | SE-5 / SE-6 switches and choice rows 43974089878; qa_gpu_tier OMT-16 | Pass (43974089878); GPU tier: owner OMT-16 |
| PiP rules | P10 | LocalPlayerDeviceTest a,c 43708220122; SurfaceModeDeviceTest | Pass (43708220122) |
| Skeleton or universal loader by video type | P1, Y1 | code read only | Not run (no device test; screenshot check in the review APK) |
| Yumeiro Drama | P4, Y4 | DramaPlaybackDeviceTest 43701056608; DramaHealthTest | Pass (43701056608) |
| Jump back in syncs on app open | H2, G5-PC2 | h2 43693532355 / 43696246671; G5-PC2 fail-before 44200206502 | Pass (43696246671) |
| Extension bar hides on scroll down | Y3 | code read, no bug | Blocked: owner feel test OMT-9 |
| Search inside "Show videos from" | Y5, NBUG-10 / G15-1 | PinnedExtensionsDeviceTest 43705847970; G15-1 tv-api34 44480266808, phones 44487432724 | Pass (44487432724) |
| No extension header between sections | Y1 | Yumix feed unit tests | Unit |
| Drama on by default | Y4 | every Yumix device run opens on drama | Pass (43701056608) |
| Pin favourite extensions | E5 | PinnedExtensionsDeviceTest 43671202859, 43705847970 | Pass (43705847970) |
| Feed never replaced by cached rows while scrolling | H1, FEED-1, GATE-1-FEED-LATE | FeedPositionDeviceTest 43683520404 / 43687398312; r15 CI 44992235866 | Pass (44992235866) |
| No fake liked songs, playlists or play time | L6, L7, L9, G5-EP | LibraryDeviceTest#a 43738288853; LibraryEmptyProfileDeviceTest 44201835648 | Pass (44201835648) |
| Music Online when online, On-device when offline | M2 | inventory M2 | Unit; real network switch: owner |
| Infinite online music feed | M3 | FEED-1 append-only feed (device), Round13LogicTest, LivePagedTest | Pass (inventory M3) |
| Redesigned manga page | T1, T2, G7-MR1, G7-MB1 | MangaTallPageDeviceTest 44239024442; MangaDuplicateDeviceTest 44245826911 | Pass (44245826911); reading: owner OMT-11 |
| Backup and restore | S25, NBUG-7 | BK-1 device; SE-1 restore CI 43877618176 | Pass (43877618176) |
| Notifications with the app closed, not all at once | X5, X5-2 (NotifyPacer), NOTIFY-1 | ClosedAppAlertDeviceTest; ledger X5-1, X5-2 | Pass (ledger X5-1 / X5-2) |
| Custom theme image from the gallery | S26 | BackdropImportDeviceTest (SE-2, SE-4) | Pass (ledger SE-2 / SE-4) |
| Smooth scrolling on low-end, extension market does not hang | X3, ST-7, E1 | ExtensionsDeviceTest 43704211410; ST-7 jank only on software emulators | Pass market (43704211410); scrolling: owner OMT-12 |
| Device heating | X4, P19 | HeatGuardDeviceTest; LocalPlayerDeviceTest d 43708220122 | Pass (43708220122) |
| Crash log saved as a file | X6 | CrashUiDeviceTest (X6-1, CR-1) | Pass (ledger X6-1) |
| Universal APK with all ABIs, one app for TV | X10-1, X9 | release build review (DONE-LEDGER 116); the TV runs use the same APK | Pass (r15 CI review-APK lane) |
| No OOM, no ANR | X2, X3, ST-17 | ST-2, ST-18 emulator; gate trio 44992836781 | Pass on emulator (ST-2, ST-18, 44992836781); Fail ST-17 (1 spin in 40 stress runs); X2-1 (baseline profile gain on real hardware) Blocked: owner OMT-12 |
| Forced download for sources that block downloads | D2 | DL-1 device, DL-9 CI, DL-10 emulator | Pass (ledger DL-1 / DL-10) |
