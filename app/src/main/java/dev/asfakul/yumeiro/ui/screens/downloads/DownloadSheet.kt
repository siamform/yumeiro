package dev.asfakul.yumeiro.ui.screens.downloads

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * OL-6: the download popup, redesigned. Users said the old dialog was a wall of text (tips, paths and two big
 * radio cards before the Download button). This one reads top to bottom in one glance:
 *  1. what is downloading and one summary line (quality, size, free space),
 *  2. Offline / Permanent as one segmented switch with a single line saying what it means,
 *  3. the quality / audio / subtitle chips (OL-5),
 *  4. "Where it goes and tips" folded away,
 *  5. one wide Download button that always stays on screen.
 * Phones get a bottom sheet (thumb reach); TV and wide screens get the same content in a centred dialog.
 */
object DownloadSheet {
    /** Bottom sheet on phones; a centred dialog on TV and on screens 600 dp or wider. */
    fun useSheet(widthDp: Int, tv: Boolean): Boolean = !tv && widthDp < 600

    /** One summary line: "Up to 720p · about 340 MB · 12 GB free". */
    fun summary(quality: String, sizeBytes: Long, freeBytes: Long): String = listOfNotNull(
        quality.takeIf { it.isNotBlank() },
        DownloadPrompt.sizeText(sizeBytes).takeIf { it.isNotBlank() }?.let { "about $it" },
        if (freeBytes > 0) "${DownloadPrompt.sizeText(freeBytes)} free" else null,
    ).joinToString(" · ")

    /** The single line under the Offline / Permanent switch. */
    fun keepNote(keep: Boolean, days: Int): String = when {
        keep -> tr(R.string.kept_until_you_delete_it_the, "Kept until you delete it. The timer never touches it.")
        days > 0 -> "Deleted by itself ${DownloadSettings.autoDeleteLabel(days)} after it finishes."
        else -> tr(R.string.stays_until_you_delete_it_pick, "Stays until you delete it. Pick a timer to clean it up by itself.")
    }

    fun startLabel(count: Int): String = if (count > 1) "Download $count" else "Download"

    /** OWN-18: how long the popup waits for a size before saying it is not known. */
    const val PROBE_MS = 8_000L

    sealed interface SizeState {
        data object Checking : SizeState
        data object Unknown : SizeState
        data class Known(val size: dev.asfakul.yumeiro.online.download.DownloadSize.Size) : SizeState
    }

    /** OWN-18: "Size: 340 MB · 12 GB free", "Size: checking…", or why it is not known. Pure. */
    fun sizeLine(state: SizeState, freeBytes: Long, note: String?): String {
        val free = if (freeBytes > 0) " · ${DownloadPrompt.sizeText(freeBytes)} free" else ""
        return when (state) {
            is SizeState.Known -> "Size: ${dev.asfakul.yumeiro.online.download.DownloadSize.label(state.size)}$free"
            SizeState.Checking -> "Size: checking…$free"
            SizeState.Unknown -> "Size: ${note ?: "the server doesn't say; shown in Downloads once it starts"}$free"
        }
    }

    /** OWN-18: the chips: quality first, then the request's facts, no blanks or repeats. Pure. */
    fun facts(quality: String, more: List<String>): List<String> =
        (listOf(quality) + more).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
}

