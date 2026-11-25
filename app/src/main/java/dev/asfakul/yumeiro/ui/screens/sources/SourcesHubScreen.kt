/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.servers.DlnaTree
import dev.asfakul.yumeiro.sources.servers.HomeServers
import dev.asfakul.yumeiro.sources.servers.PlexClient
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.PillTabRow
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One place for every source ecosystem besides CloudStream: Aniyomi (anime) and Mihon (manga)
 * extension repos, Stremio add-ons, Mangayomi-style JavaScript sources and home servers.
 * Everything runs on the device; nothing here needs a Yumeiro server.
 */
@Composable
fun SourcesHubScreen(onBack: () -> Unit, initialTab: Int = -1, onOpenCloudStream: (() -> Unit)? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.sources.Bridges.start(context.applicationContext) }
    // GROUP D: a stack of pages (overview → ecosystem → Browse / Repositories) instead of tabs inside tabs.
    var keys by rememberSaveable { mutableStateOf(SourcesNav.save(SourcesNav.start(initialTab))) }
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val stack = SourcesNav.restore(keys)
    fun go(r: SourceRoute) {
        if (r.part == null && r.page != SourcePage.HOME) lastOpened = r.page.name
        keys = SourcesNav.save(SourcesNav.push(SourcesNav.restore(keys), r))
    }
    fun up() { val p = SourcesNav.pop(SourcesNav.restore(keys)); if (p == null) onBack() else keys = SourcesNav.save(p) }
    val depth: (SourceRoute) -> Int = { if (it.page == SourcePage.HOME) 0 else if (it.part == null) 1 else 2 }

    dev.asfakul.yumeiro.ui.components.TabSwap(stack.last(), depth, Modifier.fillMaxSize().testTag("sources_hub"), label = "sources_page") { r ->
        val nav = SourcesPageNav(r, ::up, ::go)
        Column(Modifier.fillMaxSize().testTag(r.tag)) {
            when (r.page) {
                SourcePage.HOME -> HomePage(nav, lastOpened?.let { n -> runCatching { SourcePage.valueOf(n) }.getOrNull() }, onOpenCloudStream)
                SourcePage.ANIYOMI -> TachiyomiTab(TachiyomiExtensions.Kind.ANIME, nav)
                SourcePage.MIHON -> TachiyomiTab(TachiyomiExtensions.Kind.MANGA, nav)
                SourcePage.STREMIO -> StremioTab(nav)
                SourcePage.JS -> JsTab(nav)
                SourcePage.SCRIPTS -> { nav.Header("Script repos", "Vega provider repositories"); ScriptReposTab() }
                SourcePage.SERVERS -> ServersTab(nav)
            }
        }
    }
}

/** What a page needs to move around the Source page flow. */
internal class SourcesPageNav(val route: SourceRoute, val up: () -> Unit, val go: (SourceRoute) -> Unit) {
    fun open(part: HubPart) = go(SourceRoute(route.page, part))
}

@Composable
internal fun SourcesPageNav.Header(title: String, subtitle: String? = null, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    SubPageHeader(title = title, onBack = up, subtitle = subtitle, actions = actions)
}

@Composable
private fun HomePage(nav: SourcesPageNav, lastOpened: SourcePage?, onOpenCloudStream: (() -> Unit)?) {
    val context = LocalContext.current
    var any by rememberSaveable { mutableStateOf(false) }
    nav.Header("Sources", "Extensions, add-ons and home servers") {
        dev.asfakul.yumeiro.ui.components.AddIconButton("Add anything", "sources_add_any", { any = true })
        dev.asfakul.yumeiro.ui.components.HelpIconButton("About sources", SourcesNav.HOME_HELP, "sources_help")
    }
    if (any) UniversalAddDialog(onDismiss = { any = false }, onServer = { out -> AppMessages.show(context, out.message); any = false; nav.go(SourceRoute(SourcePage.SERVERS)) })
    SourcesHome(lastOpened, onOpenCloudStream) { nav.go(SourceRoute(it)) }
}

