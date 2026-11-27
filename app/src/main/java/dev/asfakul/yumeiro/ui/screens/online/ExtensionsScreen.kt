/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.ui.components.TsukiDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Checklist
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.asfakul.yumeiro.online.ProviderOverrides
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.InstalledExtension
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
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
import dev.asfakul.yumeiro.ui.theme.yuRound

/** Videos → Online → Extensions: installed extensions, their updates and the user's repositories. */
@Composable
fun ExtensionsScreen(onBack: () -> Unit, onOpenMarket: () -> Unit, onOpenSources: () -> Unit = {}, onOpenScriptRepos: () -> Unit = onOpenSources) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    // OWN-5: Vega / Nuvio / SkyStream script sources were only visible under Sources → Script repos.
    val scripts by dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry.state.collectAsState()
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry.start(context.applicationContext) } }

    var available by remember { mutableStateOf<List<RemoteExtension>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showAnyDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<InstalledExtension?>(null) }
    var confirmRepo by remember { mutableStateOf<ExtensionManager.RepoRef?>(null) }
    var working by remember { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var status by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    var showFilters by remember { mutableStateOf(false) }
    // R16 Group 2: select all / individual selection and bulk actions.
    var selecting by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmBulkRemove by remember { mutableStateOf(false) }
    var bulkBusy by remember { mutableStateOf(false) }
    fun endSelection() { selecting = false; selected = emptySet() }
    // Back while selecting ends selection. Routed through the header's own BackHandler: a separate
    // handler declared here lost to it (composed later, so it wins) and Back left the screen (device run 43701058752).
    LaunchedEffect(engine.installed) { selected = dev.asfakul.yumeiro.online.ExtensionBulk.prune(selected, engine.installed.map { it.key }) }

    suspend fun check(force: Boolean) {
        checking = true
        available = runCatching { ExtensionSources.all(context, force).items }.getOrDefault(available)
        checking = false
    }
    LaunchedEffect(engine.ready, engine.repos.size) { if (engine.ready) check(false) }
    val updates = remember(available, engine.installed) { ExtensionManager.updatesFrom(available) }

    fun afterChange() { OnlineRepository.clearCaches() }

    fun update(ext: InstalledExtension, remote: RemoteExtension, mode: dev.asfakul.yumeiro.online.ExtensionReview.Mode = dev.asfakul.yumeiro.online.ExtensionReview.Mode.ALWAYS): kotlinx.coroutines.Job = scope.launch {
        runCatching { if (ext.manual) ExtensionManager.applyUpdate(context, ext, remote) else ExtensionManager.install(context, remote, mode) }
            .onSuccess { AppMessages.success(context, "${it.name} updated to v${it.version}"); afterChange() }
            .onFailure { if (it !is dev.asfakul.yumeiro.online.ExtensionReview.Declined) AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { update(ext, remote) } }
    }
    /** One at a time, one summary message (R16 Group 2: was one parallel job and one toast per extension). */
    suspend fun bulkUpdate(list: List<Pair<InstalledExtension, RemoteExtension>>) {
        if (list.isEmpty()) return
        bulkBusy = true
        val o = dev.asfakul.yumeiro.online.ExtensionBulk.runEach(list, { it.first.name }) { (e, r) ->
            if (e.manual) ExtensionManager.applyUpdate(context, e, r) else ExtensionManager.install(context, r, dev.asfakul.yumeiro.online.ExtensionReview.Mode.IF_RISKY)
        }
        bulkBusy = false
        afterChange()
        val msg = dev.asfakul.yumeiro.online.ExtensionBulk.summary("Updated", o)
        if (o.failed.isEmpty()) AppMessages.success(context, msg) else AppMessages.error(context, msg)
    }
    suspend fun bulkSet(keys: Set<String>, on: Boolean) {
        val list = engine.installed.filter { it.key in keys && it.enabled != on }
        bulkBusy = true
        val o = dev.asfakul.yumeiro.online.ExtensionBulk.runEach(list, { it.name }) { ExtensionManager.setEnabled(context, it.key, on) }
        bulkBusy = false
        afterChange()
        val broken = if (on) list.filter { ExtensionManager.state.value.info(it.key).error != null }.map { it.name } else emptyList()
        val msg = dev.asfakul.yumeiro.online.ExtensionBulk.summary(if (on) "Turned on" else "Turned off", o.copy(ok = o.ok - broken.size, failed = o.failed + broken))
        if (o.failed.isEmpty() && broken.isEmpty()) AppMessages.success(context, if (list.isEmpty()) "Already ${if (on) "on" else "off"}" else msg) else AppMessages.error(context, msg)
        endSelection()
    }
    suspend fun bulkRemove(keys: Set<String>) {
        val list = engine.installed.filter { it.key in keys }
        bulkBusy = true
        val o = dev.asfakul.yumeiro.online.ExtensionBulk.runEach(list, { it.name }) { ExtensionManager.uninstall(context, it.key) }
        bulkBusy = false
        afterChange()
        AppMessages.show(context, dev.asfakul.yumeiro.online.ExtensionBulk.summary("Removed", o))
        endSelection()
    }
    var checkingKey by remember { mutableStateOf<String?>(null) }
    /** Fresh check against the extension's own repository (or its download link). */
    fun checkOne(ext: InstalledExtension) = scope.launch {
        checkingKey = ext.key
        val result = ExtensionManager.checkUpdate(context, ext)
        checkingKey = null
        when (result) {
            is ExtensionManager.UpdateCheck.UpToDate -> AppMessages.success(context, "${ext.name} is up to date (v${ext.version})")
            is ExtensionManager.UpdateCheck.Available -> update(ext, result.remote)
            is ExtensionManager.UpdateCheck.Unavailable -> AppMessages.error(context, "${ext.name}: ${result.reason}")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            working = true
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            dev.asfakul.yumeiro.sources.UniversalAdd.addFile(context, uri, name)
                .onSuccess { AppMessages.success(context, it.message); afterChange() }
                .onFailure { if (it !is dev.asfakul.yumeiro.online.ExtensionReview.Declined) AppMessages.error(context, OnlineErrors.friendly(it)) }
            working = false
        }
    }

    // Rows on screen (after search and filter); "Select all" acts on these.
    val shown = remember(engine.installed, engine.runtime, query, status, updates) {
        engine.installed.sortedBy { it.name.lowercase() }.filter { ext ->
                val q = query.trim()
                (q.isEmpty() || ext.name.contains(q, true) || ext.internalName.contains(q, true) || ext.repoName.orEmpty().contains(q, true)) &&
                    when (status) {
                        "on" -> ext.enabled
                        "off" -> !ext.enabled
                        "problems" -> engine.info(ext.key).error != null
                        "updates" -> updates.any { it.first.key == ext.key }
                        else -> true
                    }
            }
    }
    val shownKeys = remember(shown) { shown.map { it.key } }
    Column(Modifier.fillMaxSize().testTag("extensions_screen")) {
        SubPageHeader(
            title = "Extensions", onBack = { if (selecting) endSelection() else onBack() },
            subtitle = if (engine.ready) "${engine.installed.size} installed · ${engine.enabledCount} active" else "Loading…",
        ) {
            if (engine.installed.size > 1) {
                dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen || query.isNotEmpty(), { if (searchOpen || query.isNotEmpty()) { searchOpen = false; query = "" } else searchOpen = true }, "extensions_search_toggle", "Search installed extensions")
                dev.asfakul.yumeiro.ui.components.FilterIconButton(if (status != "all") 1 else 0, { showFilters = true }, "extensions_filter")
            }
            if (engine.installed.isNotEmpty()) IconButton(onClick = { if (selecting) endSelection() else selecting = true }, modifier = Modifier.testTag("extensions_select")) {
                Icon(Icons.Default.Checklist, if (selecting) "Stop selecting" else "Select extensions", tint = if (selecting) SpotifyGreen else TextPrimary)
            }
            IconButton(onClick = onOpenSources, modifier = Modifier.testTag("extensions_sources_hub")) { Icon(Icons.Default.Language, "More sources", tint = TextPrimary) }
            IconButton(onClick = onOpenMarket, modifier = Modifier.testTag("extensions_market")) { Icon(Icons.Default.Storefront, "Market", tint = TextPrimary) }
            IconButton(onClick = { showAddMenu = true }, modifier = Modifier.testTag("extensions_add")) {
                Icon(Icons.Default.Add, "Add", tint = TextPrimary)
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("Add anything (auto-detect)") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { showAddMenu = false; showAnyDialog = true }, modifier = Modifier.testTag("extensions_add_any"))
                    DropdownMenuItem(text = { Text("Add repository") }, leadingIcon = { Icon(Icons.Default.Storefront, null) },
                        onClick = { showAddMenu = false; showRepoDialog = true }, modifier = Modifier.testTag("extensions_add_repo"))
                    DropdownMenuItem(text = { Text("Install from file (.cs3 or .apk)") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        onClick = { showAddMenu = false; picker.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("extensions_add_file"))
                    DropdownMenuItem(text = { Text("Install from link") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                        onClick = { showAddMenu = false; showLinkDialog = true }, modifier = Modifier.testTag("extensions_add_link"))
                }
            }
        }
        dev.asfakul.yumeiro.ui.components.HeaderSearchField(searchOpen || query.isNotEmpty(), query, { query = it }, "Search installed extensions", "extensions_search")
        if (selecting) SelectionBar(
            count = selected.size,
            allShownSelected = shownKeys.isNotEmpty() && selected.containsAll(shownKeys),
            canUpdate = updates.any { it.first.key in selected },
            busy = bulkBusy,
            onSelectAll = { selected = dev.asfakul.yumeiro.online.ExtensionBulk.toggleAll(selected, shownKeys) },
            onOn = { scope.launch { bulkSet(selected, true) } },
            onOff = { scope.launch { bulkSet(selected, false) } },
            onUpdate = { val keys = selected; scope.launch { bulkUpdate(updates.filter { it.first.key in keys }); endSelection() } },
            onRemove = { confirmBulkRemove = true },
            onCancel = { endSelection() },
        )
        if (working || bulkBusy) androidx.compose.material3.LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())

        if (!engine.ready) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonRow(trailing = true) } }
            return@Column
        }
        PullToRefreshBox(isRefreshing = checking, onRefresh = { scope.launch { check(true) } }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("extensions_list"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("extensions"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (updates.isNotEmpty()) item("updates") {
                    Surface(shape = yuRound(18), color = SpotifyGreen.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth().testTag("extensions_updates")) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Text("${updates.size} update${if (updates.size == 1) "" else "s"} available", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Button(onClick = { scope.launch { bulkUpdate(updates) } }, enabled = !bulkBusy, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = yuRound(20), modifier = Modifier.testTag("extensions_update_all")) {
                                Text("Update all")
                            }
                        }
                    }
                }
                item("more_sources") {
                    Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("extensions_more_sources"),
                        onClick = onOpenSources) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, null, tint = SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("More sources", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Aniyomi, Mihon manga, Stremio add-ons, JavaScript sources and home servers", color = TextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (scripts.repos.isNotEmpty() || scripts.installed.isNotEmpty()) item("script_sources") {
                    // OWN-5: installed script sources (Vega, Nuvio, SkyStream) with a direct Manage shortcut.
                    val names = scripts.installed.map { it.name }
                    val kinds = (scripts.installed.map { it.ecosystem.label } + scripts.repos.map { it.ecosystem.label }).distinct().joinToString(", ")
                    Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("extensions_script_sources"),
                        onClick = onOpenScriptRepos) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Extension, null, tint = SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$kinds sources: ${names.size} installed", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(if (names.isEmpty()) "${scripts.repos.size} repositor${if (scripts.repos.size == 1) "y" else "ies"} added. Tap to install providers"
                                    else names.take(4).joinToString(", ") + if (names.size > 4) " and ${names.size - 4} more" else "",
                                    color = TextTertiary, fontSize = 12.sp, maxLines = 2)
                            }
                            Text("Manage", color = SpotifyGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("extensions_script_manage"))
                        }
                    }
                }
                if (engine.installed.isEmpty()) item("empty") {
                    StateMessage(Icons.Default.Extension, "No extensions installed", "Extensions add more online sources: home rows, search, details and streams. Yumeiro's own built-in sources are below.",
                        primary = "Browse market" to onOpenMarket, secondary = "Add a repository" to { showRepoDialog = true }, testTag = "extensions_empty")
                }
                if (engine.installed.isNotEmpty() && shown.isEmpty()) item("no_match") {
                    StateMessage(Icons.Default.Extension, "No extension matches", if (query.isNotBlank()) "Nothing installed matches \"${query.trim()}\"." else "No extension is in this state.",
                        primary = "Clear filters" to { query = ""; status = "all"; searchOpen = false }, testTag = "extensions_no_match")
                }
                items(shown, key = { it.key }) { ext ->
                    val info = engine.info(ext.key)
                    val upd = updates.firstOrNull { it.first.key == ext.key }?.second
                    InstalledCard(
                        ext = ext, info = info, busy = ext.key in engine.busy || checkingKey == ext.key, update = upd,
                        onCheckUpdate = { checkOne(ext) },
                        onToggle = { on -> scope.launch { ExtensionManager.setEnabled(context, ext.key, on); afterChange()
                            ExtensionManager.state.value.info(ext.key).error?.let { AppMessages.error(context, "${ext.name}: $it") } } },
                        onAuto = { on -> scope.launch { ExtensionManager.setAutoUpdate(context, ext.key, on) } },
                        onUpdate = { upd?.let { update(ext, it) } },
                        onDelete = { confirmDelete = ext },
                        selecting = selecting, isSelected = ext.key in selected,
                        onSelect = { selected = dev.asfakul.yumeiro.online.ExtensionBulk.toggle(selected, ext.key) },
                        onLongPress = { if (!selecting) { selecting = true; selected = setOf(ext.key) } },
                    )
                }
                // OWN-9: the official built-in sources, right after the installed ones (before: only in the market).
                item("official_header") {
                    Text("Official · built in", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp).testTag("extensions_official"))
                }
                if (!officialHub() && dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.OFFICIAL_CLASSICS)) item("builtin_classics") { ClassicsBuiltinCard() }
                item("builtin_drama_installed") { DramaBuiltinCard() }
                item("repos_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your repositories", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Extra CloudStream repositories shown in the market", color = TextTertiary, fontSize = 11.sp)
                        }
                        TextButton(onClick = { showRepoDialog = true }) { Text("Add", color = SpotifyGreen) }
                    }
                }
                if (engine.repos.isEmpty()) item("repos_empty") {
                    Text("No repositories added. The market already includes verified ones.", color = TextTertiary, fontSize = 12.sp)
                }
                items(engine.repos, key = { "repo_" + it.url }) { repo ->
                    Surface(shape = yuRound(16), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("extensions_repo")) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(repo.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(repo.url, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { confirmRepo = repo }) { Icon(Icons.Default.Delete, "Remove repository", tint = TextSecondary) }
                        }
                    }
                }
                item("popups") { ExtensionPopupsCard() }
                item("security_checks") { SecurityChecksCard() }
                item("guide") { dev.asfakul.yumeiro.ui.screens.settings.GuideCard(dev.asfakul.yumeiro.ui.screens.settings.SetupGuides.cloudstream) }
                item("note") {
                    Text(
                        "Extensions run code from their authors. Only install ones you trust, and use them for content you're allowed to watch. Protected (DRM) and torrent sources are not played.",
                        color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (showFilters) dev.asfakul.yumeiro.ui.components.FilterSheet(
        onDismiss = { showFilters = false }, onReset = if (status != "all") ({ status = "all" }) else null, resultCount = null, tag = "extensions_filter_sheet",
    ) {
        dev.asfakul.yumeiro.ui.components.FilterGroup("Show") {
            listOf("all" to "All", "on" to "On", "off" to "Off", "problems" to "Needs attention", "updates" to "Has update").forEach { (k, l) ->
                OnlineChip(l, status == k, { status = k }, Modifier.testTag("extensions_status_$k"))
            }
        }
    }
    if (showAnyDialog) dev.asfakul.yumeiro.ui.screens.sources.UniversalAddDialog(onDismiss = { showAnyDialog = false; afterChange() },
        onServer = { AppMessages.show(context, it.message); onOpenSources() })
    if (showRepoDialog) InputDialog(
        title = "Add repository", label = "Repository link", hint = "https://…/repo.json or a GitHub repo",
        confirm = "Add", onDismiss = { showRepoDialog = false },
    ) { text, done ->
        scope.launch {
            // OWN-4: a Vega, SkyStream, Stremio, Aniyomi/Mihon or single-extension link works here too.
            dev.asfakul.yumeiro.sources.UniversalAdd.addFromRepoDialog(context, text)
                .onSuccess { (repo, other) ->
                    done(null); showRepoDialog = false
                    if (repo != null) { AppMessages.success(context, "Added ${repo.name}"); check(true); onOpenMarket() }
                    else { AppMessages.success(context, other?.message ?: "Added"); afterChange() }
                }
                .onFailure { if (it is dev.asfakul.yumeiro.online.ExtensionReview.Declined) { done(null); showRepoDialog = false } else done(OnlineErrors.friendly(it)) }
        }
    }
    if (showLinkDialog) InputDialog(
        title = "Install from link", label = "Extension link", hint = "https://…/Extension.cs3 (any format is detected)",
        confirm = "Install", onDismiss = { showLinkDialog = false },
    ) { text, done ->
        scope.launch {
            dev.asfakul.yumeiro.sources.UniversalAdd.add(context, text)
                .onSuccess { AppMessages.success(context, it.message); done(null); showLinkDialog = false; afterChange() }
                .onFailure { if (it is dev.asfakul.yumeiro.online.ExtensionReview.Declined) { done(null); showLinkDialog = false } else done(OnlineErrors.friendly(it)) }
        }
    }
    confirmDelete?.let { ext ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${ext.name}?") },
            text = { Text("Its sources disappear from Online. You can install it again from the market.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        ExtensionManager.uninstall(context, ext.key); afterChange()
                        AppMessages.show(context, "${ext.name} removed")
                    }
                }, modifier = Modifier.testTag("extensions_confirm_delete")) { Text("Remove", color = ErrorColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    if (confirmBulkRemove) TsukiDialog(
        tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
        onDismissRequest = { confirmBulkRemove = false },
        title = { Text("Remove ${selected.size} extension${if (selected.size == 1) "" else "s"}?") },
        text = { Text("Their sources disappear from Online. You can install them again from the market.") },
        confirmButton = {
            TextButton(onClick = { confirmBulkRemove = false; val keys = selected; scope.launch { bulkRemove(keys) } },
                modifier = Modifier.testTag("extensions_bulk_confirm_remove")) { Text("Remove", color = ErrorColor) }
        },
        dismissButton = { TextButton(onClick = { confirmBulkRemove = false }) { Text("Cancel") } },
    )
    confirmRepo?.let { repo ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.DANGER,
            onDismissRequest = { confirmRepo = null },
            title = { Text("Remove repository?") },
            text = { Text("${repo.name} won't appear in the market anymore. Extensions you installed from it stay installed.") },
            confirmButton = { TextButton(onClick = { confirmRepo = null; scope.launch { ExtensionManager.removeRepository(context, repo.url); check(false) } }) { Text("Remove", color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { confirmRepo = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InstalledCard(
    ext: InstalledExtension, info: ExtensionManager.LoadInfo, busy: Boolean, update: RemoteExtension?,
    onToggle: (Boolean) -> Unit, onAuto: (Boolean) -> Unit, onUpdate: () -> Unit, onDelete: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    selecting: Boolean = false, isSelected: Boolean = false, onSelect: () -> Unit = {}, onLongPress: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var report by remember(ext.fileName) { mutableStateOf<dev.asfakul.yumeiro.online.ExtensionScanner.Report?>(null) }

    var editUrl by remember { mutableStateOf<com.lagradost.cloudstream3.MainAPI?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val plugin = remember(info.loaded, ext.key) { ExtensionManager.pluginOf(ext.key) as? com.lagradost.cloudstream3.plugins.Plugin }
    // F7: the extension's clone sites are listed with it (they share its file).
    val cloneTick by dev.asfakul.yumeiro.online.SiteClones.versionFlow.collectAsState()
    val apis = remember(info.loaded, info.providers, cloneTick) {
        ExtensionManager.providers().filter { it.name in info.providers || dev.asfakul.yumeiro.online.SiteClones.originOf(it.name) in info.providers }
    }
    var cloneOf by remember { mutableStateOf<com.lagradost.cloudstream3.MainAPI?>(null) }
    LaunchedEffect(ext.fileName) { report = ExtensionManager.reportFor(context, ext) }
    var signedIn by remember(ext.key) { mutableStateOf(apis.any { a -> siteHost(a.mainUrl)?.let { dev.asfakul.yumeiro.online.web.SiteLogins.has(context, it) } == true }) }
    fun signIn(api: com.lagradost.cloudstream3.MainAPI) {
        dev.asfakul.yumeiro.online.web.BrowserCheck.openSite(api.mainUrl.trimEnd('/') + "/", dev.asfakul.yumeiro.online.web.BrowserCheck.Purpose.LOGIN) { ok ->
            if (ok) { signedIn = true; OnlineRepository.clearCaches(); AppMessages.success(context, "${api.name} will use your sign-in") }
        }
    }
    fun signOut() {
        apis.forEach { a -> siteHost(a.mainUrl)?.let { dev.asfakul.yumeiro.online.web.SiteLogins.remove(context, it) } }
        signedIn = false; OnlineRepository.clearCaches(); AppMessages.show(context, "Signed out of ${ext.name}'s site")
    }
    report?.takeIf { showReport }?.let { rep ->
        TsukiDialog(
            tone = dev.asfakul.yumeiro.ui.components.DialogTone.WARNING,
            onDismissRequest = { showReport = false },
            title = { Text("Safety check · ${ext.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rep.summary, color = levelColor(rep.level), fontWeight = FontWeight.SemiBold)
                    rep.findings.forEach { f -> Text("• ${f.title} — ${f.detail}", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                    Text("Static check of the installed file: ${rep.classes} classes, ${rep.methods} methods read; nothing was run.", color = TextTertiary, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showReport = false }) { Text("OK") } },
        )
    }
    fun openSettings() {
        val open = plugin?.openSettings ?: return
        val host = com.lagradost.cloudstream3.CommonActivity.activity ?: context
        runCatching { open(host) }.onFailure { AppMessages.error(context, "${ext.name} settings couldn't open. ${dev.asfakul.yumeiro.online.OnlineErrors.friendly(it, "Try updating the extension.")}") }
    }
    editUrl?.let { api ->
        InputDialog(
            title = "Website address", label = api.name, hint = ProviderOverrides.mainUrl(api.name) ?: api.mainUrl, confirm = "Save",
            onDismiss = { editUrl = null },
        ) { text, done ->
            if (text.isNotBlank() && ProviderOverrides.normalize(text) == null) done("That isn't a web address")
            else { ProviderOverrides.setMainUrl(api, text); done(null); editUrl = null
                AppMessages.success(context, if (text.isBlank()) "${api.name} uses its default address" else "${api.name} now uses ${api.mainUrl}") }
        }
    }
    cloneOf?.let { api ->
        InputDialog(
            title = "Clone site", label = "Address of a site like ${api.name}", hint = "https://another-site.example", confirm = "Add clone",
            onDismiss = { cloneOf = null },
        ) { text, done ->
            dev.asfakul.yumeiro.online.SiteClones.add(context, api, text)
                .onSuccess { name -> done(null); cloneOf = null; AppMessages.success(context, "Added $name. It searches and opens like any source") }
                .onFailure { done(it.message ?: "Couldn't add that clone") }
        }
    }
    Surface(
        shape = yuRound(20), color = if (isSelected) SpotifyGreen.copy(alpha = 0.16f) else GlassFill,
        modifier = Modifier.fillMaxWidth().testTag("extension_${ext.internalName}")
            .semantics { if (selecting) selected = isSelected }
            .combinedClickable(onClick = { if (selecting) onSelect() }, onLongClick = onLongPress, onLongClickLabel = "Select"),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) androidx.compose.material3.Checkbox(
                    checked = isSelected, onCheckedChange = { onSelect() },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = SpotifyGreen, checkmarkColor = OnAccent),
                    modifier = Modifier.testTag("extension_select_${ext.internalName}"),
                )
                ExtensionIcon(ext.iconUrl)
                Spacer(Modifier.width(12.dp))
                // F-21: shared label — name + language chip, then "3 sources • CloudStream • Repo • v12".
                val rich = remember { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.EXTENSION_LABELS) }
                val label = remember(rich, ext.name, ext.language, ext.repoName, ext.version, info.loaded, info.providers.size) {
                    if (!rich) dev.asfakul.yumeiro.online.ExtensionLabels.Label(ext.name, null, "v${ext.version} · ${ext.repoName}")
                    else dev.asfakul.yumeiro.online.ExtensionLabels.build(
                        ext.name, ext.language, info.providers.size.takeIf { info.loaded }, "CloudStream", ext.repoName, listOf("v${ext.version}"),
                    )
                }
                ExtensionLabelText(label, titleColor = TextPrimary, lineColor = TextTertiary, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                else if (!selecting) Switch(
                    checked = ext.enabled, onCheckedChange = onToggle,
                    colors = dev.asfakul.yumeiro.ui.theme.yumeSwitchColors(),
                    modifier = Modifier.testTag("extension_toggle_${ext.internalName}"),
                )
                if (!selecting && plugin?.openSettings != null && ext.enabled) IconButton(onClick = { openSettings() }, modifier = Modifier.testTag("extension_settings_${ext.internalName}")) {
                    Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                }
                if (!selecting) IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (plugin?.openSettings != null && ext.enabled) DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menu = false; openSettings() })
                        apis.forEach { api ->
                            DropdownMenuItem(text = { Text(if (apis.size > 1) "Website address · ${api.name}" else "Website address") }, leadingIcon = { Icon(Icons.Default.Language, null) }, onClick = { menu = false; editUrl = api })
                        }
                        // F7: a copy of a source that reads a clone site; clones themselves can be removed here.
                        if (ext.enabled) apis.filter { api -> !dev.asfakul.yumeiro.online.SiteClones.isClone(api.name) }.forEach { api ->
                            DropdownMenuItem(text = { Text(if (apis.size > 1) "Clone site · ${api.name}" else "Clone site") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = { menu = false; cloneOf = api }, modifier = Modifier.testTag("extension_clone_${api.name}"))
                        }
                        apis.filter { api -> dev.asfakul.yumeiro.online.SiteClones.isClone(api.name) }.forEach { api ->
                            DropdownMenuItem(text = { Text("Remove clone · ${api.name}") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; dev.asfakul.yumeiro.online.SiteClones.remove(context, api.name); AppMessages.info(context, "Removed ${api.name}") },
                                modifier = Modifier.testTag("extension_clone_remove_${api.name}"))
                        }
                        if (update != null) DropdownMenuItem(text = { Text("Update to v${update.version}") }, leadingIcon = { Icon(Icons.Default.SystemUpdate, null) }, onClick = { menu = false; onUpdate() })
                        DropdownMenuItem(text = { Text("Check for update") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onCheckUpdate() })
                        if (ext.enabled) apis.forEach { api ->
                            DropdownMenuItem(text = { Text(if (apis.size > 1) "Sign in to site · ${api.name}" else "Sign in to site") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Login, null) }, onClick = { menu = false; signIn(api) })
                        }
                        if (signedIn) DropdownMenuItem(text = { Text("Sign out of site") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) }, onClick = { menu = false; signOut() })
                        if (report != null) DropdownMenuItem(text = { Text("Safety check") }, leadingIcon = { Icon(Icons.Default.Shield, null) }, onClick = { menu = false; showReport = true })
                        if (!ext.manual) DropdownMenuItem(
                            text = { Text(if (ext.autoUpdate) "Turn off auto-update" else "Turn on auto-update") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onAuto(!ext.autoUpdate) },
                        )
                        DropdownMenuItem(text = { Text("Remove", color = ErrorColor) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorColor) },
                            onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("extension_delete_${ext.internalName}"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    !ext.enabled -> Pill("Off", TextTertiary)
                    info.error != null -> Pill("Error", ErrorColor)
                    info.loaded -> Pill(if (dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.EXTENSION_LABELS)) "On" else "${info.providers.size} source${if (info.providers.size == 1) "" else "s"}")
                }
                if (!ext.manual) Pill(if (ext.autoUpdate) "Auto-update" else "Manual updates", TextSecondary)
                if (update != null) Pill("Update v${update.version}", WarnColor)
                report?.let { r -> androidx.compose.foundation.layout.Box(Modifier.clickable { showReport = true }) { Pill(levelLabel(r.level), levelColor(r.level)) } }
                if (signedIn) Pill("Signed in", SpotifyGreen)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (update != null) Button(
                    onClick = onUpdate, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    shape = yuRound(18), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("extension_update_${ext.internalName}"),
                ) { Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Update to v${update.version}", fontSize = 12.sp) }
                else androidx.compose.material3.OutlinedButton(
                    onClick = onCheckUpdate, enabled = !busy && !(ext.manual && !ext.sourceUrl.startsWith("http")),
                    shape = yuRound(18), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("extension_check_${ext.internalName}"),
                ) { Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Check for update", color = TextSecondary, fontSize = 12.sp) }
                if (ext.enabled && apis.isNotEmpty() && !signedIn) TextButton(onClick = { signIn(apis.first()) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(32.dp)) {
                    Text("Sign in to site", color = SpotifyGreen, fontSize = 12.sp)
                }
            }
            info.error?.takeIf { ext.enabled }?.let {
                Row(Modifier.padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = ErrorColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Couldn't start: $it", color = TextSecondary, fontSize = 12.sp)
                }
            }
            if (info.loaded && info.providers.isNotEmpty()) Text(info.providers.joinToString(", "), color = TextTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
        }
    }
}

/** Bar shown while selecting: count, select all (of what is shown) and the bulk actions. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(
    count: Int, allShownSelected: Boolean, canUpdate: Boolean, busy: Boolean,
    onSelectAll: () -> Unit, onOn: () -> Unit, onOff: () -> Unit, onUpdate: () -> Unit, onRemove: () -> Unit, onCancel: () -> Unit,
) {
    Surface(shape = yuRound(18), color = GlassFill, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("extensions_selection_bar")) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = allShownSelected, onCheckedChange = { onSelectAll() }, enabled = !busy,
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = SpotifyGreen, checkmarkColor = OnAccent),
                    modifier = Modifier.testTag("extensions_select_all"),
                )
                Text(if (count == 0) "Select all" else "$count selected", color = TextPrimary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).testTag("extensions_selected_count"))
                TextButton(onClick = onCancel, modifier = Modifier.testTag("extensions_bulk_cancel")) { Text("Done", color = TextSecondary) }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                val on = count > 0 && !busy
                TextButton(onClick = onOn, enabled = on, modifier = Modifier.testTag("extensions_bulk_on")) { Text("Turn on", color = if (on) SpotifyGreen else TextTertiary) }
                TextButton(onClick = onOff, enabled = on, modifier = Modifier.testTag("extensions_bulk_off")) { Text("Turn off", color = if (on) TextPrimary else TextTertiary) }
                if (canUpdate) TextButton(onClick = onUpdate, enabled = on, modifier = Modifier.testTag("extensions_bulk_update")) { Text("Update", color = if (on) WarnColor else TextTertiary) }
                TextButton(onClick = onRemove, enabled = on, modifier = Modifier.testTag("extensions_bulk_remove")) { Text("Remove", color = if (on) ErrorColor else TextTertiary) }
            }
        }
    }
}

/** Text input dialog that stays open and shows the error until the action succeeds. */
@Composable
internal fun InputDialog(
    title: String, label: String, hint: String, confirm: String, onDismiss: () -> Unit,
    onConfirm: (text: String, done: (error: String?) -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    TsukiDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, label = { Text(label) },
                    placeholder = { Text(hint, fontSize = 12.sp) }, singleLine = true, isError = error != null, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("input_dialog_field"),
                )
                error?.let { Text(it, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("input_dialog_error")) }
                if (busy) androidx.compose.material3.LinearProgressIndicator(color = SpotifyGreen, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                onConfirm(text.trim()) { err -> busy = false; error = err }
            }, modifier = Modifier.testTag("input_dialog_confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("input_dialog_cancel")) { Text("Cancel") } },
    )
}

/** Host of an extension's site, for its sign-in. */
internal fun siteHost(url: String): String? = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")
