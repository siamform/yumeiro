/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f05

import dev.asfakul.yumeiro.sources.ecosystem.ScriptCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-05-N (ADR-057): the host half of the crypto-js shim. Vectors: RFC 1321 (MD5), FIPS 180-2 (SHA-256),
 * the RFC 2104 / Wikipedia HMAC example, NIST SP 800-38A F.2.1 (AES-128-CBC), and OpenSSL
 * EVP_BytesToKey(MD5, 1 round) as `openssl enc -p -md md5` prints it (checked with Node's crypto).
 */
class ScriptCryptoTest {
    private fun h(s: String) = ScriptCrypto.hex(s.toByteArray())

    @Test fun hashesMatchTheStandardVectors() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", ScriptCrypto.hash("MD5", h("hello")))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ScriptCrypto.hash("SHA256", h("abc")))
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            ScriptCrypto.hmac("SHA256", h("key"), h("The quick brown fox jumps over the lazy dog")))
        assertEquals("an empty HMAC key works like crypto-js", "5a470ef74cd7af75c375be99c6ef771f", ScriptCrypto.hmac("MD5", "", h("x")))
    }

    @Test fun aesCbcMatchesNistAndRoundTrips() {
        val nist = "2b7e151628aed2a6abf7158809cf4f3c"; val iv = "000102030405060708090a0b0c0d0e0f"
        val pt = "6bc1bee22e409f96e93d7e117393172a"
        assertEquals("7649abac8119b246cee98e9b12e9197d", ScriptCrypto.cipher(true, "AES", "CBC", "NoPadding", nist, iv, pt))
        val padded = ScriptCrypto.cipher(true, "AES", "CBC", "Pkcs7", nist, iv, pt)
        assertEquals("7649abac8119b246cee98e9b12e9197d8964e0b149c10b7b682e6e39aaeb731c", padded)
        assertEquals(pt, ScriptCrypto.cipher(false, "AES", "CBC", "Pkcs7", nist, iv, padded))
    }

    @Test fun tripleDesAndEcbWork() {
        val k = "0123456789abcdeffedcba98765432100123456789abcdef"
        val ct = ScriptCrypto.cipher(true, "DES-EDE", "ECB", "Pkcs7", k, "", h("triple"))
        assertEquals("f8b82a7a5aa76b6d", ct)
        assertEquals(h("triple"), ScriptCrypto.cipher(false, "TripleDES", "ECB", "Pkcs7", k, "", ct))
        assertEquals("a 16-byte key repeats K1 like crypto-js", ct, ScriptCrypto.cipher(true, "DES-EDE", "ECB", "Pkcs7", k.take(32), "", h("triple")))
    }

    @Test fun passphraseKeysFollowOpenSsl() {
        assertEquals("222776d258e716efdaa0cf5a7e504b9d21982575418a44be8d93693294778eeaf44a4ca59001d91d765385d7bea75845",
            ScriptCrypto.evpKdf(h("pw"), "0102030405060708", 32, 16))
    }

    @Test fun badInputFailsInsteadOfGuessing() {
        assertTrue(runCatching { ScriptCrypto.cipher(true, "AES", "CBC", "Pkcs7", "00", "", "00") }.isFailure)
        assertTrue(runCatching { ScriptCrypto.hash("WHIRLPOOL", "00") }.isFailure)
        assertTrue(runCatching { ScriptCrypto.unhex("zz") }.isFailure)
    }
}
