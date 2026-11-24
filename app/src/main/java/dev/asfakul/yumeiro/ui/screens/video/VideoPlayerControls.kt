/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.material.icons.outlined.Movie
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import dev.asfakul.yumeiro.ui.theme.OnAccent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.ui.components.formatDuration
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

enum class ScreenOrientationOption {
    SENSOR,
    LANDSCAPE,
    PORTRAIT
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerTopBar(
    video: VideoItem,
    currentSpeed: Float,
    isPipSupported: Boolean,
    onBackClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSpeedLongClick: () -> Unit = {},
    captionsOn: Boolean = true,
    onCaptionsToggle: () -> Unit = {},
    onAspectRatioClick: () -> Unit,
    onPipClick: () -> Unit,
    onInfoClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Current quality ("Auto · 720p", "1080p"); the quality button shows only when set. */
    qualityLabel: String? = null,
    onQualityClick: () -> Unit = {},
    /** Docked watch-page player (small 16:9 box): slimmer bar, no subtitle line. */
    compact: Boolean = false,
    /** R17: time left on the video sleep timer ("12:30", "End of this video"), or null when off. */
    sleepLabel: String? = null,
    onSleepTimerClick: () -> Unit = {},
    /** A4K-2: the active Anime4K profile ("Off", "Balanced · Sharpen", ...) and the sheet that switches it. */
    enhanceLabel: String? = null,
    onEnhanceClick: () -> Unit = {},
) {
    // OL-7: the buttons get exactly the room they use, at the trailing edge; the title takes the rest.
    val aligned = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.PLAYER_TOPBAR_ALIGN)
    // OL-7-EDGE: icon buttons already carry a 4 dp inset (40 dp inside their 48 dp touch target), so the bar
    // uses Material 3's 4 dp app-bar side padding; with 12 dp the More icon sat 16 dp (icon 25 dp) from the edge.
    val edgeDp = if (compact || (aligned && dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.PLAYER_TOPBAR_EDGE))) 4f else 12f
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val buttonsMaxDp = TopBarFit.buttonsWidth(maxWidth.value, compact, edgeDp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = edgeDp.dp, vertical = if (compact) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(if (compact) 40.dp else 44.dp)
                .testTag("video_player_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close player",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Title and badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("video_player_title")
            )
            if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SpotifyGreen.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = video.resolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // R17: the buttons that fit, in the user's order, then More (the rest + sleep timer).
        // Before, this row scrolled sideways with no hint, so on a portrait phone Speed, PiP, Info
        // and Lock were off-screen (Group 3 proposal). See TopBarFit.
        val order by dev.asfakul.yumeiro.player.VideoPrefs.controls.collectAsState()
        val hidden by dev.asfakul.yumeiro.player.VideoPrefs.hidden.collectAsState()
        val visible = remember(order, hidden, qualityLabel != null, isPipSupported) {
            order.filter { c ->
                c !in hidden &&
                    (c != dev.asfakul.yumeiro.player.VideoPrefs.Control.QUALITY || qualityLabel != null) &&
                    (c != dev.asfakul.yumeiro.player.VideoPrefs.Control.PIP || isPipSupported)
            }
        }
        BoxWithConstraints(
            modifier = if (aligned) Modifier.widthIn(max = buttonsMaxDp.dp).testTag("video_top_buttons")
            else Modifier.weight(1.4f, fill = false)
        ) {
            val fit = TopBarFit.split(visible, maxWidth.value, qualityLabel)
            // OWN-19: the full-screen bar keeps only the main buttons; the docked bar is already small.
            // PC-1: how many stay on the bar is the user's (Settings > Video player > Player controls), default 3.
            val barCount by dev.asfakul.yumeiro.player.VideoPrefs.barCount.collectAsState()
            val split = if (!compact && dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.PLAYER_ESSENTIALS)) TopBarFit.essentials(fit, barCount) else fit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TopBarFit.GAP_DP.dp)
            ) {
                split.inline.forEach { control ->
                when (control) {
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.QUALITY -> {
                        // Video quality (adaptive streams with several variants)
                        if (qualityLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onQualityClick)
                                    .testTag("video_quality_btn")
                            ) {
                                Text(
                                    text = qualityLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.AUDIO -> {
                        // Audio track
                        IconButton(
                            onClick = onAudioTrackClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_audio_track_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Audio tracks",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.SUBTITLES -> {
                        // CC on / off
                        IconButton(
                            onClick = onCaptionsToggle,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_cc_btn")
                        ) {
                            Icon(
                                imageVector = if (captionsOn) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                                contentDescription = if (captionsOn) "Captions on" else "Captions off",
                                tint = if (captionsOn) Color.White else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Subtitles
                        IconButton(
                            onClick = onSubtitleClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_subtitle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Subtitles",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.SPEED -> {
                        // Speed
                        // P-2X: tap opens the speed list; long press (touch or a held OK key) switches 1x / 2x.
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .combinedClickable(
                                    onClick = onSpeedClick,
                                    onLongClick = onSpeedLongClick,
                                    onLongClickLabel = "Switch between 1x and 2x",
                                    role = androidx.compose.ui.semantics.Role.Button,
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("video_speed_btn"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${currentSpeed}x",
                                    color = SpotifyGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.ASPECT -> {
                        // Aspect ratio
                        IconButton(
                            onClick = onAspectRatioClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_aspect_ratio_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect ratio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.PIP -> {
                        // PiP mode
                        if (isPipSupported) {
                            IconButton(
                                onClick = onPipClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_pip_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = "Picture in picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.INFO -> {
                        // Video details
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_info_btn")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Movie,
                                contentDescription = "Video details",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.LOCK -> {
                        // Lock screen
                        IconButton(
                            onClick = onLockClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_screen_lock_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Lock controls",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                }
                VideoPlayerMoreMenu(
                    overflow = split.overflow,
                    qualityLabel = qualityLabel,
                    currentSpeed = currentSpeed,
                    captionsOn = captionsOn,
                    sleepLabel = sleepLabel,
                    onQualityClick = onQualityClick,
                    onAudioTrackClick = onAudioTrackClick,
                    onCaptionsToggle = onCaptionsToggle,
                    onSubtitleClick = onSubtitleClick,
                    onSpeedClick = onSpeedClick,
                    onAspectRatioClick = onAspectRatioClick,
                    onPipClick = onPipClick,
                    onInfoClick = onInfoClick,
                    onLockClick = onLockClick,
                    onSleepTimerClick = onSleepTimerClick,
                    enhanceLabel = enhanceLabel,
                    onEnhanceClick = onEnhanceClick,
                )
            }
        }
    }
    }
}

/** R17: the More button at the end of the top row and its labelled menu (see [TopBarFit]). */
@Composable
private fun VideoPlayerMoreMenu(
    overflow: List<dev.asfakul.yumeiro.player.VideoPrefs.Control>,
    qualityLabel: String?,
    currentSpeed: Float,
    captionsOn: Boolean,
    sleepLabel: String?,
    onQualityClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onCaptionsToggle: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onPipClick: () -> Unit,
    onInfoClick: () -> Unit,
    onLockClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    enhanceLabel: String? = null,
    onEnhanceClick: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("video_more_btn")
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = if (sleepLabel != null) "More options, sleep timer on" else "More options",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                // A running sleep timer is visible without opening the menu.
                if (sleepLabel != null) Box(
                    Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(SpotifyGreen).testTag("video_sleep_dot")
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("video_more_menu")) {
            @Composable
            fun item(label: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, trailing: String? = null, onClick: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = trailing?.let { t -> { Text(t, color = SpotifyGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) } },
                    onClick = { open = false; onClick() },
                    modifier = Modifier.testTag(tag),
                )
            }
            overflow.forEach { c ->
                when (c) {
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.QUALITY ->
                        item("Quality", "video_quality_btn_menu", Icons.Default.HighQuality, qualityLabel, onQualityClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.AUDIO ->
                        item("Audio tracks", "video_audio_track_btn_menu", Icons.Default.Audiotrack, onClick = onAudioTrackClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.SUBTITLES -> {
                        item(if (captionsOn) "Turn captions off" else "Turn captions on", "video_cc_btn_menu",
                            if (captionsOn) Icons.Default.ClosedCaptionDisabled else Icons.Default.ClosedCaption, onClick = onCaptionsToggle)
                        item("Subtitles", "video_subtitle_btn_menu", Icons.Default.Subtitles, onClick = onSubtitleClick)
                    }
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.SPEED ->
                        item("Speed", "video_speed_btn_menu", Icons.Default.Speed, "${currentSpeed}x", onSpeedClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.ASPECT ->
                        item("Aspect ratio", "video_aspect_ratio_btn_menu", Icons.Default.AspectRatio, onClick = onAspectRatioClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.PIP ->
                        item("Picture-in-picture", "video_pip_btn_menu", Icons.Default.PictureInPicture, onClick = onPipClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.INFO ->
                        item("Video details", "video_info_btn_menu", androidx.compose.material.icons.Icons.Outlined.Movie, onClick = onInfoClick)
                    dev.asfakul.yumeiro.player.VideoPrefs.Control.LOCK ->
                        item("Lock controls", "video_screen_lock_btn_menu", Icons.Default.LockOpen, onClick = onLockClick)
                }
            }
            if (overflow.isNotEmpty()) HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            item("Sleep timer", "video_sleep_timer_menu", Icons.Default.Bedtime, sleepLabel, onSleepTimerClick)
            if (enhanceLabel != null) item("Anime4K", "video_anime4k_menu", androidx.compose.material.icons.Icons.Default.AutoFixHigh, enhanceLabel, onEnhanceClick)
        }
    }
}

@Composable
fun VideoPlayerCenterControls(
    isPlaying: Boolean,
    onRewind10s: () -> Unit,
    onPlayPause: () -> Unit,
    onForward10s: () -> Unit,
    modifier: Modifier = Modifier,
    isEnded: Boolean = false,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    /** Docked watch-page player: smaller buttons, prev / next live in the strip under the player. */
    compact: Boolean = false,
    /** Lets a remote / keyboard land on play-pause when the controls open (TV). */
    playFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PC-1 (owner list 11): each of these can be switched off in Settings > Video player.
        val off by dev.asfakul.yumeiro.player.VideoPrefs.extrasOff.collectAsState()
        val skips = !compact && dev.asfakul.yumeiro.player.VideoPrefs.Extra.PREV_NEXT !in off
        val seeks = dev.asfakul.yumeiro.player.VideoPrefs.Extra.SEEK !in off
        if (skips) CenterRoundButton(Icons.Default.SkipPrevious, "Previous video", onPrevious, "video_previous_btn", 46, 28, enabledLook = hasPrevious)
        if (seeks) CenterRoundButton(Icons.Default.FastRewind, "Rewind 10s", onRewind10s, "video_rewind_10s_btn", if (compact) 38 else 52, if (compact) 20 else 28)

        // Play / Pause / Replay
        Box(
            modifier = Modifier
                .size(if (compact) 50.dp else 72.dp)
                .clip(CircleShape)
                .background(Color.White)
                .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
                .clickable { onPlayPause() }
                .testTag("video_play_pause_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isEnded -> Icons.Default.Replay
                    isPlaying -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    isEnded -> "Replay"
                    isPlaying -> "Pause"
                    else -> "Play"
                },
                tint = Color(0xFF1C1636),
                modifier = Modifier.size(if (compact) 28.dp else 38.dp)
            )
        }

        if (seeks) CenterRoundButton(Icons.Default.FastForward, "Forward 10s", onForward10s, "video_forward_10s_btn", if (compact) 38 else 52, if (compact) 20 else 28)
        if (skips) CenterRoundButton(Icons.Default.SkipNext, "Next video", onNext, "video_next_btn", 46, 28, enabledLook = hasNext)
    }
}

@Composable
private fun CenterRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
    size: Int,
    iconSize: Int,
    enabledLook: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .minimumInteractiveComponentSize()
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabledLook) 1f else 0.4f),
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Shown when a video ends and the queue has a next video: counts down 3 s, then plays it. */
@Composable
fun UpNextCard(
    next: dev.asfakul.yumeiro.data.model.VideoItem,
    secondsLeft: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = Color(0xFF2C2452),
        shadowElevation = 10.dp,
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("video_up_next_card")
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = secondsLeft.toString(),
                    color = Color(0xFF1C1636),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    modifier = Modifier.testTag("video_up_next_countdown")
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Up next", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    next.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.testTag("video_up_next_cancel")) {
                Text("Cancel", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.material3.TextButton(onClick = onPlayNow, modifier = Modifier.testTag("video_up_next_play_now")) {
                Text("Play now", color = Color(0xFFB9A6FF), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VideoPlayerBottomBar(
    currentPositionMs: Long,
    durationMs: Long,
    sliderProgress: Float,
    isUserSeeking: Boolean,
    onSeekProgressChange: (Float) -> Unit,
    onSeekProgressFinish: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Docked watch-page player: one slim row (time · seek bar · fullscreen). */
    compact: Boolean = false,
    /** Live stream: a LIVE badge instead of a length. */
    isLive: Boolean = false,
    /** Still opening / buffering (the length shows 00:00 until it is known). */
    isLoading: Boolean = false,
    /** BUG-5 TV-5: TV layout is always full screen, so it has no full-screen toggle. */
    showFullscreenButton: Boolean = true,
) {
    // PC-1 (owner list 11): the full-screen player's button can be switched off; the docked player keeps it,
    // it is the only way up to full screen there.
    val extrasOff by dev.asfakul.yumeiro.player.VideoPrefs.extrasOff.collectAsState()
    @Suppress("NAME_SHADOWING")
    val showFullscreenButton = showFullscreenButton && (compact || dev.asfakul.yumeiro.player.VideoPrefs.Extra.FULLSCREEN !in extrasOff)
    if (compact) {
        val known = durationMs.takeIf { it > dev.asfakul.yumeiro.player.SeekMath.UNKNOWN }
        val pos = if (isUserSeeking) dev.asfakul.yumeiro.player.SeekMath.positionFor(sliderProgress, known) ?: currentPositionMs else currentPositionMs
        Row(
            modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatDuration(pos), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = if (isUserSeeking) sliderProgress else dev.asfakul.yumeiro.player.SeekMath.fraction(currentPositionMs, known),
                onValueChange = onSeekProgressChange,
                onValueChangeFinished = onSeekProgressFinish,
                enabled = known != null,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = SpotifyGreen, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp).testTag("video_seekbar")
            )
            if (known != null) Text(formatDuration(durationMs), color = TextSecondary, fontSize = 11.sp)
            else DurationPending(isLive = isLive, isLoading = isLoading)
            if (showFullscreenButton) IconButton(onClick = onToggleFullscreen, modifier = Modifier.testTag("video_fullscreen_btn")) {
                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (isFullscreen) "Exit fullscreen" else "Enter fullscreen", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val known = durationMs.takeIf { it > dev.asfakul.yumeiro.player.SeekMath.UNKNOWN }
        val displayPos = if (isUserSeeking) {
            dev.asfakul.yumeiro.player.SeekMath.positionFor(sliderProgress, known) ?: currentPositionMs
        } else currentPositionMs
        val remainingMs = ((known ?: displayPos) - displayPos).coerceAtLeast(0L)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayPos),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (known != null) Text(
                    text = "-${formatDuration(remainingMs)}  /  ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                ) else DurationPending(isLive = isLive, isLoading = isLoading)
                if (showFullscreenButton) IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("video_fullscreen_btn")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        val currentRatio = dev.asfakul.yumeiro.player.SeekMath.fraction(currentPositionMs, known)
        Slider(
            value = if (isUserSeeking) sliderProgress else currentRatio,
            onValueChange = onSeekProgressChange,
            onValueChangeFinished = onSeekProgressFinish,
            enabled = known != null,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SpotifyGreen,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_seekbar")
        )
    }
}

@Composable
fun LockedFloatingUnlockButton(
    isVisible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.78f),
            tonalElevation = 8.dp,
            modifier = Modifier
                .clickable { onUnlock() }
                .testTag("video_screen_unlock_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock controls",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Controls locked · tap to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * What the time label shows while the length isn't known: a LIVE badge for live streams, and
 * otherwise "00:00" (master prompt, phase 2: show 00:00 instead of "--:--" when a video starts).
 * The old "--:--" read as broken; the later pulsing pill was an endless animation that kept the
 * frame clock busy for as long as a stream had no length.
 */
@Composable
private fun DurationPending(isLive: Boolean, @Suppress("UNUSED_PARAMETER") isLoading: Boolean) {
    if (isLive) Text(
        "LIVE",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .background(Color(0xFFE5484D), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .testTag("video_live_badge"),
    ) else Text(
        formatDuration(0L),
        color = TextSecondary,
        fontSize = 11.sp,
        modifier = Modifier.testTag("video_duration_pending"),
    )
}
