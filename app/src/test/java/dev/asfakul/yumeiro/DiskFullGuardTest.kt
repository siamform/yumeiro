/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.database.sqlite.SQLiteFullException
import dev.asfakul.yumeiro.diagnostics.DiskFullGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FINAL-17: a full disk inside WorkManager's background threads must not crash the app; nothing else is hidden. */
@RunWith(RobolectricTestRunner::class)
class DiskFullGuardTest {
    private fun frames(vararg classes: String) = classes.map { StackTraceElement(it, "run", "X.kt", 1) }.toTypedArray()
    private fun full(vararg classes: String) = SQLiteFullException("database or disk is full (code 13 SQLITE_FULL)").apply { stackTrace = frames(*classes) }

    private val workFrames = arrayOf("android.database.sqlite.SQLiteConnection", "androidx.work.impl.model.SystemIdInfoDao_Impl",
        "androidx.work.impl.background.systemjob.SystemJobScheduler", "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker")

    @Test fun workManagerDiskFullOnABackgroundThreadIsRecognised() {
        assertTrue(DiskFullGuard.isWorkManagerDiskFull(full(*workFrames), mainThread = false))
        assertTrue(DiskFullGuard.isWorkManagerDiskFull(RuntimeException("wrapped", full(*workFrames)), mainThread = false))
    }

    @Test fun everythingElseStillCrashes() {
        assertFalse("main thread", DiskFullGuard.isWorkManagerDiskFull(full(*workFrames), mainThread = true))
        assertFalse("our own database", DiskFullGuard.isWorkManagerDiskFull(full("dev.asfakul.yumeiro.data.AppDatabase"), mainThread = false))
        assertFalse("another exception in WorkManager", DiskFullGuard.isWorkManagerDiskFull(
            IllegalStateException("x").apply { stackTrace = frames(*workFrames) }, mainThread = false))
        assertFalse("out of memory", DiskFullGuard.isWorkManagerDiskFull(OutOfMemoryError(), mainThread = false))
    }

    @Test fun theHandlerKeepsTheAppAliveOnlyForThatCase() {
        val forwarded = ArrayList<Throwable>()
        val h = DiskFullGuard.wrap { _, e -> forwarded += e }
        val before = DiskFullGuard.swallowed
        val bg = Thread({}, "WM.task-1")
        h.uncaughtException(bg, full(*workFrames))
        assertEquals("disk full in WorkManager is not forwarded", 0, forwarded.size)
        assertEquals(before + 1, DiskFullGuard.swallowed)
        val other = IllegalStateException("real bug")
        h.uncaughtException(bg, other)
        assertEquals(listOf<Throwable>(other), forwarded)
        h.uncaughtException(Thread.currentThread().takeIf { android.os.Looper.getMainLooper().thread === it } ?: bg, full("dev.asfakul.yumeiro.X"))
        assertEquals(2, forwarded.size)
    }
}
