/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import java.util.Locale

/** Track choices for audio and captions. Pure → unit tested. */
object TrackPicks {

    data class Text(val group: Int, val index: Int, val language: String?, val label: String?, val forced: Boolean = false, val isDefault: Boolean = false)

    /** Base language code ("en-US" → "en", "eng" → "en"). */
    fun lang(tag: String?): String? {
        val t = tag?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
        val base = t.substringBefore('-').substringBefore('_')
        return runCatching { if (base.length == 3) Locale(base).language.takeIf { it.length == 2 } ?: base else base }.getOrDefault(base)
    }

    private fun labelLang(label: String?): String? {
        val l = label?.lowercase(Locale.ROOT) ?: return null
        return Locale.getAvailableLocales().firstOrNull { loc ->
            val name = loc.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
            name.isNotEmpty() && l.startsWith(name)
        }?.language
    }

    /**
     * The caption track to show when the user turns captions on: a track in [preferred] languages
     * (app / phone language first), then English, then a track the source marked default, then the
     * first one. Forced-only tracks (signs & songs) lose to full ones in the same language.
     */
    fun bestText(tracks: List<Text>, preferred: List<String>): Text? {
        if (tracks.isEmpty()) return null
        val wanted = (preferred.mapNotNull { lang(it) } + "en").distinct()
        fun langOf(t: Text) = lang(t.language) ?: labelLang(t.label)
        for (w in wanted) {
            val match = tracks.filter { langOf(it) == w }
            if (match.isNotEmpty()) return match.sortedWith(compareBy<Text> { it.forced || it.label.orEmpty().contains("sign", true) }.thenBy { !it.isDefault }).first()
        }
        return tracks.firstOrNull { it.isDefault } ?: tracks.first()
    }

    data class Audio(val group: Int, val index: Int, val language: String?, val label: String?, val channels: Int, val mime: String?, val supported: Boolean, val selected: Boolean)

    /**
     * One row per distinct audio track: HLS often lists the same language once per video variant,
     * which filled the menu with look-alike entries. The selected (else a playable) copy is kept.
     */
    fun dedupeAudio(tracks: List<Audio>): List<Audio> =
        tracks.groupBy { listOf(lang(it.language) ?: it.language.orEmpty(), it.label.orEmpty().lowercase(Locale.ROOT), it.channels.toString(), it.mime.orEmpty()) }
            .values.map { same -> same.firstOrNull { it.selected } ?: same.firstOrNull { it.supported } ?: same.first() }
            .sortedWith(compareBy<Audio> { it.group }.thenBy { it.index })
}
