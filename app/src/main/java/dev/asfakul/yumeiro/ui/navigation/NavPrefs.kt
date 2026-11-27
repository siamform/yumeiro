/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Link
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
 * bar (icon plus short label, NAV-BAR-2) holds 3 to 7 items. Home is
 * always first and can't be removed: it is where everything else is reachable from.
 */
object NavPrefs {
    const val MIN_ITEMS = 3
    const val MAX_ITEMS = 7
    const val MAX_SHORTCUTS = 3
    const val SHORTCUT_PREFIX = "shortcut:"

    /** A shortcut the user can pin to the bar; tapping it opens that page. */
    enum class Shortcut(val id: String, val label: String, val detail: String, val item: BottomNavItem, val newer: Boolean = false) {
        MANGA("manga", "Manga", tr(R.string.your_reading_list_and_manga_sources, "Your reading list and manga sources"), nav("manga", "Manga", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)),
        NOVELS("novels", "Novels", tr(R.string.web_novels_from_lnreader_sources, "Web novels from LNReader sources"), nav("novels", "Novels", Icons.Filled.AutoStories, Icons.Outlined.AutoStories)),
        LIVE("live", tr(R.string.live_tv, "Live TV"), tr(R.string.channels_and_your_playlists, "Channels and your playlists"), nav("live", "Live", Icons.Filled.LiveTv, Icons.Outlined.LiveTv)),
        DOWNLOADS("downloads", "Downloads", tr(R.string.everything_saved_for_offline, "Everything saved for offline"), nav("downloads", "Saved", Icons.Filled.Download, Icons.Outlined.Download)),
        EXTENSIONS("extensions", "Extensions", tr(R.string.install_and_manage_extensions, "Install and manage extensions"), nav("extensions", "Exts", Icons.Filled.Extension, Icons.Outlined.Extension)),
        SOURCES("sources", "Sources", tr(R.string.repositories_health_and_sites, "Repositories, health and sites"), nav("sources", "Sources", Icons.Filled.Hub, Icons.Outlined.Hub)),
        YOUTUBE("youtube", "YouTube", tr(R.string.videos_from_youtube, "Videos from YouTube"), nav("youtube", "YouTube", Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay)),
        SEARCH("search", "Search", tr(R.string.search_every_extension_at_once, "Search every extension at once"), nav("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)),
        WEBSITES("websites", "Websites", tr(R.string.play_videos_from_sites_you_saved, "Play videos from sites you saved"), nav("websites", "Sites", Icons.Filled.Language, Icons.Outlined.Language)),
        TELEGRAM("telegram", "Telegram", tr(R.string.videos_from_public_telegram_channels, "Videos from public Telegram channels"), nav("telegram", "Telegram", Icons.AutoMirrored.Filled.Send, Icons.AutoMirrored.Outlined.Send)),
        // B15: live scores and fixtures (Live TV → Tools → Sports), pinnable like the other tools.
        SPORTS("sports", "Sports", tr(R.string.sports_shortcut_detail, "Live scores and fixtures"), nav("sports", "Sports", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer)),
        YUMIX("yumix", "Yumix", tr(R.string.anime_movies_and_shows_from_your, "Anime, movies and shows from your extensions"), nav("yumix", "Yumix", dev.asfakul.yumeiro.ui.components.YumixLogo.Filled, dev.asfakul.yumeiro.ui.components.YumixLogo.Icon)),
        YTMUSIC("ytmusic", tr(R.string.youtube_music, "YouTube Music"), tr(R.string.songs_albums_and_artists_online, "Songs, albums and artists online"), nav("ytmusic", tr(R.string.yt_music, "YT Music"), Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)),
        MIX("mix", tr(R.string.music_mix, "Music mix"), tr(R.string.your_songs_and_youtube_music_woven, "Your songs and YouTube Music woven together"), nav("mix", "Mix", Icons.Filled.Shuffle, Icons.Outlined.Shuffle)),
        DEVICE_VIDEOS("device_videos", tr(R.string.device_videos, "Device videos"), tr(R.string.videos_and_folders_on_this_phone, "Videos and folders on this phone"), nav("device_videos", "Device", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)),
        MY_LIST("mylist", tr(R.string.my_list, "My List"), tr(R.string.titles_you_saved_in_yumix, "Titles you saved in Yumix"), nav("mylist", tr(R.string.my_list, "My List"), Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)),
        HISTORY("history", "History", tr(R.string.continue_what_you_were_watching, "Continue what you were watching"), nav("history", "History", Icons.Filled.History, Icons.Outlined.History)),
        MARKET("market", tr(R.string.extension_market, "Extension market"), tr(R.string.find_and_install_new_extensions, "Find and install new extensions"), nav("market", "Market", Icons.Filled.Storefront, Icons.Outlined.Storefront)),
        HEALTH("health", tr(R.string.source_health, "Source health"), tr(R.string.which_sources_work_right_now, "Which sources work right now"), nav("health", "Health", Icons.Filled.MonitorHeart, Icons.Outlined.MonitorHeart)),
        EQUALIZER("equalizer", "Equalizer", tr(R.string.sound_presets_and_bands, "Sound presets and bands"), nav("equalizer", "EQ", Icons.Filled.Equalizer, Icons.Outlined.Equalizer)),
        WATCH_TOGETHER("watch_together", tr(R.string.watch_together, "Watch together"), tr(R.string.watch_in_sync_with_friends, "Watch in sync with friends"), nav("watch_together", "Together", Icons.Filled.Groups, Icons.Outlined.Groups)),
        INBOX("inbox", "Inbox", tr(R.string.news_and_messages_from_yumeiro, "News and messages from Yumeiro"), nav("inbox", "Inbox", Icons.Filled.Inbox, Icons.Outlined.Inbox)),
        SETTINGS("settings", "Settings", tr(R.string.every_option_in_one_place, "Every option in one place"), nav("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)),
        // OL-10 (owner): the newer pages can be pinned too (shown only with NAV_MORE_DESTINATIONS).
        PROFILES("profiles", "Profiles", tr(R.string.whos_watching_pin_18_per_person, "Who's watching, PIN, 18+ per person"), nav("profiles", "Profiles", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle), newer = true),
        STATS("stats", tr(R.string.your_statistics, "Your statistics"), tr(R.string.streaks_totals_and_the_activity_map, "Streaks, totals and the activity map"), nav("stats", "Stats", Icons.Filled.Insights, Icons.Outlined.Insights), newer = true),
        UPCOMING("upcoming", tr(R.string.airing_soon, "Airing soon"), tr(R.string.new_episodes_of_what_you_follow, "New episodes of what you follow"), nav("upcoming", "Airing", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth), newer = true),
        READING("reading", "Reading", tr(R.string.manga_and_books_you_read, "Manga and books you read"), nav("reading", "Reading", Icons.Filled.AutoStories, Icons.Outlined.AutoStories), newer = true),
        TRACKING("tracking", "Tracking", "AniList and MyAnimeList sync", nav("tracking", "Tracking", Icons.Filled.TrackChanges, Icons.Outlined.TrackChanges), newer = true),
        // H-12 (owner item 12): straight to "Play link" (URL, M3U8, MPD, RTSP, magnet), the Network Stream page.
        NETWORK_STREAM("network_stream", tr(R.string.network_stream, "Network stream"), tr(R.string.play_a_link_url_m3u8_dash, "Play a link: URL, M3U8, DASH, RTSP or magnet"), nav("network_stream", "Stream", Icons.Filled.Link, Icons.Outlined.Link)),
        ;
        companion object {
            fun of(id: String?): Shortcut? = entries.firstOrNull { it.id == id }
            /** OL-10: what Settings -> Navigation bar offers (the newer ones only with their flags on). */
            fun offered(): List<Shortcut> = entries.filter { s ->
                !s.newer || (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.NAV_MORE_DESTINATIONS) &&
                    (s != STATS || dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.PROFILE_STATS)))
            }
        }
    }

    data class Config(
        /** Routes of the main tabs that are shown, in bar order (Home always first). */
        val tabs: List<String> = DEFAULT_TABS,
        val shortcuts: List<Shortcut> = emptyList(),
        /** B14: every bar route in the user's order (Home first). Empty = tabs, then shortcuts. */
        val order: List<String> = emptyList(),
    ) {
        val size get() = tabs.size + shortcuts.size
        /** Bar routes in bar order. */
        val routes: List<String> get() {
            val all = tabs + shortcuts.map { SHORTCUT_PREFIX + it.id }
            return (listOf(Screen.Home.route) + order.filter { it in all && it != Screen.Home.route } + all).distinct().filter { it in all }
        }
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
                order = p.getString("order", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
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
            val t = (tabs + back).sortedBy { known.indexOf(it) }
            return Config(t, shortcuts, Config(t, shortcuts, c.order).routes)
        }
        return Config(tabs, shortcuts, Config(tabs, shortcuts, c.order).routes)
    }

    /**
     * B14: moves bar item [route] to bar position [to] (Home keeps position 0). False when it is
     * not on the bar, is Home, or [to] is outside 1 until size.
     */
    fun move(context: Context, route: String, to: Int): Boolean {
        val c = _config.value
        val r = c.routes.toMutableList()
        val from = r.indexOf(route)
        if (from <= 0 || to !in 1 until r.size) return false
        if (from == to) return true
        r.add(to, r.removeAt(from))
        return commit(context, c.copy(order = r))
    }

    fun setTab(context: Context, route: String, shown: Boolean): Boolean {
        val c = _config.value
        // A tab coming back lands where it usually sits among the tabs (before the shortcuts).
        val next = if (shown) c.copy(tabs = c.tabs + route, order = placeTab(c.routes, route)) else c.copy(tabs = c.tabs - route)
        return commit(context, next)
    }

    fun setShortcut(context: Context, shortcut: Shortcut, shown: Boolean): Boolean {
        val c = _config.value
        val next = if (shown) c.copy(shortcuts = c.shortcuts + shortcut) else c.copy(shortcuts = c.shortcuts - shortcut)
        return commit(context, next)
    }

    fun reset(context: Context) { commit(context, Config()) }

    private fun placeTab(routes: List<String>, route: String): List<String> {
        val known = DEFAULT_TABS
        // Before the tab that usually follows it; else right after the last tab on the bar.
        val next = routes.indexOfFirst { it in known && known.indexOf(it) > known.indexOf(route) }
        val at = if (next >= 0) next else routes.indexOfLast { it in known } + 1
        return routes.toMutableList().apply { add(at.coerceIn(1, size), route) }
    }

    /** False when the change would break the 3–6 rule (the caller explains why). */
    private fun commit(context: Context, next: Config): Boolean {
        if (next.size !in MIN_ITEMS..MAX_ITEMS || next.shortcuts.size > MAX_SHORTCUTS) return false
        val clean = sanitize(next)
        _config.value = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tabs", clean.tabs.joinToString(","))
            .putString("shortcuts", clean.shortcuts.joinToString(",") { it.id })
            .putString("order", clean.order.joinToString(","))
            .apply()
        return true
    }

    /** The main tabs to show (pager pages), in order. */
    fun tabItems(c: Config): List<BottomNavItem> = c.routes.mapNotNull { r -> bottomNavItems.firstOrNull { it.route == r } }

    /** Everything on the bar: main tabs, then pinned shortcuts. */
    fun barItems(c: Config): List<BottomNavItem> = c.routes.mapNotNull { r ->
        bottomNavItems.firstOrNull { it.route == r } ?: Shortcut.of(r.removePrefix(SHORTCUT_PREFIX))?.item
    }

    private fun nav(id: String, label: String, sel: androidx.compose.ui.graphics.vector.ImageVector, unsel: androidx.compose.ui.graphics.vector.ImageVector) =
        BottomNavItem(SHORTCUT_PREFIX + id, label, sel, unsel, "nav_shortcut_$id")
}
