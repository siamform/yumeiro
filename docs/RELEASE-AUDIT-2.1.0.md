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

## 2. Owner list 2026-11-22 evening (OWN-1 .. OWN-10), triage

The owner sent ten items with "hurry, fix all and prepare release". Blockers (Part 5 definition) are fixed now with the
standard method; the rest are fixed now only if small, safe and testable before the final gate, else queued for 2.1.1
with a reason. Status is updated in this table as work lands.

| ID | Owner item | Root cause found (file) | Blocker? | Plan / status |
|---|---|---|---|---|
| OWN-1 | Page transitions on My List / Reading and across Yumix -> details -> player close or cut off | open (investigating nav code) | Investigating | open |
| OWN-2 | Online features stop working after skipping the tour, a fresh install or an update | Code reading: OnboardingGate releases correctly after Skip (MTPlayerApp.kt:1672-1681, SetupGuide.kt:216). The one setting that differs between "did the setup" and "skipped / fresh / never touched": DNS. The checklist step "Set up DNS" (SetupGuide.kt:85) is the only place a user picks a resolver; skipped = System DNS + auto bypass (NetworkSettings.kt `DnsConfig()`). On networks whose DNS hijacks or blocks hosts in ways the bypass does not notice, sources fail. An update keeps the old default too (no `dns_provider` key saved). Second cause on fresh installs: no source installed at all (OWN-9). **Probable** root cause: not reproducible in CI (runners are not on a blocking network) | Yes (core flow) | **Fixed with OWN-7** (78a667f6). Owner home-network test OMT-83 decides whether it is the whole cause |
| OWN-3 | Telegram sign-in with API ID, API hash and phone code, like Hikari | Already built (F-02 login, TDLib, ADR-052) behind flag `f02_telegram_login` = **off**; it uses the build's own api_id (repo secrets). Hikari lets the user type their own api_id / api_hash (my.telegram.org). Full sign-in is Blocked in CI (Telegram has no public test accounts, tdlib/td#3083); OMT-63 | No (feature, flag off) | open: decide flag + own-key option |
| OWN-4 | Vega, SkyStream and other single source links not accepted in the Extension Market add box | **Found**: Extension Market "Add repository" (ExtensionMarketScreen.kt:315-327) and Extensions "Add repository" (ExtensionsScreen.kt:360-369) called `ExtensionManager.addRepository` (CloudStream only). `UniversalAdd.add` (every format) was used only by "Install from link" / "Add anything" | Announced-feature gap | **Fixed** a4dd5ddf: both dialogs try CloudStream first, then the universal detection (`UniversalAdd.addFromRepoDialog`). Test Own4RepoDialogTest; fail-before ab/own4-before CI 45025907314 (pending). A single `.sky` file stays "recognised, can't run yet" (unchanged, honest message) |
| OWN-5 | Vega and other managed repos: no installed status, not in the installed list, no shortcut | **Found**: EcosystemRegistry (Vega / Nuvio / SkyStream) sources were never part of the Extensions list; only Sources -> Script repos (tab 5) showed them | No (usability) but small and testable | **Fixed** a5d8d0d3: Extensions page card "<ecosystems> sources: N installed" with names and Manage -> Sources -> Script repos. Test Own5ScriptSourcesListTest; fail-before ab/own5-before CI 45026045898 (pending) |
| OWN-6 | Yumix -> details -> player is slow (feed itself fast) | open | No (perf) | open |
| OWN-7 | Cloudflare as the default network | DNS default was System (NetworkSettings.kt DnsConfig) | Part of OWN-2 | **Fixed** 78a667f6: no saved choice -> Cloudflare DNS-over-HTTPS, mode Always; system DNS is the safety net when Cloudflare is unreachable; LAN names (.local, .lan, no dot) stay on system DNS; saved choices kept. Flag `own7_cloudflare_default` (on). Test Own7CloudflareDefaultTest (4); DnsSettingsRaceTest fresh-prefs line follows the new default (spec change, not weaker). Fail-before ab/own7-before CI 45025542939 (pending) |
| OWN-8 | Downloader: pick quality, subtitles, audio (sub / dub) before download; same in the player | OL-5 / DL-ONE already offer HLS heights, audio and subtitles in the download popup for website / HLS links; open: check the extension episode path | No | open |
| OWN-9 | Official Yumeiro extension, none installed by default | **Confirmed**: no official extension exists. Owner decision 2026-11-21 "no third-party starter extensions; use the Yumeiro master extension (E1)". E1 (Yumeiro Hub) is **not built**, planned 2.2.0 (docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md). A fresh install has zero sources; the empty Home shows Get started (B-32) | No code fix possible in 2.1.0 without building E1 (new feature, freeze) | Known issue in 2.1.0 notes; E1 stays 2.2.0 |
| OWN-10 | TeraBox and "Linkwala" (read as DiskWala) not working; check how Hikari does it | **TeraBox, correction**: the 400210 "need verify_v2" seen first (12:59 UTC) came from a request that did not follow the share page redirect, so it had no jsToken: not a valid measurement. Re-measured 13:06 UTC with Yumeiro's exact request (redirect followed, jsToken, app_id params): `shorturlinfo` errno 0 in 4 of 4 tries; a bare request without the token gets 460030 "dragdrop" (slider check). So the anonymous step works; the signed-in steps (`share/list` dlink, `share/streaming`) need a TeraBox account: **Not checked (no TeraBox account in the audit)**. Hikari differs: share origin instead of terabox.app, a `browserid` cookie, one fresh-session retry on a verify answer. **DiskWala**: the share page in headless Chromium (13:0x UTC, XHR log) shows "Download the app to access this file", makes no file API call and has no video element; `temp_info` gives name and size only. Hikari's WebView fetch hook would capture nothing on today's page | TeraBox: maybe (announced link type) | TeraBox: **fixed the dead end** 179d9e30: errno 400210 / 460030 / 400141 open the share page in the app's browser for the user's own check (never solved by the app), then retry. Test in TeraBoxApiTest; fail-before ab/own10-before CI 45025909528 (pending). Whether TeraBox plays for a signed-in account: owner test OMT-84. DiskWala: unchanged, handoff to the DiskWala app is the only working path (the website itself refuses to play) |

