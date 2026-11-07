/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
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
    var showRepoDialog by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }

    suspend fun load(force: Boolean) {
        refreshing = true
        runCatching { ExtensionSources.all(context, force) }
            .onSuccess { data = it; error = if (it.items.isEmpty()) it.warning else null }
            .onFailure { if (data == null) error = OnlineErrors.friendly(it) else AppMessages.error(context, OnlineErrors.friendly(it)) }
        refreshing = false
    }
    LaunchedEffect(engine.repos.size) { load(false) }

    val installedKeys = remember(engine.installed) { engine.installed.associateBy { it.key } }
    val updates = remember(data, engine.installed) { ExtensionManager.updatesFrom(data?.items.orEmpty()).associate { it.first.key to it.second } }
    val adult by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    // 18+ extensions only when the user turned them on (Settings → Online videos).
    val safe = remember(data, adult) { data?.items.orEmpty().filter { adult || !it.isNsfw } }
    val categories = remember(safe) { listOf("All") + PICK_CATEGORIES.filter { c -> safe.any { c in it.categories } } + listOf("Other", "18+").filter { c -> safe.any { c in it.categories } } }
    val languages = remember(safe) { safe.mapNotNull { it.language?.lowercase()?.takeIf { l -> l.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val visible = remember(safe, query, tab, category, language, sort, repoFilter, installedKeys, updates) {
        val q = query.trim()
        safe.asSequence()
            .filter {
                when (tab) {
                    MarketTab.INSTALLED -> it.key in installedKeys
                    // One card per extension unless browsing a single repository or searching.
                    else -> it.primary || repoFilter != null || q.isNotEmpty() || it.key in installedKeys
                }
            }
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
                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { install(remote) } }
        }
    }
    fun updateAll() {
        val list = safe.filter { it.key in updates }
        scope.launch {
            var ok = 0
            list.forEach { r -> runCatching { ExtensionManager.install(context, r) }.onSuccess { ok++ } }
            OnlineRepository.clearCaches()
            AppMessages.success(context, "Updated $ok of ${list.size}")
        }
    }

    Column(Modifier.fillMaxSize().testTag("market_screen")) {
        SubPageHeader(
            title = "Extension market", onBack = onBack,
            subtitle = data?.let { d -> "${safe.count { it.primary }} extensions · ${d.repos.size} repositories" } ?: "Loading…",
        ) {
            IconButton(onClick = { showRepoDialog = true }, modifier = Modifier.testTag("market_add_repo")) { Icon(Icons.Default.Add, "Add repository", tint = TextPrimary) }
        }
        // Tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(22.dp)).background(GlassFill).padding(4.dp)) {
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
        if (tab != MarketTab.REPOS) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search name, site, author") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear", tint = TextSecondary) } },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen, unfocusedBorderColor = GlassFill, focusedContainerColor = GlassFill, unfocusedContainerColor = GlassFill),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("market_search"),
            )
        }
        if (tab == MarketTab.DISCOVER) {
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { c -> OnlineChip(c, c == category, { category = c }, Modifier.testTag("market_cat_$c")) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    OnlineChip(language?.let { "Language: ${it.uppercase()}" } ?: "Any language", language != null, { showLanguages = true }, Modifier.testTag("market_language"))
                    DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                        DropdownMenuItem(text = { Text("Any language") }, onClick = { language = null; showLanguages = false })
                        languages.take(40).forEach { l -> DropdownMenuItem(text = { Text(languageName(l)) }, onClick = { language = l; showLanguages = false }) }
                    }
                }
                MarketSort.entries.forEach { s2 -> OnlineChip(s2.label, s2 == sort, { sort = s2 }) }
            }
            repoFilter?.let { rf ->
                val name = repos.firstOrNull { it.url.equals(rf, true) }?.name ?: ExtensionFormats.repoNameFromUrl(rf)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OnlineChip("Repository: $name  ✕", true, { repoFilter = null }, Modifier.testTag("market_repo_filter"))
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

        when {
            data == null && error == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(6) { SkeletonRow(trailing = true) } }
            data == null || (data?.items?.isEmpty() == true && error != null && tab != MarketTab.REPOS) -> StateMessage(
                Icons.Default.CloudOff, "Market unavailable", error ?: "Check your connection and try again.", tint = ErrorColor,
                primary = "Retry" to { scope.launch { error = null; data = null; load(true) } },
                secondary = "Add a repository" to { showRepoDialog = true }, testTag = "market_error",
                modifier = Modifier.padding(top = 32.dp),
            )
            tab == MarketTab.REPOS -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { load(true) } }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().testTag("market_repos"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item("repo_note") {
                        Text("Found automatically from CloudStream's community list and GitHub. Curated repositories are checked and mirrored by the market.", color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    items(repos, key = { "r_" + it.url }) { r ->
                        RepoCard(r) { repoFilter = r.url; category = "All"; language = null; tab = MarketTab.DISCOVER }
                    }
                }
            }
            else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { load(true) } }, modifier = Modifier.fillMaxSize()) {
                if (visible.isEmpty()) StateMessage(
                    Icons.Default.SearchOff, "Nothing here",
                    when { query.isNotBlank() -> "No extension matches \"$query\"."; tab == MarketTab.INSTALLED -> "You haven't installed any extension yet."; else -> "No extensions in this list yet." },
                    tint = TextSecondary, testTag = "market_empty", modifier = Modifier.padding(top = 32.dp),
                )
                else LazyColumn(Modifier.fillMaxSize().testTag("market_list"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tab == MarketTab.INSTALLED && updates.isNotEmpty()) item("update_all") {
                        Button(onClick = { updateAll() }, colors = ButtonDefaults.buttonColors(containerColor = WarnColor, contentColor = OnAccent), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("market_update_all")) {
                            Text("Update all (${updates.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                    items(visible, key = { it.key }) { ext ->
                        MarketCard(
                            ext = ext,
                            installedVersion = installedKeys[ext.key]?.version,
                            hasUpdate = ext.key in updates,
                            busy = ext.key in engine.busy,
                            showRank = tab == MarketTab.DISCOVER && sort == MarketSort.TOP && category == "All" && language == null && repoFilter == null && query.isBlank(),
                            onInstall = { install(ext) },
                            onManage = onOpenExtensions,
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

    if (showRepoDialog) InputDialog(
        title = "Add repository", label = "Repository link", hint = "https://…/repo.json or a GitHub repo",
        confirm = "Add", onDismiss = { showRepoDialog = false },
    ) { text, done ->
        scope.launch {
            runCatching { ExtensionManager.addRepository(context, text) }
                .onSuccess { repo ->
                    done(null); showRepoDialog = false
                    AppMessages.success(context, "Added ${repo.name}")
                    load(true); repoFilter = repo.url; tab = MarketTab.DISCOVER
                }
                .onFailure { done(OnlineErrors.friendly(it)) }
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
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(14.dp))
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
    onInstall: () -> Unit, onManage: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("market_item_${ext.internalName}")) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showRank && ext.rank in 1..999) {
                    Text("${ext.rank}", color = if (ext.rank <= 3) SpotifyGreen else TextTertiary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.width(30.dp))
                }
                ExtensionIcon(ext.iconUrl?.replace("%size%", "128"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ext.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (ext.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Verified, "Curated", tint = SpotifyGreen, modifier = Modifier.size(14.dp)) }
                    }
                    Text(
                        listOfNotNull("v${ext.version}", ext.repoName, ext.fileSize?.takeIf { it > 0 }?.let { formatSize(it) }).joinToString(" · "),
                        color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // FlowRow + single-line chunks: when the trailing button grows (e.g. "Installed"),
                    // whole chunks wrap to the next line instead of breaking text letter by letter.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("market_stats_${ext.internalName}")) {
                        ext.stars?.takeIf { it > 0 }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(12.dp))
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
                ext.language?.takeIf { it.isNotBlank() }?.let { Pill(it.uppercase(), TextSecondary) }
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
