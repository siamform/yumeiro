# Update: universal sources (v1.0.5)

Branch `feat/universal-sources`. One pull request, merged once everything below was in place.

## What's new for users

| Area | What you get | Where in the app |
|---|---|---|
| Aniyomi anime | Install Aniyomi extension APKs from any repo (`index.min.json`). They run as normal sources: Home, Search, details, player, downloads, history. | Sources hub → Aniyomi |
| Mihon / Tachiyomi manga | Mihon extension APKs plus a manga home, detail page and a vertical/paged reader with progress. | Sources hub → Mihon, Library → Reading |
| Stremio add-ons | Paste a `manifest.json` or `stremio://` link. Catalogs, metadata, HTTP streams and subtitles. Torrent-only streams are skipped. | Sources hub → Stremio |
| JavaScript sources | Mangayomi-format JavaScript sources run in a sandboxed QuickJS engine; network only through the app's client. | Sources hub → JavaScript |
| Home servers | Jellyfin, Emby, Plex, DLNA/UPnP (auto-discovered on Wi-Fi), WebDAV and SMB shares. Folders sort naturally (Episode 2 before Episode 10). | Sources hub → Home servers |
| Tracking | AniList (existing), MyAnimeList, Kitsu (sign in with your Kitsu account), Trakt (device code; needs a free Trakt API app). Progress is sent at about 85% of an episode. | Settings → Tracking |
| Backup and restore | One zip with settings, history, repositories, add-ons, servers and (optionally) installed extensions. Zip-slip safe. | Settings → Backup & restore |
| Privacy | App lock with the device screen lock (biometric or PIN), incognito mode that records no history and hides the widget. | Settings → Privacy |
| Watch together | Host a room on the same Wi-Fi (found automatically, or join by address and 4-digit code). Play, pause, seek and episode changes follow the host. No server. | Library → Videos → Watch together, or Settings → Watch together |
| Widget | "Continue watching" home-screen widget with poster; tapping opens the title. | Home screen |
| Android TV | The existing leanback launcher entry and banner. The new screens use the same D-pad-friendly components. | TV launcher |
| Source status | On-device check of every installed source (home + search, 25 s timeout), shareable report, and a public page for every market repo. | Settings → Source status; [public page](https://yumeiro-phi.vercel.app/status) |
| Library redesign | Hero header with stats, three tabs (Music, Videos, Reading), playlist mosaics, shelf covers. | Library |
| Updates | Fixed "504 Gateway Time-out" when downloading an update: the updater now uses the GitHub API asset link first and the website `/download` route is the public download link. | Settings → Updates |

## For the community

- `extension-kit/`: working samples for a static Stremio add-on and a JavaScript source, plus pointers for CloudStream, Aniyomi and Mihon templates.
- Public status page: <https://yumeiro-phi.vercel.app/status>, fed by the existing `extension-market` workflow (every 3 days, no new Actions minutes).
- F-Droid / IzzyOnDroid metadata in `fastlane/metadata/android/en-US/`. See [DISTRIBUTION.md](DISTRIBUTION.md).
- "What's different" table in the README.

## Known limits

- Not yet tested on a physical device for every ecosystem; run the Source status check after installing.
- Self-signed HTTPS on home servers is not accepted (use HTTP on the LAN or a real certificate).
- No torrent streaming.
- Trakt and MyAnimeList need a free API client id that the user (or a builder of the APK) supplies; nothing is paid.
- Everything is free and serverless: no backend, no Play Console. Watch together works only on the same network.
