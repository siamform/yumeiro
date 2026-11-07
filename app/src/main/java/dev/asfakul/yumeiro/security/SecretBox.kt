/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (account tokens, API keys) before they reach SharedPreferences.
 *
 * Uses an AES-256-GCM key generated inside the Android Keystore. The key never leaves the
 * Keystore and is not part of backups. A restored or migrated device can't decrypt old values,
 * so the user simply signs in again.
 *
 * Stored format: `v1:` + base64(iv || ciphertext+tag).
 */
object SecretBox {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "yumeiro_secrets_v1"
    private const val PREFIX = "v1:"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    /** Returns the sealed form, or null if the Keystore is unavailable (callers must not fall back to plaintext). */
    fun seal(plain: String): String? = runCatching {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
    }.getOrNull()

    /** Returns the plaintext, or null if the value can't be decrypted (wrong key, tampered or corrupt). */
    fun open(stored: String): String? = runCatching {
        require(isSealed(stored))
        val raw = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
        require(raw.size > 12 + TAG_BITS / 8)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
