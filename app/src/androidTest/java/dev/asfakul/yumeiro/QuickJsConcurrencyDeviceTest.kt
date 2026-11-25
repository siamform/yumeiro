/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.network.JavaScriptEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * QJS-RACE-1 on the real QuickJS native library: many threads creating, using and closing their own runtimes
 * at the same time, the way "All" search runs Nuvio scrapers next to extension helpers. Before the fix this
 * aborts the process (JNI "attempt to remove stale Global" / "use of deleted global reference"), which the
 * runner reports as a crash; after it, every run must answer.
 */
@RunWith(AndroidJUnit4::class)
class QuickJsConcurrencyDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun parallelCreateAndCloseNeverCorruptsJniRefs() {
        val threads = 8
        val rounds = 250
        val ok = AtomicInteger()
        val firstError = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val t0 = System.currentTimeMillis()
        repeat(threads) { n ->
            thread(isDaemon = true, name = "qjs-race-$n") {
                try {
                    start.await()
                    repeat(rounds) { i ->
                        val r = runBlocking { JavaScriptEngine(ctx).evaluate<Any?>("($n * 1000 + $i).toString()") }
                        if (r?.toString() == (n * 1000 + i).toString()) ok.incrementAndGet()
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        val finished = done.await(240, TimeUnit.SECONDS)
        Log.i("YuQjsRace", "QJSRACE finished=$finished ok=${ok.get()}/${threads * rounds} in ${System.currentTimeMillis() - t0} ms error=${firstError.get()}")
        firstError.get()?.let { throw AssertionError("a runtime failed", it) }
        assertEquals("all parallel runs finished in 240 s", true, finished)
        assertEquals(threads * rounds, ok.get())
    }
}
