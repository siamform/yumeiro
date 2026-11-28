/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.stream

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.sports.SportsScores
import dev.asfakul.yumeiro.sports.SportsScores.Sport
import dev.asfakul.yumeiro.sports.SportsScores.State
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.LocalReducedMotion
import dev.asfakul.yumeiro.ui.components.SkeletonBlock
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainer
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sports in tab order with their labels. */
@Composable
internal fun sportLabel(s: Sport): String = stringResource(
    when (s) {
        Sport.FOOTBALL -> R.string.sport_football
        Sport.CRICKET -> R.string.sport_cricket
        Sport.BASKETBALL -> R.string.sport_basketball
        Sport.VOLLEYBALL -> R.string.sport_volleyball
        Sport.AMERICAN_FOOTBALL -> R.string.sport_american_football
        Sport.HOCKEY -> R.string.sport_hockey
        Sport.BASEBALL -> R.string.sport_baseball
        Sport.TENNIS -> R.string.sport_tennis
        Sport.RUGBY -> R.string.sport_rugby
        Sport.HANDBALL -> R.string.sport_handball
    }
)

private val LiveRed = Color(0xFFE5484D)

/**
 * B15: Live TV → Tools → Sports. Pick a sport and a day; matches come grouped by league, live ones first.
 * Today's board updates itself every 30 s while a game is live and the page is on screen; nothing polls otherwise.
 */
