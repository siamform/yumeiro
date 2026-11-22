# Yumeiro 2.1.0 release audit (independent)

DOCS UPDATE IN PROGRESS: this file is being written step by step; sections marked "open" are not finished.

Auditor started 2026-11-22 18:56 +06 (12:56 UTC). Branch `r15-stability`. Nothing here trusts an earlier "Pass" line;
every result below has a run ID or file the auditor checked, or it says "Not checked (reason)".

## 1. Scope and method

- Range: base `e47f450f` (v2.0.0 tag, = `git merge-base origin/main r15-stability`) .. HEAD.
- At start HEAD = `dd4ae197` (2026-11-22 10:51 UTC). Count: **243 commits** in base..HEAD (the "44 commits" in the
  brief is the count since `b553505`, not since 2.0.0). Finding AUD-DOC-1 (doc drift, low).
- version.properties: 2.0.0 / 18 (needs 2.1.0 / 19 at release; see section 9).
- No local JDK or KVM in the audit sandbox: compile, unit and device tests only through CI.

## 2. Owner list 2026-11-22 evening (OWN-1 .. OWN-19), triage

The owner sent ten items with "hurry, fix all and prepare release". Blockers (Part 5 definition) are fixed now with the
standard method; the rest are fixed now only if small, safe and testable before the final gate, else queued for 2.1.1
with a reason. Status is updated in this table as work lands.

