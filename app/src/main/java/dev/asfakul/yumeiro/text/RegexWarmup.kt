/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.text

/**
 * R15: the pattern tables live in objects, and an object's patterns compile on first touch. The
 * first touch used to be a card being composed (release tags under a poster, audio-language
 * chips, Yumix feed rules) — on the main thread, during pager prefetch. The ANR trace from the
 * Xiaomi showed exactly that (`Pattern.compile` under `LazyLayoutPinnableItem`). Touching them
 * here, on the startup background thread, moves that one-time cost off the UI.
 */
object RegexWarmup {
    @Volatile var done = false
        private set

    fun run() {
        if (done) return
        // Each reference forces the object's static initialiser (its Regex constants) to run now.
        listOf<Any>(
            dev.asfakul.yumeiro.online.ReleaseTags,
            dev.asfakul.yumeiro.online.AudioLanguages,
            dev.asfakul.yumeiro.online.SourceFacts,
            dev.asfakul.yumeiro.online.EpisodeMatcher,
            dev.asfakul.yumeiro.ui.screens.online.YumixFeed,
            dev.asfakul.yumeiro.stream.LinkHosts,
            dev.asfakul.yumeiro.stream.WebPageExtractor,
            dev.asfakul.yumeiro.ui.components.Kana,
        ).forEach { it.hashCode() }
        // Every other class that holds a pattern constant (kept complete by RegexGuardTest).
        val loader = RegexWarmup::class.java.classLoader
        for (name in CLASSES) runCatching { Class.forName(name, true, loader) }
        // A real parse, so lazily built members are ready too.
        runCatching { dev.asfakul.yumeiro.online.ReleaseTags.parse("Show S01E01 1080p WEB-DL HEVC x265 DDP5.1 Dual Audio") }
        runCatching { dev.asfakul.yumeiro.ui.components.Kana.forTitle("Home") }
        done = true
    }

