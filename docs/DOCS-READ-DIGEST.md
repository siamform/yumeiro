# Docs read digest (owner order 2026-11-22: "you have to read every single doc in the project by past agents")

Written by the agent on 2026-11-22 for the next agent. Every top-level doc in docs/ and every file in docs/groups,
docs/proposals, docs/redesign, docs/reports, docs/security, docs/website and docs/licenses was opened.
How: the live docs (AGENT-START-HERE, HANDOVER-LIVE, R15-STATUS, MAJOR list, BUGS-AND-FIXES-2.0.0, SYSTEM-DESIGN
all 1815 lines, FEED-ENGINE-2.1, GOAL-TOP-OF-CLASS, UNVERIFIED-QUEUE, INVENTORY, OWNER-MANUAL-TESTS, F-05, E1) were
read in full. The large history files (HANDOVER 87 KB, MASTER-CHECKLIST-FINAL 98 KB, DONE-LEDGER 232 KB,
CHECKLIST-FEATURES-BUGS, GUIDE, groups/, proposals/, redesign/, reports/, security/) were read by headings plus
every open / pending / blocked / not-run / owner line. docs/evidence/ holds raw run logs (data, not prose): not read
line by line.

## Action summary (what reading changed)

| # | Finding | Action |
|---|---|---|
| D-1 | Worker is deployed (SERVER.md "Production state", 2026-11-20, migrations 0003-0005). MAJOR "Airing alerts" said the deploy waits for the owner | Fixed the row: end-to-end check is an owner phone test (OMT-52), not a deploy |
| D-2 | Prod check 2026-11-22: GET /v1/health ok; POST /v1/lastfm/sign answers 503 `lastfm_off`; Worker secrets are only ADMIN_TOKEN, FCM_SERVICE_ACCOUNT | Built-in Last.fm sign-in (ADR-048, SEC-LFM) waits for the owner to rotate the Last.fm secret and set LASTFM_SHARED_SECRET (B-9). Recorded |
| D-3 | MAJOR OL-6 / OL-13 rows still said "pending" | Closed with their runs (OL-6-T CI 44944975133; OL-13 device 44936349725 / 44936347387) |
| D-4 | FE-S4 device: SpeedDeviceTest logs live2..live4 (1453..25101 ms in 44899145489) but asserts nothing for FE-S4 | Stays Not run as a budget pass (no assertion); GOAL text updated so it no longer says "next run" |
| D-5 | Yumeiro is GPL-3.0 (LICENSE). Owner rule still forbids copying GPL/AGPL code | Rule kept; noted |
| D-6 | E1 Layer B "resolver fan-out" is the same seam as F-05 stream merge | Build the F-05 seam keyed by tmdb/imdb + season/episode so E1-B reuses it |
| D-7 | LIC-1: NOTICE does not name the Google SDKs (owner wording) | Stays owner |
| D-8 | docs/website/yumeiro-web-v1.9.0.patch waits for a "go"; the site was not checked for 2.0.0 | Owner item, not touched |
| D-9 | Proposal headers (F-01, F-04, F-06..F-08, F-10, F-12 "proposal, not built") are point-in-time | MAJOR list is the source of truth; not rewritten |
| D-10 | Agent rules found only in older docs: no scheduled workflows; ≤ ~10 test classes per CI run; never batch the release gate; upgrade check on the exact review APK before main (B-1, FINAL-46) | Followed |
| D-11 | EDGE-CASE-AUDIT open: tracker sync retry after offline (P2-4), WebView page audio vs music (P2-5) | Added to UNVERIFIED-QUEUE reconciliation (step 8) |
| D-12 | ROADMAP owner todos: uptime monitor on /v1/health; rotate the Cloudflare token shared in chat | Owner |
| D-13 | Torrent goal bar is FIRST FRAME <= 15 s, but BuiltinTorrentDeviceTest measures the first 64 KB over HTTP | New row B31-FRAME (Not run) in MAJOR list and ledger; flag stays off until a real-player frame test passes |