/** Last fetched repository listings, so going Installed → Browse → back does not refetch every index. */
internal object HubAvailable {
    val tachi = java.util.concurrent.ConcurrentHashMap<String, List<TachiyomiExtensions.Available>>()
    @Volatile var js: Pair<Int, List<JsSources.Entry>>? = null
}

// region shared bits

@Composable
internal fun HubCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    badge: String? = null,
    warning: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(shape = RoundedCornerShape(18.dp), color = GlassFill, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = SpotifyGreen.copy(alpha = 0.16f)) {
                            Text(badge, color = SpotifyGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = TextTertiary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (warning != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Warning, null, tint = dev.asfakul.yumeiro.ui.theme.WarnTone, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(warning, color = dev.asfakul.yumeiro.ui.theme.WarnTone, fontSize = 11.sp, maxLines = 3)
                }
            }
            trailing()
        }
    }
}

@Composable
internal fun SectionTitle(text: String, hint: String? = null, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (hint != null) Text(hint, color = TextTertiary, fontSize = 11.sp)
        }
        if (action != null) TextButton(onClick = action.second) { Text(action.first, color = SpotifyGreen) }
    }
}

/** GROUP C: "Your repositories" with + (add sheet) and ? (what this tab is) instead of a link box and a paragraph. */
@Composable
internal fun RepoHeader(hint: String, tagPrefix: String, addLabel: String, helpTitle: String, helpText: String, onAdd: () -> Unit) {
    dev.asfakul.yumeiro.ui.components.YuSectionHeader("Your repositories", hint) {
        dev.asfakul.yumeiro.ui.components.AddIconButton(addLabel, tagPrefix + "_add_plus", onAdd)
        dev.asfakul.yumeiro.ui.components.HelpIconButton(helpTitle, helpText, tagPrefix + "_help")
    }
}

/** GROUP C: a list heading with a search icon (slim field only while open) and a refresh icon. */
@Composable
internal fun BrowseHeader(
    title: String, hint: String, searchOpen: Boolean, query: String,
    onSearch: (Boolean) -> Unit, onQuery: (String) -> Unit, placeholder: String, onRefresh: () -> Unit,
) {
    Column {
        dev.asfakul.yumeiro.ui.components.YuSectionHeader(title, hint) {
            dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), { onSearch(!(searchOpen || query.isNotEmpty())) }, "hub_filter_open", "Search $title".lowercase().replaceFirstChar { it.uppercase() })
            IconButton(onClick = onRefresh, modifier = Modifier.testTag("hub_refresh")) { Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary) }
        }
        dev.asfakul.yumeiro.ui.components.HeaderSearchField(searchOpen || query.isNotEmpty(), query, onQuery, placeholder, "hub_filter", horizontalPadding = 0.dp)
    }
}

