/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.ui.screens.watch.WatchLightsOverlay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.ui.screens.watch.WatchScreenStatus
import androidx.compose.foundation.layout.heightIn
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen

private enum class Panel { EPISODES, SOURCES, SUBTITLES }

private val PanelColor = Color(0xF2121418)
private val ChipColor = Color.White.copy(alpha = 0.14f)

/**
 * Text colour of the Episodes / Sources / Subtitles lists. White over video; the theme's own
 * text colour when the same lists open as a sheet from the (themed) watch page.
 */
private val LocalPanelInk = androidx.compose.runtime.staticCompositionLocalOf { Color.White }

/** Online controls drawn over the video player: Episodes, Sources, Sub/Dub, Next. */
@Composable
fun BoxScope.OnlinePlayerOverlay(controlsVisible: Boolean, player: dev.asfakul.yumeiro.ui.screens.video.PlayerHandle, onClosePlayer: () -> Unit) {
    val context = LocalContext.current
    val s by OnlineSession.state.collectAsState()
    val state = s ?: return
    val positionMs = player.positionMs
    var panel by remember { mutableStateOf<Panel?>(null) }

    // The session is finding new links while this player is still mounted (next episode, Sub/Dub
    // switch, all links refused → fresh links or the site's own page). The old source is stopped
    // and the status is shown inside the player, so the user never stares at a dead black surface.
    if (state.phase != OnlineSession.Phase.PLAYING) {
        LaunchedEffect(state.phase, state.index, state.version) { player.pause() }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.matchParentSize().background(Color.Black)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .testTag("online_player_resolving"),
        ) {
            // The floating mini player is too small for the status card: a spinner is enough there.
            if (maxWidth >= 260.dp) CompactResolve(state, onClose = onClosePlayer)
            else CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(28.dp))
        }
        return
    }
    BackHandler(enabled = panel != null) { panel = null }

    // Anime: AniSkip intro / outro button (or automatic skip).
    LaunchedEffect(state.videoId, state.phase) {
        if (state.phase == OnlineSession.Phase.PLAYING) dev.asfakul.yumeiro.online.anime.AnimeSync.load(context, state.detail, state.episode, state.index, player.durationMs())
    }
    SkipButton(player)

    // Docked on the watch page: the page below the player already has these actions.
    val docked = dev.asfakul.yumeiro.ui.screens.video.LocalPlayerDocked.current
    AnimatedVisibility(visible = controlsVisible && panel == null && !docked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
        Row(
            Modifier.statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp).horizontalScroll(rememberScrollState()).testTag("online_player_bar"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.episodes.size > 1) PlayerChip(Icons.AutoMirrored.Filled.List, state.episode?.let { e -> listOfNotNull(e.season?.let { "S$it" }, e.episode?.let { "E$it" }).joinToString("").ifBlank { "Episodes" } } ?: "Episodes", "online_player_episodes") { panel = Panel.EPISODES }
            if (state.links.isNotEmpty()) PlayerChip(Icons.Default.HighQuality, "Source ${state.linkIndex + 1}/${state.links.size}", "online_player_sources") { panel = Panel.SOURCES }
            PlayerChip(Icons.Default.ClosedCaption, "Subtitles", "online_player_subtitles") { panel = Panel.SUBTITLES }
            val castOk = remember { dev.asfakul.yumeiro.cast.CastSupport.available(context) }
            if (castOk && state.link != null) PlayerChip(Icons.Default.Cast, "Cast", "online_player_cast") { castCurrent(context, state, player) }
            if (state.detail.versions.size > 1) {
                val other = state.detail.versions.firstOrNull { it != state.version }
                if (other != null) PlayerChip(Icons.Default.Translate, "${state.version.ifBlank { "Default" }} → ${other.ifBlank { "Default" }}", "online_player_version") { OnlineSession.switchVersion(context, other) }
            }
            if (state.hasNext) PlayerChip(Icons.Default.SkipNext, "Next", "online_player_next") { OnlineSession.next(context) }
            if (state.link != null && state.link?.isDash != true && state.detail.kind != dev.asfakul.yumeiro.online.OnlineKind.LIVE) PlayerChip(Icons.Default.Download, "Download", "online_player_download") {
                askDownloadCurrent(context, state)
            }
        }
    }

    // Switching episode / version inside the player.
    if (state.phase != OnlineSession.Phase.PLAYING) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
            if (docked) CompactResolve(state, onClose = onClosePlayer) else ResolveCard(state, onClose = onClosePlayer, inPlayer = true)
        }
    }

    AnimatedVisibility(
        visible = panel != null, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        Surface(color = PanelColor, shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp), modifier = Modifier.fillMaxHeight().widthIn(max = 380.dp).fillMaxWidth(0.86f).testTag("online_player_panel")) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Panel.EPISODES to "Episodes", Panel.SOURCES to "Sources", Panel.SUBTITLES to "Subtitles").forEach { (p, label) ->
                        if (p == Panel.EPISODES && state.episodes.size <= 1) return@forEach
                        val sel = panel == p
                        Text(label, color = if (sel) Color.White else Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { panel = p }.padding(horizontal = 8.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { panel = null }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
                when (panel) {
                    Panel.EPISODES -> EpisodesPanel(state) { i -> panel = null; OnlineSession.playEpisode(context, i) }
                    Panel.SOURCES -> SourcesPanel(state, positionMs) { i -> panel = null; OnlineSession.switchSource(context, i, positionMs()) }
                    Panel.SUBTITLES -> SubtitlesPanel(state) { sub -> panel = null; OnlineSession.addSubtitle(context, sub, positionMs()) }
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = ChipColor, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).testTag(tag)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodesPanel(state: OnlineSession.State, onPick: (Int) -> Unit) {
    val ink = LocalPanelInk.current
    val errInk = if (ink.luminance() > 0.5f) ErrorColor else Color(0xFFB3261E)
    val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct() }
    var season by remember(state.index) { mutableStateOf(state.episode?.season) }
    val historyVersion by OnlineHistory.version.collectAsState()
    if (seasons.size > 1) {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            seasons.forEach { n -> OnlineChip(state.detail.seasonNames[n ?: -1] ?: n?.let { "Season $it" } ?: "Other", n == season, { season = n }) }
        }
    }
    val shown = state.episodes.withIndex().filter { seasons.size <= 1 || it.value.season == season }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = shown.indexOfFirst { it.index == state.index }.coerceAtLeast(0))
    LazyColumn(state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(shown, key = { _, e -> "pe_" + e.index }) { _, (i, e) ->
            val current = i == state.index
            val p = remember(historyVersion, e.data) { OnlineHistory.progressOf(state.api, e.data) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (current) SpotifyGreen.copy(alpha = 0.22f) else ink.copy(alpha = 0.06f))
                    .clickable { onPick(i) }.padding(10.dp).testTag("online_player_episode"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    if (current) Icon(Icons.Default.PlayArrow, null, tint = SpotifyGreen)
                    else Text("${e.episode ?: i + 1}", color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p != null && p.fraction > 0.01f) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { p.fraction }, color = SpotifyGreen, trackColor = ink.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth().height(3.dp))
                    }
                }
                if (p?.finished == true) Icon(Icons.Default.CheckCircle, "Watched", tint = SpotifyGreen.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SourcesPanel(state: OnlineSession.State, positionMs: () -> Long = { 0L }, onPick: (Int) -> Unit) {
    val ink = LocalPanelInk.current
    val errInk = if (ink.luminance() > 0.5f) ErrorColor else Color(0xFFB3261E)
    if (!state.linksDone) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Still finding sources…", color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
    // Round 9: same per-language Sub / Dub groups and audio chips as the watch page.
    val context = LocalContext.current
    val split = dev.asfakul.yumeiro.ui.screens.watch.AudioSplit
    val audioPref by dev.asfakul.yumeiro.online.AudioLanguages.preferred.collectAsState()
    val audioOptions = remember(state.links, state.version) { audioOptionsFor(state.links, state.version) }
    val allGroups = remember(state.links, state.version, audioPref) {
        split.groups(state.links.map { it.name + " " + it.source }, state.version, audioPref).let { g -> if (g.size == 1) listOf(g[0].copy(kind = null)) else g }
    }
    val activeAudio = audioPref?.takeIf { sel -> audioOptions.any { it.key == sel } }
    val groups = allGroups.filter { activeAudio == null || allGroups.size == 1 || it.key == activeAudio }
    val folded = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(it) } })) { androidx.compose.runtime.mutableStateListOf<String>() }
    // Round 12: only the group that's playing starts open; long groups show a page at a time.
    val defaultOpen = (groups.firstOrNull { state.linkIndex in it.indices } ?: groups.firstOrNull())?.key?.ifBlank { "all" }
    val pages = remember(state.links.size, state.version) { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (audioOptions.size > 1) item(key = "audio_filter") {
            dev.asfakul.yumeiro.ui.screens.watch.AudioFilterRow(audioOptions, audioPref, { k -> pickAudio(context, state, k, positionMs()) }, onDark = ink.luminance() > 0.5f)
        }
        groups.forEach { g ->
        val kind = g.kind
        val idx = g.indices
        val gk = g.key.ifBlank { "all" }
        val isFolded = kind != null && groups.size > 1 && ((gk == defaultOpen) == (gk in folded))
        if (kind != null) item(key = "hdr_$gk") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = if (isFolded) "Show ${g.label} sources" else "Hide ${g.label} sources") { if (gk in folded) folded.remove(gk) else folded.add(gk) }
                    .padding(horizontal = 8.dp, vertical = 6.dp).testTag("online_player_source_group"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (kind == dev.asfakul.yumeiro.ui.screens.watch.AudioSplit.Kind.DUB) Icons.Default.Mic else Icons.Default.ClosedCaption, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("${g.label} · ${idx.size}", color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (isFolded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            }
        }
        val shown = dev.asfakul.yumeiro.ui.screens.watch.visibleServerCount(idx.size, idx.indexOf(state.linkIndex), pages[gk] ?: 1)
        if (!isFolded) items(idx.take(shown), key = { i -> state.links[i].url + "#" + i }) { i ->
            val l = state.links[i]
            val current = i == state.linkIndex
            val failed = l.url in state.failed
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (current) SpotifyGreen.copy(alpha = 0.22f) else ink.copy(alpha = 0.06f))
                    .clickable { onPick(i) }.padding(12.dp).testTag("online_player_source"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(l.name.ifBlank { l.source }, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val facts = remember(l) { dev.asfakul.yumeiro.online.SourceFacts.of(l, state.version) }
                    Text(listOf(facts.line, l.source.takeIf { it != l.name }.orEmpty(), if (failed) "didn't work" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        color = when { failed -> errInk; facts.low -> dev.asfakul.yumeiro.ui.theme.WarnTone; else -> ink.copy(alpha = 0.55f) }, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("online_player_source_detail"))
                }
                if (current) Icon(Icons.Default.CheckCircle, "Playing", tint = SpotifyGreen, modifier = Modifier.size(18.dp))
            }
        }
        val more = idx.size - shown
        if (!isFolded && more > 0) item(key = "more_$gk") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, ink.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .clickable { pages[gk] = (pages[gk] ?: 1) + 1 }.padding(10.dp).testTag("online_player_source_more"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ExpandMore, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Show ${minOf(more, dev.asfakul.yumeiro.ui.screens.watch.SERVER_PAGE)} more" + if (more > dev.asfakul.yumeiro.ui.screens.watch.SERVER_PAGE) " of $more" else "", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        }
        if (state.subtitles.isNotEmpty()) item { Text("${state.subtitles.size} subtitle track${if (state.subtitles.size == 1) "" else "s"} available in Captions", color = ink.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(6.dp)) }
    }
}

/** "Finding the best source…" card, also used for the error state. */
@Composable
internal fun ResolveCard(state: OnlineSession.State, onClose: () -> Unit, inPlayer: Boolean, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(22.dp), color = PanelColor, modifier = Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().testTag("online_resolving")) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(state.api, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            if (state.phase == OnlineSession.Phase.FAILED) {
                Icon(Icons.Default.ErrorOutline, null, tint = WarnColor, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(state.message ?: "Couldn't play this.", if (state.skipped > 0) "${state.skipped} protected or torrent source${if (state.skipped == 1) " was" else "s were"} skipped." else null).joinToString("\n"),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.testTag("online_resolve_error"),
                )
                Spacer(Modifier.height(12.dp))
                dev.asfakul.yumeiro.ui.components.ErrorSuggestions(
                    remember(state.message) { dev.asfakul.yumeiro.ui.components.ErrorHelp.forExtension(state.message ?: "") }
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose) { Text(if (inPlayer) "Back" else "Close", color = Color.White) }
                    Button(onClick = { onRetry?.invoke() ?: OnlineSession.retry(context) }, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Try again") }
                }
            } else {
                CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.links.isEmpty()) "Finding the best source…" else "Found ${state.links.size} source${if (state.links.size == 1) "" else "s"} — starting…",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                )
                if (!inPlayer) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onClose, modifier = Modifier.testTag("online_resolve_cancel")) { Text("Cancel", color = Color.White) }
                }
            }
        }
    }
}