/** OL-6 host: picks the container and wires the buttons. State lives in [DownloadPromptHost]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadSheetHost(
    req: DownloadPrompt.Request,
    offer: DownloadTracks.Offer?,
    height: Int, onHeight: (Int) -> Unit,
    audio: String?, onAudio: (String?) -> Unit,
    subs: String, onSubs: (String) -> Unit,
    keep: Boolean, onKeep: (Boolean) -> Unit,
    dontAsk: Boolean, onDontAsk: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val p = LocalMtPalette.current
    val tv = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current || dev.asfakul.yumeiro.Edition.isTv
    val content: @Composable () -> Unit = {
        DownloadSheetContent(req, offer, height, onHeight, audio, onAudio, subs, onSubs, keep, onKeep, dontAsk, onDontAsk, onStart)
    }
    if (DownloadSheet.useSheet(LocalConfiguration.current.screenWidthDp, tv)) {
        ModalBottomSheet(
            onDismissRequest = { DownloadPrompt.dismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.surface, shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeSheet,
            dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(),
        ) { content() }
    } else {
        Dialog(onDismissRequest = { DownloadPrompt.dismiss() }) {
            Surface(shape = yuRound(28), color = p.surface, modifier = Modifier.widthIn(max = 560.dp)) {
                Box(Modifier.padding(top = 20.dp)) { content() }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DownloadSheetContent(
    req: DownloadPrompt.Request,
    offer: DownloadTracks.Offer?,
    height: Int, onHeight: (Int) -> Unit,
    audio: String?, onAudio: (String?) -> Unit,
    subs: String, onSubs: (String) -> Unit,
    keep: Boolean, onKeep: (Boolean) -> Unit,
    dontAsk: Boolean, onDontAsk: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    remember { DownloadSettings.init(context); true }
    val days by DownloadSettings.autoDeleteDays.collectAsState()
    val toPhone by DownloadSettings.saveYouTubeToPhone.collectAsState()
    val free by rememberFreeBytes(context, req)
    val phonePath by rememberStoragePath(context, req.folder)
    var details by rememberSaveable { mutableStateOf(false) }
    // OL-6-REVEAL: the details open at the bottom of the scroll area; keep them in view while they unfold.
    val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val yt = req.type == DownloadPrompt.Type.YT_VIDEO || req.type == DownloadPrompt.Type.YT_AUDIO
    val pickQuality = req.startAt != null || req.startWith != null

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("download_confirm")) {
        // 1. What + one summary line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(p.accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Download, null, tint = p.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.download_2, req.what), color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(req.title, color = p.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("download_sheet_title"))
            }
        }
        Spacer(Modifier.height(8.dp))
        // OWN-18: the size for the picked quality, found off the main thread when the source didn't give it.
        val probed by androidx.compose.runtime.produceState<DownloadSheet.SizeState>(
            if (req.sizeBytes > 0) DownloadSheet.SizeState.Known(dev.asfakul.yumeiro.online.download.DownloadSize.Size(req.sizeBytes, exact = true))
            else if (req.sizeFor != null) DownloadSheet.SizeState.Checking else DownloadSheet.SizeState.Unknown,
            req, height,
        ) {
            val probe = req.sizeFor
            if (req.sizeBytes > 0 || probe == null) return@produceState
            value = DownloadSheet.SizeState.Checking
            val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(DownloadSheet.PROBE_MS) { runCatching { probe(height) }.getOrNull() }
            }
            value = if (got != null && got.bytes > 0) DownloadSheet.SizeState.Known(got) else DownloadSheet.SizeState.Unknown
        }
        val sizeBytes = (probed as? DownloadSheet.SizeState.Known)?.size?.bytes ?: 0L
        Column(Modifier.fillMaxWidth().testTag("download_summary")) {
            DownloadFacts(DownloadSheet.facts(req.quality, req.facts))
            Text(DownloadSheet.sizeLine(probed, free, req.sizeNote), color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).testTag("download_size"))
        }
        if (sizeBytes > 0 && free in 1 until sizeBytes)
            Text(stringResource(R.string.not_enough_free_space_for_this), color = if (p.isDark) Color(0xFFFF8A80) else Color(0xFFB3261E), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp).testTag("download_space_warning"))

        // Middle part scrolls on its own so the Download button never leaves the screen.
        Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
            // 2. Keep as: one segmented switch + one line.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("download_keep_switch")) {
                SegmentedButton(
                    selected = !keep, onClick = { onKeep(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("download_mode_offline"),
                ) { Text(stringResource(R.string.offline_copy), maxLines = 1) }
                SegmentedButton(
                    selected = keep, onClick = { onKeep(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Default.PushPin, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("download_mode_permanent"),
                ) { Text(stringResource(R.string.permanent), maxLines = 1) }
            }
            Text(DownloadSheet.keepNote(keep, days), color = p.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp).testTag("download_keep_note"))
            AnimatedVisibility(!keep) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp).testTag("download_timer"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadSettings.AUTO_DELETE_CHOICES.forEach { d ->
                        FilterChip(selected = d == days, onClick = { DownloadSettings.setAutoDeleteDays(context, d) },
                            label = { Text(if (d == 0) "Never" else DownloadSettings.autoDeleteLabel(d), fontSize = 12.sp) },
                            modifier = Modifier.testTag("download_timer_$d"))
                    }
                }
            }

            // 3. Quality / audio / subtitles.
            Spacer(Modifier.height(12.dp))
            if (offer != null && offer.heights.size > 1) TrackChips("Quality", "download_track_quality", listOf(0 to "Best") + offer.heights.map { it to "${it}p" }, height, onHeight)
            else if (pickQuality) QualityPicker(height, onHeight)
            if (offer != null) TrackPicker(offer, audio, onAudio, subs, onSubs)

            // 4. Details folded away.
            Row(
                Modifier.fillMaxWidth().clickable { details = !details }.padding(vertical = 8.dp).testTag("download_details"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.where_it_goes_and_tips), color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (details) tr(R.string.hide_details, "Hide details") else tr(R.string.show_details, "Show details"), tint = p.textTertiary)
            }
            // The requester sits on the AnimatedVisibility itself: its height grows every frame while it unfolds
            // (the inner column has its full size from the first frame), so the scroll follows to the end.
            val unfold = remember { androidx.compose.animation.core.MutableTransitionState(details) }
            // Once fully open, one last scroll so the whole text is in view even if no size change came after.
            androidx.compose.runtime.LaunchedEffect(details) {
                unfold.targetState = details
                if (details) {
                    androidx.compose.runtime.snapshotFlow { unfold.isIdle && unfold.currentState }.first { it }
                    reveal.bringIntoView()
                }
            }
            AnimatedVisibility(
                visibleState = unfold,
                modifier = Modifier.bringIntoViewRequester(reveal)
                    .onSizeChanged { if (details && it.height > 0) scope.launch { reveal.bringIntoView() } },
            ) {
                Column(Modifier.testTag("download_details_body")) {
                    DetailLine(Icons.Default.Folder, "Downloads → ${req.folder}")
                    DetailLine(Icons.Default.PhoneAndroid,
                        if (yt && toPhone) "Phone: ${if (req.type == DownloadPrompt.Type.YT_AUDIO) "Music" else "Movies"}/Yumeiro (your gallery sees it)"
                        else "Phone: ${phonePath ?: "app storage"} (private to Yumeiro)")
                    tipsFor(req.type).forEach { Text("• $it", color = p.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onDontAsk(!dontAsk) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dont_ask_again), color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(dontAsk, onDontAsk, modifier = Modifier.testTag("download_dont_ask"))
            }
        }

        // 5. One wide button that stays in view.
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStart, shape = yuRound(16),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("download_confirm_start"),
        ) {
            Icon(Icons.Default.Download, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(DownloadSheet.startLabel(req.count), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { DownloadPrompt.dismiss(); DownloadsNav.open() }, modifier = Modifier.testTag("download_sheet_settings")) { Text(stringResource(R.string.download_settings)) }
            TextButton(onClick = { DownloadPrompt.dismiss() }, modifier = Modifier.testTag("download_sheet_cancel")) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** OWN-18: quality, sub / dub, language, format and source as small chips under the title. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DownloadFacts(facts: List<String>) {
    if (facts.isEmpty()) return
    val p = LocalMtPalette.current
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().testTag("download_facts"),
    ) {
        facts.forEach { f ->
            Text(f, color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.background(p.accent.copy(alpha = 0.14f), yuRound(8)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val p = LocalMtPalette.current
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = p.textTertiary, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = p.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
