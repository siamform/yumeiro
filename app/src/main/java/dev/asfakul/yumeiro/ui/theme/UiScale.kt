/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * F12 (Beta chapter, audits/AUDIT_Q.md): "Compact text and corners", one switch in Settings › Appearance.
 *
 * Text: every sp in the app (theme styles and the ~1,100 hand-set sizes) is drawn 8 % smaller by scaling the
 * activity's font scale once ([wrap], from MainActivity.attachBaseContext), so dialogs, sheets and pop-ups follow
 * too. A person who raised Android's font size above [MAX_SYSTEM_SCALE] keeps their size: compact never fights an
 * accessibility choice.
 *
 * Corners: [yuRound] trims radii above 8 dp by about a fifth (10→8, 12→9, 14→11, 16→12, 18→14, 20→16, 24→19,
 * 28→22), the range SkyStream and VyseTV use; small radii (badges, 4–8 dp) and pills stay as they are.
 *
 * Phones start compact; Yumeiro TV starts standard (ten-foot reading distance).
 */
object UiScale {
    private const val PREFS = "mt_appearance"
    private const val KEY = "compact_ui"
    const val TEXT_FACTOR = 0.92f
    const val MAX_SYSTEM_SCALE = 1.15f

    /** Snapshot state, so inline [yuRound] shapes redraw when the switch moves. */
    var compact by mutableStateOf(defaultCompact(dev.asfakul.yumeiro.Edition.isTv))
        private set
    @Volatile private var loaded = false

    fun defaultCompact(tv: Boolean): Boolean = !tv

    fun init(context: Context) {
        if (loaded) return
        compact = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, defaultCompact(dev.asfakul.yumeiro.Edition.isTv))
        loaded = true
    }

    fun set(context: Context, on: Boolean) {
        compact = on
        loaded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** The factor for Android's font scale [system]; 1 when off or when the person chose large text. Pure. */
    fun textFactor(system: Float, compact: Boolean): Float =
        if (compact && system <= MAX_SYSTEM_SCALE) TEXT_FACTOR else 1f

    /** A corner radius in dp as drawn. Pure. */
    fun corner(r: Float, compact: Boolean): Float =
        if (!compact || r <= 8f) r else max(8f, (r * 0.78f).roundToInt().toFloat())

    fun cornerDp(r: Int): Dp = corner(r.toFloat(), compact).dp

    /** [base] with the compact font scale applied (the activity's context). */
    fun wrap(base: Context): Context {
        init(base)
        val cfg = Configuration(base.resources.configuration)
        val f = textFactor(cfg.fontScale, compact)
        if (f == 1f) return base
        cfg.fontScale = cfg.fontScale * f
        return base.createConfigurationContext(cfg)
    }

    private val compactShapes = arrayOfNulls<RoundedCornerShape>(65)
    private val standardShapes = arrayOfNulls<RoundedCornerShape>(65)

    internal fun shape(r: Int): RoundedCornerShape {
        val on = compact
        if (r !in 0..64) return RoundedCornerShape(corner(r.toFloat(), on).dp)
        val cache = if (on) compactShapes else standardShapes
        return cache[r] ?: RoundedCornerShape(corner(r.toFloat(), on).dp).also { cache[r] = it }
    }
}

/** F12: the app's rounded corner of [r] dp, trimmed in compact mode (see [UiScale]); shapes are shared, not rebuilt. */
fun yuRound(r: Int): RoundedCornerShape = UiScale.shape(r)
