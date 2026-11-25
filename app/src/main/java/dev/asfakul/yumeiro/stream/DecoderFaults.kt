/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * G12 decoder fault injection (debug builds only; [YumeiroRenderersFactory] ignores it in release).
 * A video decoder fault cannot be forced on the emulators any other way. The fake decoder copies the
 * real one's capabilities (so the renderer ranks it first) under a name MediaCodec cannot open, so
 * its initialisation fails exactly like a broken vendor decoder:
 * - [Mode.FIRST_VIDEO_FAILS]: fake first, real decoders after it → the player must fall back.
 * - [Mode.ALL_VIDEO_FAIL]: only the fake → the player must show its error, not crash.
 * - [Mode.PHONE_ORDER_FAILS] (H-3): only the fake while the phone's own decoder order is in use; the real
 *   decoders once the order is software-first (Auto after its switch, or Software) → Auto must recover.
 */
@OptIn(UnstableApi::class)
object DecoderFaults {
    enum class Mode { NONE, FIRST_VIDEO_FAILS, ALL_VIDEO_FAIL, PHONE_ORDER_FAILS }

    const val FAKE_DECODER = "yumeiro.fault.decoder"

    /** Read when a player is built; tests set it before opening a video. */
    @Volatile var mode: Mode = Mode.NONE

    /** How many times the fake decoder was offered to a renderer (tests read it). */
    val offered = java.util.concurrent.atomic.AtomicInteger()

    internal fun wrap(base: MediaCodecSelector): MediaCodecSelector = MediaCodecSelector { mime, secure, tunneling ->
        val real = base.getDecoderInfos(mime, secure, tunneling)
        val m = mode
        if (m == Mode.NONE || !MimeTypes.isVideo(mime) || real.isEmpty()) return@MediaCodecSelector real
        if (m == Mode.PHONE_ORDER_FAILS && (DecoderAuto.softwareFirst ||
                dev.asfakul.yumeiro.player.VideoPrefs.decoder.value == dev.asfakul.yumeiro.player.VideoPrefs.Decoder.SOFTWARE)) return@MediaCodecSelector real
        offered.incrementAndGet()
        val r = real.first()
        val fake = MediaCodecInfo.newInstance(
            FAKE_DECODER, r.mimeType, r.codecMimeType, r.capabilities,
            r.hardwareAccelerated, r.softwareOnly, r.vendor, false, secure,
        )
        if (m == Mode.FIRST_VIDEO_FAILS) listOf(fake) + real else listOf(fake) // ALL_VIDEO_FAIL, PHONE_ORDER_FAILS
    }
}
