package dev.asfakul.yumeiro.online

// F-01 (absorbed by F-16, docs/proposals/FEATURE-F-01-language-sub-dub-badges.md): tapping the Dub / Sub chip on a
// poster opens that title with the tapped version chosen. Fail-before: VersionHint does not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class F01TagPreselectTest {
    @Test fun dubChipWantsDubAndSubChipWantsSub() {
        assertEquals("dub", VersionHint.wantFor("Dub"))
        assertEquals("sub", VersionHint.wantFor("Sub"))
        assertEquals("dub", VersionHint.wantFor("Dub · Sub"))
        assertEquals("dub", VersionHint.wantFor("Hindi Dub"))
        assertNull(VersionHint.wantFor("Hindi"))
        assertNull(VersionHint.wantFor("Dual"))
    }

    @Test fun theHintPicksTheMatchingVersionAndFallsBack() {
        assertEquals("Dub", VersionHint.pick(listOf("Sub", "Dub"), "dub", fallback = "Sub"))
        assertEquals("Sub", VersionHint.pick(listOf("Sub", "Dub"), "sub", fallback = "Dub"))
        assertEquals("Sub", VersionHint.pick(listOf("Sub"), "dub", fallback = "Sub"))
        assertEquals("Sub", VersionHint.pick(listOf("Sub", "Dub"), null, fallback = "Sub"))
    }

    @Test fun theHintIsUsedOnceAndOnlyForThatTitle() {
        VersionHint.set("https://a/1", "dub")
        assertNull(VersionHint.take("https://a/2"))
        assertEquals("dub", VersionHint.take("https://a/1"))
        assertNull(VersionHint.take("https://a/1"))
    }
}
