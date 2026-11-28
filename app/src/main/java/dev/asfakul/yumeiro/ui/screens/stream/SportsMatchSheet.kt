/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.sports.SportsScores
import dev.asfakul.yumeiro.sports.SportsScores.State
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.sheetDragHandle
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * B15: one match's page — score by period/set, team statistics, the key moments, and each team's last games
 * ("statistics of the last match"). Everything comes from ESPN's match summary; what ESPN does not send is not
 * shown (a short line says so), and TheSportsDB matches, whose free tier has no statistics, say that too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SportsMatchSheet(
    m: SportsScores.Match,
    sportName: String,
    clock: (Instant) -> String,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var detail by remember(m.id) { mutableStateOf<SportsScores.Detail?>(null) }
    var failed by remember(m.id) { mutableStateOf(false) }
    var loading by remember(m.id) { mutableStateOf(m.espnPath != null) }
    LaunchedEffect(m.id) {
        if (m.espnPath == null) return@LaunchedEffect
        SportsScores.loadDetail(context, m).onSuccess { detail = it }.onFailure { failed = true }
        loading = false
    }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("sports_match_sheet")) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head") { Scoreboard(m, detail, sportName, clock) }
            val d = detail
            when {
                loading -> item(key = "loading") {
                    Column(Modifier.fillMaxWidth().testTag("sports_detail_loading"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { SkeletonBlock(width = null, height = 14.dp) }
                    }
                }
                m.espnPath == null -> item(key = "nodetail") { Quiet(stringResource(R.string.sports_detail_none_source, m.sourceLabel()), "sports_detail_none") }
                failed || d == null -> item(key = "fail") { Quiet(stringResource(R.string.sports_detail_failed), "sports_detail_failed", error = true) }
                else -> {
                    if (d.summary.isNotEmpty()) item(key = "sum") { Text(d.summary, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("sports_detail_summary")) }
                    if (d.sides.size == 2 && d.sides.all { it.periods.isNotEmpty() }) item(key = "periods") { Periods(d.sides) }
                    if (d.stats.isNotEmpty()) {
                        item(key = "stats_h") { Heading(stringResource(R.string.sports_detail_stats)) }
                        item(key = "stats_names") { TeamNames(d.sides) }
                        d.stats.forEachIndexed { i, s -> item(key = "stat$i") { StatRow(s) } }
                    }
                    if (d.moments.isNotEmpty()) {
                        item(key = "mom_h") { Heading(stringResource(R.string.sports_detail_moments)) }
                        d.moments.forEachIndexed { i, mo -> item(key = "mom$i") { MomentRow(mo) } }
                    }
                    if (d.form.isNotEmpty()) {
                        item(key = "form_h") { Heading(stringResource(R.string.sports_detail_form)) }
                        d.form.forEachIndexed { i, f -> item(key = "form$i") { FormBlock(f, dateFmt) } }
                    }
                    if (d.venue != null || d.notes.isNotEmpty()) item(key = "info") {
                        Column(Modifier.testTag("sports_detail_info"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            d.venue?.let { v ->
                                val line = if (d.attendance != null) stringResource(R.string.sports_detail_venue_att, v, d.attendance) else v
                                Text(line, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                            d.notes.forEach { Text(it, color = TextTertiary, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    if (d.thin) item(key = "thin") { Quiet(stringResource(R.string.sports_detail_thin), "sports_detail_thin") }
                    item(key = "src") {
                        Text(
                            stringResource(R.string.sports_detail_source, clock(Instant.ofEpochMilli(d.fetchedAt))), color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("sports_detail_source"),
                        )
                    }
                }
            }
            m.link?.let { url ->
                item(key = "link") {
                    TextButton(onClick = { onOpenLink(url) }, modifier = Modifier.testTag("sports_detail_link")) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sports_detail_open_espn), color = SpotifyGreen)
                    }
                }
            }
        }
    }
}

private fun SportsScores.Match.sourceLabel() = if (espnPath != null) SportsScores.Source.ESPN.label else SportsScores.Source.SPORTSDB.label

@Composable
private fun Scoreboard(m: SportsScores.Match, d: SportsScores.Detail?, sportName: String, clock: (Instant) -> String) {
    // The page header keeps the board's own numbers until the match page has answered; the newer one wins.
    val names = m.sides.map { it.name }
    val scores = d?.sides?.takeIf { it.size == 2 }?.map { it.score } ?: m.sides.map { it.score }
    val status = when {
        d != null && d.status.isNotEmpty() -> d.status
        m.state == State.UPCOMING -> m.start?.let(clock) ?: m.detail
        else -> m.detail.ifEmpty { m.note }
    }
    val live = (d?.state ?: m.state) == State.LIVE
    Column(Modifier.fillMaxWidth().testTag("sports_detail_head"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(sportName, color = TextTertiary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(names[0], color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("sports_detail_team"))
            Text(
                listOf(scores[0] ?: "–", scores[1] ?: "–").joinToString("  :  "), color = if (live) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("sports_detail_score"),
            )
            Text(names[1], color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("sports_detail_team"))
        }
        Spacer(Modifier.height(4.dp))
        Text(status, color = if (live) LiveRedSheet else TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = if (live) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.testTag("sports_detail_status"))
    }
}

private val LiveRedSheet = androidx.compose.ui.graphics.Color(0xFFE5484D)

@Composable
private fun Heading(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }.testTag("sports_detail_heading"))
}

@Composable
private fun TeamNames(sides: List<SportsScores.DetailSide>) {
    if (sides.size < 2) return
    Row(Modifier.fillMaxWidth()) {
        Text(sides[0].name, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(sides[1].name, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Periods(sides: List<SportsScores.DetailSide>) {
    val n = sides.maxOf { it.periods.size }
    Column(
        Modifier.fillMaxWidth().clip(yuRound(14)).background(DarkSurfaceContainer).padding(12.dp).testTag("sports_detail_periods"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Spacer(Modifier.weight(1f))
            for (i in 1..n) Text(i.toString(), color = TextTertiary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 30.dp))
        }
        sides.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, color = TextPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (s.winner) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                for (i in 0 until n) Text(s.periods.getOrElse(i) { "" }, color = TextSecondary, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.widthIn(min = 30.dp))
            }
        }
    }
}

@Composable
private fun StatRow(s: SportsScores.StatLine) {
    val hn = s.home.trimEnd('%').toDoubleOrNull(); val an = s.away.trimEnd('%').toDoubleOrNull()
    val share = if (hn != null && an != null && hn >= 0 && an >= 0 && hn + an > 0) (hn / (hn + an)).toFloat() else null
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${s.label}: ${s.home}, ${s.away}" }.testTag("sports_detail_stat"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.home, color = TextPrimary, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 48.dp))
            Text(s.label, color = TextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(s.away, color = TextPrimary, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 48.dp))
        }
        // A bar only for plain counts; text values ("39-86") have no fair split.
        if (share != null) Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)) {
            if (share > 0f) Box(Modifier.weight(share).height(4.dp).background(SpotifyGreen))
            if (share < 1f) Box(Modifier.weight(1f - share).height(4.dp).background(TextPrimary.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun MomentRow(mo: SportsScores.Moment) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("sports_detail_moment"), verticalAlignment = Alignment.Top) {
        Text(mo.clock, color = TextTertiary, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            Text(mo.text, color = TextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            mo.team?.let { Text(it, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}

@Composable
private fun FormBlock(f: SportsScores.Form, dateFmt: DateTimeFormatter) {
    val zone = remember { ZoneId.systemDefault() }
    Column(
        Modifier.fillMaxWidth().clip(yuRound(14)).background(DarkSurfaceContainer).padding(12.dp).focusable().testTag("sports_detail_form"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(f.team, color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        f.games.forEachIndexed { i, g ->
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                val tint = when (g.result) { "W" -> SpotifyGreen; "L" -> LiveRedSheet; else -> TextSecondary }
                Box(Modifier.size(22.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(g.result.ifEmpty { "·" }, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (g.atHome) R.string.sports_detail_vs else R.string.sports_detail_at, g.opponent) + if (i == 0) " · " + stringResource(R.string.sports_detail_last) else "",
                    color = TextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (g.own != null && g.other != null) Text(listOf(g.own, g.other).joinToString("–"), color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
                g.date?.let { Text(it.atZone(zone).toLocalDate().format(dateFmt), color = TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp)) }
            }
        }
    }
}

@Composable
private fun Quiet(text: String, tag: String, error: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        if (error) { Icon(Icons.Default.ErrorOutline, null, tint = TextTertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
