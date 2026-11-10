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

## 0.5 Reliability follow-ups (after v1.0.5)

| Area | Item |
|---|---|
| Push | ~~Optional UnifiedPush~~ **Done (HANDOVER §17)**: serverless wake-ups via ntfy scheduled delivery. Next: let the "App feed" workflow push announcements to subscribed users instantly (needs opt-in topic sharing). |
| Playback | Feed `BlackFrameWatchdog` hits into source ranking, so links that went black are tried last next time. |
| Network | Show which hosts use the automatic DNS bypass in Settings → Network, with a "forget" button. |
| Settings | Device pass over the new Settings pages and guides (small screens, large font). Translate the setup guides (Bangla first). |
| YouTube | Optional YouTube Music library / subscriptions sync on top of the account (only if users ask; keep it opt-in). Validate the account with an InnerTube call after sign-in, like ArchiveTune (pasted cookies are already checked against youtube.com/account). |
| Search | Collapse the same title from several Hues into one card with "on N Hues" (data is in `SearchRanker.Ranked.alsoOn`). |

## 0.6 After the playback fixes

- [ ] Test on real phones: AC-3/E-AC-3 streams, audio switching on HLS with separate audio, subtitle delay.
- [ ] Extension scanner: tune rules with real extension repos (false positives), consider a community allow-list.
- [ ] Sign-in: per-extension account notes (which sites need an account) in the Hues market.

## 0.7 After subtitles / download managers / ad shield

- [ ] Test on real phones: SubDL and SubSource with real free keys (season packs, anime numbering), 1DM and ADM hand-off with header-protected links, the captcha page on sites with pop-under ads.
- [ ] Built-in torrent streaming: see [proposals/TORRENT.md](proposals/TORRENT.md) (libtorrent4j + loopback stream server, opt-in).
- [ ] SubDL / SubSource: optional search by TMDB id when an extension provides it.

## 0.8 After Simkl / Last.fm / LRCLIB (§22)

- [ ] Test on real phones: Simkl device sign-in and a watched anime episode + movie, Last.fm sign-in, scrobble after
  half a song, offline queue sent later, LRCLIB lyrics on a YouTube song and on a local file without tags.
- [ ] Scrobbling runs while the player UI exists; move `Scrobbler.watch` into `PlaybackService` so screen-off,
  app-swiped sessions scrobble too.

## 1. Universal extension engine (the differentiator)

Make Yumeiro the hub that runs the big ecosystems side by side, each behind one adapter interface
(`SourceAdapter`: home, search, details, episodes, links, subtitles) so the UI never knows the format.

| Ecosystem | Format | How | Notes |
|---|---|---|---|
| CloudStream | `.cs3` (DEX) | done | keep crash guard |
| Aniyomi anime | APK with `AnimeHttpSource` | done (v1.0.5) | biggest anime catalogue |
| Mihon / Tachiyomi manga | APK with `HttpSource` | done (v1.0.5), with reader | opens manga/manhwa audience |
| Stremio addons | HTTP JSON (`/manifest.json`, `/catalog`, `/meta`, `/stream`, `/subtitles`) | done (v1.0.5), HTTP streams only | safest format, huge community |
| Mangayomi / Dartotsu | JavaScript | done (v1.0.5), QuickJS | no native code |
| Jellyfin / Plex / DLNA / SMB | server APIs | done (v1.0.5), plus Emby and WebDAV | home media users |

Shared on top: one market for all formats (done: Discover lists in every Sources tab, from `sources.json`), per-source health score, auto-fallback, sub/dub and quality
preferences, unified search across every installed source, de-duplicated by AniList/TMDB id.

Done: Mihon's v2 store index (`repo.json` → `index.pb` / `index.json`) and extensions-lib 1.6, so Keiyoushi works (HANDOVER §27). Optional: Mihon's native image decoder, only for AVIF/JPEG XL/HDR pages in 4 extensions (they otherwise use Android's decoder). Still open: unified search de-duplicated by AniList/TMDB id, auto-fallback to another source, Dartotsu-specific APIs.

Done in v1.0.5 from the lists below: MyAnimeList, Kitsu and Trakt tracking; LAN watch together; backup and restore
(local file); app lock and incognito; continue-watching widget; extension author kit; public status page;
README "what's different" table; F-Droid/IzzyOnDroid metadata. Simkl, Last.fm scrobbling and LRCLIB lyrics landed
in §22. Still open: two-way sync, watch together over
the internet (needs a free relay), Google Drive/WebDAV backup target, kids profile, now-playing widget, a dedicated
TV layout, Weblate localisation.

## 2. Features that set it apart

- Tracking: AniList, MyAnimeList, Kitsu, Trakt, Simkl (all exist); two-way sync of progress.
- Watch together (synced playback room over a link), and cast (exists) with queue.
- Smart library: one entry per title across sources; "available on 3 sources" with best one picked.
- Downloads: background queue (done), Wi-Fi only, storage location choice (SD card / SAF folder), export to gallery.
- Player: per-title audio/sub memory, AniSkip (exists), auto next episode, picture-in-picture everywhere, gesture presets.
- Backup and restore (local file + optional Google Drive/WebDAV), including extensions, repos, history, settings.
- App lock (biometric), incognito mode, per-profile libraries (kids profile with adult content hard-off).
- Widgets: continue watching, now playing; Android TV / large-screen layout.
- Localisation (Bangla first, then the top 10 languages) through Weblate.

## 0.9 Yumeiro Cloud (§23, docs/SERVER.md)

- [x] Deployed to `api.yumeiro.click` on the free plan (§24). Server scope fixed at alerts only.
- [x] Airing times come from phones; the server no longer calls AniList, which blocks Workers (§25).
- [ ] Owner: add `/v1/health` to a free uptime monitor (SERVER.md "Is it working?").
- [ ] Owner: rotate the Cloudflare API token that was shared in chat.
- [ ] Phone test: push on via ntfy, bookmark an airing anime, confirm a wake ~30 min after AniList's airing time.
- [ ] Watch D1 / Queue usage in the Cloudflare dashboard the first week; tune cron or batch if needed.

## 2.5 Serverless versions of Sozo's best ideas (see INSPIRATION.md)

- [ ] Trailer "Shorts" feed from TMDB `/videos` (key is in place: `TMDB_API_KEY`), vertical pager, tap to open the title.
- [ ] Stats, streaks and a yearly recap computed from local watch / listen history (no account, nothing uploaded).
- [ ] On-device subtitle translation (ML Kit on-device translation is free; check model size and licence first).
- [ ] ListenBrainz as a second scrobbler (token only, no secret), reusing `ScrobbleTracker` and the offline queue.
- [ ] Last.fm: love / unlove from Now Playing, scrobble from the background service when the UI is closed.
- [ ] Link a TV by QR over the LAN (reuse watch-together discovery).

## 3. Growth and community

- Extension author kit: template repo + docs + test runner, listed in the market with credit.
- Public status page of source health (from the market workflow).
- Discord/Telegram, GitHub Discussions, monthly release notes in the in-app inbox (feed exists).
- Clear "what is different" table in README (everything in one app, any extension format, safety guard).