@Composable
internal fun UrlDialog(title: String, hint: String, examples: String?, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    TsukiDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(hint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("hub_url_field"))
                if (examples != null) Text(examples, color = TextTertiary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSubmit(text.trim()) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LazyListScope.note(key: String, text: String) = item(key) {
    Text(text, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
}

private fun afterChange() = OnlineRepository.clearCaches()

// endregion

// region Aniyomi / Mihon

@Composable
private fun TachiyomiTab(kind: TachiyomiExtensions.Kind, nav: SourcesPageNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by TachiyomiExtensions.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    val repos = state.repos.filter { it.kind == kind }
    val installed = state.installed.filter { it.kind == kind }
    val cacheKey = kind.name + "/" + repos.size
    var available by remember(kind) { mutableStateOf(HubAvailable.tachi[cacheKey] ?: emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var trustAsk by remember { mutableStateOf<TachiyomiExtensions.Installed?>(null) }
    var addOpen by rememberSaveable(kind) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(kind) { mutableStateOf(false) }
    val kTag = "hub_tachiyomi_${kind.name.lowercase()}"

    suspend fun refresh() {
        loading = true
        available = runCatching { TachiyomiExtensions.available(context, kind) }.getOrDefault(available)
        HubAvailable.tachi[kind.name + "/" + TachiyomiExtensions.state.value.repos.count { it.kind == kind }] = available
        loading = false
    }
    LaunchedEffect(kind, repos.size) { if (repos.isNotEmpty() && HubAvailable.tachi[cacheKey] == null) refresh() }
    suspend fun addRepo(url: String): Boolean {
        loading = true
        val r = TachiyomiExtensions.addRepo(context, url, kind)
        r.onSuccess { AppMessages.success(context, "Repository added · $it extensions"); refresh() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { scope.launch { addRepo(url) } } }
        loading = false
        return r.isSuccess
    }
    fun addRepoUrl(url: String) { scope.launch { addRepo(url) } }

    val installedPkgs = installed.associateBy { it.pkg }
    val shown = available
        .filter { adult || it.entry.nsfw == 0 }
        .filter { query.isBlank() || it.displayName.contains(query, true) || it.entry.lang.equals(query, true) }
        .sortedBy { it.displayName.lowercase() }
    // GROUP D: Installed is the page; Browse and Repositories open as their own pages (no inner tab row).
    val part = nav.route.part ?: HubPart.INSTALLED
    val updates = installed.count { e -> available.any { it.entry.pkg == e.pkg && e.versionCode < it.entry.code } }
    val help = (if (kind == TachiyomiExtensions.Kind.ANIME) "Aniyomi anime extensions (lib 12–16) run inside Yumeiro. Their titles appear in Videos → Online like any other source."
        else "Mihon / Tachiyomi manga extensions (lib 1.2–1.5). Their titles appear in the Manga section.") +
        "\n\nAdd a repository with +: GitHub repo links, raw links or the index.min.json URL. Only add repositories you trust."
    val page = if (kind == TachiyomiExtensions.Kind.ANIME) "Aniyomi" else "Mihon"

    Column(Modifier.fillMaxSize()) {
        nav.Header(
            when (part) { HubPart.BROWSE -> "Browse"; HubPart.REPOS -> "Repositories"; else -> page },
            when (part) { HubPart.INSTALLED -> "${kind.label.replaceFirstChar { it.uppercase() }} extensions"; else -> page },
        ) {
            dev.asfakul.yumeiro.ui.components.AddIconButton("Add a ${kind.label} repository", kTag + "_add_plus", { addOpen = true })
            dev.asfakul.yumeiro.ui.components.HelpIconButton("About ${kind.label} extensions", help, kTag + "_help")
        }
        if (loading || busy != null) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_tachiyomi_${kind.name.lowercase()}"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("hub_tachiyomi_" + kind.name + part.tag), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (part) {
                HubPart.INSTALLED -> {
                    item("go") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubPageLink("src_go_browse", "Browse extensions",
                                if (repos.isEmpty()) "Add a repository first" else "${available.size} available" + if (updates > 0) " · $updates updates" else "") { nav.open(HubPart.BROWSE) }
                            SubPageLink("src_go_repos", "Repositories", "${repos.size} added · Discover more") { nav.open(HubPart.REPOS) }
                        }
                    }
                    item("installed") { SectionTitle("Installed", "${installed.size} extension${if (installed.size == 1) "" else "s"}") }
                    if (installed.isEmpty()) item("noinst") {
                        HubEmpty("Nothing installed yet.", if (repos.isEmpty()) "Add a repository" to { nav.open(HubPart.REPOS) } else "Browse extensions" to { nav.open(HubPart.BROWSE) })
                    }
                    items(installed, key = { "i" + it.pkg }) { e ->
                        HubCard(
                            title = e.name,
                            subtitle = "v${e.versionName} · ${e.sources} source${if (e.sources == 1) "" else "s"}" + (if (e.nsfw) " · 18+" else ""),
                            badge = if (e.trusted) "Trusted" else null,
                            warning = e.error ?: if (!e.trusted) "Not trusted yet. Its code won't run until you trust it." else null,
                        ) {
                            if (!e.trusted && e.signature != null) TextButton(onClick = { trustAsk = e }) { Text("Trust", color = SpotifyGreen) }
                            IconButton(onClick = { scope.launch { TachiyomiExtensions.uninstall(context, e.pkg); afterChange() } }) { Icon(Icons.Default.Delete, "Uninstall", tint = TextSecondary) }
                        }
                    }
                }
                HubPart.BROWSE -> {
                    if (repos.isEmpty()) item("nobrowse") { HubEmpty("Add a repository first; its extensions are listed here.", "Add a repository" to { nav.open(HubPart.REPOS) }) }
                    else {
                        item("available") {
                            BrowseHeader("Available", "${shown.size} in your ${repos.size} repositor${if (repos.size == 1) "y" else "ies"}", searchOpen, query,
                                onSearch = { searchOpen = it; if (!it) query = "" }, onQuery = { query = it }, placeholder = "Name or language (en, ja…)") { scope.launch { refresh() } }
                        }
                        if (shown.isEmpty() && !loading) note("noavail", if (query.isBlank()) "Your repositories list nothing for this app yet. Pull Refresh or add another repository." else "Nothing matches \"$query\".")
                        items(shown.take(400), key = { "a" + it.repo.url + it.entry.pkg }) { a ->
                            val have = installedPkgs[a.entry.pkg]
                            val upd = have != null && have.versionCode < a.entry.code
                            HubCard(a.displayName, "${a.entry.lang.uppercase()} · v${a.entry.version}" + (if (a.entry.nsfw == 1) " · 18+" else ""), badge = if (have != null && !upd) "Installed" else null) {
                                if (have == null || upd) Button(
                                    onClick = {
                                        scope.launch {
                                            busy = a.entry.pkg
                                            TachiyomiExtensions.install(context, a)
                                                .onSuccess { AppMessages.success(context, "${a.displayName} installed"); afterChange() }
                                                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                            busy = null
                                        }
                                    },
                                    enabled = busy == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = RoundedCornerShape(20.dp),
                                ) { Text(if (upd) "Update" else "Install") }
                            }
                        }
                    }
                }
                HubPart.REPOS -> {
                    item("repos") { SectionTitle("Your repositories", "${repos.size} added") }
                    if (repos.isEmpty()) note("norepo", "No repositories yet. Tap + to add a link, or pick one from Discover below.")
                    items(repos, key = { "r" + it.url }) { r ->
                        HubCard(r.name.ifBlank { r.url }, r.url) {
                            IconButton(onClick = { scope.launch { TachiyomiExtensions.removeRepo(context, r.url); available = available.filterNot { it.repo.url == r.url } } }) {
                                Icon(Icons.Default.Delete, "Remove", tint = TextSecondary)
                            }
                        }
                    }
                    item("market") {
                        val repoUrls = repos.map { it.url }.toSet()
                        RepoMarket(
                            kind = if (kind == TachiyomiExtensions.Kind.ANIME) dev.asfakul.yumeiro.sources.SourceMarket.Kind.ANIYOMI else dev.asfakul.yumeiro.sources.SourceMarket.Kind.MIHON,
                            label = kind.label,
                            isAdded = { runCatching { TachiyomiExtensions.normalizeRepo(it) in repoUrls }.getOrDefault(false) },
                            startExpanded = repos.isEmpty(),
                            onAdd = { addRepoUrl(it) },
                        )
                    }
                    item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(if (kind == TachiyomiExtensions.Kind.ANIME) dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.aniyomi else dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.mihon) }
                }
            }
        }
    }
    if (addOpen) dev.asfakul.yumeiro.ui.components.AddLinkSheet(
        "Add a ${kind.label} repository", "https://…/index.min.json", "GitHub repo links, raw links or the index.min.json URL. Only add repositories you trust.",
        kTag + "_add", onDismiss = { addOpen = false }, onAdd = { addRepo(it) },
    )
    trustAsk?.let { e ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.WARNING,
            onDismissRequest = { trustAsk = null },
            icon = { Icon(Icons.Default.Verified, null) },
            title = { Text("Trust ${e.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extensions are code from other people. Only trust ones from repositories you know. Yumeiro remembers this signing key; an update signed with a different key is refused.")
                    Text("Signing key (SHA-256)", color = TextTertiary, fontSize = 12.sp)
                    Text(e.signature.orEmpty().chunked(2).joinToString(":").uppercase(), fontSize = 11.sp, color = TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { val sig = e.signature ?: return@TextButton; trustAsk = null
                scope.launch { TachiyomiExtensions.trust(context, e.pkg, sig); afterChange() } }) { Text("Trust") } },
            dismissButton = { TextButton(onClick = { trustAsk = null }) { Text("Cancel") } },
        )
    }
}

