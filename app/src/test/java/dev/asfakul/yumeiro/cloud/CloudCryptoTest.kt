/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cloud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudCryptoTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun hkdfMatchesRfc5869Case1() {
        val okm = CloudCrypto.hkdf(ByteArray(22) { 0x0b }, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", CloudCrypto.hex(okm))
    }

    @Test fun sealOpenRoundTripAndAadBinding() {
        val key = CloudCrypto.randomBytes(32)
        val sealed = CloudCrypto.sealText(key, "hello room", "ABCDEFGH|state")
        assertTrue(Regex("^v1\\.[A-Za-z0-9_-]{38,}$").matches(sealed)) // what the server accepts
        assertEquals("hello room", CloudCrypto.openText(key, sealed, "ABCDEFGH|state"))
        try { CloudCrypto.openText(key, sealed, "ABCDEFGH|msg"); fail() } catch (_: Exception) {}
        try { CloudCrypto.openText(CloudCrypto.randomBytes(32), sealed, "ABCDEFGH|state"); fail() } catch (_: Exception) {}
        assertNotEquals(sealed, CloudCrypto.sealText(key, "hello room", "ABCDEFGH|state")) // fresh IV each time
    }

    @Test fun emptyPlaintextStillPassesServerCheck() {
        val sealed = CloudCrypto.sealText(CloudCrypto.randomBytes(32), "", "x")
        assertTrue(sealed.length - 3 >= 38)
    }


    @Test fun base32RoundTripsAndForgivesTypos() {
        val raw = CloudCrypto.randomBytes(CloudKeys.ROOM_BYTES)
        val code = CloudCodes.encode(raw)
        assertEquals(26, code.length)
        val grouped = CloudCodes.group(code)
        assertArrayEquals(raw, CloudCodes.decode(grouped.lowercase(), CloudKeys.ROOM_BYTES))
        assertArrayEquals(raw, CloudCodes.decode(" " + grouped.replace('-', ' ') + " ", CloudKeys.ROOM_BYTES))
        assertArrayEquals(CloudCodes.decode("OIBA", 2), CloudCodes.decode("01ba", 2))
        assertNull(CloudCodes.decode(code.dropLast(1), CloudKeys.ROOM_BYTES))
        assertNull(CloudCodes.decode(code.dropLast(1) + "!", CloudKeys.ROOM_BYTES))
        assertEquals("MZXW6YQ", CloudCodes.encode("foob".toByteArray())) // RFC 4648 vector without padding
    }

    @Test fun roomKeysAreStableAndBoundToTheRoom() {
        val secret = ByteArray(16) { 7 }
        assertArrayEquals(CloudKeys.Room("AAAAAAAA", secret).key, CloudKeys.Room("AAAAAAAA", secret.copyOf()).key)
        assertFalse(CloudKeys.Room("AAAAAAAA", secret).key.contentEquals(CloudKeys.Room("BBBBBBBB", secret).key))
        assertEquals("AAAAAAAA|msg", CloudKeys.Room("AAAAAAAA", secret).aad("msg"))
    }

    @Test fun gatePausesAfterFailuresAndReopens() {
        CloudGate.reset()
        assertTrue(CloudGate.open(0))
        CloudGate.failed(0)
        assertFalse(CloudGate.open(60_000))
        assertTrue(CloudGate.open(2 * 60_000))
        CloudGate.failed(0); CloudGate.failed(0)
        assertEquals(8 * 60_000L, CloudGate.wait(0)) // 2, 4, 8 minutes
        repeat(10) { CloudGate.failed(0) }
        assertEquals(60 * 60_000L, CloudGate.wait(0)) // capped at an hour
        CloudGate.ok()
        assertTrue(CloudGate.open(0))
    }
}
