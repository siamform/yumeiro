/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.player.VideoPrefs.InfoField
import kotlinx.coroutines.delay

/**
 * Round 13: Settings → Player → Player info overlay. A "Stats for nerds" style card shown with
 * the controls. It reads the player once a second (cheap getters, no work on every frame) and
 * only the fields the user picked.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerInfoOverlay(player: ExoPlayer, title: String, modifier: Modifier = Modifier) {
    val fields by VideoPrefs.infoFields.collectAsState()
    val context = LocalContext.current
    var videoDecoder by remember(player) { mutableStateOf<String?>(null) }
    var audioDecoder by remember(player) { mutableStateOf<String?>(null) }
    var rows by remember(player) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    DisposableEffect(player) {
        val l = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(e: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
            override fun onAudioDecoderInitialized(e: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
        }
        player.addAnalyticsListener(l)
        onDispose { player.removeAnalyticsListener(l) }
    }
    LaunchedEffect(player, fields) {
        while (true) {
            val online = dev.asfakul.yumeiro.online.OnlineSession.state.value
            val link = online?.links?.getOrNull(online.linkIndex)
            val eq = runCatching { dev.asfakul.yumeiro.player.equalizer.EqualizerManager.getInstance(context).equalizerState.value }.getOrNull()
            rows = InfoField.entries.filter { it in fields }.mapNotNull { f ->
                val v = player.videoFormat
                val a = player.audioFormat
                val value: String? = when (f) {
                    InfoField.RESOLUTION -> v?.takeIf { it.width > 0 }?.let { "${it.width}×${it.height}" }
                    InfoField.SOURCE -> link?.let { l -> l.name.ifBlank { l.source } } ?: title
                    InfoField.PROVIDER -> online?.api?.takeIf { link != null } ?: link?.source ?: "This device"
                    InfoField.QUALITY -> v?.let { minOf(it.width, it.height).takeIf { h -> h > 0 } }?.let { h -> dev.asfakul.yumeiro.player.QualityEngine.label(dev.asfakul.yumeiro.player.QualityEngine.snap(h)) }
                    InfoField.VIDEO_CODEC -> v?.let(::codecName)
                    InfoField.AUDIO_CODEC -> a?.let { codecName(it) + (it.channelCount.takeIf { c -> c > 0 }?.let { c -> " · ${c}ch" } ?: "") + (it.sampleRate.takeIf { r -> r > 0 }?.let { r -> " · ${r / 1000.0}kHz" } ?: "") }
                    InfoField.FRAME_RATE -> v?.frameRate?.takeIf { it > 0f }?.let { "%.2f fps".format(it) }
                    InfoField.VIDEO_BITRATE -> v?.let { f2 -> (if (f2.averageBitrate > 0) f2.averageBitrate else if (f2.peakBitrate > 0) f2.peakBitrate else f2.bitrate).takeIf { it > 0 } }?.let { "%.2f Mbps".format(it / 1_000_000.0) }
                    InfoField.BUFFER -> "%.1f s".format(((player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)) / 1000.0)
                    InfoField.DROPPED_FRAMES -> player.videoDecoderCounters?.let { c -> c.ensureUpdated(); "${c.droppedBufferCount} of ${c.renderedOutputBufferCount + c.droppedBufferCount}" }
                    InfoField.DECODER -> listOfNotNull(videoDecoder?.let { "V: " + shortDecoder(it) }, audioDecoder?.let { "A: " + shortDecoder(it) }).joinToString("  ").ifBlank { null }
                    InfoField.SPEED -> "${player.playbackParameters.speed}x"
                    InfoField.AUDIO_TRACK -> a?.let { listOfNotNull(it.label, it.language?.takeIf { l -> l.isNotBlank() && l != "und" }).joinToString(" · ").ifBlank { "Default" } }
                    InfoField.SUBTITLE_TRACK -> player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }?.let { g ->
                        (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }?.let { listOfNotNull(it.label, it.language).joinToString(" · ").ifBlank { "On" } }
                    } ?: "Off"
                    InfoField.AUDIO_BOOST -> eq?.let { e -> if (e.isEnabled && e.bassBoostStrength > 0) "Bass +${e.bassBoostStrength / 10}%" else if (e.isEnabled) "EQ ${e.currentPreset}" else "Off" } ?: "Off"
                }
                value?.let { f.label to it }
            }
            delay(1000)
        }
    }
    PlayerInfoPanel(rows, modifier)
}

/** The panel itself (rows of label → value). Split out so previews and mock shots use the real look. */
@Composable
internal fun PlayerInfoPanel(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    Column(
        modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 8.dp).testTag("player_info_overlay"),
    ) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, color = Color.White.copy(alpha = 0.6f), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(108.dp), maxLines = 1)
                Text(v, color = Color.White, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "video/avc" + "avc1.64001F" → "H.264 (avc1.64001F)". Pure. */
internal fun codecName(f: Format): String {
    val m = (f.sampleMimeType ?: f.containerMimeType).orEmpty()
    val name = when (m) {
        "video/avc" -> "H.264"; "video/hevc" -> "HEVC"; "video/av01" -> "AV1"; "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"; "video/mp4v-es" -> "MPEG-4"; "video/mpeg2" -> "MPEG-2"
        "audio/mp4a-latm" -> "AAC"; "audio/ac3" -> "AC-3"; "audio/eac3" -> "E-AC-3"; "audio/eac3-joc" -> "E-AC-3 Atmos"
        "audio/opus" -> "Opus"; "audio/vorbis" -> "Vorbis"; "audio/flac" -> "FLAC"; "audio/mpeg" -> "MP3"
        "audio/true-hd" -> "TrueHD"; "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"; "audio/raw" -> "PCM"
        else -> m.substringAfter('/').ifBlank { "unknown" }
    }
    return f.codecs?.takeIf { it.isNotBlank() }?.let { "$name ($it)" } ?: name
}

/** "c2.android.avc.decoder" → "c2.android.avc (software)"; vendor names stay as they are. */
internal fun shortDecoder(n: String): String {
    val soft = n.startsWith("c2.android.") || n.startsWith("OMX.google.") || n.contains("ffmpeg", true)
    return n.removeSuffix(".decoder") + if (soft) " (software)" else " (hardware)"
}
