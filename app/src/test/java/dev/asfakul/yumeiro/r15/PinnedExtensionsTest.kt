/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.online.PinnedExtensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R15 Phase 5: favourite extensions come first, in pin order, and the list is bounded. */
class PinnedExtensionsTest {
    @Test fun pinnedComeFirstInPinOrder() {
        val names = listOf("A", "B", "C", "D")
        assertEquals(listOf("C", "A", "B", "D"), PinnedExtensions.pinnedFirst(names, listOf("C", "A")))
        // A pinned extension that is off (not in names) is not shown.
        assertEquals(listOf("B", "A", "C", "D"), PinnedExtensions.pinnedFirst(names, listOf("Z", "B")))
    }

    @Test fun toggleAddsRemovesAndCaps() {
        assertEquals(listOf("A"), PinnedExtensions.toggled(emptyList(), "A"))
        assertEquals(emptyList<String>(), PinnedExtensions.toggled(listOf("A"), "A"))
        val full = (1..PinnedExtensions.MAX).map { "E$it" }
        assertNull(PinnedExtensions.toggled(full, "new"))
        assertEquals(full - "E1", PinnedExtensions.toggled(full, "E1"))
    }

    @Test fun encodingRoundTripsAndIgnoresJunk() {
        val pins = listOf("Anime World", "Drama · ONShort")
        assertEquals(pins, PinnedExtensions.decode(PinnedExtensions.encode(pins)))
        assertEquals(listOf("A"), PinnedExtensions.decode("A\n\nA\n "))
        assertEquals(emptyList<String>(), PinnedExtensions.decode(null))
    }
}
