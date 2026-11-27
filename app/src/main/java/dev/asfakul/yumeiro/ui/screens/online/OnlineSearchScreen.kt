/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.ui.components.Suggestion.Kind as SuggestionKind
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineItem
import kotlinx.coroutines.flow.collectLatest
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.asfakul.yumeiro.ui.theme.yuRound

private sealed interface ProviderResult {
    data object Searching : ProviderResult
    data class Done(val items: List<OnlineItem>) : ProviderResult
    data class Failed(val message: String) : ProviderResult
}

/**
 * Last finished search, so coming back from a title page shows the same results instantly
 * instead of clearing the list and asking every extension again.
 */
private object OnlineSearchMemory {
    var key: String? = null
    var results: Map<String, ProviderResult> = emptyMap()
}

/** Searches every enabled extension at once; results appear per source as they arrive. */
@Composable
fun OnlineSearchScreen(onBack: () -> Unit, onOpenTitle: (OnlineItem) -> Unit, onOpenExtensions: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    LaunchedEffect(Unit) { SearchView.load(context) }
    // 18+ off: adult-only extensions that are still installed (18+ was on earlier) are not searched,
    // and NSFW-typed items from mixed sources are dropped. With 18+ on (private or not) search finds them.
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    // SPEED-4: search starts with the extensions loaded so far (engine.usable), like Yumix; before it waited for
    // every installed extension to load (engine.ready).
    val providers = remember(engine.generation, engine.usable, adultOn) {
        if (!engine.usable) emptyList()
        else ExtensionManager.providers().filter { adultOn || !dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.name) }
    }

    // F-08: a search started from Upcoming arrives with its title already typed and submitted.
    val pending = remember { OnlineSearchPending.take() }
    var text by rememberSaveable { mutableStateOf(pending.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(pending.orEmpty()) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val results = remember { mutableStateMapOf<String, ProviderResult>() }
    val history = remember { mutableStateListOf<String>().apply { addAll(SearchHistory.load(context)) } }
    val focus = remember { FocusRequester() }
    // True while the user is typing: shows the typeahead list until a search is committed.
    var typing by remember { mutableStateOf(false) }
    val bookmarks by dev.asfakul.yumeiro.online.OnlineBookmarks.items.collectAsState()
    val watched by dev.asfakul.yumeiro.online.OnlineHistory.entries.collectAsState()
    LaunchedEffect(Unit) { if (submitted.isEmpty()) runCatching { focus.requestFocus() } }

    // Debounced live search; pressing Search submits immediately.
    LaunchedEffect(text) {
        val q = text.trim()
        if (q.length >= 2 && q != submitted) { delay(450); submitted = q }
        if (q.isEmpty()) submitted = ""
    }
    // Keyed on the *names* of the sources: a new engine snapshot (another extension finished
    // loading) used to clear the results mid-search and start over.
    val providerNames = remember(providers) { providers.map { it.name }.sorted() }
    // SPEED-4: the search is keyed on the words only. Extensions that finish loading during a search join it
    // (SearchJoin.plan); before, every newly loaded extension cancelled the whole search and started it again.
    val latestProviders = androidx.compose.runtime.rememberUpdatedState(providers)
    LaunchedEffect(submitted, attempt, providerNames.isEmpty()) {
        val key = "$submitted|" + providerNames.joinToString(",")
        // Retry always asks again; otherwise a finished search for the same words is reused.
        if (attempt == 0 && submitted.isNotBlank() && OnlineSearchMemory.key == key && OnlineSearchMemory.results.values.none { it is ProviderResult.Searching }) {
            if (results.isEmpty()) results.putAll(OnlineSearchMemory.results)
            return@LaunchedEffect
        }
        results.clear()
        if (submitted.isBlank() || providers.isEmpty()) return@LaunchedEffect
        SearchHistory.add(context, submitted); history.clear(); history.addAll(SearchHistory.load(context))
        val q = submitted
        val started = mutableSetOf<String>()
        kotlinx.coroutines.coroutineScope {
            androidx.compose.runtime.snapshotFlow { latestProviders.value }.collect { now ->
                val step = dev.asfakul.yumeiro.online.SearchJoin.plan(started, now.map { it.name })
                step.drop.forEach { results.remove(it); started.remove(it) }
                val batch = now.filter { it.name in step.start }
                if (batch.isEmpty()) return@collect
                started += step.start
                batch.forEach { results[it.name] = ProviderResult.Searching }
                launch {
                    OnlineRepository.search(q, batch, retry = attempt > 0).collect { u ->
                        if (u.api !in started) return@collect
                        results[u.api] = if (u.error != null) ProviderResult.Failed(u.error)
                            else ProviderResult.Done(if (adultOn) u.items else u.items.filterNot { dev.asfakul.yumeiro.online.AdultContent.isAdultType(it.type) })
                        if (results.values.none { it is ProviderResult.Searching }) {
                            OnlineSearchMemory.key = "$q|" + results.keys.sorted().joinToString(",")
                            OnlineSearchMemory.results = results.toMap()
                        }
                    }
                }
            }
        }
    }

    SearchAgainAfterPass(submitted, { latestProviders.value }, results, adultOn) { submitted }

    Column(Modifier.fillMaxSize().imePadding().testTag("online_search_screen")) {
        SubPageHeader(title = "Search online", onBack = onBack, subtitle = if (providers.isEmpty()) null else "${providers.size} source${if (providers.size == 1) "" else "s"}")
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(120); typing = true },
            placeholder = { Text("Title, series or anime", color = TextTertiary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
            trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = ""; submitted = "" }) { Icon(Icons.Default.Close, "Clear", tint = TextSecondary) } },
            singleLine = true, shape = yuRound(24),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = text.trim(); typing = false; keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SpotifyGreen, unfocusedBorderColor = DarkOutline,
                focusedContainerColor = GlassFill, unfocusedContainerColor = GlassFill,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("online_search_input"),
        )
        Spacer(Modifier.height(8.dp))

        val searching = results.values.count { it is ProviderResult.Searching }
        // SEARCH-CAP-1 (A0): how each source is searched (its own search, a title lookup, or its catalog here).
        val ways = remember(providers) { providers.associate { it.name to dev.asfakul.yumeiro.online.SearchAdapters.wayOf(it) } }
        // Ranked by match quality across every Hue (stable while results stream in), unique keys.
        val order = remember(providers) { providers.map { it.name } }
        // FE-S2 (docs/FEED-ENGINE-2.1.md FE-6): the ranking (Unicode normalize + regex per title, every result of every
        // extension) ran in composition on the main thread, again on each extension's answer. It now runs on
        // Dispatchers.Default; a newer answer replaces an older ranking (collectLatest).
        val ranked by androidx.compose.runtime.produceState(0 to emptyList<OnlineItem>(), submitted, order) {
            androidx.compose.runtime.snapshotFlow {
                results.entries.mapNotNull { (n, r) -> (r as? ProviderResult.Done)?.let { n to it.items } }.toMap()
            }.collectLatest { done ->
                value = done.size to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    dev.asfakul.yumeiro.online.SearchRanker.rank(submitted, done, order).map { it.item }
                }
            }
        }
        val all = ranked.second
        // Answers that arrived but are not ranked yet: never say "No results" in that gap.
        val rankingBehind = ranked.first != results.values.count { it is ProviderResult.Done }
        // Typeahead: recent searches, My List, watched titles, titles seen while browsing and the live results.
        // FE-S2: the typeahead folds ~400 titles with regexes; it used to do that in composition on every keystroke.
        val historyNow = history.toList()
        val suggestions by androidx.compose.runtime.produceState(emptyList<dev.asfakul.yumeiro.ui.components.Suggestion>(), text, historyNow, bookmarks, watched, all.size) {
            if (text.isBlank()) { value = emptyList(); return@produceState }
            val liveTitles = all.take(60)
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                fun S(t: String, k: SuggestionKind, d: String?) = dev.asfakul.yumeiro.ui.components.Suggestion(t, k, d)
                val c = buildList {
                    historyNow.forEach { add(S(it, SuggestionKind.RECENT, null)) }
                    bookmarks.forEach { add(S(it.name, SuggestionKind.TITLE, (if (it.listed) "My List" else "Favorites") + " · ${it.api}")) }
                    watched.forEach { add(S(it.name, SuggestionKind.TITLE, "Watched · ${it.api}")) }
                    liveTitles.forEach { add(S(it.name, SuggestionKind.TITLE, it.api)) }
                    OnlineRepository.previews.values.take(300).forEach { add(S(it.name, SuggestionKind.TITLE, it.api)) }
                }
                dev.asfakul.yumeiro.ui.components.SearchSuggest.rank(text, c, limit = 6)
            }
        }
        if (typing && text.isNotBlank()) {
            dev.asfakul.yumeiro.ui.components.SuggestionList(
                query = text, suggestions = suggestions,
                onPick = { sg -> text = sg.text; submitted = sg.text; typing = false; keyboard?.hide() },
                onFill = { sg -> text = sg.text },
                tag = "online_search_suggestions",
            )
        }
        when {
            !engine.ready -> PosterGridSkeleton(rows = 3)
            providers.isEmpty() -> StateMessage(
                Icons.Default.Extension, "No sources to search", "Install or turn on an extension first.",
                primary = "Extensions" to onOpenExtensions, testTag = "online_search_no_sources",
            )
            submitted.isBlank() -> RecentSearches(history, onClear = { SearchHistory.clear(context); history.clear() }) { h -> text = h; submitted = h }
            else -> SearchResultsBody(
                results = results, all = all, rankingBehind = rankingBehind, filter = filter, onFilter = { filter = it },
                searching = searching, ways = ways, submitted = submitted, onRetry = { attempt++ }, onOpenTitle = onOpenTitle,
            )
        }
    }
}

