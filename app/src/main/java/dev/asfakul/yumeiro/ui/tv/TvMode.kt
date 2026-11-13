/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 12: Yumeiro TV is its own app (always this layout). In the phone app the TV ("ten-foot")
 * layout is still chosen at runtime, for boxes running a phone image and for people who
 * sideloaded the phone app on a TV (they're offered Yumeiro TV, see [TvSwitch]).
 *
 * Detection is deliberately conservative. A TV is a device that says so (UI mode television,
 * the leanback feature, Fire TV / Google TV markers) or a big screen with no touchscreen at all.
 * Chromebooks and desktop-mode phones (no touchscreen but a mouse) report `type.pc` and keep the
 * normal layout. Settings → Navigation bar → Layout can force TV or Phone either way, for boxes
 * that report themselves wrongly (common on cheap Android TV boxes running a phone image).
 */
object TvMode {
    enum class Pref(val label: String) { AUTO("Automatic"), TV("TV"), PHONE("Phone & tablet") }

    private const val PREFS = "yumeiro_tv_mode"
    private const val KEY = "layout"

    private val _pref = MutableStateFlow(Pref.AUTO)
    val pref: StateFlow<Pref> = _pref.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _pref.value = Pref.entries.firstOrNull { it.name == raw } ?: Pref.AUTO
        loaded = true
    }

    fun set(context: Context, value: Pref) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
        _pref.value = value
    }

    /** What the device itself reports, ignoring the user's choice. */
    fun detect(context: Context): Boolean {
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return decide(
            uiModeTelevision = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION,
            leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("android.software.leanback_only"),
            vendorTv = pm.hasSystemFeature("amazon.hardware.fire_tv") || pm.hasSystemFeature("com.google.android.tv.installed") ||
                pm.hasSystemFeature("android.hardware.type.television"),
            touchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            pc = pm.hasSystemFeature("android.hardware.type.pc"),
            smallestWidthDp = context.resources.configuration.smallestScreenWidthDp,
        )
    }

    /** Pure decision, unit tested. */
    fun decide(uiModeTelevision: Boolean, leanback: Boolean, vendorTv: Boolean, touchscreen: Boolean, pc: Boolean, smallestWidthDp: Int): Boolean {
        if (pc) return false
        if (uiModeTelevision || leanback || vendorTv) return true
        // A screen of at least TV size that cannot be touched is driven by a remote.
        return !touchscreen && smallestWidthDp >= 360
    }

    /** The TV app is always in TV layout; the phone app follows the choice / detection. */
    fun resolve(pref: Pref, detected: Boolean, tvApp: Boolean = dev.asfakul.yumeiro.Edition.isTv): Boolean = if (tvApp) true else when (pref) {
        Pref.AUTO -> detected
        Pref.TV -> true
        Pref.PHONE -> false
    }
}

/** True while the ten-foot layout is active (left rail, larger focus cues, remote keys). */
val LocalTvMode = staticCompositionLocalOf { false }