## 3. Per-commit table (W2)

open

## 4. Findings register

| ID | Severity | Blocker | File:line | Root cause | Fix / deferral | Evidence |
|---|---|---|---|---|---|---|
| AUD-TB-1 | Low | No | this file, OWN-10 first version | Auditor's own wrong measurement (no redirect follow) reported as TeraBox needing a human check | Corrected above with the re-measurement | curl logs 13:06 UTC |
| AUD-AB-1 | Low | No | origin ab/*-before | 8 old fail-before branches still on origin (b27, b29, b30, freeze1, freeze2, gate1, prof1, t1) although the method says delete after | Delete after the owner's go (deleting is not reversible; listed in the checklist) | `git branch -r` |
| AUD-DOC-1 | Low | No | brief / HANDOVER-LIVE.md | "44 commits" counted from b553505; base..HEAD is 243. HANDOVER-LIVE header 14:12 vs progress log 16:50, stale "Pending runs" lines | Docs fixed in this audit | `git rev-list --count e47f450f..dd4ae197` = 243 |

## 5. Regression runs on the release candidate (W1)

| Run | Workflow | Commit | Started (UTC) | Result |
|---|---|---|---|---|
| 45024851633 | CI review_apk=true (baseline on the pre-audit head) | dd4ae197 | 12:56 | running (gitleaks Pass) |
| 45025542939 | CI fail-before OWN-7 | ab/own7-before cab29234 | 13:04 | running (must fail only in Own7CloudflareDefaultTest) |
| 45025907314 | CI fail-before OWN-4 | ab/own4-before | 13:08 | running (must fail only in Own4RepoDialogTest) |
| 45025909528 | CI fail-before OWN-10 | ab/own10-before | 13:08 | running (must fail only in TeraBoxApiTest.ownTen...) |
| 45026045898 | CI fail-before OWN-5 | ab/own5-before | 13:10 | running (must fail only in Own5ScriptSourcesListTest) |

## 6. Flags (W3), 7. Security (W4), 8. Upgrade (W5), 9. Release checklist (W8), 10. Not checked, 11. GO / NO-GO

open
