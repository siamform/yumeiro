/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.asfakul.yumeiro.online.ExtensionFormats
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SkeletonRow
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private enum class MarketTab(val label: String) { DISCOVER("Discover"), REPOS("Repositories"), INSTALLED("Installed") }
private enum class MarketSort(val label: String) { TOP("Top"), UPDATED("Recently updated"), NAME("A–Z") }

/**
 * Videos → Online → Market. Every extension the market workflow found (curated, CloudStream's
 * community list and GitHub search), ranked by popularity and freshness, plus the user's own
 * repositories. Same extension in many repositories shows once (best copy).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtensionMarketScreen(onBack: () -> Unit, onOpenExtensions: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }

    var data by remember { mutableStateOf<ExtensionSources.Available?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(MarketTab.DISCOVER) }
    var category by rememberSaveable { mutableStateOf("All") }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(MarketSort.TOP) }
    var repoFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var originFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var repoQuery by rememberSaveable { mutableStateOf("") }
    var showRepos by remember { mutableStateOf(false) }
    var showRepoDialog by remember { mutableStateOf(false) }
    // UX-MARKET-1: the extension whose details sheet is open (by key, so a list refresh keeps it).
    var infoKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showLanguages by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    suspend fun load(force: Boolean) {
        refreshing = true
        runCatching { ExtensionSources.all(context, force) }
            .onSuccess { data = it; error = if (it.items.isEmpty()) it.warning else null }
            .onFailure { if (data == null) error = OnlineErrors.friendly(it) else AppMessages.error(context, OnlineErrors.friendly(it)) }
        refreshing = false
    }
    LaunchedEffect(engine.repos.size) { load(false) }

    // Round 13: the market hung on phones with thousands of listings, because filtering and
    // sorting ran on the main thread on every keystroke and every engine update. All of it now
    // runs on a background thread; the screen keeps showing the last result until the new one is ready.
    val installedKeys = remember(engine.installed) { engine.installed.associateBy { it.key } }
    val adult by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val derived by androidx.compose.runtime.produceState(MarketDerived(), data, adult, engine.installed) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val items = data?.items.orEmpty()
            val updates = ExtensionManager.updatesFrom(items).associate { it.first.key to it.second }
            // 18+ extensions only when the user turned them on (Settings → Online videos).
            val safe = items.filter { adult || !it.isNsfw }
            val categories = listOf("All") + PICK_CATEGORIES.filter { c -> c == "Short drama" || safe.any { c in it.categories } } + listOf("Other", "18+").filter { c -> safe.any { c in it.categories } }
            val languages = safe.mapNotNull { it.language?.lowercase()?.takeIf { l -> l.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            MarketDerived(safe, updates, categories, languages, dev.asfakul.yumeiro.online.MarketFacets.origins(safe))
        }
    }
    val safe = derived.safe
    val updates = derived.updates
    val categories = derived.categories
    val languages = derived.languages
    val origins = derived.origins
    // The repositories offered follow the picked source (MKT-FILTER-1).
    val repoFacets = remember(safe, originFilter) { dev.asfakul.yumeiro.online.MarketFacets.repositories(safe, originFilter) }
    // Typing waits a moment before filtering so each key press stays instant.
    val visible by androidx.compose.runtime.produceState(emptyList<RemoteExtension>(), safe, query, tab, category, language, sort, repoFilter, originFilter, installedKeys, updates) {
        if (query.isNotBlank()) kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            marketVisible(safe, query, tab, category, language, sort, repoFilter, installedKeys.keys, updates.keys, originFilter)
        }
    }
    val activeFilters = (if (category != "All") 1 else 0) + (if (language != null) 1 else 0) + (if (sort != MarketSort.TOP) 1 else 0) + (if (repoFilter != null) 1 else 0) + (if (originFilter != null) 1 else 0)
    val repos = remember(data) { data?.repos.orEmpty().sortedWith(compareByDescending<ExtensionSources.RepoListing> { it.origin == "user" }.thenByDescending { it.verified }.thenByDescending { it.stars ?: 0 }.thenByDescending { it.count }) }

    fun install(remote: RemoteExtension) {
        if (remote.apiVersion > OnlineRuntime.API_VERSION) {
            AppMessages.error(context, "${remote.name} needs a newer app version"); return
        }
        scope.launch {
            runCatching { ExtensionManager.install(context, remote) }
                .onSuccess {
                    OnlineRepository.clearCaches()
                    val err = ExtensionManager.state.value.info(it.key).error
                    if (err != null) AppMessages.error(context, "${it.name} installed but couldn't start: $err")
                    else AppMessages.show(context, "${it.name} installed", AppMessages.Kind.SUCCESS, "Manage") { onOpenExtensions() }
                }
                .onFailure { if (it !is dev.asfakul.yumeiro.online.ExtensionReview.Declined) AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { install(remote) } }
        }
    }
    fun updateAll() {
        val list = safe.filter { it.key in updates }
        scope.launch {
            // R16 Group 2: failures were counted silently ("Updated 3 of 5", always a success toast).
            val o = dev.asfakul.yumeiro.online.ExtensionBulk.runEach(list, { it.name }) { r -> ExtensionManager.install(context, r, dev.asfakul.yumeiro.online.ExtensionReview.Mode.IF_RISKY) }
            OnlineRepository.clearCaches()
            val msg = dev.asfakul.yumeiro.online.ExtensionBulk.summary("Updated", o)
            if (o.failed.isEmpty()) AppMessages.success(context, msg) else AppMessages.error(context, msg)
        }
    }

    Column(Modifier.fillMaxSize().testTag("market_screen")) {
        SubPageHeader(
            title = "Extension market", onBack = onBack,
            subtitle = data?.let { d -> "${safe.count { it.primary }} extensions · ${d.repos.size} repositories" } ?: "Loading…",
        ) {
            if (tab != MarketTab.REPOS) {
                dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), { if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true }, "market_search_toggle", "Search the market")
            }
            if (tab == MarketTab.DISCOVER) dev.asfakul.yumeiro.ui.components.FilterIconButton(activeFilters, { showFilters = true }, "market_filter")
            IconButton(onClick = { showRepoDialog = true }, modifier = Modifier.testTag("market_add_repo")) { Icon(Icons.Default.Add, "Add repository", tint = TextPrimary) }
        }
        dev.asfakul.yumeiro.ui.components.HeaderSearchField(
            open = tab != MarketTab.REPOS && (searchOpen || query.isNotEmpty()), query = query, onQuery = { query = it },
            placeholder = "Name, site, author or repository", tag = "market_search",
        )
        // B2: the tab switch and the applied-filter chips slide away on scroll down, back on scroll up.
        dev.asfakul.yumeiro.ui.components.HideOnScrollColumn(showKey = tab, header = {
        // Tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(GlassFill).padding(4.dp)) {
            MarketTab.entries.forEach { t ->
                val sel = t == tab
                val label = when (t) {
                    MarketTab.INSTALLED -> "Installed" + if (updates.isNotEmpty()) " · ${updates.size}" else ""
                    MarketTab.REPOS -> "Repos"
                    else -> t.label
                }
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (sel) SpotifyGreen else Color.Transparent)
                        .clickable { tab = t }.testTag("market_tab_${t.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (sel) OnAccent else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
            }
        }
        // Only what is applied shows on the page (one slim row); everything else lives in the sheet.
        if (tab == MarketTab.DISCOVER && activeFilters > 0) {
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().testTag("market_active_filters"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (category != "All") item("c") { OnlineChip("$category  ✕", true, { category = "All" }, Modifier.testTag("market_active_category")) }
                language?.let { l -> item("l") { OnlineChip("${languageName(l)}  ✕", true, { language = null }, Modifier.testTag("market_active_language")) } }
                if (sort != MarketSort.TOP) item("s") { OnlineChip("${sort.label}  ✕", true, { sort = MarketSort.TOP }) }
                originFilter?.let { o -> item("o") { OnlineChip("${dev.asfakul.yumeiro.online.MarketFacets.originLabel(o)}  ✕", true, { originFilter = null }, Modifier.testTag("market_active_source")) } }
                repoFilter?.let { rf ->
                    val name = repos.firstOrNull { it.url.equals(rf, true) }?.name ?: repoFacets.firstOrNull { it.key.equals(rf, true) }?.label ?: ExtensionFormats.repoNameFromUrl(rf)
                    item("r") { OnlineChip("$name  ✕", true, { repoFilter = null }, Modifier.testTag("market_repo_filter")) }
                }
            }
        }
        data?.warning?.takeIf { data?.items?.isNotEmpty() == true }?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, null, tint = WarnColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }) {
        when {
            data == null && error == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(6) { SkeletonRow(trailing = true) } }
            data == null || (data?.items?.isEmpty() == true && error != null && tab != MarketTab.REPOS) -> StateMessage(
                Icons.Default.CloudOff, "Market unavailable", error ?: "Check your connection and try again.", tint = ErrorColor,
                primary = "Retry" to { scope.launch { error = null; data = null; load(true) } },
                secondary = "Add a repository" to { showRepoDialog = true }, testTag = "market_error",
                modifier = Modifier.padding(top = 32.dp),
            )
            tab == MarketTab.REPOS -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { load(true) } }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("market_repos"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("market_repos"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item("repo_note") {
                        Text("Found automatically from CloudStream's community list and GitHub. Curated repositories are checked and mirrored by the market.", color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    items(repos, key = { "r_" + it.url }) { r ->
                        RepoCard(r) { repoFilter = r.url; category = "All"; language = null; tab = MarketTab.DISCOVER }
                    }
                }
            }
            else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { load(true) } }, modifier = Modifier.fillMaxSize()) {
                if (visible.isEmpty() && !(tab == MarketTab.DISCOVER && category == "Short drama")) StateMessage(
                    Icons.Default.SearchOff, "Nothing here",
                    when { query.isNotBlank() -> "No extension matches \"$query\"."; tab == MarketTab.INSTALLED -> "You haven't installed any extension yet."; else -> "No extensions in this list yet." },
                    tint = TextSecondary, testTag = "market_empty", modifier = Modifier.padding(top = 32.dp),
                )
                else LazyColumn(Modifier.fillMaxSize().testTag("market_list"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("market_list"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tab == MarketTab.INSTALLED && updates.isNotEmpty()) item("update_all") {
                        Button(onClick = { updateAll() }, colors = ButtonDefaults.buttonColors(containerColor = WarnColor, contentColor = OnAccent), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("market_update_all")) {
                            Text("Update all (${updates.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                    // OWN-13: with the hub on, one "Yumeiro" card (Discover: All, drama, Movies, TV; Installed) instead of two.
                    val hubCard = officialHub() && repoFilter == null &&
                        ((tab == MarketTab.DISCOVER && category in setOf("All", "Short drama", "Asian drama", "Movies", "TV", "Anime")) || (tab == MarketTab.INSTALLED && query.isBlank())) &&
                        (query.isBlank() || "yumeiro all in one official drama short dramabox reelshort shortmax classics movies films documentaries cartoons public domain archive film noir anime gundam muse ani-one viz retrocrush tezuka".contains(query.trim(), true))
                    if (hubCard) item("builtin_yumeiro") { YumeiroHubCard() }
                    if (!officialHub() && tab == MarketTab.DISCOVER && repoFilter == null && (category == "All" || category == "Short drama" || category == "Asian drama") &&
                        (query.isBlank() || "yumeiro drama short dramabox reelshort shortmax".contains(query.trim(), true))
                    ) item("builtin_drama") { DramaBuiltinCard() }
                    // OWN-9: the official Yumeiro Classics source, in Discover (All / Movies) and in Installed (with Drama).
                    if (!officialHub() && dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.OFFICIAL_CLASSICS) && repoFilter == null &&
                        ((tab == MarketTab.DISCOVER && (category == "All" || category == "Movies")) || tab == MarketTab.INSTALLED) &&
                        (query.isBlank() || "yumeiro classics movies public domain archive film noir cartoons".contains(query.trim(), true))
                    ) item("builtin_classics") { ClassicsBuiltinCard() }
                    if (!officialHub() && tab == MarketTab.INSTALLED && repoFilter == null && query.isBlank()) item("builtin_drama_installed") { DramaBuiltinCard() }
                    items(visible, key = { it.key }, contentType = { "market_card" }) { ext ->
                        MarketCard(
                            ext = ext,
                            installedVersion = installedKeys[ext.key]?.version,
                            hasUpdate = ext.key in updates,
                            busy = ext.key in engine.busy,
                            showRank = tab == MarketTab.DISCOVER && sort == MarketSort.TOP && category == "All" && language == null && repoFilter == null && query.isBlank(),
                            onInstall = { install(ext) },
                            onManage = onOpenExtensions,
                            onInfo = { infoKey = ext.key },
                        )
                    }
                    if (tab == MarketTab.DISCOVER) item("legal") {
                        Text(
                            "Extensions are third-party code found automatically. Yumeiro doesn't host or check their content and never bypasses DRM or logins. Install only what you trust and are allowed to watch.",
                            color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        }
    }

    if (showFilters) dev.asfakul.yumeiro.ui.components.FilterSheet(
        onDismiss = { showFilters = false },
        onReset = if (activeFilters > 0) ({ category = "All"; language = null; sort = MarketSort.TOP; repoFilter = null; originFilter = null; repoQuery = "" }) else null,
        resultCount = visible.size, tag = "market_filter_sheet",
    ) {
        dev.asfakul.yumeiro.ui.components.FilterGroup("Sort by") {
            MarketSort.entries.forEach { s2 -> OnlineChip(s2.label, s2 == sort, { sort = s2 }, Modifier.testTag("market_sort_${s2.name.lowercase()}")) }
        }
        dev.asfakul.yumeiro.ui.components.FilterGroup("Category") {
            categories.forEach { c -> OnlineChip(c, c == category, { category = c }, Modifier.testTag("market_cat_$c")) }
        }
        if (languages.isNotEmpty()) dev.asfakul.yumeiro.ui.components.FilterGroup("Language") {
            OnlineChip("Any", language == null, { language = null }, Modifier.testTag("market_language"))
            languages.take(if (showLanguages) 60 else 11).forEach { l -> OnlineChip(languageName(l), l == language, { language = l }, Modifier.testTag("market_lang_$l")) }
            if (!showLanguages && languages.size > 11) OnlineChip("+${languages.size - 11} more", false, { showLanguages = true }, Modifier.testTag("market_lang_more"))
        }
        // MKT-FILTER-1 (owner list 9): Source and Repository come from the loaded listing, with counts.
        if (origins.isNotEmpty()) dev.asfakul.yumeiro.ui.components.FilterGroup("Source") {
            OnlineChip("Any", originFilter == null, { originFilter = null }, Modifier.testTag("market_source_any"))
            origins.forEach { o ->
                OnlineChip("${o.label} · ${o.count}", o.key == originFilter, {
                    originFilter = if (o.key == originFilter) null else o.key
                    // A repository outside the new source would leave an empty list.
                    if (repoFilter != null && originFilter != null && safe.none { it.origin == originFilter && it.repoUrl.equals(repoFilter, true) }) repoFilter = null
                }, Modifier.testTag("market_source_${o.key}"))
            }
        }
        if (repoFacets.size > 1 || repoFilter != null) dev.asfakul.yumeiro.ui.components.FilterGroup("Repository") {
            val many = repoFacets.size > REPO_CHIPS
            val shown = if (repoQuery.isNotBlank()) dev.asfakul.yumeiro.online.MarketFacets.search(repoFacets, repoQuery) else if (showRepos || !many) repoFacets else repoFacets.take(REPO_CHIPS)
            OnlineChip("Any", repoFilter == null, { repoFilter = null }, Modifier.testTag("market_repo_any"))
            // The picked repository stays visible even when it is not among the first chips.
            val picked = repoFilter?.let { rf -> repoFacets.firstOrNull { it.key.equals(rf, true) } }
            (listOfNotNull(picked) + shown.filter { it != picked }).forEach { f ->
                OnlineChip("${f.label} · ${f.count}", f.key.equals(repoFilter, true), {
                    repoFilter = if (f.key.equals(repoFilter, true)) null else safe.firstOrNull { it.repoUrl.equals(f.key, true) }?.repoUrl ?: f.key
                }, Modifier.testTag("market_repo_${f.label}"))
            }
            if (many && !showRepos && repoQuery.isBlank()) OnlineChip("+${repoFacets.size - REPO_CHIPS} more", false, { showRepos = true }, Modifier.testTag("market_repo_more"))
        }
        if (repoFacets.size > REPO_CHIPS) dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
            repoQuery, { repoQuery = it }, "Search ${repoFacets.size} repositories", "market_repo_search",
            Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    infoKey?.let { k -> visible.firstOrNull { it.key == k } }?.let { ext ->
        MarketInfoSheet(
            ext = ext, installedVersion = installedKeys[ext.key]?.version, hasUpdate = ext.key in updates,
            onInstall = { install(ext) }, onManage = onOpenExtensions, onDismiss = { infoKey = null },
        )
    }
    if (showRepoDialog) InputDialog(
        title = "Add repository", label = "Repository link", hint = "https://…/repo.json or a GitHub repo",
        confirm = "Add", onDismiss = { showRepoDialog = false },
    ) { text, done ->
        scope.launch {
            // OWN-4: a Vega, SkyStream, Stremio, Aniyomi/Mihon or single-extension link works here too.
            dev.asfakul.yumeiro.sources.UniversalAdd.addFromRepoDialog(context, text)
                .onSuccess { (repo, other) ->
                    done(null); showRepoDialog = false
                    if (repo != null) {
                        AppMessages.success(context, "Added ${repo.name}")
                        load(true); repoFilter = repo.url; tab = MarketTab.DISCOVER
                    } else { AppMessages.success(context, other?.message ?: "Added"); load(true) }
                }
                .onFailure { if (it is dev.asfakul.yumeiro.online.ExtensionReview.Declined) { done(null); showRepoDialog = false } else done(OnlineErrors.friendly(it)) }
        }
    }
}

@Composable
private fun RepoCard(r: ExtensionSources.RepoListing, onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen).testTag("market_repo")) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    OriginPill(r.origin, r.verified)
                }
                Text(
                    listOfNotNull("${r.count} extension${if (r.count == 1) "" else "s"}", r.updated.takeIf { it > 0 }?.let { "updated " + relativeAge(it) }, r.github).joinToString(" · "),
                    color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                r.error?.let { Text(it, color = ErrorColor, fontSize = 11.sp) }
            }
            r.stars?.takeIf { it > 0 }?.let {
                Icon(Icons.Default.Star, null, tint = dev.asfakul.yumeiro.ui.theme.StarTone, modifier = Modifier.size(14.dp))
                Text(" $it", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OriginPill(origin: String, verified: Boolean) {
    when {
        origin == "curated" || (verified && origin != "user") -> Pill("Curated", SpotifyGreen)
        origin == "community" -> Pill("Community", TextSecondary)
        origin == "discovered" -> Pill("Discovered", WarnColor)
        else -> Pill("Added by you", TextSecondary)
    }
}

@Composable
private fun MarketCard(
    ext: RemoteExtension, installedVersion: Int?, hasUpdate: Boolean, busy: Boolean, showRank: Boolean,
    onInstall: () -> Unit, onManage: () -> Unit, onInfo: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rich = remember { dev.asfakul.yumeiro.FeatureFlags.on(ctx, dev.asfakul.yumeiro.FeatureFlags.Flag.EXTENSION_LABELS) }
    Surface(
        onClick = onInfo, shape = RoundedCornerShape(20.dp), color = GlassFill,
        modifier = Modifier.fillMaxWidth().testTag("market_item_${ext.internalName}"),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showRank && ext.rank in 1..999) {
                    Text("${ext.rank}", color = if (ext.rank <= 3) SpotifyGreen else TextTertiary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.width(30.dp))
                }
                ExtensionIcon(ext.iconUrl?.replace("%size%", "128"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // F-21: shared label. Source count is only known after install, so the market leaves it out.
                    val label = remember(rich, ext.name, ext.language, ext.repoName, ext.version, ext.fileSize) {
                        dev.asfakul.yumeiro.online.ExtensionLabels.build(
                            ext.name, ext.language.takeIf { rich }, null, "CloudStream".takeIf { rich }, ext.repoName,
                            listOfNotNull("v${ext.version}", ext.fileSize?.takeIf { it > 0 }?.let { formatSize(it) }),
                        )
                    }
                    ExtensionLabelText(label, titleColor = TextPrimary, lineColor = TextTertiary) {
                        if (ext.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Verified, "Curated", tint = SpotifyGreen, modifier = Modifier.size(14.dp)) }
                    }
                    // FlowRow + single-line chunks: when the trailing button grows (e.g. "Installed"),
                    // whole chunks wrap to the next line instead of breaking text letter by letter.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("market_stats_${ext.internalName}")) {
                        ext.stars?.takeIf { it > 0 }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = dev.asfakul.yumeiro.ui.theme.StarTone, modifier = Modifier.size(12.dp))
                                Text(" $it", color = TextSecondary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        if (ext.updated > 0) Text("updated ${relativeAge(ext.updated)}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                        if (ext.copies > 1) Text("in ${ext.copies} repos", color = TextTertiary, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    busy -> CircularProgressIndicator(Modifier.size(24.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                    hasUpdate -> Button(onClick = onInstall, colors = ButtonDefaults.buttonColors(containerColor = WarnColor, contentColor = OnAccent), shape = RoundedCornerShape(20.dp), modifier = Modifier.testTag("market_update_${ext.internalName}")) { Text("Update") }
                    installedVersion != null -> OutlinedButton(onClick = onManage, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Check, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Installed", color = TextPrimary, maxLines = 1, softWrap = false)
                    }
                    else -> Button(
                        onClick = onInstall, enabled = !ext.isDown && ext.apiVersion <= OnlineRuntime.API_VERSION,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("market_install_${ext.internalName}"),
                    ) { Text(if (ext.isDown) "Down" else "Install") }
                }
            }
            ext.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(ext.statusLabel, when (ext.status) { 0 -> ErrorColor; 2, 3 -> WarnColor; else -> SpotifyGreen })
                // F-21: the language is the chip next to the name now; keep a pill only when it isn't a code.
                ext.language?.takeIf { it.isNotBlank() && (!rich || dev.asfakul.yumeiro.online.ExtensionLabels.langCode(it) == null) }?.let { Pill(it.uppercase(), TextSecondary) }
                ext.categories.take(3).forEach { Pill(it, TextSecondary) }
                if (ext.origin != "curated") OriginPill(ext.origin, ext.verified)
                if (ext.apiVersion > OnlineRuntime.API_VERSION) Pill("Needs app update", ErrorColor)
                if (ext.compatNote != null) Pill("May not work", WarnColor)
            }
            ext.compatNote?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = WarnColor, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            }
        }
    }
}

private fun languageName(code: String): String =
    runCatching { java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(code, true) } }.getOrNull()
        ?.let { "$it (${code.uppercase()})" } ?: code.uppercase()

internal fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}

/** What the market derives from the listing (computed off the main thread). */
private data class MarketDerived(
    val safe: List<RemoteExtension> = emptyList(),
    val updates: Map<String, RemoteExtension> = emptyMap(),
    val categories: List<String> = listOf("All"),
    val languages: List<String> = emptyList(),
    val origins: List<dev.asfakul.yumeiro.online.MarketFacets.Facet> = emptyList(),
)

