/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Slim vertical level bar (like the phone's own volume slider): percentage on top, a thin rounded
 * track that fills from the bottom, and the icon underneath. Used for brightness and volume.
 */
@Composable
private fun VerticalLevelBar(
    level: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
) {
    val animated by androidx.compose.animation.core.animateFloatAsState(level.coerceIn(0f, 1f), androidx.compose.animation.core.tween(90), label = "level")
    val accent = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent
    // Phone-style linear level: a hairline track with a glowing gold fill, no box behind it.
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(vertical = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${(animated * 100).toInt()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.weight(1f).width(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(animated).clip(RoundedCornerShape(2.dp)).background(accent))
        }
        Spacer(Modifier.height(8.dp))
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BrightnessHUD(
    brightness: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + androidx.compose.animation.slideInHorizontally { -it / 2 },
        exit = fadeOut() + androidx.compose.animation.slideOutHorizontally { -it / 2 },
        modifier = modifier
    ) {
        VerticalLevelBar(brightness, Icons.Default.BrightnessMedium, "Brightness", "brightness_hud")
    }
}

@Composable
fun VolumeHUD(
    volumeLevel: Int,
    maxVolume: Int,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 2 },
        exit = fadeOut() + androidx.compose.animation.slideOutHorizontally { it / 2 },
        modifier = modifier
    ) {
        val safeMax = if (maxVolume > 0) maxVolume else 1
        val ratio = (volumeLevel.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
        val icon = when {
            volumeLevel == 0 -> Icons.AutoMirrored.Filled.VolumeMute
            ratio < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
            else -> Icons.AutoMirrored.Filled.VolumeUp
        }
        VerticalLevelBar(ratio, icon, "Volume", "volume_hud")
    }
}

@Composable
fun ScrubSeekHUD(
    targetTimeMs: Long,
    durationMs: Long,
    deltaMs: Long,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        val isForward = deltaMs >= 0
        val safeDur = if (durationMs > 0) durationMs else 1L
        val progress = (targetTimeMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
        val deltaSign = if (isForward) "+" else "-"
        val formattedDelta = "$deltaSign${formatDuration(abs(deltaMs))}"

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.85f),
            tonalElevation = 8.dp,
            modifier = Modifier.testTag("scrub_seek_hud")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = if (isForward) "Forward" else "Rewind",
                        tint = if (isForward) SpotifyGreen else Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[$formattedDelta]",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isForward) SpotifyGreen else Color(0xFFFF9800)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatDuration(targetTimeMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(130.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SpotifyGreen,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
fun DoubleTapFeedback(
    isForward: Boolean,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.70f),
            modifier = Modifier
                .size(76.dp)
                .testTag(if (isForward) "double_tap_forward_hud" else "double_tap_rewind_hud")
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = if (isForward) "+10s" else "-10s",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    text = if (isForward) "+10s" else "-10s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ToastHUD(
    message: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && message.isNotBlank(),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.85f),
            tonalElevation = 6.dp,
            modifier = Modifier.testTag("toast_hud")
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun ResumePill(
    playbackPositionMs: Long,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.graphics.Color(0xFF2C2452),
        shadowElevation = 10.dp,
        modifier = modifier.testTag("video_resume_pill")
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Resumed at ${formatDuration(playbackPositionMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(
                onClick = onRestart,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen),
                modifier = Modifier.height(40.dp).testTag("video_resume_start_over")
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Start Over",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss resume notice",
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
