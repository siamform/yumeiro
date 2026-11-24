# Live handover (updated with every push; read this first after AGENT-START-HERE.md)

Owner rule 2026-11-21 16:30 +06: agents can run out of limit at any time, so this file is rewritten with every push:
what was just done, what runs are pending, and the exact next step. Every bug and feature on the lists must be done
AND verified. Mandatory focus now: feed loading, extension loading, load speed, search speed.
Owner 2026-11-22: every doc by past agents must be read. Done: docs/DOCS-READ-DIGEST.md (read it; D-1..D-13).

## Last updated
2026-11-24 R22 (Asia/Dhaka, evening). Branch **r21-major-list** pushed; **no PR yet** (owner: none until item 22 is
verified, and the owner approves). main = v2.2.0. Per-item status: **docs/OWNER-LIST-R21.md** (items 1-28; specs of
25-28 also in the private repo siamform/yumeiro-docs, list/ITEMS-25-28-OWNER-SPECS.md). Ledger rows "(R21)"/"(R22)"
in docs/BUGS-AND-FIXES-2.0.0.md.

### Now (read first)
- TB-LOGIN (c28878a7, item 19a): sealed probe 45417577195 showed the TeraBox sign-in WORKS in the page browser (ndus
  set on .terabox.app, page dm.terabox.app/wap) but Yumeiro then sent the user back to the sign-in page. Cause, measured
  with the same account and the Android web view UA in headless Chromium: www.terabox.app share/streaming gives the
  30 s preview even when signed in; dm.terabox.app gives the full 10532 s HLS. Fix: TeraBoxApi.SIGNED_API asked first;
  signed-in users are never looped back to sign-in (SIGNED_PREVIEW_REASON). Sealed re-probe 45423589183 (fed by
  /tmp/tbfeed.sh: watches the field-probe status and sends secrets e/w). NEXT: decrypt, read logcat YuTeraBox + shots.
- ADCHAIN-2 test fix: Adchain2OtherFileTest needs Robolectric (CI 45416827172 failed 2 tests). CI 45423820417.
- Open probes: 45421421959 (anime search → Gundam SEED DESTINY → Play), 45422681170 (install MovieBox, Pin, relaunch).
- Remaining: 8A-D Telegram sealed probe (owner sends the code), 19b Diskwala (@sky577bot Mini App), 10 pins,
  15 device route, 21 "Update completed!", 13 label re-verify, 23 srnky last step needs a human tick (Turnstile is
  never solved by Yumeiro), then 25-28, then final deliverables (web changelog, 15+ mockups zip, README, PR, release).
- Probe dumps of sealed runs contain the typed password: always redact before printing; never commit them.

### Latest changes (this session)
- 2026-11-24 R22 SEARCH-ML-1 (owner item 24): search history keeps a multiline search as one entry with its breaks
  (SearchHistoryCodec, JSON array; old "\n" lists still load), rows grow to fit. Test MultilineSearchHistoryTest.
  NEXT: CI, then field probe that pastes a multiline query (adb) and shows the history list.
- 2026-11-24 R22 ADCHAIN-1 (owner item 23, ADR-060): link-chain helper in the page finder (assets/js/link_chain.js,
  online/web/LinkChain.kt, ui/screens/stream/LinkChainBanner.kt). Real Chrome walk: both example links reach
  shortsync.epdexx.com (which answers "Broken Link" itself). Fixtures test/resources/pages/chain-*.html (REDACTED
  tokens); CI step runs scripts/adchain1_link_chain_check.js. NEXT: CI, then field probe `send https://srnky.com/0T1EnbeuLf`
  with long waits + shots; the Turnstile tick needs `tap Verify you are human` or `xy`.
- 2026-11-24 R22 VERIFY-1 probe 45373197518 (AnimePahe installed): no "Quick security check" in any UI dump; logcat
  "refused the request with a firewall block (not a captcha) — not opening the check page"; Security checks row
  "The site's firewall blocks this network…". CI 45378789204 on 89b24aca: unit tests passed. Duplicate label fixed.
- 2026-11-24 R22 VERIFY-1 (owner item 22, ADR-059): VerificationCenter (one saved state per site) + ExtensionCheckWindows
  (an extension's own check window is concealed, its page read: firewall block → closed + one note + 30 min hold;
  person needed → shown once; closed by the user → paused 10 min..1 day) + Extensions → Security checks card.
  Root cause from the decompiled AnimePahe.cs3 (see ADR-059). Tests: VerificationPolicyTest, ExtensionCheckWindowsTest,
  scripts/verify1_kind_js_check.js (needs `npm i jsdom@24`). NEXT: CI on the VERIFY-1 commit, then field probe with
  AnimePahe installed (recipe of 45345639530) — expect no "Quick security check" in UI dumps and a Security checks row
  "animepahe.pw · blocked". Then item 23 ADCHAIN-1. No PR before 22 is verified (owner rule).
- 2026-11-24 R22 UPD-MSG-1 (e7947675, owner item 21): updater messages; CI pending.
- TB-PREVIEW-2 (6e010fce, 488889a3): TeraBox ties a sign-in to the site it was made on. Measured in a real browser
  with the owner's test account (credentials only in the ignored secrets/yumeiro.env.local; never in probe steps):
  signed in on terabox.app, `share/streaming` M3U8_AUTO_480 = full HLS, 1057 segments, 10532 s; anonymous = 30 s
  preview, never played. In-app emulator playback with a signed-in account is NOT shown yet.
- TL-3 (3a7e532a): seam band for manga translation. Device-check 45256209381: MangaTranslateDeviceTest 5/5 PASS.
- SD-2 (70e2535f) + ERR-CARD-1 (0098825a): item 6 emulator verified end to end (probe 45265113188): shared movielinkbd
  page → "Find the video" → "HLS stream · master" detected → plays 1080p 2:21:10 in Yumeiro's player.
