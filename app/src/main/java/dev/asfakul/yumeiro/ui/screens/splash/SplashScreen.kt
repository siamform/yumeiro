/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.splash

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.withFrameNanos
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
 * - B17 (r27): the logo's two parts move differently: the crescent turns on an orbit, the triangle presses and
 *   pushes along its play axis. The splash ends with its own leave (mark drifts forward and dissolves), so the
 *   logo is gone before Home takes over.
 * - B17: at least 1.5 s on screen ([SplashTiming.hold] + the exit), a tap skips it, and with Remove
 *   animations / reduced motion nothing moves: the still mark and wordmark show, then fade.
 * - B1/B17: it is the only place the big mark is drawn; after its exit it leaves the tree, and it plays once
 *   per process ([SplashGate]), so no logo is left behind or shown again.
 */
object SplashTiming {
    /** B17: the arrival (wash, stars, the crescent's orbit, the triangle's push, words). */
    const val ANIM_MS = 1300
    /**
     * B17: the leave, still inside the splash: the mark drifts toward the viewer and dissolves (crescent keeps
     * turning, triangle pushes on), words sink. The logo is gone before the splash hands over, so a slow first
     * frame of Home can never freeze a logo on screen (device run 46078131306: logo still there after it left).
     */
    const val LEAVE_MS = 400
    /** A tap skips to a shorter leave. */
    const val SKIP_LEAVE_MS = 200
    /** B17: time from the start of the motion until the splash hands over (≥ 1.5 s, owner rule). */
    const val HOLD_MS = 1700L
    const val HOLD_PRESS_MS = 1700L
    const val HOLD_REDUCED_MS = 1500L
    /** After the hand-over only the plain page colour is left; it fades into Home. */
    const val EXIT_MS = 180
    const val EXIT_REDUCED_MS = 200
    /**
     * B17: before the motion starts the splash waits (still, on its first frame, which matches the
     * system splash) until Home's first heavy composition is done and the main thread draws calmly:
     * [CALM_FRAMES] frames in a row each under [CALM_GAP_MS], or [SETTLE_MAX_MS] at most.
     * Otherwise start-up work froze the 1.5 s motion (2 frames drawn of 90 on an emulator).
     */
    const val CALM_FRAMES = 3
    const val CALM_GAP_MS = 25L
    const val SETTLE_MAX_MS = 1200L
    /** Pure → unit-tested: true once the last [CALM_FRAMES] frame gaps (ns) are all calm. */
    fun calm(gapsNs: List<Long>): Boolean =
        gapsNs.size >= CALM_FRAMES && gapsNs.takeLast(CALM_FRAMES).all { it < CALM_GAP_MS * 1_000_000L }
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
        /** Star and wash strength left (1 = all; 0 = gone, the leave). */
        val sky: Float = 1f,
        /** Extra scale of the mark (the leave drifts it toward the viewer). */
        val markScale: Float = 1f,
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

