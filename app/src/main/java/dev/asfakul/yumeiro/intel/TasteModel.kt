/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * B20 S3: one profile's taste for one kind of media, built from its signals (pure, unit tested).
 *
 * Each tag gets Σ (title weight × the tag's rank on that title), shrunk when only one title has it (one title is not
 * a taste: support = df / (df + 1)). Loved titles count double, "Not for me" titles build a separate negative taste.
 * Kept per [MediaCategory]: an Action anime habit says nothing about Action films (spec: separate profiles).
 */
data class TasteProfile(
    val category: MediaCategory,
    /** Aspect → tag id → strength (positive taste). */
    val positive: Map<Aspect, Map<String, Double>>,
    /** Aspect → tag id → strength (what this profile marked "Not for me" or turned away). */
    val negative: Map<Aspect, Map<String, Double>>,
    /** Display names of the tag ids (first seen spelling). */
    val names: Map<String, String>,
    /** The strongest liked titles with ids (seeds for candidate searches), best first. */
    val seeds: List<TasteSignals.Entry>,
    /** Titles that count (positive weight and tags). */
    val titles: Int,
) {
    val empty: Boolean get() = titles == 0

    /** The strongest tags of [aspect] (or of all aspects), for searches and the assistant. */
    fun top(n: Int, aspect: Aspect? = null): List<Pair<String, Double>> =
        (if (aspect != null) positive[aspect].orEmpty().entries else positive.values.flatMap { it.entries })
            .sortedByDescending { it.value }.take(n).map { it.key to it.value }

    companion object {
        /** Titles with fewer than this many tagged titles give no shelf (too little to say anything honest). */
        const val MIN_TITLES = 2

        fun build(entries: List<TasteSignals.Entry>, category: MediaCategory, now: Long): TasteProfile {
            val mine = entries.filter { it.category == category && it.tags.isNotEmpty() }
            val pos = HashMap<Aspect, HashMap<String, Double>>()
            val neg = HashMap<Aspect, HashMap<String, Double>>()
            val df = HashMap<String, Int>()
            val names = HashMap<String, String>()
            val weights = mine.associateWith { it.weight(now) }
            for ((e, w) in weights) if (w > 0) e.tags.forEach { t -> df[t.id] = (df[t.id] ?: 0) + 1 }
            var counted = 0
            for ((e, w) in weights) {
                if (w == 0.0) continue
                if (w > 0) counted++
                val into = if (w > 0) pos else neg
                for (t in e.tags) {
                    names.putIfAbsent(t.id, t.name)
                    val support = if (w > 0) df[t.id]!!.let { it / (it + 1.0) } else 1.0
                    val m = into.getOrPut(t.aspect) { HashMap() }
                    m[t.id] = (m[t.id] ?: 0.0) + kotlin.math.abs(w) * t.weight * support
                }
            }
            val seeds = weights.entries.filter { it.value > 0.2 && !it.key.ids.empty }.sortedByDescending { it.value }.map { it.key }
            return TasteProfile(category, pos, neg, names, seeds, counted)
        }
    }
}

/**
 * B20 S3: how well a candidate fits a [TasteProfile] and why (pure, unit tested). Plan "Preference and similarity":
 * weighted cosine per aspect, minus overlap with what the profile turned away, popularity damped so a famous title
 * never wins on fame alone, then MMR so the shelf is not ten copies of one idea.
 */
object Similarity {
    const val LAMBDA = 0.7
    private const val NEG_WEIGHT = 0.6

    data class Candidate(
        val key: String,
        val category: MediaCategory,
        val title: String,
        val tags: List<Tag>,
        val popularity: Int = 0,
        val score: Int = 0,
        val year: Int? = null,
        val releasedAt: String? = null,
        /** Seed titles that recommended it (AniList / TMDB recommendations), shown as "Because you liked …". */
        val because: List<String> = emptyList(),
    )

    /** One reason shown in the "why" sheet: a real tag both sides have (never a spoiler tag). */
    data class Reason(val name: String, val aspect: Aspect, val share: Double)

    data class Scored(val candidate: Candidate, val relevance: Double, val score: Double, val reasons: List<Reason>)

    /** Tag id → idf over [pool] (the candidates plus the profile's own titles): common tags tell less. */
    fun idf(pool: List<List<Tag>>): Map<String, Double> {
        val df = HashMap<String, Int>()
        pool.forEach { tags -> tags.map { it.id }.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        return df.mapValues { TasteSignals.idf(pool.size, it.value) }
    }

    private fun vector(tags: List<Tag>): Map<Aspect, Map<String, Double>> =
        tags.groupBy { it.aspect }.mapValues { (_, l) -> l.associate { it.id to it.weight.toDouble() } }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>, idf: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for ((k, v) in a) { val w = v * (idf[k] ?: 1.0); na += w * w; b[k]?.let { dot += w * it * (idf[k] ?: 1.0) } }
        for ((k, v) in b) { val w = v * (idf[k] ?: 1.0); nb += w * w }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
    }

