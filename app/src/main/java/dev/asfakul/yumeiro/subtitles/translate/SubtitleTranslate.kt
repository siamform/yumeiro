/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles.translate

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.asfakul.yumeiro.intel.ai.AiError
import dev.asfakul.yumeiro.intel.ai.AiHttp
import dev.asfakul.yumeiro.intel.ai.AiMessage
import dev.asfakul.yumeiro.intel.ai.AiRequest
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlinePlayback
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.subtitles.SubtitleHit
import dev.asfakul.yumeiro.online.subtitles.SubtitleHub
import dev.asfakul.yumeiro.online.subtitles.SubtitleNet
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.manga.MangaTlPlan
import dev.asfakul.yumeiro.sources.manga.MangaTranslate
import dev.asfakul.yumeiro.subtitles.SubtitleCharset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * B20 S6: translates the playing episode's subtitles into the profile's language. The source is the English track
 * that came with the stream, else the best English match from the subtitle sites the user linked (SubDL, SubSource,
 * OpenSubtitles). Lines are translated in batches of 40, from where the person is watching, by ML Kit on the phone
 * (the manga/novel packs) or by the profile's AI provider (TV, or when chosen). Translated lines are kept per
 * episode, so reopening, switching to romanised, or resuming after a stop never translates a line twice. The new
 * track is added next to the original and turned on; the original stays selectable.
 */
object SubtitleTranslate {
    private const val TAG = "SubtitleTranslate"
    private const val PREFS = "sub_tl"
    const val BATCH = 40
    private const val MAX_SOURCE = 4L * 1024 * 1024

    enum class Mode(val code: String, val label: String) {
        NATIVE("native", "Translated"), LATIN("latin", "Romanised"), BOTH("both", "Both lines");
        companion object { fun of(c: String?) = entries.firstOrNull { it.code == c } ?: NATIVE }
    }

    enum class Engine(val code: String, val label: String) {
        DEVICE("device", "On this phone"), AI("ai", "Yumeiro AI");
        companion object { fun of(c: String?) = entries.firstOrNull { it.code == c } }
    }

    data class Settings(val target: String, val mode: Mode, val engine: Engine?)

    sealed interface Status {
        data object Idle : Status
        data class Finding(val what: String) : Status
        data class Translating(val done: Int, val total: Int, val engine: Engine, val source: String) : Status
        data class Ready(val label: String, val source: String, val engine: Engine) : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()
    @Volatile private var job: Job? = null
    @Volatile private var jobKey: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // region settings (per profile)

    private fun prefs(c: Context) = c.getSharedPreferences(Profiles.prefsName(c, PREFS), Context.MODE_PRIVATE)

    fun settings(c: Context): Settings {
        val p = prefs(c)
        val device = java.util.Locale.getDefault().language
        return Settings(
            target = p.getString("target", null)?.takeIf { it in MangaTlPlan.TARGETS && it != "en" } ?: device.takeIf { it in MangaTlPlan.TARGETS && it != "en" } ?: "bn",
            mode = Mode.of(p.getString("mode", null)),
            engine = Engine.of(p.getString("engine", null)),
        )
    }

    fun save(c: Context, s: Settings) {
        prefs(c).edit().putString("target", s.target).putString("mode", s.mode.code).putString("engine", s.engine?.code).apply()
    }

    /** Engines usable now: ML Kit on phones (Google's terms exclude TVs), the AI when set up and allowed for subtitles. */
    fun engines(c: Context): List<Engine> = buildList {
        if (MangaTranslate.allowedOn(c)) add(Engine.DEVICE)
        val ai = AiSettings.get(c)
        if (ai.ready && ai.forSubtitles) add(Engine.AI)
    }

    fun engineFor(c: Context, s: Settings): Engine? = engines(c).let { e -> s.engine?.takeIf { it in e } ?: e.firstOrNull() }

    /** Targets offered (every one is an ML Kit language, so packs are shared with manga and novels). */
    val TARGETS: Map<String, String> get() = MangaTlPlan.TARGETS.filterKeys { it != "en" }

