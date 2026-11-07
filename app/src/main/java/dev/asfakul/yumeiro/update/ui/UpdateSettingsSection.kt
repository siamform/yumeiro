/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update.ui

import dev.asfakul.yumeiro.ui.theme.OnAccent
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkOutlineVariant
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.update.UpdateManager
import dev.asfakul.yumeiro.update.UpdateState

/** "App updates" card for the Settings screen. */
@Composable
fun UpdateSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { UpdateManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val autoCheck by manager.autoCheckEnabled.collectAsStateWithLifecycle()
    val lastChecked by manager.lastCheckedAt.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxWidth().testTag("settings_updates_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
        border = BorderStroke(0.5.dp, DarkOutline),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Current version
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(SpotifyGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Build ${BuildConfig.VERSION_CODE} · ${if (manager.usesAuthenticatedChannel) "Private" else "Public"} GitHub channel",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    )
                }
            }

            Divider()

            // Check for updates row with live status
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = state !is UpdateState.Checking) { manager.checkForUpdates(userInitiated = true) }
                    .padding(vertical = 8.dp)
                    .testTag("setting_check_updates"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(statusText(state, lastChecked), style = MaterialTheme.typography.bodySmall, color = statusColor(state))
                }
                Spacer(Modifier.width(12.dp))
                StatusIcon(state)
            }

            val downloading = state as? UpdateState.Downloading
            if (downloading != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = SpotifyGreen, trackColor = DarkSurfaceVariant, strokeCap = StrokeCap.Round,
                )
            }

            Divider()

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Look for new versions each time the app opens", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoCheck,
                    onCheckedChange = manager::setAutoCheckEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black, checkedTrackColor = SpotifyGreen,
                        uncheckedThumbColor = TextTertiary, uncheckedTrackColor = DarkSurfaceVariant,
                    ),
                    modifier = Modifier.testTag("toggle_auto_update"),
                )
            }

            Divider()

            Text(
                "About the Play Protect \"scan app\" prompt: Google shows it for app versions it hasn't seen yet, which " +
                    "happens with every new release installed outside the Play Store. When it appears, choose to always " +
                    "send unknown apps for scanning (Play Protect → Settings → Improve harmful app detection) and it " +
                    "won't ask again.",
                style = MaterialTheme.typography.bodySmall, color = TextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Divider()

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(manager.releasesPageUrl)))
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Release history", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("View all versions and changelogs on GitHub", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatusIcon(state: UpdateState) {
    when (state) {
        is UpdateState.Checking -> CircularProgressIndicator(Modifier.size(22.dp), color = SpotifyGreen, strokeWidth = 2.5.dp)
        is UpdateState.UpToDate -> Icon(Icons.Default.CheckCircle, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
        is UpdateState.Available, is UpdateState.ReadyToInstall ->
            Box(Modifier.clip(RoundedCornerShape(50)).background(SpotifyGreen).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("NEW", style = MaterialTheme.typography.labelMedium, color = OnAccent, fontWeight = FontWeight.Black)
            }
        is UpdateState.Downloading -> Icon(Icons.Default.CloudDownload, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
        is UpdateState.Failed -> Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(22.dp))
        else -> Icon(Icons.Default.SystemUpdate, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
    }
}

private fun statusText(state: UpdateState, lastChecked: Long): String = when (state) {
    is UpdateState.Checking -> "Checking GitHub releases…"
    is UpdateState.UpToDate -> "You're on the latest version"
    is UpdateState.Available -> "v${state.release.versionName} is available — tap to view"
    is UpdateState.Downloading -> "Downloading v${state.release.versionName} · ${(state.progress * 100).toInt()}%"
    is UpdateState.ReadyToInstall -> "v${state.release.versionName} is ready — tap to install"
    is UpdateState.Installing -> "Installing v${state.release.versionName}…"
    is UpdateState.Failed -> state.message
    else -> "Last checked: ${formatRelative(lastChecked)}"
}

@Composable
private fun statusColor(state: UpdateState): Color = when (state) {
    is UpdateState.Available, is UpdateState.ReadyToInstall, is UpdateState.Downloading -> SpotifyGreen
    is UpdateState.Failed -> Color(0xFFFF6B6B)
    else -> TextSecondary
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(0.5.dp).background(DarkOutlineVariant))
}
