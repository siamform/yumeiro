/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import dev.asfakul.yumeiro.online.AccessLadder
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.watch.StreamWatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchLogicTest {
    @After fun tearDown() { AccessLadder.forgetLearned(); StreamWatch.resetAutoSwitch() }

    private fun ch(url: String, title: String, group: String? = null) = StreamRequest(url = url, title = title, group = group)

    @Test fun nameKeyDropsQualityAndBackupTags() {
        assertEquals(StreamWatch.nameKey("Sony HD"), StreamWatch.nameKey("Sony (Backup)"))
        assertEquals(StreamWatch.nameKey("Sony"), StreamWatch.nameKey("SONY 1080p"))
    }

    @Test fun alternativesGroupsSameChannelLinks() {
        val list = listOf(ch("https://a/1", "Sony HD"), ch("https://b/2", "Sony [Backup]"), ch("https://c/3", "Zee"))
        assertEquals(listOf("https://a/1", "https://b/2"), StreamWatch.alternatives(list, "https://b/2").map { it.url })
        assertTrue(StreamWatch.alternatives(list, "https://nope").isEmpty())
    }

    @Test fun filterByGroupAndText() {
        val list = listOf(ch("u1", "News One", "News"), ch("u2", "Sports Max", "Sports"), ch("u3", "World News", "News"))
        assertEquals(listOf(0, 2), StreamWatch.filter(list, "News", "").map { it.index })
        assertEquals(listOf(2), StreamWatch.filter(list, null, "world").map { it.index })
    }

    @Test fun autoSwitchIsRateLimited() {
        repeat(StreamWatch.MAX_AUTO_SWITCHES) { assertTrue(StreamWatch.allowAutoSwitch(1_000L + it)) }
        assertFalse(StreamWatch.allowAutoSwitch(2_000L))
        assertTrue(StreamWatch.allowAutoSwitch(1_000L + StreamWatch.AUTO_SWITCH_WINDOW_MS + 10))
    }

    @Test fun learnedStepGoesFirstPerHost() {
        assertNull(AccessLadder.learned("https://cdn.example/a.m3u8"))
        assertEquals(AccessLadder.Step.entries, AccessLadder.ordered("https://cdn.example/a.m3u8"))
        AccessLadder.learn("https://cdn.example/a.m3u8", AccessLadder.Step.VLC_UA)
        assertEquals(AccessLadder.Step.VLC_UA, AccessLadder.learned("https://CDN.example/other.ts"))
        val order = AccessLadder.ordered("https://cdn.example/b.m3u8")
        assertEquals(AccessLadder.Step.VLC_UA, order.first())
        assertEquals(AccessLadder.Step.entries.size, order.size)
        assertNull(AccessLadder.learned("https://other.example/a"))
    }
}
