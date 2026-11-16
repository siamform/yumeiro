/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** G12: the debug decoder fault hook offers the fake decoder only for video and only when asked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DecoderFaultsTest {
    private fun info(name: String, mime: String) =
        MediaCodecInfo.newInstance(name, mime, mime, null, true, false, true, false, false)
    private val base = MediaCodecSelector { mime, _, _ -> listOf(info("c2.real.${mime.substringAfter('/')}", mime)) }
    private fun names(mime: String) = DecoderFaults.wrap(base).getDecoderInfos(mime, false, false).map { it.name }

    @After fun clear() { DecoderFaults.mode = DecoderFaults.Mode.NONE }

    @Test fun offModeLeavesTheListAlone() {
        assertEquals(listOf("c2.real.avc"), names(MimeTypes.VIDEO_H264))
    }

    @Test fun firstFailsPutsTheFakeBeforeTheRealDecoders() {
        DecoderFaults.mode = DecoderFaults.Mode.FIRST_VIDEO_FAILS
        assertEquals(listOf(DecoderFaults.FAKE_DECODER, "c2.real.avc"), names(MimeTypes.VIDEO_H264))
    }

    @Test fun allFailLeavesOnlyTheFake() {
        DecoderFaults.mode = DecoderFaults.Mode.ALL_VIDEO_FAIL
        assertEquals(listOf(DecoderFaults.FAKE_DECODER), names(MimeTypes.VIDEO_H264))
    }

    @Test fun audioIsNeverTouched() {
        DecoderFaults.mode = DecoderFaults.Mode.ALL_VIDEO_FAIL
        assertEquals(listOf("c2.real.mp4a-latm"), names(MimeTypes.AUDIO_AAC))
    }
}
