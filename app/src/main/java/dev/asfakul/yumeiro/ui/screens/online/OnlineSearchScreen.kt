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
    val providers = remember(engine.generation, engine.ready) { if (engine.ready) ExtensionManager.providers() else emptyList() }

    var text by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
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
    LaunchedEffect(submitted, providerNames, attempt) {
        val key = "$submitted|" + providerNames.joinToString(",")
        // Retry always asks again; otherwise a finished search for the same words is reused.
        if (attempt == 0 && submitted.isNotBlank() && OnlineSearchMemory.key == key && OnlineSearchMemory.results.values.none { it is ProviderResult.Searching }) {
            if (results.isEmpty()) results.putAll(OnlineSearchMemory.results)
            return@LaunchedEffect
        }
        results.clear()
        if (submitted.isBlank() || providers.isEmpty()) return@LaunchedEffect
        providers.forEach { results[it.name] = ProviderResult.Searching }
        SearchHistory.add(context, submitted); history.clear(); history.addAll(SearchHistory.load(context))
        OnlineRepository.search(submitted, providers).collect { u ->
            results[u.api] = if (u.error != null) ProviderResult.Failed(u.error) else ProviderResult.Done(u.items)
        }
        OnlineSearchMemory.key = key
        OnlineSearchMemory.results = results.toMap()
    }

    Column(Modifier.fillMaxSize().imePadding().testTag("online_search_screen")) {
        SubPageHeader(title = "Search online", onBack = onBack, subtitle = if (providers.isEmpty()) null else "${providers.size} source${if (providers.size == 1) "" else "s"}")
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(120); typing = true },
            placeholder = { Text("Title, series or anime", color = TextTertiary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
            trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = ""; submitted = "" }) { Icon(Icons.Default.Close, "Clear", tint = TextSecondary) } },
            singleLine = true, shape = RoundedCornerShape(24.dp),
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
        // Ranked by match quality across every Hue (stable while results stream in), unique keys.
        val done = results.entries.mapNotNull { (n, r) -> (r as? ProviderResult.Done)?.let { n to it.items } }.toMap()
        val order = remember(providers) { providers.map { it.name } }
        val all = remember(submitted, done, order) {
            dev.asfakul.yumeiro.online.SearchRanker.rank(submitted, done, order).map { it.item }
        }
        // Typeahead: recent searches, My List, watched titles, titles seen while browsing and the live results.
        val suggestions = remember(text, history.toList(), bookmarks, watched, all.size) {
            if (text.isBlank()) emptyList() else {
                        fun S(t: String, k: SuggestionKind, d: String?) = dev.asfakul.yumeiro.ui.components.Suggestion(t, k, d)
                val c = buildList {
                    history.forEach { add(S(it, SuggestionKind.RECENT, null)) }
                    bookmarks.forEach { add(S(it.name, SuggestionKind.TITLE, "My List · ${it.api}")) }
                    watched.forEach { add(S(it.name, SuggestionKind.TITLE, "Watched · ${it.api}")) }
                    all.take(60).forEach { add(S(it.name, SuggestionKind.TITLE, it.api)) }
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
            submitted.isBlank() -> LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp)) {
                if (history.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { SearchHistory.clear(context); history.clear() }) { Text("Clear", color = TextTertiary) }
                    }
                }
                items(history, key = { it }) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(44.dp)
                            .then(Modifier.testTag("online_search_history")),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { text = h; submitted = h }, modifier = Modifier.weight(1f)) {
                            Text(h, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            else -> {
                val names = results.keys.sorted()
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OnlineChip("All (${all.size})", filter == null, { filter = null }) }
                    items(names, key = { it }) { n ->
                        val label = when (val r = results[n]) {
                            ProviderResult.Searching -> "$n …"
                            is ProviderResult.Done -> "$n (${r.items.size})"
                            is ProviderResult.Failed -> "$n (!)"
                            null -> n
                        }
                        OnlineChip(label, filter == n, { filter = if (filter == n) null else n }, Modifier.testTag("online_search_source_$n"))
                    }
                }
                val shown = if (filter == null) all else all.filter { it.api == filter }
                val failed = (results[filter] as? ProviderResult.Failed)?.message
                when {
                    shown.isEmpty() && (searching > 0 && (filter == null || results[filter] is ProviderResult.Searching)) ->
                        Column(Modifier.fillMaxWidth()) {
                            Text("Searching $searching extension${if (searching == 1) "" else "s"}…", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                            PosterGridSkeleton(rows = 3)
                        }
                    failed != null -> StateMessage(Icons.Default.SearchOff, "$filter couldn't search", failed, tint = WarnColor,
                        primary = "Retry" to { attempt++ }, testTag = "online_search_failed")
                    shown.isEmpty() -> StateMessage(Icons.Default.SearchOff, "No results for “$submitted”", "Try another spelling or a shorter title.", testTag = "online_search_empty")
                    else -> {
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
        }
    }
}

/** Last searches, kept on the device. */
internal object SearchHistory {
    private const val KEY = "online_search_history"
    private fun prefs(c: Context) = c.getSharedPreferences("online", Context.MODE_PRIVATE)
    fun load(c: Context): List<String> = prefs(c).getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun add(c: Context, q: String) {
        val list = (listOf(q) + load(c).filterNot { it.equals(q, true) }).take(12)
        prefs(c).edit().putString(KEY, list.joinToString("\n")).apply()
    }
    fun clear(c: Context) = prefs(c).edit().remove(KEY).apply()
}
