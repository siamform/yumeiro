/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.player.VideoCodecSupport
import dev.asfakul.yumeiro.player.VideoCodecSupport.Level
import dev.asfakul.yumeiro.youtube.YouTubeStreams
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/** R15 Phase 2: the YouTube stream is chosen by what the device can decode, not just by height. */
class YouTubePickVideoTest {
    private fun stream(itag: Int, codec: String, height: Int): VideoStream {
        val item = ItagItem.getItag(itag).also { it.codec = codec; it.height = height; it.width = height * 16 / 9; it.fps = 30 }
        return VideoStream.Builder().setId(itag.toString()).setContent("https://example.invalid/$itag", true)
            .setMediaFormat(item.mediaFormat ?: MediaFormat.MPEG_4).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(true).setResolution(item.resolutionString ?: "${item.height}p").setItagItem(item).build()
    }

    private val avc1080 = stream(137, "avc1.640028", 1080)
    private val vp91080 = stream(248, "vp09.00.40.08", 1080)
    private val av11080 = stream(399, "av01.0.08M.08", 1080)
    private val vp92160 = stream(313, "vp09.00.51.08", 2160)
    private val av12160 = stream(401, "av01.0.12M.08", 2160)
    private val avc720 = stream(136, "avc1.4d401f", 720)

    /** A 2022 mid-range phone: AVC + VP9 in hardware up to 1080p, VP9 4K only in software, no AV1. */
    private fun midRange(s: VideoStream): Pair<String?, Level> {
        val mime = VideoCodecSupport.mimeFor(s.codec)
        val level = when (mime) {
            "video/avc" -> if (s.height <= 1080) Level.HARDWARE else Level.NONE
            "video/x-vnd.on2.vp9" -> if (s.height <= 1080) Level.HARDWARE else Level.SOFTWARE
            else -> Level.NONE
        }
        return mime to level
    }

    @Test fun choosingFourKOnAPhoneWithoutA4kDecoderPlaysTheBestHardwareStream() {
        val all = listOf(avc1080, vp91080, av11080, vp92160, av12160, avc720)
        val pick = YouTubeStreams.pickVideo(all, ::midRange)
        assertEquals(1080, pick?.height)
        assertEquals("avc1.640028", pick?.codec)
    }

    @Test fun av1IsNeverPickedWithoutADecoder() {
        val pick = YouTubeStreams.pickVideo(listOf(av11080, avc720), ::midRange)
        assertEquals(720, pick?.height)
    }

    @Test fun softwareOnlyDevicesStillGetAPicture() {
        val pick = YouTubeStreams.pickVideo(listOf(vp92160, av12160)) { s -> VideoCodecSupport.mimeFor(s.codec) to Level.SOFTWARE }
        assertEquals(2160, pick?.height)
        assertEquals("vp09.00.51.08", pick?.codec)
    }

    @Test fun flagshipGetsFourKInHardware() {
        val pick = YouTubeStreams.pickVideo(listOf(avc1080, vp92160, av12160)) { s -> VideoCodecSupport.mimeFor(s.codec) to Level.HARDWARE }
        assertEquals(2160, pick?.height)
        assertEquals("vp09.00.51.08", pick?.codec) // VP9 beats AV1 at equal hardware support (cheaper)
    }

    @Test fun mimeMapping() {
        assertEquals("video/avc", VideoCodecSupport.mimeFor("avc1.64001F"))
        assertEquals("video/av01", VideoCodecSupport.mimeFor("av01.0.05M.08"))
        assertEquals("video/x-vnd.on2.vp9", VideoCodecSupport.mimeFor("vp9"))
        assertEquals("video/hevc", VideoCodecSupport.mimeFor("hvc1.1.6.L93.B0"))
    }
}
