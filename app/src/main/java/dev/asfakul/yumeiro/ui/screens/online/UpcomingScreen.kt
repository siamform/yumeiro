/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.upcoming.TvmazeSchedule
import dev.asfakul.yumeiro.online.upcoming.UpcomingFilter
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
import dev.asfakul.yumeiro.ui.theme.yuRound

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
    // H-9a (owner item 9): Movies have a country too (Wikidata); "Streaming" is a series-only schedule.
    val movieCountry = kind == Upcoming.Kind.MOVIES && country != dev.asfakul.yumeiro.online.upcoming.TvmazeSchedule.STREAMING
    val source = if (byCountry && (kind == Upcoming.Kind.SERIES || movieCountry)) country else null
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var list by remember(kind, source) { mutableStateOf<List<Upcoming.Entry>?>(null) }
    var failed by remember(kind, source) { mutableStateOf(false) }
    var loading by remember(kind, source) { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    // B14: where the shown list came from (credit line and empty-state text follow it).
    var from by remember(kind, source) { mutableStateOf<dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From?>(null) }
    LaunchedEffect(kind, source, retry, kindLoaded) {
        if (!kindLoaded) return@LaunchedEffect
        loading = true; failed = false
        val plan = dev.asfakul.yumeiro.online.upcoming.UpcomingHub.plan(kind, source, dev.asfakul.yumeiro.sources.hub.HubDiscover.available())
        // Cached first (the first source in the plan that has a cache), then fresh when stale.
        val cached = withContext(Dispatchers.IO) { plan.firstNotNullOfOrNull { f -> upcomingCached(context, f, kind, source, today)?.takeIf { it.isNotEmpty() || f == plan.last() }?.let { f to it } } }
        if (cached != null) { list = cached.second; from = cached.first }
        val stale = cached == null || retry > 0 || !withContext(Dispatchers.IO) { upcomingFresh(context, cached.first, kind, source) }
        if (stale) {
            // B14: a source that fails or has nothing hands over to the next one; the last one's answer stands.
            var got: Pair<dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From, List<Upcoming.Entry>>? = null
            for (f in plan) {
                val r = withContext(Dispatchers.IO) {
                    runCatching { upcomingRefresh(context, f, kind, source, today, zone) }
                        .onFailure { android.util.Log.i("YuUpcoming", "${kind.name} ${source ?: "all"} ${f.name}: ${it.javaClass.simpleName}") }.getOrNull()
                }
                android.util.Log.i("YuUpcoming", "B14 ${kind.name} ${source ?: "all"} ${f.name} -> ${r?.size ?: "failed"}")
                if (r != null && (r.isNotEmpty() || got == null)) got = f to r
                if (r != null && r.isNotEmpty()) break
            }
            if (got != null) { list = got.second; from = got.first } else failed = true
        }
        loading = false
    }
    // OWN-16: search and filters built from the loaded list (genres, channel countries, days), behind a filter icon.
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var channel by rememberSaveable { mutableStateOf<String?>(null) }
    var dayText by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val options = remember(list) { UpcomingFilter.options(list.orEmpty()) }
    val choice = UpcomingFilter.Choice(query, genre, channel, dayText?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    val shown = remember(list, choice) { UpcomingFilter.apply(list.orEmpty(), choice) }
    val days = remember(shown) { shown.groupBy { it.day }.toList() }
    val scheduleCountries = remember(options) {
        UpcomingFilter.scheduleCountries(java.util.Locale.getDefault().country, TvmazeSchedule.COUNTRIES, options.countries, TvmazeSchedule.STREAMING)
    }.let { all -> if (kind == Upcoming.Kind.MOVIES) all.filter { it != TvmazeSchedule.STREAMING } else all }
    Column(Modifier.fillMaxSize().testTag("upcoming_screen")) {
        SubPageHeader(title = tr(R.string.upcoming, "Upcoming"), subtitle = "${if (from == dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB) "Next two months" else "Next two weeks"} · ${kind.label}", onBack = onBack) {
            // GROUP C catch-all: search is an icon; the field opens under the header only when asked for.
            dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), {
                if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true
            }, "upcoming_search_open", tr(R.string.search_upcoming, "Search upcoming"))
        }
        // UP-FILTER-1 (owner list R21, item 16B): search, then three filter buttons (Category, Country, Date) that
        // open their own sheets. Every choice comes from what is loaded (genres, channel countries, days) or from
        // the platform's country list for TVmaze schedules; a button shows what is on.
        dev.asfakul.yumeiro.ui.components.HeaderSearchField(searchOpen || query.isNotEmpty(), query, { query = it }, "Search ${kind.label.lowercase()} in the next two weeks", "upcoming_search")
        val v = UpcomingFilter.valid(choice, options)
        val scheduleOn = byCountry && (kind == Upcoming.Kind.SERIES || kind == Upcoming.Kind.MOVIES)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpcomingFilterButton(
                androidx.compose.material.icons.Icons.Default.Category, "Category",
                listOfNotNull(kind.label, v.genre).joinToString(" · "), on = v.genre != null, tag = "upcoming_category_button", modifier = Modifier.weight(1f),
            ) { sheet = "category" }
            UpcomingFilterButton(
                androidx.compose.material.icons.Icons.Default.Public, "Country",
                when { scheduleOn && source != null -> UpcomingCountries.label(source); v.country != null -> UpcomingCountries.label(v.country); else -> "All" },
                on = (scheduleOn && source != null) || v.country != null, tag = "upcoming_country_button", modifier = Modifier.weight(1f),
            ) { sheet = "country" }
            UpcomingFilterButton(
                androidx.compose.material.icons.Icons.Default.DateRange, "Date",
                v.day?.let { Upcoming.dayLabel(it, today) } ?: tr(R.string.any_day, "Any day"), on = v.day != null, tag = "upcoming_date_button", modifier = Modifier.weight(1f),
            ) { sheet = "date" }
        }
        val close = { sheet = null }
        when (sheet) {
            "category" -> dev.asfakul.yumeiro.ui.components.FilterSheet(onDismiss = close, onReset = { genre = null }, resultCount = shown.size, tag = "upcoming_category_sheet") {
                dev.asfakul.yumeiro.ui.components.FilterGroup("Type") {
                    Upcoming.Kind.entries.forEach { k -> OnlineChip(k.label, k == kind, { kindName = k.name }, Modifier.testTag("upcoming_kind_${k.name.lowercase()}")) }
                }
                if (options.genres.isNotEmpty()) dev.asfakul.yumeiro.ui.components.FilterGroup("Genre", tag = "upcoming_genres") {
                    OnlineChip("All genres", v.genre == null, { genre = null }, Modifier.testTag("upcoming_genre_all"))
                    options.genres.forEach { g -> OnlineChip(g, g.equals(genre, ignoreCase = true), { genre = g }, Modifier.testTag("upcoming_genre_${g.lowercase()}")) }
                } else Text(stringResource(R.string.this_calendar_has_no_genres_country), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("upcoming_genre_none"))
            }
            "country" -> dev.asfakul.yumeiro.ui.components.FilterSheet(onDismiss = close, onReset = { channel = null; if (byCountry) country = null }, resultCount = shown.size, tag = "upcoming_country_sheet") {
                if (scheduleOn) {
                    var cq by rememberSaveable { mutableStateOf("") }
                    val more = remember(cq, scheduleCountries) { UpcomingCountries.search(cq, scheduleCountries) }
                    dev.asfakul.yumeiro.ui.components.FilterGroup("Schedule", tag = "upcoming_countries") {
                        OnlineChip("All countries", source == null, { country = null }, Modifier.testTag("upcoming_country_all"))
                        (if (cq.isBlank()) scheduleCountries else more).forEach { c ->
                            OnlineChip(UpcomingCountries.label(c), c == source, { country = c }, Modifier.testTag("upcoming_country_${c.lowercase()}"))
                        }
                    }
                    dev.asfakul.yumeiro.ui.components.YuSearchField(cq, { cq = it.take(40) }, tr(R.string.another_country, "Another country"), "upcoming_country_search", Modifier.fillMaxWidth())
                    if (cq.isNotBlank() && more.isEmpty()) Text("No country called \"${cq.trim()}\"", color = TextSecondary, fontSize = 13.sp)
                }
                if (options.countries.size > 1) dev.asfakul.yumeiro.ui.components.FilterGroup(tr(R.string.channel_country, "Channel country")) {
                    OnlineChip("All", v.country == null, { channel = null }, Modifier.testTag("upcoming_channel_all"))
                    options.countries.forEach { c -> OnlineChip(UpcomingCountries.label(c), c == channel, { channel = c }, Modifier.testTag("upcoming_channel_${c.lowercase()}")) }
                }
                if (!scheduleOn && options.countries.size <= 1) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.the_calendar_is_worldwide_schedules_by, kind.label.lowercase()), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("upcoming_country_none"))
                    if (byCountry) OnlineChip("Show series by country", false, { kindName = Upcoming.Kind.SERIES.name }, Modifier.testTag("upcoming_country_to_series"))
                }
            }
            "date" -> dev.asfakul.yumeiro.ui.components.FilterSheet(onDismiss = close, onReset = { dayText = null }, resultCount = shown.size, tag = "upcoming_date_sheet") {
                if (options.days.isNotEmpty()) dev.asfakul.yumeiro.ui.components.FilterGroup("Day") {
                    val perDay = list.orEmpty().groupingBy { it.day }.eachCount()
                    OnlineChip("Any day", v.day == null, { dayText = null }, Modifier.testTag("upcoming_day_all"))
                    options.days.forEach { d -> OnlineChip("${Upcoming.dayLabel(d, today)} · ${perDay[d] ?: 0}", d.toString() == dayText, { dayText = d.toString() }, Modifier.testTag("upcoming_day_$d")) }
                } else Text(stringResource(R.string.nothing_loaded_yet), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("upcoming_filter_none"))
            }
        }
        when {
            list == null && loading -> dev.asfakul.yumeiro.ui.components.SkeletonList(rows = 6, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("upcoming_loading")) // SKELETON-2
            list == null && failed -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.couldnt_load_the_calendar_check_your), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.testTag("upcoming_error"))
                TextButton(onClick = { retry++ }, modifier = Modifier.testTag("upcoming_retry")) { Text(stringResource(R.string.try_again), color = SpotifyGreen) }
            }
            days.isEmpty() && list.orEmpty().isNotEmpty() -> Text(if (query.isNotBlank()) "Nothing matches \"${query.trim()}\"." else "Nothing matches these filters.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(24.dp).testTag("upcoming_no_match"))
            // H-9b (owner item 9): India (and other countries) in Series came back empty with no word why: TVmaze
            // lists almost no Indian TV channels. The page now says so and offers what does have that country.
            days.isEmpty() && source != null -> Column(Modifier.fillMaxWidth().padding(24.dp).testTag("upcoming_empty_country"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = UpcomingCountries.label(source)
                val movies = kind == Upcoming.Kind.MOVIES
                Text(
                    if (from == dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB) "No ${if (movies) "films" else "new series"} from $name are listed for the next two months."
                    else if (movies) tr(R.string.wikidata_lists_no_films_from_in, "Wikidata lists no films from %1\$s in the next two weeks.", name) else tr(R.string.tvmaze_lists_no_tv_episodes_for, "TVmaze lists no TV episodes for %1\$s in the next two weeks. Many of its shows are on streaming services.", name),
                    color = TextSecondary, fontSize = 14.sp,
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!movies) OnlineChip("Streaming series", false, { country = TvmazeSchedule.STREAMING }, Modifier.testTag("upcoming_empty_streaming"))
                    if (!movies) OnlineChip("Movies from $name", false, { kindName = Upcoming.Kind.MOVIES.name }, Modifier.testTag("upcoming_empty_movies"))
                    if (movies) OnlineChip("Series from $name", false, { kindName = Upcoming.Kind.SERIES.name }, Modifier.testTag("upcoming_empty_series"))
                    OnlineChip("All countries", false, { country = null }, Modifier.testTag("upcoming_empty_all"))
                }
            }
            days.isEmpty() -> Text(stringResource(R.string.nothing_listed_for_the_next_two), color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(24.dp).testTag("upcoming_empty"))
            // Device 45045030577 (api33-lowram): the offline note was the list's first row, so after a scroll it sat
            // off screen. It stays above the list now.
            else -> Column(Modifier.fillMaxSize()) {
                if (failed) Text(stringResource(R.string.offline_showing_the_last_saved_calendar), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("upcoming_stale"))
                // F10 (Group O): the most-awaited titles of what is shown, as a multi-browse carousel on top.
                val highlights = remember(shown, kind) { dev.asfakul.yumeiro.online.upcoming.UpcomingSoon.picks(mapOf(kind to shown), today, days = 400, max = 12) }
                LazyColumn(Modifier.fillMaxSize().testTag("upcoming_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (highlights.size >= 3) item("highlights", contentType = "carousel") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.most_awaited), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() })
                        UpcomingCarousel(highlights, today, tag = "upcoming_highlight") { onSearch(it.entry.title) }
                    }
                }
                days.forEach { (day, entries) ->
                    item("h_$day") {
                        Text(if (sections) Upcoming.dayHeader(day, today, kind) else Upcoming.dayLabel(day, today), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp).semantics { heading() })
                    }
                    items(entries, key = { "e_${it.simklId}_${it.day}_${it.season}" }) { e -> UpcomingRow(e, showTime = sections) { onSearch(e.title) } }
                }
                item("credit") {
                    Text(when (from) {
                        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB -> tr(R.string.release_data_tmdb_themoviedb_org_through, "Release data: TMDB (themoviedb.org) through Yumeiro Cloud. This product uses the TMDB API but is not endorsed or certified by TMDB. Tap a title to look for it in your sources.")
                        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.WIKIDATA -> tr(R.string.release_data_wikidata_wikidata_org_cc0, "Release data: Wikidata (wikidata.org), CC0. Tap a title to look for it in your sources.")
                        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.TVMAZE -> tr(R.string.schedule_data_tvmaze_tvmaze_com_cc, "Schedule data: TVmaze (tvmaze.com), CC BY-SA. Tap a title to look for it in your sources.")
                        else -> tr(R.string.calendar_data_simkl_simkl_com_tap, "Calendar data: Simkl (simkl.com). Tap a title to look for it in your sources.")
                    }, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(16.dp).navigationBarsPadding().testTag("upcoming_credit"))
                }
            }
            }
        }
    }
}

