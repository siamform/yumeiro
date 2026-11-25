/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineSession
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.shimmer
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private sealed interface DetailState {
    data object Loading : DetailState
    data class Ready(val detail: OnlineDetail) : DetailState
    data class MissingSource(val api: String) : DetailState
    data class Failed(val message: String) : DetailState
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnlineDetailScreen(api: String, url: String, onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit, onOpenExtensions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val preview = remember(api, url) { OnlineRepository.previews["$api|$url"] }

    var state by remember(api, url) { mutableStateOf<DetailState>(DetailState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(api, url, engine.ready, engine.generation, attempt) {
        if (!engine.ready) return@LaunchedEffect
        val provider = ExtensionManager.provider(api)
        if (provider == null) { state = DetailState.MissingSource(api); return@LaunchedEffect }
        if (state !is DetailState.Ready) state = DetailState.Loading
        val result = runCatching { OnlineRepository.load(provider, url, force = attempt > 0) }
        // R16 G4 (Y7): a load cancelled by a restart of this effect (an extension finished loading,
        // so engine.generation changed) used to show "Couldn't open this title" for a moment.
        // A failed refresh (pull down while offline) used to replace the page with that error;
        // it now keeps the page and says so.
        if (!isActive) { refreshing = false; return@LaunchedEffect }
        val shown = state as? DetailState.Ready
        state = result.fold(
            { DetailState.Ready(it) },
            { e ->
                if (shown != null) { dev.asfakul.yumeiro.ui.components.AppMessages.show(context, OnlineErrors.friendly(e, "Couldn't refresh"), dev.asfakul.yumeiro.ui.components.AppMessages.Kind.INFO); shown }
                else DetailState.Failed(OnlineErrors.friendly(e, "Couldn't open this title"))
            },
        )
        (state as? DetailState.Ready)?.let { r ->
            dev.asfakul.yumeiro.online.OnlineBookmarks.ensureLoaded(context)
            dev.asfakul.yumeiro.online.OnlineBookmarks.seen(context, api, url, dev.asfakul.yumeiro.online.NewEpisodesWorker.episodeCount(r.detail))
        }
        refreshing = false
    }

    Box(Modifier.fillMaxSize().testTag("online_detail_screen")) {
        when (val s = state) {
            DetailState.Loading -> DetailSkeleton(preview, onBack)
            is DetailState.MissingSource -> Column {
                SubPageHeader(preview?.name ?: "Title", onBack)
                StateMessage(Icons.Default.Extension, "Source not available", "The extension that provides “$api” isn't installed or is turned off.",
                    primary = "Manage extensions" to onOpenExtensions, secondary = "Go back" to onBack, testTag = "online_detail_missing")
            }
            is DetailState.Failed -> Column {
                SubPageHeader(preview?.name ?: "Title", onBack)
                StateMessage(Icons.Default.ErrorOutline, "Couldn't open this title", s.message, tint = ErrorColor,
                    primary = "Retry" to { attempt++ }, secondary = "Go back" to onBack, testTag = "online_detail_error")
            }
            is DetailState.Ready -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; attempt++ }) {
                DetailContent(
                    d = s.detail, onBack = onBack, onOpenTitle = onOpenTitle,
                    onPlay = { version, index -> OnlineSession.play(context, s.detail, version, index) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(d: OnlineDetail, onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit, onPlay: (version: String, index: Int) -> Unit) {
    val versions = d.versions
    // F-01: a Dub / Sub chip tapped on the poster picks that version first.
    var version by rememberSaveable(d.url) {
        mutableStateOf(dev.asfakul.yumeiro.online.VersionHint.pick(versions, dev.asfakul.yumeiro.online.VersionHint.take(d.url), dev.asfakul.yumeiro.online.OnlineSession.defaultVersion(versions)))
    }
    if (version !in versions && versions.isNotEmpty()) version = versions.first()
    val seasons = remember(d, version) { d.seasons(version) }
    var seasonIndex by rememberSaveable(d.url, version) { mutableIntStateOf(0) }
    val season = seasons.getOrNull(seasonIndex) ?: seasons.firstOrNull()
    // NBUG-5: a list that mixes language runs keeps each run together and labels its tiles.
    val labelled = remember(d, version) { OnlineSession.labelledEpisodes(d, version) }
    val ordered = remember(labelled) { labelled.map { it.first } }
    val episodes = remember(ordered, season) { ordered.withIndex().filter { season == null || it.value.season == season.number } }
    val historyVersion by OnlineHistory.version.collectAsState()
    // NF-1: what was the newest episode last time? Read once per title + version off the main thread, then remember
    // what this visit shows (the stored value only grows). Episodes newer than the old value are "Fresh".
    val ctxNf1 = androidx.compose.ui.platform.LocalContext.current
    val episodeStatusOn = remember { dev.asfakul.yumeiro.FeatureFlags.on(ctxNf1, dev.asfakul.yumeiro.FeatureFlags.Flag.EPISODE_STATUS) }
    val seenBefore by androidx.compose.runtime.produceState<Int?>(null, d.api, d.url, version) {
        value = if (!episodeStatusOn) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val old = dev.asfakul.yumeiro.online.EpisodeStatus.Seen.get(ctxNf1, d.api, d.url, version)
            dev.asfakul.yumeiro.online.EpisodeStatus.highest(ordered)?.let { dev.asfakul.yumeiro.online.EpisodeStatus.Seen.put(ctxNf1, d.api, d.url, version, it) }
            old
        }
    }
    fun statusOf(e: OnlineEpisode, p: OnlineHistory.Progress?): dev.asfakul.yumeiro.online.EpisodeStatus? = if (!episodeStatusOn) null else
        dev.asfakul.yumeiro.online.EpisodeStatus.of(p, (dev.asfakul.yumeiro.online.EpisodeStatus.ordinal(e) ?: 0) - 1, seenBefore)
    // Round 13: thumbnails for episodes the extension sends without one.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(d.api, d.url) { dev.asfakul.yumeiro.online.EpisodeStills.ensure(ctx, d) }
    val stillsMap by dev.asfakul.yumeiro.online.EpisodeStills.stills.collectAsState()
    val stills = stillsMap[dev.asfakul.yumeiro.online.EpisodeStills.keyOf(d)]
    val resume = remember(historyVersion, d.url) { OnlineHistory.entry(d.api, d.url) }
    // Episode to continue: the one in history (same version when possible), else the first.
    val resumeIndex = remember(resume, ordered) { resume?.episodeData?.let { ed -> ordered.indexOfFirst { it.data == ed } }?.takeIf { it >= 0 } }
    var gridMode by rememberSaveable(d.url) { mutableStateOf(true) }
    val tileCols = (gridColumns() + 2).coerceAtLeast(5)
    var plotExpanded by remember { mutableStateOf(false) }
    val downloads by dev.asfakul.yumeiro.online.download.PrivateDownloads.visibleState()
    val savedData = remember(downloads, d.url, d.api) {
        downloads.filter { it.api == d.api && it.titleUrl == d.url && it.status == dev.asfakul.yumeiro.online.download.OnlineDownloads.Status.DONE }.mapNotNull { it.data }.toSet()
    }

    // META-1 (owner list 13): release date, IMDb rating and votes, country, language, director, writers, companies,
    // cast and trailers; the extension's own answer at once, the free sources (TitleFacts) when they arrive.
    val facts by androidx.compose.runtime.produceState(dev.asfakul.yumeiro.online.meta.TitleFacts.fromDetail(d), d.api, d.url) {
        value = dev.asfakul.yumeiro.online.meta.TitleFacts.load(ctx, d)
    }
    val year = d.year ?: facts.released?.take(4)?.toIntOrNull()
    val runtime = d.durationMin ?: facts.runtimeMin

    LazyColumn(Modifier.fillMaxSize().testTag("online_detail_list"), contentPadding = PaddingValues(bottom = 48.dp)) {
        item("hero") {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    PosterImage(d.backgroundUrl ?: d.posterUrl, d.posterHeaders, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, DarkBackground))))
                }
                Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).testTag("subpage_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        dev.asfakul.yumeiro.ui.components.YumeiroMark(light = true)
                        Text(d.api, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 170.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                        PosterImage(d.posterUrl, d.posterHeaders, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Text(d.name, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp, modifier = Modifier.testTag("online_detail_title"))
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Pill(d.type)
                            year?.let { Pill("$it", TextSecondary) }
                            runtime?.let { Pill(if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m", TextSecondary) }
                            (d.contentRating ?: facts.certification)?.let { Pill(it, TextSecondary) }
                            d.score?.takeIf { it > 0 }?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = dev.asfakul.yumeiro.ui.theme.StarTone, modifier = Modifier.size(14.dp))
                                    Text(String.format(java.util.Locale.US, " %.1f", it), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        facts.rating?.let { r ->
                            Row(Modifier.padding(top = 6.dp).testTag("online_detail_imdb"), verticalAlignment = Alignment.CenterVertically) {
                                Text("IMDb", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5C518)).padding(horizontal = 5.dp, vertical = 1.dp))
                                Text(String.format(java.util.Locale.US, " %.1f", r), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                facts.votes?.let { Text(" · " + dev.asfakul.yumeiro.online.meta.TitleFacts.votesText(it), color = TextSecondary, fontSize = 12.sp) }
                            }
                        }
                        Text(d.api, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        item("actions") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                d.unsupportedReason?.let {
                    StateMessage(Icons.Default.ErrorOutline, "Can't play this title", it, tint = WarnColor, testTag = "online_detail_unsupported")
                }
                val target = resumeIndex ?: if (ordered.isNotEmpty()) 0 else null
                val targetEp = target?.let { ordered.getOrNull(it) }
                val canPlay = (d.playData != null || targetEp != null) && d.unsupportedReason == null
                if (canPlay) {
                    val resumeText = resume?.takeIf { it.positionMs > 15_000 }?.let { r -> " · " + formatClock(r.positionMs) + " left of " + formatClock(r.durationMs) }
                    Button(
                        onClick = { if (targetEp != null) onPlay(version, target) else onPlay(version, -1) },
                        shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("online_detail_play"),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        val epLabel = targetEp?.let { e -> listOfNotNull(e.season?.let { "S$it" }, e.episode?.let { "E$it" }).joinToString("").ifBlank { "episode ${target + 1}" } }
                        Text(
                            when {
                                d.playData != null && d.kind == OnlineKind.LIVE -> "Watch live"
                                resume != null && targetEp != null && resumeIndex != null -> "Continue $epLabel"
                                resume != null && targetEp == null && resume.positionMs > 15_000 -> "Resume"
                                targetEp != null -> "Play $epLabel"
                                else -> "Play"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                        if (targetEp == null && resumeText != null) Text(resumeText.replace(" left of ", " / "), fontSize = 12.sp)
                    }
                }
                MyListButton(d)
                if (d.unsupportedReason == null && (d.playData != null || ordered.isNotEmpty()) && d.kind != OnlineKind.LIVE) {
                    Spacer(Modifier.height(8.dp))
                    TitleDownloadButton(d, version, ordered, episodes, season?.title?.takeIf { seasons.size > 1 }, Modifier.fillMaxWidth(), current = target, onVersion = { version = it })
                }
                d.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        plot, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.animateContentSize().clickable { plotExpanded = !plotExpanded }.testTag("online_detail_plot"),
                    )
                }
                if (d.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.tags.forEach { Pill(it, TextTertiary) }
                    }
                }
            }
        }

        titleFactsItems(ctx, d, facts, tagPrefix = "online_detail") { title, trailing -> SectionHeader(title, trailing) }
        if (versions.size > 1 || versions.firstOrNull()?.isNotEmpty() == true && versions.size == 1 && d.kind == OnlineKind.ANIME) {
            item("versions") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio", color = TextSecondary, fontSize = 13.sp)
                    versions.forEach { v -> OnlineChip(v.ifBlank { "Default" }, v == version, { version = v }, Modifier.testTag("online_version_$v")) }
                }
            }
        }
        if (seasons.size > 1) {
            item("seasons") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("online_seasons"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEachIndexed { i, se -> OnlineChip(se.title, i == seasonIndex, { seasonIndex = i }) }
                }
            }
        }
        if (episodes.isNotEmpty()) {
            item("ep_header") {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader(if (seasons.size > 1) season?.title ?: "Episodes" else "Episodes", episodeCountText(episodes.map { it.value }, episodes.mapNotNull { labelled[it.index].second }.distinct().size)) }
                    IconButton(onClick = { gridMode = !gridMode }, modifier = Modifier.testTag("online_episode_layout")) {
                        Icon(if (gridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, if (gridMode) "Show as list" else "Show as grid", tint = TextSecondary)
                    }
                }
            }
            if (gridMode) {
                // Numbered tiles: fastest way through long anime seasons.
                items(episodes.chunked(tileCols), key = { "epg_" + it.first().index }) { line ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { (i, e) ->
                            val p = remember(historyVersion, e.data) { OnlineHistory.progressOf(d.api, e.data) }
                            EpisodeTile(e, tag = labelled[i].second, current = i == resumeIndex, progress = p?.fraction ?: 0f, watched = p?.finished == true, downloaded = e.data in savedData, modifier = Modifier.weight(1f), fresh = statusOf(e, p) == dev.asfakul.yumeiro.online.EpisodeStatus.Fresh) { onPlay(version, i) }
                        }
                        repeat(tileCols - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                items(episodes, key = { "ep_" + it.index + it.value.data }) { (i, e) ->
                    val p = remember(historyVersion, e.data) { OnlineHistory.progressOf(d.api, e.data) }
                    EpisodeRow(e, d, stills, progress = p?.fraction ?: 0f, watched = p?.finished == true, status = statusOf(e, p), trailing = { EpisodeDownloadIcon(d, version, i, e) }) { onPlay(version, i) }
                }
            }
        }
        if (d.recommendations.isNotEmpty()) {
            item("recs") {
                Column {
                    SectionHeader("More like this")
                    PosterGrid(d.recommendations, onClick = { OnlineRepository.previews[it.key] = it; onOpenTitle(it) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(e: OnlineEpisode, d: OnlineDetail, stills: Map<String, String>?, progress: Float = 0f, watched: Boolean = false, status: dev.asfakul.yumeiro.online.EpisodeStatus? = null, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick).testTag("online_episode")) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                EpisodeThumb(d, e, stills, Modifier.fillMaxSize())
                Icon(if (watched) Icons.Default.CheckCircle else Icons.Default.PlayArrow, null, tint = if (watched) SpotifyGreen else Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(3.dp))
                if (progress > 0.01f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(SpotifyGreen))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                e.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp) }
                val runtime = e.runtimeMin?.takeIf { it > 0 }?.let { "$it min" }
                if (status != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.label, color = if (status is dev.asfakul.yumeiro.online.EpisodeStatus.InProgress) TextSecondary else SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("online_episode_status"))
                    if (runtime != null) Text("  ·  $runtime", color = TextTertiary, fontSize = 11.sp)
                } else runtime?.let { Text(it, color = TextTertiary, fontSize = 11.sp) }
            }
            trailing()
        }
    }
}

@Composable
private fun DetailSkeleton(preview: OnlineItem?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("online_detail_loading")) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            if (preview?.posterUrl != null) {
                PosterImage(preview.posterUrl, preview.posterHeaders, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), DarkBackground))))
            } else Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(preview?.name.orEmpty(), color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(null, 50.dp, shape = RoundedCornerShape(24.dp))
            SkeletonBlock(null, 12.dp); SkeletonBlock(null, 12.dp); SkeletonBlock(220.dp, 12.dp)
            Spacer(Modifier.height(8.dp))
            repeat(4) { SkeletonBlock(null, 64.dp, shape = RoundedCornerShape(16.dp)) }
        }
    }
}

