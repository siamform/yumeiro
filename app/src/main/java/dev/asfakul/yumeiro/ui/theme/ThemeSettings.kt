/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("System", "Tsuki Night / Washi Day — follows the device light / dark setting"),
    LIGHT(tr(R.string.washi_day, "Washi Day"), tr(R.string.always_light_warm_paper_with_a, "Always light — warm paper with a deep amber accent")),
    DARK(tr(R.string.tsuki_night, "Tsuki Night"), tr(R.string.always_dark_sumi_ink_with_a, "Always dark — sumi ink with a moonlight-gold accent")),
    DYNAMIC(tr(R.string.material_you, "Material You"), tr(R.string.colors_from_your_wallpaper_android_12, "Colors from your wallpaper (Android 12+)")),
    TIME(tr(R.string.time_of_day, "Time of day"), tr(R.string.morning_noon_afternoon_evening_and_midnight, "Morning, noon, afternoon, evening and midnight moods")),
    AFTERGLOW("Afterglow", "Warm ember accent on deep black"),
}

enum class DayPart(val label: String) { MORNING("Morning"), NOON("Noon"), AFTERNOON("Afternoon"), EVENING("Evening"), MIDNIGHT("Midnight") }

fun dayPartFor(hour: Int): DayPart = when (hour) {
    in 5..10 -> DayPart.MORNING
    in 11..14 -> DayPart.NOON
    in 15..17 -> DayPart.AFTERNOON
    in 18..21 -> DayPart.EVENING
    else -> DayPart.MIDNIGHT
}

fun currentDayPart(): DayPart = dayPartFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

fun DayPart.palette(): MtPalette = when (this) {
    DayPart.MORNING -> Palettes.Morning
    DayPart.NOON -> Palettes.Noon
    DayPart.AFTERNOON -> Palettes.Afternoon
    DayPart.EVENING -> Palettes.Evening
    DayPart.MIDNIGHT -> Palettes.Midnight
}

/** Persisted appearance settings. */
object ThemeSettings {
    private const val PREFS = "mt_appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_AMOLED = "amoled_black"
    private const val KEY_AMBIENT = "ambient_artwork"
    private const val KEY_KANA = "kana_accents"
    private const val KEY_ACCENT = "accent_tone"

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()
    private val _amoled = MutableStateFlow(false)
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    private val _ambient = MutableStateFlow(true)
    /** Tint the page background with the colour of what's playing (Spotify / Apple Music style). */
    val ambient: StateFlow<Boolean> = _ambient.asStateFlow()
    private val _kana = MutableStateFlow(true)
    /** Small Japanese accents (kana overlines such as 設定 or 音楽) next to page titles. */
    val kana: StateFlow<Boolean> = _kana.asStateFlow()
    private val _accent = MutableStateFlow(AccentTone.GOLD)
    /** TH-1: accent colour over the chosen theme (gold = the theme's own). */
    val accent: StateFlow<AccentTone> = _accent.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mode.value = modeFrom(p.getString(KEY_MODE, null))
        _amoled.value = p.getBoolean(KEY_AMOLED, false)
        _ambient.value = p.getBoolean(KEY_AMBIENT, true)
        _kana.value = p.getBoolean(KEY_KANA, true)
        _accent.value = AccentTone.from(p.getString(KEY_ACCENT, null))
        loaded = true
    }

    /**
     * R19 THEME-1: the saved theme name. The Kasumi glass theme ("GLASS", 1.9.0-2.1.0) was removed (owner
     * 2026-11-23); it was a dark theme, so people who chose it get Tsuki Night, not a light System theme.
     */
    fun modeFrom(saved: String?): ThemeMode = when (saved) {
        null -> ThemeMode.SYSTEM
        "GLASS" -> ThemeMode.DARK
        else -> runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setMode(context: Context, mode: ThemeMode) {
        _mode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setAccent(context: Context, tone: AccentTone) {
        _accent.value = tone
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, tone.name).apply()
    }

    fun setKana(context: Context, enabled: Boolean) {
        _kana.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_KANA, enabled).apply()
    }

    fun setAmoled(context: Context, enabled: Boolean) {
        _amoled.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    fun setAmbient(context: Context, enabled: Boolean) {
        _ambient.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AMBIENT, enabled).apply()
    }
}
