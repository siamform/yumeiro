/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.screens.video

import dev.asfakul.mtrevanced.ui.theme.GlassFill
import dev.asfakul.mtrevanced.ui.components.PillTabRow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.mtrevanced.data.model.VideoFolder
import dev.asfakul.mtrevanced.data.model.VideoItem
import dev.asfakul.mtrevanced.data.model.VideoSort
import dev.asfakul.mtrevanced.ui.components.ContinueWatchingCard
import dev.asfakul.mtrevanced.ui.components.EmptyMediaView
import dev.asfakul.mtrevanced.ui.components.VideoFolderCard
import dev.asfakul.mtrevanced.ui.components.VideoGridCard
import dev.asfakul.mtrevanced.ui.components.VideoRowCard
import dev.asfakul.mtrevanced.ui.components.formatFileSize
import dev.asfakul.mtrevanced.ui.theme.DarkOutline
import dev.asfakul.mtrevanced.ui.theme.DarkSurface
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.mtrevanced.ui.theme.DarkSurfaceVariant
import dev.asfakul.mtrevanced.ui.theme.SpotifyGreen
import dev.asfakul.mtrevanced.ui.theme.TextPrimary
import dev.asfakul.mtrevanced.ui.theme.TextSecondary
import dev.asfakul.mtrevanced.ui.theme.TextTertiary

