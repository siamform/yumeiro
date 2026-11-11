/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import dev.asfakul.yumeiro.ui.theme.GlassFill
import android.net.Uri
import dev.asfakul.yumeiro.ui.components.AppMessages
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.StreamPlaylist
import dev.asfakul.yumeiro.stream.guessTitle
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.stream.DrmScheme
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.PlatformRules
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamResolver
import dev.asfakul.yumeiro.stream.StreamSniffer
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.UserAgentPreset
import dev.asfakul.yumeiro.stream.looksLikeM3uPlaylist
import dev.asfakul.yumeiro.stream.parseHeaderLines
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** Network stream player: URL / M3U8 / MPD / Smooth / RTSP / TS, IPTV playlists, headers & DRM. */
@Composable
fun StreamScreen(
    modifier: Modifier = Modifier,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenWebsites: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    remember(context) { StreamRepository.init(context); StreamRepository }
    val history by StreamRepository.history.collectAsState()
    val channels by StreamRepository.playlist.collectAsState()
    val playlistName by StreamRepository.playlistName.collectAsState()
    val openPlaylistId by StreamRepository.playlistId.collectAsState()
    val playlists by PlaylistStore.playlists.collectAsState()
    val lastSync by PlaylistStore.lastSync.collectAsState()
    val listState = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("stream")
    LaunchedEffect(Unit) {
        runCatching { PlaylistStore.init(context.applicationContext) }
        // Background market check (at most every 6 h, silent unless something was updated).
        val r = runCatching { PlaylistStore.autoUpdateMarket(context.applicationContext) }.getOrNull()
        if (r != null && r.updated.isNotEmpty()) AppMessages.success(context, "Updated ${r.updated.joinToString()}")
    }

    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(StreamFormat.AUTO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var uaPreset by rememberSaveable { mutableStateOf(UserAgentPreset.DEFAULT) }
    var customUa by rememberSaveable { mutableStateOf("") }
    var referer by rememberSaveable { mutableStateOf("") }
    var origin by rememberSaveable { mutableStateOf("") }
    var cookie by rememberSaveable { mutableStateOf("") }
    var extraHeaders by rememberSaveable { mutableStateOf("") }
    var drm by rememberSaveable { mutableStateOf(DrmScheme.NONE) }
    var license by rememberSaveable { mutableStateOf("") }
    var clearKeys by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageFinder by remember { mutableStateOf<Resolution.NeedsBrowser?>(null) }
    var handoff by remember { mutableStateOf<Resolution.Handoff?>(null) }
    val busyIds = remember { mutableStateMapOf<String, Boolean>() }
    var syncing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StreamPlaylist?>(null) }
    var addingRemote by remember { mutableStateOf(false) }
    var creatingLocal by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StreamPlaylist?>(null) }
    var addTarget by remember { mutableStateOf<StreamRequest?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val app = context.applicationContext
    val activePlaylists = playlists.filter { it.enabled }
    val disabledPlaylists = playlists.filterNot { it.enabled }
    val localPlaylists = playlists.filter { it.isLocal }

    fun toast(msg: String) = AppMessages.show(context, msg)
    fun toastError(msg: String) = AppMessages.error(context, msg)

    /** Runs [block] off the main thread; any failure becomes a short message instead of a crash. */
    fun launchSafe(onError: (String) -> Unit = ::toastError, block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { onError(dev.asfakul.yumeiro.online.OnlineErrors.friendly(e, "Something went wrong. Try again.")) }
    }

    /** Opens a saved playlist on its own page; [force] re-downloads it first (card menu → Refresh now). */
    fun openSaved(p: StreamPlaylist, force: Boolean = false) {
        if (!force) { onOpenPlaylist(p.id); return }
        if (busyIds[p.id] == true) return
        busyIds[p.id] = true
        launchSafe(onError = { toastError("${p.name}: $it") }) {
            try {
                val n = PlaylistStore.refresh(app, p.id)
                if (openPlaylistId == p.id) StreamRepository.setPlaylist(p.name, PlaylistStore.channels(app, p.id), p.id)
                AppMessages.success(context, "${p.name} updated · $n streams")
            } finally { busyIds.remove(p.id) }
        }
    }

    fun refreshOpenIfShown(id: String) {
        if (openPlaylistId == id) launchSafe {
            val p = PlaylistStore.get(id) ?: return@launchSafe
            StreamRepository.setPlaylist(p.name, PlaylistStore.channels(app, id), id)
        }
    }

    fun addToLocal(target: StreamPlaylist, item: StreamRequest) = launchSafe {
        PlaylistStore.addChannels(app, target.id, listOf(item.copy(title = item.displayTitle)))
        toast("Saved to ${target.name}")
        refreshOpenIfShown(target.id)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val id = pendingExport; pendingExport = null
        if (uri != null) launchSafe {
            if (id == null || id == TEMP_EXPORT) {
                PlaylistStore.exportChannels(app, playlistName, StreamRepository.playlist.value, uri)
                toast("Exported ${StreamRepository.playlist.value.size} streams")
            } else {
                val n = PlaylistStore.export(app, id, uri)
                toast("Exported $n streams")
            }
        }
    }

    fun exportPlaylist(id: String?, name: String?) {
        pendingExport = id ?: TEMP_EXPORT
        runCatching { exportLauncher.launch(fileNameFor(name)) }.onFailure { pendingExport = null; toast("No app available to save files") }
    }

    fun sync() {
        if (syncing) return
        syncing = true
        launchSafe {
            try {
                val r = PlaylistStore.sync(app)
                toast(buildString {
                    append("Updated ${r.updated} playlist${if (r.updated == 1) "" else "s"}")
                    if (r.added > 0) append(", ${r.added} new")
                    if (r.failed > 0) append(", ${r.failed} failed")
                })
                openPlaylistId?.let { id -> if (PlaylistStore.get(id)?.isDefault == true) refreshOpenIfShown(id) }
            } finally { syncing = false }
        }
    }

    fun currentRequest() = StreamRequest(
        url = url.trim(),
        format = format,
        userAgent = customUa.ifBlank { null } ?: uaPreset.value,
        referer = referer.ifBlank { null },
        origin = origin.ifBlank { null },
        cookie = cookie.ifBlank { null },
        headers = parseHeaderLines(extraHeaders),
        drmScheme = drm,
        drmLicenseUrl = license.ifBlank { null },
        clearKeys = clearKeys.ifBlank { null },
    )

    fun loadPlaylist(source: String, name: String?) {
        loading = true; error = null
        scope.launch {
            runCatching { StreamRepository.loadPlaylist(context, source, currentRequest().takeIf { source.startsWith("http") }) }
                .onSuccess { StreamRepository.setPlaylist(name, it); onOpenPlaylist(OPENED_PLAYLIST_SLUG) }
                .onFailure { error = dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Couldn't load this playlist.") }
            loading = false
        }
    }

    fun play() {
        val u = url.trim()
        error = null
        if (!Regex("^(https?|rtsps?)://.+", RegexOption.IGNORE_CASE).matches(u) && !dev.asfakul.yumeiro.stream.LinkHosts.isTorrent(u)) {
            error = "Enter a valid http(s)://, rtsp:// or magnet: link"
            return
        }
        handoff = null
        if (format == StreamFormat.AUTO && looksLikeM3uPlaylist(u)) { loadPlaylist(u, Uri.parse(u).lastPathSegment); return }
        val request = currentRequest()
        // A format picked by hand (or RTSP) is played exactly as before, without inspection.
        if (format != StreamFormat.AUTO || (!u.startsWith("http", ignoreCase = true) && !dev.asfakul.yumeiro.stream.LinkHosts.isTorrent(u))) { IncomingMedia.open(context, request); return }
        // Otherwise look at what the link really returns (headers, MIME type, redirects, content).
        loading = true
        scope.launch {
            val r = runCatching { StreamResolver.resolve(context, request, thorough = true) }
                .getOrElse { Resolution.Failed("Couldn't open the link", dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Check the link and try again.")) }
            loading = false
            when (r) {
                is Resolution.Playable -> IncomingMedia.open(context, r.request)
                is Resolution.Playlist -> {
                    StreamRepository.setPlaylist(r.name ?: Uri.parse(u).lastPathSegment ?: Uri.parse(u).host, r.channels)
                    onOpenPlaylist(OPENED_PLAYLIST_SLUG)
                }
                is Resolution.NeedsBrowser -> pageFinder = r
                is Resolution.Handoff -> { handoff = r; error = r.reason }
                is Resolution.Failed -> error = "${r.title}. ${r.message}"
            }
        }
    }

    /** Resolves the link like Play does, then saves it with the built-in downloader. */
    fun download() {
        val u = url.trim()
        error = null
        if (u.isBlank()) { error = "Paste a link first"; return }
        loading = true
        scope.launch {
            val r = runCatching { StreamResolver.resolve(context, currentRequest(), thorough = true) }.getOrNull()
            loading = false
            when (r) {
                is Resolution.Playable -> {
                    val req = r.request
                    val fmt = req.resolvedFormat()
                    if (fmt == StreamFormat.DASH || fmt == StreamFormat.SMOOTH || req.drmScheme != dev.asfakul.yumeiro.stream.DrmScheme.NONE) {
                        error = "This stream can't be saved (DASH or protected). You can still play it."
                        return@launch
                    }
                    val headers = req.requestHeaders() + listOfNotNull(req.userAgent?.let { "User-Agent" to it })
                    val title = req.title.ifBlank { Uri.parse(req.url).lastPathSegment?.substringBeforeLast('.') ?: PlatformRules.hostOf(u) ?: "Video" }
                    val link = dev.asfakul.yumeiro.online.OnlineLink(
                        name = title, source = PlatformRules.hostOf(u) ?: "Link", url = req.url, referer = req.referer, quality = 0,
                        isHls = fmt == StreamFormat.HLS, isDash = false, headers = headers,
                    )
                    val err = dev.asfakul.yumeiro.online.download.OnlineDownloads.enqueue(context, title, "Network", u, null, req.logoUrl, emptyMap(), link)
                    if (err != null) error = err else dev.asfakul.yumeiro.ui.components.AppMessages.info(context, "Downloading \"$title\"")
                }
                is Resolution.NeedsBrowser -> pageFinder = r
                is Resolution.Handoff -> { handoff = r; error = r.reason }
                is Resolution.Playlist -> error = "This is a playlist. Open it and download a single channel or file."
                is Resolution.Failed -> error = "${r.title}. ${r.message}"
                null -> error = "Couldn't open the link."
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPlaylist(uri.toString(), uri.lastPathSegment?.substringAfterLast('/'))
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SpotifyGreen, unfocusedBorderColor = DarkOutline,
        focusedLabelColor = SpotifyGreen, cursorColor = SpotifyGreen,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    )

    pageFinder?.let { pf ->
        PageStreamFinderDialog(
            pageUrl = pf.pageUrl,
            reason = pf.reason,
            base = pf.base,
            onPlay = { req -> pageFinder = null; IncomingMedia.open(context, req) },
            onDismiss = { pageFinder = null },
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("stream_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "input") {
            Surface(shape = RoundedCornerShape(24.dp), color = GlassFill, border = null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(SpotifyGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stream, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Network stream", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("HLS · DASH · MP4 · M3U · YouTube · Drive · TeraBox · Torrent", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text("Stream or playlist URL") },
                        placeholder = { Text("https://example.com/live/index.m3u8") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = {
                            if (url.isEmpty()) IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                                Icon(Icons.Default.ContentPaste, "Paste", tint = TextSecondary)
                            } else IconButton(onClick = { url = "" }) { Icon(Icons.Default.Close, "Clear", tint = TextSecondary) }
                        },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("stream_url"),
                    )
                    if (url.isNotBlank() && format == StreamFormat.AUTO) {
                        val detected = when {
                            looksLikeM3uPlaylist(url) -> "M3U playlist"
                            PlatformRules.ruleFor(url.trim()) is PlatformRules.Handoff -> "${PlatformRules.ruleFor(url.trim())?.platform} (opens in its app)"
                            PlatformRules.ruleFor(url.trim()) is PlatformRules.WebSession -> "${PlatformRules.ruleFor(url.trim())?.platform} web page"
                            else -> StreamSniffer.fromUrl(url.trim())?.label ?: "checked when you press Play"
                        }
                        Text("Detected: $detected", color = TextTertiary, fontSize = 12.sp)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(StreamFormat.entries, key = { it.name }) { f ->
                            Chip(f.label.substringBefore(" ("), selected = format == f) { format = f }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { advanced = !advanced }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Headers & DRM", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("User-Agent, Referer, Origin, Cookie", color = TextTertiary, fontSize = 11.sp)
                        Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
                    }
                    AnimatedVisibility(advanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("User-Agent", color = TextSecondary, fontSize = 12.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(UserAgentPreset.entries, key = { it.name }) { p -> Chip(p.label, uaPreset == p && customUa.isBlank()) { uaPreset = p; customUa = "" } }
                            }
                            OutlinedTextField(customUa, { customUa = it }, label = { Text("Custom user agent") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(referer, { referer = it }, label = { Text("Referer") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(origin, { origin = it }, label = { Text("Origin") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(cookie, { cookie = it }, label = { Text("Cookie") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(extraHeaders, { extraHeaders = it }, label = { Text("Extra headers (Key: Value per line)") }, minLines = 2, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            Text("DRM", color = TextSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DrmScheme.entries.forEach { d -> Chip(d.label, drm == d) { drm = d } } }
                            if (drm != DrmScheme.NONE) {
                                OutlinedTextField(license, { license = it }, label = { Text("License server URL") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            }
                            if (drm == DrmScheme.CLEARKEY) {
                                OutlinedTextField(clearKeys, { clearKeys = it }, label = { Text("ClearKey kid:key (hex)") }, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                "Headers are sent with every manifest, segment and key request — use them when a host only accepts its own web player.",
                                color = TextTertiary, fontSize = 11.sp,
                            )
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.testTag("stream_error")) }
                    handoff?.let { h ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(h.url))) }
                                .onFailure { error = "No app on this device can open ${h.platform} links." }
                        }) { Text("Open in ${h.platform}", color = SpotifyGreen, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = ::play,
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("stream_play"),
                        ) {
                            if (loading) CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play", fontWeight = FontWeight.Bold) }
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")) },
                            border = null,
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = TextPrimary),
                            modifier = Modifier.height(48.dp),
                        ) { Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("M3U file") }
                    }
                    if (url.isNotBlank()) OutlinedButton(
                        onClick = ::download, enabled = !loading, border = null,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("stream_download"),
                    ) { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download") }
                    if (url.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { loadPlaylist(url.trim(), Uri.parse(url.trim()).host) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlaylistPlay, null, tint = SpotifyGreen); Spacer(Modifier.width(6.dp))
                                Text("Open as channel list", color = SpotifyGreen, maxLines = 1)
                            }
                            TextButton(
                                onClick = {
                                    val u = url.trim()
                                    if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+").matches(u)) error = "Enter a valid stream URL"
                                    else addTarget = currentRequest().copy(title = guessTitle(u))
                                },
                                modifier = Modifier.testTag("stream_save"),
                            ) {
                                Icon(Icons.Default.PlaylistAdd, null, tint = SpotifyGreen); Spacer(Modifier.width(6.dp))
                                Text("Save", color = SpotifyGreen)
                            }
                        }
                    }
                }
            }
        }

        item(key = "playlists_header") {
            PlaylistsHeader(
                lastSync = lastSync, syncing = syncing, onMarket = onOpenMarket,
                onAddUrl = { addingRemote = true }, onNew = { creatingLocal = true }, onSync = ::sync,
                onRestore = { launchSafe { val n = PlaylistStore.restoreDefaults(app); toast(if (n > 0) "Restored $n default playlists" else "Default playlists enabled") } },
            )
        }
        if (channels.isNotEmpty() && openPlaylistId == null) item(key = "opened_list") {
            OpenedListCard(name = playlistName, count = channels.size, onOpen = { onOpenPlaylist(OPENED_PLAYLIST_SLUG) }, onClose = { StreamRepository.setPlaylist(null, emptyList()) })
        }
        item(key = "market_entry") { MarketEntryCard(installed = playlists.count { it.isMarket }, onClick = onOpenMarket) }
        item(key = "websites") {
            val pal = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GlassFill)
                    .clickable(onClickLabel = "Open websites", onClick = onOpenWebsites).padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("stream_websites"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(pal.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Language, null, tint = pal.accentBright, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Websites", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Save a video site and play its videos here", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
            }
        }
        fun cardFor(p: StreamPlaylist): @Composable () -> Unit = {
            PlaylistCard(
                playlist = p, busy = busyIds[p.id] == true || (syncing && p.isDefault && p.enabled),
                onOpen = { openSaved(p) },
                onToggle = { on ->
                    launchSafe { PlaylistStore.setEnabled(app, p.id, on) }
                    if (!on && openPlaylistId == p.id) StreamRepository.setPlaylist(null, emptyList())
                },
                onRefresh = { openSaved(p, force = true) },
                onEdit = { editing = p },
                onExport = { exportPlaylist(p.id, p.name) },
                onDelete = { deleting = p },
            )
        }
        items(activePlaylists, key = { "pl_${it.id}" }) { p -> cardFor(p)() }
        if (disabledPlaylists.isNotEmpty()) {
            item(key = "disabled_header") { Text("Disabled", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp)) }
            items(disabledPlaylists, key = { "pl_${it.id}" }) { p -> cardFor(p)() }
        }

        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recent streams", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { StreamRepository.clear(context) }) { Text("Clear", color = TextSecondary) }
                }
            }
            items(history, key = { "h_${it.url}" }) { h ->
                StreamRow(
                    h, subtitle = h.url,
                    onClick = { IncomingMedia.open(context, h) },
                    onDelete = { StreamRepository.remove(context, h) },
                    onEdit = {
                        url = h.url; format = h.format; customUa = h.userAgent.orEmpty(); referer = h.referer.orEmpty()
                        origin = h.origin.orEmpty(); cookie = h.cookie.orEmpty()
                        extraHeaders = h.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        drm = h.drmScheme; license = h.drmLicenseUrl.orEmpty(); clearKeys = h.clearKeys.orEmpty()
                    },
                )
            }
        }

        if (channels.isEmpty() && history.isEmpty() && playlists.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LiveTv, null, tint = TextTertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Paste a link or open an M3U playlist", color = TextSecondary)
                    Text("You can also share links to Yumeiro from any app", color = TextTertiary, fontSize = 12.sp)
                }
            }
        }
        item(key = "bottom_space") { Spacer(Modifier.height(96.dp)) }
    }

    if (addingRemote || creatingLocal) {
        val remote = addingRemote
        PlaylistEditDialog(
            playlist = null, remote = remote,
            onDismiss = { addingRemote = false; creatingLocal = false },
            onSave = { name, u ->
                val p = if (remote) PlaylistStore.addRemote(app, name, u!!) else PlaylistStore.createLocal(app, name)
                toast(if (remote) "Added ${p.name} (${p.channelCount} streams)" else "Created ${p.name}")
            },
        )
    }
    editing?.let { p ->
        PlaylistEditDialog(
            playlist = p, remote = !p.isLocal,
            onDismiss = { editing = null },
            onSave = { name, u ->
                PlaylistStore.update(app, p.id, name, u)
                if (openPlaylistId == p.id) {
                    val updated = PlaylistStore.get(p.id)
                    if (updated != null && updated.url != p.url) StreamRepository.setPlaylist(updated.name, PlaylistStore.channels(app, p.id, forceRefresh = true), p.id)
                    else StreamRepository.setPlaylist(name.ifBlank { p.name }, StreamRepository.playlist.value, p.id)
                }
            },
        )
    }
    deleting?.let { p ->
        ConfirmDeleteDialog(p, onDismiss = { deleting = null }) {
            deleting = null
            if (openPlaylistId == p.id) StreamRepository.setPlaylist(null, emptyList())
            launchSafe { PlaylistStore.delete(app, p.id); toast("Deleted ${p.name}") }
        }
    }
    addTarget?.let { item ->
        AddToPlaylistDialog(
            item = item, localPlaylists = localPlaylists,
            onDismiss = { addTarget = null },
            onPick = { p -> addTarget = null; addToLocal(p, item) },
            onCreate = { name ->
                addTarget = null
                launchSafe { val p = PlaylistStore.createLocal(app, name, listOf(item.copy(title = item.displayTitle))); toast("Saved to ${p.name}") }
            },
        )
    }
}

private const val TEMP_EXPORT = "__opened__"

internal fun fileNameFor(name: String?): String =
    (name ?: "playlist").replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().replace(' ', '_').ifBlank { "playlist" }.take(60) + ".m3u"

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = GlassFill, labelColor = TextSecondary,
            selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent,
        ),
        border = null,
    )
}

@Composable
internal fun StreamRow(
    item: StreamRequest,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onEditIcon: (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(GlassFill), contentAlignment = Alignment.Center) {
                if (item.logoUrl != null) AsyncImage(model = item.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                else Icon(Icons.Default.LiveTv, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayTitle, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("Edit", color = TextSecondary, fontSize = 12.sp) }
            if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.PlaylistAdd, "Save to playlist", tint = TextTertiary) }
            if (onEditIcon != null) IconButton(onClick = onEditIcon) { Icon(Icons.Default.Edit, "Edit stream", tint = TextTertiary) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Remove", tint = TextTertiary) }
        }
    }
}
