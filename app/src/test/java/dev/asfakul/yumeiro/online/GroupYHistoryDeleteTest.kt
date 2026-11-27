/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.security.Privacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Group Y (B6): delete history by time range, many at once, and Undo that puts everything back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupYHistoryDeleteTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val day = 86_400_000L

    private fun e(name: String, ago: Long, ep: String? = "$name-ep2", adult: Boolean = false) = OnlineHistory.Entry(
        "Demo", "https://x/$name", name, null, emptyMap(), "Anime", "1", ep, ep?.let { "Episode 2" }, 600_000, 1_400_000, now - ago, adult,
    )

    private val seed = listOf(e("a", 10 * min), e("b", 3 * 3_600_000L), e("c", 3 * day), e("d", 20 * day), e("e", 90 * day))
    private val marks = mapOf(
        OnlineHistory.progressKey("Demo", "a-ep2") to OnlineHistory.Progress(600_000, 1_400_000, now - 10 * min),
        OnlineHistory.progressKey("Demo", "a-ep1") to OnlineHistory.Progress(1_400_000, 1_400_000, now - 40 * min),
        OnlineHistory.progressKey("Demo", "c-ep2") to OnlineHistory.Progress(600_000, 1_400_000, now - 3 * day),
        OnlineHistory.progressKey("Demo", "e-ep2") to OnlineHistory.Progress(600_000, 1_400_000, now - 90 * day),
    )

    // Robolectric gives every test a new files dir: forget the file of the previous test so writes land in this one.
    @Before fun setUp() { Privacy.setIncognito(ctx, false); OnlineHistory.resetForTests(); OnlineHistory.seedForTests(ctx, seed, marks) }
    @After fun tidy() { OnlineHistory.clear(ctx) }

    private fun names() = OnlineHistory.entries.value.map { it.name }

    @Test fun rangeCountsMatchWhatEachChoiceDeletes() {
        val counts = HistoryRange.entries.map { HistoryRange.count(seed, it, now) }
        assertEquals(listOf(1, 2, 3, 4, 5), counts)
        assertEquals(now - 3_600_000L, HistoryRange.HOUR.since(now))
        assertNull(HistoryRange.ALL.since(now))
        // The boundary is inside: exactly 7 days ago belongs to "Last 7 days".
        assertEquals(true, HistoryRange.WEEK.contains(now - 7 * day, now))
        assertEquals(false, HistoryRange.WEEK.contains(now - 7 * day - 1, now))
    }

    @Test fun lastHourRemovesItsTitleAndEveryMarkWatchedInIt_undoRestoresExactly() {
        val since = HistoryRange.HOUR.since(now)!!
        val r = OnlineHistory.removeWhere(ctx, marksSince = since) { HistoryRange.HOUR.contains(it.updatedAt, now) }
        assertEquals(1, r.count)
        assertEquals(listOf("b", "c", "d", "e"), names())
        assertNull(OnlineHistory.progressOf("Demo", "a-ep2"))
        assertNull(OnlineHistory.progressOf("Demo", "a-ep1")) // watched 40 minutes ago: inside the hour
        assertNotNull(OnlineHistory.progressOf("Demo", "c-ep2"))
        OnlineHistory.restore(ctx, r)
        assertEquals(listOf("a", "b", "c", "d", "e"), names())
        assertEquals(600_000L, OnlineHistory.entry("Demo", "https://x/a")!!.positionMs)
        assertEquals(1_400_000L, OnlineHistory.progressOf("Demo", "a-ep1")!!.positionMs)
        assertEquals(600_000L, OnlineHistory.progressOf("Demo", "a-ep2")!!.positionMs)
    }

    @Test fun selectedTitlesGoTogetherAndComeBackInOrder() {
        val keys = setOf("Demo|https://x/b", "Demo|https://x/d")
        val r = OnlineHistory.removeWhere(ctx) { it.key in keys }
        assertEquals(2, r.count)
        assertEquals(listOf("a", "c", "e"), names())
        assertNotNull(OnlineHistory.progressOf("Demo", "a-ep1")) // no time range: other marks stay
        OnlineHistory.restore(ctx, r)
        assertEquals(listOf("a", "b", "c", "d", "e"), names())
    }

    @Test fun allTimeEmptiesAndUndoBringsAllBack_evenAfterReload() {
        val r = OnlineHistory.removeWhere(ctx, marksSince = Long.MIN_VALUE) { true }
        assertEquals(5, r.count)
        assertEquals(emptyList<String>(), names())
        assertNull(OnlineHistory.progressOf("Demo", "e-ep2"))
        OnlineHistory.restore(ctx, r)
        OnlineHistory.flush(); OnlineHistory.unloadForTests(); OnlineHistory.ensureLoaded(ctx)
        assertEquals(listOf("a", "b", "c", "d", "e"), names())
        assertNotNull(OnlineHistory.progressOf("Demo", "e-ep2"))
    }

    @Test fun undoDoesNotOverwriteATitleWatchedAgainAfterTheDelete() {
        val r = OnlineHistory.removeWhere(ctx) { it.name == "c" }
        // Watched again (newer spot) before Undo.
        val again = e("c", 0).copy(positionMs = 900_000)
        OnlineHistory.seedForTests(ctx, OnlineHistory.entries.value + again, emptyMap())
        OnlineHistory.restore(ctx, r)
        assertEquals(900_000L, OnlineHistory.entry("Demo", "https://x/c")!!.positionMs)
        assertEquals(1, OnlineHistory.entries.value.count { it.name == "c" })
    }

    @Test fun filterNarrowsBySourceAndType() {
        val list = seed + OnlineHistory.Entry("Other", "https://y/m", "m", null, emptyMap(), "Movie", "1", null, null, 0, 0, now - 5 * min) +
            OnlineHistory.Entry("Other", "https://y/n", "n", null, emptyMap(), null, "1", null, null, 0, 0, now - 5 * day)
        val all = HistoryDeleteFilter(HistoryRange.ALL)
        assertEquals(7, all.pick(list, now).size)
        assertEquals(listOf("m", "n"), all.copy(apis = setOf("Other")).pick(list, now).map { it.name })
        assertEquals(listOf("m"), all.copy(types = setOf("Movie")).pick(list, now).map { it.name })
        assertEquals(listOf("n"), all.copy(types = setOf(HistoryRange.OTHER_TYPE)).pick(list, now).map { it.name })
        assertEquals(listOf("m"), HistoryDeleteFilter(HistoryRange.HOUR, apis = setOf("Other")).pick(list, now).map { it.name })
        assertEquals(Long.MIN_VALUE, all.marksSince(now))
        assertEquals(now - 3_600_000L, HistoryDeleteFilter(HistoryRange.HOUR).marksSince(now))
        assertNull(all.copy(apis = setOf("Other")).marksSince(now))
    }

    @Test fun nothingMatchedIsANoOp() {
        val r = OnlineHistory.removeWhere(ctx) { false }
        assertEquals(true, r.isEmpty)
        assertEquals(5, names().size)
    }
}
