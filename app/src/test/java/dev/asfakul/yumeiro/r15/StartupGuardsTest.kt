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
        // R16: the third StrictMode pass (run 43683520404) added these; each one is named by a
        // DiskReadViolation on the main thread at start.
        for (name in listOf("mt_appearance", "mt_player_music_prefs", "mt_player_video_prefs", "mt_player_eq_prefs", "scrobble",
                "yu_nav", "setup_guide", "home_layout", "yt_topics", "search_recent", "notify_settings", "push_wake", "background_work")) {
            assertTrue(name, name in PrefsWarmup.FIRST_FRAME)
        }
    }

    @Test fun warmUpLoadsTheInboxOffTheMainThread() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        PrefsWarmup.start(app)
        val deadline = System.currentTimeMillis() + 5_000
        val field = dev.asfakul.yumeiro.notify.Inbox::class.java.getDeclaredField("file").apply { isAccessible = true }
        while (field.get(dev.asfakul.yumeiro.notify.Inbox) == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("inbox not loaded by the warm-up", field.get(dev.asfakul.yumeiro.notify.Inbox) != null)
    }

    @Test fun warmUpLoadsBookmarksDownloadsAndBackdropOffTheMainThread() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        PrefsWarmup.start(app)
        fun loaded(o: Any) = o::class.java.getDeclaredField("loaded").apply { isAccessible = true }.getBoolean(o)
        val objs = listOf<Any>(dev.asfakul.yumeiro.online.OnlineBookmarks, dev.asfakul.yumeiro.online.download.OnlineDownloads, dev.asfakul.yumeiro.ui.theme.BackdropSettings)
        val deadline = System.currentTimeMillis() + 5_000
        while (objs.any { !loaded(it) } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        for (o in objs) assertTrue("${o::class.simpleName} not loaded by the warm-up", loaded(o))
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
