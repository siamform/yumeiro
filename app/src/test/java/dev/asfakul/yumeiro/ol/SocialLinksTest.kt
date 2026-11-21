/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.notify.SocialLinks
import dev.asfakul.yumeiro.ui.screens.settings.DeveloperCard
import dev.asfakul.yumeiro.ui.screens.settings.SocialMediaCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * OL-13 (owner 2026-11-22): About lists every official Yumeiro account and the developer's own ones, and each
 * button opens exactly the owner's link. 320 dp wide: the smallest phone, so nothing may be cut off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-xhdpi")
class SocialLinksTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun on() { FeatureFlags.set(app, FeatureFlags.Flag.SOCIAL_LINKS, true) }

    private fun opens(tag: String, url: String) {
        while (shadowOf(app).nextStartedActivity != null) Unit
        rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        rule.waitForIdle()
        val started: Intent? = shadowOf(app).nextStartedActivity
        assertNotNull("$tag opened nothing", started)
        assertEquals(tag, Intent.ACTION_VIEW, started!!.action)
        assertEquals(tag, url, started.dataString)
    }

    @Test fun ol13_theAccountsAreTheOwnersOnes() {
        assertEquals(
            listOf(
                "https://discord.gg/eqkNymUAMJ",
                "https://www.facebook.com/officialyumeiro",
                "https://www.instagram.com/officialyumeiro",
                "https://x.com/officialyumeiro",
                "https://www.tiktok.com/@officialyumeiro",
                "https://www.reddit.com/user/officialyumeiro",
            ),
            SocialLinks.yumeiro.map { it.url },
        )
        assertEquals(
            listOf("https://www.facebook.com/siam1p", "https://www.instagram.com/asfakul_siam", "https://t.me/siamorwho"),
            SocialLinks.developer.map { it.url },
        )
    }

    @Test fun ol13_communityCardOpensEveryYumeiroAccount() {
        rule.setContent { MTPlayerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { SocialMediaCard() } } }
        rule.waitForIdle()
        opens("social_telegram_channel", "https://t.me/yumeiroofficial")
        opens("social_discord", "https://discord.gg/eqkNymUAMJ")
        opens("social_facebook", "https://www.facebook.com/officialyumeiro")
        opens("social_instagram", "https://www.instagram.com/officialyumeiro")
        opens("social_x", "https://x.com/officialyumeiro")
        opens("social_tiktok", "https://www.tiktok.com/@officialyumeiro")
        opens("social_reddit", "https://www.reddit.com/user/officialyumeiro")
        opens("social_website", "https://yumeiro.click")
    }

    @Test fun ol13_developerCardOpensFacebookInstagramTelegram() {
        rule.setContent { MTPlayerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DeveloperCard() } } }
        rule.waitForIdle()
        opens("dev_facebook", "https://www.facebook.com/siam1p")
        opens("dev_instagram", "https://www.instagram.com/asfakul_siam")
        opens("dev_telegram", "https://t.me/siamorwho")
        opens("dev_github", "https://github.com/siamform")
    }
}
