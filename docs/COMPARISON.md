# How Yumeiro compares

## One app instead of five

Great apps exist for each piece of the puzzle — **CloudStream** for extensions, **ArchiveTune**, **InnerTune** and
**Metrolist** for YouTube Music, **NewPipe** and **LibreTube** for YouTube, **VLC** and **MX Player** for local video,
**TiviMate** for IPTV. Each one does its own job well, and each one stops there.

**Yumeiro brings all of them together in one app** — one library, one player, one history, one design:

| | What you get |
|---|---|
| **Everything in one place** | Local music and video, YouTube, YouTube Music, IPTV/M3U and CloudStream `.cs3` extensions share one player, one queue and one *Continue watching* row. |
| **Your existing extensions work** | CloudStream extension repos install as they are, with a built-in market, auto-updates, sub/dub, seasons and a crash guard, so one broken extension can't take the app down. |
| **Fixes refused streams for you** | Before showing an error, Yumeiro retries the link with different headers, fetches fresh links, and if needed captures the stream from the page itself. |
| **Offline downloads** | Save a whole season or the next five episodes into one folder per series (`Solo Leveling-(HiAnime-(Sub))`), pin what you want to keep, and let an auto-delete timer clear the rest. YouTube videos in every quality up to 4K, or music as audio only. Prefer **1DM** or **ADM**? Pick it in Settings → Downloads and episodes go straight to it, with the right headers. |
| **Every extension format** | CloudStream `.cs3`, Aniyomi anime and Mihon/Tachiyomi manga APKs, Stremio add-ons and Mangayomi-style JavaScript sources, side by side in one Sources hub. |
| **Add anything** | Paste any link or short code, or open any file: Yumeiro detects whether it is a CloudStream plugin, repository or list, an Aniyomi or Mihon repository or APK, a Stremio add-on, a JavaScript repository or a Jellyfin, Emby or Plex server, and installs it in the right place. 2,247 of the 2,248 CloudStream extensions in the market load ([compatibility audit](EXTENSION-COMPAT.md)). |
| **A market for every format** | No more hunting for repo links: each Sources tab has a Discover list of repositories and add-ons found on GitHub and in Stremio's catalogs, ranked by stars, recent updates and size, with search, sort and one-tap Add. Pasting a link by hand still works. |
| **Your home servers** | Jellyfin, Emby, Plex, DLNA/UPnP, WebDAV and SMB shares show up as sources, with natural episode order. |
| **Track, back up, stay private** | AniList, MyAnimeList, Kitsu and Trakt; one-file backup and restore; app lock and incognito mode. |
| **Watch together** | Rooms on the same Wi-Fi keep play, pause and seek in sync. No account, no server. |
| **No account needed** | YouTube and YouTube Music — search, quick picks, background play — with no Google account (signing in is optional). |
| **Live catalogs** | The playlist market, extension market and in-app news update without a new app release. |
| **Safe updates** | Releases are signed with one key, and the in-app updater checks the signing certificate and SHA-256 before it installs anything. |
| **Designed, not assembled** | The *Tsuki* design system, background glow that takes its colour from the artwork, one floating dock, and smooth 60 fps. |


## Yumeiro vs. the usual stack

| | Yumeiro | Typical music player | Typical video player | YouTube app |
|---|:---:|:---:|:---:|:---:|
| Local music + video in one app | ✓ | – | – | – |
| YouTube & YouTube Music, no login | ✓ | – | – | needs account for most features |
| CloudStream extensions | ✓ | – | – | – |
| IPTV / M3U playlists | ✓ | – | some | – |
| Synced lyrics, equalizer | ✓ | ✓ | – | – |
| No ads, no tracking, no account | ✓ | varies | varies | – |
| Seamless self-updates | ✓ | – | – | via store |

## What's different

How Yumeiro compares with the apps it learns from. All of them are great at their own job; the point is having it in one place.

| | Yumeiro | Aniyomi | Mihon | CloudStream | Stremio | Kodi | Jellyfin app |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| CloudStream `.cs3` extensions | ✓ | – | – | ✓ | – | – | – |
| Aniyomi anime extensions | ✓ | ✓ | – | – | – | – | – |
| Mihon / Tachiyomi manga extensions + reader | ✓ | ✓ | ✓ | – | – | – | – |
| Stremio add-ons (HTTP streams) | ✓ | – | – | – | ✓ | – | – |
| JavaScript sources (Mangayomi format) | ✓ | – | – | – | – | – | – |
| Jellyfin / Plex / DLNA / WebDAV / SMB | ✓ | – | – | – | – | ✓ | Jellyfin only |
| Local music + video player | ✓ | – | – | – | – | ✓ | – |
| YouTube & YouTube Music, no login | ✓ | – | – | – | – | add-on | – |
| IPTV / M3U | ✓ | – | – | – | – | ✓ | Live TV via server |
| AniList, MAL, Kitsu, Trakt tracking | ✓ | ✓ | ✓ | AniList, MAL | Trakt | add-on | – |
| Backup / app lock / incognito | ✓ | ✓ | ✓ | backup | – | – | – |
| Watch together without a server | ✓ (same Wi-Fi) | – | – | – | – | – | SyncPlay via server |
| Crash guard for broken extensions | ✓ | – | – | partial | n/a | – | n/a |
| Public source status page | [✓](https://yumeiro.click/status) | – | – | – | – | – | – |

Torrent links (magnet links, raw info-hashes and Stremio torrent add-ons) play through your own TorrServer (Settings → Video → Video quality & playback). A built-in torrent engine is in testing and off by default.

<sub>Back to the [README](../README.md).</sub>
