/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import androidx.compose.runtime.remember
import dev.asfakul.yumeiro.ui.theme.OnAccent
import android.net.Uri
import androidx.annotation.OptIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.components.formatFileSize
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.DarkSurface
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val channels: Int,
    val sampleRate: Int,
    val isSelected: Boolean,
    /** False when no decoder on this phone (nor FFmpeg) can play it. */
    val supported: Boolean = true,
    /** Short codec name ("AAC", "E-AC-3", …). */
    val codec: String? = null,
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean
)

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    TsukiDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Adjust video and audio speed seamlessly with pitch preservation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                val speedChunks = speeds.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speedChunks.forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSpeeds.forEach { speed ->
                                val isSelected = currentSpeed == speed
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) SpotifyGreen else DarkSurfaceContainerHigh,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSpeedSelect(speed)
                                            onDismiss()
                                        }
                                        .testTag("speed_chip_${speed}x")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${speed}x",
                                            color = if (isSelected) dev.asfakul.yumeiro.ui.theme.OnAccent else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun AspectRatioDialog(
    currentResizeMode: Int,
    onResizeModeSelect: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit (Letterbox)", "Maintains original aspect ratio with black bars if necessary"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Crop / Fill", "Zooms video to fill the entire screen (eliminates black bars)"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch", "Stretches the video picture to touch all display borders"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "100% Original", "Renders without scaling at native pixel size")
    )

    TsukiDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aspect ratio",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (mode, title, desc) ->
                    val isSelected = currentResizeMode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onResizeModeSelect(mode, title)
                                onDismiss()
                            }
                            .testTag("aspect_ratio_item_$mode")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onResizeModeSelect(mode, title)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SpotifyGreen else TextPrimary
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun AudioTrackDialog(
    audioTracks: List<AudioTrackInfo>,
    onSelectTrack: (groupIndex: Int, trackIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Round 9 redesign: language names instead of raw codes, the playing track is obvious
    // (check + "Playing"), unsupported tracks explain why, and the sheet follows the app theme.
    // Round 12: grouped by language, only the playing one open, "Show more" for long lists.
    val audioToggled = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val audioPages = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    TsukiDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("audio_track_dialog"),
        icon = { Icon(Icons.Default.Audiotrack, null) },
        title = {
            Column {
                Text("Audio", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (audioTracks.size > 1) "${audioTracks.size} tracks · tap one to switch" else "Language and sound of this video",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Normal,
                )
            }
        },
        text = {
            if (audioTracks.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DarkSurfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Audiotrack, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This video has one audio track, so there's nothing to switch. For another language, pick a Dub source in Sources.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trackGroupItems("audio_track", audioTracks.map { it.language }, audioTracks.indexOfFirst { it.isSelected }, audioToggled, audioPages) { index ->
                        val track = audioTracks[index]
                        val isSelected = track.isSelected
                        val channelStr = when (track.channels) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1 surround"
                            8 -> "7.1 surround"
                            else -> if (track.channels > 0) "${track.channels} ch" else null
                        }
                        val langName = track.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
                            runCatching { java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH) }.getOrNull()
                                ?.takeIf { it.isNotBlank() && !it.equals(code, true) } ?: code.uppercase()
                        }
                        val title = track.label?.takeIf { it.isNotBlank() } ?: langName ?: "Track ${index + 1}"
                        val pick = { if (track.supported) { onSelectTrack(track.groupIndex, track.trackIndex); onDismiss() } }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) SpotifyGreen.copy(alpha = 0.14f) else DarkSurfaceContainer,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = track.supported, onClickLabel = "Play $title") { pick() }
                                .testTag("audio_track_item_$index")
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected, onClick = { pick() }, enabled = track.supported,
                                    colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                        color = if (track.supported) TextPrimary else TextSecondary, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            langName?.takeIf { it != title },
                                            track.codec, channelStr,
                                            if (track.sampleRate > 0) "${track.sampleRate / 1000} kHz" else null,
                                            if (!track.supported) "this phone can't decode it" else null,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp
                                    )
                                }
                                if (isSelected) Text("Playing", color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("audio_track_done")) { Text("Done", fontWeight = FontWeight.Bold) }
        },
    )
}

