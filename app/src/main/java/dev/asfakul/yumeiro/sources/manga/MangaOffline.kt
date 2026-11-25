/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * F-13 (ADR-040): manga chapters saved on the phone (app storage, removed with the app). A chapter is written
 * to a `.part` folder page by page and renamed only when every page is on disk, so a cut connection or a
 * closed app never leaves a half chapter. Reading a saved chapter needs neither the network nor the source:
 * [provider] serves the pages from disk. One chapter downloads at a time (2 GB phones).
 */
object MangaOffline {
    private const val TAG = "MangaOffline"
    private const val DIR = "manga_offline"
    /** Leave this much free space; a chapter is usually 5–40 MB. */
    private const val MIN_FREE_BYTES = 150L * 1024 * 1024

    data class Saved(
        val providerId: String, val mangaUrl: String, val mangaTitle: String,
        val chapterUrl: String, val chapterName: String, val pages: Int, val bytes: Long, val savedAt: Long,
        /** MANGA-ORDER-1: the chapter's number (source's, else read from the name); -1 = none. */
        val number: Float = -1f,
    ) {
        fun chapter() = MangaChapter(chapterUrl, chapterName, number)
    }

    /** Download progress per chapter key ([folderName]): 0..1, or absent when idle. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    /** Bumped whenever a chapter is saved or removed, so screens re-read [saved]. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val jobs = HashMap<String, Job>()

    fun folderName(providerId: String, chapterUrl: String): String =
        "c_" + MessageDigest.getInstance("SHA-256").digest("$providerId|$chapterUrl".toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private fun root(c: Context) = File(c.applicationContext.filesDir, DIR)
    private fun folder(c: Context, providerId: String, chapterUrl: String) = File(root(c), folderName(providerId, chapterUrl))
    private fun pageFile(dir: File, i: Int) = File(dir, String.format(java.util.Locale.ROOT, "%04d.img", i))

    private fun read(dir: File): Saved? = runCatching {
        val o = JSONObject(File(dir, "meta.json").readText())
        Saved(o.getString("provider"), o.getString("manga"), o.optString("title"), o.getString("chapter"),
            o.optString("name"), o.getInt("pages"), o.optLong("bytes"), o.optLong("at"),
            // Saves from before MANGA-ORDER-1 have no number: read it from the name.
            if (o.has("number")) o.optDouble("number", -1.0).toFloat() else MangaChapterOrder.number(o.optString("name")))
            .takeIf { s -> (0 until s.pages).all { pageFile(dir, it).isFile } }
    }.getOrNull()

    fun saved(c: Context, providerId: String, chapterUrl: String): Saved? = folder(c, providerId, chapterUrl).takeIf { it.isDirectory }?.let(::read)

    /**
     * MANGA-ORDER-1: the saved chapters of one title, newest first by chapter number (the order the title page and
     * [MangaChapterNav] expect), never by download time. Reads the disk: call off the main thread.
     */
    fun chapters(c: Context, providerId: String, mangaUrl: String): List<MangaChapter> =
        MangaChapterOrder.newestFirst(list(c).filter { it.providerId == providerId && it.mangaUrl == mangaUrl }.map { it.chapter() })

    /** Every saved chapter, newest download first. */
    fun list(c: Context): List<Saved> = root(c).listFiles { f -> f.isDirectory && !f.name.endsWith(".part") }
        ?.mapNotNull(::read)?.sortedByDescending { it.savedAt }.orEmpty()

    fun delete(c: Context, providerId: String, chapterUrl: String): Boolean {
        val dir = folder(c, providerId, chapterUrl)
        val had = dir.exists()
        dir.deleteRecursively()
        File(dir.path + ".part").deleteRecursively()
        if (had) _changes.value++
        return had
    }

