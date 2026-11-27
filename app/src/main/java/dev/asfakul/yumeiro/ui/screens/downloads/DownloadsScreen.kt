/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.downloads

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.download.DownloadGroups
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.screens.online.OnlineChip
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.screens.settings.DownloadSettingsCard
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.asfakul.yumeiro.ui.theme.yuRound

enum class DownloadFilter(val label: String) { ALL("All"), OFFLINE("Offline"), PERMANENT("Permanent"), ACTIVE("In progress") }

/**
 * Downloads page: in-progress queue, then Permanent and Offline copies grouped by series folder,
 * offline settings (auto-delete timer, quality, parallel) and tips. Opened from Home, Library,
 * the download notification and the "View" action after starting a download.
 */
@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlayVideo: (VideoItem) -> Unit, onBrowse: () -> Unit) {
    val context = LocalContext.current
    // G8 D8 (R17): the list file and the settings were read on the main thread when the page opened.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { OnlineDownloads.ensureLoaded(context); DownloadSettings.init(context) } }
    val all by dev.asfakul.yumeiro.online.download.PrivateDownloads.visibleState()
    val days by DownloadSettings.autoDeleteDays.collectAsState()
    val quality by DownloadSettings.quality.collectAsState()
    val parallel by DownloadSettings.parallel.collectAsState()
    val showTips by DownloadSettings.showTips.collectAsState()
    val split = remember(all) { DownloadGroups.split(all) }
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.ALL) }
    var filterSheet by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val open = remember { mutableStateListOf<String>() }
    val used = remember(all) { all.filter { it.status == OnlineDownloads.Status.DONE }.sumOf { it.bytes } }
    val free by rememberFreeBytes(context, all)
    // DL-H: the Private vault. Not saved: leaving the page (or the app) locks it again.
    val vaultOn = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.PRIVATE_DOWNLOADS) }
    var vaultOpen by remember { mutableStateOf(false) }
    var askVault by remember { mutableStateOf(false) }
    val stealth by dev.asfakul.yumeiro.online.download.PrivateDownloads.stealth.collectAsState()
    LaunchedEffect(Unit) { if (vaultOn) withContext(Dispatchers.IO) { dev.asfakul.yumeiro.online.download.PrivateDownloads.loadStealth(context) } }
    if (askVault) VaultUnlock(onDismiss = { askVault = false }, onOpen = { askVault = false; vaultOpen = true })
    if (vaultOpen) { PrivateVault(days, onBack = { vaultOpen = false }, onPlayVideo = onPlayVideo); return }

    if (filterSheet) dev.asfakul.yumeiro.ui.components.FilterSheet(
        onDismiss = { filterSheet = false }, onReset = if (filter != DownloadFilter.ALL) ({ filter = DownloadFilter.ALL }) else null, resultCount = null, tag = "downloads_filter_sheet",
    ) {
        dev.asfakul.yumeiro.ui.components.FilterGroup("Show", "downloads_filter_group") {
            DownloadFilter.entries.forEach { f ->
                val n = when (f) { DownloadFilter.ALL -> all.size; DownloadFilter.OFFLINE -> split.offlineCount; DownloadFilter.PERMANENT -> split.permanentCount; DownloadFilter.ACTIVE -> split.active.size }
                dev.asfakul.yumeiro.ui.components.FilterPill("${f.label} $n", filter == f, { filter = f }, testTag = "downloads_filter_${f.name.lowercase()}")
            }
        }
    }
    Column(Modifier.fillMaxSize().testTag("downloads_screen")) {
        SubPageHeader(
            "Downloads", onBack,
            subtitle = listOfNotNull(
                "${split.permanentCount} permanent", "${split.offlineCount} offline",
                if (split.active.isNotEmpty()) "${split.active.size} in progress" else null,
            ).joinToString(" · "),
        ) {
            // DL-H: a lock in the header, not a row in the list, so the page looks the same for everyone
            // and nothing moves down (DownloadUiScreenshotTest, CI 44514234313). Hidden in stealth mode.
            if (vaultOn && !stealth) IconButton(onClick = { askVault = true }, modifier = Modifier.testTag("downloads_private")) {
                Icon(Icons.Default.Lock, "Private (locked)", tint = TextPrimary)
            }
            // GROUP C catch-all: the All/Offline/Permanent/Active chips moved behind a filter icon.
            if (!split.isEmpty) dev.asfakul.yumeiro.ui.components.FilterIconButton(if (filter != DownloadFilter.ALL) 1 else 0, { filterSheet = true }, "downloads_filter", "Filter downloads")
            IconButton(onClick = { settingsOpen = !settingsOpen }, modifier = Modifier.testTag("downloads_settings")) {
                Icon(Icons.Default.Settings, "Offline settings", tint = if (settingsOpen) SpotifyGreen else TextPrimary)
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(1), Modifier.fillMaxSize().testTag("downloads_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.grid("downloads")) {
            item("storage") {
                StorageCard(used, free, days, quality, parallel, onSettings = { settingsOpen = !settingsOpen },
                    onLongPress = if (vaultOn) ({ askVault = true }) else null)
            }
            if (settingsOpen) item("settings") {
                Column {
                    Text("Offline settings", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
                    DownloadSettingsCard()
                }
            }
            if (showTips) item("tips") { TipsCard(onClose = { DownloadSettings.setShowTips(context, false) }) }
            if (split.isEmpty) item("empty") { EmptyDownloads(onBrowse) }
            downloadSections(split, filter, days, open, onPlayVideo)
        }
    }
}

/** The grouped download list (In progress, Permanent, Offline), shared by the Downloads page and Library. */
fun LazyGridScope.downloadSections(
    split: DownloadGroups.Split,
    filter: DownloadFilter,
    days: Int,
    open: SnapshotStateList<String>,
    onPlayVideo: (VideoItem) -> Unit,
    limitFolders: Int = Int.MAX_VALUE,
) {
    if (split.active.isNotEmpty() && (filter == DownloadFilter.ALL || filter == DownloadFilter.ACTIVE)) {
        item("dl_sec_active", span = { GridItemSpan(maxLineSpan) }) {
            val context = LocalContext.current
            val failed = split.active.count { it.status == OnlineDownloads.Status.FAILED }
            val paused = split.active.count { it.status == OnlineDownloads.Status.PAUSED }
            SectionHead(Icons.Default.Download, "In progress", "${split.active.size} · newest first",
                action = when {
                    failed + paused > 0 -> "Resume all" to { split.active.filter { !it.active }.forEach { OnlineDownloads.start(context, it.id) } }
                    else -> "Pause all" to { split.active.forEach { OnlineDownloads.pause(context, it.id) } }
                })
        }
        items(split.active.take(if (limitFolders == Int.MAX_VALUE) Int.MAX_VALUE else 4), key = { "dl_a_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { d -> DownloadRow(d, days, onPlayVideo) }
    }
    if (split.permanent.isNotEmpty() && (filter == DownloadFilter.ALL || filter == DownloadFilter.PERMANENT)) {
        item("dl_sec_perm", span = { GridItemSpan(maxLineSpan) }) {
            SectionHead(Icons.Default.PushPin, "Permanent", "${split.permanentCount} · kept forever, never auto-deleted")
        }
        folderItems("p", split.permanent.take(limitFolders), days, open, onPlayVideo)
    }
    if (split.offline.isNotEmpty() && (filter == DownloadFilter.ALL || filter == DownloadFilter.OFFLINE)) {
        item("dl_sec_off", span = { GridItemSpan(maxLineSpan) }) {
            SectionHead(Icons.Default.Schedule, "Offline", "${split.offlineCount} · " +
                if (days > 0) "auto-deletes ${DownloadSettings.autoDeleteLabel(days)} after finishing" else "auto-delete is off (set a timer in settings)")
        }
        folderItems("o", split.offline.take(limitFolders), days, open, onPlayVideo)
    }
}

private fun LazyGridScope.folderItems(part: String, folders: List<DownloadGroups.Folder>, days: Int, open: SnapshotStateList<String>, onPlayVideo: (VideoItem) -> Unit) {
    folders.forEach { f ->
        val key = part + "|" + f.name
        item("dl_f_$key", span = { GridItemSpan(maxLineSpan) }) {
            DownloadFolderRow(f, days, expanded = key in open, onToggle = { if (key in open) open.remove(key) else open.add(key) })
        }
        if (key in open) items(f.items, key = { "dl_${part}_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { d ->
            Box(Modifier.padding(start = 14.dp)) { DownloadRow(d, days, onPlayVideo) }
        }
    }
}

@Composable
private fun SectionHead(icon: ImageVector, title: String, subtitle: String, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextTertiary, fontSize = 11.sp, maxLines = 2)
        }
        action?.let { (label, run) -> TextButton(onClick = run) { Text(label) } }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun StorageCard(used: Long, free: Long, days: Int, quality: Int, parallel: Int, onSettings: () -> Unit, onLongPress: (() -> Unit)? = null) {
    Surface(shape = yuRound(16), color = DarkSurfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        .then(if (onLongPress != null) Modifier.clip(yuRound(16)).combinedClickable(onClickLabel = null, onLongClickLabel = null, onLongClick = onLongPress, onClick = {}) else Modifier)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DownloadForOffline, null, tint = SpotifyGreen)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull((DownloadPrompt.sizeText(used).takeIf { it.isNotBlank() && used > 0 }?.let { "$it used" } ?: "No space used yet"), if (free > 0) DownloadPrompt.sizeText(free) + " free" else null).joinToString(" · "),
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Auto-delete: ${DownloadSettings.autoDeleteLabel(days)} · Quality: ${DownloadSettings.qualityLabel(quality)} · $parallel at once",
                        color = TextTertiary, fontSize = 12.sp)
                }
                TextButton(onClick = onSettings) { Text("Change") }
            }
            if (used > 0 && free > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (used.toFloat() / (used + free)).coerceIn(0.01f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = SpotifyGreen)
            }
        }
    }
}

