/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import androidx.compose.material.icons.filled.Remove
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
import dev.asfakul.yumeiro.ui.theme.yuRound

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
                    text = stringResource(R.string.playback_speed),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.adjust_video_and_audio_speed_seamlessly),
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
                                    shape = yuRound(20),
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
                Text(stringResource(R.string.close))
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
    onDismiss: () -> Unit,
    // GROUP K: picture size from the dialog (− / + / Reset), so a TV remote can zoom too. Null = no row.
    zoomScale: Float = 1f,
    onZoom: ((Int) -> Unit)? = null,
) {
    val modes = listOf(
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, tr(R.string.fit_letterbox, "Fit (Letterbox)"), tr(R.string.maintains_original_aspect_ratio_with_black, "Maintains original aspect ratio with black bars if necessary")),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, tr(R.string.crop_fill, "Crop / Fill"), tr(R.string.zooms_video_to_fill_the_entire, "Zooms video to fill the entire screen (eliminates black bars)")),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch", "Stretches the video picture to touch all display borders"),
        Triple(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "100% Original", tr(R.string.renders_without_scaling_at_native_pixel, "Renders without scaling at native pixel size"))
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
                    text = stringResource(R.string.aspect_ratio),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            // GROUP K: scrolls, so the Picture size row is reachable in landscape on small phones.
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (mode, title, desc) ->
                    val isSelected = currentResizeMode == mode
                    Surface(
                        shape = yuRound(12),
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
                if (onZoom != null) AspectZoomRow(zoomScale, onZoom)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text(stringResource(R.string.close))
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
                Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (audioTracks.size > 1) "${audioTracks.size} tracks · tap one to switch" else tr(R.string.language_and_sound_of_this_video, "Language and sound of this video"),
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Normal,
                )
            }
        },
        text = {
          // F15: scrolls, so the compressor controls fit a landscape phone too.
          Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (audioTracks.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(yuRound(14)).background(DarkSurfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Audiotrack, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.this_video_has_one_audio_track),
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
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
                            shape = yuRound(14),
                            color = if (isSelected) SpotifyGreen.copy(alpha = 0.14f) else DarkSurfaceContainer,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(yuRound(14))
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
                                if (isSelected) Text(stringResource(R.string.playing), color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // H-1: the dynamic range compressor, changed live while the video plays.
            dev.asfakul.yumeiro.ui.screens.video.DrcPresetRow()
            // F15: its controls too, live while the video plays.
            dev.asfakul.yumeiro.ui.screens.video.DrcCustomSliders()
          }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("audio_track_done")) { Text(stringResource(R.string.done), fontWeight = FontWeight.Bold) }
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
                    text = stringResource(R.string.subtitles_captions),
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
                        text = stringResource(R.string.external_subtitle_file),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onPickExternalFile,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        shape = yuRound(10),
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
                            text = stringResource(R.string.load_from_file_srt_vtt_ass),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSearchOnline,
                        shape = yuRound(10),
                        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_online_subtitle_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.search_subtitles_online), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (externalSubtitleName != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = yuRound(8),
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
                                    text = stringResource(R.string.loaded, externalSubtitleName),
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
                        text = stringResource(R.string.more_from_this_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    moreFromSource.forEach { (label, load) ->
                        Surface(
                            shape = yuRound(10), color = DarkSurfaceContainer,
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
                        shape = yuRound(10),
                        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                        modifier = Modifier.fillMaxWidth().testTag("subtitle_style_btn"),
                    ) {
                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.subtitle_style_size_colour_position), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Section 2: Tracks
                item {
                    HorizontalDivider(color = DarkOutline)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.subtitle_tracks),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Disable subtitles option
                    Surface(
                        shape = yuRound(10),
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
                                text = stringResource(R.string.subtitles_off),
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
                        shape = yuRound(10),
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
                            text = stringResource(R.string.subtitle_sync_delay),
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
                            shape = yuRound(8),
                            modifier = Modifier.testTag("subtitle_delay_sub_1s")
                        ) {
                            Text(stringResource(R.string.t_1_0s), fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs - 250L) },
                            shape = yuRound(8),
                            modifier = Modifier.testTag("subtitle_delay_sub_250ms")
                        ) {
                            Text(stringResource(R.string.t_250ms), fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(0L) },
                            shape = yuRound(8),
                            modifier = Modifier.testTag("subtitle_delay_reset")
                        ) {
                            Text(stringResource(R.string.reset), fontSize = 11.sp, color = SpotifyGreen)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 250L) },
                            shape = yuRound(8),
                            modifier = Modifier.testTag("subtitle_delay_add_250ms")
                        ) {
                            Text(stringResource(R.string.t_250ms_2), fontSize = 11.sp, color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { onAdjustDelay(subtitleDelayMs + 1000L) },
                            shape = yuRound(8),
                            modifier = Modifier.testTag("subtitle_delay_add_1s")
                        ) {
                            Text(stringResource(R.string.t_1_0s_2), fontSize = 11.sp, color = TextPrimary)
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
                Text(stringResource(R.string.done))
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
                    text = stringResource(R.string.video_details),
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
                    InfoRow(label = tr(R.string.video_codec, "Video codec"), value = vf.sampleMimeType ?: tr(R.string.hardware_h_264_hevc, "Hardware H.264/HEVC"))
                    if (vf.frameRate > 0) {
                        InfoRow(label = tr(R.string.frame_rate, "Frame rate"), value = "%.2f fps".format(vf.frameRate))
                    }
                    if (vf.bitrate > 0) {
                        InfoRow(label = tr(R.string.bitrate, "Bitrate"), value = "${vf.bitrate / 1000} kbps")
                    }
                }

                audioFormat?.let { af ->
                    InfoRow(label = tr(R.string.audio_codec, "Audio codec"), value = af.sampleMimeType ?: tr(R.string.aac_pcm, "AAC / PCM"))
                    if (af.channelCount > 0) {
                        val ch = when (af.channelCount) {
                            1 -> "Mono"
                            2 -> "Stereo (2.0)"
                            6 -> "5.1 Surround"
                            else -> "${af.channelCount} channels"
                        }
                        InfoRow(label = tr(R.string.channels_2, "Channels"), value = ch)
                    }
                }

                if (video.folderName.isNotBlank()) InfoRow(label = "Folder", value = video.folderName)
                if (video.folderPath.isNotBlank()) {
                    InfoRow(label = tr(R.string.path, "Path"), value = video.folderPath)
                }
                healthNow?.let { h -> StreamHealthSection(h, dnsSource) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)
            ) {
                Text(stringResource(R.string.close))
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
    val notProvided = if (facts.pending) "Reading…" else tr(R.string.not_provided_by_the_source, "Not provided by the source")
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
        snap.live -> tr(R.string.live_stream, "Live stream")
        facts.size != null -> if (facts.sizeEstimated) "${facts.size} (estimated from bitrate)" else facts.size
        else -> notProvided
    }
    val audioSnap = if (snap.audioLanguage == null && audioFormat != null) snap.copy(
        audioLanguage = audioFormat.language?.takeIf { it.isNotBlank() && !it.equals("und", true) },
        audioLabel = audioFormat.label,
        audioChannels = audioFormat.channelCount.coerceAtLeast(0),
    ) else snap
    val audio = dev.asfakul.yumeiro.player.PlaybackFacts.audioName(audioSnap) ?: if (facts.pending) "Reading…" else tr(R.string.language_not_tagged_in_the_stream, "Language not tagged in the stream")
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
    title: String = tr(R.string.cant_play_this_video, "Can't play this video"),
    /** Full explanation; when null the generic decoder text is shown with [errorMessage] appended. */
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showRetry: Boolean = true,
) {
    // ERR-CARD-1: the card used to be laid out for a full-screen player. In the short portrait player
    // (about 220 dp) its buttons fell below the player's edge, so "Find the video" could not be tapped.
    // The card now fits the space it has: the text scrolls and the buttons always stay on screen.
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 380.dp
        val room = maxHeight
        Surface(
            shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
            color = Color.Black.copy(alpha = 0.90f),
            tonalElevation = 10.dp,
            modifier = Modifier
                .padding(if (compact) 8.dp else 24.dp)
                .fillMaxWidth(if (compact) 0.96f else 0.85f)
                .heightIn(max = room - if (compact) 16.dp else 48.dp)
                .testTag("error_card")
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
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
                }
                val parsed = remember(detail, errorMessage, title) {
                    dev.asfakul.yumeiro.ui.components.ErrorHelp.parse(
                        detail ?: errorMessage.ifBlank { tr(R.string.this_phones_decoders_couldnt_play_this, "This phone's decoders couldn't play this file, or the file is damaged.") }
                    ).let { if (it.tips.isEmpty()) it.copy(tips = dev.asfakul.yumeiro.ui.components.ErrorHelp.suggestionsFor("$title $errorMessage")) else it }
                }
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
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
                Spacer(modifier = Modifier.height(if (compact) 10.dp else 20.dp))
                // Remote / keyboard: land on the obvious next step so OK works without hunting.
                val primaryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                val keyDriven = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current ||
                    androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
                val hasAction = actionLabel != null && onAction != null
                LaunchedEffect(showRetry, keyDriven, hasAction) {
                    if (!keyDriven) return@LaunchedEffect
                    for (i in 0 until 10) { if (runCatching { primaryFocus.requestFocus() }.isSuccess) break; kotlinx.coroutines.delay(60) }
                }
                // In the compact card the action shares the button row so it is never cut off.
                val actionInRow = compact && hasAction
                val retryInRow = showRetry && !(actionInRow)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (retryInRow || actionInRow) Modifier else Modifier.focusRequester(primaryFocus))
                            .testTag("error_close_btn")
                    ) {
                        Text(stringResource(R.string.close), color = TextPrimary, maxLines = 1)
                    }
                    if (actionInRow && showRetry) androidx.compose.material3.IconButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("error_retry_btn"),
                    ) { Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.retry), tint = TextPrimary) }
                    if (retryInRow) Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(primaryFocus)
                            .testTag("error_retry_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    if (actionInRow) Button(
                        onClick = onAction!!,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.weight(1.4f).focusRequester(primaryFocus).testTag("error_action_btn")
                    ) { Text(actionLabel!!, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
                if (hasAction && !actionInRow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAction!!,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth().testTag("error_action_btn")
                    ) { Text(actionLabel!!, fontWeight = FontWeight.Bold) }
                }
                if (onNextVideo != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onNextVideo,
                        modifier = Modifier.fillMaxWidth().testTag("error_next_video_btn")
                    ) {
                        Text(stringResource(R.string.skip_to_next_video), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamHealthSection(h: dev.asfakul.yumeiro.player.PlaybackHealth, dnsSource: ((String) -> String?)?) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(stringResource(R.string.stream_health), style = MaterialTheme.typography.titleSmall, color = SpotifyGreen, fontWeight = FontWeight.Bold)
    if (h.height > 0) InfoRow(label = tr(R.string.playing, "Playing"), value = "${h.width} × ${h.height}" + if (h.videoBitrate > 0) " · ${h.videoBitrate / 1000} kbps" else "")
    InfoRow(label = tr(R.string.bandwidth, "Bandwidth"), value = if (h.bandwidthBps > 0) "%.1f Mbps (estimated)".format(h.bandwidthBps / 1_000_000.0) else "Measuring…")
    InfoRow(label = tr(R.string.buffer, "Buffer"), value = "%.1f s ahead".format(h.bufferedMs / 1000.0))
    h.liveOffsetMs?.let { InfoRow(label = tr(R.string.live_latency, "Live latency"), value = "%.1f s behind live".format(it / 1000.0)) }
    InfoRow(label = tr(R.string.rebuffers, "Rebuffers"), value = h.rebuffers.toString())
    InfoRow(label = tr(R.string.dropped_frames, "Dropped frames"), value = h.droppedFrames.toString())
    if (h.qualitySwitches > 0) InfoRow(label = tr(R.string.quality_changes, "Quality changes"), value = h.qualitySwitches.toString())
    if (h.loadErrors > 0) InfoRow(label = tr(R.string.load_retries, "Load retries"), value = h.loadErrors.toString())
    h.host?.let { host ->
        InfoRow(label = tr(R.string.server, "Server"), value = host)
        dnsSource?.invoke(host)?.let { InfoRow(label = "DNS", value = it) }
    }
}

/** One selectable video quality. `null` height = Auto (adaptive). */
data class QualityOption(
    val label: String, val groupIndex: Int, val trackIndex: Int, val height: Int, val bitrate: Int,
    /** Round 13: a quality the engine built (another stream / source), not a track of this one. */
    val alt: dev.asfakul.yumeiro.player.QualityEngine.Alt? = null,
    val note: String? = null,
    /**
     * BUG-4 (F4): false = the stream has this quality but no decoder on this device takes it. It is
     * listed (so the list matches what a download would offer) but cannot be picked.
     */
    val playable: Boolean = true,
)

@Composable
fun QualityDialog(
    options: List<QualityOption>,
    selected: QualityOption?,
    autoLabel: String?,
    onSelect: (QualityOption?) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quality), style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                if (autoLabel != null) item {
                    QualityRow(autoLabel, tr(R.string.adapts_to_your_connection, "Adapts to your connection"), selected == null) { onSelect(null); onDismiss() }
                }
                itemsIndexed(options) { _, o ->
                    val sub = o.note ?: if (o.bitrate > 0) "${o.bitrate / 1000} kbps" else null
                    QualityRow(o.label, sub, selected == o || (selected == null && autoLabel == null && o.alt?.current == true), enabled = o.playable) { onSelect(o); onDismiss() }
                }
                if (footer != null) item {
                    Text(footer, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)) { Text(stringResource(R.string.close)) }
        },
        containerColor = DarkSurface,
        tonalElevation = 6.dp,
    )
}

