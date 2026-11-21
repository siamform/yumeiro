package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FE-4b: with 1000 extensions only the working set (picked, pinned, first 24 / 12 in speed order) fetches live;
 * the set grows by 12 when the feed reaches its end. With 24 or fewer extensions every one is live, as before.
 */
class FeedWorkingSetTest {
    private val thousand = (1..1000).map { "ext$it" }

    @Test fun thousandExtensionsOnlyTheWorkingSetGoesLive() {
        val live = FeedWorkingSet.live(thousand, FeedWorkingSet.initial(lowEnd = false), picked = "ext900", pinned = listOf("ext500", "gone"))
        assertEquals("picked + pinned + first 24", 26, live.size)
        assertEquals("picked first", "ext900", live.first())
        assertTrue("pinned live", "ext500" in live)
        assertTrue("unknown pin ignored", "gone" !in live)
        assertTrue("first in speed order live", (1..24).all { "ext$it" in live })
        assertTrue("25th waits", "ext25" !in live)
        assertEquals("2 GB phone: 12", 12, FeedWorkingSet.live(thousand, FeedWorkingSet.initial(lowEnd = true), null, emptyList()).size)
    }

    @Test fun fewExtensionsAllLiveAsBefore() {
        val twenty = thousand.take(20)
        assertEquals(twenty.toSet(), FeedWorkingSet.live(twenty, FeedWorkingSet.initial(lowEnd = false), null, emptyList()))
    }

    @Test fun growsOnlyWhileSomeAreWaiting() {
        assertEquals(36, FeedWorkingSet.grow(24, waiting = 976))
        assertEquals(24, FeedWorkingSet.grow(24, waiting = 0))
    }

    /** Yumix must pass the working set to every fetch and not count waiting extensions as loading. */
    @Test fun yumixUsesTheWorkingSet() {
        val src = listOf("src/main/java", "app/src/main/java").map { File(it, "dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt") }.first { it.exists() }.readText()
        assertTrue("loadAll asks FeedWorkingSet.live", src.contains("FeedWorkingSet.live("))
        assertTrue("fetch gets live =", src.contains("loadOne(api, force, live ="))
        assertTrue("waiting not counted as loading", src.contains("waitingLive.containsKey(n) ||"))
        assertTrue("feed end grows the set", src.contains("FeedWorkingSet.grow("))
    }
}
