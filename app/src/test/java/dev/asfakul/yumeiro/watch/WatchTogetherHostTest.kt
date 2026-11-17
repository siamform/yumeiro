/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/**
 * GROUP-13 N1 (unit, Robolectric): Watch Together, local parts only.
 * N1-2: hosting while a video is already bound (floating window) never listened to the player and never
 * started the 3 s publish tick, so guests kept the position from the moment the room opened.
 * N1-1: a wrong room code reset the status and the guest card vanished with no message.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34])
class WatchTogetherHostTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private var exo: ExoPlayer? = null

    @After fun tearDown() {
        WatchTogether.bind(null)
        WatchTogether.leave(ctx)
        exo?.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun hostingWithAPlayerAlreadyBoundKeepsPublishing() {
        val p = ExoPlayer.Builder(ctx).setLooper(Looper.getMainLooper()).build().also { exo = it }
        WatchTogether.bind(p) // the floating window is already up
        WatchTogether.host(ctx, "test room")
        assertEquals(WatchTogether.Role.HOST, WatchTogether.status.value.role)
        val first = WatchTogether.status.value.state.seq
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(6_500))
        assertTrue("room state republished while hosting (seq $first → ${WatchTogether.status.value.state.seq})",
            WatchTogether.status.value.state.seq >= first + 2)
    }

    @Test fun wrongRoomCodeTellsTheGuest() {
        // Stand-in room that answers like RoomServer does for a wrong code.
        val stand = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response =
                newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "wrong code")
        }.also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
        try {
            WatchTogether.join(ctx, "127.0.0.1:${stand.listeningPort}", "0000", "test room")
            val deadline = System.currentTimeMillis() + 10_000
            var text: String? = null
            while (System.currentTimeMillis() < deadline && text == null) {
                Thread.sleep(50)
                shadowOf(Looper.getMainLooper()).idle()
                text = ShadowToast.getTextOfLatestToast()?.takeIf { it.contains("Wrong room code") }
            }
            assertEquals("Wrong room code. Check the 4-digit code on the host's screen", text)
            assertEquals(WatchTogether.Role.NONE, WatchTogether.status.value.role)
        } finally { stand.stop() }
    }
}