@Composable
private fun QualityRow(label: String, sub: String?, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp).testTag("quality_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = when { isSelected -> SpotifyGreen; enabled -> TextPrimary; else -> TextSecondary }, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (isSelected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.selected), tint = SpotifyGreen, modifier = Modifier.size(20.dp))
    }
}

/** Builds a de-duplicated list of qualities (highest first) from the player's video tracks. */
fun qualityOptions(tracks: androidx.media3.common.Tracks): List<QualityOption> {
    val all = mutableListOf<QualityOption>()
    tracks.groups.forEachIndexed { gi, g ->
        if (g.type != androidx.media3.common.C.TRACK_TYPE_VIDEO) return@forEachIndexed
        for (ti in 0 until g.length) {
            // BUG-4 (F4): a track no decoder here takes used to vanish without a word, so a 4K HEVC
            // or AV1 rendition the download sheet lists was missing from the player. It is now
            // listed, greyed, with the reason; it cannot be picked (the player would fail on it).
            val supported = g.isTrackSupported(ti)
            val f = g.getTrackFormat(ti)
            val hgt = minOf(f.height, f.width).takeIf { f.width > 0 && f.height > 0 } ?: f.height
            val label = when {
                hgt <= 0 -> if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else "Track ${ti + 1}"
                f.frameRate >= 49f -> "${hgt}p${f.frameRate.toInt()}"
                else -> "${hgt}p"
            }
            val note = if (supported) null else "Not playable on this device (no ${codecLabel(f.sampleMimeType ?: f.codecs)} decoder)"
            all += QualityOption(label, gi, ti, hgt, f.bitrate, note = note, playable = supported)
        }
    }
    // A playable track wins over an unplayable one with the same label.
    return all.sortedWith(compareByDescending<QualityOption> { it.height }.thenByDescending { it.playable }.thenByDescending { it.bitrate })
        .distinctBy { it.label }
}

