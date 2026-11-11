/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.watch

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-page switches shared by Hues, YouTube and Stream (the moon-dial toggles):
 * Light (dim the page around the player), Auto Play (start new episodes / channels by themselves)
 * and Auto Next (next episode when one ends; next channel when a stream can't be opened).
 */
object WatchPrefs {
    private const val PREFS = "watch_prefs"
    private val _lightsOff = MutableStateFlow(false)
    val lightsOff: StateFlow<Boolean> = _lightsOff.asStateFlow()
    private val _autoPlay = MutableStateFlow(true)
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()
    private val _autoNext = MutableStateFlow(true)
    val autoNext: StateFlow<Boolean> = _autoNext.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _autoPlay.value = p.getBoolean("auto_play", true)
        _autoNext.value = p.getBoolean("auto_next", true)
    }

    /** Lights are a per-session thing, like on the web: they come back on next time. */
    fun setLightsOff(v: Boolean) { _lightsOff.value = v }
    fun setAutoPlay(context: Context, v: Boolean) { _autoPlay.value = v; put(context, "auto_play", v) }
    fun setAutoNext(context: Context, v: Boolean) { _autoNext.value = v; put(context, "auto_next", v) }

    private fun put(context: Context, key: String, v: Boolean) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply()
}

/** One server button in [WatchServerBox]. */
data class WatchServer(val label: String, val selected: Boolean, val tag: String, val onClick: () -> Unit)

/** A row of servers (e.g. SUB / DUB for Hues, LINK / MODE for streams). */
data class WatchServerRow(val label: String, val icon: ImageVector, val servers: List<WatchServer>)

/**
 * Yumeiro's "moon dial": a glass card under the player with capsule toggles (Dim, Autoplay,
 * Auto-advance, Skip intros) and round previous / next buttons. Active toggles glow in Tsuki gold.
 */
@Composable
fun WatchControlBar(
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String = "Next",
    previousLabel: String = "Previous",
    autoNextLabel: String = "Auto-advance",
    autoSkip: Boolean? = null,
    onAutoSkip: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    remember { WatchPrefs.init(context); true }
    val lightsOff by WatchPrefs.lightsOff.collectAsState()
    val autoPlay by WatchPrefs.autoPlay.collectAsState()
    val autoNext by WatchPrefs.autoNext.collectAsState()
    val p = LocalMtPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.10f), dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.03f))))
            .border(1.dp, Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.45f), dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.05f), p.accent2.copy(alpha = 0.35f))), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag("watch_control_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlToggle(Icons.Default.DarkMode, "Dim", lightsOff, "watch_light") { WatchPrefs.setLightsOff(!lightsOff) }
            ControlToggle(Icons.Default.PlayCircle, "Autoplay", autoPlay, "watch_autoplay") { WatchPrefs.setAutoPlay(context, !autoPlay) }
            ControlToggle(Icons.Default.SkipNext, autoNextLabel, autoNext, "watch_autonext") { WatchPrefs.setAutoNext(context, !autoNext) }
            if (autoSkip != null) ControlToggle(Icons.Default.FastForward, "Skip intros", autoSkip, "watch_autoskip") { onAutoSkip(!autoSkip) }
        }
        Spacer(Modifier.width(6.dp))
        StepButton(Icons.Default.SkipPrevious, previousLabel, onPrevious, "watch_prev", filled = false)
        Spacer(Modifier.width(6.dp))
        StepButton(Icons.Default.SkipNext, nextLabel, onNext, "watch_next", filled = true)
    }
}

