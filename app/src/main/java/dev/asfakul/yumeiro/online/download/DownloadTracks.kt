/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * OL-5: the choices a download can offer beyond Offline / Permanent: quality, audio track
 * (language / dub) and subtitles, read from an HLS master playlist (RFC 8216 §4.3.4.1
 * `EXT-X-MEDIA`, §4.3.4.2 `EXT-X-STREAM-INF`) or from what an extension reports.
 * Pure: no network, no regex (attribute lists are split by hand).
 */
object DownloadTracks {
    /** One `EXT-X-MEDIA` rendition. [uri] null = muxed into the variant. */
    data class Rendition(val group: String, val name: String, val language: String?, val uri: String?, val default: Boolean) {
        val label: String get() = name.ifBlank { language.orEmpty() }.ifBlank { "Track" }
    }

    data class Master(val heights: List<Int>, val audios: List<Rendition>, val subtitles: List<Rendition>)

    /** Subtitles to save next to the video: none, every one found, or the one(s) matching a label / language. */
    const val SUBS_NONE = ""
    const val SUBS_ALL = "*"

    /**
     * What the user picked in the download popup. [maxHeight] -1 = Settings value, 0 = best.
     * [audio] null = the stream's default track. [subtitles] [SUBS_NONE], [SUBS_ALL] or a label / language.
     */
    data class Pick(val maxHeight: Int = -1, val audio: String? = null, val subtitles: String = SUBS_NONE)

    /** What the popup offers. [subtitlesLater]: the source names its subtitles only when the download starts (extensions). */
    data class Offer(
        val heights: List<Int> = emptyList(),
        val audios: List<String> = emptyList(),
        val subtitles: List<String> = emptyList(),
        val subtitlesLater: Boolean = false,
        /** OWN-11: [audios] are audio-only downloads offered next to the video (YouTube), so even one is a choice. */
        val audioOnly: Boolean = false,
        /** OWN-8: with [subtitlesLater], languages offered for a batch ("English", "Bengali"); saved per episode. */
        val subtitleLanguages: List<String> = emptyList(),
    ) {
        val hasChoices: Boolean get() = heights.size > 1 || audios.size > 1 || (audioOnly && audios.isNotEmpty()) || subtitles.isNotEmpty() || subtitlesLater
    }

    /**
     * The popup's audio row: its title and chips (value null = the default), or null for no row.
     * OWN-11: for a YouTube video it reads "Save as: Video / Audio only · …" and shows with a single audio choice.
     */
    fun audioRow(offer: Offer): Pair<String, List<Pair<String?, String>>>? = when {
        offer.audioOnly && offer.audios.isNotEmpty() -> "Save as" to (listOf<Pair<String?, String>>(null to "Video") + offer.audios.map { it to it })
        offer.audios.size > 1 -> "Audio track" to (listOf<Pair<String?, String>>(null to "Default") + offer.audios.map { it to it })
        else -> null
    }

    fun offerOf(m: Master): Offer = Offer(
        heights = m.heights.filter { it > 0 }.distinct().sortedDescending(),
        audios = m.audios.filter { it.uri != null }.map { it.label }.distinct(),
        subtitles = m.subtitles.filter { it.uri != null }.map { it.label }.distinct(),
    )

    /** `KEY=VALUE,KEY="a, b"` → map (keys upper-case). */
    fun attrs(line: String): Map<String, String> {
        val body = line.substringAfter(':', "")
        val out = HashMap<String, String>()
        var i = 0
        while (i < body.length) {
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val key = body.substring(i, eq).trim().trimStart(',').trim().uppercase()
            var j = eq + 1
            val value: String
            if (j < body.length && body[j] == '"') {
                val end = body.indexOf('"', j + 1).let { if (it < 0) body.length else it }
                value = body.substring(j + 1, end)
                j = (end + 1).coerceAtMost(body.length)
                val comma = body.indexOf(',', j)
                i = if (comma < 0) body.length else comma + 1
            } else {
                val comma = body.indexOf(',', j)
                value = body.substring(j, if (comma < 0) body.length else comma).trim()
                i = if (comma < 0) body.length else comma + 1
            }
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }

    fun resolve(base: String, ref: String): String = base.toHttpUrlOrNull()?.resolve(ref.trim())?.toString() ?: ref.trim()

    /** Parses an HLS master playlist; null when [text] is a media playlist. */
    fun parseMaster(text: String, base: String): Master? {
        if ("#EXT-X-STREAM-INF" !in text) return null
        val heights = ArrayList<Int>(); val audios = ArrayList<Rendition>(); val subs = ArrayList<Rendition>()
        for (raw in text.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXT-X-STREAM-INF") -> attrs(l)["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull()?.let { heights += it }
                l.startsWith("#EXT-X-MEDIA:") -> {
                    val a = attrs(l)
                    val r = Rendition(
                        group = a["GROUP-ID"].orEmpty(), name = a["NAME"].orEmpty(),
                        language = a["LANGUAGE"]?.takeIf { it.isNotBlank() },
                        uri = a["URI"]?.takeIf { it.isNotBlank() }?.let { resolve(base, it) },
                        default = a["DEFAULT"].equals("YES", true),
                    )
                    when (a["TYPE"]?.uppercase()) {
                        "AUDIO" -> audios += r
                        "SUBTITLES" -> subs += r
                    }
                }
            }
        }
        return Master(heights, audios, subs)
    }

    private fun matches(r: Rendition, want: String): Boolean =
        r.label.equals(want, true) || r.name.equals(want, true) || (r.language != null && r.language.equals(want, true)) ||
            (r.language != null && want.length >= 2 && r.language.substringBefore('-').equals(want.substringBefore('-'), true)) ||
            (want.length > 3 && (sameLanguage(r.label, want) || (r.language != null && sameLanguage(r.language, want))))