// endregion

// region Stremio

@Composable
private fun StremioTab(nav: SourcesPageNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addons by StremioAddons.addons.collectAsState()
    var busy by remember { mutableStateOf(false) }
    suspend fun addAddon(url: String): Boolean {
        busy = true
        val r = StremioAddons.add(context, url)
        r.onSuccess { AppMessages.success(context, "${it.name} added"); afterChange() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { scope.launch { addAddon(url) } } }
        busy = false
        return r.isSuccess
    }
    fun addUrl(url: String) { scope.launch { addAddon(url) } }
    // GROUP D: add-ons have no repositories: the page is "Your add-ons", "Get add-ons" opens as a page.
    var addOpen by rememberSaveable { mutableStateOf(false) }
    val part = nav.route.part ?: HubPart.INSTALLED
    Column(Modifier.fillMaxSize()) {
        nav.Header(if (part == HubPart.BROWSE) "Get add-ons" else "Stremio", if (part == HubPart.BROWSE) "Stremio" else "Catalogs and streams") {
            dev.asfakul.yumeiro.ui.components.AddIconButton("Add a Stremio add-on", "hub_stremio_add_plus", { addOpen = true })
            dev.asfakul.yumeiro.ui.components.HelpIconButton("About Stremio add-ons",
                "Stremio add-ons give catalogs (movies, series, channels, sports, anime) and streams. Pick one from Get add-ons or add any add-on's manifest link with +. HTTP streams and subtitles are supported; torrent-only streams are skipped.",
                "hub_stremio_help")
        }
        if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_stremio"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("hub_stremio" + part.tag), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (part == HubPart.INSTALLED) {
                item("go") { SubPageLink("src_go_browse", "Get add-ons", "Suggested add-ons and Discover") { nav.open(HubPart.BROWSE) } }
                item("head") { SectionTitle("Your add-ons", "${addons.size} added") }
                if (addons.isEmpty()) item("none") { HubEmpty("No add-ons yet.", "Get add-ons" to { nav.open(HubPart.BROWSE) }) }
                items(addons, key = { it.manifestUrl }) { a ->
                    val res = a.resources.joinToString(" · ") { it.name }
                    HubCard(a.name, "v${a.version} · ${a.types.joinToString()} · $res", badge = if (a.catalogs.isNotEmpty()) "${a.catalogs.size} catalogs" else null) {
                        IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { StremioAddons.remove(context, a.manifestUrl) }; afterChange() } }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                    }
                }
            } else {
                if (addons.none { it.manifestUrl == StremioAddons.CINEMETA }) item("cinemeta") {
                    HubCard("Cinemeta", "Official Stremio catalog of movies and series (metadata only). Good base for other stream add-ons.", badge = "Suggested") {
                        TextButton(onClick = { addUrl(StremioAddons.CINEMETA) }) { Text("Add", color = SpotifyGreen) }
                    }
                }
                item("market") {
                    val have = addons.map { it.manifestUrl }.toSet()
                    AddonMarket(isAdded = { StremioAddons.normalize(it) in have }, startExpanded = addons.isEmpty(), onAdd = { addUrl(it) })
                }
                item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.stremio) }
            }
        }
    }
    if (addOpen) dev.asfakul.yumeiro.ui.components.AddLinkSheet(
        "Add a Stremio add-on", "https://…/manifest.json", "stremio:// links work too.", "hub_stremio_add",
        onDismiss = { addOpen = false }, onAdd = { addAddon(it) },
    )
}

