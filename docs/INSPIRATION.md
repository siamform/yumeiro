# Inspiration and resources

Open-source projects Yumeiro learns from. We reuse code only where the license allows it and credit it in `NOTICE`.

| Project | What we took or learned | Link |
|---|---|---|
| CloudStream | Plugin API embedded as the common source contract; `.cs3` repos | https://github.com/recloudstream/cloudstream |
| Aniyomi | Anime source API (Apache-2.0), tracker flows, backup and app-lock ideas | https://github.com/aniyomiorg/aniyomi |
| Mihon | Manga source API, extension repo format (`index.min.json`), reader UX | https://github.com/mihonapp/mihon |
| Keiyoushi | Largest community Mihon extension repo, used for testing | https://github.com/keiyoushi |
| Mangayomi | JavaScript source format and runtime shims (Apache-2.0) | https://github.com/kodjodevf/mangayomi |
| quickjs-kt | QuickJS engine for Android | https://github.com/dokar3/quickjs-kt |
| Stremio add-on protocol | Manifest, catalog, meta, stream, subtitles resources | https://stremio.github.io/stremio-addon-sdk/protocol.html |
| Stremio add-on SDK | Reference for static add-ons | https://github.com/Stremio/stremio-addon-sdk |
| Kotatsu | Manga reader ideas, natural sort | https://github.com/KotatsuApp/Kotatsu |
| Dantotsu | AniList-first anime UI | https://github.com/albertxiao/Dantotsu |
| Kodi | Add-on repositories and home-server browsing | https://kodi.wiki/view/Add-on_repositories |
| Jellyfin | Server API for Jellyfin and Emby | https://jellyfin.org |
| Seal, YTDLnis | Download UX | https://github.com/JunkFood02/Seal, https://github.com/deniscerri/YTDLnis |
| NewPipe Extractor | YouTube without login | https://github.com/TeamNewPipe/NewPipeExtractor |
| Kitsu API | Tracking | https://kitsu.app/api/edge/ |
| Trakt API | Tracking (device code login) | https://trakt.docs.apiary.io |
| CloudStream repo docs | Repo JSON format for the market | https://recloudstream.github.io/csdocs/devs/create-your-own-json-repository/ |
| IzzyOnDroid / F-Droid | Free distribution | https://apt.izzysoft.de/fdroid/ , https://f-droid.org |

## Competitor study (2026-11, v1.0.5 stability pass)

How similar apps handle the problems users reported, and what Yumeiro does instead. We don't copy their code.

| Problem | CloudStream / Aniyomi / Mihon / others | Yumeiro |
|---|---|---|
| ISP blocks sources | Aniyomi and Mihon ship a DNS-over-HTTPS setting the user must find and turn on (Settings → Advanced); users are told to try a VPN. https://aniyomi.org/docs/faq/settings , https://aniyomi.org/docs/guides/troubleshooting/common-issues | Automatic: system DNS first, blocked hosts detected (no answer, sinkhole, reset) and moved to DoH with provider rotation, remembered per host |
| Black screen / stuck playback | CloudStream's advice is manual: lower the cache, switch player, software decoding. https://recloudstream.github.io/csdocs/troubleshooting/ , https://github.com/recloudstream/cloudstream/issues/530 | HLS ad-splice filter, WebView ad blocking, frame watchdog that flushes the decoder and then fails over to the next link |
| Ads in embedded players | Users add AdGuard DNS as the DoH server. https://github.com/aniyomiorg/aniyomi/discussions/882 | Built-in host + VAST blocking in `shouldInterceptRequest`, following the hosts-list approach of Firefox Focus. https://firefox-source-docs.mozilla.org/mobile/android/focus-android/Content-blocking.html , https://www.hidroh.com/2016/05/19/hacking-up-ad-blocker-android/ |
| Background checks killed | YTDLnis exposes "Ignore battery optimization"; ffupdater detects killed jobs with a heartbeat. https://ytdlnis.org/docs/guides/troubleshooting/common-issues , https://github.com/Tobi823/ffupdater/issues/409 , https://dontkillmyapp.com/general | Direct battery exemption request + OEM pages, heartbeat that tells the user when checks stall, catch-up run on every app open |
| Screen sleeps | Media3 guidance: `keepScreenOn` on the player view and `setWakeMode`. https://developer.android.com/media/media3/ui/playerview , https://developer.android.com/develop/background-work/background-tasks/awake | Same, applied to the Compose player, plus network wake mode for streams |
| Push without Firebase | UnifiedPush / ntfy (needs a distributor app). https://unifiedpush.org/users/distributors/ntfy/ , https://docs.ntfy.sh/subscribe/phone/ , scheduled delivery https://docs.ntfy.sh/publish/#scheduled-delivery | Optional: the app schedules its own wake-ups on its ntfy endpoint, no server (HANDOVER §17) |
| YouTube sign-in blocked | ArchiveTune "Login with token" (paste the InnerTube cookie, no guide) https://github.com/NatuneGroup/ArchiveTune ; yt-dlp cookie export advice (private window, robots.txt) https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies | Paste cookie accepts header / cURL / cookies.txt / JSON / ArchiveTune token, checked with youtube.com, step-by-step guide |