/**
 * Full-screen "getting it ready" layer shown between tapping a title and the player opening
 * (and when nothing could be played).
 */
@Composable
fun OnlineResolveLayer(playerOpen: Boolean, onOpenTitle: (api: String, url: String) -> Unit, onOpenItem: (dev.asfakul.yumeiro.online.OnlineItem) -> Unit = {}) {
    val s by OnlineSession.state.collectAsState()
    val opening by OnlineSession.opening.collectAsState()
    opening?.let { item ->
        val kind = remember(item.type) { dev.asfakul.yumeiro.ui.screens.watch.WatchKind.of(item.type) }
        // Details still loading: the watch page appears right away with skeletons.
        dev.asfakul.yumeiro.ui.screens.watch.WatchScaffold(
            backdrop = item.posterUrl, backdropHeaders = item.posterHeaders,
            onBack = { OnlineSession.cancelOpening() }, tag = "online_opening",
            screen = {
                when (kind) {
                    dev.asfakul.yumeiro.ui.screens.watch.WatchKind.MOVIE -> WatchScreenStatus("Opening the film…", onCancel = { OnlineSession.cancelOpening() }, overline = "映画 · MOVIE")
                    dev.asfakul.yumeiro.ui.screens.watch.WatchKind.SERIES -> WatchScreenStatus("Loading episodes…", onCancel = { OnlineSession.cancelOpening() }, overline = "連続 · SERIES")
                    else -> WatchScreenStatus("Opening…", onCancel = { OnlineSession.cancelOpening() })
                }
            },
        ) {
            dev.asfakul.yumeiro.ui.screens.watch.WatchTitle(item.name, overline = item.api, meta = listOfNotNull(item.type, item.quality).joinToString(" · "))
            dev.asfakul.yumeiro.ui.screens.watch.WatchDetailsSkeleton(kind)
        }
        return
    }
    val state = s ?: return
    // One owner at a time: this layer stays up until the player has actually mounted, so the
    // hand-over never leaves a frame with neither of them (which showed the page underneath).
    val handingOver by dev.asfakul.yumeiro.stream.IncomingMedia.pending.collectAsState()
    if (playerOpen) return
    if (state.phase == OnlineSession.Phase.PLAYING && handingOver == null) return
    val close = {
        val d = state.detail
        OnlineSession.end()
        // When nothing played, offer the title page (episodes, other versions).
        if (state.phase == OnlineSession.Phase.FAILED && d.episodes.isEmpty().not()) onOpenTitle(d.api, d.url)
    }
    dev.asfakul.yumeiro.ui.screens.watch.WatchScaffold(
        backdrop = state.detail.backgroundUrl ?: state.detail.posterUrl, backdropHeaders = state.detail.posterHeaders,
        onBack = { OnlineSession.end() }, tag = "online_watch_loading",
        screen = { CompactResolve(state, onClose = close) },
    ) {
        OnlineWatchPanel(player = null, onOpenItem = onOpenItem)
    }
}

