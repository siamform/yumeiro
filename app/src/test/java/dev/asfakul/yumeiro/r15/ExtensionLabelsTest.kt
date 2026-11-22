/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.asfakul.yumeiro.online.ExtensionLabels
import dev.asfakul.yumeiro.ui.screens.online.SourceFilterSheet
import dev.asfakul.yumeiro.ui.screens.online.SourceStatus
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-21: two extensions called "HDHub4u" (one Hindi from Phisher Repo, one English from another repo) looked the
 * same in the market, the installed list and "Show videos from". Every row now carries a language chip and
 * "N sources • ecosystem • repo".
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h740dp")
class ExtensionLabelsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lineHasCountEcosystemAndRepoInOrder() {
        val l = ExtensionLabels.build("HDHub4u", "hi", 3, "CloudStream", "Phisher Repo", listOf("v12"))
        assertEquals("HDHub4u", l.title)
        assertEquals("HI", l.lang)
        assertEquals("3 sources • CloudStream • Phisher Repo • v12", l.line)
    }

    @Test fun oneSourceIsSingularAndUnknownPartsAreLeftOut() {
        assertEquals("1 source • CloudStream", ExtensionLabels.build("X", null, 1, "CloudStream", "  ").line)
        assertEquals("CloudStream • Repo", ExtensionLabels.build("X", null, 0, "CloudStream", "Repo").line)
        assertEquals("Nuvio", ExtensionLabels.build("X", null, null, "Nuvio", "nuvio").line)
        assertEquals("", ExtensionLabels.build("X", null, null, null, null).line)
    }

    @Test fun languageCodes() {
        assertEquals("EN", ExtensionLabels.langCode("en-US"))
        assertEquals("PT", ExtensionLabels.langCode("pt_BR"))
        assertEquals("MULTI", ExtensionLabels.langCode("mul"))
        assertNull(ExtensionLabels.langCode("Hindi dubbed"))
        assertNull(ExtensionLabels.langCode(null))
    }

    @Test fun filterSheetRowsShowChipAndLineAndLongNamesStayOneLine() {
        val long = "A very long extension name that will never fit on a phone row at all"
        val labels = mapOf(
            "HDHub4u" to ExtensionLabels.build("HDHub4u", "hi", 3, "CloudStream", "Phisher Repo"),
            long to ExtensionLabels.build(long, "en", 1, "CloudStream", "Some Really Long Repository Name For Testing"),
        )
        rule.setContent {
            MTPlayerTheme {
                SourceFilterSheet(names = labels.keys.toList(), selected = null, status = { SourceStatus.Ready(120) }, onPick = {}, onDismiss = {}, details = labels::get)
            }
        }
        rule.waitForIdle()
        val chips = rule.onAllNodesWithTag("ext_lang_chip", true).fetchSemanticsNodes()
        assertEquals(2, chips.size)
        val lines = rule.onAllNodesWithTag("ext_label_line", true)
        lines[0].assertTextEquals("120 titles on its home • 3 sources • CloudStream • Phisher Repo")
        // The chip stays visible next to a long name (the name ellipsizes, the chip keeps its width).
        val row = rule.onNodeWithTag("online_filter_$long", true).fetchSemanticsNode().boundsInRoot
        chips.forEach { assertEquals(true, it.boundsInRoot.right <= row.right + 1f && it.boundsInRoot.width > 0f) }
    }
}
