/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.online.OnlineSearchScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP U (F14 BlackSource), entry and opt-in, through Search online as a user reaches it. Uses only APIs that existed
 * before the group, so it ran on the old code too (fail-before on `ab/groupu-before`). The search itself is
 * [GroupUBlackSourceFlowDeviceTest].
 * Log tag YuGroupU, result line GROUPU_a_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class GroupUBlackSourceDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun prefs() = ctx.getSharedPreferences("blacksource", Context.MODE_PRIVATE)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, what: () -> Boolean) = runCatching { compose.waitUntil(ms) { what() }; true }.getOrDefault(false)

    @Before fun setUp() { prefs().edit().clear().commit() }
    @After fun tearDown() { prefs().edit().clear().commit() }

    @Test fun a_openFromSearchAsksFirstAndRemembers() {
        compose.setContent { CompositionLocalProvider(LocalTvMode provides Edition.isTv) { MTPlayerTheme { OnlineSearchScreen(onBack = {}, onOpenTitle = {}, onOpenExtensions = {}) } } }
        compose.waitForIdle()
        val entry = waitFor(5_000) { has("online_search_blacksource") }
        Log.i("YuGroupU", "GROUPU_a entry=$entry")
        assertTrue("Search online has no BlackSource entry", entry)
        compose.onNodeWithTag("online_search_blacksource", true).performClick()
        val alert = waitFor(5_000) { has("blacksource_optin") }
        FirstRun.shot("groupu_optin")
        assertTrue("No opt-in alert about the extra time", alert)
        assertTrue("Opted in before accepting", !prefs().getBoolean("opted_in", false))
        compose.onNodeWithTag("blacksource_optin_accept", true).performClick()
        val screen = waitFor(5_000) { has("blacksource_screen") && !has("blacksource_optin") }
        val saved = prefs().getBoolean("opted_in", false)
        FirstRun.shot("groupu_screen")
        Log.i("YuGroupU", "GROUPU_a_RESULT ${if (screen && saved) "PASS" else "FAIL"} screen=$screen saved=$saved")
        assertTrue(screen); assertTrue("Opt-in not remembered", saved)
    }
}
