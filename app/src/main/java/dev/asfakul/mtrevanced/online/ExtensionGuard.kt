/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps third-party extension code from taking the whole app down.
 *
 * CloudStream extensions are arbitrary code loaded at runtime. When several of them load their
 * home pages together (the YouTube-style "For You" feed) the chance that *one* of them throws on a
 * thread it started itself (`thread {}`, `GlobalScope.launch`, an OkHttp callback, a Handler post)
 * grows with every extension. Such an exception never reaches our `runCatching` — it goes straight
 * to the thread's uncaught-exception handler and Android kills the process. That was the
 * "For You crashes with many extensions" bug.
 *
 * [install] wraps the default handler: an uncaught throwable whose stack trace runs through
 * extension code (anything outside the app, AndroidX, Kotlin, Java and Android packages — or a
 * known extension package) is logged, counted per extension, and swallowed. On the main thread the
 * looper is restarted so the UI keeps running. Every other crash still goes to the original
 * handler, so real app bugs are not hidden.
 */
object ExtensionGuard {
    private const val TAG = "ExtensionGuard"

    /** Packages that belong to the app or the platform; a crash only in these is a real crash. */
    private val TRUSTED_PREFIXES = listOf(
        "dev.asfakul.", "android.", "androidx.", "com.android.", "java.", "javax.", "kotlin.", "kotlinx.",
        "sun.", "jdk.", "dalvik.", "libcore.", "org.json.", "org.chromium.", "com.google.", "okhttp3.", "okio.",
        "coil.", "org.schabi.newpipe.", "com.fasterxml.", "io.ktor.", "org.mozilla.", "org.jsoup.",
    )

    private val swallowed = AtomicInteger(0)
    val swallowedCount: Int get() = swallowed.get()

    @Volatile private var installed = false
    @Volatile var lastSwallowed: String? = null; private set

    /** Bounded pool for extension network/parse work so 20 extensions can't starve the app's IO pool. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(8)

    /** Logs instead of crashing when an extension coroutine fails. */
    val coroutineHandler = CoroutineExceptionHandler { _, t ->
        swallowed.incrementAndGet()
        lastSwallowed = t.toString()
        Log.w(TAG, "extension coroutine failed", t)
    }

    /** Scope for fire-and-forget extension work (supervised: one failure never cancels the rest). */
    val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineHandler)

    fun install() {
        if (installed) return
        installed = true
        if (android.os.Build.FINGERPRINT == "robolectric") return // unit tests drive the looper themselves
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread !== Looper.getMainLooper().thread && isFromExtension(error)) {
                note(error, "background thread ${thread.name}") // the worker dies quietly
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
        // Main thread: run the looper inside a guarded loop (posted once, then it never returns).
        // An extension exception unwinds only to here and the loop restarts; anything else is
        // re-thrown and crashes as usual.
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    if (!isFromExtension(t)) throw t
                    note(t, "main thread")
                }
            }
        }
    }

    private fun note(t: Throwable, where: String) {
        swallowed.incrementAndGet()
        lastSwallowed = t.toString()
        Log.e(TAG, "Swallowed a crash from extension code on $where", t)
    }

    /** True when [error] (or a cause) has a stack frame from extension code. Pure → unit-testable. */
    fun isFromExtension(error: Throwable): Boolean {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            if (t is OutOfMemoryError) return false // never hide memory pressure
            if (t.stackTrace.any { frameIsExtension(it.className) }) return true
            t = t.cause.takeIf { it !== t }
            depth++
        }
        return false
    }

    internal fun frameIsExtension(className: String): Boolean {
        if (className.isBlank()) return false
        if (TRUSTED_PREFIXES.any { className.startsWith(it) }) return false
        if (className.startsWith("com.lagradost.")) return false // the bundled CloudStream library
        // Anything else was loaded from an extension's dex (e.g. "com.example.HiAnimeProvider").
        return true
    }
}