    // endregion

    // region pure helpers

    /** The English track among the stream's own subtitles (by language tag or label); translated ones never count. */
    internal fun englishTrack(subs: List<OnlineSubtitle>): OnlineSubtitle? = subs.filter { !it.auto }.firstOrNull { s ->
        val tag = OnlinePlayback.languageTag(s.language)
        tag == "en" || tag?.startsWith("en-") == true || s.language.contains("english", true)
    }

    /**
     * Release-name match for an automatic pick: shared words (group, source, resolution) count, hearing-impaired
     * tracks and season packs lose a little (both add lines or need the episode picked out).
     */
    internal fun score(hit: SubtitleHit, linkName: String?): Double {
        fun words(s: String) = s.lowercase().split(' ').flatMap { it.split('.', '-', '_', '[', ']', '(', ')') }.filter { it.length >= 2 }.toSet()
        val a = words(hit.release)
        val b = linkName?.let(::words).orEmpty()
        val overlap = if (b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / b.size
        return overlap * 10 + (if (hit.hearingImpaired) -1.5 else 0.0) + (if (hit.seasonPack) -0.5 else 0.0) + kotlin.math.ln(1.0 + hit.downloads) * 0.2
    }

    /** The unit to start from: the first that is still on screen at or after [positionMs]. */
    internal fun startUnit(cues: List<SubCues.Cue>, units: List<SubCues.Unit>, positionMs: Long): Int =
        units.indexOfFirst { u -> cues[u.parts.last().first].endMs >= positionMs }.takeIf { it >= 0 } ?: 0

    /** Batch order: from [start] to the end, then the beginning (so what is on screen comes first). */
    internal fun order(total: Int, start: Int, batch: Int = BATCH): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var i = start.coerceIn(0, total)
        while (i < total) { ranges += i until minOf(total, i + batch); i += batch }
        i = 0
        while (i < start.coerceIn(0, total)) { ranges += i until minOf(start, i + batch); i += batch }
        return ranges
    }

    /** The AI prompt for one batch: numbered lines in, the same count out. */
    internal fun aiRequest(lines: List<String>, target: String, title: String): AiRequest {
        val name = MangaTlPlan.TARGETS[target] ?: target
        val system = """You translate film and TV subtitles from English into $name.
Write natural, spoken $name as people really talk, keeping meaning, tone, jokes and politeness level.
Keep names, places and made-up terms as they are (write them in $name script only when that is the usual way).
Each input line is one subtitle; return exactly the same number of lines in the same order, never merge or split lines, never add notes.
Answer with one JSON object: {"t": ["line 1", "line 2", ...]}.
The show is: ${title.take(120)}."""
        val body = JSONObject().put("lines", JSONArray(lines)).toString()
        return AiRequest(model = "", system = system, messages = listOf(AiMessage(true, body)), json = true, maxTokens = 4096, temperature = 0.2)
    }

