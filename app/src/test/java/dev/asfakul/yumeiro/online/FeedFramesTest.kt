package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.FeedFrames.atMostEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FE-4: 1000 row answers in 10 s must not rebuild the Yumix mix 1000 times on the main thread. The first answer
 * shows at once, the newest one is never lost, and the rebuilds stay at about one per 300 ms frame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedFramesTest {
    @Test fun thousandAnswersBecomeFramesAndTheLastIsKept() = runTest {
        val times = ArrayList<Long>()
        val got = flow { repeat(1000) { emit(it); delay(10) } }.atMostEvery(300).let { f ->
            val out = ArrayList<Int>(); f.collect { out += it; times += currentTime }; out
        }
        assertEquals("first answer at once", 0, got.first()); assertEquals(0L, times.first())
        assertEquals("newest answer never lost", 999, got.last())
        assertTrue("rebuilds ${got.size} for 1000 answers over 10 s (want about one per 300 ms)", got.size in 2..40)
    }

    /** The Yumix screen must take its mix through the frames (guard against a revert to per-answer rebuilds). */
    @Test fun yumixUsesFrames() {
        val src = listOf("src/main/java", "app/src/main/java").map { File(it, "dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt") }.first { it.exists() }.readText()
        assertTrue(src.contains("atMostEvery("))
    }
}
