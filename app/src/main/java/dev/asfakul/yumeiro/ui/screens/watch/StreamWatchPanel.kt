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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_STREAMWATCHPANEL_HOT_1 = Regex("""[\[(].*?[\])]""")
private val RX_STREAMWATCHPANEL_HOT_2 = Regex("""[^\p{L}\p{N}]+""")

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
        .replace(RX_STREAMWATCHPANEL_HOT_1, " ")
        .replace(QUALITY, " ")
        .replace(RX_STREAMWATCHPANEL_HOT_2, " ")
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
        IncomingMedia.open(context, variant, recent = if (step == null) IncomingMedia.Recent.KEEP else IncomingMedia.Recent.NONE, id = id, folder = base.group)
    }

    fun play(context: android.content.Context, r: StreamRequest) {
        _mode.value = _mode.value - r.url
        IncomingMedia.open(context, r, recent = IncomingMedia.Recent.LIVE, folder = r.group)
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
    internal fun resetAutoSwitch() { switches.clear(); failKey = null; failTried.clear() }

    /** F-14: how long the links already tried for one channel are remembered. */
    const val FAILOVER_WINDOW_MS = 2L * 60 * 1000
    private var failKey: String? = null
    private var failAt = 0L
    private val failTried = HashSet<String>()

    /**
     * F-14 (ADR-040): the next link of the same channel after [failedUrl] dies, each link once per round
     * (Kodi PVR and TiviMate-style players fail over to backup URLs the same way). Null when every link of
     * the channel has failed in the last [FAILOVER_WINDOW_MS], or the channel has only one link.
     */
    @Synchronized
    fun failoverSource(channels: List<StreamRequest>, failedUrl: String, now: Long): StreamRequest? {
        val alts = alternatives(channels, failedUrl)
        if (alts.size < 2) return null
        val key = nameKey(alts.first { it.url == failedUrl }.displayTitle)
        if (key != failKey || now - failAt > FAILOVER_WINDOW_MS) { failKey = key; failTried.clear() }
        failAt = now
        failTried += failedUrl
        val from = alts.indexOfFirst { it.url == failedUrl }
        val next = (1 until alts.size).map { alts[(from + it) % alts.size] }.firstOrNull { it.url !in failTried } ?: return null
        failTried += next.url
        return next
    }

    /** F-14: plays the channel's next link; false when there is none (then Auto-zap or the error card). */
    fun failover(context: android.content.Context, failedUrl: String): Boolean {
        if (!dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.LIVE_FAILOVER)) return false
        val list = StreamRepository.playlist.value
        val next = failoverSource(list, failedUrl, System.currentTimeMillis()) ?: return false
        val n = alternatives(list, failedUrl).indexOfFirst { it.url == next.url } + 1
        dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "This source isn't working — trying source $n")
        play(context, next)
        return true
    }

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
 * G8-TV7A: Download for a link / website video when the watch page is not docked (TV, landscape phone,
 * fullscreen). Same action and checks as the watch page's Download; shown with the player controls.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.StreamPlayerOverlay(controlsVisible: Boolean, currentUrl: String) {
    val context = LocalContext.current
    val channels by StreamRepository.playlist.collectAsState()
    val base = remember(currentUrl, channels) { channels.firstOrNull { it.url == currentUrl } ?: StreamRepository.requestFor(currentUrl) }
    if (base == null || !dev.asfakul.yumeiro.stream.StreamDownload.canSave(base)) return
    val docked = dev.asfakul.yumeiro.ui.screens.video.LocalPlayerDocked.current
    androidx.compose.animation.AnimatedVisibility(
        visible = controlsVisible && !docked, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(
            Modifier.statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp).testTag("stream_player_bar"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            dev.asfakul.yumeiro.ui.screens.online.PlayerChip(Icons.Default.Download, "Download", "stream_watch_download") {
                // OL-4: the same Offline / Permanent popup as every other download.
                dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk.ask(context, base, base.pageUrl ?: currentUrl)
            }
        }
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
    val playlistId by StreamRepository.playlistId.collectAsState()
    val guide by androidx.compose.runtime.produceState<dev.asfakul.yumeiro.stream.EpgGuide.Guide?>(null, playlistId, inPlaylist) {
        if (!inPlaylist || !dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.LIVE_GUIDE)) return@produceState
        val id = playlistId ?: return@produceState
        val url = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.stream.PlaylistStore.epgUrl(context, id) } ?: return@produceState
        value = dev.asfakul.yumeiro.stream.EpgGuide.guide(context, url)
    }
    val minute by androidx.compose.runtime.produceState(System.currentTimeMillis() / 60_000L) {
        while (true) { kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L); value = System.currentTimeMillis() / 60_000L }
    }

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
                PlaybackFactsRow()
                // F-15: what is on now and next, from the playlist's TV guide (url-tvg).
                val (on, next) = remember(guide, base, minute) { base?.let { guide?.nowNext(it, minute * 60_000L) } ?: (null to null) }
                if (on != null || next != null) {
                    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
                    Text(
                        listOfNotNull(on?.let { "Now: ${it.title} (until ${fmt.format(java.util.Date(it.stop))})" }, next?.let { "Next ${fmt.format(java.util.Date(it.start))}: ${it.title}" }).joinToString("  ·  "),
                        color = TextTertiary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("stream_watch_guide"),
                    )
                }
            }
            if (base != null) item(key = "servers") {
                val current = modes[currentUrl]
                val rows = buildList {
                    if (alternatives.size > 1) add(
                        WatchServerRow("Sources for this channel", Icons.Default.LiveTv, alternatives.mapIndexed { i, alt ->
                            WatchServer("Source ${i + 1}", alt.url == currentUrl, "stream_server_link",
                                detail = listOfNotNull(alt.resolvedFormat().label, runCatching { android.net.Uri.parse(alt.url).host }.getOrNull()?.removePrefix("www.")).joinToString(" · ")) { StreamWatch.play(context, alt) }
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
                    // BUG-7 7a: links and website videos can be saved from the player, as on the Play link page.
                    if (base != null && dev.asfakul.yumeiro.stream.StreamDownload.canSave(base)) {
                        WatchAction(Icons.Default.Download, "Download", "stream_watch_download") {
                            // OL-4: the same Offline / Permanent popup as every other download.
                            dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk.ask(context, base, base.pageUrl ?: currentUrl)
                        }
                    }
                    WatchAction(Icons.AutoMirrored.Filled.OpenInNew, "Other player", "stream_watch_external") {
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
