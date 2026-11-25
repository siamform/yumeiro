/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PictureLook
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.ui.screens.video.Anime4kSheet
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A4K-3: the real in-player sheet on the device. Presets, picture controls stepped with the D-pad (the TV path),
 * "?" help, the Anime4K filter section, "This title only" and Reset all must change what the player reads
 * ([PictureLook.live] every frame, saved profiles on lift). Log tag YuPictureSheet; PICTURE_SHEET_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PictureSheetDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var previews = 0
    private var rebuilds = 0
    private var a4kWas = false

    @Before fun setUp() {
        a4kWas = VideoPrefs.anime4k.value
        PictureLook.init(ctx); PictureLook.resetAll(ctx); PictureLook.resetForTests(); PictureLook.init(ctx)
        VideoPrefs.setAnime4k(ctx, true)
        rule.setContent {
            MTPlayerTheme(Palettes.Dark) {
                Anime4kSheet(onChanged = { rebuilds++ }, onDismiss = {}, titleKey = "frieren", onPreview = { previews++ })
            }
        }
        rule.waitForIdle()
    }

    @After fun tearDown() { PictureLook.resetAll(ctx); PictureLook.resetForTests(); VideoPrefs.setAnime4k(ctx, a4kWas) }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true).performScrollTo()

    @Test fun sheetDrivesWhatThePlayerReads() {
        // Preset → saved globally and live for the shader.
        tag("pic_look_VIVID").performClick(); rule.waitForIdle()
        assertEquals(PictureLook.Look.VIVID, PictureLook.global.value.look)
        assertEquals(PictureLook.resolve(PictureLook.Profile(PictureLook.Look.VIVID)), PictureLook.live.grade)
        assertTrue("paused frame redraw requested", previews >= 1)

        // TV: D-pad right twice on Brightness = +2 steps, value text follows, label turns Custom (based on Vivid).
        tag("pic_brightness").requestFocus()
        tag("pic_brightness").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        tag("pic_brightness").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(0.04f, PictureLook.global.value.controls.brightness, 1e-4f)
        tag("pic_brightness_value").assertTextEquals("+4")
        tag("pic_active").assertTextEquals("Now: Custom (based on Vivid)")
        assertEquals(PictureLook.resolve(PictureLook.global.value), PictureLook.live.grade)
        // Per-item reset.
        tag("pic_brightness_reset").performClick(); rule.waitForIdle()
        assertEquals(0f, PictureLook.global.value.controls.brightness, 0f)

        // Video filter pill.
        tag("pic_filter_SEPIA").performClick(); rule.waitForIdle()
        assertEquals(PictureLook.VideoFilter.SEPIA, PictureLook.live.grade.filter)

        // "?" help opens and closes.
        tag("a4k_threshold_help").performClick(); rule.waitForIdle()
        rule.onNodeWithText("How strong an edge must be", substring = true).assertExists()
        rule.onNodeWithTag("a4k_threshold_help_ok").performClick(); rule.waitForIdle()

        // Filter section: D-pad on Threshold, Iterations rebuilds the effect list.
        tag("a4k_threshold").requestFocus()
        tag("a4k_threshold").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(0.25f, PictureLook.global.value.tuning.threshold, 1e-4f)
        assertEquals(0.025f, PictureLook.live.tuning.gateLow, 1e-4f)
        val before = rebuilds
        tag("a4k_iterations").requestFocus()
        tag("a4k_iterations").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(2, PictureLook.global.value.tuning.iterations)
        assertTrue("iterations rebuild the chain", rebuilds > before)

        // This title only: the show gets its own preset, other videos keep the main one.
        tag("pic_scope").performClick(); rule.waitForIdle()
        assertTrue(PictureLook.hasOverride("frieren"))
        tag("pic_look_COOL").performClick(); rule.waitForIdle()
        assertEquals(PictureLook.Look.COOL, PictureLook.profileFor("frieren").look)
        assertEquals(PictureLook.Look.VIVID, PictureLook.profileFor("dune").look)

        // Reset all (with confirmation) → everything default, live picture neutral.
        tag("pic_reset_all").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("pic_reset_all_ok").performClick(); rule.waitForIdle()
        assertTrue(PictureLook.profileFor("frieren").isDefault)
        assertTrue(PictureLook.profileFor("dune").isDefault)
        assertFalse(PictureLook.hasOverride("frieren"))
        assertTrue(PictureLook.live.grade.neutral)
        Log.w("YuPictureSheet", "PICTURE_SHEET_RESULT PASS previews=$previews rebuilds=$rebuilds")
    }
}
