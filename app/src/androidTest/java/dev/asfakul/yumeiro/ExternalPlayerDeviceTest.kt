/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.ExternalPlayerHost
import dev.asfakul.yumeiro.player.ExternalPlayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * F-07 device check, round trip through the real Android activity-result path: MainActivity's launcher
 * starts another app's player (the test APK's [StubPlayerActivity], separate process, MX Player's intent
 * API) with the extras Yumeiro builds, and the position it reports comes back to the caller.
 *  a. "Choose each time" (no app picked; the default and, before F-07-VIS, the only choice on Android 11+):
 *     the position must come back too (F-07-RES: MX returns a result only when asked with return_result).
 *  b. MX picked: headers arrive and the position comes back.
 */
@RunWith(AndroidJUnit4::class)
class ExternalPlayerDeviceTest {
    @get:Rule val hang = HangDumpRule()
    /**
     * F07-T3 (device run 44901993315): the app asks for POST_NOTIFICATIONS at start; the system dialog paused
     * MainActivity (12:13:16.232) and it never resumed, so the stub's result was never delivered to it. The
     * permission is granted before the activity starts, as in TvBug5ReproDeviceTest.
     */
    @get:Rule(order = 0) val grant = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val inst = InstrumentationRegistry.getInstrumentation()
                // F07-T4 (run 44903495726): the start-up request also asks for the media permissions; grant them all.
                listOf("android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE")
                    .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName, it) } }
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private fun log(s: String) = Log.i("F07", s)
    private val stub get() = ComponentName(InstrumentationRegistry.getInstrumentation().context.packageName, StubPlayerActivity::class.java.name)
    private val headers = mapOf("Referer" to "https://site.example/", "User-Agent" to "UA/1")

    private fun roundTrip(app: ExternalPlayers.App?): ExternalPlayers.Watched? {
        val extras = ExternalPlayers.extras(app, "Ep 1", 61_000L, headers, emptyList(), null)
        val intent = ExternalPlayers.toIntent(app, Uri.parse("https://site.example/v/master.m3u8"), "video/*", extras)
            .setPackage(null).setComponent(stub)
        val got = AtomicReference<ExternalPlayers.Watched?>(null)
        val done = CountDownLatch(1)
        var launched = false
        rule.runOnUiThread {
            assertTrue("MainActivity registered no launcher", ExternalPlayerHost.launcher != null)
            launched = ExternalPlayerHost.launch(rule.activity, intent, null) { w -> got.set(w); done.countDown() }
        }
        assertTrue("the hand-off did not start", launched)
        done.await(20, TimeUnit.SECONDS)
        rule.waitForIdle()
        log("F07_RESULT app=${app?.name ?: "chooser"} watched=${got.get()}")
        return got.get()
    }

    @Test fun a_chooseEachTimeGetsThePositionBack() {
        val w = roundTrip(null)
        assertTrue("no position came back from the player (\"Choose each time\")", w != null)
        assertEquals(66_000L, w!!.positionMs)
        assertEquals("headers did not reach the player", 60_000L, w.durationMs)
    }

    @Test fun b_mxPickedGetsHeadersAndThePositionBack() {
        val w = roundTrip(ExternalPlayers.App.MX)
        assertTrue("no position came back from MX", w != null)
        assertEquals(66_000L, w!!.positionMs)
        assertEquals("headers did not reach the player", 60_000L, w.durationMs)
    }
}
