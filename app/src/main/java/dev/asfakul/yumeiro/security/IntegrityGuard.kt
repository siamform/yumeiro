/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import android.content.Context
import android.content.pm.PackageManager
import dev.asfakul.yumeiro.BuildConfig
import java.security.MessageDigest

/**
 * Detects re-signed / repackaged copies of the app.
 *
 * Official releases are signed with one key whose certificate digest is baked into the build.
 * If the running APK is signed by anything else it is an unofficial build: the in-app updater is
 * disabled (official updates could not be installed over it anyway) and Settings shows a notice
 * pointing users to the real download page. Debug builds are never flagged.
 */
object IntegrityGuard {

    enum class Status { OFFICIAL, DEVELOPMENT, UNOFFICIAL }

    @Volatile private var cached: Status? = null

    fun status(context: Context): Status = cached ?: compute(context.applicationContext).also { cached = it }

    fun isUnofficial(context: Context): Boolean = status(context) == Status.UNOFFICIAL

    private fun expected(): String {
        val key = BuildConfig.UPDATER_TOKEN_KEY
        return BuildConfig.INTEGRITY_BYTES.mapIndexed { i, b -> (b xor ((key + i * 7) and 0xFF)).toChar() }.joinToString("")
    }

    private fun compute(context: Context): Status {
        if (BuildConfig.DEBUG) return Status.DEVELOPMENT
        val want = expected()
        if (want.length != 64) return Status.DEVELOPMENT
        val digests = runCatching { signerDigests(context) }.getOrDefault(emptyList())
        if (digests.isEmpty()) return Status.UNOFFICIAL
        return if (digests.any { it == want }) Status.OFFICIAL else Status.UNOFFICIAL
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(context: Context): List<String> {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return emptyList()
        val sigs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
    }
}
