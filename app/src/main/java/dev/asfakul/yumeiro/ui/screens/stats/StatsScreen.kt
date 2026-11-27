/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stats

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.stats.ActivityLog
import dev.asfakul.yumeiro.stats.StatsMath
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * F-17 profile statistics: a GitHub-style activity map of the last weeks (one Canvas, at most 371 cells), streaks,
 * this week / all-time totals, plays per kind and milestones. Everything is read from [ActivityLog] on this
 * device; nothing is sent anywhere. Colour is never the only signal: a legend, the selected day's text and a
 * screen-reader summary say the same thing.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { ActivityLog.ensureLoaded(context); true }
    val days by ActivityLog.days.collectAsState()
    val today = remember { ActivityLog.today() }
    val byDay = remember(days) { days.associateBy { it.epochDay } }
    val streaks = remember(days) { StatsMath.streaks(days, today) }
    val weekStart = remember(today) { StatsMath.gridStart(today, 1) }
    val week = remember(days) { StatsMath.totals(days, weekStart, today) }
    val all = remember(days) { StatsMath.totals(days, Long.MIN_VALUE, Long.MAX_VALUE) }
    val milestones = remember(days) { StatsMath.milestones(days, today) }
    var selected by rememberSaveable { mutableStateOf(today) }
    var confirmClear by remember { mutableStateOf(false) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.ROOT

    LazyColumn(Modifier.fillMaxSize().testTag("stats_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("header") { SubPageHeader(title = "Your activity", subtitle = "Kept only on this device", onBack = onBack) }
        if (days.isEmpty()) {
            item("empty") {
                Text(stringResource(R.string.play_a_song_or_a_video), color = TextSecondary, fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp).testTag("stats_empty"))
            }
        }
        item("cards") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("This week", StatsMath.duration(week.totalMs), Modifier.weight(1f).testTag("stats_week"))
                StatCard("Streak", dayCount(streaks.current), Modifier.weight(1f).testTag("stats_streak"))
                StatCard("Best streak", dayCount(streaks.longest), Modifier.weight(1f).testTag("stats_best"))
            }
        }
        item("map") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionTitle("Activity")
                ActivityMap(byDay, today, selected, onSelect = { selected = it })
                val d = byDay[selected]
                val label = LocalDate.ofEpochDay(selected).format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
                Text(
                    if (d == null || !d.active) "$label: nothing played" else "$label: ${StatsMath.duration(d.totalMs)} · ${plays(d.plays.sum())}",
                    color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).testTag("stats_selected_day"),
                )
                Legend()
            }
        }
        item("split") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionTitle("All time")
                Bar("Listening", all.musicMs, all.totalMs, StatsMath.duration(all.musicMs), "stats_music_time")
                Bar("Watching", all.videoMs, all.totalMs, StatsMath.duration(all.videoMs), "stats_video_time")
                StatsMath.Kind.entries.forEach { k ->
                    val n = all.plays.getOrElse(k.ordinal) { 0 }
                    Bar(k.label, n.toLong(), all.allPlays.toLong(), plays(n), "stats_plays_${k.name.lowercase()}")
                }
                Text(stringResource(R.string.with_activity, dayCount(all.activeDays)), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (milestones.isNotEmpty()) item("milestones") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("stats_milestones")) {
                SectionTitle("Milestones")
                milestones.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(yuRound(3)).background(SpotifyGreen))
                        Text(m.title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        item("privacy") {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.statistics_stay_on_this_device_are),
                    color = TextTertiary, fontSize = 12.sp)
                if (days.isNotEmpty()) TextButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("stats_clear")) {
                    Text(stringResource(R.string.clear_statistics), color = SpotifyGreen)
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_statistics_2)) },
        text = { Text(stringResource(R.string.the_activity_map_streaks_and_totals)) },
        confirmButton = { TextButton(onClick = { ActivityLog.clear(context); confirmClear = false }, modifier = Modifier.testTag("stats_clear_confirm")) { Text(stringResource(R.string.clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun dayCount(n: Int) = if (n == 1) "1 day" else "$n days"
private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

/** Five steps from "nothing" to "more than 3 h"; step 0 is a visible outline so empty days still read as cells. */
@Composable
private fun levelColors(): List<Color> {
    val a = SpotifyGreen
    return listOf(TextPrimary.copy(alpha = 0.12f), a.copy(alpha = 0.30f), a.copy(alpha = 0.52f), a.copy(alpha = 0.76f), a)
}

@Composable
private fun ActivityMap(byDay: Map<Long, StatsMath.Day>, today: Long, selected: Long, onSelect: (Long) -> Unit) {
    val density = LocalDensity.current
    val levels = levelColors()
    val outline = TextPrimary
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = 12.dp; val gap = 3.dp
        // As many whole weeks as fit the width (about 22 on a phone, a year on a tablet or TV), never above 53.
        val weeks = ((maxWidth + gap) / (cell + gap)).toInt().coerceIn(4, 53)
        val start = StatsMath.gridStart(today, weeks)
        val active = (0 until weeks * 7).count { (byDay[start + it]?.active) == true }
        val summary = "Activity map, last $weeks weeks: $active active days. Tap a day to see it."
        val cellPx = with(density) { cell.toPx() }; val gapPx = with(density) { gap.toPx() }
        Canvas(
            Modifier.fillMaxWidth().height(cell * 7 + gap * 6).testTag("stats_activity_map")
                .semantics { contentDescription = summary }
                .pointerInput(start, weeks) {
                    detectTapGestures { o ->
                        val col = (o.x / (cellPx + gapPx)).toInt(); val row = (o.y / (cellPx + gapPx)).toInt()
                        if (col in 0 until weeks && row in 0..6) {
                            val d = start + col * 7L + row
                            if (d <= today) onSelect(d)
                        }
                    }
                },
        ) {
            val r = CornerRadius(cellPx * 0.22f)
            for (c in 0 until weeks) for (row in 0..6) {
                val d = start + c * 7L + row
                if (d > today) continue
                val o = Offset(c * (cellPx + gapPx), row * (cellPx + gapPx))
                drawRoundRect(levels[StatsMath.levelOf(byDay[d])], o, Size(cellPx, cellPx), r)
                if (d == selected) drawRoundRect(outline, o, Size(cellPx, cellPx), r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density.density))
            }
        }
    }
}

@Composable
private fun Legend() {
    val levels = levelColors()
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.less), color = TextTertiary, fontSize = 11.sp)
        levels.forEach { Box(Modifier.size(10.dp).clip(yuRound(2)).background(it)) }
        Text(stringResource(R.string.more_0_15_min_1_h), color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(yuRound(14)).background(GlassFill).padding(12.dp)) {
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Bar(label: String, value: Long, total: Long, text: String, tag: String) {
    val f = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text(text, color = TextSecondary, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(yuRound(3)).background(TextPrimary.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(yuRound(3)).background(SpotifyGreen))
        }
    }
}

/** Library entry to [StatsScreen]: streak and this week's time at a glance. */
@Composable
fun StatsEntryCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { ActivityLog.ensureLoaded(context); true }
    val days by ActivityLog.days.collectAsState()
    val today = remember { ActivityLog.today() }
    val streak = remember(days) { StatsMath.streaks(days, today).current }
    val week = remember(days) { StatsMath.totals(days, StatsMath.gridStart(today, 1), today).totalMs }
    Row(
        modifier.fillMaxWidth().clip(yuRound(16)).background(GlassFill)
            .androidxClickable(onOpen).padding(horizontal = 16.dp, vertical = 12.dp).testTag("lib_stats_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.your_activity), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(stringResource(R.string.streak_this_week, dayCount(streak), StatsMath.duration(week)), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Text(stringResource(R.string.see_all), color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClickLabel = "Open your activity", role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
