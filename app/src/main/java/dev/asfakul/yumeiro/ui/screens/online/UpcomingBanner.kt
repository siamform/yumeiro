/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.online.upcoming.UpcomingSoon
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * UP-BANNER-1 (owner list R21, item 16A): Yumix's "Coming up" card. It rotates every 5 s through what comes out
 * today, tomorrow and in the next 3 days (from the Upcoming calendars: cache first, refreshed in the background
 * when older than 6 h). Rotation stops while touched or focused, with reduced motion and when the phone is hot.
 * Tapping a slide searches your sources for that title; "See all" opens the Upcoming page. With nothing to show
 * (offline, first start) it falls back to the plain Upcoming entry.
 */
@Composable
internal fun UpcomingBanner(onOpenUpcoming: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var picks by remember { mutableStateOf<List<UpcomingSoon.Pick>?>(null) }
    LaunchedEffect(Unit) {
        picks = withContext(Dispatchers.IO) {
            val lists = Upcoming.Kind.entries.associateWith { k -> Upcoming.cached(context, k, today).orEmpty() }
            UpcomingSoon.picks(lists, today)
        }
        val stale = withContext(Dispatchers.IO) { Upcoming.Kind.entries.filter { !Upcoming.isFresh(context, it) } }
        if (stale.isNotEmpty()) {
            val fresh = withContext(Dispatchers.IO) {
                val client = dev.asfakul.yumeiro.network.NetworkClient.get(context)
                Upcoming.Kind.entries.associateWith { k ->
                    if (k in stale) runCatching { Upcoming.refresh(context, client, k, today, zone) }.getOrNull() ?: Upcoming.cached(context, k, today).orEmpty()
                    else Upcoming.cached(context, k, today).orEmpty()
                }.let { UpcomingSoon.picks(it, today) }
            }
            if (fresh.isNotEmpty()) picks = fresh
        }
    }
    val list = picks
    if (list.isNullOrEmpty()) { UpcomingEntryCard(onOpenUpcoming); return }
    val p = LocalMtPalette.current
    val pager = rememberPagerState { list.size }
    var held by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val reduced = remember { dev.asfakul.yumeiro.ui.components.isReducedMotion(context) }
    val started = dev.asfakul.yumeiro.ui.components.rememberIsStarted()
    LaunchedEffect(list.size, held, focused, reduced, started) {
        if (list.size < 2 || !started || held || focused || reduced) return@LaunchedEffect
        while (true) {
            delay(ROTATE_MS)
            if (!pager.isScrollInProgress && dev.asfakul.yumeiro.player.HeatGuard.allowDecorativeMotion())
                pager.animateScrollToPage((pager.currentPage + 1) % list.size)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("upcoming_banner")) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, null, tint = p.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Coming up", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onOpenUpcoming)
                    .padding(horizontal = 8.dp, vertical = 12.dp).testTag("upcoming_banner_all"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See all", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.accent, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalPager(
            pager,
            Modifier.fillMaxWidth().height(BANNER_HEIGHT).clip(RoundedCornerShape(20.dp))
                .onFocusChanged { if (focused != it.hasFocus) focused = it.hasFocus }
                .pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false); held = true; waitForUpOrCancellation(); held = false }
                },
            key = { i -> "up_" + list[i].entry.simklId + "_" + list[i].entry.day },
        ) { i -> UpcomingSlide(list[i], today) { onSearch(list[i].entry.title) } }
        if (list.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(list.size) { i ->
                val on = pager.currentPage == i
                Box(Modifier.padding(horizontal = 3.dp).size(width = if (on) 16.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (on) p.accent else TextTertiary.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun UpcomingSlide(pick: UpcomingSoon.Pick, today: LocalDate, onClick: () -> Unit) {
    val e = pick.entry
    val whenText = UpcomingSoon.whenLabel(e.day, today)
    val sub = listOfNotNull(Upcoming.timeLabel(e), e.episodeLabel).joinToString(" • ")
    Box(
        Modifier.fillMaxSize().background(GlassFill).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${e.title}, ${pick.kind.label}, $whenText. Search your sources" }
            .testTag("upcoming_banner_slide"),
    ) {
        // The poster, cropped wide and darkened, behind the text; the full poster on the right.
        PosterImage(e.posterUrl, emptyMap(), Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.25f)))))
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${whenText.uppercase()}  ·  ${pick.kind.label.uppercase()}", color = LocalMtPalette.current.accent,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(e.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("Tap to find it in your sources", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            PosterImage(e.posterUrl, emptyMap(), Modifier.width(76.dp).height(112.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}

private const val ROTATE_MS = 5_000L
private val BANNER_HEIGHT = 140.dp