## Notes per doc

## AGENT-START-HERE.md (228 lines) — read
- Rules match the resume prompt. Extra: do not generate images for features that don't work; "Partial" not allowed as
  test status; push after every job.
- NEW: keep two shared documents up to date: "Yumeiro Handover for Next Agent" and "Yumeiro 2.x Major Bugs and
  Features" (same names). Where do they live? (check: Google Docs/Notion connector?) -> find out.
- Owner decisions: one registry for Vega/Nuvio/SkyStream; no third-party starter extensions, use E1; discreet icon
  dropped; Telegram full login; reverse engineering allowed for interop; no DRM/login/paywall bypass.
- Section 7 order (2026-11-21): F-02 TDLib, B-31 torrent, B-32 extensions load nothing, E1, F-21/F-22, rest.
- Traps: RegexGuardTest; compose test clocks; git add with deleted path; main-thread disk on lowram.

## HANDOVER-LIVE.md — read
- Lower half is stale (2026-11-21 OL-* pending runs). Need to check OL-1..OL-12 rows in the ledger are closed;
  OL-6 downloader modal redesign listed as next (check status). Part 8 step 2 device tests for NF-2b, F-02 split
  pages, F-18, Country (check written?).

## R15-STATUS.md (637 lines) — read
- Line 3 resume pointer -> HANDOVER-LIVE. Rest is a superseded history log (2026-11-15..20).
- Older open items to re-check: ST-7 (main-thread stalls, real phone OMT-12), ST-17 (spins, stays open), X3-TL,
  X13-6, TV-4 cross-source search (waiting on owner), YouTube quality on a home network (CI IPs bot-walled),
  "Still owed: rotate Last.fm shared secret before built-in Last.fm sign-in".

## MAJOR-BUGS-AND-FEATURES-2.x.md (213 lines) — read
- Open/not-final rows: GATE-1 (real phone), F-05/X-1 Partial, F-22 part built (filters Blocked: Simkl lacks
  country/genre), F-21 device screenshot Not run, 2x Built (device check), F-11 Built (real phone), PIP-1 Built,
  Airing alerts end-to-end Blocked (Worker deploy go OMT-52; NOTE: I found Worker deployed 2026-11-20 — re-check),
  OL-6 status says r15 CI 44936285767 pending, OL-13 says device runs pending -> check ledger whether closed,
  B-3 Not reproduced (needs real link), S-1 guarded, BUG-3 owner, C5/C6 Not run, NBUG-8 OMT-34, F-20 row text
  contradicts itself ("Not built: security questions" vs Verified B-19) -> fix wording.
- GOAL-TOP-OF-CLASS G-01..G-10 gap rows (debrid, skip credits, seek previews, sync, MDBList, FCast) follow owner order.

