/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * B3 (Group B): Yumeiro's loader is the splash mark. The same moon-and-play drawable the system and
 * in-app splash show sits in the middle, a short moonlight arc orbits it and the mark breathes very
 * slightly, so every page that waits looks like the app opening, not a stock spinner.
 *
 * Cost: one infinite transition read only in the draw phase (no recomposition per frame), one
 * vector and one arc: well inside a 16 ms frame on low-RAM phones and TV. With reduced motion the
 * arc stands still and the mark does not breathe. TalkBack hears "[label]" as a busy indicator.
 */
object YuLoaderSpec {
    const val ORBIT_MS = 1100
    const val BREATH_MS = 1400
    const val SWEEP = 100f
    fun animates(reduced: Boolean): Boolean = !reduced
}

@Composable
fun YuLoader(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    label: String = "Loading",
    color: Color? = null,
) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val tint = color ?: (if (p.isDark) p.accentBright else p.accent)
    val turn: State<Float>
    val breath: State<Float>
    if (YuLoaderSpec.animates(reduced)) {
        val t = rememberInfiniteTransition(label = "yuLoader")
        turn = t.animateFloat(0f, 360f, infiniteRepeatable(tween(YuLoaderSpec.ORBIT_MS, easing = LinearEasing)), label = "orbit")
        breath = t.animateFloat(0.94f, 1f, infiniteRepeatable(tween(YuLoaderSpec.BREATH_MS, easing = YuMotion.Standard), RepeatMode.Reverse), label = "breath")
    } else {
        turn = remember { mutableFloatStateOf(-90f) }
        breath = remember { mutableFloatStateOf(1f) }
    }
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .testTag("yu_loader"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = (size.toPx() * 0.06f).coerceAtLeast(2.dp.toPx())
            val inset = w / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(tint.copy(alpha = 0.14f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            rotate(turn.value) {
                drawArc(tint, 0f, YuLoaderSpec.SWEEP, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        // The drawable's mark fills the middle half of its canvas, so drawn at the loader's size
        // it sits inside the ring with room to breathe.
        Image(
            painterResource(R.drawable.ic_launch_moon),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.25f).graphicsLayer { val b = breath.value; scaleX = b; scaleY = b },
        )
    }
}
