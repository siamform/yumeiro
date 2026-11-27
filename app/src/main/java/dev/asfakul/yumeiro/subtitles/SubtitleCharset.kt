/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * F3 (Group T, audits/AUDIT_T.md): reads subtitle text written in old code pages. Many SRT files are not UTF-8 but
 * Windows-1250…1258 or Thai 874 (and CJK files Shift_JIS, GBK, Big5, EUC-KR). Before, everything that was not UTF-8
 * was read as Windows-1252, so Arabic, Russian, Greek, Hebrew, Thai, Polish or Turkish subtitles showed as "Ïðèâåò".
 *
 * How it decides (no network, no library): BOMs first, then strict UTF-8. Otherwise every single-byte candidate is
 * decoded and scored by how much its non-ASCII text looks like real writing in the candidate's script: letters of the
 * script count for it; replacement characters, control characters, symbols stuck between letters, combining marks
 * with no letter before them and lower→UPPER flips inside a word count against it; runs of accented Latin letters
 * (what non-Latin text looks like when read as Latin) count against the Latin pages. The language [hint] (the
 * subtitle's language, else the app's language) breaks ties and is the only way CJK pages are tried, since those
 * cannot be told apart reliably by bytes alone.
 */
object SubtitleCharset {
    private enum class Script { LATIN, CYRILLIC, GREEK, HEBREW, ARABIC, THAI, CJK }

    private class Page(val names: List<String>, val script: Script)

    private val W1252 = Page(listOf("windows-1252"), Script.LATIN)
    private val W1250 = Page(listOf("windows-1250"), Script.LATIN)
    private val W1254 = Page(listOf("windows-1254"), Script.LATIN)
    private val W1257 = Page(listOf("windows-1257"), Script.LATIN)
    private val W1258 = Page(listOf("windows-1258"), Script.LATIN)
    private val W1251 = Page(listOf("windows-1251"), Script.CYRILLIC)
    private val W1253 = Page(listOf("windows-1253"), Script.GREEK)
    private val W1255 = Page(listOf("windows-1255"), Script.HEBREW)
    private val W1256 = Page(listOf("windows-1256"), Script.ARABIC)
    private val W874 = Page(listOf("x-windows-874", "windows-874", "TIS-620"), Script.THAI)
    private val SJIS = Page(listOf("windows-31j", "Shift_JIS"), Script.CJK)
    private val GBK = Page(listOf("GB18030", "GBK"), Script.CJK)
    private val BIG5 = Page(listOf("Big5"), Script.CJK)
    private val EUCKR = Page(listOf("EUC-KR", "x-windows-949"), Script.CJK)

    private const val SAMPLE = 64 * 1024
    private val NORDIC = setOf("is", "fo")

    private val SINGLE = listOf(W1252, W1250, W1254, W1257, W1258, W1251, W1253, W1255, W1256, W874)

    /** The page a language usually used before UTF-8 (ISO 639-1, also the old Java codes iw/in/ji). */
    private fun pageFor(lang: String?): Page? = when (lang?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')) {
        null, "" -> null
        "pl", "cs", "sk", "hu", "sl", "hr", "ro", "bs", "sq" -> W1250
        "ru", "uk", "be", "bg", "mk", "sr", "kk", "ky", "mn", "tg" -> W1251
        "el" -> W1253
        "tr", "az" -> W1254
        "he", "iw", "yi", "ji" -> W1255
        "ar", "fa", "ur", "ps", "ku" -> W1256
        "et", "lv", "lt" -> W1257
        "vi" -> W1258
        "th" -> W874
        "ja" -> SJIS
        "zh" -> GBK
        "ko" -> EUCKR
        else -> W1252
    }

    private fun charset(p: Page): Charset? = p.names.firstNotNullOfOrNull { n -> runCatching { Charset.forName(n) }.getOrNull() }

    private fun strict(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) { null }

    /** The decoded text and the charset name used (for logs and tests). */
    data class Result(val text: String, val charset: String)

    fun decode(bytes: ByteArray, hint: String? = null): String = detect(bytes, hint).text

