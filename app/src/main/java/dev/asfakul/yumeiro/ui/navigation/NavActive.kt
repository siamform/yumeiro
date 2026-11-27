/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

/**
 * B13: which bar item is lit, and where the bar shows. Every function a user can pin has its own
 * active state: Live TV, Network stream, YouTube, Yumix and Device videos all live on the Videos tab
 * (one route, different sources), so the route alone lit "Videos" for all of them. Here the item
 * is chosen from the route AND the tab's source, plus the item the user tapped last ([intent]), so
 * two pins that share a screen (Live TV and Network stream; Settings and Profiles) stay apart.
 *
 * Pure: no Compose, no Android, so the rules are unit tested (GroupB13NavActiveTest).
 */
object NavActive {
    /** What is on screen: the route, the Videos source tag and the Music mode key. */
    data class State(val route: String?, val videoSource: String, val musicMode: String)

    private const val P = NavPrefs.SHORTCUT_PREFIX

    /** Pages that are their own world (readers): no bar over them. Players are overlays (see the app). */
    fun isImmersive(route: String?): Boolean =
        route != null && (route.startsWith("manga/read") || route.startsWith("novels/read"))

    /** Does pinned item [route] (a tab route or "shortcut:id") describe what is on screen? */
    fun matches(item: String, s: State): Boolean {
        val r = s.route ?: return false
        if (!item.startsWith(P)) return r == item
        val sc = NavPrefs.Shortcut.of(item.removePrefix(P)) ?: return false
        fun under(base: String) = r == base || r.startsWith("$base/") || r.startsWith("$base?")
        val videos = r == Screen.Videos.route
        val music = r == Screen.Music.route
        return when (sc) {
            NavPrefs.Shortcut.LIVE, NavPrefs.Shortcut.NETWORK_STREAM -> videos && s.videoSource == "live"
            NavPrefs.Shortcut.YOUTUBE -> videos && s.videoSource == "youtube"
            NavPrefs.Shortcut.YUMIX -> videos && s.videoSource == "online"
            NavPrefs.Shortcut.DEVICE_VIDEOS -> videos && s.videoSource == "device"
            NavPrefs.Shortcut.YTMUSIC -> music && s.musicMode == "online"
            NavPrefs.Shortcut.MIX -> music && s.musicMode == "mix"
            NavPrefs.Shortcut.MANGA -> under("manga")
            NavPrefs.Shortcut.NOVELS -> under("novels")
            NavPrefs.Shortcut.DOWNLOADS -> r == Screen.Downloads.route
            NavPrefs.Shortcut.EXTENSIONS -> r == Screen.Extensions.route
            NavPrefs.Shortcut.SOURCES -> under("sources") && !r.startsWith(Screen.SourceHealth.route)
            NavPrefs.Shortcut.SEARCH -> r == Screen.OnlineSearch.route
            NavPrefs.Shortcut.WEBSITES -> r == Screen.WebSites.route
            NavPrefs.Shortcut.TELEGRAM -> r == Screen.Telegram.route
            NavPrefs.Shortcut.SPORTS -> r == Screen.Sports.route
            NavPrefs.Shortcut.MY_LIST -> r == Screen.OnlineLibrary.route
            NavPrefs.Shortcut.HISTORY -> r == Screen.OnlineHistory.route
            NavPrefs.Shortcut.MARKET -> r == Screen.ExtensionMarket.route
            NavPrefs.Shortcut.HEALTH -> r == Screen.SourceHealth.route
            NavPrefs.Shortcut.EQUALIZER -> r == Screen.Equalizer.route
            NavPrefs.Shortcut.WATCH_TOGETHER -> r == Screen.WatchTogether.route
            NavPrefs.Shortcut.SETTINGS, NavPrefs.Shortcut.PROFILES -> r == Screen.Settings.route
            NavPrefs.Shortcut.TRACKING -> r == Screen.Tracking.route
            NavPrefs.Shortcut.STATS -> r == Screen.Stats.route
            NavPrefs.Shortcut.UPCOMING -> r == Screen.OnlineUpcoming.route
            NavPrefs.Shortcut.READING -> r == Screen.ReadingLibrary.route
            NavPrefs.Shortcut.INBOX -> false // a sheet over the page, not a route
        }
    }

    /**
     * The lit item among [items] (bar routes in order). Rules, first hit wins:
     * 1. the item the user tapped, while it still describes the screen;
     * 2. a pinned shortcut that describes the screen (Live TV pinned: the Live page lights Live TV);
     * 3. the tab itself;
     * 4. on a deeper page: the item the user came in through, else the last tab.
     * Null when nothing in the bar fits (the capsule hides instead of lighting a wrong tab).
     */
    fun active(s: State, intent: String?, lastTab: String?, items: List<String>): String? {
        if (intent != null && intent in items && matches(intent, s)) return intent
        items.firstOrNull { it.startsWith(P) && matches(it, s) }?.let { return it }
        if (s.route != null && s.route in items) return s.route
        if (intent != null && intent in items) return intent
        return lastTab?.takeIf { it in items }
    }

    /** The tap is forgotten once the user is on a tab it does not describe (swiped away, Back). */
    fun keepIntent(intent: String?, s: State, onTab: Boolean): String? =
        if (intent != null && onTab && !matches(intent, s)) null else intent
}
