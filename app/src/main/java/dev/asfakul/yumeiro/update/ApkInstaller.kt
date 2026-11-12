/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Verifies and installs a downloaded update APK through the platform [PackageInstaller].
 *
 * Security checks performed before handing the file to the system:
 *  - SHA-256 matches the digest GitHub / SHA256SUMS.txt publishes for the asset (required)
 *  - package name is exactly this app's package name
 *  - version code is strictly higher than the installed one
 *  - signing certificate matches the installed app (same constant release keystore)
 */
internal object ApkInstaller {

    const val ACTION_INSTALL_STATUS = "dev.asfakul.yumeiro.update.action.INSTALL_STATUS"

    class VerificationException(message: String) : Exception(message)

    fun canInstallPackages(context: Context): Boolean = dev.asfakul.yumeiro.OsCompat.canInstallPackages(context)

    /** Intent that opens "Install unknown apps" for this app (Android 8+). */
    fun unknownSourcesSettingsIntent(context: Context): Intent = dev.asfakul.yumeiro.OsCompat.unknownSourcesIntent(context)

    suspend fun verify(context: Context, apk: File, expectedSha256: String?) = withContext(Dispatchers.IO) {
        if (!apk.exists() || apk.length() == 0L) throw VerificationException("The downloaded file is missing.")

        // Fail closed: never install an update whose SHA-256 was not published with the release.
        if (expectedSha256.isNullOrBlank()) {
            throw VerificationException("No checksum was published for this update, so it can't be verified.")
        }
        val actual = sha256(apk)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw VerificationException("Checksum mismatch. The download may be corrupted.")
        }

        val pm = context.packageManager
        val flags = dev.asfakul.yumeiro.OsCompat.SIGNING_FLAGS
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw VerificationException("The downloaded file is not a valid APK.")

        if (archive.packageName != context.packageName) {
            throw VerificationException("This update is for a different app (${archive.packageName}).")
        }

        val installed = pm.getPackageInfo(context.packageName, flags)
        if (dev.asfakul.yumeiro.OsCompat.versionCode(archive) <= dev.asfakul.yumeiro.OsCompat.versionCode(installed)) {
            throw VerificationException("The downloaded version is not newer than the installed one.")
        }

        val newCerts = signerDigests(archive)
        val currentCerts = signerDigests(installed)
        // If the platform couldn't read archive signatures we let the system installer enforce it.
        if (newCerts.isNotEmpty() && currentCerts.isNotEmpty() && newCerts.intersect(currentCerts).isEmpty()) {
            throw VerificationException("Signature mismatch: this APK was not signed with the official key.")
        }
    }

    /** Streams the APK into a PackageInstaller session; the result arrives at [InstallResultReceiver]. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // PACKAGE_SOURCE_OTHER (not DOWNLOADED_FILE): a self-update from the official signed
                // release is not a "file downloaded from the web", so Android doesn't apply the
                // restricted-settings treatment to it.
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, bufferSize = 1 shl 16)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION_INSTALL_STATUS)
                .setPackage(context.packageName)
            // FLAG_MUTABLE is required: the system fills in the status extras.
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signerDigests(info: PackageInfo): Set<String> = dev.asfakul.yumeiro.OsCompat.signerDigests(info).toSet()
}
