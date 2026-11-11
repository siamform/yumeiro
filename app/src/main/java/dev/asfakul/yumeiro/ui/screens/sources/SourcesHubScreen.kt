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
import kotlinx.coroutines.launch

/**
 * One place for every source ecosystem besides CloudStream: Aniyomi (anime) and Mihon (manga)
 * extension repos, Stremio add-ons, Mangayomi-style JavaScript sources and home servers.
 * Everything runs on the device; nothing here needs a Yumeiro server.
 */
@Composable
fun SourcesHubScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val tabs = listOf("Aniyomi", "Mihon", "Stremio", "JavaScript", "Home servers")
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.sources.Bridges.start(context.applicationContext) }

    Column(Modifier.fillMaxSize().testTag("sources_hub")) {
        SubPageHeader(title = "Sources", onBack = onBack, subtitle = "Extensions, add-ons and home servers")
        PillTabRow(tabs = tabs, selectedIndex = tab, onSelect = { tab = it }, tagPrefix = "sources_tab")
        when (tab) {
            0 -> TachiyomiTab(TachiyomiExtensions.Kind.ANIME)
            1 -> TachiyomiTab(TachiyomiExtensions.Kind.MANGA)
            2 -> StremioTab()
            3 -> JsTab()
            else -> ServersTab()
        }
    }
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
private fun TachiyomiTab(kind: TachiyomiExtensions.Kind) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by TachiyomiExtensions.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    val repos = state.repos.filter { it.kind == kind }
    val installed = state.installed.filter { it.kind == kind }
    var available by remember(kind) { mutableStateOf<List<TachiyomiExtensions.Available>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var addRepo by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var trustAsk by remember { mutableStateOf<TachiyomiExtensions.Installed?>(null) }

    suspend fun refresh() {
        loading = true
        available = runCatching { TachiyomiExtensions.available(context, kind) }.getOrDefault(available)
        loading = false
    }
    LaunchedEffect(kind, repos.size) { if (repos.isNotEmpty()) refresh() }
    fun addRepoUrl(url: String) = scope.launch {
        loading = true
        TachiyomiExtensions.addRepo(context, url, kind)
            .onSuccess { AppMessages.success(context, "Repository added · $it extensions"); refresh() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
        loading = false
    }

    val installedPkgs = installed.associateBy { it.pkg }
    val shown = available
        .filter { adult || it.entry.nsfw == 0 }
        .filter { query.isBlank() || it.displayName.contains(query, true) || it.entry.lang.equals(query, true) }
        .sortedBy { it.displayName.lowercase() }

    Column(Modifier.fillMaxSize()) {
        if (loading || busy != null) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_tachiyomi_${kind.name.lowercase()}"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("about") {
                Text(
                    if (kind == TachiyomiExtensions.Kind.ANIME) "Aniyomi anime extensions (lib 12–16) run inside Yumeiro. Their titles appear in Videos → Online like any other source."
                    else "Mihon / Tachiyomi manga extensions (lib 1.2–1.5). Their titles appear in the Manga section.",
                    color = TextSecondary, fontSize = 13.sp,
                )
            }
            item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(if (kind == TachiyomiExtensions.Kind.ANIME) dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.aniyomi else dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.mihon, startExpanded = repos.isEmpty()) }
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
            item("repos") { SectionTitle("Your repositories", "Or paste any repo link (index.min.json)", "Add" to { addRepo = true }) }
            if (repos.isEmpty()) note("norepo", "No repositories yet. Pick one from Discover above, or paste a link you trust.")
            items(repos, key = { "r" + it.url }) { r ->
                HubCard(r.name.ifBlank { r.url }, r.url) {
                    IconButton(onClick = { scope.launch { TachiyomiExtensions.removeRepo(context, r.url); available = available.filterNot { it.repo.url == r.url } } }) {
                        Icon(Icons.Default.Delete, "Remove", tint = TextSecondary)
                    }
                }
            }
            item("installed") { SectionTitle("Installed", "${installed.size} extension${if (installed.size == 1) "" else "s"}") }
            if (installed.isEmpty()) note("noinst", "Nothing installed yet.")
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
            if (repos.isNotEmpty()) {
                item("available") {
                    SectionTitle("Available", "${shown.size} in your repositories", "Refresh" to { scope.launch { refresh() } })
                    OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Filter by name or language (en, ja…)") }, modifier = Modifier.fillMaxWidth().testTag("hub_filter"))
                }
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
    }
    if (addRepo) UrlDialog(
        title = "Add ${kind.label} repository", hint = "Repository link",
        examples = "Works with GitHub repo links, raw links or the index.min.json URL.",
        onDismiss = { addRepo = false },
    ) { url ->
        addRepo = false
        addRepoUrl(url)
    }
    trustAsk?.let { e ->
        TsukiDialog(
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
private fun StremioTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val addons by StremioAddons.addons.collectAsState()
    var add by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun addUrl(url: String) = scope.launch {
        busy = true
        StremioAddons.add(context, url)
            .onSuccess { AppMessages.success(context, "${it.name} added"); afterChange() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
        busy = false
    }
    Column(Modifier.fillMaxSize()) {
        if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_stremio"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("about") {
                Text("Stremio add-ons give catalogs (movies, series, channels, sports, anime) and streams. Pick one from Discover or paste any add-on's manifest link. HTTP streams and subtitles are supported; torrent-only streams are skipped.",
                    color = TextSecondary, fontSize = 13.sp)
            }
            item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.stremio, startExpanded = addons.isEmpty()) }
            item("market") {
                val have = addons.map { it.manifestUrl }.toSet()
                AddonMarket(isAdded = { StremioAddons.normalize(it) in have }, startExpanded = addons.isEmpty(), onAdd = { addUrl(it) })
            }
            item("head") { SectionTitle("Your add-ons", "${addons.size} added · or paste a manifest link", "Add" to { add = true }) }
            if (addons.none { it.manifestUrl == StremioAddons.CINEMETA }) item("cinemeta") {
                HubCard("Cinemeta", "Official Stremio catalog of movies and series (metadata only). Good base for other stream add-ons.", badge = "Suggested") {
                    TextButton(onClick = { addUrl(StremioAddons.CINEMETA) }) { Text("Add", color = SpotifyGreen) }
                }
            }
            items(addons, key = { it.manifestUrl }) { a ->
                val res = a.resources.joinToString(" · ") { it.name }
                HubCard(a.name, "v${a.version} · ${a.types.joinToString()} · $res", badge = if (a.catalogs.isNotEmpty()) "${a.catalogs.size} catalogs" else null) {
                    IconButton(onClick = { StremioAddons.remove(context, a.manifestUrl); afterChange() }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                }
            }
        }
    }
    if (add) UrlDialog("Add Stremio add-on", "Manifest link", "Example: https://…/manifest.json (stremio:// links work too)", { add = false }) { add = false; addUrl(it) }
}

