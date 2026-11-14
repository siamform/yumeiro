/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.notify.AppNotifier

/** Status checks and "take me to the right system page" shortcuts for every permission Yumeiro uses. */
object PermissionCenter {
    private const val PREFS = "permission_center"

    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun mediaAllowed(c: Context): Boolean = mediaPermissions().all {
        ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
    }

    fun notificationsAllowed(c: Context): Boolean = AppNotifier.systemAllowed(c)

    fun installAllowed(c: Context): Boolean = dev.asfakul.yumeiro.OsCompat.canInstallPackages(c)

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true

    private fun start(c: Context, i: Intent): Boolean = runCatching {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun openAppSettings(c: Context) {
        start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))
    }

    fun openNotificationSettings(c: Context) {
        val ok = start(c, dev.asfakul.yumeiro.OsCompat.notificationSettingsIntent(c))
        if (!ok) openAppSettings(c)
    }

    fun openInstallSettings(c: Context) {
        val ok = start(c, dev.asfakul.yumeiro.OsCompat.unknownSourcesIntent(c))
        if (!ok) openAppSettings(c)
    }

    /**
     * Asks Android to stop battery-optimizing Yumeiro, straight from the app (one-tap system
     * dialog for this package). Falls back to the app's own page, then the system list, and on
     * phones with an extra vendor battery manager also offers its auto-start/battery page.
     */
    fun openBatterySettings(c: Context) {
        if (!batteryUnrestricted(c)) {
            @android.annotation.SuppressLint("BatteryLife")
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
            if (start(c, request)) return
        }
        if (openVendorBatterySettings(c)) return
        if (start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)))) return
        start(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Vendor "auto-start / background" pages (Round 2: one list, with fallbacks, in [dev.asfakul.yumeiro.notify.ReliabilityGuide]). */
    internal fun vendorBatteryIntents(manufacturer: String): List<Pair<String, String>> =
        dev.asfakul.yumeiro.notify.ReliabilityGuide.vendorFor(manufacturer).pages.map { it.packageName to it.className }

    /** Only after the Android exemption is granted: the vendor page is the second, separate switch. */
    fun openVendorBatterySettings(c: Context): Boolean {
        if (!batteryUnrestricted(c)) return false
        val v = dev.asfakul.yumeiro.notify.ReliabilityGuide.vendor()
        if (dev.asfakul.yumeiro.notify.ReliabilityGuide.availablePages(c, v).isEmpty()) return false
        return dev.asfakul.yumeiro.notify.ReliabilityGuide.openVendorPage(c, v)
    }

    /**
     * Whether to show the in-app "Turn on notifications" card: notifications are off, and we haven't
     * asked in this app version (at most once per version, and never more than 4 times overall).
     */
    fun shouldPromptNotifications(c: Context): Boolean {
        if (notificationsAllowed(c)) return false
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt("notif_prompt_version", -1) != BuildConfig.VERSION_CODE && p.getInt("notif_prompt_count", 0) < 4
    }

    fun markNotificationsPrompted(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("notif_prompt_version", BuildConfig.VERSION_CODE)
            .putInt("notif_prompt_count", p.getInt("notif_prompt_count", 0) + 1).apply()
    }
}
