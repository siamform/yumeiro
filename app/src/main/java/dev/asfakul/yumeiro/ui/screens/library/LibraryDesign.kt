/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.components.formatFileSize
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder

/* Shared building blocks for the Library tabs: a soft accent hero, stat strip, tiles, shelves. */

@Composable
internal fun LibrarySectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null, testTag: String? = null) {
    // Same editorial voice as Home: kanji · LABEL overline, then the title. The subtitle becomes the overline label.
    val kana = when (title) {
        "Jump back in" -> "続き"; "Playlists" -> "選曲"; "On repeat" -> "愛聴"; "Continue reading" -> "続読"; "Saved" -> "保存"
        else -> null
    }
    dev.asfakul.yumeiro.ui.components.YuSection(
        kana = kana, label = subtitle ?: when (title) { "Saved" -> "Your shelf"; "Continue reading" -> "Pick up again"; else -> "Library" }, title = title,
        top = 14.dp, action = action, onAction = onAction, actionTag = testTag,
    )
}

/** Big accent card: the hero of each tab. */
@Composable
internal fun LibraryHero(
    overline: String, title: String, subtitle: String, glyph: String,
    modifier: Modifier = Modifier, testTag: String = "", onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null, bottom: @Composable (() -> Unit)? = null,
) {
    val accent = SpotifyGreen
    val shape = RoundedCornerShape(26.dp)
    val palette = LocalMtPalette.current
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), palette.surfaceContainerHigh.copy(alpha = 0.85f), palette.surfaceContainerHigh.copy(alpha = 0.6f))))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width * 0.95f, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f, center = Offset(size.width * 0.95f, 0f))
            }
            .border(1.dp, rememberGlassBorder(), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .testTag(testTag),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(overline, color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                trailing?.invoke()
            }
            bottom?.let { Spacer(Modifier.height(16.dp)); it() }
        }
    }
}

@Composable
internal fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(TextPrimary.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun RoundAction(icon: ImageVector, description: String, filled: Boolean, testTag: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if (filled) SpotifyGreen else Color.Black.copy(alpha = 0.22f))
        .clickable(role = Role.Button, onClick = onClick).testTag(testTag), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = if (filled) OnAccent else TextPrimary, modifier = Modifier.size(24.dp))
    }
}

