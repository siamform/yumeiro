/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import dev.asfakul.yumeiro.youtube.YtAccount

import dev.asfakul.yumeiro.ui.components.Suggestion.Kind as SuggestionKind
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.AccentSecondary
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Unbounded
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder
import dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtHistory
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.YtShelf
import dev.asfakul.yumeiro.youtube.formatYtDuration
import dev.asfakul.yumeiro.youtube.formatYtViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

private fun friendly(t: Throwable): String = when {
    YtAccount.accountCanHelp(t) -> YtAccount.blockedHint()
    t.javaClass.simpleName.contains("ReCaptcha") -> "YouTube is rate-limiting this network. Try again in a few minutes or switch networks."
    t is java.io.IOException -> "No connection to YouTube."
    else -> t.message?.take(140) ?: "YouTube didn't answer."
}

/** Videos → YouTube: login-free YouTube home (kiosks + curated shelves), search and history. */
@Composable
fun YouTubeVideoScreen(onPlay: (YtItem) -> Unit, modifier: Modifier = Modifier) {
    YtBrowse(
        music = false,
        shelves = listOf(YtShelf.ANIME, YtShelf.AMV, YtShelf.TRENDING_MUSIC, YtShelf.TRENDING_GAMING, YtShelf.TRENDING_MOVIES, YtShelf.LIVE),
        searchFilter = YouTube.Filter.VIDEOS,
        placeholder = "Search YouTube",
        onPlay = { item, _ -> onPlay(item) },
        modifier = modifier.testTag("youtube_video_home"),
    )
}

/** Music → YouTube Music: quick picks, mood shelves and song search, no account needed. */
@Composable
fun YouTubeMusicScreen(onPlay: (YtItem, List<YtItem>) -> Unit, modifier: Modifier = Modifier) {
    YtBrowse(
        music = true,
        shelves = listOf(YtShelf.TRENDING_MUSIC, YtShelf.JPOP, YtShelf.TOP_HITS, YtShelf.LOFI, YtShelf.WORKOUT),
        searchFilter = YouTube.Filter.SONGS,
        placeholder = "Songs, artists, anime OSTs…",
        onPlay = onPlay,
        modifier = modifier.testTag("youtube_music_home"),
    )
}

