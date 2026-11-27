/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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

    /**
     * F-09 (ADR-033): PLAN is "Watch later". NONE is only for a Favorite that is not in My List; it is written as
     * PLAN + "nostatus" so older builds still read the title. NONE is never offered as a status chip or menu row.
     */
    enum class Status(val label: String) { WATCHING("Watching"), PLAN(tr(R.string.watch_later, "Watch later")), ON_HOLD(tr(R.string.on_hold, "On hold")), COMPLETED("Completed"), DROPPED("Dropped"), NONE(tr(R.string.no_status, "No status"));
        companion object { /** The statuses a user picks from (NONE is implied by tr(R.string.favorite_only, "Favorite only")). */ val picks: List<Status> get() = entries.filter { it != NONE } }
    }

    /** F-09: what a poster shows for a saved title. */
    data class Mark(val watchLater: Boolean, val favorite: Boolean, val status: Status)

    /** F-09: result of a poster action. */
    enum class Change { ADDED, REMOVED }

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
        /** F-09: marked as a Favorite (independent of the status). */
        val favorite: Boolean = false,
    ) {
        /** In My List (a Favorite-only title is not). */
        val listed: Boolean get() = status != Status.NONE
        val key: String get() = "$api|$url"
        fun toItem() = OnlineItem(name, url, api, posterUrl, posterHeaders, type)
    }

    private val _items = MutableStateFlow<List<Bookmark>>(emptyList())
    /** Most recently added first. */
    val items: StateFlow<List<Bookmark>> = _items.asStateFlow()
    private val _marks = MutableStateFlow<Map<String, Mark>>(emptyMap())
    /** F-09: key ("api|url") to the poster mark, rebuilt on every change. Screens read it once, not per card. */
    val marks: StateFlow<Map<String, Mark>> = _marks.asStateFlow()

    private fun publish(list: List<Bookmark>) {
        _items.value = list
        _marks.value = list.associate { it.key to Mark(it.status == Status.PLAN, it.favorite, it.status) }
    }
    @Volatile private var loaded = false
    private var file: File? = null

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = dev.asfakul.yumeiro.profiles.Profiles.file(context, "online_bookmarks.json") /* PR-1 */.also { file = it }
        // R16 (G5): an unreadable file is kept as .bad and a leftover .tmp is used (SafeJsonFile).
        val a = dev.asfakul.yumeiro.data.SafeJsonFile.read(f) { JSONObject(it).getJSONArray("items") }.value ?: return
        publish((0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::from) }.sortedByDescending { it.addedAt })
    }

    fun get(api: String, url: String): Bookmark? = _items.value.firstOrNull { it.api == api && it.url == url }
    /** In My List. F-09: a Favorite-only title (status NONE) is saved but not listed. */
    fun isSaved(api: String, url: String): Boolean = get(api, url)?.listed == true

    fun save(context: Context, item: OnlineItem, status: Status = Status.PLAN, episodes: Int = -1) {
        ensureLoaded(context)
        val old = get(item.api, item.url)
        val b = Bookmark(
            item.api, item.url, item.name, item.posterUrl ?: old?.posterUrl, item.posterHeaders.ifEmpty { old?.posterHeaders.orEmpty() },
            item.type ?: old?.type, status, old?.addedAt ?: System.currentTimeMillis(),
            if (episodes >= 0) episodes else old?.episodes ?: -1, old?.notify ?: (status != Status.NONE), old?.newEpisodes ?: 0,
            old?.favorite ?: false,
        )
        update { list -> listOf(b) + list.filterNot { it.key == b.key } }
    }

    /** Adds or removes [item]; returns true when it's saved afterwards. */
    fun toggle(context: Context, item: OnlineItem, episodes: Int = -1): Boolean {
        ensureLoaded(context)
        return if (isSaved(item.api, item.url)) { leaveList(context, item.api, item.url); false } else { save(context, item, Status.PLAN, episodes); true }
    }

    /** F-09: out of My List; a Favorite stays saved with no status. */
    private fun leaveList(context: Context, api: String, url: String) {
        val b = get(api, url) ?: return
        if (b.favorite) setStatus(context, api, url, Status.NONE) else remove(context, api, url)
    }

    /**
     * F-09 poster action. Not saved or Favorite-only: Watch later. Watch later: off again (a Favorite stays).
     * Another status (Watching, Completed…): moved to Watch later.
     */
    fun toggleWatchLater(context: Context, item: OnlineItem): Change {
        ensureLoaded(context)
        val b = get(item.api, item.url)
        return when {
            b == null -> { save(context, item, Status.PLAN); Change.ADDED }
            b.status == Status.PLAN -> { leaveList(context, item.api, item.url); Change.REMOVED }
            else -> { setStatus(context, item.api, item.url, Status.PLAN); Change.ADDED }
        }
    }

    /** F-09 poster action: flips Favorite. A new Favorite outside My List has no status and no alerts. */
    fun toggleFavorite(context: Context, item: OnlineItem): Change {
        ensureLoaded(context)
        val b = get(item.api, item.url)
        return when {
            b == null -> { save(context, item, Status.NONE); setFavorite(context, item.api, item.url, true); Change.ADDED }
            !b.favorite -> { setFavorite(context, item.api, item.url, true); Change.ADDED }
            b.status == Status.NONE -> { remove(context, item.api, item.url); Change.REMOVED }
            else -> { setFavorite(context, item.api, item.url, false); Change.REMOVED }
        }
    }

    private fun setFavorite(context: Context, api: String, url: String, on: Boolean) {
        ensureLoaded(context)
        update { list -> list.map { if (it.api == api && it.url == url) it.copy(favorite = on) else it } }
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
        // R16 (G5): the count never goes down. A source that answers a short list once (one
        // dub group, a partial page) used to lower it, and the next full answer then looked like
        // new episodes and posted a false "N new episodes" notification.
        val n = maxOf(b.episodes, episodes)
        if (b.episodes == n && b.newEpisodes == 0) return
        update { list -> list.map { if (it.key == b.key) it.copy(episodes = n, newEpisodes = 0) else it } }
    }

    /** Background check result: [episodes] now available. Returns how many are new. */
    fun checked(context: Context, api: String, url: String, episodes: Int): Int {
        ensureLoaded(context)
        val b = get(api, url) ?: return 0
        val added = if (b.episodes >= 0 && episodes > b.episodes) episodes - b.episodes else 0
        if (added == 0 && episodes <= b.episodes) return 0 // short or same answer: nothing to store (see [seen])
        update { list -> list.map { if (it.key == b.key) it.copy(episodes = maxOf(it.episodes, episodes), newEpisodes = it.newEpisodes + added) else it } }
        return added
    }

    fun remove(context: Context, api: String, url: String) {
        ensureLoaded(context)
        update { list -> list.filterNot { it.api == api && it.url == url } }
    }

    private fun update(change: (List<Bookmark>) -> List<Bookmark>) {
        // One writer at a time: the new-episode check updates several series in parallel.
        synchronized(this) { publish(change(_items.value).take(MAX)); write() }
    }

    private fun write() {
        val f = file ?: return
        val o = JSONObject().put("version", 1).put("items", JSONArray().apply { _items.value.forEach { put(json(it)) } })
        runCatching { dev.asfakul.yumeiro.data.SafeJsonFile.write(f, o.toString()) }.onFailure { Log.w(TAG, "save", it) }
    }

    private fun json(b: Bookmark) = JSONObject()
        .put("api", b.api).put("url", b.url).put("name", b.name).put("poster", b.posterUrl)
        .put("posterHeaders", JSONObject(b.posterHeaders)).put("type", b.type)
        // F-09: older builds know no NONE; they read a Favorite-only title as Watch later.
        .put("status", if (b.status == Status.NONE) Status.PLAN.name else b.status.name)
        .apply { if (b.status == Status.NONE) put("nostatus", true); if (b.favorite) put("fav", true) }
        .put("at", b.addedAt).put("episodes", b.episodes).put("notify", b.notify).put("new", b.newEpisodes)

    internal fun from(o: JSONObject): Bookmark? {
        val api = o.optString("api").ifBlank { return null }
        val url = o.optString("url").ifBlank { return null }
        val headers = o.optJSONObject("posterHeaders")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        return Bookmark(
            api, url, o.optString("name").ifBlank { "Untitled" }, o.optString("poster").ifBlank { null }, headers,
            o.optString("type").ifBlank { null },
            if (o.optBoolean("nostatus")) Status.NONE else runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.PLAN),
            o.optLong("at"), o.optInt("episodes", -1), o.optBoolean("notify", true), o.optInt("new", 0),
            o.optBoolean("fav"),
        )
    }

    /** For tests. */
    internal fun reset() { synchronized(this) { publish(emptyList()); loaded = true; file = null } }
    /** For tests: forget the loaded state, so the next call reads the file again. */
    internal fun unloadForTests() { synchronized(this) { publish(emptyList()); loaded = false; file = null } }
}