// B4 (Group M): a site's check was just passed; its sources that failed or found nothing for these words (the
// check page parsed as "no titles") are asked again. Before, they kept the old failure until Retry, and Retry
// inside 2 minutes got the remembered failure back.
@Composable
private fun SearchAgainAfterPass(
    submitted: String, providers: () -> List<com.lagradost.cloudstream3.MainAPI>,
    results: androidx.compose.runtime.snapshots.SnapshotStateMap<String, ProviderResult>, adultOn: Boolean, current: () -> String,
) {
    val adult by androidx.compose.runtime.rememberUpdatedState(adultOn)
    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        val q = submitted
        dev.asfakul.yumeiro.online.web.VerificationCenter.passed.collect { host ->
            val again = providers().filter { api ->
                dev.asfakul.yumeiro.online.web.SitePass.matches(runCatching { api.mainUrl }.getOrNull(), host) &&
                    results[api.name].let { it is ProviderResult.Failed || (it is ProviderResult.Done && it.items.isEmpty()) }
            }
            if (again.isEmpty()) return@collect
            android.util.Log.i("YuSearch", "check passed on $host: searching ${again.map { it.name }} again")
            again.forEach { results[it.name] = ProviderResult.Searching }
            launch {
                OnlineRepository.search(q, again, retry = true).collect { u ->
                    if (current() != q) return@collect
                    results[u.api] = if (u.error != null) ProviderResult.Failed(u.error)
                        else ProviderResult.Done(if (adult) u.items else u.items.filterNot { dev.asfakul.yumeiro.online.AdultContent.isAdultType(it.type) })
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(history: List<String>, onClear: () -> Unit, onPick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
        if (history.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear", color = TextTertiary) }
            }
        }
        items(history, key = { it }) { h -> SearchHistoryRow(h) { onPick(h) } }
    }
}

/**
 * TV-API24-JIT (Group L/M, probe run 45861134577): Android 7's JIT crashed (SIGSEGV in the register allocator,
 * UsePosition::Dup recursion on the "Jit thread pool") while compiling OnlineSearchScreen as one very large method.
 * The results part is its own composable now, so no single method is that large.
 */
@Composable
private fun SearchResultsBody(
    results: Map<String, ProviderResult>, all: List<OnlineItem>, rankingBehind: Boolean, filter: String?, onFilter: (String?) -> Unit,
    searching: Int, ways: Map<String, dev.asfakul.yumeiro.online.SearchAdapters.Way>, submitted: String, onRetry: () -> Unit,
    onOpenTitle: (OnlineItem) -> Unit,
) {
    val names = results.keys.sorted()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OnlineChip("All (${all.size})", filter == null, { onFilter(null) }) }
        items(names, key = { it }) { n ->
            val label = when (val r = results[n]) {
                ProviderResult.Searching -> "$n …"
                is ProviderResult.Done -> "$n (${r.items.size})"
                is ProviderResult.Failed -> "$n (!)"
                null -> n
            }
            OnlineChip(label, filter == n, { onFilter(if (filter == n) null else n) }, Modifier.testTag("online_search_source_$n"))
        }
    }
    val shown = if (filter == null) all else all.filter { it.api == filter }
    val failed = (results[filter] as? ProviderResult.Failed)?.message
    val way = filter?.let { ways[it] } ?: dev.asfakul.yumeiro.online.SearchAdapters.Way.NATIVE
    val summary = if (filter != null) null else dev.asfakul.yumeiro.online.SearchAdapters.summary(
        searching = searching,
        withResults = results.values.count { it is ProviderResult.Done && it.items.isNotEmpty() },
        empty = results.values.count { it is ProviderResult.Done && it.items.isEmpty() },
        failed = results.values.count { it is ProviderResult.Failed },
    )
    val note = filter?.let { dev.asfakul.yumeiro.online.SearchAdapters.note(way, it) }
    if (names.size > 1 && summary != null) Text(summary, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 6.dp).testTag("online_search_summary"))
    if (note != null) Text(note, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("online_search_way"))
    when {
        shown.isEmpty() && (rankingBehind || searching > 0 && (filter == null || results[filter] is ProviderResult.Searching)) ->
            Column(Modifier.fillMaxWidth()) {
                if (searching > 0) Text("Searching $searching extension${if (searching == 1) "" else "s"}…", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                PosterGridSkeleton(rows = 3)
            }
        failed != null -> StateMessage(Icons.Default.SearchOff, "$filter couldn't search", failed, tint = WarnColor,
            primary = "Retry" to onRetry, testTag = "online_search_failed")
        shown.isEmpty() && filter != null -> StateMessage(Icons.Default.SearchOff, "No results for “$submitted”",
            dev.asfakul.yumeiro.online.SearchAdapters.emptyReason(way, filter!!, submitted), testTag = "online_search_empty")
        shown.isEmpty() -> StateMessage(Icons.Default.SearchOff, "No results for “$submitted”", "Try another spelling or a shorter title.", testTag = "online_search_empty")
        filter == null && names.size > 1 && SearchView.grouped -> {
            SearchViewBar()
            GroupedResults(results, all, onFilter, onRetry, onOpenTitle)
        }
        else -> {
            if (filter == null && names.size > 1) SearchViewBar()
            if (searching > 0) Text("Still searching $searching source${if (searching == 1) "" else "s"}…", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns()),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().testTag("online_search_results"),
            ) {
                items(shown, key = { it.key }) { item ->
                    PosterCard(item, onClick = { OnlineRepository.previews[item.key] = item; onOpenTitle(item) }, modifier = Modifier.fillMaxWidth(), showSource = filter == null && names.size > 1)
                }
            }
        }
    }
}

