/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.service

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** G6-WL: streams get a Wi-Fi lock (WAKE_MODE_NETWORK), device files do not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MusicWakeModeTest {
    @Test fun streamsUseNetworkWakeMode() {
        listOf("mtyt://dQw4w9WgXcQ", "MTYT://x", "https://rr1.googlevideo.com/videoplayback?x=1", "http://192.168.1.2/song.mp3", " https://a/b.m4a")
            .forEach { assertEquals(it, C.WAKE_MODE_NETWORK, PlaybackService.wakeModeFor(it)) }
    }

    @Test fun deviceFilesUseLocalWakeMode() {
        listOf("content://media/external/audio/media/42", "file:///storage/emulated/0/Music/a.mp3", "/sdcard/Music/a.mp3", "", null)
            .forEach { assertEquals(it.toString(), C.WAKE_MODE_LOCAL, PlaybackService.wakeModeFor(it)) }
    }
}