// endregion

// region JavaScript

@Composable
private fun JsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by JsSources.state.collectAsState()
    val adult by OnlineSettings.adult.collectAsState()
    var available by remember { mutableStateOf<List<JsSources.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    suspend fun refresh() { loading = true; available = runCatching { JsSources.available(context) }.getOrDefault(available); loading = false }
    LaunchedEffect(state.repos.size) { if (state.repos.isNotEmpty()) refresh() }
    fun addRepoUrl(url: String) = scope.launch {
        loading = true
        JsSources.addRepo(context, url)
            .onSuccess { AppMessages.success(context, "Repository added · $it sources"); refresh() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
        loading = false
    }
    val installedIds = state.installed.associateBy { it.id }
    val shown = available.filter { it.isJs && (adult || !it.nsfw) && (query.isBlank() || it.name.contains(query, true) || it.lang.equals(query, true)) }.sortedBy { it.name.lowercase() }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_js"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("about") {
                Text("JavaScript sources in the Mangayomi format run in a sandboxed QuickJS engine (no file or app access). Manga sources show in the Manga section; anime and video sources in Videos → Online.",
                    color = TextSecondary, fontSize = 13.sp)
            }
            item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.javascript, startExpanded = state.repos.isEmpty()) }
            item("market") {
                val have = state.repos.toSet()
                RepoMarket(
                    kind = dev.asfakul.yumeiro.sources.SourceMarket.Kind.MANGAYOMI, label = "Mangayomi",
                    isAdded = { runCatching { JsSources.normalizeRepo(it) in have }.getOrDefault(false) },
                    startExpanded = state.repos.isEmpty(),
                    onAdd = { addRepoUrl(it) },
                )
            }
            item("repos") { SectionTitle("Your repositories", "Or paste any index.json link", "Add" to { add = true }) }
            if (state.repos.isEmpty()) note("norepo", "No repositories yet. Pick one from Discover above, or paste a link you trust.")
            items(state.repos, key = { "r$it" }) { r ->
                HubCard(r.substringAfter("://").substringBefore("/"), r) {
                    IconButton(onClick = { JsSources.removeRepo(context, r) }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                }
            }
            item("inst") { SectionTitle("Installed", "${state.installed.size} sources") }
            items(state.installed, key = { "i" + it.id }) { e ->
                HubCard(e.name, "${e.kindLabel} · ${e.lang.uppercase()} · v${e.version}") {
                    IconButton(onClick = { JsSources.uninstall(context, e.id); afterChange() }) { Icon(Icons.Default.Delete, "Uninstall", tint = TextSecondary) }
                }
            }
            if (state.repos.isNotEmpty()) {
                item("avail") {
                    SectionTitle("Available", "${shown.size} JavaScript sources", "Refresh" to { scope.launch { refresh() } })
                    OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Filter by name or language") }, modifier = Modifier.fillMaxWidth())
                }
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
    }
    if (add) UrlDialog("Add JavaScript repository", "index.json link", "Mangayomi-format repositories. Only JavaScript sources are used.", { add = false }) { url ->
        add = false
        addRepoUrl(url)
    }
}

