/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anime

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Intro / outro / recap times for anime episodes from the community AniSkip database
 * (api.aniskip.com, keyed by MyAnimeList id + episode number).
 */
object AniSkip {
    enum class Kind(val label: String) { INTRO(tr(R.string.skip_intro, "Skip intro")), OUTRO(tr(R.string.skip_outro, "Skip outro")), RECAP(tr(R.string.skip_recap, "Skip recap")), MIXED_INTRO(tr(R.string.skip_intro, "Skip intro")), MIXED_OUTRO(tr(R.string.skip_outro, "Skip outro")) }

    /**
     * [episodeLengthMs]: length of the video the times were submitted for (0 = unknown). [fits]: false when
     * that length differs from the playing video's by more than [FIT_MS], so the times may belong to another
     * cut; such a segment is offered as a button but never skipped automatically.
     */
    data class Segment(val kind: Kind, val startMs: Long, val endMs: Long, val episodeLengthMs: Long = 0, val fits: Boolean = true) {
        fun contains(posMs: Long) = posMs in startMs until (endMs - 500)
    }

    private val cache = ConcurrentHashMap<String, List<Segment>>()

    /** AniSkip's own length tolerance (its answers for One Piece 1000, 1430.7 s: 1445 s found, 1460 s not). */
    const val FIT_MS = 15_000L
    /** Up to this far off, end credits are placed relative to the end of the video. */
    const val END_SHIFT_MS = 120_000L

    /**
     * SKIP-1: every submission for the episode, asked with `episodeLength=0`. Sending the playing
     * video's length (as before) made AniSkip answer 404 "No skip times found" whenever the source's cut
     * differed from the submitted one by more than about 15 s (checked 2026-11-24: One Piece 1000 has a
     * 1430.7 s submission; 1440 s found, 1460 s and 1500 s not found), and a length the player didn't
     * know yet (C.TIME_UNSET) was a 400. The best submission for this video is chosen in [pick].
     * @return null when AniSkip could not be asked (no network, server trouble); empty when it has nothing.
     */
    fun fetch(client: OkHttpClient, malId: Int, episode: Int): List<Segment>? {
        val key = "$malId:$episode"
        cache[key]?.let { return it }
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode?types[]=op&types[]=ed&types[]=mixed-op&types[]=mixed-ed&types[]=recap&episodeLength=0"
        // Only real answers are cached: a timeout or no network used to cache "no segments" for
        // the rest of the process, so intro skip stayed off for that episode after one bad moment.
        val list: List<Segment>? = runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                when {
                    r.isSuccessful -> parse(r.body.string())
                    r.code == 404 -> emptyList() // no data for this episode (normal)
                    else -> null // server trouble: try again next time
                }
            }
        }.getOrNull()
        if (list != null) cache[key] = list
        return list
    }

    /** The segments for a video of [durationMs] (0 or less = not known yet). */
    fun segments(client: OkHttpClient, malId: Int, episode: Int, durationMs: Long = 0): List<Segment> =
        pick(fetch(client, malId, episode).orEmpty(), durationMs)

    /**
     * SKIP-1: one segment per kind, from the submission whose video length is closest to [durationMs]
     * (the earliest when the length isn't known). A segment starting after
     * the end of this video, or overlapping one already chosen, is dropped; see [Segment.fits].
     */
    fun pick(all: List<Segment>, durationMs: Long): List<Segment> {
        val known = durationMs > 0
        fun off(x: Segment) = if (known && x.episodeLengthMs > 0) kotlin.math.abs(x.episodeLengthMs - durationMs) else Long.MAX_VALUE
        fun mixed(x: Segment) = x.kind == Kind.MIXED_INTRO || x.kind == Kind.MIXED_OUTRO
        val chosen = ArrayList<Segment>()
        // One recap, one opening (plain or mixed) and one ending: the closest-length submission of each,
        // a plain one before a mixed one at the same distance, the earlier one after that.
        for (group in listOf(setOf(Kind.RECAP), setOf(Kind.INTRO, Kind.MIXED_INTRO), setOf(Kind.OUTRO, Kind.MIXED_OUTRO))) {
            val best = all.filter { it.kind in group && (!known || it.startMs < durationMs) }
                .sortedWith(compareBy<Segment>({ if (known) off(it) else 0L }, { mixed(it) }))
                .firstOrNull { c -> chosen.none { c.startMs < it.endMs && it.startMs < c.endMs } } ?: continue
            val fits = best.fits && (!known || best.episodeLengthMs <= 0 || off(best) <= FIT_MS)
            // Credits sit at the end: for another cut up to 2 minutes longer or shorter they move with the end.
            val shift = if (!fits && Kind.OUTRO in group && off(best) <= END_SHIFT_MS) durationMs - best.episodeLengthMs else 0L
            val s = (best.startMs + shift).coerceAtLeast(0)
            val e = (best.endMs + shift).let { if (known) minOf(it, durationMs) else it }
            if (e > s) chosen += best.copy(startMs = s, endMs = e, fits = fits)
        }
        return chosen.sortedBy { it.startMs }
    }

    internal fun parse(text: String): List<Segment> {
        val o = JSONObject(text)
        if (!o.optBoolean("found")) return emptyList()
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            val iv = r.optJSONObject("interval") ?: return@mapNotNull null
            val kind = when (r.optString("skipType")) {
                "op" -> Kind.INTRO; "ed" -> Kind.OUTRO; "recap" -> Kind.RECAP
                "mixed-op" -> Kind.MIXED_INTRO; "mixed-ed" -> Kind.MIXED_OUTRO
                else -> return@mapNotNull null
            }
            val s = (iv.optDouble("startTime") * 1000).toLong(); val e = (iv.optDouble("endTime") * 1000).toLong()
            val len = (r.optDouble("episodeLength", 0.0) * 1000).toLong().coerceAtLeast(0)
            if (e > s) Segment(kind, s, e, len) else null
        }.sortedBy { it.startMs }
    }
}
