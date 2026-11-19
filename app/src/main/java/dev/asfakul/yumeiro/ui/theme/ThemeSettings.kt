/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("System", "Tsuki Night / Washi Day — follows the device light / dark setting"),
    LIGHT("Washi Day", "Always light — warm paper with a deep amber accent"),
    DARK("Tsuki Night", "Always dark — sumi ink with a moonlight-gold accent"),
    DYNAMIC("Material You", "Colors from your wallpaper (Android 12+)"),
    TIME("Time of day", "Morning, noon, afternoon, evening and midnight moods"),
    AFTERGLOW("Afterglow", "Warm ember accent on deep black"),
    /** F-10 (ADR-017): glass bars, sheets and mini player over a misty moonlit scene. */
    GLASS("Kasumi", "Misty glass bars and player over a moonlit scene"),
}

/**
 * F-10 (ADR-017): how see-through the Kasumi chrome is. No backdrop blur in this release (no GPU cost on any
 * device); GLASS = translucent chrome, TINT = nearly solid, for low-RAM phones, TV, battery saver, a hot phone
 * or "Remove animations / reduce transparency" in accessibility.
 */
enum class GlassTier {
    GLASS, TINT;

    companion object {
        fun choose(lowEnd: Boolean, tv: Boolean, saver: Boolean, hot: Boolean, reduceTransparency: Boolean): GlassTier =
            if (lowEnd || tv || saver || hot || reduceTransparency) TINT else GLASS

        /** Reads the device state once per call (cheap system services, no I/O). */
        fun current(context: Context): GlassTier {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val hot = android.os.Build.VERSION.SDK_INT >= 29 && (pm?.currentThermalStatus ?: 0) >= android.os.PowerManager.THERMAL_STATUS_SEVERE
            val reduce = runCatching {
                android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            return choose(dev.asfakul.yumeiro.DeviceClass.lowEnd, dev.asfakul.yumeiro.Edition.isTv, pm?.isPowerSaveMode == true, hot, reduce)
        }
    }
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
        _mode.value = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        _amoled.value = p.getBoolean(KEY_AMOLED, false)
        _ambient.value = p.getBoolean(KEY_AMBIENT, true)
        _kana.value = p.getBoolean(KEY_KANA, true)
        _accent.value = AccentTone.from(p.getString(KEY_ACCENT, null))
        loaded = true
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
