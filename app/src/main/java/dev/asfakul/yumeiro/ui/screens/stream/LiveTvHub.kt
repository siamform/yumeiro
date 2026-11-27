/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.ui.feed.feedCard
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

/*
 * Round 8 — Live TV home.
 *
 * Before: the page opened on a URL form and a list of playlist *cards*; the channels of the
 * default playlist were two taps away (open the card → its own page). Now Live TV starts with the
 * channels themselves as a logo grid.
 *
 * NBUG-15 (ADR-028): the feed is one list merged from every enabled playlist and grouped by category
 * (stream/LiveChannelFeed); a Filter button ("Show videos from") picks a playlist; every tool sits in
 * one Tools modal, and playlists are managed on their own page (LiveTvChrome.kt).
 */

/** Channels of one playlist as the hub sees them. */
@Immutable
internal data class LiveLoad(val loading: Boolean = true, val channels: List<StreamRequest> = emptyList(), val error: String? = null)

/** R15: Live TV channel lists for the process (see StreamScreen.loadLive and ui/feed/LiveFeed.kt). */
internal object LiveTvMemory {
    val loads = androidx.compose.runtime.mutableStateMapOf<String, LiveLoad>()
    val stamps = HashMap<String, Long>()
    val gen = HashMap<String, Int>()
}

/** One shortcut tile in the Live TV "Tools" grid. */
@Immutable
internal data class LiveTool(val tag: String, val title: String, val status: String, val icon: ImageVector, val hue: Color, val onClick: () -> Unit)

/** Stable per-playlist colour for its avatar. */
internal fun liveHue(name: String): Color {
    val hues = listOf(0xFFD9668A, 0xFFD9553F, 0xFF8F6CC9, 0xFFE0A030, 0xFF4FA87A, 0xFF4C8FD6, 0xFF5866C2, 0xFF3E9E94)
    return Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
}

/** One channel: logo on a glass tile (logos are fitted, never cropped), a live dot and the name. */
@Composable
internal fun LiveChannelTile(ch: StreamRequest, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = "live_channel") {
    Column(
        modifier.clip(yuRound(16)).clickable(onClickLabel = "Play ${ch.displayTitle}", onClick = onClick)
            .padding(bottom = 6.dp).testTag(tag).feedCard("live", ch.url),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(yuRound(16)).background(GlassFill)
                .border(1.dp, Color.White.copy(alpha = 0.06f), yuRound(16)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ch.displayTitle.trim().take(2).uppercase(), color = TextTertiary, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1,
            )
            if (!ch.logoUrl.isNullOrBlank()) AsyncImage(
                model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(yuRound(6)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                Spacer(Modifier.width(3.dp))
                Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, lineHeight = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(ch.displayTitle, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(ch.group?.takeIf { it.isNotBlank() } ?: " ", color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

/**
 * NB15D-2 (ADR-027): "Recently played" row on the Live TV feed — channels played from Live TV lists,
 * newest first, at most [MAX_RECENT_ROW]. Play Link streams never appear here.
 */
@Composable
internal fun LiveRecentRow(recent: List<StreamRequest>, onPlay: (StreamRequest) -> Unit, onClear: () -> Unit) {
    val pal = LocalMtPalette.current
    Column(Modifier.fillMaxWidth().testTag("live_recent_row")) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recently played", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
                Text("${recent.size.coerceAtMost(MAX_RECENT_ROW)} channels", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                "Clear", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(yuRound(10)).clickable(role = Role.Button, onClickLabel = "Clear recently played", onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 6.dp).testTag("live_recent_clear"),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(recent.take(MAX_RECENT_ROW), key = { "recent_" + it.url }) { ch ->
                LiveChannelTile(ch, onClick = { onPlay(ch) }, modifier = Modifier.width(128.dp), tag = "live_recent_channel")
            }
        }
    }
}

internal const val MAX_RECENT_ROW = 20

@Composable
internal fun LiveTileSkeleton(modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(yuRound(16)).background(GlassFill))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(yuRound(4)).background(GlassFill))
    }
}

@Composable
internal fun LiveProblem(message: String, onRetry: () -> Unit) {
    val pal = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(yuRound(16)).background(GlassFill).padding(horizontal = 14.dp, vertical = 10.dp).testTag("live_problem"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Retry", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** The organised Live TV tools: a 4-column grid of small seals with a name and a status. */
@Composable
internal fun LiveToolsGrid(tools: List<LiveTool>, columns: Int = 4) {
    Column(Modifier.fillMaxWidth().testTag("live_tools"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).clip(yuRound(16))
                            .clickable(role = Role.Button, onClickLabel = t.title, onClick = t.onClick)
                            .semantics(mergeDescendants = true) { contentDescription = "${t.title}, ${t.status}" }
                            .padding(vertical = 4.dp).testTag(t.tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(50.dp).clip(yuRound(17)).background(Brush.linearGradient(listOf(t.hue, t.hue.copy(alpha = 0.72f)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text(t.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(t.status, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** LIVE-1: the countries picked lately on Live TV, newest first (at most 5). Call off the main thread. */
internal object LiveCountryRecent {
    private const val PREFS = "live_tv"
    private const val KEY = "country_recent"
    private const val MAX = 5

    fun read(ctx: android.content.Context): List<String> =
        ctx.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(ctx, PREFS), android.content.Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(ctx: android.content.Context, key: String) {
        val next = (listOf(key) + read(ctx).filterNot { it.equals(key, ignoreCase = true) }).take(MAX)
        ctx.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(ctx, PREFS), android.content.Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString("\n")).apply()
    }
}
