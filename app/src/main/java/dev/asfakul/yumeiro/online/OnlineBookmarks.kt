/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "My List": saved online titles, grouped by status like CloudStream's library (Watching,
 * Plan to watch, Completed…). Also remembers the episode count seen last, so the new-episode
 * check can tell what's new. Small JSON file in app storage.
 */
object OnlineBookmarks {
    private const val TAG = "OnlineBookmarks"
    private const val MAX = 1000

    enum class Status(val label: String) { WATCHING("Watching"), PLAN("Plan to watch"), ON_HOLD("On hold"), COMPLETED("Completed"), DROPPED("Dropped") }

    data class Bookmark(
        val api: String,
        val url: String,
        val name: String,
        val posterUrl: String?,
        val posterHeaders: Map<String, String>,
        val type: String?,
        val status: Status,
        val addedAt: Long,
        /** Episodes the title had when last checked (series only; -1 unknown). */
        val episodes: Int = -1,
        /** Notify about new episodes of this title. */
        val notify: Boolean = true,
        /** New episodes found since the user last opened the title. */
        val newEpisodes: Int = 0,
    ) {
        val key: String get() = "$api|$url"
        fun toItem() = OnlineItem(name, url, api, posterUrl, posterHeaders, type)
    }

    private val _items = MutableStateFlow<List<Bookmark>>(emptyList())
    /** Most recently added first. */
    val items: StateFlow<List<Bookmark>> = _items.asStateFlow()
    @Volatile private var loaded = false
    private var file: File? = null

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.applicationContext.filesDir, "online_bookmarks.json").also { file = it }
        val a = runCatching { if (f.exists()) JSONObject(f.readText()).optJSONArray("items") else null }.getOrNull() ?: return
        _items.value = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::from) }.sortedByDescending { it.addedAt }
    }

    fun get(api: String, url: String): Bookmark? = _items.value.firstOrNull { it.api == api && it.url == url }
    fun isSaved(api: String, url: String): Boolean = get(api, url) != null

    fun save(context: Context, item: OnlineItem, status: Status = Status.PLAN, episodes: Int = -1) {
        ensureLoaded(context)
        val old = get(item.api, item.url)
        val b = Bookmark(
            item.api, item.url, item.name, item.posterUrl ?: old?.posterUrl, item.posterHeaders.ifEmpty { old?.posterHeaders.orEmpty() },
            item.type ?: old?.type, status, old?.addedAt ?: System.currentTimeMillis(),
            if (episodes >= 0) episodes else old?.episodes ?: -1, old?.notify ?: true, old?.newEpisodes ?: 0,
        )
        update { list -> listOf(b) + list.filterNot { it.key == b.key } }
    }

    /** Adds or removes [item]; returns true when it's saved afterwards. */
    fun toggle(context: Context, item: OnlineItem, episodes: Int = -1): Boolean {
        ensureLoaded(context)
        return if (isSaved(item.api, item.url)) { remove(context, item.api, item.url); false } else { save(context, item, Status.PLAN, episodes); true }
    }

    fun setStatus(context: Context, api: String, url: String, status: Status) {
        ensureLoaded(context)
        update { list -> list.map { if (it.api == api && it.url == url) it.copy(status = status) else it } }
    }

    fun setNotify(context: Context, api: String, url: String, notify: Boolean) {
        ensureLoaded(context)
        update { list -> list.map { if (it.api == api && it.url == url) it.copy(notify = notify) else it } }
    }

    /** The title was opened: remember its episode count and clear its "new" badge. */
    fun seen(context: Context, api: String, url: String, episodes: Int) {
        ensureLoaded(context)
        val b = get(api, url) ?: return
        if (b.episodes == episodes && b.newEpisodes == 0) return
        update { list -> list.map { if (it.key == b.key) it.copy(episodes = episodes, newEpisodes = 0) else it } }
    }

    /** Background check result: [episodes] now available. Returns how many are new. */
    fun checked(context: Context, api: String, url: String, episodes: Int): Int {
        ensureLoaded(context)
        val b = get(api, url) ?: return 0
        val added = if (b.episodes >= 0 && episodes > b.episodes) episodes - b.episodes else 0
        update { list -> list.map { if (it.key == b.key) it.copy(episodes = episodes, newEpisodes = it.newEpisodes + added) else it } }
        return added
    }

    fun remove(context: Context, api: String, url: String) {
        ensureLoaded(context)
        update { list -> list.filterNot { it.api == api && it.url == url } }
    }

    private fun update(change: (List<Bookmark>) -> List<Bookmark>) {
        synchronized(this) { _items.value = change(_items.value).take(MAX) }
        write()
    }

    private fun write() {
        val f = file ?: return
        val o = JSONObject().put("version", 1).put("items", JSONArray().apply { _items.value.forEach { put(json(it)) } })
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }.onFailure { Log.w(TAG, "save", it) }
    }

    private fun json(b: Bookmark) = JSONObject()
        .put("api", b.api).put("url", b.url).put("name", b.name).put("poster", b.posterUrl)
        .put("posterHeaders", JSONObject(b.posterHeaders)).put("type", b.type).put("status", b.status.name)
        .put("at", b.addedAt).put("episodes", b.episodes).put("notify", b.notify).put("new", b.newEpisodes)

    internal fun from(o: JSONObject): Bookmark? {
        val api = o.optString("api").ifBlank { return null }
        val url = o.optString("url").ifBlank { return null }
        val headers = o.optJSONObject("posterHeaders")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        return Bookmark(
            api, url, o.optString("name").ifBlank { "Untitled" }, o.optString("poster").ifBlank { null }, headers,
            o.optString("type").ifBlank { null },
            runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.PLAN),
            o.optLong("at"), o.optInt("episodes", -1), o.optBoolean("notify", true), o.optInt("new", 0),
        )
    }

    /** For tests. */
    internal fun reset() { synchronized(this) { _items.value = emptyList(); loaded = true; file = null } }
}