/** Small "finding a source" / error status that fits inside the 16:9 player area. */
@Composable
private fun BoxScope.CompactResolve(state: OnlineSession.State, onClose: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp).testTag("online_resolving"), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.phase == OnlineSession.Phase.FAILED) {
            Icon(Icons.Default.ErrorOutline, null, tint = WarnColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(state.message ?: "Couldn't play this.", if (state.skipped > 0) "${state.skipped} protected source${if (state.skipped == 1) "" else "s"} skipped." else null).joinToString(" "),
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("online_resolve_error"),
            )
            val tip = remember(state.message) { dev.asfakul.yumeiro.ui.components.ErrorHelp.forExtension(state.message ?: "").firstOrNull() }
            if (tip != null) Text("Tip: $tip", color = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose) { Text("Close", color = Color.White) }
                Button(onClick = { OnlineSession.retry(context) }, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Try again") }
            }
        } else {
            CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            // Film vs episode: the status says what is being opened.
            Text(
                if (state.episodes.size > 1) "連続 · EPISODE ${state.index + 1} OF ${state.episodes.size}" else "映画 · MOVIE",
                color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                modifier = Modifier.testTag("online_resolving_kind"),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (state.links.isEmpty()) "Finding the best source…" else "Found ${state.links.size} source${if (state.links.size == 1) "" else "s"} — starting…",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(state.title, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 4.dp).testTag("online_resolve_cancel"))
        }
    }
}

