/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Round 9: what a source says about its audio — Sub (original audio) or Dub, and in which
 * language. Extensions only label links with free text ("HD-1 [ENG DUB]", "Kiwi · hin",
 * "Vidstream (Latino)", "Dual audio 1080p"), so this reads that text the way a person would.
 *
 * Rules, in order:
 *  1. An explicit dub word wins ("dub", "dubbed", "dual/multi audio", "<lang> dub", "dublado",
 *     "doblado", "doublage", "latino", "castellano").
 *  2. An explicit sub word ("sub", "softsub", "hardsub", "subbed", "raw", "vostfr", "legendado").
 *  3. A spoken-language name or code ("English", "eng", "[EN]", "Hindi", "हिन्दी"…): original-audio
 *     languages (Japanese/Korean/Chinese for anime-style sources) mean Sub, anything else Dub.
 *  4. Otherwise fall back to the version ("Sub" / "Dub"), else unknown.
 */
object AudioLanguages {
    enum class Kind(val label: String) { SUB("Sub"), DUB("Dub") }

    /** [language] is an ISO 639-1 code ("en", "hi", "es") or "multi" for dual/multi audio. */
    data class Audio(val kind: Kind?, val language: String?)

    const val MULTI = "multi"

    // Languages people actually see on streaming links. code → spellings (names, native names, 3-letter codes).
    private val LANGS: Map<String, List<String>> = linkedMapOf(
        "en" to listOf("english", "eng", "inglés", "ingles", "anglais"),
        "ja" to listOf("japanese", "jpn", "jap", "jp", "日本語", "japonés", "japones"),
        "ko" to listOf("korean", "kor", "한국어"),
        "zh" to listOf("chinese", "chi", "zho", "mandarin", "cantonese", "中文", "国语", "粤语"),
        "hi" to listOf("hindi", "hin", "हिन्दी", "हिंदी"),
        "ta" to listOf("tamil", "tam", "தமிழ்"),
        "te" to listOf("telugu", "tel", "తెలుగు"),
        "ml" to listOf("malayalam", "mal", "മലയാളം"),
        "kn" to listOf("kannada", "kan", "ಕನ್ನಡ"),
        "bn" to listOf("bengali", "bangla", "ben", "বাংলা"),
        "mr" to listOf("marathi", "mar"),
        "ur" to listOf("urdu", "urd"),
        "es" to listOf("spanish", "spa", "español", "espanol", "castellano", "latino", "latam", "esp"),
        "pt" to listOf("portuguese", "por", "português", "portugues", "brazilian", "ptbr", "pt-br", "dublado"),
        "fr" to listOf("french", "fre", "fra", "français", "francais", "vf"),
        "de" to listOf("german", "ger", "deu", "deutsch"),
        "it" to listOf("italian", "ita", "italiano"),
        "ru" to listOf("russian", "rus", "русский"),
        "ar" to listOf("arabic", "ara", "العربية", "عربي"),
        "tr" to listOf("turkish", "tur", "türkçe", "turkce"),
        "id" to listOf("indonesian", "indonesia", "ind", "bahasa"),
        "ms" to listOf("malay", "melayu", "msa"),
        "th" to listOf("thai", "tha", "ไทย"),
        "vi" to listOf("vietnamese", "vie", "tiếng việt", "vietsub"),
        "tl" to listOf("tagalog", "filipino", "fil"),
        "pl" to listOf("polish", "pol", "polski"),
        "uk" to listOf("ukrainian", "ukr"),
        "nl" to listOf("dutch", "nld", "dut"),
        "fa" to listOf("persian", "farsi", "per", "fas"),
    )

    /** 2-letter codes, only trusted when they stand alone in brackets or after a separator ("[EN]", "- es"). */
    private val CODE2 = LANGS.keys
    private val ORIGINAL_AUDIO = setOf("ja", "ko", "zh")