/** F13: how results are shown (grouped by extension or one mixed grid) and how groups are sorted; kept on the device. */
internal object SearchView {
    private const val PREFS = "online"
    var grouped by mutableStateOf(true)
    var sort by mutableStateOf(dev.asfakul.yumeiro.online.SearchGroups.Sort.BEST)
    private var loaded = false
    /** Reads the saved choice off the main thread (first prefs read is disk I/O). */
    suspend fun load(c: Context) {
        if (loaded) return
        loaded = true
        val (g, s) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.getBoolean("search_grouped", true) to p.getString("search_sort", null)
        }
        grouped = g
        sort = runCatching { dev.asfakul.yumeiro.online.SearchGroups.Sort.valueOf(s ?: "BEST") }.getOrDefault(dev.asfakul.yumeiro.online.SearchGroups.Sort.BEST)
    }
    fun save(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("search_grouped", grouped).putString("search_sort", sort.name).apply()
    /** Tests only. */
    internal fun resetForTest() { loaded = false; grouped = true; sort = dev.asfakul.yumeiro.online.SearchGroups.Sort.BEST }
}

@Composable
private fun SearchViewBar() {
    val c = androidx.compose.ui.platform.LocalContext.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("online_search_view")) {
        item { OnlineChip("Grouped", SearchView.grouped, { SearchView.grouped = true; SearchView.save(c) }, Modifier.testTag("online_search_mode_grouped")) }
        item { OnlineChip("Mixed", !SearchView.grouped, { SearchView.grouped = false; SearchView.save(c) }, Modifier.testTag("online_search_mode_mixed")) }
        if (SearchView.grouped) items(dev.asfakul.yumeiro.online.SearchGroups.Sort.entries, key = { it.name }) { s ->
            OnlineChip(s.label, SearchView.sort == s, { SearchView.sort = s; SearchView.save(c) }, Modifier.testTag("online_search_sort_${s.name.lowercase()}"))
        }
    }
}

