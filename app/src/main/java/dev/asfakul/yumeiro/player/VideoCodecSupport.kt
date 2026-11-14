/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * R15 Phase 2: can this device decode a video stream — and in hardware?
 *
 * The YouTube resolution switcher picked the tallest stream for the chosen height, which above
 * 1080p is VP9 or AV1 only. On phones without a hardware decoder for it (e.g. no AV1 on 2022
 * mid-range Snapdragons) the player fell back to a software decoder at 1440p/2160p: the playback
 * thread stalled, releasing the decoder timed out and the player showed "This video couldn't
 * play (timeout)". Streams are now ranked by what the device can really decode.
 */
object VideoCodecSupport {
    enum class Level { HARDWARE, SOFTWARE, NONE }

    private val cache = ConcurrentHashMap<String, Level>()

    /** RFC 6381 codec string ("avc1.640028", "vp09.00.40.08", "av01.0.08M.08") → MIME type. Pure. */
    fun mimeFor(codecs: String?): String? {
        val c = codecs?.trim()?.lowercase()?.substringBefore(',') ?: return null
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") -> "video/avc"
            c.startsWith("hev1") || c.startsWith("hvc1") -> "video/hevc"
            c.startsWith("vp09") || c == "vp9" -> "video/x-vnd.on2.vp9"
            c.startsWith("vp08") || c == "vp8" -> "video/x-vnd.on2.vp8"
            c.startsWith("av01") -> "video/av01"
            c.startsWith("dvh") -> "video/dolby-vision"
            else -> null
        }
    }

    /** Best decoder level for [mime] at [width]×[height] / [fps]. Unknown MIME = SOFTWARE (let the player try). */
    fun level(mime: String?, width: Int, height: Int, fps: Int): Level {
        if (mime == null) return Level.SOFTWARE
        val w = if (width > 0) width else height * 16 / 9
        val key = "$mime|$w|$height|$fps"
        cache[key]?.let { return it }
        val result = runCatching { query(mime, w, height, fps.coerceAtLeast(1)) }.getOrDefault(Level.SOFTWARE)
        cache[key] = result
        return result
    }

    private fun query(mime: String, width: Int, height: Int, fps: Int): Level {
        var best = Level.NONE
        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            val caps = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: continue
            // Either orientation: portrait Shorts are 1080×1920.
            val ok = runCatching { caps.areSizeAndRateSupported(width, height, fps.toDouble()) || caps.areSizeAndRateSupported(height, width, fps.toDouble()) }.getOrDefault(false)
            if (!ok) continue
            if (isHardware(info)) return Level.HARDWARE
            best = Level.SOFTWARE
        }
        return best
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated && !info.isSoftwareOnly
        val n = info.name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.") || !n.startsWith("omx.") && !n.startsWith("c2."))
    }

    /**
     * Pure ranking used for stream choice: higher is better. Hardware beats software beats
     * nothing; among equals, AVC (cheapest to decode everywhere) > VP9 > HEVC > AV1.
     */
    fun rank(level: Level, mime: String?): Int {
        val l = when (level) { Level.HARDWARE -> 300; Level.SOFTWARE -> 100; Level.NONE -> 0 }
        val c = when (mime) { "video/avc" -> 4; "video/x-vnd.on2.vp9" -> 3; "video/hevc" -> 2; "video/av01" -> 1; else -> 0 }
        return l + c
    }
}
