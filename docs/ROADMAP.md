# Yumeiro roadmap

Goal: the one Android app that plays everything (local, YouTube, YouTube Music, IPTV, any extension format),
organised better than any single-source app. Ordered by impact for launch.

## 0. Before promoting (must do)

| Area | Item |
|---|---|
| Stability | Device test pass (Android 8, 10, 13, 14, 15; one Xiaomi/Samsung with aggressive battery rules). Crash reporter that stays on device and lets the user share a report (ACRA-style, opt-in). |
| Security | Signed release builds with a published SHA-256 certificate fingerprint in README; reproducible-build notes. Extension install shows source repo, author, permissions-like summary and a warning for unverified repos. Keep `TrustedSources` HTTPS allow-list; add certificate pinning for the update and feed hosts. |
| Privacy | One-page privacy policy (no analytics, what goes over the network, per-feature). Export/delete all local data. |
| Fallbacks | Every network feature has an offline state, retry, and "open in browser" escape hatch. Extension failure falls through to the next source automatically (ranked by recent success). |
| Docs | Install guide with screenshots, FAQ ("why is a source empty", "battery settings for notifications"), CONTRIBUTING, issue templates, extension author guide. |
| Release | Changelog per version, F-Droid/IzzyOnDroid listing, Obtainium link, GitHub Releases with split APKs. |

## 1. Universal extension engine (the differentiator)

Make Yumeiro the hub that runs the big ecosystems side by side, each behind one adapter interface
(`SourceAdapter`: home, search, details, episodes, links, subtitles) so the UI never knows the format.

| Ecosystem | Format | How | Notes |
|---|---|---|---|
| CloudStream | `.cs3` (DEX) | done | keep crash guard |
| Aniyomi anime | APK with `AnimeHttpSource` | load APK DEX with a compat stub of extensions-lib (Apache-2.0) | biggest anime catalogue |
| Mihon / Tachiyomi manga | APK with `HttpSource` | same loader + a reader screen | opens manga/manhwa audience |
| Stremio addons | HTTP JSON (`/manifest.json`, `/catalog`, `/meta`, `/stream`, `/subtitles`) | pure Kotlin client, no code execution | safest format, huge community |
| Mangayomi / Dartotsu | JavaScript | sandboxed QuickJS runtime, network only through our client | no native code |
| Jellyfin / Plex / DLNA / SMB | server APIs | "My servers" source | home media users |

Shared on top: one market for all formats, per-source health score, auto-fallback, sub/dub and quality
preferences, unified search across every installed source, de-duplicated by AniList/TMDB id.

## 2. Features that set it apart

- Tracking: AniList (exists), add MyAnimeList, Kitsu, Simkl, Trakt; two-way sync of progress.
- Watch together (synced playback room over a link), and cast (exists) with queue.
- Smart library: one entry per title across sources; "available on 3 sources" with best one picked.
- Downloads: background queue (done), Wi-Fi only, storage location choice (SD card / SAF folder), export to gallery.
- Player: per-title audio/sub memory, AniSkip (exists), auto next episode, picture-in-picture everywhere, gesture presets.
- Backup and restore (local file + optional Google Drive/WebDAV), including extensions, repos, history, settings.
- App lock (biometric), incognito mode, per-profile libraries (kids profile with adult content hard-off).
- Widgets: continue watching, now playing; Android TV / large-screen layout.
- Localisation (Bangla first, then the top 10 languages) through Weblate.

## 3. Growth and community

- Extension author kit: template repo + docs + test runner, listed in the market with credit.
- Public status page of source health (from the market workflow).
- Discord/Telegram, GitHub Discussions, monthly release notes in the in-app inbox (feed exists).
- Clear "what is different" table in README (everything in one app, any extension format, safety guard).
