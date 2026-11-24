/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SPLASH-2 (owner list 31): Yumeiro's signature opening — "the moon rises over the page".
 *
 * Continuity: the Android 12+ system splash shows [R.drawable.ic_launch_moon] at 288 dp in the
 * centre on the theme's page colour (see LaunchLook / values-v31/themes.xml); this screen draws the
 * same drawable at the same size and place on the same flat colour, so the hand-over has no jump and
 * no flash. Then, once: a soft moonlight halo widens behind the mark and the 夢色 wordmark rises in.
 *
 * Timing: the app below is already composing while this shows (MTPlayerApp keeps Home under it), so
 * the splash adds no work to start-up. It holds for [HOLD_MS] (was a fixed 900 ms), a tap skips it,
 * and with Remove animations / reduced motion nothing moves and it leaves after [HOLD_REDUCED_MS].
 */
object SplashTiming {
    const val HOLD_MS = 700L
    const val HOLD_REDUCED_MS = 300L
    fun hold(reduced: Boolean): Long = if (reduced) HOLD_REDUCED_MS else HOLD_MS
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
    val halo = remember { Animatable(if (reduced) 1f else 0f) }
    val words = remember { Animatable(if (reduced) 1f else 0f) }
    val ease = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) } // M3 emphasized decelerate

    LaunchedEffect(Unit) {
        if (!reduced) {
            launch { halo.animateTo(1f, tween(620, easing = ease)) }
            launch { delay(120); words.animateTo(1f, tween(420, easing = ease)) }
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
        // Moonlight halo: one ring that widens and fades, drawn behind the mark (no infinite animation).
        Canvas(Modifier.size(288.dp).testTag("splash_halo")) {
            val t = halo.value
            if (t <= 0f || t >= 1f && !reduced) return@Canvas
            val r = size.minDimension * (0.26f + 0.20f * t)
            val a = if (reduced) 0.10f else 0.22f * (1f - t)
            drawCircle(
                Brush.radialGradient(listOf(p.accent.copy(alpha = a), Color.Transparent), center = center, radius = r * 1.25f),
                radius = r * 1.25f,
            )
            drawCircle(p.accent.copy(alpha = a * 1.4f), radius = r, style = Stroke(width = 1.5.dp.toPx()))
        }
        Image(
            painter = painterResource(R.drawable.ic_launch_moon),
            contentDescription = null,
            modifier = Modifier.size(288.dp).testTag("splash_logo_icon"),
        )
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
