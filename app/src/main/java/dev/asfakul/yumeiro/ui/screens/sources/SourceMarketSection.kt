/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.sources.SourceMarket
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private const val PAGE = 8

/** Shared "Discover" block: loads the market, offers search and sort, and keeps the market's own errors quiet. */
@Composable
private fun MarketFrame(
    title: String,
    hint: String,
    tag: String,
    startExpanded: Boolean,
    content: @Composable (catalog: SourceMarket.Catalog, query: String, expanded: Boolean) -> Unit,
    controls: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by SourceMarket.state.collectAsState()
    var expanded by rememberSaveable(tag) { mutableStateOf(startExpanded) }
    var query by rememberSaveable(tag + "_q") { mutableStateOf("") }
    LaunchedEffect(Unit) { SourceMarket.load(context.applicationContext) }

    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title, hint, (if (expanded) "Hide" else "Show") to { expanded = !expanded })
        if (!expanded) return@Column
        when (val s = state) {
            is SourceMarket.State.Ready -> {
                OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search by name, language or source") }, modifier = Modifier.fillMaxWidth().testTag(tag + "_search"))
                controls()
                content(s.catalog, query, expanded)
                Text("Listed automatically from GitHub and public catalogs. Yumeiro doesn't check what a repository contains; add the ones you trust.",
                    color = TextTertiary, fontSize = 11.sp)
            }
            is SourceMarket.State.Error -> {
                Text(s.message, color = TextTertiary, fontSize = 12.sp)
                TextButton(onClick = { scope.launch { SourceMarket.load(context.applicationContext, force = true) } }) { Text("Retry", color = SpotifyGreen) }
            }
            else -> Text("Loading the market…", color = TextTertiary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepoMarket(
    kind: SourceMarket.Kind,
    label: String,
    isAdded: (String) -> Boolean,
    startExpanded: Boolean,
    onAdd: (String) -> Unit,
) {
    val adult by OnlineSettings.adult.collectAsState()
    var sort by rememberSaveable(kind.key + "_sort") { mutableStateOf(SourceMarket.Sort.TOP) }
    var limit by remember(kind) { mutableIntStateOf(PAGE) }
    MarketFrame(
        title = "Discover",
        hint = "$label repositories found on GitHub, ranked by stars, updates and size",
        tag = "market_${kind.key}",
        startExpanded = startExpanded,
        controls = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceMarket.Sort.entries.forEach { s ->
                    FilterChip(selected = s == sort, onClick = { sort = s; limit = PAGE }, label = { Text(s.label) },
                        modifier = Modifier.testTag("market_sort_${s.name.lowercase()}"))
                }
            }
        },
        content = { catalog, query, _ ->
            val list = SourceMarket.filterRepos(catalog.repos[kind].orEmpty(), query, sort, adult)
            if (list.isEmpty()) Text("Nothing matches.", color = TextTertiary, fontSize = 12.sp)
            list.take(limit).forEach { r ->
                val added = isAdded(r.url)
                val n = r.count + if (adult) r.nsfw else 0
                val meta = buildString {
                    append("★ ").append(r.stars).append(" · ").append(SourceMarket.ago(r.updated)).append(" · ")
                    append(n).append(if (n == 1) " extension" else " extensions")
                    if (r.langs.isNotEmpty()) append(" · ").append(r.langs.take(4).joinToString(", ") { it.uppercase() })
                    if (r.sample.isNotEmpty()) append("\n").append(r.sample.take(4).joinToString(", "))
                }
                // Mangayomi repos publish separate manga and anime indexes; tell them apart.
                val title = if (kind == SourceMarket.Kind.MANGAYOMI) r.name + if (r.url.endsWith("anime_index.json")) " · Anime" else " · Manga" else r.name
                HubCard(title, meta, badge = r.owner.takeIf { it.isNotBlank() }) {
                    TextButton(onClick = { onAdd(r.url) }, enabled = !added) { Text(if (added) "Added" else "Add", color = if (added) TextTertiary else SpotifyGreen) }
                }
            }
            if (list.size > limit) TextButton(onClick = { limit += PAGE * 2 }) { Text("Show more (${list.size - limit})", color = SpotifyGreen) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddonMarket(isAdded: (String) -> Boolean, startExpanded: Boolean, onAdd: (String) -> Unit) {
    val adult by OnlineSettings.adult.collectAsState()
    var sort by rememberSaveable { mutableStateOf(SourceMarket.AddonSort.FEATURED) }
    var type by rememberSaveable { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(PAGE) }
    MarketFrame(
        title = "Discover",
        hint = "Add-ons from Stremio's official and community catalogs, each one checked",
        tag = "market_stremio",
        startExpanded = startExpanded,
        controls = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceMarket.AddonSort.entries.forEach { s ->
                    FilterChip(selected = s == sort, onClick = { sort = s; limit = PAGE }, label = { Text(s.label) })
                }
                listOf("movie", "series", "anime", "tv").forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = if (type == t) "" else t; limit = PAGE },
                        label = { Text(t.replaceFirstChar { it.uppercase() }) })
                }
            }
        },
        content = { catalog, query, _ ->
            val list = SourceMarket.filterAddons(catalog.addons, query, sort, adult)
                .filter { type.isEmpty() || it.types.any { t -> t.equals(type, true) } }
            if (list.isEmpty()) Text("Nothing matches.", color = TextTertiary, fontSize = 12.sp)
            list.take(limit).forEach { a ->
                val added = isAdded(a.url)
                val meta = buildString {
                    if (a.version.isNotBlank()) append("v").append(a.version).append(" · ")
                    append(a.types.joinToString()).append(" · ").append(a.resources.joinToString(" · "))
                    if (a.description.isNotBlank()) append("\n").append(a.description)
                }
                HubCard(a.name, meta, badge = if (a.featured) "Featured" else null) {
                    TextButton(onClick = { onAdd(a.url) }, enabled = !added) { Text(if (added) "Added" else "Add", color = if (added) TextTertiary else SpotifyGreen) }
                }
            }
            if (list.size > limit) TextButton(onClick = { limit += PAGE * 2 }) { Text("Show more (${list.size - limit})", color = SpotifyGreen) }
        },
    )
}
