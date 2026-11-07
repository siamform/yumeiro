/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import dev.asfakul.yumeiro.ui.components.SearchSuggest
import dev.asfakul.yumeiro.ui.components.Suggestion
import dev.asfakul.yumeiro.ui.components.Suggestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestTest {
    @Test fun foldIgnoresAccentsCaseAndPunctuation() {
        assertEquals("pokemon x y", SearchSuggest.fold("Pokémon: X & Y!"))
    }

    @Test fun scoreOrdersPrefixThenWordThenContains() {
        assertEquals(0, SearchSuggest.score("one", "One Piece"))
        assertEquals(1, SearchSuggest.score("pie", "One Piece"))
        assertEquals(2, SearchSuggest.score("iec", "One Piece"))
        assertEquals(3, SearchSuggest.score("pi on", "One Piece"))
        assertNull(SearchSuggest.score("naruto", "One Piece"))
    }

    @Test fun rankPrefersPrefixRecentAndDropsDuplicates() {
        val c = listOf(
            Suggestion("Attack on Titan", Kind.TITLE),
            Suggestion("Titan Song", Kind.SONG),
            Suggestion("titan song", Kind.RECENT),
            Suggestion("Remember the Titans", Kind.VIDEO),
            Suggestion("titan", Kind.RECENT),
        )
        val r = SearchSuggest.rank("titan", c)
        assertEquals(listOf("titan song", "Attack on Titan", "Remember the Titans"), r.map { it.text })
        assertEquals(Kind.RECENT, r.first().kind)
    }

    @Test fun rankEmptyForBlankQuery() {
        assertTrue(SearchSuggest.rank("  ", listOf(Suggestion("x", Kind.SONG))).isEmpty())
    }
}
