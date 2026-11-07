/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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

/** Videos → Online → Extensions: installed extensions, their updates and the user's repositories. */
@Composable
fun ExtensionsScreen(onBack: () -> Unit, onOpenMarket: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }

    var available by remember { mutableStateOf<List<RemoteExtension>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<InstalledExtension?>(null) }
    var confirmRepo by remember { mutableStateOf<ExtensionManager.RepoRef?>(null) }
    var working by remember { mutableStateOf(false) }

    suspend fun check(force: Boolean) {
        checking = true
        available = runCatching { ExtensionSources.all(context, force).items }.getOrDefault(available)
        checking = false
    }
    LaunchedEffect(engine.ready, engine.repos.size) { if (engine.ready) check(false) }
    val updates = remember(available, engine.installed) { ExtensionManager.updatesFrom(available) }

    fun afterChange() { OnlineRepository.clearCaches() }

    fun update(ext: InstalledExtension, remote: RemoteExtension): kotlinx.coroutines.Job = scope.launch {
        runCatching { ExtensionManager.install(context, remote) }
            .onSuccess { AppMessages.success(context, "${it.name} updated to v${it.version}"); afterChange() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { update(ext, remote) } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            working = true
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            runCatching { ExtensionManager.installFromFile(context, uri, name) }
                .onSuccess { AppMessages.success(context, "${it.name} added"); afterChange() }
                .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
            working = false
        }
    }

    Column(Modifier.fillMaxSize().testTag("extensions_screen")) {
        SubPageHeader(
            title = "Extensions", onBack = onBack,
            subtitle = if (engine.ready) "${engine.installed.size} installed · ${engine.enabledCount} active" else "Loading…",
        ) {
            IconButton(onClick = onOpenMarket, modifier = Modifier.testTag("extensions_market")) { Icon(Icons.Default.Storefront, "Market", tint = TextPrimary) }
            IconButton(onClick = { showAddMenu = true }, modifier = Modifier.testTag("extensions_add")) {
                Icon(Icons.Default.Add, "Add", tint = TextPrimary)
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("Add repository") }, leadingIcon = { Icon(Icons.Default.Storefront, null) },
                        onClick = { showAddMenu = false; showRepoDialog = true }, modifier = Modifier.testTag("extensions_add_repo"))
                    DropdownMenuItem(text = { Text("Install from file (.cs3)") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        onClick = { showAddMenu = false; picker.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("extensions_add_file"))
                    DropdownMenuItem(text = { Text("Install from link") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                        onClick = { showAddMenu = false; showLinkDialog = true }, modifier = Modifier.testTag("extensions_add_link"))
                }
            }
        }
        if (working) androidx.compose.material3.LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())

        if (!engine.ready) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonRow(trailing = true) } }
            return@Column
        }
        PullToRefreshBox(isRefreshing = checking, onRefresh = { scope.launch { check(true) } }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("extensions_list"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (updates.isNotEmpty()) item("updates") {
                    Surface(shape = RoundedCornerShape(18.dp), color = SpotifyGreen.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth().testTag("extensions_updates")) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = SpotifyGreen)
                            Spacer(Modifier.width(12.dp))
                            Text("${updates.size} update${if (updates.size == 1) "" else "s"} available", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Button(onClick = { updates.forEach { (e, r) -> update(e, r) } }, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = RoundedCornerShape(20.dp), modifier = Modifier.testTag("extensions_update_all")) {
                                Text("Update all")
                            }
                        }
                    }
                }
                if (engine.installed.isEmpty()) item("empty") {
                    StateMessage(Icons.Default.Extension, "No extensions installed", "Extensions add online sources: home rows, search, details and streams.",
                        primary = "Browse market" to onOpenMarket, secondary = "Add a repository" to { showRepoDialog = true }, testTag = "extensions_empty")
                }
                items(engine.installed.sortedBy { it.name.lowercase() }, key = { it.key }) { ext ->
                    val info = engine.info(ext.key)
                    val upd = updates.firstOrNull { it.first.key == ext.key }?.second
                    InstalledCard(
                        ext = ext, info = info, busy = ext.key in engine.busy, update = upd,
                        onToggle = { on -> scope.launch { ExtensionManager.setEnabled(context, ext.key, on); afterChange()
                            ExtensionManager.state.value.info(ext.key).error?.let { AppMessages.error(context, "${ext.name}: $it") } } },
                        onAuto = { on -> scope.launch { ExtensionManager.setAutoUpdate(context, ext.key, on) } },
                        onUpdate = { upd?.let { update(ext, it) } },
                        onDelete = { confirmDelete = ext },
                    )
                }
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
                    Surface(shape = RoundedCornerShape(16.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("extensions_repo")) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(repo.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(repo.url, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { confirmRepo = repo }) { Icon(Icons.Default.Delete, "Remove repository", tint = TextSecondary) }
                        }
                    }
                }
                item("note") {
                    Text(
                        "Extensions run code from their authors. Only install ones you trust, and use them for content you're allowed to watch. Protected (DRM) and torrent sources are not played.",
                        color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp),
                    )
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
                .onSuccess { AppMessages.success(context, "Added ${it.name}"); done(null); showRepoDialog = false; check(true); onOpenMarket() }
                .onFailure { done(OnlineErrors.friendly(it)) }
        }
    }
    if (showLinkDialog) InputDialog(
        title = "Install from link", label = "Extension link", hint = "https://…/Extension.cs3",
        confirm = "Install", onDismiss = { showLinkDialog = false },
    ) { text, done ->
        scope.launch {
            runCatching { ExtensionManager.installFromUrl(context, text) }
                .onSuccess { AppMessages.success(context, "${it.name} installed"); done(null); showLinkDialog = false; afterChange() }
                .onFailure { done(OnlineErrors.friendly(it)) }
        }
    }
    confirmDelete?.let { ext ->
        AlertDialog(
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
    confirmRepo?.let { repo ->
        AlertDialog(
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
) {
    var menu by remember { mutableStateOf(false) }
    var editUrl by remember { mutableStateOf<com.lagradost.cloudstream3.MainAPI?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val plugin = remember(info.loaded, ext.key) { ExtensionManager.pluginOf(ext.key) as? com.lagradost.cloudstream3.plugins.Plugin }
    val apis = remember(info.loaded, info.providers) { ExtensionManager.providers().filter { it.name in info.providers } }
    fun openSettings() {
        val open = plugin?.openSettings ?: return
        val host = com.lagradost.cloudstream3.CommonActivity.activity ?: context
        runCatching { open(host) }.onFailure { AppMessages.error(context, "${ext.name} settings couldn't open: ${it.message ?: it.javaClass.simpleName}") }
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
    Surface(shape = RoundedCornerShape(20.dp), color = GlassFill, modifier = Modifier.fillMaxWidth().testTag("extension_${ext.internalName}")) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionIcon(ext.iconUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ext.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("v${ext.version} · ${ext.repoName}", color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                else Switch(
                    checked = ext.enabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = SpotifyGreen),
                    modifier = Modifier.testTag("extension_toggle_${ext.internalName}"),
                )
                if (plugin?.openSettings != null && ext.enabled) IconButton(onClick = { openSettings() }, modifier = Modifier.testTag("extension_settings_${ext.internalName}")) {
                    Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (plugin?.openSettings != null && ext.enabled) DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menu = false; openSettings() })
                        apis.forEach { api ->
                            DropdownMenuItem(text = { Text(if (apis.size > 1) "Website address · ${api.name}" else "Website address") }, leadingIcon = { Icon(Icons.Default.Language, null) }, onClick = { menu = false; editUrl = api })
                        }
                        if (update != null) DropdownMenuItem(text = { Text("Update to v${update.version}") }, leadingIcon = { Icon(Icons.Default.SystemUpdate, null) }, onClick = { menu = false; onUpdate() })
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
                    info.loaded -> Pill("${info.providers.size} source${if (info.providers.size == 1) "" else "s"}")
                }
                if (!ext.manual) Pill(if (ext.autoUpdate) "Auto-update" else "Manual updates", TextSecondary)
                if (update != null) Pill("Update v${update.version}", WarnColor)
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

/** Text input dialog that stays open and shows the error until the action succeeds. */
@Composable
internal fun InputDialog(
    title: String, label: String, hint: String, confirm: String, onDismiss: () -> Unit,
    onConfirm: (text: String, done: (error: String?) -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, label = { Text(label) },
                    placeholder = { Text(hint, fontSize = 12.sp) }, singleLine = true, isError = error != null, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("input_dialog_field"),
                )
                error?.let { Text(it, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) androidx.compose.material3.LinearProgressIndicator(color = SpotifyGreen, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = {
                busy = true
                onConfirm(text.trim()) { err -> busy = false; error = err }
            }, modifier = Modifier.testTag("input_dialog_confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
