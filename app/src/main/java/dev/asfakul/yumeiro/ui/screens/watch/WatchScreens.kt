/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.watch

import androidx.compose.material.icons.filled.Download

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.shimmer
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.AccentSecondary
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.Unbounded
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.youtube.YtWatch
import dev.asfakul.yumeiro.youtube.formatYtDuration
import dev.asfakul.yumeiro.youtube.formatYtViews

// region shared watch-page building blocks

/**
 * Page shown before the player is ready: a 16:9 "screen" (blurred artwork + status) on top and
 * the details below. Once the stream starts, the real player docks into the same spot, so the
 * page doesn't jump.
 */
@Composable
fun WatchScaffold(
    backdrop: String?,
    backdropHeaders: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    screen: @Composable BoxScope.() -> Unit,
    tag: String = "watch_scaffold",
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(DarkBackground).statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) {}
            .testTag(tag)
    ) {
        // Same app bar as the docked player, so nothing jumps when the stream starts.
        PlayerBrandBar(onMinimize = onBack, onClose = onBack, minimizeLabel = "Back")
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            if (backdrop != null) {
                PosterImage(backdrop, backdropHeaders, Modifier.fillMaxSize().blur(18.dp))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            } else {
                Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))
            }
            screen()
        }
        Column(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

/** Height of [PlayerBrandBar]; the docked player and the loading watch page share it. */
val PlayerBrandBarHeight = 52.dp

/**
 * App bar above the docked player on every watch page: minimize (chevron), the Yumeiro logo and
 * wordmark, and close. Uses the current (app) theme, so it matches Settings → Appearance.
 */
@Composable
fun PlayerBrandBar(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    minimizeLabel: String = "Minimize player",
) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val brush = remember(p.textPrimary, p.accentBright) { Brush.linearGradient(listOf(p.textPrimary, p.accentBright)) }
    Row(
        modifier.fillMaxWidth().height(PlayerBrandBarHeight).padding(horizontal = 4.dp).testTag("player_brand_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimize, modifier = Modifier.testTag("player_brand_minimize")) {
            Icon(Icons.Default.ExpandMore, minimizeLabel, tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(dev.asfakul.yumeiro.R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Yumeiro",
            style = androidx.compose.ui.text.TextStyle(brush = brush, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp),
            maxLines = 1,
            modifier = Modifier.testTag("player_brand_name"),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.testTag("watch_close")) {
            Icon(Icons.Default.Close, "Close player", tint = TextPrimary)
        }
    }
}

/** Spinner + status line centred in the 16:9 screen. */
@Composable
fun BoxScope.WatchScreenStatus(text: String, onCancel: (() -> Unit)? = null, tag: String = "watch_status") {
    Column(Modifier.align(Alignment.Center).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (onCancel != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("watch_cancel"),
            )
        }
    }
}

/** Error in the 16:9 screen. */
@Composable
fun BoxScope.WatchScreenError(message: String, onRetry: () -> Unit, onClose: () -> Unit, tag: String = "watch_error") {
    Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, null, tint = dev.asfakul.yumeiro.ui.theme.WarnTone, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        val tip = remember(message) { dev.asfakul.yumeiro.ui.components.ErrorHelp.suggestionsFor(message).firstOrNull() }
        if (tip != null) Text("Tip: $tip", color = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("error_tip"))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onClose) { Text("Close", color = Color.White) }
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) { Text("Try again") }
        }
    }
}

/** Skeleton of the details area (title, meta, actions, text, list). */
@Composable
fun WatchDetailsSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp).testTag("watch_skeleton"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(width = null, height = 22.dp)
        SkeletonBlock(width = 200.dp, height = 14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { SkeletonBlock(width = 74.dp, height = 30.dp, shape = CircleShape) }
        }
        Spacer(Modifier.height(2.dp))
        SkeletonBlock(width = null, height = 12.dp)
        SkeletonBlock(width = null, height = 12.dp)
        SkeletonBlock(width = 240.dp, height = 12.dp)
        Spacer(Modifier.height(6.dp))
        repeat(rows) { WatchRowSkeleton() }
    }
}

@Composable
fun WatchRowSkeleton() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(width = 148.dp, height = 84.dp, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(width = null, height = 14.dp)
            SkeletonBlock(width = 120.dp, height = 12.dp)
            SkeletonBlock(width = 80.dp, height = 10.dp)
        }
    }
}

