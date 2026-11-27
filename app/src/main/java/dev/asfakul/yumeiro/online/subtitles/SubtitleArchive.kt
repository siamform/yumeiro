/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import dev.asfakul.yumeiro.text.RegexCache

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_SUBTITLEARCHIVE_1 = Regex("""(19|20)\d\d""")

/**
 * Subtitle sites hand out ZIP archives (one file, or a whole season). This picks the right
 * text subtitle out of one — matching the episode when there are several — with hard size
 * limits so a hostile archive can't fill memory. Pure Kotlin, unit-tested.
 */
object SubtitleArchive {
    /** One subtitle file taken out of an archive. */
    data class Entry(val name: String, val extension: String, val text: String)

    private val TEXT_FORMATS = listOf("srt", "ass", "ssa", "vtt")
    private const val MAX_ENTRY = 4L * 1024 * 1024
    private const val MAX_TOTAL = 24L * 1024 * 1024
    private const val MAX_ENTRIES = 400

    fun isZip(bytes: ByteArray): Boolean = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /**
     * The best subtitle in [bytes] (a ZIP, or a bare subtitle file) for [season]/[episode].
     * Returns null when the archive holds no usable text subtitle (e.g. only .sub/.idx or a RAR).
     */
    fun pick(bytes: ByteArray, season: Int? = null, episode: Int? = null, fallbackName: String = "subtitle.srt"): Entry? {
        if (!isZip(bytes)) {
            val text = decode(bytes)
            if (!looksLikeSubtitle(text)) return null
            val ext = fallbackName.substringAfterLast('.', "srt").lowercase().takeIf { it in TEXT_FORMATS } ?: sniffExtension(text)
            return Entry(fallbackName, ext, text)
        }
        val entries = mutableListOf<Entry>()
        var total = 0L
        var seen = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (++seen > MAX_ENTRIES) break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in TEXT_FORMATS || name.startsWith("._")) continue
                val data = readCapped(zip) ?: continue
                total += data.size
                if (total > MAX_TOTAL) break
                entries += Entry(name, ext, decode(data))
            }
        }
        if (entries.isEmpty()) return null
        return entries.maxWithOrNull(compareBy<Entry> { score(it.name, season, episode) }.thenBy { TEXT_FORMATS.size - TEXT_FORMATS.indexOf(it.extension) }.thenBy { it.text.length })
    }

    /** Higher is better: exact SxxEyy, then a bare episode number in the name. */
    internal fun score(name: String, season: Int?, episode: Int?): Int {
        if (episode == null) return 0
        val n = name.lowercase()
        val s = season
        val patterns = buildList {
            if (s != null) {
                add(RegexCache.get("""s0*${s}[ ._-]*e0*${episode}(?!\d)"""))
                add(RegexCache.get("""(?<!\d)0*${s}x0*${episode}(?!\d)"""))
            }
        }
        if (patterns.any { it.containsMatchIn(n) }) return 3
        if (RegexCache.get("""(?:e|ep|episode|第)[ ._-]*0*${episode}(?!\d)""").containsMatchIn(n)) return 2
        // Anime style "Show - 07 [1080p].ass": the number standing alone.
        if (RegexCache.get("""(?<![\dx])0*${episode}(?![\dp])""").containsMatchIn(n.replace(RX_SUBTITLEARCHIVE_1, " "))) return 1
        return -1
    }

    private fun readCapped(zip: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buf); if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_ENTRY) return null
        }
        return out.toByteArray()
    }

    /** UTF-8 / UTF-16 by BOM, else the old code page that fits the text (Group T, [SubtitleCharset]). */
    fun decode(bytes: ByteArray, hint: String? = null): String = dev.asfakul.yumeiro.subtitles.SubtitleCharset.decode(bytes, hint)

    private fun looksLikeSubtitle(text: String): Boolean {
        val head = text.take(4000)
        return head.contains("-->") || head.contains("[Script Info]", ignoreCase = true) || head.startsWith("WEBVTT")
    }

    private fun sniffExtension(text: String): String = when {
        text.startsWith("WEBVTT") -> "vtt"
        text.take(2000).contains("[Script Info]", ignoreCase = true) -> "ass"
        else -> "srt"
    }
}
