/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.os.Process
import dev.asfakul.yumeiro.FeatureFlags
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * GATE-1-RENDER-5: extension work (OnlineRepository.home runs on ExtensionGuard.dispatcher) must not
 * compete with the UI thread: its threads run at THREAD_PRIORITY_BACKGROUND, and 8 can still wait on
 * the network at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtensionThreadsTest {
    @Test fun extensionWorkRunsAtBackgroundPriority() = runBlocking {
        val prio = withContext(ExtensionGuard.dispatcher) { Process.getThreadPriority(Process.myTid()) }
        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, prio)
    }

    @Test fun eightTasksRunAtOnce() = runBlocking {
        val all = CountDownLatch(ExtensionThreads.SIZE)
        val ok = (1..ExtensionThreads.SIZE).map {
            async(ExtensionGuard.dispatcher) { all.countDown(); all.await(5, TimeUnit.SECONDS) }
        }.awaitAll()
        assertTrue("all ${ExtensionThreads.SIZE} tasks were running together", ok.all { it })
    }

    @Test fun flagIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.EXTENSION_BG_PRIORITY.default)
    }
}
