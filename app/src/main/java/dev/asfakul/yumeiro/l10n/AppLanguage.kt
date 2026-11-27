/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.l10n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * F2 (Beta Group S, audits/AUDIT_S.md): the app's language. Fully offline (only translations shipped in the APK, no
 * translation service). Follows the device language by default; a person can pick another in Settings › Appearance ›
 * Language, and the pick is kept. Android 13+ also shows and changes it in the system's per-app language settings
 * (res/xml/locales_config.xml), and both stay in sync. The chosen locale drives text, right-to-left layout, plurals
 * and dates/numbers ([Locale.setDefault]). Missing strings fall back to English (values/strings.xml).
 */
object AppLanguage {
    private const val PREFS = "mt_language"
    private const val KEY = "tag"

    /**
     * Languages that ship translations. [complete] is true only when every string of the interface is translated,
     * reviewed and tested (the owner rule); the others are listed as previews so nobody mistakes them for finished.
     */
    data class Lang(val tag: String, val native: String, val english: String, val complete: Boolean, val rtl: Boolean = false)

    val SHIPPED: List<Lang> = listOf(
        Lang("en", "English", "English", complete = true),
        Lang("bn", "বাংলা", "Bangla", complete = false),
        Lang("ar", "العربية", "Arabic", complete = false, rtl = true),
    )

    @Volatile private var cached: String? = null

    /** The saved pick: a BCP-47 tag, or "" to follow the device. */
    fun saved(context: Context): String {
        cached?.let { return it }
        var tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            // A change made in Android's own per-app language page wins (it is the person's latest choice).
            val sys = context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
            val sysTag = if (sys == null || sys.isEmpty) "" else sys[0].toLanguageTag()
            if (sysTag != tag) { tag = normalize(sysTag); store(context, tag) }
        }
        cached = tag
        return tag
    }

    /** Keeps only shipped languages ("bn-BD" → "bn"); anything else follows the device. Pure. */
    fun normalize(tag: String): String {
        if (tag.isBlank()) return ""
        val t = tag.replace('_', '-')
        SHIPPED.firstOrNull { it.tag.equals(t, true) }?.let { return it.tag }
        val lang = t.substringBefore('-').lowercase()
        return SHIPPED.firstOrNull { it.tag == lang }?.tag ?: ""
    }

    private fun store(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Saves [tag] ("" = device language) and redraws the open screen in it. */
    fun set(context: Context, tag: String) {
        val t = normalize(tag)
        cached = t
        store(context, t)
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales =
                if (t.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(t)
        }
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        // Android 13+ recreates the activity itself after a per-app locale change.
        if (Build.VERSION.SDK_INT < 33) (c as? Activity)?.recreate()
    }

    /** The locale the interface uses now: the pick, or the device's first language. */
    fun current(context: Context): Locale {
        val t = saved(context)
        return if (t.isEmpty()) deviceLocale() else Locale.forLanguageTag(t)
    }

    fun deviceLocale(): Locale = android.content.res.Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** [base] in the picked language (Activity and Application contexts). Device language: [base] unchanged. */
    fun wrap(base: Context): Context {
        val t = saved(base)
        // Back to the device language: undo an earlier pick's default locale (dates, numbers) without a restart.
        if (t.isEmpty()) { Locale.setDefault(deviceLocale()); return base }
        val locale = Locale.forLanguageTag(t)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLocales(LocaleList(locale))
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** For tests: forget the cached pick. */
    internal fun resetCache() { cached = null }
}
