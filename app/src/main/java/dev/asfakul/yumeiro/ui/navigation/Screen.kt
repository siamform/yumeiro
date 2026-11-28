/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Music : Screen("music")
    data object Videos : Screen("videos")
    data object Stream : Screen("stream")
    data object WebSites : Screen("websites")
    /** F-02 split: saved public Telegram channels on their own page. */
    data object Telegram : Screen("telegram")
    /** B15: Live TV → Tools → Sports (live scores and fixtures). */
    data object Sports : Screen("stream/sports")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object Equalizer : Screen("equalizer")
    /** One stream playlist on its own page; slug = playlist id or "opened" for an unsaved list. */
    data object PlaylistDetail : Screen("stream/playlist/{slug}") {
        const val ARG = "slug"
        fun route(slug: String): String = "stream/playlist/" + android.net.Uri.encode(slug)
    }
    data object Market : Screen("stream/market")
    /** Videos → Online. */
    data object OnlineSearch : Screen("videos/online/search")
    data object OnlineTitle : Screen("videos/online/title?api={api}&url={url}") {
        const val ARG_API = "api"
        const val ARG_URL = "url"
        fun route(api: String, url: String): String =
            "videos/online/title?api=" + android.net.Uri.encode(api) + "&url=" + android.net.Uri.encode(url)
    }
    /** "See all" for one row of an extension's home page. */
    data object OnlineRow : Screen("videos/online/row?api={api}&title={title}&name={name}&data={data}") {
        const val ARG_API = "api"
        const val ARG_TITLE = "title"
        const val ARG_NAME = "name"
        const val ARG_DATA = "data"
        fun route(api: String, title: String, name: String, data: String): String =
            "videos/online/row?api=" + android.net.Uri.encode(api) + "&title=" + android.net.Uri.encode(title) +
                "&name=" + android.net.Uri.encode(name) + "&data=" + android.net.Uri.encode(data)
    }
    /** "See all" for Continue watching. */
    data object OnlineHistory : Screen("videos/online/history")
    /** Group W (B1): "Find something": catalog results with filters (the filter travels as [DiscoverFilter.encode]). */
    data object OnlineDiscover : Screen("videos/online/discover?f={f}") {
        const val ARG_F = "f"
        fun route(f: dev.asfakul.yumeiro.sources.hub.DiscoverFilter = dev.asfakul.yumeiro.sources.hub.DiscoverFilter()): String =
            "videos/online/discover?f=" + android.net.Uri.encode(f.encode())
    }
    /** Group W (B2): every genre and theme, searchable, by kind. */
    data object OnlineGenres : Screen("videos/online/genres?k={k}") {
        const val ARG_K = "k"
        fun route(kind: dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind = dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ALL): String =
            "videos/online/genres?k=" + kind.name.lowercase()
    }
    /** Group W (B3): every streaming service in the chosen country. */
    data object OnlineServices : Screen("videos/online/services")
    /** F-08: release calendar. */
    data object OnlineUpcoming : Screen("videos/online/upcoming")
    /** F-17: profile statistics. */
    data object Stats : Screen("library/stats")
    /** My List, downloads and watch history for videos. */
    data object OnlineLibrary : Screen("videos/library")
    /** Offline downloads: Permanent / Offline groups, settings and tips. */
    data object Downloads : Screen("videos/downloads")
    /** Saved manga and continue reading (was Library's "Reading" tab). */
    data object ReadingLibrary : Screen("library/reading")
    data object Extensions : Screen("videos/online/extensions")
    data object ExtensionMarket : Screen("videos/online/market")
    /** Aniyomi / Mihon / Stremio / JavaScript / home servers. */
    data object SourcesHub : Screen("sources?tab={tab}") {
        const val ARG = "tab"
        /** GROUP D: -1 = the Source overview; 0 Aniyomi, 1 Mihon, 2 Stremio, 3 JavaScript, 4 Home servers, 5 Script repos. */
        fun route(tab: Int = -1): String = "sources?tab=$tab"
    }
    data object Manga : Screen("manga")
    data object MangaTitle : Screen("manga/title?p={p}&u={u}&t={t}&i={i}") {
        fun route(provider: String, url: String, title: String, cover: String? = null): String =
            "manga/title?p=" + android.net.Uri.encode(provider) + "&u=" + android.net.Uri.encode(url) + "&t=" + android.net.Uri.encode(title) +
                "&i=" + android.net.Uri.encode(cover.orEmpty())
    }
    data object MangaReader : Screen("manga/read?p={p}&u={u}&t={t}&c={c}&n={n}") {
        fun route(provider: String, url: String, title: String, chapter: String, name: String): String =
            "manga/read?p=" + android.net.Uri.encode(provider) + "&u=" + android.net.Uri.encode(url) + "&t=" + android.net.Uri.encode(title) +
                "&c=" + android.net.Uri.encode(chapter) + "&n=" + android.net.Uri.encode(name)
    }
    /** NOVEL-1 (item 26): web novels from LNReader plugins. */
    data object Novels : Screen("novels")
    data object NovelSources : Screen("novels/sources")
    data object NovelTitle : Screen("novels/title?p={p}&u={u}&t={t}&i={i}") {
        fun route(plugin: String, path: String, name: String, cover: String? = null): String =
            "novels/title?p=" + android.net.Uri.encode(plugin) + "&u=" + android.net.Uri.encode(path) + "&t=" + android.net.Uri.encode(name) +
                "&i=" + android.net.Uri.encode(cover.orEmpty())
    }
    data object NovelReader : Screen("novels/read?p={p}&u={u}&t={t}&c={c}") {
        fun route(plugin: String, path: String, name: String, chapter: String): String =
            "novels/read?p=" + android.net.Uri.encode(plugin) + "&u=" + android.net.Uri.encode(path) + "&t=" + android.net.Uri.encode(name) +
                "&c=" + android.net.Uri.encode(chapter)
    }
    data object Privacy : Screen("settings/privacy")
    data object Tracking : Screen("settings/tracking")
    data object AiSettings : Screen("settings/ai")
    /** R28 item 31: the person's instructions, translation style, glossary and notes for Yumeiro AI. */
    data object AiPersonal : Screen("settings/ai/personal")
    /** B21: the assistant space; [ARG] = a MediaCategory code or empty (everything). */
    data object Assistant : Screen("assistant?cat={cat}") {
        const val ARG = "cat"
        fun route(cat: String? = null): String = "assistant?cat=${cat.orEmpty()}"
    }
    data object YouTubeAccount : Screen("settings/youtube")
    data object Backup : Screen("settings/backup")
    data object WatchTogether : Screen("watch/together")
    data object SourceHealth : Screen("sources/health")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        label = "Home",
        selectedIcon = NavIcons.HomeFilled,
        unselectedIcon = NavIcons.HomeOutline,
        testTag = "nav_tab_home"
    ),
    BottomNavItem(
        route = Screen.Music.route,
        label = "Music",
        selectedIcon = NavIcons.MusicFilled,
        unselectedIcon = NavIcons.MusicOutline,
        testTag = "nav_tab_music"
    ),
    BottomNavItem(
        route = Screen.Videos.route,
        label = "Videos",
        selectedIcon = NavIcons.VideosFilled,
        unselectedIcon = NavIcons.VideosOutline,
        testTag = "nav_tab_videos"
    ),
    BottomNavItem(
        route = Screen.Library.route,
        label = "Library",
        selectedIcon = NavIcons.LibraryFilled,
        unselectedIcon = NavIcons.LibraryOutline,
        testTag = "nav_tab_library"
    )
)
