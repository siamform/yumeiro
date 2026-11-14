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

    fun opensPage(context: Context, item: OnlineItem): Boolean? {
        load(context)
        return cache[key(item.api, item.type)]
    }

    fun learn(context: Context, item: OnlineItem, opensPage: Boolean) {
        load(context)
        val k = key(item.api, item.type)
        if (cache[k] == opensPage) return
        cache[k] = opensPage
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(k, opensPage).apply()
    }

    private fun load(context: Context) {
        if (loaded) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v is Boolean) cache[k] = v }
        loaded = true
    }
}
