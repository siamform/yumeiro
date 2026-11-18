/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.data.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Round 13: Home → Continue ("Jump back in") shows the item really played last, at the position it
 * was left, for songs from any source (device, YouTube Music, servers) and for YouTube videos,
 * which the device and online histories don't time-stamp.
 *
 * G6-QP (ADR-019): the queue that song was played in is kept too, in `last_queue.json` through
 * [SafeJsonFile] (synced temp file + rename; a broken file is kept as `.bad`). The queue is written
 * when it changes (play, play next, add, remove), on one background thread, latest write wins. The
 * position is not in that file: it stays in the `audio` entry, written every 5 s, on pause and on a
 * track change, and the queue is matched to it by track id. Before, Home Continue rebuilt the
 * queue from Recently played after a process death, so the order and the songs not yet played
 * were lost. A missing, broken or old file falls back to that behaviour ([continueQueue]).
 */
object LastPlayed {
    private const val PREFS = "yu_last_played"
    /** G6-QP: the queue file under filesDir. Left out of backups (device-specific URIs, like a cache). */
    const val QUEUE_FILE = "last_queue.json"
    /** G6-QP: a queue longer than this is saved as a window around the current song. */
    internal const val QUEUE_MAX = 1000
    private const val QUEUE_BEFORE = 200

    /** G6-QP: the saved queue; [currentId] is the song that was current when it was written. */
    data class SavedQueue(val tracks: List<AudioTrack>, val currentId: String?, val at: Long)

