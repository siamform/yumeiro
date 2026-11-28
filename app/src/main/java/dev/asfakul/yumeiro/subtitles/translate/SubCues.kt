/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.subtitles.translate

/**
 * B20 S6: subtitle files as timed cues (pure, unit-tested). Reads SRT, WebVTT and ASS/SSA; writes WebVTT. Timing and
 * order are kept exactly; styling becomes plain text, except a cue that is wholly italic stays italic. For
 * translation, a cue's lines become one sentence (speaker turns marked with "-" stay separate), and a sentence that
 * runs over into the next cue is translated once and split back by length ([units] / [splitBack]).
 */
object SubCues {
    data class Cue(val startMs: Long, val endMs: Long, val text: String, val italic: Boolean = false)

    /** One piece of text to translate: [parts] are (cue index, segment index inside that cue). */
    data class Unit(val text: String, val parts: List<Pair<Int, Int>>, val weights: List<Int>)

    private val TIME = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val ARROW = Regex("""^\s*(\S+)\s+-->\s+(\S+)""")
    private val BLANK = Regex("""\n\s*\n""")
    private val HTML = Regex("""</?[a-zA-Z][^>]*>""")
    private val ASS_TAG = Regex("""\{[^}]*\}""")
    private val ASS_ITALIC = Regex("""\{[^}]*\\i1[^}]*\}""")
    private val ASS_TIME = Regex("""^(\d+):(\d{2}):(\d{2})[.:](\d{1,3})$""")
    private val SPACES = Regex("""[ \t]+""")
    private val WHOLE_ITALIC = Regex("""^\s*<i>([\s\S]*)</i>\s*$""", RegexOption.IGNORE_CASE)
    private val END_PUNCT = Regex("""[.!?…。！？؟।"'”’»)\]♪~]\s*$""")
    private val DASH = Regex("""^\s*[-–—]\s*""")

    const val MAX_CUES = 6_000

    fun parse(text: String): List<Cue> {
        val t = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val head = t.trimStart().take(200)
        val cues = when {
            head.startsWith("WEBVTT") -> parseBlocks(t, vtt = true)
            t.contains("[Events]") || t.lineSequence().any { it.startsWith("Dialogue:") } -> parseAss(t)
            else -> parseBlocks(t, vtt = false)
        }
        return cues.filter { it.endMs > it.startMs && it.text.isNotBlank() }.sortedBy { it.startMs }.take(MAX_CUES)
    }