    /**
     * Audio rendition of [group] to save. [want] (a label or language) first, then DEFAULT=YES, then
     * the first with a URI. Null when the group's audio is muxed into the video.
     */
    fun pickAudio(audios: List<Rendition>, group: String?, want: String?): Rendition? {
        val inGroup = audios.filter { group == null || it.group == group }.filter { it.uri != null }
        if (inGroup.isEmpty()) return null
        if (want != null) inGroup.firstOrNull { matches(it, want) }?.let { return it }
        return inGroup.firstOrNull { it.default } ?: inGroup.first()
    }

    /** Subtitle renditions to save for [want]. */
    fun pickSubtitles(subs: List<Rendition>, want: String): List<Rendition> {
        val withUri = subs.filter { it.uri != null }.distinctBy { it.uri }
        return when (want) {
            SUBS_NONE -> emptyList()
            SUBS_ALL -> withUri
            else -> withUri.filter { matches(it, want) }.take(1)
        }
    }

    /** Extension subtitles (label → url) to save for [want]: exact label first, then by language prefix. */
    fun pickNamed(found: List<Pair<String, String>>, want: String): List<Pair<String, String>> {
        val distinct = found.distinctBy { it.second }
        return when (want) {
            SUBS_NONE -> emptyList()
            SUBS_ALL -> distinct.take(MAX_SUBS)
            else -> (distinct.firstOrNull { it.first.equals(want, true) } ?: distinct.firstOrNull { it.first.startsWith(want, true) }
                ?: distinct.firstOrNull { sameLanguage(it.first, want) })?.let { listOf(it) }.orEmpty()
        }
    }

    /**
     * OWN-8: an extension's subtitle label in the language [want] (an English language name): "en", "eng",
     * "en-US", "English (CC)", "Inglés"-free labels by code. Pure.
     */
    fun sameLanguage(label: String, want: String): Boolean {
        val l = label.trim()
        if (l.startsWith(want, true)) return true
        val code = l.split(' ', '(', '[', ',', '_').firstOrNull().orEmpty().replace('_', '-')
        if (code.length !in 2..8) return false
        val name = runCatching { java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH) }.getOrNull().orEmpty()
        if (name.isNotEmpty() && !name.equals(code, true) && name.equals(want, true)) return true
        // ISO 639-2 three-letter codes ("eng", "spa", "ben").
        return java.util.Locale.getAvailableLocales().any { loc ->
            loc.getDisplayLanguage(java.util.Locale.ENGLISH).equals(want, true) && runCatching { loc.isO3Language }.getOrNull().equals(code, true)
        }
    }

    /** OWN-8: the languages an episode batch offers: the Settings subtitle language first, then English. */
    fun batchLanguages(preferredCode: String): List<String> {
        val pref = runCatching { java.util.Locale.forLanguageTag(preferredCode).getDisplayLanguage(java.util.Locale.ENGLISH) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(preferredCode, true) }
        return listOfNotNull(pref, "English").distinct()
    }

    const val MAX_SUBS = 12

    /** File name-safe tag for a subtitle label ("English (CC)" → "English CC"). */
    fun tag(label: String): String = label.map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else ' ' }
        .joinToString("").split(' ').filter { it.isNotBlank() }.joinToString(" ").take(40).ifBlank { "sub" }

    /** Side file for subtitle [label] of video [videoName] ("Ep 1.mp4" → "Ep 1.English.vtt"). */
    fun sidecarName(videoName: String, label: String, ext: String): String =
        videoName.substringBeforeLast('.') + "." + tag(label) + "." + ext.lowercase().ifBlank { "vtt" }

    /** Subtitle extension from a URL path or the file's first bytes. */
    fun subtitleExt(url: String, head: String): String {
        val path = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val e = path.substringAfterLast('.', "").lowercase()
        if (e in SUB_EXTS) return e
        val t = head.trimStart('\uFEFF').trimStart()
        return when {
            t.startsWith("WEBVTT") -> "vtt"
            t.startsWith("[Script Info]") -> "ass"
            else -> "srt"
        }
    }

    val SUB_EXTS = setOf("vtt", "srt", "ass", "ssa")

    /** Player MIME type for a saved subtitle file. */
    fun subtitleMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    /**
     * Side files saved next to [videoName] among [names] (same folder), as (file name, label).
     * "Ep 1.English.vtt" → ("Ep 1.English.vtt", "English").
     */
    fun sidecarsOf(videoName: String, names: List<String>): List<Pair<String, String>> {
        val stem = videoName.substringBeforeLast('.') + "."
        return names.filter { n -> n.startsWith(stem) && n != videoName && n.substringAfterLast('.', "").lowercase() in SUB_EXTS }
            .sorted().map { n -> n to n.removePrefix(stem).substringBeforeLast('.').ifBlank { "Subtitles" } }
    }

    /** Joins WebVTT segments into one file: the first keeps its header, later ones lose theirs. */
    fun joinVtt(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        val sb = StringBuilder(parts.first().trimEnd()).append("\n\n")
        for (p in parts.drop(1)) {
            val t = p.trimStart('\uFEFF')
            val body = if (t.trimStart().startsWith("WEBVTT")) {
                val idx = t.indexOf("\n\n").let { if (it < 0) t.indexOf("\r\n\r\n") else it }
                if (idx < 0) "" else t.substring(idx).trim()
            } else t.trim()
            if (body.isNotEmpty()) sb.append(body).append("\n\n")
        }
        return sb.toString()
    }
}
