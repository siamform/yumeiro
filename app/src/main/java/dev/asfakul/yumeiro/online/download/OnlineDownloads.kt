/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Offline copies of online videos. Direct files (MP4/MKV/WebM) are fetched with resume support;
 * HLS streams are saved segment by segment into one file (clear AES-128 HLS is decrypted like a
 * player would; DRM streams are never offered). Files stay in the app's own Movies folder, so
 * no storage permission is needed, and play from the Library offline.
 *
 * Kinds: [Kind.LINK] (what's playing now), [Kind.EPISODE] (queued from a title page; the link is
 * resolved when the download starts, so it never expires in the queue), [Kind.YT_VIDEO] and
 * [Kind.YT_AUDIO] (YouTube / YouTube Music, any quality). Episodes of one series share a folder
 * `Title-(Extension-(Sub))` ([DownloadNaming]). Finished items are removed after the auto-delete
 * timer unless pinned with "Keep forever" ([cleanup]).
 */
object OnlineDownloads {
    private const val TAG = "OnlineDownloads"

    enum class Status { QUEUED, RUNNING, PAUSED, DONE, FAILED }
    enum class Kind { LINK, EPISODE, YT_VIDEO, YT_AUDIO }

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
        val kind: Kind = Kind.LINK,
        /** Sub-folder inside Downloads ("" = top level). */
        val folder: String = "",
        val version: String? = null,
        /** Episode data for the extension's loadLinks (EPISODE). */
        val data: String? = null,
        /** Episode order inside its folder. */
        val order: Int = 0,
        val ytId: String? = null,
        val videoItag: Int = -1,
        val audioItag: Int = -1,
        /** Output container for YouTube downloads (mp4, webm, m4a). */
        val container: String? = null,
        /** Pinned: never auto-deleted. */
        val keep: Boolean = false,
        val finishedAt: Long = 0,
        val quality: String? = null,
        /** Copy in the phone's Movies / Music folder (YouTube downloads), when saved there. */
        val publicUri: String? = null,
        /** File name without extension for EPISODE items (extension known once the link is found). */
        val baseName: String? = null,
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

    fun folderDir(context: Context, folder: String): File = if (folder.isBlank()) dir(context) else File(dir(context), folder)

    fun fileOf(context: Context, item: Item): File = File(folderDir(context, item.folder).apply { mkdirs() }, item.fileName)

    /** Something playable is saved for [item] (in the app folder or the phone's media folders). */
    fun isSaved(context: Context, item: Item): Boolean =
        item.status == Status.DONE && (item.publicUri != null || (item.fileName.isNotBlank() && fileOf(context, item).exists()))