private enum class Sheet { SOURCES, SUBTITLES }

/**
 * Yumeiro watch details under the player: title, meta, Sub/Dub, sources & subtitles, synopsis,
 * genres, season tabs + episode list (current one highlighted), and "More like this".
 * A single movie / video shows its details and recommendations only.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnlineWatchPanel(player: dev.asfakul.yumeiro.ui.screens.video.PlayerHandle?, onOpenItem: (dev.asfakul.yumeiro.online.OnlineItem) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s by OnlineSession.state.collectAsState()
    val state = s ?: return
    val d = state.detail
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val seasons = remember(state.episodes) { state.episodes.map { it.season }.distinct() }
    var season by remember(state.version, state.index) { mutableStateOf(state.episode?.season) }
    val shown = remember(state.episodes, season, seasons) { state.episodes.withIndex().filter { seasons.size <= 1 || it.value.season == season } }
    val historyVersion by OnlineHistory.version.collectAsState()
    val hasThumbs = remember(state.episodes) { state.episodes.count { it.posterUrl != null } > state.episodes.size / 2 }
    val listState = rememberLazyListState()

    val autoSkip by dev.asfakul.yumeiro.online.OnlineSettings.autoSkip.collectAsState()
    // Long series get the numbered episode board; short ones (or ones with stills) keep the list.
    val useGrid = state.episodes.size > 24 && !hasThumbs
    Box(modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("online_watch_panel"), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "controls") {
            dev.asfakul.yumeiro.ui.screens.watch.WatchControlBar(
                onPrevious = if (state.hasPrevious) ({ OnlineSession.previous(context) }) else null,
                onNext = if (state.hasNext) ({ OnlineSession.next(context) }) else null,
                autoSkip = autoSkip,
                onAutoSkip = { dev.asfakul.yumeiro.online.OnlineSettings.setAutoSkip(context, it) },
            )
        }
        item(key = "title") {
            val ep = state.episode
            dev.asfakul.yumeiro.ui.screens.watch.WatchTitle(
                title = d.name,
                overline = listOfNotNull(d.api, ep?.let { e -> listOfNotNull(e.season?.let { "S$it" }, e.episode?.let { "E$it" }).joinToString(" ").ifBlank { null } }).joinToString(" · "),
                meta = ep?.name?.takeIf { it.isNotBlank() && !it.equals("Episode ${ep.episode}", true) },
            )
        }
        item(key = "meta") {
            val chips = listOfNotNull(
                d.type.takeIf { it.isNotBlank() }, d.year?.toString(), d.score?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) },
                d.contentRating, d.durationMin?.let { "${it}m" }, if (state.episodes.size > 1) "${state.episodes.size} episodes" else null,
            )
            if (chips.isNotEmpty()) FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { c ->
                    Surface(shape = RoundedCornerShape(8.dp), color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.08f)) {
                        Text(c, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        item(key = "facts") {
            val al = dev.asfakul.yumeiro.online.AudioLanguages
            val srcAudio = state.link?.let { l -> al.filterKey(al.classify(l.name + " " + l.source, state.version)).takeIf { it.isNotEmpty() }?.let { al.keyLabel(it) } }
            dev.asfakul.yumeiro.ui.screens.watch.PlaybackFactsRow(sourceAudio = srcAudio)
        }
        item(key = "actions") {
            dev.asfakul.yumeiro.ui.screens.watch.WatchActionsRow {
                if (state.hasNext) dev.asfakul.yumeiro.ui.screens.watch.WatchAction(Icons.Default.SkipNext, "Next episode", "online_watch_next", highlighted = true) { OnlineSession.next(context) }
                dev.asfakul.yumeiro.ui.screens.watch.WatchAction(Icons.Default.HighQuality, if (state.links.isNotEmpty()) "Source ${state.linkIndex + 1}/${state.links.size}" else "Sources", "online_watch_sources") { sheet = Sheet.SOURCES }
                dev.asfakul.yumeiro.ui.screens.watch.WatchAction(Icons.Default.ClosedCaption, "Subtitles", "online_watch_subtitles") { sheet = Sheet.SUBTITLES }
                val castOk = remember { dev.asfakul.yumeiro.cast.CastSupport.available(context) }
                if (castOk && state.link != null && player != null) dev.asfakul.yumeiro.ui.screens.watch.WatchAction(Icons.Default.Cast, "Cast", "online_watch_cast") { castCurrent(context, state, player) }
                if (state.link != null && state.link?.isDash != true && d.kind != dev.asfakul.yumeiro.online.OnlineKind.LIVE) {
                    dev.asfakul.yumeiro.ui.screens.watch.WatchAction(Icons.Default.Download, "Download", "online_watch_download") {
                        askDownloadCurrent(context, state)
                    }
                }
            }
        }
        item(key = "servers") {
            val versions = d.versions.filter { it.isNotBlank() }.ifEmpty { listOf(state.version.ifBlank { "Sources" }) }
            fun iconFor(v: String) = when {
                v.contains("dub", true) -> Icons.Default.Mic
                v.contains("sub", true) -> Icons.Default.ClosedCaption
                else -> Icons.Default.HighQuality
            }
            val pos = { player?.positionMs?.invoke() ?: 0L }
            // Round 8: a version called "Sub" often carries dubbed links too. The current version's
            // links are split by what each link says about itself into Sub and Dub groups (only
            // when both kinds are really there); every group folds (tap the header) so long lists
            // of sources stay tidy. Indices still point at state.links, so switching is unchanged.
            val split = dev.asfakul.yumeiro.ui.screens.watch.AudioSplit
            val audioPref by dev.asfakul.yumeiro.online.AudioLanguages.preferred.collectAsState()
            val audioOptions = remember(state.links, state.version) { audioOptionsFor(state.links, state.version) }
            val rows = versions.flatMap { v ->
                val isCurrent = v == state.version || versions.size == 1
                if (isCurrent) {
                    if (state.links.isEmpty()) return@flatMap listOf(dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow(
                        split.label(v, split.explicit(v)), iconFor(v),
                        listOf(dev.asfakul.yumeiro.ui.screens.watch.WatchServer(if (state.linksDone) "No sources" else "Finding sources…", false, "online_server_wait") {}),
                        startExpanded = true,
                    ))
                    // Round 9: grouped per audio language ("Sub · original audio", "Dub · English",
                    // "Dub · Hindi"…), so dubs in different languages no longer pile into one row.
                    val groups = split.groups(state.links.map { l -> l.name + " " + l.source }, v, audioPref, onlyVersion = versions.size == 1)
                    groups.map { g ->
                        dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow(
                            g.label,
                            when (g.kind) { dev.asfakul.yumeiro.ui.screens.watch.AudioSplit.Kind.DUB -> Icons.Default.Mic; dev.asfakul.yumeiro.ui.screens.watch.AudioSplit.Kind.SUB -> Icons.Default.ClosedCaption; else -> iconFor(v) },
                            g.indices.map { i ->
                                val l = state.links[i]
                                val name = l.name.ifBlank { l.source }.ifBlank { "Source ${i + 1}" }.let { if (it.length > 40) it.take(39) + "…" else it }
                                // Round 12: quality, release, format, size and audio under every source.
                                val facts = dev.asfakul.yumeiro.online.SourceFacts.of(l, v)
                                dev.asfakul.yumeiro.ui.screens.watch.WatchServer(name, i == state.linkIndex, "online_server", detail = facts.line, low = facts.low) { OnlineSession.switchSource(context, i, pos()) }
                            },
                            filterKey = if (groups.size > 1) g.key else null,
                        )
                    }
                } else {
                    val has = remember(state.episode, v) { OnlineSession.hasEpisodeIn(v) }
                    listOf(dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow(v, iconFor(v),
                        listOf(dev.asfakul.yumeiro.ui.screens.watch.WatchServer(if (has) "Switch to $v" else "Not for this episode", false, "online_watch_version") { OnlineSession.switchVersion(context, v) })))
                }
            }
            val ep = state.episode
            dev.asfakul.yumeiro.ui.screens.watch.WatchServerBox(
                watching = if (ep != null) "Episode ${ep.episode ?: state.index + 1}" + (ep.name?.takeIf { it.isNotBlank() && !it.equals("Episode ${ep.episode}", true) }?.let { " · $it" } ?: "") else d.name,
                hint = "Stuck on black? Tap another source. Yumeiro already retries refused ones with fresh links and headers.",
                rows = rows,
                audioOptions = audioOptions, audioSelected = audioPref,
                onAudioSelect = { k -> pickAudio(context, state, k, pos()) },
            )
        }
        item(key = "plot") {
            dev.asfakul.yumeiro.ui.screens.watch.WatchDescription(d.plot.orEmpty(), header = d.tags.takeIf { it.isNotEmpty() }?.take(8)?.joinToString(" · "))
        }

        if (state.episodes.size > 1) {
            item(key = "ep_header") {
                val pos = state.index + 1
                dev.asfakul.yumeiro.ui.screens.watch.WatchSectionLabel("Episodes", "Watching $pos of ${state.episodes.size}")
            }
            if (seasons.size > 1) {
                item(key = "seasons") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        seasons.forEach { n -> OnlineChip(d.seasonNames[n ?: -1] ?: n?.let { "Season $it" } ?: "Other", n == season, { season = n }, Modifier.testTag("online_watch_season")) }
                    }
                }
            }
            if (useGrid) item(key = "ep_grid") {
                dev.asfakul.yumeiro.ui.screens.watch.WatchNumberGrid(
                    title = "List of episodes",
                    count = shown.size,
                    current = shown.indexOfFirst { it.index == state.index },
                    numberOf = { k -> shown[k].value.episode ?: (shown[k].index + 1) },
                    onPick = { k -> OnlineSession.playEpisode(context, shown[k].index) },
                    watched = { k -> OnlineHistory.progressOf(state.api, shown[k].value.data)?.finished == true },
                )
            }
            if (!useGrid) items(shown.size, key = { "we_" + shown[it].index }) { k ->
                val (i, e) = shown[k]
                val current = i == state.index
                val p = remember(historyVersion, e.data) { OnlineHistory.progressOf(state.api, e.data) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (current) SpotifyGreen.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(enabled = !current) { OnlineSession.playEpisode(context, i) }
                        .padding(horizontal = 4.dp, vertical = 6.dp).testTag("online_watch_episode"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasThumbs) {
                        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f))) {
                            PosterImage(e.posterUrl ?: d.posterUrl, d.posterHeaders, Modifier.fillMaxSize())
                            if (current) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Now playing", tint = Color.White) }
                            if (p != null && p.fraction > 0.01f) LinearProgressIndicator(progress = { p.fraction }, color = SpotifyGreen, trackColor = Color.Black.copy(alpha = 0.4f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                        }
                    } else {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (current) SpotifyGreen else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            if (current) Icon(Icons.Default.PlayArrow, "Now playing", tint = OnAccent)
                            else Text("${e.episode ?: i + 1}", color = dev.asfakul.yumeiro.ui.theme.PageInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label, color = if (current) SpotifyGreen else dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(if (current) "Now playing" else null, e.runtimeMin?.let { "${it}m" }, if (p?.finished == true) "Watched" else null).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1)
                        e.description?.takeIf { it.isNotBlank() && hasThumbs }?.let { Text(it, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        if (!hasThumbs && p != null && p.fraction > 0.01f) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { p.fraction }, color = SpotifyGreen, trackColor = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth().height(3.dp))
                        }
                    }
                    if (p?.finished == true) Icon(Icons.Default.CheckCircle, "Watched", tint = SpotifyGreen.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
            }
        }

        if (d.recommendations.isNotEmpty()) {
            item(key = "recs_header") { dev.asfakul.yumeiro.ui.screens.watch.WatchSectionLabel("More like this") }
            item(key = "recs") {
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(d.recommendations.size, key = { "rec_" + d.recommendations[it].key }) { k ->
                        val r = d.recommendations[k]
                        PosterCard(r, onClick = { onOpenItem(r) })
                    }
                }
            }
        }
    }
    WatchLightsOverlay()
    }
    // Scroll so the playing episode is visible when the page opens / the episode changes.
    LaunchedEffect(state.index, season) {
        if (useGrid) return@LaunchedEffect
        val k = shown.indexOfFirst { it.index == state.index }
        if (k > 0) listState.scrollToItem((6 + (if (seasons.size > 1) 1 else 0) + k - 1).coerceAtLeast(0))
    }

    if (sheet != null) {
        val sheetPal = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = sheetPal.surfaceContainer, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet, dragHandle = { dev.asfakul.yumeiro.ui.components.MoonSheetHandle() }) {
          androidx.compose.runtime.CompositionLocalProvider(LocalPanelInk provides sheetPal.textPrimary) {
            Text(if (sheet == Sheet.SOURCES) "Sources" else "Subtitles", color = sheetPal.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Box(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                Column {
                    when (sheet) {
                        Sheet.SOURCES -> SourcesPanel(state, { player?.positionMs?.invoke() ?: 0L }) { i -> sheet = null; OnlineSession.switchSource(context, i, player?.positionMs?.invoke() ?: 0L) }
                        Sheet.SUBTITLES -> SubtitlesPanel(state) { sub -> sheet = null; OnlineSession.addSubtitle(context, sub, player?.positionMs?.invoke() ?: 0L) }
                        null -> {}
                    }
                }
            }
        }
          }
    }
}

@Composable
private fun BoxScope.SkipButton(player: dev.asfakul.yumeiro.ui.screens.video.PlayerHandle) {
    val skips by dev.asfakul.yumeiro.online.anime.AnimeSync.skips.collectAsState()
    val auto by dev.asfakul.yumeiro.online.OnlineSettings.autoSkip.collectAsState()
    val segments = skips?.segments.orEmpty()
    if (segments.isEmpty()) return
    var pos by remember { mutableStateOf(0L) }
    val autoDone = remember(skips?.key) { HashSet<Long>() }
    LaunchedEffect(skips?.key) {
        while (true) { pos = player.positionMs(); kotlinx.coroutines.delay(400) }
    }
    val current = segments.firstOrNull { it.contains(pos) }
    LaunchedEffect(current, auto) {
        if (auto && current != null && autoDone.add(current.startMs)) player.seekTo(current.endMs)
    }
    AnimatedVisibility(visible = current != null && !auto, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd)) {
        val seg = current ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(12.dp), color = Color.White,
            modifier = Modifier.navigationBarsPadding().padding(end = 20.dp, bottom = 120.dp).clip(RoundedCornerShape(12.dp))
                .clickable { player.seekTo(seg.endMs) }.testTag("online_skip"),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SkipNext, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(seg.kind.label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** Searches every subtitle site with a free key (OpenSubtitles, SubDL, SubSource) for the playing title / episode. */