/** F13: one row per extension (name, count, See all), the ones with nothing folded into summary lines. */
@Composable
private fun GroupedResults(
    results: Map<String, ProviderResult>, all: List<OnlineItem>, onFilter: (String?) -> Unit, onRetry: () -> Unit, onOpenTitle: (OnlineItem) -> Unit,
) {
    val states = results.mapValues { (_, r) ->
        when (r) {
            ProviderResult.Searching -> dev.asfakul.yumeiro.online.SearchGroups.State.SEARCHING
            is ProviderResult.Done -> dev.asfakul.yumeiro.online.SearchGroups.State.DONE
            is ProviderResult.Failed -> dev.asfakul.yumeiro.online.SearchGroups.State.FAILED
        }
    }
    val sort = SearchView.sort
    val plan = remember(all, states, sort) { dev.asfakul.yumeiro.online.SearchGroups.plan(all, states, sort) }
    // A new sort starts from its first row: the list would otherwise keep the old first row in place
    // and hide the new one above it (seen on TV, where only a couple of rows fit).
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var shownSort by remember { mutableStateOf(sort) }
    LaunchedEffect(sort) { if (shownSort != sort) { shownSort = sort; listState.scrollToItem(0) } }
    LazyColumn(Modifier.fillMaxSize().testTag("online_search_groups"), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        items(plan.groups, key = { "g_" + it.name }, contentType = { "group" }) { g ->
            Column(Modifier.testTag("online_search_group_${g.name}")) {
                SectionHeader(g.name, subtitle = "${g.items.size} result${if (g.items.size == 1) "" else "s"}") {
                    TextButton(onClick = { onFilter(g.name) }, modifier = Modifier.testTag("online_search_group_all_${g.name}")) { Text("See all", color = SpotifyGreen, fontSize = 13.sp) }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(g.items.take(GROUP_ROW_MAX), key = { it.key }) { item ->
                        PosterCard(item, onClick = { OnlineRepository.previews[item.key] = item; onOpenTitle(item) }, width = 112.dp)
                    }
                }
            }
        }
        if (plan.searching.isNotEmpty()) item("g_searching") {
            Text("Still searching: " + plan.searching.joinToString(", "), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_search_group_searching"))
        }
        if (plan.empty.isNotEmpty()) item("g_empty") {
            Text("Nothing found in: " + plan.empty.joinToString(", "), color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("online_search_group_none"))
        }
        if (plan.failed.isNotEmpty()) item("g_failed") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("online_search_group_failed"), verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't search: " + plan.failed.joinToString(", "), color = WarnColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("online_search_group_retry")) { Text("Retry", color = SpotifyGreen) }
            }
        }
    }
}

