/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.diagnostics.MainThreadWatchdog
import dev.asfakul.yumeiro.online.web.BrowserCheck
import dev.asfakul.yumeiro.service.VideoPlaybackService
import dev.asfakul.yumeiro.service.VideoSessionBridge
import dev.asfakul.yumeiro.startup.PrefsWarmup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R15, device run 43626650174: the app was killed by `ForegroundServiceDidNotStartInTimeException`
 * and frozen at start-up by WebView start and settings reads on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupGuardsTest {

    @Test fun videoServiceWithoutPlayerStopsAndIsNeverSticky() {
        VideoSessionBridge.player = null
        val ctl = Robolectric.buildService(VideoPlaybackService::class.java, Intent()).create()
        val rc = ctl.get().onStartCommand(null, 0, 1)
        assertEquals(Service.START_NOT_STICKY, rc)
        assertTrue("stopped itself", shadowOf(ctl.get()).isStoppedBySelf)
        ctl.destroy()
    }

    @Test fun estimatedAgentLooksLikeWebView() {
        val ua = BrowserCheck.estimatedAgent("14", "131.0.6778.39")
        assertTrue(ua, ua.contains("Android 14;") && ua.contains("; wv)") && ua.contains("Chrome/131.0.6778.39 Mobile"))
        // Unknown WebView version: still a valid agent.
        assertTrue(BrowserCheck.estimatedAgent("9", null).contains("Chrome/130.0.0.0"))
    }

    @Test fun workerNeverStartsWebViewForTheAgent() {
        BrowserCheck.init(ApplicationProvider.getApplicationContext())
        var got: String? = null
        val t = Thread { got = BrowserCheck.userAgent() }
        t.start(); t.join(2_000)
        assertTrue("returned without waiting for the main thread", got != null && got!!.startsWith("Mozilla/5.0"))
    }

    @Test fun firstFrameSettingsAreWarmedUp() {
        for (name in listOf("mt_appearance", "mt_player_music_prefs", "mt_player_video_prefs", "mt_player_eq_prefs", "scrobble")) {
            assertTrue(name, name in PrefsWarmup.FIRST_FRAME)
        }
    }

    @Test fun stallSamplesNameOurFrames() {
        val trace = arrayOf(
            StackTraceElement("androidx.compose.runtime.SlotWriter", "close", "SlotTable.kt", 1),
            StackTraceElement("dev.asfakul.yumeiro.ui.screens.HomeScreenKt", "HomeFeed", "HomeScreen.kt", 210),
            StackTraceElement("dev.asfakul.yumeiro.online.ExtensionGuard", "install\$lambda\$1", "ExtensionGuard.kt", 83),
        )
        assertEquals("HomeScreenKt.HomeFeed:210", MainThreadWatchdog.appFrames(trace))
    }
}