@Composable
private fun SubtitlesPanel(state: OnlineSession.State, onPick: (dev.asfakul.yumeiro.online.OnlineSubtitle) -> Unit) {
    val ink = LocalPanelInk.current
    val errInk = if (ink.luminance() > 0.5f) ErrorColor else Color(0xFFB3261E)
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val osKey by dev.asfakul.yumeiro.online.OnlineSettings.openSubtitlesKey.collectAsState()
    val subdlKey by dev.asfakul.yumeiro.online.OnlineSettings.subdlKey.collectAsState()
    val subsourceKey by dev.asfakul.yumeiro.online.OnlineSettings.subsourceKey.collectAsState()
    val lang by dev.asfakul.yumeiro.online.OnlineSettings.subtitleLang.collectAsState()
    val hub = dev.asfakul.yumeiro.online.subtitles.SubtitleHub
    val anyKey = osKey.isNotBlank() || dev.asfakul.yumeiro.online.subtitles.OpenSubtitles.builtIn || subdlKey.isNotBlank() || subsourceKey.isNotBlank()
    var outcome by remember(state.videoId) { mutableStateOf<dev.asfakul.yumeiro.online.subtitles.SubtitleHub.Outcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.videoId, osKey, subdlKey, subsourceKey, lang) {
        if (!anyKey) return@LaunchedEffect
        busy = true
        outcome = runCatching {
            hub.search(
                dev.asfakul.yumeiro.network.NetworkClient.get(context), dev.asfakul.yumeiro.online.anime.AnimeIds.cleanTitle(state.detail.name),
                state.detail.imdbId, state.episode?.season, state.episode?.episode, lang,
            )
        }.getOrElse { dev.asfakul.yumeiro.online.subtitles.SubtitleHub.Outcome(emptyList(), mapOf("Search" to (it.message ?: "failed")), emptyList()) }
        busy = false
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                if (state.subtitles.isEmpty()) "No subtitles came with this source." else "${state.subtitles.size} subtitle track${if (state.subtitles.size == 1) "" else "s"} from the source — pick them in Captions.",
                color = ink.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(4.dp),
            )
        }
        if (!anyKey) item {
            Text("Search OpenSubtitles, SubDL and SubSource: add any of their free API keys in Settings → Online videos.", color = ink.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(4.dp))
        }
        val sites = outcome?.providers?.joinToString(", ") ?: hub.configured().joinToString(", ")
        if (busy) item { Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Text("Searching $sites (${lang.uppercase()})…", color = ink.copy(alpha = 0.6f), fontSize = 12.sp) } }
        outcome?.errors?.forEach { (site, e) -> item(key = "err_$site") { Text("$site: $e", color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(6.dp)) } }
        val hits = outcome?.hits.orEmpty()
        if (outcome != null && hits.isEmpty() && !busy) item { Text("Nothing found in this language.", color = ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(6.dp)) }
        itemsIndexed(hits, key = { _, r -> r.key }) { _, r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.06f)).clickable(enabled = fetching == null) {
                    fetching = r.key
                    scope.launch {
                        val link = runCatching {
                            hub.resolve(context, dev.asfakul.yumeiro.network.NetworkClient.get(context), r, state.episode?.season, state.episode?.episode)
                        }
                        fetching = null
                        link.onSuccess { onPick(dev.asfakul.yumeiro.online.OnlineSubtitle("${r.language.uppercase()} · ${r.provider}", it, emptyMap())) }
                            .onFailure { dev.asfakul.yumeiro.ui.components.AppMessages.error(context, dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Couldn't get the subtitle.")) }
                    }
                }.padding(12.dp).testTag("online_subtitle_result"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.release, color = ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(r.provider, r.language.uppercase(), "${r.downloads} downloads".takeIf { r.downloads > 0 }, "HI".takeIf { r.hearingImpaired },
                        "season pack".takeIf { r.seasonPack }, r.uploader).joinToString(" · "), color = ink.copy(alpha = 0.55f), fontSize = 11.sp)
                }
                if (fetching == r.key) CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Sends the playing source to a Chromecast / Google TV (picks a device first if needed). */
private fun castCurrent(context: android.content.Context, state: OnlineSession.State, player: dev.asfakul.yumeiro.ui.screens.video.PlayerHandle) {
    val link = state.link ?: return
    val activity = com.lagradost.cloudstream3.CommonActivity.activity ?: (context as? android.app.Activity) ?: return
    if (link.headers.isNotEmpty() || link.referer != null) {
        dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "This source needs special request headers; if the TV can't play it, pick another source and cast again")
    }
    val start = {
        val ok = dev.asfakul.yumeiro.cast.CastSupport.play(
            context, dev.asfakul.yumeiro.cast.CastSupport.Media(
                url = link.url, title = state.detail.name, subtitle = state.episode?.let { e -> listOfNotNull(e.season?.let { "S$it" }, e.episode?.let { "E$it" }, e.name).joinToString(" ") },
                posterUrl = state.detail.posterUrl, isHls = link.isHls, isDash = link.isDash, positionMs = player.positionMs(),
                subtitles = state.subtitles.map { it.language to it.url },
            ),
        )
        if (ok) { player.pause(); dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Casting ${state.detail.name}") }
        else dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "Couldn't start casting")
    }
    if (dev.asfakul.yumeiro.cast.CastSupport.connected(context)) start()
    else { dev.asfakul.yumeiro.cast.CastSupport.whenConnected(context) { activity.runOnUiThread { start() } }; dev.asfakul.yumeiro.cast.CastSupport.pickDevice(activity) }
}

