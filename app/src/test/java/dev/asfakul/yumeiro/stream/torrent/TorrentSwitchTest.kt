/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.stream.TorrentSettings
import dev.asfakul.yumeiro.ui.screens.settings.TorrServerCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B31-ON-1: the built-in engine is offered by default now that the device tests pass, but it plays nothing until
 * the user turns it on and accepts the privacy notice. A tap anywhere on the row asks first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TorrentSwitchTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun offeredByDefaultButOffUntilTheNoticeIsAccepted() {
        assertTrue("flag on by default", FeatureFlags.Flag.BUILTIN_TORRENT.default)
        TorrentSettings.init(ctx)
        TorrentSettings.setEngineAccepted(ctx, false)
        assertFalse("not playing torrents before the notice", TorrentStreams.allowed(TorrentStreams.offered(ctx), TorrentSettings.engineAccepted.value))
        rule.setContent { MTPlayerTheme { TorrServerCard() } }
        rule.onNodeWithText("Built-in torrent engine").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag("torrent_engine_notice").assertExists()
        assertFalse("asking is not accepting", TorrentSettings.engineAccepted.value)
        rule.onNodeWithTag("torrent_engine_accept").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue(TorrentSettings.engineAccepted.value)
        TorrentSettings.setEngineAccepted(ctx, false)
    }
}
