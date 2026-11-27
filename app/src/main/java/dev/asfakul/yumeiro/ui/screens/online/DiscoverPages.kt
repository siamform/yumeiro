/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.ui.components.sheetDragHandle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.sources.hub.DiscoverFilter
import dev.asfakul.yumeiro.sources.hub.GenreCatalog
import dev.asfakul.yumeiro.sources.hub.GenreMemory
import dev.asfakul.yumeiro.sources.hub.HubDiscover
import dev.asfakul.yumeiro.sources.hub.HubDiscover.toOnlineItem
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.shimmer
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/*
 * Group W (B1, B2, B3): the "Find something" results page, the Genres page and the Streaming services page.
 *
 * Studied (behaviour only, no code; both GPL-3.0): Zangetsu's services (films and series providers merged, the big
 * ones first, rent stores left out, wide logo cards, a See all page) and Hikari's genre page (search, two columns of
 * art cards, anime themes). Yumeiro's own take: one results page that every card opens with a filter preset, so a
 * genre, a service and "See all" all land on the same page where the filters can be changed, the choice kept in the
 * route (Back returns to the same results).
 */

private val Pill = RoundedCornerShape(50)

/** "Find something": catalog titles with filters; every card opens its catalog title. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(initial: DiscoverFilter, onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit) {
    var encoded by rememberSaveable { mutableStateOf(initial.encode()) }
    val filter = remember(encoded) { DiscoverFilter.decode(encoded) }
    val set: (DiscoverFilter) -> Unit = { encoded = it.encode() }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val key = encoded
    val items = remember(key) { mutableStateListOf<OnlineItem>().also { l -> Discover.memory["dsc|$key"]?.let { p -> l += p.items.map { it.toOnlineItem() } } } }
    var page by remember(key) { mutableIntStateOf(if (Discover.memory["dsc|$key"] != null) 1 else 0) }
    var more by remember(key) { mutableStateOf(Discover.memory["dsc|$key"]?.hasNext ?: true) }
    var busy by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    val state = rememberLazyGridState()
    LaunchedEffect(key) { if (page == 0) state.scrollToItem(0) }
    val nearEnd by remember { derivedStateOf { val i = state.layoutInfo; (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 9 } }
    LaunchedEffect(key, nearEnd, more, failed, page) {
        if ((!nearEnd && page > 0) || !more || busy || failed) return@LaunchedEffect
        busy = true
        val got = filter.load(page + 1)
        if (got == null) failed = true else {
            val have = items.mapTo(HashSet()) { it.url }
            items += got.items.map { it.toOnlineItem() }.filter { have.add(it.url) }
            page++; more = got.hasNext && page < 20
            if (page == 1) Discover.memory["dsc|$key"] = got
        }
        busy = false
    }
    Column(Modifier.fillMaxSize().testTag("discover_page")) {
        SubPageHeader(
            filter.title ?: stringResource(R.string.dw_find_something), onBack = onBack,
            subtitle = stringResource(if (filter.filterable) R.string.dw_subtitle_filterable else R.string.dw_subtitle_shelf),
        ) {
            if (filter.filterable) FilterButton(filter.activeCount) { sheet = true }
        }
        if (filter.filterable) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("discover_kinds")) {
                items(GenreCatalog.Kind.entries, key = { it.name }) { k ->
                    OnlineChip(kindLabel(k), filter.kind == k, { set(filter.copy(kind = k, only = null, genres = filter.genres.filter { g -> GenreCatalog.byKey(g)?.supports(k) == true })) },
                        Modifier.testTag("discover_kind_${k.name.lowercase()}"))
                }
                item("sort") { SortChip(filter.sort) { set(filter.copy(sort = it)) } }
            }
            ActiveFilters(filter, onChange = set)
        }
        LazyVerticalGrid(
            GridCells.Adaptive(108.dp), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("discover_results"),
        ) {
            items(items, key = { it.url }) { PosterCard(it, onClick = { onOpenTitle(it) }, modifier = Modifier.fillMaxWidth()) }
            if (items.isEmpty() && !failed && more) items(12, key = { "sk$it" }) { PosterCardSkeleton(null) }
            else if (busy || (more && !failed)) items(3, key = { "more$it" }) { PosterCardSkeleton(null) }
            if (failed) item(span = { GridItemSpan(maxLineSpan) }, key = "failed") {
                StateMessage(
                    Icons.Default.ErrorOutline, stringResource(R.string.dw_cloud_error_title), stringResource(R.string.dw_cloud_error_text),
                    primary = stringResource(R.string.try_again) to { failed = false }, testTag = "discover_error", suggestions = false,
                )
            }
            if (!more && items.isEmpty() && !failed) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                StateMessage(
                    Icons.Default.SearchOff, stringResource(R.string.dw_nothing_matches), stringResource(if (filter.activeCount > 0) R.string.dw_try_fewer else R.string.dw_nothing_listed),
                    primary = if (filter.activeCount > 0) stringResource(R.string.dw_clear_filters) to { set(filter.cleared()) } else null, testTag = "discover_empty",
                )
            }
            if (items.isNotEmpty() && !more) item(span = { GridItemSpan(maxLineSpan) }, key = "credit") {
                Text(stringResource(R.string.dw_tmdb_credit), color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    if (sheet) FilterSheet(filter, onDismiss = { sheet = false }, onApply = { set(it); sheet = false })
}

@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    BadgedBox(badge = { if (count > 0) Badge(containerColor = SpotifyGreen) { Text(count.toString(), color = Color.Black) } }) {
        val label = if (count > 0) stringResource(R.string.dw_filters_on, count) else stringResource(R.string.filters)
        IconButton(onClick = onClick, modifier = Modifier.testTag("discover_filters").semantics { contentDescription = label }) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = TextPrimary)
        }
    }
}

@Composable
private fun SortChip(sort: HubDiscover.Sort, onPick: (HubDiscover.Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OnlineChip(stringResource(R.string.dw_sort_label, sortLabel(sort)), false, { open = true }, Modifier.testTag("discover_sort"))
        DropdownMenu(open, onDismissRequest = { open = false }) {
            HubDiscover.Sort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(sortLabel(s)) }, onClick = { onPick(s); open = false }, modifier = Modifier.testTag("discover_sort_${s.key}").semantics { selected = s == sort })
            }
        }
    }
}

@Composable
internal fun sortLabel(s: HubDiscover.Sort): String = stringResource(when (s) {
    HubDiscover.Sort.POPULAR -> R.string.dw_sort_popular
    HubDiscover.Sort.TOP -> R.string.dw_sort_top
    HubDiscover.Sort.NEW -> R.string.dw_sort_new
    HubDiscover.Sort.UPCOMING -> R.string.dw_sort_upcoming
    HubDiscover.Sort.RATING -> R.string.dw_sort_rating
})

@Composable
internal fun kindLabel(k: GenreCatalog.Kind): String = stringResource(when (k) {
    GenreCatalog.Kind.ALL -> R.string.dw_kind_all
    GenreCatalog.Kind.MOVIE -> R.string.dw_kind_movie
    GenreCatalog.Kind.TV -> R.string.dw_kind_tv
    GenreCatalog.Kind.ANIME -> R.string.dw_kind_anime
})

@Composable
internal fun groupLabel(g: GenreCatalog.Group): String = stringResource(when (g) {
    GenreCatalog.Group.MAIN -> R.string.dw_genres
    GenreCatalog.Group.ANIME -> R.string.dw_anime_themes
    GenreCatalog.Group.THEMES -> R.string.dw_themes
})

/** The filters that are on, each with ×, then "Clear all". Wraps onto more lines (labels are never cut). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(f: DiscoverFilter, onChange: (DiscoverFilter) -> Unit) {
    if (f.activeCount == 0) return
    val services = Discover.providerMemory.values.flatten().associateBy { it.id }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("discover_active"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        f.genres.forEach { g -> RemovableChip(GenreCatalog.byKey(g)?.name ?: g) { onChange(f.copy(genres = f.genres - g)) } }
        f.services.forEach { s -> RemovableChip(services[s]?.name ?: stringResource(R.string.dw_service)) { onChange(f.copy(services = f.services - s)) } }
        f.origin?.let { RemovableChip(stringResource(R.string.dw_from_country, countryName(it))) { onChange(f.copy(origin = null)) } }
        f.lang?.let { RemovableChip(Locale(it).getDisplayLanguage(LocalConfiguration.current.locales[0]).ifBlank { it }) { onChange(f.copy(lang = null)) } }
        if (f.yearFrom != null || f.yearTo != null) RemovableChip(yearsLabel(f.yearFrom, f.yearTo)) { onChange(f.copy(yearFrom = null, yearTo = null)) }
        f.minRating?.let { RemovableChip(stringResource(R.string.dw_score_at_least, it)) { onChange(f.copy(minRating = null)) } }
        TextButton(onClick = { onChange(f.cleared()) }, modifier = Modifier.heightIn(min = 32.dp).testTag("discover_clear")) { Text(stringResource(R.string.dw_clear_all), color = SpotifyGreen, fontSize = 12.sp) }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true, onClick = onRemove, label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dw_remove, label), modifier = Modifier.size(16.dp)) },
        modifier = Modifier.testTag("discover_active_chip"),
    )
}

@Composable
internal fun yearsLabel(from: Int?, to: Int?): String = when {
    from != null && to != null && from == to -> "%d".format(from)
    from != null && to != null -> stringResource(R.string.dw_years_range, from, to)
    from != null -> stringResource(R.string.dw_years_from, from)
    to != null -> stringResource(R.string.dw_years_to, to)
    else -> stringResource(R.string.dw_years_any)
}

/** The country's name in the app's language (Android's own list of names). */
internal fun countryName(iso: String) = Locale("", iso).getDisplayCountry(Locale.getDefault()).ifBlank { iso }

