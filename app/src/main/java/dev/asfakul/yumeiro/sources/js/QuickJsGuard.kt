/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * QJS-RACE-1 (crash logs 2026-11-25, 2.3.0 on a Redmi, Android 13): SIGABRT "JNI ERROR (app bug): attempt to
 * remove stale Global" in `QuickJs.<init>` → `initGlobals`, while another thread was in `QuickJs.close`, and a
 * freeze where the main thread and a network thread waited forever in `NewGlobalRef` (the JNI global table lock
 * held by the aborting thread while tombstoned timed out).
 *
 * Cause (quickjs-kt 1.0.18 native code, jni_globals.c / quickjs_jni.c): the instance counter and the cached
 * class references are process-wide statics with no lock. `initGlobals` increments the counter and fills the
 * cache; `releaseGlobals` decrements it and, at zero, deletes every cached global ref. Two runtimes created or
 * closed on two threads at once can lose a count update, or one thread deletes the cached refs while another is
 * creating a runtime with them. Yumeiro hits this since A0: "All" search runs several Nuvio scrapers at once, each
 * in its own short-lived runtime, next to Mangayomi, novel and extension helper runtimes.
 *
 * Fix: every create and close in the app goes through this object, which runs them one at a time, and one small
 * runtime is kept open for the life of the process so the counter never reaches zero and the cached refs are
 * never deleted under a live runtime. Evaluations are not serialised: only the native create and close are.
 */
object QuickJsGuard {
    private val lock = Any()
    @Volatile private var keeper: QuickJs? = null

    /** Runtimes created through the guard and not yet closed (diagnostics and tests). */
    @Volatile var open: Int = 0
        private set

    /** A new runtime; close it with [close], never with [QuickJs.close] directly. */
    fun create(dispatcher: CoroutineDispatcher): QuickJs = synchronized(lock) {
        if (keeper == null) keeper = QuickJs.create(Dispatchers.Unconfined)
        QuickJs.create(dispatcher).also { open++ }
    }

    /** Closes [q] (once; a second call is a no-op in quickjs-kt). */
    fun close(q: QuickJs) {
        synchronized(lock) {
            if (q.isClosed) return
            try { q.close() } finally { open = (open - 1).coerceAtLeast(0) }
        }
    }
}
