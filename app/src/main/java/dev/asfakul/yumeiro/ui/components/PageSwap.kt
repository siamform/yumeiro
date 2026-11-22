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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
    /**
     * OWN-14: Back mirrors the push exactly (same length, same curve, same distances, played in
     * reverse). It used to be 320 ms with an accelerating curve on the top page and a different
     * curve on the page below, so the two pages ran out of step and closing looked unlike opening.
     */
    const val POP_MS = PUSH_MS
    /** How far the page underneath drifts (a third of the width) and how dim it gets. */
    const val UNDER_SHIFT_DIV = 3
    const val UNDER_ALPHA = 0.55f
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
        if (reduced) fadeIn(tween(REDUCED_MS)) else slideInHorizontally(tween(PUSH_MS, easing = YuMotion.Emphasized)) { it }

    fun pushExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(REDUCED_MS))
        else slideOutHorizontally(tween(PUSH_MS, easing = YuMotion.Emphasized)) { -it / UNDER_SHIFT_DIV } +
            fadeOut(tween(PUSH_MS, easing = YuMotion.Standard), targetAlpha = UNDER_ALPHA)

    fun popEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(REDUCED_MS))
        else slideInHorizontally(tween(POP_MS, easing = YuMotion.Emphasized)) { -it / UNDER_SHIFT_DIV } +
            fadeIn(tween(POP_MS, easing = YuMotion.Standard), initialAlpha = UNDER_ALPHA)

    fun popExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(REDUCED_MS)) else slideOutHorizontally(tween(POP_MS, easing = YuMotion.Emphasized)) { it }

    /** Reduced motion: the same short cross-fade both ways. */
    const val REDUCED_MS = 150

    // ---- Full-screen pages outside the NavHost (Inbox / Support, Settings categories) ----------
    /** A page that rises in; [pageRiseOut] is the same motion reversed. */
    fun pageRiseIn(reduced: Boolean, ms: Int): EnterTransition =
        if (reduced) fadeIn(tween(REDUCED_MS)) else slideInVertically(tween(ms, easing = YuMotion.Emphasized)) { it / 6 } + fadeIn(tween(ms, easing = YuMotion.Standard))

    fun pageRiseOut(reduced: Boolean, ms: Int): ExitTransition =
        if (reduced) fadeOut(tween(REDUCED_MS)) else slideOutVertically(tween(ms, easing = YuMotion.Emphasized)) { it / 6 } + fadeOut(tween(ms, easing = YuMotion.Standard))

    /** Settings home → category and back: the NavHost push / pop, so it feels like every other page. */
    fun <S> pushOrPop(scope: AnimatedContentTransitionScope<S>, forward: Boolean, reduced: Boolean): ContentTransform = with(scope) {
        if (forward) push(reduced) togetherWith pushExit(reduced) else popEnter(reduced) togetherWith popExit(reduced)
    }

    /** Pure, unit-tested: every pair runs as long both ways (OWN-14). */
    fun isSymmetric(): Boolean = POP_MS == PUSH_MS
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