/** Countries titles often come from (origin filter), the user's own first. */
internal fun originChoices(phone: String): List<String> =
    (listOf(phone) + listOf("US", "GB", "JP", "KR", "CN", "IN", "FR", "ES", "DE", "IT", "TR", "TH", "BR", "MX", "BD")).filter { it.length == 2 }.distinct()

/** Year spans offered (from, to); null = open. */
internal fun yearChoices(now: Int = LocalDate.now().year): List<Pair<Int?, Int?>> =
    listOf(now to now, (now - 1) to (now - 1), 2020 to null, 2010 to 2019, 2000 to 2009, 1990 to 1999, null to 1989)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(current: DiscoverFilter, onDismiss: () -> Unit, onApply: (DiscoverFilter) -> Unit) {
    var draft by remember { mutableStateOf(current) }
    val region = remember { current.region ?: Discover.region() }
    val services by produceState(Discover.providerMemory[region], region) {
        if (value == null) HubDiscover.providersNear(region)?.let { (r, list) -> Discover.providerMemory[region] = list; Discover.providerRegion[region] = r; value = list }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("discover_sheet")) {
      // Group W (B1): Reset / Show results stay pinned under the scrolling filters (they were at the very end of a
      // long list, out of reach on phones and several D-pad presses away on TV).
      Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp).testTag("discover_sheet_list")) {
            Text(stringResource(R.string.filters), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val genres = GenreCatalog.forKind(draft.kind)
            SheetGroup(stringResource(R.string.dw_genres), stringResource(R.string.dw_genres_hint, DiscoverFilter.MAX_GENRES)) {
                genres.filter { it.group == GenreCatalog.Group.MAIN }.forEach { g -> GenreToggle(g, draft) { draft = it } }
            }
            if (draft.kind == GenreCatalog.Kind.ANIME || draft.kind == GenreCatalog.Kind.ALL) SheetGroup(stringResource(R.string.dw_anime_themes), null) {
                genres.filter { it.group == GenreCatalog.Group.ANIME }.forEach { g -> GenreToggle(g, draft) { draft = it } }
            }
            SheetGroup(stringResource(R.string.dw_themes), null) {
                genres.filter { it.group == GenreCatalog.Group.THEMES }.forEach { g -> GenreToggle(g, draft) { draft = it } }
            }
            SheetGroup(stringResource(R.string.dw_streaming_on), stringResource(R.string.dw_streaming_hint, DiscoverFilter.MAX_SERVICES, countryName(Discover.providerRegion[region] ?: region))) {
                val list = services
                when {
                    list == null -> repeat(6) { Box(Modifier.size(96.dp, 32.dp).shimmer(Pill)) }
                    list.isEmpty() -> Text(stringResource(R.string.dw_no_services_country), color = TextTertiary, fontSize = 12.sp)
                    else -> list.take(24).forEach { p ->
                        val on = p.id in draft.services
                        FilterChip(
                            selected = on, enabled = on || draft.services.size < DiscoverFilter.MAX_SERVICES,
                            onClick = { draft = draft.copy(services = if (on) draft.services - p.id else draft.services + p.id, region = Discover.providerRegion[region] ?: region) },
                            label = { Text(p.name, fontSize = 12.sp, maxLines = 1) },
                            leadingIcon = p.logo?.let { l -> { AsyncImage(l, null, Modifier.size(18.dp).clip(yuRound(4))) } },
                            modifier = Modifier.testTag("discover_sheet_service_${p.id}"),
                        )
                    }
                }
            }
            SheetGroup(stringResource(R.string.dw_from), null) {
                originChoices(Discover.region()).forEach { c ->
                    FilterChip(selected = draft.origin == c, onClick = { draft = draft.copy(origin = if (draft.origin == c) null else c) }, label = { Text(countryName(c), fontSize = 12.sp) },
                        modifier = Modifier.testTag("discover_sheet_origin_${c.lowercase()}"))
                }
            }
            SheetGroup(stringResource(R.string.dw_released), null) {
                yearChoices().forEach { (a, b) ->
                    val on = draft.yearFrom == a && draft.yearTo == b
                    FilterChip(selected = on, onClick = { draft = if (on) draft.copy(yearFrom = null, yearTo = null) else draft.copy(yearFrom = a, yearTo = b) },
                        label = { Text(yearsLabel(a, b), fontSize = 12.sp) }, modifier = Modifier.testTag("discover_sheet_year_${a ?: 0}_${b ?: 0}"))
                }
            }
            SheetGroup(stringResource(R.string.dw_score), stringResource(R.string.dw_score_hint)) {
                listOf(6, 7, 8).forEach { r ->
                    FilterChip(selected = draft.minRating == r, onClick = { draft = draft.copy(minRating = if (draft.minRating == r) null else r) },
                        label = { Text(stringResource(R.string.dw_score_plus, r), fontSize = 12.sp) }, modifier = Modifier.testTag("discover_sheet_score_$r"))
                }
            }
        }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp).testTag("discover_sheet_footer"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = draft.cleared() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("discover_sheet_reset")) { Text(stringResource(R.string.dw_reset)) }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("discover_sheet_apply"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black)) {
                    Text(if (draft.activeCount > 0) stringResource(R.string.dw_show_results_n, draft.activeCount) else stringResource(R.string.dw_show_results))
                }
            }
      }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetGroup(title: String, hint: String?, content: @Composable FlowRowScope.() -> Unit) {
    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
    hint?.let { Text(it, color = TextTertiary, fontSize = 11.sp) }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun GenreToggle(g: GenreCatalog.Genre, draft: DiscoverFilter, onChange: (DiscoverFilter) -> Unit) {
    val on = g.key in draft.genres
    FilterChip(
        selected = on, enabled = on || draft.genres.size < DiscoverFilter.MAX_GENRES,
        onClick = { onChange(draft.copy(genres = if (on) draft.genres - g.key else draft.genres + g.key)) },
        label = { Text(g.name, fontSize = 12.sp, maxLines = 1) }, modifier = Modifier.testTag("discover_sheet_genre_${g.key}"),
    )
}

// region Genres page

/** Every genre and theme by kind, searchable; a card opens its results, a learned genre searches the sources. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenresScreen(initialKind: GenreCatalog.Kind, onBack: () -> Unit, onOpen: (DiscoverFilter) -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var query by rememberSaveable { mutableStateOf("") }
    val learned by produceState(GenreCatalog.learned()) { withContext(Dispatchers.IO) { GenreMemory.load(context) }; value = GenreCatalog.learned() }
    val q = remember(query) { GenreCatalog.normalize(query) }
    val shown = remember(kind, q) {
        GenreCatalog.forKind(kind).filter { g -> q.isEmpty() || GenreCatalog.normalize(g.name).contains(q) || g.aliases.any { GenreCatalog.normalize(it).contains(q) } }
    }
    val learnedShown = remember(learned, q) { learned.filter { q.isEmpty() || GenreCatalog.normalize(it).contains(q) } }
    val tv = LocalConfiguration.current.screenWidthDp >= 900
    Column(Modifier.fillMaxSize().testTag("genres_page")) {
        SubPageHeader(stringResource(R.string.dw_genres), onBack = onBack, subtitle = stringResource(R.string.dw_genres_count, shown.size + learnedShown.size))
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(40) }, singleLine = true,
            placeholder = { Text(stringResource(R.string.dw_search_genres)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.dw_clear_search)) } },
            shape = Pill, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("genres_search"),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GenreCatalog.Kind.entries, key = { it.name }) { k -> OnlineChip(kindLabel(k), kind == k, { kind = k }, Modifier.testTag("genres_kind_${k.name.lowercase()}")) }
        }
        LazyVerticalGrid(
            if (tv) GridCells.Adaptive(220.dp) else GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("genres_grid"),
        ) {
            GenreCatalog.Group.entries.forEach { group ->
                val list = shown.filter { it.group == group }
                if (list.isEmpty()) return@forEach
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.name}") { GroupTitle(groupLabel(group)) }
                items(list, key = { it.key }) { g -> GenreArtCard(g, kind) { onOpen(DiscoverFilter(kind = kind, genres = listOf(g.key), title = g.name)) } }
            }
            if (learnedShown.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_learned") { GroupTitle(stringResource(R.string.dw_from_sources), stringResource(R.string.dw_from_sources_hint)) }
                items(learnedShown, key = { "l_$it" }) { name -> GenreArtCard(null, kind, name) { onSearch(name) } }
            }
            if (shown.isEmpty() && learnedShown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                StateMessage(Icons.Default.SearchOff, stringResource(R.string.dw_no_genre, query), stringResource(R.string.dw_search_instead), primary = stringResource(R.string.bs_search_sources) to { onSearch(query.trim()) }, testTag = "genres_empty")
            }
        }
    }
}

@Composable
private fun GroupTitle(title: String, hint: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        hint?.let { Text(it, color = TextTertiary, fontSize = 11.sp) }
    }
}

/**
 * A genre card: the backdrop of the genre's most popular title (looked up once per genre and kind, kept for the
 * session; a failed lookup is remembered too, so it is not asked again), under a shade and the name. Fixed size.
 */
@Composable
internal fun GenreArtCard(genre: GenreCatalog.Genre?, kind: GenreCatalog.Kind, label: String? = null, width: Dp? = null, onClick: () -> Unit) {
    val name = genre?.name ?: label.orEmpty()
    val artKey = genre?.let { "${kind.name}|${it.key}" }
    val art by produceState(artKey?.let { Discover.genreArt[it] }, artKey) {
        val g = genre ?: return@produceState
        if (artKey == null || Discover.genreArt.containsKey(artKey)) return@produceState
        val page = DiscoverFilter(kind = kind, genres = listOf(g.key)).load(1)
        val url = page?.items?.firstOrNull { it.backdrop != null }?.backdrop
        // Only a real answer is kept (a failed call is asked again next time the page opens).
        if (page != null) Discover.genreArt[artKey] = url.orEmpty()
        value = url.orEmpty()
    }
    val hue = ((genre?.key ?: name).hashCode().let { if (it < 0) -it else it } % 360).toFloat()
    Box(
        (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)).height(92.dp).clip(yuRound(16))
            .background(Brush.linearGradient(listOf(Color.hsl(hue, 0.45f, 0.30f), Color.hsl((hue + 40) % 360, 0.5f, 0.18f))))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dw_open, name), onClick = onClick)
            .testTag("genre_card_${genre?.key ?: "learned"}"),
        contentAlignment = Alignment.BottomStart,
    ) {
        art?.takeIf { it.isNotEmpty() }?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.72f)))))
        Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
    }
}