## BUGS-AND-FIXES-2.0.0.md (ledger, 235 lines) — read fully
- OL-1..OL-13 all Pass (OL-6 closed via OL-6-T, CI 44944975133). F-02-TV Pass 44936347387 (so the F-02 login
  row's "TV api34 PENDING-DEV" text is stale -> fix wording).
- Still open in the ledger: B-8 thread spike (real phone), B-9 Last.fm rotate (owner), GATE-1-MUSIC-START PENDING,
  GATE-1-VIDEOS-COMPOSE open, F-21 device screenshot Not run, F-22 device check Not run + filters Blocked,
  FE-4b 1000-extension device run Not run, FE-5 device Not run, FE-S2 device Not run, FE-S4 device (second live
  search on measured sites) Not run, FE-4a frame counter Not run, SPEED-3/4 device (search during cold start) Not
  run, F-07-VIS device Not run (later covered by F-07 Pass), B-29 signed-in Blocked (owner account),
  E2 in-app Blocked (owner go), E3 website detector Not run, B-32 upstream report (owner decides), T-GATE watch.
- RULES learned: dex register budget 224 per Compose method (scripts/dex_register_budget.py) — split big
  composables, never raise; never release before upgrade check verify=0 fatal=0 on the exact review APK
  (phone + TV); timestamps from `TZ=Asia/Dhaka date` (TIME-FIX).

## HANDOVER-NEXT-AGENT-2026-11-20.md — read
- Method: map release stacks with androguard on the release APK (no mapping.txt uploaded); Compose trace strings
  give file:line. Dependabot branches exist (check Jackson pins). FREEZE-4: ask owner for a 2.1.0 freeze report and
  read `draws` lines. Release steps: version.properties 2.1.0/19, CHANGELOG, WhatsNew.shouldPost 2.1.x.

## proposals/FEATURE-F-05-nuvio-skystream-repos.md — read (KEY for current work)
- Rules: no default repos ever; user-added only with ExtensionReview consent sheet (TV: Cancel focused first);
  DRM streams (drm/ClearKey) dropped ("Protected stream, not supported from extensions"); no TMDB keys shipped;
  no bundling of GPL cheerio/crypto-js builds or harness files (Hikari/Nuvio/SkyStream GPL, Vega PolyForm,
  vega-providers no licence). PublicOnly (SRC-2) guard, 30 req/call, 5 MB/response, script 2 MB cap, sha256 +
  re-consent on hash change, fresh runtime per call, memory 48/32 MB, 4/2 concurrent, no background runs.
- Flags named scripts_nuvio / scripts_vega / scripts_skystream (check FeatureFlags names actually used).
- Source badge "Nuvio"/"SkyStream" in the stream picker; empty state "No scrapers returned a stream".
- Planned NuvioRunnerTest (fixture scraper returns 2 streams; DRM dropped) + ScriptSourcesDeviceTest.
- Nuvio app: fresh QuickJS per call, MAX_CONCURRENT 10, 60 s timeout; DOM via native Ksoup bridge (Yumeiro has
  dom_* host already). SkyStream plugin API: globalThis getHome/search/load/loadStreams with callback
  cb({success,data}), `manifest` global with baseUrl; .sky zip = plugin.json+plugin.js (path-escape checks).

## SYSTEM-DESIGN.md (1815 lines) — lines 1-1520 read
- Next free ADR number: ADR-057 (last is ADR-056 F-12 vertical mode). ADR-047 is the F-05 seam ADR.
- ADR-047 says Nuvio/SkyStream need a "stream-merge seam" first; Stremio stream-only add-ons are also unused
  today (StremioAddons.publish only publishes catalog add-ons) -> the seam can serve Stremio too.
- ADR-047 deviation: consent = explicit Install tap per provider (ExtensionReview is DEX-only). OMT-48 real repo.
- ADR-032: JS limits 20 s load / 60 s call; JsBridge PublicOnly via SmartDns.isBogon on every hop.
- ADR-006: PlaybackService+MediaController move = future work. ADR-016 DL-11 budget "nearly met" (1.1004x).
- ADR-017 F-10 glass & ADR-018 F-12 shorts: "Built, not tested" with evidence owed (frame timing etc.).
- ADR-025/026: data-branch/logo fixes only reach users after main runs market workflows (owner action at release).
- ADR-039/043: Worker deploy needs migrations 0003-0005; Worker found deployed 2026-11-20 (re-check fcm_follows).
- ADR-041 profiles: PIN PBKDF2-SHA1 12000 rounds; extensions per profile still shared (Epic).
- ADR-048: Last.fm built-in sign needs Worker secret LASTFM_SHARED_SECRET (owner go) — check if set.
- SYSTEM-DESIGN rest (1520-1815) read: ADR-049 DiskFullGuard, ADR-050 TV FFmpeg surround gate, ADR-051 TV keyboard
  on OK, ADR-052 TDLib (flag TELEGRAM_LOGIN off until device tests), ADR-053 torrent (flag b31_builtin_torrent off
  until device bars; ONE-TIME IP-sharing NOTICE "not built yet" — check code), ADR-054 ABI splits (var
  YUMEIRO_ABI_SPLITS), ADR-055 curated live (var YUMEIRO_CURATED_LIVE), ADR-056 F-12 vertical (f12_vertical_shorts
  off until Verified). ADR-017/018 status words "Built, not tested".

## FEED-ENGINE-2.1.md — read
- Status IN PROGRESS. Open: FE-4b device (1000-ext working set) Not run; lazy code load Not run (argued unnecessary);
  FE-5 warm-up device Not run. FE-S4 device shows Pass 44899145489 here, but ledger lists "FE-S4 device (second
  live search on measured sites) Not run" -> CONTRADICTION to reconcile (first search still 25 s for unmeasured sites).
