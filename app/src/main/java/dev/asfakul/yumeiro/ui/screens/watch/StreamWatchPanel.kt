/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.watch

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import dev.asfakul.yumeiro.online.AccessLadder
import dev.asfakul.yumeiro.stream.toVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Channel list helpers (pure, unit tested). */
object StreamWatch {
    /** Channels of the open playlist with the current one's group first. */
    fun ordered(channels: List<StreamRequest>, currentUrl: String): List<StreamRequest> {
        val group = channels.firstOrNull { it.url == currentUrl }?.group
        return channels.sortedBy { if (group != null && it.group == group) 0 else 1 }
    }

    fun neighbour(channels: List<StreamRequest>, currentUrl: String, step: Int): StreamRequest? {
        val i = channels.indexOfFirst { it.url == currentUrl }
        if (i < 0 || channels.size < 2) return null
        return channels[(i + step).mod(channels.size)]
    }

    private val QUALITY = Regex("""\b(uhd|fhd|hd|sd|hevc|h265|4k|8k|\d{3,4}p|backup|alt|server ?\d+|link ?\d+)\b""")

    /** Channel name without quality / backup tags, so "Sony HD" and "Sony (Backup)" match. */
    fun nameKey(title: String): String = title.lowercase()
        .replace(Regex("""[\[(].*?[\])]"""), " ")
        .replace(QUALITY, " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

    /** Other links of the same channel in the playlist (the "servers" of a channel), current included. */
    fun alternatives(channels: List<StreamRequest>, currentUrl: String): List<StreamRequest> {
        val cur = channels.firstOrNull { it.url == currentUrl } ?: return emptyList()
        val key = nameKey(cur.displayTitle).ifBlank { return listOf(cur) }
        return channels.filter { nameKey(it.displayTitle) == key }.distinctBy { it.url }
    }

    /** Group + text filter for the channel list. */
    fun filter(channels: List<StreamRequest>, group: String?, query: String): List<IndexedValue<StreamRequest>> {
        val q = query.trim().lowercase()
        return channels.withIndex().filter { (_, c) ->
            (group == null || c.group == group) && (q.isEmpty() || q in c.displayTitle.lowercase() || q in (c.group ?: "").lowercase())
        }
    }

    /** Header profiles the user can pick by hand ("servers" for one link). Null step = Auto. */
    val MODES: List<Pair<String, AccessLadder.Step?>> = listOf(
        "Auto" to null,
        "Browser" to AccessLadder.Step.BROWSER,
        "Web player" to AccessLadder.Step.SELF_BROWSER,
        "VLC" to AccessLadder.Step.VLC_UA,
        "Media player" to AccessLadder.Step.PLAYER_UA,
        "No referer" to AccessLadder.Step.NO_REFERER,
    )

    private val _mode = MutableStateFlow<Map<String, AccessLadder.Step>>(emptyMap())
    /** Header profile picked by hand per link. */
    val mode: StateFlow<Map<String, AccessLadder.Step>> = _mode.asStateFlow()

    /** Opens [base] with a hand-picked header profile ([step] null = Auto). */
    fun openWithMode(context: android.content.Context, base: StreamRequest, step: AccessLadder.Step?) {
        val variant = step?.let { AccessLadder.variant(base, it, base.pageUrl) } ?: base
        _mode.value = if (step == null) _mode.value - base.url else _mode.value + (base.url to step)
        AccessLadder.reset(base.url)
        val id = base.toVideoItem().id + (step?.let { "~" + it.name } ?: "")
        IncomingMedia.open(context, variant, remember = step == null, id = id, folder = base.group)
    }

    fun play(context: android.content.Context, r: StreamRequest) {
        _mode.value = _mode.value - r.url
        IncomingMedia.open(context, r, folder = r.group)
    }

    // ---- Auto-zap for live channels: a channel that can't be opened hands over to the next. ----
    private val switches = ArrayDeque<Long>()
    const val MAX_AUTO_SWITCHES = 5
    const val AUTO_SWITCH_WINDOW_MS = 60_000L

    /** Whether another automatic switch is allowed (at most 5 a minute, so a dead list can't loop). */
    @Synchronized
    fun allowAutoSwitch(now: Long): Boolean {
        while (switches.isNotEmpty() && now - switches.first() > AUTO_SWITCH_WINDOW_MS) switches.removeFirst()
        if (switches.size >= MAX_AUTO_SWITCHES) return false
        switches.addLast(now)
        return true
    }

    @Synchronized
    internal fun resetAutoSwitch() = switches.clear()

    /** Called when [failedUrl] couldn't be played: opens the next channel when Auto-zap is on. */
    fun autoSwitch(context: android.content.Context, failedUrl: String): Boolean {
        WatchPrefs.init(context)
        if (!WatchPrefs.autoNext.value) return false
        val list = StreamRepository.playlist.value
        val next = neighbour(list, failedUrl, 1) ?: return false
        if (!allowAutoSwitch(System.currentTimeMillis())) return false
        dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "This channel isn't available — playing ${next.displayTitle}")
        play(context, next)
        return true
    }
}

