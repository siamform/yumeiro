/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Bedtime
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.lyrics.Lyrics
import dev.asfakul.yumeiro.lyrics.LyricsSource
import dev.asfakul.yumeiro.lyrics.TrackDetails
import dev.asfakul.yumeiro.lyrics.TrackDetailsRepository
import dev.asfakul.yumeiro.ui.components.formatFileSize
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.data.model.PlayerRepeatMode
import dev.asfakul.yumeiro.ui.components.EqualizerBars
import dev.asfakul.yumeiro.ui.components.GlassIconButton
import dev.asfakul.yumeiro.ui.components.TrackArtwork
import dev.asfakul.yumeiro.ui.components.drawVinylRings
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.theme.ChipSelectedColor
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnChipSelectedColor
import dev.asfakul.yumeiro.ui.theme.OverlineStyle
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.artGradientFor
import dev.asfakul.yumeiro.ui.theme.glass
import dev.asfakul.yumeiro.ui.theme.pageBackground
import dev.asfakul.yumeiro.ui.theme.rememberArtBrush

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onDismissError: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    positionMs: () -> Long = { playbackState.currentPositionMs },
    onMoreOptions: (() -> Unit)? = null,
    sleepTimer: dev.asfakul.yumeiro.player.SleepTimer? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }
    var showSleepSheet by remember { mutableStateOf(false) }
    val sleepState = sleepTimer?.state?.collectAsState()?.value
    val sleepLeft = rememberSleepRemaining(sleepState)

    val track = playbackState.currentTrack
    // 0 = artwork, 1 = lyrics, 2 = up next, 3 = info. Tapping the active pill returns to artwork.
    var panel by rememberSaveable { mutableIntStateOf(PANEL_ART) }
    val glow = remember(track?.id) { artGradientFor(track?.id).start }
    val context = LocalContext.current
    var lyricsReload by remember { mutableIntStateOf(0) }
    val lyricsState by produceState(LyricsUi(loading = true), track?.id, track?.uri, lyricsReload) {
        value = LyricsUi(loading = true)
        value = if (track == null) LyricsUi() else LyricsUi(
            lyrics = runCatching { TrackDetailsRepository.loadLyrics(context, track.id, track.uri, track.title, track.artist, track.album, track.durationMs.takeIf { it > 0 } ?: playbackState.durationMs) }.getOrNull()
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pageBackground()
            // Soft artwork-colored glow at the top (single cached radial draw, no blur).
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.22f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("now_playing_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Controls are anchored to the bottom (thumb reach); the flexible area sits above them.
            verticalArrangement = Arrangement.Bottom
        ) {
            // Header: collapse · 再生中 NOW PLAYING · more. Sleep and EQ moved to the labelled tool rail.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dev.asfakul.yumeiro.ui.components.YumeIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse player",
                    onClick = onClose,
                    modifier = Modifier.testTag("now_playing_collapse_button")
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EqualizerBars(color = SpotifyGreen, playing = playbackState.isPlaying, barCount = 4)
                        Spacer(Modifier.width(8.dp))
                        if (dev.asfakul.yumeiro.ui.components.kanaEnabled()) Text(
                            if (sleepLeft != null) "おやすみ · " else "再生中 · ", style = OverlineStyle, color = SpotifyGreen, maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                        Text(if (sleepLeft != null) "Sleep timer · ${sleepLeft}" else "Now playing", style = OverlineStyle,
                            color = SpotifyGreen, maxLines = 1)
                    }
                    val from = track?.album?.takeIf { it.isNotBlank() }
                    if (from != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(from, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onMoreOptions != null) {
                    dev.asfakul.yumeiro.ui.components.YumeIconButton(
                        icon = Icons.Default.MoreHoriz,
                        contentDescription = "Song options",
                        onClick = onMoreOptions,
                        modifier = Modifier.testTag("now_playing_more_button")
                    )
                } else Spacer(Modifier.width(48.dp))
            }

            AnimatedVisibility(visible = playbackState.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A1525))
                        .padding(12.dp)
                        .testTag("now_playing_error_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF8FA3), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playbackState.errorMessage ?: "Playback error",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD6DD)
                        )
                        val tip = remember(playbackState.errorMessage) {
                            dev.asfakul.yumeiro.ui.components.ErrorHelp.suggestionsFor(playbackState.errorMessage.orEmpty()).firstOrNull()
                        }
                        if (tip != null) Text("Tip: $tip", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB3C0), modifier = Modifier.testTag("now_playing_error_tip"))
                    }
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismissError).testTag("now_playing_error_dismiss"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "Dismiss error", tint = Color(0xFFFFD6DD), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Flexible content area: fills everything between the header and the controls.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Crossfade(targetState = panel, animationSpec = tween(220), label = "nowPlayingPanel") { p ->
                    when (p) {
                        PANEL_LYRICS -> LyricsPanel(
                            state = lyricsState,
                            trackId = track?.id,
                            positionMs = positionMs,
                            onSeek = onSeek,
                            onReload = { lyricsReload++ },
                        )
                        PANEL_QUEUE -> QueuePanel(playbackState, onPlayQueueIndex)
                        PANEL_INFO -> if (track != null) SongInfoPanel(track) else EmptyPanel("Nothing is playing")
                        else -> ArtworkPanel(
                            track = track,
                            lyrics = lyricsState.lyrics,
                            positionMs = positionMs,
                            onOpenLyrics = { panel = PANEL_LYRICS },
                        )
                    }
                }
            }

            // Title / artist / favorite
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title?.takeIf { it.isNotBlank() } ?: if (track != null) "Untitled" else "Select a song", style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        // Long titles drift slowly instead of being cut off (still when reduced motion is on).
                        modifier = Modifier.testTag("now_playing_title")
                            .then(if (dev.asfakul.yumeiro.ui.components.reducedMotion()) Modifier else Modifier.basicMarquee(initialDelayMillis = 2000, velocity = 28.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            track?.artist?.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) } ?: "Unknown artist",
                            track?.album?.takeIf { it.isNotBlank() && !it.equals("Unknown Album", true) },
                        ).joinToString(" \u00B7 "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.07f),
                        color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (track != null) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onToggleFavorite(track.id) }
                            .testTag("now_playing_favorite_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (track.isFavorite) SpotifyGreen else TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SeekBar(durationMs = playbackState.durationMs, positionMs = positionMs, onSeek = onSeek)
            Spacer(Modifier.height(10.dp))

            // Transport: shuffle · prev · PLAY · next · repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundControl(
                    Icons.Default.Shuffle, "Shuffle", if (playbackState.isShuffleEnabled) SpotifyGreen else TextSecondary,
                    22, onToggleShuffle, "now_playing_shuffle_button"
                )
                RoundControl(Icons.Default.SkipPrevious, "Previous track", TextPrimary, 34, onPrevious, "now_playing_previous_button", 56)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(ChipSelectedColor)
                        .clickable(role = Role.Button) { onTogglePlayPause() }
                        .testTag("now_playing_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = OnChipSelectedColor,
                        modifier = Modifier.size(38.dp)
                    )
                }
                RoundControl(Icons.Default.SkipNext, "Next track", TextPrimary, 34, onNext, "now_playing_next_button", 56)
                val (repeatIcon, repeatDesc) = when (playbackState.repeatMode) {
                    PlayerRepeatMode.OFF -> Icons.Default.Repeat to "Repeat off"
                    PlayerRepeatMode.ALL -> Icons.Default.Repeat to "Repeat all"
                    PlayerRepeatMode.ONE -> Icons.Default.RepeatOne to "Repeat one"
                }
                RoundControl(
                    repeatIcon, repeatDesc, if (playbackState.repeatMode == PlayerRepeatMode.OFF) TextSecondary else SpotifyGreen,
                    22, onCycleRepeat, "now_playing_repeat_button"
                )
            }

            Spacer(Modifier.height(18.dp))
            // Tool rail: every Now Playing tool, always labelled (Lyrics · Up next · Info · Sleep · EQ).
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(dev.asfakul.yumeiro.ui.theme.Yume.radiusLg))
                    .background(GlassFill).padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RailTool(Icons.Default.Lyrics, "Lyrics", selected = panel == PANEL_LYRICS,
                    onClick = { panel = if (panel == PANEL_LYRICS) PANEL_ART else PANEL_LYRICS },
                    modifier = Modifier.weight(1f).testTag("now_playing_lyrics_button"), description = "Toggle lyrics")
                RailTool(Icons.AutoMirrored.Filled.QueueMusic, "Up next", selected = panel == PANEL_QUEUE,
                    onClick = { panel = if (panel == PANEL_QUEUE) PANEL_ART else PANEL_QUEUE },
                    modifier = Modifier.weight(1f).testTag("now_playing_queue_toggle_button"), description = "Toggle queue")
                RailTool(Icons.Default.Info, "Info", selected = panel == PANEL_INFO,
                    onClick = { panel = if (panel == PANEL_INFO) PANEL_ART else PANEL_INFO },
                    modifier = Modifier.weight(1f).testTag("now_playing_info_button"), description = "Toggle Song Info")
                if (sleepTimer != null) {
                    RailTool(Icons.Default.Bedtime, sleepLeft ?: "Sleep", selected = sleepState != null,
                        onClick = { showSleepSheet = true },
                        modifier = Modifier.weight(1f).testTag("now_playing_sleep_button"),
                        description = if (sleepLeft != null) "Sleep timer: $sleepLeft" else "Sleep timer",
                        dot = if (sleepState != null) "sleep_timer_active_dot" else null)
                }
                RailTool(Icons.Default.Equalizer, "EQ", selected = false, onClick = onOpenEqualizer,
                    modifier = Modifier.weight(1f).testTag("now_playing_equalizer_button"), description = "Equalizer")
            }
        }
        if (showSleepSheet && sleepTimer != null) {
        SleepTimerSheet(
            state = sleepState,
            onStart = sleepTimer::start,
            onEndOfTrack = sleepTimer::endOfTrack,
            onExtend = sleepTimer::extend,
            onCancel = sleepTimer::cancel,
            onDismiss = { showSleepSheet = false },
        )
    }
}
}