@Composable
private fun TipsCard(onClose: () -> Unit) {
    Surface(shape = yuRound(16), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("downloads_tips")) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("How downloads work", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Hide tips", tint = TextTertiary) }
            }
            listOf(
                "Anime and movies: open a title and tap Download (this season, all, next 5), or the arrow next to one episode.",
                "YouTube and YouTube Music: tap Download under the video, then pick a quality or Audio only.",
                "Each series gets its own folder, like Solo Leveling-(HiAnime-(Sub)).",
                "Permanent copies stay until you delete them. Offline copies are deleted by the auto-delete timer.",
                "Tap the pin on a folder or episode to switch between Permanent and Offline.",
                "Everything plays without internet from here or Library → Videos.",
            ).forEach { Text("• $it", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp, end = 10.dp)) }
        }
    }
}

@Composable
private fun EmptyDownloads(onBrowse: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(yuRound(16)).background(DarkSurfaceContainer).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.DownloadForOffline, null, tint = SpotifyGreen, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text("No downloads yet", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text("Save episodes, movies, YouTube videos and music to watch or listen without internet.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Find something to save", color = dev.asfakul.yumeiro.ui.theme.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(yuRound(20)).background(SpotifyGreen).clickable(onClick = onBrowse).padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

@Composable
internal fun DownloadRow(d: OnlineDownloads.Item, days: Int, onPlayVideo: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val done = d.status == OnlineDownloads.Status.DONE
    val hideOn = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.PRIVATE_DOWNLOADS) }
    val canHide = done && hideOn
    Row(
        Modifier.fillMaxWidth().clip(yuRound(14)).background(DarkSurfaceContainer)
            .clickable(enabled = done) { scope.launch { play(context, d, onPlayVideo) } }.padding(10.dp).testTag("library_download"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(yuRound(8))) {
            PosterImage(d.posterUrl, d.posterHeaders, Modifier.fillMaxSize())
            // DL-H: the play mark sits on the poster, so a finished row has room for Hide.
            if (done) Icon(Icons.Default.PlayArrow, "Play", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x66000000)).padding(3.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.episodeLabel?.takeIf { d.kind == OnlineDownloads.Kind.EPISODE } ?: d.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (d.kind == OnlineDownloads.Kind.EPISODE) d.title else d.episodeLabel ?: d.api, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when (d.status) {
                OnlineDownloads.Status.DONE -> listOfNotNull(
                    if (d.keep) "Permanent" else "Offline", d.quality, DownloadPrompt.sizeText(d.bytes).ifBlank { null },
                    if (d.publicUri != null) "on phone" else null, if (d.fileName.endsWith(".ts", true)) "TS file" else null, deleteLine(d, days),
                ).joinToString(" · ")
                OnlineDownloads.Status.RUNNING -> if (d.stage == "convert") "Converting to MP4…" else if (d.total > 0) "${(d.fraction * 100).toInt()}% · ${DownloadPrompt.sizeText(d.bytes)} of ~${DownloadPrompt.sizeText(d.total)}" else "Downloading · ${DownloadPrompt.sizeText(d.bytes).ifBlank { "starting" }}"
                OnlineDownloads.Status.QUEUED -> "Waiting for a free slot…"
                OnlineDownloads.Status.PAUSED -> "Paused · ${DownloadPrompt.sizeText(d.bytes).ifBlank { "0 MB" }} · tap ↓ to resume"
                OnlineDownloads.Status.FAILED -> d.error ?: "Failed"
            }
            Text(line, color = if (d.status == OnlineDownloads.Status.FAILED) dev.asfakul.yumeiro.ui.theme.DangerTone else TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.status == OnlineDownloads.Status.RUNNING) {
                Spacer(Modifier.height(5.dp))
                if (d.total > 0) LinearProgressIndicator(progress = { d.fraction }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SpotifyGreen)
                else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = SpotifyGreen)
            }
        }
        when (d.status) {
            OnlineDownloads.Status.DONE -> if (canHide) IconButton(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        if (d.hidden) dev.asfakul.yumeiro.online.download.PrivateDownloads.unhide(context, d.id)
                        else dev.asfakul.yumeiro.online.download.PrivateDownloads.hide(context, d.id)
                    }
                    if (!ok) AppMessages.error(context, if (d.hidden) "Couldn't move it back (not enough space?)" else "Couldn't hide it (not enough space?)")
                    else AppMessages.success(context, if (d.hidden) "Back in Downloads" else "Moved to Private")
                }
            }, modifier = Modifier.testTag(if (d.hidden) "download_unhide" else "download_hide")) {
                Icon(if (d.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (d.hidden) "Unhide (back to Downloads)" else "Hide in Private", tint = TextTertiary)
            }
            OnlineDownloads.Status.RUNNING, OnlineDownloads.Status.QUEUED -> IconButton(onClick = { OnlineDownloads.pause(context, d.id) }) { Icon(Icons.Default.Pause, "Pause", tint = TextPrimary) }
            OnlineDownloads.Status.PAUSED -> IconButton(onClick = { OnlineDownloads.start(context, d.id) }) { Icon(Icons.Default.Download, "Resume", tint = TextPrimary) }
            OnlineDownloads.Status.FAILED -> IconButton(onClick = { OnlineDownloads.start(context, d.id) }) { Icon(Icons.Default.Refresh, "Retry", tint = TextPrimary) }
        }
        IconButton(onClick = {
            OnlineDownloads.setKeep(context, d.id, !d.keep)
            AppMessages.success(context, if (!d.keep) "Permanent: kept forever" else "Offline: the auto-delete timer applies")
        }, modifier = Modifier.testTag("library_download_keep")) {
            Icon(if (d.keep) Icons.Default.PushPin else Icons.Outlined.PushPin, if (d.keep) "Permanent (tap to make offline)" else "Offline (tap to keep forever)", tint = if (d.keep) SpotifyGreen else TextTertiary)
        }
        IconButton(onClick = { OnlineDownloads.delete(context, d.id) }) { Icon(Icons.Default.Delete, "Delete", tint = TextTertiary) }
    }
}