    internal fun parseAi(text: String, expected: Int): List<String>? {
        val o = AiHttp.firstJson(text) ?: return null
        val a = o.optJSONArray("t") ?: o.optJSONArray("lines") ?: return null
        val out = (0 until a.length()).map { a.optString(it).trim() }
        return out.takeIf { it.size == expected }
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(10).joinToString("") { "%02x".format(it) }

    // endregion

    // region the work

    /** Episode key of the session that is playing (null when nothing is). */
    fun keyOf(s: OnlineSession.State?): String? = s?.let { SubtitleTemp.keyFor(it.api, it.detail.url, it.episode?.data ?: it.detail.playData) }

    fun cancel() { job?.cancel(); job = null; jobKey = null; _status.value = Status.Idle }

    /** Starts (or restarts with new settings) the translation of the playing episode; [positionMs] picks the first batch. */
    fun start(c: Context, positionMs: Long, s: Settings = settings(c)) {
        val app = c.applicationContext
        val state = OnlineSession.state.value ?: run { _status.value = Status.Failed("Nothing is playing"); return }
        val key = keyOf(state) ?: return
        val engine = engineFor(app, s) ?: run {
            _status.value = Status.Failed(if (MangaTranslate.allowedOn(app)) "Translation needs a language pack or Yumeiro AI" else "On TV, subtitles are translated by Yumeiro AI. Add a free key in Settings → Yumeiro AI")
            return
        }
        job?.cancel()
        jobKey = key
        // Right away, so a Ready / Failed from the last run is never read as this run's result.
        _status.value = Status.Finding("Finding an English subtitle")
        SubtitleTemp.keepOnly(app, key)
        job = scope.launch {
            try {
                save(app, s) // B20 S8: preferences off the main thread
                run(app, state, key, positionMs, s, engine)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "translate: ${e.javaClass.simpleName}")
                _status.value = Status.Failed(friendly(e))
            }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is AiError -> e.message ?: "The AI couldn't translate this"
        is java.io.IOException -> "Couldn't download the subtitle. Check the connection"
        is IllegalStateException -> e.message ?: "Couldn't translate the subtitles"
        else -> "Couldn't translate the subtitles"
    }

    private suspend fun run(app: Context, state: OnlineSession.State, key: String, positionMs: Long, s: Settings, engine: Engine) {
        val dir = SubtitleTemp.dir(app, key, state.api, state.episode?.data ?: state.detail.playData)
        _status.value = Status.Finding("Finding an English subtitle")
        val (text, sourceLabel) = source(app, state, dir)
        val cues = SubCues.parse(text)
        if (cues.isEmpty()) throw IllegalStateException("The English subtitle has no lines Yumeiro can read")
        val units = SubCues.units(cues)
        val store = File(dir, "lines.${s.target}.${engine.code}.${sha(text)}.json")
        val done = HashMap<Int, String>()
        runCatching { JSONObject(store.readText()) }.getOrNull()?.let { o -> o.keys().forEach { k -> k.toIntOrNull()?.let { done[it] = o.optString(k) } } }
        val start = startUnit(cues, units, positionMs)
        _status.value = Status.Translating(done.size, units.size, engine, sourceLabel)
        if (engine == Engine.DEVICE) withContext(Dispatchers.IO) { MangaTranslate.prepare(app, MangaTranslate.Settings(true, "en", s.target, false)).getOrThrow() }
        var failures = 0
        for (range in order(units.size, start)) {
            val todo = range.filter { it !in done }
            if (todo.isEmpty()) continue
            val src = todo.map { units[it].text }
            val out = when (engine) {
                Engine.DEVICE -> MangaTranslate.texts(app, src, "en", s.target, wifiOnly = false)
                Engine.AI -> ai(app, src, s.target, state.detail.name)
            }
            todo.forEachIndexed { i, u -> out.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { done[u] = it } ?: failures++ }
            store.writeText(JSONObject().apply { done.forEach { (k, v) -> put(k.toString(), v) } }.toString())
            _status.value = Status.Translating(done.size, units.size, engine, sourceLabel)
        }
        if (done.isEmpty()) throw IllegalStateException("No line could be translated")
        if (failures > units.size / 4) Log.w(TAG, "$failures of ${units.size} lines kept in English")
        val translated = units.indices.map { done[it] }
        val name = MangaTlPlan.TARGETS[s.target] ?: s.target
        val latinOk = Romanize.supports(s.target)
        val mode = if (latinOk) s.mode else Mode.NATIVE
        val outCues = when (mode) {
            Mode.NATIVE -> SubCues.apply(cues, units, translated)
            Mode.LATIN -> SubCues.apply(cues, units, translated.map { t -> t?.let { Romanize.text(it, s.target) } })
            Mode.BOTH -> SubCues.apply(cues, units, translated) { native -> Romanize.text(native, s.target) }
        }
        val label = when (mode) { Mode.NATIVE -> "$name (translated)"; Mode.LATIN -> "$name (romanised)"; Mode.BOTH -> "$name + romanised" }
        val file = File(dir, "translated.${s.target}.${mode.code}.vtt")
        file.writeText(SubCues.toVtt(outCues), Charsets.UTF_8)
        // Still the same episode? Then the track goes in and is turned on.
        if (keyOf(OnlineSession.state.value) != key) { _status.value = Status.Idle; return }
        withContext(Dispatchers.Main) { OnlineSession.offerSubtitle(OnlineSubtitle(label, Uri.fromFile(file).toString(), emptyMap(), auto = true)) }
        _status.value = Status.Ready(label, sourceLabel, engine)
    }

    private suspend fun ai(app: Context, lines: List<String>, target: String, title: String): List<String> {
        // Two tries: models now and then return one line too few; the second try sends the same lines again.
        repeat(2) {
            val reply = AiSettings.chat(app, aiRequest(lines, target, title))
            parseAi(reply.text, lines.size)?.let { return it }
        }
        // Halves, so one odd line doesn't cost the whole batch.
        if (lines.size > 1) {
            val mid = lines.size / 2
            return ai(app, lines.take(mid), target, title) + ai(app, lines.drop(mid), target, title)
        }
        return listOf("")
    }

    /** The English source text and where it came from; kept in the episode folder for the next time. */
    private suspend fun source(app: Context, state: OnlineSession.State, dir: File): Pair<String, String> = withContext(Dispatchers.IO) {
        val saved = File(dir, "source.txt")
        val savedFrom = File(dir, "source.from")
        if (saved.isFile && saved.length() > 0) return@withContext saved.readText() to savedFrom.takeIf { it.isFile }?.readText().orEmpty().ifBlank { "English subtitle" }
        val client = NetworkClient.get(app)
        val own = englishTrack(state.subtitles)
        val (bytes, from) = if (own != null) {
            read(app, own.url, own.headers) to "${own.language} · from the source"
        } else {
            if (SubtitleHub.configured().isEmpty()) throw IllegalStateException("This source has no English subtitle. Link a free SubDL or SubSource key in Settings → Subtitles & intros to find one")
            _status.value = Status.Finding("Searching ${SubtitleHub.configured().joinToString(", ")}")
            val ep = state.episode
            val outcome = SubtitleHub.search(client, dev.asfakul.yumeiro.online.anime.AnimeIds.cleanTitle(state.detail.name), state.detail.imdbId, ep?.season, ep?.episode, "en",
                tmdbId = state.detail.tmdbId, year = state.detail.year)
            val hit = outcome.hits.filter { it.language == "en" || it.language.startsWith("en") }.maxByOrNull { score(it, state.link?.name) }
                ?: throw IllegalStateException("No English subtitle found for this episode")
            _status.value = Status.Finding("Downloading from ${hit.provider}")
            val url = SubtitleHub.resolve(app, client, hit, ep?.season, ep?.episode)
            read(app, url, emptyMap()) to "English · ${hit.provider}"
        }
        val text = SubtitleCharset.decode(bytes, "en")
        saved.writeText(text); savedFrom.writeText(from)
        text to from
    }

    private fun read(app: Context, url: String, headers: Map<String, String>): ByteArray {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") {
            val f = File(uri.path ?: throw IllegalStateException("Bad subtitle file"))
            // Only Yumeiro's own cache (hub downloads) may be read this way.
            if (!f.canonicalPath.startsWith(app.cacheDir.canonicalPath)) throw IllegalStateException("Bad subtitle file")
            if (f.length() > MAX_SOURCE) throw IllegalStateException("The subtitle file is too large")
            return f.readBytes()
        }
        if (uri.scheme != "https" && uri.scheme != "http") throw IllegalStateException("This subtitle can't be read")
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> runCatching { header(k, v) } } }.build()
        return NetworkClient.get(app).newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            SubtitleNet.readCapped(r.body.byteStream(), MAX_SOURCE)
        }
    }

    // endregion
}
