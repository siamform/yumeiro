/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Keeps the hidden `memo` data (extensions-lib 1.6) that a source attaches to its manga and
 * chapters, so it can be handed back on the next call — some sources need it (a series slug, an
 * id) to build their requests. Yumeiro only stores manga / chapter urls, so the memo lives here,
 * one small file per source. Empty memos (most sources) are never written.
 */
class MemoStore(private val file: File?, private val max: Int = 5000) {
    private val map = object : LinkedHashMap<String, JsonObject>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?) = size > max
    }
    private var loaded = false

    @Synchronized
    fun get(key: String): JsonObject? {
        load()
        return map[key]
    }

    /** Saves the memos of a batch; writes the file once, only if something changed. */
    @Synchronized
    fun putAll(entries: Iterable<Pair<String, JsonObject>>) {
        load()
        var changed = false
        for ((key, memo) in entries) {
            if (memo.isEmpty()) continue
            if (map.put(key, memo) != memo) changed = true
        }
        if (changed) save()
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val f = file ?: return
        runCatching {
            if (f.isFile) Json.parseToJsonElement(f.readText()).jsonObject.forEach { (k, v) -> map[k] = v.jsonObject }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(JsonObject(map.toMap()).toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    companion object {
        fun mangaKey(url: String) = "m:$url"
        fun chapterKey(url: String) = "c:$url"
    }
}