    /** JVM names of the classes whose static initialiser compiles patterns. */
    val CLASSES: List<String> = listOf(
        // UI classes first: a screen's first composition is what touches them.
        "com.lagradost.cloudstream3.ui.home.HomeViewModelKt",
        "dev.asfakul.yumeiro.ui.components.ErrorHelp",
        "dev.asfakul.yumeiro.ui.components.SearchSuggestKt",
        "dev.asfakul.yumeiro.ui.screens.home.HomeOnlineKt",
        "dev.asfakul.yumeiro.ui.screens.music.MusicMixKt",
        "dev.asfakul.yumeiro.ui.screens.music.MusicScreenKt",
        "dev.asfakul.yumeiro.ui.screens.online.OnlineHomeHeroKt",
        "dev.asfakul.yumeiro.ui.screens.online.YumixFeed",
        "dev.asfakul.yumeiro.ui.screens.online.YumixFeed\$Category",
        "dev.asfakul.yumeiro.online.web.WebDownloads\$Holder",
        "dev.asfakul.yumeiro.ui.screens.online.YumixFilterKt",
        "dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalogKt",
        "dev.asfakul.yumeiro.ui.screens.stream.StreamPlaylistUiKt",
        "dev.asfakul.yumeiro.ui.screens.stream.StreamScreenKt",
        "dev.asfakul.yumeiro.ui.screens.watch.StreamWatch",
        "dev.asfakul.yumeiro.ui.screens.watch.StreamWatchPanelKt",
        "dev.asfakul.yumeiro.ui.screens.watch.WatchKind",
        "dev.asfakul.yumeiro.update.ui.UpdateFormatKt",
        "com.lagradost.cloudstream3.extractors.VidSrcExtractorKt",
        "com.lagradost.cloudstream3.network.DdosGuardKillerKt",
        "com.lagradost.cloudstream3.plugins.PluginManager",
        "com.lagradost.cloudstream3.plugins.RepositoryManagerKt",
        "dev.asfakul.yumeiro.data.repository.MediaRepositoryKt",
        "dev.asfakul.yumeiro.diagnostics.CrashLog",
        "dev.asfakul.yumeiro.lyrics.LrcParser",
        "dev.asfakul.yumeiro.lyrics.LyricsKt",
        "dev.asfakul.yumeiro.lyrics.TrackDetailsRepositoryKt",
        "dev.asfakul.yumeiro.network.SmartDnsKt",
        "dev.asfakul.yumeiro.notify.FcmPlan",
        "dev.asfakul.yumeiro.online.AudioLanguages",
        "dev.asfakul.yumeiro.online.EpisodeMatcherKt",
        "dev.asfakul.yumeiro.online.ExtensionFormats",
        "dev.asfakul.yumeiro.online.ExtensionManagerKt",
        "dev.asfakul.yumeiro.online.ExtensionMarketKt",
        "dev.asfakul.yumeiro.online.ExtensionModelsKt",
        "dev.asfakul.yumeiro.online.ExtensionScanner",
        "dev.asfakul.yumeiro.online.ExtensionScannerKt",
        "dev.asfakul.yumeiro.online.OnlinePlaybackKt",
        "dev.asfakul.yumeiro.online.OnlineSessionKt",
        "dev.asfakul.yumeiro.online.OnlineRepositoryKt",
        "dev.asfakul.yumeiro.online.ReleaseTags",
        "dev.asfakul.yumeiro.online.SearchRankerKt",
        "dev.asfakul.yumeiro.online.SourceFacts",
        "dev.asfakul.yumeiro.online.UrlFixKt",
        "dev.asfakul.yumeiro.online.anime.AnimeIdsKt",
        "dev.asfakul.yumeiro.online.download.DownloadNaming",
        "dev.asfakul.yumeiro.online.download.DownloadNamingKt",
        "dev.asfakul.yumeiro.online.download.ExternalDownloaderKt",
        "dev.asfakul.yumeiro.online.download.HlsDownloaderKt",
        "dev.asfakul.yumeiro.online.download.OnlineDownloads",
        "dev.asfakul.yumeiro.online.subtitles.SubDLKt",
        "dev.asfakul.yumeiro.online.subtitles.SubSourceKt",
        "dev.asfakul.yumeiro.online.subtitles.SubtitleArchiveKt",
        "dev.asfakul.yumeiro.cast.SrtToVtt",
        "dev.asfakul.yumeiro.online.subtitles.SubtitleHubKt",
        "dev.asfakul.yumeiro.online.web.AdFilter",
        "dev.asfakul.yumeiro.stream.TeraBoxApi", // B-29 jsToken pattern
        "dev.asfakul.yumeiro.stream.DiskWalaApi", // B-30 share id pattern
        "dev.asfakul.yumeiro.online.ExtensionPopups", // ADR-014 classifier words (first touch is a window check on main)
        "dev.asfakul.yumeiro.scrobble.ScrobbleRulesKt",
        "dev.asfakul.yumeiro.scrobble.TrackNames",
        "dev.asfakul.yumeiro.sources.UniversalAdd",
        "dev.asfakul.yumeiro.sources.UniversalAddKt",
        "dev.asfakul.yumeiro.sources.drama.DramaHealth",
        "dev.asfakul.yumeiro.sources.drama.DramaHealthKt",
        "dev.asfakul.yumeiro.sources.drama.OnShortCatalogKt",
        "dev.asfakul.yumeiro.sources.drama.YumeiroDrama",
        "dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider", // OWN-9 adult words, ids, query cleaning
        "dev.asfakul.yumeiro.sources.manga.MangaTlPlan", // F-11 bubble filter (first touch is the reader)
        "dev.asfakul.yumeiro.online.telegram.TelegramPublicKt", // F-02 public post / channel name
        "dev.asfakul.yumeiro.sources.js.JsBridgeKt",
        "dev.asfakul.yumeiro.sources.js.JsProvidersKt",
        "dev.asfakul.yumeiro.sources.js.JsUnpacker",
        "dev.asfakul.yumeiro.sources.servers.TreeProviderKt",
        "dev.asfakul.yumeiro.sources.stremio.StremioProviderKt",
        "dev.asfakul.yumeiro.sources.tachiyomi.AniyomiProviderKt",
        "dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensionsKt",
        "dev.asfakul.yumeiro.stream.EpgGuide",
        "dev.asfakul.yumeiro.stream.AdFilterDataSource",
        "dev.asfakul.yumeiro.stream.HlsAdFilter",
        "dev.asfakul.yumeiro.stream.IncomingMediaKt",
        "dev.asfakul.yumeiro.stream.KvsPlayer",
        "dev.asfakul.yumeiro.stream.LinkHostsKt",
        "dev.asfakul.yumeiro.stream.LinkHosts", // T-1 info-hash patterns
        "dev.asfakul.yumeiro.stream.torrent.TorrentPlan", // B-31 info-hash, episode in file names
        "dev.asfakul.yumeiro.stream.LiveCountry", // Live TV country filter
        "dev.asfakul.yumeiro.profiles.Profiles", // F-19 answer normalising
        "dev.asfakul.yumeiro.stream.M3uParser",
        "dev.asfakul.yumeiro.stream.PlaylistMarketKt",
        "dev.asfakul.yumeiro.stream.PlaylistStoreKt",
        "dev.asfakul.yumeiro.stream.SubtitleSniffer",
        "dev.asfakul.yumeiro.stream.WebPageExtractor",
        "dev.asfakul.yumeiro.stream.WebPageExtractorKt",
        "dev.asfakul.yumeiro.subtitles.OnlineSubtitlesKt",
        "dev.asfakul.yumeiro.subtitles.SubtitleSitesProviderKt",
        "dev.asfakul.yumeiro.update.GitHubReleaseClientKt",
        "dev.asfakul.yumeiro.update.UpdateManagerKt",
        "dev.asfakul.yumeiro.youtube.YouTubePlaybackKt",
        "dev.asfakul.yumeiro.youtube.YtCookiePaste",
        "dev.asfakul.yumeiro.youtube.YtDiscoveryKt",
        "dev.asfakul.yumeiro.youtube.YtWatchKt",
        "dev.asfakul.yumeiro.youtube.music.YmParser",
        "dev.asfakul.yumeiro.youtube.music.YmParserKt",
        "dev.asfakul.yumeiro.youtube.music.YtMusicApi",
        "eu.kanade.tachiyomi.animesource.model.Video",
    )
}
