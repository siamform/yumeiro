/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.ui.theme.AccentSecondary
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Unbounded
import dev.asfakul.yumeiro.ui.theme.artGradientFor
import dev.asfakul.yumeiro.ui.theme.rememberGlassBorder
import dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush

/** One entry of the YouTube-style "Mix" feed. */
sealed interface FeedEntry {
    val key: String
    /** A single title as a big card, tagged with its channel (extension). */
    data class Card(val item: OnlineItem, val horizontal: Boolean, override val key: String) : FeedEntry
    /** A row of an extension shown as a playlist shelf. */
    data class Shelf(val row: OnlineRow, override val key: String) : FeedEntry
}

/**
 * Builds the mixed home feed from every channel's rows — the core of the multi-extension
 * "For you" page. Pure and deterministic (unit-tested):
 *  - cards are taken round-robin across channels, then across each channel's rows, so one big
 *    extension never drowns the others (YouTube mixes channels the same way);
 *  - titles are de-duplicated across the whole feed (same title from two rows / channels);
 *  - after every [cardsPerShelf] cards a shelf (one channel row, rotating channels) is inserted;
 *  - keys are unique by construction (position + identity), so Lazy lists never crash on
 *    "Key was already used" when two extensions return the same URL.
 */
fun buildMixFeed(channels: List<Pair<String, List<OnlineRow>>>, maxCards: Int, cardsPerShelf: Int = 4, skip: Set<String> = emptySet()): List<FeedEntry> {
    val usable = channels.map { (name, rows) -> name to rows.filter { it.items.isNotEmpty() } }.filter { it.second.isNotEmpty() }
    if (usable.isEmpty() || maxCards <= 0) return emptyList()
    // Per channel: interleave its rows (row0[0], row1[0], row0[1], ...).
    val streams = usable.map { (_, rows) ->
        val out = ArrayList<Pair<OnlineItem, Boolean>>()
        val longest = rows.maxOf { it.items.size }
        for (i in 0 until longest) rows.forEach { r -> r.items.getOrNull(i)?.let { out += it to r.horizontal } }
        out
    }
    val shelves = ArrayDeque<OnlineRow>().apply {
        val longest = usable.maxOf { it.second.size }
        for (i in 0 until longest) usable.forEach { (_, rows) -> rows.getOrNull(i)?.let { add(it) } }
    }
    val seen = HashSet<String>(skip)
    val cursors = IntArray(streams.size)
    val feed = ArrayList<FeedEntry>()
    var cards = 0
    var sinceShelf = 0
    var progressed = true
    while (cards < maxCards && progressed) {
        progressed = false
        for (c in streams.indices) {
            if (cards >= maxCards) break
            val s = streams[c]
            while (cursors[c] < s.size) {
                val (item, horizontal) = s[cursors[c]++]
                if (!seen.add(item.key)) continue
                feed += FeedEntry.Card(item, horizontal, "card_${feed.size}_${item.key}")
                cards++; sinceShelf++; progressed = true
                break
            }
            if (sinceShelf >= cardsPerShelf && shelves.isNotEmpty()) {
                val row = shelves.removeFirst()
                feed += FeedEntry.Shelf(row, "shelf_${feed.size}_${row.api}_${row.title}")
                sinceShelf = 0
            }
        }
    }
    return feed
}

/** Circular channel avatar: extension initial over its own gradient, with a glowing ring when selected. */
@Composable
internal fun ChannelAvatar(name: String, selected: Boolean, size: androidx.compose.ui.unit.Dp = 58.dp, mix: Boolean = false) {
    val signature = rememberSignatureBrush()
    val art = remember(name) { artGradientFor(name) }
    val ring = if (selected) signature else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
    Box(Modifier.size(size).clip(CircleShape).background(ring).padding(if (selected) 2.5.dp else 1.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(if (mix) signature else Brush.linearGradient(listOf(art.start, art.end))),
            contentAlignment = Alignment.Center,
        ) {
            if (mix) Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
            else {
                // Fallback (and what shows while the logo loads): the Hue's initials.
                Text(
                    name.trim().take(2).uppercase().ifBlank { "?" }, color = Color.White, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope,
                    fontWeight = FontWeight.Bold, fontSize = (size.value * 0.30f).sp, maxLines = 1,
                )
                HueLogo(name, Modifier.fillMaxSize())
            }
        }
    }
}

/** "Stories"-like row of channels: Mix first, then one avatar per extension. */
@Composable
internal fun ChannelStrip(channels: List<String>, loading: Set<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.testTag("online_channels"),
    ) {
        item("mix") { ChannelChipItem("All", selected == null, mix = true, loading = false) { onSelect(null) } }
        itemsIndexed(channels, key = { i, n -> "ch_${i}_$n" }) { _, name ->
            ChannelChipItem(name, selected == name, mix = false, loading = name in loading) { onSelect(if (selected == name) null else name) }
        }
    }
}

