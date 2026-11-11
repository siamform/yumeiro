/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.ui.screens.online.hueGridRows
import dev.asfakul.yumeiro.ui.screens.online.mixGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTagsTest {
    private fun labels(title: String, q: String? = null, type: String? = null, dub: Set<String> = emptySet()) =
        ReleaseTags.parse(title, q, type, dub).map { it.label }

    @Test fun dualAudioWebDl() {
        assertEquals(listOf("Movie", "WEB-DL", "720p", "Dual · HI-EN"), labels("Pathaan (2023) Dual Audio [Hindi-English] WEB-DL 720p", type = "Movie"))
    }

    @Test fun bengaliSeriesBluRay() {
        assertEquals(listOf("Series", "BluRay", "1080p", "Bengali"), labels("Mohanagar S02 Bengali BluRay 1080p"))
    }

    @Test fun multiAudioAndCamIsLow() {
        val tags = ReleaseTags.parse("Kalki 2898 AD Multi Audio HDTS", null, "Movie")
        assertEquals(listOf("Movie", "HDTS", "Multi Audio"), tags.map { it.label })
        assertTrue(tags.first { it.label == "HDTS" }.low)
        assertEquals(listOf("CAMRip"), labels("Some Film 2024 CAMRip").filter { it == "CAMRip" })
    }

    @Test fun extensionQualityAndDubFallback() {
        assertEquals(listOf("Anime", "BluRay", "Dub · Sub"), labels("Frieren", q = "BlueRay", type = "Anime", dub = setOf("Dubbed", "Subbed")))
        assertEquals(listOf("Movie", "4K"), labels("Dune Part Two", q = "FourK", type = "Movie"))
        assertEquals(listOf("Series", "HDTS"), labels("Show", q = "Telesync", type = "TV series"))
    }

    @Test fun noFalsePositivesInPlainTitles() {
        assertEquals(emptyList<String>(), labels("Cam (2018)"))
        assertEquals(emptyList<String>(), labels("Dual (2022)"))
        assertEquals(emptyList<String>(), labels("Camera Buff"))
        assertEquals(emptyList<String>(), labels("Unknown", q = "Unknown"))
    }

    private fun item(n: Int) = OnlineItem("t$n", "u$n", "api", null)

    @Test fun gridRowsChunkByOrientation() {
        val feed = (1..4).map { item(it) to false } + (5..7).map { item(it) to true } + listOf(item(8) to false)
        assertEquals(listOf(3, 1, 2, 1, 1), hueGridRows(feed).map { it.size })
    }

    @Test fun mixGroupsSkipEmptyAndLimit() {
        val r = { n: String, c: Int -> OnlineRow("a", n, (1..c).map { item(it) }) }
        val groups = mixGroups(listOf("A" to listOf(r("x", 0), r("y", 2), r("z", 3), r("w", 1)), "B" to listOf(r("e", 0)), "C" to listOf(r("q", 4))), maxGroups = 5)
        assertEquals(listOf("A", "C"), groups.map { it.name })
        assertEquals(listOf("y", "z"), groups[0].rows.map { it.title })
        assertEquals(6, groups[0].titles)
        assertEquals(1, mixGroups(listOf("A" to listOf(r("y", 2)), "C" to listOf(r("q", 4))), maxGroups = 1).size)
    }
}
