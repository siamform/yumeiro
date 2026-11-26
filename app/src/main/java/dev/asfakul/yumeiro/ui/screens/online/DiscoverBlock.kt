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
        DISCOVER("Discover"), MOVIES("Movies"), TV("TV Shows"), ANIME("Anime"), TOP("Top Rated"), HOT("Hot Right Now"),
    }

    sealed interface Shelf { val key: String }

    /** A poster row with a full grid behind "See all". */
    @Immutable data class Row(override val key: String, val title: String, val query: HubDiscover.Query?, val shelf: String?) : Shelf {
        suspend fun load(page: Int): HubDiscover.Page? = when {
            query != null -> HubDiscover.page(query, page)
            shelf != null -> HubDiscover.shelf(shelf, page)
            else -> null
        }
    }
    @Immutable data class IfYouLike(override val key: String = "if_you_like") : Shelf
    @Immutable data class Services(val kind: String, override val key: String = "services_$kind") : Shelf
    @Immutable data class Genres(val kind: String, override val key: String = "genres_$kind") : Shelf

    private fun q(kind: String, sort: HubDiscover.Sort, anime: Boolean = false) = HubDiscover.Query(kind, sort, anime = anime)

    /** Pure: the shelves of a section, in order (unit-tested). */
    fun shelves(section: Section): List<Shelf> = when (section) {
        Section.DISCOVER -> listOf(
            Row("hot", "Hot right now", null, "hot_today"), IfYouLike(), Services("movie"), Genres("movie"),
            Row("top_all", "All-time greats", null, "top_movies"),
        )
        Section.MOVIES -> listOf(
            Row("m_pop", "Popular movies", q("movie", HubDiscover.Sort.POPULAR), null), Services("movie"), Genres("movie"),
            Row("m_new", "New movies", q("movie", HubDiscover.Sort.NEW), null), Row("m_top", "Top rated movies", q("movie", HubDiscover.Sort.TOP), null),
        )
        Section.TV -> listOf(
            Row("t_pop", "Series everyone is watching", q("tv", HubDiscover.Sort.POPULAR), null), Services("tv"), Genres("tv"),
            Row("t_new", "New series", q("tv", HubDiscover.Sort.NEW), null), Row("t_top", "Top rated series", q("tv", HubDiscover.Sort.TOP), null),
        )
        Section.ANIME -> listOf(
            Row("a_pop", "Popular anime", q("tv", HubDiscover.Sort.POPULAR, anime = true), null),
            Row("a_new", "New anime", q("tv", HubDiscover.Sort.NEW, anime = true), null),
            Row("a_top", "Top rated anime", q("tv", HubDiscover.Sort.TOP, anime = true), null),
            Row("a_mov", "Anime movies", q("movie", HubDiscover.Sort.POPULAR, anime = true), null),
        )
        Section.TOP -> listOf(
            Row("top_m", "Top rated movies", q("movie", HubDiscover.Sort.TOP), null), Row("top_t", "Top rated series", q("tv", HubDiscover.Sort.TOP), null),
            Row("top_a", "Top rated anime", q("tv", HubDiscover.Sort.TOP, anime = true), null),
        )
        Section.HOT -> listOf(
            Row("hot", "Hot right now", null, "hot_today"), IfYouLike(),
            Row("hot_m", "Trending movies", null, "trending_movies"), Row("hot_t", "Trending series", null, "trending_tv"),
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

    /** The open "See all" grid (kept while a title from it is open, so Back returns to the grid) and the section. */
    internal var grid by mutableStateOf<Row?>(null)
    internal var section by mutableStateOf(Section.DISCOVER)
}

/** Home lays its list out with side gutters; Discover rows run edge to edge like Yumix's (they pad themselves). */
internal fun Modifier.discoverBleed(gutter: androidx.compose.ui.unit.Dp): Modifier = if (gutter.value <= 0f) this else layout { m, c ->
    val px = gutter.roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
    // The parent sees the width coerced to its constraints and centres the wider child: edge to edge.
    layout(p.width, p.height) { p.place(0, 0) }
}

private val Chip = RoundedCornerShape(50)
private val Tile = RoundedCornerShape(16.dp)

/** The section bar (one row of chips). */
@Composable
internal fun DiscoverBar(section: Discover.Section, onPick: (Discover.Section) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("discover_bar")) {
        dev.asfakul.yumeiro.ui.components.YuSection("発見", "Discover", "Find something new", modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Discover.Section.entries, key = { it.name }) { s ->
                OnlineChip(s.label, s == section, { onPick(s) }, Modifier.testTag("discover_section_${s.name.lowercase()}"))
            }
        }
    }
}

/** One shelf of the current section. Empty or failed shelves take no space. */
@Composable
internal fun DiscoverShelf(shelf: Discover.Shelf, seeds: List<OnlineItem>, onOpen: (OnlineItem) -> Unit, onGrid: (Discover.Row) -> Unit) {
    when (shelf) {
        is Discover.Row -> DiscoverRow(shelf, onOpen, onGrid)
        is Discover.IfYouLike -> IfYouLikeRow(seeds, onOpen, onGrid)
        is Discover.Services -> ServicesRow(shelf.kind, onGrid)
        is Discover.Genres -> GenresGrid(shelf.kind, onGrid)
    }
}

