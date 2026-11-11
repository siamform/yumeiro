/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Music : Screen("music")
    data object Videos : Screen("videos")
    data object Stream : Screen("stream")
    data object WebSites : Screen("websites")
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
    /** My List, downloads and watch history for videos. */
    data object OnlineLibrary : Screen("videos/library")
    /** Offline downloads: Permanent / Offline groups, settings and tips. */
    data object Downloads : Screen("videos/downloads")
    data object Extensions : Screen("videos/online/extensions")
    data object ExtensionMarket : Screen("videos/online/market")
    /** Aniyomi / Mihon / Stremio / JavaScript / home servers. */
    data object SourcesHub : Screen("sources?tab={tab}") {
        const val ARG = "tab"
        fun route(tab: Int = 0): String = "sources?tab=$tab"
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
    data object Privacy : Screen("settings/privacy")
    data object Tracking : Screen("settings/tracking")
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
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    BottomNavItem(
        route = Screen.Music.route,
        label = "Music",
        selectedIcon = Icons.Filled.MusicNote,
        unselectedIcon = Icons.Outlined.MusicNote,
        testTag = "nav_tab_music"
    ),
    BottomNavItem(
        route = Screen.Videos.route,
        label = "Videos",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_tab_videos"
    ),
    BottomNavItem(
        route = Screen.Library.route,
        label = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_tab_library"
    )
)
