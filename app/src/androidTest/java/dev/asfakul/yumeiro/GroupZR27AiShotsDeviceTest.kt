/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.intel.ai.AiPreset
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.online.anticipation.AnticipationPrefs
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.intel.AssistantScreen
import dev.asfakul.yumeiro.ui.screens.settings.AiSettingsScreen
import dev.asfakul.yumeiro.ui.screens.settings.QualityAndAudioCard
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP Z (beta r27, B20–B23) captures for the mockups, dark theme, phone and TV layout: Settings › Yumeiro AI with a
 * provider picked (no key typed, nothing sent), the Assistant before AI is set up, and the next-episode switches in
 * Settings › Quality & audio. Only real screens, no made-up answers. Shots r27_<form>_<name>. Log tag YuR27Shots.
 */
@RunWith(AndroidJUnit4::class)
class GroupZR27AiShotsDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tv get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, ok: () -> Boolean) = runCatching { rule.waitUntil(ms) { runCatching(ok).getOrDefault(false) }; true }.getOrDefault(false)
    private var modeBefore: ThemeMode? = null
    private var prepBefore = true
    private var askBefore = true

    private fun snap(name: String, shows: Boolean) {
        SystemClock.sleep(600); rule.waitForIdle()
        FirstRun.shot("r27_${form}_$name")
        Log.i("YuR27Shots", "R27_SHOT ${form}_$name ${if (shows) "ok" else "missing"}")
        assertTrue("capture $name does not show what it claims", shows)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalTvMode provides tv) {
            MTPlayerTheme { Box(Modifier.fillMaxSize().background(DarkBackground)) { content(); AppMessageHost() } }
        }
    }

    @Before fun setUp() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value
        ThemeSettings.setMode(ctx, ThemeMode.DARK)
        AnticipationPrefs.init(ctx)
        prepBefore = AnticipationPrefs.enabled.value; askBefore = AnticipationPrefs.askBeforeAlternative.value
        AiSettings.clear(ctx)
    }

    @After fun tearDown() {
        runCatching { AiSettings.clear(ctx) }
        runCatching { AnticipationPrefs.setEnabled(ctx, prepBefore); AnticipationPrefs.setAskBeforeAlternative(ctx, askBefore) }
        modeBefore?.let { m -> runCatching { ThemeSettings.setMode(ctx, m) } }
    }

    @Test fun a_aiSettingsWithAProvider() {
        AiSettings.setProvider(ctx, AiPreset.GEMINI)
        show { AiSettingsScreen(onBack = {}) }
        assertTrue(waitFor(20_000) { has("ai_settings") && has("ai_provider_note") })
        snap("ai_settings", has("ai_provider_gemini") && has("ai_get_key"))
    }

    @Test fun b_assistantBeforeSetup() {
        show { AssistantScreen(initialCategory = null, onBack = {}, onOpenSettings = {}, onOpenRec = {}) }
        snap("assistant_setup", waitFor(20_000) { has("assistant_setup") && has("assistant_setup_open") })
    }

    @Test fun c_nextEpisodeSwitches() {
        AnticipationPrefs.setEnabled(ctx, true); AnticipationPrefs.setAskBeforeAlternative(ctx, true)
        show { Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) { QualityAndAudioCard() } }
        assertTrue(waitFor(20_000) { has("qa_prepare_upcoming") })
        runCatching { rule.onNodeWithTag("qa_ask_alternative", true).performScrollTo() }
        snap("next_episode", has("qa_prepare_upcoming") && has("qa_ask_alternative"))
    }
}