- Budgets: Yumix first content ≤1 s when saved; first live row fresh install ≤5 s api33-lowram; no ANR with 50 ext.
- FE-Q1/Q2 closed: no reinstall after update (44888340063).

## UNVERIFIED-QUEUE.md — read (STALE in parts)
- Features table still says F-01/F-04/F-06..F-10 "Queued" though many were built/verified later (MAJOR list) ->
  reconcile in Part 6 step 8. F-05-N row = current work (stream-merge seam). Owner tests OMT-1..34, 47..53 listed;
  OMT-35..72 not in this table. Open agent gaps: G7-GAP-1/2, NB12-5b, NB16-6/7, TV-PIN-1, TV-YTC-1, TV-YT-1,
  ER-28-TV34, TV-A11Y-1, G8-CQ-2/3/4, G8-WEBM7, FINAL-46b (R8 invalid VideoPlayerModalContent with empty build values:
  ALWAYS upgrade-check any rebuild before publishing), FINAL-35/45 not reproduced, L4 re-audit, L5 final sweep.

## INVENTORY.md — read (STALE in parts)
- Non-done items: H12 widget audited; E10/E12 audited; E13/E14 "in progress (feat/sources)" (done since ADR-032/047);
  P8/P13-P17 audited; Y2/Y7/Y8 unit only; X3 ST-7/ST-17 open; N10 "Built, not tested" (profiles etc. later verified?);
  "Proposed features (not built)" table is outdated (most built). "Not covered" section never filled. -> step 8.

## OWNER-MANUAL-TESTS.md — structure read (OMT-1..72)
- Status summary table lacks OMT-72 (GATE-1 no freeze) -> add. OMT-22 text should add the 2 APIs (testing,
  toolresults) + Editor role found 2026-11-22. OMT-63 Telegram login: flag f02_telegram_login off until it passes.
- Owner tests never block work. Build to test: "1.9.0 RC review APK" text is outdated (2.x now) -> update wording.

## Small top-level docs read: TV-AND-PHONE, DISTRIBUTION, USER-TEST-PROGRAM, UPDATE-universal-sources,
## RELEASE-POST-2.0.0, STATUS-v1.8.1, GATE-1-REAL-DEVICE, PROGRAM-2026-11-19, EXTENSION-COMPAT, TV, SERVER-TEST-FINAL
- USER-TEST-PROGRAM still says build "1.9.0" (stale; 2.x). DISTRIBUTION: IzzyOnDroid/F-Droid ideas (foss flavor) not done.
- EXTENSION-COMPAT: run scripts/extension_link_audit.py after every CloudStream library bump.
- SERVER-TEST-FINAL S-09 Last.fm sign route was 404 in prod, S-12 migrations 0003-0005 Blocked -> re-check prod now
  (Worker found deployed 2026-11-20): GET /v1/health, POST /v1/lastfm/sign shape, D1 tables fcm_follows.
- TV.md "Not verified on real hardware" list (rail on real TV, 1 GB stick, Android 7 WebView LE, voice search).
- PROGRAM-2026-11-19: features need proposal in docs/proposals with competitor refs, options, side effects,
  2 GB + TV perf budget, test plan BEFORE build (owner approval given on that condition).