| ID | Owner item | Root cause found (file) | Blocker? | Plan / status |
|---|---|---|---|---|
| OWN-1 | Page transitions on My List / Reading and across Yumix -> details -> player close or cut off | **Not reproduced** in the audit (no device here; no device test shows it). Code reading, two likely causes: (a) `popSub` (MTPlayerApp.kt:254) has no guard while a 340 ms push animation runs, so a fast double Back pops two pages; (b) `navigateSub` for reusable routes (SubNav.reusable, MTPlayerApp.kt:245-253) pops back to an existing My List / Reading entry, which closes every page above it (looks like "the page closed by itself"). A guard on pops could also drop pops the app makes itself (player close, deep links): too risky to add in the code freeze without a repro | No (no crash / data loss; not reproduced) | **Fixed** bc768506 (owner order 2026-11-22, 3rd message): NavGuard drops a second push of the same route within 700 ms and a second button Back within 350 ms (fast double taps popped two pages); Yumix -> details: the page slides in under the loading layer, which clears after 450 ms (before: the layer vanished first and the feed flashed). Flag `own1_nav_guard` (on). Test Own1Own6OpenTest. Final CI: see §5. Owner phone check OMT-89 |
| OWN-2 | Online features stop working after skipping the tour, a fresh install or an update | Code reading: OnboardingGate releases correctly after Skip (MTPlayerApp.kt:1672-1681, SetupGuide.kt:216). The one setting that differs between "did the setup" and "skipped / fresh / never touched": DNS. The checklist step "Set up DNS" (SetupGuide.kt:85) is the only place a user picks a resolver; skipped = System DNS + auto bypass (NetworkSettings.kt `DnsConfig()`). On networks whose DNS hijacks or blocks hosts in ways the bypass does not notice, sources fail. An update keeps the old default too (no `dns_provider` key saved). Second cause on fresh installs: no source installed at all (OWN-9). **Probable** root cause: not reproducible in CI (runners are not on a blocking network) | Yes (core flow) | **Fixed with OWN-7** (78a667f6). Owner home-network test OMT-83 decides whether it is the whole cause |
| OWN-3 | Telegram sign-in with API ID, API hash and phone code, like Hikari | Already built (F-02 login, TDLib, ADR-052) behind flag `f02_telegram_login` = **off**; it uses the build's own api_id (repo secrets). Hikari lets the user type their own api_id / api_hash (my.telegram.org). Full sign-in is Blocked in CI (Telegram has no public test accounts, tdlib/td#3083); OMT-63 | No (feature, flag off) | **Built** 9704ae29 (owner order 2026-11-22, 3rd message, overrides the 2.1.1 deferral): the sign-in screen takes the user's own API ID / API hash from my.telegram.org (like Hikari); the hash is sealed with the Android Keystore (SecretBox); user keys win over the build's; a build without keys asks for them first; new keys close TDLib and drop local Telegram data. Flag `f02_telegram_login` now **on**. Test Own3TelegramKeysTest (key checks). Full sign-in with a real account: still Blocked in CI (tdlib/td#3083) = owner OMT-63 / OMT-90 |
| OWN-4 | Vega, SkyStream and other single source links not accepted in the Extension Market add box | **Found**: Extension Market "Add repository" (ExtensionMarketScreen.kt:315-327) and Extensions "Add repository" (ExtensionsScreen.kt:360-369) called `ExtensionManager.addRepository` (CloudStream only). `UniversalAdd.add` (every format) was used only by "Install from link" / "Add anything" | Announced-feature gap | **Fixed** a4dd5ddf: both dialogs try CloudStream first, then the universal detection (`UniversalAdd.addFromRepoDialog`). Test Own4RepoDialogTest; fail-before (valid) CI 45025907314 (pending). A single `.sky` file stays "recognised, can't run yet" (unchanged, honest message) |
| OWN-5 | Vega and other managed repos: no installed status, not in the installed list, no shortcut | **Found**: EcosystemRegistry (Vega / Nuvio / SkyStream) sources were never part of the Extensions list; only Sources -> Script repos (tab 5) showed them | No (usability) but small and testable | **Fixed** a5d8d0d3: Extensions page card "<ecosystems> sources: N installed" with names and Manage -> Sources -> Script repos. Test Own5ScriptSourcesListTest; fail-before ab/own5-before CI 45026045898 (pending) |
| OWN-6 | Yumix -> details -> player is slow (feed itself fast) | Code reading: `OnlineSession.open` (online/OnlineSession.kt:107-124) waits for the full `OnlineRepository.load` (the extension's load call: network) before it navigates, so the tap shows nothing until the source answers; then the player waits for `loadLinks`. Improvement = open the page at once with the card data and fill it in (the way CloudStream / Hikari show the details shell first). It changes the open path of every extension and needs before / after numbers on a device | No (perf, works) | **Fixed** bc768506: a source known to open a title page navigates at once and loads in the background (OpeningKinds learns it); same-title loads already running are joined, not repeated (OnlineRepository.loadsInFlight). Flag `own6_fast_title_open` (on). Test Own1Own6OpenTest. Owner phone check OMT-89 |
| OWN-7 | Cloudflare as the default network | DNS default was System (NetworkSettings.kt DnsConfig) | Part of OWN-2 | **Fixed** 78a667f6: no saved choice -> Cloudflare DNS-over-HTTPS, mode Always; system DNS is the safety net when Cloudflare is unreachable; LAN names (.local, .lan, no dot) stay on system DNS; saved choices kept. Flag `own7_cloudflare_default` (on). Test Own7CloudflareDefaultTest (4); DnsSettingsRaceTest fresh-prefs line follows the new default (spec change, not weaker). Fail-before ab/own7-before CI 45025542939 (pending) |
| OWN-8 | Downloader: pick quality, subtitles, audio (sub / dub) before download; same in the player | Checked in code: the one download popup (DownloadSheets.kt, DL-ONE) offers real HLS heights, audio track and subtitles for website / HLS / player downloads (flag `ol5_download_tracks`, on); the episodes download sheet has Sub / Dub chips (DownloadSheets.kt:151), quality "up to" (:241) and subtitles None / "Save all the source has" (DownloadPrompt.kt:226-228). Choosing one subtitle language per episode batch is not offered (all are saved) | No (built) | **Fixed (gap)** 4c64bf4e: the episode batch download now offers the subtitle language from Online settings and English (before: none or all); tracks match by name or code (en, eng, English (CC), es-419). Flag `own8_batch_sub_language` (on). Test Own8BatchSubLanguageTest. Single-item quality / audio / subtitle choices were already built (DL-ONE) |
| OWN-9 | Official Yumeiro extension, none installed by default | **Confirmed**: no official extension exists. Owner decision 2026-11-21 "no third-party starter extensions; use the Yumeiro master extension (E1)". E1 (Yumeiro Hub) is **not built**, planned 2.2.0 (docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md). A fresh install has zero sources; the empty Home shows Get started (B-32) | No code fix possible in 2.1.0 without building E1 (new feature, freeze) | **Built** 7719e54e (owner order 2026-11-22, 3rd message). Correction: an official built-in source did exist (Yumeiro Drama, on by default since Round 13), but it was shown only in the market's drama shelf, so a fresh install looked empty. Now: new official source **Yumeiro Classics** (Internet Archive public domain: feature films, noir, sci-fi, silent, cartoons, classic TV; 18+ dropped twice), on by default, flag `own9_official_classics`; Extensions shows an "Official · built in" section (Classics + Drama); market Discover / Installed and Get started show them. Test Own9YumeiroClassicsTest. E1 (Yumeiro Hub, all-in-one) stays 2.2.0. Owner check OMT-91 |
| OWN-10 | TeraBox and "Linkwala" (read as DiskWala) not working; check how Hikari does it | **TeraBox, correction**: the 400210 "need verify_v2" seen first (12:59 UTC) came from a request that did not follow the share page redirect, so it had no jsToken: not a valid measurement. Re-measured 13:06 UTC with Yumeiro's exact request (redirect followed, jsToken, app_id params): `shorturlinfo` errno 0 in 4 of 4 tries; a bare request without the token gets 460030 "dragdrop" (slider check). So the anonymous step works; the signed-in steps (`share/list` dlink, `share/streaming`) need a TeraBox account: **Not checked (no TeraBox account in the audit)**. Hikari differs: share origin instead of terabox.app, a `browserid` cookie, one fresh-session retry on a verify answer. **DiskWala**: the share page in headless Chromium (13:0x UTC, XHR log) shows "Download the app to access this file", makes no file API call and has no video element; `temp_info` gives name and size only. Hikari's WebView fetch hook would capture nothing on today's page | TeraBox: maybe (announced link type) | **Fixed** 179d9e30 (human check opens the share page) + b44d5237 (studied Hikari's flow, no code copied): API calls go to the share's own origin, a per-session `browserid` cookie, one fresh-session retry after a verify / refused answer, a dlink already in shorturlinfo plays at once, extraction-code shares say so. Tests TeraBoxApiTest (3 new). DiskWala keeps the hand-off to its app (app-integrity check, Hikari also refuses it). Real-share check OMT-92 |
| OWN-11 | Owner 2026-11-22 (2nd message): "no audio download option while downloading a youtube video" | **Found** (2.1.0 regression from DL-ONE): YouTube moved into the shared popup. (a) DownloadPrompt.ask (DownloadPrompt.kt:118-122) skips the popup when "Ask before download" is off and starts the fallback = best video; (b) the audio row (TrackPicker, DownloadPrompt.kt:225) needed 2+ audio choices, and Offer.hasChoices (DownloadTracks.kt:41) too, so a video with one audio-only format had no audio option; (c) the row was titled "Audio track / Default", not "Audio only". Before DL-ONE the YouTube sheet always had Video / Audio tabs (NewPipe-style). | Yes (announced "Audio only for YouTube" did not work in these cases) | **Fixed** commit OWN-11 (flag `own11_yt_audio_choice` on): a YouTube video with an audio-only format always shows the popup, row "Save as: Video / Audio only · N kbps". Songs and other sources unchanged. Test Own11YtAudioChoiceTest. Fail-before ab/own1112-before (flags off = old behaviour) CI 45028979680; pass-after r15 CI 45028981973. OMT-87. |
| OWN-12 | Owner 2026-11-22 (2nd message): setting a profile PIN never asks for the reset / forgot question; removing it never asks for the current PIN | **Found**: ProfileSettings.kt EditProfileDialog: "Remove PIN" called Profiles.setPin(null) at once; "Change PIN" and the security-question rows ran at once; after a first PIN, the flow ended (security questions F-19 existed but only as a separate row). The current profile opens its edit dialog without a lock check (other profiles need unlock first). | Yes (owner order; a child on the open profile could remove the PIN) | **Fixed** commit OWN-12 (flag `own12_pin_guard` on): Change PIN, Remove PIN, Set / Change / Remove security questions ask for the current PIN first (same unlock as opening the profile, with "Forgot?"); a first PIN goes on to the security questions (Cancel skips). Test Own12PinGuardTest. Fail-before / pass-after: same runs as OWN-11. OMT-88. |
| OWN-13 | Owner 2026-11-22 (4th message): "Yumeiro's extension should provide everything without installing a single extension, all in one, not different and separate installation" (= E1 Yumeiro Hub, was 2.2.0) | Was: Yumeiro Drama and Yumeiro Classics, two separate built-in sources with two cards | Owner order | **Coded, CI pending**: one built-in source "Yumeiro" (sources/hub/YumeiroHub.kt, flag `own13_yumeiro_hub`): one card, one switch, one home with every shelf, one search over every part, titles open through their own part, old saved names alias to it. Classics gained documentaries (Prelinger), classic cartoons and films by country (Hindi, French, German, Italian, Russian, Japanese, Spanish, Chinese). Legal sources only. Not in 2.1.0 (stays E1 for 2.2.0): catalog Worker, resolver fan-out to installed extensions. Test own/Own13YumeiroHubTest |
| OWN-14 | Owner 2026-11-22 (4th): "on some page, open is normal, but close / back is not like how it opens, different and buggy" | **In progress** (audit of every pushed page's enter vs exit animation) | Owner order | open |
| OWN-15 | Owner 2026-11-22 (4th): locked profile, app swiped from recents, 2-3 h later: unlock -> force close, then stuck on the Yumeiro logo | **In progress** (process death + profile unlock path) | Yes (crash) | open |
| OWN-16 | Owner 2026-11-22 (4th): Yumix > Upcoming: filters hard-coded, design poor, no search; filter should be an icon, list dynamic (= F-22 rest) | **In progress** | Owner order | open |
| OWN-17 | Owner 2026-11-22 (4th): cross-check MAJOR-BUGS-AND-FEATURES-2.x.md + NEW-BUGS-FEATURES-2026-11-18.md; partial / fake / not done is never acceptable; new list of open items | docs/OPEN-ITEMS-2.1.0.md | Owner order | open |
| OWN-18 | Owner 2026-11-22 (6th): "in the download modal it never shows the file size ... every download modal should show quality, size, sub / dub and all needed info", mainly from the player page | Was: size only when the source gave it (YouTube); the player page, episodes and links showed none | Owner order | **Coded, CI pending**: online/download/DownloadSize.kt finds the size for the picked quality off the main thread (8 s cap): HEAD, then Range bytes=0-0, HLS master + media playlist (exact with BYTERANGE, else bandwidth x duration, marked "about"), DASH duration x bandwidth. Every popup shows chips (quality, Sub / Dub, format, audio tracks, source) and a size line; a batch says honestly why there is no total. Research: NewPipe reads Content-Length before listing streams; yt-dlp filesize_approx = bitrate x duration (ideas only, no code). Test own/Own18DownloadSizeTest |
| OWN-19 | Owner 2026-11-22 (6th): "in full screen there are lots of buttons, not good; show only necessary buttons and move others into the 3-dot menu" | Was: top bar showed every control that fit (8 in landscape) plus a second row of 7 chips | Owner order | **Coded, CI pending**: flag `own19_player_essentials`. Top bar keeps the first 3 controls in the user's order (default Quality, Audio, Subtitles) + More; Speed, Aspect, PiP, Info, Lock move to More (TopBarFit.essentials). Online row keeps Episodes + Next; Source, Subtitles, Sub/Dub switch, Cast, Download go under a More chip (old tags kept). Research: YouTube, Netflix, MX Player keep 2-4 icons + one menu. Test own/Own19PlayerEssentialsTest |

## 3. Per-commit table (W2)

open

## 4. Findings register

| ID | Severity | Blocker | File:line | Root cause | Fix / deferral | Evidence |
|---|---|---|---|---|---|---|
| AUD-CLAIM-1 | High (false claim) | Yes (false changelog / README / image claim) | CHANGELOG.md:64, README.md:56, scripts/release_kit_210.py:184, post-05 / square-05 images, yumeiro-web lib/changelog.ts:38 | "A curated list of about 20 working channels per country (E2)" is announced, but the curated lists were never published (origin/streams has no curated/ files; repo variable YUMEIRO_CURATED_LIVE not set; CHANGELOG-FULL E2 row: "In-app part Blocked: needs the first publish") | Fixed: wording now "country filter in big playlists"; E2 listed under "Still being checked"; both images repainted; website PR #5 text fixed in the same step | `git ls-tree origin/streams` (no curated/), CHANGELOG-FULL.md:227, :292 |
| AUD-CLAIM-2 | Medium | Yes (claim) | CHANGELOG.md "New" | "Smaller downloads per phone type (ABI split APKs) are ready" listed as new, but the switch (repo variable YUMEIRO_ABI_SPLITS) is off, so users get only the universal APK | Moved to "Still being checked" as "built, switched off" | release.yml:116-117, build.gradle.kts:108 |
| AUD-T7-1 | Medium (process) | No | app/src/test/.../network/Own7CloudflareDefaultTest.kt:60 | Auditor's own new test did not compile (`byteArrayOf(93, 184, ...)`: 184 is not a Byte). Found by fail-before 45025542939; it also broke fail-before 45026045898 (own5 branch is based on the OWN-7 commit) | Fixed aa590039 (r15), da7fa9c7 (ab/own7-before), 5cf606cf (ab/own5-before); both fail-befores re-run: 45027077707, 45027534810 | run logs "e: ...Own7CloudflareDefaultTest.kt:60:61" |
| AUD-FLAKE-1 | Low | No (test isolation) | app/src/test/.../scrobble/ScrobbleFlushMockedTest.kt:134 | Baseline CI 45024851633 on dd4ae197: 1830 tests, 1 failed: `listensOlderThan14DaysAreDroppedUnsent` expected `[[T2, T3]]` but got `[[T2, T3], []]`: one extra request without `track[i]` fields reached the test's mock client (the mock counts every request except /v1/lastfm/sign). Same test passed in earlier CIs (8/8 in 44227662448 and every later green run). The product rule (14-day cutoff, Scrobbler.kt:111-112) held: T2, T3 sent, T0, T1 dropped. Most likely a stray background call from another component sharing NetworkClient during the test | Not changed (code freeze; changing the test's counting would need its own review). Watch in the final CI; if it repeats, it is a finding with its own fix | reports artifact of 45024851633, TEST-...ScrobbleFlushMockedTest.xml |
| AUD-TB-1 | Low | No | this file, OWN-10 first version | Auditor's own wrong measurement (no redirect follow) reported as TeraBox needing a human check | Corrected above with the re-measurement | curl logs 13:06 UTC |
| AUD-AB-1 | Low | No | origin ab/*-before | 8 old fail-before branches still on origin (b27, b29, b30, freeze1, freeze2, gate1, prof1, t1) although the method says delete after | Delete after the owner's go (deleting is not reversible; listed in the checklist) | `git branch -r` |
| AUD-DOC-1 | Low | No | brief / HANDOVER-LIVE.md | "44 commits" counted from b553505; base..HEAD is 243. HANDOVER-LIVE header 14:12 vs progress log 16:50, stale "Pending runs" lines | Docs fixed in this audit | `git rev-list --count e47f450f..dd4ae197` = 243 |

## 5. Regression runs on the release candidate (W1)

| Run | Workflow | Commit | Started (UTC) | Result |
|---|---|---|---|---|
| 45024851633 | CI review_apk=true (baseline on the pre-audit head) | dd4ae197 | 12:56 | **Fail**: unit 1830 / 1 failed (AUD-FLAKE-1); gitleaks Pass; review APK skipped |
| 45025542939 | CI fail-before OWN-7 | ab/own7-before cab29234 | 13:04 | **Invalid**: test did not compile (AUD-T7-1) |
| 45027077707 | CI fail-before OWN-7 (redo) | ab/own7-before da7fa9c7 | 13:2x | **Valid**: only Own7CloudflareDefaultTest (2) failed |
| 45025907314 | CI fail-before OWN-4 | ab/own4-before c1e4665d | 13:08 | **Valid**: 1831 tests, only Own4RepoDialogTest.aNonCloudStreamLinkIsDetectedAndAdded failed ("This repository has no plugin lists" = old CloudStream-only dialog). Branch deleted |
| 45025909528 | CI fail-before OWN-10 | ab/own10-before 5d9a8a79 | 13:08 | **Valid**: 1831 tests, only TeraBoxApiTest.ownTenAHumanCheckOpensTheSharePageForTheUser failed ("TeraBox refused the share (error 400210)" = old dead end). ScrobbleFlushMockedTest passed here (AUD-FLAKE-1 intermittent). Branch deleted |
| 45026045898 | CI fail-before OWN-5 | ab/own5-before 1c8085fa | 13:10 | **Invalid**: OWN-7 test did not compile (AUD-T7-1) |
| 45027534810 | CI fail-before OWN-5 (redo) | ab/own5-before 5cf606cf | 13:3x | **Valid**: only Own5ScriptSourcesListTest failed |
| 45027652884 | CI review_apk=true (pass-after OWN-2/4/5/7/10 + release prep) | r15 51c507ba | 13:3x | cancelled by the next r15 dispatch (OWN-11/12 landed); logged |
| 45028979680 | CI fail-before OWN-11 + OWN-12 | ab/own1112-before 0ed0a622 (fix with both flags off) | 14:0x | **Valid**: only Own11YtAudioChoiceTest (3) and Own12PinGuardTest (4) failed |
| 45028981973 | CI review_apk=true (pass-after OWN-11/12) | r15 dfa5e334 | 14:0x | **Blocked** (Maven: 46 Robolectric failures from MavenArtifactFetcher IOException; not code; gitleaks Pass) |
| 45027687692 | device-check tv-api24 | r15 51c507ba | 13:4x | **Pass** |
| 45027685406 | device-check phones | r15 51c507ba | 13:4x | cancelled on the owner's order ("stop this CI"); logged |
| 45032539983 | CI review_apk=true, the first batch run (OWN-1/3/6/8/9/10 + debug logs removed) | r15 7719e54e | 14:1x | cancelled (head replaced by 385e293a); logged |
| 45032831510 | CI review_apk=true | r15 385e293a | 14:26 | **Blocked** (Maven 404: libtorrent4j 2.1.0-39, error_prone_annotations 2.30.0; build failed in 21 s) |
| 45033016908 | CI review_apk=true (rerun) | r15 fc433b21 | 14:30 | **Fail**: 1865 unit tests, 5 failed: Own12PinGuardTest x2 (pad's OK key off Robolectric's 470 dp screen), Own9YumeiroClassicsTest (%20 vs +), OnlinePagesTest (Classics on by default), AppNavigationCrashTest.quickNavigationThenHomeDoesNotCrash (**real NavGuard bug**: same page after a system Back was dropped). Fixed 39ca1e6b |
| 45032861815 | device-check phones default | r15 385e293a | 14:26 | **Fail**: HomeNavigationDeviceTest.h2 on api33-lowram (**real OWN-1 bug**: Back during the 450 ms title hand-off only hid the loading layer; fixed 8ff0e0d9); ExtensionsDeviceTest.e13 on api36 (precondition not met: 4 of 5 seeded drama-feed timeouts, external onshort.net; EXT-T1 test); DramaPlaybackDeviceTest on api36 (studios refused / timed out: external). CrashUiDeviceTest-a crashes on purpose |
| 45035791400 | CI review_apk=true | r15 ada9cff6 | 15:0x | cancelled (8ff0e0d9 changes app code); logged |
| 45037617014 | CI review_apk=true, final candidate | r15 8ff0e0d9 | 15:4x | running |
| 45037619473 | device-check HomeNavigationDeviceTest (pass-after h2) | r15 8ff0e0d9 | 15:4x | running |
| 45037636802 | device-check ExtensionsDeviceTest (e13 rerun) | r15 8ff0e0d9 | 15:4x | running |

## 6. Flags (W3)

Read from app/src/main/java/dev/asfakul/yumeiro/FeatureFlags.kt (78 flags at the audit head, 84 after the owner fixes; 2.0.0 had 19). Flags are local prefs only (no remote switch): a flag can be turned off only by a new build or by `adb` on a
debug/support build. So "behind a flag" means "built-in default"; the release kill switch is the next build.
One flag is **off**: `b31_builtin_torrent` (built-in torrent engine), listed only under "Still being checked".
`f02_telegram_login` was off; the owner's 2026-11-22 order (OWN-3) turned it on with own API keys; the real-account
sign-in stays an owner check (OMT-63 / OMT-90), said so in CHANGELOG.md. New 2.1.0 owner flags (all on):
`own1_nav_guard`, `own6_fast_title_open`, `own8_batch_sub_language`, `own9_official_classics`, plus OWN-7/11/12 flags.
Build-time switches, not app flags: E2 curated Live TV (repo variable YUMEIRO_CURATED_LIVE, not set: off) and E3 ABI
split APKs (YUMEIRO_ABI_SPLITS, not set: off). Both now only in "Still being checked" (AUD-CLAIM-1/2).

<details><summary>All 84 flags</summary>

| # | Enum | Key | Default | New in 2.1.0 |
|---|---|---|---|---|
| 1 | POSTER_ACTIONS | `f09_poster_actions` | true | no |
| 2 | YUMIX_TAG_SECTIONS | `f16_tag_sections` | true | no |
| 3 | EPISODE_STATUS | `nf1_episode_status` | true | no |
| 4 | UPCOMING | `f08_upcoming` | true | no |
| 5 | SHORTS_PLAYER | `f12_shorts_player` | true | no |
| 6 | SHORTS_VERTICAL | `f12_vertical_shorts` | true | yes |
| 7 | SHORTS_ONE_VIEW | `f12_one_view` | true | yes |
| 8 | HISTORY_OFF_MAIN | `f12_history_off_main` | true | yes |
| 9 | VERTICAL_MODE | `f12_vertical_mode` | true | yes |
| 10 | SHORTS_AUTOHIDE | `f12_shorts_autohide` | true | yes |
| 11 | PROXY_REACH_FIRST | `nbug13_reach_first` | true | yes |
| 12 | PAGE_HINT_AUTOHIDE | `ol8_hint_autohide` | true | yes |
| 13 | STREAM_PANEL_CONTROLS | `ol2_stream_panel_controls` | true | yes |
| 14 | STREAM_CURRENT_ONLY | `ol3_stream_current_only` | true | yes |
| 15 | WEBSITE_TAB | `ol1_website_tab` | true | yes |
| 16 | STREAM_DOWNLOAD_PROMPT | `ol4_stream_download_prompt` | true | yes |
| 17 | PLAYER_TOPBAR_ALIGN | `ol7_player_topbar_align` | true | yes |
| 18 | HOME_SPACES_MORE | `ol9_home_spaces_more` | true | yes |
| 19 | NAV_MORE_DESTINATIONS | `ol10_nav_more_destinations` | true | yes |
| 20 | EPISODE_RANGE_DOWNLOAD | `ol11_episode_range_download` | true | yes |
| 21 | PLAYLIST_DOWNLOAD | `ol12_playlist_download` | true | yes |
| 22 | DOWNLOAD_TRACKS | `ol5_download_tracks` | true | yes |
| 23 | DOWNLOAD_SHEET | `ol6_download_sheet` | true | yes |
| 24 | PLAYER_TOPBAR_EDGE | `ol7_player_topbar_edge` | true | yes |
| 25 | SOCIAL_LINKS | `ol13_social_links` | true | yes |
| 26 | DUPLICATE_CAP | `pin1_duplicate_cap` | true | yes |
| 27 | PROFILE_EXTENSIONS | `f19_profile_extensions` | true | yes |
| 28 | TAB_NEXT_FRAME | `gate1_tab_next_frame` | true | yes |
| 29 | SHADER_PREWARM | `gate1_shader_prewarm` | true | yes |
| 30 | FEED_STAGED_REVEAL | `gate1_feed_staged_reveal` | true | yes |
| 31 | FEED_STAGED_LATE | `gate1_feed_staged_late` | true | yes |
| 32 | POSTER_PLAIN_IMAGE | `gate1_poster_plain_image` | true | yes |
| 33 | EXTENSION_BG_PRIORITY | `gate1_extension_bg_priority` | true | yes |
| 34 | MUSIC_PLAYER_ON_USE | `gate1_music_player_on_use` | true | yes |
| 35 | PROFILE_STATS | `f17_profile_stats` | true | no |
| 36 | GLASS_THEME | `f10_glass_theme` | true | no |
| 37 | TV4_ONLINE_SEARCH | `tv4_online_search` | true | no |
| 38 | LIVE_FAILOVER | `f14_live_failover` | true | no |
| 39 | LIVE_GUIDE | `f15_live_guide` | true | no |
| 40 | OFFLINE_MANGA | `f13_offline_manga` | true | no |
| 41 | PROFILES | `f18_profiles` | true | no |
| 42 | PRIVATE_DOWNLOADS | `f18_private_downloads` | true | no |
| 43 | TELEGRAM_PUBLIC | `telegram_public` | true | no |
| 44 | TELEGRAM_LOGIN | `f02_telegram_login` | true (OWN-3, was false) | yes |
| 45 | BUILTIN_TORRENT | `b31_builtin_torrent` | false | yes |
| 46 | CLOUD_CAPTURE_V2 | `cloud_capture_v2` | true | no |
| 47 | MANGA_TRANSLATE | `f11_manga_translate` | true | no |
| 48 | APP_PROXY | `nbug13_proxy` | true | no |
| 49 | DNS_FALLBACK | `nbug6_dns_fallback` | true | no |
| 50 | CLOUDFLARE_DEFAULT_DNS | `own7_cloudflare_default` | true | yes |
| 51 | EXTENSION_LABELS | `f21_extension_labels` | true | yes |
| 52 | UPCOMING_SECTIONS | `f22_upcoming_sections` | true | yes |
| 53 | E4_SPEED_ORDER | `e4_speed_order` | true | yes |
| 54 | FE_PRIORITY_GATE | `fe2_priority_gate` | true | yes |
| 55 | B31_STREAM_WINDOW | `b31_stream_window` | true | yes |
| 56 | B31_TARGET_FIRST | `b31_target_first` | true | yes |
| 57 | FE3_FIRST_ROW | `fe3_first_row` | true | yes |
| 58 | FE4_FEED_FRAMES | `fe4_feed_frames` | true | yes |
| 59 | FE4_WORKING_SET | `fe4_working_set` | true | yes |
| 60 | E4_THROTTLE | `e4_throttle` | true | yes |
| 61 | FE_S3_FAIL_MEMORY | `fe_s3_fail_memory` | true | yes |
| 62 | FE_S4_ADAPTIVE_TIMEOUT | `fe_s4_adaptive_timeout` | true | yes |
| 63 | FE_PAUSE_WHILE_PLAYING | `fe_pause_while_playing` | true | yes |
| 64 | FE_CALL_LANES | `fe2c_call_lanes` | true | yes |
| 65 | FE5_FEED_WARM | `fe5_feed_warm` | true | yes |
| 66 | SCRIPTS_VEGA | `scripts_vega` | true | no |
| 67 | SCRIPTS_NUVIO | `scripts_nuvio` | true | yes |
| 68 | STREAM_ADDONS | `f05_stream_addons` | true | yes |
| 69 | F05_STREMIO_STREAMS | `f05_stremio_streams` | true | yes |
| 70 | F22_COUNTRY_GENRE | `f22_country_genre` | true | yes |
| 71 | F09_FEED_ACTIONS | `f09_feed_actions` | true | yes |
| 72 | DL_ONE_SHEET | `dl_one_sheet` | true | yes |
| 73 | TAB_JUMP_HOLD | `tab_jump_hold` | true | yes |
| 74 | WEB_DOWNLOAD_LISTENER | `web_download_listener` | true | yes |
| 75 | VERTICAL_BACK_LEAVES | `vertical_back_leaves` | true | yes |
| 76 | SCRIPTS_SKYSTREAM | `scripts_skystream` | true | yes |
| 77 | SCRIPT_ERROR_MESSAGE | `f05_script_error_message` | true | yes |
| 78 | EXT_HOLD_WHILE_TYPING | `ext_hold_while_typing` | true | yes |
| 79 | OFFICIAL_CLASSICS | `own9_official_classics` | true | yes (owner list 2026-11-22) |
| 80 | YT_AUDIO_CHOICE | `own11_yt_audio_choice` | true | yes (owner list 2026-11-22) |
| 81 | PIN_GUARD | `own12_pin_guard` | true | yes (owner list 2026-11-22) |
| 82 | FAST_TITLE_OPEN | `own6_fast_title_open` | true | yes (owner list 2026-11-22) |
| 83 | NAV_GUARD | `own1_nav_guard` | true | yes (owner list 2026-11-22) |
| 84 | BATCH_SUB_LANGUAGE | `own8_batch_sub_language` | true | yes (owner list 2026-11-22) |
| 85 | YUMEIRO_HUB | `own13_yumeiro_hub` | true | yes (owner list 2026-11-22) |
| 86 | PLAYER_ESSENTIALS | `own19_player_essentials` | true | yes (owner list 2026-11-22, OWN-19) |

</details>

## 7. Security (W4)

| Check | Result | Evidence |
|---|---|---|
| gitleaks on the pre-audit head | Pass | CI 45024851633 job "Secret scan (gitleaks)" success |
| gitleaks on the final head | open (final CI) | |
| Manifest changes 2.0.0 -> 2.1.0 | Only 5 `<queries>` package names for external players (F-07-VIS). No new permission, no new exported component, `allowBackup=false` unchanged | `git diff e47f450f HEAD -- app/src/main/AndroidManifest.xml` |
| Network security config | Unchanged since 2.0.0: system CAs + ISRG Root X1 only (no user CAs); cleartext off for account / token / update hosts; cleartext allowed for user stream URLs (IPTV needs it, documented) | res/xml/network_security_config.xml |
| New code: trust-all TLS, hostname verifier, world-readable files, file access in WebView, new JS interfaces | None found in the 2.1.0 diff | `git diff e47f450f HEAD -- app/src/main` + rg for those APIs |
| Telegram api_id / api_hash | Read from secrets at build time into BuildConfig (TG_API_ID / TG_API_HASH), never in the repo. Note: a Telegram client's api_hash always ships inside the app (Telegram's own clients do the same); it identifies the app, it is not a server key. Feature is off in 2.1.0 | app/build.gradle.kts optionalKey(...) |
| Telegram session key | Stored sealed with Android Keystore (SecretBox.seal / open) | telegram code, SecretBox |
| APK secret search (review APK) | open (after the final CI's review APK) | |
| SEC-1 (Last.fm shared secret), UPG-1 (rotation) | Owner's items, not checked by the auditor (owner rule) | |

MASVS short list (L1): STORAGE (no backup, Keystore-sealed tokens) OK by code reading; CRYPTO (no custom crypto in the
diff except Keystore AES-GCM wrapper) OK by code reading; NETWORK (see above) OK; PLATFORM (no new exported
components / JS bridges) OK; CODE (R8 on release, no debuggable flag in release) Not checked on the release APK yet;
RESILIENCE: out of scope (open-source app).

## 8. Upgrade (W5)

| From | Phone | TV | Run |
|---|---|---|---|
| v2.0.0 | open | open | upgrade-check.yml with the final review APK |
| v1.9.0 | open | open | |
| v1.8.x | open | open | |

Code reading for the upgrade path of this audit's changes: OWN-7 changes the DNS default only when `dns_provider` is
missing in prefs (NetworkSettings.init); a saved choice from 2.0.0 is kept (Own7CloudflareDefaultTest
aSavedChoiceIsKept). version 2.1.0 / 19 > 2.0.0 / 18, same signing certificate check in release.yml.

## 9. Release checklist (W8): prepared, NOT executed

Nothing below is done until the owner says "go".

1. Version: done on the branch in f90b4999 (VERSION_NAME 2.1.0, VERSION_CODE 19). release.yml runs
   compute-version.sh: 2.1.0 is above the v2.0.0 tag, so it is used as is; code = max(19, 18 + 1) = 19; `changed=false`,
   so there is **no second bump commit** (double bump avoided).
2. What's new note: shouldPost now true for 2.1.x after an update (same note ID, nobody sees it twice);
   SettingsDiscoverabilityTest updated in the same commit.
3. PR r15-stability -> main: text prepared in docs/evidence/final/PR-2.1.0.md. main is an ancestor of r15-stability
   (0 commits behind), so the merge has no conflicts. main is not branch-protected.
4. Merge: one merge commit (not squash, keeps the per-commit history the audit checked). One push to main.
5. That push starts: release.yml (signed APKs, tag v2.1.0, GitHub release, SHA256SUMS checked in the job) and
   stream-sync.yml (2 changed files under its paths: .github/streams/curated.json, the workflow; it refreshes the public Live TV data branch as its 3-day schedule
   does; curated lists stay off because YUMEIRO_CURATED_LIVE is not set). No other workflow has changed paths.
6. After the release job: download the release APK, compare its sha256 with SHA256SUMS.txt and with the signing
   certificate in .github/release-signing-cert.sha256 (the job checks it too).
7. Website (siamform/yumeiro-web PR #5) and the Cloudflare Worker (server/, manual `wrangler deploy`): separate
   owner steps; nothing is deployed by the merge. Worker: `git diff origin/main r15-stability -- server/` is empty, so 2.1.0
   needs no Worker deploy.
8. Rollback: if 2.1.0 is bad, do not delete the tag; publish 2.1.1 from main with the fix (VERSION_CODE 20), because
   phones that took 19 cannot go back to 18. The in-app updater reads the latest release, so a new release replaces it.
   Mark the 2.1.0 release as pre-release on GitHub to stop new downloads while 2.1.1 is built.
9. After "go" and the release: delete leftover branches ab/*-before (AUD-AB-1), close the audit.

## 10. Not checked, 11. GO / NO-GO

open
