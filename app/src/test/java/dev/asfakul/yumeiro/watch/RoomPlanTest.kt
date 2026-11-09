/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import dev.asfakul.yumeiro.cloud.CloudCodes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlanTest {
    private val secret = ByteArray(16) { (it * 3).toByte() }
    private val key = CloudCodes.encode(secret)

    @Test fun inviteLinkRoundTrips() {
        val link = RoomPlan.inviteLink("ABCD2345", secret)
        assertTrue(link.startsWith("https://") && link.contains("/r/ABCD2345#"))
        val (id, s) = RoomPlan.parseInvite(link)!!
        assertEquals("ABCD2345", id); assertArrayEquals(secret, s)
    }

    @Test fun parsesDeepLinkSharedTextAndTyped() {
        assertEquals("ABCD2345", RoomPlan.parseInvite("yumeiro://room/ABCD2345#$key")!!.first)
        assertEquals("ABCD2345", RoomPlan.parseInvite("Watch with me on Yumeiro: https://api.yumeiro.click/r/ABCD2345#$key")!!.first)
        assertArrayEquals(secret, RoomPlan.parseInvite("abcd2345 ${key.lowercase()}")!!.second)
    }

    @Test fun rejectsIncompleteInvites() {
        assertNull(RoomPlan.parseInvite("https://api.yumeiro.click/r/ABCD2345"))
        assertNull(RoomPlan.parseInvite("https://api.yumeiro.click/r/ABCD2345#${key.drop(2)}"))
        assertNull(RoomPlan.parseInvite("ABC#$key"))
        assertNull(RoomPlan.parseInvite(""))
    }

    @Test fun hostSendsOnChangesAndHeartbeatOnly() {
        val a = WatchTogether.RoomState(api = "x", url = "u", index = 1, positionMs = 10_000, playing = true, at = 1_000)
        assertTrue(RoomPlan.shouldSend(null, a, 1_000, 0))
        // Playing smoothly, 3 s later: extrapolates fine → no frame until the heartbeat.
        val b = a.copy(positionMs = 13_000, at = 4_000)
        assertFalse(RoomPlan.shouldSend(a, b, 4_000, 1_000))
        assertFalse(RoomPlan.shouldSend(a, b.copy(at = 12_000, positionMs = 21_000), 12_000, 1_000))
        assertTrue(RoomPlan.shouldSend(a, b.copy(at = 31_000, positionMs = 40_000), 31_000, 1_000)) // 30 s heartbeat
        assertTrue(RoomPlan.shouldSend(a, b.copy(playing = false), 4_000, 1_000))
        assertTrue(RoomPlan.shouldSend(a, b.copy(positionMs = 60_000), 4_000, 1_000)) // seek
        assertTrue(RoomPlan.shouldSend(a, b.copy(index = 2), 4_000, 1_000))
    }

    @Test fun backoffGrowsAndCaps() {
        assertEquals(1_000, RoomPlan.backoffMs(1))
        assertEquals(2_000, RoomPlan.backoffMs(2))
        assertEquals(30_000, RoomPlan.backoffMs(12))
    }
}