    /** Weighted cosine over the aspects the taste has (an aspect the taste lacks neither helps nor hurts). */
    fun match(taste: Map<Aspect, Map<String, Double>>, tags: List<Tag>, idf: Map<String, Double>): Double {
        val c = vector(tags)
        var sum = 0.0; var w = 0.0
        for ((aspect, t) in taste) {
            if (t.isEmpty()) continue
            w += aspect.weight
            sum += aspect.weight * cosine(t, c[aspect].orEmpty(), idf)
        }
        return if (w == 0.0) 0.0 else sum / w
    }

    /** Plan damping: famous titles lose a little, never enough to beat a better personal match. */
    fun damp(popularity: Int): Double = 1.0 / (1.0 + 0.15 * ln(1.0 + max(0, popularity) / 1000.0))

    fun score(profile: TasteProfile, c: Candidate, idf: Map<String, Double>): Scored {
        val rel = match(profile.positive, c.tags, idf)
        val no = if (profile.negative.isEmpty()) 0.0 else match(profile.negative, c.tags, idf)
        val seedBonus = min(c.because.size, 3) * 0.04
        val relevance = (rel - NEG_WEIGHT * no + seedBonus).coerceIn(0.0, 1.0)
        return Scored(c, relevance, relevance * damp(c.popularity), reasons(profile.positive, profile.names, c.tags, idf))
    }

    /** The shared tags that carry the match, strongest first; spoiler tags are matched but never named. */
    fun reasons(taste: Map<Aspect, Map<String, Double>>, names: Map<String, String>, tags: List<Tag>, idf: Map<String, Double>, n: Int = 4): List<Reason> {
        val out = ArrayList<Reason>()
        for (t in tags) {
            if (t.spoiler) continue
            val mine = taste[t.aspect]?.get(t.id) ?: continue
            out += Reason(names[t.id] ?: t.name, t.aspect, t.aspect.weight * mine * t.weight * (idf[t.id] ?: 1.0))
        }
        val total = out.sumOf { it.share }.takeIf { it > 0 } ?: return emptyList()
        return out.sortedByDescending { it.share }.take(n).map { it.copy(share = it.share / total) }
    }

    /** Similar to one title (the title page's "Similar by story"): the title's own tags are the taste. */
    fun like(seed: List<Tag>, c: Candidate, idf: Map<String, Double>): Scored {
        val taste = vector(seed)
        val names = seed.associate { it.id to it.name }
        val rel = (match(taste, c.tags, idf) + min(c.because.size, 1) * 0.05).coerceIn(0.0, 1.0)
        return Scored(c, rel, rel * damp(c.popularity), reasons(taste, names, c.tags, idf))
    }

    private fun jaccard(a: List<Tag>, b: List<Tag>): Double {
        val x = a.filter { it.aspect != Aspect.GENRE }.map { it.id }.toSet()
        val y = b.filter { it.aspect != Aspect.GENRE }.map { it.id }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    /** Maximal marginal relevance (λ 0.7): each next pick trades a little relevance for being different. */
    fun mmr(scored: List<Scored>, n: Int, lambda: Double = LAMBDA): List<Scored> {
        val left = scored.sortedByDescending { it.score }.toMutableList()
        val top = left.firstOrNull()?.score?.takeIf { it > 0 } ?: 1.0
        val out = ArrayList<Scored>()
        while (out.size < n && left.isNotEmpty()) {
            // Relevance scaled to 0..1 (the best is 1) so it weighs against tag overlap (0..1) as MMR intends.
            val best = left.maxByOrNull { s -> lambda * (s.score / top) - (1 - lambda) * (out.maxOfOrNull { jaccard(it.candidate.tags, s.candidate.tags) } ?: 0.0) }!!
            out += best; left.remove(best)
        }
        return out
    }

    /** "Hidden gem": in the quieter 30 % of the pool and well rated (mean ≥ 70). */
    fun gems(pool: List<Candidate>): Set<String> {
        val pops = pool.map { it.popularity }.filter { it > 0 }.sorted()
        if (pops.size < 5) return emptySet()
        val cut = pops[(pops.size * 0.3).toInt().coerceAtMost(pops.size - 1)]
        return pool.filter { it.popularity in 1..cut && it.score >= 70 }.map { it.key }.toSet()
    }

    /** "New": released in the last 120 days (date as yyyy-mm-dd). */
    fun isNew(releasedAt: String?, now: Long): Boolean {
        val d = runCatching { java.time.LocalDate.parse(releasedAt) }.getOrNull() ?: return false
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate())
        return days in 0..120
    }
}
