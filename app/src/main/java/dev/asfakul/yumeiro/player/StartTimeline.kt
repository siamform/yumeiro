/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.os.SystemClock
import android.util.Log

/**
 * R16: where the time goes between a tap on Play and the first picture. One line per stage in
 * logcat (`YuStart`), relative to the tap, so a slow start can be split into source lookup,
 * extension answer, network and decoder setup on any device, including a user's phone.
 */
object StartTimeline {
    private const val TAG = "YuStart"
    @Volatile private var t0 = 0L
    @Volatile private var label = ""
    private val seen = java.util.Collections.synchronizedSet(HashSet<String>())

    fun begin(what: String) {
        t0 = SystemClock.elapsedRealtime(); label = what.take(60); seen.clear()
        Log.i(TAG, "+0ms tap $label")
    }

    /** Logs [stage] once per start; later repeats (a failover) are logged with a suffix. */
    fun mark(stage: String, detail: String = "") {
        if (t0 == 0L) return
        val key = if (seen.add(stage)) stage else "$stage(again)"
        Log.i(TAG, "+${SystemClock.elapsedRealtime() - t0}ms $key${if (detail.isBlank()) "" else " · ${detail.take(100)}"} [$label]")
    }

    fun sinceTapMs(): Long = if (t0 == 0L) -1 else SystemClock.elapsedRealtime() - t0
}
