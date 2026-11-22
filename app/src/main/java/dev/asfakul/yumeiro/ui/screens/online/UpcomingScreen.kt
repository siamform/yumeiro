/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import dev.asfakul.yumeiro.online.upcoming.TvmazeSchedule
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
    val sections = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.UPCOMING_SECTIONS) }
    var kindName by rememberSaveable { mutableStateOf(Upcoming.Kind.SERIES.name) }
    var kindLoaded by rememberSaveable { mutableStateOf(!sections) }
    val kind = Upcoming.Kind.valueOf(kindName)
    // F-22: the last category is kept per profile (read and written off the main thread).
    // F-22-C: Series by country (TVmaze) and a genre filter; null = all countries (Simkl) / all genres.
    val byCountry = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.F22_COUNTRY_GENRE) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!kindLoaded) {
            withContext(Dispatchers.IO) {
                val k = UpcomingPrefs.kind(context)
                val c = if (byCountry) UpcomingPrefs.country(context) else null
                val g = if (byCountry) UpcomingPrefs.genre(context) else null
                Triple(k, c, g)
            }.let { (k, c, g) -> k?.let { kindName = it.name }; country = c; genre = g }
            kindLoaded = true
        }
    }
    LaunchedEffect(kindName, country, genre, kindLoaded) {
        if (sections && kindLoaded) withContext(Dispatchers.IO) {
            UpcomingPrefs.setKind(context, kind)
            if (byCountry) { UpcomingPrefs.setCountry(context, country); UpcomingPrefs.setGenre(context, genre) }
        }
    }
    val source = if (byCountry && kind == Upcoming.Kind.SERIES) country else null
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var list by remember(kind, source) { mutableStateOf<List<Upcoming.Entry>?>(null) }
    var failed by remember(kind, source) { mutableStateOf(false) }
    var loading by remember(kind, source) { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(kind, source, retry, kindLoaded) {
        if (!kindLoaded) return@LaunchedEffect
        loading = true; failed = false
        val cached = withContext(Dispatchers.IO) { if (source != null) TvmazeSchedule.cached(context, source, today) else Upcoming.cached(context, kind, today) }
        if (cached != null) list = cached
        if (cached == null || retry > 0 || !withContext(Dispatchers.IO) { if (source != null) TvmazeSchedule.isFresh(context, source) else Upcoming.isFresh(context, kind) }) {
            val fresh = withContext(Dispatchers.IO) {
                val client = dev.asfakul.yumeiro.network.NetworkClient.get(context)
                runCatching { if (source != null) TvmazeSchedule.refresh(context, client, source, today, zone) else Upcoming.refresh(context, client, kind, today, zone) }.getOrNull()
            }
            if (fresh != null) list = fresh else failed = true
        }
        loading = false
    }
    val genres = remember(list) { if (byCountry) TvmazeSchedule.genresOf(list.orEmpty()) else emptyList() }
    val shown = remember(list, genre, genres) { if (genre != null && genre in genres) TvmazeSchedule.filterGenre(list.orEmpty(), genre) else list.orEmpty() }
    val days = remember(shown) { shown.groupBy { it.day }.toList() }
    Column(Modifier.fillMaxSize().testTag("upcoming_screen")) {
        SubPageHeader(title = "Upcoming", subtitle = "Next two weeks · ${kind.label}", onBack = onBack)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Upcoming.Kind.entries.forEach { k ->
                OnlineChip(k.label, k == kind, { kindName = k.name }, Modifier.testTag("upcoming_kind_${k.name.lowercase()}"))
            }
        }
        if (byCountry && kind == Upcoming.Kind.SERIES) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp).testTag("upcoming_countries"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineChip("All countries", country == null, { country = null }, Modifier.testTag("upcoming_country_all"))
                (TvmazeSchedule.COUNTRIES + TvmazeSchedule.STREAMING).forEach { c ->
                    OnlineChip(TvmazeSchedule.label(c), c == country, { country = c }, Modifier.testTag("upcoming_country_${c.lowercase()}"))
                }
            }
        }
        if (genres.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp).testTag("upcoming_genres"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnlineChip("All genres", genre == null || genre !in genres, { genre = null }, Modifier.testTag("upcoming_genre_all"))
                genres.forEach { g -> OnlineChip(g, g == genre, { genre = g }, Modifier.testTag("upcoming_genre_${g.lowercase()}")) }
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
                        Text(if (sections) Upcoming.dayHeader(day, today, kind) else Upcoming.dayLabel(day, today), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).semantics { heading() })
                    }
                    items(entries, key = { "e_${it.simklId}_${it.day}_${it.season}" }) { e -> UpcomingRow(e, showTime = sections) { onSearch(e.title) } }
                }
                item("credit") {
                    Text(if (source != null) "Schedule data: TVmaze (tvmaze.com), CC BY-SA. Tap a title to look for it in your sources." else "Calendar data: Simkl (simkl.com). Tap a title to look for it in your sources.", color = TextTertiary, fontSize = 11.sp,
                        modifier = Modifier.padding(16.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(e: Upcoming.Entry, showTime: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(GlassFill)
            .clickable(onClick = onClick).padding(8.dp).testTag("upcoming_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(e.posterUrl, emptyMap(), Modifier.width(48.dp).height(72.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // F-22: air time and episode on one line ("20:00 • S2 E4"); the time only when the calendar has one.
            val sub = listOfNotNull(Upcoming.timeLabel(e).takeIf { showTime }, e.episodeLabel).joinToString(" • ")
            if (sub.isNotEmpty()) Text(sub, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("upcoming_entry_sub"))
            // F-22-C: country and genre badges ("JP • Anime, Fantasy") when the schedule has them.
            TvmazeSchedule.badge(e)?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("upcoming_entry_badge")) }
        }
    }
}

/** F-22: the Upcoming category of the current profile. Disk: call on Dispatchers.IO. */
internal object UpcomingPrefs {
    private fun prefs(c: android.content.Context) = c.applicationContext.getSharedPreferences(
        dev.asfakul.yumeiro.profiles.Profiles.prefsName(c, "upcoming"), android.content.Context.MODE_PRIVATE)
    fun kind(c: android.content.Context): Upcoming.Kind? =
        prefs(c).getString("kind", null)?.let { n -> Upcoming.Kind.entries.firstOrNull { it.name == n } }
    fun setKind(c: android.content.Context, k: Upcoming.Kind) { prefs(c).edit().putString("kind", k.name).apply() }
    fun country(c: android.content.Context): String? = prefs(c).getString("country", null)?.takeIf { it in TvmazeSchedule.COUNTRIES || it == TvmazeSchedule.STREAMING }
    fun setCountry(c: android.content.Context, v: String?) { prefs(c).edit().putString("country", v).apply() }
    fun genre(c: android.content.Context): String? = prefs(c).getString("genre", null)
    fun setGenre(c: android.content.Context, v: String?) { prefs(c).edit().putString("genre", v).apply() }
}
