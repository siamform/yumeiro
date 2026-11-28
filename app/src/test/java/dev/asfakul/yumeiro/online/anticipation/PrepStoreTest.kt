/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** B23 ADR-B23-1: what survives a restart, the 7-day cleanup, and one file per profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrepStoreTest {
    private val day = 24 * 60 * 60_000L
    private val t0 = 1_800_000_000_000L

    @After fun tearDown() = PrepStore.resetForTest()

    @Test fun missesAreRememberedForSixHours() {
        val d = PrepData().touch("s", t0).miss("s", "s|e2", t0)
        assertTrue(d.isMiss("s|e2", t0 + 60_000))
        assertFalse(d.isMiss("s|e2", t0 + AnticipationPolicy.MISS_MS))
        assertFalse(d.isMiss("s|e3", t0))
    }

    @Test fun sevenDaysWithoutWatchingRemovesOnlyThatSeriesData() {
        val d = PrepData()
            .touch("old", t0).miss("old", "old|e2", t0).match("old", "Other", "https://o/old", t0)
            .touch("new", t0 + 6 * day).miss("new", "new|e5", t0 + 6 * day).match("new", "Other", "https://o/new", t0 + 6 * day)
        val c = d.cleaned(t0 + 7 * day + 1)
        assertEquals(setOf("new"), c.series.keys)
        assertTrue(c.misses.keys.all { it.startsWith("new") })
        assertEquals(listOf("https://o/new"), c.matchesFor("new").map { it.url })
        assertTrue(c.matchesFor("old").isEmpty())
    }

    @Test fun matchesAreKeptPerSeriesAndSource() {
        val d = PrepData().match("s", "A", "https://a/1", t0).match("s", "B", "https://b/1", t0 + 1).match("s", "A", "https://a/2", t0 + 2)
        assertEquals(listOf("https://a/2", "https://b/1"), d.matchesFor("s").map { it.url })
    }

    @Test fun sizesAreBounded() {
        var d = PrepData()
        repeat(PrepData.MAX_MISSES + 50) { d = d.miss("s", "s|e$it", t0 + it) }
        assertEquals(PrepData.MAX_MISSES, d.misses.size)
        assertTrue("the newest stay", d.misses.containsKey("s|e${PrepData.MAX_MISSES + 49}"))
    }

    @Test fun theFileRoundTripsAndABrokenFileIsIgnored() {
        val dir = kotlin.io.path.createTempDirectory("prep").toFile()
        val f = File(dir, PrepStore.FILE)
        val d = PrepData().touch("s", t0).miss("s", "s|e2", t0).match("s", "A", "https://a", t0)
        PrepStore.write(f, d)
        assertEquals(d, PrepStore.read(f))
        f.writeText("{not json")
        assertEquals(PrepData(), PrepStore.read(f))
        dir.deleteRecursively()
    }

    @Test fun eachProfileHasItsOwnFile() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        Profiles.init(c)
        val first = Profiles.currentId(c)
        PrepStore.update(c) { it.touch("series-of-$first", t0) }
        val other = Profiles.add(c, "B23 other", AccentTone.GOLD).id
        Profiles.select(c, other)
        assertTrue("the new profile sees nothing of the first", PrepStore.get(c).series.isEmpty())
        PrepStore.update(c) { it.touch("series-of-other", t0) }
        Profiles.select(c, first)
        assertEquals(setOf("series-of-$first"), PrepStore.get(c).series.keys)
        assertTrue(Profiles.file(c, PrepStore.FILE).isFile)
    }
}
