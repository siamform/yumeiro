/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking.v2

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.AnimeIds
import dev.asfakul.yumeiro.online.anime.MyAnimeList
import dev.asfakul.yumeiro.tracking.Kitsu
import dev.asfakul.yumeiro.tracking.Simkl
import dev.asfakul.yumeiro.tracking.Trakt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TRACK-2: Tracking V2. Off by default; V1 (AnimeSync.onProgress) stays exactly as it was and is
 * used whenever V2 is off. With V2 on:
 *  - videos (anime, shows, movies) at 85 % and manga / novel chapters read to the end go into
 *    [TrackQueue] on disk;
 *  - the queue is sent right away, and again by WorkManager whenever the phone is online, until
 *    each tracker has it (backoff per [TrackQueue.backoff]);
 *  - titles without tracker ids are matched with [TrackMatch] (no guessing: a weak match sends nothing);
 *  - AniList and MyAnimeList also get manga and novel chapters (never lowered, completed at the end).
 * Incognito records nothing, as in V1.
 */
object TrackV2 {
    private const val TAG = "TrackV2"
    private const val PREFS = "tracking_v2"
    private const val WORK = "tracking_v2_flush"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    data class LogLine(val at: Long, val title: String, val unit: Int, val tracker: String, val ok: Boolean, val note: String)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()
    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()
    @Volatile private var loaded = false

    /** Called once at start-up off the main thread (reads prefs and the queue file). */
    fun init(context: Context) {
        val app = context.applicationContext
        _enabled.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
        loadQueue(app)
        if (_enabled.value && _queue.value.isNotEmpty()) schedule(app)
    }

    fun setEnabled(context: Context, on: Boolean) {
        val app = context.applicationContext
        _enabled.value = on
        scope.launch { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply() }
        if (on) scope.launch { loadQueue(app); if (_queue.value.isNotEmpty()) schedule(app) }
    }

    private fun file(app: Context) = File(app.filesDir, "tracking_v2.json")

    @Synchronized private fun loadQueue(app: Context) {
        if (loaded) return
        _queue.value = SafeJsonFile.read(file(app)) { TrackQueue.decode(it) }.value.orEmpty()
        loaded = true
    }

    private fun save(app: Context, q: List<TrackItem>) {
        _queue.value = q
        runCatching { SafeJsonFile.write(file(app), TrackQueue.encode(q)) }.onFailure { Log.w(TAG, "save", it) }
    }

    /** Signed-in trackers for [kind]. */
    fun trackersFor(kind: TrackKind, anime: Boolean): Set<String> = buildSet {
        val reading = kind.reading
        if ((anime || reading) && AniList.signedIn) add("anilist")
        if ((anime || reading) && MyAnimeList.signedIn) add("mal")
        if (anime && Kitsu.signedIn) add("kitsu")
        if (!reading && Trakt.signedIn) add("trakt")
        if (!reading && Simkl.signedIn) add("simkl")
    }

    /** V2 replacement for AnimeSync.onProgress. Returns true when V2 handled it (V1 must not run). */
    fun onVideo(context: Context, detail: OnlineDetail, episode: OnlineEpisode?, index: Int, positionMs: Long, durationMs: Long): Boolean {
        if (!_enabled.value) return false
        if (durationMs <= 0 || positionMs < durationMs * 0.85) return true
        if (detail.kind == OnlineKind.LIVE || detail.kind == OnlineKind.OTHER) return true
        val anime = AnimeIds.isAnime(detail)
        val kind = when { anime -> TrackKind.ANIME; detail.kind == OnlineKind.MOVIE || episode == null -> TrackKind.MOVIE; else -> TrackKind.SHOW }
        val unit = episode?.episode ?: (index + 1).takeIf { episode != null } ?: 1
        add(context, TrackItem(
            key = "${kind.name}|${detail.api}|${detail.url}", kind = kind, title = detail.name, year = detail.year, unit = unit,
            season = episode?.season, malId = detail.malId, aniListId = detail.aniListId, imdbId = detail.imdbId, tmdbId = detail.tmdbId,
            pending = trackersFor(kind, anime), at = System.currentTimeMillis(),
        ))
        return true
    }

    /** A manga chapter read to its last page. [source] + [url] identify the manga. */
    fun onMangaChapter(context: Context, source: String, url: String, title: String, chapterName: String, chapterNumber: Double?, total: Int?) =
        reading(context, TrackKind.MANGA, source, url, title, TrackQueue.chapterNumber(chapterName, chapterNumber), total)

    fun onNovelChapter(context: Context, source: String, url: String, title: String, chapterName: String, chapterNumber: Double?, index: Int) =
        reading(context, TrackKind.NOVEL, source, url, title, TrackQueue.chapterNumber(chapterName, chapterNumber) ?: (index + 1), null)

    private fun reading(context: Context, kind: TrackKind, source: String, url: String, title: String, unit: Int?, total: Int?) {
        if (!_enabled.value || unit == null || title.isBlank()) return
        add(context, TrackItem(key = "${kind.name}|$source|$url", kind = kind, title = title, unit = unit, total = total,
            pending = trackersFor(kind, false), at = System.currentTimeMillis()))
    }

