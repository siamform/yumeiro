/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import dev.asfakul.yumeiro.online.download.EpisodeScope
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.YtDownloadOptions
import dev.asfakul.yumeiro.online.download.YtDownloads
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.youtube.YtItem
import dev.asfakul.yumeiro.ui.theme.yuRound

/** Download state of one episode, shown on its row (tap: download / pause / nothing when saved). */
@Composable
internal fun EpisodeDownloadIcon(d: OnlineDetail, version: String, index: Int, e: OnlineEpisode) {
    val context = LocalContext.current
    val all by dev.asfakul.yumeiro.online.download.PrivateDownloads.visibleState()
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
                if (item?.status == OnlineDownloads.Status.PAUSED) { OnlineDownloads.start(context, id); return@IconButton }
                askEpisodes(context, d, version, listOf(index to e), e.label)
            }, modifier = Modifier.testTag("episode_download")) { Icon(Icons.Default.Download, "Download episode", tint = TextSecondary) }
        }
    }
}

/** "Download" button on a title page: opens the episode download sheet. */
@Composable
internal fun TitleDownloadButton(d: OnlineDetail, version: String, ordered: List<OnlineEpisode>, season: List<IndexedValue<OnlineEpisode>>, seasonTitle: String?, modifier: Modifier = Modifier, current: Int? = null, onVersion: ((String) -> Unit)? = null) {
    val all by dev.asfakul.yumeiro.online.download.PrivateDownloads.visibleState()
    var open by remember { mutableStateOf(false) }
    val folder = remember(d, version) { DownloadNaming.seriesFolder(d.name, d.api, version) }
    val saved = all.count { it.folder == folder && it.status == OnlineDownloads.Status.DONE }
    val active = all.count { it.folder == folder && it.active }
    OutlinedButton(onClick = { open = true }, shape = yuRound(24), modifier = modifier.height(46.dp).testTag("online_detail_download")) {
        Icon(if (saved > 0 && active == 0) Icons.Default.DownloadDone else Icons.Default.Download, null, tint = if (saved > 0) SpotifyGreen else TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text(
            when { active > 0 -> "Downloading $active"; saved > 0 -> "$saved saved"; else -> "Download" },
            color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
    if (open) EpisodeDownloadSheet(d, version, ordered, season, seasonTitle, folder, current, onVersion) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDownloadSheet(
    d: OnlineDetail, version: String, ordered: List<OnlineEpisode>, season: List<IndexedValue<OnlineEpisode>>,
    seasonTitle: String?, folder: String, current: Int?, onVersion: ((String) -> Unit)?, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { DownloadSettings.init(context) }
    val quality by DownloadSettings.quality.collectAsState()
    val autoDelete by DownloadSettings.autoDeleteDays.collectAsState()
    val all by dev.asfakul.yumeiro.online.download.PrivateDownloads.visibleState()
    val have = all.filter { it.folder == folder && (it.active || it.status == OnlineDownloads.Status.DONE) }.mapNotNull { it.data }.toSet()
    fun queue(list: List<Pair<Int, OnlineEpisode>>, what: String) {
        onDismiss()
        askEpisodes(context, d, version, list, if (what == "title") "this movie" else if (list.size == 1) list.first().second.label else "${list.size} episodes", movie = what == "title")
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle()) {
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
            // OL-5: Sub / Dub (the source's versions) picked right here; the lists below follow it.
            if (onVersion != null && d.versions.size > 1 && dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.DOWNLOAD_TRACKS)) {
                Text("Version", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 10.dp).testTag("download_version"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.versions.forEachIndexed { i, v -> OnlineChip(v.ifBlank { "Default" }, v == version, { onVersion(v) }, Modifier.testTag("download_version_$i")) }
                }
            }
            if (ordered.isEmpty()) {
                val movie = d.playData
                if (movie != null) SheetOption(Icons.Default.Movie, "Download movie", d.name) {
                    queue(listOf(0 to OnlineEpisode(movie, d.name, null, null, d.posterUrl, null, d.durationMin)), "title")
                } else Text("Nothing to download here.", color = TextTertiary)
            } else {
                val scopes = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.EPISODE_RANGE_DOWNLOAD)
                // OL-11: "This episode only" = the episode Play / Continue opens.
                if (scopes) ordered.getOrNull(current ?: -1)?.let { cur ->
                    val one = EpisodeScope.single(ordered, current, have)
                    SheetOption(Icons.Default.Download, "This episode only", if (one == null) "${cur.label} · already saved or queued" else cur.label, enabled = one != null, tag = "episode_scope_this") {
                        one?.let { queue(listOf(it), "episodes") }
                    }
                }
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
                // OL-11: a from–to range over the season shown (all episodes when there is one season).
                if (scopes) EpisodeRangePicker(season, have) { picked -> queue(picked, "episodes") }
                if (allLeft.size > 5) SheetOption(Icons.Default.Download, "Next 5 episodes", "Starting from the first one not saved") {
                    queue(allLeft.take(5).map { it.index to it.value }, "episodes")
                }
            }
            Text(
                "Next you'll see where it's saved and choose Permanent or Offline (auto-delete). One episode per extension downloads at a time, so the site doesn't block you.",
                color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * OL-11: "Episode range" row. Collapsed it is one option; open it shows From / To boxes
 * (pre-filled with the first and last episode number) and a live "Download N episodes" button.
 * Saved / queued episodes in the range are skipped and the count says so.
 */
@Composable
internal fun EpisodeRangePicker(list: List<IndexedValue<OnlineEpisode>>, have: Set<String>, onPick: (List<Pair<Int, OnlineEpisode>>) -> Unit) {
    val b = EpisodeScope.bounds(list) ?: return
    var open by remember(list) { mutableStateOf(false) }
    var fromText by remember(list) { mutableStateOf(b.first.toString()) }
    var toText by remember(list) { mutableStateOf(b.last.toString()) }
    SheetOption(Icons.Default.Download, "Episode range", "Pick from and to (E${b.first}–E${b.last})", tag = "episode_scope_range") { open = !open }
    if (!open) return
    val from = EpisodeScope.parse(fromText); val to = EpisodeScope.parse(toText)
    val inRange = if (from != null && to != null) EpisodeScope.range(list, from, to) else emptyList()
    val picked = inRange.filter { it.second.data !in have }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val kb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        androidx.compose.material3.OutlinedTextField(fromText, { fromText = it.filter(Char::isDigit).take(6) }, label = { Text("From") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f).testTag("episode_range_from"))
        Text("to", color = TextSecondary)
        androidx.compose.material3.OutlinedTextField(toText, { toText = it.filter(Char::isDigit).take(6) }, label = { Text("To") }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f).testTag("episode_range_to"))
    }
    val skipped = inRange.size - picked.size
    Text(
        when {
            from == null || to == null -> "Type both episode numbers"
            inRange.isEmpty() -> "No episodes between E${minOf(from, to)} and E${maxOf(from, to)}"
            skipped > 0 -> "${picked.size} to download · $skipped already saved or queued"
            else -> "${picked.size} episodes"
        },
        color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp).testTag("episode_range_count"),
    )
    androidx.compose.material3.Button(
        onClick = { onPick(picked) }, enabled = picked.isNotEmpty(), shape = yuRound(24),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("episode_range_start"),
    ) { Text(if (picked.size == 1) "Download 1 episode" else "Download ${picked.size} episodes", fontWeight = FontWeight.SemiBold) }
}

/** Asks where/how (see [DownloadPrompt]) before queuing [list] of [d]. */
internal fun askEpisodes(context: android.content.Context, d: OnlineDetail, version: String, list: List<Pair<Int, OnlineEpisode>>, what: String, movie: Boolean = false) {
    DownloadSettings.init(context)
    val tracks = dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.DOWNLOAD_TRACKS)
    DownloadPrompt.ask(context, DownloadPrompt.Request(
        type = if (movie) DownloadPrompt.Type.MOVIE else DownloadPrompt.Type.EPISODES,
        title = d.name, what = what, count = list.size,
        folder = DownloadNaming.seriesFolder(d.name, d.api, version), quality = DownloadSettings.qualityLabel(DownloadSettings.quality.value) + " (up to)",
        defaultKeep = DownloadSettings.keepByDefault.value,
        // OWN-18: each episode's link (and so its size) is fetched only when it starts; say so instead of a made-up number.
        facts = listOfNotNull(version.trim().takeIf { it.isNotEmpty() && !it.equals("default", true) }, if (movie) "Movie" else "${list.size} ${if (list.size == 1) "episode" else "episodes"}", "Source: ${d.api}"),
        sizeNote = if (list.size > 1) "each episode's size shows in Downloads when it starts" else "shown in Downloads when it starts (the source gives its link then)",
        // NF-2b: the prompt offers a quality for this download; -1 keeps the Settings value.
        startAt = { keep, h -> if (OnlineDownloads.enqueueEpisodes(context, d, version, list, keep, h) == 0) "Already downloaded or queued" else null },
        // OL-5: extensions name their subtitles only when each episode's link is fetched, so the popup offers "save all".
        // OWN-8: one language for the whole batch (the Settings subtitle language, English), or all, or none.
        offer = if (tracks) dev.asfakul.yumeiro.online.download.DownloadTracks.Offer(
            subtitlesLater = true,
            subtitleLanguages = if (dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.BATCH_SUB_LANGUAGE))
                dev.asfakul.yumeiro.online.download.DownloadTracks.batchLanguages(dev.asfakul.yumeiro.online.OnlineSettings.subtitleLang.value) else emptyList(),
        ) else null,
        startWith = if (tracks) { keep, pick -> if (OnlineDownloads.enqueueEpisodes(context, d, version, list, keep, pick.maxHeight, pick.subtitles) == 0) "Already downloaded or queued" else null } else null,
    ) { keep -> if (OnlineDownloads.enqueueEpisodes(context, d, version, list, keep) == 0) "Already downloaded or queued" else null })
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    Surface(
        shape = yuRound(14), color = GlassFill,
        modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(vertical = 4.dp).clip(yuRound(14)).clickable(enabled = enabled, onClick = onClick),
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
            .onFailure { error = dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Couldn't load qualities.") }
    }
    // DL-ONE: with the qualities known, hand over to the one shared download popup (same as every other source).
    val oneSheet = remember { dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.DL_ONE_SHEET) }
    LaunchedEffect(choices, oneSheet) {
        val list = choices
        if (oneSheet && !list.isNullOrEmpty()) { onDismiss(); askYouTubeInSharedPopup(context, item, list) }
    }
    if (oneSheet && !choices.isNullOrEmpty()) return
    var audioTab by remember(item.id) { mutableStateOf(item.music) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle()) {
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
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { dev.asfakul.yumeiro.ui.components.YuLoader(size = 48.dp) }
                else -> {
                    val shown = list.filter { it.audioOnly == audioTab }
                    if (shown.isEmpty()) Text("Nothing in this format.", color = TextTertiary, modifier = Modifier.padding(vertical = 16.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown, key = { "${it.videoItag}_${it.audioItag}_${it.audioOnly}" }) { c ->
                            SheetOption(if (c.audioOnly) Icons.Default.MusicNote else Icons.Default.Download, c.label, c.detail) {
                                onDismiss()
                                DownloadPrompt.ask(context, DownloadPrompt.Request(
                                    type = if (c.audioOnly) DownloadPrompt.Type.YT_AUDIO else DownloadPrompt.Type.YT_VIDEO,
                                    title = item.title, what = if (c.audioOnly) "audio (${c.label.removePrefix("Audio ")})" else "${c.label} video", count = 1,
                                    folder = OnlineDownloads.youTubeFolder(c.audioOnly, item.music), quality = c.label + (c.detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    sizeBytes = c.sizeBytes, defaultKeep = true, facts = listOf("YouTube", if (c.audioOnly) "Audio only" else "Video + audio"),
                                ) { keep -> OnlineDownloads.enqueueYouTube(context, item.id, item.title, item.channel, item.thumbnail, item.music, c, keep) })
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
                    if (toPhone) "Saved to your phone's ${if (audioTab) "Music" else "Movies"}/Yumeiro folder and to Downloads (Home or Library → Videos)."
                    else "Saved in Downloads (Home or Library → Videos), app storage.",
                    color = TextTertiary, fontSize = 11.sp,
                )
            }
        }
    }
}

/** DL-ONE: a YouTube video or song in the shared download popup: Quality = video heights, Audio track = audio only. */
internal fun askYouTubeInSharedPopup(context: android.content.Context, item: YtItem, choices: List<dev.asfakul.yumeiro.online.download.YtDownloadOptions.Choice>) {
    val one = dev.asfakul.yumeiro.online.download.YtOneSheet
    val best = one.choose(choices, item.music, dev.asfakul.yumeiro.online.download.DownloadTracks.Pick()) ?: return
    DownloadPrompt.ask(context, DownloadPrompt.Request(
        type = if (best.audioOnly) DownloadPrompt.Type.YT_AUDIO else DownloadPrompt.Type.YT_VIDEO,
        title = item.title, what = if (item.music) "this song" else "this video", count = 1,
        folder = OnlineDownloads.youTubeFolder(best.audioOnly, item.music),
        quality = best.label + (best.detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
        defaultKeep = true,
        offer = one.offer(choices, item.music),
        alwaysAsk = one.alwaysAsk(choices, item.music),
        // OWN-18: the size follows the picked quality.
        sizeFor = { h -> one.choose(choices, item.music, dev.asfakul.yumeiro.online.download.DownloadTracks.Pick(maxHeight = h))?.sizeBytes?.takeIf { it > 0 }
            ?.let { dev.asfakul.yumeiro.online.download.DownloadSize.Size(it, exact = true) } },
        facts = listOf("YouTube", if (best.audioOnly) "Audio only" else "Video + audio"),
        startWith = { keep, pick ->
            val c = one.choose(choices, item.music, pick)
            if (c == null) "No downloadable formats for this video"
            else OnlineDownloads.enqueueYouTube(context, item.id, item.title, item.channel, item.thumbnail, item.music, c, keep)
        },
    ) { keep -> OnlineDownloads.enqueueYouTube(context, item.id, item.title, item.channel, item.thumbnail, item.music, best, keep) })
}
