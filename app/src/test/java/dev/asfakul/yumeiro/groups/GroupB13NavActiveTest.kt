/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import dev.asfakul.yumeiro.ui.navigation.NavActive
import dev.asfakul.yumeiro.ui.navigation.NavActive.State
import dev.asfakul.yumeiro.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B13: every pinned function lights its own bar item; the bar shows on all but immersive pages. */
class GroupB13NavActiveTest {
    private val live = "shortcut:live"
    private val stream = "shortcut:network_stream"
    private val manga = "shortcut:manga"
    private val settings = "shortcut:settings"
    private val profiles = "shortcut:profiles"
    private val tabs = listOf(Screen.Home.route, Screen.Music.route, Screen.Videos.route, Screen.Library.route)

    private fun videos(src: String) = State(Screen.Videos.route, src, "device")

    @Test fun liveShortcutLightsItselfNotVideos() {
        val items = tabs + live
        assertEquals(live, NavActive.active(videos("live"), intent = live, lastTab = Screen.Videos.route, items = items))
        // Reached through the Videos source menu, with Live pinned: still Live TV.
        assertEquals(live, NavActive.active(videos("live"), intent = null, lastTab = Screen.Videos.route, items = items))
        // Yumix source: Videos.
        assertEquals(Screen.Videos.route, NavActive.active(videos("online"), intent = null, lastTab = Screen.Videos.route, items = items))
    }

    @Test fun liveAndNetworkStreamShareAScreenButStayApart() {
        val items = tabs + live + stream
        assertEquals(stream, NavActive.active(videos("live"), intent = stream, lastTab = Screen.Videos.route, items = items))
        assertEquals(live, NavActive.active(videos("live"), intent = live, lastTab = Screen.Videos.route, items = items))
        // Tapping the Videos tab while on Live keeps Videos lit (the user asked for Videos).
        assertEquals(Screen.Videos.route, NavActive.active(videos("live"), intent = Screen.Videos.route, lastTab = Screen.Videos.route, items = items))
    }

    @Test fun subPagesLightTheirShortcutOrTheSectionTheyCameFrom() {
        val items = tabs + manga
        val title = State("manga/title?p={p}&u={u}&t={t}&i={i}", "online", "device")
        assertEquals(manga, NavActive.active(title, intent = null, lastTab = Screen.Home.route, items = items))
        val online = State(Screen.OnlineTitle.route, "online", "device")
        assertEquals(Screen.Videos.route, NavActive.active(online, intent = null, lastTab = Screen.Videos.route, items = items))
        assertEquals(Screen.Home.route, NavActive.active(online, intent = Screen.Home.route, lastTab = Screen.Home.route, items = items))
        // Nothing fits (last tab unpinned): no item is lit rather than a wrong one.
        assertNull(NavActive.active(online, intent = null, lastTab = "gone", items = items))
    }

    @Test fun settingsAndProfilesShareARouteButStayApart() {
        val items = tabs + settings + profiles
        val s = State(Screen.Settings.route, "online", "device")
        assertEquals(profiles, NavActive.active(s, intent = profiles, lastTab = Screen.Home.route, items = items))
        assertEquals(settings, NavActive.active(s, intent = settings, lastTab = Screen.Home.route, items = items))
    }

    @Test fun musicModesLightTheirPins() {
        val items = tabs + "shortcut:ytmusic" + "shortcut:mix"
        assertEquals("shortcut:mix", NavActive.active(State(Screen.Music.route, "online", "mix"), null, Screen.Music.route, items))
        assertEquals("shortcut:ytmusic", NavActive.active(State(Screen.Music.route, "online", "online"), null, Screen.Music.route, items))
        assertEquals(Screen.Music.route, NavActive.active(State(Screen.Music.route, "online", "device"), null, Screen.Music.route, items))
    }

    @Test fun staleTapIsForgottenOnAnotherTab() {
        assertNull(NavActive.keepIntent(manga, State(Screen.Home.route, "online", "device"), onTab = true))
        assertEquals(manga, NavActive.keepIntent(manga, State("manga/title?p={p}&u={u}&t={t}&i={i}", "online", "device"), onTab = false))
        assertEquals(live, NavActive.keepIntent(live, videos("live"), onTab = true))
    }

    @Test fun everyShortcutHasAScreenItLights() {
        // Each pin (but the Inbox sheet) must describe at least one real screen state, or it could never be lit.
        val states = listOf("live", "youtube", "online", "device").map { videos(it) } +
            listOf("online", "mix").map { State(Screen.Music.route, "online", it) } +
            listOf(
                "manga", "novels", Screen.Downloads.route, Screen.Extensions.route, Screen.SourcesHub.route, Screen.OnlineSearch.route,
                Screen.WebSites.route, Screen.Telegram.route, Screen.OnlineLibrary.route, Screen.OnlineHistory.route,
                Screen.ExtensionMarket.route, Screen.SourceHealth.route, Screen.Equalizer.route, Screen.WatchTogether.route,
                Screen.Settings.route, Screen.Tracking.route, Screen.Stats.route, Screen.OnlineUpcoming.route, Screen.ReadingLibrary.route,
            ).map { State(it, "online", "device") }
        dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.entries
            .filter { it != dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.INBOX }
            .forEach { sc -> assertTrue("${sc.id} lights nothing", states.any { NavActive.matches("shortcut:${sc.id}", it) }) }
        // Health is not "Sources".
        assertFalse(NavActive.matches("shortcut:sources", State(Screen.SourceHealth.route, "online", "device")))
    }

    @Test fun readersAreImmersiveEverythingElseKeepsTheBar() {
        assertTrue(NavActive.isImmersive(Screen.MangaReader.route))
        assertTrue(NavActive.isImmersive(Screen.NovelReader.route))
        listOf(Screen.Settings.route, Screen.MangaTitle.route, Screen.OnlineTitle.route, Screen.Downloads.route, Screen.Novels.route)
            .forEach { assertFalse(it, NavActive.isImmersive(it)) }
    }
}