/**
 * Yumeiro watch page for the Stream tab: moon-dial toggles (Dim, Autoplay, Auto-zap, previous /
 * next), the source card (other links of the same channel + open-as modes), quick actions, then
 * the channel list with search, group capsules and numbered rows.
 */
@Composable
fun StreamWatchPanel(currentUrl: String, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val channels by StreamRepository.playlist.collectAsState()
    val playlistName by StreamRepository.playlistName.collectAsState()
    val modes by StreamWatch.mode.collectAsState()
    val base = remember(currentUrl, channels) { channels.firstOrNull { it.url == currentUrl } ?: StreamRepository.requestFor(currentUrl) }
    val groups = remember(channels) { channels.mapNotNull { it.group?.takeIf(String::isNotBlank) }.distinct() }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val inPlaylist = channels.any { it.url == currentUrl }
    val list = remember(channels, group, query) { StreamWatch.filter(channels, group, query).take(600) }
    val alternatives = remember(channels, currentUrl) { StreamWatch.alternatives(channels, currentUrl) }
    val p = LocalMtPalette.current
    val listState = rememberLazyListState()
    val learned = remember(currentUrl) { AccessLadder.learned(currentUrl) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("stream_watch_panel"), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "controls") {
                WatchControlBar(
                    onPrevious = if (inPlaylist && channels.size > 1) ({ StreamWatch.neighbour(channels, currentUrl, -1)?.let { StreamWatch.play(context, it) } }) else null,
                    onNext = if (inPlaylist && channels.size > 1) ({ StreamWatch.neighbour(channels, currentUrl, 1)?.let { StreamWatch.play(context, it) } }) else null,
                    autoNextLabel = "Auto-zap",
                )
            }
            item(key = "title") {
                val format = base?.resolvedFormat()?.label
                WatchTitle(
                    title = title,
                    overline = listOfNotNull("Live", base?.group).joinToString(" · "),
                    meta = listOfNotNull(format, playlistName?.takeIf { inPlaylist }?.let { "from $it" }).joinToString(" · ").ifBlank { null },
                )
            }
            if (base != null) item(key = "servers") {
                val current = modes[currentUrl]
                val rows = buildList {
                    if (alternatives.size > 1) add(
                        WatchServerRow("Sources for this channel", Icons.Default.LiveTv, alternatives.mapIndexed { i, alt ->
                            WatchServer("Source ${i + 1}", alt.url == currentUrl, "stream_server_link") { StreamWatch.play(context, alt) }
                        })
                    )
                    add(
                        WatchServerRow("Open it as", Icons.Default.Tune, StreamWatch.MODES.mapNotNull { (label, step) ->
                            if (step != null && AccessLadder.variant(base, step, base.pageUrl).let { it == null || it == base }) return@mapNotNull null
                            WatchServer(label, current == step, "stream_server_mode") { StreamWatch.openWithMode(context, base, step) }
                        })
                    )
                }
                WatchServerBox(
                    watching = title,
                    hint = buildString {
                        append("Stuck on black? Tap another source or open-as mode. Yumeiro already tries each one on its own and remembers what works.")
                        if (learned != null) append(" Last good mode here: ${StreamWatch.MODES.firstOrNull { it.second == learned }?.first ?: learned.name.lowercase()}.")
                    },
                    rows = rows,
                )
            }
            item(key = "actions") {
                WatchActionsRow {
                    WatchAction(Icons.Default.Share, "Share", "stream_watch_share") {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$currentUrl")
                        runCatching { context.startActivity(Intent.createChooser(send, "Share stream").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    WatchAction(Icons.Default.ContentCopy, "Copy link", "stream_watch_copy") {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("Stream link", currentUrl))
                        dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Link copied")
                    }
                    WatchAction(Icons.Default.OpenInNew, "Other player", "stream_watch_external") {
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(currentUrl), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            if (channels.isNotEmpty()) {
                item(key = "label") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Channels", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                            Text("Up next on this list", color = dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${channels.size}", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item(key = "search") {
                    WatchSearchField(query, { query = it }, "Find a channel", "stream_watch_search", Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                }
                if (groups.size > 1) item(key = "groups") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                        item { GroupChip("All", group == null) { group = null } }
                        items(groups.take(80), key = { it }) { g -> GroupChip(g, group == g) { group = g } }
                    }
                }
                if (list.isEmpty()) item(key = "none") {
                    Text("No channel matches \"$query\".", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                items(list.size, key = { "ch_" + list[it].index + list[it].value.url }) { k ->
                    val (i, ch) = list[k]
                    ChannelRow(i + 1, ch, playing = ch.url == currentUrl, striped = k % 2 == 1) { StreamWatch.play(context, ch) }
                }
            } else {
                item(key = "empty") {
                    Text(
                        "Open an M3U playlist in Videos → Live TV to switch channels from here.",
                        color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        WatchLightsOverlay()
    }
    // Bring the playing channel into view when the page opens.
    androidx.compose.runtime.LaunchedEffect(currentUrl) {
        val k = list.indexOfFirst { it.value.url == currentUrl }
        val fixed = 4 + (if (base != null) 1 else 0) + (if (groups.size > 1) 1 else 0)
        if (k > 3) listState.scrollToItem(fixed + k - 2)
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Text(
        label, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (selected) p.onAccent else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(if (selected) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp).testTag("stream_watch_group"),
    )
}

/** Channel row: number, logo, name; the playing one gets an accent bar and play icon. */
@Composable
private fun ChannelRow(number: Int, ch: StreamRequest, playing: Boolean, striped: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (playing) p.accent.copy(alpha = 0.14f) else if (striped) dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.025f) else Color.Transparent)
            .clickable(enabled = !playing, onClick = onClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .testTag("stream_watch_channel"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (playing) p.accent else Color.Transparent))
        Text(
            "$number", color = if (playing) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp).padding(start = 10.dp),
        )
        Box(
            Modifier.padding(vertical = 7.dp).size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (ch.logoUrl != null) PosterImage(ch.logoUrl, emptyMap(), Modifier.fillMaxSize().padding(4.dp), Icons.Default.LiveTv)
            else Icon(Icons.Default.LiveTv, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(ch.displayTitle, color = if (playing) p.accent else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(if (playing) "Now playing" else null, ch.group).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Icon(Icons.Default.PlayArrow, "Now playing", tint = p.accent, modifier = Modifier.padding(end = 14.dp).size(20.dp))
        else Spacer(Modifier.width(14.dp))
    }
}
