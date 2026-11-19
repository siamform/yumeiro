/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.ui.screens.stream.LiveTool
import dev.asfakul.yumeiro.ui.screens.stream.LiveToolsSheet
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G10 ER-28 (queued NB15-OB2): every Material bottom sheet wraps a non-null drag handle in a clickable,
 * focusable box with a "Drag handle" tooltip (androidx-main material3 BottomSheet.kt lines 359–399).
 * On a TV remote the first key press lands on that handle, the tooltip pops up, and OK on it dismisses
 * the sheet (Expanded → animateToDismiss) — the user's first OK closes the Tools sheet without doing
 * anything. A remote can't drag, so on TV the sheets must have no handle.
 *
 * Real key events (`input keyevent`) on the Live TV Tools sheet with LocalTvMode = true, so it runs on
 * phones and TVs alike. Old code: focus on the handle, tooltip "Drag handle" shown, OK closes the sheet.
 * Log tag YuSheetTv: SHEET_TV_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class SheetFocusTvDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun key(code: Int) { FirstRun.shell("input keyevent $code"); Thread.sleep(400); compose.waitForIdle() }

    @Test fun a_firstRemoteKeyLandsOnAToolNotTheDragHandle() {
        var dismissed = false
        var picked: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalTvMode provides true) {
                MTPlayerTheme {
                    Box(Modifier.fillMaxSize()) {
                        if (!dismissed) LiveToolsSheet(
                            listOf(
                                LiveTool("sheet_tv_tool_a", "Playlists", "2", Icons.Default.Movie, Color(0xFFD9668A)) { picked = "a" },
                                LiveTool("sheet_tv_tool_b", "Play Link", "", Icons.Default.Link, Color(0xFF4C8FD6)) { picked = "b" },
                            ),
                            onDismiss = { dismissed = true },
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) { has("live_tools_sheet") }
        Thread.sleep(1_000) // sheet slide-in
        key(20) // DPAD_DOWN: the first key press on a remote
        val focusedInSheet = compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("live_tools_sheet")), true).fetchSemanticsNodes().isNotEmpty()
        val focused = compose.onAllNodes(isFocused(), true).fetchSemanticsNodes()
            .joinToString("+") { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: it.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString().orEmpty() }
        val tooltip = compose.onAllNodesWithText("Drag handle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        FirstRun.shot("sheet_tv_after_down")
        key(23) // DPAD_CENTER
        val closedForNothing = dismissed && picked == null
        Log.i("YuSheetTv", "SHEET_TV_RESULT focused=[$focused] inSheet=$focusedInSheet tooltip=$tooltip dismissed=$dismissed picked=$picked")
        assertFalse("ER-28: the \"Drag handle\" tooltip shows on a TV sheet (focus on the handle)", tooltip)
        assertTrue("ER-28: the first remote key did not land inside the sheet's content (focused=[$focused])", focusedInSheet)
        assertFalse("ER-28: OK closed the sheet without running any tool", closedForNothing)
    }
}