@Composable
private fun ChannelChipItem(name: String, selected: Boolean, mix: Boolean, loading: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "ch").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).testTag("online_channel"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.alpha(if (loading) pulse else 1f)) { ChannelAvatar(name, selected, mix = mix) }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = if (selected) TextPrimary else TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Big YouTube-style feed card. Landscape art fills the 16:9 frame; portrait posters sit on a
 * blurred, tinted copy of themselves so they never look cropped.
 */
@Composable
internal fun FeedCard(item: OnlineItem, horizontal: Boolean, onClick: () -> Unit, onChannel: () -> Unit) {
    val border = rememberGlassBorder()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_feed_card")) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(22.dp))
                .border(1.dp, border, RoundedCornerShape(22.dp)).background(GlassFill).clickable(onClick = onClick),
        ) {
            if (horizontal) {
                PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
            } else {
                PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize().blur(28.dp).alpha(0.75f))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.65f)))))
                Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                        PosterImage(item.posterUrl, item.posterHeaders, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        item.type?.takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
                        Text(item.name, color = Color.White, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.score?.takeIf { it > 0 }?.let { s -> Badge("★ %.1f".format(java.util.Locale.US, s)) }
                item.quality?.takeIf { it.isNotBlank() && it != "Unknown" }?.let { Badge(it) }
            }
            Box(
                Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.clip(CircleShape).clickable(onClick = onChannel)) { ChannelAvatar(item.api, selected = false, size = 36.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.api, item.type?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onChannel),
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1)
    }
}

/** Header of a single channel (extension) page: avatar, name, stats, search shortcut. */
@Composable
internal fun ChannelHeader(name: String, rows: Int, titles: Int, onSearch: () -> Unit) {
    val art = remember(name) { artGradientFor(name) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(art.start.copy(alpha = 0.55f), art.end.copy(alpha = 0.25f), Color.Transparent)))
        .testTag("online_channel_header")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(name, selected = true, size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Extension · $rows playlist${if (rows == 1) "" else "s"} · $titles titles", maxLines = 1, color = TextSecondary, fontSize = 12.sp)
            }
            Surface(shape = CircleShape, color = SpotifyGreen.copy(alpha = 0.18f), modifier = Modifier.size(42.dp).clickable(onClick = onSearch)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, "Search", tint = SpotifyGreen) }
            }
        }
    }
}

/** Small section label used between feed blocks ("Mixed for you"). */
@Composable
internal fun FeedLabel(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(SpotifyGreen, AccentSecondary))))
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Star, null, tint = SpotifyGreen, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Logo candidates for a Hue, best first: the extension's own icon from its repository, then the
 * site's favicon (from the provider's main URL). Empty → initials only.
 */
internal fun hueLogoCandidates(iconUrl: String?, mainUrl: String?): List<String> {
    val out = ArrayList<String>(3)
    iconUrl?.trim()?.takeIf { it.startsWith("http") }?.let { out += it.replace("%size%", "128") }
    val host = mainUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }?.takeIf { it.isNotBlank() }
    if (host != null) {
        out += "https://www.google.com/s2/favicons?domain=$host&sz=128"
        out += "https://$host/favicon.ico"
    }
    return out.distinct()
}

/** The extension icon / site favicon over the initials. Tries each candidate, then gives up quietly. */
@Composable
internal fun HueLogo(name: String, modifier: Modifier = Modifier) {
    val engine by dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState()
    val candidates = remember(name, engine.generation, engine.installed.size) {
        val ext = engine.installed.firstOrNull { name in engine.info(it.key).providers }
        val main = runCatching { dev.asfakul.yumeiro.online.ExtensionManager.provider(name)?.mainUrl }.getOrNull()
        hueLogoCandidates(ext?.iconUrl, main)
    }
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    var loaded by remember(candidates) { mutableStateOf(false) }
    val url = candidates.getOrNull(attempt) ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = remember(url) { coil.request.ImageRequest.Builder(context).data(url).crossfade(true).build() }
    coil.compose.AsyncImage(
        model = request,
        contentDescription = "$name logo",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.clip(CircleShape).then(if (loaded) Modifier.background(Color(0xFF15101F)) else Modifier),
        onSuccess = { st ->
            // Google returns a tiny generic globe for unknown sites: treat < 32px as "no logo".
            val d = st.result.drawable
            if (d.intrinsicWidth in 1..31) { loaded = false; attempt++ } else loaded = true
        },
        onError = { loaded = false; attempt++ },
    )
}
