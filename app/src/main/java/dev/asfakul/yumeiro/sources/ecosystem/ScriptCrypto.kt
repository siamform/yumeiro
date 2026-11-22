/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * F-05-N (ADR-057): the host half of the `crypto-js` shim in `assets/js/provider_runtime.js`.
 * Nuvio scrapers `require("crypto-js")`; instead of bundling a copy of that library, the shim keeps
 * crypto-js's shapes (WordArray, enc.*, AES / TripleDES with CBC / ECB and Pkcs7 / NoPadding, the
 * hashes and HMACs) and sends the bytes here, where the platform's javax.crypto does the work.
 * Everything is hex in, hex out, so the JS side never needs a binary channel.
 * Pure JVM code: no Android types, unit-tested in `ScriptCryptoTest`.
 */
internal object ScriptCrypto {
    /** A call answers at most this much (a scraper decrypts a page or a token, never a video). */
    const val MAX_BYTES = 4 * 1024 * 1024

    fun hash(alg: String, dataHex: String): String {
        val name = when (alg.uppercase()) {
            "MD5" -> "MD5"; "SHA1" -> "SHA-1"; "SHA224" -> "SHA-224"; "SHA256" -> "SHA-256"
            "SHA384" -> "SHA-384"; "SHA512" -> "SHA-512"
            else -> error("Unsupported hash $alg")
        }
        return hex(MessageDigest.getInstance(name).digest(unhex(dataHex)))
    }

    fun hmac(alg: String, keyHex: String, dataHex: String): String {
        val name = when (alg.uppercase()) {
            "MD5" -> "HmacMD5"; "SHA1" -> "HmacSHA1"; "SHA224" -> "HmacSHA224"; "SHA256" -> "HmacSHA256"
            "SHA384" -> "HmacSHA384"; "SHA512" -> "HmacSHA512"
            else -> error("Unsupported HMAC $alg")
        }
        val key = unhex(keyHex)
        val mac = Mac.getInstance(name)
        // An empty HMAC key is legal in crypto-js; javax.crypto refuses a zero-length SecretKeySpec.
        mac.init(if (key.isEmpty()) EmptyKey(name) else SecretKeySpec(key, name))
        return hex(mac.doFinal(unhex(dataHex)))
    }

    private class EmptyKey(private val alg: String) : javax.crypto.SecretKey {
        override fun getAlgorithm() = alg
        override fun getFormat() = "RAW"
        override fun getEncoded() = ByteArray(0)
    }

    /**
     * [cipher] "AES" or "DES-EDE" (TripleDES), [mode] "CBC" or "ECB", [padding] "Pkcs7" or "NoPadding".
     * Key sizes: AES 16 / 24 / 32 bytes; TripleDES 16 or 24 (a 16-byte key repeats K1, like crypto-js).
     */
    fun cipher(encrypt: Boolean, cipher: String, mode: String, padding: String, keyHex: String, ivHex: String, dataHex: String): String {
        val data = unhex(dataHex)
        var key = unhex(keyHex)
        val (alg, block) = when (cipher.uppercase()) {
            "AES" -> "AES" to 16
            "DES-EDE", "TRIPLEDES", "3DES" -> "DESede" to 8
            else -> error("Unsupported cipher $cipher")
        }
        if (alg == "AES") require(key.size == 16 || key.size == 24 || key.size == 32) { "AES key must be 16, 24 or 32 bytes" }
        if (alg == "DESede") {
            require(key.size == 16 || key.size == 24) { "TripleDES key must be 16 or 24 bytes" }
            if (key.size == 16) key = key + key.copyOfRange(0, 8)
        }
        val m = when (mode.uppercase()) { "CBC" -> "CBC"; "ECB" -> "ECB"; else -> error("Unsupported mode $mode") }
        val p = when (padding.lowercase()) { "pkcs7", "pkcs5" -> "PKCS5Padding"; "nopadding", "none" -> "NoPadding"; else -> error("Unsupported padding $padding") }
        val c = Cipher.getInstance("$alg/$m/$p")
        val spec = SecretKeySpec(key, alg)
        if (m == "CBC") {
            val iv = unhex(ivHex).let { if (it.size >= block) it.copyOf(block) else it + ByteArray(block - it.size) }
            c.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, spec, IvParameterSpec(iv))
        } else {
            c.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, spec)
        }
        return hex(c.doFinal(data))
    }

    /**
     * OpenSSL EVP_BytesToKey with MD5 and one round, as crypto-js uses for `AES.decrypt(text, "passphrase")`:
     * returns key + iv hex ([keyBytes] + [ivBytes] long).
     */
    fun evpKdf(passHex: String, saltHex: String, keyBytes: Int, ivBytes: Int): String {
        val pass = unhex(passHex); val salt = unhex(saltHex)
        val need = keyBytes + ivBytes
        require(need in 1..64) { "Bad key size" }
        val out = java.io.ByteArrayOutputStream()
        var prev = ByteArray(0)
        val md = MessageDigest.getInstance("MD5")
        while (out.size() < need) {
            md.reset(); md.update(prev); md.update(pass); md.update(salt)
            prev = md.digest(); out.write(prev)
        }
        return hex(out.toByteArray().copyOf(need))
    }

    private const val HEX = "0123456789abcdef"
    fun hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xff; sb.append(HEX[v shr 4]).append(HEX[v and 15]) }
        return sb.toString()
    }

    fun unhex(s: String): ByteArray {
        require(s.length / 2 <= MAX_BYTES) { "Too much data for one call" }
        val n = s.length / 2
        val out = ByteArray(n)
        for (i in 0 until n) {
            val hi = Character.digit(s[2 * i], 16); val lo = Character.digit(s[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "Not hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
