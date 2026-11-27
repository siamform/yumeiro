/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.splash

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * SPLASH-2 (owner list 31) + F4 + B17 (Beta): Yumeiro's opening, "the moon rises over a dreaming sky".
 *
 * - One clock (0..1 over [SplashTiming.ANIM_MS]) drives everything, and it is read only in draw and
 *   graphicsLayer blocks: no recomposition per frame, so the splash keeps 60 fps while Home composes below.
 * - A twilight wash rises from the bottom in the theme's accent, a few stars twinkle and drift up, the mark
 *   rises a little while its glow breathes, and the wordmark, tagline and by-line rise in one after another.
 * - Android 7–11 (no system splash icon): the crescent is drawn in by a sweep of moonlight first.
 *   Android 12+: the system splash has just shown the whole mark at 288 dp on the page colour, so the
 *   in-app splash starts from that exact picture (no jump, no flash) and the triangle "presses play".
 * - B17: at least 1.5 s on screen ([SplashTiming.hold] + the exit), a tap skips it, and with Remove
 *   animations / reduced motion nothing moves: the still mark and wordmark show, then fade.
 * - B1/B17: it is the only place the big mark is drawn; after its exit it leaves the tree, and it plays once
 *   per process ([SplashGate]), so no logo is left behind or shown again.
 */
object SplashTiming {
    /** B17: the whole motion (wash, stars, mark, words). */
    const val ANIM_MS = 1500
    /** B17: time before the splash starts to leave (≥ 1.5 s, owner rule), then [EXIT_MS] of fade. */
    const val HOLD_MS = 1650L
    const val HOLD_PRESS_MS = 1650L
    const val HOLD_REDUCED_MS = 1500L
    const val EXIT_MS = 420
    const val EXIT_REDUCED_MS = 200
    /** Android 12+ starts from the system splash's full mark. */
    fun startsFromSystemMark(sdk: Int = android.os.Build.VERSION.SDK_INT): Boolean = sdk >= 31
    fun hold(reduced: Boolean, sdk: Int = android.os.Build.VERSION.SDK_INT): Long =
        if (reduced) HOLD_REDUCED_MS else if (startsFromSystemMark(sdk)) HOLD_PRESS_MS else HOLD_MS
    /** Total time on screen, exit included. */
    fun onScreen(reduced: Boolean, sdk: Int = android.os.Build.VERSION.SDK_INT): Long =
        hold(reduced, sdk) + if (reduced) EXIT_REDUCED_MS else EXIT_MS
}

/** B17: what every layer shows at clock [t] 0..1 (pure, so it is unit-tested without a screen). */
object SplashMotion {
    data class Frame(
        val mark: dev.asfakul.yumeiro.ui.components.YuMarkFrame,
        /** Upward drift of the mark, in dp. */
        val markRiseDp: Float,
        val wash: Float,
        val title: Float,
        val tagline: Float,
        val byline: Float,
    )

    private fun ease(t: Float) = dev.asfakul.yumeiro.ui.components.YuMarkGeometry.ease(t)
    private fun phase(t: Float, a: Float, b: Float) = dev.asfakul.yumeiro.ui.components.YuMarkGeometry.phase(t, a, b)
    private fun bell(t: Float) = dev.asfakul.yumeiro.ui.components.YuMarkGeometry.bell(t)

    const val RISE_DP = 8f
    val Still = Frame(dev.asfakul.yumeiro.ui.components.YuMarkFrame.Full, 0f, 0.6f, 1f, 1f, 1f)

    fun at(t: Float, fromSystem: Boolean): Frame {
        val base = if (fromSystem) dev.asfakul.yumeiro.ui.components.YuMarkFrame.press(phase(t, 0.08f, 0.5f))
        else dev.asfakul.yumeiro.ui.components.YuMarkFrame.drawIn(phase(t, 0f, 0.6f))
        // After the first bloom the glow keeps breathing once, softly, until the splash leaves.
        val breath = 0.45f * bell(phase(t, 0.45f, 1f))
        return Frame(
            mark = base.copy(glow = maxOf(base.glow, breath)),
            markRiseDp = RISE_DP * ease(t),
            wash = 0.6f * ease(phase(t, 0f, 0.7f)),
            title = ease(phase(t, if (fromSystem) 0.18f else 0.4f, if (fromSystem) 0.5f else 0.7f)),
            tagline = ease(phase(t, if (fromSystem) 0.3f else 0.5f, if (fromSystem) 0.62f else 0.8f)),
            byline = ease(phase(t, 0.55f, 0.9f)),
        )
    }

    /** One star: place (fractions of the screen), size (dp) and when it twinkles (clock start). */
    data class Star(val x: Float, val y: Float, val r: Float, val start: Float)

