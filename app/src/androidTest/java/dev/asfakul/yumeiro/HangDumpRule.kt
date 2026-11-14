/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * R15: when a device test runs longer than expected, every thread's stack goes to logcat (tag
 * `YuHang`) so a hung run shows where it hung instead of only "TIMEOUT". It does not fail or stop
 * the test; device-check.sh still enforces the 900 s limit per class.
 */
class HangDumpRule(private val atMs: LongArray = longArrayOf(240_000L, 600_000L)) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            val t0 = System.currentTimeMillis()
            val watcher = Thread({
                for (at in atMs) {
                    while (!finished.get() && System.currentTimeMillis() - t0 < at) runCatching { Thread.sleep(500) }
                    if (finished.get()) return@Thread
                    dump("${description.methodName} still running after ${at / 1000}s")
                }
            }, "yu-hang-dump").apply { isDaemon = true }
            watcher.start()
            try { base.evaluate() } finally { finished.set(true) }
        }
    }

    companion object {
        fun dump(why: String) {
            Log.w("YuHang", "=== $why")
            for ((t, st) in Thread.getAllStackTraces()) {
                if (st.isEmpty() || t.name == "yu-hang-dump") continue
                // Idle pool threads park in the same frames; skip them to keep the log readable.
                val top = st.first()
                if (st.size < 4 && (top.methodName == "park" || top.methodName == "wait")) continue
                Log.w("YuHang", "--- ${t.name} (${t.state})")
                st.take(28).forEach { Log.w("YuHang", "    at $it") }
            }
        }
    }
}
