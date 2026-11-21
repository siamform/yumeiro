/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.VolumeBoost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-06 device check: the volume boost binds a real [LoudnessEnhancer] on a real audio session (the
 * emulator's audio framework), sets the gain the bar shows, never does that work on the main thread,
 * and frees the effect on reset. Gain is read back from the platform effect, not from our own state.
 */
@RunWith(AndroidJUnit4::class)
class VolumeBoostDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("F06", s)

    private fun enhancer(): LoudnessEnhancer? = VolumeBoost::class.java.getDeclaredField("enhancer").let { it.isAccessible = true; it.get(VolumeBoost) as LoudnessEnhancer? }

    private fun waitFor(ms: Long, what: String, ok: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) { if (runCatching(ok).getOrDefault(false)) return; Thread.sleep(20) }
        throw AssertionError("$what not reached in ${ms}ms")
    }

    @After fun tearDown() { VolumeBoost.reset() }

    @Test fun boostBindsTheRealEffectOffMainAndReleases() {
        val am = ctx.getSystemService(AudioManager::class.java)
        val session = am.generateAudioSessionId()
        assertTrue("no audio session from AudioManager: $session", session > 0)

        // Called from the main thread the way the player's volume bar calls it: must return at once.
        var callMs = 0L
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = SystemClock.elapsedRealtime(); VolumeBoost.set(session, 1f); callMs = SystemClock.elapsedRealtime() - t
        }
        log("set(1f) on main returned in ${callMs}ms")
        assertTrue("VolumeBoost.set blocked the main thread ${callMs}ms", callMs < 50)

        waitFor(5_000, "effect bound") { enhancer() != null || !VolumeBoost.supported.value }
        assertTrue("LoudnessEnhancer unavailable on this device", VolumeBoost.supported.value)
        waitFor(5_000, "gain 1000 mB") { enhancer()!!.targetGain == VolumeBoost.MAX_GAIN_MB.toFloat() && enhancer()!!.enabled }
        val e = enhancer()!!
        log("F06_RESULT bound session=$session gain=${e.targetGain} enabled=${e.enabled}")
        assertEquals(200, VolumeBoost.percent(15, 15, 1f))

        VolumeBoost.set(session, 0.5f)
        waitFor(5_000, "gain 500 mB") { enhancer()!!.targetGain == 500f }

        VolumeBoost.set(session, 0f)
        waitFor(5_000, "effect disabled at 0") { enhancer()?.enabled == false }

        VolumeBoost.reset()
        waitFor(5_000, "effect released on reset") { enhancer() == null }
        assertEquals(0f, VolumeBoost.boost.value)
        log("F06_RESULT PASS released")
    }
}
