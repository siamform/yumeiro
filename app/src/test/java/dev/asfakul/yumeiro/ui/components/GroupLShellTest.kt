/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.screens.splash.SplashTiming
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Group L (Beta chapter): B2 hide-on-scroll chip bars, B6/F11 nav capsule and height, F4 splash mark
 * frames, B3 entrance quiet window. The device classes (GroupLShellDeviceTest) run the same on
 * phones and TV.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
class GroupLShellTest {
    @get:Rule val rule = createComposeRule()

    // ---- B2 ---------------------------------------------------------------------------------
    @Test fun stateConsumesOnlyTheBarsHeightAndSettles() {
        val s = HideOnScrollState()
        s.heightPx = 100
        assertEquals(-40f, s.consume(-40f), 0.01f)
        assertEquals("never more than the bar", -60f, s.consume(-500f), 0.01f)
        assertTrue(s.hidden)
        assertEquals("nothing left to hide", 0f, s.consume(-10f), 0.01f)
        assertEquals(30f, s.consume(30f), 0.01f)
        assertEquals(0.7f, s.fraction, 0.01f)
        s.show(); assertEquals(0f, s.offsetPx, 0f)
    }

    private fun page(reduced: Boolean = true) {
        rule.setContent {
            MTPlayerTheme {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    HideOnScrollColumn(Modifier.fillMaxSize(), header = {
                        Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Gray).testTag("chips"))
                    }) {
                        LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                            items(200) { i -> Text("Row $i", Modifier.fillMaxWidth().height(56.dp).testTag("row_$i")) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun chipBarHidesOnScrollDownAndReturnsOnScrollUp() {
        page()
        val listTop0 = rule.onNodeWithTag("list").getUnclippedBoundsInRoot().top
        assertEquals("starts shown", 0f, rule.onNodeWithTag("chips").getUnclippedBoundsInRoot().top.value, 0.5f)
        rule.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 400) }
        rule.waitForIdle()
        val hidden = rule.onNodeWithTag("chips").getUnclippedBoundsInRoot()
        assertTrue("bar slid away: ${hidden.top}", hidden.bottom.value <= 0.5f)
        assertTrue("the page grew into its space", rule.onNodeWithTag("list").getUnclippedBoundsInRoot().top < listTop0)
        rule.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 100f, endY = top + 400f, durationMillis = 300) }
        rule.waitForIdle()
        assertEquals("first scroll up brings it back", 0f, rule.onNodeWithTag("chips").getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    // ---- B6 / F11 ---------------------------------------------------------------------------
    @Test fun capsuleHoldsIconAndLabelAndBarIsShorter() {
        rule.setContent { MTPlayerTheme { MTBottomBar(currentRoute = bottomNavItems[1].route, onNavigateToRoute = {}) } }
        rule.waitForIdle()
        val cap = rule.onNodeWithTag("nav_capsule", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = rule.onNodeWithTag("nav_label_${bottomNavItems[1].testTag}", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val icon = rule.onNodeWithTag("nav_active_pill", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("label inside the capsule: cap=$cap label=$label", label.top >= cap.top - 0.5.dp && label.bottom <= cap.bottom + 0.5.dp)
        assertTrue("icon inside the capsule: cap=$cap icon=$icon", icon.top >= cap.top - 0.5.dp && icon.bottom <= cap.bottom + 0.5.dp)
        assertTrue("F11: bar at most 62 dp (was 68)", DockNavHeight <= 62.dp)
    }

    // ---- F4 ---------------------------------------------------------------------------------
    @Test fun splashMarkDrawsInThenRests() {
        val f0 = YuMarkFrame.drawIn(0f)
        assertEquals(0f, f0.moon, 0f); assertEquals(0f, f0.play, 0f)
        val mid = YuMarkFrame.drawIn(0.4f)
        assertTrue("crescent drawing, triangle not yet", mid.moon in 0.1f..0.99f && mid.play < 0.3f)
        val end = YuMarkFrame.drawIn(1f)
        assertEquals(1f, end.moon, 0f); assertEquals(1f, end.play, 0f); assertEquals(0f, end.glow, 0.001f)
        // Android 12+: starts and ends on the system splash's full mark.
        assertEquals(YuMarkFrame.press(0f).moon, 1f, 0f)
        assertEquals(1f, YuMarkFrame.press(0f).playScale, 0.001f)
        assertTrue("presses in the middle", YuMarkFrame.press(0.25f).playScale < 0.9f)
        assertEquals(1f, YuMarkFrame.press(1f).playScale, 0.001f)
        assertTrue(SplashTiming.hold(false, 30) < 900L && SplashTiming.hold(false, 34) < 900L)
        assertTrue(SplashTiming.hold(true, 34) < SplashTiming.hold(false, 34))
    }

    // ---- B3 ---------------------------------------------------------------------------------
    @Test fun noCardEntranceWhileAPageSlidesIn() {
        Entrance.resetForTest()
        assertTrue(Entrance.enabled(reduced = false, lowRam = false, now = 10_000L))
        Entrance.quiet(now = 10_000L)
        assertTrue("quiet during the push", !Entrance.enabled(reduced = false, lowRam = false, now = 10_000L + PageMotion.PUSH_MS))
        assertTrue("back afterwards", Entrance.enabled(reduced = false, lowRam = false, now = 10_000L + Entrance.QUIET_MS + 1))
        assertTrue("the quiet window covers the whole push", Entrance.QUIET_MS >= PageMotion.PUSH_MS)
        Entrance.resetForTest()
    }
}
