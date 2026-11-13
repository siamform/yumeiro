/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * The few platform calls that differ between Android 7 (API 24, many TVs and Fire TV sticks) and
 * Android 9+ (where Yumeiro started). One place, so every caller gets the same fallback.
 */
object OsCompat {
    /** Flags that return signing certificates on every version (signingInfo on 9+, signatures before). */
    @Suppress("DEPRECATION")
    val SIGNING_FLAGS: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        else PackageManager.GET_SIGNATURES

    /** SHA-256 hex of each signing certificate (current signers; rotation history on 9+). */
    @Suppress("DEPRECATION")
    fun signerDigests(info: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            val si = info.signingInfo!!
            (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory).orEmpty().toList()
        } else info.signatures?.toList().orEmpty()
        return sigs.filterNotNull().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    fun versionCode(info: PackageInfo): Long = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)

    /** Android 8+ asks per app; before that "Unknown sources" is one system switch the installer checks itself. */
    fun canInstallPackages(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.packageManager.canRequestPackageInstalls() else true

    fun unknownSourcesIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
        else Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun notificationSettingsIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null))
}
