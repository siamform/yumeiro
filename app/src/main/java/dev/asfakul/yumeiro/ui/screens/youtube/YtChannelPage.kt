/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.youtube

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.youtube.YouTube
import dev.asfakul.yumeiro.youtube.YtChannel
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.formatYtSubscribers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

private data class TabPage(val items: List<YtItem> = emptyList(), val next: Page? = null, val loading: Boolean = false, val error: String? = null, val done: Boolean = false)

private fun tabLabel(tab: String) = when (tab) {
    "videos" -> "Videos"; "shorts" -> "Shorts"; "livestreams" -> "Live"; else -> tab.replaceFirstChar { it.uppercase() }
}

/**
 * A channel the way YouTube shows it: banner, avatar, name and subscribers, then Videos / Shorts /
 * Live tabs that page forever. Opens in place inside the YouTube tab; back returns to the feed.
 */
@Composable
internal fun YtChannelPage(url: String, fallbackName: String, onBack: () -> Unit, onPlay: (YtItem, List<YtItem>) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pal = LocalMtPalette.current
    var channel by remember(url) { mutableStateOf<YtChannel?>(null) }
    var failed by remember(url) { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(url) { mutableStateOf("videos") }
    val pages = remember(url) { mutableStateMapOf<String, TabPage>() }

    fun loadTab(t: String) {
        val cur = pages[t] ?: TabPage()
        if (cur.loading || cur.done) return
        pages[t] = cur.copy(loading = true, error = null)
        scope.launch {
            pages[t] = runCatching { YouTube.channelTab(context, url, t, cur.next) }.fold(
                { p -> TabPage((cur.items + p.items).distinctBy { it.id }, p.next, done = p.next == null) },
                { cur.copy(loading = false, error = friendly(it)) },
            )
        }
    }
    LaunchedEffect(url) {
        runCatching { YouTube.channel(context, url) }.fold(
            { c -> channel = c; if (c.tabs.isNotEmpty() && tab !in c.tabs) tab = c.tabs.first(); loadTab(tab) },
            { failed = friendly(it) },
        )
    }

    val list = rememberLazyListState()
    val page = pages[tab] ?: TabPage(loading = channel != null)
    YtEndlessTrigger(list, channel != null && !page.done && page.error == null) { loadTab(tab) }
    LazyColumn(Modifier.fillMaxSize().testTag("youtube_channel"), state = list, contentPadding = PaddingValues(bottom = 160.dp)) {
        item("ch_head") {
            val c = channel
            Column {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 5f).padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(pal.accent.copy(alpha = 0.55f), pal.accent2.copy(alpha = 0.35f)))),
                    ) { if (c?.banner != null) PosterImage(c.banner, emptyMap(), Modifier.fillMaxSize()) }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 18.dp, top = 6.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).testTag("youtube_channel_back"),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(pal.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        if (c?.avatar != null) PosterImage(c.avatar, emptyMap(), Modifier.fillMaxSize())
                        else Text((c?.name ?: fallbackName).take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c?.name?.ifBlank { null } ?: fallbackName, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val subs = c?.let { formatYtSubscribers(it.subscribers) }.orEmpty()
                        if (subs.isNotBlank()) Text(subs, color = TextSecondary, fontSize = 13.sp)
                    }
                }
                val desc = c?.description.orEmpty().trim()
                if (desc.isNotEmpty()) Text(desc, color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp))
                if (c != null && c.tabs.size > 1) LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("youtube_channel_tabs"),
                ) {
                    itemsIndexed(c.tabs, key = { _, t -> "t_$t" }) { _, t -> YtChip(tabLabel(t), t == tab) { tab = t; if (pages[t] == null) loadTab(t) } }
                }
            }
        }
        val err = failed
        if (err != null) item("ch_err") { YtProblem(err, null) }
        else {
            if (page.items.isEmpty() && page.loading) itemsIndexed(List(4) { it }, key = { i, _ -> "csk$i" }) { _, _ ->
                SkeletonBlock(null, 220.dp, Modifier.padding(16.dp), RoundedCornerShape(20.dp))
            }
            if (tab == "shorts") {
                val rows = page.items.chunked(2)
                itemsIndexed(rows, key = { i, r -> "sr${i}_${r.first().id}" }) { _, r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.forEach { s -> Box(Modifier.weight(1f)) { YtShortCard(s, Modifier.fillMaxWidth()) { onPlay(s, page.items) } } }
                        if (r.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else itemsIndexed(page.items, key = { i, it -> "cv${i}_${it.id}" }) { _, item ->
                YtVideoCard(item, wide = true, showChannel = false) { onPlay(item, page.items) }
            }
            if (!page.loading && page.items.isEmpty() && channel != null && page.error == null) item("ch_none") { YtProblem("This channel has nothing here yet.", null) }
            if (page.error != null) item("ch_perr") { YtProblem(page.error) { loadTab(tab) } }
            if (page.items.isNotEmpty() && page.loading) item("ch_more") { YtLoadingMore() }
        }
    }
}

/** 9:16 Shorts tile: thumbnail with the title over a bottom scrim. */
@Composable
internal fun YtShortCard(item: YtItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag("youtube_short")) {
        PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Text(
            item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
