# Owner list R21: "Yumeiro — Major Bugs, Improvements & New Features" (items 1-20, then 22 and 23)

Owner request on 2026-11-23, in the R21 session. Item 19 (TeraBox / Diskwala) was added later in the same session. Items 22 and 23 were added on 2026-11-24 with
the rule "first complete all 20, then start these two" (there is no item 21 in the owner's messages).
The references are v1.5.0 (the most successful release; do not go back to its design) and the competitor Hikari
(https://github.com/codegeasse1/hikari.git, cloned at /home/user/workspace/ref-hikari; run `git pull` first).

Every item is on branch **r21-major-list** unless it says otherwise. Every row has a ledger row in
docs/BUGS-AND-FIXES-2.0.0.md with the same ID.

Labels: CI verified / emulator verified / real-device verified / implemented but untested / blocked / not started.
"CI pending" means the code is pushed and waiting for the branch CI run listed in docs/HANDOVER-LIVE.md.

## Status table

| # | Item | ID | Status | Notes |
|---|------|----|--------|-------|
| 1 | Live TV: search by channel name, filter by country, easier country picking, replace the two confusing category rows, redesign inspired by Yumix | LIVE-1, LIVE-1c | emulator verified: Country sheet search "bangla" → Bangladesh 32, pick, channel search "atn" → 2, ATN Bangla plays (probe 45247707490). LIVE-1c: header shows the real count (348 → 7555 after load), search "atn" → 6 channels with the keyboard closed, ATN Bangla UK plays 576p (probe 45257174845, shot live-play) | Probes 45243905968, 45247707490 shots |
| 2 | Settings sub-pages show the Settings page through them ("glass") for about half a second while opening, except Your statistics | NAV-2, NAV-4 | **verified on emulator** | Cause: the Settings in-screen page layers had no background, so the dimmed Settings home showed through during the slide (Your statistics is a separate opaque route). NAV-2 paints each layer's own themed background. Recorded at 10x slow motion: v2.2.0 run 45355877234 shows Settings text through Profiles and Look & feel; branch run 45355890089 is solid. Frames in docs/evidence/nav4. Note: the earlier NAV-3 "delay" work measured frame time; that was not the owner's problem |
| 3 | Extension Sources page redesign (add repo / browse / install clearly separated) | SRC-HUB-1 | emulator verified: Sources opens with Aniyomi / Mihon / Stremio / JavaScript / Home servers, Installed / Browse / Repositories, add-repo card and Discover ranked by stars (probe 45257174845, shot sources-hub) | Installed / Browse / Repositories parts |
| 4 | Manga translation is broken: engine research, downloadable engine, on-screen auto-translate, language packs | TL-2, TL-3 | emulator verified, 5/5 on api36 with real on-device ML Kit (device-check 45256209381): ja→en, overlay on/off, es→en, a line cut by a strip seam read whole ("MUCHAS GRACIAS" → "Thank you very much"), same-language note + packs list. Real-device check is owner-side | Causes found in code: Latin pages always sent as English, source fixed to Japanese, English→English did nothing silently, failures hidden, webtoon seam bubbles split, packs not manageable, offered on TV against ML Kit terms. Engine stays on-device ML Kit (OCR bundled, translation packs downloaded on demand, managed in the sheet). No online engine added: no free keyless legal API fits 200k users |
| 5 | Websites page: "+" with the name read from metadata, search icon, auto-add visited sites, Back / Forward / Reload / Open in Chrome, ad blocker | WEB-5 | emulator verified (probe 45257033081): search icon, +, add-from-list, "Remember sites I visit"; typing an address offers "Go to archive.org", the page opens with Back / Forward / Reload / Block element / Open in Chrome; Block element shows "Tap the part of the page to hide" and turns into Cancel blocking. Recently visited was hidden while the address stayed in the search box; WEB-RECENT-2 fixes that (unit tested, CI 45263833897 green; not re-run on the emulator) | Visited sites go to "Recently visited" (one tap to save), not straight into the saved list |
| 6 | Stream detector misses streams (test site a5x2nt.movielinkbd.at) | SD-1, SD-2 | emulator verified end to end (probes 45261500409, 45265113188): sharing a5x2nt.movielinkbd.at/movie/haiku-2026 → "This is a web page" card with a visible "Find the video" (ERR-CARD-1) → the finder detects "HLS stream · master" → it plays in Yumeiro's player, 1080p, 2:21:10, Hindi stereo (shot sd-play-2). SD-1 found a second bug (the page logo played), fixed in SD-2. Test site only; it is a piracy-type site | Cause: playlist without extension served as image/jpeg inside an iframe; now found by its content |
| 7 | The Telegram page shows the last website shortcut | TG-TAB-1 | emulator verified: the Telegram page shows only its own content, no website shortcut (probe 45253665347, shot tg-tab) | |
| 8A | Telegram: own avatar and name; channel and group avatars and names; "+ Add Public Channel" at the top | TG-8ABC-1 | CI pending | |
| 8B | Telegram: chat counts (videos, photos, audio) | TG-8ABC-1 | CI pending | getChatMessageCount, return_local=false |
| 8C | Telegram: gallery with video previews | TG-8ABC-1 | CI pending | LazyVerticalGrid tg_video_grid |
| 8D | Telegram videos don't play and send the user to the Telegram app (critical) | TG-PLAY-1 | emulator verified for public posts (probe 45227123635); signed-in chats not checked | Baseline v2.1.0 probe 45225547048 reproduced the bug on t.me/telegram/441 |
| 8E | Player "Up next" shows unrelated Live TV channels | UP-NEXT-1 | emulator verified for a public post: t.me/telegram/441 plays to the end (0:06) in Yumeiro's player and no Live TV channels are offered as Up next (probe 45253665347, shot tg-play-end) | |
| 9 | Market Discover: dynamic repository/source filter | MKT-FILTER-1 | emulator verified: Filters sheet shows Sort, Category, Language and a Source filter built from the loaded index ("Checked by Yumeiro · 2"), "Show 1446 results" (probe 45252551715) | Filters sheet: Source + Repository from the loaded index |
| 10 | Yumix "Show videos from": 30 pins, dynamic source filter, search icon, redesign | YF-2 | CI pending | COUNT-1 emulator verified (probe 45342280336): Home "Yumix, 2 sources" matches Videos "2 sources on" on the same phone. Yumix shows Spotlight, Coming up with See all, Fresh from your sources (probes 45335841840, 45345639530). Pins and the filter redesign still need their own device route |
| 11 | Settings > Video Player: customizable player controls | PC-1 | emulator verified (probe 45268021731): Settings › Video player shows the Player controls card (bar count, order / hide per button, Previous and next, Rewind and forward 10 s, Fullscreen). Switching off "Rewind and forward 10 s" (switch read back off) → in the player only Pause is left in the middle; before (probe 45257174845) it showed Rewind 10s / Pause / Forward 10s | |
| 12 | Torrent: open info-hashes and magnets (e.g. BF2063A5E9A69F14716288FCA7C2D6078BDA7369) | TOR-1, TOR-2 | TOR-1 was NOT working on the emulator (shared hash → "Malformed URL", probe 45253665347). TOR-2 emulator verified (probe 45260133429): the hash resolves and the player offers TorrServer / a torrent app; TOR-3 names the right Settings page and the built-in engine. Built-in engine device test 45268091524: the owner's hash opens in about 2 s (5.87 GB MKV) but the emulator cannot decode 10-bit HEVC; the open film Sintel shows its first frame in 6.4 s (api36) and 5.6 s (api33-lowram), budget 15 s, but seeking to the middle took 13.6 s on api36 and did not resume in 30 s on api33-lowram (budget 5 s). So flag b31_builtin_torrent stays off: torrents play only through the user's TorrServer or a torrent app until the seek is fixed. 2026-11-24: B31-SEEK6, TOR-CLOSE-1, B31-PEERS-1 fixed the low-RAM seek (it never resumed; now 1.6-7.6 s) and kept peers connected; 3-shard runs 45306375960 / 45308014362 / 45309673778 still show single-block stalls of 5-10 s in about 1 of 3 seeks, so the flag stays off (B31-STALL-1) Built-in engine (no TorrServer needed): B31-SEEK7 + B31-RESCUE-1 + TOR-IDLE-1 emulator verified on Sintel, 11 of 11 seeks within 5 s and first frames within 15 s on api36 and api33-lowram (runs 45321992083, 45323267366), resume after a 140 s pause 3.9 / 4.2 s (45325875819); B31-ON-1 offers it by default (opt-in notice); the whole in-app path (share magnet → message → Settings notice → turn on → play) emulator verified on api33-lowram over mobile data (probe 45328880256). | Root cause: a pasted hash was checked as a magnet, but the bare hash was sent |
| 13 | Video details metadata (year, duration, rating + votes, country, language, release date, director, writers, companies, cast, trailers); show only the fields that exist | META-1 | CI pending | Cinemeta + Simkl + Wikidata by IMDb id; fixtures in app/src/test/resources/meta |
| 14 | Yumix feed: no duplicate titles across extensions | DEDUP-1 | **emulator verified 2026-11-24** | online/TitleDedupe.kt. Field probe 45345639530 (api36, release build, AnimePahe + MovieBoxProvider + Watch32): YuDedupe "all feed: 2 extensions, 160 cards, 56 same-title cards removed (e.g. Avengers: Doomsday, The Hunger Games: Sunrise on the Reaping, The Odyssey, Spider-Man: Brand New Day, Steps)"; Yumix reads "Mixed from 2 of 4 sources" |
| 15 | A selected extension shows its own sections with See All | EXT-SEC-1 | implemented, CI pending | Check first: the Hue page (OnlineHuePage.kt) shows playlist tabs and an endless feed, with no shelves and no See All |
| 16A | Upcoming: a rotating carousel on Yumix | UP-BANNER-1 | emulator verified: "Coming up" banner rotates (The Simpsons Today, On Cinema In 2 days...) | Probe 45247707490 shots coming-up, coming-up-2 |
| 16B | Upcoming page redesign: 3 filter icon buttons, dynamic country and category | UP-FILTER-1 | implemented, CI pending | |
| 17 | README cleanup | DOC-README-1 | done (docs) | README went from 545 to 245 lines, plus docs/WHATS-NEW.md, SCREENSHOTS.md, COMPARISON.md and FAQ.md |
| 18 | Fix Skip Intro and add Skip Outro (AniSkip / IntroDB) | SKIP-1, SKIP-2 | implemented, unit tested on real API answers; emulator check pending | IntroDB needs the source to give an IMDb id |
| 19a | TeraBox plays only a 30 s preview | TB-PREVIEW-1, TB-PREVIEW-2 | Unit-tested; API verified in a real browser with the test account | Signed in, TeraBox gives the full video (10532 s HLS) only on the site where you signed in; the app now asks that site. Anonymous stays 30 s, which Yumeiro never plays. In-app emulator playback with a signed-in account still to be shown |
| 19b | Diskwala links don't open | — | **closed by owner 2026-11-24 ("don't waste time on Diskwala")**: Yumeiro shows the file name and size and says it opens only in DiskWala's app | See "DiskWala findings" below |
| 20 | Yumeiro Mega Extension (all-in-one) | OWN-13, E1-A/B/D | built earlier as the one built-in "Yumeiro" source (catalog shelves from Yumeiro Cloud/TMDB, Yumeiro Drama, Yumeiro Classics public-domain films, play through installed sources). This row said "not started": that was wrong. Emulator: Yumix shows its Spotlight (probe 45247707490). Open: a legal free-anime part | Only legal sources; no 18+; no copied code |
| 21 | Updater modal messages | UPD-MSG-1 | **implemented (e7947675), CI pending**: plain stage texts from the real download status, "Update completed!" after install, no host name in messages | Update mechanism unchanged |
| 22 | Extension security checks and CAPTCHA: one verification manager for all extensions (e.g. AnimePahe "Quick Security Check" popup keeps coming back) | VERIFY-1 | **implemented, CI + emulator probe pending** (2026-11-24): VerificationCenter + ExtensionCheckWindows + Security checks card (ADR-059). Baseline: probe 45345639530 shows the AnimePahe sheet over a Cloudflare block page | No CAPTCHA solving, no fabricated tokens, no defeating access rules |
| 23 | Ad chains, link shorteners and popups: a dedicated engine in Yumeiro's browser that reaches the real link (e.g. shrinkme.click/alwf74z, srnky.com/0T1EnbeuLf) | ADCHAIN-1 | **implemented, CI + emulator pending** (2026-11-24): link_chain.js + LinkChainPolicy + banner (ADR-060); both chains reached the destination in a real Chrome; the final site shortsync.epdexx.com itself says "Broken Link · Invalid reference" | No captcha solving, timers waited |
| 24 | Multiline search queries and search history: keep and show the whole query, line breaks included | SEARCH-ML-1 | **implemented, CI + emulator pending** (2026-11-24): JSON-array history (old lists still load), rows grow to fit, recent suggestions show every line | Search boxes stay single line (owner) |
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

### 22. No repeated security checks (VERIFY-1, VERIFY-2). Owner rule replaced 2026-11-24

Goal (owner, 2026-11-24, replaces the earlier item 22 text): users must not keep stopping for "Quick security
check", "Just a moment" or CAPTCHA pages while using extensions, streaming or the browser. Measure the result; showing
a modal again and again is not a solution. Study and reuse Hikari, Zangetsu and CloudStream (same ecosystem).

What Yumeiro does (one engine, BrowserCheck + VerificationCenter, for every extension type and the player):
- A real browser passes the site's own check silently: a hidden WebView with the app's WebView user agent runs the
  page until the site sets its pass cookie (cf_clearance, __ddg*). One check per site at a time; other requests wait
  and reuse it (CloudStream CloudflareKiller, Hikari CloudflareSolver).
- The pass is reused everywhere (extension requests, the player, the page browser) with the same user agent, and
  after a restart from the cookie store, until the site itself asks again.
- Check pages are recognised in every form: 403/503 with Cloudflare markup, `cf-mitigated: challenge`, DDoS-Guard,
  the DooPlay "One moment, please" wsidchk page, and check pages served as 200 (VERIFY-2, from Hikari).
- A site whose pass ran out is passed again in the background when its extension opens (VERIFY-2 warm, Hikari's
  CloudflareSolver.warm), at most every 90 s, silently.
- A firewall block ("you have been blocked", 1020) is not a check: one note, no page, held 30 min (VERIFY-1).
- An extension's own check windows are concealed and read (ExtensionCheckWindows, like Hikari's ExtensionVerifyGuard).
- A site that insists on an interactive checkbox shows its own widget once; the pass is then kept as above.

Limits (agent's boundary, explained to the owner 2026-11-24): no CAPTCHA/Turnstile auto-solvers, no bought or forged
tokens, no fingerprint spoofing to pass as a human. Everything above uses a real browser running the site's own page.
- Proof: emulator session with extensions (AnimePahe, a Cloudflare site, the shrinkme chain): count check pages
  shown before and after; logcat BrowserCheck lines.
- Finding 2026-11-24 (field probe 45335841840, api36, AnimePahe + MovieBoxProvider + Watch32 installed): on Yumix the
  window tree holds a 1x1-pixel app window with a WebView showing Cloudflare's "Sorry, you have been blocked · You are
  unable to access animepahe.pw". It is not drawn, but it stays attached (TalkBack can read it, and it keeps a renderer
  alive). Yumeiro's own code adds no window (no addView / WindowManager use); the AnimePahe extension or the CloudStream
  library it calls creates it. VERIFY-1 must own every extension WebView: detach and destroy it after the check, hide it
  from accessibility, and turn a firewall block into one calm message.

### 23. Ad chains, link verifiers and popups (ADCHAIN-1, implemented — see ADR-060)

Owner brief (2026-11-24): link shorteners and ad providers put content behind ads, popups, redirects, countdowns and
repeated Continue / Skip steps (examples shrinkme.click/alwf74z, srnky.com/0T1EnbeuLf). Yumeiro shows no ads and
makes no money from them.

- Research: walk the example chains in a real browser and record every hop (redirects, scripts, timers, forms, what the
  server checks); study open-source blockers and redirect-skippers (uBlock Origin / EasyList filter rules, AdGuard
  rules, and link-bypass userscripts) for ideas only.
- Engine inside Yumeiro's browser: block popups, pop-unders, ad frames and ad redirects with filter lists; recognise
  known shortener families by their page structure, not only by domain; when the destination is already in the page
  or its redirects, go there directly; otherwise take the user through the needed steps automatically without the ad
  pages. Normal sites and logins keep working (allow-list and per-site off switch).
- Limits: nothing that breaks a site's login, payment or account rules; where a server really enforces a wait, the
  engine waits quietly instead of faking it.
- Proof: both example links reach their real target on the emulator, with the hops logged.

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

## Queued by the owner on 2026-11-24 (after the current list)

Order: finish 1-24 first. These two are research-first; no implementation before the plan is written and reviewed.

### Bug/Feature 26 (owner queue no. 24). Zangetsu research and Yumeiro V2 integration — queued, not started
Reference: https://github.com/Spyou/Zangetsu (sequence Hikaei → Zangetsu). Study the real source, don't copy.
1. Novels (new): novel extensions through the existing extension system; browse/search; details and chapters;
   dedicated reader; translation reusing the manga translation path (no second translation system); progress,
   last chapter and position; loading/empty/error states.
2. Source Finding V2 next to V1 (V1 stays): research Zangetsu's scheduling, parallelism, caching, ranking,
   dedupe, timeouts, cancellation, UI feedback; measure V1 vs V2, don't assume; one failing source never blocks.
3. Tracking V2 next to V1: anime/series, manga, novels; automatic progress, resume, matching, dedupe, conflicts.
   Never break existing tracking data; plan migrations first.
4. README and GitHub overview: concise Yumeiro-native presentation (intro, identity, short features, screenshots,
   install/download, navigation). No badge walls, emoji, repeated text.
5. Anime4K V2: presets based on real shaders (e.g. Performance/Balanced/Quality/Custom), only controls that reach
   the pipeline, compatible extra effects, mandatory in-player control (toggle, quick presets, custom, active
   profile shown, persisted), GPU/device fallbacks; current Anime4K stays.
Method per feature: read code, trace paths, deps, UI, perf, limits, compare with Yumeiro, plan. Every change lists
existing/proposed behaviour, files, deps, compatibility, side effects, failures, migration.
Phases A research → B Yumeiro audit → C comparison → D plan (reviewed) → E implementation.
Report (14 parts) marks each finding: verified implementation / observed behaviour / inference / proposal.

### Item 25. Repository humanization, anti-slop and docs separation — queued, not started
1. Look for installed skills (Anti-Slop, Code Quality, Repository Cleanup, Motomation Design); read them fully; if
   absent, say so and run a conservative audit. Never claim a skill that wasn't found.
2. Whole-repo audit: needless wrappers, duplicates, generic comments, dead code, unused deps, inconsistent naming,
   duplicated config. Simplify with a reason per change; keep every feature, extension compatibility, playback,
   downloads, Anime4K, YouTube, IPTV, DNS, settings, updater. No cosmetic rewrites, no history manipulation.
3. Docs to a private repo `yumeiro-docs`: inventory, keep history where reasonable, index, update links; main repo
   keeps README, install, contributing, license/legal, security/support. Check Pages/releases/artifacts don't
   publish private docs. If a private repo can't be created, stop and ask; never a public substitute. No secrets.
4. Dependencies one by one; no major upgrades without reason.
5. Validate (format, lint, tests, builds, links, diffs), report only checks that ran. Stages A-E with final report.

### Item 27 (owner title "Bug/Feature 26 — Player seeking & offline manga"). Queued 2026-11-24, not started
Two owner items carry the number 26; this one is filed as 27 so ledger IDs stay unique.
1. Major: seeking forward sometimes shows an error; reloading the source fixes it. Find the real cause (seek and
   position handling, source lifecycle, HTTP Range/206, HLS/DASH segments, buffering, state sync, stale requests on
   rapid seeks, source limits). Seeking both ways must work; recover from temporary seek failures without
   reloading the whole source; stale requests never override a newer seek; unsupported seeking handled gracefully.
2. Downloaded manga chapters listed in download order: order by chapter metadata (numbers, decimals like 12.5,
   extras, odd titles), never by timestamp or plain alphabetical; same order online and offline; keep ids/metadata.
3. Offline Previous/Next chapter buttons: go to the adjacent downloaded chapter in chapter order, no network,
   clear message when the neighbour isn't downloaded, progress kept, works after restart.
4. Audit the offline reader: indexing, files, metadata, page order, progress, restore, offline detection,
   missing/corrupt files, deletion sync.
5. Side-effect analysis first; no duplicate navigation system. Validate the listed scenarios (seek forward/back,
   rapid seeks, after buffering, near end, each source type, recovery; random download order, decimals/specials,
   offline navigation, missing neighbour, restart, progress) and report which were really tested.

### Item 28. Integrate the external AdChain subsystem additively — queued 2026-11-24, not started
Source: https://github.com/wanxtaexe-ai/legendary-octo-fishstick, `yumeiro-adchain/yumeiro-dropin/` (drop-in holds
AdChainGateHost.kt, YumeiroAdChainBridge.kt, an integration patch, an installer and a README; the other classes
— AdProviderRecognizer, AdChainHttpFastPath, AdFilterPolicy, AdChainDomScripts, AdChainSessionCoordinator,
DestinationValidator, AdChainWebViewRunner — live elsewhere in that repo).
Rules: audit both sides first (signatures, packages, lifecycles, coroutines, overlap with ADCHAIN-1/2, ADR-060/062);
additive only — StreamResolver, AdFilter/BrowserFilters/PopupPolicy, page browser, SafeWebView, NetworkClient
(DNS/DoH, proxy, TLS), downloads, player and extensions unchanged; AdChain failure/timeout/unsupported hands back to
the existing path. HTTP first (redirects, token wrappers, WP Safelink, AdLinkFly /links/go, meta refresh, JS
redirects, countdown pages), WebView only when needed, human checks shown only when needed and confirmed
independently (a click is not success; no forged tokens). Corrections to check: UA parity HTTP/WebView, OkHttp ↔
Chromium cookie sync with attributes, bridge only for shortener/ad-chain failures, bounded redirects/timers, renderer
crash/cancel safety, URL safety (schemes, private networks, look-alike hosts), no first-party/media over-blocking,
reuse NetworkClient. UI: AdChainGateHost in BrowserCheckHost style (FullScreenDialogBars, SpotifyGreen,
TextPrimary/Secondary). Never overwrite same-named files; minimal Gradle changes. Deliver the 9-part report with
tested vs untested clearly separated.
