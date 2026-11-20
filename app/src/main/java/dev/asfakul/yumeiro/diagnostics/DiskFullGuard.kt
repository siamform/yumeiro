/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import android.database.sqlite.SQLiteFullException
import android.os.Looper
import android.util.Log

/**
 * FINAL-17 (ADR-049): WorkManager writes its own database on its internal background threads. When the
 * storage is full, a commit there throws [SQLiteFullException] that nothing in the app can catch, and
 * the whole app crashed (LowStorageDeviceTest.b_fillsWhileWriting, api33-lowram, run 44640073858:
 * `SystemIdInfoDao.removeSystemIdInfo` from `SystemJobScheduler.cancel`). The transaction is rolled
 * back by SQLite, so the database stays consistent; the work item is retried by WorkManager later.
 *
 * Only that exact case is kept from crashing: a full disk, on a background thread, thrown from
 * WorkManager's own code. Everything else (main thread, any other exception, any other library) still
 * reaches the previous handler and crashes as before. Each one is kept as a "Handled" crash report.
 */
object DiskFullGuard {
    private const val TAG = "DiskFullGuard"
    @Volatile private var installed = false
    @Volatile var swallowed = 0
        private set

    /** True for a full-disk error thrown from WorkManager code on a background thread. Pure → unit-tested. */
    fun isWorkManagerDiskFull(error: Throwable, mainThread: Boolean): Boolean {
        if (mainThread) return false
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            if (t is OutOfMemoryError) return false
            if (t is SQLiteFullException) return t.stackTrace.any { it.className.startsWith("androidx.work.") } ||
                error.stackTrace.any { it.className.startsWith("androidx.work.") }
            t = t.cause.takeIf { it !== t }
            depth++
        }
        return false
    }

    /** Chains in front of the current default handler. Call after CrashLog and ExtensionGuard. */
    fun install() {
        if (installed) return
        installed = true
        Thread.setDefaultUncaughtExceptionHandler(wrap(Thread.getDefaultUncaughtExceptionHandler()))
    }

    /** The handler [install] sets: keeps the WorkManager full-disk case, forwards everything else to [previous]. */
    fun wrap(previous: Thread.UncaughtExceptionHandler?) = Thread.UncaughtExceptionHandler { thread, error ->
        val main = runCatching { thread === Looper.getMainLooper()?.thread }.getOrDefault(false)
        if (isWorkManagerDiskFull(error, main)) {
            swallowed++
            runCatching { Log.w(TAG, "Storage full: WorkManager could not save on ${thread.name}; the app keeps running", error) }
            runCatching { CrashLog.nonFatal("Storage full in background work on ${thread.name} (the app kept running)", error) }
        } else {
            previous?.uncaughtException(thread, error)
        }
    }
}