private const val PANEL_ART = 0
private const val PANEL_LYRICS = 1
private const val PANEL_QUEUE = 2
private const val PANEL_INFO = 3

@Immutable
private data class LyricsUi(val loading: Boolean = false, val lyrics: Lyrics? = null)

@Composable
private fun ArtworkPanel(track: AudioTrack?, lyrics: Lyrics?, positionMs: () -> Long, onOpenLyrics: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        val artBrush = rememberArtBrush(track?.id)
        // Artwork grows to the available height (max 360dp) and sits right above the controls.
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 360.dp)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .shadow(20.dp, dev.asfakul.yumeiro.ui.theme.Yume.shapeXl, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(dev.asfakul.yumeiro.ui.theme.Yume.shapeXl)
                .background(artBrush)
                .drawBehind { drawVinylRings() }
                .testTag("now_playing_artwork_box"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
            if (!track?.albumArtUri.isNullOrBlank()) {
                AsyncImage(
                    model = track?.albumArtUri,
                    contentDescription = track?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Live lyric line preview (synced lyrics only); tap opens the full lyrics view.
        if (lyrics != null && lyrics.isSynced) {
            val idx by remember(lyrics) { derivedStateOf { lyrics.indexAt(positionMs()) } }
            val line = lyrics.lines.getOrNull(idx)?.text?.takeIf { it.isNotBlank() } ?: "\u266A"
            Spacer(Modifier.height(16.dp))
            Text(
                line,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenLyrics)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("now_playing_lyric_preview")
            )
        }
    }
}

@Composable
private fun LyricsPanel(
    state: LyricsUi,
    trackId: String?,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTrackId by rememberUpdatedState(trackId)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = currentTrackId
        if (uri != null && id != null) {
            scope.launch {
                val ok = TrackDetailsRepository.importLyrics(context, id, uri)
                if (ok) dev.asfakul.yumeiro.ui.components.AppMessages.success(context, "Lyrics imported")
                else dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "That file has no readable lyrics")
                if (ok) onReload()
            }
        }
    }
    val launchImport: () -> Unit = {
        runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure {
            dev.asfakul.yumeiro.ui.components.AppMessages.error(context, "No file picker available")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .glass(RoundedCornerShape(28.dp))
            .testTag("now_playing_lyrics_panel")
    ) {
        val lyrics = state.lyrics
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            lyrics == null -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Lyrics, null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No lyrics found", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Embed lyrics in the file's tags, put a .lrc file with the same name next to the song, or import one.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                PillButton(Icons.Default.FileOpen, "Import .lrc", selected = true, onClick = launchImport,
                    modifier = Modifier.widthIn(min = 160.dp).testTag("lyrics_import_button"))
            }
            else -> {
                SyncedLyricsList(lyrics, positionMs, onSeek, Modifier.weight(1f).fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        (if (lyrics.isSynced) "Synced \u00B7 " else "") + lyrics.source.label,
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (lyrics.source == LyricsSource.IMPORTED) {
                        TextButton(onClick = {
                            val id = currentTrackId ?: return@TextButton
                            scope.launch { TrackDetailsRepository.removeImportedLyrics(context, id); onReload() }
                        }) { Text("Remove", color = TextSecondary, style = MaterialTheme.typography.labelLarge) }
                    }
                    TextButton(onClick = launchImport, modifier = Modifier.testTag("lyrics_import_button")) {
                        Text("Import", color = SpotifyGreen, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(lyrics: Lyrics, positionMs: () -> Long, onSeek: (Long) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Only recomposes when the active line changes, not on every position tick.
    val active by remember(lyrics) { derivedStateOf { lyrics.indexAt(positionMs()) } }
    var userScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userScrolling = true
        else { kotlinx.coroutines.delay(2500); userScrolling = false }
    }
    LaunchedEffect(active, lyrics) {
        if (lyrics.isSynced && active >= 0 && !userScrolling) {
            runCatching { listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.testTag("lyrics_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
            val isActive = lyrics.isSynced && i == active
            val color = when {
                !lyrics.isSynced -> TextPrimary
                isActive -> TextPrimary
                i < active -> TextSecondary
                else -> TextSecondary.copy(alpha = 0.7f)
            }
            Text(
                text = line.text.ifBlank { "\u266A" },
                style = if (lyrics.isSynced) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.ExtraBold else if (lyrics.isSynced) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (line.timeMs != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSeek(line.timeMs) }
                        else Modifier
                    )
                    .padding(vertical = 2.dp)
                    .testTag("lyric_line_$i")
            )
        }
    }
}

@Composable
private fun QueuePanel(playbackState: PlaybackState, onPlayQueueIndex: (Int) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = playbackState.currentQueueIndex.coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Text(
            "Up next \u00B7 ${playbackState.queue.size} songs",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        if (playbackState.queue.isEmpty()) {
            EmptyPanel("The queue is empty")
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("playback_queue_list"),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(playbackState.queue, key = { i, t -> "${i}_${t.id}" }) { index, item ->
                val isCurrent = index == playbackState.currentQueueIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) GlassFill else Color.Transparent)
                        .clickable { onPlayQueueIndex(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("queue_item_$index"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(artUri = item.albumArtUri, key = item.id, size = 44.dp, cornerRadius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title, style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) SpotifyGreen else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(item.artist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isCurrent) EqualizerBars(SpotifyGreen, playing = playbackState.isPlaying)
                    else Text(formatDuration(item.durationMs), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SongInfoPanel(track: AudioTrack) {
    val context = LocalContext.current
    val details by produceState<TrackDetails?>(null, track.uri) {
        value = runCatching { TrackDetailsRepository.loadDetails(context, track.uri) }.getOrNull() ?: TrackDetails()
    }
    val d = details
    val rows = buildList {
        add("Title" to track.title)
        add("Artist" to track.artist)
        add("Album" to track.album)
        d?.albumArtist?.takeIf { it != track.artist }?.let { add("Album artist" to it) }
        d?.composer?.let { add("Composer" to it) }
        d?.genre?.let { add("Genre" to it) }
        d?.year?.let { add("Year" to it) }
        d?.trackNumber?.let { add("Track" to it) }
        d?.discNumber?.let { add("Disc" to it) }
        add("Duration" to formatDuration(track.durationMs))
        d?.format?.let { add("Format" to it) }
        d?.bitrateKbps?.let { add("Bitrate" to "$it kbps") }
        d?.sampleRateHz?.let { add("Sample rate" to String.format(Locale.US, "%.1f kHz", it / 1000f)) }
        d?.bitsPerSample?.let { add("Bit depth" to "$it-bit") }
        if (track.sizeBytes > 0) add("Size" to formatFileSize(track.sizeBytes))
        if (track.playCount > 0) add("Plays" to track.playCount.toString())
        add("Folder" to track.folderName)
        d?.path?.let { add("Path" to it) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .glass(RoundedCornerShape(28.dp))
            .testTag("now_playing_info_panel"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Text("Song info", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
        items(rows, key = { it.first }) { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(104.dp))
                Text(value.ifBlank { "Unknown" }, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            }
        }
        if (d == null) item(key = "loading") {
            Text("Reading tags\u2026", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector, desc: String, tint: Color, iconSize: Int, onClick: () -> Unit, tag: String, size: Int = 48,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(iconSize.dp)) }
}

@Composable
private fun PillButton(
    icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) ChipSelectedColor else GlassFill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (selected) OnChipSelectedColor else TextPrimary
        Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
    }
}

/** One labelled tool of the Now Playing rail; selected = gold tonal pill (same language as the dock). */
@Composable
private fun RailTool(
    icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String? = null, dot: String? = null,
) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val tint = if (selected) (if (p.isDark) p.accentBright else p.accent) else TextPrimary
    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.width(46.dp).height(28.dp).clip(CircleShape)
                .background(if (selected) p.accent.copy(alpha = if (p.isDark) 0.18f else 0.12f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            if (dot != null) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(SpotifyGreen).testTag(dot))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) tint else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Seek bar: 4dp track at 16% contrast, accent fill, 14dp thumb. The live position is read in the
 * draw phase; the time labels recompose at most once per second.
 *
 * Fixed glitches (see [dev.asfakul.yumeiro.player.SeekMath]):
 *  - touch used `x / width` but the track is drawn inset by the thumb radius, so the thumb jumped
 *    sideways on touch; touch now maps through the same inset as drawing;
 *  - after a drag the bar snapped back to the old position until the player caught up; the seek
 *    target is now held until the player reports it (or 2 s pass);
 *  - an unknown duration (C.TIME_UNSET → coerced to 1 ms) drew a full bar; it now stays empty,
 *    seeking is ignored and the label shows "--:--".
 */
@Composable
private fun SeekBar(durationMs: Long, positionMs: () -> Long, onSeek: (Long) -> Unit) {
    val known = durationMs.takeIf { it > dev.asfakul.yumeiro.player.SeekMath.UNKNOWN }
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    var pendingMs by remember { mutableStateOf<Long?>(null) }
    var pendingAt by remember { mutableLongStateOf(0L) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentKnown by rememberUpdatedState(known)
    val accent = SpotifyGreen
    val accent2 = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.accent2
    val trackColor = TextPrimary.copy(alpha = 0.16f)
    val thumb = ChipSelectedColor
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = with(density) { 7.dp.toPx() }

    fun shownMs(): Long = dev.asfakul.yumeiro.player.SeekMath.displayed(positionMs(), pendingMs, pendingAt, System.currentTimeMillis())
    // Releases the held seek target once the player has caught up (state is never written in draw).
    LaunchedEffect(pendingMs) {
        while (pendingMs != null) {
            kotlinx.coroutines.delay(100L)
            if (dev.asfakul.yumeiro.player.SeekMath.settled(positionMs(), pendingMs, pendingAt, System.currentTimeMillis())) pendingMs = null
        }
    }
    fun fraction(): Float = if (dragFraction >= 0f) dragFraction else dev.asfakul.yumeiro.player.SeekMath.fraction(shownMs(), currentKnown)
    fun xToFraction(x: Float, width: Int): Float {
        val usable = (width - inset * 2).coerceAtLeast(1f)
        return ((x - inset) / usable).coerceIn(0f, 1f)
    }
    fun seekTo(f: Float) {
        val target = dev.asfakul.yumeiro.player.SeekMath.positionFor(f, currentKnown) ?: return
        pendingMs = target
        pendingAt = System.currentTimeMillis()
        currentOnSeek(target)
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .testTag("now_playing_seek_slider")
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction(), 0f..1f)
                    setProgress { v -> if (currentKnown == null) false else { seekTo(v); true } }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> seekTo(xToFraction(o.x, size.width)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> if (currentKnown != null) dragFraction = xToFraction(o.x, size.width) },
                        onDragEnd = {
                            if (dragFraction >= 0f) seekTo(dragFraction)
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f },
                    ) { change, _ ->
                        if (currentKnown == null) return@detectHorizontalDragGestures
                        change.consume()
                        dragFraction = xToFraction(change.position.x, size.width)
                    }
                }
                .drawBehind {
                    val h = 4.dp.toPx(); val r = inset
                    val y = size.height / 2f
                    val usable = size.width - r * 2
                    val f = fraction()
                    drawRoundRect(trackColor, Offset(r, y - h / 2), Size(usable, h), CornerRadius(h / 2))
                    drawRoundRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent2, accent), startX = r, endX = r + usable),
                        Offset(r, y - h / 2), Size(usable * f, h), CornerRadius(h / 2)
                    )
                    drawCircle(thumb, radius = r, center = Offset(r + usable * f, y))
                }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val elapsedSec by remember(known) {
                derivedStateOf {
                    val ms = if (dragFraction >= 0f && known != null) (dragFraction * known).toLong()
                        else dev.asfakul.yumeiro.player.SeekMath.clamp(shownMs(), known)
                    ms / 1000L
                }
            }
            Text(if (elapsedSec <= 0L) "00:00" else formatDuration(elapsedSec * 1000L), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(if (known != null) formatDuration(known) else "--:--", style = MaterialTheme.typography.labelMedium, color = TextTertiary, textAlign = TextAlign.End)
        }
    }
}
