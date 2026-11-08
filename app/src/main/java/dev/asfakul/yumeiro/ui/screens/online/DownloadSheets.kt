/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.download.DownloadNaming
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.YtDownloadOptions
import dev.asfakul.yumeiro.online.download.YtDownloads
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.youtube.YtItem

/** Download state of one episode, shown on its row (tap: download / pause / nothing when saved). */
@Composable
internal fun EpisodeDownloadIcon(d: OnlineDetail, version: String, index: Int, e: OnlineEpisode) {
    val context = LocalContext.current
    val all by OnlineDownloads.items.collectAsState()
    val id = remember(d.api, d.url, version, e.data) { OnlineDownloads.episodeId(d.api, d.url, version.ifBlank { null }, e.data) }
    val item = all.firstOrNull { it.id == id }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (item?.status) {
            OnlineDownloads.Status.DONE -> Icon(Icons.Default.DownloadDone, "Downloaded", tint = SpotifyGreen, modifier = Modifier.size(22.dp).testTag("episode_downloaded"))
            OnlineDownloads.Status.RUNNING, OnlineDownloads.Status.QUEUED -> IconButton(onClick = { OnlineDownloads.pause(context, id) }) {
                if (item.status == OnlineDownloads.Status.RUNNING && item.total > 0)
                    CircularProgressIndicator(progress = { item.fraction }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = SpotifyGreen)
                else CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = SpotifyGreen)
            }
            OnlineDownloads.Status.FAILED -> IconButton(onClick = { OnlineDownloads.start(context, id) }) { Icon(Icons.Default.ErrorOutline, "Retry download", tint = TextTertiary) }
            else -> IconButton(onClick = {
                val n = OnlineDownloads.enqueueEpisodes(context, d, version, listOf(index to e))
                if (n > 0) AppMessages.success(context, "Downloading ${e.label}")
                else if (item?.status == OnlineDownloads.Status.PAUSED) OnlineDownloads.start(context, id)
            }, modifier = Modifier.testTag("episode_download")) { Icon(Icons.Default.Download, "Download episode", tint = TextSecondary) }
        }
    }
}

