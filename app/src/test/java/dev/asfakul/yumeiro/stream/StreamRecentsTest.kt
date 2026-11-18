/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NBUG-15 (d) (NB15D-1, ADR-027): opening a stream records it in Play Link recents only when the caller
 * says it came from Play Link. Old code: `IncomingMedia.open` recorded every stream by default, so a
 * caller that forgets (websites, the player's page finder, any new screen) filled Play Link recents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamRecentsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun openRecordsNothingInPlayLinkByDefault() {
        StreamRepository.init(context)
        val url = "https://cdn.example/default-${System.nanoTime()}.m3u8"
        IncomingMedia.open(context, StreamRequest(url = url, title = "Default caller"))
        IncomingMedia.consume()
        assertFalse(
            "NB15D-1: a stream opened without saying where from was put into Play Link recents",
            StreamRepository.history.value.any { it.url == url },
        )
    }
}
