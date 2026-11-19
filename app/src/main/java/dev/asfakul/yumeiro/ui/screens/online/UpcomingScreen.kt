/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** What a search started from another page should type in (consumed once by [OnlineSearchScreen]). */
internal object OnlineSearchPending {
    @Volatile var query: String? = null
    fun take(): String? = query.also { query = null }
}

/**
 * F-08 Upcoming: the next two weeks of series episodes, movie releases and anime, grouped by day in the phone's
 * time zone. Cached list first, refresh in the background when older than 6 h; offline keeps the cached list.
 * Tapping a title searches the user's own sources for it (Yumeiro does not stream from Simkl).
 */
@Composable
fun UpcomingScreen(onBack: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    var kindName by rememberSaveable { mutableStateOf(Upcoming.Kind.SERIES.name) }
    val kind = Upcoming.Kind.valueOf(kindName)
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var list by remember(kind) { mutableStateOf<List<Upcoming.Entry>?>(null) }
    var failed by remember(kind) { mutableStateOf(false) }
    var loading by remember(kind) { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(kind, retry) {
        loading = true; failed = false
        val cached = withContext(Dispatchers.IO) { Upcoming.cached(context, kind, today) }
        if (cached != null) list = cached
        if (cached == null || retry > 0 || !withContext(Dispatchers.IO) { Upcoming.isFresh(context, kind) }) {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { Upcoming.refresh(context, dev.asfakul.yumeiro.network.NetworkClient.get(context), kind, today, zone) }.getOrNull()
            }
            if (fresh != null) list = fresh else failed = true
        }
        loading = false
    }
    val days = remember(list) { list.orEmpty().groupBy { it.day }.toList() }
    Column(Modifier.fillMaxSize().testTag("upcoming_screen")) {
        SubPageHeader(title = "Upcoming", subtitle = "Next two weeks · ${kind.label}", onBack = onBack)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Upcoming.Kind.entries.forEach { k ->
                OnlineChip(k.label, k == kind, { kindName = k.name }, Modifier.testTag("upcoming_kind_${k.name.lowercase()}"))
            }
        }
        when {
            list == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.testTag("upcoming_loading"))
            }
            list == null && failed -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load the calendar. Check your connection.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.testTag("upcoming_error"))
                TextButton(onClick = { retry++ }, modifier = Modifier.testTag("upcoming_retry")) { Text("Try again", color = SpotifyGreen) }
            }
            days.isEmpty() -> Text("Nothing listed for the next two weeks.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(24.dp).testTag("upcoming_empty"))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("upcoming_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (failed) item("offline") {
                    Text("Offline — showing the last saved calendar.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("upcoming_stale"))
                }
                days.forEach { (day, entries) ->
                    item("h_$day") {
                        Text(Upcoming.dayLabel(day, today), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).semantics { heading() })
                    }
                    items(entries, key = { "e_${it.simklId}_${it.day}_${it.season}" }) { e -> UpcomingRow(e) { onSearch(e.title) } }
                }
                item("credit") {
                    Text("Calendar data: Simkl (simkl.com). Tap a title to look for it in your sources.", color = TextTertiary, fontSize = 11.sp,
                        modifier = Modifier.padding(16.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(e: Upcoming.Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(GlassFill)
            .clickable(onClick = onClick).padding(8.dp).testTag("upcoming_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(e.posterUrl, emptyMap(), Modifier.width(48.dp).height(72.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            e.episodeLabel?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
        }
    }
}
