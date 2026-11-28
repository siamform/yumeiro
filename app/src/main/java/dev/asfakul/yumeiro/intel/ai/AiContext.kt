/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import dev.asfakul.yumeiro.intel.Aspect
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.TasteProfile
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.profiles.Profiles
import org.json.JSONArray
import org.json.JSONObject

/**
 * B21 S4: what the assistant may know about the open profile's taste, built from that profile's own signals only
 * (never another profile's, never 18+ titles, nothing while nothing is recorded). Small on purpose: the request for
 * one category carries that category's slice (≤ 4 KB), so a free-tier key goes further and less is shared.
 */
object AiContext {
    private const val SLICE_MAX = 4_096

    /** The open profile's most played music artists (set by the music library, which is per profile). */
    @Volatile var musicArtists: List<String> = emptyList()

    data class Slice(val category: MediaCategory?, val json: JSONObject, val titles: Int) {
        val empty: Boolean get() = titles == 0
    }

    private data class Memo(val profile: String, val version: Int, val category: MediaCategory?, val slice: Slice)
    @Volatile private var memo: Memo? = null

    /** The slice for [category] (null = an overview of every category), rebuilt only when the signals changed. */
    fun slice(c: Context, category: MediaCategory?, now: Long = System.currentTimeMillis()): Slice {
        val profile = Profiles.currentId(c)
        val version = TasteSignals.version.value
        memo?.takeIf { it.profile == profile && it.version == version && it.category == category }?.let { return it.slice }
        val built = build(TasteSignals.entries(c), category, now, musicArtists, youTubeChannels(c),
            Discovery.SHELVES.mapNotNull { Discovery.shelf(c, it) }.flatMap { s -> s.recs.take(8).map { s.category to it.title } })
        memo = Memo(profile, version, category, built)
        return built
    }

    private fun youTubeChannels(c: Context): List<String> = runCatching {
        dev.asfakul.yumeiro.youtube.YtHistory.load(c).map { it.channel }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(10).map { it.key }
    }.getOrDefault(emptyList())

    /** Pure: the context JSON. Titles are names and years only; adult titles never appear. */
    internal fun build(
        entries: List<TasteSignals.Entry>, category: MediaCategory?, now: Long,
        artists: List<String> = emptyList(), channels: List<String> = emptyList(), shown: List<Pair<MediaCategory, String>> = emptyList(),
    ): Slice {
        val cats = if (category != null) listOf(category) else Discovery.SHELVES
        val out = JSONObject()
        var titles = 0
        for (cat in cats) {
            val mine = entries.filter { it.category == cat && it.weight(now) != 0.0 }
            if (mine.isEmpty()) continue
            titles += mine.size
            val o = JSONObject()
            val taste = TasteProfile.build(entries, cat, now)
            if (!taste.empty) o.put("taste", JSONObject().apply {
                for (a in listOf(Aspect.THEMES, Aspect.CHARACTERS, Aspect.SETTING, Aspect.GENRE, Aspect.STYLE)) {
                    val top = taste.top(if (category != null) 8 else 4, a)
                    if (top.isEmpty()) continue
                    val max = top.first().second.takeIf { it > 0 } ?: 1.0
                    put(a.label, JSONArray().apply { top.forEach { (id, v) -> put("${taste.names[id] ?: id} (${(v / max * 100).toInt()})") } })
                }
            })
            fun names(list: List<TasteSignals.Entry>, n: Int) = JSONArray().apply { list.take(n).forEach { put(label(it)) } }
            val byRecent = mine.sortedByDescending { maxOf(it.lastAt, it.feedbackAt) }
            val n = if (category != null) 15 else 5
            TasteSignals.Feedback.entries.forEach { f ->
                val l = byRecent.filter { it.feedback == f }
                if (l.isNotEmpty()) o.put(f.code, names(l, n))
            }
            byRecent.filter { it.completed && it.feedback == null }.takeIf { it.isNotEmpty() }?.let { o.put("finished", names(it, if (category != null) 30 else 8)) }
            byRecent.filter { it.dropped(now) }.takeIf { it.isNotEmpty() }?.let { o.put("dropped", names(it, n)) }
            byRecent.filter { it.started && !it.completed && !it.dropped(now) }.takeIf { it.isNotEmpty() }?.let { o.put("watching", names(it, n)) }
            byRecent.filter { it.dismissedAt > 0 }.takeIf { it.isNotEmpty() }?.let { o.put("not_interested", names(it, n)) }
            shown.filter { it.first == cat }.map { it.second }.distinct().takeIf { it.isNotEmpty() }?.let { o.put("recently_suggested", JSONArray(it.take(n))) }
            out.put(cat.code, o)
        }
        if (category == null || category == MediaCategory.MUSIC) artists.takeIf { it.isNotEmpty() }?.let { out.put("music_artists", JSONArray(it.take(10))); titles += it.size }
        if (category == null || category == MediaCategory.YOUTUBE) channels.takeIf { it.isNotEmpty() }?.let { out.put("youtube_channels", JSONArray(it.take(10))); titles += it.size }
        return Slice(category, trim(out), titles)
    }

    private fun label(e: TasteSignals.Entry) = e.title.take(80) + (e.year?.let { " ($it)" } ?: "")

    /** Keeps the slice under [SLICE_MAX] by shortening the longest lists first (finished, then the rest). */
    private fun trim(o: JSONObject): JSONObject {
        var guard = 0
        while (o.toString().length > SLICE_MAX && guard++ < 200) {
            var longest: JSONArray? = null
            o.keys().forEach { k ->
                val v = o.opt(k)
                val arrays = when (v) {
                    is JSONObject -> v.keys().asSequence().mapNotNull { v.optJSONArray(it) }.toList()
                    is JSONArray -> listOf(v)
                    else -> emptyList()
                }
                arrays.forEach { a -> if (longest == null || a.length() > longest!!.length()) longest = a }
            }
            val l = longest ?: break
            if (l.length() <= 1) break
            l.remove(l.length() - 1)
        }
        return o
    }

    fun unload() { memo = null; musicArtists = emptyList() }
}