/** "Download" in the player: asks where/how first ([dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt]). */
/** Round 9: audio-language chips for the current links (only when they really differ). */
internal fun audioOptionsFor(links: List<dev.asfakul.yumeiro.online.OnlineLink>, version: String): List<dev.asfakul.yumeiro.ui.screens.watch.AudioFilterOption> {
    val al = dev.asfakul.yumeiro.online.AudioLanguages
    val keys = links.map { al.filterKey(al.classify(it.name + " " + it.source, version)) }
    val counts = keys.groupingBy { it }.eachCount()
    if (counts.size <= 1) return emptyList()
    return counts.entries.sortedWith(compareBy<Map.Entry<String, Int>> { when (it.key) { al.ORIG -> 0; "" -> 3; al.DUB_OTHER -> 2; else -> 1 } }.thenByDescending { it.value })
        .map { dev.asfakul.yumeiro.ui.screens.watch.AudioFilterOption(it.key, al.keyLabel(it.key), it.value) }
}

/** Remembers [key] as the preferred audio and moves playback to it when the current source isn't. */
internal fun pickAudio(context: android.content.Context, state: OnlineSession.State, key: String?, positionMs: Long) {
    val al = dev.asfakul.yumeiro.online.AudioLanguages
    al.setPreferred(context, key)
    if (key == null) return
    val keyOf = { l: dev.asfakul.yumeiro.online.OnlineLink -> al.filterKey(al.classify(l.name + " " + l.source, state.version)) }
    val current = state.links.getOrNull(state.linkIndex)
    if (current != null && keyOf(current) == key) return
    val target = state.links.indexOfFirst { keyOf(it) == key && it.url !in state.failed }.takeIf { it >= 0 } ?: return
    OnlineSession.switchSource(context, target, positionMs)
    dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "Switched to ${al.keyLabel(key)}", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.INFO)
}

