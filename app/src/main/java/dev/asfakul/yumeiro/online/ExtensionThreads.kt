/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * GATE-1-RENDER-5: extension threads run at Android's background priority.
 *
 * Gate run 44955115811 (api33-lowram, 300 providers loading): Yumix frames took about 1 s while the
 * named work inside them added up to about 400 ms (a single text measure 40–78 ms), i.e. the UI
 * thread was waiting for CPU behind extension parsing on the 8 shared IO threads at normal priority.
 * Android: "set the thread priority of worker threads to THREAD_PRIORITY_BACKGROUND" so they do not
 * compete with the UI thread (https://developer.android.com/topic/performance/threads). Same 8 threads
 * (network waits need them); idle threads end after 30 s.
 *
 * GATE-1-RENDER-5b: the priority is set again before every task, not only when a thread starts.
 * Extension code is third-party and may call Process.setThreadPriority itself; a pooled thread would
 * then keep that priority for every later extension task (and Robolectric's per-test reset showed the
 * same gap: CI 44960387093). One setpriority call per task costs about a microsecond.
 */
object ExtensionThreads {
    const val SIZE = 8
    const val PRIORITY = android.os.Process.THREAD_PRIORITY_BACKGROUND
    private val n = AtomicInteger()

    private val pool = ThreadPoolExecutor(SIZE, SIZE, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "yu-ext-" + n.incrementAndGet()).apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    private fun background() {
        runCatching { android.os.Process.setThreadPriority(PRIORITY) }
    }

    /** Every task starts at [PRIORITY], whatever the previous task on that thread did. */
    private val executor = Executor { task -> pool.execute { background(); task.run() } }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
}