/** "Download" button on a title page: opens the episode download sheet. */
@Composable
internal fun TitleDownloadButton(d: OnlineDetail, version: String, ordered: List<OnlineEpisode>, season: List<IndexedValue<OnlineEpisode>>, seasonTitle: String?, modifier: Modifier = Modifier) {
    val all by OnlineDownloads.items.collectAsState()
    var open by remember { mutableStateOf(false) }
    val folder = remember(d, version) { DownloadNaming.seriesFolder(d.name, d.api, version) }
    val saved = all.count { it.folder == folder && it.status == OnlineDownloads.Status.DONE }
    val active = all.count { it.folder == folder && it.active }
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(24.dp), modifier = modifier.height(46.dp).testTag("online_detail_download")) {
        Icon(if (saved > 0 && active == 0) Icons.Default.DownloadDone else Icons.Default.Download, null, tint = if (saved > 0) SpotifyGreen else TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text(
            when { active > 0 -> "Downloading $active"; saved > 0 -> "$saved saved"; else -> "Download" },
            color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
    if (open) EpisodeDownloadSheet(d, version, ordered, season, seasonTitle, folder) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDownloadSheet(
    d: OnlineDetail, version: String, ordered: List<OnlineEpisode>, season: List<IndexedValue<OnlineEpisode>>,
    seasonTitle: String?, folder: String, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { DownloadSettings.init(context) }
    val quality by DownloadSettings.quality.collectAsState()
    val autoDelete by DownloadSettings.autoDeleteDays.collectAsState()
    val all by OnlineDownloads.items.collectAsState()
    val have = all.filter { it.folder == folder && (it.active || it.status == OnlineDownloads.Status.DONE) }.mapNotNull { it.data }.toSet()
    fun queue(list: List<Pair<Int, OnlineEpisode>>, what: String) {
        val n = OnlineDownloads.enqueueEpisodes(context, d, version, list)
        AppMessages.success(context, if (n == 0) "Already downloaded or queued" else "Downloading $n $what")
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).testTag("episode_download_sheet")) {
            Text("Download for offline", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(folder, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Quality: ${DownloadSettings.qualityLabel(quality)} · Auto-delete: ${DownloadSettings.autoDeleteLabel(autoDelete)} · change in Settings → Downloads",
                color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (ordered.isEmpty()) {
                val movie = d.playData
                if (movie != null) SheetOption(Icons.Default.Movie, "Download movie", d.name) {
                    queue(listOf(0 to OnlineEpisode(movie, d.name, null, null, d.posterUrl, null, d.durationMin)), "title")
                } else Text("Nothing to download here.", color = TextTertiary)
            } else {
                val seasonLeft = season.filter { it.value.data !in have }
                val allLeft = ordered.withIndex().filter { it.value.data !in have }
                if (seasonTitle != null && season.size != ordered.size) {
                    SheetOption(Icons.Default.Download, "This season", "$seasonTitle · ${seasonLeft.size} of ${season.size} left", enabled = seasonLeft.isNotEmpty()) {
                        queue(seasonLeft.map { it.index to it.value }, "episodes")
                    }
                }
                SheetOption(Icons.Default.Download, "All episodes", "${allLeft.size} of ${ordered.size} left", enabled = allLeft.isNotEmpty()) {
                    queue(allLeft.map { it.index to it.value }, "episodes")
                }
                if (allLeft.size > 5) SheetOption(Icons.Default.Download, "Next 5 episodes", "Starting from the first one not saved") {
                    queue(allLeft.take(5).map { it.index to it.value }, "episodes")
                }
            }
            Text(
                "One episode per extension downloads at a time, so the site doesn't block you. Episodes are saved in one folder per series and play offline from Library → Downloads.",
                color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = GlassFill,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) SpotifyGreen else TextTertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) TextPrimary else TextTertiary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Quality picker for a YouTube video or song: every resolution, plus audio only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloadSheet(item: YtItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var choices by remember(item.id) { mutableStateOf<List<YtDownloadOptions.Choice>?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        DownloadSettings.init(context)
        runCatching { YtDownloads.optionsFor(context, item.id).second }
            .onSuccess { choices = it; if (it.isEmpty()) error = "No downloadable formats for this video" }
            .onFailure { error = it.message ?: "Couldn't load qualities" }
    }
    var audioTab by remember(item.id) { mutableStateOf(item.music) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp).testTag("yt_download_sheet")) {
            Text("Download", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(item.title, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineChip("Video", !audioTab, { audioTab = false }, Modifier.testTag("yt_download_video_tab"))
                OnlineChip("Audio only", audioTab, { audioTab = true }, Modifier.testTag("yt_download_audio_tab"))
            }
            Spacer(Modifier.height(8.dp))
            val list = choices
            when {
                error != null -> Text(error.orEmpty(), color = TextTertiary, modifier = Modifier.padding(vertical = 16.dp))
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) }
                else -> {
                    val shown = list.filter { it.audioOnly == audioTab }
                    if (shown.isEmpty()) Text("Nothing in this format.", color = TextTertiary, modifier = Modifier.padding(vertical = 16.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown, key = { "${it.videoItag}_${it.audioItag}_${it.audioOnly}" }) { c ->
                            SheetOption(if (c.audioOnly) Icons.Default.MusicNote else Icons.Default.Download, c.label, c.detail) {
                                val err = OnlineDownloads.enqueueYouTube(context, item.id, item.title, item.channel, item.thumbnail, item.music, c)
                                if (err != null) AppMessages.error(context, err) else AppMessages.success(context, "Downloading ${c.label}")
                                onDismiss()
                            }
                        }
                    }
                }
            }
            val toPhone by DownloadSettings.saveYouTubeToPhone.collectAsState()
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (toPhone) "Saved to your phone's ${if (audioTab) "Music" else "Movies"}/Yumeiro folder and to Library → Downloads."
                    else "Saved in Library → Downloads (app storage).",
                    color = TextTertiary, fontSize = 11.sp,
                )
            }
        }
    }
}
