/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.music

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.youtube.YouTubePlayback
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem

/**
 * Music → Mix: your device library and your YouTube Music listening in one place. Everything on
 * this page is real data — device songs, play counts, recently played, and the songs you played
 * on YouTube Music (YtHistory). "Shuffle the mix" queues both together.
 */
@Composable
fun MusicMixScreen(
    tracks: List<AudioTrack>,
    mostPlayed: List<AudioTrack>,
    recentlyPlayed: List<AudioTrack>,
    currentTrackId: String?,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayOnline: (YtItem, List<YtItem>) -> Unit,
    onOpenDevice: () -> Unit,
    onOpenOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { YtHistory.load(context) }
    val history by YtHistory.items.collectAsState()
    val onlineSongs = remember(history) { history.filter { it.music }.distinctBy { it.id } }
    val fresh = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(12) }
    val p = LocalMtPalette.current

    LazyColumn(
        modifier.fillMaxSize().testTag("music_mix"),
        contentPadding = PaddingValues(bottom = 170.dp),
    ) {
        item("hero") {
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(shape)
                    .background(Brush.linearGradient(listOf(p.accent.copy(alpha = if (p.isDark) 0.22f else 0.16f), p.surfaceContainerHigh)))
                    .padding(18.dp)
            ) {
                Text("Your mix", style = MaterialTheme.typography.headlineSmall, color = p.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${tracks.size} songs on this phone · ${onlineSongs.size} played online",
                    style = MaterialTheme.typography.bodyMedium, color = p.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val canMix = tracks.isNotEmpty() || onlineSongs.isNotEmpty()
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (canMix) p.accent else p.textPrimary.copy(alpha = 0.08f))
                            .clickable(enabled = canMix, role = Role.Button) {
                                val queue = (tracks.shuffled().take(60) + onlineSongs.take(40).map { YouTubePlayback.toTrack(it) }).shuffled()
                                queue.firstOrNull()?.let { onPlayTrack(it, queue) }
                            }
                            .testTag("music_mix_shuffle")
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Shuffle, null, tint = if (canMix) p.onAccent else p.textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle the mix", color = if (canMix) p.onAccent else p.textTertiary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        if (tracks.isEmpty() && onlineSongs.isEmpty()) item("empty") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to mix yet", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text(
                    "Play a song from your device or from YouTube Music and it shows up here.",
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixLink("Device music", Icons.Default.LibraryMusic, onOpenDevice)
                    MixLink("Online music", Icons.Default.Album, onOpenOnline)
                }
            }
        }
        if (mostPlayed.isNotEmpty()) item("most") { TrackShelf("Most played", "On this phone", mostPlayed.take(12), currentTrackId, onPlayTrack) }
        if (onlineSongs.isNotEmpty()) item("online") {
            MixHeader("From YouTube Music", "Songs you played online", onOpenOnline)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(onlineSongs.take(16), key = { "o_" + it.id }) { yt ->
                    Column(Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlayOnline(yt, onlineSongs) }.testTag("music_mix_online_${yt.id}").padding(bottom = 10.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
                            TrackArtwork(artUri = null, key = yt.id, size = 128.dp, cornerRadius = 14.dp, modifier = Modifier.fillMaxSize())
                            AsyncImage(model = yt.thumbnail, contentDescription = yt.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(yt.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(yt.channel, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (recentlyPlayed.isNotEmpty()) item("recent") { TrackShelf("Played recently", "Device and online", recentlyPlayed.take(12), currentTrackId, onPlayTrack) }
        if (fresh.isNotEmpty()) item("fresh") { TrackShelf("New on this phone", "Newest files first", fresh, currentTrackId, onPlayTrack) }
    }
}

@Composable
private fun MixLink(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.textPrimary.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MixHeader(title: String, subtitle: String, onMore: (() -> Unit)? = null) {
    val kana = when (title) {
        "Most played" -> "愛聴"; "Played recently" -> "最近"; "New on this phone" -> "新着"; "From YouTube Music" -> "配信"
        else -> null
    }
    dev.asfakul.yumeiro.ui.components.YuSection(
        kana = kana, label = subtitle, title = title,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp), top = 20.dp,
        action = if (onMore != null) "Open" else null, onAction = onMore,
    )
}

@Composable
private fun TrackShelf(title: String, subtitle: String, shelfTracks: List<AudioTrack>, currentId: String?, onPlay: (AudioTrack, List<AudioTrack>) -> Unit) {
    val p = LocalMtPalette.current
    val tracks = remember(shelfTracks) { shelfTracks.distinctBy { it.id } }
    MixHeader(title, subtitle)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks, key = { title + it.id }) { t ->
            Column(Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlay(t, tracks) }.padding(bottom = 10.dp)) {
                TrackArtwork(artUri = t.albumArtUri, key = t.id, size = 128.dp, cornerRadius = 14.dp, contentDescription = t.album)
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (t.id == currentId) p.accent else p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

