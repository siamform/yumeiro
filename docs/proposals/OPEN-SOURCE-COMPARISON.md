# Phase 6: Open-source comparison (proposal only)

Yumeiro vs CloudStream (cs-repos plus the main app) and Hikari. Written 14 November 2026. Nothing here is built
yet, and nothing in the Yumeiro repo was changed. Every Yumeiro claim points at a file path in
`yumeiro/app/src/main/java/` (short form: `dev/asfakul/yumeiro/...` is written as `y/...`). Every claim about
another project has a link.

---

## Summary

- **cs-repos is not an app.** [recloudstream/cs-repos](https://github.com/recloudstream/cs-repos) is CloudStream's
  community list of extension repositories: one file,
  [`repos-db.json`](https://github.com/recloudstream/cs-repos/blob/master/repos-db.json) (34 entries today, only
  the official one marked `verified`), a CI check (`ci_check.py`) that confirms every listed `repo.json` and plugin
  file answers, and `create_markdown.py`, which builds the public list on
  [rentry.org/cs3-repos](https://rentry.org/cs3-repos). It has **no releases and no changelog**; its history is 115
  commits (Aug 2022 to Oct 2026). Yumeiro already reads it: `.github/scripts/build_extension_market.py:39`
  (`COMMUNITY_DB`) feeds the Extension Market. The features that matter therefore live in the main app,
  [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream), so I studied all 17 of its releases
  and its 2026 commit history (423 commits) as well.
- **Hikari** ([codegeasse1/hikari](https://github.com/codegeasse1/hikari), GPL-3.0, started 16 Aug 2026) is the
  closest rival: a Kotlin + Compose "one player, every ecosystem" app. In three months it shipped 62 GitHub
  releases (v0.1.0 to [v0.12.51](https://github.com/codegeasse1/hikari/releases/tag/v0.12.51), plus continuous
  builds up to 0.12.100 in its [CHANGELOG](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md), 356
  entries, 1,532 commits). It is broad (11 extension formats, Telegram, full Live TV, AI dub, manga translation, a
  second music app inside), and its own changelog spends a lot of space on stalls, wrong episodes and blank posters.
- **Yumeiro is ahead on** stability engineering, privacy, one shared player, local media, YouTube and YouTube
  Music, home servers, music features, signed updates, extension safety and documented behaviour.
- **Yumeiro is behind on** five things users notice: (1) skip intro for films and series, not only anime;
  (2) a real Live TV guide (XMLTV EPG, Xtream login, catch-up); (3) torrent streaming; (4) profiles; (5) audio
  comfort (dialogue boost and volume above 100 %). There is also a cluster of smaller "polish" gaps: seekbar
  previews, filler marks, title logos, a calendar, stats, Android TV home rows, more trackers.
- **Recommended for approval first (all S or M, low RAM friendly):** Skip segments for all titles, dialogue boost
  and volume boost, filler marks, EPG guide plus Xtream, Android TV Watch Next, stats page, release calendar,
  delisted-repo notice, CloudStream library 4.9 bump. Torrent and profiles next. Defer AI dub, Telegram and new
  extension engines.

---

## Sources studied

### recloudstream/cs-repos (repository index)

| What | Link |
|---|---|
| Repo | https://github.com/recloudstream/cs-repos |
| The list | https://github.com/recloudstream/cs-repos/blob/master/repos-db.json |
| Rendered list | https://rentry.org/cs3-repos |
| Releases | none (`gh release list` returns 0) |
| Commit history read in full | https://github.com/recloudstream/cs-repos/commits/master |
| Notable commits | "Remove stolen repository" [31b6aac](https://github.com/recloudstream/cs-repos/commit/31b6aacef1aa70cc7f4cd61da13f44de7811cfcf) (Feb 2026), "Remove stolen repo (#87)" [0f5ff9b](https://github.com/recloudstream/cs-repos/commit/0f5ff9bd3eb697b75a918c85069c130e18cb48c6) (Mar 2026), "Removed NivinCNC" [1b53e39](https://github.com/recloudstream/cs-repos/commit/1b53e39c0511aefddb0479edd87332ee04940319) (Jun 2026), "Add Eclipsia (#108)" [d8383d3](https://github.com/recloudstream/cs-repos/commit/d8383d3bb2234866c6b61647a84010684713a807) (Oct 2026) |

The list itself warns that extensions "can execute arbitrary code inside the app" and "can also read all of the
Cloudstream's data" (`create_markdown.py`, [source](https://github.com/recloudstream/cs-repos/blob/master/create_markdown.py)).

### recloudstream/cloudstream (main app; cs-repos features depend on it)

All 17 releases were read with `gh release view`:

| Release | Main points | Link |
|---|---|---|
| pre-release (9 Nov 2026) | launch fix | https://github.com/recloudstream/cloudstream/releases/tag/pre-release |
| v4.9.1 (9 Nov 2026) | AniList/MAL login fix, HW+ decoding | https://github.com/recloudstream/cloudstream/releases/tag/v4.9.1 |
| v4.9.0 | new settings, accounts and extension UI, clock in TV player, new subtitle delay dialog | https://github.com/recloudstream/cloudstream/releases/tag/v4.9.0 |
| v4.8.0 | intro skip database, metadata overlay on TV, more external players, security fixes, filler database update | https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0 |
| v4.7.0 | brightness feature, zoom, title logo, download queue | https://github.com/recloudstream/cloudstream/releases/tag/v4.7.0 |
| v4.6.0 | episodes in player, provider pinning, paginated search, shareable links, profile photo, play mirror, TV channel previews, speed dialog | https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0 |
| v4.5.4 | volume booster, more external players, download settings | https://github.com/recloudstream/cloudstream/releases/tag/v4.5.4 |
| v4.5.2 | MyDramaList, subtitle grouping, episode sorting, MKV seek | https://github.com/recloudstream/cloudstream/releases/tag/v4.5.2 |
| v4.4.0 | FCast | https://github.com/recloudstream/cloudstream/releases/tag/v4.4.0 |
| v4.3.0 | account update, preview seekbar | https://github.com/recloudstream/cloudstream/releases/tag/v4.3.0 |
| v4.2.0, v4.1.10, v4.1.9 | updater fix, TV fixes, 2-3x download speed, mute audio fix | https://github.com/recloudstream/cloudstream/releases/tag/v4.1.9 |
| v4.0.1 | Library, subscription notifications | https://github.com/recloudstream/cloudstream/releases/tag/v4.0.1 |
| v3.5.0, v3.2.4, v3.2.1 | TV home, preview popups, skip OP/ED popup | https://github.com/recloudstream/cloudstream/releases/tag/v3.2.4 |

Pull requests and files read for detail: TheIntroDB skip
[#2599](https://github.com/recloudstream/cloudstream/pull/2599) and
[#2631](https://github.com/recloudstream/cloudstream/pull/2631)
([TheIntroDBSkip.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/videoskip/TheIntroDBSkip.kt));
dynamic range compressor [#3117](https://github.com/recloudstream/cloudstream/pull/3117)
([DynamicRangeCompressor.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/DynamicRangeCompressor.kt));
HW+ [#3312](https://github.com/recloudstream/cloudstream/pull/3312); Android TV channels
[#1929](https://github.com/recloudstream/cloudstream/pull/1929)
([TvChannelUtils.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/TvChannelUtils.kt));
seekbar previews ([PreviewGenerator.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/PreviewGenerator.kt));
torrents ([Torrent.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/Torrent.kt));
filler ([FillerEpisodeCheck.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/FillerEpisodeCheck.kt));
FCast ([fcast/](https://github.com/recloudstream/cloudstream/tree/master/app/src/main/java/com/lagradost/cloudstream3/actions/temp/fcast));
MyDramaList ([MyDramaList.kt](https://github.com/recloudstream/cloudstream/blob/master/library/src/commonMain/kotlin/com/lagradost/cloudstream3/metaproviders/MyDramaList.kt));
library moving to kotlinx-serialization [#2791](https://github.com/recloudstream/cloudstream/pull/2791) and ABI
validation of the extension library [#2835](https://github.com/recloudstream/cloudstream/pull/2835).

### codegeasse1/hikari

| What | Link |
|---|---|
| Repo | https://github.com/codegeasse1/hikari |
| All 62 releases (read every body; v0.3.4 to v0.3.45 are empty) | https://github.com/codegeasse1/hikari/releases |
| Latest release v0.12.51 (5 Nov 2026) | https://github.com/codegeasse1/hikari/releases/tag/v0.12.51 |
| v0.12.0: new TV UI, novel reader, Zangetsu and LNReader repos | https://github.com/codegeasse1/hikari/releases/tag/v0.12.0 |
| v0.11.33: poster hold menu, Sources page, reader up to 4K, profiles with pictures | https://github.com/codegeasse1/hikari/releases/tag/v0.11.33 |
| v0.11.04: open-with repo links, silent plugin auto-update with restore | https://github.com/codegeasse1/hikari/releases/tag/v0.11.04 |
| v0.11.03: trackers catalogue, calendar, Anymex and Sora, download-all, external player, torrent in IPTV tab | https://github.com/codegeasse1/hikari/releases/tag/v0.11.03 |
| v0.10.66: My Stuff lock, swipe-to-seek, volume booster to 200 % | https://github.com/codegeasse1/hikari/releases/tag/v0.10.66 |
| v0.10.57: profiles, Vega, network streams, Telegram, genre strip, recent searches | https://github.com/codegeasse1/hikari/releases/tag/v0.10.57 |
| v0.10.42: Telegram sign-in, six trackers incl. Shikimori, app lock, QR pairing | https://github.com/codegeasse1/hikari/releases/tag/v0.10.42 |
| v0.10.25: manga reader, stats with heatmap, title logo, pair and sync, collections, subtitle sites | https://github.com/codegeasse1/hikari/releases/tag/v0.10.25 |
| v0.9.8: IPTV, Stremio, Aniyomi, Nuvio, SkyStream, poster styling, DNS mode | https://github.com/codegeasse1/hikari/releases/tag/v0.9.8 |
| v0.5.17: TMDB language, personal catalog creator, library categories, DoH chooser | https://github.com/codegeasse1/hikari/releases/tag/v0.5.17 |
| v0.3.60 to v0.3.68: Nuvio, yt-dlp fallback, auto Cloudflare, PiP, downloads, trailers | https://github.com/codegeasse1/hikari/releases/tag/v0.3.68 |
| Clone voice model release (dub voices) | https://github.com/codegeasse1/hikari/releases/tag/clone-model |
| Continuous builds 0.12.52 to 0.12.100 (AI dub, Live TV home, Xtream/XMLTV, lossless music) | https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md |
| Design docs | https://github.com/codegeasse1/hikari/tree/main/docs (PAIRING, STATS, SEARCH, PERFORMANCE, DUB_VOICES, PERSONAL_CATALOG, CONTENT_FILTERS) |

---

## Feature matrix

Legend: Yes = shipped, Part = partly, No = not there. CS = CloudStream app. Yumeiro paths are under
`app/src/main/java/`, `y/` = `dev/asfakul/yumeiro/`.

| Area | Feature | Yumeiro (verified in code) | CloudStream | Hikari |
|---|---|---|---|---|
| Extensions | CloudStream `.cs3` | Yes, library 4.8.0 (`gradle/libs.versions.toml:33`, `y/online/ExtensionManager.kt`) | Yes, native | Yes ([README](https://github.com/codegeasse1/hikari#add-extensions)) |
| | Community repo list (cs-repos) | Yes, in market build (`.github/scripts/build_extension_market.py:39`) | Yes ([rentry](https://rentry.org/cs3-repos)) | Yes, mirrors repos-db.json ([ExtensionsScreen.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/ui/screens/ExtensionsScreen.kt)) |
| | Aniyomi anime / Mihon manga APKs | Yes (`y/sources/tachiyomi/AniyomiProvider.kt`, `MihonMangaProvider.kt`) | No | Yes ([v0.9.8](https://github.com/codegeasse1/hikari/releases/tag/v0.9.8)) |
| | Stremio add-ons | Yes, HTTP only (`y/sources/stremio/StremioProvider.kt`) | No | Yes, incl. magnets ([v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42)) |
| | Mangayomi-style JS | Yes, QuickJS (`y/sources/js/JsEngine.kt`) | No | Yes, AnymeX ([v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03)) |
| | Nuvio, Vega, SkyStream, Sora, Kotatsu, Zangetsu, LNReader, own `.hiki` | No | No | Yes ([v0.9.8](https://github.com/codegeasse1/hikari/releases/tag/v0.9.8), [v0.10.57](https://github.com/codegeasse1/hikari/releases/tag/v0.10.57), [v0.12.0](https://github.com/codegeasse1/hikari/releases/tag/v0.12.0)) |
| | Paste-anything / open-with repo links | Yes (`y/sources/UniversalAdd.kt`) | Part (`cloudstreamrepo://`) | Yes ([v0.11.04](https://github.com/codegeasse1/hikari/releases/tag/v0.11.04)) |
| | Checksum-verified install, crash guard, static scanner | Yes (`ExtensionManager.kt:234`, `y/online/ExtensionGuard.kt`, `ExtensionScanner.kt`) | Part | Part (checksum) |
| | Provider pinning | No (only download pins) | Yes ([v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0)) | Yes ([v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25)) |
| | Delisted / stolen repo warning | No | Removes from list ([0f5ff9b](https://github.com/recloudstream/cs-repos/commit/0f5ff9bd3eb697b75a918c85069c130e18cb48c6)) | No |
| Home servers | Jellyfin, Emby, Plex, DLNA, SMB, WebDAV | Yes (`y/sources/servers/*`) | No | No |
| YouTube | YouTube + YT Music, no login | Yes (`y/youtube/YouTube.kt`, `y/youtube/music/YtMusicApi.kt`) | No | Part, music search via YouTube ([0.12.90](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| Local media | Local music + video library | Yes (`y/data/MediaRepository.kt`) | No | No |
| Music | Lyrics, EQ, sleep timer, Last.fm | Yes (`y/lyrics/LrcLib.kt`, `y/player/equalizer/EqualizerManager.kt`, `y/player/SleepTimer.kt`, `y/scrobble/LastFm.kt`) | No | Part, EQ15, crossfade, lossless via third-party proxy ([commit 2245ca1](https://github.com/codegeasse1/hikari/commit/2245ca19c128d671ecaa7b0e9749803d183a98bb)) |
| Player | One Media3 player, HW+ decoder, FFmpeg audio | Yes (`y/stream/YumeiroRenderersFactory.kt`, `y/player/VideoPrefs.kt`) | Yes, HW+ ([#3312](https://github.com/recloudstream/cloudstream/pull/3312)) | Yes ([v0.11.33](https://github.com/codegeasse1/hikari/releases/tag/v0.11.33)) |
| | Pinch zoom, PiP, hold for speed, gestures | Yes (`y/player/VideoZoom.kt`, `PipController.kt`, `y/ui/screens/video/VideoPlayerGestures.kt:138`) | Yes ([v4.7.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.7.0)) | Yes ([v0.11.33](https://github.com/codegeasse1/hikari/releases/tag/v0.11.33)) |
| | Episodes / sources / subtitles inside player | Yes (`y/ui/screens/online/OnlinePlayerUi.kt:98`) | Yes ([v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0)) | Yes |
| | External player hand-off (VLC, MX, mpv) | Yes (`y/ui/screens/video/VideoPlayerModal.kt:1048`) | Yes ([v4.8.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0)) | Yes ([v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03)) |
| | Stats for nerds overlay | Yes (`y/ui/screens/video/PlayerInfoOverlay.kt`) | No | Yes ([v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25)) |
| | Anime4K upscale, quality ladder, heat guard | Yes (`y/player/Anime4kEffect.kt`, `QualityEngine.kt`, `HeatGuard.kt`) | No | Part, "enhance presets" |
| | Source failover, black-frame watchdog | Yes (`y/player/StreamRecovery.kt`, `BlackFrameWatchdog.kt`) | Part | Yes ([v0.12.53](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| | Skip intro, anime (AniSkip) | Yes (`y/online/anime/AniSkip.kt`) | Yes | No |
| | Skip intro, films and series (TheIntroDB) | No | Yes ([v4.8.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0), [#2631](https://github.com/recloudstream/cloudstream/pull/2631)) | No |
| | Volume above 100 % / dialogue compressor | No (bass boost only, `y/ui/screens/equalizer/EqualizerScreen.kt:313`) | Yes ([v4.5.4](https://github.com/recloudstream/cloudstream/releases/tag/v4.5.4), [#3117](https://github.com/recloudstream/cloudstream/pull/3117)) | Yes, 200 % ([v0.10.66](https://github.com/codegeasse1/hikari/releases/tag/v0.10.66)) |
| | Seekbar thumbnail preview | No | Yes ([v4.3.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.3.0)) | No |
| | Clock / metadata overlay on TV player | No | Yes ([v4.9.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.9.0), [v4.8.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0)) | No |
| | AI dub (TTS of subtitles), speech-to-text subtitles | No | No | Yes ([0.12.52](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md), [DUB_VOICES.md](https://github.com/codegeasse1/hikari/blob/main/docs/DUB_VOICES.md)) |
| Subtitles | Local files, style editor, OpenSubtitles + SubDL + SubSource | Yes (`y/online/subtitles/*`, `y/ui/screens/video/SubtitleStyleDialog.kt`) | Part, OpenSubtitles + SubDL | Yes, 5 sites ([v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25)) |
| | Subtitle translation | No (roadmap 2.5) | No | Yes, via Google web endpoint ([Translator.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/data/Translator.kt)) |
| Casting | Chromecast | Yes (`y/cast/CastSupport.kt`) | Yes | No |
| | FCast (open protocol) | No | Yes ([v4.4.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.4.0)) | No |
| Torrent | Magnet / .torrent streaming | No, proposal only (`docs/proposals/TORRENT.md`; skipped in `y/online/download/DownloadWorker.kt:152`) | Yes ([Torrent.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/Torrent.kt)) | Yes, TorrServer lib ([build.gradle.kts](https://github.com/codegeasse1/hikari/blob/main/app/build.gradle.kts), [v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03)) |
| Live TV | M3U playlists, market, headers, DRM | Yes (`y/stream/M3uParser.kt`, `PlaylistMarket.kt`, `y/ui/screens/stream/LiveTvHub.kt`) | No | Yes |
| | XMLTV EPG guide grid | No (EPG URL only parsed and shown: `y/stream/PlaylistStore.kt:548`, `y/ui/screens/stream/StreamPlaylistUi.kt:312`) | No | Yes ([XmltvParser.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/iptv/XmltvParser.kt), [0.12.76](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| | Xtream Codes login, catch-up, recordings, reminders, parental | No | No | Yes ([iptv/](https://github.com/codegeasse1/hikari/tree/main/app/src/main/java/com/hikari/app/iptv)) |
| Other video | Telegram channels as source | No | No | Yes ([v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42)) |
| | File-host links (TeraBox, Pixeldrain...) | Yes (`y/stream/LinkHosts.kt`, `y/ui/screens/stream/PageStreamFinderDialog.kt`) | No | Yes |
| | Saved websites + ad-shielded browser | Yes (`y/ui/screens/sites/WebSitesScreen.kt`, `y/online/web/AdShield.kt`) | No | Yes ([0.12.81](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| | Built-in short-drama studios | Yes (`y/sources/drama/YumeiroDrama.kt`) | No | No |
| Reading | Manga reader (webtoon + paged) | Yes (`y/ui/screens/manga/MangaScreens.kt:483`) | No | Yes, more modes, up to 4K ([v0.11.33](https://github.com/codegeasse1/hikari/releases/tag/v0.11.33)) |
| | Light novel reader | No | No | Yes ([v0.12.0](https://github.com/codegeasse1/hikari/releases/tag/v0.12.0)) |
| | Manga page translation (OCR) | No | No | Yes ([v0.12.51](https://github.com/codegeasse1/hikari/releases/tag/v0.12.51)) |
| Metadata | Filler episode marks | No (field exists, unused: `com/lagradost/cloudstream3/ui/result/ResultEpisode.kt:30`) | Yes ([v4.8.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0)) | No |
| | Title logo, TMDB/TVDB art, cast/characters | Part, episode stills only (`y/online/EpisodeStills.kt`) | Yes, logo ([v4.7.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.7.0)) | Yes ([v0.12.0](https://github.com/codegeasse1/hikari/releases/tag/v0.12.0), [0.12.64](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| | Release calendar | No (airing alerts only: `y/online/NewEpisodesWorker.kt`) | No | Yes ([v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03)) |
| Trackers | AniList, MAL, Kitsu, Trakt, Simkl | Yes (`com/lagradost/.../AniListApi.kt`, `MALApi.kt`, `y/tracking/Kitsu.kt`, `Trakt.kt`, `Simkl.kt`) | AniList, MAL, Simkl, Trakt, Kitsu | Yes |
| | Shikimori, MyDramaList | No | MDL ([v4.5.2](https://github.com/recloudstream/cloudstream/releases/tag/v4.5.2)) | Shikimori ([v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42)) |
| Library | Status lists (Watching, Plan...) | Yes (`y/online/OnlineBookmarks.kt:26`) | Yes ([v4.0.1](https://github.com/recloudstream/cloudstream/releases/tag/v4.0.1)) | Yes |
| | Custom collections / TMDB-list rows on Home | No | No | Yes ([PERSONAL_CATALOG.md](https://github.com/codegeasse1/hikari/blob/main/docs/PERSONAL_CATALOG.md)) |
| | Stats page (time, heatmap, top titles) | Part, music listening time only (`y/player/ListeningTime.kt`) | No | Yes ([STATS.md](https://github.com/codegeasse1/hikari/blob/main/docs/STATS.md)) |
| Accounts | Profiles (separate libraries, kids) | No | Yes, accounts ([v4.3.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.3.0)) | Yes ([v0.10.57](https://github.com/codegeasse1/hikari/releases/tag/v0.10.57)) |
| | Pair two devices by QR over Wi-Fi | No (watch-together discovery exists: `y/watch/WatchTogether.kt`) | No | Yes ([PAIRING.md](https://github.com/codegeasse1/hikari/blob/main/docs/PAIRING.md)) |
| Privacy | App lock, incognito | Yes (`y/security/Privacy.kt`, `y/ui/screens/extras/AppLockGate.kt`) | Lock | Lock, per profile |
| | Backup v2, reset center | Yes (`y/backup/*`) | Backup | Yes |
| Social | Watch together on LAN | Yes (`y/watch/WatchTogether.kt`) | No | No |
| TV | Android TV / Fire TV layout | Yes, separate TV flavour (`y/ui/tv/TvNavRail.kt`, `docs/TV.md`) | Yes | Yes |
| | Android TV home rows / Watch Next | No | Yes ([#1929](https://github.com/recloudstream/cloudstream/pull/1929)) | No |
| | Shareable title links | No (`yumeiro://` used only for sign-in) | Yes ([v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0)) | Part (deeplink) |
| Network | Automatic DoH with per-host bypass | Yes (`y/network/SmartDns.kt`) | Manual | Manual chooser ([v0.5.17](https://github.com/codegeasse1/hikari/releases/tag/v0.5.17)) |
| | HLS ad-splice filter, ad shield | Yes (`y/stream/HlsAdFilter.kt`, `y/online/web/AdShield.kt`) | No | Ad blocker |
| Ops | Signed in-app updater (SHA-256 + cert) | Yes (`y/update/UpdateManager.kt`) | Updater | Updater |
| | On-device crash log | Yes (`y/diagnostics/CrashLog.kt`) | No | Yes, plus heap dumps ([0.12.76](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)) |
| | Push notices (FCM, ntfy) | Yes (`y/notify/FcmPush.kt`) | No | No |
| | Downloads: HLS mux, 1DM/ADM, groups, YouTube | Yes (`y/online/download/*`) | Yes, queue ([v4.7.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.7.0)) | Yes ([v0.3.68](https://github.com/codegeasse1/hikari/releases/tag/v0.3.68)) |

---

## Missing features: proposals (prioritised)

Shared design rules for every item, so the 200k monthly users on low-RAM phones and TV sticks stay safe:

1. **Bridges stay bridges.** A new source type publishes `MainAPI`s into `SourceHub`
   (`y/sources/SourceHub.kt`) under its own key and is started from `Bridges.start`
   (`y/sources/Bridges.kt`). One failing bridge empties only its own key.
2. **One network stack.** All HTTP goes through `NetworkClient.get()` (`y/network/NetworkClient.kt`, SmartDns,
   AutoBypassInterceptor) or `OnlineRuntime.extensionClient()` (`y/online/OnlineRuntime.kt`). No second OkHttp.
3. **One player.** Audio and video features are Media3 `AudioProcessor`s, renderers or overlays plugged into
   `YumeiroRenderersFactory` and the existing player screens; no second player activity.
4. **Off by default when heavy**, honour `HeatGuard` (`y/player/HeatGuard.kt`) and the low-end device rule
   (3 GB or less RAM) already used for posters. TV flavour gets the smaller defaults.
5. **No secrets in the repo.** Any API key comes from the existing build-config secret path or the user.

Effort: S = up to a week, M = 1 to 3 weeks, L = more than 3 weeks, for one developer.

### P1. Skip segments for films and series (TheIntroDB next to AniSkip)

- **What:** "Skip intro", "Skip recap" and "Skip credits" buttons for non-anime titles. Today Yumeiro only asks
  AniSkip (`y/online/anime/AniSkip.kt`), which needs a MAL id.
- **Who has it:** CloudStream v4.8.0 "Added intro skip database"
  ([release](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0),
  [#2631](https://github.com/recloudstream/cloudstream/pull/2631)), updated to TheIntroDB API v4 in Oct 2026
  ([TheIntroDBSkip.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/videoskip/TheIntroDBSkip.kt)),
  which queries `api.theintrodb.org` by TMDB or IMDb id ([docs](https://theintrodb.org/docs)).
- **User value:** high. Most watch time in Yumix is series; skipping a 90 s intro on every episode is felt daily.
- **Better than they did:** CloudStream has two separate skip paths. Build one `SkipSegments` interface with
  providers in order (AniSkip for anime with MAL id, TheIntroDB for TMDB/IMDb id, then a local "learned" segment:
  if the user pressed +85 s at the same spot on two episodes of a show, offer it on the third). Cache answers per
  episode in `DataCache` for 30 days, including "no data", so we never re-ask.
- **Design:** `y/player/skip/SkipSegments.kt` (interface + merger) - `AniSkipProvider` (wrap existing) -
  `IntroDbProvider` (shared OkHttp, 3 s timeout, runs after first frame) - the result is a small list of
  `(start, end, kind)` drawn on the seekbar and as a button in `OnlinePlayerUi`. Ids come from `LoadResponse`
  (`OnlineRuntime` already sets the `mal`/`kitsu` prefixes; add TMDB/IMDb reads).
- **Effort / risk:** S. Risk low: third-party service can go away; it is optional and cached. Check its terms
  before shipping.
- **Low RAM / TV:** one tiny JSON per episode; the TV button takes focus for 5 s so a remote can skip with OK.

### P1. Audio comfort: dialogue boost (compressor) and volume above 100 %

- **What:** a "Dialogue boost" switch that evens out loud action and quiet speech, plus a volume booster up to
  200 %. Yumeiro has an equalizer and bass boost (`y/ui/screens/equalizer/EqualizerScreen.kt:313`) and an "Audio
  boost" field in stats that only reports the EQ (`y/ui/screens/video/PlayerInfoOverlay.kt:90`).
- **Who has it:** CloudStream volume booster in [v4.5.4](https://github.com/recloudstream/cloudstream/releases/tag/v4.5.4)
  and a dynamic range compressor ported from VLC as a Media3 `AudioProcessor`
  ([#3117](https://github.com/recloudstream/cloudstream/pull/3117)); Hikari "volume booster that actually boosts
  (up to 200 %)" in [v0.10.66](https://github.com/codegeasse1/hikari/releases/tag/v0.10.66)
  ([VolumeBoostProcessor.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/player/VolumeBoostProcessor.kt)).
- **User value:** high on cheap phone speakers and in noisy places, which is a large share of Yumeiro users.
- **Better than they did:** CloudStream exposes five expert sliders (threshold, ratio, attack, release, makeup).
  Ship three presets (Off, Night, Dialogue) and keep sliders under "Advanced". Combine boost and compressor in
  one processor with a soft limiter, so 200 % never clips. Remember the choice per title type (film vs music).
- **Design:** `y/player/audio/LoudnessProcessor.kt` (a `BaseAudioProcessor`, 16-bit and float PCM) inserted in
  the `DefaultAudioSink` built by `YumeiroRenderersFactory`. Yumeiro already decodes to PCM on phones (v1.8.0
  CHANGELOG), so passthrough does not bypass it. Gain is a `StateFlow` read per buffer, no player rebuild. Same
  processor for music in `MusicPlayerManager`.
- **Effort / risk:** S. Risk: hearing safety; show a warning above 150 % and reset boost when headphones connect.
- **Low RAM / TV:** a few multiplications per sample; TV defaults to Off because TVs have their own processing.

### P1. Filler episode marks

- **What:** a "Filler" tag on anime episodes and an optional "Hide filler" toggle.
- **Who has it:** CloudStream ([FillerEpisodeCheck.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/FillerEpisodeCheck.kt),
  database updated in [v4.8.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.8.0)).
- **User value:** medium-high for long shonen series (Naruto, One Piece, Bleach).
- **Better than they did:** Yumeiro already carries an `isFiller` field from the CloudStream and Aniyomi models
  (`com/lagradost/cloudstream3/ui/result/ResultEpisode.kt:30`, `eu/kanade/tachiyomi/animesource/model/SEpisode.kt`)
  but never shows it. Respect what the extension says first, then fill gaps from a small filler list keyed by
  MAL id that the existing market workflow publishes weekly (same pattern as `PlaylistMarket`), so phones never
  load a full filler database.
- **Design:** `y/online/anime/FillerMarks.kt` - one JSON per MAL id fetched on detail open via shared OkHttp -
  merged in `EpisodeMatcher` - tag in episode rows and in the in-player episode panel. "Skip filler" in
  auto-next.
- **Effort / risk:** S. Risk low.
- **Low RAM / TV:** a list of integers per show.

### P1. Live TV guide: XMLTV EPG, Xtream Codes login, catch-up

- **What:** a "Now / Next" line on every channel, a guide grid, and adding an IPTV account by server + user +
  password (Xtream), with catch-up for channels that support it. Today Yumeiro reads the `x-tvg-url` and only
  prints it (`y/stream/PlaylistStore.kt:548`, `y/ui/screens/stream/StreamPlaylistUi.kt:312`).
- **Who has it:** Hikari 0.12.76 "Full Live TV section ... Xtream logins ... XMLTV guide sources" and 0.12.80
  Live TV home with guide and channel switcher ([CHANGELOG](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md);
  code: [XmltvParser.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/iptv/XmltvParser.kt),
  [XtreamApi.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/iptv/XtreamApi.kt),
  [CatchupUrlBuilder.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/iptv/CatchupUrlBuilder.kt)).
  Format reference: [XMLTV](https://wiki.xmltv.org/index.php/XMLTVFormat).
- **User value:** high. Live TV users compare against TiviMate (README names it); a guide is the first thing they
  look for, especially on TV.
- **Better than they did:** Hikari ships guide, recordings, reminders, sports and parental controls at once.
  Ship the guide first, do it in a streaming way: parse XMLTV with `XmlPullParser` straight from the gzip stream,
  keep only channels that exist in the user's playlists and only a 36 hour window, write to a small SQLite table
  in batches of 500. Refresh in a WorkManager job on Wi-Fi or charging. Xtream is just another playlist type
  inside the existing `PlaylistStore` (it produces the same `Channel` objects), so the player, favourites and
  channel up/down keep working.
- **Design:** `y/stream/epg/EpgStore.kt` (SQLite: channel_id, start, stop, title, desc) -
  `y/stream/epg/XmltvReader.kt` (pull parser, gzip) - `y/stream/xtream/XtreamSource.kt` (account check,
  live/VOD/series lists, credentials sealed with `y/security/SecretBox.kt`) - `EpgWorker` - UI: Now/Next line in
  `LiveTvHub`, guide grid as its own screen (TV first). Catch-up adds `utc`/`lutc` or Xtream timeshift URLs to the
  channel's `StreamRequest`.
- **Effort / risk:** M for EPG + Xtream, plus S for catch-up. Risk: Xtream panels are often pirate services; keep
  the app neutral (user brings their own account, no list of panels in the market).
- **Low RAM / TV:** big guides are 50 to 200 MB of XML; never build a DOM, never hold it in memory, cap the table
  (for example 20k programmes), skip guide refresh on devices under 2 GB unless the user asks.

### P1. Android TV home: Watch Next row

- **What:** Yumeiro's Continue watching appears on the Google TV / Android TV home screen.
- **Who has it:** CloudStream "Added TV channel previews" in
  [v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0)
  ([#1929](https://github.com/recloudstream/cloudstream/pull/1929),
  [TvChannelUtils.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/utils/TvChannelUtils.kt)).
- **User value:** medium-high for the TV edition; it is how TV users resume without opening the app.
- **Better than they did:** use the Watch Next row (system-managed, one API) rather than a custom preview
  channel; update it from the same place `y/widget/ContinueWidget.kt` reads, so phone widget and TV row always
  agree. See [Android's Watch Next guide](https://developer.android.com/training/tv/discovery/watch-next-add-programs).
- **Design:** `y/ui/tv/WatchNextPublisher.kt` in the `tv` flavour only (androidx.tvprovider), updated on
  `ON_STOP` of the player and from `LastPlayed`. Deep link `yumeiro://watch?...` handled in `MainActivity`.
  CloudStream had to "swallow exception when device does not support channels"
  ([a2adb6c](https://github.com/recloudstream/cloudstream/commit/a2adb6caaad38465b7b1e75a2d0de35c7e95ccf2)), so wrap every call and skip Fire TV.
- **Effort / risk:** S. Risk low.
- **Low RAM / TV:** max 10 items; posters are URLs, not bitmaps.

### P1. Delisted repository notice (cs-repos removals)

- **What:** when CloudStream's community list removes a repo (for example "Remove stolen repo"), users who
  already installed plugins from it get a calm notice and a one-tap "Remove" or "Keep".
- **Who has it:** nobody does it well. cs-repos removes entries
  ([0f5ff9b](https://github.com/recloudstream/cs-repos/commit/0f5ff9bd3eb697b75a918c85069c130e18cb48c6),
  [31b6aac](https://github.com/recloudstream/cs-repos/commit/31b6aacef1aa70cc7f4cd61da13f44de7811cfcf),
  [1b53e39](https://github.com/recloudstream/cs-repos/commit/1b53e39c0511aefddb0479edd87332ee04940319)), but
  installed copies in CloudStream stay silently.
- **User value:** safety, which protects 200k users and Yumeiro's reputation.
- **Better way:** the market workflow already diffs community entries (`build_extension_market.py:145`) and has
  a `blocked` list (`:345`). Publish a `delisted.json` (repo URL, date, reason "removed from community list" or
  "blocked by Yumeiro"). The app checks installed plugins' `sourceUrl` against it during the existing extension
  update check.
- **Design:** workflow change + `ExtensionManager.checkDelisted()` + an Inbox entry (`y/notify/Inbox.kt`). Never
  auto-delete; users may rely on a repo that was just moved.
- **Effort / risk:** S. Risk: false alarms when a repo only changes URL; match by GitHub owner/repo too.
- **Low RAM / TV:** one small JSON with the existing check.

### P1. CloudStream library 4.9 bump and compatibility watch

- **What:** Yumeiro pins `cloudstreamLibrary = "4.8.0"` (`gradle/libs.versions.toml:33`). CloudStream 4.9.0/4.9.1
  shipped in Nov 2026, and the library is moving from Jackson to kotlinx-serialization
  ([#2791](https://github.com/recloudstream/cloudstream/pull/2791)), to Kotlin Multiplatform source sets
  (`library/src/commonMain`, see [MyDramaList.kt path](https://github.com/recloudstream/cloudstream/blob/master/library/src/commonMain/kotlin/com/lagradost/cloudstream3/metaproviders/MyDramaList.kt))
  and now enforces ABI validation ([#2835](https://github.com/recloudstream/cloudstream/pull/2835)).
- **User value:** new and updated extensions are built against the newer library; staying behind slowly lowers
  the "2,247 of 2,248 load" figure (`docs/EXTENSION-COMPAT.md`).
- **Better way:** bump behind the existing link audit (`scripts/extension_link_audit.py`) plus a nightly job that
  loads the top 200 market plugins in a JVM test and diffs results against 4.8.0. Ship only if the count does not
  drop.
- **Effort / risk:** S to M. Risk: Yumeiro's shimmed `com/lagradost/cloudstream3/*` classes may clash; the audit
  catches it before users do.

### P2. Torrent streaming (opt-in)

- **What:** play `magnet:` and `.torrent` links from Stremio add-ons and CloudStream providers. Today they are
  skipped (`y/online/download/DownloadWorker.kt:152`; README "Yumeiro does not ship torrent streaming").
- **Who has it:** CloudStream, via a bundled torrent server
  ([Torrent.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/Torrent.kt));
  Hikari, using `com.github.recloudstream:torrentserver` ([build.gradle.kts](https://github.com/codegeasse1/hikari/blob/main/app/build.gradle.kts)),
  "Torrent streams in the IPTV tab" ([v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03)) and
  Stremio "magnet links play as a torrent" ([v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42)).
- **User value:** high for Stremio users (many add-ons return only torrents); medium otherwise.
- **Better than they did:** both apps run a Go torrent server in-process. CloudStream's own code notes that its
  `shutdown()` "should not be used because I am unable to start it again, and the stopTorrentServer() crashes
  the app" ([Torrent.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/Torrent.kt)).
  Follow Yumeiro's own plan in `docs/proposals/TORRENT.md`: libtorrent4j behind a `TorrentEngine` interface, a
  loopback HTTP server with Range support and a random token, sequential + deadline piece priority, Wi-Fi only,
  size-capped cache, never seed after the player closes. Two improvements on that plan: run the engine in a
  separate `:torrent` process so a native crash cannot take the player down and RAM is returned when it stops,
  and ship the native libraries as an optional on-demand download (or a separate "Yumeiro Torrent" add-on APK)
  so the base APK does not grow for users who never use it.
- **Design:** `y/torrent/TorrentEngine.kt` (interface) - `LibtorrentEngine` in `:torrent` process -
  `TorrentStreamServer` (127.0.0.1, Range) - `TorrentResolver` registered in `OnlineRepository` for `magnet:` and
  `.torrent` links, returns an `http://127.0.0.1:port/token/file` link to Media3. Foreground service type
  `dataSync`. Stremio bridge stops filtering torrent streams when the setting is on.
- **Effort / risk:** L. Risks: legal exposure and user IP visibility (one-time notice, off by default, no torrent
  search built in), Play Protect heuristics (keep the VirusTotal check in release CI), battery.
- **Low RAM / TV:** libtorrent cache capped at 32 MB RAM on devices of 2 GB or less; disk cache 2 GB default;
  TV sticks often have 8 GB storage, so the cap must follow free space.

### P2. Profiles (with a kids profile)

- **What:** several people on one phone or TV, each with their own history, My List, trackers and settings; a
  kids profile with adult content hard off.
- **Who has it:** Hikari [v0.10.57](https://github.com/codegeasse1/hikari/releases/tag/v0.10.57) (profiles hold
  "extensions, Library, history, accounts, collections and every setting"), locks per profile in
  [v0.11.04](https://github.com/codegeasse1/hikari/releases/tag/v0.11.04), pictures in
  [v0.11.33](https://github.com/codegeasse1/hikari/releases/tag/v0.11.33); CloudStream accounts in
  [v4.3.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.3.0), profile photo in
  [v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0). Already on Yumeiro's ROADMAP ("kids
  profile").
- **User value:** high on shared family TVs and shared phones, common in Bangladesh and South Asia.
- **Better than they did:** Hikari copies every extension per profile, which multiplies disk use and update
  work. Share extensions and caches across profiles; split only personal data (history, bookmarks, trackers,
  Last.fm, YouTube cookie, settings). Kids profile = `AdultContent` (`y/online/AdultContent.kt`) forced off plus
  an allow-list of sources.
- **Design:** `y/profile/Profiles.kt` with a `profileId`; personal stores (`OnlineHistory`, `OnlineBookmarks`,
  `LastPlayed`, tracker tokens, `SharedPreferences` files) get a profile suffix through one helper. Backup v2
  gains a "profiles" section. Switching profile restarts the activity, not the process.
- **Effort / risk:** M. Risk: data mix-ups during migration; existing data becomes "Profile 1" with no copying.
- **Low RAM / TV:** no extra memory; one profile is loaded at a time.

### P2. Pair a new device by QR over Wi-Fi

- **What:** move a full setup (extensions list, repos, My List, settings) from phone to TV by scanning a QR code.
- **Who has it:** Hikari [v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42) and
  [PAIRING.md](https://github.com/codegeasse1/hikari/blob/main/docs/PAIRING.md) (NanoHTTPD + UDP beacon, six
  character code). Yumeiro ROADMAP 2.5 already lists "Link a TV by QR over the LAN".
- **User value:** high for TV setup; typing repo URLs with a remote is the worst part of TV onboarding.
- **Better than they did:** reuse Backup v2 sections (`y/backup/Backup.kt`) as the payload, so pairing and backup
  can never disagree, and the staged restore + safety backup already exist. Reuse `NsdManager` discovery from
  `y/watch/WatchTogether.kt`. Encrypt the payload with a key derived from the code (the QR carries the key), so
  others on the same Wi-Fi cannot read it. Never send app lock or YouTube cookies.
- **Design:** `y/pair/PairHost.kt` (short-lived local server only while the screen is open), `PairClient.kt`,
  QR shown on the TV (it has the screen), phone scans it with the camera intent or types the code.
- **Effort / risk:** S to M. Risk low.
- **Low RAM / TV:** a one-off transfer of a few hundred KB.

### P2. Stats page (watch, read and listen time)

- **What:** time watched, listened and read; a day heatmap; top titles and artists; optional yearly recap.
- **Who has it:** Hikari [v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25) and
  [STATS.md](https://github.com/codegeasse1/hikari/blob/main/docs/STATS.md). Yumeiro has real listening time for
  music (`y/player/ListeningTime.kt`) and the idea is on ROADMAP 2.5.
- **User value:** medium; good for engagement and sharing.
- **Better than they did:** Hikari's doc notes that history overwrites rows on re-watch, so it keeps a separate
  counter. Do the same, but store daily buckets (date, kind, seconds, top id), at most 366 rows per kind, so the
  page loads instantly on any phone. Everything on device, nothing uploaded.
- **Design:** extend `ListeningTime` into `y/stats/UsageStats.kt` fed by the video and music players and the
  reader; Compose page drawing the heatmap with Canvas (no chart library).
- **Effort / risk:** S. Risk low.

### P2. Release calendar

- **What:** a week view of upcoming episodes for titles in My List, plus "airing today" for anime.
- **Who has it:** Hikari "Anime, movie and series calendar" ([v0.11.03](https://github.com/codegeasse1/hikari/releases/tag/v0.11.03),
  [AnimeReleaseCalendarRepository.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/data/AnimeReleaseCalendarRepository.kt)).
- **User value:** medium-high for anime fans; it also explains the airing notifications Yumeiro already sends.
- **Better way:** Yumeiro phones already read AniList airing times for alerts (ROADMAP 0.9: "Airing times come
  from phones"). Reuse that data plus TMDB next-episode dates; only for saved titles by default, so it costs a
  few requests, not a catalogue crawl.
- **Design:** `y/online/calendar/ReleaseCalendar.kt` reading `OnlineBookmarks` + `AniList` + TMDB, cached a day;
  a Home shortcut and a TV row.
- **Effort / risk:** S. Risk low.

### P2. Seekbar thumbnail previews

- **What:** a small picture above the seekbar while scrubbing.
- **Who has it:** CloudStream "Preview seekbar" ([v4.3.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.3.0),
  [PreviewGenerator.kt](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/ui/player/PreviewGenerator.kt),
  which pulls frames with `MediaMetadataRetriever` and parses HLS with `M3u8Helper`).
- **User value:** medium; very visible quality signal.
- **Better than they did:** CloudStream downloads frames from the stream, which costs data and memory. Use free
  sources first: YouTube storyboards from NewPipeExtractor's preview frames
  ([NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)), HLS `EXT-X-I-FRAME-STREAM-INF` playlists
  when the stream offers them, and local/downloaded files via `MediaMetadataRetriever`. For other streams, show
  time only.
- **Design:** `y/player/preview/SeekPreview.kt` with providers (Storyboard, IFrameHls, LocalFile), 160 px frames,
  LRU of 2 MB, generated only while the finger is on the bar.
- **Effort / risk:** M. Risk: extra decoding on weak phones; respect `HeatGuard`.
- **Low RAM / TV:** off on TV sticks under 2 GB; TV remotes scrub in 10 s steps where previews help less.

### P2. Title logos and richer artwork

- **What:** the title's logo on the detail hero and loading screen; wide backdrops; cast or characters rows.
- **Who has it:** CloudStream title logo ([v4.7.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.7.0));
  Hikari TMDB hero images ([v0.12.0](https://github.com/codegeasse1/hikari/releases/tag/v0.12.0)) and a
  TMDB to TVDB to AniList/Bangumi chain ([0.12.64](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)).
- **User value:** medium; makes Yumix and TV feel premium.
- **Better than they did:** Hikari's changelog has dozens of entries fixing wrong matches ("Wrong metadata
  vetoed", "TMDB cache v4 drops poisoned Barbie-style mappings", 0.12.61 to 0.12.68). Only enrich when the
  extension already gives a TMDB, IMDb, MAL or AniList id (Yumeiro reads these ids already); never match by title
  alone. Use TMDB `/images` with the existing key and `include_image_language`.
- **Design:** `y/online/meta/ArtworkEnricher.kt` behind `OnlineDetailScreen`, disk-cached, 1 request per title.
- **Effort / risk:** S. Risk low with the id-only rule.
- **Low RAM / TV:** logos are small PNGs; low-end devices already decode posters in 16-bit.

### P3. Shikimori and MyDramaList trackers

- **Who has it:** Hikari Shikimori ([v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42));
  CloudStream MyDramaList ([v4.5.2](https://github.com/recloudstream/cloudstream/releases/tag/v4.5.2)).
- **User value:** Shikimori for Russian-speaking anime fans; MDL for the drama audience that Yumeiro Drama already
  attracts.
- **Design:** same isolated pattern as `y/tracking/Simkl.kt` (one failure never blocks others). MDL has no public
  write API, so offer it as metadata (ratings, cast) only. Shikimori uses OAuth ([API docs](https://shikimori.one/api/doc)).
- **Effort / risk:** S each. Risk low.

### P3. Custom collections and Home rows

- **What:** user folders of titles and Home rows built from a TMDB list, studio, network or genre query.
- **Who has it:** Hikari personal catalog creator ([v0.5.17](https://github.com/codegeasse1/hikari/releases/tag/v0.5.17),
  [PERSONAL_CATALOG.md](https://github.com/codegeasse1/hikari/blob/main/docs/PERSONAL_CATALOG.md)).
- **Better way:** keep it small: "Collections" as named groups inside My List, plus a "Row from TMDB list" that
  resolves through installed extensions only when the user taps a title.
- **Effort / risk:** S to M. Risk low.

### P3. On-device translation (subtitles first, manga later)

- **What:** translate a subtitle track to the user's language; later, translate manga speech bubbles.
- **Who has it:** Hikari manga and novel translation ([v0.12.51](https://github.com/codegeasse1/hikari/releases/tag/v0.12.51))
  using ML Kit OCR and an unofficial Google Translate web endpoint
  ([Translator.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/data/Translator.kt)),
  which its own release notes say gets rate-limited ("Fixed Google Translate getting rate-limited on manga pages").
- **Better than they did:** no unofficial endpoints. Use an on-device model, downloaded per language only when
  asked. [ML Kit translation](https://developers.google.com/ml-kit/language/translation) is free and offline but
  closed-source, which conflicts with the F-Droid/IzzyOnDroid plan on the ROADMAP; ship it only in the main
  flavour or look at an open model (for example Mozilla's [Firefox Translations models](https://github.com/mozilla/firefox-translations-models)).
- **Effort / risk:** M for subtitles. Risk: model size (around 30 MB per language pair), licence.
- **Low RAM / TV:** translate in the background a few lines ahead; never on TV sticks under 2 GB.

### P3. FCast casting

- **What:** cast to FCast receivers (open protocol, works with non-Google TVs and PCs).
- **Who has it:** CloudStream [v4.4.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.4.0)
  ([fcast/](https://github.com/recloudstream/cloudstream/tree/master/app/src/main/java/com/lagradost/cloudstream3/actions/temp/fcast)).
  Protocol: [fcast.org](https://fcast.org).
- **Design:** second backend next to `y/cast/CastSupport.kt` with mDNS discovery reused from watch-together.
- **Effort / risk:** S to M. Risk low; small audience.

### P3. Small polish items

| Item | Who has it | Yumeiro design | Effort |
|---|---|---|---|
| Clock on TV player | CloudStream [v4.9.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.9.0) | Optional text in the TV controls overlay | S |
| Provider pinning in Yumix filter | CloudStream [v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0), Hikari [v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25) | `pinned` flag in `y/ui/screens/online/YumixFilter.kt` ordering | S |
| Shareable title links | CloudStream [v4.6.0](https://github.com/recloudstream/cloudstream/releases/tag/v4.6.0) | `https://yumeiro.click/t/...` that opens the app (App Links) and falls back to the website | S |
| Recent searches with per-engine result chips | Hikari [v0.10.57](https://github.com/codegeasse1/hikari/releases/tag/v0.10.57) | Store last 20 queries; chips from `SearchRanker` results | S |
| Heap snapshot in crash reports | Hikari [0.12.76](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md) | Optional `Debug.dumpHprofData` button next to `CrashLog` | S |

### Not recommended now

| Feature | Who has it | Why not (yet) |
|---|---|---|
| AI dub (neural TTS of subtitles, voice cloning, speech-to-text) | Hikari [0.12.52 to 0.12.70](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md), [DUB_VOICES.md](https://github.com/codegeasse1/hikari/blob/main/docs/DUB_VOICES.md), [clone-model release](https://github.com/codegeasse1/hikari/releases/tag/clone-model) | Hundreds of MB of models (the changelog mentions a "~200 MB cloning download"), 64-bit only, heavy CPU, heat. If wanted, start with "Read subtitles aloud" using Android's system TTS as an accessibility option (S). |
| Telegram as a video source | Hikari [v0.10.42](https://github.com/codegeasse1/hikari/releases/tag/v0.10.42) | Needs the user's Telegram login inside Yumeiro and a Telegram API id registered by us; large TDLib native library; account-ban and privacy risk. |
| Lossless music via third-party proxies | Hikari [VxLosslessApi.kt](https://github.com/codegeasse1/hikari/blob/main/app/src/main/java/com/hikari/app/music/VxLosslessApi.kt) and [commit b94b9b6](https://github.com/codegeasse1/hikari/commit/b94b9b668fcacd70682f6e445d58833fb75e43f6) | Relies on an unofficial worker in front of a paid service; legal and reliability risk. |
| Nuvio, Vega, SkyStream, Sora, Kotatsu, Zangetsu, LNReader engines | Hikari ([v0.9.8](https://github.com/codegeasse1/hikari/releases/tag/v0.9.8), [v0.10.57](https://github.com/codegeasse1/hikari/releases/tag/v0.10.57), [v0.12.0](https://github.com/codegeasse1/hikari/releases/tag/v0.12.0)) | Each engine adds a maintenance stream; Hikari's changelog is full of per-engine fixes. If one is added, pick a JS format that fits the existing QuickJS runtime (`y/sources/js/JsEngine.kt`) and publish through `SourceHub` like the other bridges. Light novels (LNReader) would be the strongest next format, since Yumeiro has no novel reader at all. |
| Automatic cross-extension "search everything" for servers | Hikari ([v0.3.66](https://github.com/codegeasse1/hikari/releases/tag/v0.3.66) onward) | Hikari later had to make "Only this extension" the default (see [SEARCH.md](https://github.com/codegeasse1/hikari/blob/main/docs/SEARCH.md)) and fix wrong-episode plays and probe storms ([0.12.68](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)). Yumeiro's staggered, capped gathering (v1.8.0) is the safer path. |

---

## Things Yumeiro does better

- **One app for local media, YouTube and YouTube Music, plus streaming.** Neither CloudStream nor Hikari plays a
  local music or video library, and Hikari's music needed a separate "music app over Hikari"
  ([0.12.85](https://github.com/codegeasse1/hikari/blob/main/CHANGELOG.md)). Yumeiro: `y/data/MediaRepository.kt`,
  `y/youtube/*`, `y/service/PlaybackService.kt`.
- **Home servers.** Jellyfin, Emby, Plex, DLNA, SMB, WebDAV (`y/sources/servers/*`); neither rival has them.
- **Music depth.** LRCLIB synced lyrics with a fixed order, Last.fm with real-time rules and an offline queue,
  equalizer, sleep timer, continue widget (`y/lyrics/*`, `y/scrobble/*`, `y/widget/ContinueWidget.kt`).
- **Extension safety.** Checksum-verified installs, `.part` files, a crash guard and a static scanner
  (`y/online/ExtensionManager.kt`, `ExtensionGuard.kt`, `ExtensionScanner.kt`), plus a public status page.
  Hikari's own notes record "Extensions uninstalling themselves on failed updates"
  ([v0.11.33](https://github.com/codegeasse1/hikari/releases/tag/v0.11.33)).
- **Network that fixes itself.** Automatic DNS-over-HTTPS per blocked host (`y/network/SmartDns.kt`) instead of a
  manual DNS menu ([Hikari v0.5.17](https://github.com/codegeasse1/hikari/releases/tag/v0.5.17)); HLS ad-splice
  filter (`y/stream/HlsAdFilter.kt`).
- **Player engineering for cheap phones.** Heat guard, Anime4K with GPU tiers, quality ladder from real track
  heights, black-frame watchdog and failover (`y/player/HeatGuard.kt`, `Anime4kEffect.kt`, `QualityEngine.kt`,
  `BlackFrameWatchdog.kt`, `StreamRecovery.kt`).
- **Privacy and trust.** No account, no analytics, on-device crash reports, signed updates checked by
  certificate and SHA-256 (`y/update/UpdateManager.kt`), sealed keys (`y/security/SecretBox.kt`), subtitle keys
  never leak across redirects (`docs/INSPIRATION.md`).
- **Watch together on LAN without a server** (`y/watch/WatchTogether.kt`), Chromecast (`y/cast/CastSupport.kt`),
  push notices (`y/notify/FcmPush.kt`); a code search of Hikari finds no Chromecast, watch-together or push code.
- **Separate, tuned TV edition** with smaller caches and buffers (CHANGELOG v1.7.0), versus a single shared APK.
- **Honest, tested releases.** 755 unit tests in v1.8.1 and a correction release that names what did not work.
  Hikari publishes many builds a day and notes "Many others thing also added i didn't write everything in
  changelogs" ([v0.10.25](https://github.com/codegeasse1/hikari/releases/tag/v0.10.25)).
- **Built-in drama studios** with a health check (`y/sources/drama/YumeiroDrama.kt`, `DramaHealth.kt`).

---

## Recommended approval list

Approve now (small, safe, high value; all fit bridges + shared OkHttp + one Media3 player):

| # | Item | Effort | Risk |
|---|---|---|---|
| 1 | Skip segments for films and series (TheIntroDB + AniSkip + learned skips) | S | Low |
| 2 | Dialogue boost and volume boost (one Media3 AudioProcessor, presets) | S | Low |
| 3 | Filler episode marks and "skip filler" | S | Low |
| 4 | Delisted repository notice (from cs-repos removals and our block list) | S | Low |
| 5 | CloudStream library 4.9 bump behind the plugin load audit | S-M | Medium |
| 6 | Live TV guide: XMLTV EPG (streaming parser) + Xtream accounts, then catch-up | M (+S) | Medium |
| 7 | Android TV Watch Next row (TV flavour) | S | Low |
| 8 | Stats page with daily buckets | S | Low |
| 9 | Release calendar for saved titles | S | Low |

Approve next (bigger, plan first):

| # | Item | Effort | Risk |
|---|---|---|---|
| 10 | Profiles with a kids profile (shared extensions, split personal data) | M | Medium |
| 11 | Pair a TV by QR, reusing Backup v2 and watch-together discovery | S-M | Low |
| 12 | Torrent streaming, opt-in, separate process, optional engine download | L | High |
| 13 | Seekbar previews from storyboards / I-frame playlists / local files | M | Medium |
| 14 | Title logos and artwork by id only | S | Low |

Later or only if users ask: Shikimori and MDL, collections, on-device subtitle translation, FCast, the small
polish table, a light novel reader. Not now: AI dub, Telegram, proxy-based lossless music, extra extension engines.

## R16 re-check (15 November 2026)

Re-read release by release on 15 Nov 2026, 05:40 UTC. Nothing here is built; it is a proposal.

| Source | What changed since 14 Nov | Link |
|---|---|---|
| cs-repos | No commits since 1 Nov 2026; `repos-db.json` unchanged | [commits](https://github.com/recloudstream/cs-repos/commits/master) |
| CloudStream | No release after v4.9.1 (9 Nov) and the 9 Nov pre-release, both already above | [releases](https://github.com/recloudstream/cloudstream/releases) |
| Hikari releases | No tagged release after v0.12.51 (5 Nov); `clone-model` (8 Nov) already above | [releases](https://github.com/codegeasse1/hikari/releases) |
| Hikari commits 14–15 Nov | 0.12.101 "Butter pass": bundled baseline profile, 250 ms start-up budget, async language load, bigger Coil cache, off-main reads in `MainActivity`, fastest refresh rate, predictive back opt-in, home marquee paused when hidden ([d91fcb4](https://github.com/codegeasse1/hikari/commit/d91fcb4), [59be331](https://github.com/codegeasse1/hikari/commit/59be331), [e0c2c1d](https://github.com/codegeasse1/hikari/commit/e0c2c1d)). 0.12.102 "Butter art pass": TMDB art cross-fades in, rows warm the art cache while scrolling ([791c33c](https://github.com/codegeasse1/hikari/commit/791c33c), [8bada6e](https://github.com/codegeasse1/hikari/commit/8bada6e)). 0.12.103: host-side NewPipe downloader for extractor plugins ([02315e3](https://github.com/codegeasse1/hikari/commit/02315e3)). Music fixes (JioSaavn, Qobuz badge) | [commits](https://github.com/codegeasse1/hikari/commits/main) |

What this means for Yumeiro (checked in the code on `r15-stability`):

| Hikari change | Yumeiro today | Proposal | Effort | Risk |
|---|---|---|---|---|
| Bundled baseline profile | `androidx.profileinstaller` 1.4.1 is a dependency, but the app ships **no** `baseline-prof.txt` of its own (only the profiles inside AndroidX libraries) | **P1.** Add a `:baselineprofile` module (Macrobenchmark + `BaselineProfileRule`) covering cold start → Home → Yumix scroll → player open, generated on the CI emulators and committed. Measure with the existing `YuStart` lines before and after on api33-lowram | S-M | Low (build-time only) |
| Predictive back opt-in | `MainActivity` sets `android:enableOnBackInvokedCallback="false"` since the first commit, with no recorded reason | Audit in G1 H6 first: check every `BackHandler` and the player's back path on api36, then decide. Turning it on without that audit could break nested back | S | Medium |
| Off-main reads in `MainActivity` | R15/R16 already moved prefs warm-up (`PrefsWarmup`), regex tables, FCM, workers and the source-menu icons to `yu-startup` | Nothing new; keep measuring with the stress campaign (ST-6, ST-7) | — | — |
| Marquee paused when hidden | Yumeiro has no auto-moving rows; the hero carousel should stop when not visible | Check in G4 Y6 that the hero timer stops on `ON_STOP` | S | Low |
| Art cross-fade, warm art cache while scrolling | Coil with `ImageCacheSize`; no prefetch of next rows' posters | P3. Prefetch posters for the next 1–2 rows on idle, only on unmetered networks and not on low-RAM devices | S | Low |
| Host-side NewPipe downloader for plugins | Yumeiro already sets up NewPipe at app start (`YouTubePlayback.install` → `NewPipe.init(NewPipeDownloader(...))`), before extensions load | Nothing to add. Confirm in G2 that a CloudStream extension that calls the YouTube extractor works | — | — |

No change to the approval list above, except one new P1 entry: **baseline profile** (S-M, low risk), to place after item 4.

## Step A.5 notes (Part 7 bugs)

- BUG-1 watch progress: [FEATURE-BUG-1-watch-progress.md](FEATURE-BUG-1-watch-progress.md) (CloudStream 5d66bbe, NewPipe 1e61c9a).
- BUG-2 YouTube channels: [FEATURE-BUG-2-youtube-channels.md](FEATURE-BUG-2-youtube-channels.md) (NewPipe 1e61c9a, Hikari 9694add; CloudStream has no YouTube channel browser).
- BUG-3 download throughput: [FEATURE-BUG-3-download-throughput.md](FEATURE-BUG-3-download-throughput.md) (CloudStream 5d66bbe, Nuvio 04cfe4a).
- BUG-4 quality list: [FEATURE-BUG-4-quality-selection.md](FEATURE-BUG-4-quality-selection.md) (CloudStream 5d66bbe, Hikari 9694add).
