/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.StyleRes
import dev.asfakul.yumeiro.R

/**
 * SPLASH-2 (owner list 31): the colour the app opens on. Before this, the window was always black,
 * so with a light theme every cold start went black (system splash / empty window), then light.
 *
 * The window is painted with the page colour of the theme chosen in Yumeiro before the first frame,
 * and on Android 13+ the system splash is told which launch theme to use next time
 * ([android.window.SplashScreen.setSplashScreenTheme] is remembered by the system), so even the very
 * first pixels match Yumeiro's light, dark or AMOLED theme rather than only the phone's.
 */
object LaunchLook {
    enum class Look(@StyleRes val style: Int, val color: Long) {
        LIGHT(R.style.Theme_Yumeiro_Launch_Light, 0xFFF6F5F2),
        DARK(R.style.Theme_Yumeiro_Launch_Dark, 0xFF0E0E11),
        BLACK(R.style.Theme_Yumeiro_Launch_Black, 0xFF000000),
    }

    /** Pure: which launch look a palette needs. */
    fun lookFor(isDark: Boolean, amoled: Boolean): Look = when {
        !isDark -> Look.LIGHT
        amoled -> Look.BLACK
        else -> Look.DARK
    }

    /** Pure: the theme's darkness without Compose (the same rules as [rememberMtPalette]). */
    fun isDark(mode: ThemeMode, systemDark: Boolean, hour: Int): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AFTERGLOW -> true
        ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> systemDark
        ThemeMode.TIME -> dayPartFor(hour).palette().isDark
    }

    /** Before setContent: the saved theme's look (prefs are already read for the theme itself). */
    fun beforeFirstFrame(activity: Activity) {
        ThemeSettings.init(activity)
        val night = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        apply(activity, lookFor(isDark(ThemeSettings.mode.value, night, hour), ThemeSettings.amoled.value))
    }

    /** Paints the window and (Android 13+) sets the next system splash. Cheap; safe to repeat. */
    fun apply(activity: Activity, look: Look, exact: Int = look.color.toInt()) {
        activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(exact))
        if (Build.VERSION.SDK_INT >= 33) runCatching { activity.splashScreen.setSplashScreenTheme(look.style) }
    }
}
