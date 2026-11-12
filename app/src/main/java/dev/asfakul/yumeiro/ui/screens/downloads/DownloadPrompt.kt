/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.downloads

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opens the Downloads page from anywhere (snackbar action, notification, Home, Library). */
object DownloadsNav {
    private val _requests = MutableStateFlow(0L)
    /** Bumped on every open request; the app's NavHost navigates when it changes. */
    val requests: StateFlow<Long> = _requests.asStateFlow()
    fun open() { _requests.value = System.nanoTime() }
    /** Called after navigating, so a re-created NavHost doesn't open it again. */
    fun consumed() { _requests.value = 0L }
}

/**
 * The step before every download: what is saved, in which quality, where it goes, and whether it
 * stays permanently or is an offline copy the auto-delete timer clears. Shown by [DownloadPromptHost];
 * "Don't ask again" turns it off (Settings → Downloads turns it back on).
 */
object DownloadPrompt {
    enum class Type { EPISODES, MOVIE, CURRENT, YT_VIDEO, YT_AUDIO }

    data class Request(
        val type: Type,
        /** Title of the show / video. */
        val title: String,
        /** "12 episodes", "Episode 3", "1080p video", "Audio 160 kbps". */
        val what: String,
        val count: Int,
        /** Folder inside Downloads. */
        val folder: String,
        val quality: String,
        val sizeBytes: Long = 0,
        val defaultKeep: Boolean,
        /** Starts the download; returns an error text or null. */
        val start: (keep: Boolean) -> String?,
    )

    private val _current = MutableStateFlow<Request?>(null)
    val current: StateFlow<Request?> = _current.asStateFlow()
    @Volatile internal var hosts = 0

    fun ask(context: Context, request: Request) {
        DownloadSettings.init(context)
        if (DownloadSettings.askFirst.value && hosts > 0) _current.value = request
        else run(context, request, request.defaultKeep)
    }

    internal fun dismiss() { _current.value = null }

    internal fun run(context: Context, r: Request, keep: Boolean) {
        _current.value = null
        val err = r.start(keep)
        if (err != null) { AppMessages.error(context, err); return }
        val mode = if (keep) "Permanent" else "Offline"
        AppMessages.show(context, "Downloading ${r.what} · $mode · see Downloads", AppMessages.Kind.SUCCESS, "View") { DownloadsNav.open() }
    }

    fun sizeText(b: Long): String = when {
        b <= 0 -> ""
        b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
        else -> String.format(java.util.Locale.US, "%.0f MB", (b / 1048576.0).coerceAtLeast(1.0))
    }
}

