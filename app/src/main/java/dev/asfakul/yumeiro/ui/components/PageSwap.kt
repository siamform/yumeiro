/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Round 8: one motion vocabulary for every place a page swaps its content in place.
 *
 * Before this, in-page tabs (Sources hub, Extension market, Music tabs), the Videos and Music
 * source switch (Yumix · YouTube · Device · Live TV) and the Yumix extension page replaced their
 * content with a bare `when`, so the new content appeared in the same frame as the tap: no
 * motion at all, which read as "click and load". These helpers give each kind of change the
 * Material 3 pattern that fits it:
 *
 * - [TabSwap]: siblings with an order (tabs, extension channels) use the shared X axis: the new
 *   content glides in from the side of the tab that was tapped while the old one drifts out and
 *   fades. The direction tells the user where they are.
 * - [FadeThrough]: unrelated destinations (the source switch) fade through: the old content
 *   leaves fast, the new one fades in with a slight scale-up.
 *
 * Reduced motion ("Remove animations") turns both into a short cross-fade with no movement.
 * Exits are shorter than entrances (about 60 %), as the M3 duration tokens recommend.
 */
object PageMotion {
    /** Sub-page push (NavHost). Long enough to read as a slide, decelerating, never front-loaded. */
    const val PUSH_MS = 420
    /** Back is quicker than forward. */
    const val POP_MS = 320
    const val TAB_ENTER_MS = 300
    const val TAB_EXIT_MS = 180
    const val FADE_IN_MS = 240
    const val FADE_OUT_MS = 110

    fun <S> sharedX(scope: AnimatedContentTransitionScope<S>, forward: Boolean, reduced: Boolean): ContentTransform = with(scope) {
        if (reduced) return@with fadeIn(tween(150)) togetherWith fadeOut(tween(90))
        val dir = if (forward) 1 else -1
        (slideInHorizontally(tween(TAB_ENTER_MS, easing = YuMotion.Emphasized)) { w -> dir * w / 5 } +
            fadeIn(tween(TAB_ENTER_MS - 60, delayMillis = 60, easing = YuMotion.Standard))) togetherWith
            (slideOutHorizontally(tween(TAB_EXIT_MS, easing = YuMotion.EmphasizedAccelerate)) { w -> -dir * w / 8 } +
                fadeOut(tween(TAB_EXIT_MS - 40, easing = YuMotion.EmphasizedAccelerate)))
    }

    fun <S> fadeThrough(scope: AnimatedContentTransitionScope<S>, reduced: Boolean): ContentTransform = with(scope) {
        if (reduced) return@with fadeIn(tween(150)) togetherWith fadeOut(tween(90))
        (fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS - 20, easing = YuMotion.Standard)) +
            scaleIn(tween(FADE_IN_MS + 40, delayMillis = FADE_OUT_MS - 20, easing = YuMotion.Emphasized), initialScale = 0.96f)) togetherWith
            fadeOut(tween(FADE_OUT_MS, easing = YuMotion.EmphasizedAccelerate))
    }

    // ---- NavHost (sub-pages) -------------------------------------------------------------------
    // The page arrives with the M3 emphasized curve over 420 ms. The old "emphasized decelerate"
    // covered ~70 % of the distance in the first 80 ms, so a page looked like it had simply
    // appeared. The page underneath drifts a third of the way with a slight fade, so the motion is
    // visible from both sides and nothing is ever drawn half-transparent on top of another page.
    fun push(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(150)) else slideInHorizontally(tween(PUSH_MS, easing = YuMotion.Emphasized)) { it }

    fun pushExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(120))
        else slideOutHorizontally(tween(PUSH_MS, easing = YuMotion.Emphasized)) { -it / 3 } +
            fadeOut(tween(PUSH_MS, easing = YuMotion.Standard), targetAlpha = 0.55f)

    fun popEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(150))
        else slideInHorizontally(tween(POP_MS, easing = YuMotion.Emphasized)) { -it / 3 } +
            fadeIn(tween(POP_MS, easing = YuMotion.Standard), initialAlpha = 0.55f)

    fun popExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(120)) else slideOutHorizontally(tween(POP_MS, easing = YuMotion.EmphasizedAccelerate)) { it }
}

/**
 * Swaps in-page content along the X axis. [order] gives each state its position (the tab index),
 * so moving to a later tab slides left and to an earlier one slides right.
 */
@Composable
fun <T> TabSwap(
    target: T,
    order: (T) -> Int,
    modifier: Modifier = Modifier,
    label: String = "tab_swap",
    content: @Composable (T) -> Unit,
) {
    val reduced = reducedMotion()
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            PageMotion.sharedX(this, forward = order(targetState) >= order(initialState), reduced = reduced)
                .using(null)
        },
        label = label,
    ) { state -> content(state) }
}

/** Fades through to unrelated content (Yumix → YouTube → Device → Live TV, Music modes). */
@Composable
fun <T> FadeThrough(
    target: T,
    modifier: Modifier = Modifier,
    label: String = "fade_through",
    content: @Composable (T) -> Unit,
) {
    val reduced = reducedMotion()
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = { PageMotion.fadeThrough(this, reduced).using(null) },
        label = label,
    ) { state -> content(state) }
}
