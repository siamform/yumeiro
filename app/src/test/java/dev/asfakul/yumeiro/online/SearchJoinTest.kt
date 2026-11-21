/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEED-4: extensions that load during a search join it; the ones already searching are not restarted. */
class SearchJoinTest {
    @Test fun firstStepStartsEverythingLoaded() {
        assertEquals(SearchJoin.Step(listOf("A", "B"), emptyList()), SearchJoin.plan(emptySet(), listOf("A", "B")))
    }

    @Test fun aLateExtensionJoinsWithoutRestartingTheOthers() {
        assertEquals(SearchJoin.Step(listOf("C"), emptyList()), SearchJoin.plan(setOf("A", "B"), listOf("A", "B", "C")))
    }

    @Test fun aRemovedExtensionIsDroppedAndNothingRestarts() {
        assertEquals(SearchJoin.Step(emptyList(), listOf("B")), SearchJoin.plan(setOf("A", "B"), listOf("A")))
    }

    @Test fun duplicateNamesStartOnce() {
        assertEquals(listOf("A"), SearchJoin.plan(emptySet(), listOf("A", "A")).start)
    }
}
