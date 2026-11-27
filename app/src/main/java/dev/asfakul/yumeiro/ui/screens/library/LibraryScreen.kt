/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.ui.components.EmptyMediaView
import dev.asfakul.yumeiro.ui.components.TrackItemRow
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.components.formatFileSize
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkOutlineVariant
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.SpotifyGreenBright
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

@Composable
fun LibraryScreen(
    stats: StorageStats,
    playlists: List<Playlist>,
    allTracks: List<AudioTrack>,
    recentlyPlayed: List<AudioTrack>,
    mostPlayed: List<AudioTrack>,
    currentPlayingTrack: AudioTrack?,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onReorderPlaylist: (String, Int, Int) -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    onTrackOptionsClick: (AudioTrack) -> Unit,
    huesCount: Int = 0,
    onRescanClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenVideos: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenTogether: () -> Unit = {},
    onOpenStats: (() -> Unit)? = null,
) {
    var selectedPlaylistId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) } // kept across rotation
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val listenedMs by dev.asfakul.yumeiro.player.ListeningTime.total.collectAsState()

    // Dialog: Create Playlist
    if (showCreateDialog) {
        TsukiDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = {
                Text(
                    text = stringResource(R.string.new_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.name_your_playlist),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.e_g_focus_chill), color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = DarkOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    },
                    modifier = Modifier.testTag("create_playlist_confirm")
                ) {
                    Text(stringResource(R.string.create), color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceContainerHigh
        )
    }

    // Dialog: Rename Playlist
    playlistToRename?.let { pl ->
        TsukiDialog(
            onDismissRequest = { playlistToRename = null },
            title = {
                Text(
                    text = stringResource(R.string.rename_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist(pl.id, renameText.trim())
                            playlistToRename = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.save), color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceContainerHigh
        )
    }

    // Dialog: Delete Playlist
    playlistToDelete?.let { pl ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
            onDismissRequest = { playlistToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.are_you_sure_you_want_to, pl.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(pl.id)
                        if (selectedPlaylistId == pl.id) {
                            selectedPlaylistId = null
                        }
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = dev.asfakul.yumeiro.ui.theme.DangerTone, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceContainerHigh
        )
    }

    if (selectedPlaylist != null) {
        // Detailed Playlist View
        BackHandler { selectedPlaylistId = null }

        val playlistTracks = remember(selectedPlaylist.trackIds, trackMap) { selectedPlaylist.trackIds.mapNotNull { trackMap[it] } }

        LazyColumn(state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("library_main"), 
            modifier = modifier
                .fillMaxSize()
                .testTag("playlist_detail_view"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedPlaylistId = null },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_library),
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPlaylist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.songs, playlistTracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Rename
                    IconButton(
                        onClick = {
                            playlistToRename = selectedPlaylist
                            renameText = selectedPlaylist.name
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.rename_playlist),
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = { playlistToDelete = selectedPlaylist },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_playlist_2),
                            tint = dev.asfakul.yumeiro.ui.theme.DangerTone,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Play & Shuffle buttons
            if (playlistTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                playlistTracks.firstOrNull()?.let { onTrackClick(it, playlistTracks) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = yuRound(20),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = OnAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.play_all), color = OnAccent, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (playlistTracks.isNotEmpty()) {
                                    val shuffled = playlistTracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = yuRound(20),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = SpotifyGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.shuffle), color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Playlist track items with reorder (Move Up / Down) & Remove buttons
            if (playlistTracks.isEmpty()) {
                item {
                    EmptyMediaView(
                        title = "Playlist is empty",
                        subtitle = "In Music, tap ⋮ on any song and choose Add to playlist.",
                        buttonText = "Browse music",
                        onButtonClick = onNavigateToMusic
                    )
                }
            } else {
                itemsIndexed(playlistTracks, key = { i, t -> "${t.id}_$i" }) { index, track ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(yuRound(10)),
                        color = if (currentPlayingTrack?.id == track.id) SpotifyGreen.copy(alpha = 0.15f) else GlassFill
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(track, playlistTracks) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                modifier = Modifier.width(22.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentPlayingTrack?.id == track.id) SpotifyGreenBright else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Move Up
                            if (index > 0) {
                                IconButton(
                                    onClick = { onReorderPlaylist(selectedPlaylist.id, index, index - 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(R.string.move_up),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Move Down
                            if (index < playlistTracks.size - 1) {
                                IconButton(
                                    onClick = { onReorderPlaylist(selectedPlaylist.id, index, index + 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.move_down),
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Remove from playlist
                            IconButton(
                                onClick = { onRemoveTrackFromPlaylist(selectedPlaylist.id, track.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = stringResource(R.string.remove_from_playlist),
                                    tint = TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Main Library Overview
        LazyColumn(
            state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("library"),
            modifier = modifier
                .fillMaxSize()
                .testTag("library_screen_root"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            musicOverview(
                stats = stats, playlists = playlists, allTracks = allTracks, trackMap = trackMap,
                recentlyPlayed = recentlyPlayed, mostPlayed = mostPlayed, currentPlayingTrack = currentPlayingTrack,
                onPlay = onTrackClick, onOpenMusic = onNavigateToMusic, onOpenEqualizer = onNavigateToEqualizer,
                onRescan = onRescanClick, onCreatePlaylist = { showCreateDialog = true }, onOpenPlaylist = { selectedPlaylistId = it },
                shortcuts = libraryShortcuts(
                    scanning = stats.isScanning, onVideos = onOpenVideos, onReading = onOpenReading, onEqualizer = onNavigateToEqualizer,
                    onNewPlaylist = { showCreateDialog = true }, onRescan = onRescanClick, onHistory = onOpenHistory,
                    onDownloads = onOpenDownloads, onTogether = onOpenTogether,
                ),
                listenedMs = listenedMs,
                statsEntry = onOpenStats?.let { open -> { dev.asfakul.yumeiro.ui.screens.stats.StatsEntryCard(open) } },
            )
        }
    }
}

@Composable
fun LibraryCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        modifier = modifier
            .clip(yuRound(20))
            .clickable { onClick() }
            .testTag(testTag),
        color = GlassFill,
        border = null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(yuRound(12))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
