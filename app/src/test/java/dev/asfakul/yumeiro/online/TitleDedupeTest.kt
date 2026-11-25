/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEDUP-1 (owner list 14): one card per title across extensions on Yumix "All". */
class TitleDedupeTest {
    private fun item(name: String, api: String, type: String? = null) = OnlineItem(name, "https://$api.example/${name.hashCode()}", api, null, type = type)
    private fun row(api: String, vararg items: OnlineItem) = OnlineRow(api, "Popular", items.toList())

    @Test fun releaseNoiseIsIgnored() {
        val a = TitleDedupe.key(item("Dune: Part Two (2024) [Hindi + English] 1080p WEB-DL", "A"))
        val b = TitleDedupe.key(item("Dune Part Two", "B"))
        assertEquals(a.base, b.base)
        assertEquals(2024, a.year)
    }

    @Test fun secondExtensionLosesItsCopy() {
        val out = TitleDedupe.acrossExtensions(listOf(
            "A" to listOf(row("A", item("Dune: Part Two (2024)", "A"), item("Oppenheimer", "A"))),
            "B" to listOf(row("B", item("Dune Part Two 2024 Dual Audio", "B"), item("Inside Out 2", "B"))),
            "C" to listOf(row("C", item("OPPENHEIMER (2023) HDRip", "C"))),
        ))
        assertEquals(listOf("A", "B"), out.map { it.first })
        assertEquals(listOf("Inside Out 2"), out[1].second.single().items.map { it.name })
    }

    @Test fun sameExtensionShelvesStay() {
        val p = item("Solo Leveling", "A")
        val out = TitleDedupe.acrossExtensions(listOf("A" to listOf(row("A", p), OnlineRow("A", "Latest", listOf(p.copy(url = "https://a/2"))))))
        assertEquals(2, out.single().second.size)
    }

    @Test fun differentYearsOrKindsStayApart() {
        val s = TitleDedupe.Seen()
        assertTrue(s.offer(item("The Lion King (1994)", "A")))
        assertTrue("remake", s.offer(item("The Lion King (2019)", "B")))
        assertTrue(s.offer(item("Shogun", "A", type = "Movie")))
        assertTrue("film vs series", s.offer(item("Shogun", "B", type = "TvSeries")))
        assertFalse(s.offer(item("The Lion King", "C")))
    }

    @Test fun titleWordsAreNotNoise() {
        // "Free", "Online", "Download" can be real title words.
        assertEquals("free guy", TitleDedupe.key(item("Free Guy", "A")).base)
        assertEquals("the sub", TitleDedupe.key(item("The Sub", "A")).base)
        assertTrue(TitleDedupe.Seen().run { offer(item("Guy", "A")); offer(item("Free Guy", "B")) })
    }

    @Test fun exploreSkipsWhatIsAbove() {
        val above = listOf(item("Dune: Part Two", "A"))
        val c = listOf(item("Dune Part Two [1080p]", "B"), item("Dune: Part Two", "A"), item("Arcane", "B"))
        assertEquals(listOf("Dune: Part Two", "Arcane"), TitleDedupe.exploreCandidates(c, above, emptyList()) { it }.map { it.name })
    }
}
