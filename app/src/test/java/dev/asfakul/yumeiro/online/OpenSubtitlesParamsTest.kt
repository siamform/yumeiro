/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.subtitles.OpenSubtitles
import org.junit.Assert.assertEquals
import org.junit.Test

/** The API 301-redirects requests whose query isn't sorted by name and lowercase. */
class OpenSubtitlesParamsTest {
    @Test fun sortedAndLowercase() {
        val p = OpenSubtitles.searchParams("Show", "tt0012345", 2, 3, "EN")
        assertEquals(listOf("episode_number", "imdb_id", "languages", "season_number"), p.map { it.first })
        assertEquals("12345", p.first { it.first == "imdb_id" }.second); assertEquals("en", p.first { it.first == "languages" }.second)
    }

    @Test fun titleSearch() {
        assertEquals(listOf("languages" to "fr", "query" to "the movie"), OpenSubtitles.searchParams("The Movie", null, null, null, "fr"))
    }
}
