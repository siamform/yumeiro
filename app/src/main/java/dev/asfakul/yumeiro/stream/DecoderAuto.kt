/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

/**
 * H-3 (owner item 3): the "Auto" video decoder. It starts like Hardware+. When the video fails to decode, the
 * recovery ([dev.asfakul.yumeiro.player.PlaybackRecovery]) calls [switchToSoftware] once and re-prepares the
 * player: the renderers then pick software decoders first (YumeiroRenderersFactory.AutoSelector), at the same
 * position. A new video starts on the phone's decoders again ([reset]). DRM (secure) streams keep the phone's
 * decoders: software ones can't decrypt.
 */
object DecoderAuto {
    @Volatile var softwareFirst: Boolean = false
        private set
    /** Times the software order was handed out since the last [reset] (the device test reads it). */
    @Volatile var used: Int = 0
    @Volatile var switches: Int = 0
        private set

    fun reset() { softwareFirst = false; used = 0 }

    /** True if this call switched (the first decode failure of this video under Auto). */
    fun switchToSoftware(): Boolean {
        if (softwareFirst) return false
        softwareFirst = true; switches++
        return true
    }
}