/** G8 D8 (R17): finding the file (mkdirs + exists) ran on the main thread on every tap. */
private suspend fun play(context: android.content.Context, d: OnlineDownloads.Item, onPlayVideo: (VideoItem) -> Unit) {
    var sidecars = emptyList<dev.asfakul.yumeiro.stream.StreamSubtitle>()
    val uri = withContext(Dispatchers.IO) {
        val f = if (d.fileName.isNotBlank()) OnlineDownloads.fileOf(context, d) else null
        when {
            f != null && f.exists() -> android.net.Uri.fromFile(f).toString().also {
                // OL-5: subtitles saved with the download play with it, offline.
                if (dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.DOWNLOAD_TRACKS)) sidecars = subtitleSidecars(f)
            }
            d.publicUri != null -> d.publicUri
            else -> null
        }
    }
    if (uri != null && sidecars.isNotEmpty()) dev.asfakul.yumeiro.stream.StreamRepository.register(
        dev.asfakul.yumeiro.stream.StreamRequest(url = uri, title = listOfNotNull(d.title, d.episodeLabel).joinToString(" · "), subtitles = sidecars))
    if (uri != null) onPlayVideo(VideoItem(id = "download:${d.id}", title = listOfNotNull(d.title, d.episodeLabel).joinToString(" · "), durationMs = 0L, uri = uri, resolution = d.quality.orEmpty(), folderName = d.folder.ifBlank { "Downloads" }, sizeBytes = d.bytes))
    else AppMessages.error(context, "The file was removed")
}

