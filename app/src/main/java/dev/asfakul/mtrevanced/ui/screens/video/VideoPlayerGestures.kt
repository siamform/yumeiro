/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.screens.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class DragMode {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
    /** Swipe down in the centre: minimise the player (YouTube style) or leave fullscreen. */
    SWIPE_DOWN
}

@Composable
fun VideoGestureOverlay(
    isScreenLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubDelta: (Float) -> Unit,
    onScrubCommit: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onGestureEnd: () -> Unit,
    onLockedTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** Called when the user drags down from the centre of the video far enough. Null disables it. */
    onSwipeDown: (() -> Unit)? = null,
    /** Live drag distance in px while swiping down (0 when not swiping), for a follow-the-finger effect. */
    onSwipeDownProgress: (Float) -> Unit = {},
) {
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeDownProgress by rememberUpdatedState(onSwipeDownProgress)
    val coroutineScope = rememberCoroutineScope()
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    // After a double tap on a side, further quick taps on the same side keep seeking
    // (tap-tap-tap = 30 s), like popular video players.
    var lastSeekTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastSeekSide by remember { mutableIntStateOf(0) }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTapLeft by rememberUpdatedState(onDoubleTapLeft)
    val currentOnDoubleTapRight by rememberUpdatedState(onDoubleTapRight)
    val currentOnDoubleTapCenter by rememberUpdatedState(onDoubleTapCenter)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubDelta by rememberUpdatedState(onScrubDelta)
    val currentOnScrubCommit by rememberUpdatedState(onScrubCommit)
    val currentOnBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val currentOnVolumeDelta by rememberUpdatedState(onVolumeDelta)
    val currentOnGestureEnd by rememberUpdatedState(onGestureEnd)
    val currentOnLockedTap by rememberUpdatedState(onLockedTap)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isScreenLocked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val touchSlop = viewConfiguration.touchSlop

                    if (isScreenLocked) {
                        // In locked state, wait for release and trigger onLockedTap
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                released = true
                                break
                            }
                        }
                        if (released) {
                            currentOnLockedTap()
                        }
                        return@awaitEachGesture
                    }

                    var dragDirection = DragMode.NONE
                    var isDragging = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!pointer.pressed) {
                            // Finger lifted!
                            if (isDragging) {
                                if (dragDirection == DragMode.SEEK) {
                                    currentOnScrubCommit()
                                }
                                if (dragDirection == DragMode.SWIPE_DOWN) {
                                    currentOnSwipeDownProgress(0f)
                                    val dy = pointer.position.y - startY
                                    if (dy > maxOf(height * 0.18f, 48.dp.toPx())) currentOnSwipeDown?.invoke()
                                }
                                currentOnGestureEnd()
                            } else {
                                // Registered a tap
                                val currentTime = System.currentTimeMillis()
                                val dist = (pointer.position - lastTapPosition).getDistance()
                                val isDoubleTap = (currentTime - lastTapTimeMs) < 320L && dist < (touchSlop * 4f)
                                val tapRatio = if (width > 0f) pointer.position.x / width else 0.5f
                                val tapSide = when {
                                    tapRatio < 0.4f -> -1
                                    tapRatio > 0.6f -> 1
                                    else -> 0
                                }
                                val isSeekContinuation = tapSide != 0 && tapSide == lastSeekSide &&
                                    (currentTime - lastSeekTapTimeMs) < 700L

                                if (isSeekContinuation) {
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = null
                                    lastTapTimeMs = 0L
                                    lastSeekTapTimeMs = currentTime
                                    if (tapSide < 0) currentOnDoubleTapLeft() else currentOnDoubleTapRight()
                                } else if (isDoubleTap) {
                                    // Cancel any scheduled single tap
                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = null
                                    lastTapTimeMs = 0L

                                    lastSeekSide = tapSide
                                    lastSeekTapTimeMs = currentTime
                                    when (tapSide) {
                                        -1 -> currentOnDoubleTapLeft()
                                        1 -> currentOnDoubleTapRight()
                                        else -> currentOnDoubleTapCenter()
                                    }
                                } else {
                                    lastTapTimeMs = currentTime
                                    lastTapPosition = pointer.position

                                    pendingSingleTapJob?.cancel()
                                    pendingSingleTapJob = coroutineScope.launch {
                                        delay(260L)
                                        currentOnSingleTap()
                                        lastTapTimeMs = 0L
                                    }
                                }
                            }
                            break
                        }

                        val totalDx = pointer.position.x - startX
                        val totalDy = pointer.position.y - startY

                        if (!isDragging) {
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                isDragging = true
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = null

                                val centre = width > 0f && startX / width in 0.3f..0.7f
                                dragDirection = if (abs(totalDx) > abs(totalDy)) {
                                    DragMode.SEEK
                                } else if (centre && totalDy > 0f && currentOnSwipeDown != null) {
                                    DragMode.SWIPE_DOWN
                                } else {
                                    if (startX < width / 2f) DragMode.BRIGHTNESS else DragMode.VOLUME
                                }

                                if (dragDirection == DragMode.SEEK) {
                                    currentOnScrubStart()
                                }
                            }
                        }

                        if (isDragging) {
                            val deltaX = pointer.position.x - pointer.previousPosition.x
                            val deltaY = pointer.position.y - pointer.previousPosition.y
                            pointer.consume()

                            when (dragDirection) {
                                DragMode.SEEK -> currentOnScrubDelta(deltaX)
                                DragMode.BRIGHTNESS -> currentOnBrightnessDelta(-deltaY / height)
                                DragMode.VOLUME -> currentOnVolumeDelta(-deltaY / height)
                                DragMode.SWIPE_DOWN -> currentOnSwipeDownProgress((pointer.position.y - startY).coerceAtLeast(0f))
                                DragMode.NONE -> {}
                            }
                        }
                    }
                }
            }
    )
}