internal fun askDownloadCurrent(context: android.content.Context, state: OnlineSession.State) {
    val s = dev.asfakul.yumeiro.online.download.DownloadSettings.also { it.init(context) }
    val choice = s.downloader.value
    if (choice != dev.asfakul.yumeiro.online.download.ExternalDownloader.IN_APP) {
        val err = dev.asfakul.yumeiro.online.download.OnlineDownloads.sendCurrentToExternal(context)
        if (err == null) { dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Sent to ${dev.asfakul.yumeiro.online.download.ExternalDownloader.label(choice)}"); return }
        dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "$err. Using Yumeiro's downloader instead")
    }
    dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt.ask(context, dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt.Request(
        type = dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt.Type.CURRENT,
        title = state.detail.name, what = state.episode?.label ?: "this video", count = 1,
        folder = if (state.episode != null) dev.asfakul.yumeiro.online.download.DownloadNaming.seriesFolder(state.detail.name, state.api, state.version) else dev.asfakul.yumeiro.online.download.DownloadGroups.LOOSE,
        quality = state.link?.qualityLabel?.ifBlank { null } ?: "Current source",
        defaultKeep = s.keepByDefault.value,
    ) { keep -> dev.asfakul.yumeiro.online.download.OnlineDownloads.downloadCurrent(context, keep) })
}
