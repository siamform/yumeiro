/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The in-app notification inbox (bell icon). Every notification Yumeiro sends also lands here,
 * so nothing is lost when system notifications are off. Stored on the device only.
 */
object Inbox {
    private const val TAG = "Inbox"
    private const val MAX = 100

    enum class Kind(val label: String) {
        EPISODE("New episode"), EXTENSION("Extensions"), PLAYLIST("Streams"), UPDATE("App update"),
        NEWS("What's new"), NUDGE("For you"), INFO("Yumeiro"),
        /** Maintenance / outage notices and messages for this device (push). */
        ALERT(tr(R.string.service_alert, "Service alert"));
        companion object { fun of(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: INFO }
    }

    data class Item(
        val id: String,
        val kind: Kind,
        val title: String,
        val body: String,
        val at: Long,
        val read: Boolean = false,
        /** Optional: https link, or an in-app action like "update", "extensions", "streams". */
        val link: String? = null,
    )

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()
    @Volatile private var file: File? = null

    @Synchronized
    fun ensureLoaded(c: Context) {
        if (file != null) return
        val f = File(c.applicationContext.filesDir, "inbox.json").also { file = it }
        // R16 (G5): an unreadable file is kept as .bad and a leftover .tmp is used (SafeJsonFile).
        val list = dev.asfakul.yumeiro.data.SafeJsonFile.read(f) { t -> JSONArray(t).let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) } } }.value.orEmpty()
        publish(list.sortedByDescending { it.at })
    }

    /** Adds [item] unless one with the same id is already there. Returns true when it was new. */
    fun add(c: Context, item: Item): Boolean {
        ensureLoaded(c)
        synchronized(this) {
            if (_items.value.any { it.id == item.id }) return false
            publish((listOf(item) + _items.value).sortedByDescending { it.at }.take(MAX))
        }
        save()
        return true
    }

    fun markRead(c: Context, id: String) = edit(c) { l -> l.map { if (it.id == id) it.copy(read = true) else it } }
    fun markAllRead(c: Context) = edit(c) { l -> l.map { if (it.read) it else it.copy(read = true) } }
    fun remove(c: Context, id: String) = edit(c) { l -> l.filterNot { it.id == id } }
    fun clear(c: Context) = edit(c) { emptyList() }

    private fun edit(c: Context, f: (List<Item>) -> List<Item>) {
        ensureLoaded(c)
        synchronized(this) { publish(f(_items.value)) }
        save()
    }

    private fun publish(list: List<Item>) { _items.value = list; _unread.value = list.count { !it.read } }

    /*
     * N7-1: every tap in the inbox (mark read, mark all read when the page closes, remove, clear)
     * wrote and fsynced inbox.json on the main thread, and two saves from different threads used
     * the same .tmp file at once. Now writes are serialised under one lock and always write the
     * newest list; a save from the main thread runs on one background writer, a save from a
     * worker (a new notification) stays synchronous so it is on disk before the worker ends.
     */
    private val writeLock = Any()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1))

    private fun save() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) writer.launch { writeNow() } else writeNow()
    }

    private fun writeNow() {
        synchronized(writeLock) {
            val f = file ?: return
            val a = JSONArray().apply { _items.value.forEach { put(toJson(it)) } }
            runCatching { dev.asfakul.yumeiro.data.SafeJsonFile.write(f, a.toString()) }.onFailure { Log.w(TAG, "save", it) }
        }
    }

    /** For tests: waits until background saves queued so far are on disk. */
    @androidx.annotation.VisibleForTesting
    internal fun awaitSavedForTests() { kotlinx.coroutines.runBlocking { writer.launch { }.join() } }

    private fun toJson(i: Item) = JSONObject().put("id", i.id).put("kind", i.kind.name).put("title", i.title)
        .put("body", i.body).put("at", i.at).put("read", i.read).put("link", i.link ?: "")

    private fun fromJson(o: JSONObject): Item? {
        val id = o.optString("id").ifBlank { return null }
        return Item(id, Kind.of(o.optString("kind")), o.optString("title"), o.optString("body"), o.optLong("at"),
            o.optBoolean("read"), o.optString("link").ifBlank { null })
    }
}
