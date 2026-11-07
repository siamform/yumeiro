/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Offline copies of online videos. Direct files (MP4/MKV/WebM) are fetched with resume support;
 * HLS streams are saved segment by segment into one file (clear AES-128 HLS is decrypted like a
 * player would; DRM streams are never offered). Files stay in the app's own Movies folder, so
 * no storage permission is needed, and play from the Library offline.
 */
object OnlineDownloads {
    private const val TAG = "OnlineDownloads"

    enum class Status { QUEUED, RUNNING, PAUSED, DONE, FAILED }

    data class Item(
        val id: String,
        val title: String,
        val api: String,
        val titleUrl: String,
        val episodeLabel: String?,
        val posterUrl: String?,
        val posterHeaders: Map<String, String>,
        val url: String,
        val isHls: Boolean,
        val headers: Map<String, String>,
        val fileName: String,
        val status: Status = Status.QUEUED,
        val bytes: Long = 0,
        val total: Long = -1,
        val error: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
    ) {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
        val active: Boolean get() = status == Status.QUEUED || status == Status.RUNNING
    }

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    @Volatile private var loaded = false
    private var file: File? = null

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "Downloads").apply { mkdirs() }

    fun fileOf(context: Context, item: Item): File = File(dir(context), item.fileName)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.applicationContext.filesDir, "online_downloads.json").also { file = it }
        val a = runCatching { if (f.exists()) JSONObject(f.readText()).optJSONArray("items") else null }.getOrNull() ?: return
        _items.value = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::from) }
            // A download that was running when the app died continues as queued.
            .map { if (it.status == Status.RUNNING) it.copy(status = Status.QUEUED) else it }
    }

    fun get(id: String): Item? = _items.value.firstOrNull { it.id == id }

    /** Downloads what's playing now (current source of the online session). */
    fun downloadCurrent(context: Context): String? {
        val s = OnlineSession.state.value ?: return "Nothing is playing"
        val link = s.link ?: return "No source yet"
        return enqueue(context, s.title, s.api, s.detail.url, s.episode?.label, s.detail.posterUrl, s.detail.posterHeaders, link)
    }

    /** Queues [link]; returns an error text, or null when queued. */
    fun enqueue(
        context: Context, title: String, api: String, titleUrl: String, episodeLabel: String?,
        posterUrl: String?, posterHeaders: Map<String, String>, link: OnlineLink,
    ): String? {
        ensureLoaded(context)
        if (link.isDash) return "This source can't be saved (DASH). Pick another source."
        val id = (api + "|" + titleUrl + "|" + (episodeLabel ?: "")).hashCode().toUInt().toString(16)
        get(id)?.let { old -> if (old.status == Status.DONE && fileOf(context, old).exists()) return "Already downloaded"; if (old.active) return "Already downloading" }
        val ext = when { link.isHls -> "ts"; link.container in setOf("mp4", "mkv", "webm", "m4v", "mov", "avi") -> link.container!!; else -> "mp4" }
        val name = safeName(listOfNotNull(title.takeIf { it.isNotBlank() }, link.qualityLabel.takeIf { it.isNotBlank() }).joinToString(" ")) + "-" + id + "." + ext
        val headers = buildMap {
            putAll(link.headers)
            link.referer?.let { if (keys.none { k -> k.equals("Referer", true) }) put("Referer", it) }
            if (keys.none { k -> k.equals("User-Agent", true) }) put("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
        }
        val item = Item(id, title, api, titleUrl, episodeLabel, posterUrl, posterHeaders, link.url, link.isHls, headers, name)
        update { list -> listOf(item) + list.filterNot { it.id == id } }
        start(context, id)
        return null
    }

    fun start(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            // No network constraint: the app doesn't hold ACCESS_NETWORK_STATE; failures retry with backoff.
            .addTag(DownloadWorker.TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("dl_$id", ExistingWorkPolicy.KEEP, request)
        set(id) { if (it.status != Status.DONE) it.copy(status = Status.QUEUED, error = null) else it }
    }

    fun pause(context: Context, id: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("dl_$id")
        set(id) { if (it.active) it.copy(status = Status.PAUSED) else it }
    }

    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("dl_$id")
        get(id)?.let { runCatching { fileOf(context, it).delete(); File(dir(context), it.fileName + ".part").delete() } }
        update { list -> list.filterNot { it.id == id } }
    }

    /** Resumes queued downloads after a restart. */
    fun resumeQueued(context: Context) {
        ensureLoaded(context)
        _items.value.filter { it.status == Status.QUEUED }.forEach { start(context, it.id) }
    }

    internal fun progress(id: String, bytes: Long, total: Long) = set(id, persist = false) { it.copy(status = Status.RUNNING, bytes = bytes, total = total) }
    internal fun finished(id: String, bytes: Long) = set(id) { it.copy(status = Status.DONE, bytes = bytes, total = bytes, error = null) }
    internal fun failed(id: String, message: String) = set(id) { it.copy(status = Status.FAILED, error = message) }
    internal fun paused(id: String) = set(id) { if (it.status != Status.DONE) it.copy(status = Status.PAUSED) else it }

    private fun set(id: String, persist: Boolean = true, change: (Item) -> Item) {
        synchronized(this) { _items.value = _items.value.map { if (it.id == id) change(it) else it } }
        if (persist) write()
    }

    private fun update(change: (List<Item>) -> List<Item>) {
        synchronized(this) { _items.value = change(_items.value) }
        write()
    }

    private var lastWrite = 0L
    private fun write() {
        val f = file ?: return
        lastWrite = System.currentTimeMillis()
        val o = JSONObject().put("version", 1).put("items", JSONArray().apply { _items.value.forEach { put(json(it)) } })
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }.onFailure { Log.w(TAG, "save", it) }
    }

    internal fun safeName(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "video" }

    private fun json(i: Item) = JSONObject()
        .put("id", i.id).put("title", i.title).put("api", i.api).put("titleUrl", i.titleUrl).put("episode", i.episodeLabel)
        .put("poster", i.posterUrl).put("posterHeaders", JSONObject(i.posterHeaders)).put("url", i.url).put("hls", i.isHls)
        .put("headers", JSONObject(i.headers)).put("file", i.fileName).put("status", i.status.name)
        .put("bytes", i.bytes).put("total", i.total).put("error", i.error).put("at", i.createdAt)

    private fun from(o: JSONObject): Item? {
        fun map(k: String) = o.optJSONObject(k)?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        return Item(
            id = o.optString("id").ifBlank { return null }, title = o.optString("title"), api = o.optString("api"),
            titleUrl = o.optString("titleUrl"), episodeLabel = o.optString("episode").ifBlank { null },
            posterUrl = o.optString("poster").ifBlank { null }, posterHeaders = map("posterHeaders"),
            url = o.optString("url").ifBlank { return null }, isHls = o.optBoolean("hls"), headers = map("headers"),
            fileName = o.optString("file").ifBlank { return null },
            status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.FAILED),
            bytes = o.optLong("bytes"), total = o.optLong("total", -1), error = o.optString("error").ifBlank { null }, createdAt = o.optLong("at"),
        )
    }
}
