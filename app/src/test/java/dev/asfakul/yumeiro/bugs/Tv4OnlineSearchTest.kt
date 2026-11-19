package dev.asfakul.yumeiro.bugs

import dev.asfakul.yumeiro.ui.components.TopBarSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TV-4 (ADR-036, OMT-21 option B): a committed library search offers the same query as an online Yumix search. */
class Tv4OnlineSearchTest {
    @Test fun committedQueryIsOfferedOnline() {
        assertEquals("naruto", TopBarSearch.onlineQuery("  naruto ", "  naruto "))
    }
    @Test fun nothingWhileTypingOrBlankOrOneLetter() {
        assertNull(TopBarSearch.onlineQuery("nar", "")) // still typing: suggestions show instead
        assertNull(TopBarSearch.onlineQuery("   ", "   "))
        assertNull(TopBarSearch.onlineQuery("a", "a"))
    }
    @Test fun longQueriesAreCut() {
        assertEquals(100, TopBarSearch.onlineQuery("x".repeat(300), "x".repeat(300))!!.length)
    }
}