    private val _queue = MutableStateFlow<SavedQueue?>(null)
    val queue: StateFlow<SavedQueue?> = _queue.asStateFlow()
    @Volatile private var queueLoaded = false
    private val pendingQueue = AtomicReference<Pair<File, SavedQueue?>?>(null)
    private val queueWriter by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "yu-last-queue").apply { isDaemon = true } } }

    data class Audio(val track: AudioTrack, val positionMs: Long, val durationMs: Long, val at: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private val _audio = MutableStateFlow<Audio?>(null)
    val audio: StateFlow<Audio?> = _audio.asStateFlow()
    /** YouTube video id → when it was last opened. */
    private val _ytAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val ytAt: StateFlow<Map<String, Long>> = _ytAt.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _audio.value = runCatching { decodeAudio(p.getString("audio", null)) }.getOrNull()
        _ytAt.value = runCatching { JSONObject(p.getString("yt", null) ?: "{}").let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } }.getOrDefault(emptyMap())
    }

    fun saveAudio(context: Context, track: AudioTrack, positionMs: Long, durationMs: Long) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        val a = Audio(track, positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L), System.currentTimeMillis())
        _audio.value = a
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("audio", encodeAudio(a)).apply()
    }

    /**
     * G6-QP: reads the saved queue once. Call off the main thread (MainViewModel does, on IO).
     * A broken file is kept as `.bad` by [SafeJsonFile] and the old single-song Continue is used.
     */
    fun loadQueue(context: Context) {
        if (queueLoaded) return
        queueLoaded = true
        val f = File(context.applicationContext.filesDir, QUEUE_FILE)
        val saved = SafeJsonFile.read(f) { decodeQueue(it) ?: throw IllegalArgumentException("not a queue") }.value
        // A queue saved while this read ran is newer than the file: keep it.
        _queue.compareAndSet(null, saved)
    }

    /**
     * G6-QP: saves [tracks] with [current] as the current song. Empty queues are not saved, so
     * Stop keeps the queue Home Continue plays. Incognito saves nothing. Returns at once; the
     * JSON is built and the file written on one background thread and only the latest request is written.
     */
    fun saveQueue(context: Context, tracks: List<AudioTrack>, current: AudioTrack?) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        if (tracks.isEmpty()) return
        val q = SavedQueue(window(tracks, current?.id), current?.id, System.currentTimeMillis())
        queueLoaded = true
        _queue.value = q
        val f = File(context.applicationContext.filesDir, QUEUE_FILE)
        if (pendingQueue.getAndSet(f to q) == null) queueWriter.execute { flushQueue() }
    }

    private fun flushQueue() {
        val (f, q) = pendingQueue.getAndSet(null) ?: return
        runCatching { if (q == null) { f.delete(); SafeJsonFile.tmpOf(f).delete() } else SafeJsonFile.write(f, encodeQueue(q)) }
            .onFailure { runCatching { android.util.Log.w("LastPlayed", "queue write failed: ${it.javaClass.simpleName}") } }
    }

    /** Tests: forgets what was loaded, as in a new process. */
    internal fun resetForTests() { loaded = false; queueLoaded = false; _audio.value = null; _ytAt.value = emptyMap(); _queue.value = null }

    /** Tests: waits until the queue file is written. */
    internal fun awaitQueueWrites() { queueWriter.submit { }.get() }

    /**
     * G6-QP: the queue Home Continue plays for [track]. The saved queue when it holds [track];
     * otherwise [fallback] (the behaviour before G6-QP: Recently played, the library, or the
     * song alone). Pure.
     */
    fun continueQueue(saved: SavedQueue?, track: AudioTrack, fallback: () -> List<AudioTrack>): List<AudioTrack> {
        val tracks = saved?.tracks.orEmpty()
        if (tracks.any { it.id == track.id }) return tracks
        return fallback()
    }

    fun markYouTube(context: Context, id: String) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        val next = (_ytAt.value + (id to System.currentTimeMillis())).entries.sortedByDescending { it.value }.take(60).associate { it.key to it.value }
        _ytAt.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("yt", JSONObject(next as Map<*, *>).toString()).apply()
    }

    fun clear(context: Context) {
        _audio.value = null; _ytAt.value = emptyMap(); _queue.value = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        val f = File(context.applicationContext.filesDir, QUEUE_FILE)
        if (pendingQueue.getAndSet(f to null) == null) queueWriter.execute { flushQueue() }
    }

    /** G6-QP: at most [QUEUE_MAX] songs, keeping [QUEUE_BEFORE] before the current one. Pure. */
    internal fun window(tracks: List<AudioTrack>, currentId: String?): List<AudioTrack> {
        if (tracks.size <= QUEUE_MAX) return tracks
        val i = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val start = (i - QUEUE_BEFORE).coerceIn(0, tracks.size - QUEUE_MAX)
        return tracks.subList(start, start + QUEUE_MAX).toList()
    }

    private fun trackJson(t: AudioTrack) = JSONObject()
        .put("id", t.id).put("t", t.title).put("ar", t.artist).put("al", t.album)
        .put("d", t.durationMs).put("u", t.uri).put("art", t.albumArtUri ?: "").put("aid", t.albumId)
        .put("f", t.folderName).put("fp", t.folderPath)

    private fun trackFrom(o: JSONObject): AudioTrack? {
        val t = AudioTrack(
            id = o.getString("id"), title = o.optString("t"), artist = o.optString("ar"), album = o.optString("al"),
            durationMs = o.optLong("d"), uri = o.optString("u"), albumArtUri = o.optString("art").ifBlank { null },
            albumId = o.optLong("aid"), folderName = o.optString("f", "Music"), folderPath = o.optString("fp"),
        )
        return t.takeIf { it.id.isNotBlank() && it.uri.isNotBlank() }
    }

    /** G6-QP: format 1: {"v":1,"cur":id,"at":ms,"q":[track…]}. Pure. */
    internal fun encodeQueue(q: SavedQueue): String = JSONObject()
        .put("v", 1).put("cur", q.currentId ?: "").put("at", q.at)
        .put("q", JSONArray().apply { q.tracks.forEach { put(trackJson(it)) } }).toString()

    /** G6-QP: null when [raw] is not a queue file (old, empty or another format). Throws on broken JSON. Pure. */
    internal fun decodeQueue(raw: String?): SavedQueue? {
        if (raw.isNullOrBlank()) return null
        val o = JSONObject(raw)
        val arr = o.optJSONArray("q") ?: return null
        val tracks = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { trackFrom(it) }.getOrNull() } }
        if (tracks.isEmpty()) return null
        return SavedQueue(tracks, o.optString("cur").ifBlank { null }, o.optLong("at"))
    }

    internal fun encodeAudio(a: Audio): String = JSONObject()
        .put("id", a.track.id).put("t", a.track.title).put("ar", a.track.artist).put("al", a.track.album)
        .put("d", a.track.durationMs).put("u", a.track.uri).put("art", a.track.albumArtUri ?: "").put("aid", a.track.albumId)
        .put("f", a.track.folderName).put("fp", a.track.folderPath)
        .put("pos", a.positionMs).put("dur", a.durationMs).put("at", a.at).toString()

    internal fun decodeAudio(raw: String?): Audio? {
        if (raw.isNullOrBlank()) return null
        val o = JSONObject(raw)
        val t = AudioTrack(
            id = o.getString("id"), title = o.optString("t"), artist = o.optString("ar"), album = o.optString("al"),
            durationMs = o.optLong("d"), uri = o.optString("u"), albumArtUri = o.optString("art").ifBlank { null },
            albumId = o.optLong("aid"), folderName = o.optString("f", "Music"), folderPath = o.optString("fp"),
        )
        if (t.uri.isBlank()) return null
        return Audio(t, o.optLong("pos"), o.optLong("dur"), o.optLong("at"))
    }
}