@Composable
fun SubtitleOptionsDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    isSubtitlesDisabled: Boolean,
    externalSubtitleName: String?,
    subtitleDelayMs: Long,
    onPickExternalFile: () -> Unit,
    onSearchOnline: () -> Unit = {},
    onSelectInternalTrack: (groupIndex: Int, trackIndex: Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the subtitle look editor (size, colour, outline, position). */
    onOpenStyle: (() -> Unit)? = null,
    /** Subtitles the source found after playback started (label → load). */
    moreFromSource: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val subToggled = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val subPages = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Subtitles & Captions",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: External Subtitle File
                item {
                    Text(
                        text = "EXTERNAL SUBTITLE FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onPickExternalFile,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_external_subtitle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Load from file (.srt, .vtt, .ass)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSearchOnline,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_online_subtitle_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Search subtitles online", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (externalSubtitleName != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Loaded: $externalSubtitleName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (moreFromSource.isNotEmpty()) item {
                    Text(
                        text = "MORE FROM THIS SOURCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    moreFromSource.forEach { (label, load) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp), color = DarkSurfaceContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { load(); onDismiss() }.testTag("source_subtitle_item"),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (onOpenStyle != null) item {
                    OutlinedButton(
                        onClick = onOpenStyle,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                        modifier = Modifier.fillMaxWidth().testTag("subtitle_style_btn"),
                    ) {
                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Subtitle style: size, colour, position", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Section 2: Tracks
                item {
                    HorizontalDivider(color = DarkOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SUBTITLE TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Disable subtitles option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSubtitlesDisabled) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDisableSubtitles() }
                            .testTag("disable_subtitles_item")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSubtitlesDisabled,
                                onClick = onDisableSubtitles,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subtitles off",
                                color = if (isSubtitlesDisabled) SpotifyGreen else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                trackGroupItems("subtitle_track", subtitleTracks.map { it.language }, if (isSubtitlesDisabled) -1 else subtitleTracks.indexOfFirst { it.isSelected }, subToggled, subPages) { index ->
                    val track = subtitleTracks[index]
                    val isSelected = !isSubtitlesDisabled && track.isSelected
                    val label = track.label?.takeIf { it.isNotBlank() } ?: TrackGroups.languageName(track.language) ?: "Subtitle track ${index + 1}"

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else DarkSurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectInternalTrack(track.groupIndex, track.trackIndex) }
                            .testTag("subtitle_track_item_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectInternalTrack(track.groupIndex, track.trackIndex) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
                                    unselectedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = if (isSelected) SpotifyGreen else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Section 3: Subtitle Delay Adjustment
                item {
                    HorizontalDivider(color = DarkOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUBTITLE SYNC DELAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (subtitleDelayMs >= 0) "+${subtitleDelayMs} ms" else "$subtitleDelayMs ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (subtitleDelayMs == 0L) TextSecondary else SpotifyGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stepper chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs - 1000L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_sub_1s")
                        ) {
                            Text("-1.0s", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs - 250L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_sub_250ms")
                        ) {
                            Text("-250ms", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(0L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_reset")
                        ) {
                            Text("Reset", fontSize = 11.sp, color = SpotifyGreen)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 250L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_add_250ms")
                        ) {
                            Text("+250ms", fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 1000L) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("subtitle_delay_add_1s")
                        ) {
                            Text("+1.0s", fontSize = 11.sp, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = subtitleDelayMs.toFloat(),
                        onValueChange = { onAdjustDelay(it.toLong()) },
                        valueRange = -5000f..5000f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = SpotifyGreen,
                            activeTrackColor = SpotifyGreen,
                            inactiveTrackColor = TextPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_delay_slider")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Done")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

@Composable
fun VideoInfoDialog(
    video: VideoItem,
    videoFormat: Format?,
    audioFormat: Format?,
    onDismiss: () -> Unit,
    /** Live stream / network health, shown for network streams. */
    health: (() -> dev.asfakul.yumeiro.player.PlaybackHealth)? = null,
    dnsSource: ((String) -> String?)? = null,
) {
    var healthNow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(health?.invoke()) }
    if (health != null) androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { healthNow = health(); kotlinx.coroutines.delay(1000L) }
    }
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Video details",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Round 12: streams have no size / length in their VideoItem (both 0), so the
                // values come from what the player actually opened and decoded; anything the
                // source really doesn't say reads "Not provided" instead of a fake "0 MB".
                val snap by dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.collectAsState()
                val rows = videoDetailRows(video, snap, videoFormat, audioFormat)
                rows.forEach { (label, value) -> InfoRow(label = label, value = value) }

                videoFormat?.let { vf ->
                    InfoRow(label = "Video codec", value = vf.sampleMimeType ?: "Hardware H.264/HEVC")
                    if (vf.frameRate > 0) {
                        InfoRow(label = "Frame rate", value = "%.2f fps".format(vf.frameRate))
                    }
                    if (vf.bitrate > 0) {
                        InfoRow(label = "Bitrate", value = "${vf.bitrate / 1000} kbps")
                    }
                }

                audioFormat?.let { af ->
                    InfoRow(label = "Audio codec", value = af.sampleMimeType ?: "AAC / PCM")
                    if (af.channelCount > 0) {
                        val ch = when (af.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo (2.0)"
                            6 -> "5.1 Surround"
                            else -> "${af.channelCount} channels"
                        }
                        InfoRow(label = "Channels", value = ch)
                    }
                }

                if (video.folderName.isNotBlank()) InfoRow(label = "Folder", value = video.folderName)
                if (video.folderPath.isNotBlank()) {
                    InfoRow(label = "Path", value = video.folderPath)
                }
                healthNow?.let { h -> StreamHealthSection(h, dnsSource) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text("Close")
            }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp
    )
}

/**
 * Title, resolution, duration, size and audio for "Video details". Library files bring their own
 * numbers; streams fall back to [snap] (what the player decoded, and the size from the server's
 * response). Pure so it is unit-tested.
 */
internal fun videoDetailRows(
    video: VideoItem,
    snap: dev.asfakul.yumeiro.player.PlaybackDiagnostics.Snapshot,
    videoFormat: Format? = null,
    audioFormat: Format? = null,
): List<Pair<String, String>> {
    val facts = dev.asfakul.yumeiro.player.PlaybackFacts.of(snap)
    val notProvided = if (facts.pending) "Reading…" else "Not provided by the source"
    val w = video.width.takeIf { it > 0 } ?: snap.width.takeIf { it > 0 } ?: videoFormat?.width?.takeIf { it > 0 } ?: 0
    val h = video.height.takeIf { it > 0 } ?: snap.height.takeIf { it > 0 } ?: videoFormat?.height?.takeIf { it > 0 } ?: 0
    val resolution = when {
        w > 0 && h > 0 -> "$w × $h (${dev.asfakul.yumeiro.player.PlaybackFacts.qualityName(w, h)})"
        video.resolution.isNotBlank() -> video.resolution
        else -> notProvided
    }
    val duration = when {
        snap.live -> "Live"
        video.durationMs > 0 -> formatDuration(video.durationMs)
        snap.durationMs > 0 -> formatDuration(snap.durationMs)
        else -> notProvided
    }
    val size = when {
        video.sizeBytes > 0 -> formatFileSize(video.sizeBytes)
        snap.live -> "Live stream"
        facts.size != null -> if (facts.sizeEstimated) "${facts.size} (estimated from bitrate)" else facts.size
        else -> notProvided
    }
    val audioSnap = if (snap.audioLanguage == null && audioFormat != null) snap.copy(
        audioLanguage = audioFormat.language?.takeIf { it.isNotBlank() && !it.equals("und", true) },
        audioLabel = audioFormat.label,
        audioChannels = audioFormat.channelCount.coerceAtLeast(0),
    ) else snap
    val audio = dev.asfakul.yumeiro.player.PlaybackFacts.audioName(audioSnap) ?: if (facts.pending) "Reading…" else "Language not tagged in the stream"
    return listOf(
        "Title" to video.title,
        "Resolution" to resolution,
        "Duration" to duration,
        "File size" to size,
        "Audio" to audio,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PlaybackErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onNextVideo: (() -> Unit)? = null,
    title: String = "Can't play this video",
    /** Full explanation; when null the generic decoder text is shown with [errorMessage] appended. */
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showRetry: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.90f),
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val parsed = remember(detail, errorMessage, title) {
                    dev.asfakul.yumeiro.ui.components.ErrorHelp.parse(
                        detail ?: errorMessage.ifBlank { "This phone's decoders couldn't play this file, or the file is damaged." }
                    ).let { if (it.tips.isEmpty()) it.copy(tips = dev.asfakul.yumeiro.ui.components.ErrorHelp.suggestionsFor("$title $errorMessage")) else it }
                }
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = parsed.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    dev.asfakul.yumeiro.ui.components.ErrorSuggestions(parsed.tips)
                }
                Spacer(modifier = Modifier.height(20.dp))
                // Remote / keyboard: land on the obvious next step so OK works without hunting.
                val primaryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                val keyDriven = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current ||
                    androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
                LaunchedEffect(showRetry, keyDriven) {
                    if (!keyDriven) return@LaunchedEffect
                    for (i in 0 until 10) { if (runCatching { primaryFocus.requestFocus() }.isSuccess) break; kotlinx.coroutines.delay(60) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (showRetry) Modifier else Modifier.focusRequester(primaryFocus))
                            .testTag("error_close_btn")
                    ) {
                        Text("Close", color = TextPrimary)
                    }
                    if (showRetry) Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(primaryFocus)
                            .testTag("error_retry_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth().testTag("error_action_btn")
                    ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
                }
                if (onNextVideo != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onNextVideo,
                        modifier = Modifier.fillMaxWidth().testTag("error_next_video_btn")
                    ) {
                        Text("Skip to next video", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamHealthSection(h: dev.asfakul.yumeiro.player.PlaybackHealth, dnsSource: ((String) -> String?)?) {
    Spacer(modifier = Modifier.height(4.dp))
    Text("Stream health", style = MaterialTheme.typography.titleSmall, color = SpotifyGreen, fontWeight = FontWeight.Bold)
    if (h.height > 0) InfoRow(label = "Playing", value = "${h.width} × ${h.height}" + if (h.videoBitrate > 0) " · ${h.videoBitrate / 1000} kbps" else "")
    InfoRow(label = "Bandwidth", value = if (h.bandwidthBps > 0) "%.1f Mbps (estimated)".format(h.bandwidthBps / 1_000_000.0) else "Measuring…")
    InfoRow(label = "Buffer", value = "%.1f s ahead".format(h.bufferedMs / 1000.0))
    h.liveOffsetMs?.let { InfoRow(label = "Live latency", value = "%.1f s behind live".format(it / 1000.0)) }
    InfoRow(label = "Rebuffers", value = h.rebuffers.toString())
    InfoRow(label = "Dropped frames", value = h.droppedFrames.toString())
    if (h.qualitySwitches > 0) InfoRow(label = "Quality changes", value = h.qualitySwitches.toString())
    if (h.loadErrors > 0) InfoRow(label = "Load retries", value = h.loadErrors.toString())
    h.host?.let { host ->
        InfoRow(label = "Server", value = host)
        dnsSource?.invoke(host)?.let { InfoRow(label = "DNS", value = it) }
    }
}

/** One selectable video quality. `null` height = Auto (adaptive). */
data class QualityOption(val label: String, val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int)

@Composable
fun QualityDialog(
    options: List<QualityOption>,
    selected: QualityOption?,
    autoLabel: String,
    onSelect: (QualityOption?) -> Unit,
    onDismiss: () -> Unit,
) {
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quality", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                item {
                    QualityRow(autoLabel, "Adapts to your connection", selected == null) { onSelect(null); onDismiss() }
                }
                itemsIndexed(options) { _, o ->
                    QualityRow(o.label, if (o.bitrate > 0) "${o.bitrate / 1000} kbps" else null, selected == o) { onSelect(o); onDismiss() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)) { Text("Close") }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp,
    )
}

@Composable
private fun QualityRow(label: String, sub: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp).testTag("quality_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (isSelected) SpotifyGreen else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
    }
}

/** Builds a de-duplicated list of qualities (highest first) from the player's video tracks. */
fun qualityOptions(tracks: androidx.media3.common.Tracks): List<QualityOption> {
    val all = mutableListOf<QualityOption>()
    tracks.groups.forEachIndexed { gi, g ->
        if (g.type != androidx.media3.common.C.TRACK_TYPE_VIDEO) return@forEachIndexed
        for (ti in 0 until g.length) {
            if (!g.isTrackSupported(ti)) continue
            val f = g.getTrackFormat(ti)
            val hgt = minOf(f.height, f.width).takeIf { f.width > 0 && f.height > 0 } ?: f.height
            val label = when {
                hgt <= 0 -> if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else "Track ${ti + 1}"
                f.frameRate >= 49f -> "${hgt}p${f.frameRate.toInt()}"
                else -> "${hgt}p"
            }
            all += QualityOption(label, gi, ti, hgt, f.bitrate)
        }
    }
    return all.sortedWith(compareByDescending<QualityOption> { it.height }.thenByDescending { it.bitrate })
        .distinctBy { it.label }
}
