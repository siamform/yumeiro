/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.alpha
import dev.asfakul.yumeiro.ui.components.shimmer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.sources.hub.HubDiscover.toOnlineItem
import dev.asfakul.yumeiro.sources.hub.YumeiroCatalog
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * Group O (F5, F6, F9): discovery in Yumix "All" and on Home, from Yumeiro Cloud's TMDB metadata.
 *
 * Studied (behaviour only, no code): VyseTV's home (a section bar Movies / TV / Anime / Top Rated, "Hot right now",
 * "If you like <title ▾>" seeded from what was watched, streaming-service tiles by region, a genre grid) and
 * Hikari's genre chips. Yumeiro's own take: one section bar that swaps the shelves below it, every card opens as a
 * Yumeiro catalog title (played through the user's own extensions), a full grid behind every "See all".
 *
 * Built as separate list items ([shelves] + [DiscoverShelf]) so a lazy list composes one shelf at a time.
 */
object Discover {
    enum class Section(val label: String) {
        DISCOVER("Discover"), MOVIES("Movies"), TV(tr(R.string.tv_shows, "TV Shows")), ANIME("Anime"), TOP(tr(R.string.top_rated, "Top Rated")), HOT(tr(R.string.hot_right_now, "Hot Right Now")),
    }

    sealed interface Shelf { val key: String }

    /** A poster row with a full grid behind "See all". */
    @Immutable data class Row(override val key: String, val title: String, val query: HubDiscover.Query?, val shelf: String?) : Shelf {
        suspend fun load(page: Int): HubDiscover.Page? = when {
            query != null -> HubDiscover.page(query, page)
            shelf != null -> HubDiscover.shelf(shelf, page)
            else -> null
        }

        /** Group W (B1): the results page this row's "See all" opens (a ready shelf, or the row's ask as filters). */
        fun filter(): dev.asfakul.yumeiro.sources.hub.DiscoverFilter = when {
            query != null -> dev.asfakul.yumeiro.sources.hub.DiscoverFilter.of(query, title)
            else -> dev.asfakul.yumeiro.sources.hub.DiscoverFilter(shelf = shelf, title = title)
        }
    }
    @Immutable data class IfYouLike(override val key: String = "if_you_like") : Shelf
    @Immutable data class Services(val kind: String, override val key: String = "services_$kind") : Shelf
    /** [kind]: "movie", "tv" or "anime". */
    @Immutable data class Genres(val kind: String, override val key: String = "genres_$kind") : Shelf {
        val genreKind: dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind get() = when (kind) {
            "movie" -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.MOVIE
            "tv" -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.TV
            "anime" -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ANIME
            else -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ALL
        }
    }

    private fun q(kind: String, sort: HubDiscover.Sort, anime: Boolean = false) = HubDiscover.Query(kind, sort, anime = anime)

    /** Pure: the shelves of a section, in order (unit-tested). */
    fun shelves(section: Section): List<Shelf> = when (section) {
        Section.DISCOVER -> listOf(
            Row("hot", tr(R.string.hot_right_now_2, "Hot right now"), null, "hot_today"), IfYouLike(), Services("all"), Genres("all"),
            Row("top_all", tr(R.string.all_time_greats, "All-time greats"), null, "top_movies"),
        )
        Section.MOVIES -> listOf(
            Row("m_pop", tr(R.string.popular_movies, "Popular movies"), q("movie", HubDiscover.Sort.POPULAR), null), Services("movie"), Genres("movie"),
            Row("m_new", tr(R.string.new_movies, "New movies"), q("movie", HubDiscover.Sort.NEW), null), Row("m_top", tr(R.string.top_rated_movies, "Top rated movies"), q("movie", HubDiscover.Sort.TOP), null),
        )
        Section.TV -> listOf(
            Row("t_pop", tr(R.string.series_everyone_is_watching, "Series everyone is watching"), q("tv", HubDiscover.Sort.POPULAR), null), Services("tv"), Genres("tv"),
            Row("t_new", tr(R.string.new_series, "New series"), q("tv", HubDiscover.Sort.NEW), null), Row("t_top", tr(R.string.top_rated_series, "Top rated series"), q("tv", HubDiscover.Sort.TOP), null),
        )
        Section.ANIME -> listOf(
            Row("a_pop", tr(R.string.popular_anime, "Popular anime"), q("tv", HubDiscover.Sort.POPULAR, anime = true), null), Genres("anime"),
            Row("a_new", tr(R.string.new_anime, "New anime"), q("tv", HubDiscover.Sort.NEW, anime = true), null),
            Row("a_top", tr(R.string.top_rated_anime, "Top rated anime"), q("tv", HubDiscover.Sort.TOP, anime = true), null),
            Row("a_mov", tr(R.string.anime_movies, "Anime movies"), q("movie", HubDiscover.Sort.POPULAR, anime = true), null),
        )
        Section.TOP -> listOf(
            Row("top_m", tr(R.string.top_rated_movies, "Top rated movies"), q("movie", HubDiscover.Sort.TOP), null), Row("top_t", tr(R.string.top_rated_series, "Top rated series"), q("tv", HubDiscover.Sort.TOP), null),
            Row("top_a", tr(R.string.top_rated_anime, "Top rated anime"), q("tv", HubDiscover.Sort.TOP, anime = true), null),
        )
        Section.HOT -> listOf(
            Row("hot", tr(R.string.hot_right_now_2, "Hot right now"), null, "hot_today"), IfYouLike(),
            Row("hot_m", tr(R.string.trending_movies, "Trending movies"), null, "trending_movies"), Row("hot_t", tr(R.string.trending_series, "Trending series"), null, "trending_tv"),
        )
    }

    /** The phone's region for streaming services (ISO 3166-1, US when the locale has none). */
    fun region(locale: Locale = Locale.getDefault()): String = locale.country.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } } ?: "US"

    /** Pure: seeds for "If you like": catalog titles the user opened (most recent first), then today's hot titles. */
    fun seeds(watched: List<OnlineItem>, hot: List<OnlineItem>, max: Int = 6): List<OnlineItem> =
        (watched + hot).filter { YumeiroCatalog.refOf(it.url) != null }.distinctBy { it.url }.take(max)

    /** Shown pages, kept while the app runs, so coming back to a page does not flash empty shelves. */
    internal val memory = ConcurrentHashMap<String, HubDiscover.Page>()
    internal val providerMemory = ConcurrentHashMap<String, List<HubDiscover.Provider>>()
    /** Group W (B3): the country whose list [providerMemory] holds for a phone region (Bangladesh → India). */
    internal val providerRegion = ConcurrentHashMap<String, String>()
    /** Group W (B2): genre card art ("" = the genre has none), per kind and genre. */
    internal val genreArt = ConcurrentHashMap<String, String>()

    /** The section shown on Home and Yumix. */
    internal var section by mutableStateOf(Section.DISCOVER)

    /** Group W (B1, B13): "See all", a genre, a service: each opens its own page (a real route, Back returns). */
    fun openRow(row: Row) = dev.asfakul.yumeiro.ui.navigation.NavRequests.open(dev.asfakul.yumeiro.ui.navigation.Screen.OnlineDiscover.route(row.filter()))
    fun openFilter(f: dev.asfakul.yumeiro.sources.hub.DiscoverFilter) = dev.asfakul.yumeiro.ui.navigation.NavRequests.open(dev.asfakul.yumeiro.ui.navigation.Screen.OnlineDiscover.route(f))
    fun openGenres(kind: dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind) = dev.asfakul.yumeiro.ui.navigation.NavRequests.open(dev.asfakul.yumeiro.ui.navigation.Screen.OnlineGenres.route(kind))
    fun openServices() = dev.asfakul.yumeiro.ui.navigation.NavRequests.open(dev.asfakul.yumeiro.ui.navigation.Screen.OnlineServices.route)
}

