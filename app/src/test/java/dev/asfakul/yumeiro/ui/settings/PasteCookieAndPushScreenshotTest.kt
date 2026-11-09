/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.screens.settings.GroupLabel
import dev.asfakul.yumeiro.ui.screens.settings.GuideCard
import dev.asfakul.yumeiro.ui.screens.settings.PushAlertsCard
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import dev.asfakul.yumeiro.ui.screens.settings.YouTubeAccountScreen
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class PasteCookieAndPushScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pasteCookiePage() {
        rule.setContent { MTPlayerTheme { Column(Modifier.fillMaxSize().background(DarkBackground)) { YouTubeAccountScreen(onBack = {}) } } }
        rule.onNodeWithTag("yt_paste_cookie").assertIsDisplayed()
        save("settings_youtube_account")
        rule.onNodeWithTag("yt_paste_cookie").performClick()
        rule.onNodeWithTag("yt_paste_page").assertIsDisplayed()
        save("settings_youtube_paste")
        rule.onNodeWithTag("yt_cookie_input").performTextInput("VISITOR_INFO1_LIVE=v; PREF=x")
        rule.onNodeWithTag("yt_cookie_save").performClick()
        rule.onNodeWithTag("yt_cookie_error").assertIsDisplayed()
        save("settings_youtube_paste_error")
    }

    @Test fun pushAlertsCard() {
        rule.setContent {
            MTPlayerTheme {
                Column(
                    Modifier.fillMaxSize().background(DarkBackground).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GroupLabel("On-time alerts")
                    PushAlertsCard()
                    GuideCard(SetupGuides.unifiedPush, startExpanded = true)
                }
            }
        }
        rule.onNodeWithTag("toggle_push_alerts").assertIsDisplayed()
        save("settings_push_alerts")
    }
}
