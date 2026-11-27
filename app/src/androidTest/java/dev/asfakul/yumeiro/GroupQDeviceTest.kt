/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.ui.screens.settings.AppearanceSettingsCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F12 (beta chapter): smaller text and corners app-wide, from one switch in Settings › Appearance. The real
 * Appearance card is drawn inside the real theme; a probe reads the drawn body text size and card corner, and a
 * real row title is measured. Compact must draw body text at ≤ 12 sp and card corners at ≤ 15 dp, Standard
 * the old 13 sp / 18 dp, and the row title must be shorter in Compact. Old code: no switch, 13 sp / 18 dp.
 * Log tag YuGroupQ.
 */
@RunWith(AndroidJUnit4::class)
class GroupQDeviceTest {
    @get:Rule val rule = createComposeRule()
    private fun log(s: String) = Log.i("YuGroupQ", s)
    @Volatile private var bodySp = 0f
    @Volatile private var cornerDp = 0f
    private var initial: Boolean? = null

    private fun switchOn(): Boolean? = rule.onAllNodesWithTag("toggle_compact_ui", true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ToggleableState)?.let { it == ToggleableState.On }

    private fun setSwitch(on: Boolean) {
        if (switchOn() == on) return
        rule.onNodeWithTag("toggle_compact_ui", true).performScrollTo().performClick()
        rule.waitUntil(5_000) { switchOn() == on }
        rule.waitForIdle()
    }

    private fun titleHeight(): Int = rule.onAllNodesWithText("Japanese accents", useUnmergedTree = true).fetchSemanticsNodes().first().size.height

    @After fun tearDown() { initial?.let { runCatching { setSwitch(it) } } }

    @Test fun a_compactTextAndCornersFromOneSwitch() {
        // Compact applies only at Android's normal font sizes; the emulators run at 1.0.
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        rule.setContent {
            val compact = dev.asfakul.yumeiro.ui.theme.UiScale.compact
            val base = LocalDensity.current
            val scaled = androidx.compose.runtime.remember(compact, base) {
                val f = dev.asfakul.yumeiro.ui.theme.UiScale.wrap(ctx).resources.configuration.fontScale
                androidx.compose.ui.unit.Density(base.density, f)
            }
            CompositionLocalProvider(LocalTvMode provides Edition.isTv, LocalDensity provides scaled) {
                MTPlayerTheme {
                    val d = LocalDensity.current
                    // What is drawn: the theme size through the font scale of the window (the compact wrap of the
                    // activity context in the app; the same wrap is applied here around the real card).
                    val body = with(d) { MaterialTheme.typography.bodyMedium.fontSize.toPx() / density }
                    val corner = (MaterialTheme.shapes.medium as? RoundedCornerShape)?.topStart?.toPx(Size(1000f, 1000f), d)?.let { it / d.density } ?: -1f
                    SideEffect { bodySp = body; cornerDp = corner }
                    FirstRun.AppBackground { Column(Modifier.verticalScroll(rememberScrollState())) { AppearanceSettingsCard() } }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Japanese accents", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        initial = switchOn()
        log("GROUPQ a initial=$initial body=$bodySp corner=$cornerDp tv=${Edition.isTv}")
        assertTrue("F12: Settings › Appearance has no 'Compact text and corners' switch (body=$bodySp sp, corner=$cornerDp dp)", initial != null)
        if (!Edition.isTv) assertTrue("F12: phones start compact", initial == true)

        setSwitch(true)
        val cBody = bodySp; val cCorner = cornerDp; val cTitle = titleHeight()
        FirstRun.shot("groupq_f12_compact")
        setSwitch(false)
        val sBody = bodySp; val sCorner = cornerDp; val sTitle = titleHeight()
        FirstRun.shot("groupq_f12_standard")
        log("GROUPQ a compact body=$cBody corner=$cCorner title=$cTitle · standard body=$sBody corner=$sCorner title=$sTitle")
        // The real app window: MainActivity's own font scale with compact on.
        val sys = ctx.resources.configuration.fontScale
        log("GROUPQ a system fontScale=$sys")
        val ok = cBody <= 12f && cCorner <= 15f && kotlin.math.abs(sBody - 13f * sys) < 0.05f && sCorner == 18f && cTitle < sTitle
        log("GROUPQ_RESULT a ${if (ok) "PASS" else "FAIL"}")
        assertTrue("F12: compact body $cBody sp ≤ 12, corner $cCorner dp ≤ 15", cBody <= 12f && cCorner <= 15f)
        assertTrue("F12: standard keeps 13 sp / 18 dp (got $sBody / $sCorner)", kotlin.math.abs(sBody - 13f * sys) < 0.05f && sCorner == 18f)
        assertTrue("F12: a real row title is shorter in compact ($cTitle vs $sTitle px)", cTitle < sTitle)
    }

    /** The real app window (MainActivity) draws its text through the compact font scale, dialogs and sheets included. */
    @Test fun b_appWindowUsesCompactScale() {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val ui = dev.asfakul.yumeiro.ui.theme.UiScale
        ui.init(ctx)
        val before = ui.compact
        val sys = ctx.resources.configuration.fontScale
        try {
            for (on in listOf(true, false)) {
                ui.set(ctx, on)
                androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { sc ->
                    var f = 0f
                    sc.onActivity { a -> f = a.resources.configuration.fontScale }
                    log("GROUPQ b compact=$on system=$sys window=$f")
                    val want = sys * ui.textFactor(sys, on)
                    assertTrue("F12: window font scale $f, want $want (compact=$on)", kotlin.math.abs(f - want) < 0.01f)
                }
            }
            log("GROUPQ_RESULT b PASS")
        } finally { ui.set(ctx, before) }
    }
}
