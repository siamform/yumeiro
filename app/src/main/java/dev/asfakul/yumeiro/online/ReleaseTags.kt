/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * Poster tags (language, release source, resolution, kind) read from what an extension gives us:
 * its `SearchQuality`, `TvType`, anime dub / sub status, and — because many South Asian sites
 * only say it in the name ("Pathaan (2023) Dual Audio [Hindi-English] WEB-DL 720p") — the title.
 *
 * Pure and deterministic (unit-tested). Nothing here hides or filters content; it only labels it.
 */
object ReleaseTags {
    enum class Kind { LANGUAGE, SOURCE, RESOLUTION, TYPE }

    /** [low] marks theatre recordings (CAM / TS) so the UI can tint them as a quality warning. */
    data class Tag(val label: String, val kind: Kind, val low: Boolean = false)

    private class Rule(val label: String, val kind: Kind, val regex: Regex, val low: Boolean = false)

    private fun r(p: String) = Regex("(?<![A-Za-z0-9])(?:$p)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

    // Order matters inside a kind: the first match of each kind wins (most specific first).
    private val sourceRules = listOf(
        Rule("WEB-DL", Kind.SOURCE, r("web[ ._-]?dl(?:rip)?|webdl")),
        Rule("WEBRip", Kind.SOURCE, r("web[ ._-]?rip")),
        Rule("BluRay", Kind.SOURCE, r("blu[ ._-]?ray|bdrip|brrip|bdremux|remux")),
        Rule("HDTS", Kind.SOURCE, r("hd[ ._-]?ts|hd[ ._-]?telesync"), low = true),
        Rule("HDCAM", Kind.SOURCE, r("hd[ ._-]?cam"), low = true),
        Rule("CAMRip", Kind.SOURCE, r("cam[ ._-]?rip|camrip"), low = true),
        Rule("HDTC", Kind.SOURCE, r("hd[ ._-]?tc|telecine"), low = true),
        Rule("TS", Kind.SOURCE, r("telesync|pdvd|pre[ ._-]?dvd(?:rip)?"), low = true),
        Rule("HC HDRip", Kind.SOURCE, r("hc[ ._-]?hd[ ._-]?rip")),
        Rule("HDRip", Kind.SOURCE, r("hd[ ._-]?rip")),
        Rule("DVDRip", Kind.SOURCE, r("dvd[ ._-]?rip|dvdscr|dvd")),
        Rule("HDTV", Kind.SOURCE, r("hdtv|pdtv|tvrip")),
    )

    private val resolutionRules = listOf(
        Rule("4K", Kind.RESOLUTION, r("2160p|4k|uhd")),
        Rule("1080p", Kind.RESOLUTION, r("1080p|fhd")),
        Rule("720p", Kind.RESOLUTION, r("720p")),
        Rule("480p", Kind.RESOLUTION, r("480p")),
    )

    private val multiRule = Rule("Multi Audio", Kind.LANGUAGE, r("multi[ ._-]?(?:audio|lang(?:uage)?s?)"))
    private val dualRule = Rule("Dual Audio", Kind.LANGUAGE, r("dual[ ._-]?audio"))

    private val languageRules = listOf(
        Rule("Bengali", Kind.LANGUAGE, r("bengali|bangla|বাংলা")),
        Rule("Hindi", Kind.LANGUAGE, r("hindi|हिन्दी|हिंदी")),
        Rule("English", Kind.LANGUAGE, r("english|eng")),
        Rule("Tamil", Kind.LANGUAGE, r("tamil")),
        Rule("Telugu", Kind.LANGUAGE, r("telugu")),
        Rule("Malayalam", Kind.LANGUAGE, r("malayalam")),
        Rule("Kannada", Kind.LANGUAGE, r("kannada")),
        Rule("Urdu", Kind.LANGUAGE, r("urdu")),
        Rule("Korean", Kind.LANGUAGE, r("korean")),
        Rule("Japanese", Kind.LANGUAGE, r("japanese")),
    )

    /** CloudStream `SearchQuality` names → the label people know. */
    private fun fromQuality(q: String?): Tag? = when (q?.trim()?.lowercase()) {
        null, "", "unknown" -> null
        "cam" -> Tag("CAM", Kind.SOURCE, low = true)
        "camrip" -> Tag("CAMRip", Kind.SOURCE, low = true)
        "hdcam" -> Tag("HDCAM", Kind.SOURCE, low = true)
        "telesync" -> Tag("HDTS", Kind.SOURCE, low = true)
        "telecine" -> Tag("HDTC", Kind.SOURCE, low = true)
        "workprint" -> Tag("Workprint", Kind.SOURCE, low = true)
        "blueray", "bluray" -> Tag("BluRay", Kind.SOURCE)
        "webrip" -> Tag("WEBRip", Kind.SOURCE)
        "dvd" -> Tag("DVD", Kind.SOURCE)
        "fourk", "uhd" -> Tag("4K", Kind.RESOLUTION)
        "hd" -> Tag("HD", Kind.RESOLUTION)
        "hq" -> Tag("HQ", Kind.RESOLUTION)
        "hdr" -> Tag("HDR", Kind.RESOLUTION)
        "sd" -> Tag("SD", Kind.RESOLUTION)
        "sdr" -> null
        else -> q.takeIf { it.length <= 10 }?.let { Tag(it, Kind.SOURCE) }
    }

    /** `TvType` label (see OnlineRepository.typeLabel) → Movie / Series / Anime. */
    private fun fromType(type: String?): Tag? = when (type?.trim()?.lowercase()) {
        "movie", "anime movie" -> Tag("Movie", Kind.TYPE)
        "tv series", "asian drama", "cartoon", "documentary" -> Tag("Series", Kind.TYPE)
        "anime", "ova" -> Tag("Anime", Kind.TYPE)
        "live" -> Tag("Live", Kind.TYPE)
        else -> null
    }

    private val seriesHint = r("s\\d{1,2}(?:e\\d{1,3})?|season[ ._-]?\\d+|complete[ ._-]?series|web[ ._-]?series|tv[ ._-]?series|episode[ ._-]?\\d+")

    /**
     * Every tag we can tell, in display order: type, source, resolution, then audio.
     * [dub] is the anime dub / sub status from the extension ("Dubbed", "Subbed").
     */
    fun parse(title: String, quality: String? = null, type: String? = null, dub: Set<String> = emptySet()): List<Tag> {
        val out = ArrayList<Tag>(5)
        val t = title.replace('_', ' ')
        // Type: the extension knows best; fall back to obvious series markers in the name.
        (fromType(type) ?: if (seriesHint.containsMatchIn(t)) Tag("Series", Kind.TYPE) else null)?.let { out += it }
        // Source: the title is more specific than SearchQuality ("WEB-DL" beats "HD").
        val titleSource = sourceRules.firstOrNull { it.regex.containsMatchIn(t) }?.let { Tag(it.label, it.kind, it.low) }
        val q = fromQuality(quality)
        val source = titleSource ?: q?.takeIf { it.kind == Kind.SOURCE }
        source?.let { out += it }
        val res = resolutionRules.firstOrNull { it.regex.containsMatchIn(t) }?.let { Tag(it.label, it.kind) }
            ?: q?.takeIf { it.kind == Kind.RESOLUTION }
        res?.let { out += it }
        // Audio: multi > dual > the languages named, else anime dub / sub.
        val langs = languageRules.filter { it.regex.containsMatchIn(t) }.map { it.label }
        when {
            multiRule.regex.containsMatchIn(t) || langs.size >= 3 -> out += Tag("Multi Audio", Kind.LANGUAGE)
            dualRule.regex.containsMatchIn(t) || langs.size == 2 -> out += Tag(
                if (langs.size == 2) "Dual · " + langs.joinToString("-") { short(it) } else "Dual Audio", Kind.LANGUAGE,
            )
            langs.size == 1 -> out += Tag(langs[0], Kind.LANGUAGE)
            else -> {
                val d = dub.map { it.lowercase() }
                val dubbed = d.any { it.startsWith("dub") }
                val subbed = d.any { it.startsWith("sub") }
                when {
                    dubbed && subbed -> out += Tag("Dub · Sub", Kind.LANGUAGE)
                    dubbed -> out += Tag("Dub", Kind.LANGUAGE)
                    subbed -> out += Tag("Sub", Kind.LANGUAGE)
                }
            }
        }
        return out
    }

    private fun short(lang: String) = when (lang) {
        "Bengali" -> "BN"; "Hindi" -> "HI"; "English" -> "EN"; "Tamil" -> "TA"; "Telugu" -> "TE"; "Malayalam" -> "ML"
        "Kannada" -> "KN"; "Urdu" -> "UR"; "Korean" -> "KO"; "Japanese" -> "JA"; else -> lang.take(2).uppercase()
    }
}