    private fun word(p: String) = Regex("(?<![\\p{L}\\p{N}])(?:$p)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val dubWord = word("dub|dubs|dubbed|dubbing|dublado|dublada|doblado|doblaje|doublage|dual[ -]?audio|multi[ -]?audio|dual|latino|castellano")
    private val multiWord = word("dual[ -]?audio|multi[ -]?audio|dual|multi")
    private val subWord = word("sub|subs|subbed|subtitled|softsub|hardsub|soft[ -]?sub|hard[ -]?sub|raw|vostfr|vost|legendado|vietsub|sub[ -]?esp|sub[ -]?eng")
    private val spellings: List<Pair<String, Regex>> = LANGS.flatMap { (code, names) -> names.map { code to word(Regex.escape(it)) } }
    private val bracketCode = Regex("[\\[(|·\\-_/]\\s*([A-Za-z]{2})\\s*(?=[\\])|·\\-_/]|$)")
    // Round 13: compiled once. These used to be rebuilt on every call, and sorting 100+ links
    // called classify() thousands of times on the main thread (a cause of player ANRs).
    private val dubbedBefore = Regex("(?i)([\\p{L}]+)[ -]?(?:dub|dubbed|audio)\\b")
    private val dubbedAfter = Regex("(?i)\\b(?:dub|audio)[ -:]?([\\p{L}]+)")
    private val subsBefore = Regex("(?i)([\\p{L}]+)[ -]?(?:sub|subs|subbed|subtitles?)\\b")
    private val subsAfter = Regex("(?i)\\b(?:sub|subs|subtitles?)[ -:]?([\\p{L}]+)")
    private val wordToCode: Map<String, String> = HashMap<String, String>().apply { LANGS.forEach { (code, names) -> names.forEach { putIfAbsent(it, code) } } }
    private val classifyCache = object : LinkedHashMap<String, Audio>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Audio>?) = size > 2048
    }

    /** Spoken language mentioned in [text], or null. "Dual/Multi audio" → [MULTI]. */
    fun languageOf(text: String): String? {
        if (text.isBlank()) return null
        if (multiWord.containsMatchIn(text)) return MULTI
        // Language right before/after "dub"/"audio" wins over subtitle languages ("Japanese audio, English subs").
        val dubbed = dubbedBefore.findAll(text).mapNotNull { m -> codeOfWord(m.groupValues[1]) }.firstOrNull()
            ?: dubbedAfter.findAll(text).mapNotNull { m -> codeOfWord(m.groupValues[1]) }.firstOrNull()
        if (dubbed != null) return dubbed
        // Ignore languages that only describe subtitles ("Eng sub", "English subtitles").
        val withoutSubs = text.replace(subsBefore, " ").replace(subsAfter, " ")
        spellings.firstOrNull { (_, r) -> r.containsMatchIn(withoutSubs) }?.let { return it.first }
        bracketCode.findAll(" $withoutSubs").map { it.groupValues[1].lowercase() }.firstOrNull { it in CODE2 }?.let { return it }
        return null
    }

    private fun codeOfWord(w: String): String? {
        return wordToCode[w.lowercase()]
    }

    /** Sub / Dub and language for one link. [version] is the extension's version label. */
    fun classify(linkText: String, version: String = ""): Audio {
        val key = version + "\u0000" + linkText
        synchronized(classifyCache) { classifyCache[key] }?.let { return it }
        return classifyUncached(linkText, version).also { a -> synchronized(classifyCache) { classifyCache[key] = a } }
    }

    private fun classifyUncached(linkText: String, version: String): Audio {
        val lang = languageOf(linkText)
        val explicitDub = dubWord.containsMatchIn(linkText)
        val explicitSub = !explicitDub && subWord.containsMatchIn(linkText)
        val kind = when {
            explicitDub -> Kind.DUB
            explicitSub -> Kind.SUB
            lang == MULTI -> Kind.DUB
            lang != null && lang !in ORIGINAL_AUDIO -> versionKind(version) ?: Kind.DUB
            lang != null -> Kind.SUB
            else -> versionKind(version)
        }
        // A "Sub" link whose only language is its subtitles ("Eng sub") has original audio.
        val language = when {
            kind == Kind.SUB && lang != null && lang !in ORIGINAL_AUDIO && !explicitDub -> null
            else -> lang ?: if (kind == Kind.DUB) languageOf(version) else null
        }
        return Audio(kind, language)
    }

    private fun versionKind(version: String): Kind? = when {
        version.isBlank() -> null
        dubWord.containsMatchIn(version) -> Kind.DUB
        subWord.containsMatchIn(version) -> Kind.SUB
        else -> null
    }

    /** Filter key for one link: the audio language, "orig" for Sub, "" when unknown. */
    fun filterKey(a: Audio): String = when {
        a.kind == Kind.SUB -> ORIG
        a.language != null -> a.language
        a.kind == Kind.DUB -> DUB_OTHER
        else -> ""
    }

    /** 2-letter code for a track language ("jpn", "ja-JP", "en_US" → "ja", "ja", "en"). */
    fun normalizeTrackLanguage(lang: String?): String? {
        val l = lang?.trim()?.lowercase()?.replace('_', '-')?.substringBefore('-')?.takeIf { it.isNotBlank() && it != "und" } ?: return null
        if (l.length == 2) return l
        return iso3[l] ?: LANGS.entries.firstOrNull { (_, names) -> l in names }?.key ?: l
    }

    private val iso3: Map<String, String> by lazy {
        HashMap<String, String>().apply {
            java.util.Locale.getISOLanguages().forEach { c -> runCatching { java.util.Locale(c).isO3Language }.getOrNull()?.let { put(it, c) } }
            put("chi", "zh"); put("fre", "fr"); put("ger", "de"); put("dut", "nl"); put("per", "fa"); put("cze", "cs"); put("gre", "el")
        }
    }

    /**
     * Round 13: which of the playing video's audio tracks fits filter [key] ("orig", "dub", or a
     * language code). [languages] are the tracks' language tags in order. Returns the index or -1.
     */
    fun trackFor(key: String, languages: List<String?>): Int {
        val codes = languages.map { normalizeTrackLanguage(it) }
        return when (key) {
            ORIG -> codes.indexOfFirst { it in ORIGINAL_AUDIO }
            DUB_OTHER -> codes.indexOfFirst { it != null && it !in ORIGINAL_AUDIO }
            MULTI, "" -> -1
            else -> codes.indexOfFirst { it == key }
        }
    }

    const val ORIG = "orig"
    const val DUB_OTHER = "dub"

    /** Human label for a filter key. */
    fun keyLabel(key: String): String = when (key) {
        ORIG -> "Original · Sub"
        DUB_OTHER -> "Dub"
        MULTI -> "Multi audio"
        "" -> "Unlabelled"
        else -> displayName(key)
    }

    fun displayName(code: String): String = when (code) {
        MULTI -> "Multi audio"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.uppercase() }
    }

    // region preference

    private const val PREFS = "watch_prefs"
    private const val KEY = "preferred_audio"
    private val _preferred = MutableStateFlow<String?>(null)
    /** The user's preferred audio filter key (null = no preference). Moves matching links first. */
    val preferred: StateFlow<String?> = _preferred.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _preferred.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    fun setPreferred(context: Context, key: String?) {
        _preferred.value = key
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (key == null) remove(KEY) else putString(KEY, key) }.apply()
    }

    // endregion
}
