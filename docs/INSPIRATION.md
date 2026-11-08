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
| Push without Firebase | UnifiedPush / ntfy (needs a distributor app). https://unifiedpush.org/users/distributors/ntfy/ , https://docs.ntfy.sh/subscribe/phone/ | Roadmap item (optional) |