    internal fun time(s: String): Long? {
        val m = TIME.matchEntire(s.trim()) ?: return null
        val (h, mi, se, ms) = m.destructured
        val frac = ms.padEnd(3, '0').take(3).toLong()
        return ((h.ifEmpty { "0" }.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + frac
    }

    private fun parseBlocks(t: String, vtt: Boolean): List<Cue> = BLANK.split(t).mapNotNull { block ->
        val lines = block.lines().filter { it.isNotEmpty() }
        val at = lines.indexOfFirst { "-->" in it }
        if (at < 0) return@mapNotNull null
        if (vtt && lines.first().let { it.startsWith("NOTE") || it.startsWith("STYLE") || it.startsWith("REGION") }) return@mapNotNull null
        val m = ARROW.find(lines[at]) ?: return@mapNotNull null
        val start = time(m.groupValues[1]) ?: return@mapNotNull null
        val end = time(m.groupValues[2]) ?: return@mapNotNull null
        val raw = lines.drop(at + 1).joinToString("\n")
        val italic = WHOLE_ITALIC.matches(raw)
        Cue(start, end, clean(raw.replace(ASS_TAG, "")), italic)
    }

    private fun parseAss(t: String): List<Cue> {
        var fields = listOf("Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text")
        var inEvents = false
        val out = ArrayList<Cue>()
        for (line in t.lineSequence()) {
            val l = line.trim()
            if (l.startsWith("[")) { inEvents = l.equals("[Events]", true); continue }
            if (!inEvents && !l.startsWith("Dialogue:")) continue
            if (l.startsWith("Format:")) { fields = l.removePrefix("Format:").split(',').map { it.trim() }; continue }
            if (!l.startsWith("Dialogue:")) continue
            val values = l.removePrefix("Dialogue:").split(',', limit = fields.size).map { it.trim() }
            if (values.size < fields.size) continue
            fun f(n: String) = fields.indexOfFirst { it.equals(n, true) }.takeIf { it >= 0 }?.let { values[it] }
            val start = f("Start")?.let(::assTime) ?: continue
            val end = f("End")?.let(::assTime) ?: continue
            val raw = f("Text") ?: continue
            // Vector drawings ({\p1}) are shapes, not words.
            if (raw.contains("\\p1")) continue
            val italic = ASS_ITALIC.containsMatchIn(raw)
            val text = raw.replace(ASS_TAG, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
            out += Cue(start, end, clean(text), italic)
        }
        return out
    }

    private fun assTime(s: String): Long? {
        val m = ASS_TIME.matchEntire(s.trim()) ?: return null
        val (h, mi, se, cs) = m.destructured
        val frac = when (cs.length) { 1 -> cs.toLong() * 100; 2 -> cs.toLong() * 10; else -> cs.take(3).toLong() }
        return ((h.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + frac
    }

    private fun clean(s: String): String = s.replace(HTML, "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .lines().map { it.replace(SPACES, " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    // region writing

    private fun stamp(ms: Long): String {
        val h = ms / 3_600_000; val m = ms / 60_000 % 60; val s = ms / 1000 % 60; val f = ms % 1000
        return "%02d:%02d:%02d.%03d".format(h, m, s, f)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun toVtt(cues: List<Cue>): String = buildString {
        append("WEBVTT\n\n")
        cues.forEachIndexed { i, c ->
            append(i + 1).append('\n').append(stamp(c.startMs)).append(" --> ").append(stamp(c.endMs)).append('\n')
            val body = esc(c.text)
            append(if (c.italic) "<i>$body</i>" else body).append("\n\n")
        }
    }

    // endregion

    // region translation units

    /** A cue's speaker turns ("- Hi.\n- Hello.") or its lines as one sentence. */
    fun segments(c: Cue): List<String> {
        val lines = c.text.lines().filter { it.isNotBlank() }
        return if (lines.size >= 2 && lines.all { DASH.containsMatchIn(it) }) lines.map { it.replace(DASH, "") }
        else listOf(lines.joinToString(" "))
    }

    private fun dashed(c: Cue) = c.text.lines().filter { it.isNotBlank() }.let { l -> l.size >= 2 && l.all { DASH.containsMatchIn(it) } }

    /**
     * Pieces to translate. A one-sentence cue that doesn't end a sentence is joined with the next (gap ≤ 1.5 s, at
     * most 3 cues), so "I wanted to tell you" / "that I'm leaving." is translated as one sentence.
     */
    fun units(cues: List<Cue>): List<Unit> {
        val out = ArrayList<Unit>()
        var i = 0
        while (i < cues.size) {
            val c = cues[i]
            if (dashed(c)) {
                segments(c).forEachIndexed { s, text -> out += Unit(text, listOf(i to s), listOf(text.length.coerceAtLeast(1))) }
                i++; continue
            }
            val parts = arrayListOf(i to 0)
            val texts = arrayListOf(segments(c).first())
            var j = i
            while (parts.size < 3 && j + 1 < cues.size && !END_PUNCT.containsMatchIn(texts.last()) && !dashed(cues[j + 1]) &&
                cues[j + 1].startMs - cues[j].endMs <= 1_500) {
                j++
                parts += j to 0
                texts += segments(cues[j]).first()
            }
            out += Unit(texts.joinToString(" "), parts, texts.map { it.length.coerceAtLeast(1) })
            i = j + 1
        }
        return out
    }

    /** [text] cut into [weights].size pieces in proportion, at spaces when there are any (else between characters). */
    fun splitBack(text: String, weights: List<Int>): List<String> {
        if (weights.size <= 1) return listOf(text.trim())
        val total = weights.sum().toDouble()
        val spaced = text.trim().contains(' ')
        val tokens: List<String> = if (spaced) text.trim().split(' ').filter { it.isNotEmpty() } else text.trim().map { it.toString() }
        val joiner = if (spaced) " " else ""
        val len = tokens.sumOf { it.length }.coerceAtLeast(1)
        val out = ArrayList<String>()
        var k = 0
        var acc = 0.0
        weights.forEachIndexed { w, weight ->
            acc += weight / total
            val piece = ArrayList<String>()
            if (w == weights.lastIndex) { while (k < tokens.size) piece += tokens[k++] }
            else {
                // Leave at least one token for each later piece.
                while (k < tokens.size - (weights.size - 1 - w) && (piece.isEmpty() || (tokens.take(k).sumOf { it.length } + tokens[k].length / 2.0) / len <= acc)) piece += tokens[k++]
            }
            out += piece.joinToString(joiner)
        }
        return out
    }

    /** Long lines in two, at the space nearest the middle (42 characters fit a TV and a phone). */
    fun wrap(s: String, max: Int = 42): String {
        if (s.length <= max || '\n' in s) return s
        val mid = s.length / 2
        val cut = (0..mid).firstNotNullOfOrNull { d -> listOf(mid - d, mid + d).firstOrNull { it in 1 until s.length && s[it] == ' ' } } ?: return s
        return s.substring(0, cut).trimEnd() + "\n" + s.substring(cut + 1).trimStart()
    }

    /** The cues with their text replaced by [translated] (one string per unit, same order as [units]). */
    fun apply(cues: List<Cue>, units: List<Unit>, translated: List<String?>, extra: ((String) -> String?)? = null): List<Cue> {
        val seg = HashMap<Pair<Int, Int>, String>()
        units.forEachIndexed { u, unit ->
            val t = translated.getOrNull(u)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
            splitBack(t, unit.weights).forEachIndexed { p, piece -> seg[unit.parts[p]] = piece }
        }
        return cues.mapIndexed { i, c ->
            val n = segments(c).size
            val pieces = (0 until n).map { s -> seg[i to s] }
            if (pieces.all { it == null }) return@mapIndexed c
            val dashed = dashed(c)
            val native = pieces.mapIndexed { s, p -> (p ?: segments(c)[s]).let { if (dashed) "- $it" else it } }
            val text = if (dashed) native.joinToString("\n") else wrap(native.joinToString(" "))
            val second = extra?.let { f -> f(native.joinToString(if (dashed) "\n" else " ")) }?.let { if (dashed) it else wrap(it) }
            c.copy(text = when (second) { null -> text; else -> if (second == text) text else "$text\n$second" })
        }
    }

    // endregion
}
