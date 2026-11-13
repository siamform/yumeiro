/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Yumeiro motion tokens (round 3). Durations and curves follow Material 3's easing & duration
 * tokens (https://m3.material.io/styles/motion/easing-and-duration/tokens-specs): small things
 * move fast, large things a bit slower, exits are shorter than entrances.
 *
 * Reduced motion: when the system "Remove animations" setting is on (animator duration scale 0),
 * [LocalReducedMotion] is true. Compose already scales most built-in animations to 0 in that case;
 * our own decorative effects (pulse rings, staggered entrances, slides) check the flag and fall
 * back to a plain fade or no movement, as M3 recommends for reduced-motion users.
 */
object YuMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 150
    const val MEDIUM = 300
    const val ENTER = 400
    const val EXIT = 200

    /** Tween for an element entering (or a snap when reduced motion is on). */
    fun <T> enter(reduced: Boolean, ms: Int = ENTER, delay: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(ms, delay, EmphasizedDecelerate)

    fun <T> exit(reduced: Boolean, ms: Int = EXIT): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(ms, easing = EmphasizedAccelerate)

    fun <T> move(reduced: Boolean, ms: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(ms, easing = Emphasized)
}

/** True when the user asked the system to remove animations. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean = LocalReducedMotion.current

/** Reads Settings.Global.ANIMATOR_DURATION_SCALE (0 = "Remove animations" / animations off). */
fun isReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)
