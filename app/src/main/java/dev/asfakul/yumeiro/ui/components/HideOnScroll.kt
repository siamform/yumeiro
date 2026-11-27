/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * B2 (Beta chapter): a top category / filter bar that slides away while the page scrolls down and
 * comes straight back on the first scroll up, like YouTube's chip bar.
 *
 * Trigger: a vertical scroll (finger, fling, mouse wheel, D-pad scroll) of the page under the bar.
 * Rules: scrolling down first moves the bar up until it is gone (that part of the scroll is used by
 * the bar, so the page follows the finger 1:1, never twice as fast); scrolling up brings it back the
 * same way. A fling that ends with the bar half out settles it to the nearer end. A new category
 * ([showKey] changes) or remote focus entering the bar always shows it.
 * Feedback: the page grows into the space the bar leaves (layout only while the bar moves, at most
 * the bar's height of scroll), so no item hides under it and the list end is always reachable.
 * Reduced motion: the settle jumps instead of animating.
 */
@Stable
class HideOnScrollState {
    /** Height of the bar in px (measured). */
    var heightPx by mutableIntStateOf(0)
        internal set
    /** 0 = fully shown, -[heightPx] = fully hidden. */
    var offsetPx by mutableFloatStateOf(0f)
        internal set

    val hidden: Boolean get() = heightPx > 0 && offsetPx <= -heightPx + 0.5f
    val fraction: Float get() = if (heightPx == 0) 0f else -offsetPx / heightPx

    internal var reduced = false

    fun show() { offsetPx = 0f }

    internal fun consume(dy: Float): Float {
        val h = heightPx.toFloat()
        if (h <= 0f || dy == 0f) return 0f
        val old = offsetPx
        val new = (old + dy).coerceIn(-h, 0f)
        offsetPx = new
        return new - old
    }

    internal suspend fun settle() {
        val h = heightPx.toFloat()
        val o = offsetPx
        if (h <= 0f || o >= 0f || o <= -h) return
        val target = if (-o < h / 2f) 0f else -h
        if (reduced) { offsetPx = target; return }
        animate(o, target, animationSpec = tween(180, easing = YuMotion.Standard)) { v, _ -> offsetPx = v }
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            Offset(0f, consume(available.y))

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(); return Velocity.Zero
        }
    }
}

@Composable
fun rememberHideOnScrollState(): HideOnScrollState = remember { HideOnScrollState() }

/**
 * A column whose [header] (chips, filters) hides on scroll down and returns on scroll up, above
 * [content] (the scrolling page). Use it instead of `Column { chips; list }`.
 */
@Composable
fun HideOnScrollColumn(
    modifier: Modifier = Modifier,
    state: HideOnScrollState = rememberHideOnScrollState(),
    showKey: Any? = null,
    enabled: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    state.reduced = reducedMotion()
    LaunchedEffect(showKey, enabled) { state.show() }
    Layout(
        content = {
            Column(Modifier.testTag("hide_on_scroll_header").onFocusChanged { if (it.hasFocus) state.show() }) { header() }
            Column(Modifier.fillMaxSize(), content = content)
        },
        modifier = modifier
            .clipToBounds()
            .then(if (enabled) Modifier.nestedScroll(state.connection) else Modifier),
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val head = measurables[0].measure(loose)
        if (state.heightPx != head.height) {
            state.heightPx = head.height
            if (state.offsetPx < -head.height) state.offsetPx = -head.height.toFloat()
        }
        val off = if (enabled) state.offsetPx.roundToInt() else 0
        val top = (head.height + off).coerceAtLeast(0)
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else head.height + 2000
        val body = measurables[1].measure(loose.copy(minHeight = 0, maxHeight = (h - top).coerceAtLeast(0)))
        layout(constraints.maxWidth, h) {
            body.placeRelative(0, top)
            head.placeRelative(0, off)
        }
    }
}
/**
 * Round 13: a bar that hides after a real scroll down and shows again after a small scroll up
 * (YouTube's bottom navigation). It only reads scroll deltas, nothing is consumed.
 */
class HideOnScroll(private val hideAfterPx: Float = 48f, private val showAfterPx: Float = 12f) : NestedScrollConnection {
    var visible by mutableStateOf(true)
        private set
    private var down = 0f
    private var up = 0f

    /** Feeds one scroll delta (negative y = content moves up = user scrolls down). Pure state. */
    fun onDelta(dy: Float) {
        if (dy < 0) { down += -dy; up = 0f; if (visible && down > hideAfterPx) visible = false }
        else if (dy > 0) { up += dy; down = 0f; if (!visible && up > showAfterPx) visible = true }
    }

    fun show() { visible = true; down = 0f; up = 0f }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        onDelta(consumed.y)
        // Pulled at the very top (nothing left to scroll): always show.
        if (available.y > 0f) show()
        return Offset.Zero
    }
}