/** Renders [DownloadPrompt] requests. Add once near the app root. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadPromptHost() {
    val context = LocalContext.current
    DisposableEffect(Unit) { DownloadPrompt.hosts++; onDispose { DownloadPrompt.hosts-- } }
    val r by DownloadPrompt.current.collectAsState()
    val req = r ?: return
    var keep by remember(req) { mutableStateOf(req.defaultKeep) }
    var dontAsk by remember(req) { mutableStateOf(false) }

    TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.INFO,
        onDismissRequest = { DownloadPrompt.dismiss() },
        modifier = Modifier.testTag("download_confirm"),
        icon = { Icon(Icons.Default.Download, null) },
        title = { Text("Download ${req.what}?", fontWeight = FontWeight.Bold) },
        text = { DownloadConfirmBody(req, keep, { keep = it }, dontAsk, { dontAsk = it }) },
        confirmButton = {
            dev.asfakul.yumeiro.ui.components.DialogConfirmButton(
                if (req.count > 1) "Download ${req.count}" else "Download",
                onClick = {
                    if (dontAsk) DownloadSettings.setAskFirst(context, false)
                    DownloadPrompt.run(context, req, keep)
                },
                modifier = Modifier.testTag("download_confirm_start"),
            )
        },
        dismissButton = {
            Row {
                TextButton(onClick = { DownloadPrompt.dismiss(); DownloadsNav.open() }) { Text("Settings") }
                TextButton(onClick = { DownloadPrompt.dismiss() }) { Text("Cancel") }
            }
        },
    )
}

/** Body of the download confirmation (separate so it can be previewed and tested). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadConfirmBody(req: DownloadPrompt.Request, keep: Boolean, onKeep: (Boolean) -> Unit, dontAsk: Boolean, onDontAsk: (Boolean) -> Unit) {
    val context = LocalContext.current
    remember { DownloadSettings.init(context); true }
    val days by DownloadSettings.autoDeleteDays.collectAsState()
    val toPhone by DownloadSettings.saveYouTubeToPhone.collectAsState()
    val free = remember(req) { OnlineDownloads.freeBytes(context) }
    val yt = req.type == DownloadPrompt.Type.YT_VIDEO || req.type == DownloadPrompt.Type.YT_AUDIO
    val audio = req.type == DownloadPrompt.Type.YT_AUDIO
    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        Text(req.title, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))

        // What + space
        InfoLine(Icons.Default.SdStorage, listOfNotNull(
            "Quality: ${req.quality}",
            DownloadPrompt.sizeText(req.sizeBytes).takeIf { it.isNotBlank() }?.let { "about $it" },
            if (free > 0) "${DownloadPrompt.sizeText(free)} free" else null,
        ).joinToString(" · "))
        if (req.sizeBytes > 0 && free in 1 until req.sizeBytes)
            Text("Not enough free space for this download.", color = if (dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.isDark) Color(0xFFFF8A80) else Color(0xFFB3261E), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(start = 26.dp, top = 2.dp))

        // Where
        Spacer(Modifier.height(10.dp))
        Text("Where it goes", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        InfoLine(Icons.Default.Folder, "Downloads → ${req.folder}  (open it from Home or Library → Videos)")
        if (yt && toPhone) InfoLine(Icons.Default.PhoneAndroid, "Phone: ${if (audio) "Music" else "Movies"}/Yumeiro, so your gallery and other players see it too")
        else InfoLine(Icons.Default.PhoneAndroid, "Phone: ${OnlineDownloads.storagePath(context, req.folder)} (private to Yumeiro)", small = true)

        // Keep as
        Spacer(Modifier.height(12.dp))
        Text("Keep as", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        KeepOption(
            selected = !keep, icon = Icons.Default.Schedule, title = "Offline copy",
            body = if (days > 0) "Deleted automatically ${DownloadSettings.autoDeleteLabel(days)} after it finishes. Good for watch-once episodes."
            else "Auto-delete is off, so it stays until you delete it. Pick a timer below.",
            tag = "download_mode_offline",
        ) { onKeep(false) }
        if (!keep) FlowRow(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DownloadSettings.AUTO_DELETE_CHOICES.forEach { d ->
                FilterChip(selected = d == days, onClick = { DownloadSettings.setAutoDeleteDays(context, d) },
                    label = { Text(if (d == 0) "Never" else DownloadSettings.autoDeleteLabel(d), fontSize = 12.sp) })
            }
        }
        KeepOption(
            selected = keep, icon = Icons.Default.PushPin, title = "Permanent",
            body = "Kept forever, the timer never touches it. You can switch any time in Downloads.",
            tag = "download_mode_permanent",
        ) { onKeep(true) }

        // Tips
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = GlassFill, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tips", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                tipsFor(req.type).forEach { Text("• $it", color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp)) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onDontAsk(!dontAsk) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(dontAsk, onDontAsk, modifier = Modifier.testTag("download_dont_ask"))
            Text("Don't ask again (turn it back on in Settings → Downloads)", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

internal fun tipsFor(type: DownloadPrompt.Type): List<String> = buildList {
    add("Downloads keep going in the background. Progress shows in your notifications and in Downloads.")
    when (type) {
        DownloadPrompt.Type.EPISODES, DownloadPrompt.Type.MOVIE, DownloadPrompt.Type.CURRENT -> {
            add("One episode per extension downloads at a time, so the site doesn't block you.")
            add("A fresh link is found when each episode starts, so long queues don't expire.")
        }
        DownloadPrompt.Type.YT_VIDEO -> add("HD video and its sound download separately and are joined on your phone.")
        DownloadPrompt.Type.YT_AUDIO -> add("Audio only is the smallest download and plays like any song.")
    }
    add("Uses mobile data when Wi-Fi is off. Quality and parallel downloads are in Settings → Downloads.")
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, small: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = TextTertiary, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (small) TextTertiary else TextSecondary, fontSize = if (small) 11.sp else 13.sp, lineHeight = if (small) 14.sp else 17.sp)
    }
}

@Composable
private fun KeepOption(selected: Boolean, icon: ImageVector, title: String, body: String, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = if (selected) SpotifyGreen.copy(alpha = 0.12f) else GlassFill,
        border = if (selected) BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick)
            Icon(icon, null, tint = if (selected) SpotifyGreen else TextTertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(body, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