    /** Downloads every page of [chapter] now (suspends until done); throws when a page fails. */
    suspend fun save(c: Context, provider: MangaProvider, item: MangaItem, chapter: MangaChapter): Saved = withContext(Dispatchers.IO) {
        val key = folderName(provider.id, chapter.url)
        val dir = folder(c, provider.id, chapter.url)
        read(dir)?.let { return@withContext it }
        root(c).mkdirs()
        if (root(c).usableSpace in 1 until MIN_FREE_BYTES) error("Not enough free space on this device")
        val part = File(dir.path + ".part")
        part.deleteRecursively(); part.mkdirs()
        try {
            val refs = provider.pages(chapter.url)
            if (refs.isEmpty()) error("This chapter has no pages")
            var bytes = 0L
            refs.forEachIndexed { i, ref ->
                ensureActive()
                val img = provider.image(ref)
                pageFile(part, i).writeBytes(img.bytes)
                bytes += img.bytes.size
                _progress.value = _progress.value + (key to (i + 1f) / refs.size)
            }
            val saved = Saved(provider.id, item.url, item.title, chapter.url, chapter.name, refs.size, bytes, System.currentTimeMillis(),
                MangaChapterOrder.numberOf(chapter))
            File(part, "meta.json").writeText(JSONObject().put("provider", saved.providerId).put("manga", saved.mangaUrl)
                .put("title", saved.mangaTitle).put("chapter", saved.chapterUrl).put("name", saved.chapterName)
                .put("pages", saved.pages).put("bytes", saved.bytes).put("at", saved.savedAt).put("number", saved.number.toDouble()).toString())
            dir.deleteRecursively()
            if (!part.renameTo(dir)) error("Couldn't save the chapter")
            _changes.value++
            saved
        } catch (t: Throwable) {
            part.deleteRecursively()
            throw t
        } finally {
            _progress.value = _progress.value - key
        }
    }

    /** Queues [save] in the background, one chapter at a time; [onDone] gets null on success or the error. */
    fun enqueue(c: Context, provider: MangaProvider, item: MangaItem, chapter: MangaChapter, onDone: (Throwable?) -> Unit = {}) {
        val key = folderName(provider.id, chapter.url)
        val app = c.applicationContext
        synchronized(jobs) {
            if (jobs[key]?.isActive == true) return
            _progress.value = _progress.value + (key to 0f)
            jobs[key] = scope.launch {
                val r = runCatching { gate.withLock { save(app, provider, item, chapter) } }
                r.exceptionOrNull()?.let { if (it !is kotlinx.coroutines.CancellationException) Log.w(TAG, "save: ${it.message}") }
                _progress.value = _progress.value - key
                synchronized(jobs) { jobs.remove(key) }
                withContext(Dispatchers.Main) { onDone(r.exceptionOrNull()) }
            }
        }
    }

    fun cancel(providerId: String, chapterUrl: String) {
        val key = folderName(providerId, chapterUrl)
        synchronized(jobs) { jobs.remove(key)?.cancel() }
        _progress.value = _progress.value - key
    }

    /** Reads a saved chapter from disk; works with the source removed and with no connection. */
    fun provider(c: Context, providerId: String, chapterUrl: String, fallbackName: String): MangaProvider? {
        val dir = folder(c, providerId, chapterUrl)
        val s = read(dir) ?: return null
        return object : MangaProvider {
            override val id = providerId
            override val name = fallbackName
            override val lang = ""
            override val ecosystem = "offline"
            override val nsfw = false
            override val supportsLatest = false
            override val baseUrl: String? = null
            override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
            override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
            override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
            override suspend fun details(url: String) = MangaDetails(providerId, s.mangaUrl, s.mangaTitle, null)
            // MANGA-ORDER-1: every saved chapter of the title, so Previous / Next work offline.
            override suspend fun chapters(url: String) = withContext(Dispatchers.IO) { chapters(c, providerId, s.mangaUrl) }.ifEmpty { listOf(s.chapter()) }
            override suspend fun pages(chapterUrl: String) = (0 until s.pages).map { MangaPageRef(it, "offline:${dir.name}/$it", null) }
            override suspend fun image(page: MangaPageRef) = withContext(Dispatchers.IO) { MangaImage(pageFile(dir, page.index).readBytes(), null) }
        }
    }
}
