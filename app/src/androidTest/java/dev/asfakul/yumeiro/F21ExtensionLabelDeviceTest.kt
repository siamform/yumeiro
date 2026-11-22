/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.online.ExtensionLabels
import dev.asfakul.yumeiro.ui.screens.online.ExtensionLabelText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-21 (EXT-LABELS): the real two-line extension label on a real device, at the narrow width of a
 * phone row and at font scale 2.0 (largest accessibility size).
 *
 * Two same-name extensions from different repos must stay told apart: each keeps its language chip
 * on screen (inside the row, not squeezed away by the long name) and its second line stays one line.
 * Log tag YuF21: F21_BOUNDS.
 */
@RunWith(AndroidJUnit4::class)
class F21ExtensionLabelDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val a = ExtensionLabels.build("HiAnime Super Long Extension Name For Testing", "en-US", 3, "CloudStream", "Recloudstream Official Repository")
    private val b = ExtensionLabels.build("HiAnime Super Long Extension Name For Testing", "hi", 1, "Aniyomi", "Keiyoushi")

    @Test fun twoSameNameExtensionsKeepChipAndOneLineAtFontScaleTwo() {
        assertTrue("flag f21_extension_labels must be on", FeatureFlags.isOn(FeatureFlags.Flag.EXTENSION_LABELS))
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                Column {
                    Box(Modifier.width(220.dp).testTag("row_a")) { ExtensionLabelText(a, Color.White, Color.Gray) }
                    Box(Modifier.width(220.dp).testTag("row_b")) { ExtensionLabelText(b, Color.White, Color.Gray) }
                }
            }
        }
        compose.waitForIdle()
        val chips = compose.onAllNodesWithTag("ext_lang_chip", useUnmergedTree = true).fetchSemanticsNodes()
        val lines = compose.onAllNodesWithTag("ext_label_line", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("both rows show a language chip", 2, chips.size)
        assertEquals("both rows show a second line", 2, lines.size)
        listOf("row_a", "row_b").forEachIndexed { i, tag ->
            val row = compose.onNodeWithTag(tag).getBoundsInRoot()
            val chip = chips[i].boundsInRoot
            val line = lines[i].boundsInRoot
            Log.i("YuF21", "F21_BOUNDS $tag row=$row chip=$chip line=$line")
            with(compose.density) {
                assertTrue("$tag chip has width", chip.width > 4f)
                assertTrue("$tag chip inside the row", chip.right <= row.right.toPx() + 0.5f)
                // 12sp at font scale 2 = 24sp of text: one line is about 28sp tall, two lines about 56sp.
                // 42sp-at-scale-2 (= 42 × density px) sits between them.
                assertTrue("$tag second line is one line (h=${line.height})", line.height < 42f * density)
            }
        }
        assertTrue("labels differ by language", a.lang != b.lang)
        assertTrue("labels differ by line", a.line != b.line)
    }
}
