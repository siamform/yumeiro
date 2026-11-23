/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UX-LIB-1 (theme probe 45145661829, Library on a 411 dp phone): the "on device" pill of the Liked songs card showed
 * "Unkn…" for an empty library, and any real size ("153.4 MB") is wider than the ~48 dp of text room the pill has.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryHeroStatTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyLibraryIsZeroNotUnknown() {
        assertEquals("0 MB", librarySize(0))
        assertEquals("1.0 GB", librarySize(1024L * 1024 * 1024))
    }

    @Test fun longSizeFitsTheQuarterPill() {
        // the pill width the Liked songs card gives the fourth stat on a 411 dp screen, measured on the probe shot (148 of 706 px)
        rule.setContent { MTPlayerTheme { Row { HeroStat("153.4 MB", "on device", Modifier.width(86.dp)) } } }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("153.4 MB").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("value is cut", layouts.single().hasVisualOverflow)
        assertFalse("value is cut", layouts.single().isLineEllipsized(0))
    }
}
