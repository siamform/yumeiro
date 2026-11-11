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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.asfakul.yumeiro.youtube.music.toYt
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
 * Music → Mix: your device library and YouTube Music woven into one endless listening list.
 *
 * Online picks are real YouTube Music data: Quick picks from its home and songs by the artists you
 * play most on this phone. They are interleaved with your own songs (one device song, one online,
 * and so on), so "Play your mix" really mixes both. Offline, the device half still plays and the
 * page says so instead of showing empty shelves.
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
    val online by dev.asfakul.yumeiro.network.Connectivity.online.collectAsState()
    val p = LocalMtPalette.current

    // Artists this phone plays most: the seeds for "Because you play…".
    val seeds = remember(mostPlayed, tracks) {
        (mostPlayed + tracks).map { it.artist }.filter { it.isNotBlank() && !it.equals("<unknown>", true) && !it.equals("Unknown artist", true) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(2)
    }
    var quick by remember { mutableStateOf<List<YtItem>>(emptyList()) }
    var because by remember { mutableStateOf<List<Pair<String, List<YtItem>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(online, seeds) {
        if (!online) { loading = false; return@LaunchedEffect }
        loading = true
        val home = runCatching { dev.asfakul.yumeiro.youtube.music.YtMusicApi.home(context) }.getOrNull()
        quick = home?.shelves.orEmpty().flatMap { it.items }.filterIsInstance<dev.asfakul.yumeiro.youtube.music.YmItem.Song>()
            .filter { !it.video }.distinctBy { it.videoId }.take(40).map { it.toYt() }
        because = seeds.mapNotNull { artist ->
            val page = runCatching { dev.asfakul.yumeiro.youtube.music.YtMusicApi.search(context, artist, dev.asfakul.yumeiro.youtube.music.YtMusicApi.Filter.SONGS) }.getOrNull()
            val songs = page?.shelves.orEmpty().flatMap { it.items }.filterIsInstance<dev.asfakul.yumeiro.youtube.music.YmItem.Song>().take(15).map { it.toYt() }
            if (songs.isEmpty()) null else artist to songs
        }
        loading = false
    }
    val onlinePool = remember(quick, because, onlineSongs) {
        (quick + because.flatMap { it.second } + onlineSongs.take(20)).distinctBy { it.id }
    }
    // One device song, one online song, repeated: a real mix rather than two lists glued together.
    val woven: List<MixEntry> = remember(tracks, onlinePool) {
        val dev = tracks.shuffled(java.util.Random(java.time.LocalDate.now().toEpochDay())).take(40)
        val net = onlinePool.shuffled(java.util.Random(java.time.LocalDate.now().toEpochDay() + 7)).take(40)
        buildList {
            val n = maxOf(dev.size, net.size)
            for (i in 0 until n) {
                dev.getOrNull(i)?.let { add(MixEntry.Device(it)) }
                net.getOrNull(i)?.let { add(MixEntry.Online(it)) }
            }
        }
    }
    val queue = remember(woven) { woven.map { when (it) { is MixEntry.Device -> it.track; is MixEntry.Online -> YouTubePlayback.toTrack(it.item) } } }
    fun playAt(i: Int) { queue.getOrNull(i)?.let { onPlayTrack(it, queue) } }

    LazyColumn(
        modifier.fillMaxSize().testTag("music_mix"),
        contentPadding = PaddingValues(bottom = 170.dp),
    ) {
        item("hero") {
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(shape)
                    .background(Brush.linearGradient(listOf(p.accent.copy(alpha = if (p.isDark) 0.24f else 0.16f), p.surfaceContainerHigh)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("混 · YOUR MIX", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Device + YouTube Music", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !online -> "Offline: your ${tracks.size} songs on this phone"
                            loading && onlinePool.isEmpty() -> "Finding online picks…"
                            else -> "${woven.count { it is MixEntry.Device }} of yours, ${woven.count { it is MixEntry.Online }} from YouTube Music"
                        },
                        style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                    )
                }
                val canMix = queue.isNotEmpty()
                Box(
                    Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (canMix) p.accent else p.textPrimary.copy(alpha = 0.08f))
                        .clickable(enabled = canMix, role = Role.Button, onClickLabel = "Play your mix") { playAt(0) }
                        .testTag("music_mix_play"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.PlayArrow, "Play your mix", tint = if (canMix) p.onAccent else p.textTertiary, modifier = Modifier.size(28.dp)) }
            }
        }
        if (tracks.isEmpty() && onlinePool.isEmpty() && !loading) item("empty") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to mix yet", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text(
                    if (online) "Add songs to this phone or play something on YouTube Music." else "Connect to the internet for online picks, or add songs to this phone.",
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixLink("Device music", Icons.Default.LibraryMusic, onOpenDevice)
                    MixLink("Online music", Icons.Default.Album, onOpenOnline)
                }
            }
        }
        if (woven.isNotEmpty()) {
            item("up_next_head") { MixHeader("Up next in your mix", "Yours and online, woven together") }
            itemsIndexed(woven.take(12), key = { i, e -> "w$i" + e.key }) { i, e -> MixRow(e, e.key == currentTrackId) { playAt(i) } }
        }
        if (quick.isNotEmpty()) item("quick") { OnlineShelf("Quick picks", "From YouTube Music", quick.take(16), onOpenOnline, onPlayOnline) }
        because.forEach { (artist, songs) -> item("because_$artist") { OnlineShelf("Because you play $artist", "On YouTube Music", songs, null, onPlayOnline) } }
        if (loading && online && quick.isEmpty()) item("loading") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { dev.asfakul.yumeiro.ui.components.SkeletonBlock(128.dp, 128.dp, shape = RoundedCornerShape(14.dp)) }
            }
        }
        if (mostPlayed.isNotEmpty()) item("most") { TrackShelf("Most played", "On this phone", mostPlayed.take(12), currentTrackId, onPlayTrack) }
        if (onlineSongs.isNotEmpty()) item("online") { OnlineShelf("Played online", "Your YouTube Music history", onlineSongs.take(16), onOpenOnline, onPlayOnline) }
        if (recentlyPlayed.isNotEmpty()) item("recent") { TrackShelf("Played recently", "Device and online", recentlyPlayed.take(12), currentTrackId, onPlayTrack) }
        if (fresh.isNotEmpty()) item("fresh") { TrackShelf("New on this phone", "Newest files first", fresh, currentTrackId, onPlayTrack) }
    }
}