/** "video/hevc" → "HEVC"; "video/av01" → "AV1". Pure. */
internal fun codecLabel(mime: String?): String {
    val m = mime?.lowercase().orEmpty().substringAfter('/')
    return when {
        m.isBlank() -> "matching"
        m.startsWith("hevc") || m.startsWith("hvc") || m.startsWith("hev") -> "HEVC"
        m.startsWith("av01") || m == "av1" -> "AV1"
        m.startsWith("x-vnd.on2.vp9") || m.startsWith("vp9") || m.startsWith("vp09") -> "VP9"
        m.startsWith("avc") -> "H.264"
        m.startsWith("dolby-vision") -> tr(R.string.dolby_vision, "Dolby Vision")
        else -> m.uppercase()
    }
}


/** GROUP K: Zoom − [100 %] + and Reset; the dialog stays open so each press shows its effect. */
@Composable
private fun AspectZoomRow(scale: Float, onZoom: (Int) -> Unit) {
    val atMin = scale <= dev.asfakul.yumeiro.player.VideoZoom.MIN + 0.001f
    val atMax = scale >= dev.asfakul.yumeiro.player.VideoZoom.MAX - 0.001f
    Surface(shape = yuRound(12), color = DarkSurfaceContainer, modifier = Modifier.fillMaxWidth().testTag("aspect_zoom_row")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.picture_size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(dev.asfakul.yumeiro.player.VideoZoom.label(scale), style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    fontSize = 11.sp, modifier = Modifier.testTag("aspect_zoom_value"))
            }
            IconButton(onClick = { onZoom(-1) }, enabled = !atMin, modifier = Modifier.testTag("aspect_zoom_out")) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.smaller_picture), tint = if (atMin) TextSecondary else TextPrimary)
            }
            IconButton(onClick = { onZoom(1) }, enabled = !atMax, modifier = Modifier.testTag("aspect_zoom_in")) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.bigger_picture), tint = if (atMax) TextSecondary else TextPrimary)
            }
            TextButton(onClick = { onZoom(0) }, enabled = !atMin, modifier = Modifier.testTag("aspect_zoom_reset"),
                colors = ButtonDefaults.textButtonColors(contentColor = SpotifyGreen)) { Text(stringResource(R.string.reset)) }
        }
    }
}
