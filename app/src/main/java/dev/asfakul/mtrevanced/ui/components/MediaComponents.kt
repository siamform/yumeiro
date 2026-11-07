/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.components

import androidx.compose.ui.draw.clipToBounds
import dev.asfakul.mtrevanced.ui.theme.rememberArtBrush
import dev.asfakul.mtrevanced.ui.theme.GlassFill
import dev.asfakul.mtrevanced.ui.theme.OverlineStyle
import dev.asfakul.mtrevanced.ui.theme.glass

import dev.asfakul.mtrevanced.ui.theme.OnAccent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.mtrevanced.data.model.AudioTrack
import dev.asfakul.mtrevanced.data.model.Playlist
import dev.asfakul.mtrevanced.data.model.StorageStats
import dev.asfakul.mtrevanced.data.model.VideoFolder
import dev.asfakul.mtrevanced.data.model.VideoItem
import dev.asfakul.mtrevanced.ui.theme.DarkOutline
import dev.asfakul.mtrevanced.ui.theme.DarkOutlineVariant
import dev.asfakul.mtrevanced.ui.theme.DarkSurface
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceContainer
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceVariant
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreenBright
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@Composable
fun StorageStatBanner(
    stats: StorageStats,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("storage_stat_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "OFFLINE VAULT", style = OverlineStyle, color = SpotifyGreen)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "On-device media", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            if (stats.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SpotifyGreen, strokeWidth = 2.5.dp)
            } else {
                IconButton(
                    onClick = onRescanClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .glass(CircleShape)
                        .testTag("banner_rescan_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan media", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(label = "Tracks", value = "${stats.audioCount}", icon = Icons.Default.MusicNote, modifier = Modifier.weight(1f))
            StatPill(label = "Videos", value = "${stats.videoCount}", icon = Icons.Default.Videocam, modifier = Modifier.weight(1f))
            StatPill(label = "Storage", value = formatFileSize(stats.totalStorageUsedBytes), icon = Icons.Default.Folder, modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = value, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItemRow(
    track: AudioTrack,
    isSelected: Boolean,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreOptionsClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    isPlaying: Boolean = LocalIsPlaying.current,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isChecked) SpotifyGreen.copy(alpha = 0.22f)
                else if (isSelected) GlassFill
                else Color.Transparent
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onCheckedChange != null) onCheckedChange(!isChecked) else onTrackClick()
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = SpotifyGreen,
                    uncheckedColor = TextTertiary,
                    checkmarkColor = OnAccent
                ),
                modifier = Modifier.testTag("track_select_checkbox_${track.id}")
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        TrackArtwork(
            artUri = track.albumArtUri,
            key = track.id,
            size = 48.dp,
            cornerRadius = 12.dp,
            contentDescription = track.album
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) SpotifyGreen else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${track.artist} \u00B7 ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isSelected) {
            EqualizerBars(color = SpotifyGreen, playing = isPlaying)
        } else {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (!isSelectionMode) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .testTag("track_favorite_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Unfavorite" else "Favorite",
                    tint = if (track.isFavorite) SpotifyGreen else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (onMoreOptionsClick != null) {
                IconButton(
                    onClick = onMoreOptionsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .testTag("track_more_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Song options",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridCard(
    video: VideoItem,
    onVideoClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onVideoClick() }
            .testTag("video_item_${video.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Column {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clipToBounds()
                    .background(rememberArtBrush(video.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
                AsyncImage(
                    model = dev.asfakul.mtrevanced.ui.image.VideoThumb(video.uri),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.32f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f)
                                )
                            )
                        )
                )

                // Resolution badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = video.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Favorite button top-right
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("video_fav_btn_${video.id}")
                ) {
                    Icon(
                        imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (video.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (video.isFavorite) SpotifyGreen else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Duration badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Progress bar overlay
                if (video.playbackPositionMs > 0 && video.durationMs > 0) {
                    val progress = (video.playbackPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .align(Alignment.BottomCenter),
                        color = SpotifyGreen,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        // Always two lines tall so every card in the grid has the same size.
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onOptionsClick,
                        modifier = Modifier
                            .size(28.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("video_options_btn_${video.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Video options",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val inProgress = video.playbackPositionMs > 0 && video.durationMs > 0
                    Text(
                        text = if (inProgress) "Resume " + ((video.playbackPositionMs * 100 / video.durationMs).coerceIn(0, 100)) + "%" else video.folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (inProgress) SpotifyGreen else TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }

            }
        }
    }
}

@Composable
fun VideoRowCard(
    video: VideoItem,
    onVideoClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onVideoClick() }
            .testTag("video_row_${video.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(rememberArtBrush(video.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
                AsyncImage(
                    model = dev.asfakul.mtrevanced.ui.image.VideoThumb(video.uri),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Resolution badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = video.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Duration badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Progress bar overlay
                if (video.playbackPositionMs > 0 && video.durationMs > 0) {
                    val progress = (video.playbackPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = SpotifyGreen,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.folderName} • ${formatFileSize(video.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (video.playbackPositionMs > 0 && video.durationMs > 0) {
                    val pct = ((video.playbackPositionMs.toDouble() / video.durationMs.toDouble()) * 100).toInt()
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Resume at ${formatDuration(video.playbackPositionMs)} ($pct%)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Actions
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(36.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_row_fav_${video.id}")
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (video.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (video.isFavorite) SpotifyGreen else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier
                    .size(36.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_row_options_${video.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    video: VideoItem,
    onResumeClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onResumeClick() }
            .testTag("continue_card_${video.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(rememberArtBrush(video.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
                AsyncImage(
                    model = dev.asfakul.mtrevanced.ui.image.VideoThumb(video.uri),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                // Play icon button circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume video",
                        tint = OnAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Resolution badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = video.resolution,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Progress bar overlay at bottom
                if (video.playbackPositionMs > 0 && video.durationMs > 0) {
                    val progress = (video.playbackPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = SpotifyGreen,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val pct = if (video.durationMs > 0) ((video.playbackPositionMs.toDouble() / video.durationMs.toDouble()) * 100).toInt() else 0
                Text(
                    text = "Left off at ${formatDuration(video.playbackPositionMs)} ($pct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun VideoFolderCard(
    folder: VideoFolder,
    onFolderClick: () -> Unit,
    onHideFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onFolderClick() }
            .testTag("video_folder_${folder.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.videoCount} videos • ${formatFileSize(folder.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (folder.path.isNotBlank() && folder.path != folder.name) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onHideFolder,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hide_folder_btn_${folder.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "Hide folder",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VideoDetailsDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHideFolder: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Video Details",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailItem(label = "Title", value = video.title)
                DetailItem(label = "Duration", value = formatDuration(video.durationMs))
                DetailItem(label = "Resolution", value = "${video.resolution} (${if (video.width > 0 && video.height > 0) "${video.width}x${video.height}" else "Auto"})")
                DetailItem(label = "File Size", value = formatFileSize(video.sizeBytes))
                DetailItem(label = "Folder", value = video.folderName)
                if (video.folderPath.isNotBlank()) {
                    DetailItem(label = "Location", value = video.folderPath)
                }
                if (video.playbackPositionMs > 0) {
                    val pct = if (video.durationMs > 0) ((video.playbackPositionMs.toDouble() / video.durationMs.toDouble()) * 100).toInt() else 0
                    DetailItem(
                        label = "Watch Progress",
                        value = "${formatDuration(video.playbackPositionMs)} / ${formatDuration(video.durationMs)} ($pct%)"
                    )
                }
                DetailItem(label = "Favorite", value = if (video.isFavorite) "Yes ★" else "No")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onPlay()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                modifier = Modifier.testTag("dialog_play_video_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = OnAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (video.playbackPositionMs > 0) "Resume" else "Play",
                    color = OnAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dialog_favorite_btn")
                ) {
                    Text(
                        text = if (video.isFavorite) "Unfavorite" else "Favorite",
                        color = SpotifyGreen
                    )
                }
                TextButton(
                    onClick = {
                        onHideFolder()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dialog_hide_folder_btn")
                ) {
                    Text("Hide Folder", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurfaceContainerHigh,
        modifier = Modifier.testTag("video_details_dialog")
    )
}

@Composable
fun ManageHiddenFoldersDialog(
    hiddenFolderPaths: Set<String>,
    folders: List<VideoFolder>,
    onDismiss: () -> Unit,
    onUnhideFolder: (String) -> Unit,
    onUnhideAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Hidden Video Folders",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (hiddenFolderPaths.isEmpty()) {
                    Text(
                        text = "No folders are currently hidden. You can hide unwanted video folders from their 3-dot options menu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "Videos in these folders are hidden from your library and searches:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(hiddenFolderPaths.toList()) { path ->
                            val folderName = folders.firstOrNull { it.path == path || it.name == path }?.name
                                ?: path.trim('/').split('/').lastOrNull() ?: path

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextTertiary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { onUnhideFolder(path) },
                                        modifier = Modifier.testTag("unhide_folder_$folderName")
                                    ) {
                                        Text("Unhide", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hiddenFolderPaths.isNotEmpty()) {
                Button(
                    onClick = {
                        onUnhideAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.testTag("unhide_all_folders_btn")
                ) {
                    Text("Unhide All", color = OnAccent, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = SpotifyGreen)
                }
            }
        },
        dismissButton = {
            if (hiddenFolderPaths.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurfaceContainerHigh,
        modifier = Modifier.testTag("manage_hidden_folders_dialog")
    )
}

@Composable
fun EmptyMediaView(
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GlassFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onButtonClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.testTag("empty_state_action_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buttonText,
                color = OnAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionPromptCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("permission_prompt_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Storage Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Yumeiro operates 100% offline. Allow media access to scan and play your device's audio tracks and video clips.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text(
                    text = "Grant Media Access",
                    color = OnAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Phase 4: Long-Press Options, Playlists Modal & Batch Actions
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionBottomSheet(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurfaceContainerHigh,
        modifier = modifier.testTag("song_action_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Track Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    // Artwork is drawn on top of the fallback icon; if it fails to load the icon stays visible.
                    if (!track.albumArtUri.isNullOrBlank()) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = track.album,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Items
            SheetActionRow(
                title = "Play Next",
                icon = Icons.Default.PlayArrow,
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
                testTag = "sheet_action_play_next"
            )

            SheetActionRow(
                title = "Add to Queue",
                icon = Icons.Default.PlaylistAdd,
                onClick = {
                    onAddToQueue()
                    onDismiss()
                },
                testTag = "sheet_action_add_queue"
            )

            SheetActionRow(
                title = "Add to Playlist...",
                icon = Icons.Default.QueueMusic,
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                },
                testTag = "sheet_action_add_playlist"
            )

            SheetActionRow(
                title = if (track.isFavorite) "Remove from Favorites" else "Add to Favorites",
                icon = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                iconTint = if (track.isFavorite) SpotifyGreen else TextPrimary,
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
                testTag = "sheet_action_toggle_fav"
            )

            SheetActionRow(
                title = "Song Details",
                icon = Icons.Default.Info,
                onClick = {
                    onDismiss()
                    onShowDetails()
                },
                testTag = "sheet_action_details"
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SheetActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    iconTint: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onSelectPlaylist: (String) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isCreatingNew) "Create New Playlist" else "Add to Playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            if (isCreatingNew) {
                Column {
                    Text(
                        text = "Enter a name for your new playlist:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Road Trip", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = DarkOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isCreatingNew = true }
                                .padding(vertical = 8.dp),
                            color = SpotifyGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "New Playlist...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyGreen
                                )
                            }
                        }
                    }

                    items(playlists) { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(vertical = 4.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${playlist.trackIds.size} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextTertiary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreateNewPlaylist(newPlaylistName.trim())
                        }
                    }
                ) {
                    Text("Create & Add", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isCreatingNew) isCreatingNew = false else onDismiss()
                }
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceContainerHigh
    )
}

@Composable
fun SongDetailsDialog(
    track: AudioTrack,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Song Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailItem(label = "Title", value = track.title)
                DetailItem(label = "Artist", value = track.artist)
                DetailItem(label = "Album", value = track.album)
                DetailItem(label = "Duration", value = formatDuration(track.durationMs))
                DetailItem(label = "File Size", value = formatFileSize(track.sizeBytes))
                DetailItem(label = "Folder", value = track.folderName)
                if (track.folderPath.isNotBlank()) {
                    DetailItem(label = "Path", value = track.folderPath)
                }
                DetailItem(label = "Plays", value = "${track.playCount} times")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SpotifyGreen, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceContainerHigh
    )
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SpotifyGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
fun BatchActionBar(
    selectedCount: Int,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToFavorites: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("batch_action_bar"),
        color = GlassFill,
        border = androidx.compose.foundation.BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play Selected
                IconButton(onClick = onPlayAll, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Selected",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Add Queue
                IconButton(onClick = onAddToQueue, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Queue Selected",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Add to Playlist
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Add to playlist",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Favorite
                IconButton(onClick = onAddToFavorites, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite Selected",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
