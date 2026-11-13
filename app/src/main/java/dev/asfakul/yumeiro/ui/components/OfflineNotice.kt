/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/** The places that keep working without a connection; provided once by the app shell. */
@Immutable
data class OfflineExits(
    val deviceMusic: () -> Unit = {},
    val deviceVideos: () -> Unit = {},
    val downloads: () -> Unit = {},
    val library: () -> Unit = {},
)

val LocalOfflineExits = staticCompositionLocalOf { OfflineExits() }

/**
 * Shown at the top of a screen that needs the internet while the phone is offline. It says plainly
 * what is unavailable and offers the features that still work. The screen below stays as it is
 * (saved history, cached lists), so nothing is hidden and nothing pretends to load.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfflineNotice(what: String, modifier: Modifier = Modifier, tag: String = "offline_notice") {
    val p = LocalMtPalette.current
    val exits = LocalOfflineExits.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.85f else 1f))
            .border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .testTag(tag).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(p.textPrimary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CloudOff, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You're offline", style = MaterialTheme.typography.titleSmall, color = p.textPrimary, fontWeight = FontWeight.Bold)
                Text("$what needs the internet. These still work:", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OfflineChip(Icons.Default.LibraryMusic, "Device music", "offline_exit_music", exits.deviceMusic)
            OfflineChip(Icons.Default.VideoLibrary, "Device videos", "offline_exit_videos", exits.deviceVideos)
            OfflineChip(Icons.Default.Download, "Downloads", "offline_exit_downloads", exits.downloads)
            OfflineChip(Icons.Default.Bookmarks, "Library", "offline_exit_library", exits.library)
        }
    }
}

@Composable
private fun OfflineChip(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(p.accent.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick).testTag(tag).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
