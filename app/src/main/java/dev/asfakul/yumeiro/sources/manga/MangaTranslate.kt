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

    /**
     * TL-2: [from] is a language code ("ja", "es"...) or [MangaTlPlan.AUTO] (the source's language, else read
     * from the page). The OCR script follows from it ([src]); the first version had only four scripts and
     * sent every Latin-script page to the translator as English, so Spanish or Indonesian manga came out wrong.
     */
    data class Settings(val on: Boolean, val from: String, val dst: String, val wifiOnly: Boolean) {
        val src: MangaTlPlan.Src get() = MangaTlPlan.script(from)
    }

    /** Why a page has no translations, for the reader to say so instead of showing nothing. */
    class SameLanguage(val lang: String) : Exception("already $lang")
    class TranslateFailed : Exception("translation failed")

    fun settings(c: Context): Settings {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            on = p.getBoolean("tl_on", false),
            from = p.getString("tl_src", null)?.takeIf { it in MangaTlPlan.FROM } ?: MangaTlPlan.AUTO,
            dst = p.getString("tl_dst", null)?.takeIf { it in MangaTlPlan.TARGETS } ?: MangaTlPlan.defaultTarget(java.util.Locale.getDefault().language),
            wifiOnly = p.getBoolean("tl_wifi_only", false),
        )
    }

    fun save(c: Context, s: Settings) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tl_on", s.on).putString("tl_src", s.from)
            .putString("tl_dst", s.dst).putBoolean("tl_wifi_only", s.wifiOnly).apply()
    }

    /**
     * TL-2: Google's ML Kit translation terms allow phones, tablets and computers only, not TVs. Yumeiro TV and a
     * phone build on a TV box therefore have no page translation.
     */
    fun allowedOn(c: Context): Boolean = !dev.asfakul.yumeiro.Edition.isTv &&
        !c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)

    /** The concrete settings for one chapter: Auto becomes the source's language when it is known. */
    fun resolve(s: Settings, providerLang: String?): Settings =
        if (s.from != MangaTlPlan.AUTO) s else MangaTlPlan.fromProvider(providerLang)?.let { s.copy(from = it) } ?: s

    /** Swappable for tests; the real one is created on first use. */
    @Volatile var engineFactory: (Context) -> MangaTlEngine = { MlKitTlEngine(it.applicationContext) }
    @Volatile private var engine: MangaTlEngine? = null
    private val lock = Mutex()
    private val mem = LruCache<String, List<MangaTlPlan.Bubble>>(48)
    @Volatile private var watching = false

    private fun engine(c: Context): MangaTlEngine = engine ?: synchronized(this) {
        engine ?: engineFactory(c).also { engine = it; watchMemory(c) }
    }

    /**
     * Downloads the language pack if needed. Blocking network; call off the main thread. With Auto and an unknown
     * source language nothing is fetched yet: the page decides ([page]).
     */
    suspend fun prepare(c: Context, s: Settings): Result<Unit> = withContext(Dispatchers.IO) {
        if (s.from == MangaTlPlan.AUTO || s.from == s.dst) return@withContext Result.success(Unit)
        runCatching { lock.withLock { engine(c).prepare(s.from, s.dst, s.wifiOnly) } }
            .onFailure { Log.w(TAG, "prepare: ${it.javaClass.simpleName}") }
    }

    fun ready(c: Context, s: Settings): Boolean =
        s.from == MangaTlPlan.AUTO || s.from == s.dst || runCatching { engine(c).ready(s.from, s.dst) }.getOrDefault(false)

    /**
     * NOVEL-1: plain-text translation for the novel reader, through the same engine and language packs as manga
     * pages (no second translation system). [from] must be a concrete code (not Auto). Blocking work off the main thread.
     */
    suspend fun texts(c: Context, texts: List<String>, from: String, dst: String, wifiOnly: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (from == dst || texts.isEmpty()) return@withContext texts
        lock.withLock {
            val e = engine(c)
            if (!e.ready(from, dst)) e.prepare(from, dst, wifiOnly)
            e.translate(texts, from, dst)
        }
    }

    /** TL-2: language packs on this phone (codes; English is built in), and removing one. Blocking. */
    suspend fun packs(c: Context): List<String> = withContext(Dispatchers.IO) { runCatching { engine(c).packs() }.getOrDefault(emptyList()) }
    suspend fun deletePack(c: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { lock.withLock { engine(c).deletePack(code) } }.isSuccess.also { if (it) mem.evictAll() }
    }

    /**
     * Translated bubbles for one page (all strips of a tall page, in page pixels). Memory → disk → work.
     * One page at a time, so a fast scroll never stacks OCR jobs. Throws [SameLanguage] when the page is already
     * in the target language and [TranslateFailed] when every bubble failed to translate (nothing is cached then).
     */
    suspend fun page(c: Context, pageId: String, strips: List<Bitmap>, s: Settings): List<MangaTlPlan.Bubble> {
        if (s.from == s.dst) throw SameLanguage(s.dst)
        val key = MangaTlPlan.key(pageId, s.from, s.dst)
        mem.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                mem.get(key)?.let { return@withLock it }
                readDisk(c, key)?.let { mem.put(key, it); return@withLock it }
                val e = engine(c)
                val totalH = strips.sumOf { it.height }
                val width = strips.maxOfOrNull { it.width } ?: 0
                fun read(script: MangaTlPlan.Src): List<MangaTlPlan.Box> {
                    val boxes = ArrayList<MangaTlPlan.Box>()
                    val seams = ArrayList<Int>()
                    var top = 0
                    for (b in strips) {
                        e.ocr(b, script).forEach { boxes += it.copy(t = it.t + top, b = it.b + top) }
                        top += b.height
                        if (top < totalH) seams += top
                    }
                    // TL-3: a seam that cuts through a line of letters leaves half-height glyphs on both sides, which
                    // OCR misreads ("MUCL" for "MUCHAS GRACIAS", device test D). Read a band across each seam too; a
                    // line found across the seam there replaces the half-reads.
                    var spliced: List<MangaTlPlan.Box> = boxes
                    for ((i, seam) in seams.withIndex()) {
                        val up = strips[i]; val down = strips[i + 1]
                        val above = minOf(MangaTlPlan.SEAM_BAND, up.height); val below = minOf(MangaTlPlan.SEAM_BAND, down.height)
                        val band = runCatching { Bitmap.createBitmap(maxOf(up.width, down.width), above + below, Bitmap.Config.ARGB_8888) }.getOrNull() ?: continue
                        try {
                            val cv = android.graphics.Canvas(band)
                            cv.drawColor(android.graphics.Color.WHITE)
                            cv.drawBitmap(up, android.graphics.Rect(0, up.height - above, up.width, up.height), android.graphics.Rect(0, 0, up.width, above), null)
                            cv.drawBitmap(down, android.graphics.Rect(0, 0, down.width, below), android.graphics.Rect(0, above, down.width, above + below), null)
                            val bandTop = seam - above
                            val found = runCatching { e.ocr(band, script) }.getOrDefault(emptyList()).map { it.copy(t = it.t + bandTop, b = it.b + bandTop) }
                            spliced = MangaTlPlan.spliceBand(spliced, found, bandTop, seam + below, seam)
                        } finally { band.recycle() }
                    }
                    // A webtoon is decoded in strips; a bubble cut by a seam is read as two halves. Join them back.
                    return MangaTlPlan.mergeSeams(spliced, seams, script).filter { MangaTlPlan.keep(it, width, totalH, script) }
                }
                var from = s.from
                var kept = read(MangaTlPlan.script(from))
                if (from == MangaTlPlan.AUTO) {
                    // Source language unknown: the Japanese reader also reads Latin and Han; the text says which.
                    from = MangaTlPlan.detect(kept.joinToString(" ") { it.text })
                    if (from == "ko" || MangaTlPlan.script(from) == MangaTlPlan.Src.LATIN) kept = read(MangaTlPlan.script(from))
                    if (from == s.dst) throw SameLanguage(from)
                    if (kept.isNotEmpty() && !e.ready(from, s.dst)) withContext(Dispatchers.IO) { e.prepare(from, s.dst, s.wifiOnly) }
                }
                val out = if (kept.isEmpty()) emptyList() else {
                    val tl = e.translate(kept.map { it.text }, from, s.dst)
                    val bubbles = kept.zip(tl).mapNotNull { (b, t) -> t.trim().takeIf { it.isNotEmpty() }?.let { MangaTlPlan.Bubble(b.l, b.t, b.r, b.b, b.text, it) } }
                    if (bubbles.isEmpty()) throw TranslateFailed()
                    bubbles
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
    /** [from] and [dst] are language codes (ML Kit BCP-47 tags). */
    fun ready(from: String, dst: String): Boolean
    fun prepare(from: String, dst: String, wifiOnly: Boolean)
    fun translate(texts: List<String>, from: String, dst: String): List<String>
    fun packs(): List<String> = emptyList()
    fun deletePack(code: String) {}
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

    private fun translator(from: String, dst: String) = synchronized(translators) {
        translators.getOrPut("$from>$dst") {
            val o = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(from) ?: error("unsupported $from"))
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

    override fun ready(from: String, dst: String): Boolean {
        val m = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        fun has(tag: String) = tag == "en" || runCatching {
            await(m.isModelDownloaded(com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(tag).build()), 10)
        }.getOrDefault(false)
        return has(from) && has(dst)
    }

    override fun prepare(from: String, dst: String, wifiOnly: Boolean) {
        val cond = com.google.mlkit.common.model.DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        await(translator(from, dst).downloadModelIfNeeded(cond), 300)
    }

    override fun translate(texts: List<String>, from: String, dst: String): List<String> {
        if (from == dst) return texts
        val t = translator(from, dst)
        return texts.map { s -> runCatching { await(t.translate(s), 20) }.onFailure { Log.w("MangaTranslate", "translate: ${it.javaClass.simpleName}") }.getOrDefault("") }
    }

    override fun packs(): List<String> = await(
        com.google.mlkit.common.model.RemoteModelManager.getInstance().getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java), 10,
    ).map { it.language }.filter { it != "en" }.sorted()

    override fun deletePack(code: String) {
        if (code == "en") return
        // Translators holding the pack are closed first, so the files can go.
        synchronized(translators) { translators.keys.filter { it.startsWith("$code>") || it.endsWith(">$code") }.forEach { k -> translators.remove(k)?.let { runCatching { it.close() } } } }
        await(com.google.mlkit.common.model.RemoteModelManager.getInstance().deleteDownloadedModel(com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(code).build()), 30)
    }

    override fun close() {
        synchronized(recognizers) { recognizers.values.forEach { runCatching { it.close() } }; recognizers.clear() }
        synchronized(translators) { translators.values.forEach { runCatching { it.close() } }; translators.clear() }
    }
}

/** Pure, unit-tested parts of F-11. */
object MangaTlPlan {
    /** Bump when OCR or joining changes, so old cached pages are redone. */
    const val ENGINE = "mlkit-2" // TL-2: language codes, joined seams
    const val OCR_MAX_EDGE = 2048

    enum class Src(val code: String, val label: String) {
        JA("ja", "Japanese"), ZH("zh", "Chinese"), KO("ko", "Korean"), LATIN("en", "Latin script");
        val cjk get() = this != LATIN
        companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: JA }
    }

    /** "Auto": the source's own language when it says, else read from the page ([detect]). */
    const val AUTO = "auto"

    /**
     * TL-2: languages a page can be read from. Japanese, Chinese and Korean have their own text readers; the
     * rest share ML Kit's Latin reader (accents included) and are translated from their own language.
     */
    val FROM = linkedMapOf(
        AUTO to "Auto", "ja" to "Japanese", "zh" to "Chinese", "ko" to "Korean", "en" to "English", "es" to "Spanish",
        "pt" to "Portuguese", "fr" to "French", "id" to "Indonesian", "vi" to "Vietnamese", "it" to "Italian", "de" to "German", "tr" to "Turkish",
    )

    fun script(from: String): Src = when (from) { "ja", AUTO -> Src.JA; "zh" -> Src.ZH; "ko" -> Src.KO; else -> Src.LATIN }

    /** A source's language tag ("ja", "pt-BR", "es-419", "zh-Hans") → a [FROM] code, or null ("all", "multi", unknown). */
    fun fromProvider(lang: String?): String? {
        val base = lang?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
        return base.takeIf { it != AUTO && it in FROM }
    }

    /** Which language a page's text is in, by script: kana → Japanese, Hangul → Korean, Han only → Chinese, else English. */
    fun detect(text: String): String {
        var kana = 0; var hangul = 0; var han = 0; var latin = 0
        for (ch in text) {
            when (Character.UnicodeBlock.of(ch)) {
                Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA, Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS -> kana++
                Character.UnicodeBlock.HANGUL_SYLLABLES, Character.UnicodeBlock.HANGUL_JAMO, Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> hangul++
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS, Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A -> han++
                else -> if (ch.isLetter()) latin++
            }
        }
        return when {
            kana > 0 && kana * 5 >= han -> "ja"
            hangul > 0 && hangul >= kana -> "ko"
            han > 0 && han >= latin -> if (kana > 0) "ja" else "zh"
            else -> "en"
        }
    }

    /**
     * TL-2: a tall page is read strip by strip, so a bubble crossing a seam came back as two half-sentences,
     * each translated on its own. Boxes touching the same seam from both sides (within [SEAM_PX]) and
     * overlapping by half the narrower width are joined into one, text in reading order.
     */
    const val SEAM_PX = 8

    /** TL-3: pixels taken from each side of a seam for the extra band read (a few lines of bubble text). */
    const val SEAM_BAND = 160

    /**
     * TL-3: [found] = boxes read on a band [bandTop, bandBottom) across [seam]. A box that crosses the seam and sits
     * inside the band (not cut by its edges) is a whole line; the strip boxes it overlaps were half-reads and go.
     */
    fun spliceBand(boxes: List<Box>, found: List<Box>, bandTop: Int, bandBottom: Int, seam: Int): List<Box> {
        val whole = found.filter { it.t < seam && it.b > seam && it.t > bandTop + 1 && it.b < bandBottom - 1 && it.text.isNotBlank() }
        if (whole.isEmpty()) return boxes
        fun overlaps(a: Box, b: Box) = a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b
        return boxes.filter { s -> whole.none { overlaps(it, s) } } + whole
    }
    fun mergeSeams(boxes: List<Box>, seams: List<Int>, src: Src): List<Box> {
        if (seams.isEmpty() || boxes.size < 2) return boxes
        val out = boxes.toMutableList()
        for (seam in seams) {
            var i = 0
            while (i < out.size) {
                val a = out[i]
                if (a.b < seam - SEAM_PX || a.b > seam + SEAM_PX || a.t >= seam) { i++; continue }
                val j = out.indices.firstOrNull { k ->
                    val b = out[k]
                    k != i && b.t >= seam - SEAM_PX && b.t <= seam + SEAM_PX && b.b > seam &&
                        (minOf(a.r, b.r) - maxOf(a.l, b.l)) * 2 >= minOf(a.r - a.l, b.r - b.l)
                }
                if (j == null) { i++; continue }
                val b = out[j]
                out[i] = Box(minOf(a.l, b.l), a.t, maxOf(a.r, b.r), b.b, join(listOf(a.text, b.text), src))
                out.removeAt(j)
                if (j < i) i--
            }
        }
        return out
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

    fun key(pageId: String, from: String, dst: String): String =
        MessageDigest.getInstance("SHA-256").digest("$ENGINE|$from|$dst|$pageId".toByteArray()).take(16).joinToString("") { "%02x".format(it) }

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
