/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.shimmer
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Poster image with request headers (some sources need a Referer), shimmer while loading and a fallback icon. */
@Composable
internal fun PosterImage(url: String?, headers: Map<String, String>, modifier: Modifier = Modifier, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Movie) {
    val context = LocalContext.current
    val request = remember(url, headers) {
        ImageRequest.Builder(context).data(url?.takeIf { it.isNotBlank() }).apply {
            headers.forEach { (k, v) -> runCatching { addHeader(k, v) } }
        }.crossfade(true).build()
    }
    SubcomposeAsyncImage(
        model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp))) },
        error = {
            Box(Modifier.fillMaxSize().background(GlassFill), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, null, tint = TextTertiary, modifier = Modifier.size(28.dp))
            }
        },
    )
}

/**
 * Release tags over a poster: kind (Movie / Series / Anime) and release source top-left, audio
 * (Bengali, Hindi, Dual, Multi, Dub) bottom-left. Small translucent glass chips so the art stays
 * the hero; theatre recordings (CAM / TS) get an amber tint as a quality hint.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.PosterTags(item: OnlineItem, compact: Boolean = false, max: Int = 3) {
    val tags = item.tags
    if (tags.isEmpty()) return
    val type = tags.firstOrNull { it.kind == dev.asfakul.yumeiro.online.ReleaseTags.Kind.TYPE }
    val release = tags.filter { it.kind == dev.asfakul.yumeiro.online.ReleaseTags.Kind.SOURCE || it.kind == dev.asfakul.yumeiro.online.ReleaseTags.Kind.RESOLUTION }
        .take(if (compact) 1 else 2)
    val audio = tags.firstOrNull { it.kind == dev.asfakul.yumeiro.online.ReleaseTags.Kind.LANGUAGE }
    val top = (listOfNotNull(type) + release).take(max)
    val pad = if (compact) 5.dp else 8.dp
    // Round 8: the chips share the width that's left next to the score badge and ellipsize, so
    // long release labels never run under the score or off the poster.
    val scoreRoom = if ((item.score ?: 0.0) > 0) 34.dp else 0.dp
    if (top.isNotEmpty()) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = pad, top = pad, end = pad + scoreRoom).testTag("poster_tags"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        top.forEach { Box(Modifier.weight(1f, fill = false)) { TagChip(it, compact, strong = it.kind == dev.asfakul.yumeiro.online.ReleaseTags.Kind.TYPE) } }
    }
    if (audio != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(pad), contentAlignment = Alignment.BottomStart) { TagChip(audio, compact, strong = false) }
}

@Composable
private fun TagChip(tag: dev.asfakul.yumeiro.online.ReleaseTags.Tag, compact: Boolean, strong: Boolean) {
    val fill = when {
        tag.low -> Color(0xFFB4690E).copy(alpha = 0.88f)
        strong -> SpotifyGreen.copy(alpha = 0.92f)
        else -> Color(0xFF0E0B14).copy(alpha = 0.62f)
    }
    val fg = if (strong && !tag.low) OnAccent else Color.White
    Text(
        tag.label, color = fg, fontSize = if (compact) 8.5.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        letterSpacing = 0.2.sp, lineHeight = if (compact) 10.sp else 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(fill)
            .border(0.5.dp, Color.White.copy(alpha = if (strong) 0f else 0.16f), RoundedCornerShape(5.dp))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 1.dp else 2.dp),
    )
}

@Composable
internal fun PosterCard(item: OnlineItem, onClick: () -> Unit, width: Dp = 116.dp, horizontal: Boolean = false, showSource: Boolean = false, modifier: Modifier? = null, progress: Float? = null) {
    val w = if (horizontal) width * 1.6f else width
    Column((modifier ?: Modifier.width(w)).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("online_poster").padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (horizontal) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(14.dp))) {
            PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
            PosterTags(item, compact = true)
            item.score?.takeIf { it > 0 }?.let { s ->
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(10.dp))
                    Text(String.format(java.util.Locale.US, "%.1f", s), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(SpotifyGreen))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Round 8: the title always takes exactly two lines (minLines = maxLines = 2) and the meta
        // line is always there, so every card in a shelf / grid row has the same height. Before, a
        // long title made its card taller than its neighbours and the whole shelf jumped while
        // scrolling sideways.
        Text(item.name.replace('\n', ' '), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        val sub = listOfNotNull(item.type?.takeIf { it.isNotBlank() }, if (showSource) item.api else null).joinToString(" · ")
        Text(sub.ifBlank { " " }, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PosterRow(items: List<OnlineItem>, onClick: (OnlineItem) -> Unit, horizontal: Boolean = false, showSource: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("online_row")) {
        itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onClick(it) }, horizontal = horizontal, showSource = showSource) }
    }
}

/**
 * Fixed grid of posters (no horizontal scrolling) for use inside a LazyColumn item.
 * Shows at most [rows] rows; the rest is reached through "See all".
 */
@Composable
internal fun PosterGrid(
    items: List<OnlineItem>,
    onClick: (OnlineItem) -> Unit,
    columns: Int = gridColumns(),
    rows: Int = 2,
    horizontal: Boolean = false,
    showSource: Boolean = false,
    progress: (OnlineItem) -> Float? = { null },
    padding: Dp = 16.dp,
) {
    val cols = if (horizontal) (columns - 1).coerceAtLeast(2) else columns
    val shown = if (rows > 0) items.take(cols * rows) else items
    Column(Modifier.fillMaxWidth().padding(horizontal = padding).testTag("online_grid"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.chunked(cols).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        PosterCard(item, onClick = { onClick(item) }, horizontal = horizontal, showSource = showSource, modifier = Modifier.fillMaxWidth(), progress = progress(item))
                    }
                }
                repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 3 columns on phones, more on wide screens. */
@Composable
internal fun gridColumns(): Int {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return (w / 128).coerceIn(3, 8)
}

@Composable
internal fun PosterGridSkeleton(rows: Int = 2) {
    val cols = gridColumns()
    Column(Modifier.fillMaxWidth().testTag("online_row_skeleton")) {
        SkeletonBlock(140.dp, 14.dp, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(cols) {
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shimmer(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.height(6.dp))
                            SkeletonBlock(60.dp, 10.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeeAllButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag("online_see_all")) {
        Text("See all", color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun PosterRowSkeleton(count: Int = 6) {
    Column(Modifier.fillMaxWidth().testTag("online_row_skeleton")) {
        SkeletonBlock(140.dp, 14.dp, Modifier.padding(start = 16.dp, bottom = 10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) {
                Column(Modifier.width(116.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).shimmer(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(90.dp, 10.dp)
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

@Composable
internal fun OnlineChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = modifier,
        label = { Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = GlassFill, labelColor = TextSecondary,
            selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent,
        ),
        border = null,
    )
}

/** Extension icon (from its repository) or a puzzle-piece fallback. */
@Composable
internal fun ExtensionIcon(url: String?, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(GlassFill), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) Icon(Icons.Default.Extension, null, tint = SpotifyGreen, modifier = Modifier.size(size * 0.5f))
        else PosterImage(url.replace("%size%", "128"), emptyMap(), Modifier.size(size * 0.7f).clip(RoundedCornerShape(8.dp)), Icons.Default.Extension)
    }
}

@Composable
internal fun Pill(text: String, color: Color = SpotifyGreen) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), maxLines = 1)
    }
}

internal val WarnColor: Color @Composable @ReadOnlyComposable get() = dev.asfakul.yumeiro.ui.theme.WarnTone
internal val ErrorColor: Color @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable get() = dev.asfakul.yumeiro.ui.theme.DangerTone