@Composable
private fun ControlToggle(icon: ImageVector, label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(if (on) p.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (on) p.accent.copy(alpha = 0.7f) else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (on) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: (() -> Unit)?, tag: String, filled: Boolean) {
    val p = LocalMtPalette.current
    val enabled = onClick != null
    val brush = dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush()
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(
                when {
                    !enabled -> Modifier.background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.04f))
                    filled -> Modifier.background(brush)
                    else -> Modifier.background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.08f))
                }
            )
            .clickable(enabled = enabled) { onClick?.invoke() }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, label,
            tint = when { !enabled -> dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.25f); filled -> p.onAccent; else -> dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.9f) },
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Yumeiro "source card": a moon-bar header ("✦ 再生中 · NOW PLAYING", the title and a short tip)
 * followed by one row of capsule sources per group (sub / dub, links / modes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchServerBox(watching: String, hint: String, rows: List<WatchServerRow>, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    val brush = dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f), p.accent2.copy(alpha = 0.06f))))
            .border(1.dp, dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .testTag("watch_server_box"),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
            Box(Modifier.width(3.dp).height(44.dp).clip(CircleShape).background(brush))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Now playing", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(watching, color = dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(hint, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.55f), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        rows.filter { it.servers.isNotEmpty() }.forEach { row ->
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Icon(row.icon, null, tint = p.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(row.label, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.servers.forEach { s ->
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .then(if (s.selected) Modifier.background(brush) else Modifier.background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f)).border(1.dp, dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.10f), CircleShape))
                                .clickable(enabled = !s.selected, onClick = s.onClick)
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                                .testTag(s.tag),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (s.selected) {
                                Icon(Icons.Default.PlayArrow, null, tint = p.onAccent, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(s.label, maxLines = 1, color = if (s.selected) p.onAccent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Yumeiro episode board: a kanji overline, range capsules (1–100 …), a number finder and a grid
 * of rounded tiles. The playing tile wears the signature gradient; watched ones get a small moon dot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchNumberGrid(
    title: String,
    count: Int,
    current: Int,
    numberOf: (Int) -> Int,
    onPick: (Int) -> Unit,
    watched: (Int) -> Boolean = { false },
    searchHint: String = "Jump to #",
) {
    val p = LocalMtPalette.current
    val gridBrush = dev.asfakul.yumeiro.ui.theme.rememberSignatureBrush()
    val rangeSize = 100
    val ranges = (count + rangeSize - 1) / rangeSize
    var range by remember(count, current) { mutableIntStateOf((current.coerceAtLeast(0) / rangeSize).coerceIn(0, (ranges - 1).coerceAtLeast(0))) }
    var query by remember { mutableStateOf("") }
    val hit = query.toIntOrNull()?.let { n -> (0 until count).firstOrNull { numberOf(it) == n } }
    if (hit != null && hit / rangeSize != range) range = hit / rangeSize
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("watch_number_grid")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Episodes", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(title, color = dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(10.dp)).background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Box {
                    if (query.isEmpty()) Text(searchHint, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.4f), fontSize = 12.sp, maxLines = 1)
                    BasicTextField(
                        value = query, onValueChange = { v -> query = v.filter(Char::isDigit).take(5) }, singleLine = true,
                        textStyle = TextStyle(color = dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 12.sp),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp).testTag("watch_grid_search"),
                    )
                }
            }
        }
        if (ranges > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(ranges) { r ->
                    val from = numberOf(r * rangeSize)
                    val to = numberOf(minOf(count - 1, r * rangeSize + rangeSize - 1))
                    val sel = r == range
                    Text(
                        "$from–$to", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sel) p.onAccent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.8f),
                        modifier = Modifier.clip(CircleShape).background(if (sel) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.06f)).border(1.dp, if (sel) p.accent else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.10f), CircleShape)
                            .clickable { range = r }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("watch_grid_range"),
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val gap = 6.dp
            val cols = ((maxWidth + gap) / (56.dp + gap)).toInt().coerceAtLeast(5)
            // Half a dp of slack so float rounding never wraps the last tile of a row.
            val tile = (maxWidth - gap * (cols - 1)) / cols - 0.5.dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                val start = range * rangeSize
                for (i in start until minOf(count, start + rangeSize)) {
                    val isCurrent = i == current
                    val isHit = i == hit
                    val seen = watched(i)
                    Box(
                        Modifier
                            .size(tile, 42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (isCurrent) Modifier.background(gridBrush) else Modifier.background(if (seen) dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.03f) else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.07f)))
                            .then(if (isHit && !isCurrent) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(enabled = !isCurrent) { onPick(i) }
                            .testTag("watch_grid_tile"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCurrent) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, "Now playing", tint = p.onAccent, modifier = Modifier.size(14.dp))
                            Text("${numberOf(i)}", color = p.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "${numberOf(i)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (seen) dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.4f) else dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.9f),
                            )
                            if (seen) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
        }
    }
}

/** Search box used by the channel list (Stream watch page). */
@Composable
fun WatchSearchField(query: String, onQuery: (String) -> Unit, hint: String, tag: String, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, color = dev.asfakul.yumeiro.ui.theme.PageInk.copy(alpha = 0.4f), fontSize = 13.sp, maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = dev.asfakul.yumeiro.ui.theme.PageInk, fontSize = 13.sp), cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }
    }
}

/**
 * "Dim": a night veil over everything under the player (the player stays bright). Tap to lift it.
 */
@Composable
fun BoxScope.WatchLightsOverlay() {
    val off by WatchPrefs.lightsOff.collectAsState()
    AnimatedVisibility(off, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { WatchPrefs.setLightsOff(false) }
                .testTag("watch_lights_overlay"),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                Modifier.padding(top = 18.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DarkMode, null, tint = LocalMtPalette.current.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dimmed for focus · tap to brighten", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