/** Repository chips shown before "+N more". */
private const val REPO_CHIPS = 8

/** The market cards for the current tab, filters, search and sort. Pure, runs on a background thread. */
private fun marketVisible(
    safe: List<RemoteExtension>, query: String, tab: MarketTab, category: String, language: String?, sort: MarketSort,
    repoFilter: String?, installedKeys: Set<String>, updates: Set<String>, originFilter: String? = null,
): List<RemoteExtension> {
    val q = query.trim()
    val base = safe.asSequence()
    return (if (tab == MarketTab.INSTALLED) base.filter { it.key in installedKeys }
        // One card per extension unless browsing a single repository or searching; a picked source shows its own copy.
        else dev.asfakul.yumeiro.online.MarketFacets.bySource(base, originFilter) { it.primary || repoFilter != null || q.isNotEmpty() || it.key in installedKeys })
        .filter { repoFilter == null || it.repoUrl.equals(repoFilter, true) }
        .filter { category == "All" || category in it.categories }
        .filter { language == null || it.language.equals(language, true) }
        .filter { q.isEmpty() || it.name.contains(q, true) || it.internalName.contains(q, true) || it.description.orEmpty().contains(q, true) || it.repoName.contains(q, true) || it.authors.any { a -> a.contains(q, true) } }
        .let { seq ->
            when (sort) {
                MarketSort.TOP -> seq.sortedWith(compareByDescending<RemoteExtension> { it.key in updates }.thenBy { it.isDown }.thenByDescending { it.score }.thenByDescending { it.stars ?: 0 }.thenBy { it.name.lowercase() })
                MarketSort.UPDATED -> seq.sortedByDescending { it.updated }
                MarketSort.NAME -> seq.sortedBy { it.name.lowercase() }
            }
        }
        .toList()
}
