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
            thread.penaltyListener(listener) { v -> MemoryWatch.breadcrumb(crumb(v.javaClass.simpleName, v.stackTrace)) }
            vm.penaltyListener(listener) { v -> MemoryWatch.breadcrumb("strict-vm " + v.javaClass.simpleName) }
        }
        StrictMode.setThreadPolicy(thread.build())
        StrictMode.setVmPolicy(vm.build())
    }

    /**
     * G12 X6-2: the breadcrumb used the first `dev.asfakul` frame. On the main thread the guarded
     * looper (`ExtensionGuard.install`) sits under every frame, so a violation raised from framework
     * code only (view detach, renderer) was blamed on `ExtensionGuard.kt:83` (device run 43763558754),
     * which sends a reader to the wrong file. Use the stall watchdog's app-frame filter: real callers
     * are named as before (up to 3 frames), framework-only stacks say so.
     */
    internal fun crumb(violation: String, trace: Array<StackTraceElement>): String =
        "strict $violation " + MainThreadWatchdog.appFrames(trace, 3)
}
