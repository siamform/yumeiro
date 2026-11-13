/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * The mini player of the Tsuki dock. Progress is the moon path: a gold line running along the
 * TOP edge of the strip (where the eye already is), not hidden at the bottom. Swipe the artwork
 * and title left/right to skip to the next/previous track (the strip follows the finger and
 * springs back); the explicit previous / play-pause / next / close buttons stay for precision
 * and accessibility, and both skips are also exposed as accessibility custom actions. The progress is read in the draw phase from [positionMs], so position
 * ticks only redraw that line — nothing recomposes while music plays.
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    onClose: () -> Unit = {},
    positionMs: () -> Long = { 0L },
    /** Docked on top of the navigation bar (ArchiveTune-style joined floating stack). */
    attachedToNav: Boolean = false,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack
    val palette = LocalMtPalette.current
    val content = palette.onMiniPlayer
    val sub = content.copy(alpha = 0.78f)
    val accent = palette.accent.let { if (!palette.isDark) palette.accentBright else it }
    val duration = playbackState.durationMs

    AnimatedVisibility(
        visible = track != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier
    ) {
        if (track != null) {
            val shape = if (attachedToNav) {
                RoundedCornerShape(topStart = DockOuterCorner, topEnd = DockOuterCorner, bottomStart = DockJunctionCorner, bottomEnd = DockJunctionCorner)
            } else RoundedCornerShape(DockStandaloneCorner)
            val border = dev.asfakul.yumeiro.ui.theme.rememberGlassBorder()
            val accent2 = palette.accent2
            val ambient = dev.asfakul.yumeiro.ui.theme.LocalAmbientColor.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DockHorizontalPadding, end = DockHorizontalPadding, top = 4.dp, bottom = if (attachedToNav) DockGap else DockPillGap)
                    .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(palette.miniPlayer)
                    .border(1.dp, border, shape)
                    .drawWithContent {
                        // Soft ambient glow (colour of the current artwork) behind the artwork side.
                        val glow = ambient?.value ?: accent
                        drawRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(glow.copy(alpha = 0.20f), Color.Transparent), endX = size.width * 0.6f
                            )
                        )
                        drawContent()
                        val p = if (duration > 0) (positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
                        val h = 3.dp.toPx()
                        val inset = 18.dp.toPx()
                        val trackW = size.width - inset * 2
                        val top = 0f
                        drawRoundRect(content.copy(alpha = 0.10f), Offset(inset, top), Size(trackW, h), androidx.compose.ui.geometry.CornerRadius(h))
                        if (p > 0f) drawRoundRect(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent2, accent), startX = inset, endX = inset + trackW),
                            Offset(inset, top), Size(trackW * p, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h)
                        )
                    }
                    .clickable(onClickLabel = "Open player", onClick = onClick)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Next track") { onNext(); true },
                            CustomAccessibilityAction("Previous track") { onPrevious(); true },
                        )
                    }
                    .testTag("mini_player_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DockMiniHeight)
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val swipe = remember(track.id) { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
                    Row(
                        Modifier
                            .weight(1f)
                            .offset { androidx.compose.ui.unit.IntOffset(swipe.value.roundToInt(), 0) }
                            .graphicsLayer { alpha = 1f - (abs(swipe.value) / (threshold * 3f)).coerceIn(0f, 0.6f) }
                            .pointerInput(track.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val v = swipe.value
                                        when {
                                            v <= -threshold -> onNext()
                                            v >= threshold -> onPrevious()
                                        }
                                        scope.launch { swipe.animateTo(0f, dev.asfakul.yumeiro.ui.theme.Yume.settle()) }
                                    },
                                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch { swipe.snapTo((swipe.value + drag).coerceIn(-threshold * 1.6f, threshold * 1.6f)) }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    TrackArtwork(
                        artUri = track.albumArtUri,
                        key = track.id,
                        size = 48.dp,
                        cornerRadius = 12.dp,
                        contentDescription = track.album
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.94f),
                            color = content, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    }
                    MiniButton(Icons.Default.SkipPrevious, "Previous track", content, onPrevious, "mini_player_previous_button")
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable(role = Role.Button, onClick = onTogglePlayPause)
                            .testTag("mini_player_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = palette.onAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MiniButton(Icons.Default.SkipNext, "Next track", content, onNext, "mini_player_next_button")
                    MiniButton(Icons.Default.Close, "Stop and close player", sub, onClose, "mini_player_close_button", 20)
                }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit, tag: String, iconSize: Int = 24) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(iconSize.dp)) }
}