/** Compact square-ish tile with an icon chip, title and a one-line count. */
@Composable
internal fun LibraryTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, testTag: String = "", onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(GlassFill).clickable(onClick = onClick).padding(14.dp).testTag(testTag)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(SpotifyGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 2×2 artwork mosaic for a playlist (falls back to the gradient tile). */
@Composable
internal fun PlaylistMosaic(tracks: List<AudioTrack>, key: String, size: Dp) {
    val arts = tracks.mapNotNull { it.albumArtUri }.distinct().take(4)
    if (arts.size < 4) {
        TrackArtwork(arts.firstOrNull(), key, size, 16.dp, icon = Icons.AutoMirrored.Filled.QueueMusic)
    } else Column(Modifier.size(size).clip(RoundedCornerShape(16.dp))) {
        for (r in 0..1) Row(Modifier.weight(1f)) {
            for (c in 0..1) AsyncImage(arts[r * 2 + c], null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize().background(GlassFill))
        }
    }
}

internal fun songsLabel(n: Int, ms: Long): String =
    (if (n == 1) "1 song" else "$n songs") + if (ms > 0) " · " + formatDuration(ms) else ""

/** Music tab overview (playlist detail stays in [LibraryScreen]). */
internal fun LazyListScope.musicOverview(
    stats: StorageStats,
    playlists: List<Playlist>,
    allTracks: List<AudioTrack>,
    trackMap: Map<String, AudioTrack>,
    recentlyPlayed: List<AudioTrack>,
    mostPlayed: List<AudioTrack>,
    currentPlayingTrack: AudioTrack?,
    onPlay: (AudioTrack, List<AudioTrack>) -> Unit,
    onOpenMusic: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onRescan: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val favorites = allTracks.filter { it.isFavorite }
    item(key = "hero") {
        LibraryHero(
            overline = "Your music", title = "Liked songs",
            subtitle = if (favorites.isEmpty()) "Tap the heart on any song to keep it here" else songsLabel(favorites.size, favorites.sumOf { it.durationMs }),
            glyph = "夢", testTag = "lib_fav_card", onClick = onOpenMusic,
            trailing = {
                RoundAction(Icons.Default.PlayArrow, "Play liked songs", filled = true, testTag = "lib_play_liked") {
                    val q = favorites.ifEmpty { allTracks }; q.firstOrNull()?.let { onPlay(it, q) }
                }
            },
            bottom = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("storage_stat_banner")) {
                    HeroStat("${stats.audioCount}", "songs", Modifier.weight(1f))
                    HeroStat("${playlists.size}", "playlists", Modifier.weight(1f))
                    HeroStat(formatDuration(stats.totalAudioDurationMs), "playtime", Modifier.weight(1.2f))
                    HeroStat(formatFileSize(stats.totalStorageUsedBytes), "on device", Modifier.weight(1.2f))
                }
            },
        )
    }
    item(key = "tiles") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTile("Equalizer", "Presets & effects", Icons.Default.Equalizer, Modifier.weight(1f), "lib_eq_card", onOpenEqualizer)
            LibraryTile("New playlist", "Build a mix", Icons.Default.Add, Modifier.weight(1f), "add_playlist_button", onCreatePlaylist)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(GlassFill).clickable(enabled = !stats.isScanning, onClick = onRescan).padding(14.dp).testTag("banner_rescan_button")) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(SpotifyGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    if (stats.isScanning) CircularProgressIndicator(Modifier.size(18.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, null, tint = SpotifyGreen, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(if (stats.isScanning) "Scanning…" else "Rescan", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Find new files", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    if (recentlyPlayed.isNotEmpty()) {
        item(key = "recent_h") { LibrarySectionHeader("Jump back in", "Recently played") }
        item(key = "recent") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentlyPlayed.take(15), key = { "r_" + it.id }) { t ->
                    Column(Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).clickable { onPlay(t, recentlyPlayed) }.testTag("lib_recent_${t.id}").padding(bottom = 10.dp)) {
                        TrackArtwork(t.albumArtUri, t.albumId, 132.dp, 16.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(t.title, color = if (currentPlayingTrack?.id == t.id) SpotifyGreen else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    item(key = "pl_h") {
        LibrarySectionHeader("Playlists", if (playlists.isEmpty()) "Your mixes live here" else "${playlists.size} playlists", "+ New", onCreatePlaylist)
    }
    if (playlists.isEmpty()) item(key = "pl_empty") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GlassFill).clickable(onClick = onCreatePlaylist).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArtwork(null, "new", 56.dp, 14.dp, icon = Icons.Default.Add)
            Spacer(Modifier.width(14.dp))
            Column { Text("Create your first playlist", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Add songs from any list with the ⋮ menu", color = TextTertiary, fontSize = 12.sp) }
        }
    } else items(playlists.chunked(2), key = { row -> "pl_" + row.joinToString { it.id } }) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { pl ->
                val tracks = remember(pl.trackIds, trackMap) { pl.trackIds.mapNotNull { trackMap[it] } }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onOpenPlaylist(pl.id) }.testTag("playlist_item_${pl.id}").padding(bottom = 10.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) { PlaylistMosaic(tracks, pl.id, maxWidth) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(pl.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(songsLabel(tracks.size, tracks.sumOf { it.durationMs }), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (mostPlayed.isNotEmpty()) {
        item(key = "top_h") { LibrarySectionHeader("On repeat", "Your most played") }
        itemsIndexed(mostPlayed.take(5), key = { _, t -> "m_" + t.id }) { i, t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPlay(t, mostPlayed) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = if (i == 0) SpotifyGreen else TextTertiary, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
                TrackArtwork(t.albumArtUri, t.albumId, 48.dp, 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = if (currentPlayingTrack?.id == t.id) SpotifyGreen else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${t.playCount} plays", color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

/** Quick tiles shown above the Videos tab. */
@Composable
internal fun VideoLibraryHero(historyCount: Int, savedCount: Int, downloadCount: Int, onHistory: () -> Unit, onDownloads: () -> Unit, onTogether: () -> Unit, onSources: () -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LibraryHero(overline = "Your videos", title = "Watch list", subtitle = "Movies, series, anime, sports and everything on this phone", glyph = "映", testTag = "lib_video_hero",
            bottom = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat("$historyCount", "watched", Modifier.weight(1f))
                    HeroStat("$savedCount", "in My List", Modifier.weight(1f))
                    HeroStat("$downloadCount", "downloads", Modifier.weight(1f))
                }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTile("History", "Everything you watched", Icons.Default.History, Modifier.weight(1f), "lib_tile_history", onHistory)
            LibraryTile("Downloads", "Offline & permanent", Icons.Default.CloudDownload, Modifier.weight(1f), "lib_tile_downloads", onDownloads)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTile("Watch together", "Same Wi-Fi room", Icons.Default.Groups, Modifier.weight(1f), "lib_tile_together", onTogether)
            LibraryTile("Sources", "Add-ons & servers", Icons.Default.Extension, Modifier.weight(1f), "lib_tile_sources", onSources)
        }
    }
}

/** Reading tab: saved manga and continue reading. */
@Composable
fun ReadingLibraryContent(onBrowse: () -> Unit, onOpenTitle: (MangaItem) -> Unit, onOpenSources: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MangaLibrary.load(context) }
    val entries by MangaLibrary.entries.collectAsState()
    val reading = remember(entries) { entries.filter { it.lastChapterUrl != null }.sortedByDescending { it.updatedAt } }
    val saved = remember(entries) { entries.filter { it.saved }.sortedBy { it.title.lowercase() } }
    LazyColumn(modifier.fillMaxSize().testTag("reading_library"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("hero") {
            LibraryHero(overline = "Your shelf", title = "Manga & comics", subtitle = if (entries.isEmpty()) "Read from Mihon, Aniyomi and JavaScript sources" else "${saved.size} saved · ${reading.size} in progress",
                glyph = "本", testTag = "lib_reading_hero", onClick = onBrowse,
                trailing = { RoundAction(Icons.AutoMirrored.Filled.MenuBook, "Browse manga", filled = true, testTag = "lib_browse_manga", onClick = onBrowse) })
        }
        if (reading.isNotEmpty()) {
            item("cr_h") { LibrarySectionHeader("Continue reading") }
            item("cr") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(reading.take(20), key = { "cr_" + it.key }) { e -> ShelfCover(e, e.lastChapterName) { onOpenTitle(MangaItem(e.providerId, e.url, e.title, e.cover)) } }
                }
            }
        }
        item("sv_h") { LibrarySectionHeader("Saved", if (saved.isEmpty()) null else "${saved.size} titles", "Browse", onBrowse) }
        if (saved.isEmpty()) item("sv_empty") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GlassFill).padding(18.dp)) {
                Text("Nothing on your shelf yet", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Install a manga source, open a title and tap Save. Reading progress is kept on this phone.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Text("Add manga sources", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(SpotifyGreen).clickable(onClick = onOpenSources).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        } else items(saved.chunked(3), key = { r -> "sv_" + r.joinToString { it.key } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { e -> Box(Modifier.weight(1f)) { ShelfCover(e, e.lastChapterName, fill = true) { onOpenTitle(MangaItem(e.providerId, e.url, e.title, e.cover)) } } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShelfCover(e: MangaLibrary.Entry, subtitle: String?, fill: Boolean = false, onClick: () -> Unit) {
    Column((if (fill) Modifier.fillMaxWidth() else Modifier.width(108.dp)).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(14.dp)).background(GlassFill)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = TextTertiary, modifier = Modifier.align(Alignment.Center).size(28.dp))
            AsyncImage(e.cover, e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(e.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        subtitle?.let { Text(it, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Music | Videos | Reading switch at the top of Library. */
@Composable
fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(Triple("Music", Icons.Default.MusicNote, 0), Triple("Videos", Icons.Default.VideoLibrary, 1), Triple("Reading", Icons.AutoMirrored.Filled.MenuBook, 2))
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GlassFill).padding(4.dp).testTag("library_tabs")) {
        tabs.forEach { (label, icon, i) ->
            val sel = i == selected
            Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (sel) SpotifyGreen else Color.Transparent)
                .clickable(role = Role.Tab) { onSelect(i) }.testTag("library_tab_$i"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (sel) OnAccent else TextSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (sel) OnAccent else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
