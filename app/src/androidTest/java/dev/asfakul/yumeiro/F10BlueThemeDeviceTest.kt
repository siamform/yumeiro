/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.settings.AppearanceSettingsCard
import dev.asfakul.yumeiro.ui.theme.AccentTone
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-10 Blue theme: tapping "Ao blue" in Settings > Appearance must turn the theme that is actually drawn blue,
 * on the dark theme (light tone 0xFF7FB2FF) and on the light theme (deep tone 0xFF1F5FBF). The probe reads
 * LocalMtPalette inside MTPlayerTheme, so a saved setting that never reaches the screen fails.
 * Old code (accent not applied in MTPlayerTheme): the drawn accent stays the theme's own gold. Log tag YuF10.
 */
@RunWith(AndroidJUnit4::class)
class F10BlueThemeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val tv get() = Edition.isTv
    private fun log(s: String) = Log.i("YuF10", s)
    private var modeBefore = ThemeMode.SYSTEM
    private var accentBefore = AccentTone.GOLD
    @Volatile private var drawn: Color = Color.Unspecified
    @Volatile private var dark: Boolean? = null

    @Before fun setUp() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value; accentBefore = ThemeSettings.accent.value
    }

    @After fun tearDown() {
        ThemeSettings.setMode(ctx, modeBefore); ThemeSettings.setAccent(ctx, accentBefore)
    }

    private fun tapBlueOn(mode: ThemeMode, expected: Color) {
        ThemeSettings.setMode(ctx, mode); ThemeSettings.setAccent(ctx, AccentTone.GOLD)
        compose.waitForIdle()
        val gold = drawn
        compose.onNodeWithTag("accent_ao", true).performScrollTo().performClick()
        val ok = runCatching { compose.waitUntil(5_000) { drawn == expected }; true }.getOrDefault(false)
        log("F10_BLUE mode=$mode dark=$dark gold=$gold drawn=$drawn expected=$expected saved=${ThemeSettings.accent.value} tv=$tv")
        FirstRun.shot("f10_blue_${mode.name.lowercase()}")
        assertEquals("saved accent", AccentTone.AO, ThemeSettings.accent.value)
        assertEquals("drawn accent on $mode (ok=$ok)", expected, drawn)
    }

    @Test fun aoBlueIsDrawnOnDarkAndLightThemes() {
        compose.setContent {
            CompositionLocalProvider(LocalTvMode provides tv) {
                MTPlayerTheme {
                    val p = LocalMtPalette.current
                    SideEffect { drawn = p.accent; dark = p.isDark }
                    FirstRun.AppBackground { Column(Modifier.verticalScroll(rememberScrollState())) { AppearanceSettingsCard() } }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("accent_ao", true).fetchSemanticsNodes().isNotEmpty() }
        tapBlueOn(ThemeMode.DARK, Color(0xFF7FB2FF))
        tapBlueOn(ThemeMode.LIGHT, Color(0xFF1F5FBF))
        log("F10_BLUE_RESULT ok tv=$tv")
    }
}
