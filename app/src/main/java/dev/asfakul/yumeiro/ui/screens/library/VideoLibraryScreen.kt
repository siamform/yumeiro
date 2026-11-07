/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.ui.screens.online.ContinueRow
import dev.asfakul.yumeiro.ui.screens.online.OnlineChip
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.screens.online.SectionHeader
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/**
 * The video half of Library: continue watching (online and on this device), My List with
 * statuses, and offline downloads.
 */
@Composable
fun VideoLibraryContent(
    deviceContinue: List<VideoItem>,
    onOpenTitle: (OnlineItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onBrowseOnline: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OnlineBookmarks.ensureLoaded(context); OnlineDownloads.ensureLoaded(context); OnlineHistory.ensureLoaded(context)
    }
    val bookmarks by OnlineBookmarks.items.collectAsState()
    val downloads by OnlineDownloads.items.collectAsState()
    val historyAll by OnlineHistory.entries.collectAsState()
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    val history = androidx.compose.runtime.remember(historyAll, adultOn, adultPrivate) { OnlineHistory.forFeed(historyAll, adultOn, adultPrivate) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(bookmarks, status) { bookmarks.filter { status == null || it.status.name == status } }

    LazyVerticalGrid(
        GridCells.Adaptive(108.dp), modifier.fillMaxSize().testTag("video_library"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) item("hdr", span = { GridItemSpan(maxLineSpan) }) { header() }
        if (history.isNotEmpty()) item("cont", span = { GridItemSpan(maxLineSpan) }) {
            ContinueRow(history, onClick = onOpenTitle, onSeeAll = onOpenHistory, edgePadding = 0.dp)
        }
        if (deviceContinue.isNotEmpty()) item("devcont", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                LibHeader("On this device")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(deviceContinue.take(15), key = { it.id }) { v -> DeviceCard(v) { onPlayVideo(v) } }
                }
            }
        }
        if (downloads.isNotEmpty()) {
            item("dlh", span = { GridItemSpan(maxLineSpan) }) { LibHeader("Downloads", subtitle = "${downloads.count { it.status == OnlineDownloads.Status.DONE }} saved for offline") }
            items(downloads, key = { "dl_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { d -> DownloadRow(d, onPlayVideo) }
        }
        item("mlh", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                LibHeader("My List", subtitle = if (bookmarks.isEmpty()) null else "${bookmarks.size} saved")
                if (bookmarks.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OnlineChip("All", status == null, { status = null }) }
                    items(OnlineBookmarks.Status.entries.filter { s -> bookmarks.any { it.status == s } }, key = { it.name }) { s ->
                        OnlineChip(s.label, status == s.name, { status = if (status == s.name) null else s.name })
                    }
                }
            }
        }
        if (bookmarks.isEmpty()) item("mlempty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyCard(Icons.Outlined.BookmarkBorder, "Nothing saved yet", "Tap My List on any movie or show to keep it here — new episodes of saved shows are announced.", "Browse online", onBrowseOnline)
        }
        items(shown, key = { "bm_" + it.key }) { b -> BookmarkCard(b) { onOpenTitle(b.toItem()) } }
        if (bookmarks.isEmpty() && downloads.isEmpty() && history.isEmpty() && deviceContinue.isEmpty()) item("allempty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyCard(Icons.Outlined.VideoLibrary, "Your videos show up here", "Watch something on this device or online and it will be ready to continue.", null, null)
        }
    }
}

