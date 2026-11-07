/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Local-only "recently watched / played on YouTube" list (no account, nothing leaves the device). */
object YtHistory {
    private const val PREFS = "yt_history"
    private const val KEY = "items"
    private const val MAX = 60

    private val _items = MutableStateFlow<List<YtItem>>(emptyList())
    val items: StateFlow<List<YtItem>> = _items.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context): List<YtItem> {
        if (!loaded) {
            loaded = true
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _items.value = runCatching { decode(raw) }.getOrDefault(emptyList())
        }
        return _items.value
    }

    fun add(context: Context, item: YtItem) {
        load(context)
        val next = (listOf(item) + _items.value.filterNot { it.id == item.id }).take(MAX)
        _items.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(next)).apply()
    }

    fun clear(context: Context) {
        _items.value = emptyList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    internal fun encode(list: List<YtItem>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("url", it.url).put("t", it.title).put("c", it.channel)
                .put("th", it.thumbnail ?: "").put("d", it.durationSec).put("m", it.music).put("l", it.live))
        }
    }.toString()

    internal fun decode(raw: String?): List<YtItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val a = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            YtItem(
                id = o.getString("id"), url = o.optString("url"), title = o.optString("t"), channel = o.optString("c"),
                thumbnail = o.optString("th").ifBlank { null }, durationSec = o.optLong("d"), music = o.optBoolean("m"), live = o.optBoolean("l"),
            )
        }
    }
}