@Composable
fun SportsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, onWatch: (() -> Unit)? = null) {
    val context = LocalContext.current
    val tv = LocalTvMode.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var sportId by rememberSaveable { mutableStateOf(Sport.FOOTBALL.id) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val sport = Sport.entries.firstOrNull { it.id == sportId } ?: Sport.FOOTBALL
    val day = today.plusDays(offset.toLong())
    var board by remember { mutableStateOf<SportsScores.Board?>(null) }
    var failed by remember { mutableStateOf<SportsScores.Reason?>(null) }
    var loading by remember { mutableStateOf(true) }
    var ask by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var open by remember { mutableStateOf<SportsScores.Match?>(null) }

    /**
     * [quiet]: keep what is on screen while asking (refresh button, live updates). The service reuses a fresh
     * answer, spaces requests out and, when the feeds can't be reached, hands back its last answer marked stale
     * (the banner says how old it is). With nothing to show, the error says why; numbers are never made up.
     */
    suspend fun load(quiet: Boolean) {
        if (!quiet) { loading = true; board = null; failed = null }
        val r = SportsScores.load(context, sport, day, force = quiet, today = today)
        r.onSuccess { board = it; failed = null }
            .onFailure { e ->
                if (!quiet || board == null) { board = null; failed = (e as? SportsScores.Failed)?.reason ?: SportsScores.Reason.SOURCE_DOWN }
                else board = board?.copy(stale = true)
            }
        loading = false
    }

    LaunchedEffect(sport, offset, ask) { load(quiet = false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val every = SportsScores.refreshEveryMs(board, today)
    LaunchedEffect(every, sport, offset) {
        if (every == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(every); load(quiet = true) }
        }
    }

    val timeFmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    fun clock(i: Instant) = timeFmt.format(java.util.Date.from(i))
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    Column(modifier.fillMaxSize().testTag("sports_page")) {
        SubPageHeader(
            title = stringResource(R.string.sports_title),
            onBack = onBack,
            subtitle = day.format(dayFmt),
            actions = {
                if (onWatch != null) IconButton(onClick = onWatch, modifier = Modifier.testTag("sports_watch")) {
                    Icon(Icons.Default.LiveTv, stringResource(R.string.sports_watch), tint = TextPrimary)
                }
                IconButton(onClick = { scope.launch { load(quiet = board != null) } }, enabled = !loading, modifier = Modifier.testTag("sports_refresh")) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.sports_refresh), tint = TextPrimary)
                }
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("sports_sports"),
        ) {
            items(Sport.entries, key = { it.id }) { s ->
                FilterPill(sportLabel(s), selected = s == sport, onClick = { if (s != sport) sportId = s.id }, testTag = "sports_sport_${s.id}")
            }
        }
        val dayState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
        LazyRow(
            state = dayState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("sports_days"),
        ) {
            items((-3..7).toList(), key = { it }) { o ->
                val label = when (o) {
                    -1 -> stringResource(R.string.sports_yesterday)
                    0 -> stringResource(R.string.sports_today)
                    1 -> stringResource(R.string.sports_tomorrow)
                    else -> today.plusDays(o.toLong()).format(dayFmt)
                }
                FilterPill(label, selected = o == offset, onClick = { if (o != offset) offset = o }, testTag = "sports_day_$o")
            }
        }
        // Where the numbers come from and how fresh they are, always on screen (and one calm status for TalkBack).
        val b = board
        if (b != null) {
            val names = b.sources.joinToString(" + ") { it.label }
            val at = clock(Instant.ofEpochMilli(b.fetchedAt))
            val src = if (b.fallback) stringResource(R.string.sports_source_fallback, names, at) else stringResource(R.string.sports_source, names, at)
            val liveLine = if (b.liveCount > 0 && !b.stale) stringResource(R.string.sports_live_count, b.liveCount) + " · " + stringResource(R.string.sports_auto) else null
            if (b.stale) Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(yuRound(12)).background(LiveRed.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp).testTag("sports_stale")
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CloudOff, null, tint = LiveRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sports_stale, at), color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("sports_source")
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(src, color = TextTertiary, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                if (liveLine != null) Text(liveLine, color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("sports_live_line"))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && b == null -> SportsSkeleton()
                failed != null -> {
                    val (icon, title, msg) = when (failed) {
                        SportsScores.Reason.OFFLINE -> Triple(Icons.Default.CloudOff, R.string.sports_err_offline_title, R.string.sports_err_offline)
                        SportsScores.Reason.BAD_ANSWER -> Triple(Icons.Default.ErrorOutline, R.string.sports_err_down_title, R.string.sports_err_bad)
                        SportsScores.Reason.BUSY -> Triple(Icons.Default.ErrorOutline, R.string.sports_err_down_title, R.string.sports_err_busy)
                        else -> Triple(Icons.Default.ErrorOutline, R.string.sports_err_down_title, R.string.sports_err_down)
                    }
                    StateMessage(icon, stringResource(title), stringResource(msg), primary = stringResource(R.string.sports_retry) to { ask++ },
                        testTag = "sports_error", suggestions = false)
                }
                b != null && b.empty -> StateMessage(
                    Icons.Default.EventBusy, stringResource(R.string.sports_empty_title),
                    stringResource(R.string.sports_empty_msg, sportLabel(sport), day.format(dayFmt)), testTag = "sports_empty",
                )
                b != null -> LazyColumn(
                    Modifier.fillMaxSize().testTag("sports_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    b.leagues.forEachIndexed { li, league ->
                        item(key = "L$li:${league.name}") {
                            Text(
                                league.name, color = TextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, top = if (li == 0) 0.dp else 10.dp).semantics { heading() }.testTag("sports_league"),
                            )
                        }
                        items(league.matches, key = { "M$li:${it.id}" }) { m ->
                            MatchRow(m, day, zone, ::clock, dayFmt, tv) { open = m }
                        }
                    }
                    item(key = "foot") { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
    open?.let { m ->
        SportsMatchSheet(m, sportLabel(sport), ::clock, onDismiss = { open = null }, onOpenLink = { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
    }
}

@Composable
private fun MatchRow(
    m: SportsScores.Match, day: LocalDate, zone: ZoneId, clock: (Instant) -> String, dayFmt: DateTimeFormatter,
    tv: Boolean, open: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = yuRound(16)
    val otherDay = m.start?.atZone(zone)?.toLocalDate()?.takeIf { it != day }
    val status = when (m.state) {
        State.LIVE -> m.detail.ifEmpty { stringResource(R.string.sports_live) }
        State.UPCOMING -> m.start?.let { clock(it) } ?: m.detail
        State.FINISHED -> m.detail
        State.OFF -> m.detail.ifEmpty { m.note }
    }
    val a = m.sides[0]; val bSide = m.sides[1]
    val spoken = buildString {
        append(stringResource(R.string.sports_cd_vs, a.name + (a.score?.let { " $it" } ?: ""), bSide.name + (bSide.score?.let { " $it" } ?: "")))
        append(", ")
        append(if (m.state == State.LIVE) stringResource(R.string.sports_status_live, status) else if (m.state == State.UPCOMING) stringResource(R.string.sports_starts, status) else status)
        if (otherDay != null) append(", ").append(otherDay.format(dayFmt))
        if (m.note.isNotEmpty() && m.note != status) append(". ").append(m.note)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(DarkSurfaceContainer, shape)
            .then(if (focused) Modifier.border(2.dp, SpotifyGreen, shape) else Modifier)
            .then(
                // Every row opens its match page (score by period, statistics, key moments, each team's last games).
                Modifier.clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button) { open() }
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("sports_match"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (m.state == State.LIVE) LiveDot()
            Text(
                status, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = when (m.state) { State.LIVE -> LiveRed; State.OFF -> TextTertiary; else -> TextSecondary },
                style = MaterialTheme.typography.labelMedium, fontWeight = if (m.state == State.LIVE) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.testTag("sports_status"),
            )
            if (otherDay != null) Text(otherDay.format(dayFmt), color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SideLine(a, m.state)
            SideLine(bSide, m.state)
            if (m.note.isNotEmpty() && m.note != status) Text(
                m.note, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("sports_note"),
            )
        }
    }
}

@Composable
private fun SideLine(s: SportsScores.Side, state: State) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(TextPrimary.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            if (s.logo != null) AsyncImage(model = s.logo, contentDescription = null, modifier = Modifier.size(20.dp))
            else Text(s.short.take(1).uppercase(), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            s.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("sports_team"),
            style = MaterialTheme.typography.bodyMedium, fontWeight = if (s.winner) FontWeight.Bold else FontWeight.Normal,
        )
        // Fixed slot so a score arriving never pushes the names around.
        Text(
            s.score ?: "", color = if (state == State.LIVE) TextPrimary else TextSecondary, textAlign = TextAlign.End, maxLines = 1,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = if (s.winner || state == State.LIVE) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.widthIn(min = 28.dp).padding(start = 8.dp).testTag("sports_score"),
        )
    }
}

@Composable
private fun LiveDot() {
    val still = LocalReducedMotion.current
    val pulse = if (still) 1f else {
        val t = rememberInfiniteTransition(label = "sports_live")
        val v by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sports_live_a")
        v
    }
    Box(Modifier.padding(bottom = 4.dp).size(8.dp).alpha(pulse).clip(CircleShape).background(LiveRed).testTag("sports_live_dot"))
}

@Composable
private fun SportsSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("sports_loading"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(width = 160.dp, height = 14.dp)
        repeat(6) {
            Row(Modifier.fillMaxWidth().height(72.dp).clip(yuRound(16)).background(DarkSurfaceContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = 48.dp, height = 14.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(width = null, height = 12.dp)
                    SkeletonBlock(width = null, height = 12.dp)
                }
            }
        }
    }
}
