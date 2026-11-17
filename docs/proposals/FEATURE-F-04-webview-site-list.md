# F-04 WebView site list (proposal only; gated)

Status: **proposal, not built.** Part 8 gate not passed (4.4). The owner approves before any code.

## 1. Current code (read 2026-11-18, head a3b40c3)

Yumeiro already has a Web sites tab (`Screen.WebSites`):

- `online/web/SavedSites.kt` (101 lines): user-saved sites in SharedPreferences `yu_sites` (JSON list, max 200,
  atomic `apply` of the whole value); `normalize` (https added, no whitespace, host must have a dot),
  `defaultName`, `add` (de-dupe by host + path, newest first), `rename`, `remove`. Comment: "nothing is
  pre-installed". Backed up: `backup/PrefSections.kt:27`.
- `ui/screens/sites/WebSitesScreen.kt` (225 lines): add field, help dialog, list rows with a letter avatar,
  long-press / menu Rename and Remove, empty state. Opens the site in the hardened page browser
  (`SafeWebView.harden`, `AdShield`, `HeadlessCapture`, 22 s timeout) and plays the found video in Yumeiro.
- Group 13 N2 swept this tab (all rows OK).

Gaps: no search/filter, no ordering or pinning, no per-site settings, no import/export of the list (only the
whole-app backup), letter avatars only, D-pad path not specifically tested for the list.

## 2. Competitor (ideas and contracts only; Hikari is GPL-3.0, no code copied)

Hikari `codegeasse1/hikari` at commit 8aa1652b2af151aca85be316b926cbabee742e63:

- `app/src/main/java/com/hikari/app/ui/screens/WebSitesScreen.kt` (508 lines): search box filters by name or
  URL; grid of cards; **live preview thumbnails**: each site rendered in a hidden 1080×720 WebView with
  JavaScript on, screenshot after 4.5 s, PNG per SHA-1 of the URL in `filesDir/site-previews`, queued one at a
  time with a timeout; "Retake" action.
- `app/src/main/java/com/hikari/app/web/WebViewActivity.kt` (2,432 lines): per-host pop-up allow list
  (`webview_popup_allow`), redirect allow / block-forever prompts.
- `app/src/main/java/com/hikari/app/web/UserscriptManager.kt`: user scripts matched by URL glob.

Weaknesses for Yumeiro's users: the hidden preview WebView runs every saved site's JavaScript (ads,
trackers) in the background, costs 50–100 MB per render on low-RAM phones and writes ~1 MB PNGs; user
scripts widen the attack surface. Neither is acceptable at 200k users on 2 GB phones.

## 3. Design (Yumeiro target: more stable and lighter than Hikari)

- **Search/filter** in the list (name, host), shown when ≥ 6 sites.
- **Pin and reorder**: pinned sites first; long-press Move up/down (D-pad friendly, no drag needed on TV).
- **Per-site settings** (stored in the same `Site` record, new optional fields with defaults, so old JSON
  decodes unchanged): desktop mode, allow pop-ups for this site, ad shield strength. The shield stays on by
  default.
- **Icon**: the site's favicon fetched once through the app's OkHttp client (≤ 32 KB, cached in `cache/`),
  letter avatar as fallback. No hidden WebView, no JavaScript run off-screen.
- **Share / import list**: export the list as a small JSON file or text of links through the share sheet; import
  merges (de-dupe by host + path), never replaces. Still nothing pre-installed (owner policy on terms).
- **TV**: list as a focusable column, focus restored after returning from a site.
- Feature flag `web_sites_v2` with kill switch; migration is additive only.

## 4. Action–reaction

| Action / event | Reaction |
|---|---|
| Type in search | List filters as you type; Back clears search first |
| Pin a site | Moves to the top, survives restart and backup/restore |
| Move up/down (TV remote) | Order changes, focus stays on the moved row |
| Toggle desktop mode | Next open uses desktop user agent for that site only |
| Favicon fetch fails / offline | Letter avatar, retried on next open at most once a day |
| Import a list with duplicates or bad links | Duplicates merged, bad lines skipped with a count |
| Import > 200 sites | First 200 kept, message says how many were skipped |
| Kill and restore mid-edit | List as last saved (atomic write) |
| Rotate | Search text and scroll kept |
| Old backup restored | Old records load; new fields take defaults |

## 5. Pros and cons

- Favicon instead of live preview: + no background JavaScript, tiny memory and disk; − less visual than
  Hikari's screenshots.
- Per-site settings in the `Site` record: + single source of truth, backed up; − record grows (bounded).
- Import/export: + moving between devices without full backup; − users may share lists of sites with unclear
  terms (Yumeiro ships none, the user chooses).
- No user scripts: + security; − power users lose a Hikari capability (owner call).

## 6. Tests (when approved)

Unit (pure logic, stated as unit only): `SavedSites` migration from old JSON, merge/import, pin order.
Device: Web sites list search, pin, reorder by D-pad on tv-api34/tv-api24, restore after kill; fail-before
on the current build (no search/pin), pass-after on the change.

## 7. Owner decision

Approve scope (search, pin, per-site settings, favicon, import/export), and whether user scripts are ever
wanted (recommendation: no).
