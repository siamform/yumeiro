/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import android.os.Build
import android.os.StrictMode
import java.util.concurrent.Executors

/**
 * R15: StrictMode in debug builds only. Disk/network on the main thread, leaked closeables and
 * leaked activities are logged (tag `StrictMode`) and kept as breadcrumbs; nothing crashes, so
 * a debug build stays usable while the remaining violations are found and fixed.
 */
object DevStrictMode {
    fun install() {
        if (!dev.asfakul.yumeiro.BuildConfig.DEBUG) return
        val listener = Executors.newSingleThreadExecutor { r -> Thread(r, "yu-strictmode").apply { isDaemon = true } }
        val thread = StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .detectCustomSlowCalls()
            .detectDiskReads()
            .detectDiskWrites()
            .apply { if (Build.VERSION.SDK_INT >= 26) detectResourceMismatches() }
            .penaltyLog()
        val vm = StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .detectLeakedRegistrationObjects()
            .penaltyLog()
        if (Build.VERSION.SDK_INT >= 28) {
            thread.penaltyListener(listener) { v -> MemoryWatch.breadcrumb("strict " + (v.javaClass.simpleName) + " " + (v.stackTrace.firstOrNull { it.className.startsWith("dev.asfakul") } ?: "")) }
            vm.penaltyListener(listener) { v -> MemoryWatch.breadcrumb("strict-vm " + v.javaClass.simpleName) }
        }
        StrictMode.setThreadPolicy(thread.build())
        StrictMode.setVmPolicy(vm.build())
    }
}
