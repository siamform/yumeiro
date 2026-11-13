# Architecture

Yumeiro is one Android module (`app/`), Kotlin + Jetpack Compose, minSdk 24 (Android 7.0, for TV boxes; see docs/TV.md). CloudStream's plugin API is embedded
(`com.lagradost.cloudstream3`) and is the common contract every online source speaks.

## Big picture

```
            ┌──────────────────────── UI (Compose) ────────────────────────┐
            │ Home · Search · Details · Player · Library (Music/Videos/     │
            │ Reading) · Manga reader · Sources hub · Settings · Widget      │
            └───────────────┬──────────────────────────────┬────────────────┘
                            │ MainAPI (video)              │ MangaProvider (manga)
            ┌───────────────┴───────────── ExtensionManager.providers() ─────┐
            │  CloudStream .cs3 plugins  +  SourceHub.state.video             │
            └───────────────┬───────────────────────────────────────────────┘
                            │ publishVideo / publishManga (one key per bridge)
  ┌──────────────┬──────────┴───┬──────────────┬──────────────┬──────────────┐
  │ Aniyomi      │ Mihon        │ Stremio      │ JavaScript   │ Home servers │
  │ APK → DEX    │ APK → DEX    │ HTTP JSON    │ QuickJS      │ Jellyfin/Emby│
  │ AnimeSource  │ HttpSource   │ manifest/    │ Mangayomi    │ Plex · DLNA  │
  │ → MainAPI    │ → Manga-     │ catalog/meta/│ runtime      │ WebDAV · SMB │
  │              │   Provider   │ stream       │ → MainAPI /  │ → MainAPI    │
  │              │              │ → MainAPI    │ MangaProvider│ (TreeProvider)│
  └──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

Rule: a bridge converts its ecosystem into `MainAPI` (or `MangaProvider`) and publishes it under its own key in
`SourceHub`. Bridges never call each other, so one broken ecosystem only empties its own key. The rest of the app
(For you, search, details, player, downloads, history, bookmarks, widget, watch together) needs no per-ecosystem code.

## Packages

| Package | What lives there |
|---|---|
| `sources/UniversalAdd.kt`, `ui/screens/sources/UniversalAddUi.kt` | "Add anything": one field, file or incoming link for every format. Detects CloudStream plugin, repo or list, Aniyomi/Mihon repo or APK, Stremio, Mangayomi JS, Jellyfin/Emby/Plex and routes to the right engine. See EXTENSION-COMPAT.md. |
| `com/lagradost/cloudstream3/extractors/`, `utils/SubtitleHelperPlatformJvm.kt` | Extractors removed from CloudStream library 4.8 and a JVM shim, restored so older plugins still link. |
| `sources/SourceHub.kt`, `Bridges.kt` | Registry of bridged sources and their ecosystem badge; starts all bridges once per process. |
| `sources/tachiyomi/` | `TachiyomiExtensions` (repos, `index.min.json`, APK download, same-signer check on update, DEX load), `TachiyomiRuntime` (Injekt graph extensions expect), `AniyomiProvider`, `MihonMangaProvider`. |
| `eu/kanade/tachiyomi/**`, `tachiyomi/**`, `mihon/**` | Vendored Aniyomi/Mihon source API (Apache-2.0) the extension APKs link against. Keep class names; ProGuard keeps them. |
| `sources/stremio/` | `StremioAddons` (install, manifest cache) and `StremioProvider` (catalog → home rows, meta → episodes, stream → links). |
| `sources/js/` | `JsSources` (repos, `index.json`), `JsEngine` (QuickJS, one context per source), `JsBridge` (answers `sendMessage` calls: http, DOM, crypto, unpacker), `JsProviders`. Runtime shims in `assets/js/mangayomi_runtime.js`. |
| `sources/servers/` | `HomeServers` (saved servers), `JellyfinProvider` (Jellyfin + Emby), `PlexProvider`, `MediaTree` + `TreeProvider` for DLNA, WebDAV and SMB (`SmbProxy` serves SMB files to the player over a local token-protected HTTP port). |
| `sources/manga/` | `MangaProvider` interface, `MangaLibrary` (saved manga + reading progress). |
| `tracking/` | `Kitsu`, `Trakt`. AniList and MyAnimeList live in `online/anime/`. `AnimeSync` fans progress out to every signed-in tracker. |
| `backup/Backup.kt` | Zip export/restore of prefs and files (allow-list + zip-slip check). |
| `security/Privacy.kt`, `ui/screens/extras/AppLockGate.kt` | Incognito and app lock (BiometricPrompt with device credential). |
| `watch/WatchTogether.kt` | LAN rooms: NSD advertisement, small TCP JSON protocol, `RoomState` with host-clock extrapolation. |
| `widget/ContinueWidget.kt` | RemoteViews widget fed by `OnlineHistory`; `TitleNav` opens a title from a widget tap. |
| `ui/screens/sources/` | Sources hub (5 tabs) and Source status. |
| `ui/screens/library/LibraryDesign.kt` | Library hero, tabs, tiles, mosaics. |
| `update/` | Self-updater: GitHub API, SHA256SUMS + signing-cert check, PackageInstaller. |

## Data flow for one episode

1. A bridge publishes `MainAPI`s → `ExtensionManager.providers()` includes them.
2. Home/Search call `getMainPage` / `search` with the CloudStream crash guard and timeouts.
3. Details → `load()` → episodes; Player → `loadLinks()` → `ExtractorLink`s → Media3.
4. Progress → `OnlineHistory` (skipped in incognito) → widget refresh → `AnimeSync` → trackers.
5. In a watch-together room the host broadcasts `RoomState`; guests open the same `api/url/index` and seek to `positionAt(hostNow)`.

## Security model

- Extension code (DEX from Aniyomi/Mihon/CloudStream) runs in-process, like in those apps. Users add repos themselves; the market lists only repos that pass the `extension-market` workflow.
- JavaScript sources get no file system and no direct sockets; all network goes through OkHttp.
- Stremio add-ons are data only (HTTP JSON); no code runs.
- Backups only restore into `shared_prefs/` and `files/`.
- Updates are verified against the release signing certificate and SHA-256 before install.
