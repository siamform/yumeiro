/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.lyrics

/** One lyric line. [timeMs] is null for unsynced (plain) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

enum class LyricsSource(val label: String) {
    EMBEDDED("Embedded in file"),
    SIDECAR(".lrc file"),
    IMPORTED("Imported .lrc"),
}

data class Lyrics(val lines: List<LyricLine>, val source: LyricsSource) {
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }

    /** Index of the line active at [positionMs] (binary search), or -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        if (!isSynced) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if ((lines[mid].timeMs ?: 0L) <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/** Parses LRC (`[mm:ss.xx]` tags, multiple tags per line, `[offset:±ms]`) or plain text. */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\[(ar|ti|al|au|by|re|ve|length|offset|#)\s*:.*]$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        var sawTimeTag = false
        for (line0 in text.split('\n')) {
            val line = line0.trim()
            if (line.isEmpty()) { plain.add(LyricLine(null, "")); continue }
            if (metaTag.matches(line)) continue
            val tags = timeTag.findAll(line).toList()
            if (tags.isNotEmpty() && line.startsWith("[")) {
                sawTimeTag = true
                val body = line.substring(tags.last().range.last + 1).trim()
                    // strip enhanced-LRC word timings <mm:ss.xx>
                    .replace(Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>"""), "").trim()
                for (t in tags) {
                    val min = t.groupValues[1].toLongOrNull() ?: 0L
                    val sec = t.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = t.groupValues[3]
                    val frac = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        else -> fracStr.take(3).toLong()
                    }
                    val ms = (min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0L)
                    synced.add(LyricLine(ms, body))
                }
            } else {
                plain.add(LyricLine(null, line))
            }
        }
        if (sawTimeTag && synced.isNotEmpty()) return synced.sortedBy { it.timeMs }
        // Plain text: trim leading/trailing blank lines, collapse runs of blanks.
        val out = ArrayList<LyricLine>()
        for (l in plain) {
            if (l.text.isEmpty() && (out.isEmpty() || out.last().text.isEmpty())) continue
            out.add(l)
        }
        while (out.isNotEmpty() && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
        return out
    }
}