@Composable
private fun EpisodeTile(e: OnlineEpisode, tag: String? = null, current: Boolean, progress: Float, watched: Boolean, modifier: Modifier, downloaded: Boolean = false, fresh: Boolean = false, onClick: () -> Unit) {
    val bg = when { current -> SpotifyGreen.copy(alpha = 0.24f); watched -> Color.White.copy(alpha = 0.04f); else -> GlassFill }
    Box(modifier.aspectRatio(1.25f).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick).testTag("online_episode"), contentAlignment = Alignment.Center) {
        Text(e.episode?.toString() ?: "•", color = if (watched) TextTertiary else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (tag != null) Text(tag, color = TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp, end = 14.dp).testTag("online_episode_tag"))
        // NF-1: a small green dot for an episode added since the last visit (TalkBack reads "Fresh").
        if (fresh && !downloaded) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(SpotifyGreen).semantics { contentDescription = "Fresh" }.testTag("online_episode_fresh"))
        if (downloaded) Icon(Icons.Default.CheckCircle, "Downloaded", tint = SpotifyGreen, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(12.dp))
        if (progress > 0.01f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(SpotifyGreen))
            }
        }
    }
}

/**
 * Header count. NBUG-5: when one list holds several language runs ([runs] > 1) the count is of different
 * episodes, not of entries — Blue Lock's Dub list said "59 episodes" for 24.
 */
