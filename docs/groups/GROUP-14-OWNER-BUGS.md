# Group 14: owner bug list of 2026-11-18 (NBUG-11 … NBUG-16)

Why this group exists: the owner reported six bugs and one redesign on 2026-11-18, during the G9 TV
regression. Owner order of 2026-11-19: close G9, then this list with the full method (research,
analysis, side effects, reverse engineering of competitors, architecture and system design, tests that
fail on the old code, fixes, pass-after, regression), then G10 and G11.

Rules: the same as every group (master prompt §13a, ten steps). NBUG-15 (a)–(c) are a redesign: a
ui-ux-pro-max proposal goes to the owner first and nothing is built before the owner approves it;
NBUG-15 (d) is a logic bug and follows the normal method.

Order (user risk first): NBUG-11 (18+ shown with 18+ off: a privacy leak) → NBUG-12/13 (adult
streams: "Access denied", VPN needed; one network investigation) → NBUG-16 (updates never run, false
"update" flags) → NBUG-14 (IPTV logos) → NBUG-15 (d) → NBUG-15 (a)–(c) proposal.

## NBUG-11: 18+ off, adult titles still in Home → "Most played"

### Files and lines read

- `data/PlayCounts.kt` (all): the play-count store behind Home's "Most played" videos.
- `ui/screens/home/HomeScreen.kt` 186–193 (`Played`), 290–330 (state, the Most played filter),
  624–660 (the row), 1189–1200 (`SquareCard`, test tags).
- `online/AdultContent.kt` (all), `online/OnlineSettings.kt` 20–72 (the two switches).
- `online/OnlineHistory.kt` 140–200 (Continue watching: `adult` stored per entry, `forFeed`, `clearAdult`).
- `online/OnlineSession.kt` 200–220 (where a play is counted).
- `online/ExtensionManager.kt` 70–90, 205–255 (`ready`, `generation`, `providers()`, `provider()`,
  `extensionOf()`).
- `ui/screens/home/HomeOnline.kt` 70–110, `ui/screens/online/OnlineHomeScreen.kt` 185–200, 260–270,
  `OnlineHomePicks.kt` 45–70, `OnlineSearchScreen.kt` 100–145, `ui/screens/library/VideoLibraryScreen.kt`
  115–120, `MTPlayerApp.kt` 660–666, `online/OnlineRepository.kt` 500–515: every other place that
  filters 18+, to compare.

### Data flow

Play an online title → `OnlineSession` 214 `PlayCounts.record(kind = ONLINE, id = "api|url", …)`;
no type, no 18+ mark → prefs `play_counts` → Home `PlayCounts.entries` → `played` (HomeScreen 321)
drops an ONLINE entry only if `AdultContent.isAdultApi(entry.api)` is true right now →
`isAdultApi` asks `ExtensionManager.provider(api)`, which is null until the extensions have loaded
(`ready = false`), and always null for an extension that is turned off or removed → false.

### Action → reaction

| Action | Expected | Code | Result |
|---|---|---|---|
| 18+ off, cold start, open Home | adult titles absent | `played` is computed once at first composition, before `ExtensionManager.ready`; its `remember` keys (`mostPlayed, videoPlays, recentVideos, adultOn, adultPrivate`) do not include the extension state, so it is never computed again when the extensions load | **shown: NB11-1** |
| 18+ off, the adult extension turned off or removed | absent | `provider(api)` null → `isAdultApi` false | **shown: NB11-1** |
| 18+ off, adult title from a mixed extension (Movie + NSFW types, item `TvType.NSFW`) | absent | PlayCounts keeps no type or mark; `isAdultApi` is false for a mixed extension | **shown: NB11-2** |
| Same title in Continue watching | absent | `OnlineHistory.Entry.adult` is stored at record time (`isAdult(api, type)`), `forFeed` filters it | OK (the model to follow) |
| 18+ on and not private | shown | filter off | OK |
| Toggling 18+ while Home is open | row updates | `adultOn` is a key | OK (why the owner may see it come and go) |
| Feed rows, Yumix home, picks | adult extensions absent | `remember(engine.generation, engine.ready, …)` | OK (keys include the extension state) |

### Competitors (reverse engineering, read 2026-11-19)

