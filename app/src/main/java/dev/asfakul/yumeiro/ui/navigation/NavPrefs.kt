/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings → Navigation bar: which tabs the bottom bar shows.
 *
 * Four tabs by default (Home, Music, Videos, Library). Music, Videos and Library can be hidden
 * (they stay one tap away on Home), and up to three shortcuts can be added (any feature: Yumix,
 * YouTube, YouTube Music, Mix, Manga, Live TV, Websites, Downloads, My List, History, Extensions,
 * Market, Sources, Source health, Search, Equalizer, Watch together, Inbox, Settings…), so the
 * icon-only bar holds 3 to 7 items. Home is
 * always first and can't be removed: it is where everything else is reachable from.
 */
object NavPrefs {
    const val MIN_ITEMS = 3
    const val MAX_ITEMS = 7
    const val MAX_SHORTCUTS = 3
    const val SHORTCUT_PREFIX = "shortcut:"

    /** A shortcut the user can pin to the bar; tapping it opens that page. */
    enum class Shortcut(val id: String, val label: String, val detail: String, val item: BottomNavItem) {
        MANGA("manga", "Manga", "Your reading list and manga sources", nav("manga", "Manga", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)),
        LIVE("live", "Live TV", "Channels and your playlists", nav("live", "Live", Icons.Filled.LiveTv, Icons.Outlined.LiveTv)),
        DOWNLOADS("downloads", "Downloads", "Everything saved for offline", nav("downloads", "Saved", Icons.Filled.Download, Icons.Outlined.Download)),
        EXTENSIONS("extensions", "Extensions", "Install and manage extensions", nav("extensions", "Exts", Icons.Filled.Extension, Icons.Outlined.Extension)),
        SOURCES("sources", "Sources", "Repositories, health and sites", nav("sources", "Sources", Icons.Filled.Hub, Icons.Outlined.Hub)),
        YOUTUBE("youtube", "YouTube", "Videos from YouTube", nav("youtube", "YouTube", Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay)),
        SEARCH("search", "Search", "Search every extension at once", nav("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)),
        WEBSITES("websites", "Websites", "Play videos from sites you saved", nav("websites", "Sites", Icons.Filled.Language, Icons.Outlined.Language)),
        YUMIX("yumix", "Yumix", "Anime, movies and shows from your extensions", nav("yumix", "Yumix", dev.asfakul.yumeiro.ui.components.YumixLogo.Filled, dev.asfakul.yumeiro.ui.components.YumixLogo.Icon)),
        YTMUSIC("ytmusic", "YouTube Music", "Songs, albums and artists online", nav("ytmusic", "YT Music", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)),
        MIX("mix", "Music mix", "Your songs and YouTube Music woven together", nav("mix", "Mix", Icons.Filled.Shuffle, Icons.Outlined.Shuffle)),
        DEVICE_VIDEOS("device_videos", "Device videos", "Videos and folders on this phone", nav("device_videos", "Device", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)),
        MY_LIST("mylist", "My List", "Titles you saved in Yumix", nav("mylist", "My List", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)),
        HISTORY("history", "History", "Continue what you were watching", nav("history", "History", Icons.Filled.History, Icons.Outlined.History)),
        MARKET("market", "Extension market", "Find and install new extensions", nav("market", "Market", Icons.Filled.Storefront, Icons.Outlined.Storefront)),
        HEALTH("health", "Source health", "Which sources work right now", nav("health", "Health", Icons.Filled.MonitorHeart, Icons.Outlined.MonitorHeart)),
        EQUALIZER("equalizer", "Equalizer", "Sound presets and bands", nav("equalizer", "EQ", Icons.Filled.Equalizer, Icons.Outlined.Equalizer)),
        WATCH_TOGETHER("watch_together", "Watch together", "Watch in sync with friends", nav("watch_together", "Together", Icons.Filled.Groups, Icons.Outlined.Groups)),
        INBOX("inbox", "Inbox", "News and messages from Yumeiro", nav("inbox", "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox)),
        SETTINGS("settings", "Settings", "Every option in one place", nav("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)),
        ;
        companion object { fun of(id: String?): Shortcut? = entries.firstOrNull { it.id == id } }
    }

    data class Config(
        /** Routes of the main tabs that are shown, in bar order (Home always first). */
        val tabs: List<String> = DEFAULT_TABS,
        val shortcuts: List<Shortcut> = emptyList(),
    ) {
        val size get() = tabs.size + shortcuts.size
    }

    private val DEFAULT_TABS = bottomNavItems.map { it.route }
    private const val PREFS = "yu_nav"
    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = sanitize(
            Config(
                tabs = p.getString("tabs", null)?.split(',')?.filter { it.isNotBlank() } ?: DEFAULT_TABS,
                shortcuts = p.getString("shortcuts", null)?.split(',')?.mapNotNull { Shortcut.of(it) }.orEmpty(),
            ),
        )
        loaded = true
    }

    /** Keeps Home first, only known tabs in their usual order, at most two shortcuts, 3–6 items. */
    internal fun sanitize(c: Config): Config {
        val known = DEFAULT_TABS
        val tabs = (listOf(Screen.Home.route) + c.tabs.filter { it in known && it != Screen.Home.route }).distinct()
            .sortedBy { known.indexOf(it) }
        val shortcuts = c.shortcuts.distinct().take(MAX_SHORTCUTS).take((MAX_ITEMS - tabs.size).coerceAtLeast(0))
        if (tabs.size + shortcuts.size < MIN_ITEMS) {
            // Too few: bring hidden tabs back in their usual order until the bar has three.
            val back = known.filter { it !in tabs }.take(MIN_ITEMS - tabs.size - shortcuts.size)
            return Config((tabs + back).sortedBy { known.indexOf(it) }, shortcuts)
        }
        return Config(tabs, shortcuts)
    }

    fun setTab(context: Context, route: String, shown: Boolean): Boolean {
        val c = _config.value
        val next = if (shown) c.copy(tabs = c.tabs + route) else c.copy(tabs = c.tabs - route)
        return commit(context, next)
    }

    fun setShortcut(context: Context, shortcut: Shortcut, shown: Boolean): Boolean {
        val c = _config.value
        val next = if (shown) c.copy(shortcuts = c.shortcuts + shortcut) else c.copy(shortcuts = c.shortcuts - shortcut)
        return commit(context, next)
    }

    fun reset(context: Context) { commit(context, Config()) }

    /** False when the change would break the 3–6 rule (the caller explains why). */
    private fun commit(context: Context, next: Config): Boolean {
        if (next.size !in MIN_ITEMS..MAX_ITEMS || next.shortcuts.size > MAX_SHORTCUTS) return false
        val clean = sanitize(next)
        _config.value = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tabs", clean.tabs.joinToString(","))
            .putString("shortcuts", clean.shortcuts.joinToString(",") { it.id })
            .apply()
        return true
    }

    /** The main tabs to show (pager pages), in order. */
    fun tabItems(c: Config): List<BottomNavItem> = bottomNavItems.filter { it.route in c.tabs }

    /** Everything on the bar: main tabs, then pinned shortcuts. */
    fun barItems(c: Config): List<BottomNavItem> = tabItems(c) + c.shortcuts.map { it.item }

    private fun nav(id: String, label: String, sel: androidx.compose.ui.graphics.vector.ImageVector, unsel: androidx.compose.ui.graphics.vector.ImageVector) =
        BottomNavItem(SHORTCUT_PREFIX + id, label, sel, unsel, "nav_shortcut_$id")
}
