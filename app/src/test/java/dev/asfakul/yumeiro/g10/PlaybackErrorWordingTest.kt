/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import androidx.media3.common.PlaybackException
import dev.asfakul.yumeiro.player.ErrorFacts
import dev.asfakul.yumeiro.player.PlaybackRecovery
import dev.asfakul.yumeiro.player.RecoveryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G10 ER-25: the last branch of [PlaybackRecovery.decide] called every error it didn't know an
 * "Unsupported video format … hardware decoders could not play this file" and told the user to switch
 * the Video decoder to Software. That is wrong for audio output failures (Bluetooth headset switched,
 * audio track init/write), for the player's own errors (timeout, runtime check, unspecified) and for the
 * four network-stream errors excluded from the retry branch (file not found, no permission, cleartext,
 * read position out of range). Changing the video decoder can't help any of them.
 * NewPipe v0.28.0 `Player.java` onPlayerError (lines 1450–1480) files FILE_NOT_FOUND, NO_PERMISSION,
 * CLEARTEXT and READ_POSITION_OUT_OF_RANGE as stream failures and TIMEOUT/UNSPECIFIED as reloads; the
 * media3 1.8.0 demo `PlayerActivity.PlayerErrorMessageProvider` names decoders only for a
 * DecoderInitializationException. Old code: every code below gets "Unsupported video format".
 */
class PlaybackErrorWordingTest {

    private fun decide(code: Int): RecoveryAction = PlaybackRecovery(now = { 0L }).decide(ErrorFacts(code, message = "x"), canRefresh = false, autoFormat = false)

    private val notDecoder = mapOf(
        "audio track init" to PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        "audio track write" to PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        "player timeout" to PlaybackException.ERROR_CODE_TIMEOUT,
        "runtime check" to PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        "unspecified" to PlaybackException.ERROR_CODE_UNSPECIFIED,
        "file not found" to PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        "no permission" to PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        "cleartext" to PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        "read position" to PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    )

    private fun words(a: RecoveryAction) = when (a) {
        is RecoveryAction.Fail -> a.title + " / " + a.message
        is RecoveryAction.Retry -> "retry: " + a.reason
        else -> a.toString()
    }

    @Test fun onlyDecoderErrorsAreCalledDecoderFailures() {
        notDecoder.forEach { (name, code) ->
            val w = words(decide(code))
            assertFalse("$name ($code) is called a video format failure: $w", "Unsupported video format" in w)
            assertFalse("$name ($code) gets the decoder tip: $w", PlaybackRecovery.DECODER_TIP in w || "hardware decoders" in w)
        }
    }

    @Test fun audioOutputFailureRetriesOnceThenSaysAudio() {
        val r = PlaybackRecovery(now = { 0L })
        val f = ErrorFacts(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
        assertTrue("first audio output failure should re-prepare", r.decide(f, canRefresh = false, autoFormat = false) is RecoveryAction.Retry)
        val last = r.decide(f, canRefresh = false, autoFormat = false)
        assertTrue("second audio output failure should fail with an audio message: ${words(last)}", last is RecoveryAction.Fail && "audio" in words(last).lowercase())
    }

    @Test fun decoderErrorsKeepTheDecoderTip() {
        val a = decide(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        assertTrue(a is RecoveryAction.Fail)
        assertEquals("Unsupported video format", (a as RecoveryAction.Fail).title)
        assertTrue(PlaybackRecovery.DECODER_TIP in a.message)
    }
}
