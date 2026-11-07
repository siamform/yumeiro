/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Featured titles at the top of Online (the big banner MovieBox / CloudStream / DramaBox open
 * with): swipeable, advances on its own, Play + My List + Info.
 */
@Composable
internal fun HeroCarousel(
    items: List<OnlineItem>,
    onPlay: (OnlineItem) -> Unit,
    onInfo: (OnlineItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val pager = rememberPagerState { items.size }
    val saved by OnlineBookmarks.items.collectAsState()
    LaunchedEffect(items.size) {
        while (items.size > 1) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val bg = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier.fillMaxWidth().testTag("online_hero")) {
        val h = (maxWidth * 1.2f).coerceAtMost(560.dp).coerceAtLeast(360.dp)
        HorizontalPager(pager, Modifier.fillMaxWidth().height(h), key = { items.getOrNull(it)?.key ?: "hero_$it" }) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            Box(Modifier.fillMaxSize().clickable { onInfo(item) }) {
                PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to bg)))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val tags = listOfNotNull(item.type, item.quality?.takeIf { it.isNotBlank() && it != "Unknown" }, item.score?.takeIf { it > 0 }?.let { "★ %.1f".format(java.util.Locale.US, it) }, item.api)
                    Text(tags.joinToString("  •  "), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Text(item.name, color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isSaved = saved.any { it.api == item.api && it.url == item.url }
                        HeroIconButton(if (isSaved) Icons.Default.Check else Icons.Default.Add, if (isSaved) "Saved" else "My List") {
                            val now = OnlineBookmarks.toggle(context, item)
                            dev.asfakul.yumeiro.ui.components.AppMessages.show(context, if (now) "Added to My List" else "Removed from My List")
                        }
                        Button(
                            onClick = { onPlay(item) }, shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp), modifier = Modifier.testTag("online_hero_play"),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        HeroIconButton(Icons.Outlined.Info, "Info") { onInfo(item) }
                    }
                }
            }
        }
        if (items.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(items.size) { i ->
                val sel = i == pager.currentPage
                val w by animateDpAsState(if (sel) 18.dp else 6.dp, label = "dot")
                val c by animateColorAsState(if (sel) SpotifyGreen else Color.White.copy(alpha = 0.35f), label = "dotc")
                Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(c))
            }
        }
    }
}

@Composable
private fun HeroIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1)
    }
}

/** "Top 10" row: big outlined rank numbers next to posters. */
@Composable
internal fun TopTenRow(title: String, items: List<OnlineItem>, onClick: (OnlineItem) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.testTag("online_top10")) {
        SectionHeader(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items.take(10), key = { i, it -> "${i}_${it.key}" }) { i, item ->
                Row(Modifier.clickable { onClick(item) }.testTag("online_poster"), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(if (i == 9) 62.dp else 44.dp).height(150.dp), contentAlignment = Alignment.BottomEnd) {
                        Text(
                            "${i + 1}", fontSize = 92.sp, fontWeight = FontWeight.Black, lineHeight = 92.sp,
                            color = SpotifyGreen.copy(alpha = 0.9f),
                            style = TextStyle(drawStyle = Stroke(width = 5f)),
                            modifier = Modifier.offset(x = 10.dp, y = 14.dp),
                        )
                    }
                    Box(Modifier.width(104.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) {
                        PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Landscape "Continue watching" cards with the episode and a progress bar. */
@Composable
internal fun ContinueRow(entries: List<OnlineHistory.Entry>, onClick: (OnlineItem) -> Unit, onSeeAll: () -> Unit, edgePadding: androidx.compose.ui.unit.Dp = 16.dp) {
    if (entries.isEmpty()) return
    Column(Modifier.testTag("online_continue")) {
        SectionHeader("Continue watching") { SeeAllButton(onSeeAll) }
        LazyRow(contentPadding = PaddingValues(horizontal = edgePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(entries.take(15), key = { i, it -> "${i}_${it.key}" }) { _, e ->
                Column(Modifier.width(200.dp).clip(RoundedCornerShape(14.dp)).clickable { onClick(e.toItem()) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
                        PosterImage(e.posterUrl, e.posterHeaders, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                        Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxWidth(e.fraction).height(3.dp).background(SpotifyGreen))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(e.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(e.episodeLabel, e.api).joinToString(" · "), color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A titled horizontal row of posters with "See all" (the classic streaming-app shelf). */
@Composable
internal fun PosterShelf(title: String, subtitle: String?, items: List<OnlineItem>, horizontal: Boolean, onClick: (OnlineItem) -> Unit, onSeeAll: (() -> Unit)?) {
    if (items.isEmpty()) return
    Column(Modifier.testTag("online_row")) {
        SectionHeader(title, subtitle = subtitle) { if (onSeeAll != null) SeeAllButton(onSeeAll) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onClick(it) }, horizontal = horizontal, width = 112.dp) }
        }
    }
}

/** Picks the banner titles: the first few of each source's first row, interleaved. */
internal fun heroPicks(rows: List<List<OnlineItem>>, max: Int = 8): List<OnlineItem> {
    val lists = rows.map { r -> r.filter { !it.posterUrl.isNullOrBlank() }.take(4) }.filter { it.isNotEmpty() }
    val out = LinkedHashMap<String, OnlineItem>()
    var i = 0
    while (out.size < max && lists.any { i < it.size }) {
        lists.forEach { l -> l.getOrNull(i)?.let { if (out.size < max) out.putIfAbsent(it.key, it) } }
        i++
    }
    return out.values.toList()
}