    fun episodeId(api: String, titleUrl: String, version: String?, data: String) =
        ("ep|" + api + "|" + titleUrl + "|" + (version ?: "") + "|" + data).hashCode().toUInt().toString(16)

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
        val folder = if (s.episode != null) DownloadNaming.seriesFolder(s.detail.name, s.api, s.version) else ""
        return enqueue(context, s.title, s.api, s.detail.url, s.episode?.label, s.detail.posterUrl, s.detail.posterHeaders, link, folder, s.version)
    }

    /** Queues [link]; returns an error text, or null when queued. */
    fun enqueue(
        context: Context, title: String, api: String, titleUrl: String, episodeLabel: String?,
        posterUrl: String?, posterHeaders: Map<String, String>, link: OnlineLink,
        folder: String = "", version: String? = null,
    ): String? {
        ensureLoaded(context)
        if (link.isDash) return "This source can't be saved (DASH). Pick another source."
        val id = (api + "|" + titleUrl + "|" + (episodeLabel ?: "")).hashCode().toUInt().toString(16)
        get(id)?.let { old -> if (isSaved(context, old)) return "Already downloaded"; if (old.active) return "Already downloading" }
        val ext = DownloadNaming.extensionFor(link)
        val name = safeName(listOfNotNull(title.takeIf { it.isNotBlank() }, link.qualityLabel.takeIf { it.isNotBlank() }).joinToString(" ")) + "-" + id + "." + ext
        val item = Item(id, title, api, titleUrl, episodeLabel, posterUrl, posterHeaders, link.url, link.isHls, headersFor(link), name,
            folder = folder, version = version, keep = DownloadSettings.keepByDefault.value, quality = link.qualityLabel.ifBlank { null })
        update { list -> listOf(item) + list.filterNot { it.id == id } }
        start(context, id)
        return null
    }

    internal fun headersFor(link: OnlineLink): Map<String, String> = buildMap {
        putAll(link.headers)
        link.referer?.let { if (keys.none { k -> k.equals("Referer", true) }) put("Referer", it) }
        if (keys.none { k -> k.equals("User-Agent", true) }) put("User-Agent", com.lagradost.cloudstream3.USER_AGENT)
    }

    /**
     * Queues whole episodes of [detail] ([episodes]: index in watch order → episode). Links are
     * found when each download starts. Returns how many were added (already saved/queued skipped).
     */
    fun enqueueEpisodes(context: Context, detail: OnlineDetail, version: String, episodes: List<Pair<Int, OnlineEpisode>>): Int {
        ensureLoaded(context); DownloadSettings.init(context)
        val folder = DownloadNaming.seriesFolder(detail.name, detail.api, version)
        val now = System.currentTimeMillis()
        val added = episodes.mapNotNull { (index, ep) ->
            val id = episodeId(detail.api, detail.url, version, ep.data)
            val old = get(id)
            if (old != null && (old.active || isSaved(context, old))) return@mapNotNull null
            Item(
                id = id, title = detail.name, api = detail.api, titleUrl = detail.url,
                episodeLabel = ep.label,
                posterUrl = ep.posterUrl ?: detail.posterUrl, posterHeaders = detail.posterHeaders,
                url = "", isHls = false, headers = emptyMap(), fileName = "",
                createdAt = now + index, kind = Kind.EPISODE, folder = folder, version = version.ifBlank { null },
                data = ep.data, order = index, keep = DownloadSettings.keepByDefault.value,
                baseName = DownloadNaming.episodeBase(ep.season, ep.episode, ep.name, index),
            )
        }
        if (added.isEmpty()) return 0
        val ids = added.map { it.id }.toSet()
        update { list -> added + list.filterNot { it.id in ids } }
        added.forEach { start(context, it.id) }
        return added.size
    }

    /** Queues a YouTube video or song in [choice] quality. Returns an error text, or null when queued. */
    fun enqueueYouTube(context: Context, ytId: String, title: String, channel: String?, thumbnail: String?, music: Boolean, choice: YtDownloadOptions.Choice): String? {
        ensureLoaded(context); DownloadSettings.init(context)
        val id = ("yt|" + ytId + "|" + choice.videoItag + "|" + choice.audioItag).hashCode().toUInt().toString(16)
        get(id)?.let { old -> if (isSaved(context, old)) return "Already downloaded"; if (old.active) return "Already downloading" }
        val base = DownloadNaming.clean(title.ifBlank { ytId }, 90)
        val suffix = if (choice.audioOnly) "" else " (" + choice.label + ")"
        val item = Item(
            id = id, title = title.ifBlank { ytId }, api = "YouTube", titleUrl = "https://www.youtube.com/watch?v=$ytId",
            episodeLabel = channel?.takeIf { it.isNotBlank() }, posterUrl = thumbnail, posterHeaders = emptyMap(),
            url = "", isHls = false, headers = emptyMap(), fileName = base + suffix + "-" + id + "." + choice.container,
            kind = if (choice.audioOnly) Kind.YT_AUDIO else Kind.YT_VIDEO,
            folder = if (choice.audioOnly || music) "YouTube Music" else "YouTube",
            ytId = ytId, videoItag = choice.videoItag, audioItag = choice.audioItag, container = choice.container,
            quality = choice.label, keep = true,
        )
        update { list -> listOf(item) + list.filterNot { it.id == id } }
        start(context, id)
        return null
    }

    fun setKeep(context: Context, id: String, keep: Boolean) { ensureLoaded(context); set(id) { it.copy(keep = keep) } }

    fun setKeepFolder(context: Context, folder: String, keep: Boolean) {
        ensureLoaded(context)
        update { list -> list.map { if (it.folder == folder) it.copy(keep = keep) else it } }
    }

    /** Deletes every download in [folder] and the folder itself. */
    fun deleteFolder(context: Context, folder: String) {
        ensureLoaded(context)
        _items.value.filter { it.folder == folder }.forEach { delete(context, it.id) }
        if (folder.isNotBlank()) runCatching { folderDir(context, folder).deleteRecursively() }
    }

    /** Removes finished, unpinned downloads older than the auto-delete timer. Returns how many. */
    fun cleanup(context: Context, now: Long = System.currentTimeMillis()): Int {
        ensureLoaded(context); DownloadSettings.init(context)
        val days = DownloadSettings.autoDeleteDays.value
        val old = _items.value.filter { it.status == Status.DONE && DownloadNaming.expired(it.finishedAt, it.keep, days, now) }
        old.forEach { delete(context, it.id) }
        // Empty series folders go too.
        runCatching { dir(context).listFiles()?.filter { it.isDirectory && it.list().isNullOrEmpty() }?.forEach { it.delete() } }
        return old.size
    }

    /** Daily auto-delete pass (cheap; no network). */
    fun scheduleCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<DownloadCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork("dl_cleanup", ExistingPeriodicWorkPolicy.KEEP, request)
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
        get(id)?.let { item ->
            if (item.fileName.isNotBlank()) runCatching {
                val f = fileOf(context, item)
                listOf(f, File(f.parentFile, f.name + ".part"), File(f.parentFile, f.name + ".v"), File(f.parentFile, f.name + ".a")).forEach { it.delete() }
            }
            item.publicUri?.let { u -> runCatching { context.contentResolver.delete(android.net.Uri.parse(u), null, null) } }
        }
        update { list -> list.filterNot { it.id == id } }
    }

    /** Resumes queued downloads after a restart. */
    fun resumeQueued(context: Context) {
        ensureLoaded(context)
        _items.value.filter { it.status == Status.QUEUED }.forEach { start(context, it.id) }
    }

    internal fun progress(id: String, bytes: Long, total: Long) = set(id, persist = false) { it.copy(status = Status.RUNNING, bytes = bytes, total = total) }
    internal fun finished(id: String, bytes: Long) = set(id) { it.copy(status = Status.DONE, bytes = bytes, total = bytes, error = null, finishedAt = System.currentTimeMillis()) }
    internal fun resolved(id: String, change: (Item) -> Item) = set(id, change = change)
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
        val o = JSONObject().put("version", 2).put("items", JSONArray().apply { _items.value.forEach { put(json(it)) } })
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
        .put("kind", i.kind.name).put("folder", i.folder).put("ver", i.version).put("data", i.data).put("order", i.order)
        .put("ytId", i.ytId).put("vItag", i.videoItag).put("aItag", i.audioItag).put("container", i.container)
        .put("keep", i.keep).put("finishedAt", i.finishedAt).put("quality", i.quality).put("publicUri", i.publicUri)
        .put("base", i.baseName)

    private fun from(o: JSONObject): Item? {
        fun map(k: String) = o.optJSONObject(k)?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        fun str(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
        val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.LINK)
        return Item(
            id = o.optString("id").ifBlank { return null }, title = o.optString("title"), api = o.optString("api"),
            titleUrl = o.optString("titleUrl"), episodeLabel = str("episode"),
            posterUrl = str("poster"), posterHeaders = map("posterHeaders"),
            url = o.optString("url").also { if (it.isBlank() && kind == Kind.LINK) return null }, isHls = o.optBoolean("hls"), headers = map("headers"),
            fileName = o.optString("file").also { if (it.isBlank() && kind != Kind.EPISODE) return null },
            status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.FAILED),
            bytes = o.optLong("bytes"), total = o.optLong("total", -1), error = str("error"), createdAt = o.optLong("at"),
            kind = kind, folder = o.optString("folder"), version = str("ver"), data = str("data"), order = o.optInt("order"),
            ytId = str("ytId"), videoItag = o.optInt("vItag", -1), audioItag = o.optInt("aItag", -1), container = str("container"),
            keep = o.optBoolean("keep"), finishedAt = o.optLong("finishedAt"), quality = str("quality"), publicUri = str("publicUri"), baseName = str("base"),
        )
    }
}