- CloudStream: 18+ extensions are hidden by the "Preferred media → NSFW" choice and NSFW items in
  mixed extensions by "Enable NSFW on supported extensions" ([recloudstream/cloudstream#1149](https://github.com/recloudstream/cloudstream/issues/1149));
  its home skips pinned NSFW-only providers when NSFW is not chosen
  ([HomeFragment.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt)).
  Its filter is per provider and per type, decided from loaded providers.
- Porn4fans, the owner's example, is an NSFW-only extension (CXXX list,
  [admknight/CloudstreamExtensions](https://github.com/admknight/CloudstreamExtensions/blob/master/README.md)),
  so `isAdultApi` is true for it once it is loaded: the owner's case is NB11-1 (timing), not NB11-2.
- Lesson: decide at record time and store the answer (as Yumeiro's own Continue watching does);
  a later check against loaded extensions is only a fallback.

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB11-1 | 18+ titles in Most played after a cold start, and for an extension that is off or removed | Most played asked the live extension list, once, before it loaded; unknown counted as "not 18+" | High (privacy: shown to anyone who opens Home) | Open |
| NB11-2 | 18+ titles of a mixed extension in Most played | PlayCounts stores no 18+ mark or type | High | Open |
| NB11-OB1 | Observation, other surface: with 18+ on but private, an NSFW-typed title of a mixed extension can still appear in that extension's feed rows (`OnlineRepository.toItem` 508 drops NSFW items only when 18+ is off; the private rule hides whole 18+ extensions) | the private rule is per extension, not per title | Medium (needs the owner's reading of "private") | Queued (UNVERIFIED-QUEUE NB11-OB1); not part of the owner's report, not built |

### Design (ADR-023)

See docs/SYSTEM-DESIGN.md ADR-023.

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB11-1, NB11-2 | pending (`Nbug11MostPlayedAdultDeviceTest` case a; case b is the 18+-on control and must pass on both) | pending | emulator, TV emulator |

## NBUG-12 and NBUG-13: adult streams fail with "Access denied"; they need a VPN

One investigation (owner order): both are "an adult stream does not play", and NBUG-13's cause can
only be told apart once NBUG-12's is known.

### Files and lines read

- `online/AccessLadder.kt` (all): the header ladder behind "Retrying like a browser / as a media
  player / as VLC / like the host's own web player" — the cycle the owner saw.
- `online/OnlineSession.kt` 300–425 (`playCurrent`, `failover`, the fresh `loadLinks`, the headless
  capture, `pageFor`).
- `online/web/HeadlessCapture.kt` (all), `ui/screens/stream/PageStreamFinderDialog.kt` 110–245
  (`PageStreamCapture`: which requests count as a stream).
- `online/OnlineRepository.kt` 365–400 (`load`: `MovieLoadResponse.dataUrl` → `playData`).
- `stream/StreamMediaFactory.kt` 245–265 (player HTTP: `NetworkClient` OkHttp), `network/SmartDns.kt`
  1–60, `network/NetworkClient.kt`, `online/OnlineRuntime.kt` 50–56 (extension `app` HTTP uses the same
  OkHttp client), `res/xml/network_security_config.xml`.
- `stream/WebPageExtractor.kt` (all), `stream/StreamSniffer.kt` (extension tests), `stream/StreamResolver.kt`
  (`resolve`, candidate probing, at most 6).
- yt-dlp's KVS support (`_kvs_get_real_url`, `_kvs_get_license_token`,
  [generic.py](https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/extractor/generic.py), Unlicense):
  how KVS pages (kt_player) store and scramble their file links.
- The owner's extension: [phisher98/CXXX Porn4fans.kt](https://raw.githubusercontent.com/phisher98/CXXX/master/Porn4fans/src/main/kotlin/com/Porn4fans/Porn4fans.kt).

### Reverse engineering the site (probe from the sandbox, 2026-11-18 19:46 UTC, labelled "network probe")

| Request | Result |
|---|---|
| ld+json `contentUrl` (what the extension plays): `…/get_file/5/<hash>/12000/12934/12934_720p.mp4/`, with and without Referer | **403** `text/html` (Cloudflare) |
| Page player data (kt_player 2.7.5 flashvars) `video_url` / `video_alt_url`, which carry `?v-acctoken=…` | **206** `video/mp4` (redirect to `st2.…/remote_control.php`), with or without Referer, with a browser User-Agent |
| Same, no User-Agent header at all | 403 (any User-Agent works: Chrome, `okhttp/5.1`, Yumeiro's; rechecked 2026-11-19, the 2026-11-18 token still valid) |

So the extension is out of date: the site moved to tokenised links and the `contentUrl` it reads is
refused for everyone. No header variant can fix a refused URL, which is why the ladder cycles and
fails. The site's own page still plays.

### Data flow (old code)

Tap → `load()` → `playData = contentUrl` → `loadLinks()` → one link, `url = contentUrl`,
`referer = mainUrl` (bare origin) → player 403 → AccessLadder: 7 header variants, each 403 → fresh
`loadLinks()` → same URL, 403 → last resort `pageFor(s)`: (1) a referer with a path — none, the referer
is a bare origin; (2) **`s.data` — the refused `.mp4` itself**; (3) the title page — never reached →
`HeadlessCapture` loads the `.mp4` in a WebView, gets the same 403 page, finds no player → "Every
source refused playback, even from the site's own page."

### Findings register

| ID | Finding | Root cause | Severity | Status |
|---|---|---|---|---|
| NB12-1 | A title whose data is a direct media URL never reaches the site-page fallback | `pageFor` took the episode data as "the page" even when it was the refused stream file | High (every "direct file" source with expiring or moved links; the owner's case) | Open |
| NB12-3 | Even on the title page, Yumeiro cannot find a KVS (kt_player) stream | (a) KVS links end in `.mp4/` (trailing slash), so the page extractor's and the sniffer's extension tests miss them; (b) the links sit in the `flashvars` script object, not in `<video>`; (c) many KVS sites scramble them (`function/0/…`, de-scrambled with the page's `license_code`), which Yumeiro does not support. Live check: headless WebView capture of the real title page found no stream on either phone (diagnostic 44389256785, throwaway branch, deleted) (likely reason, not verified: kt_player requests the file only after a tap on its play button). | High (KVS is a common adult-site engine) | Open |
| NB12-4 | After "fresh loadLinks" returns the same refused link, the player never retries and the title sits on the error forever (the last resort is never reached) | `playCurrent` builds the same video id for the same link, so `IncomingMedia.open` publishes an equal `VideoItem`; `_pending` is a StateFlow and drops equal values, so no player reload, no second `onFatalError`, no `failover`. Found in fail-before 44394971017 (api36): phase PLAYING with the 403 for 240 s, the page never requested | High (every source whose links do not change on refresh) | Open |
| NB12-5 | After one episode needed the refresh and the page step, the next episode skips both and stays on the player error | `playEpisode` copies the state and keeps `refreshed` and `captured` from the previous episode (code read while fixing NB12-4) | Medium (series from sources with refused links) | Open |
| NB12-2 | The extension's link is dead (site moved to `v-acctoken` links) | Third-party extension out of date | — (not ours) | Report to the extension author (owner, OMT) |
| NB13-1 | Adult hosts need a VPN on the owner's network | Bangladesh blocks adult sites at the gateway (BTRC; DNS tampering, IP blocking or gateway filtering, [OONI 2025](https://ooni.org/post/2025-bangladesh-report/); 22,000+ adult and gambling sites through the CTDR system, [bdnews24](https://bangla.bdnews24.com/tech/article1936753.bdnews)) | — | See below |

### NBUG-13 analysis

- What the app already does: every OkHttp path (extensions through `OnlineRuntime`, the player through
  `StreamMediaFactory`, images) resolves through `SmartDns`; with "auto" on, a host the network blocks
  by DNS (no answer, sinkhole address, reset after a system lookup) is resolved over DNS-over-HTTPS
  (NBUG-6, NBUG-9). So a **DNS-only** block is already bypassed in those paths.
- Gaps: (a) WebView paths (headless capture, the in-app browser, the host web player) use the system
  resolver: Android WebView has no DNS hook; (b) **IP or SNI blocking cannot be bypassed by DNS at
  all** — only a tunnel or proxy outside the country can, which is what the owner's VPN does.
- NBUG-12 also looked like "needs a VPN": the 403 is the same everywhere, so a VPN alone would not
  have fixed Porn4fans. After NB12-1 the owner must retest on the home network (OMT) to tell which
  remains.
- Design options (ADR-024, owner decision, nothing built without approval because it is a new network
  feature): (1) a user-set HTTP/SOCKS5 proxy in Settings → Network applied to OkHttp and, through
  `androidx.webkit.ProxyController`, to WebView; (2) WebView DNS through a local proxy that resolves with
  `SmartDns` (fixes DNS-only blocks in WebView, not IP/SNI); (3) leave it to a system VPN / Private DNS.

### Design (ADR-024)

Last resort for a refused title, in order: (1) the capture page is the title page whenever the episode
data is itself a media file (`CapturePage.pick`, NB12-1); (2) read the page HTML over OkHttp with the
site's referer and the CloudStream User-Agent and let `StreamResolver` find the stream, with KVS
`flashvars` support (best quality first, `realUrl` de-scrambling, links needing login skipped)
(NB12-3); (3) only then the headless WebView capture. The id suffix stays `:web` (resume and history
unchanged). No new dependency; the KVS code is a port of yt-dlp's (Unlicense, compatible with GPL-3).

### Verification

| Item | Fail-before (old code) | Pass-after | Label |
|---|---|---|---|
| NB12-1, NB12-3, NB12-4, NB12-5 | pending (`Nbug12RefusedMediaPageDeviceTest`: local KVS site; the extension's link 403) | pending | emulator (local server), CI unit (`CapturePageTest`, `KvsPlayerTest`) |
| NB12-2 | — | — | OMT-32 (owner retest; tell the extension author) |
| NB13-1 | owner network only | — | OMT-33 (decision + retest) |
