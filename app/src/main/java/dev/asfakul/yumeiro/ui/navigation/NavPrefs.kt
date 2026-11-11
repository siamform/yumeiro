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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings → Navigation bar: which tabs the bottom bar shows.
 *
 * Four tabs by default (Home, Music, Videos, Library). Music, Videos and Library can be hidden
 * (they stay one tap away on Home), and up to two shortcuts can be added (Manga, Live TV,
 * Downloads, Extensions, Sources, YouTube, Search), so the bar holds 3 to 6 items. Home is
 * always first and can't be removed: it is where everything else is reachable from.
 */
object NavPrefs {
    const val MIN_ITEMS = 3
    const val MAX_ITEMS = 6
    const val MAX_SHORTCUTS = 2
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