/** Home lays its list out with side gutters; Discover rows run edge to edge like Yumix's (they pad themselves). */
internal fun Modifier.discoverBleed(gutter: androidx.compose.ui.unit.Dp): Modifier = if (gutter.value <= 0f) this else layout { m, c ->
    val px = gutter.roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
    // The parent sees the width coerced to its constraints and centres the wider child: edge to edge.
    layout(p.width, p.height) { p.place(0, 0) }
}

private val Chip = RoundedCornerShape(50)
private val Tile = yuRound(16)

/**
 * The section bar: "Find something" (opens the results page with every filter) above one row of section chips.
 * Group W (B1): before, "Find something new" was only a heading; nothing on it could be tapped.
 */
@Composable
internal fun DiscoverBar(section: Discover.Section, onPick: (Discover.Section) -> Unit, modifier: Modifier = Modifier, onFind: () -> Unit = { Discover.openFilter(dev.asfakul.yumeiro.sources.hub.DiscoverFilter()) }) {
    Column(modifier.fillMaxWidth().testTag("discover_bar")) {
        dev.asfakul.yumeiro.ui.components.YuSection("発見", "Discover", tr(R.string.find_something_new, "Find something new"), modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().height(48.dp).clip(Chip)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dw_find_something), onClick = onFind).padding(horizontal = 16.dp).testTag("discover_find"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.dw_find_hint), color = TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Discover.Section.entries, key = { it.name }) { s ->
                OnlineChip(s.label, s == section, { onPick(s) }, Modifier.testTag("discover_section_${s.name.lowercase()}"))
            }
        }
    }
}

