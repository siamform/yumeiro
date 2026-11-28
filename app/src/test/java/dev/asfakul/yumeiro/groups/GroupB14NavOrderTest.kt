/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B14: the bar is a reorderable list. Home stays first; the order survives a restart and edits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupB14NavOrderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val live = NavPrefs.SHORTCUT_PREFIX + "live"
    private fun routes() = NavPrefs.config.value.routes
    private fun bar() = NavPrefs.barItems(NavPrefs.config.value).map { it.route }

    @Before fun clean() { NavPrefs.init(ctx); NavPrefs.reset(ctx) }

    @Test fun movesAnyItemButHome() {
        assertTrue(NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true))
        assertEquals(listOf(Screen.Home.route, Screen.Music.route, Screen.Videos.route, Screen.Library.route, live), routes())
        assertTrue(NavPrefs.move(ctx, live, 1))
        assertEquals(listOf(Screen.Home.route, live, Screen.Music.route, Screen.Videos.route, Screen.Library.route), bar())
        assertTrue(NavPrefs.move(ctx, Screen.Music.route, 4))
        assertEquals(listOf(Screen.Home.route, live, Screen.Videos.route, Screen.Library.route, Screen.Music.route), bar())
        // Home never moves, nothing moves onto Home's place, out-of-range is refused.
        assertFalse(NavPrefs.move(ctx, Screen.Home.route, 2))
        assertFalse(NavPrefs.move(ctx, live, 0))
        assertFalse(NavPrefs.move(ctx, live, 9))
        assertFalse(NavPrefs.move(ctx, "shortcut:manga", 1))
        // The pager follows the bar order of the tabs.
        assertEquals(listOf(Screen.Home.route, Screen.Videos.route, Screen.Library.route, Screen.Music.route),
            NavPrefs.tabItems(NavPrefs.config.value).map { it.route })
    }

    @Test fun orderSurvivesARestart() {
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        NavPrefs.move(ctx, live, 1)
        val saved = ctx.getSharedPreferences("yu_nav", Context.MODE_PRIVATE).getString("order", null)
        assertEquals(routes().joinToString(","), saved)
        // A fresh load reads the same order back.
        val reread = NavPrefs.sanitize(NavPrefs.Config(NavPrefs.config.value.tabs, NavPrefs.config.value.shortcuts, saved!!.split(',')))
        assertEquals(routes(), reread.routes)
    }

    @Test fun offAndOnAgainLandsSensibly() {
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        NavPrefs.move(ctx, live, 1)
        assertTrue(NavPrefs.setTab(ctx, Screen.Videos.route, false))
        assertEquals(listOf(Screen.Home.route, live, Screen.Music.route, Screen.Library.route), bar())
        // A tab coming back sits before the tab that usually follows it, the user's order otherwise kept.
        assertTrue(NavPrefs.setTab(ctx, Screen.Videos.route, true))
        assertEquals(listOf(Screen.Home.route, live, Screen.Music.route, Screen.Videos.route, Screen.Library.route), bar())
        // Reset clears the order.
        NavPrefs.reset(ctx)
        assertEquals(listOf(Screen.Home.route, Screen.Music.route, Screen.Videos.route, Screen.Library.route), bar())
    }

    @Test fun limitsHold() {
        NavPrefs.Shortcut.offered().take(3).forEach { assertTrue(NavPrefs.setShortcut(ctx, it, true)) }
        assertEquals(7, NavPrefs.config.value.size)
        assertFalse("8 items refused", NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.offered()[3], true))
        assertTrue(NavPrefs.setTab(ctx, Screen.Library.route, false))
        assertFalse("4 shortcuts refused", NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.offered()[3], true))
        assertEquals(bar().size, routes().size)
    }
}