    /** A fixed, hand-free field (same every launch): a small LCG, spread away from the mark in the centre. */
    val stars: List<Star> by lazy {
        var seed = 0x2545F491L
        fun next(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return seed / 2147483647f }
        val out = ArrayList<Star>(18)
        while (out.size < 18) {
            val x = next(); val y = next() * 0.9f
            val dx = x - 0.5f; val dy = y - 0.45f
            if (dx * dx + dy * dy < 0.045f) continue
            out += Star(x, y, 1f + next() * 1.3f, next() * 0.55f)
        }
        out
    }

    /** Star alpha at clock [t]: fades in, twinkles once, settles at a soft glow. */
    fun starAlpha(s: Star, t: Float): Float {
        val p = phase(t, s.start, s.start + 0.45f)
        return (0.25f * ease(p) + 0.55f * bell(p)).coerceIn(0f, 1f)
    }
}

/**
 * B1 (Beta): the in-app splash plays once per process. Reopening the app while it is still in
 * memory (back-exit then launcher, recents, a new activity) used to replay the whole splash about
 * two seconds after the system splash had already gone, so the logo appeared again after the splash.
 */
/** B17 test probe: how often [SplashScreen] composed (it must not recompose per animation frame). */
object SplashProbe {
    @Volatile var compositions = 0
    /** System.nanoTime() when the splash started, asked to leave, and left the tree (0 = not yet). */
    @Volatile var shownAtNs = 0L
    @Volatile var finishedAtNs = 0L
    @Volatile var disposedAtNs = 0L
    fun reset() { shownAtNs = 0L; finishedAtNs = 0L; disposedAtNs = 0L }
}

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
    // B17: the one clock. Animatable (not a target-state API) because it is a single imperative timeline.
    val clock = remember { Animatable(if (reduced) 1f else 0f) }
    val opening = stringResource(R.string.splash_opening)

    val finish = androidx.compose.runtime.rememberUpdatedState {
        if (SplashProbe.finishedAtNs == 0L) SplashProbe.finishedAtNs = System.nanoTime()
        onSplashFinished()
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SplashProbe.disposedAtNs = System.nanoTime() } }
    LaunchedEffect(Unit) {
        SplashProbe.shownAtNs = System.nanoTime()
        if (!reduced) launch { clock.animateTo(1f, tween(SplashTiming.ANIM_MS, easing = LinearEasing)) }
        delay(SplashTiming.hold(reduced))
        finish.value()
    }
    fun frame() = if (reduced) SplashMotion.Still else SplashMotion.at(clock.value, fromSystem)
    androidx.compose.runtime.SideEffect { SplashProbe.compositions++ }

    val accent = p.accent
    val wash = remember(accent) { Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to accent.copy(alpha = 0.22f)) }
    val starColor = remember(p.isDark, accent) { if (p.isDark) dev.asfakul.yumeiro.ui.components.YuMarkGeometry.moonColors[0] else accent }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Page colour (the system splash's colour) with the twilight wash and the stars on top.
            .background(p.background)
            .drawBehind {
                val t = clock.value
                val f = frame()
                if (f.wash > 0f) drawRect(wash, alpha = f.wash)
                val rise = 10.dp.toPx()
                for (s in SplashMotion.stars) {
                    val a = if (reduced) 0.3f else SplashMotion.starAlpha(s, t)
                    if (a <= 0f) continue
                    drawCircle(starColor, radius = s.r.dp.toPx(), center = Offset(s.x * size.width, s.y * size.height - rise * t), alpha = a)
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish.value() }
            .semantics { contentDescription = opening }
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center,
    ) {
        // F4: the mark in motion (same geometry, colours, size and place as the system splash icon).
        dev.asfakul.yumeiro.ui.components.YuMark(
            Modifier
                .size(288.dp)
                .graphicsLayer { translationY = -frame().markRiseDp.dp.toPx() }
                .testTag("splash_logo_icon"),
            glowColor = accent,
        ) { frame().mark }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 112.dp).testTag("splash_wordmark"),
        ) {
            Text(
                stringResource(R.string.text_2), color = accent, fontSize = 13.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { val v = frame().title; alpha = v; translationY = (1f - v) * 14.dp.toPx() },
            )
            Text(
                stringResource(R.string.app_name), color = p.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                modifier = Modifier.graphicsLayer { val v = frame().title; alpha = v; translationY = (1f - v) * 14.dp.toPx() },
            )
            Text(
                stringResource(R.string.music_video_streams), color = p.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp).graphicsLayer { val v = frame().tagline; alpha = v; translationY = (1f - v) * 10.dp.toPx() },
            )
        }
        Text(
            text = stringResource(R.string.by_asfakul_siam),
            color = p.textTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = frame().byline },
        )
    }
}
