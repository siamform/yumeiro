/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.DynamicRangeCompressor
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.Preset
import dev.asfakul.yumeiro.ui.screens.video.DrcCustomSliders
import dev.asfakul.yumeiro.ui.screens.video.DrcPresetRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group Y (B8): the compressor controls as they appear in Settings → Video player and the player's Audio dialog.
 * Every preset chip is there and switches the live values; Manual shows all six values (knee is new) and keeps the
 * person's own; Save as preset stores them under a name that survives a restart; Delete removes it; Reset is Night.
 * Log tag YuGroupY: GY_RESULT_F PASS. Shots gy_f_presets, gy_f_saved.
 */
@RunWith(AndroidJUnit4::class)
class GroupYDrcDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = ctx.getSharedPreferences("yumeiro_drc", Context.MODE_PRIVATE)
    private val drc = DynamicRangeCompressor
    // By name, so this test also runs (and fails) on the code before Group Y, which lacks these presets.
    private fun preset(name: String) = Preset.entries.firstOrNull { it.name == name }
    private val voices get() = preset("DIALOGUE")?.settings

    @Before fun setUp() { prefs.edit().clear().commit(); drc.reloadForTest(ctx) }
    @After fun tearDown() { drc.setPreset(ctx, Preset.OFF); prefs.edit().clear().commit(); drc.reloadForTest(ctx) }

    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    // Dialog buttons have no scrolling parent; only nodes in the page are scrolled into view first.
    private fun tap(tag: String) {
        val n = compose.onNodeWithTag(tag, true)
        runCatching { n.performScrollTo() }
        // TV has no touch screen: the remote's OK is the node's click action (a touch can land on the rail).
        if (Edition.isTv) n.performSemanticsAction(SemanticsActions.OnClick) else n.performClick()
        compose.waitForIdle()
    }
    private fun text(tag: String): String = runCatching {
        compose.onAllNodesWithTag(tag, true)[0].fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
    }.getOrDefault("?")
    private fun waitFor(what: String, ms: Long = 6_000, ok: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { compose.waitForIdle(); if (ok()) return; SystemClock.sleep(100) }
        throw AssertionError("timed out: $what")
    }

    @Test fun f_presetsManualSaveDeleteReset() {
        compose.setContent {
            FirstRun.AppBackground {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { DrcPresetRow(); DrcCustomSliders() }
            }
        }
        waitFor("preset row") { has("drc_row") }
        val chips = Preset.entries.map { "drc_preset_${it.name.lowercase()}" }
        val missing = chips.filterNot { has(it) }
        Log.i("YuGroupY", "GY_F missing chips=$missing")
        assertTrue("every preset chip, Manual included, from the start: missing $missing", missing.isEmpty())

        tap("drc_preset_dialogue")
        assertEquals(voices, drc.active)
        waitFor("knee slider") { has("drc_knee") }
        assertTrue("knee shows Voices' value: ${text("drc_knee_value")}", text("drc_knee_value").startsWith("${voices!!.kneeDb.toInt()} dB"))
        SystemClock.sleep(300); compose.waitForIdle(); FirstRun.shot("gy_f_presets")

        // Knee is now a control of its own.
        compose.onAllNodesWithTag("drc_knee", true)[0].performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        compose.waitForIdle()
        assertEquals(Preset.CUSTOM, drc.preset.value)
        assertEquals(16f, drc.active!!.kneeDb)
        assertEquals("the rest stays Voices'", voices!!.thresholdDb, drc.active!!.thresholdDb)

        tap("drc_save")
        waitFor("name field") { has("drc_save_name") }
        compose.onNodeWithTag("drc_save_name", true).performTextReplacement("Late show")
        tap("drc_save_confirm")
        waitFor("saved chip") { has("drc_saved_Late show") && !has("drc_save_name") }
        assertTrue("summary names it: ${text("drc_summary")}", text("drc_summary").contains("Late show"))
        FirstRun.shot("gy_f_saved")

        tap("drc_preset_strong")
        assertEquals(Preset.STRONG.settings, drc.active)
        drc.reloadForTest(ctx)
        val stored = prefs.getString("saved", null).orEmpty()
        assertTrue("the saved preset is on disk: $stored", stored.contains("Late show") && stored.contains("\"knee\":16"))
        waitFor("saved chip after reload") { has("drc_saved_Late show") }
        tap("drc_saved_Late show")
        assertEquals(16f, drc.active!!.kneeDb)
        assertEquals(Preset.CUSTOM, drc.preset.value)

        tap("drc_delete_saved")
        waitFor("chip gone") { !has("drc_saved_Late show") }
        assertTrue("deleted on disk too", !prefs.getString("saved", null).orEmpty().contains("Late show"))

        tap("drc_reset")
        assertEquals(Preset.NIGHT, drc.preset.value)
        assertEquals(Preset.NIGHT.settings, drc.active)
        Log.i("YuGroupY", "GY_RESULT_F PASS")
    }
}