@Composable
private fun YtBrowse(
    music: Boolean,
    shelves: List<YtShelf>,
    searchFilter: YouTube.Filter,
    placeholder: String,
    onPlay: (YtItem, List<YtItem>) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val loads = remember { mutableStateMapOf<YtShelf, Load<List<YtItem>>>() }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<dev.asfakul.yumeiro.ui.components.Suggestion>>(emptyList()) }
    val recentScope = if (music) "ytmusic" else "youtube"
    var results by remember { mutableStateOf<Load<List<YtItem>>?>(null) }
    var chip by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { YtHistory.load(context) }
    val history by YtHistory.items.collectAsState()
    val mine = remember(history) { history.filter { it.music == music } }

    fun load(force: Boolean) {
        val gate = Semaphore(2)
        shelves.forEach { s ->
            if (!force && loads[s] is Load.Ready) return@forEach
            loads[s] = Load.Loading
            scope.launch {
                loads[s] = gate.withPermit {
                    runCatching { YouTube.shelf(context, s, force) }.fold({ if (it.isEmpty()) Load.Failed("Nothing here right now.") else Load.Ready(it) }, { Load.Failed(friendly(it)) })
                }
            }
        }
    }
    LaunchedEffect(Unit) { load(false) }
    LaunchedEffect(query) {
        if (query.isBlank() || query == submitted) { suggestions = emptyList(); return@LaunchedEffect }
        // Instant local typeahead first (recent searches + what you played), then YouTube's own suggestions.
        val local = dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(
            query,
            dev.asfakul.yumeiro.ui.components.RecentSearches.asSuggestions(context, recentScope) +
                mine.take(80).map { dev.asfakul.yumeiro.ui.components.Suggestion(it.title, SuggestionKind.YOUTUBE, if (music) "Listened" else "Watched") },
            limit = 3,
        )
        suggestions = local
        delay(200)
        val remote = runCatching { YouTube.suggestions(context, query) }.getOrDefault(emptyList())
            .map { dev.asfakul.yumeiro.ui.components.Suggestion(it, SuggestionKind.QUERY) }
        suggestions = (local + remote).distinctBy { dev.asfakul.yumeiro.ui.components.SearchSuggest.fold(it.text) }.take(8)
    }
    fun search(q: String) {
        val text = q.trim().takeIf { it.isNotEmpty() } ?: return
        query = text; submitted = text; suggestions = emptyList(); focus.clearFocus()
        dev.asfakul.yumeiro.ui.components.RecentSearches.add(context, recentScope, text)
        results = Load.Loading
        scope.launch {
            results = runCatching { YouTube.search(context, text, searchFilter).items }.fold({ Load.Ready(it) }, { Load.Failed(friendly(it)) })
        }
    }

    Column(modifier.fillMaxSize()) {
        YtSearchBar(query, placeholder, music, onChange = { query = it; if (it.isBlank()) { submitted = null; results = null } }, onSearch = { search(query) }, onClear = { query = ""; submitted = null; results = null })
        dev.asfakul.yumeiro.ui.components.SuggestionList(
            query = query, suggestions = suggestions,
            onPick = { search(it.text) }, onFill = { query = it.text },
            tag = "youtube_suggestions",
        )
        val res = results
        if (submitted != null && res != null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
                when (res) {
                    Load.Loading -> itemsIndexed(List(6) { it }, key = { i, _ -> "sk$i" }) { _, _ -> if (music) SongRowSkeleton() else SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(22.dp)) }
                    is Load.Failed -> item("err") { YtProblem(res.message) { search(submitted.orEmpty()) } }
                    is Load.Ready -> {
                        if (res.value.isEmpty()) item("none") { YtProblem("No results for “$submitted”.", null) }
                        itemsIndexed(res.value, key = { i, it -> "r${i}_${it.id}" }) { _, item ->
                            if (music) SongRow(item) { onPlay(item, res.value) } else YtVideoCard(item, wide = true) { onPlay(item, res.value) }
                        }
                    }
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().testTag("youtube_home_list"), contentPadding = PaddingValues(bottom = 160.dp)) {
            item("chips") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(shelves, key = { i, s -> "c${i}_${s.name}" }) { i, s -> YtChip(s.title, i == chip) { chip = i } }
                }
            }
            if (mine.isNotEmpty()) item("history") {
                YtShelfRow(if (music) "Listen again" else "Watch again", Icons.Default.History, mine.take(20), music) { onPlay(it, mine) }
            }
            // Featured shelf (selected chip): quick-pick grid for music, big cards for video.
            val featured = shelves.getOrNull(chip) ?: shelves.first()
            when (val l = loads[featured] ?: Load.Loading) {
                Load.Loading -> item("feat_load") { SkeletonBlock(null, 230.dp, Modifier.padding(16.dp), RoundedCornerShape(24.dp)) }
                is Load.Failed -> item("feat_err") { YtProblem(l.message) { load(true) } }
                is Load.Ready -> {
                    item("feat_label") { YtLabel(featured.title, if (music) "Quick picks" else "Featured") }
                    if (music) {
                        itemsIndexed(l.value.take(8), key = { i, it -> "f${i}_${it.id}" }) { _, item -> SongRow(item) { onPlay(item, l.value) } }
                    } else {
                        itemsIndexed(l.value.take(6), key = { i, it -> "f${i}_${it.id}" }) { _, item -> YtVideoCard(item, wide = true) { onPlay(item, l.value) } }
                    }
                }
            }
            shelves.filter { it != featured }.forEach { s ->
                item("shelf_${s.name}") {
                    when (val l = loads[s] ?: Load.Loading) {
                        Load.Loading -> Column { YtLabel(s.title, null); Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { SkeletonBlock(if (music) 140.dp else 220.dp, if (music) 140.dp else 124.dp, shape = RoundedCornerShape(18.dp)) } } }
                        is Load.Failed -> Unit
                        is Load.Ready -> YtShelfRow(s.title, if (music) Icons.Default.MusicNote else Icons.Default.SmartDisplay, l.value, music) { onPlay(it, l.value) }
                    }
                }
            }
            item("note") {
                Text(
                    "Streams come straight from YouTube without an account. Nothing is uploaded; history stays on this device.",
                    color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun YtSearchBar(query: String, placeholder: String, music: Boolean, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val border = rememberGlassBorder()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(GlassFill).border(1.dp, border, RoundedCornerShape(25.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (music) Icons.Default.MusicNote else Icons.Default.SmartDisplay, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = TextTertiary, fontSize = 14.sp)
            BasicTextField(
                query, onChange, singleLine = true, cursorBrush = SolidColor(SpotifyGreen),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().testTag("youtube_search"),
            )
        }
        if (query.isNotEmpty()) IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Clear", tint = TextSecondary) }
    }
}

@Composable
private fun YtChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val brush = rememberSignatureBrush()
    Box(
        Modifier.clip(CircleShape).then(if (selected) Modifier.background(brush) else Modifier.background(GlassFill)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(label, color = if (selected) OnAccent else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun YtLabel(title: String, overline: String?) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)) {
        if (overline != null) Text(overline.uppercase(), color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun YtShelfRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<YtItem>, music: Boolean, onClick: (YtItem) -> Unit) {
    Column {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { i, it -> "${i}_${it.id}" }) { _, item ->
                if (music) SquareCard(item, 144.dp) { onClick(item) } else Box(Modifier.width(240.dp)) { YtVideoCard(item, wide = false) { onClick(item) } }
            }
        }
    }
}

/** YouTube-style video card: 16:9 thumbnail with duration / LIVE badge, channel and views below. */
@Composable
private fun YtVideoCard(item: YtItem, wide: Boolean, onClick: () -> Unit) {
    val border = rememberGlassBorder()
    Column(Modifier.fillMaxWidth().then(if (wide) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier).clickable(onClick = onClick).testTag("youtube_card")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(if (wide) 22.dp else 16.dp)).border(1.dp, border, RoundedCornerShape(if (wide) 22.dp else 16.dp))) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
            val badge = when { item.live -> "LIVE"; item.durationSec > 0 -> formatYtDuration(item.durationSec); else -> null }
            if (badge != null) Surface(
                shape = RoundedCornerShape(8.dp), color = if (item.live) Color(0xFFE53950) else Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        Row(Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp)) {
            if (wide) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SpotifyGreen, AccentSecondary))), contentAlignment = Alignment.Center) {
                    if (item.channelAvatar != null) PosterImage(item.channelAvatar, emptyMap(), Modifier.fillMaxSize())
                    else Text(item.channel.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = TextPrimary, fontSize = if (wide) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.channel.takeIf { it.isNotBlank() }, item.views.takeIf { it >= 0 }?.let { formatYtViews(it) }, item.uploaded).joinToString(" · "),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquareCard(item: YtItem, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(18.dp))) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote)
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.channel.removeSuffix(" - Topic"), color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SongRow(item: YtItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp).testTag("youtube_song"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp))) { PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.MusicNote) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.channel.removeSuffix(" - Topic").takeIf { it.isNotBlank() }, item.durationSec.takeIf { it > 0 }?.let { formatYtDuration(it) }).joinToString(" · "),
                color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.PlayArrow, "Play", tint = SpotifyGreen)
    }
}

@Composable
private fun SongRowSkeleton() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(54.dp, 54.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column { SkeletonBlock(180.dp, 14.dp); Spacer(Modifier.height(6.dp)); SkeletonBlock(110.dp, 12.dp) }
    }
}

@Composable
private fun YtProblem(message: String, onRetry: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, null, tint = TextSecondary)
            Spacer(Modifier.width(12.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = SpotifyGreen) }
        }
    }
}