    private fun add(context: Context, item: TrackItem) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value || item.pending.isEmpty()) return
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                loadQueue(app)
                val before = _queue.value
                val after = TrackQueue.enqueue(before, item)
                if (after === before) return@launch // same or lower unit than what is waiting
                save(app, after)
            }
            flush(app)
        }
    }

    /** Asks WorkManager to send the queue when online (survives the app being closed). */
    fun schedule(context: Context, delayMs: Long = 0L) {
        val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
        val req = OneTimeWorkRequestBuilder<TrackV2Worker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS).build()
        runCatching { wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req) }
    }

    fun syncNow(context: Context) { val app = context.applicationContext; scope.launch { flush(app, force = true) } }

    /** Sends everything that is due. Returns true when nothing is left waiting. */
    suspend fun flush(app: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            loadQueue(app)
            val now = System.currentTimeMillis()
            val due = if (force) _queue.value else TrackQueue.due(_queue.value, now)
            if (due.isEmpty()) return@withLock
            val client = NetworkClient.get(app)
            var q = _queue.value
            for (item in due) {
                val done = HashSet<String>(); var error: String? = null
                for (t in item.pending) {
                    val r = runCatching { send(app, client, item, t) }
                    r.onSuccess { note -> done += t; line(item, t, true, note) }
                    r.onFailure { e -> error = "$t: ${e.message?.take(120) ?: e.javaClass.simpleName}"; line(item, t, false, error!!) }
                }
                q = TrackQueue.afterRound(q, item.key, done, error, System.currentTimeMillis())
            }
            save(app, q)
        }
        val left = _queue.value
        if (left.isNotEmpty()) schedule(app, (left.minOf { it.nextAt } - System.currentTimeMillis()).coerceAtLeast(15_000L))
        left.isEmpty()
    }

    private fun line(item: TrackItem, tracker: String, ok: Boolean, note: String) {
        _log.value = (listOf(LogLine(System.currentTimeMillis(), item.title, item.unit, tracker, ok, note)) + _log.value).take(30)
    }

    /** One tracker for one item. Returns a short note; throws to retry later. */
    private fun send(app: Context, client: OkHttpClient, item: TrackItem, tracker: String): String {
        val needIds = item.kind == TrackKind.ANIME || item.kind.reading
        var al = item.aniListId; var mal = item.malId; var total = item.total
        if (needIds && tracker != "trakt" && (al == null || (mal == null && tracker in setOf("mal", "kitsu")))) {
            val m = matchCache[item.key] ?: TrackMatch.search(client, item.kind, item.title, item.year).also { matchCache[item.key] = it ?: NO_MATCH }
            if (m != null && m !== NO_MATCH) { al = al ?: m.aniListId; mal = mal ?: m.malId; total = total ?: m.total }
        }
        fun noMatch(): String = "no confident match, not sent"
        return when (tracker) {
            "anilist" -> {
                val id = al ?: return noMatch()
                if (item.kind.reading) TrackReading.aniList(client, id, item.unit, total) else AniList.watched(client, id, item.unit, total)
                "AniList ${if (item.kind.reading) "chapter" else "episode"} ${item.unit}"
            }
            "mal" -> {
                val id = mal ?: return noMatch()
                if (item.kind.reading) TrackReading.myAnimeList(app, client, id, item.unit, total) else MyAnimeList.watched(app, client, id, item.unit, total)
                "MyAnimeList ${item.unit}"
            }
            "kitsu" -> { val id = mal ?: return noMatch(); Kitsu.watched(app, client, id, item.unit, total); "Kitsu episode ${item.unit}" }
            "trakt" -> {
                val ep = if (item.kind == TrackKind.MOVIE) null else item.unit
                when (Trakt.watched(app, client, item.title, item.year, item.season, ep, tmdb = item.tmdbId, imdb = item.imdbId)) {
                    Trakt.Sent.ADDED -> "Trakt ${ep?.let { "episode $it" } ?: "movie"}"
                    Trakt.Sent.NOT_FOUND -> "not found on Trakt, not sent"
                    // B18: these were logged as sent. Throwing keeps the item queued for after a new sign-in.
                    Trakt.Sent.SIGNED_OUT -> throw IllegalStateException("Trakt sign-in expired. Sign in again in Tracking")
                    Trakt.Sent.REJECTED -> throw IllegalStateException("Trakt didn't accept it")
                }
            }
            "simkl" -> {
                val ep = if (item.kind == TrackKind.MOVIE) null else item.unit
                // B18 pattern fix: a false answer (Simkl didn't find it) was logged as sent too.
                if (Simkl.watched(app, client, Simkl.Match(item.title, item.year, mal, al, item.imdbId, item.tmdbId), item.season, ep)) "Simkl ${ep ?: "movie"}"
                else "not found on Simkl, not sent"
            }
            else -> "unknown tracker"
        }
    }

    private val NO_MATCH = TrackMatch.Match(null, null, null, 0.0)
    private val matchCache = java.util.concurrent.ConcurrentHashMap<String, TrackMatch.Match>()
}

/** Sends the Tracking V2 queue when the phone is online. */
class TrackV2Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!TrackV2.enabled.value) TrackV2.init(applicationContext)
        if (!TrackV2.enabled.value) return Result.success()
        TrackV2.flush(applicationContext)
        return Result.success() // remaining items reschedule themselves with their own backoff
    }
}
