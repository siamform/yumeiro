/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import java.util.Locale

/**
 * Round 12: what a source is, in a few words, read from what the extension says about it:
 * quality ("1080p", "4K"), release ("WEB-DL"), format ("HLS", "MKV"), file size ("1.4 GB") and
 * audio ("English", "5.1", "Atmos"). Nothing is downloaded to find out. Pure.
 */
object SourceFacts {
    data class Facts(val quality: String?, val release: String?, val format: String?, val size: String?, val audio: List<String>, val low: Boolean) {
        /** Short line under the source name: "1080p · WEB-DL · HLS · 1.4 GB · English 5.1". */
        val line: String get() = (listOfNotNull(quality, release, format, size) + audio.joinToString(" ").ifBlank { null }.let(::listOfNotNull)).joinToString(" · ")
    }

    private val sizeRx = Regex("""(?<![\d.])(\d{1,4}(?:[.,]\d{1,2})?)\s?(GB|GiB|MB|MiB|TB)\b""", RegexOption.IGNORE_CASE)
    private val channelsRx = Regex("""(?<![\d.])([257])[ .]([01])(?![\d])""")
    private val codecRules = listOf(
        "Atmos" to Regex("""(?i)\batmos\b"""),
        "TrueHD" to Regex("""(?i)\btrue[ -]?hd\b"""),
        "DTS" to Regex("""(?i)\bdts(?:[ -]?(?:hd|ma|x))?\b"""),
        "Dolby" to Regex("""(?i)\b(?:ddp|dd\+|eac3|e-ac-3|dd|ac3)(?=[\d. ]|\b)"""),
        "AAC" to Regex("""(?i)\baac\b"""),
        "Opus" to Regex("""(?i)\bopus\b"""),
        "FLAC" to Regex("""(?i)\bflac\b"""),
    )
    private val multiRx = Regex("""(?i)\b(?:multi[ -]?audio|dual[ -]?audio|multi)\b""")
    private val resRx = Regex("""(?i)(?<![\d])(2160|1440|1080|720|576|480|360|240)p\b|\b(4k|uhd)\b""")

    /** Size in bytes from text like "1.4 GB" / "700MB", or null. */
    fun sizeBytes(text: String): Long? {
        val m = sizeRx.find(text) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].lowercase()) { "tb" -> 1L shl 40; "gb", "gib" -> 1L shl 30; else -> 1L shl 20 }
        return (n * mult).toLong().takeIf { it in (1L shl 20)..(200L shl 30) }
    }

    fun formatSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024 * 1024)
        return if (gb >= 1) String.format(Locale.US, if (gb >= 10) "%.0f GB" else "%.1f GB", gb) else "${bytes / (1024 * 1024)} MB"
    }

    /** Audio facts: language (when [withLanguage]), "Multi audio", codec and channels. */
    fun audio(text: String, version: String = "", withLanguage: Boolean = true): List<String> {
        val out = ArrayList<String>()
        if (multiRx.containsMatchIn(text)) out += "Multi audio"
        else if (withLanguage) AudioLanguages.classify(text, version).language?.let { code ->
            if (code != AudioLanguages.MULTI) out += AudioLanguages.displayName(code)
        }
        codecRules.firstOrNull { it.second.containsMatchIn(text) }?.let { out += it.first }
        channelsRx.find(text)?.let { out += "${it.groupValues[1]}.${it.groupValues[2]}" }
        return out
    }

    fun of(link: OnlineLink, version: String = "", withLanguage: Boolean = false): Facts {
        val text = link.name + " " + link.source
        val tags = ReleaseTags.parse(text)
        val quality = link.qualityLabel.takeIf { it.isNotBlank() && it != "Auto" }
            ?: resRx.find(text)?.let { m -> if (m.groupValues[2].isNotBlank()) "4K" else m.groupValues[1].let { if (it == "2160") "4K" else "${it}p" } }
            ?: link.qualityLabel.takeIf { it == "Auto" }
        val release = tags.firstOrNull { it.kind == ReleaseTags.Kind.SOURCE }
        return Facts(
            quality = quality,
            release = release?.label,
            format = link.formatLabel.takeIf { it.isNotBlank() },
            size = sizeBytes(text)?.let(::formatSize),
            audio = audio(text, version, withLanguage),
            low = release?.low == true,
        )
    }
}