/**
 * One shelf of the current section. Group W (B12): while it loads it shows placeholders of its loaded size and a
 * failed shelf says so with Try again in the same space; only a shelf that truly has nothing takes no space.
 */
@Composable
internal fun DiscoverShelf(shelf: Discover.Shelf, seeds: List<OnlineItem>, onOpen: (OnlineItem) -> Unit, onGrid: (Discover.Row) -> Unit = Discover::openRow) {
    when (shelf) {
        is Discover.Row -> DiscoverRow(shelf, onOpen, onGrid)
        is Discover.IfYouLike -> IfYouLikeRow(seeds, onOpen, onGrid)
        is Discover.Services -> ServicesRow(shelf.kind)
        is Discover.Genres -> GenresRow(shelf.genreKind)
    }
}

/** A loaded page, or null while loading, or [Failed] (kept apart so a failure is not shown as "loading"). */
private val Failed = HubDiscover.Page(emptyList(), hasNext = false)

@Composable
private fun rowPage(row: Discover.Row, attempt: Int): HubDiscover.Page? {
    // produceState keeps its value when the key changes, so each key reads its own memory (or loads) instead of
    // trusting the old value: a changed row never shows the previous row's titles.
    val mem = row.key + row.query + row.shelf
    val page by produceState(Discover.memory[mem], mem, attempt) {
        value = Discover.memory[mem]
        if (value == null) value = row.load(1)?.also { Discover.memory[mem] = it } ?: Failed
    }
    return page
}

/** Header with "See all", then the posters, placeholders or a Try again card, always at the posters' height. */
@Composable
private fun DiscoverRow(row: Discover.Row, onOpen: (OnlineItem) -> Unit, onGrid: (Discover.Row) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val page = rowPage(row, attempt)
    val items = remember(page) { page?.items.orEmpty().map { it.toOnlineItem() } }
    if (page != null && page !== Failed && items.isEmpty()) return
    Column(Modifier.testTag("discover_row_${row.key}")) {
        // Group W (B12): See all is laid out while loading too (invisible, not focusable), so the header keeps its height.
        SectionHeader(row.title, subtitle = tr(R.string.dw_subtitle_shelf, "Yumeiro Cloud · TMDB")) {
            val ready = items.isNotEmpty()
            Box(if (ready) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}.focusProperties { canFocus = false }) { SeeAllButton { if (ready) onGrid(row) } }
        }
        when {
            page == null -> ShelfPostersSkeleton(112.dp)
            page === Failed -> ShelfError { attempt++ }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onOpen(it) }, width = 112.dp) }
            }
        }
    }
}

/** A failed shelf: the message and Try again over a still placeholder row, so the page does not move. */
@Composable
private fun ShelfError(posters: Boolean = true, height: androidx.compose.ui.unit.Dp = ServiceCardHeight, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().testTag("discover_shelf_error"), contentAlignment = Alignment.Center) {
        if (posters) Box(Modifier.alpha(0f)) { ShelfPostersSkeleton(112.dp, still = true) } else Spacer(Modifier.fillMaxWidth().height(height))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.yumeiro_cloud_didnt_answer), color = TextSecondary, fontSize = 13.sp)
            OnlineChip(stringResource(R.string.try_again), false, onRetry, Modifier.padding(top = 8.dp).testTag("discover_shelf_retry"))
        }
    }
}

@Composable
private fun IfYouLikeRow(seeds: List<OnlineItem>, onOpen: (OnlineItem) -> Unit, onGrid: (Discover.Row) -> Unit) {
    // Seeds from what the user opened; with none, today's hot titles (the hot shelf is in memory by then).
    val hot = Discover.memory.entries.firstOrNull { it.key.startsWith("hot") }?.value?.items.orEmpty().map { it.toOnlineItem() }
    val all = remember(seeds, hot) { Discover.seeds(seeds, hot) }
    if (all.isEmpty()) return
    var seedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val seed = all.firstOrNull { it.url == seedUrl } ?: all.first()
    val ref = YumeiroCatalog.refOf(seed.url) ?: return
    val key = "like_${ref.kind}_${ref.id}"
    var attempt by remember { mutableIntStateOf(0) }
    val page by produceState(Discover.memory[key], key, attempt) {
        // A new seed: drop the old seed's titles at once, then show the new ones (cached or loaded).
        value = Discover.memory[key]
        if (value == null) value = HubDiscover.recommend(ref.kind, ref.id)?.also { Discover.memory[key] = it } ?: Failed
    }
    val items = remember(page) { page?.items.orEmpty().map { it.toOnlineItem() } }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.testTag("discover_if_you_like")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.if_you_like), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box {
                Row(
                    Modifier.clip(Chip).clickable(role = Role.DropdownList, onClickLabel = tr(R.string.pick_another_title, "Pick another title")) { menu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp).testTag("discover_seed"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(seed.name, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    all.forEach { s ->
                        DropdownMenuItem(text = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { seedUrl = s.url; menu = false }, modifier = Modifier.testTag("discover_seed_option"))
                    }
                }
            }
        }
        when {
            page == null -> ShelfPostersSkeleton(112.dp)
            page === Failed -> ShelfError { attempt++ }
            items.isEmpty() -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.alpha(0f)) { ShelfPostersSkeleton(112.dp, still = true) }
                Text(stringResource(R.string.nothing_similar_listed_yet_pick_another), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onOpen(it) }, width = 112.dp) }
            }
        }
    }
}

