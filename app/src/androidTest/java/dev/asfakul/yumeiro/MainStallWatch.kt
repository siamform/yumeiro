/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Freeze probe for BUG-7: a daemon thread posts to the main thread every ~50 ms and records the worst
 * delay. A delay over [dumpAfterMs] logs the main thread's stack once per stall (FMT_STALL under [tag])
 * and keeps the first one in [firstStall].
 */
class MainStallWatch(private val tag: String, private val dumpAfterMs: Long = 1_500) {
    val maxLag = AtomicLong()
    val firstStall = AtomicReference<String?>(null)
    @Volatile private var on = false

    fun reset() { maxLag.set(0); firstStall.set(null) }

    fun start() {
        on = true
        val h = Handler(Looper.getMainLooper())
        val main = Looper.getMainLooper().thread
        Thread({
            while (on) {
                val t0 = SystemClock.uptimeMillis(); val ran = AtomicLong()
                h.post { ran.set(SystemClock.uptimeMillis()) }
                var dumped = false
                while (ran.get() == 0L && on) {
                    SystemClock.sleep(5)
                    if (!dumped && SystemClock.uptimeMillis() - t0 > dumpAfterMs) {
                        dumped = true
                        val st = main.stackTrace.take(40).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                        firstStall.compareAndSet(null, st)
                        Log.i(tag, "FMT_STALL main ${SystemClock.uptimeMillis() - t0} ms: $st")
                    }
                }
                val lag = (ran.get().takeIf { it > 0 } ?: SystemClock.uptimeMillis()) - t0
                if (lag > maxLag.get()) maxLag.set(lag)
                SystemClock.sleep(45)
            }
        }, "main-stall-watch").apply { isDaemon = true }.start()
    }

    fun stop() { on = false }
}
