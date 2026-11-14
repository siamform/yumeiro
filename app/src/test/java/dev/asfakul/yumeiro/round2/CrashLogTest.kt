/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.diagnostics.CrashLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Round 2, order 7: a crash on any thread is saved on the phone and can be read back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun backgroundThreadCrashIsSaved() {
        CrashLog.clear(ctx)
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var chained = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chained = true }
        try {
            CrashLog.install(ctx)
            val t = Thread({ throw IllegalStateException("boom https://api.example.com/v1?token=SECRET123 by me@mail.com") }, "worker-7")
            t.start(); t.join()
            val r = CrashLog.list(ctx)
            assertEquals(1, r.size)
            assertEquals("Crash", r.first().kind)
            val text = r.first().file.readText()
            assertTrue(text, text.contains("IllegalStateException") && text.contains("Thread: worker-7") && text.contains("CrashLogTest"))
            assertTrue(text, text.contains("App: ") && text.contains("Android "))
            assertFalse("secrets kept: $text", text.contains("SECRET123") || text.contains("me@mail.com"))
            assertTrue("system handler still runs", chained)
            File(ctx.filesDir, "crash").listFiles()!!.forEach { println("saved: ${it.name}") }
        } finally { Thread.setDefaultUncaughtExceptionHandler(before) }
    }

    @Test fun keepsAtMost25AndClears() {
        CrashLog.clear(ctx)
        val d = File(ctx.filesDir, "crash")
        repeat(30) { i -> CrashLog.write(d, "Handled", "test", RuntimeException("n$i"), at = 1_000L + i).setLastModified(1_000L + i) }
        assertEquals(25, CrashLog.list(ctx).size)
        CrashLog.clear(ctx)
        assertTrue(CrashLog.list(ctx).isEmpty())
    }

    @Test fun scrub() {
        assertEquals("GET https://x.io/a?… failed", CrashLog.scrub("GET https://x.io/a?key=1&b=2 failed"))
        assertEquals("password=…", CrashLog.scrub("password=hunter2"))
    }
}
