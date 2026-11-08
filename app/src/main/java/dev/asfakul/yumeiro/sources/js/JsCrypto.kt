/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Interop crypto shims exposed to third-party JavaScript sources (Mangayomi API surface).
 *
 * These reproduce what the *websites* use (CryptoJS AES-CBC, OpenSSL `Salted__` with
 * EVP_BytesToKey/MD5) so a source can decode a page's obfuscated stream links. They never
 * protect Yumeiro data: app secrets use AES-GCM in AndroidKeyStore (security/SecretBox).
 * CodeQL's weak-crypto rules are therefore suppressed for this file only, see
 * .github/codeql/suppressions.jq and docs/security/AUDIT.md.
 */
internal object JsCrypto {
    fun aesCbc(text: String, iv: String, key: String, encrypt: Boolean): String = runCatching {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
        if (encrypt) Base64.encodeToString(c.doFinal(text.toByteArray()), Base64.NO_WRAP)
        else String(c.doFinal(Base64.decode(text, Base64.DEFAULT)))
    }.getOrDefault("")

    /** OpenSSL / CryptoJS "Salted__" format with EVP_BytesToKey(MD5). */
    private fun evpKey(pass: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val md = MessageDigest.getInstance("MD5")
        var out = ByteArray(0); var prev = ByteArray(0)
        while (out.size < 48) { md.reset(); prev = md.digest(prev + pass + salt); out += prev }
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 48)
    }

    fun cryptoJsDecrypt(encrypted: String, pass: String): String = runCatching {
        val raw = Base64.decode(encrypted, Base64.DEFAULT)
        val salt = raw.copyOfRange(8, 16)
        val (k, iv) = evpKey(pass.toByteArray(), salt)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv)) }
        String(c.doFinal(raw.copyOfRange(16, raw.size)))
    }.getOrDefault("")

    fun cryptoJsEncrypt(plain: String, pass: String): String = runCatching {
        val salt = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }
        val (k, iv) = evpKey(pass.toByteArray(), salt)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv)) }
        Base64.encodeToString("Salted__".toByteArray() + salt + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }.getOrDefault("")

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun aesGcm(encrypted: String, keyHex: String, ivHex: String, tagHex: String): String = runCatching {
        val data = Base64.decode(encrypted, Base64.DEFAULT) + (if (tagHex.isNotBlank()) hex(tagHex) else ByteArray(0))
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(hex(keyHex), "AES"), GCMParameterSpec(128, hex(ivHex))) }
        String(c.doFinal(data))
    }.getOrDefault("")
}