// endregion

// region Services page

/** Every streaming service in a country (the phone's, or one picked), each opening its titles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServicesScreen(onBack: () -> Unit, onOpen: (DiscoverFilter) -> Unit) {
    var region by rememberSaveable { mutableStateOf(Discover.region()) }
    var attempt by remember { mutableIntStateOf(0) }
    val got by produceState<Pair<String, List<HubDiscover.Provider>>?>(Discover.providerMemory[region]?.let { (Discover.providerRegion[region] ?: region) to it }, region, attempt) {
        value = Discover.providerMemory[region]?.let { (Discover.providerRegion[region] ?: region) to it }
        if (value == null) value = HubDiscover.providersNear(region)?.also { (r, l) -> Discover.providerMemory[region] = l; Discover.providerRegion[region] = r }
            ?: ("" to emptyList())
    }
    val failed = got?.first == ""
    val tv = LocalConfiguration.current.screenWidthDp >= 900
    Column(Modifier.fillMaxSize().testTag("services_page")) {
        SubPageHeader(stringResource(R.string.dw_services_title), onBack = onBack, subtitle = stringResource(R.string.dw_where_to_watch, countryName(region)))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("services_regions")) {
            items(originChoices(Discover.region()), key = { it }) { c -> OnlineChip(countryName(c), region == c, { region = c }, Modifier.testTag("services_region_${c.lowercase()}")) }
        }
        val shownRegion = got?.first?.takeIf { it.isNotEmpty() }
        if (shownRegion != null && shownRegion != region) {
            Text(stringResource(R.string.dw_services_fallback, countryName(region), countryName(shownRegion)), color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("services_fallback"))
        }
        LazyVerticalGrid(
            GridCells.Adaptive(if (tv) 200.dp else 150.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().testTag("services_grid"),
        ) {
            val list = got?.second
            when {
                got == null -> items(12, key = { "sk$it" }) { Box(Modifier.fillMaxWidth().height(ServiceCardHeight).shimmer(yuRound(16))) }
                failed -> item(span = { GridItemSpan(maxLineSpan) }, key = "err") {
                    StateMessage(Icons.Default.ErrorOutline, stringResource(R.string.dw_cloud_error_title), stringResource(R.string.dw_cloud_error_text), primary = stringResource(R.string.try_again) to { attempt++ }, testTag = "services_error", suggestions = false)
                }
                list.isNullOrEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    StateMessage(Icons.Default.SearchOff, stringResource(R.string.dw_no_services), stringResource(R.string.dw_no_services_text, countryName(region)), testTag = "services_empty")
                }
                else -> items(list, key = { it.id }) { p ->
                    ServiceCard(p, Modifier.fillMaxWidth()) { onOpen(DiscoverFilter(services = listOf(p.id), region = shownRegion ?: region, title = p.name)) }
                }
            }
        }
    }
}

internal val ServiceCardHeight = 84.dp

/** A wide service card: the logo on a soft panel, the name under it; fixed height. */
@Composable
internal fun ServiceCard(p: HubDiscover.Provider, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(ServiceCardHeight).clip(yuRound(16)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), yuRound(16))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.dw_open, p.name), onClick = onClick).padding(10.dp).testTag("service_card_${p.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(yuRound(12)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            if (p.logo != null) AsyncImage(p.logo, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(p.name.take(2), color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        Text(p.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
    }
}

// endregion