private sealed interface MixEntry {
    val key: String
    data class Device(val track: AudioTrack) : MixEntry { override val key get() = track.id }
    data class Online(val item: YtItem) : MixEntry { override val key get() = YouTubePlayback.toTrack(item).id }
}

@Composable
private fun MixRow(e: MixEntry, current: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp).testTag("music_mix_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
            when (e) {
                is MixEntry.Device -> TrackArtwork(artUri = e.track.albumArtUri, key = e.track.id, size = 48.dp, cornerRadius = 10.dp)
                is MixEntry.Online -> AsyncImage(model = e.item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when (e) { is MixEntry.Device -> e.track.title; is MixEntry.Online -> e.item.title }
            val artist = when (e) { is MixEntry.Device -> e.track.artist; is MixEntry.Online -> e.item.channel.removeSuffix(" - Topic") }
            Text(title, color = if (current) p.accent else p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = p.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val online = e is MixEntry.Online
        Text(
            if (online) "Online" else "Device",
            color = if (online) p.accent else p.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background((if (online) p.accent else p.textPrimary).copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun OnlineShelf(title: String, subtitle: String, songs: List<YtItem>, onMore: (() -> Unit)?, onPlay: (YtItem, List<YtItem>) -> Unit) {
    val p = LocalMtPalette.current
    MixHeader(title, subtitle, onMore)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { title + it.id }) { yt ->
            Column(Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlay(yt, songs) }.testTag("music_mix_online_${yt.id}").padding(bottom = 10.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
                    TrackArtwork(artUri = null, key = yt.id, size = 128.dp, cornerRadius = 14.dp, modifier = Modifier.fillMaxSize())
                    AsyncImage(model = yt.thumbnail, contentDescription = yt.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(6.dp))
                Text(yt.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(yt.channel.removeSuffix(" - Topic"), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
        "Most played" -> "愛聴"; "Played recently" -> "最近"; "New on this phone" -> "新着"; "Played online" -> "配信"; "Quick picks" -> "選曲"; "Up next in your mix" -> "次"
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

