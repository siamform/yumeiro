# Owner list R21: "Yumeiro — Major Bugs, Improvements & New Features" (19 items)

Owner request on 2026-11-23, in the R21 session. Item 19 (TeraBox / Diskwala) was added later in the same session.
The references are v1.5.0 (the most successful release; do not go back to its design) and the competitor Hikari
(https://github.com/codegeasse1/hikari.git, cloned at /home/user/workspace/ref-hikari; run `git pull` first).

Every item is on branch **r21-major-list** unless it says otherwise. Every row has a ledger row in
docs/BUGS-AND-FIXES-2.0.0.md with the same ID.

Labels: CI verified / emulator verified / real-device verified / implemented but untested / blocked / not started.
"CI pending" means the code is pushed and waiting for the branch CI run listed in docs/HANDOVER-LIVE.md.

## Status table

| # | Item | ID | Status | Notes |
|---|------|----|--------|-------|
| 1 | Live TV: search by channel name, filter by country, easier country picking, replace the two confusing category rows, redesign inspired by Yumix | LIVE-1 | emulator verified (header, genre row, Country sheet); search + play probe 45247707490 | Probe 45243905968 shots |
| 2 | Settings sub-pages leave the previous page visible underneath for about 2 s | NAV-2 | CI pending | Opaque layers, and the sliding page gets its own zIndex. screenrecord on the emulator gets only about 10 frames, so this can only be proven by the Robolectric test |
| 3 | Extension Sources page redesign (add repo / browse / install clearly separated) | — | not started | Plan below |
| 4 | Manga translation is broken: engine research, downloadable engine, on-screen auto-translate, language packs | — | not started | Plan below (largest item) |
| 5 | Websites page: "+" with the name read from metadata, search icon, auto-add visited sites, Back / Forward / Reload / Open in Chrome, ad blocker | — | not started | Plan below |
| 6 | Stream detector misses streams (test site a5x2nt.movielinkbd.at) | — | not started | Plan below |
| 7 | The Telegram page shows the last website shortcut | TG-TAB-1 | CI pending | |
| 8A | Telegram: own avatar and name; channel and group avatars and names; "+ Add Public Channel" at the top | TG-8ABC-1 | CI pending | |
| 8B | Telegram: chat counts (videos, photos, audio) | TG-8ABC-1 | CI pending | getChatMessageCount, return_local=false |
| 8C | Telegram: gallery with video previews | TG-8ABC-1 | CI pending | LazyVerticalGrid tg_video_grid |
| 8D | Telegram videos don't play and send the user to the Telegram app (critical) | TG-PLAY-1 | emulator verified for public posts (probe 45227123635); signed-in chats not checked | Baseline v2.1.0 probe 45225547048 reproduced the bug on t.me/telegram/441 |
| 8E | Player "Up next" shows unrelated Live TV channels | UP-NEXT-1 | CI pending | |
| 9 | Market Discover: dynamic repository/source filter | MKT-FILTER-1 | implemented, unit tested; emulator check pending | Filters sheet: Source + Repository from the loaded index |
| 10 | Yumix "Show videos from": 30 pins, dynamic source filter, search icon, redesign | YF-2 | CI pending | |
| 11 | Settings > Video Player: customizable player controls | PC-1 | CI pending | |
| 12 | Torrent: open info-hashes and magnets (e.g. BF2063A5E9A69F14716288FCA7C2D6078BDA7369) | TOR-1 | CI pending | Root cause: a pasted hash was checked as a magnet, but the bare hash was sent |
| 13 | Video details metadata (year, duration, rating + votes, country, language, release date, director, writers, companies, cast, trailers); show only the fields that exist | META-1 | CI pending | Cinemeta + Simkl + Wikidata by IMDb id; fixtures in app/src/test/resources/meta |
| 14 | Yumix feed: no duplicate titles across extensions | DEDUP-1 | CI pending | online/TitleDedupe.kt |
| 15 | A selected extension shows its own sections with See All | EXT-SEC-1 | implemented, CI pending | Check first: the Hue page (OnlineHuePage.kt) shows playlist tabs and an endless feed, with no shelves and no See All |
| 16A | Upcoming: a rotating carousel on Yumix | UP-BANNER-1 | implemented, CI pending | |
| 16B | Upcoming page redesign: 3 filter icon buttons, dynamic country and category | UP-FILTER-1 | implemented, CI pending | |
| 17 | README cleanup | DOC-README-1 | done (docs) | README went from 545 to 245 lines, plus docs/WHATS-NEW.md, SCREENSHOTS.md, COMPARISON.md and FAQ.md |
| 18 | Fix Skip Intro and add Skip Outro (AniSkip / IntroDB) | — | not started | |
| 19a | TeraBox plays only a 30 s preview | TB-PREVIEW-1 | CI pending | The page finder played the page player's preview capture. Now: never plays a preview, resolves the share via the API, opens sign-in in the same browser and resumes by itself after sign-in. Full playback needs a TeraBox account (owner-side check) |
| 19b | Diskwala links don't open | — | **blocked: owner decision** | See "DiskWala findings" below |
| 20 | Yumeiro Mega Extension (all-in-one) | — | not started | Only legal sources; no 18+; no copied code |
| web | yumeiro-web: changelog and download text say v2.2.0 | — | done, merged | yumeiro-web PR #8. Re-check www.yumeiro.click/changelog |
| PR44 | What is inside PR 44 | — | answered | Docs only: the HANDOVER-LIVE rewrite and the REL-2.2-CI ledger row. Its commit 0265a031 is now on r21-major-list, so close PR 44 as superseded when the r21 PR opens |

## How to verify (no local JDK or Android SDK on the agent box)

- Kotlin compiles only in CI. Dispatch `gh workflow run ci.yml --ref r21-major-list -R siamform/yumeiro` (about 40 min).
  A newer dispatch on the same ref cancels the older run.
- Emulator check: run `field-probe.yml` with `-f new_tag=branch` (this builds the branch; an empty new_tag silently
  installs v2.1.0) plus `-f api=36 -f net=full -f steps='skip;wait 100;...'`. The steps are skip, tour, wait N, tap T,
  find T, rec, recpull, back, home, relaunch, shot N, gfx, swipeback, edgeback and send T (send shares a link to the app).
  Download the result with `gh run download <id> -n field-probe -D /tmp/ob`, then read timeline.txt and look at the
  shots.
- Never claim "verified" from CI alone for UI behaviour. Use the labels above.

## Plans for the items that are not started

### 1. Live TV
- Code: ui/screens/stream/LiveTv*.kt and the Live TV parts of StreamScreen.kt. Data: the streams branch
  (playlists/yumeiro_live.m3u).
- Search: a name filter over the loaded channel list, normalised (case, accents, "HD" / "FHD" suffixes), with the
  search behind an icon in the top bar (the same pattern as YF-2).
- Country: build the country list from the playlist's tvg-country / group-title values. Do not hardcode it. Show a
  country sheet with a search field, flag emoji from the ISO code, and the channel count. Put recent countries first.
- Replace the two horizontal category rows with one chip row (categories present in the data) plus the country
  button. Run the ui-ux-pro-max search first:
  `python3 /home/user/workspace/agent-skills/ui-ux-pro-max-skill/.claude/skills/ui-ux-pro-max/scripts/search.py "tv channel browser filter" --domain ux`.

### 3. Extension Sources page
- Three clear blocks: "Your repositories" (add with a URL field and paste, remove), "Browse" (the market list) and
  "Installed". Keep every current action. Read ExtensionManager.kt and the sources screen first.

### 4. Manga translation
- First reproduce why it is broken (find the current translator code with `rg -n -i "translat" app/src/main`).
- Research done so far: none in this session. Candidates to evaluate: Google ML Kit on-device translation plus text
  recognition (Apache-2.0, downloadable language models of about 30 MB each, offline after download); manga-image-translator
  (server side, too heavy for the phone); Tesseract (weak on manga fonts); ML Kit Japanese / Chinese / Korean text
  recognisers. Check Mihon / Tachiyomi forks and Hikari for existing approaches.
- Watch the DEX / APK budget (CI checks it). ML Kit translate adds a dependency, so record the reason in an ADR.

### 5. Websites page
- Code: ui/screens/sites/WebSitesScreen.kt, online/web/SavedSites.kt (it already has normalize()).
- "+": a URL field that reads <title>, og:site_name and the favicon (fetch on IO).
- Auto-add visited sites: record the hosts opened in the in-app browser into a "Recent" group, with an off switch.
- Browser bar: Back / Forward / Reload / Open in Chrome (Intent ACTION_VIEW, the user's choice only).
- Ad blocker: read how Hikari does it (`rg -n -i "adblock|blocklist" /home/user/workspace/ref-hikari/app/src`). The
  options are EasyList / uBlock lists through a host-and-path matcher in shouldInterceptRequest (for example the
  Brave adblock-rust bindings, which are heavy, or a small domain-list matcher). Prefer a small matcher plus a list
  downloaded from the data branch, so it adds no large dependency.

### 6. Stream detector
- Test site: a5x2nt.movielinkbd.at. Use a field probe with `send https://a5x2nt.movielinkbd.at/...` and a timeline. Then
  read stream/WebPageExtractor.kt and stream/StreamResolver.kt. Likely gaps: iframes on other origins, blob: / MSE
  players (hook XHR / fetch for .m3u8 / .mpd / .mp4), and links built from packed JavaScript.

### 9. Market Discover filter
- A chip or menu of the repositories present in the loaded market data (ExtensionMarket.kt). Never a fixed list.

### 15. Extension sections with See All
- When the user picks one extension on Yumix, show its home rows as shelves with "See all" (onOpenRow(row) already
  exists for rows with pageData), above or instead of the endless Hue feed. Keep the Hue feed reachable.

### 16. Upcoming
- A: a carousel of upcoming titles (with backdrops) at the top of Yumix; it rotates every 5 s, pauses on touch, and
  respects reduced motion.
- B: three icon buttons (Country, Category, Date) that open sheets. Countries and categories come from the loaded
  Upcoming data.

### 18. Skip Intro / Skip Outro
- Find the current skip code (`rg -n -i "aniskip|skip intro|introdb" app/src/main`), reproduce the failure, then add
  outro ("ed" type in AniSkip v2: `https://api.aniskip.com/v2/skip-times/{malId}/{ep}?types=op&types=ed&episodeLength=0`).
  Check that IntroDB still exists before relying on it.

### 19. TeraBox and Diskwala (added by the owner later in R21)
What exists already (read it before changing anything):
- app/src/main/java/dev/asfakul/yumeiro/stream/TeraBoxApi.kt (B-29, OWN-10). The doc comment records the measured
  cause: anonymous share/streaming returns 3 × 10 s segments plus ENDLIST, which is TeraBox's no-login 30 s preview.
  The full file (dlink) or the full HLS needs the TeraBox `ndus` sign-in cookie. Yumeiro reuses the cookie from a
  sign-in inside its own web view and returns Result.NeedsSignIn instead of playing a preview.
- app/src/main/java/dev/asfakul/yumeiro/stream/DiskWalaApi.kt (56 lines), LinkHosts.kt, StreamResolver.kt,
  CloudRecapture.kt and the StreamScreen.kt routing.
- Hikari: app/src/main/java/com/hikari/app/data/NetworkStream.kt, lines 143–560. It lists the box hosts, uses a PC
  TeraBox user agent ("terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox"), and calls shorturlinfo, then
  list, then dlink. For Diskwala it treats the share as a web page (MDISK_HOSTS, "MDisk-class download pages with a
  known API shape", stage 3b).

Steps:
1. Reproduce with a field probe (`send <terabox share>` and `send <diskwala link>`, with shots and a timeline). The
   owner must supply test links if none are in the docs (search docs/ and the ledger for "terabox" / "diskwala" links
   first).
2. TeraBox: find out whether the 30 s preview still plays somewhere. That would break the "never play a preview" rule:
   look for a path that skips TeraBoxApi, for example the WebView finder catching the preview m3u8. Find out whether
   the sign-in flow is reachable and kept, and whether the dlink plays with the right headers (Cookie ndus, Referer,
   User-Agent). Compare the API order with Hikari's.
3. Diskwala: record the real redirect chain with curl -L -v (on the agent box). Find the API the page calls (look in
   the page's JS bundle), and resolve it to a direct video URL. Play it in Yumeiro's player and open the page itself
   only in Yumeiro's browser. Never send the user to the Diskwala app or an external browser for normal playback.
4. Errors: keep the original link, and show a clear message (needs sign-in / link expired / file removed / not a
   video) with "Open in Yumeiro browser" and "Copy link".
5. Tests: save real API answers as fixtures (strip cookies and tokens) and write a parse test, plus an emulator probe.

Owner text for item 19 (verbatim summary): TeraBox links return only a 30-second preview; fix resolution and
streaming so supported videos play completely. Diskwala links don't open at all; investigate the link structure,
redirections and video extraction, and fix resolution and playback. Inspect how Hikari handles both and research
open-source projects, but don't just copy Hikari. Links must open in Yumeiro's built-in browser and play in Yumeiro's
built-in player. No redirect to the TeraBox / Diskwala apps or to external browsers for normal playback. Handle
redirects, video URLs and streaming formats. Preserve the original link and give clear feedback on extraction errors.

## DiskWala findings (R21, 2026-11-23, measured)
- The website (diskwala.com/app/<id>) is a "Get the app" page. Its whole JS bundle (main.d3b09a1c.js, 630 KB) calls
  only `file/temp_info`, which answers name / size / type and **no video address** (checked live with curl). So no
  website flow exists to capture, and Hikari's WebView fetch/XHR hook (NetworkStream.kt mdisk()) has nothing to catch
  on that page.
- DiskWala's own Android app gets the file behind an app-integrity check. Faking that is out (owner: no bypassing).
- The only working open-source route found (github.com/XKILLER2006Y/TeraBox-Video-Downloader, diskwalaDL) goes through a
  **third-party Telegram Mini App**, @sky577bot "DiskWala Video Downloader" (miniapp.diskwala.net, api2.diskwala.net).
  It is ad-funded (adsgram, richpartners, tgads scripts on the page) and authenticated with the user's Telegram
  initData. Its API answers are AES-GCM encrypted with a key taken from the Mini App bundle.
- Possible in-app design (needs owner approval, because it sends the user's Telegram identity to a third-party bot):
  with the user's TDLib sign-in, get the Mini App URL (TDLib getWebAppLinkUrl / searchWebApp for @sky577bot "open"),
  open it in Yumeiro's browser with the link as start_param, and capture the video it plays (the page decrypts its
  own answers, so no copied key is needed). The original link is kept, the user sees a clear consent line, and the
  route is off by default.
- Until the owner decides: Yumeiro keeps the honest message (file name + size from temp_info). Remove the hand-off to
  the DiskWala app only when the replacement exists, so the link doesn't become a dead end.
