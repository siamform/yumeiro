/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.Instrumentation
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.settings.DeveloperCard
import dev.asfakul.yumeiro.ui.screens.settings.SocialMediaCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OL-13 on a real phone / TV: About's Community and Developer cards show every account button, each one on screen
 * (after scrolling) and each tap starts a VIEW of the owner's link. Taps are caught by an ActivityMonitor, so no
 * browser or social app is needed on the emulator. Log tag YuSocialLinks, SOCIAL_CASE.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26) // ActivityMonitor.onStartActivity
class SocialLinksDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    @Volatile private var captured: Intent? = null
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW) return null
            captured = intent
            return Instrumentation.ActivityResult(0, null)
        }
    }.also { inst.addMonitor(it) }

    @After fun tearDown() { inst.removeMonitor(monitor) }

    private fun opens(tag: String, url: String) {
        captured = null
        rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { captured != null }
        val i = captured
        Log.i("YuSocialLinks", "SOCIAL_CASE $tag -> ${i?.dataString}")
        assertNotNull("$tag opened nothing", i)
        assertEquals(tag, url, i!!.dataString)
    }

    @Test fun everyAccountButtonShowsAndOpensItsLink() {
        FeatureFlags.set(ctx, FeatureFlags.Flag.SOCIAL_LINKS, true)
        rule.setContent { MTPlayerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DeveloperCard(); SocialMediaCard() } } }
        rule.waitForIdle()
        opens("dev_facebook", "https://www.facebook.com/siam1p")
        opens("dev_instagram", "https://www.instagram.com/asfakul_siam")
        opens("dev_telegram", "https://t.me/siamorwho")
        opens("social_telegram_channel", "https://t.me/yumeiroofficial")
        opens("social_discord", "https://discord.gg/eqkNymUAMJ")
        opens("social_facebook", "https://www.facebook.com/officialyumeiro")
        opens("social_instagram", "https://www.instagram.com/officialyumeiro")
        opens("social_x", "https://x.com/officialyumeiro")
        opens("social_tiktok", "https://www.tiktok.com/@officialyumeiro")
        opens("social_reddit", "https://www.reddit.com/user/officialyumeiro")
        opens("social_website", "https://yumeiro.click")
        Log.i("YuSocialLinks", "SOCIAL_RESULT PASS")
    }
}
