# Yumeiro 2.1.0: open items after the cross-check (OWN-17)

Made 2026-11-22 on the owner's order (OWN-17): cross-check every bug and feature from the chat (OWN-1..17) and from
docs/MAJOR-BUGS-AND-FEATURES-2.x.md and docs/NEW-BUGS-FEATURES-2026-11-18.md (status in
docs/CHECKLIST-FEATURES-BUGS.md). An item that is fixed and proven leaves the queue. Anything partial, not done or
only "built" is listed here with what is missing. Words used:

- **Done**: code in 2.1.0 and a passing test with a run ID.
- **Coded, CI pending**: code pushed, the run that proves it is still going (run ID given).
- **Owner test**: the agent part is done; only a real phone, a real account or the owner's own judgement can close it.
- **Not in 2.1.0**: not built in this release, with the reason. The owner can say "build it now".

## 1. The owner's chat list (OWN-1..17)

| ID | Item | Status |
|---|---|---|
| OWN-1..12 | First two owner lists (nav guard, fast title open, Telegram keys, OWN-8 batch subtitles, Classics, YouTube audio, PIN reset, ...) | Done: rows, commits and runs in docs/RELEASE-AUDIT-2.1.0.md section 2 |
| OWN-13 | One built-in "Yumeiro" source, all in one, no install | Coded, CI pending (34f2a2ba, CI 45040526941). One card, one switch, one home, one search; drama + films, documentaries, cartoons, TV, films by country. Rest of E1 (below) is Not in 2.1.0 |
| OWN-14 | Page close / Back not like the open animation | Coded, CI pending (d8ae2c3b): one shared enter/exit pair, same duration both ways; Inbox / Support and Settings now close the way they open |
| OWN-15 | Locked profile, app swiped away, unlock hours later: force close, then stuck on the logo | Device test written (ProfileColdStartDeviceTest, run 45039700220). Result and fix: docs/RELEASE-AUDIT-2.1.0.md row OWN-15 |
| OWN-16 | Yumix > Upcoming: hard-coded filters, no search, filter should be an icon, dynamic list | Coded, CI pending (d8ae2c3b): search box, filter icon with a sheet, every filter list built from the data (genres, channel countries, days), schedule country from the phone first |
| OWN-17 | This cross-check | This file |

## 2. MAJOR-BUGS-AND-FEATURES-2.x.md

Every row was read. Rows marked Verified / Done / Pass with run IDs stay closed and are not repeated here.

| ID | What the list says | Cross-check result | Status |
|---|---|---|---|
| E1 Yumeiro Hub | Not built, plan for 2.2.0 | The in-app part (one built-in source, no install) is OWN-13. Not built: the catalog Worker on yumeiro.click, the daily index job, and using installed extensions as extra link resolvers (plan docs/E1-E2-YUMEIRO-HUB-AND-LIVE.md, steps E1-A..E1-E). These need a server and a schedule, which need the owner's yes | Not in 2.1.0 (needs owner yes for server + schedule) |
| E2-ALL Yumeiro Live | Not built beyond E2 | E2 (curated per-country lists, dead links dropped in CI) is built and CI-tested. The merged default list and the in-app default need the first publish of E2 to the streams branch, which is the owner's go | Not in 2.1.0 (needs owner go to publish) |
| F-05 Nuvio / SkyStream / Vega | "Partial" | Re-read: all three import and run (device 44985807462, 44992834769), Stremio stream add-ons pass 44995462689. "Partial" is out of date: what is left is real repos on a real phone (OMT-73/74/75) | Owner test |
| F-11 Manga translation | Built behind a flag | Device test MangaTranslate exists; real OCR quality and APK size need a real phone | Owner test |
| F-22 Upcoming filters | Part built | Rest done as OWN-16. The "Extension" filter cannot exist: a calendar entry is not tied to an installed extension (no data links them) | Coded, CI pending (with OWN-16) |
| F-02 login | Built | Code screen reached on Telegram's test servers (44879417411, 44933818039). Telegram closed public test accounts (tdlib/td#3083), so a full sign-in needs a real account | Owner test (OMT-63) |
| UPG-1 | Not run (owner deferred) | TV upgrade and 1.9.0 to 2.0.0 upgrade check | Not in 2.1.0 (owner deferred) |
| SEC-1 | Owner action | Last.fm secret rotation before built-in sign-in is on | Owner action |
| FREEZE-4 | To confirm on a real phone | Home recomposition counter | Owner test |
| BUG-3 | Blocked: owner | Download speed on a real phone | Owner test (OMT-20) |
| PIP-1, S-1, NBUG-8 | Built / guarded | Code and unit guards in place, not reproduced on emulators | Owner test (real phone) |
| C6 "Skeleton or universal loader" | Not run (code read only) | No device test; check in the review APK screenshots | Owner test (look at the review APK) |
| C6 "Extension bar hides on scroll" | Owner feel test | | Owner test (OMT-9) |
| EXT-T1, ST-7, X2-1, X13-6 | Blocked: owner | 30 emulator runs cannot reproduce EXT-T1; ST-7 and X2-1 need real hardware; X13-6 is the owner's decision | Owner test / decision (OMT-26, OMT-12, OMT-23) |

## 3. NEW-BUGS-FEATURES-2026-11-18.md (with CHECKLIST-FEATURES-BUGS.md)

| ID | Cross-check result | Status |
|---|---|---|
| BUG-1, BUG-2, BUG-4, BUG-6, NBUG-2a..e, NBUG-3, NBUG-4a/b/c, NBUG-5, NBUG-7, NBUG-10, NBUG-11, NBUG-14, NBUG-15, NBUG-16 | Verified on emulators with run IDs (CHECKLIST section A, MAJOR part 3) | Done |
| BUG-3 = NBUG-1 | Emulator case passes; real phone needed | Owner test (OMT-20) |
| BUG-5 / TV-4 | TV part verified; cross-source search scope is the owner's call | Owner decision (OMT-21) |
| NBUG-4d | Cannot reproduce; needs a real GPU | Owner test (OMT-16) |
| NBUG-6 / NBUG-9 | Done per the owner's decision: sequential DNS fallback 1.1.1.1 then 9.9.9.9. Per-feature DoH engine deferred (ADR-037) | Done (decided scope); real network OMT-31 |
| NBUG-12 / NBUG-13 | Proxy passes (44593053433, 44593085810, 44600322143); real adult sites are not tested by the agent (no 18+ testing) | Owner test (OMT-32) |
| F-01..F-12, NF-1..NF-3 | Each has a row in MAJOR part 2 with device runs; open parts are listed in section 2 above (F-02 login, F-05, F-11) | See section 2 |
| DL-11 part 2 | Verified (budget met, medians 1.050 / 1.030) | Done |
| N6-2 | Verified on api29 (44112738603) | Done |
| X2-1, ST-7 | Real hardware | Owner test (OMT-12) |

## 4. What the agent still runs before the release

1. CI 45040526941 on 34f2a2ba (OWN-13/14/16 code and unit tests).
2. Device runs: ProfileColdStartDeviceTest (OWN-15), F22CountryDeviceTest + UpcomingDeviceTest (OWN-16),
   DramaPlaybackDeviceTest (OWN-13: drama through the one source), HomeNavigationDeviceTest (OWN-14 motion).
3. Fix any failure, then the release steps in docs/HANDOVER-LIVE.md.

Legal scope stays the same for every item: no piracy sites, no DRM or paywall bypass, no 18+ sources added by Yumeiro.
