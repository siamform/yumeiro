/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.ActivityManager
import android.content.Context

/**
 * Round 13: low-end phones (Android Go, 2 to 3 GB of RAM, small heap) stuttered while scrolling.
 * On those we skip image crossfades and decode posters as 16-bit, which halves bitmap memory and
 * the work done per frame.
 */
object DeviceClass {
    @Volatile var lowEnd: Boolean = false
        private set

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        lowEnd = isLowEnd(am.isLowRamDevice, am.memoryClass, info.totalMem, Runtime.getRuntime().availableProcessors())
    }

    /** Pure: Go devices, heaps of 192 MB or less, 3 GB of RAM or less, or 4 cores or fewer. */
    fun isLowEnd(lowRamFlag: Boolean, memoryClassMb: Int, totalMemBytes: Long, cores: Int): Boolean =
        lowRamFlag || memoryClassMb in 1..192 || totalMemBytes in 1..3_221_225_472L || cores in 1..4
}