## YouTube sign-in study (2026-11, settings redesign)

| App | What it does | Source |
|---|---|---|
| ArchiveTune | Opens Google's sign-in page in a WebView, keeps the cookies plus VISITOR_DATA / DATASYNC_ID and checks them against InnerTube; users report the login sometimes doesn't save | https://github.com/NatuneGroup/ArchiveTune/blob/main/docs/source-logins.md , https://github.com/rukamori/ArchiveTune/issues/1435 |
| NewPipe | Refuses login on purpose: privacy, and accounts used by third-party clients can be banned | https://newpipe.net/FAQ/bugs/ , https://github.com/TeamNewPipe/NewPipe/issues/12211 |
| PipePipe | Login cookie used only to get playback streams, recommended only for IP blocks and age limits; the login has been broken for months | https://anoni.net/news/en/2026/10/pipepipe-youtube-client/ , https://github.com/InfinityLoop1308/PipePipe/issues/2137 |
| Metrolist | Login for YouTube Music library sync | https://metrolist.cc/faq |

Yumeiro takes the PipePipe scope (playback only, optional, off unless signed in, "only when blocked" by default)
with ArchiveTune's WebView capture. No library sync, no history upload.


## Captcha page and subtitle sites study (2026-11, PR #17 CI fix)

| Topic | How others do it | Yumeiro |
|---|---|---|
| Captcha / site WebView | Mihon's `setDefaultSettings()` turns JavaScript, DOM storage and multiple windows on and keeps pop-ups as a stack of real tabs. https://github.com/mihonapp/mihon/blob/446f6df8/core/common/src/main/kotlin/eu/kanade/tachiyomi/util/system/WebViewUtil.kt , https://github.com/mihonapp/mihon/blob/446f6df8/app/src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt . Reikai (Mihon fork) scopes its challenge listener to the page origin in an isolated JS world and can hand off to a self-hosted FlareSolverr. https://github.com/unseensnick/Reikai/blob/main/docs/dev/subsystems/cloudflare.md | JavaScript on only in the page itself, no JS bridge, file/content access off, pop-ups opened in a JavaScript-less probe that only reads the target URL and is destroyed after 5 s; ad hosts and cross-site jumps held back (AdShield) |
| Subtitle sites | CloudStream: OpenSubtitles (login) and SubDL. https://cloudstream.miraheze.org/wiki/Subtitles . SkyStream also asks Stremio subtitle add-ons, including OpenSubtitles' own add-on, so IMDb titles get subtitles with no key. https://github.com/akashdh11/skystream/releases/tag/v2.9.0 . Kodi a4kSubtitles: many providers, auto-search first item. https://github.com/a4k-openproject/a4kSubtitles | OpenSubtitles + SubDL + SubSource in parallel, season-pack episode picking, keys sealed with `SecretBox` and scoped to their own domain across redirects |
| API keys in requests | Every common HTTP client resends a custom `X-Api-Key` header on a cross-host redirect. https://safeguard.sh/resources/blog/http-client-redirect-header-leak-authorization-api-key-2026 | `SubtitleNet.keyScoped` drops the key on any hop off the site or onto HTTP |

