/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.update.ui

import dev.asfakul.mtrevanced.ui.theme.OnAccent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.asfakul.mtrevanced.ui.theme.DarkBackground
import dev.asfakul.mtrevanced.ui.theme.DarkOutline
import dev.asfakul.mtrevanced.ui.theme.DarkSurface
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceVariant
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreenBright
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary
import dev.asfakul.mtrevanced.update.ReleaseInfo
import dev.asfakul.mtrevanced.update.UpdateConfig
import dev.asfakul.mtrevanced.update.UpdateSource
import dev.asfakul.mtrevanced.update.UpdateState

private val ErrorRed = Color(0xFFFF6B6B)

/**
 * The update popup. Renders one of: available → downloading → ready → installing, or failed.
 * Back / outside taps never cancel a running download; they just move it to the background.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit,
    onRemindLater: () -> Unit,
    onCancelDownload: () -> Unit,
    onContinueInBackground: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissAction: () -> Unit = when (state) {
        is UpdateState.Available -> onRemindLater
        is UpdateState.Downloading -> onContinueInBackground
        is UpdateState.Installing -> ({})
        else -> onDismiss
    }
    Dialog(
        onDismissRequest = dismissAction,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = state !is UpdateState.Downloading && state !is UpdateState.Installing,
        ),
    ) {
        UpdateDialogCard(
            state = state,
            currentVersion = currentVersion,
            needsInstallPermission = needsInstallPermission,
            onDownload = onDownload,
            onRemindLater = onRemindLater,
            onCancelDownload = onCancelDownload,
            onContinueInBackground = onContinueInBackground,
            onInstall = onInstall,
            onRetry = onRetry,
            onDismiss = onDismiss,
        )
    }
}

/** The dialog surface itself (separate from the window so it can be previewed / screenshot-tested). */
@Composable
internal fun UpdateDialogCard(
    state: UpdateState,
    currentVersion: String,
    needsInstallPermission: Boolean,
    onDownload: () -> Unit = {},
    onRemindLater: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onContinueInBackground: () -> Unit = {},
    onInstall: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .testTag("update_dialog"),
        shape = RoundedCornerShape(28.dp),
        color = DarkSurface,
        border = BorderStroke(0.5.dp, DarkOutline),
        shadowElevation = 12.dp,
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(160)) },
            label = "update_dialog_content",
        ) { s ->
            when (s) {
                is UpdateState.Available -> AvailableContent(s.release, currentVersion, onDownload, onRemindLater)
                is UpdateState.Downloading -> DownloadingContent(s, onCancelDownload, onContinueInBackground)
                is UpdateState.ReadyToInstall -> ReadyContent(s.release, needsInstallPermission, onInstall, onDismiss)
                is UpdateState.Installing -> InstallingContent(s.release)
                is UpdateState.Failed -> FailedContent(s, onRetry, onDismiss)
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }
}

// ----------------------------------------------------------------------------------- states

