/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.TextPrimary

/** One Library shortcut: label, icon, its own two-tone hue and the action. */
internal data class LibraryShortcut(
    val label: String,
    val icon: ImageVector,
    val from: Color,
    val to: Color,
    val testTag: String,
    val busy: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Round 10: the Music | Videos | Reading tabs are gone. Library opens on one page and everything
 * else is a compact, colourful shortcut — like the quick actions in a banking or wallet app: a
 * squircle with its own hue (so each one is told apart at a glance), a white glyph and a short
 * label. Four per row, 48 dp+ touch targets, a springy press, and a spinner while Rescan runs.
 */
@Composable
internal fun LibraryShortcutGrid(shortcuts: List<LibraryShortcut>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("library_shortcuts"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shortcuts.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ShortcutTile(it, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(s: LibraryShortcut, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "sc_scale")
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = null, enabled = s.enabled && !s.busy, role = Role.Button, onClickLabel = s.label, onClick = s.onClick)
            .padding(vertical = 4.dp)
            .testTag(s.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(10.dp, shape, ambientColor = s.to, spotColor = s.to)
                .clip(shape)
                .background(Brush.linearGradient(listOf(s.from, s.to)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), shape),
            contentAlignment = Alignment.Center,
        ) {
            // Soft top sheen: reads as a glossy, tappable object rather than a flat swatch.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.26f), 0.5f to Color.White.copy(alpha = 0f))))
            if (s.busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            else Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (s.busy) "Scanning…" else s.label, color = TextPrimary, fontSize = 11.5.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

/** The eight Library shortcuts, in the order people reach for them. */
internal fun libraryShortcuts(
    scanning: Boolean,
    onVideos: () -> Unit, onReading: () -> Unit, onEqualizer: () -> Unit, onNewPlaylist: () -> Unit,
    onRescan: () -> Unit, onHistory: () -> Unit, onDownloads: () -> Unit, onTogether: () -> Unit,
): List<LibraryShortcut> = listOf(
    LibraryShortcut("Videos", Icons.Default.VideoLibrary, Color(0xFFFF5F6D), Color(0xFFFF9A44), "lib_sc_videos", onClick = onVideos),
    LibraryShortcut("Reading", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF8E5CFF), Color(0xFFC77DFF), "lib_sc_reading", onClick = onReading),
    LibraryShortcut("Equalizer", Icons.Default.Equalizer, Color(0xFF00B89C), Color(0xFF43E3A5), "lib_eq_card", onClick = onEqualizer),
    LibraryShortcut("New playlist", Icons.Default.Add, Color(0xFFFFA41B), Color(0xFFFFD43B), "add_playlist_button", onClick = onNewPlaylist),
    LibraryShortcut("Rescan", Icons.Default.Refresh, Color(0xFF2D7BFF), Color(0xFF5CC8FF), "banner_rescan_button", busy = scanning, onClick = onRescan),
    LibraryShortcut("History", Icons.Default.History, Color(0xFFFF3D8B), Color(0xFFFF7EB6), "lib_sc_history", onClick = onHistory),
    LibraryShortcut("Downloads", Icons.Default.CloudDownload, Color(0xFF4F46E5), Color(0xFF7C8CFF), "lib_sc_downloads", onClick = onDownloads),
    LibraryShortcut("Together", Icons.Default.Groups, Color(0xFF22B455), Color(0xFF9BE15D), "lib_sc_together", onClick = onTogether),
)