Next ideas (not built): keyless subtitles through the Stremio subtitle add-ons Yumeiro can already run (the
Stremio bridge), and moving SubDL to its v2 API with the key in a header instead of the URL
(https://subdl.com/developers). Both need a device check with real titles first.

## Sozo, music clients and trackers study (2026-11, §22)

Sozo (https://github.com/Sozo-app/sozo, Flutter, GPL-3.0) is the closest "everything app" to Yumeiro. Read for
workflow and design, not copied. Its standout features (Shorts trailer feed, auto-translated subtitles, comments,
achievements, yearly recap, streaks, trivia, watch party) all run on a private Node backend (`sozo-backend`) plus
Firebase / Amplitude. Yumeiro has no server and no funds, so each idea is rebuilt on-device on free public APIs.

| Area | How others do it | Yumeiro |
|---|---|---|
| Tracking breadth | Sozo: Trakt. Dartotsu and AnymeX: AniList, MAL, Simkl, Kitsu. https://github.com/aayush2622/Dartotsu , https://github.com/RyanYuuki/AnymeX | AniList, MAL, Kitsu, Trakt and now Simkl (anime, shows and movies in one list), matched by MAL / AniList / IMDb / TMDB id, each tracker isolated so one failure never blocks the others |
| Simkl sign-in | Typical apps use the PIN flow with a client secret | OAuth 2 device flow (`/oauth2/device`) with the built-in public client id, no secret, 7-day tokens renewed ahead of expiry |
| Scrobbling | Metrolist has Last.fm built in but its own FAQ also points users to Pano Scrobbler. https://github.com/MetrolistGroup/Metrolist , https://www.apkmirror.com/apk/mostafaalagamy/metrolist/metrolist-12-9-0-release/metrolist-12-9-0-android-apk-download/ . Pano Scrobbler: offline queue, regex edits, first-artist extraction. https://github.com/kawaiiDango/pano-scrobbler | Built in, for local files and streamed songs alike: Last.fm's 50% / 4-minute rule on real listening time (seeking doesn't count), "Artist - Topic" / "(Official Video)" clean-up, offline queue (500, 14 days), paused in incognito |
| Synced lyrics | Kreate, Gyawun and N-Zik use LRCLIB and KuGou; Metrolist users report providers silently missing until the priority list is re-saved. https://h5.2113.net/topics/youtube-music-clients/ , https://github.com/MetrolistGroup/Metrolist/issues/3610 | One fixed, predictable order: imported → sidecar `.lrc` → embedded tags → LRCLIB (length-matched, synced preferred, other versions rejected), disk cache and a week-long "no lyrics" memory so songs aren't re-queried |
| Subtitles | Sozo auto-translates on its server | Keyless OpenSubtitles through the built-in key (user key still wins), parameters pre-sorted to skip the API's 301; on-device translation is on the roadmap |

Rebuild list from Sozo, all serverless (ROADMAP §2.5): trailer "Shorts" from TMDB videos, on-device stats / streaks /
yearly recap from local history, on-device subtitle translation, link-TV by QR over the LAN.

## Sozo server study (2026-11, §24)

Second look at Sozo, this time at what its backend does (read from the Flutter client in `lib/`, the server itself is
private and its base URL is obfuscated). It is a classic account backend: `auth/login`, `refresh`, `profile`,
`auth/history/sync`, `auth/favorites`, comments, streak ping, achievements, trivia leaderboards and challenges, a
socket.io `/watch` namespace for watch parties, `link-tv` + `auth/device/approve`, `remote/state` / `remote/command`,
support tickets, notifications, banners, app-version, friends / privacy / activity, a TMDB catalogue proxy, and
Trakt / AniList / MAL link sync. Everything is tied to an account and readable by the operator.

What users love there, judged against Yumeiro's limits (open source, no funds, Cloudflare free plan, ~50k monthly
users, and the app must never depend on the server). Only what improves watching and fits the free plan was built:

| Sozo (account + server reads everything) | Yumeiro Cloud |
|---|---|
| Watch party over socket.io, needs an account | Built: online rooms on a hibernating Durable Object; the invite link carries the key in `#fragment`; the server relays sealed frames and stores nothing per message; newcomers sync from the host; chat + reactions; Wi-Fi rooms still work with no server at all |
| History / favourites sync to the account | Not built: backups would fill the free 500 MB D1 database at this user count. File backup (Backup & restore) stays |
| Link a TV, approve on phone (account session) | Not built (depends on cloud sync). Later idea: LAN-only transfer, no server |
| Comments, trivia, leaderboards, friends, activity | Not rebuilt on purpose: they need identities and moderation, and the owner asked for "only what is necessary" |
| Remote control (`remote/state`, `remote/command`) | Possible later on the same room transport (a sealed `msg` kind), no new server code |
