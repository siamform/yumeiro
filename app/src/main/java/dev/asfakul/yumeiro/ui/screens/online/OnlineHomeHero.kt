/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
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
 * "Spotlight": the featured titles at the top of Yumix as a compact, endless card carousel. Each
 * card is a cinematic strip (blurred-dark backdrop, crisp poster on the right, kana overline,
 * title, tags and Play / My List / Info) and the neighbours peek in at the sides. About 1/3 of the
 * height of the old full-bleed banner, so the shelves start right below it.
 */
@Composable
internal fun HeroCarousel(
    items: List<OnlineItem>,
    onPlay: (OnlineItem) -> Unit,
    onInfo: (OnlineItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Round 10: the last unfinished title leads the banner as a Resume card. */
    resume: OnlineHistory.Entry? = null,
    onResume: (OnlineHistory.Entry) -> Unit = {},
    /** Slides that came from an extension's "latest episodes" row (labelled NEW EPISODE). */
    newKeys: Set<String> = emptySet(),
) {
    val lead = if (resume != null) 1 else 0
    if (items.isEmpty() && resume == null) return
    val context = LocalContext.current
    val n = items.size + lead
    val laps = if (n > 1) HERO_LAPS else 1
    val pager = rememberPagerState(initialPage = if (n > 1) (laps / 2) * n else 0) { n * laps }
    val saved by OnlineBookmarks.items.collectAsState()
    LaunchedEffect(n) {
        while (n > 1) {
            delay(6_500)
            if (!pager.isScrollInProgress) pager.animateScrollToPage(pager.currentPage + 1, animationSpec = androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    Column(modifier.fillMaxWidth().testTag("online_hero")) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth().height(HERO_HEIGHT),
            contentPadding = PaddingValues(horizontal = if (n > 1) 20.dp else 16.dp),
            pageSpacing = 10.dp,
            key = { val r = it % n; (if (resume != null && r == 0) "resume:${resume.key}" else items[r - lead].key) + "#$it" },
            beyondViewportPageCount = 1,
        ) { page ->
            val real = page % n
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
            val k = 1f - kotlin.math.abs(offset)
            if (resume != null && real == 0) {
                ResumeSlide(resume, Modifier.fillMaxSize().graphicsLayer { val sc = 0.92f + 0.08f * k; scaleX = sc; scaleY = sc; alpha = 0.55f + 0.45f * k }, offset) { onResume(resume) }
                return@HorizontalPager
            }
            val item = items[real - lead]
            val isNew = item.key in newKeys
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { val sc = 0.92f + 0.08f * k; scaleX = sc; scaleY = sc; alpha = 0.55f + 0.45f * k }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .clickable(onClickLabel = "Details") { onInfo(item) }
                    .testTag("online_hero_card"),
            ) {
                PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f; translationX = offset * 60f })
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.25f))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to p.accent.copy(alpha = 0.28f))))
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                (if (dev.asfakul.yumeiro.ui.components.kanaEnabled()) (if (isNew) "新着 · " else "注目 · ") else "") + (if (isNew) "NEW EPISODE  " else "SPOTLIGHT  ") + "%02d".format(java.util.Locale.US, real + 1) + " / " + "%02d".format(java.util.Locale.US, n),
                                color = p.accentBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, maxLines = 1,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(item.name, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            val tags = listOfNotNull(item.type, item.quality?.takeIf { it.isNotBlank() && it != "Unknown" }, item.score?.takeIf { it > 0 }?.let { "★ %.1f".format(java.util.Locale.US, it) })
                            Text((tags + item.api).joinToString("  ·  "), color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPlay(item) }, shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                contentPadding = PaddingValues(start = 14.dp, end = 18.dp), modifier = Modifier.height(40.dp).testTag("online_hero_play"),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            val isSaved = saved.any { it.api == item.api && it.url == item.url }
                            HeroRoundButton(if (isSaved) Icons.Default.Check else Icons.Default.Add, if (isSaved) "Remove from My List" else "Add to My List") {
                                val now = OnlineBookmarks.toggle(context, item)
                                dev.asfakul.yumeiro.ui.components.AppMessages.show(context, if (now) "Added to My List" else "Removed from My List")
                            }
                            HeroRoundButton(androidx.compose.material.icons.Icons.Outlined.Description, "Details") { onInfo(item) }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (n > 1) Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val cur = pager.currentPage % n
            repeat(n.coerceAtMost(12)) { i ->
                val sel = i == cur
                val w by animateDpAsState(if (sel) 18.dp else 6.dp, label = "dot")
                val c by animateColorAsState(if (sel) p.accent else p.textTertiary.copy(alpha = 0.4f), label = "dotc")
                Box(Modifier.height(5.dp).width(w).clip(CircleShape).background(c))
            }
        }
    }
}

internal val HERO_HEIGHT = 212.dp

/**
 * Round 10: the first banner slide — the title you were watching, with its episode, how far you
 * got and one big Resume button (from the "Warm Cinema" prototype). Tapping anywhere resumes.
 */
@Composable
private fun ResumeSlide(e: OnlineHistory.Entry, modifier: Modifier, offset: Float, onResume: () -> Unit) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black)
            .clickable(onClickLabel = "Resume") { onResume() }
            .testTag("online_hero_resume"),
    ) {
        if (e.posterUrl.isNullOrBlank()) {
            // No artwork from the source: a calm per-title gradient instead of a placeholder icon
            // sitting behind the title text.
            val art = androidx.compose.runtime.remember(e.name) { dev.asfakul.yumeiro.ui.theme.artGradientFor(e.name) }
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(art.start, art.end))))
        } else PosterImage(e.posterUrl, e.posterHeaders, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f; translationX = offset * 60f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 0.45f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.92f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Text(
                (if (dev.asfakul.yumeiro.ui.components.kanaEnabled()) "続き · " else "") + "CONTINUE WATCHING",
                color = p.accentBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(e.name, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val left = if (e.durationMs > 0) ((e.durationMs - e.positionMs).coerceAtLeast(0) / 60_000).toInt() else -1
            Text(
                listOfNotNull(e.episodeLabel, e.type, if (left > 0) "${left} min left" else null, e.api).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onResume, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = dev.asfakul.yumeiro.ui.theme.OnAccent),
                    contentPadding = PaddingValues(start = 14.dp, end = 18.dp), modifier = Modifier.height(40.dp).testTag("online_hero_resume_btn"),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(e.episodeLabel?.let { "Resume ${shortEpisode(it)}" } ?: "Resume", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(e.fraction.coerceAtLeast(0.02f)).height(4.dp).clip(CircleShape).background(p.accent))
                }
            }
        }
    }
}

/** "S1 · E5" / "Episode 5" → "E5"; anything else stays as it is (kept short for the button). */
internal fun shortEpisode(label: String): String {
    Regex("(?i)\\bE(?:p(?:isode)?)?\\.?\\s*(\\d+)").find(label)?.let { return "E" + it.groupValues[1] }
    return label.take(14)
}
private const val HERO_LAPS = 2000

@Composable
private fun HeroRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
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
                Column(Modifier.width(200.dp).clip(RoundedCornerShape(14.dp)).clickable { onClick(e.toItem()) }.padding(bottom = 10.dp)) {
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
