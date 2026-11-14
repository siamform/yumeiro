/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Round 2, order 9: everything that can stop notifications from arriving while Yumeiro is
 * closed, checked in one place, plus a step-by-step guide for the phone maker's own battery
 * manager (auto-start, sleeping apps, app launch) that Android's own switch doesn't cover.
 */
object ReliabilityGuide {
    private const val PREFS = "reliability_guide"

    /** One phone maker's extra settings. [pages] are tried in order; the first that opens wins. */
    data class Vendor(val key: String, val name: String, val steps: List<String>, val pages: List<Pair<String, String>>) {
        val aggressive: Boolean get() = key != "android"
    }

    /** (package, activity class): plain strings so the list is testable without Android. */
    private fun cn(pkg: String, cls: String) = pkg to cls
    private const val LOCK = "Open Recents, then long-press (or pull down on) Yumeiro and tap the lock, so clearing Recents never closes it"

    /** Brand names as reported by [Build.MANUFACTURER] (and [Build.BRAND]) → the maker's guide. */
    fun vendorFor(manufacturer: String, brand: String = ""): Vendor {
        val m = (manufacturer + " " + brand).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Vendor("xiaomi", "Xiaomi, Redmi and POCO (MIUI / HyperOS)", listOf(
                "Turn on Autostart for Yumeiro",
                "App battery saver: choose No restrictions",
                LOCK,
            ), listOf(
                cn("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                cn("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                cn("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            ))
            "samsung" in m -> Vendor("samsung", "Samsung (One UI)", listOf(
                "Battery: choose Unrestricted for Yumeiro",
                "Battery → Background usage limits: remove Yumeiro from Sleeping apps and Deep sleeping apps",
                "Optional: add Yumeiro to Never sleeping apps",
            ), listOf(
                cn("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                cn("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                cn("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ))
            "huawei" in m || "honor" in m -> Vendor("huawei", "Huawei and Honor (EMUI / MagicOS)", listOf(
                "App launch: turn off Manage automatically for Yumeiro",
                "In the box that opens, allow Auto-launch, Secondary launch and Run in background",
                LOCK,
            ), listOf(
                cn("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                cn("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            ))
            listOf("oppo", "realme", "oneplus").any { it in m } -> Vendor("coloros", "OPPO, realme and OnePlus (ColorOS / realme UI / OxygenOS)", listOf(
                "Allow Auto launch (Auto startup) for Yumeiro",
                "Battery usage: allow background activity (Don't optimise)",
                LOCK,
            ), listOf(
                cn("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                cn("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                cn("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                cn("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                cn("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            ))
            "vivo" in m || "iqoo" in m -> Vendor("vivo", "vivo and iQOO (Funtouch OS / OriginOS)", listOf(
                "Allow Autostart for Yumeiro",
                "Battery → Background power consumption: allow Yumeiro (High background power usage)",
                LOCK,
            ), listOf(
                cn("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                cn("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                cn("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ))
            listOf("tecno", "infinix", "itel", "transsion").any { it in m } -> Vendor("transsion", "Tecno, Infinix and itel (HiOS / XOS)", listOf(
                "Phone Master → Auto-start management: allow Yumeiro",
                "Battery → App power saving: turn it off for Yumeiro",
                LOCK,
            ), listOf(
                cn("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
                cn("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity"),
            ))
            "asus" in m -> Vendor("asus", "ASUS", listOf("Mobile Manager → Auto-start Manager: allow Yumeiro", "Power saver: leave Yumeiro out of auto-clean"), listOf(
                cn("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                cn("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            ))
            "nokia" in m || "hmd" in m -> Vendor("nokia", "Nokia (HMD)", listOf("Battery protection / Power saver exceptions: add Yumeiro"), listOf(
                cn("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"),
            ))
            "meizu" in m -> Vendor("meizu", "Meizu (Flyme)", listOf("Allow Yumeiro to run in the background", LOCK), listOf(
                cn("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
            ))
            else -> Vendor("android", "Android", emptyList(), emptyList())
        }
    }

    fun vendor(): Vendor = vendorFor(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    /** Android-level checks, in the order the guide lists them. */
    enum class Check(val title: String, val fix: String) {
        NOTIFICATIONS("Notifications allowed", "Turn on notifications for Yumeiro"),
        BACKGROUND_ON("Background checks on", "Turn on Background checks in Yumeiro"),
        BATTERY("Battery not optimised", "Let Yumeiro skip battery optimisation"),
        NOT_RESTRICTED("Background use not restricted", "Remove the Restricted battery setting"),
        NOT_STANDBY_RESTRICTED("Not put to sleep by Android", "Open Yumeiro once a day or remove the restriction"),
    }

    data class Status(val failing: Set<Check>, val stalled: Boolean, val vendor: Vendor, val vendorDone: Boolean) {
        val allGood: Boolean get() = failing.isEmpty() && !stalled && (!vendor.aggressive || vendorDone)
    }

    /** Pure: which checks fail (unit tested). Standby bucket 45 = RESTRICTED (Android 11+). */
    fun failing(notif: Boolean, backgroundOn: Boolean, batteryUnrestricted: Boolean, backgroundRestricted: Boolean, standbyBucket: Int): Set<Check> = buildSet {
        if (!notif) add(Check.NOTIFICATIONS)
        if (!backgroundOn) add(Check.BACKGROUND_ON)
        if (!batteryUnrestricted) add(Check.BATTERY)
        if (backgroundRestricted) add(Check.NOT_RESTRICTED)
        if (standbyBucket >= 45) add(Check.NOT_STANDBY_RESTRICTED)
    }

    fun backgroundRestricted(c: Context): Boolean =
        runCatching { (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted }.getOrDefault(false)

    fun standbyBucket(c: Context): Int =
        runCatching { (c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).appStandbyBucket }.getOrDefault(10)

    fun status(c: Context): Status {
        NotifySettings.init(c)
        val f = failing(
            AppNotifier.systemAllowed(c), NotifySettings.get(c, NotifySettings.Key.BACKGROUND),
            dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter.batteryUnrestricted(c), backgroundRestricted(c), standbyBucket(c),
        )
        return Status(f, runCatching { BackgroundHealth.looksStalled(c) }.getOrDefault(false), vendor(), vendorDone(c))
    }

    // region vendor pages

    /** Pages of [v] that exist on this phone (exported and resolvable), in order. */
    fun availablePages(c: Context, v: Vendor = vendor()): List<ComponentName> = v.pages.map { (p, k) -> ComponentName(p, k) }.filter { cnm ->
        runCatching {
            val info = c.packageManager.getActivityInfo(cnm, PackageManager.MATCH_DEFAULT_ONLY)
            info.exported
        }.getOrDefault(false)
    }

    /** Opens the first maker page that works; then the app's own info page (always present). */
    fun openVendorPage(c: Context, v: Vendor = vendor()): Boolean {
        for (cnm in availablePages(c, v)) {
            val i = Intent().setComponent(cnm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("package_name", c.packageName).putExtra("package_label", "Yumeiro")
            if (runCatching { c.startActivity(i); true }.getOrDefault(false)) return true
        }
        dev.asfakul.yumeiro.ui.screens.extras.PermissionCenter.openAppSettings(c)
        return false
    }

    // endregion

    // region prompt pacing

    fun vendorDone(c: Context): Boolean = prefs(c).getBoolean("vendor_done", false)
    fun markVendorDone(c: Context, done: Boolean = true) = prefs(c).edit().putBoolean("vendor_done", done).apply()

    /**
     * Pure: show the guide on its own? Only when notifications matter to this user (allowed and
     * background checks on), something is wrong (a failing check, a stall, or a strict phone
     * maker not yet confirmed), at most 3 times and at least 7 days apart.
     */
    fun shouldPrompt(s: Status, shownCount: Int, lastShownMs: Long, nowMs: Long): Boolean {
        if (Check.NOTIFICATIONS in s.failing || Check.BACKGROUND_ON in s.failing) return false
        if (s.allGood) return false
        if (shownCount >= 3) return false
        return lastShownMs == 0L || nowMs - lastShownMs >= 7L * 24 * 3_600_000
    }

    fun shouldPrompt(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        shouldPrompt(status(c), prefs(c).getInt("shown", 0), prefs(c).getLong("shown_at", 0L), now)

    fun markPrompted(c: Context) { val p = prefs(c); p.edit().putInt("shown", p.getInt("shown", 0) + 1).putLong("shown_at", System.currentTimeMillis()).apply() }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // endregion
}