    fun detect(bytes: ByteArray, hint: String? = null): Result {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE).removePrefix("\uFEFF"), "UTF-16LE")
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).removePrefix("\uFEFF"), "UTF-16BE")
        strict(bytes, Charsets.UTF_8)?.let { return Result(it.removePrefix("\uFEFF"), "UTF-8") }

        val lang = hint?.takeIf { it.isNotBlank() } ?: Locale.getDefault().language
        val hinted = pageFor(lang)
        // CJK only when the language says so: strict multi-byte decoding that succeeds is then trusted.
        if (hinted != null && hinted.script == Script.CJK) {
            val cs = charset(hinted)
            if (cs != null) strict(bytes, cs)?.let { t -> if (cjkShare(t) >= 0.6) return Result(t, cs.name()) }
        }
        var bestScore = Double.NEGATIVE_INFINITY
        // Score a sample (long files would be decoded ten times otherwise), decode the whole file once.
        val sampleLen = minOf(bytes.size, SAMPLE)
        var bestCs: Charset? = null
        for (p in SINGLE) {
            val cs = charset(p) ?: continue
            val text = String(bytes, 0, sampleLen, cs)
            var s = score(text, p.script)
            if (p === hinted) s += 0.08
            if (p === W1252 && lang !in NORDIC && share(text, ICELANDIC) > 0.15) s -= 0.2
            // Dotless ı and ş/ğ dominating the accented letters: Turkish (Czech ý or Romanian ţ read as 1254 stay below).
            if (p === W1254 && text.contains('ı') && share(text, TURKISH) > 0.35) s += 0.15
            if (p === W1252) s += 0.01
            if (s > bestScore) { bestScore = s; bestCs = cs }
        }
        val cs = bestCs ?: return Result(String(bytes, Charsets.ISO_8859_1), "ISO-8859-1")
        return Result(String(bytes, cs), cs.name())
    }

    private fun scriptOf(c: Char): Script? = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.LATIN -> Script.LATIN
        Character.UnicodeScript.CYRILLIC -> Script.CYRILLIC
        Character.UnicodeScript.GREEK -> Script.GREEK
        Character.UnicodeScript.HEBREW -> Script.HEBREW
        Character.UnicodeScript.ARABIC -> Script.ARABIC
        Character.UnicodeScript.THAI -> Script.THAI
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> Script.CJK
        else -> null
    }

    private fun cjkShare(t: String): Double {
        var n = 0; var hit = 0
        for (c in t) if (c.code >= 0x80) { n++; if (scriptOf(c) == Script.CJK || c.code in 0x3000..0x303F || c.code in 0xFF00..0xFFEF) hit++ }
        return if (n == 0) 0.0 else hit.toDouble() / n
    }

    private fun isMark(c: Char) = Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }

    /** Higher is more plausible. Pure. */
    private fun score(text: String, script: Script): Double {
        var nonAscii = 0
        var good = 0.0
        var bad = 0.0
        var latinRun = 0
        var inLongRun = 0
        var accentedLatin = 0
        var cyr = 0; var cyrVowel = 0; var cyrHard = 0
        var greek = 0; var greekTonos = 0; var greekRare = 0
        for (i in text.indices) {
            val c = text[i]
            if (c.code < 0x80) { latinRun = 0; continue }
            nonAscii++
            val prev = if (i > 0) text[i - 1] else ' '
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            val sc = scriptOf(c)
            when {
                c == '\uFFFD' || c.code in 0x80..0x9F || Character.getType(c) == Character.PRIVATE_USE.toInt() -> bad += 3
                isMark(c) -> if (prev.isLetter() && scriptOf(prev) == script) good += 1 else bad += 2
                c.isLetter() -> {
                    // A non-Latin letter glued to an ASCII letter ("Cуmo") is a wrong page.
                    if (sc != Script.LATIN && (asciiLetter(prev) || asciiLetter(next))) bad += 1
                    else if (sc == script) good += 1 else bad += 1
                    if (prev.isLowerCase() && c.isUpperCase()) bad += 2
                    if (sc == Script.LATIN) {
                        accentedLatin++
                        latinRun++
                        if (latinRun == 3) inLongRun += 3 else if (latinRun > 3) inLongRun++
                    } else latinRun = 0
                    if (sc == Script.CYRILLIC) {
                        cyr++
                        val l = c.lowercaseChar()
                        if (l in CYR_VOWELS) cyrVowel++
                        if (l == 'ъ') cyrHard++
                    }
                    if (sc == Script.GREEK) {
                        greek++
                        if (c in GREEK_TONOS) greekTonos++
                        if (c == 'ΐ' || c == 'ΰ') greekRare++
                    }
                }
                else -> {
                    // A symbol between two letters ("³", "¹", "¿" inside a word) is a wrong page.
                    if (prev.isLetter() && next.isLetter()) bad += 2 else good += 0.5
                    latinRun = 0
                }
            }
        }
        if (nonAscii == 0) return 0.0
        var s = (good - bad) / nonAscii
        // Non-Latin text read as Latin: long runs of accented letters.
        if (script == Script.LATIN && accentedLatin > 0 && inLongRun.toDouble() / accentedLatin > 0.4) s -= 0.6
        // Hebrew or Arabic bytes read as Cyrillic / Greek: letters, but not words (few vowels / no accents).
        if (script == Script.CYRILLIC && cyr >= 20 && (cyrVowel.toDouble() / cyr < 0.3 || cyrHard.toDouble() / cyr > 0.03)) s -= 0.5
        if (script == Script.GREEK && greek >= 20 && (greekTonos.toDouble() / greek < 0.03 || greekRare.toDouble() / greek > 0.02)) s -= 0.5
        return s
    }

    /** Share of [letters] among the accented Latin letters (Icelandic ý/þ/ð in a 1252 read means Turkish text). */
    private fun share(text: String, letters: String): Double {
        var acc = 0; var hit = 0
        for (c in text) if (c.code >= 0x80 && c.isLetter() && scriptOf(c) == Script.LATIN) { acc++; if (c in letters) hit++ }
        return if (acc == 0) 0.0 else hit.toDouble() / acc
    }

    private fun asciiLetter(c: Char) = c.code < 0x80 && c.isLetter()

    private const val CYR_VOWELS = "аеёиоуыэюяіїєў"
    private const val GREEK_TONOS = "άέήίόύώΆΈΉΊΌΎΏϊϋΐΰ"
    private const val ICELANDIC = "ýþðÝÞÐ"
    private const val TURKISH = "ışğİŞĞ"
}