/** OL-5: subtitle files saved next to [video] ("Ep 1.English.vtt"), for the player. Call off the main thread. */
internal fun subtitleSidecars(video: java.io.File): List<dev.asfakul.yumeiro.stream.StreamSubtitle> {
    val T = dev.asfakul.yumeiro.online.download.DownloadTracks
    val names = video.parentFile?.list()?.toList().orEmpty()
    return T.sidecarsOf(video.name, names).map { (name, label) ->
        val f = java.io.File(video.parentFile, name)
        dev.asfakul.yumeiro.stream.StreamSubtitle(url = android.net.Uri.fromFile(f).toString(), label = label, mimeType = T.subtitleMime(name))
    }
}

private fun deleteLine(d: OnlineDownloads.Item, days: Int): String? {
    val left = DownloadGroups.daysLeft(d, days, System.currentTimeMillis()) ?: return null
    return if (left <= 1) "deletes within a day" else "deletes in $left days"
}

@Composable
internal fun DownloadFolderRow(f: DownloadGroups.Folder, days: Int, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val list = f.items
    val allKept = list.isNotEmpty() && list.all { it.keep }
    val soonest = list.mapNotNull { DownloadGroups.daysLeft(it, days, System.currentTimeMillis()) }.minOrNull()
    var confirm by remember { mutableStateOf(false) }
    val realFolder = list.firstOrNull()?.folder.orEmpty()
    Row(
        Modifier.fillMaxWidth().clip(yuRound(14)).background(DarkSurfaceContainer).clickable(onClick = onToggle).padding(10.dp).testTag("library_download_folder"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(yuRound(8))) {
            PosterImage(list.firstOrNull { it.posterUrl != null }?.posterUrl, list.firstOrNull()?.posterHeaders.orEmpty(), Modifier.fillMaxSize())
            Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(
                "${list.size} ${if (list.size == 1) "item" else "items"}", DownloadPrompt.sizeText(f.bytes).ifBlank { null },
                soonest?.let { if (it <= 1) "next deletes within a day" else "next deletes in $it days" },
            ).joinToString(" · ")
            Text(line, color = TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (realFolder.isNotBlank()) IconButton(onClick = {
            OnlineDownloads.setKeepFolder(context, realFolder, !allKept)
            AppMessages.success(context, if (!allKept) "${f.name}: moved to Permanent" else "${f.name}: moved to Offline")
        }, modifier = Modifier.testTag("library_folder_keep")) {
            Icon(if (allKept) Icons.Default.PushPin else Icons.Outlined.PushPin, if (allKept) "Permanent (tap to make offline)" else "Keep whole folder forever", tint = if (allKept) SpotifyGreen else TextTertiary)
        }
        IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Delete folder", tint = TextTertiary) }
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand", tint = TextSecondary)
    }
    if (confirm) TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${list.size} download${if (list.size == 1) "" else "s"}?") },
        text = { Text("${f.name}: these files will be removed from this phone. Other copies of the series stay.") },
        confirmButton = { TextButton(onClick = { confirm = false; list.forEach { OnlineDownloads.delete(context, it.id) } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