/**
 * Group W (B3): streaming services in the phone's country (or the nearest listed one), big services first, as
 * wide logo cards; "See all" opens every service. Loading and failing keep the row's height.
 */
@Composable
private fun ServicesRow(kind: String) {
    val region = remember { Discover.region() }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val list by produceState(Discover.providerMemory[region], region, attempt) {
        if (value == null) {
            val got = HubDiscover.providersNear(region)
            if (got == null) failed = true else { failed = false; Discover.providerMemory[region] = got.second; Discover.providerRegion[region] = got.first; value = got.second }
        }
    }
    val shown = list.orEmpty().take(16)
    if (list != null && shown.isEmpty()) return
    val shownRegion = Discover.providerRegion[region] ?: region
    val cardW = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 900) 220.dp else 176.dp
    Column(Modifier.testTag("discover_services")) {
        SectionHeader(stringResource(R.string.dw_services_title), subtitle = stringResource(R.string.dw_in_country, countryName(shownRegion))) {
            SeeAllButton { Discover.openServices() }
        }
        when {
            list == null && failed -> ShelfError(posters = false) { failed = false; attempt++ }
            list == null -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false) {
                items(6) { Box(Modifier.width(cardW).height(ServiceCardHeight).shimmer(Tile)) }
            }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { p ->
                    ServiceCard(p, Modifier.width(cardW).testTag("discover_service_${p.id}")) {
                        Discover.openFilter(dev.asfakul.yumeiro.sources.hub.DiscoverFilter(kind = serviceKind(kind), services = listOf(p.id), region = shownRegion, title = p.name))
                    }
                }
                item("all") { SeeAllCard(cardW, ServiceCardHeight, stringResource(R.string.dw_all_services)) { Discover.openServices() } }
            }
        }
    }
}

/** "movie" / "tv" / anything else (films and series). */
internal fun serviceKind(kind: String) = when (kind) {
    "movie" -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.MOVIE
    "tv" -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.TV
    else -> dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ALL
}

/** Group W (B2): the genre rail: art cards for the main genres of [kind] (themes for anime), "See all" opens them all. */
@Composable
private fun GenresRow(kind: dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind) {
    val list = remember(kind) {
        val all = dev.asfakul.yumeiro.sources.hub.GenreCatalog.forKind(kind)
        val group = if (kind == dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ANIME) dev.asfakul.yumeiro.sources.hub.GenreCatalog.Group.ANIME else dev.asfakul.yumeiro.sources.hub.GenreCatalog.Group.MAIN
        all.filter { it.group == group }.take(14)
    }
    Column(Modifier.testTag("discover_genres")) {
        SectionHeader(stringResource(if (kind == dev.asfakul.yumeiro.sources.hub.GenreCatalog.Kind.ANIME) R.string.dw_anime_themes else R.string.dw_genres), subtitle = kindLabel(kind)) {
            SeeAllButton { Discover.openGenres(kind) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.key }) { g ->
                Box(Modifier.testTag("discover_genre_${g.key}")) {
                    GenreArtCard(g, kind, width = 164.dp) { Discover.openFilter(dev.asfakul.yumeiro.sources.hub.DiscoverFilter(kind = kind, genres = listOf(g.key), title = g.name)) }
                }
            }
            item("all") { SeeAllCard(164.dp, 92.dp, stringResource(R.string.dw_all_genres)) { Discover.openGenres(kind) } }
        }
    }
}

/** The last card of a rail (reachable with the remote on TV, where the header's "See all" is far away). */
@Composable
private fun SeeAllCard(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, label: String, onClick: () -> Unit) {
    Box(
        Modifier.width(width).height(height).clip(Tile).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(role = Role.Button, onClick = onClick).testTag("discover_see_all_card"),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
