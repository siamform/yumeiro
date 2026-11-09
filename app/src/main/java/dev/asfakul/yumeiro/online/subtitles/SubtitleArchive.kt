/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.subtitles

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

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
                add(Regex("""s0*${s}[ ._-]*e0*${episode}(?!\d)"""))
                add(Regex("""(?<!\d)0*${s}x0*${episode}(?!\d)"""))
            }
        }
        if (patterns.any { it.containsMatchIn(n) }) return 3
        if (Regex("""(?:e|ep|episode|第)[ ._-]*0*${episode}(?!\d)""").containsMatchIn(n)) return 2
        // Anime style "Show - 07 [1080p].ass": the number standing alone.
        if (Regex("""(?<![\dx])0*${episode}(?![\dp])""").containsMatchIn(n.replace(Regex("""(19|20)\d\d"""), " "))) return 1
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

    /** UTF-8 (with or without BOM / UTF-16 BOM), else Windows-1252 so old SRTs still read. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { utf8.decode(ByteBuffer.wrap(bytes)).toString() } catch (_: CharacterCodingException) {
            String(bytes, runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1))
        }
        return text.removePrefix("\uFEFF")
    }

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