- WEB-RECENT-2 (1a3803ff): Recently visited also matches a typed address (unit tested only).
- TOR-2 (29b7d31b) emulator verified (probe 45260133429); TOR-3 (4224947b) right Settings page in torrent messages.
- PC-1 emulator verified (probe 45268021731): "Rewind and forward 10 s" off → only Pause in the player middle.
- PROBE-XY (14a58c08): field probe step `xy X Y` taps a screen point (the video surface has no text).
- Built-in torrent engine device test 45268091524: first frame OK (Sintel 6.4 s / 5.6 s), seek FAILS (13.6 s on api36,
  no resume on api33-lowram; budget 5 s). Flag b31_builtin_torrent stays off. Ledger B31-FRAME-RUN.
- Torrent seek work (2026-11-24), each with its own ledger row: B31-SEEK6 (5fdf3418, deadlines only for the next
  4-16 pieces, sliding; held window opens on any block), TOR-CLOSE-1 (9f5f725e, closing a source ends waiting reads),
  B31-PEERS-1 (5ec4a932, close_redundant_connections off: seeds were dropped whenever a window finished),
  B31-STALL-T (ada77601, trace says the needed block's state), B31-STALL-1 (640d0fd2, 3 s timeout caps) REVERTED
  with evidence. Device runs 45303433155, 45305007621, 45306375960, 45308014362, 45309673778. Result on head: low-RAM
  seek works now (was: never); first frame mostly 3-8 s; seek 1.6-5 s in most runs, but single blocks still stall
  5-10 s ("requested x1 0B"), which libtorrent re-asks only after its average piece time. Flag stays off.
  B31-RESCUE-1 (d1f42262): a read stuck 1.2 s leaves only its piece wanted, so idle peers ask for the stuck block
  too (libtorrent end-game rule; idea studied from librqbit piece stealing, no code taken). Run 45316393663: first
  frame 2.6-8.1 s on all six, seek 2.5 / 2.9 / 3.1 / 4.1 / 4.7 s and 7.5 s (lowram shard 1), all resumed. Best so far.
  TOR-IDLE-1 (af30094f): idle pause keeps the source and the next read resumes; unit tested, device check pending.

### Pending runs (check each one first)
- None open. Last green CI: 45263833897 on 1a3803ff. Commits after it (TOR-3, PROBE-XY, docs) need one more CI:
  `gh workflow run ci.yml --ref r21-major-list -R siamform/yumeiro`.
- Evidence index: 45247707490, 45252551715, 45253665347, 45257033081, 45257174845, 45260133429, 45261500409,
  45265113188, 45268021731 (field probes); 45256209381, 45268091524 (device checks).
  Download: `gh run download <id> -R siamform/yumeiro -n field-probe -D /tmp/obX`; read timeline.txt + shots.

### Next steps, in order
1. Fresh CI on the head; fix anything red.
2. Torrent: B31-SEEK7 (2e44c086) 11 of 11 Sintel seeks within 5 s and first frames within 15 s on api36 and
   api33-lowram (runs 45321992083, 45323267366); TOR-IDLE-1 emulator verified (45325875819); B31-ON-1 (31c7ca17)
   flag on by default (still opt-in through the Settings notice). Pending: full in-app path probe 45328880256
   (share Sintel magnet, turn on, play). The owner hash can't be judged on the emulator (10-bit HEVC).
3. Owner queue rule (2026-11-24): finish all of items 1-20 first, then item 22 (VERIFY-1, extension security
   checks / CAPTCHA manager), then item 23 (ADCHAIN-1, ad chains and link shorteners). Plans in
   docs/OWNER-LIST-R21.md. Do not start 22 or 23 before 1-20 are closed.
4. Open PR r21-major-list → main only with owner approval (footer required); bump version + CHANGELOG.
5. Open items: 19b DiskWala (blocked, owner decision), Skip intro emulator check (needs an anime extension), 20 legal
   free-anime part, TeraBox in-app signed-in emulator proof, items still "CI pending" in OWNER-LIST-R21.md.

### How this agent works (copy this method)
- One owner item = one commit with its own ID (for example META-1). Order: read the code and reproduce (field
  probe or real HTTP with curl), find the root cause, make the smallest real fix, write a JVM / Robolectric test on
  real saved data (never simulated), add a ledger row after the last R21 row, commit with the Cause / Fix / Tests /
  Evidence / Timestamp / Ledger body and the Co-authored-by trailer, push, and update this file.
- The git identity must be exported before every commit (docs/AGENT-START-HERE.md has the exact export line).
- Regex rule: hoist each Regex to a val and add the class to text/RegexWarmup.kt CLASSES (RegexGuardTest fails
  otherwise). File-level vals in Foo.kt are class "FooKt".
- No main-thread I/O: read SharedPreferences and the disk on Dispatchers.IO (produceState + withContext).
- Network calls on the phone use the shared OkHttp client and DataCache (namespace, TTL) for anything cacheable.
- Secrets: secrets/yumeiro.env.local (SIMKL_CLIENT_ID etc.), secrets/vercel.env, secrets/firebase-admin.json.
  Load them with `set -a; . secrets/yumeiro.env.local; set +a` and never print them. Check that fixtures don't
  contain them (`grep -c "$SIMKL_CLIENT_ID" fixtures`).
- There is no local JDK, so read the code twice before pushing: check the imports, that names exist (rg), and the
  Compose scopes. CI is the compiler.
- Labels in every report: CI verified / emulator verified / real-device verified / implemented but untested /
  blocked. Never "done" without evidence.

## Earlier (R20 and before)
### Last updated (R20)
2026-11-23 20:48 +06 (R20, owner approved the merge). **v2.2.0 is released**: https://github.com/siamform/yumeiro/releases/tag/v2.2.0
(Release run 45189225491). Ledger rows "(R20)" after TG-PAGE-2 in docs/BUGS-AND-FIXES-2.0.0.md.
- PR #42 (r19-restore -> main) merged as 7ed6663f. CodeQL on main: 0 open alerts (was 22) = CQ-1 closed on main.
- First release run 45179237099 failed before any test ran (Robolectric jar download, MavenArtifactFetcher); nothing
  was tagged. REL-2.2-CI: PR #43 adds the Robolectric jar cache to release.yml and one retry only for that download
  failure. The re-run needed that retry and published 2.2.0.
- WEB-DL-1: yumeiro-web PR #7 merged, production deployment ba59f128 READY. Live check www.yumeiro.click/download
  (Android, no processor hint, 360 px): "Download · 126.8 MB", button 224 px, page 360 px wide, no sideways scroll.
  The site's release data refreshes every 600 s (ISR), so it switches from 2.1.0 to 2.2.0 files on its own.
- Branch cleanup: 53 remote branches deleted (probe/* field-probe triggers, ab/nav1-before, r18-live-ext, r19-restore,
  r20-release-robolectric-cache; their runs and logs stay in Actions). Kept: main and the data branches the app and
  workflows read (extensions, feed, market, streams, tdlib-bin, crash-log). yumeiro-web: only main.

## Pending runs
- None.

## Next step
1. Website copy still highlights 2.1.0 (yumeiro-web lib/changelog.ts, app/changelog/page.tsx description,
   site.fallbackRelease v2.0.0): update to 2.2.0 on owner request.
2. UI/UX audit continues (Search, Yumix source menu not covered yet).
3. Blocked: Telegram signed-in search (no account), every real-device check (no device), real slow mobile network.

## Next step (older)
1. Live TV (E2-ALL): Yumeiro Live is published on the streams branch (playlists/yumeiro_live.m3u, 1.7 MB, sync 45071581763).
   Device-check Live TV on both phones; curated per-country lists still off.
2. Extensions: ExtensionsDeviceTest e13 "five feeds failed" on device; OWN-4 / OWN-5 device checks.

## Earlier state (before the audit; history, superseded where the audit says so)
## Last updated (pre-audit, 14:12, stale)
2026-11-22 14:12 +0600, branch `r15-stability`. Owner order 2026-11-22 ~14:00: share the preview APK (owner has one
hour to test), then on his approval: PR r15-stability -> main, merge, release 2.1.0. Same download popup everywhere
(DL-ONE). Yumeiro's own all-in-one extension (E1 Yumeiro Hub) and all-in-one Live TV (E2-ALL Yumeiro Live, on top of the built E2): docs now,
build after 2.1.0 (target 2.2.0), plan docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md.
This push: DL-ONE (YouTube used its own quality list first; now the shared DownloadPrompt popup, YtOneSheet, flag
dl_one_sheet, test YtOneSheetTest, OMT-80). Finding DL-WEB: no WebView download listener (plain file links in
in-app web pages are not caught), not fixed in 2.1.0. F-09-FEED pass-after Pass 44997677870.

## Pending runs (check each, then update the ledger rows that say PENDING)
- NONE pending on the agent side (17:2x). All passed: CI 45008845576 @2d79e048 (review APK, DEX budget OK), stress 45006967759 10/10,
  tv-api24 45006388286, tv-api34 45008159007. Review APKs shared: preview/yumeiro-2.1.0-review-2d79e04-{phone,tv}.apk.
  Next: the owner's test, then on his explicit go only: PR r15-stability -> main, merge, release 2.1.0 (bump version 2.0.0/18 first).
- r15 CI pass-after 45008845576 (review_apk=true) covers TV-FP-1 + DL-WEB + ST-17-BACK. Device: tv-api34 FeedPosition 45008159007,
  stress lowram repeat 10 45006967759 (@a8f1ef0f, has the ST-17-BACK fix). Fail-befores done: 45006341391, 45006640065, 45006886661 (all valid, branches deleted).
- TV-FP-1 fix (tab_jump_hold, TabJump): fail-before ab/tvfp1-before 45006341391 (valid = only TabJumpTest fails; worktree /tmp/abtj,
  delete after); device tv-api24 FeedPositionDeviceTest on r15 (pending dispatch); r15 CI pass-after after that.
- DL-WEB fix (web_download_listener, WebDownloads, WebDownloadsTest 6): fail-before ab/dlweb-before 45006640065 (valid = only
  WebDownloadsTest fails, all 6; worktree /tmp/abdw, delete after). OMT-81. Add DL-WEB to CHANGELOG.md only after the pass-after.
- Website PR #5 (r16-v210) a7c193a: changelog text matches 2.1.0 (one downloader, Stremio / SkyStream, Upcoming by country). Kit zip rebuilt 15:13.
- ST-17 stress 44998769417: 0 spins, 9/10 Pass, run 7 Fail -> ST-17-BACK fixed (vertical_back_leaves, VerticalBackTest 5); fail-before
  ab/st17back-before 45006886661 (valid = 4 of 5 fail; worktree /tmp/absb). Then rerun the stress: device-check StabilityStressDeviceTest
  -f phone=api33-lowram -f repeat=10 on r15. r15 CI 45006709166 cancelled (logged); new r15 CI covers TV-FP-1 + DL-WEB + ST-17-BACK.
- Queue order now (owner rule 7): TV-FP-1, ST-17 (44998769417 running), B31-FRAME (Blocked emulator), then website (B),
  README mockups + kit zip (C), then DL-WEB, then E2 first publish (owner go), E2-ALL, E1 (2.2.0).
- r15 CI 44997677870 @bdcbd8e1: Build, test & lint Pass; Review APK FAILED the dex register budget (225 > 224) = DEX-REG-1, fixed in the next commit; new r15 CI review_apk=true = pass-after for DEX-REG-1 + DL-ONE and the preview APK.
  That run is 45002048828 @657cc01f: SUCCESS (DL-ONE + DEX-REG-1 pass-after). Preview APK shared to the owner:
  yumeiro-2.0.0-657cc01-review.apk sha256 b1f25b71a3564bca927162ebb828d157d30fd2119ae28e75c84cafd72f227933;
  TV yumeiro-tv-2.0.0-657cc01-review.apk sha256 74549d95c034ee6d2771ead294c1189405d1c9b9b4ff0865f6ba7c3a17191eea.
  Next: owner soft test, then on his go: PR r15-stability -> main, merge, release 2.1.0.
- Fail-before DL-ONE: ab/dlone-before 45001258208 VALID (only the 5 YtOneSheetTest cases failed); branch + worktree deleted.
- ST-17 stress 44998769417 (StabilityStressDeviceTest repeat 10, lowram @baa8c855): record.
- Open: TV-FP-1 Fail (OMT-78), B31-FRAME Blocked emulator (OMT-79), owner items SEC-1, UPG-1.

## Next step
1. Record the runs above (ledger F-05-S-ON, F-05-STREMIO, F-22-C, F-21, F-09-FEED; MAJOR C5 table "re-check" cells).
2. F-09-FEED device check (PosterActionsDeviceTest on feed cards) after phones slot frees.
3. Open rows: GATE-1-VIDEOS-COMPOSE, B31-FRAME, TV-FP-1 test rewrite, ST-17. E1 only on the owner's go.
4. LAST: ci.yml review_apk=true on r15 (only when no r15 CI runs), download `yumeiro-review-apk`, SHA256, share
   the APK; rebuild the kit zip / PDF; re-share both living docs; short final reply.

## Done this session (2026-11-21 evening / 2026-11-22), newest first
- 2026-11-22 12:56: F-09-FEED long press on Yumix / Top 10 / Continue watching (this push, CI pending).
- 2026-11-22 12:49: F-05-S-ON, F-21 device test, F-05-STREMIO, F-22-C, C5 / C6 mapping (this push).
- 2026-11-22 11:10: F-05-S SkyStream adapter (this push, flag off, CI + device pending).
- 2026-11-22 11:00: GATE-1-PREPARE-COLD harness e109bece.
- 2026-11-22 10:55: F-05-N-ON (this push); GATE-1-QUIET closed (664 / 559 ms Pass).
- 2026-11-22 10:30: GATE-1-QUIET cd0b256a.
- 2026-11-22 10:05: F-05-N-ERR 2ab4f33f.
- 2026-11-22 09:15: GATE-1-MUSIC-START-2 f46c3f22.
- 2026-11-22 09:00: F-05-N Nuvio 06200fc3, docs digest 45aac76a.
- 2026-11-22 morning: RENDER-5b 0270f069, FF-LOAD 8cb89e9b, GATE-1-AB b847caa0, GATE-1-FTL 76a6e521, MUSIC-START 9da67d0c.
- GATE-1 (newest first): RENDER-4b f30cd6c5 poster phase fix; TRACE-4 1ee95cc3; T11 345d2572; RENDER-5 935875ed;
  RENDER-4 posters; TRACE-3/3b; T10 a4cc1379; RENDER-3 4b303e0d; RENDER-2 62102ae5; RENDER a8f33c0e. Gate runs
  44951712024, 44953724011, 44955115811, 44956362034, 44957621376 (swiftshader), 44958850831 (swangle): all Fail.
- GATE-1-RENDER (17a02248) debug-only Compose composition tracing; GATE-1-TRACE-2 (8c8eec69) atrace-only gate trace
  (52 s, whole walk); CI-MAVEN-404 (865bce1e) review APK retries once on a Maven Central miss; GATE-1-ANR-T8
  (768a5698) the harness fails a class on an input ANR under instrumentation (AMS ends the run, no exit record).
- GATE-1-ANR T2..T7 walk fixes (popup window, pill label, landed checks, live accessibility tree): walk completes.
- PIN-1-DUP, F-19-EXT, F-19-EXT-2, CI-DNS-RACE-3, OL-6-T: pass-after CI 44944975133 (unit 1753 / 0 failed).
- FREEZE-4 guard test 7e31dcfe Pass 44937485728; AIR-T1 1e358ea8 Pass 44942929078; OL-13 device Pass.
- OL-13 (1ce6d8ca): About › Community shows Discord, Facebook, Instagram, X, TikTok, Reddit (@officialyumeiro) as an
  icon row; About › Developer adds Facebook siam1p, Instagram asfakul_siam, Telegram @siamorwho. Simple Icons (CC0)
  marks; README Community section. Flag ol13_social_links. Manual: OMT-71.
- OL-6-REVEAL (27bdae38, not enough) + OL-6-REVEAL-2 (ddfb93a9): "Where it goes and tips" scrolls into view when
  opened. OL-5-T2 (b2e0e3a3): subtitle test scrolls first. Both from r15 CI 44929819988.
- CI-DNS-RACE-2 (c1fb5652): a NetworkSettings.init from before a test reset no longer writes after it; fail-before
  44930913875 valid, pass-after 44932398355. CI-DNS-RACE flake closed.
- E2-DRY (0f5f9fd1): stream-sync dry_run input; run 44932539992 Pass (23 countries, nothing published).
- E3 fail-before 44879928117 confirmed valid. F-02 login lowram device Pass 44933818039.
- Device fail-befores valid: OL-4 44931006317, OL-9 44931008815, OL-10 44931866056, OL-6 44932939164, OL-5-HEIGHT
  44933186744 (redone on current code; 44932248504 on the old branch failed for another reason). All ab/ol* branches
  deleted except ab/ol13-before.
- Device Pass @71efd02d: phones 44929260054, TV 44929262746 (PopupQuality, F02Split, F18MangaChapters, LiveCountry,
  StreamWatchDownload) -> NF-2b, F-02 split, F-18, Country filter device Verified.
- r15 CI 44929819988 @197d8df1: 1712 tests, 2 fails (OL-6-REVEAL, OL-5-T2) -> OL-5, OL-7-EDGE, OL-11, OL-12 Verified.

## Owner rule 2026-11-22 (session 3)
Owner added OL-13 (social links) 2026-11-22 and repeated: partial or fake success is not acceptable.

## Owner rule 2026-11-21 (session 3)
"Continue, fix and feature all; partial or fake success is not acceptable; if a bug or feature needs a manual test,
list it, I will do it later." Manual tests go to docs/OWNER-MANUAL-TESTS.md (OMT-64..OMT-71 for OL items, F-12).
Secrets: never in code; only the gitignored secrets/ folder. SEC-1 (Last.fm shared secret) and UPG-1 rotation are the
owner's, he said not to worry about them.

## Owner top priority 2026-11-21 19:00 +06 (read docs/FEED-ENGINE-2.1.md)
Feed engine: Yumeiro loads all extensions like YouTube channels; must handle 1000+; priority (playing > picked >
on screen > background); reserve cache; instant open; feed ~10 min after install/update must be fixed; check whether
extensions reinstall after an update; YouTube and online music slow too. Plan FE-1..FE-7 in that doc.
Owner rule 19:15: after every bug/feature, mark its status in the docs, verified (run ID) or not.

## Owner rule 2026-11-21 19:01 +06
Partial is not acceptable: every bug and feature must be done AND verified (run IDs). Write the docs every push so the next agent resumes exactly. E1 master extension: research and design in docs/E1-MASTER-EXTENSION.md (owner brief quoted there).

## Owner rule 2026-11-21 17:15 +06
No hurry, nothing unverified. Before each fix: research how competitors handle the case (cite URLs in the ledger),
then build Yumeiro's own better version. All bugs mandatory; features one by one. 2.1.0 must be the most stable build.

## Owner rule 2026-11-21 19:52 +06
Commits: owner is author and committer, one `Co-authored-by: Perplexity` trailer (re-approved). Credentials: never in
code, commits or docs; only in the gitignored `secrets/` folder on the working machine.

## Done today (2026-11-21), newest first
- OL-5 (91667cdd): download popup chips for real HLS heights, audio track and subtitles (website HLS master read off
  the main thread, 6 s call timeout); episodes: "Save all the source has" + Sub/Dub chips in the sheet; picked audio
  joined; subtitles saved as "<video>.<label>.vtt|srt" (SubtitleSaver) and loaded by the player from Downloads.
  Flag ol5_download_tracks. Fail-before CI 44927927381 (ab/ol5-before, empty stubs) running.
- OL-12 (5357dce2) Download playlist on Online Music; OL-11 (fc474be6) This episode only + Episode range. Fail-befores
  44927039015 / 44926724501 running.
- OL-8 02dbbfbb, OL-2 1346cedb, OL-3 4fe266a4 + 2d27529a, OL-1 ae938183 + 95108c0c (build fix), OL-4 f08c8c38,
  OL-7 acf7f590, OL-9 123de693, OL-10 91ee246c. Valid fail-befores: OL-8 44922989207, OL-2 44923147788, OL-3
  44923308227, OL-1 44923702849 + device 44926511600 ("no kept WebView"). Device api36 Pass 44926513936 @95108c0c
  (WebsiteTab, StreamWatchDownload with the popup, MoreDestinations OL-9 / OL-10); lowram job of that run failed
  before tests (emulator package zip corrupt on the runner, infra) and was re-dispatched.
- F-12-FLAG (7e93879f): f12_vertical_shorts on. Fail-before 44922624531 valid, CI 44922673093, phones 44922675456,
  TV 44922677658 all Pass: F-12 Verified. NBUG-13-PORT Pass (44917972192 valid, 44918012619 green).
- NBUG-13-PORT (8a3e74a4): CI 44914717596 failed AppProxyTest (the random free port held the digits 407, and the proxy
  error text checked "407" before the error type, so an unreachable proxy read as "wants a login"). Fixed, flag
  nbug13_reach_first, new fixed-port test. Fail-before CI 44917972192 and pass-after CI 44918012619 running.
- F12-MODE + F12-CHROME device Pass: phones 44916676685 (vertical on, queue swipe, Back to the normal player with the
  same clip playing; chrome hides at 3.5 s, tap shows, tap pauses), TV 44916693769 (never enters / skips). Fail-before
  device 44913945589 valid (branch ab/f12mode-before deleted). First pass-after 44913948025 failed on the tests, fixed:
  F12-TEST-CLING (bf534934, Android's one-time full-screen notice took Back) and F12-TEST-CLOCK (c3ec754d, the hide
  timer follows the compose test clock). Diagnosis run 44915399473 on diag/f12back (deleted).
- F12-MODE (a2222bf5): vertical mode in the normal player for 9:16 non-YouTube videos (ADR-056): full screen portrait,
  centre swipe up / down through the queue, Back to the normal layout. F12-CHROME (215fb53d): Shorts top bar + title
  hide after 3.5 s of play, a tap brings them back. F12-LINT (7eb17931): Media3 opt-in on ShortsWindow.resizeFor (lint
  errors in CI 44911548689 and 44912685131). New asset yumeiro_portrait_clip.mp4 (180x320). Built, not tested.
- F12-HIST + F12-VIEW Pass: unit CI 44912685131 @8c304d4f (failed only on lint), device phones 44912687672 (swipe +
  ReleaseFeatures Pass, api36 inside budget now), TV 44912689981 (swipe skips by design). Fail-befores valid
  44911548689 (unit) and 44911557702 (device, views 14 vs 1); branch ab/f12view-before deleted.
- F12-HIST (3d4d780c): YouTube history written on one IO lane (was main thread on every Shorts settle / Music tap).
  F12-VIEW (93c73933): one PlayerView for the whole Shorts pager. Both from pass-after 44910278641 (api36 Fail
  1384 / 1372 ms > 1200; lowram Pass). F-12 fail-befores valid: 44910420105 (unit), 44910276470 (device). Built, not tested.
- F-12 vertical Shorts: BUILT, NOT TESTED (0288e8d test, f8acd0c feat; flag f12_vertical_shorts default off). Swipe on
  one shared player + YouTube-marked Shorts up to 3 min open the Shorts player. Not built yet: 9:16 auto-open for
  non-YouTube content, swipe-up preload, idle-hide controls, Back to the normal player.
- F-10 Verified phones + TV (new F10BlueThemeDeviceTest; fail-before 44908264228, pass 44909154883 / 44909158248). F-07 TV Pass 44908157533. NBUG-10 TV: test skips by design (FINAL-39).
- TV Pass 44906652590 (F-04 / F-09 Menu key / F-13 / F-14 / F-15 / F-17 / NF-1 now Verified phones + TV). Phones re-check Pass 44906655104.
- Device Pass on this head (phones): F-07 ExternalPlayerDeviceTest 44904481596 (MX + chooser, Watched(66000, 60000)); ReleaseFeatures / PosterActions / FinalCoverage / MangaOffline 44904447255 (F-04, F-09 phone part, F-13, F-14, F-15, F-17; F-10 glass part; F-12 retry part). CI 44903723798 @b8381ce0 Pass (357 / 1675 / 0). E1 research + design written.
- NF-1 device Pass on all 3 targets (44903495726 / 44903498117). r15 CI 44901991356 @6196d665 Pass (357 classes, 1675 cases, 0 failures): pass-after NBUG10-IME, third green for CI-DNS-RACE. F-07 lowram MX round trip Pass; F07-T4 test fix (media permission dialog).
- NBUG-10 device Pass 44901993315 (both phones, with the NBUG10-IME fix). Fail-befores valid: NBUG10-IME 44901948952, NF-2b 44901952882, F-02 split 44901941945, Country 44901945428, F-18 44899238590 (branches deleted). NF1-T3 and F07-T3 test faults fixed (app rows were right; F-07 result blocked by the permission dialog in the test).
- r15 CI 44899160113 @ff056fb7 Pass (356 classes, 1673 cases, 0 failures): second green for CI-DNS-RACE. DEV-CHK-21-C2: Nf1 rewrite did not compile; the runs on it are invalid; fixed and re-run.
- NBUG10-IME root cause found and fixed (flag EXT_HOLD_WHILE_TYPING): AnimePahe's Cloudflare-check window opened on its own and took the keyboard focus; now held behind a card while the user types. Fail-before 44900885102. F-18 fail-before 44899238590 is valid (main compiled). Fail-before redos from HEAD: 44900731236 (NF-2b), 44900735170 (F-02 split), 44900738482 (Country).
- 17:48 Recorded 44899145489 / 44899147205: Nf1 phones Pass, Speed both Pass (FE-S4 device Pass, live 1453..25101 ms), Nbug10 api36 Pass / lowram Fail (NBUG10-IME open), TvBug5 TV Pass, Nf1 TV Fail (NF1-T2, test rewritten to read rows by label), ExternalPlayer both Fail (F07-T2, logging added). Fail-befores 44899230268 / 44899234331 / 44899243747 / 44899238590 invalid (AB-INVALID-1).
- DEV-CHK-21-T: device tests fixed after run 44896550636 / 44896552485 (Java stub player; Nf1 scroll + on-screen rows; Nbug10 match check + keyboard time). SpeedDeviceTest logs 3 more live searches for FE-S4.
- CI-DNS-RACE: Nbug6 flake traced to NetworkSettings.init overwriting a setting saved during load; fixed with a write lock and test hook.
- FE-T1: 2 GB phone feed half-shown 56.8 s (was 4.4 s): startup trims with 800 MB free throttled the feed to one lane. Fixed (FeedThrottle.confirmed). Rerun SpeedDeviceTest lowram after the r15 CI.
- F-07-RES: "Choose each time" hand-off now asks for the result (MX / Just return the position only when asked). Device round trip ExternalPlayerDeviceTest + StubPlayerActivity (androidTest manifest).
- DEV-CHK-21-C: CI 44892857421 failed at device-test compile (wrong YumeiroDrama import in Nf1 test); fixed.
- F-07-VIS: external players (VLC, mpv, MX, MX Pro, Just Player) were invisible on Android 11+ (manifest `<queries>` missing them); declared. Fail-before 44890380227 verified.
- DEV-CHK-21: new device tests for rows with none: Nbug10FilterSearch, VolumeBoost (F-06), Nf1EpisodeStatus, Upcoming (F-08), F01DubTap.
- CI-429: 44889594971 Blocked by Maven Central 429 before tests; superseded by 44890073919 Pass.
- E4-T: background feed work 1 at a time when hot or short of memory, one less when warm (FeedThrottle, flag e4_throttle).
- E4-GUIDE: no change (OnboardingGate holds only a first run with nothing installed; it is the extension pop-up blocker).
- B31-SEEK5-T: TorrentBlocksTest.primePutsTheStartBeforeTheEnd updated to the B31-SEEK5 contract (tail held until the start has bytes); the only failure of 44887743406.
- FE-Q-CHK Pass 44888340063: an update does not reinstall extensions (26/26 files identical) and does not slow their load.
- FE-4b: Yumix working set: only picked + pinned + fastest 24 (12 on 2 GB) fetch live; rest saved pages, join at the feed end (flag fe4_working_set).
- FE-Q-CHK: UpdateFeedDeviceTest (install extensions, relaunch, `adb install -r` update, compare files / timings / dexopt).
- FE-P-T: PauseWhilePlayingTest expectation fixed (gate has 3 permits; it asserted 4).
- FE-S4: a site's search timeout follows its measured speed (8..25 s), quarantined 8 s, Retry 25 s (flag fe_s4_adaptive_timeout).
- FE-4a: Yumix rebuilds its mix at most every 300 ms (first update at once), not on every row answer (flag fe4_feed_frames).
- FE-S3: a site that failed a search in the last 2 min answers with that failure at once; Retry asks again (flag fe_s3_fail_memory).
- B31-SEEK5: the piece the player waits for downloads alone until it has bytes, then window + tail (flag b31_target_first).
- B31-SEEK4: a seek drops the tail deadlines set at open (TV seek 10370 ms, run 44884971496: tail served before the target).
- FE-S2: search ranking and typeahead (regex) moved off the main thread (Online search, YouTube, YT Music).
- FE-5: FeedWarmWorker refreshes the working set's saved homes every 6 h on Wi-Fi (reserve cache, flag fe5_feed_warm).
- FE-2c: HeatGuard's shared limit of 6 extension calls is a PriorityGate: search, scroll pages, picked before background rows.
- FE-P: Yumix background fetches run one at a time while something plays (PlayingNow + PriorityGate backgroundLimit).
- SPEED-T2: SpeedDeviceTest measures the Yumix pattern (20 extensions, 3 slots) and YouTube / YT Music cold start.
- FE-3: Yumix shows an extension at its first home row (`home(api, force, onRows)`, flag fe3_first_row).
- FE-S1: empty search answers kept (memory 10 min / phone 1 h), second disk read on a miss removed.
- FE-Q1 code reading: no reinstall loop after update; compiled `oat` folder is not deleted (device check still Not run).
- B31-SEEK3: torrent engine downloads only the playback window (flag b31_stream_window); B31-SEEK-T insideWindow at file end.
- FE-2: Yumix PriorityGate (picked extension first, flag fe2_priority_gate); dead site asked one batch (25 s, was 75 s).
- FE-7a/b: YouTube shelves and YouTube Music Home saved on the phone and shown at once (were memory-only).
- FE-1 `f37b4224`: `OnlineRepository.feedHome` shows any-age saved homes at once in Yumix and Home, live refresh behind.
- FEED-ENGINE doc docs/FEED-ENGINE-2.1.md (requirement, root causes FE-RC1..4, design, plan).
- SPEED-T: device test `SpeedDeviceTest` (extension relaunch load, Home first row, search first result; tag SPEED).
- B31-SEEK2: request_queue_time 1, seek target alone at 0 ms, read-ahead from 500 ms (research: libtorrent_flutter, VortX).
- SPEED-3/4: extensions load fastest-first; search starts with loaded extensions and late ones join (no restart).
- SPEED-1/2: cached homes and searches skip the permit queue; Home and search fastest-first (ledger row SPEED-1 / SPEED-2).
- E4 part 1 `b640f68d`: ExtensionSpeed (fastest-first Yumix home order, quarantine 3 fails / 6 h), flag `e4_speed_order`.
- B-31 seek fix `e451b783`: abandoned window's deadlines dropped on seek (finding B31-SEEK from device 44879268223).
- E2 `18917728`: `.github/scripts/curate_live.py` curated probed Live TV (ADR-055), switch repo var `YUMEIRO_CURATED_LIVE`.
- E3 `23fad75d`: ABI splits behind `-Pyumeiro.abiSplits=true` / repo var `YUMEIRO_ABI_SPLITS`, one versionCode (ADR-054).
- B-31 privacy notice `787a7901`: engine plays only after the Settings notice is accepted.
- F-22 `706dee41`, F-02 round trip `e0c4e7b8`, B-31 speed `2b286d6c`, F-21 `60e8d6df`, B-32 earlier.

## Verified (run IDs)
- 44916676685 phones Pass F12VerticalModeDeviceTest + F12ShortsSwipeDeviceTest (3 tests) @c3ec754d; 44916693769 TV Pass (entered=false, chrome test skips).
- 44912687672 phones Pass F12ShortsSwipeDeviceTest + ReleaseFeaturesDeviceTest @8c304d4f; 44912689981 TV skip / 5 Pass; unit 44912685131 Pass (lint Fail = F12-LINT).
- 44904481596 F-07 phones Pass; 44904447255 old-row tests phones Pass; CI 44903723798 Pass.
- CI 44901991356 @6196d665 Pass (357 / 1675 / 0). NF-1 Pass 44903495726 (phones) / 44903498117 (tv-api34).
- 44901993315 phones: Nbug10FilterSearchDeviceTest Pass (api33-lowram, api36).
- CI 44899160113 @ff056fb7: Pass, 356 classes, 1673 cases, 0 failures.
- 44899145489 phones: Nf1 Pass (both), SpeedDeviceTest Pass (both), Nbug10 Pass api36. 44899147205 tv-api34: TvBug5Repro Pass.
- CI 44896530640 @33c65f8d Pass (356 classes, 0 failures): pass-after FE-T1 and CI-DNS-RACE. CI 44894227826 @49c4c60b Pass: pass-after F-07-VIS, F-07-RES. Fail-before CI-DNS-RACE 44895869754 verified.
- Device 44896550636 / 44896552485: Pass VolumeBoost (F-06) and Upcoming (F-08) on lowram, api36, tv-api34; F01DubTap (F-01/F-16) both phones; SpeedDeviceTest both phones (FE-T1 fixed: lowram feed half 5151 ms).
- Fail-before F-07-RES 44894173529 (theGenericChooserGetsTheWidelyReadExtras failed as expected) and FE-T1 44894340441 (compile); both branches deleted.
- Phones 44892710506 @e2b87521: BuiltinTorrentDeviceTest Pass both (lowram first 2844 / seek 1129 ms: B31-SEEK5 Pass); SpeedDeviceTest Pass all budgets (GOAL run 3), lowram feed regression = FE-T1.
- Device TV api24 44892712537 @e2b87521: BuiltinTorrentDeviceTest Pass (first 64 KB 8696 ms, seek 3148 ms; was 10370 ms): B31-SEEK4 Pass.
- Pass-after CI 44890073919 @e2b87521 (abi_splits=true): success, 353 test classes, 0 failures: FE-S1, FE-S2, FE-S3, FE-S4, FE-3, FE-P (+FE-P-T), FE-2c, FE-4a, FE-4b, FE-5, E4-T, B31-SEEK4/5 (+B31-SEEK5-T) unit, SPEED-T2 compile; GateLaneTest (T-GATE) passed.
- Fail-before verified: E4-T 44889828561 (unresolved FeedThrottle), F-07-VIS 44890380227 (only failure ExternalPlayersVisibilityTest); branches deleted.
- Fail-before verified: FE-4b 44888534031 (unresolved FeedWorkingSet); branch deleted.
- Device 44888340063 @ad503cc2 phones: UpdateFeedDeviceTest Pass (FE-Q1 no reinstall, FE-Q2 load unchanged after update).
- Pass-after CI 44884554686 @346a581c (abi_splits=true): success, 338 test classes, 0 failures: F-22, E3, E2, E4, SPEED-1..4, SPEED-T1, B-31 notice / seek, B31-SEEK2, B31-SEEK3 / SEEK-T, FE-1, FE-2/2b, FE-7a/b. GateLaneTest passed there (T-GATE looks like runner timing; keep watching).
- Fail-before verified: FE-2c 44885309121 (unresolved PriorityGate.Lane); branch deleted.
- Device phones 44884969625 @7d77a583: SpeedDeviceTest Pass both (SPEED-T2 numbers in GOAL-TOP-OF-CLASS.md); BuiltinTorrentDeviceTest api36 Pass (first 64 KB 3668 ms, seek 3115 ms), api33-lowram Fail (first piece no block 16 s) -> B31-SEEK5.
- Device TV api24 44884971496 @7d77a583: BuiltinTorrentDeviceTest first 64 KB 3774 ms Pass, seek 10370 ms Fail -> B31-SEEK4 (fixed, unverified).
- Fail-before verified: B31-SEEK4+5 44886201772 (3 TorrentSourceTest assertions); branch deleted.
- Fail-before verified: FE-S4 44886794506 (unresolved searchTimeoutMs); branch deleted.
- Fail-before verified: FE-4a 44886560580 (unresolved FeedFrames / atMostEvery); branch deleted.
- Fail-before verified: FE-S2 44885736016 (SearchRankOffMainTest names OnlineSearchScreen / YtMusicBrowse / YouTubeScreens), FE-S3 44886384973 (no retry parameter); branches deleted. Finding FE-P-T: PauseWhilePlayingTest expected 4 on a 3-permit gate (test fixed).
- Fail-before verified: FE-S1 44884613831 (assertion "asked the extension again"), FE-5 44885480520 (unresolved FeedWarm); branches deleted. Same run 44884613831: GateLaneTest.tapStartsAtOnce failed (351 ms > 300): finding T-GATE, watch pass-after.
- Fail-before verified: FE-3 44884810399 (compile: home has no onRows), FE-P 44885100184 (unresolved PlayingNow); branches deleted.
- Fail-before verified: FE-1 44883755874 (unresolved feedHome), FE-7a/b 44883994031 (unresolved savedFeed / savedBrowse), FE-2 44884208915 (PriorityGate / homeTimeoutMs), B31-SEEK3 44884447258 (windowPriorities / streamWindow); branches deleted.
- Device 44883372732 (phones) / 44883375160 (TV) @2dba1982: SpeedDeviceTest Pass both phones (baseline in GOAL-TOP-OF-CLASS.md);
  BuiltinTorrentDeviceTest Fail: lowram first 64 KB 5022 ms Pass, seek 9956 ms Fail; api36 first 64 KB 15137 ms Fail;
  TV first 64 KB 6860 ms Pass, seek 19713 ms Fail -> B31-SEEK3 (diagnosed from TorrentTrace).
- CI 44882251682 (r15-stability): failure, only TorrentSourceTest.seekDropsTheOldWindowDeadlinesButKeepsTheNewOne -> B31-SEEK-T (fixed).
- Device 44882300017 / 44882301935 (B31-SEEK2): first 64 KB Pass 8724 / 4096 / 6106 ms; seek Fail 17353 / 7892 / 15941 ms
  (finding B31-SEEK3: libtorrent fetches a piece's blocks from block 0; 4 MiB pieces; diagnosing with TorrentTrace).
- Fail-before verified: E4 44880750191, SPEED-1/2 44881206020, SPEED-3/4 44881403329, B31-SEEK2 44881530280
  (branches deleted).
- CI 44881247581 (@4a58cb22): failure, finding SPEED-T1 (test used an ERROR-level deprecated constructor); fixed.
- Device 44880554878 (@e451b783, phones): notice gate Pass, first 64 KB Pass 8033 / 3192 ms, seek Fail 7566 / 10744 ms
  (B31-SEEK2). TV 44880575223 (api24): first 64 KB 5238 ms Pass, seek 12732 ms Fail (B31-SEEK2).
- Pass-after CI 44879288541 (@2b286d6c): B-32 EmptyHomeTest, F-21 ExtensionLabelsTest, B-31 TorrentBlocksTest.
- Fail-before verified: E2 44880384185, B-31 seek 44880511385 (branches deleted).
- Fail-before verified: F-21 44878962117, B-31 speed 44879233183, F-22 44879571787, E3 44879928117 (branches deleted).
- Device Pass: F-02 round trip 44879417411 (api36). B-31 first 64 KB Pass 44879268223 (2632 / 5783 ms), seek Fail B31-SEEK.

## Older pending runs (history, all ended)
- r15 unit pass-after CI 44926431107 @95108c0c (OL-8, OL-2, OL-3, OL-1, OL-4, OL-7, OL-9, OL-10 unit + compile + lint).
  When it ends: `gh workflow run ci.yml --ref r15-stability -f review_apk=true` for OL-11, OL-12, OL-5 (head 91667cdd+).
- Fail-before CIs (each must fail only on its new test): ab/ol4-before 44926496585 (StreamDownloadAskTest),
  ab/ol7-before 44926463969 (PlayerTopBarAlignTest), ab/ol9-before 44926469201 (HomeSpacesMoreTest), ab/ol10-before
  44926473786 (NavMoreDestinationsTest), ab/ol11-before 44926724501 (EpisodeRangeDownloadTest), ab/ol12-before
  44927039015 (PlaylistDownloadTest), ab/ol5-before 44927927381 (DownloadTracksTest). Read with
  `gh run download <id> -D /tmp/fb_<id>` + TEST-*.xml; no XML = compile error (`--log-failed | grep "e: "`).
- Device: r15 lowram 44927936416 and TV api34 44927947420 (WebsiteTabDeviceTest, StreamWatchDownloadDeviceTest,
  MoreDestinationsDeviceTest; logcat tags YuOlTab, YuWatchDownload, YuOlDest).
- Still to dispatch: device fail-befores ab/ol4-before (StreamWatchDownloadDeviceTest), ab/ol9-before + ab/ol10-before
  (MoreDestinationsDeviceTest), api36 is enough. Max 2 device workflows at once.
- Branch cleanup after each valid fail-before: `git worktree remove --force /tmp/fb_X; git push origin --delete
  ab/X-before; git branch -D ab/X-before` (ol8, ol2, ol3, ol1 are valid already).
- Ledger rows hold PENDING-UNIT / PENDING-FB / PENDING-DEV / PENDING-NEXT markers: replace each with the run result.

## Next steps, in order
1. Record the pending runs above (ledger + must-do Part 3b statuses). A failing test on new code = its own finding
   with its own fail-before; never relax an assertion or budget.
2. OL-6 downloader modal redesign (research other apps' download sheets first; build behind a flag; test + fail-before).
3. Part 8 step 2: device tests for NF-2b, F-02 split pages, F-18 and Country (fail-befores already valid; write the
   tests). Then step 3 F-02 Telegram TDLib login, step 4 E2 / E3 / E4 verification, step 5 F-03 / F-05 / F-19 / F-20 /
   airing alerts / X-1 / EXT-P / PIN-1 / GATE-1 ANR / FREEZE / C5 / C6, step 6 FEED-ENGINE-2.1.md leftovers.
4. E1 master extension: docs/E1-MASTER-EXTENSION.md; start E1-A only on the owner's go (open questions in section 6).
5. Release only on the owner's explicit go.
