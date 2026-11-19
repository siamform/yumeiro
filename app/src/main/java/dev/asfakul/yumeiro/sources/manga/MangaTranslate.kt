/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * F-11 (ADR-044): manga page translation inside Yumeiro's own reader. Text is found and read on the
 * phone (ML Kit text recognition, models bundled), then translated on the phone (ML Kit translation,
 * a language pack downloaded once per language). Nothing about the page leaves the device except the
 * one-time language-pack download from Google. No overlay permission, no screen capture, no cloud key.
 */
object MangaTranslate {
    private const val TAG = "MangaTranslate"
    private const val PREFS = "manga_reader"
    private const val DIR = "manga-tl"
    /** Disk cache cap; oldest pages go first. */
    const val DISK_MAX_BYTES = 8L * 1024 * 1024

    data class Settings(val on: Boolean, val src: MangaTlPlan.Src, val dst: String, val wifiOnly: Boolean)

    fun settings(c: Context): Settings {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            on = p.getBoolean("tl_on", false),
            src = MangaTlPlan.Src.of(p.getString("tl_src", null)),
            dst = p.getString("tl_dst", null)?.takeIf { it in MangaTlPlan.TARGETS } ?: MangaTlPlan.defaultTarget(java.util.Locale.getDefault().language),
            wifiOnly = p.getBoolean("tl_wifi_only", false),
        )
    }

    fun save(c: Context, s: Settings) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tl_on", s.on).putString("tl_src", s.src.code)
            .putString("tl_dst", s.dst).putBoolean("tl_wifi_only", s.wifiOnly).apply()
    }

    /** Swappable for tests; the real one is created on first use. */
    @Volatile var engineFactory: (Context) -> MangaTlEngine = { MlKitTlEngine(it.applicationContext) }
    @Volatile private var engine: MangaTlEngine? = null
    private val lock = Mutex()
    private val mem = LruCache<String, List<MangaTlPlan.Bubble>>(48)
    @Volatile private var watching = false

    private fun engine(c: Context): MangaTlEngine = engine ?: synchronized(this) {
        engine ?: engineFactory(c).also { engine = it; watchMemory(c) }
    }

    /** Downloads the language pack if needed. Blocking network; call off the main thread. */
    suspend fun prepare(c: Context, s: Settings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { lock.withLock { engine(c).prepare(s.src, s.dst, s.wifiOnly) } }
            .onFailure { Log.w(TAG, "prepare: ${it.javaClass.simpleName}") }
    }

    fun ready(c: Context, s: Settings): Boolean = runCatching { engine(c).ready(s.src, s.dst) }.getOrDefault(false)

    /**
     * Translated bubbles for one page (all strips of a tall page, in page pixels). Memory → disk → work.
     * One page at a time, so a fast scroll never stacks OCR jobs.
     */
    suspend fun page(c: Context, pageId: String, strips: List<Bitmap>, s: Settings): List<MangaTlPlan.Bubble> {
        val key = MangaTlPlan.key(pageId, s.src, s.dst)
        mem.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                mem.get(key)?.let { return@withLock it }
                readDisk(c, key)?.let { mem.put(key, it); return@withLock it }
                val e = engine(c)
                val boxes = ArrayList<MangaTlPlan.Box>()
                var top = 0
                val totalH = strips.sumOf { it.height }
                val width = strips.maxOfOrNull { it.width } ?: 0
                for (b in strips) {
                    e.ocr(b, s.src).forEach { boxes += it.copy(t = it.t + top, b = it.b + top) }
                    top += b.height
                }
                val kept = boxes.filter { MangaTlPlan.keep(it, width, totalH, s.src) }
                val out = if (kept.isEmpty()) emptyList() else {
                    val tl = e.translate(kept.map { it.text }, s.src, s.dst)
                    kept.zip(tl).mapNotNull { (b, t) -> t.trim().takeIf { it.isNotEmpty() }?.let { MangaTlPlan.Bubble(b.l, b.t, b.r, b.b, b.text, it) } }
                }
                mem.put(key, out)
                writeDisk(c, key, out)
                out
            }
        }
    }

    /** Reader closed or the phone is short of memory: models leave RAM (cached results stay). */
    fun release() {
        val e = synchronized(this) { engine.also { engine = null } }
        runCatching { e?.close() }
    }

    fun clear(c: Context) {
        mem.evictAll()
        File(c.cacheDir, DIR).deleteRecursively()
    }

    private fun watchMemory(c: Context) {
        if (watching) return
        watching = true
        c.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) release() }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java") override fun onLowMemory() = release()
        })
    }

    private fun readDisk(c: Context, key: String): List<MangaTlPlan.Bubble>? = runCatching {
        val f = File(File(c.cacheDir, DIR), "$key.json")
        if (!f.isFile) null else MangaTlPlan.decode(f.readText()).also { f.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    private fun writeDisk(c: Context, key: String, list: List<MangaTlPlan.Bubble>) = runCatching {
        val d = File(c.cacheDir, DIR).apply { mkdirs() }
        File(d, "$key.json").writeText(MangaTlPlan.encode(list))
        val files = d.listFiles().orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= DISK_MAX_BYTES) break; total -= f.length(); f.delete() }
    }
}

