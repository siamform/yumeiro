/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.cast.CastSupport
import org.junit.Assert.assertEquals
import org.junit.Test

/** Only subtitle formats a Cast receiver can show are sent (TTML, WebVTT). */
class CastSubtitlesTest {
    @Test fun keepsVttAndTtmlDropsSrt() {
        val out = CastSupport.castableSubtitles(listOf(
            "English" to "https://x.example/en.vtt?token=1",
            "French" to "https://x.example/fr.srt",
            "German" to "https://x.example/de.TTML",
            "Raw" to "https://x.example/sub?id=3",
        ))
        assertEquals(listOf("English" to "text/vtt", "German" to "application/ttml+xml"), out.map { it.first to it.third })
    }
}