internal fun episodeCountText(list: List<OnlineEpisode>, runs: Int): String {
    val n = if (runs > 1) OnlineSession.distinctEpisodeCount(list) else list.size
    return "$n episode${if (n == 1) "" else "s"}" + if (runs > 1) " · $runs versions" else ""
}

internal fun formatClock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(java.util.Locale.US, "%d:%02d", m, sec)
}

/** Add to / remove from My List, with the list status. */
@Composable
private fun MyListButton(d: OnlineDetail) {
    val context = LocalContext.current
    val saved by dev.asfakul.yumeiro.online.OnlineBookmarks.items.collectAsState()
    val saved0 = saved.firstOrNull { it.api == d.api && it.url == d.url }
    // F-09: a Favorite-only title is saved but not in My List.
    val b = saved0?.takeIf { it.listed }
    var menu by remember { mutableStateOf(false) }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val item = OnlineItem(d.name, d.url, d.api, d.posterUrl, d.posterHeaders, d.type)
                val now = dev.asfakul.yumeiro.online.OnlineBookmarks.toggle(context, item, dev.asfakul.yumeiro.online.NewEpisodesWorker.episodeCount(d))
                dev.asfakul.yumeiro.ui.components.AppMessages.show(context, if (now) "Added to My List" else "Removed from My List")
            },
            shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f).height(46.dp).testTag("online_detail_mylist"),
        ) {
            Icon(if (b != null) Icons.Default.Check else Icons.Default.Add, null, tint = if (b != null) SpotifyGreen else TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(if (b != null) "In My List" else "My List", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        if (b != null) Box {
            androidx.compose.material3.OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(24.dp), modifier = Modifier.height(46.dp)) {
                Text(b.status.label, color = TextPrimary, fontSize = 13.sp)
            }
            androidx.compose.material3.DropdownMenu(menu, onDismissRequest = { menu = false }) {
                dev.asfakul.yumeiro.online.OnlineBookmarks.Status.picks.forEach { st ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(st.label) }, onClick = { dev.asfakul.yumeiro.online.OnlineBookmarks.setStatus(context, d.api, d.url, st); menu = false })
                }
            }
        }
        // F-09: Favorite, independent of My List.
        val fav = saved0?.favorite == true
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val item = OnlineItem(d.name, d.url, d.api, d.posterUrl, d.posterHeaders, d.type)
                val c = dev.asfakul.yumeiro.online.OnlineBookmarks.toggleFavorite(context, item)
                dev.asfakul.yumeiro.ui.components.AppMessages.show(context, if (c == dev.asfakul.yumeiro.online.OnlineBookmarks.Change.ADDED) "Added to Favorites" else "Removed from Favorites")
            },
            shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(46.dp).testTag("online_detail_favorite").semantics { contentDescription = if (fav) "Remove from Favorites" else "Add to Favorites" },
        ) {
            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Color(0xFFFF6B8B) else TextPrimary)
        }
    }
}