## GOAL-TOP-OF-CLASS.md — read
- Bars: first frame direct link warm ≤1.5 s api33-lowram (is there a run ID? check ledger); torrent bar measured as
  "first 64 KB" 2844 ms / seek 1129 ms (44892710506) — NOT literally first FRAME with the test hash on a healthy
  swarm -> verify a first-frame measurement exists before claiming the bar. GATE-1 700 ms.
- search_all_answered_live has no budget (25 s); FE-S4 second-search check is the "Not run" from the ledger.
- Not yet measured: feed with 50+ installed.
- G-01..G-10 rows all Missing, order after the owner list: G-03 debrid, G-05 skip credits, G-06 seek previews,
  G-08 sync, G-02 MDBList, G-04 FCast, G-07, G-09, G-10, G-01. Each needs ADR + flag + budgets + test.
  Trakt secret "still missing (owner)".

## E1-MASTER-EXTENSION.md — read
- Not built; owner go needed; 3 questions pending (asked 2026-11-22). Agent rule: "no scheduled workflows" (why the
  probe schedule needs the owner). Layer B "resolver fan-out" = same seam as F-05 stream merge -> design the F-05
  StreamAddon seam keyed by tmdb/imdb + season/episode so E1-B can reuse it. TMDB key stays on the Worker only.

## REMAINING-2026-11-19, CODE-AUDIT-FINAL, NEW-BUGS-FEATURES-2026-11-18, ARCHITECTURE, DEVELOPMENT, STABILITY-R15,
## ROADMAP, INSPIRATION — read (historical; superseded by MAJOR list)
- DEVELOPMENT rules: one PR per update squash-merge (main protected); CI budget ≤ ~10 test classes per run, ≤2 device
  runs; never cross groups in batches; release gate never batched (one unbatched full regression on release commit).
- ROADMAP owner todo: add /v1/health to an uptime monitor; rotate Cloudflare token shared in chat (owner).
- Still-open ideas: keyless subtitles via Stremio subtitle add-ons (INSPIRATION "Next ideas"); TMDB trailer Shorts feed.
- REMAINING §3 decisions kept: G7-L5-C keep; G8-CQ-2..4/WEBM7 not in release; TV-FP-1/TV-PIN-1/TV-YTC-1 test rewrites.

## SERVER, BEHAVIOR-SPEC, PUSH-FCM, RELEASE-SUMMARY-FINAL, EDGE-CASE-AUDIT, HANDOVER-FINAL-LANES, MANUAL-TESTS — read
- SERVER.md: production deployed 2026-11-20 07:27 UTC with migrations 0003-0005 (so S-12 Blocked + MAJOR "Airing
  alerts needs owner go" are STALE). Still check whether LASTFM_SHARED_SECRET Worker secret is set (B-9 owner rotate).
- LIC-1: NOTICE lacks Google SDKs (Firebase, Play services, Cast, ML Kit) — owner wording needed.
- EDGE-CASE-AUDIT open: tracker sync retry after offline (P2-4), WebView page audio vs music (P2-5).
- FINAL-46 lesson: an upgrade-check build without Last.fm/LRCLIB/OpenSubtitles/FCM values made R8 emit invalid dex.
- BEHAVIOR-SPEC §12 "Not live yet" FCM line is stale.

## GUIDE, CHECKLIST-FEATURES-BUGS, MASTER-CHECKLIST-FINAL, HANDOVER, DONE-LEDGER — read by structure + open-row extraction
- All historical (1.9.0 RC and earlier). MASTER-CHECKLIST-FINAL: 0 Fail, P2 cells "Not run (owner P1 only)".
  CHECKLIST-FEATURES-BUGS still says F-02/F-03/F-11/F-AIR "Not started" and many "Built, not tested" -> superseded
  by MAJOR list/BUGS ledger; mark superseded header line rather than rewrite. DONE-LEDGER open: ST-7 (stays open),
  X2-1, EXT-T1, DL-11 part 2 nearly met. HANDOVER.md §29 "FCM_SERVICE_ACCOUNT missing" stale (set 2026-11-14).
