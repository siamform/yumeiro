package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MKT-FILTER-1 (owner list 9) on a real slice of the market index (12 repositories, 90 listings, 2026-11-23). */
class MarketFacetsTest {
    private val catalog = ExtensionMarket.parse(javaClass.getResource("/market_sample.json")!!.readText())
    private val items = catalog.extensions

    @Test fun sourcesComeFromTheListing() {
        val o = MarketFacets.origins(items)
        assertEquals(listOf("curated", "community", "discovered"), o.map { it.key })
        assertEquals(listOf("Checked by Yumeiro", "Community list", "Found on GitHub"), o.map { it.label })
        o.forEach { f -> assertEquals(items.filter { it.origin == f.key }.distinctBy { it.internalName.lowercase() }.size, f.count) }
        // One source only: nothing to choose, so no Source group.
        assertTrue(MarketFacets.origins(items.filter { it.origin == "discovered" }).isEmpty())
    }

    @Test fun repositoriesAreEveryRepoInTheListingMostFirst() {
        val r = MarketFacets.repositories(items)
        assertEquals(items.map { it.repoUrl.lowercase() }.toSet(), r.map { it.key }.toSet())
        assertEquals(r.sortedByDescending { it.count }.map { it.count }, r.map { it.count })
        assertTrue(r.all { it.label.isNotBlank() && it.count > 0 })
        // Following the source: only that source's repositories.
        val community = MarketFacets.repositories(items, "community")
        assertEquals(items.filter { it.origin == "community" }.map { it.repoUrl.lowercase() }.toSet(), community.map { it.key }.toSet())
    }

    @Test fun repositorySearchMatchesNameOrLink() {
        val r = MarketFacets.repositories(items)
        val target = r.last()
        val word = target.label.split(' ').first { it.length >= 3 }
        assertTrue(target in MarketFacets.search(r, word.lowercase()))
        assertTrue(MarketFacets.search(r, "zz-no-such-repo-zz").isEmpty())
        assertEquals(r, MarketFacets.search(r, "  "))
    }

    @Test fun aSourceShowsItsOwnCopyOncePerExtension() {
        val discovered = MarketFacets.bySource(items.asSequence(), "discovered") { it.primary }.toList()
        assertTrue(discovered.isNotEmpty() && discovered.all { it.origin == "discovered" })
        assertEquals(discovered.size, discovered.distinctBy { it.internalName.lowercase() }.size)
        // The slice really has extensions listed by more than one discovered repository.
        assertTrue(items.count { it.origin == "discovered" } > discovered.size)
        // No source: unchanged primary rule.
        assertEquals(items.filter { it.primary }, MarketFacets.bySource(items.asSequence(), null) { it.primary }.toList())
    }
}