// endregion

// region JavaScript

@Composable
private fun JsTab(nav: SourcesPageNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by JsSources.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    var available by remember { mutableStateOf(HubAvailable.js?.takeIf { it.first == state.repos.size }?.second ?: emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    suspend fun refresh() {
        loading = true
        available = runCatching { JsSources.available(context) }.getOrDefault(available)
        HubAvailable.js = JsSources.state.value.repos.size to available
        loading = false
    }
    LaunchedEffect(state.repos.size) { if (state.repos.isNotEmpty() && HubAvailable.js?.first != state.repos.size) refresh() }
    suspend fun addRepo(url: String): Boolean {
        loading = true
        val r = JsSources.addRepo(context, url)
        r.onSuccess { AppMessages.success(context, "Repository added · $it sources"); refresh() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { scope.launch { addRepo(url) } } }
        loading = false
        return r.isSuccess
    }
    fun addRepoUrl(url: String) { scope.launch { addRepo(url) } }
    val installedIds = state.installed.associateBy { it.id }
    val shown = available.filter { it.isJs && (adult || !it.nsfw) && (query.isBlank() || it.name.contains(query, true) || it.lang.equals(query, true)) }.sortedBy { it.name.lowercase() }
    // GROUP D: Installed is the page; Browse and Repositories open as their own pages.
    val part = nav.route.part ?: HubPart.INSTALLED
    Column(Modifier.fillMaxSize()) {
        nav.Header(when (part) { HubPart.BROWSE -> "Browse"; HubPart.REPOS -> "Repositories"; else -> "JavaScript" },
            if (part == HubPart.INSTALLED) "Mangayomi-format sources" else "JavaScript") {
            dev.asfakul.yumeiro.ui.components.AddIconButton("Add a JavaScript repository", "hub_js_add_plus", { addOpen = true })
            dev.asfakul.yumeiro.ui.components.HelpIconButton("About JavaScript sources",
                "JavaScript sources in the Mangayomi format run in a sandboxed QuickJS engine (no file or app access). Manga sources show in the Manga section; anime and video sources in Videos → Online." +
                    "\n\nAdd a Mangayomi-format repository with +. Only JavaScript sources are used.", "hub_js_help")
        }
        if (loading) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_js"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("hub_js" + part.tag), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (part) {
                HubPart.INSTALLED -> {
                    item("go") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubPageLink("src_go_browse", "Browse sources", if (state.repos.isEmpty()) "Add a repository first" else "${available.count { it.isJs }} available") { nav.open(HubPart.BROWSE) }
                            SubPageLink("src_go_repos", "Repositories", "${state.repos.size} added · Discover more") { nav.open(HubPart.REPOS) }
                        }
                    }
                    item("inst") { SectionTitle("Installed", "${state.installed.size} sources") }
                    if (state.installed.isEmpty()) item("noinst") {
                        HubEmpty("Nothing installed yet.", if (state.repos.isEmpty()) "Add a repository" to { nav.open(HubPart.REPOS) } else "Browse sources" to { nav.open(HubPart.BROWSE) })
                    }
                    items(state.installed, key = { "i" + it.id }) { e ->
                        HubCard(e.name, "${e.kindLabel} · ${e.lang.uppercase()} · v${e.version}") {
                            IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { JsSources.uninstall(context, e.id) }; afterChange() } }) { Icon(Icons.Default.Delete, "Uninstall", tint = TextSecondary) }
                        }
                    }
                }
                HubPart.BROWSE -> {
                    if (state.repos.isEmpty()) item("nobrowse") { HubEmpty("Add a repository first; its sources are listed here.", "Add a repository" to { nav.open(HubPart.REPOS) }) }
                    else {
                        item("avail") {
                            BrowseHeader("Available", "${shown.size} JavaScript sources", searchOpen, query,
                                onSearch = { searchOpen = it; if (!it) query = "" }, onQuery = { query = it }, placeholder = "Name or language") { scope.launch { refresh() } }
                        }
                        if (shown.isEmpty() && !loading) note("noavail", if (query.isBlank()) "Your repositories list no JavaScript sources yet." else "Nothing matches \"$query\".")
                        items(shown.take(400), key = { "a" + it.id }) { e ->
                            val have = installedIds[e.id]
                            val upd = have != null && have.version != e.version
                            HubCard(e.name, "${e.kindLabel} · ${e.lang.uppercase()} · v${e.version}" + if (e.nsfw) " · 18+" else "", badge = if (have != null && !upd) "Installed" else null) {
                                if (have == null || upd) IconButton(onClick = {
                                    scope.launch {
                                        JsSources.install(context, e)
                                            .onSuccess { AppMessages.success(context, "${e.name} installed"); afterChange() }
                                            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                    }
                                }) { Icon(if (upd) Icons.Default.Refresh else Icons.Default.Download, if (upd) "Update" else "Install", tint = SpotifyGreen) }
                            }
                        }
                    }
                }
                HubPart.REPOS -> {
                    item("repos") { SectionTitle("Your repositories", "${state.repos.size} added") }
                    if (state.repos.isEmpty()) note("norepo", "No repositories yet. Tap + to add a link, or pick one from Discover below.")
                    items(state.repos, key = { "r$it" }) { r ->
                        HubCard(r.substringAfter("://").substringBefore("/"), r) {
                            IconButton(onClick = { scope.launch(Dispatchers.IO) { JsSources.removeRepo(context, r) } }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                        }
                    }
                    item("market") {
                        val have = state.repos.toSet()
                        RepoMarket(
                            kind = dev.asfakul.yumeiro.sources.SourceMarket.Kind.MANGAYOMI, label = "Mangayomi",
                            isAdded = { runCatching { JsSources.normalizeRepo(it) in have }.getOrDefault(false) },
                            startExpanded = state.repos.isEmpty(),
                            onAdd = { addRepoUrl(it) },
                        )
                    }
                    item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.javascript) }
                }
            }
        }
    }
    if (addOpen) dev.asfakul.yumeiro.ui.components.AddLinkSheet(
        "Add a JavaScript repository", "https://…/index.json", "Mangayomi-format repositories. Only JavaScript sources are used.", "hub_js_add",
        onDismiss = { addOpen = false }, onAdd = { addRepo(it) },
    )
}

