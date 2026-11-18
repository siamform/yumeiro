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

    /**
     * G6-SP1: the one permission Music needs. Android 13+ asks for music and for videos in two
     * dialogs; videos allowed is not music access. Binder call: read it off the main thread.
     */
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun audioAllowed(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, audioPermission()) == PackageManager.PERMISSION_GRANTED

    fun notificationsAllowed(c: Context): Boolean = AppNotifier.systemAllowed(c)

    /** Media and notification permission, as the setup checklist shows them. */
    data class SetupPerms(val media: Boolean, val notifications: Boolean)

    /**
     * G12 X2-2: both checks are binder calls to the system (`checkPermission`,
     * `areNotificationsEnabled`) and the first one in a process is uncached. Read in Home's
     * composition they stalled the main thread for 2 s on the low-RAM emulator (43757544993).
     * They run off main through [readSetupPerms]; the last answer is kept for the first frame.
     */
    @Volatile var lastSetupPerms: SetupPerms? = null
        private set

    /** Test hook: what [readSetupPerms] asks. */
    @Volatile internal var setupPermsReader: (Context) -> SetupPerms = { c -> SetupPerms(mediaAllowed(c), notificationsAllowed(c)) }

    /** Blocking (binder); call off the main thread. */
    fun readSetupPerms(c: Context): SetupPerms = setupPermsReader(c.applicationContext ?: c).also { lastSetupPerms = it }

    fun installAllowed(c: Context): Boolean = dev.asfakul.yumeiro.OsCompat.canInstallPackages(c)

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true

    /**
     * R17 G10 (ER-2): what to do after Android's permission dialog said no. Denied once (Android
     * would still ask again): say so and stay. Blocked ("Don't allow" twice, or "Don't ask again"):
     * Android no longer shows its dialog, so offer the settings page through the message's action.
     * Before, every "no" threw the user straight into Android settings, even right after they
     * chose "Don't allow" themselves.
     */
    enum class Denial { ONCE, BLOCKED }

    fun denial(c: Context, permission: String): Denial {
        val a = c.findActivity() ?: return Denial.BLOCKED
        return if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(a, permission)) Denial.ONCE else Denial.BLOCKED
    }

    /** [what] is the user's word for it, e.g. "Notifications". */
    fun explainDenied(c: Context, permission: String, what: String, openSettings: () -> Unit = { openAppSettings(c) }) {
        when (denial(c, permission)) {
            Denial.ONCE -> dev.asfakul.yumeiro.ui.components.AppMessages.info(c, "$what not allowed. Tap again when you want to allow it")
            Denial.BLOCKED -> dev.asfakul.yumeiro.ui.components.AppMessages.error(
                c, "$what blocked for Yumeiro. Android won't ask again; allow it in Android settings", "Open settings", openSettings,
            )
        }
    }

    /**
     * After the notification permission request: granted but still off (notifications turned off
     * for the app in Android settings, the request returns "granted" at once) → open that page,
     * otherwise as [explainDenied]. Before, the row did nothing in the "granted but off" case.
     */
    fun afterNotificationRequest(c: Context, granted: Boolean) {
        when {
            granted && !notificationsAllowed(c) -> openNotificationSettings(c)
            !granted && Build.VERSION.SDK_INT >= 33 ->
                explainDenied(c, Manifest.permission.POST_NOTIFICATIONS, "Notifications") { openNotificationSettings(c) }
        }
    }

    private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

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
        dev.asfakul.yumeiro.notify.ReliabilityGuide.vendorFor(manufacturer).pages

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