@Composable
private fun DeviceCard(v: VideoItem, onClick: () -> Unit) {
    Column(Modifier.width(180.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(DarkSurfaceContainer)) {
            PosterImage(v.thumbnailUri ?: v.uri, emptyMap(), Modifier.fillMaxSize())
            if (v.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth((v.playbackPositionMs.toFloat() / v.durationMs).coerceIn(0f, 1f)).height(3.dp).background(SpotifyGreen))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(v.title, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookmarkCard(b: OnlineBookmarks.Bookmark, onClick: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag("library_bookmark")) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(DarkSurfaceContainer)) {
            PosterImage(b.posterUrl, b.posterHeaders, Modifier.fillMaxSize())
            if (b.newEpisodes > 0) Text(
                if (b.newEpisodes == 1) "NEW EP" else "+${b.newEpisodes} EPS", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(SpotifyGreen).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text("⋮", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    OnlineBookmarks.Status.entries.forEach { s ->
                        DropdownMenuItem(text = { Text((if (s == b.status) "✓ " else "") + s.label) }, onClick = { OnlineBookmarks.setStatus(context, b.api, b.url, s); menu = false })
                    }
                    DropdownMenuItem(text = { Text(if (b.notify) "Turn off new-episode alerts" else "Alert me about new episodes") }, onClick = { OnlineBookmarks.setNotify(context, b.api, b.url, !b.notify); menu = false })
                    DropdownMenuItem(text = { Text("Remove from My List") }, onClick = { OnlineBookmarks.remove(context, b.api, b.url); menu = false })
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(b.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        Text(b.status.label + " · " + b.api, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DownloadRow(d: OnlineDownloads.Item, onPlayVideo: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val done = d.status == OnlineDownloads.Status.DONE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DarkSurfaceContainer)
            .clickable(enabled = done) {
                val f = OnlineDownloads.fileOf(context, d)
                if (f.exists()) onPlayVideo(VideoItem(id = "download:${d.id}", title = listOfNotNull(d.title, d.episodeLabel).joinToString(" · "), durationMs = 0L, uri = android.net.Uri.fromFile(f).toString(), resolution = "", folderName = "Downloads", sizeBytes = f.length()))
                else dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "The file was removed")
            }.padding(10.dp).testTag("library_download"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) { PosterImage(d.posterUrl, d.posterHeaders, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            d.episodeLabel?.let { Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val line = when (d.status) {
                OnlineDownloads.Status.DONE -> "Saved · ${mb(d.bytes)}"
                OnlineDownloads.Status.RUNNING -> if (d.total > 0) "${(d.fraction * 100).toInt()}% · ${mb(d.bytes)} of ~${mb(d.total)}" else "Downloading · ${mb(d.bytes)}"
                OnlineDownloads.Status.QUEUED -> "Waiting…"
                OnlineDownloads.Status.PAUSED -> "Paused · ${mb(d.bytes)}"
                OnlineDownloads.Status.FAILED -> d.error ?: "Failed"
            }
            Text(line, color = if (d.status == OnlineDownloads.Status.FAILED) Color(0xFFFF8A80) else TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.status == OnlineDownloads.Status.RUNNING) {
                Spacer(Modifier.height(5.dp))
                if (d.total > 0) LinearProgressIndicator(progress = { d.fraction }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SpotifyGreen)
                else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = SpotifyGreen)
            }
        }
        when (d.status) {
            OnlineDownloads.Status.DONE -> Icon(Icons.Default.PlayArrow, "Play", tint = SpotifyGreen, modifier = Modifier.padding(horizontal = 6.dp))
            OnlineDownloads.Status.RUNNING, OnlineDownloads.Status.QUEUED -> IconButton(onClick = { OnlineDownloads.pause(context, d.id) }) { Icon(Icons.Default.Pause, "Pause", tint = TextPrimary) }
            OnlineDownloads.Status.PAUSED -> IconButton(onClick = { OnlineDownloads.start(context, d.id) }) { Icon(Icons.Default.Download, "Resume", tint = TextPrimary) }
            OnlineDownloads.Status.FAILED -> IconButton(onClick = { OnlineDownloads.start(context, d.id) }) { Icon(Icons.Default.Refresh, "Retry", tint = TextPrimary) }
        }
        IconButton(onClick = { OnlineDownloads.delete(context, d.id) }) { Icon(Icons.Default.Delete, "Delete", tint = TextTertiary) }
    }
}

private fun mb(b: Long): String = if (b >= 1L shl 30) "%.2f GB".format(java.util.Locale.US, b / 1073741824.0) else "%.0f MB".format(java.util.Locale.US, b / 1048576.0)

@Composable
private fun EmptyCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkSurfaceContainer).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Text(action, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(SpotifyGreen).clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun LibHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        subtitle?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1) }
    }
}

/** "Music | Videos" switch at the top of Library. */
@Composable
fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.material3.Surface(shape = RoundedCornerShape(24.dp), color = dev.asfakul.yumeiro.ui.theme.GlassFill, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().testTag("library_tabs")) {
        Row(Modifier.padding(4.dp)) {
            listOf("Music", "Videos").forEachIndexed { i, label ->
                val sel = i == selected
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp), color = if (sel) SpotifyGreen else Color.Transparent,
                    modifier = Modifier.weight(1f).height(36.dp).clickable { onSelect(i) }.testTag("library_tab_$i"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = if (sel) dev.asfakul.yumeiro.ui.theme.OnAccent else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