@Composable
private fun UpcomingFilterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, on: Boolean, tag: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    Row(
        modifier.heightIn(min = 52.dp).clip(yuRound(16))
            .background(if (on) p.accent.copy(alpha = 0.16f) else GlassFill)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = tr(R.string.change_2, "%1\$s: %2\$s. Change", title, value) }
            .padding(horizontal = 10.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = if (on) p.accent else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
            Text(value, color = if (on) p.accent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** UP-FILTER-1: country names for schedule codes and a search over every ISO country (TVmaze takes any). */
internal object UpcomingCountries {
    fun label(code: String): String {
        if (code == TvmazeSchedule.STREAMING) return "Streaming"
        val name = java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault()).takeIf { it.isNotBlank() && it != code } ?: return code
        return listOfNotNull(dev.asfakul.yumeiro.stream.LiveFind.flag(code), name).joinToString(" ")
    }

    /** Countries whose name or code matches [query]: offered ones first, then the rest by name. Max 24. */
    fun search(query: String, offered: List<String>): List<String> {
        val q = UpcomingFilter.fold(query)
        if (q.isEmpty()) return emptyList()
        val all = (offered + java.util.Locale.getISOCountries().toList()).distinct()
        return all.filter { c -> c.lowercase() == q || UpcomingFilter.fold(label(c)).contains(q) }
            .sortedWith(compareBy<String> { if (it in offered) 0 else 1 }.thenBy { label(it).substringAfter(' ') })
            .take(24)
    }
}

@Composable
private fun UpcomingRow(e: Upcoming.Entry, showTime: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(yuRound(14)).background(GlassFill)
            .clickable(onClick = onClick).padding(8.dp).testTag("upcoming_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(e.posterUrl, emptyMap(), Modifier.width(48.dp).height(72.dp).clip(yuRound(8)))
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
    fun country(c: android.content.Context): String? = prefs(c).getString("country", null)?.takeIf { it == TvmazeSchedule.STREAMING || (it.length == 2 && it.all { ch -> ch in 'A'..'Z' }) }
    fun setCountry(c: android.content.Context, v: String?) { prefs(c).edit().putString("country", v).apply() }
    fun genre(c: android.content.Context): String? = prefs(c).getString("genre", null)
    fun setGenre(c: android.content.Context, v: String?) { prefs(c).edit().putString("genre", v).apply() }
}

// B14: one place that knows every Upcoming source (Simkl worldwide, TMDB by country through the hub, TVmaze
// schedules, Wikidata films). Disk and network: call on Dispatchers.IO.
private fun upcomingCached(c: android.content.Context, f: dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From, kind: Upcoming.Kind, source: String?, today: LocalDate): List<Upcoming.Entry>? = when (f) {
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.SIMKL -> Upcoming.cached(c, kind, today)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB -> dev.asfakul.yumeiro.online.upcoming.UpcomingHub.cached(c, kind, source!!, today)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.WIKIDATA -> dev.asfakul.yumeiro.online.upcoming.MovieReleases.cached(c, source!!, today)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.TVMAZE -> TvmazeSchedule.cached(c, source!!, today)
}

private fun upcomingFresh(c: android.content.Context, f: dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From, kind: Upcoming.Kind, source: String?): Boolean = when (f) {
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.SIMKL -> Upcoming.isFresh(c, kind)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB -> dev.asfakul.yumeiro.online.upcoming.UpcomingHub.isFresh(c, kind, source!!)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.WIKIDATA -> dev.asfakul.yumeiro.online.upcoming.MovieReleases.isFresh(c, source!!)
    dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.TVMAZE -> TvmazeSchedule.isFresh(c, source!!)
}

private suspend fun upcomingRefresh(c: android.content.Context, f: dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From, kind: Upcoming.Kind, source: String?, today: LocalDate, zone: ZoneId): List<Upcoming.Entry> {
    val client = dev.asfakul.yumeiro.network.NetworkClient.get(c)
    return when (f) {
        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.SIMKL -> Upcoming.refresh(c, client, kind, today, zone)
        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.HUB -> dev.asfakul.yumeiro.online.upcoming.UpcomingHub.refresh(c, kind, source!!, today)
        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.WIKIDATA -> dev.asfakul.yumeiro.online.upcoming.MovieReleases.refresh(c, client, source!!, today)
        dev.asfakul.yumeiro.online.upcoming.UpcomingHub.From.TVMAZE -> TvmazeSchedule.refresh(c, client, source!!, today, zone)
    }
}
