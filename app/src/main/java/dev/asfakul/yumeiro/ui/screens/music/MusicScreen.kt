/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.music

import dev.asfakul.yumeiro.ui.components.PillTabRow
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.Album
import dev.asfakul.yumeiro.data.model.Artist
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.MusicFolder
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.data.model.TrackSort
import dev.asfakul.yumeiro.ui.components.BatchActionBar
import dev.asfakul.yumeiro.ui.components.EmptyMediaView
import dev.asfakul.yumeiro.ui.components.PermissionPromptCard
import dev.asfakul.yumeiro.ui.components.TrackItemRow
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkOutlineVariant
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceVariant
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

@Composable
fun MusicScreen(
    tracks: List<AudioTrack>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<MusicFolder>,
    stats: StorageStats,
    currentPlayingTrack: AudioTrack?,
    isPlaying: Boolean,
    activeTab: Int,
    currentSort: TrackSort,
    isSelectionMode: Boolean,
    selectedTrackIds: Set<String>,
    onTabChange: (Int) -> Unit,
    onSortChange: (TrackSort) -> Unit,
    onTrackClick: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackOptionsClick: (AudioTrack) -> Unit,
    onToggleSelectionMode: (String?) -> Unit,
    onToggleTrackSelection: (String) -> Unit,
    onSelectAll: (List<AudioTrack>) -> Unit,
    onClearSelection: () -> Unit,
    onPlaySelected: () -> Unit,
    onAddSelectedToQueue: () -> Unit,
    onAddSelectedToFavorites: () -> Unit,
    onAddSelectedToPlaylist: () -> Unit,
    onRescanClick: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Tracks", "Albums", "Artists", "Folders", "Favorites")
    var showSortMenu by remember { mutableStateOf(false) }

    var expandedAlbumId by remember { mutableStateOf<Long?>(null) }
    var expandedArtistName by remember { mutableStateOf<String?>(null) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }

    val favoriteTracks = remember(tracks) { tracks.filter { it.isFavorite } }
    val currentTrackList = if (activeTab == 4) favoriteTracks else tracks

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_screen_root")
    ) {
        // Tab Row
        PillTabRow(tabs = tabs, selectedIndex = activeTab, onSelect = onTabChange, tagPrefix = "music_tab")

        // Permission banner if needed
        if (!stats.hasStoragePermission && stats.permissionPermanentlyDenied) {
            PermissionPromptCard(
                onRequestPermission = onRequestPermission,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Header controls for Tracks & Favorites
        if (activeTab == 0 || activeTab == 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentTrackList.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Multi-select Toggle Button
                    IconButton(
                        onClick = { onToggleSelectionMode(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                            contentDescription = "Select Mode",
                            tint = if (isSelectionMode) SpotifyGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sort Button & Menu
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(GlassFill)
                                .clickable { showSortMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("track_sort_dropdown_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Options",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val sortLabel = when (currentSort) {
                                TrackSort.TITLE_ASC -> "A to Z"
                                TrackSort.TITLE_DESC -> "Z to A"
                                TrackSort.RECENTLY_ADDED -> "Recently Added"
                                TrackSort.DURATION -> "Duration"
                                TrackSort.ARTIST -> "Artist"
                                TrackSort.ALBUM -> "Album"
                            }
                            Text(
                                text = sortLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurfaceContainerHigh)
                        ) {
                            TrackSort.values().forEach { sortOption ->
                                val label = when (sortOption) {
                                    TrackSort.TITLE_ASC -> "Title (A to Z)"
                                    TrackSort.TITLE_DESC -> "Title (Z to A)"
                                    TrackSort.RECENTLY_ADDED -> "Recently Added"
                                    TrackSort.DURATION -> "Duration"
                                    TrackSort.ARTIST -> "Artist"
                                    TrackSort.ALBUM -> "Album"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (sortOption == currentSort) SpotifyGreen else TextPrimary
                                        )
                                    },
                                    onClick = {
                                        onSortChange(sortOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Shuffle All
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen)
                            .clickable {
                                if (currentTrackList.isNotEmpty()) {
                                    val randomTrack = currentTrackList.random()
                                    onTrackClick(randomTrack, currentTrackList.shuffled())
                                }
                            }
                            .testTag("music_shuffle_all_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle All",
                            tint = OnAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Batch Action Bar when Selection Mode is Active
        AnimatedVisibility(visible = isSelectionMode) {
            BatchActionBar(
                selectedCount = selectedTrackIds.size,
                onPlayAll = onPlaySelected,
                onAddToQueue = onAddSelectedToQueue,
                onAddToFavorites = onAddSelectedToFavorites,
                onAddToPlaylist = onAddSelectedToPlaylist,
                onCancel = onClearSelection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Tab Content
        when (activeTab) {
            0, 4 -> {
                // Tracks or Favorites Tab
                if (currentTrackList.isEmpty()) {
                    EmptyMediaView(
                        title = if (activeTab == 4) "No favorite songs yet" else "No audio files detected",
                        subtitle = if (activeTab == 4) "Tap the heart icon on any song to add it to your favorites."
                        else "Place MP3, FLAC, M4A, OGG, or WAV files in local storage and tap Rescan.",
                        buttonText = if (activeTab == 4) "Browse Songs" else "Scan Storage",
                        onButtonClick = if (activeTab == 4) { { onTabChange(0) } } else onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("music_tracks_" + activeTab),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("music_tracks_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentTrackList, key = { "t_${it.id}" }) { track ->
                            TrackItemRow(
                                track = track,
                                isSelected = currentPlayingTrack?.id == track.id,
                                onTrackClick = { onTrackClick(track, currentTrackList) },
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onLongClick = {
                                    onToggleSelectionMode(track.id)
                                },
                                onMoreOptionsClick = {
                                    onTrackOptionsClick(track)
                                },
                                isSelectionMode = isSelectionMode,
                                isChecked = selectedTrackIds.contains(track.id),
                                onCheckedChange = { onToggleTrackSelection(track.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            1 -> {
                // Albums Tab
                if (albums.isEmpty()) {
                    EmptyMediaView(
                        title = "No albums found",
                        subtitle = "Audio albums will appear here automatically when files are found.",
                        buttonText = "Rescan Media",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("music_albums"),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("albums_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(albums, key = { "al_${it.id}_${it.name}" }) { album ->
                            val isExpanded = expandedAlbumId == album.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        expandedAlbumId = if (isExpanded) null else album.id
                                    }
                                    .testTag("album_card_${album.id}"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = GlassFill),
                                border = null
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(dev.asfakul.yumeiro.ui.theme.rememberArtBrush(album.name)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                            // Artwork is drawn on top of the fallback icon; if it fails to load the icon stays visible.
                                            if (!album.albumArtUri.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = album.albumArtUri,
                                                    contentDescription = album.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${album.artist} • ${album.trackCount} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (album.tracks.isNotEmpty()) {
                                                            onTrackClick(album.tracks.first(), album.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = OnAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play Album", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            album.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, album.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            2 -> {
                // Artists Tab
                if (artists.isEmpty()) {
                    EmptyMediaView(
                        title = "No artists found",
                        subtitle = "Artists will be indexed automatically from your on-device audio files.",
                        buttonText = "Rescan Media",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("music_artists"),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artists_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(artists, key = { "ar_${it.name}" }) { artist ->
                            val isExpanded = expandedArtistName == artist.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        expandedArtistName = if (isExpanded) null else artist.name
                                    }
                                    .testTag("artist_card_${artist.name}"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = GlassFill),
                                border = null
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(SpotifyGreen.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${artist.trackCount} tracks • ${artist.albumCount} albums",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (artist.tracks.isNotEmpty()) {
                                                            onTrackClick(artist.tracks.first(), artist.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = OnAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play All", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            artist.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, artist.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }

            3 -> {
                // Folders Tab
                if (folders.isEmpty()) {
                    EmptyMediaView(
                        title = "No music folders found",
                        subtitle = "Audio directories on internal storage will appear here.",
                        buttonText = "Rescan Folders",
                        onButtonClick = onRescanClick,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("music_folders"),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("music_folders_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folders, key = { "f_${it.path}_${it.name}" }) { folder ->
                            val isExpanded = expandedFolderPath == folder.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        expandedFolderPath = if (isExpanded) null else folder.name
                                    }
                                    .testTag("folder_card_${folder.name}"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = GlassFill),
                                border = null
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SpotifyGreen.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${folder.trackCount} songs in this folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = TextTertiary
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(modifier = Modifier.padding(top = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (folder.tracks.isNotEmpty()) {
                                                            onTrackClick(folder.tracks.first(), folder.tracks)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                                    shape = RoundedCornerShape(16.dp),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = OnAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play Folder", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            folder.tracks.forEach { track ->
                                                TrackItemRow(
                                                    track = track,
                                                    isSelected = currentPlayingTrack?.id == track.id,
                                                    onTrackClick = { onTrackClick(track, folder.tracks) },
                                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                                    onLongClick = { onTrackOptionsClick(track) },
                                                    onMoreOptionsClick = { onTrackOptionsClick(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(110.dp)) }
                    }
                }
            }
        }
    }
}

/** Where the Music tab plays from. Saved as [key] so the choice survives rotation. */
enum class MusicMode(val key: String) { DEVICE("device"), ONLINE("online"), MIX("mix");
    companion object { fun of(key: String?): MusicMode = entries.firstOrNull { it.key == key } ?: DEVICE }
}

/** Header title of the Music tab for a mode ("On device", "Online", "Mix"). */
fun musicSourceTitle(mode: MusicMode): String = when (mode) { MusicMode.DEVICE -> "On device"; MusicMode.ONLINE -> "Online"; MusicMode.MIX -> "Mix" }

/** The Music source menu: where songs come from, each with its live status. */
fun musicSourceOptions(songCount: Int, online: Boolean): List<dev.asfakul.yumeiro.ui.components.SourceOption> = listOf(
    dev.asfakul.yumeiro.ui.components.SourceOption(
        "device", "On device", "Songs, albums, artists and folders on this phone",
        if (songCount == 1) "1 song" else "$songCount songs",
        androidx.compose.material.icons.Icons.Default.LibraryMusic, "music_source_device", attention = songCount == 0,
    ),
    dev.asfakul.yumeiro.ui.components.SourceOption(
        "online", "Online", "Search and stream from YouTube Music",
        if (online) "Streaming" else "Needs internet",
        androidx.compose.material.icons.Icons.Default.Album, "music_source_online", attention = !online,
    ),
    dev.asfakul.yumeiro.ui.components.SourceOption(
        "mix", "Mix", "Your songs and online picks in one place",
        if (online) "Device + online" else "Device part only",
        androidx.compose.material.icons.Icons.Default.Shuffle, "music_source_mix",
    ),
)
