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
| OWN-2 | Online features stop working after skipping the tour, a fresh install or an update | Code reading: OnboardingGate releases correctly after Skip (MTPlayerApp.kt:1672-1681, SetupGuide.kt:216). The network layer is unchanged since 2.0.0 except NBUG-13 text. The one setting that differs between "did the setup" and "skipped / fresh / never touched": DNS. The checklist step "Set up DNS" (SetupGuide.kt:85) is the only place a user is sent to pick a resolver; skipped = System DNS (NetworkSettings.kt `DnsConfig()` provider SYSTEM, fallback off). On networks whose DNS hijacks or blocks hosts (common on BD ISPs), the auto bypass only reacts per host after a failure. Second cause on fresh installs: no source is installed at all (OWN-9) | Yes (core flow: start / Home / search on a fresh install) | Fix with OWN-7 (Cloudflare DNS by default). Can not be reproduced in CI (GitHub runners are not on a blocking network): owner home-network test OMT-83 |
| OWN-3 | Telegram sign-in with API ID, API hash and phone code, like Hikari | Already built (F-02 login, TDLib, ADR-052) behind flag `f02_telegram_login` = **off**; it uses the build's own api_id (repo secrets). Hikari lets the user type their own api_id / api_hash (my.telegram.org). Full sign-in is Blocked in CI (Telegram has no public test accounts, tdlib/td#3083); OMT-63 | No (feature, flag off) | open: decide flag + own-key option |
| OWN-4 | Vega, SkyStream and other single source links not accepted in the Extension Market add box | **Found**: Extension Market "+" (ExtensionMarketScreen.kt:315-327) and Extensions "Add repository" (ExtensionsScreen.kt:360-369) call `ExtensionManager.addRepository` (CloudStream repo only). The detector that knows every format (`UniversalAdd.add`) is used only by "Install from link" / "Add anything" | Announced-feature gap (Vega / SkyStream / Nuvio are in the 2.1.0 notes) | Fix now |
| OWN-5 | Vega and other managed repos: no installed status, not in the installed list, no shortcut | open | No | open |
| OWN-6 | Yumix -> details -> player is slow (feed itself fast) | open | No (perf) | open |
| OWN-7 | Cloudflare as the default network | DNS default is System (NetworkSettings.kt DnsConfig) | Part of OWN-2 | Fix now: Cloudflare DNS-over-HTTPS for every lookup by default, system DNS as the safety net, LAN names stay on the system resolver; a user's saved choice is kept |
| OWN-8 | Downloader: pick quality, subtitles, audio (sub / dub) before download; same in the player | OL-5 / DL-ONE already offer HLS heights, audio and subtitles in the download popup for website / HLS links; open: check the extension episode path | No | open |
| OWN-9 | Official Yumeiro extension, none installed by default | **Confirmed**: no official extension exists. Owner decision 2026-11-21 "no third-party starter extensions; use the Yumeiro master extension (E1)". E1 (Yumeiro Hub) is **not built**, planned 2.2.0 (docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md). A fresh install has zero sources; the empty Home shows Get started (B-32) | No code fix possible in 2.1.0 without building E1 (new feature, freeze) | Known issue in 2.1.0 notes; E1 stays 2.2.0 |
| OWN-10 | TeraBox and "Linkwala" (read as DiskWala) not working; check how Hikari does it | TeraBox: measured 2026-11-22 13:40 UTC from the sandbox: `api/shorturlinfo` answers `errno 400210 "need verify_v2"` (TeraBox wants a human check). TeraBoxApi.kt shows that as a dead end ("TeraBox refused the share (error 400210)"). Hikari (codegeasse1/hikari NetworkStream.kt) has no handling for it either. DiskWala: `file/temp_info` still gives name and size, no URL (measured); Hikari runs the share page in a hidden WebView and reads the page's own API answers | TeraBox: broken flow for an announced link type | TeraBox: open the share page in the in-app browser for the user's own check, then retry (no captcha bypass). DiskWala: measuring the page in a headless browser |

## 3. Per-commit table (W2)

open

## 4. Findings register

| ID | Severity | Blocker | File:line | Root cause | Fix / deferral | Evidence |
|---|---|---|---|---|---|---|
| AUD-DOC-1 | Low | No | brief / HANDOVER-LIVE.md | "44 commits" counted from b553505; base..HEAD is 243. HANDOVER-LIVE header 14:12 vs progress log 16:50, stale "Pending runs" lines | Docs fixed in this audit | `git rev-list --count e47f450f..dd4ae197` = 243 |

## 5. Regression runs on the release candidate (W1)

| Run | Workflow | Commit | Started (UTC) | Result |
|---|---|---|---|---|
| 45024851633 | CI review_apk=true (baseline on the pre-audit head) | dd4ae197 | 12:56 | running |

## 6. Flags (W3), 7. Security (W4), 8. Upgrade (W5), 9. Release checklist (W8), 10. Not checked, 11. GO / NO-GO

open