@Composable
fun VideoScreen(
    videos: List<VideoItem>,
    folders: List<VideoFolder>,
    continueWatching: List<VideoItem>,
    recentlyWatched: List<VideoItem>,
    favorites: List<VideoItem>,
    hiddenFolderPaths: Set<String>,
    activeTab: Int,
    currentSort: VideoSort,
    isGridView: Boolean,
    selectedFolder: VideoFolder?,
    searchQuery: String,
    onTabChange: (Int) -> Unit,
    onSortChange: (VideoSort) -> Unit,
    onToggleLayout: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOptionsClick: (VideoItem) -> Unit,
    onFolderClick: (VideoFolder) -> Unit,
    onBackFromFolder: () -> Unit,
    onHideFolder: (String) -> Unit,
    onManageHiddenFoldersClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "All Videos",
        "Folders",
        "Continue (${continueWatching.size})",
        "Recent (${recentlyWatched.size})",
        "Favorites (${favorites.size})"
    )

    var showSortMenu by remember { mutableStateOf(false) }

    // Intercept back button if viewing folder detail
    BackHandler(enabled = selectedFolder != null) {
        onBackFromFolder()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_screen_root")
    ) {
        // Search & Filter header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text("Search offline videos by title or folder...", color = TextTertiary, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = DarkOutline,
                    focusedContainerColor = GlassFill,
                    unfocusedContainerColor = GlassFill,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_search_input")
            )
        }

        // Tab Row (only if not drilling down in folder)
        if (selectedFolder == null) {
            PillTabRow(tabs = tabs, selectedIndex = activeTab, onSelect = onTabChange, tagPrefix = "video_tab")
        }

        // Subheader with Count, Sort, and View Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFolder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackFromFolder,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("folder_detail_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Folders",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = selectedFolder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${selectedFolder.videoCount} videos • ${formatFileSize(selectedFolder.totalSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                val countText = when (activeTab) {
                    0 -> "${videos.size} offline videos"
                    1 -> "${folders.size} folders"
                    2 -> "${continueWatching.size} in progress"
                    3 -> "${recentlyWatched.size} watched recently"
                    4 -> "${favorites.size} favorite videos"
                    else -> "${videos.size} videos"
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            // Right actions (Sort dropdown & Grid toggle & Rescan)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sort menu button (for video tabs)
                if (activeTab != 1 || selectedFolder != null) {
                    Box {
                        TextButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("video_sort_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort videos",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val sortLabel = when (currentSort) {
                                VideoSort.NAME_ASC -> "A-Z"
                                VideoSort.NAME_DESC -> "Z-A"
                                VideoSort.DATE_DESC -> "Newest"
                                VideoSort.DATE_ASC -> "Oldest"
                                VideoSort.DURATION_DESC -> "Duration"
                                VideoSort.DURATION_ASC -> "Shortest"
                                VideoSort.SIZE_DESC -> "Size"
                            }
                            Text(
                                text = sortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                VideoSort.DATE_DESC to "Date Added (Newest)",
                                VideoSort.DATE_ASC to "Date Added (Oldest)",
                                VideoSort.NAME_ASC to "Name (A to Z)",
                                VideoSort.NAME_DESC to "Name (Z to A)",
                                VideoSort.DURATION_DESC to "Duration (Longest)",
                                VideoSort.DURATION_ASC to "Duration (Shortest)",
                                VideoSort.SIZE_DESC to "File Size (Largest)"
                            ).forEach { (sort, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                fontWeight = if (currentSort == sort) FontWeight.Bold else FontWeight.Normal,
                                                color = if (currentSort == sort) SpotifyGreen else Color.Unspecified
                                            )
                                            if (currentSort == sort) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = SpotifyGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortChange(sort)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Layout toggle (Grid vs List)
                IconButton(
                    onClick = onToggleLayout,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .testTag("video_layout_toggle")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "Toggle Grid/List",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Rescan button
                IconButton(
                    onClick = onRescanClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .testTag("video_rescan_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan offline videos",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Main Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when {
                // Folder detail view
                selectedFolder != null -> {
                    val folderVideos = selectedFolder.videos.filter {
                        if (searchQuery.isBlank()) true
                        else it.title.contains(searchQuery, ignoreCase = true)
                    }
                    if (folderVideos.isEmpty()) {
                        EmptyMediaView(
                            title = "No videos in ${selectedFolder.name}",
                            subtitle = "This folder does not contain any matching videos.",
                            buttonText = "Back to Folders",
                            onButtonClick = onBackFromFolder,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoListOrGrid(
                            videos = folderVideos,
                            isGridView = isGridView,
                            onVideoClick = { onVideoClick(it, folderVideos) },
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick
                        )
                    }
                }

                // Tab 0: All Videos
                activeTab == 0 -> {
                    if (videos.isEmpty()) {
                        EmptyMediaView(
                            title = "No offline videos found",
                            subtitle = "Place MP4, MKV, WebM, or 3GP video files in your device storage and tap Rescan.",
                            buttonText = "Rescan Videos",
                            onButtonClick = onRescanClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val showContinue = continueWatching.isNotEmpty() && searchQuery.isBlank()
                        // Continue Watching is the first item of the list/grid so it scrolls away
                        // with the content instead of staying pinned.
                        VideoListOrGrid(
                            videos = videos,
                            isGridView = isGridView,
                            onVideoClick = { onVideoClick(it, videos) },
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick,
                            header = if (!showContinue) null else {
                                {
                                    Column(modifier = Modifier.padding(bottom = 6.dp).testTag("continue_watching_strip")) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Continue watching",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = TextPrimary
                                            )
                                            TextButton(onClick = { onTabChange(2) }) {
                                                Text("See all", color = SpotifyGreen, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(continueWatching.take(8), key = { "cw_${it.id}" }) { video ->
                                                ContinueWatchingCard(
                                                    video = video,
                                                    onResumeClick = { onVideoClick(video, continueWatching) },
                                                    onToggleFavorite = { onToggleFavorite(video.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Tab 1: Folders
                activeTab == 1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Hidden folders management bar
                        if (hiddenFolderPaths.isNotEmpty()) {
                            Surface(
                                color = DarkSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onManageHiddenFoldersClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${hiddenFolderPaths.size} hidden folder(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "Manage",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SpotifyGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val visibleFolders = folders.filter { !it.isHidden }
                        if (visibleFolders.isEmpty()) {
                            EmptyMediaView(
                                title = "No video folders found",
                                subtitle = "Folders with offline video files will appear here.",
                                buttonText = "Rescan Media",
                                onButtonClick = onRescanClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("video_folders_list"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(visibleFolders, key = { "vf_${it.id}" }) { folder ->
                                    VideoFolderCard(
                                        folder = folder,
                                        onFolderClick = { onFolderClick(folder) },
                                        onHideFolder = { onHideFolder(folder.path) }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(110.dp))
                                }
                            }
                        }
                    }
                }

                // Tab 2: Continue Watching
                activeTab == 2 -> {
                    if (continueWatching.isEmpty()) {
                        EmptyMediaView(
                            title = "No videos in progress",
                            subtitle = "When you pause a video or exit playback, your resume point will be saved here automatically.",
                            buttonText = "Browse Videos",
                            onButtonClick = { onTabChange(0) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoListOrGrid(
                            videos = continueWatching,
                            isGridView = isGridView,
                            onVideoClick = { onVideoClick(it, continueWatching) },
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick
                        )
                    }
                }

                // Tab 3: Recently Watched
                activeTab == 3 -> {
                    if (recentlyWatched.isEmpty()) {
                        EmptyMediaView(
                            title = "No watch history",
                            subtitle = "Videos you play will be recorded here in chronological order.",
                            buttonText = "Explore Videos",
                            onButtonClick = { onTabChange(0) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoListOrGrid(
                            videos = recentlyWatched,
                            isGridView = isGridView,
                            onVideoClick = { onVideoClick(it, recentlyWatched) },
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick
                        )
                    }
                }

                // Tab 4: Favorites
                activeTab == 4 -> {
                    if (favorites.isEmpty()) {
                        EmptyMediaView(
                            title = "No favorite videos yet",
                            subtitle = "Tap the heart icon on any video card to pin it to your favorites vault.",
                            buttonText = "View All Videos",
                            onButtonClick = { onTabChange(0) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoListOrGrid(
                            videos = favorites,
                            isGridView = isGridView,
                            onVideoClick = { onVideoClick(it, favorites) },
                            onToggleFavorite = onToggleFavorite,
                            onOptionsClick = onOptionsClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoListOrGrid(
    videos: List<VideoItem>,
    isGridView: Boolean,
    onVideoClick: (VideoItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOptionsClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    if (isGridView) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val columns = when {
                maxWidth >= 1000.dp -> 5
                maxWidth >= 800.dp -> 4
                maxWidth >= 560.dp -> 3
                else -> 2
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("video_grid"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (header != null) item(key = "header", span = { GridItemSpan(columns) }, contentType = "header") { header() }
                items(videos, key = { "v_${it.id}" }, contentType = { "video" }) { video ->
                    VideoGridCard(
                        video = video,
                        onVideoClick = { onVideoClick(video) },
                        onToggleFavorite = { onToggleFavorite(video.id) },
                        onOptionsClick = { onOptionsClick(video) }
                    )
                }

                item(span = { GridItemSpan(columns) }) {
                    Spacer(modifier = Modifier.height(110.dp))
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("video_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (header != null) item(key = "header", contentType = "header") { header() }
            items(videos, key = { "v_${it.id}" }, contentType = { "video" }) { video ->
                VideoRowCard(
                    video = video,
                    onVideoClick = { onVideoClick(video) },
                    onToggleFavorite = { onToggleFavorite(video.id) },
                    onOptionsClick = { onOptionsClick(video) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }
}