@Composable
fun WatchTitle(title: String, overline: String? = null, meta: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
        if (overline != null) {
            Text(overline.uppercase(), color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, color = TextPrimary, fontFamily = dev.asfakul.yumeiro.ui.theme.Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("watch_title"))
        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WatchAction(icon: ImageVector, label: String, tag: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) SpotifyGreen else GlassFill,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (highlighted) OnAccent else TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (highlighted) OnAccent else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun WatchActionsRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Collapsible description card ("…more"). */
@Composable
fun WatchDescription(text: String, header: String? = null) {
    if (text.isBlank() && header.isNullOrBlank()) return
    var open by rememberSaveable(text) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp), color = GlassFill,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable { open = !open }.animateContentSize().testTag("watch_description"),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (!header.isNullOrBlank()) {
                Text(header, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (text.isNotBlank()) Spacer(Modifier.height(4.dp))
            }
            if (text.isNotBlank()) {
                Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Less" else "More", tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun WatchSectionLabel(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}

// endregion

// region YouTube

/** YouTube-style details under the docked player: title, channel, actions, description, Up next. */
@Composable
fun YouTubeWatchPanel(onPlay: (YtItem) -> Unit, modifier: Modifier = Modifier) {
    val st by YtWatch.state.collectAsState()
    val state = st ?: return
    val context = LocalContext.current
    val item = state.item
    val info = state.info
    var ytDownload by remember(item.id) { mutableStateOf(false) }
    if (ytDownload) dev.asfakul.yumeiro.ui.screens.online.YouTubeDownloadSheet(item) { ytDownload = false }
    LazyColumn(modifier.fillMaxSize().testTag("yt_watch_panel"), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "title") {
            val views = (info?.views ?: item.views).takeIf { it >= 0 }?.let { formatYtViews(it) }
            WatchTitle(
                title = info?.title ?: item.title,
                overline = if (item.music) "YouTube Music" else if (item.live) "Live" else "YouTube",
                meta = listOfNotNull(views, info?.uploaded ?: item.uploaded, item.durationSec.takeIf { it > 0 }?.let { formatYtDuration(it) }, info?.category).joinToString(" · "),
            )
        }
        item(key = "channel") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                val avatar = info?.channelAvatar ?: item.channelAvatar
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SpotifyGreen, AccentSecondary))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text((info?.channel ?: item.channel).take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    if (avatar != null) PosterImage(avatar, emptyMap(), Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.channel ?: item.channel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subs = info?.subscribers?.takeIf { it >= 0 }
                    if (subs != null) Text("${formatYtViews(subs).removeSuffix(" views")} subscribers", color = TextTertiary, fontSize = 12.sp, maxLines = 1)
                    else if (state.loading) SkeletonBlock(width = 90.dp, height = 10.dp)
                }
            }
        }
        item(key = "actions") {
            WatchActionsRow {
                val likes = info?.likes?.takeIf { it >= 0 }
                if (likes != null) WatchAction(Icons.Default.ThumbUp, formatYtViews(likes).removeSuffix(" views"), "yt_watch_likes") {}
                if (!item.live) WatchAction(Icons.Default.Download, "Download", "yt_watch_download") { ytDownload = true }
                WatchAction(Icons.Default.Share, "Share", "yt_watch_share") {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.url}")
                    runCatching { context.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                WatchAction(Icons.Default.ContentCopy, "Copy link", "yt_watch_copy") {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("YouTube link", item.url))
                    dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Link copied")
                }
                WatchAction(Icons.Default.OpenInNew, "Open in browser", "yt_watch_open") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        item(key = "desc") {
            if (info == null && state.loading) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(width = null, height = 12.dp); SkeletonBlock(width = null, height = 12.dp); SkeletonBlock(width = 200.dp, height = 12.dp)
                }
            } else if (info != null) {
                WatchDescription(info.description, header = info.tags.takeIf { it.isNotEmpty() }?.joinToString("  ") { "#" + it.replace(" ", "") })
            }
        }
        item(key = "upnext") { WatchSectionLabel("Up next", if (state.related.isNotEmpty()) "${state.related.size} videos" else null) }
        if (state.loading) {
            items(6, key = { "yt_sk_$it" }) { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { WatchRowSkeleton() } }
        } else if (state.related.isEmpty()) {
            item(key = "norelated") { Text("Nothing related found for this video.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(state.related, key = { "rel_" + it.id }) { r -> RelatedRow(r) { onPlay(r) } }
        }
    }
}

@Composable
private fun RelatedRow(item: YtItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp).testTag("yt_watch_related"),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(156.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).border(1.dp, dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f), RoundedCornerShape(12.dp))) {
            PosterImage(item.thumbnail, emptyMap(), Modifier.fillMaxSize(), Icons.Default.SmartDisplay)
            val badge = when { item.live -> "LIVE"; item.durationSec > 0 -> formatYtDuration(item.durationSec); else -> null }
            if (badge != null) Surface(
                shape = RoundedCornerShape(6.dp), color = if (item.live) Color(0xFFE53950) else Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            ) { Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(item.channel, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.views.takeIf { it >= 0 }?.let { formatYtViews(it) }, item.uploaded).joinToString(" · "), color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/**
 * The YouTube watch page while the stream is still resolving (or failed). Hidden as soon as the
 * player opens; the docked player then reuses [YouTubeWatchPanel].
 */
@Composable
fun YouTubeWatchLayer(playerOpen: Boolean, onPlay: (YtItem) -> Unit) {
    val st by YtWatch.state.collectAsState()
    val state = st ?: return
    if (playerOpen) return
    val close = { dev.asfakul.yumeiro.youtube.YouTubePlayback.cancel(); YtWatch.end() }
    WatchScaffold(
        backdrop = state.item.thumbnail,
        onBack = close,
        tag = "yt_watch_loading",
        screen = {
            val err = state.error
            if (err != null) WatchScreenError("YouTube couldn't play this video: $err", onRetry = { onPlay(state.item) }, onClose = close)
            else WatchScreenStatus(if (state.item.live) "Joining the live stream…" else "Loading video…", onCancel = close)
        },
    ) { YouTubeWatchPanel(onPlay = onPlay) }
}

// endregion
