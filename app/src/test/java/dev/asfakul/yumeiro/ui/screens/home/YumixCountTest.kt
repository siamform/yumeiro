package dev.asfakul.yumeiro.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COUNT-1: Home said "Yumix · 1 sources" and "Extensions · 1 on" while Videos said "2 extensions on" for the same
 * phone (field probe 45325668234): Home counted extension packages, Videos counted feed sources. The Yumix tile now
 * shows the feed-source count (same as the Yumix header, which now says "sources"), the Extensions tile the packages.
 */
class YumixCountTest {
    @Test fun yumixTileUsesTheFeedSourceCount() {
        assertEquals("2 sources", yumixTileLabel(extensions = 1, sources = 2))
        assertEquals("1 source", yumixTileLabel(extensions = 3, sources = 1))
    }

    @Test fun noSourcesAsksToAddSome() {
        assertEquals("Add sources", yumixTileLabel(extensions = 1, sources = 0))
        assertEquals("Add sources", yumixTileLabel(extensions = 0, sources = -1))
    }

    @Test fun unknownSourceCountFallsBackToExtensions() {
        assertEquals("3 sources", yumixTileLabel(extensions = 3, sources = -1))
    }
}
