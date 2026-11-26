/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SPLASH-2 (owner list 31) + F4 (Beta chapter): Yumeiro's opening, "the moon rises over the page",
 * now with the mark itself in motion (YuMark draws it from its own geometry).
 *
 * - Android 7–11 (no system splash icon): the crescent is drawn in by a sweep of moonlight, the play
 *   triangle slides and grows into place, a glow blooms behind them and the 夢色 wordmark rises in.
 * - Android 12+: the system splash has just shown the whole mark at 288 dp on the page colour, so the
 *   in-app splash starts from that exact picture (no jump, no flash) and the triangle "presses play"
 *   while the glow blooms, then the wordmark rises in.
 *
 * Timing: the app below is already composing while this shows (MTPlayerApp keeps Home under it), so
 * the splash adds no work to start-up. It holds for [SplashTiming.hold], a tap skips it, and with
 * Remove animations / reduced motion nothing moves and it leaves after [SplashTiming.HOLD_REDUCED_MS].
 * B1: it is the only place the big mark is drawn; once it has gone it is removed from the tree.
 */
object SplashTiming {
    const val HOLD_MS = 860L
    const val HOLD_PRESS_MS = 760L
    const val DRAW_MS = 720
    const val PRESS_MS = 620
    const val HOLD_REDUCED_MS = 300L
    /** Android 12+ starts from the system splash's full mark. */
    fun startsFromSystemMark(sdk: Int = android.os.Build.VERSION.SDK_INT): Boolean = sdk >= 31
    fun hold(reduced: Boolean, sdk: Int = android.os.Build.VERSION.SDK_INT): Long =
        if (reduced) HOLD_REDUCED_MS else if (startsFromSystemMark(sdk)) HOLD_PRESS_MS else HOLD_MS
}

/**
 * B1 (Beta): the in-app splash plays once per process. Reopening the app while it is still in
 * memory (back-exit then launcher, recents, a new activity) used to replay the whole splash about
 * two seconds after the system splash had already gone, so the logo appeared again after the splash.
 */
object SplashGate {
    @Volatile private var played = false
    fun shouldShow(wanted: Boolean): Boolean = wanted && !played
    fun markPlayed() { played = true }
    fun resetForTest() { played = false }
}

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    val context = LocalContext.current
    val reduced = dev.asfakul.yumeiro.ui.components.reducedMotion() ||
        remember { dev.asfakul.yumeiro.ui.components.isReducedMotion(context) }
    val fromSystem = remember { SplashTiming.startsFromSystemMark() }
    val mark = remember { Animatable(if (reduced) 1f else 0f) }
    val words = remember { Animatable(if (reduced) 1f else 0f) }
    val ease = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) } // M3 emphasized decelerate

    LaunchedEffect(Unit) {
        if (!reduced) {
            launch { mark.animateTo(1f, tween(if (fromSystem) SplashTiming.PRESS_MS else SplashTiming.DRAW_MS, easing = androidx.compose.animation.core.LinearEasing)) }
            launch { delay(if (fromSystem) 160 else 360); words.animateTo(1f, tween(420, easing = ease)) }
        }
        delay(SplashTiming.hold(reduced))
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Flat page colour (the system splash's colour); the backdrop scene appears as this fades out.
            .background(p.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSplashFinished() }
            .semantics { contentDescription = "Yumeiro is opening" }
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center,
    ) {
        // F4: the mark in motion (same geometry, colours, size and place as the system splash icon).
        dev.asfakul.yumeiro.ui.components.YuMark(
            Modifier.size(288.dp).testTag("splash_logo_icon"),
            glowColor = p.accent,
        ) {
            val t = mark.value
            when {
                reduced -> dev.asfakul.yumeiro.ui.components.YuMarkFrame.Full
                fromSystem -> dev.asfakul.yumeiro.ui.components.YuMarkFrame.press(t)
                else -> dev.asfakul.yumeiro.ui.components.YuMarkFrame.drawIn(t)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = 112.dp)
                .graphicsLayer {
                    alpha = words.value
                    translationY = (1f - words.value) * 12.dp.toPx()
                }
                .testTag("splash_wordmark"),
        ) {
            Text("夢色", color = p.accent, fontSize = 13.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Medium)
            Text("Yumeiro", color = p.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text("Music · Video · Streams", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            text = "by Asfakul Siam",
            color = p.textTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = words.value },
        )
    }
}
