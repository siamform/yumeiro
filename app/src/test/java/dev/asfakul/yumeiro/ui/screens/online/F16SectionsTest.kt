package dev.asfakul.yumeiro.ui.screens.online

// F-16 (ledger F-16, docs/proposals/FEATURE-F-16-yumix-sections-tags.md): Yumix sections
// Movies, Series, Web series, Dual Audio, Sub, Dub in that order, and the quality / Sub / Dub /
// Dual-audio tags on the banner. Fail-before: Category has no WEB_SERIES / DUAL_AUDIO / SUB / DUB
// and YumixFeed has no heroTags.

import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F16SectionsTest {
    private fun item(name: String, n: Int, type: String? = null, quality: String? = null, dub: Set<String> = emptySet()) =
        OnlineItem("$name $n", "u/$name/$n", "api", "p/$n", type = type, quality = quality, dub = dub)

    private fun cats(vararg rows: OnlineRow) = YumixFeed.categories(listOf("api" to rows.toList())).associate { it.first.name to it.second.size }

    @Test fun webSeriesComesFromTheRowTitleAndTheName() {
        val byRow = OnlineRow("api", "Latest Web Series", (0 until 4).map { item("Show", it) })
        val byName = OnlineRow("api", "Trending", (0 until 4).map { item("Mirzapur Web Series S01", it) })
        assertEquals(4, cats(byRow)["WEB_SERIES"])
        assertEquals(4, cats(byName)["WEB_SERIES"])
    }

    @Test fun dualSubAndDubComeFromTheReleaseTags() {
        val dual = OnlineRow("api", "Trending", (0 until 4).map { item("Pathaan 2023 Dual Audio Hindi English 1080p", it) })
        val sub = OnlineRow("api", "Popular", (0 until 4).map { item("Frieren", it, type = "Anime", dub = setOf("Subbed")) })
        val dub = OnlineRow("api", "New", (0 until 4).map { item("Naruto", it, type = "Anime", dub = setOf("Dubbed", "Subbed")) })
        val c = cats(dual, sub, dub)
        assertEquals(4, c["DUAL_AUDIO"])
        assertEquals(8, c["SUB"])
        assertEquals(4, c["DUB"])
    }

    @Test fun aPlainMovieIsNotSubDubOrDual() {
        val plain = OnlineRow("api", "Trending", (0 until 6).map { item("Dune", it, type = "Movie") })
        val c = cats(plain)
        assertFalse("DUAL_AUDIO" in c); assertFalse("SUB" in c); assertFalse("DUB" in c); assertFalse("WEB_SERIES" in c)
        assertEquals(6, c["MOVIES"])
    }

    @Test fun theOwnerOrderIsMoviesSeriesWebSeriesDualSubDub() {
        val order = YumixFeed.Category.entries.map { it.name }
        val wanted = listOf("MOVIES", "SERIES", "WEB_SERIES", "DUAL_AUDIO", "SUB", "DUB")
        val idx = wanted.map { order.indexOf(it) }
        assertTrue("all present: $idx", idx.all { it >= 0 })
        assertEquals(idx.sorted(), idx)
    }

    @Test fun bannerTagsShowQualityAndAudioNotOnlyTheRawQualityWord() {
        val t = YumixFeed.heroTags(item("Pathaan 2023 WEB-DL 1080p Dual Audio", 1, type = "Movie", quality = "HD"))
        assertTrue(t.toString(), "1080p" in t)
        assertTrue(t.toString(), t.any { it.startsWith("Dual") })
        val a = YumixFeed.heroTags(item("Naruto", 1, type = "Anime", dub = setOf("Dubbed", "Subbed")))
        assertTrue(a.toString(), "Dub · Sub" in a)
        // No duplicates and nothing blank.
        assertEquals(t.distinct(), t); assertTrue(t.none { it.isBlank() })
    }
}