@Composable
private fun AvailableContent(
    release: ReleaseInfo,
    currentVersion: String,
    onDownload: () -> Unit,
    onRemindLater: () -> Unit,
) {
    Column {
        Header(icon = Icons.Default.SystemUpdate, title = "Update available", pulse = true) {
            VersionChip(from = currentVersion, to = release.versionName)
        }
        Column(Modifier.padding(horizontal = 22.dp)) {
            MetaRow(release)
            Spacer(Modifier.height(16.dp))
            Text("What's new", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            ReleaseNotes(release.notes, Modifier.heightIn(max = 260.dp))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRemindLater,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("update_remind_later"),
                    border = BorderStroke(1.dp, DarkOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                ) { Text("Remind me later", maxLines = 1) }
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("update_download"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DownloadingContent(
    s: UpdateState.Downloading,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Downloading v${s.release.versionName}",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary,
        )
        Spacer(Modifier.height(22.dp))
        DownloadRing(progress = s.progress, indeterminate = s.queued && s.downloadedBytes == 0L)
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (s.queued && s.downloadedBytes == 0L) "Starting download…"
            else "${formatBytes(s.downloadedBytes)} of ${formatBytes(s.totalBytes)}",
            style = MaterialTheme.typography.bodyLarge, color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (s.bytesPerSecond > 0) "${formatBytes(s.bytesPerSecond)}/s · ${eta(s)}" else "Connecting to GitHub…",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBackground,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("update_background"),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
        ) { Text("Download in background", fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("update_cancel"),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
        ) { Text("Cancel download", maxLines = 1) }
        Spacer(Modifier.height(10.dp))
        Text(
            "You'll get a notification when it's ready to install.",
            style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadyContent(
    release: ReleaseInfo,
    needsPermission: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val popAnim = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { popAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    val pop = popAnim.value
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).scale(pop).clip(CircleShape)
                .background(Brush.radialGradient(listOf(SpotifyGreen.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpotifyGreenBright, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Ready to install", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Yumeiro v${release.versionName} has been downloaded and verified.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Source: github.com/${UpdateConfig.owner}/${UpdateConfig.repo} · SHA-256 and signature checked",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center,
        )
        if (needsPermission) {
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = DarkSurfaceContainerHigh) {
                Text(
                    "Android will ask you to allow Yumeiro to install updates. This is needed only once.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier.weight(1f).height(48.dp),
                border = BorderStroke(1.dp, DarkOutline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) { Text("Later") }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1.4f).height(48.dp).testTag("update_install"),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
            ) { Text(if (needsPermission) "Allow & install" else "Install now", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun InstallingContent(release: ReleaseInfo) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DownloadRing(progress = 1f, indeterminate = true, size = 96)
        Spacer(Modifier.height(18.dp))
        Text("Installing v${release.versionName}…", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Confirm in the system dialog if asked.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun FailedContent(s: UpdateState.Failed, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Update problem", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f).height(48.dp),
                border = BorderStroke(1.dp, DarkOutline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) { Text("Close") }
            if (s.release != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                ) { Text("Retry", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ------------------------------------------------------------------------------- building blocks

@Composable
private fun Header(icon: ImageVector, title: String, pulse: Boolean, extra: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "header_pulse")
    val ring by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring",
    )
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(SpotifyGreen.copy(alpha = 0.28f), DarkSurface)))
            .padding(top = 28.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                if (pulse) {
                    Box(
                        Modifier.size(64.dp).scale(ring).clip(CircleShape)
                            .border(2.dp, SpotifyGreen.copy(alpha = (1.25f - ring).coerceIn(0f, 0.6f)), CircleShape),
                    )
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(SpotifyGreenBright, SpotifyGreen))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = OnAccent, modifier = Modifier.size(32.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            extra()
        }
    }
}

@Composable
private fun VersionChip(from: String, to: String) {
    Surface(shape = RoundedCornerShape(50), color = DarkBackground.copy(alpha = 0.6f), border = BorderStroke(0.5.dp, DarkOutline)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("v$from", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Text("  →  ", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
            Text("v$to", style = MaterialTheme.typography.labelLarge, color = SpotifyGreenBright, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetaRow(release: ReleaseInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        MetaItem(Icons.Default.NewReleases, formatDate(release.publishedAt))
        Dot()
        MetaItem(Icons.Default.CloudDownload, formatBytes(release.asset.sizeBytes))
        Dot()
        MetaItem(
            if (release.source == UpdateSource.AUTHENTICATED) Icons.Default.Lock else Icons.Default.Public,
            "GitHub",
        )
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun Dot() {
    Text("  •  ", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
}

/** Renders the GitHub release body (lightweight Markdown: headings and bullet lists). */
@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val lines = remember(markdown) { parseNotes(markdown) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceContainerHigh,
        border = BorderStroke(0.5.dp, DarkOutline),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            if (lines.isEmpty()) {
                Text("Bug fixes and improvements.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            lines.forEachIndexed { i, line ->
                when (line) {
                    is NoteLine.Heading -> {
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Text(line.text, style = MaterialTheme.typography.labelLarge, color = SpotifyGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                    }
                    is NoteLine.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(SpotifyGreen))
                        Spacer(Modifier.width(10.dp))
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    is NoteLine.Paragraph -> Text(
                        line.text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRing(progress: Float, indeterminate: Boolean, size: Int = 148) {
    val animated by animateFloatAsState(progress, tween(350, easing = FastOutSlowInEasing), label = "progress")
    val transition = rememberInfiniteTransition(label = "ring_spin")
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "spin",
    )
    val glow by transition.animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
    )
    val accent = SpotifyGreen
    val accentBright = SpotifyGreenBright
    val trackColor = DarkSurfaceVariant
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = this.size.minDimension * 0.075f
            val inset = stroke / 2 + 4f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            // halo
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = glow * 0.25f), Color.Transparent)),
                radius = this.size.minDimension / 2,
            )
            // track
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweepBrush = Brush.sweepGradient(listOf(accent, accentBright, accent))
            if (indeterminate) {
                rotate(spin) {
                    drawArc(sweepBrush, 0f, 90f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            } else {
                drawArc(sweepBrush, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // moving glint at the head of the arc
                if (animated in 0.01f..0.995f) {
                    val angle = Math.toRadians((-90f + 360f * animated).toDouble())
                    val r = arcSize.width / 2
                    val c = Offset(this.size.width / 2, this.size.height / 2)
                    drawCircle(
                        Color.White.copy(alpha = glow),
                        radius = stroke * 0.42f,
                        center = Offset(c.x + (r * kotlin.math.cos(angle)).toFloat(), c.y + (r * kotlin.math.sin(angle)).toFloat()),
                    )
                }
            }
        }
        if (!indeterminate || size >= 140) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (indeterminate) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                } else {
                    Text(
                        "${(animated * 100).toInt()}%",
                        fontSize = (size / 4.6f).sp, fontWeight = FontWeight.Black, color = TextPrimary,
                    )
                    Text("downloaded", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                }
            }
        }
    }
}