@Composable
private fun rowPage(row: Discover.Row): HubDiscover.Page? {
    val page by produceState(Discover.memory[row.key + row.query + row.shelf], row) {
        if (value == null) row.load(1)?.let { Discover.memory[row.key + row.query + row.shelf] = it; value = it }
    }
    return page
}

@Composable
private fun DiscoverRow(row: Discover.Row, onOpen: (OnlineItem) -> Unit, onGrid: (Discover.Row) -> Unit) {
    val page = rowPage(row)
    val items = remember(page) { page?.items.orEmpty().map { it.toOnlineItem() } }
    if (items.isEmpty()) return
    Column(Modifier.testTag("discover_row_${row.key}")) {
        SectionHeader(row.title, subtitle = "Yumeiro Cloud · TMDB") { SeeAllButton { onGrid(row) } }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onOpen(it) }, width = 112.dp) }
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
    val page by produceState(Discover.memory[key], key) {
        if (value == null) HubDiscover.recommend(ref.kind, ref.id)?.let { Discover.memory[key] = it; value = it }
    }
    val items = remember(page) { page?.items.orEmpty().map { it.toOnlineItem() } }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.testTag("discover_if_you_like")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("If you like ", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box {
                Row(
                    Modifier.clip(Chip).clickable(role = Role.DropdownList, onClickLabel = "Pick another title") { menu = true }
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
            page == null -> PosterGridSkeleton(1)
            items.isEmpty() -> Text("Nothing similar listed yet. Pick another title.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items, key = { i, it -> "${i}_${it.key}" }) { _, it -> PosterCard(it, onClick = { onOpen(it) }, width = 112.dp) }
            }
        }
    }
}

@Composable
private fun ServicesRow(kind: String, onGrid: (Discover.Row) -> Unit) {
    val region = remember { Discover.region() }
    val list by produceState(Discover.providerMemory[region], region) {
        if (value == null) HubDiscover.providers(region)?.let { Discover.providerMemory[region] = it; value = it }
    }
    val shown = list.orEmpty().take(16)
    if (shown.isEmpty()) return
    Column(Modifier.testTag("discover_services")) {
        SectionHeader("Streaming services", subtitle = "In ${Locale("", region).displayCountry.ifBlank { region }}")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { p ->
                Column(
                    Modifier.width(88.dp).clip(Tile).clickable(role = Role.Button) {
                        onGrid(Discover.Row("svc_${kind}_${p.id}", "${p.name} · ${if (kind == "tv") "Series" else "Movies"}", HubDiscover.Query(kind, providers = listOf(p.id), region = region), null))
                    }.padding(4.dp).testTag("discover_service_${p.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(72.dp).clip(Tile).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        if (p.logo != null) AsyncImage(model = p.logo, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(p.name.take(2), color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(p.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** F9: the genre grid (Hikari has genre chips; here tiles with a colour each, two lines on a phone). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresGrid(kind: String, onGrid: (Discover.Row) -> Unit) {
    val genres = if (kind == "tv") HubDiscover.TV_GENRES else HubDiscover.MOVIE_GENRES
    Column(Modifier.testTag("discover_genres")) {
        SectionHeader("Genres", subtitle = if (kind == "tv") "Series" else "Movies")
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.forEach { (id, name) ->
                val hue = (id * 47 % 360).toFloat()
                Box(
                    Modifier.clip(Tile)
                        .background(Brush.linearGradient(listOf(Color.hsl(hue, 0.45f, 0.32f), Color.hsl((hue + 30) % 360, 0.5f, 0.22f))))
                        .clickable(role = Role.Button) { onGrid(Discover.Row("genre_${kind}_$id", name, HubDiscover.Query(kind, genres = listOf(id)), null)) }
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("discover_genre_$id"),
                ) { Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
    }
}

/** "See all": every page of a row in a grid, full screen (back closes it). */
@Composable
internal fun DiscoverGrid(row: Discover.Row, onOpen: (OnlineItem) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val items = remember(row) { mutableStateListOf<OnlineItem>() }
        var page by remember(row) { mutableStateOf(0) }
        var more by remember(row) { mutableStateOf(true) }
        var busy by remember(row) { mutableStateOf(false) }
        var failed by remember(row) { mutableStateOf(false) }
        val state = rememberLazyGridState()
        val nearEnd by remember { derivedStateOf { val i = state.layoutInfo; (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 9 } }
        LaunchedEffect(row, nearEnd, more, failed) {
            if (!nearEnd || !more || busy || failed) return@LaunchedEffect
            busy = true
            val got = row.load(page + 1)
            if (got == null) failed = true else {
                val have = items.mapTo(HashSet()) { it.url }
                items += got.items.map { it.toOnlineItem() }.filter { have.add(it.url) }
                page++; more = got.hasNext && page < 20
            }
            busy = false
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().testTag("discover_grid")) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("discover_grid_back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
                Text(row.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyVerticalGrid(
                GridCells.Adaptive(108.dp), state = state, contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.url }) { PosterCard(it, onClick = { onOpen(it) }) }
                if (busy || (more && !failed)) item(span = { GridItemSpan(maxLineSpan) }) { PosterGridSkeleton(1) }
                if (failed) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Yumeiro Cloud didn't answer.", color = TextSecondary, fontSize = 13.sp)
                        OnlineChip("Try again", false, { failed = false }, Modifier.padding(top = 8.dp).testTag("discover_grid_retry"))
                    }
                }
                if (!more && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nothing listed here yet.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(16.dp).testTag("discover_grid_empty"))
                }
                if (items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Data: TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}