/** What the reader needs from an OCR + translation engine (ML Kit in the app, a fake in unit tests). */
interface MangaTlEngine {
    fun ocr(bitmap: Bitmap, src: MangaTlPlan.Src): List<MangaTlPlan.Box>
    fun ready(src: MangaTlPlan.Src, dst: String): Boolean
    fun prepare(src: MangaTlPlan.Src, dst: String, wifiOnly: Boolean)
    fun translate(texts: List<String>, src: MangaTlPlan.Src, dst: String): List<String>
    fun close()
}

/** ML Kit on-device engine. Recognisers and translators are created on first use and closed on [close]. */
class MlKitTlEngine(private val app: Context) : MangaTlEngine {
    private val recognizers = HashMap<MangaTlPlan.Src, com.google.mlkit.vision.text.TextRecognizer>()
    private val translators = HashMap<String, com.google.mlkit.nl.translate.Translator>()

    init {
        // The ML Kit start-up provider is removed from the manifest (no cost at app start); initialise here.
        runCatching { com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(app) }
    }

    private fun <T> await(t: Task<T>, s: Long): T = Tasks.await(t, s, TimeUnit.SECONDS)

    private fun recognizer(src: MangaTlPlan.Src) = synchronized(recognizers) {
        recognizers.getOrPut(src) {
            val o = when (src) {
                MangaTlPlan.Src.JA -> com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions.Builder().build()
                MangaTlPlan.Src.ZH -> com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
                MangaTlPlan.Src.KO -> com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build()
                MangaTlPlan.Src.LATIN -> com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            }
            com.google.mlkit.vision.text.TextRecognition.getClient(o)
        }
    }

    private fun translator(src: MangaTlPlan.Src, dst: String) = synchronized(translators) {
        translators.getOrPut("${src.code}>$dst") {
            val o = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(src.code) ?: error("unsupported ${src.code}"))
                .setTargetLanguage(com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(dst) ?: error("unsupported $dst"))
                .build()
            com.google.mlkit.nl.translate.Translation.getClient(o)
        }
    }

    override fun ocr(bitmap: Bitmap, src: MangaTlPlan.Src): List<MangaTlPlan.Box> {
        // Big pages are read at most 2048 px on the long side (memory + speed), boxes scaled back.
        val k = MangaTlPlan.ocrScale(bitmap.width, bitmap.height)
        val img = if (k < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true) else bitmap
        try {
            val text = await(recognizer(src).process(com.google.mlkit.vision.common.InputImage.fromBitmap(img, 0)), 30)
            return text.textBlocks.mapNotNull { b ->
                val r = b.boundingBox ?: return@mapNotNull null
                val joined = MangaTlPlan.join(b.lines.map { it.text }, src)
                MangaTlPlan.Box((r.left / k).toInt(), (r.top / k).toInt(), (r.right / k).toInt(), (r.bottom / k).toInt(), joined)
            }
        } finally { if (img !== bitmap) img.recycle() }
    }

    override fun ready(src: MangaTlPlan.Src, dst: String): Boolean {
        val m = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        fun has(tag: String) = tag == "en" || runCatching {
            await(m.isModelDownloaded(com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(tag).build()), 10)
        }.getOrDefault(false)
        return has(src.code) && has(dst)
    }

    override fun prepare(src: MangaTlPlan.Src, dst: String, wifiOnly: Boolean) {
        val cond = com.google.mlkit.common.model.DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        await(translator(src, dst).downloadModelIfNeeded(cond), 300)
    }

    override fun translate(texts: List<String>, src: MangaTlPlan.Src, dst: String): List<String> {
        if (src.code == dst) return texts
        val t = translator(src, dst)
        return texts.map { s -> runCatching { await(t.translate(s), 20) }.getOrDefault("") }
    }

    override fun close() {
        synchronized(recognizers) { recognizers.values.forEach { runCatching { it.close() } }; recognizers.clear() }
        synchronized(translators) { translators.values.forEach { runCatching { it.close() } }; translators.clear() }
    }
}

/** Pure, unit-tested parts of F-11. */
object MangaTlPlan {
    /** Bump when OCR or joining changes, so old cached pages are redone. */
    const val ENGINE = "mlkit-1"
    const val OCR_MAX_EDGE = 2048

