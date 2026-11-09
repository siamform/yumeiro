/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cloud

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for online watch-together rooms (server/ in this repo). The room key is
 * derived on the device from a secret in the invite link's #fragment, which never reaches the
 * server. The server only relays AES-256-GCM ciphertext, so it can't read titles, positions or chat.
 *
 * Pure JVM code (no Android APIs), unit-tested in CloudCryptoTest.
 */
object CloudCrypto {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    /** HKDF-SHA256 (RFC 5869), extract + expand. */
    fun hkdf(ikm: ByteArray, salt: String, info: String, length: Int = 32): ByteArray =
        hkdf(ikm, salt.toByteArray(), info.toByteArray(), length)

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32)
        val prk = hmac(salt, ikm)
        val out = java.io.ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            t = hmac(prk, t + info + byteArrayOf(i.toByte()))
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    internal fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256")) }.doFinal(data)

    /** iv(12) ‖ ciphertext ‖ tag(16). [aad] binds the blob to its room / vault / slot. */
    fun seal(key: ByteArray, plain: ByteArray, aad: String): ByteArray {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad.toByteArray())
        return iv + c.doFinal(plain)
    }

    /** Throws if the key is wrong or the blob was changed. */
    fun open(key: ByteArray, sealed: ByteArray, aad: String): ByteArray {
        require(sealed.size >= 12 + 16) { "Too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        c.updateAAD(aad.toByteArray())
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    /** Text envelope the server accepts: "v1." + base64url(iv ‖ ct ‖ tag). */
    fun sealText(key: ByteArray, plain: String, aad: String): String = "v1." + b64(seal(key, plain.toByteArray(), aad))

    fun openText(key: ByteArray, sealed: String, aad: String): String {
        require(sealed.startsWith("v1.")) { "Unknown format" }
        return open(key, unb64(sealed.removePrefix("v1.")), aad).decodeToString()
    }


    fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getUrlDecoder().decode(s)
    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}

/**
 * Invite keys people may type or read out: RFC 4648 base32 (A–Z, 2–7) in groups of four. Parsing forgives
 * case, spaces, dashes and the look-alikes 0→O, 1→I, 8→B.
 */
object CloudCodes {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(b: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (x in b) {
            buffer = (buffer shl 8) or (x.toInt() and 0xff)
            bits += 8
            while (bits >= 5) { sb.append(ALPHABET[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    /** Null if a character isn't base32 after clean-up or the length doesn't give [bytes] bytes. */
    fun decode(text: String, bytes: Int): ByteArray? {
        val clean = normalize(text)
        if (clean.length != (bytes * 8 + 4) / 5) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) { out.write((buffer shr (bits - 8)) and 0xff); bits -= 8 }
        }
        return out.toByteArray().takeIf { it.size == bytes }
    }

    fun normalize(text: String): String = text.uppercase().filterNot { it == '-' || it.isWhitespace() }
        .map { when (it) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; else -> it } }.joinToString("")

    fun group(code: String, size: Int = 4): String = normalize(code).chunked(size).joinToString("-")
}

/** Room key: derived from the invite secret, bound to the room id. */
object CloudKeys {
    /** Room secret in the invite link's fragment (never sent to the server): 16 bytes = 26 characters. */
    const val ROOM_BYTES = 16

    class Room(val id: String, secret: ByteArray) {
        val key: ByteArray = CloudCrypto.hkdf(secret, "yumeiro-room", id, 32)
        fun aad(kind: String) = "$id|$kind"
    }
}
