/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * App-lock key: AES-GCM in the Android Keystore that can only be used for a few seconds after a
 * strong biometric or the device PIN/pattern. The app lock opens only when this key encrypts a
 * challenge, so the unlock is tied to real user authentication (not just a callback).
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object LockKey {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "yumeiro_lock_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    /** A cipher ready for the prompt, or null when the device can't provide one (falls back). */
    fun cipher(): Cipher? = runCatching { init() }.recoverCatching { e ->
        // New fingerprint enrolled or lock screen changed: the old key is gone for good.
        if (e is KeyPermanentlyInvalidatedException) { delete(); init() } else throw e
    }.getOrNull()

    /** True when the authenticated cipher can actually encrypt (i.e. the user really authenticated). */
    fun verify(cipher: Cipher): Boolean = runCatching { cipher.doFinal(ByteArray(16)).isNotEmpty() }.getOrDefault(false)

    private fun init(): Cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }

    private fun delete() = runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }

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
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .build(),
            )
        }.generateKey()
    }
}
