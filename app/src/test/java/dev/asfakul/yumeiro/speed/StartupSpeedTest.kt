/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.speed

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.StartupSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SPD-1…SPD-4 (ADR-038, owner report 2026-11-19: "setup triggers slowly; feed, extension and catalog loads take
 * far too long, fresh install and old install alike"). Old code: the feed waited for every extension, every
 * background update deleted the saved feed, starters waited 4 s and downloaded one by one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupSpeedTest {
    @Before fun setUp() { DataCache.init(ApplicationProvider.getApplicationContext()); DataCache.clearAll() }
    @After fun tearDown() { DataCache.clearAll() }

    /** SPD-1: each extension is published as soon as it is loaded, not after the last one. */
    @Test fun spd1_eachExtensionIsPublishedAsItLoads() = runTest {
        val events = mutableListOf<String>()
        StartupSpeed.loadProgressively(listOf("a", "b", "c"), ahead = 2,
            open = { it }, load = { x, _ -> events += "load $x"; x }, publish = { x, _ -> events += "publish $x" })
        assertEquals(listOf("load a", "publish a", "load b", "publish b", "load c", "publish c"), events)
    }

    /** SPD-1: opening the next files overlaps (4 files × 100 ms with 4 ahead = 100 ms, not 400 ms). */
    @Test fun spd1_filesAreOpenedAhead() = runTest {
        StartupSpeed.loadProgressively(listOf(1, 2, 3, 4), ahead = 4,
            open = { delay(100); it }, load = { x, o -> assertEquals(x, o); x }, publish = { _, _ -> })
        assertEquals(100L, currentTime)
    }

    /** SPD-1: an open that fails leaves the load to do the work (null), nothing is lost. */
    @Test fun spd1_failedOpenFallsBackToLoad() = runTest {
        val got = StartupSpeed.loadProgressively(listOf(1, 2), ahead = 1,
            open = { if (it == 1) error("bad dex") else it }, load = { x, o -> "$x:$o" }, publish = { _, _ -> })
        assertEquals(listOf("1:null", "2:2"), got)
    }

    /** SPD-1: pinned extensions lead; the rest keep their saved order. */
    @Test fun spd1_pinnedLoadFirst() {
        assertEquals(listOf("C", "A", "B", "D"), StartupSpeed.loadOrder(listOf("A", "B", "C", "D"), { it }, listOf("C", "Z")))
        assertEquals(listOf("A", "B"), StartupSpeed.loadOrder(listOf("A", "B"), { it }, emptyList()))
    }

    /** SPD-1: the feed can show the first loaded extension while the rest load. */
    @Test fun spd1_partialSnapshotIsUsable() {
        assertTrue(ExtensionManager.Snapshot(partial = true).usable)
        assertTrue(!ExtensionManager.Snapshot().usable)
    }

    /** SPD-2: a background extension update keeps the saved feed on the phone. */
    @Test fun spd2_extensionUpdateKeepsSavedPages() {
        DataCache.write("home", "provider-home", "[saved rows]")
        OnlineRepository.refreshAfterExtensionChange()
        DataCache.awaitClearForTests()
        DataCache.clearMemory()
        assertNotNull("saved feed was deleted by an extension update", DataCache.read("home", "provider-home"))
    }

    /** SPD-3: starters download side by side (3 × 1 s = 1 s), a failing one is skipped. */
    @Test fun spd3_startersInstallInParallel() = runTest {
        val names = StartupSpeed.installAll(listOf("Movies", "Anime", "Sports")) { delay(1_000); if (it == "Anime") error("down") else it }
        assertEquals(1_000L, currentTime)
        assertEquals(listOf("Movies", "Sports"), names)
    }

    /** SPD-3: no extra pause after the welcome screen for a new user. */
    @Test fun spd3_noExtraPauseOnFirstRun() {
        assertEquals(0L, StartupSpeed.firstChecksDelayMs(firstRun = true))
        assertEquals(4_000L, StartupSpeed.firstChecksDelayMs(firstRun = false))
    }

    /** B20-PERF-EXT: the first extension shows at once, then at most one state change per window. */
    @Test fun b20_publishIsPacedWithManyExtensions() {
        var t = 0L
        val pacer = StartupSpeed.PublishPacer(60) { t }
        assertEquals(1_500L, pacer.windowMs)
        assertEquals(500L, StartupSpeed.PublishPacer(10).windowMs)
        // 60 extensions loading every 150 ms (9 s): first at once, then one per 1.5 s.
        var shown = 0
        repeat(60) { if (pacer.loaded()) shown++; t += 150 }
        assertTrue("paced to about 7 changes, got $shown", shown in 6..8)
        val first = StartupSpeed.PublishPacer(60) { 0L }
        assertTrue("the first loaded extension is shown at once", first.loaded())
    }
}
