/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.js.JsEngine
import eu.kanade.tachiyomi.network.JavaScriptEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * SRC-1 on the real QuickJS: a script that never ends must be stopped, and the source must work
 * again afterwards. Each call runs on its own thread so a hang fails the test instead of the run.
 */
@RunWith(AndroidJUnit4::class)
class JsTimeLimitDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuJsLimit", s)

    /** Runs [block] on a thread; returns its outcome, or null if it is still running after [waitMs]. */
    private fun bounded(waitMs: Long, block: () -> Any?): Result<Any?>? {
        val out = AtomicReference<Result<Any?>?>(null)
        val t = thread(isDaemon = true, name = "js-limit") { out.set(runCatching(block)) }
        t.join(waitMs)
        return out.get()
    }

    private val source = """{"id":"990001","name":"Loop test","baseUrl":"https://example.com","lang":"en","isManga":true}"""
    private val code = """
        class DefaultExtension extends MProvider {
            async getPopular(page) { if (page == 1) { while (true) {} } return { list: [], hasNextPage: false }; }
        }
    """.trimIndent()

    @Test fun mangayomiLoopIsStoppedAndSourceRecovers() {
        val engine = JsEngine(ctx, "990001", source, code)
        val t0 = System.currentTimeMillis()
        val first = bounded(90_000) { runBlocking { engine.call("getPopular(1)") } }
        log("JSLIMIT first=$first after ${System.currentTimeMillis() - t0} ms")
        assertTrue("busy loop was never stopped (still running after 90 s)", first != null)
        assertTrue("busy loop returned a value: $first", first!!.isFailure)
        val second = bounded(30_000) { runBlocking { engine.call("getPopular(2)") } }
        log("JSLIMIT second=$second")
        assertTrue("source stayed blocked after the stop: $second", second != null && second.isSuccess)
        engine.close()
    }

    @Test fun extensionHelperLoopIsStopped() {
        val r = bounded(30_000) { runBlocking { JavaScriptEngine(ctx).evaluate<Any?>("while (true) {}") } }
        log("JSLIMIT helper=$r")
        assertTrue("JavaScriptEngine loop never stopped", r != null && r.isFailure)
        val ok = bounded(10_000) { runBlocking { JavaScriptEngine(ctx).evaluate<Any?>("1 + 1") } }
        assertEquals(2L, (ok?.getOrNull() as? Number)?.toLong())
    }

    @Test fun cashQuickJsLoopIsStopped() {
        val r = bounded(30_000) { app.cash.quickjs.QuickJs.create().use { it.evaluate("while (true) {}") } }
        log("JSLIMIT cash=$r")
        assertTrue("app.cash QuickJs loop never stopped", r != null && r.isFailure)
    }
}
