/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import dev.asfakul.yumeiro.player.AudioQuality
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtHomeOrderTest {
    private fun v(id: String, dur: Long = 300, url: String = "https://www.youtube.com/watch?v=$id") =
        YtItem(id = id, url = url, title = id, channel = "c", durationSec = dur)

    @Test fun interleaveTakesTurnsAndDropsRepeats() {
        val out = interleaveFeeds(listOf(listOf(v("a"), v("b"), v("c")), listOf(v("b"), v("x")), emptyList(), listOf(v("y"))))
        assertEquals(listOf("a", "b", "y", "c", "x"), out.map { it.id })
        assertEquals(2, interleaveFeeds(listOf(listOf(v("a"), v("b"), v("c"))), limit = 2).size)
    }

    @Test fun shortsDetected() {
        assertTrue(v("s", 45).isShort())
        assertTrue(v("s2", 0, "https://www.youtube.com/shorts/s2").isShort())
        assertFalse(v("l", 300).isShort())
        assertFalse(v("live", 30).copy(live = true).isShort())
    }

    @Test fun subscribersFormatted() {
        assertEquals("1.2M subscribers", formatYtSubscribers(1_200_000))
        assertEquals("", formatYtSubscribers(-1))
    }

    @Test fun audioTierPicksBestUnderCap() {
        val have = listOf(48, 130, 160, 256)
        assertEquals(256, AudioQuality.choose(have, AudioQuality.Tier.HIRES_MAX))
        assertEquals(256, AudioQuality.choose(have, AudioQuality.Tier.YOUTUBE))
        assertEquals(48, AudioQuality.choose(have, AudioQuality.Tier.SAVER))
        assertEquals(130, AudioQuality.choose(listOf(130, 160), AudioQuality.Tier.SAVER))
        assertEquals(null, AudioQuality.choose(emptyList(), AudioQuality.Tier.CD))
    }

    @Test fun navBarStaysBetweenThreeAndSeven() {
        val all = NavPrefs.Config()
        val s = NavPrefs.Shortcut.entries
        val clamped = NavPrefs.sanitize(all.copy(shortcuts = s + s))
        assertTrue(clamped.size <= NavPrefs.MAX_ITEMS)
        assertTrue(clamped.shortcuts.size <= NavPrefs.MAX_SHORTCUTS)
        assertTrue(NavPrefs.Shortcut.entries.size >= 20)
        assertEquals(NavPrefs.Shortcut.entries.size, NavPrefs.Shortcut.entries.map { it.id }.toSet().size)
        val tiny = NavPrefs.sanitize(NavPrefs.Config(tabs = emptyList()))
        assertTrue(tiny.size >= 3)
        assertEquals(all.tabs.first(), tiny.tabs.first())
    }
}
