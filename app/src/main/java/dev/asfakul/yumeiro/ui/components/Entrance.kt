/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * B2 (Group B): feed cards rise in once, in a short stagger, the first time they appear.
 *
 * - No index plumbing: cards that first compose within [BURST_MS] of each other form one burst and
 *   each one waits [STEP_MS] more than the one before (at most [MAX_STEPS] steps), so a row that
 *   loads at once ripples in left to right, and a single late card just fades in.
 * - Once per card per process: scrolling back, returning to a tab or a recomposition never
 *   replays it ([firstTime] remembers the last [MEMORY] keys).
 * - Draw-phase only (alpha + translation in a graphics layer): no relayout, nothing for the GPU
 *   beyond one layer, so it stays inside a 16 ms frame on weak phones.
 * - Off with reduced motion and on low-RAM devices (cards simply appear).
 */
object Entrance {
    const val BURST_MS = 120L
    const val STEP_MS = 35
    const val MAX_STEPS = 8
    const val DURATION_MS = 320
    const val RISE_DP = 12
    private const val MEMORY = 4000

    private val seen = object : LinkedHashMap<Any, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Unit>?) = size > MEMORY
    }
    private var burstAt = Long.MIN_VALUE
    private var burstCount = 0

    /** True the first time [key] is shown in this process. */
    @Synchronized fun firstTime(key: Any): Boolean = seen.put(key, Unit) == null

    /** Delay for a card that first composes at [nowMs]. */
    @Synchronized fun nextDelay(nowMs: Long): Int {
        if (burstAt == Long.MIN_VALUE || nowMs - burstAt > BURST_MS) { burstAt = nowMs; burstCount = 0 }
        val d = minOf(burstCount, MAX_STEPS) * STEP_MS
        burstCount++
        return d
    }

    @Synchronized fun resetForTest() { seen.clear(); burstAt = Long.MIN_VALUE; burstCount = 0 }

    fun enabled(reduced: Boolean, lowRam: Boolean): Boolean = !reduced && !lowRam
}

/** See [Entrance]. [key] must be stable for the card (its item key). */
fun Modifier.yuEntrance(key: Any): Modifier = composed {
    val reduced = reducedMotion()
    val context = LocalContext.current
    val lowRam = remember { runCatching { (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice }.getOrDefault(false) }
    val play = remember(key) { Entrance.enabled(reduced, lowRam) && Entrance.firstTime(key) }
    if (!play) return@composed Modifier
    val delayMs = remember(key) { Entrance.nextDelay(android.os.SystemClock.uptimeMillis()) }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(Entrance.DURATION_MS, delayMs, YuMotion.EmphasizedDecelerate)) }
    Modifier.graphicsLayer {
        val v = t.value
        alpha = v
        translationY = (1f - v) * Entrance.RISE_DP.dp.toPx()
    }
}

/** B2: a pressed card dips to 97 % with a soft spring; off with reduced motion. */
fun Modifier.yuPressScale(interaction: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val reduced = reducedMotion()
    val down by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (down && !reduced) pressed else 1f,
        if (reduced) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "yuPress",
    )
    Modifier.graphicsLayer { scaleX = s; scaleY = s }
}
