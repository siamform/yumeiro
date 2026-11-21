/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * E4 (flag E4_SPEED_ORDER): how fast each extension's home page answered, measured on this phone.
 *
 * Yumix loads at most three extensions' homes at once; the order decides what fills the screen first. Fast
 * extensions go first, extensions never measured sit in the middle, and an extension that failed
 * [QUARANTINE_FAILS] times in a row is quarantined for [QUARANTINE_MS] and goes last, so a dead site no
 * longer holds one of the three slots while working ones wait. One success ends the quarantine.
 * Kept per phone (prefs "yu_ext_speed"); [record] runs on the extension pool, never on main.
 */
object ExtensionSpeed {
    data class Stat(val avgMs: Long, val fails: Int, val lastFailAt: Long)

    const val UNKNOWN_MS = 4_000L
    const val QUARANTINE_FAILS = 3
    const val QUARANTINE_MS = 6 * 3_600_000L
    private const val PREFS = "yu_ext_speed"
    private const val KEY = "stats"
    private const val MAX_SAVED = 200

    private val stats = ConcurrentHashMap<String, Stat>()
    @Volatile private var ctx: Context? = null

    /** Loads the saved numbers. Call off the main thread (reads prefs). */
    fun init(context: Context) {
        ctx = context.applicationContext
        runCatching {
            val raw = ctx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
            val o = JSONObject(raw)
            for (k in o.keys()) o.optJSONArray(k)?.let { a -> stats.putIfAbsent(k, Stat(a.optLong(0), a.optInt(1), a.optLong(2))) }
        }
    }

    fun stat(name: String): Stat? = stats[name]

    /** One home fetch of [name] took [ms]; [ok] = it brought rows. */
    fun record(name: String, ms: Long, ok: Boolean, now: Long = System.currentTimeMillis()) {
        stats[name] = next(stats[name], ms, ok, now)
        save()
    }

    /** Pure: the new stat after one fetch (moving average 70/30, failures counted in a row). */
    fun next(prev: Stat?, ms: Long, ok: Boolean, now: Long): Stat {
        val t = ms.coerceIn(0, 120_000)
        return if (ok) Stat(if (prev == null || prev.avgMs <= 0) t else (prev.avgMs * 7 + t * 3) / 10, 0, 0)
        else Stat(prev?.avgMs ?: 0, (prev?.fails ?: 0) + 1, now)
    }

    fun quarantined(s: Stat?, now: Long = System.currentTimeMillis()): Boolean =
        s != null && s.fails >= QUARANTINE_FAILS && now - s.lastFailAt < QUARANTINE_MS

    /** Pure: [selected] first, then by measured speed (unknown = [UNKNOWN_MS]), quarantined last; stable otherwise. */
    fun <T> order(items: List<T>, name: (T) -> String, selected: String?, stat: (String) -> Stat?, now: Long): List<T> =
        items.withIndex().sortedWith(compareBy<IndexedValue<T>>(
            { if (name(it.value) == selected) 0 else 1 },
            { if (quarantined(stat(name(it.value)), now)) 1 else 0 },
            { stat(name(it.value))?.avgMs?.takeIf { ms -> ms > 0 } ?: UNKNOWN_MS },
            { it.index },
        )).map { it.value }

    /**
     * FE-S4 (SpeedDeviceTest 44884969625: every live search waited 25 s for its slowest sites): the time a search of
     * this site may take. Measured sites get 4x their average (8..25 s); a quarantined site (3 failures in a row)
     * 8 s; unknown sites and Retry the full [maxMs]. A cut site shows "didn't answer" with Retry, which waits [maxMs].
     */
    fun searchTimeoutMs(s: Stat?, retry: Boolean, now: Long, maxMs: Long = 25_000L): Long = when {
        retry || s == null -> maxMs
        quarantined(s, now) -> MIN_SEARCH_MS
        s.avgMs > 0 -> (s.avgMs * 4).coerceIn(MIN_SEARCH_MS, maxMs)
        else -> maxMs
    }
    const val MIN_SEARCH_MS = 8_000L

    fun <T> order(items: List<T>, name: (T) -> String, selected: String?): List<T> =
        order(items, name, selected, ::stat, System.currentTimeMillis())

    private fun save() {
        val c = ctx ?: return
        runCatching {
            val o = JSONObject()
            stats.entries.take(MAX_SAVED).forEach { (k, s) -> o.put(k, org.json.JSONArray().put(s.avgMs).put(s.fails).put(s.lastFailAt)) }
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        }
    }

    internal fun resetForTest() { stats.clear(); ctx = null }
}
