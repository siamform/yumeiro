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
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("upcoming_banner")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        UpcomingCarousel(list, today, tag = "upcoming_banner_slide") { onSearch(it.entry.title) }
    }
}

/**
 * F10 (Group O): the Upcoming redesign, an M3 multi-browse carousel (one large card, medium and small ones peeking).
 * Parameters as the spec asks: preferred item 186 dp, 8 dp apart, 16 dp side padding, 205 dp tall, masks clipped
 * to the extra-large shape, posters cropped. Used by Yumix's "Coming up" card and at the top of the Upcoming page.
 * Profiled with the jank bench (lane go-f10). No auto-rotation: a carousel already shows several titles.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun UpcomingCarousel(list: List<UpcomingSoon.Pick>, today: LocalDate, tag: String, onClick: (UpcomingSoon.Pick) -> Unit) {
    val state = androidx.compose.material3.carousel.rememberCarouselState { list.size }
    androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = CAROUSEL_ITEM_WIDTH,
        itemSpacing = 8.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(CAROUSEL_HEIGHT).testTag(tag + "_carousel"),
    ) { i ->
        val pick = list[i]
        val e = pick.entry
        val whenText = UpcomingSoon.whenLabel(e.day, today)
        Box(
            Modifier.fillMaxSize()
                .maskClip(androidx.compose.material3.MaterialTheme.shapes.extraLarge)
                .background(GlassFill)
                .clickable(role = Role.Button) { onClick(pick) }
                .semantics { contentDescription = "${e.title}, ${pick.kind.label}, $whenText. Search your sources" }
                .testTag(tag),
        ) {
            PosterImage(e.posterUrl, emptyMap(), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text("${whenText.uppercase()} · ${pick.kind.label.uppercase()}", color = LocalMtPalette.current.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                val sub = listOfNotNull(Upcoming.timeLabel(e), e.episodeLabel).joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal val CAROUSEL_ITEM_WIDTH = 186.dp
internal val CAROUSEL_HEIGHT = 205.dp