private const val GROUP_ROW_MAX = 30

/** One recent search. SEARCH-ML-1: shown whole (every line), at least one row high. */
@Composable
internal fun SearchHistoryRow(h: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 44.dp)
            .then(Modifier.testTag("online_search_history")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(h, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().testTag("online_search_history_text"))
        }
    }
}

/** Last searches, kept on the device. */
internal object SearchHistory {
    private const val KEY = "online_search_history"
    // B19 (GH-47): each profile keeps its own searches ("online" for the default profile, "online@p<id>" otherwise).
    private fun prefs(c: Context) = c.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(c, "online"), Context.MODE_PRIVATE)
    // SEARCH-ML-1: kept as a JSON array, so a search with line breaks stays one search (see SearchHistoryCodec).
    fun load(c: Context): List<String> = dev.asfakul.yumeiro.ui.components.SearchHistoryCodec.decode(prefs(c).getString(KEY, ""))
    fun add(c: Context, q: String) {
        val t = dev.asfakul.yumeiro.ui.components.SearchHistoryCodec.clean(q).ifEmpty { return }
        val list = dev.asfakul.yumeiro.ui.components.SearchHistoryCodec.push(load(c), t, 12)
        prefs(c).edit().putString(KEY, dev.asfakul.yumeiro.ui.components.SearchHistoryCodec.encode(list)).apply()
    }
    fun clear(c: Context) = prefs(c).edit().remove(KEY).apply()
}