    enum class Src(val code: String, val label: String) {
        JA("ja", "Japanese"), ZH("zh", "Chinese"), KO("ko", "Korean"), LATIN("en", "English");
        val cjk get() = this != LATIN
        companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: JA }
    }

    /** Target languages offered (all supported by ML Kit translation). */
    val TARGETS = linkedMapOf(
        "en" to "English", "bn" to "Bengali", "hi" to "Hindi", "id" to "Indonesian", "es" to "Spanish", "pt" to "Portuguese",
        "fr" to "French", "de" to "German", "it" to "Italian", "ru" to "Russian", "ar" to "Arabic", "tr" to "Turkish",
        "vi" to "Vietnamese", "th" to "Thai",
    )

    fun defaultTarget(deviceLang: String): String = deviceLang.takeIf { it in TARGETS } ?: "en"

    data class Box(val l: Int, val t: Int, val r: Int, val b: Int, val text: String)
    data class Bubble(val l: Int, val t: Int, val r: Int, val b: Int, val source: String, val text: String)

    /** One block's lines → one sentence. CJK lines join without spaces; Latin joins with spaces, mending "end-\nings". */
    fun join(lines: List<String>, src: Src): String {
        val parts = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (src.cjk) return parts.joinToString("")
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.isEmpty()) sb.append(p)
            else if (sb.endsWith("-") && p.first().isLowerCase()) { sb.setLength(sb.length - 1); sb.append(p) }
            else sb.append(' ').append(p)
        }
        return sb.toString()
    }

    private val NOISE = Regex("^[\\p{P}\\p{S}\\d\\s〜~ー…・]+$")

    /**
     * Drops what is not dialogue: page numbers and lone punctuation, specks under 0.05 % of the page,
     * and single Latin letters (sound effects and art read as text).
     */
    fun keep(b: Box, pageW: Int, pageH: Int, src: Src): Boolean {
        val t = b.text.trim()
        if (t.isEmpty() || NOISE.matches(t)) return false
        if (!src.cjk && t.count { it.isLetter() } < 2) return false
        val area = (b.r - b.l).toLong().coerceAtLeast(0) * (b.b - b.t).coerceAtLeast(0)
        return pageW <= 0 || pageH <= 0 || area * 2000 >= pageW.toLong() * pageH
    }

    /**
     * Where a translation is drawn, in page pixels. Japanese and Chinese bubbles are tall and narrow
     * (vertical text); English in that shape would be unreadably small, so a tall box is widened around
     * its centre (up to 45 % of the page) and kept on the page.
     */
    fun layout(b: Bubble, pageW: Int): IntArray {
        val w = b.r - b.l; val h = b.b - b.t
        if (w <= 0 || h <= 0 || pageW <= 0 || h <= w * 1.4f) return intArrayOf(b.l, b.t, b.r, b.b)
        val nw = minOf(maxOf(w.toFloat(), h * 0.75f), pageW * 0.45f).toInt().coerceAtLeast(w)
        val cx = (b.l + b.r) / 2
        var l = cx - nw / 2
        l = l.coerceIn(0, (pageW - nw).coerceAtLeast(0))
        return intArrayOf(l, b.t, l + nw, b.b)
    }

    fun ocrScale(w: Int, h: Int): Float { val m = maxOf(w, h); return if (m <= OCR_MAX_EDGE) 1f else OCR_MAX_EDGE.toFloat() / m }

    fun key(pageId: String, src: Src, dst: String): String =
        MessageDigest.getInstance("SHA-256").digest("$ENGINE|${src.code}|$dst|$pageId".toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    /**
     * Where page pixels land on screen. Fit (paged mode) letterboxes in the container; fill-width
     * (webtoon) scales to the width from the top. Returns scale, x offset, y offset.
     */
    fun fit(cw: Float, ch: Float, w: Int, h: Int, fillWidth: Boolean): Triple<Float, Float, Float> {
        if (w <= 0 || h <= 0 || cw <= 0f) return Triple(0f, 0f, 0f)
        if (fillWidth) return Triple(cw / w, 0f, 0f)
        val s = minOf(cw / w, ch / h)
        return Triple(s, (cw - w * s) / 2f, (ch - h * s) / 2f)
    }

    fun encode(list: List<Bubble>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("l", it.l).put("t", it.t).put("r", it.r).put("b", it.b).put("s", it.source).put("x", it.text)) }
    }.toString()

    fun decode(raw: String): List<Bubble> {
        val a = JSONArray(raw)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Bubble(o.getInt("l"), o.getInt("t"), o.getInt("r"), o.getInt("b"), o.optString("s"), o.getString("x"))
        }
    }
}
