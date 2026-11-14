/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context

/**
 * Round 13: what a tap on a source's poster did last time (played straight away or opened the
 * title page first), per source and content type. The loading skeleton follows it, so a title
 * that opens a page shows a page skeleton and a direct video shows only the player.
 */
object OpeningKinds {
    private const val PREFS = "yu_opening_kinds"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile private var loaded = false

    internal fun key(api: String, type: String?): String = api.lowercase() + "|" + type?.lowercase()?.trim().orEmpty()

    private val streak = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Round 2: known only after the same answer twice in a row; null = show the neutral loader. */
    fun opensPage(context: Context, item: OnlineItem): Boolean? {
        load(context)
        val k = key(item.api, item.type)
        return cache[k]?.takeIf { (streak[k] ?: 0) >= CONFIDENT }
    }

    fun learn(context: Context, item: OnlineItem, opensPage: Boolean) {
        load(context)
        val k = key(item.api, item.type)
        val n = if (cache[k] == opensPage) ((streak[k] ?: 0) + 1).coerceAtMost(99) else 1
        if (cache[k] == opensPage && streak[k] == n) return
        cache[k] = opensPage; streak[k] = n
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(k, opensPage).putInt("$k#n", n).apply()
    }

    internal const val CONFIDENT = 2

    private fun load(context: Context) {
        if (loaded) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (v is Boolean) cache[k] = v
            if (v is Int && k.endsWith("#n")) streak[k.removeSuffix("#n")] = v
        }
        loaded = true
    }
}