// endregion

// region Home servers

@Composable
private fun ServersTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by HomeServers.servers.collectAsState()
    var form by remember { mutableStateOf<HomeServers.Kind?>(null) }
    var found by remember { mutableStateOf<List<DlnaTree.Companion.Found>?>(null) }
    var scanning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (scanning) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_servers"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("about") {
                Text("Play your own media from Jellyfin, Emby, Plex, DLNA/UPnP, WebDAV or SMB shares. Yumeiro talks to your server directly; passwords and tokens are encrypted with the Android Keystore.",
                    color = TextSecondary, fontSize = 13.sp)
            }
            item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.servers, startExpanded = servers.isEmpty()) }
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
                    IconButton(onClick = { HomeServers.remove(context, s.id); afterChange() }) { Icon(Icons.Default.Delete, "Remove", tint = TextSecondary) }
                }
            }
            note("tls", "Servers with self-signed HTTPS certificates aren't supported yet; use plain HTTP on your home network or a real certificate.")
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

    fun save(secret: String) = scope.launch {
        busy = true
        HomeServers.add(context, kind, name, url, user, secret)
            .onSuccess { AppMessages.success(context, "${it.name} connected"); onSaved() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
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
                if (busy) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url.isNotBlank(), onClick = {
                if (kind != HomeServers.Kind.PLEX) save(pass)
                else scope.launch {
                    busy = true
                    runCatching {
                        val pin = PlexClient.createPin(context)
                        plexCode = pin.code
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pin.authUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        PlexClient.awaitToken(context, pin)
                    }.onSuccess { token -> busy = false; save(token) }
                        .onFailure { busy = false; plexCode = null; AppMessages.error(context, OnlineErrors.friendly(it)) }
                }
            }) { Text(if (kind == HomeServers.Kind.PLEX) "Sign in with Plex" else "Connect") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

// endregion
