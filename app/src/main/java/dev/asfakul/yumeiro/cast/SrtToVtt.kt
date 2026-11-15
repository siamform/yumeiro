/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cast

/**
 * R17 (G3 proposal): SRT to WebVTT, so SRT subtitles can be cast. Cast receivers show WebVTT, TTML
 * and CEA-608/708 only (developers.google.com/cast/docs/media), and most subtitle sites
 * (OpenSubtitles, SubDL, extensions) give SRT.
 *
 * What changes: the `WEBVTT` header, `,` → `.` in cue times, cue numbers dropped, ASS position tags
 * such as `{\an8}` removed (a receiver would print them), and `-->` inside cue text is escaped,
 * because WebVTT ends a cue at that arrow. Basic `<i>`, `<b>` and `<u>` tags are kept (WebVTT has
 * them too); `<font>` tags are removed. Broken blocks are skipped, not fatal. Pure; `SrtToVttTest`.
 */
object SrtToVtt {
    private val TIME = Regex("""^\s*(\d{1,2}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})(.*)$""")
    private val ASS_TAG = Regex("""\{\\[^}]*\}""")
    private val FONT_TAG = Regex("""</?font[^>]*>""", RegexOption.IGNORE_CASE)

    /** True when [text] looks like SRT (a cue time line with a comma), for links with no extension. */
    fun looksLikeSrt(text: String): Boolean =
        text.lineSequence().take(40).any { l -> TIME.matches(l) && l.contains(',') }

    fun convert(srt: String): String {
        val out = StringBuilder(srt.length + 16).append("WEBVTT\n\n")
        val lines = srt.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val m = TIME.matchEntire(lines[i])
            if (m == null) { i++; continue } // cue numbers, blank lines, junk
            val g = m.groupValues
            out.append(time(g[1], g[2], g[3], g[4])).append(" --> ").append(time(g[5], g[6], g[7], g[8])).append('\n')
            i++
            var any = false
            while (i < lines.size && lines[i].isNotBlank() && TIME.matchEntire(lines[i]) == null) {
                // A number right before the next time line is the next cue's number, not text.
                if (i + 1 < lines.size && TIME.matchEntire(lines[i + 1]) != null && lines[i].trim().all(Char::isDigit)) break
                val text = lines[i].replace(ASS_TAG, "").replace(FONT_TAG, "").replace("-->", "->").trimEnd()
                if (text.isNotEmpty()) { out.append(text).append('\n'); any = true }
                i++
            }
            if (!any) out.append(' ').append('\n') // keep the cue valid
            out.append('\n')
        }
        return out.toString()
    }

    private fun time(h: String, m: String, s: String, ms: String): String =
        "%02d:%02d:%02d.%s".format(h.toInt(), m.toInt(), s.toInt(), ms.padEnd(3, '0'))
}