    /**
     * B17: the leave at [p] 0..1, applied on the settled frame: the crescent keeps turning on its orbit, the
     * triangle pushes forward, the mark grows toward the viewer and dissolves (fully gone by 70 %), words sink.
     */
    fun leave(settled: Frame, p: Float): Frame {
        if (p <= 0f) return settled
        val e = ease(p)
        val fade = 1f - ease(phase(p, 0f, 0.7f))
        return settled.copy(
            mark = settled.mark.copy(
                moonTurn = settled.mark.moonTurn + 26f * e,
                playDx = settled.mark.playDx + 14f * e,
                glow = settled.mark.glow * fade,
                alpha = fade,
            ),
            markScale = 1f + 0.32f * e,
            title = settled.title * fade, tagline = settled.tagline * fade, byline = settled.byline * fade,
            sky = fade,
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
    /** When the motion started (after the main thread calmed down, see [SplashTiming.calm]). */
    @Volatile var motionAtNs = 0L
    /** When the leave started (the mark starts to dissolve). */
    @Volatile var leaveAtNs = 0L
    /** Frames the splash drew with a new motion value (arrival or leave): proof the motion ran, not a still. */
    @Volatile var movingFrames = 0
    @Volatile internal var lastDrawn = -1f
    fun reset() { shownAtNs = 0L; finishedAtNs = 0L; disposedAtNs = 0L; motionAtNs = 0L; leaveAtNs = 0L; movingFrames = 0; lastDrawn = -1f }
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
    val leaving = remember { Animatable(0f) }
    val opening = stringResource(R.string.splash_opening)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val finish = androidx.compose.runtime.rememberUpdatedState {
        if (SplashProbe.finishedAtNs == 0L) SplashProbe.finishedAtNs = System.nanoTime()
        onSplashFinished()
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { SplashProbe.disposedAtNs = System.nanoTime() } }
    LaunchedEffect(Unit) {
        // B17 (run 46105382358, tv-api24): the effect starts before the first frame reaches the screen; on a slow
        // device the page stayed blank for ~0.5 s of the hold. The hold starts once the first frame is drawn.
        withFrameNanos { }
        withFrameNanos { }
        SplashProbe.shownAtNs = System.nanoTime()
        if (!reduced) {
            val gaps = ArrayList<Long>()
            var last = withFrameNanos { it }
            val until = last + SplashTiming.SETTLE_MAX_MS * 1_000_000L
            while (!SplashTiming.calm(gaps) && last < until) {
                val t = withFrameNanos { it }
                gaps += t - last; last = t
            }
        }
        SplashProbe.motionAtNs = System.nanoTime()
        if (reduced) delay(SplashTiming.hold(true))
        else {
            // Arrival, then the leave: both on the frame clock, so the hand-over always comes after the frame
            // where the logo is gone (frame-based, not a timer that can fire while a frame is still pending).
            // On the frame clock itself, not a tween: a tween follows the process's animator duration scale, which
            // on Android 7 is not refreshed in a running process (run 46105382358: the arrival ran in ~0 ms and
            // the splash held 555 ms). Whether to animate at all is decided above from the user's setting.
            clock.runOnFrames(SplashTiming.ANIM_MS)
            SplashProbe.leaveAtNs = System.nanoTime()
            leaving.runOnFrames(SplashTiming.LEAVE_MS)
            withFrameNanos { }
        }
        finish.value()
    }
    fun frame() = if (reduced) SplashMotion.Still else SplashMotion.leave(SplashMotion.at(clock.value, fromSystem), leaving.value)
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
                val v = t + leaving.value
                if (v != SplashProbe.lastDrawn) { if (SplashProbe.lastDrawn >= 0f) SplashProbe.movingFrames++; SplashProbe.lastDrawn = v }
                if (f.wash > 0f && f.sky > 0f) drawRect(wash, alpha = f.wash * f.sky)
                val rise = 10.dp.toPx()
                for (s in SplashMotion.stars) {
                    val a = if (reduced) 0.3f else SplashMotion.starAlpha(s, t) * f.sky
                    if (a <= 0f) continue
                    drawCircle(starColor, radius = s.r.dp.toPx(), center = Offset(s.x * size.width, s.y * size.height - rise * t), alpha = a)
                }
            }
            // A tap skips the rest, but still through a short leave, so the logo never stays behind.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (reduced) finish.value()
                else scope.launch {
                    clock.snapTo(1f)
                    leaving.runOnFrames(SplashTiming.SKIP_LEAVE_MS)
                    withFrameNanos { }
                    finish.value()
                }
            }
            .semantics { contentDescription = opening }
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center,
    ) {
        // F4: the mark in motion (same geometry, colours, size and place as the system splash icon).
        dev.asfakul.yumeiro.ui.components.YuMark(
            Modifier
                .size(288.dp)
                .graphicsLayer { val f = frame(); translationY = -f.markRiseDp.dp.toPx(); scaleX = f.markScale; scaleY = f.markScale }
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

/** Runs this value from where it is to 1 over [ms] of frame time (vsync timestamps), one step per frame. */
private suspend fun Animatable<Float, *>.runOnFrames(ms: Int) {
    val from = value
    val start = withFrameNanos { it }
    while (true) {
        val t = withFrameNanos { it }
        val f = ((t - start) / 1_000_000.0 / ms).coerceIn(0.0, 1.0).toFloat()
        snapTo(from + (1f - from) * f)
        if (f >= 1f) break
    }
}