// endregion

// region Home servers

@Composable
private fun ServersTab(nav: SourcesPageNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by HomeServers.servers.collectAsState()
    var form by remember { mutableStateOf<HomeServers.Kind?>(null) }
    var found by remember { mutableStateOf<List<DlnaTree.Companion.Found>?>(null) }
    var scanning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        nav.Header("Home servers", "Play your own media") {
            dev.asfakul.yumeiro.ui.components.HelpIconButton("About home servers",
                "Play your own media from Jellyfin, Emby, Plex, DLNA/UPnP, WebDAV or SMB shares. Yumeiro talks to your server directly; passwords and tokens are encrypted with the Android Keystore." +
                    "\n\nServers with self-signed HTTPS certificates aren't supported yet; use plain HTTP on your home network or a real certificate.", "hub_servers_help")
        }
        if (scanning) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_servers"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("hub_servers"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.servers) }
            item("add") {
                SectionTitle("Add a server")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(HomeServers.Kind.JELLYFIN, HomeServers.Kind.EMBY, HomeServers.Kind.PLEX).forEach { k ->
                        Button(onClick = { form = k }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = TextPrimary)) { Text(k.label, maxLines = 1, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        scope.launch {
                            scanning = true
                            found = runCatching { DlnaTree.discover(context) }.getOrElse { AppMessages.error(context, OnlineErrors.friendly(it)); null }
                            scanning = false
                        }
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = TextPrimary)) { Text("DLNA", maxLines = 1, fontSize = 13.sp) }
                    listOf(HomeServers.Kind.WEBDAV, HomeServers.Kind.SMB).forEach { k ->
                        Button(onClick = { form = k }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = TextPrimary)) { Text(k.label, maxLines = 1, fontSize = 13.sp) }
                    }
                }
            }
            item("saved") { SectionTitle("Your servers", "${servers.size} saved") }
            if (servers.isEmpty()) note("none", "No servers yet.")
            items(servers, key = { it.id }) { s ->
                HubCard(s.name, s.url, badge = s.kind.label) {
                    IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { HomeServers.remove(context, s.id) }; afterChange() } }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                }
            }
        }
    }
    form?.let { k -> ServerForm(k, onDismiss = { form = null }) { form = null; afterChange() } }
    found?.let { list ->
        TsukiDialog(
            onDismissRequest = { found = null },
            title = { Text("DLNA servers nearby") },
            text = {
                if (list.isEmpty()) Text("Nothing answered. Make sure you're on the same Wi-Fi and the server has DLNA turned on.")
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { f ->
                        TextButton(onClick = {
                            found = null
                            scope.launch {
                                HomeServers.add(context, HomeServers.Kind.DLNA, f.name, f.location, "", "")
                                    .onSuccess { AppMessages.success(context, "${it.name} added"); afterChange() }
                                    .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(f.name, color = TextPrimary, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { found = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ServerForm(kind: HomeServers.Kind, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(if (kind == HomeServers.Kind.SMB) "smb://" else "") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var plexCode by remember { mutableStateOf<String?>(null) }
    val needsUser = kind != HomeServers.Kind.PLEX

    // ER-19 (run 43985787392, both phones): this dialog stays open on failure, so a bottom message sat
    // behind it and the keyboard: the reason was never seen and its Retry could not be tapped. The
    // reason now shows inside the form and the confirm button becomes Retry.
    var error by remember { mutableStateOf<String?>(null) }

    fun save(secret: String): kotlinx.coroutines.Job = scope.launch {
        busy = true
        error = null
        HomeServers.add(context, kind, name, url, user, secret)
            .onSuccess { AppMessages.success(context, "${it.name} connected"); onSaved() }
            .onFailure { error = OnlineErrors.friendly(it) }
        busy = false
    }

    TsukiDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add ${kind.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(if (kind == HomeServers.Kind.SMB) "smb://host/share/folder" else "Server address") },
                    placeholder = { Text(when (kind) { HomeServers.Kind.PLEX -> "http://192.168.1.10:32400"; HomeServers.Kind.WEBDAV -> "https://cloud.example/remote.php/dav/files/me"; else -> "http://192.168.1.10:8096" }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth().testTag("server_url"))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
                if (needsUser) {
                    OutlinedTextField(user, { user = it }, singleLine = true, label = { Text("User name") }, modifier = Modifier.fillMaxWidth().testTag("server_user"))
                    OutlinedTextField(pass, { pass = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("server_pass"))
                } else {
                    Text(plexCode?.let { "Waiting for you to approve code $it in the browser…" } ?: "Sign in with your Plex account in the browser. Yumeiro only stores the token Plex gives back.",
                        color = TextSecondary, fontSize = 13.sp)
                }
                error?.let { e ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().testTag("server_error")
                        .semantics(mergeDescendants = true) { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
                        Icon(Icons.Default.Warning, null, tint = dev.asfakul.yumeiro.ui.theme.WarnTone, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(e, color = dev.asfakul.yumeiro.ui.theme.WarnTone, fontSize = 13.sp)
                    }
                }
                if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url.isNotBlank(), modifier = Modifier.testTag("server_connect"), onClick = {
                if (kind != HomeServers.Kind.PLEX) save(pass)
                else scope.launch {
                    busy = true
                    runCatching {
                        val pin = PlexClient.createPin(context)
                        plexCode = pin.code
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pin.authUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        PlexClient.awaitToken(context, pin)
                    }.onSuccess { token -> busy = false; save(token) }
                        .onFailure { busy = false; plexCode = null; error = OnlineErrors.friendly(it) }
                }
            }) { Text(if (error != null) "Retry" else if (kind == HomeServers.Kind.PLEX) "Sign in with Plex" else "Connect") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

// endregion
