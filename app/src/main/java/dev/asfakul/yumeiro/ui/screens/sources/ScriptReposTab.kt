/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.PluginEntry
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ADR-047: Sources → Script repos. One list for every script ecosystem the registry runs (Vega in
 * this version); each source carries its ecosystem as a text badge. Nothing is suggested or built
 * in: the user pastes a repository they trust. Rows are the hub's [HubCard]s, so D-pad focus works
 * the same as in the other tabs; removal asks first, with Cancel focused on TV by the dialog.
 */
@Composable
internal fun ScriptReposTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by EcosystemRegistry.state.collectAsState()
    val problems by EcosystemRegistry.problems.collectAsState()
    var available by remember { mutableStateOf<List<Pair<EcosystemRegistry.Repo, PluginEntry>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<EcosystemRegistry.Repo?>(null) }
    suspend fun refresh() { loading = true; available = runCatching { EcosystemRegistry.available(context) }.getOrDefault(available); loading = false }
    LaunchedEffect(state.repos.size) { if (state.repos.isNotEmpty()) refresh() }
    fun addRepo(url: String): kotlinx.coroutines.Job = scope.launch {
        loading = true
        EcosystemRegistry.addRepo(context, url)
            .onSuccess { AppMessages.success(context, "${it.name} added · ${it.plugins.size} providers"); refresh() }
            .onFailure { AppMessages.error(context, OnlineErrors.friendly(it), "Retry") { addRepo(url) } }
        loading = false
    }
    val installed = state.installed.associateBy { it.repoUrl to it.id }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize().testTag("hub_scripts"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("hub_scripts"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("about") {
                Text("Vega provider repositories run in a sandboxed JavaScript engine: no access to your files, your apps or your home network, " +
                    "and each call is time-limited. Nuvio and SkyStream repositories are recognised but can't run in this version. " +
                    "Streams that need DRM keys from a provider are not played.", color = TextSecondary, fontSize = 13.sp)
            }
            item("repos") { SectionTitle("Your repositories", "Paste a Vega manifest.json link you trust", "Add" to { add = true }) }
            if (state.repos.isEmpty()) item("norepo") {
                Text("No repositories yet.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(state.repos, key = { "r" + it.url }) { r ->
                HubCard(r.name, r.url, badge = r.ecosystem.label, modifier = Modifier.testTag("script_repo")) {
                    IconButton(onClick = { confirmRemove = r }) { Icon(Icons.Default.Delete, "Remove ${r.name}", tint = TextSecondary) }
                }
            }
            item("inst") { SectionTitle("Installed", "${state.installed.size} sources") }
            items(state.installed, key = { "i" + it.key }) { p ->
                HubCard(p.name, "v${p.version}", badge = p.ecosystem.label, warning = problems[p.key]?.let { "Not loaded: $it" },
                    modifier = Modifier.testTag("script_installed")) {
                    IconButton(onClick = {
                        scope.launch { withContext(Dispatchers.IO) { EcosystemRegistry.uninstall(context, p.key) }; OnlineRepository.clearCaches() }
                    }) { Icon(Icons.Default.Delete, "Uninstall ${p.name}", tint = TextSecondary) }
                }
            }
            if (state.repos.isNotEmpty()) {
                item("avail") { SectionTitle("Available", "${available.size} providers", "Refresh" to { scope.launch { refresh() } }) }
                items(available.take(400), key = { (r, e) -> "a" + r.url + "|" + e.id }) { (r, e) ->
                    val have = installed[r.url to e.id]
                    val upd = have != null && have.version != e.version
                    HubCard(e.name, "${r.ecosystem.label} · v${e.version}" + (e.types.firstOrNull()?.let { " · $it" } ?: ""),
                        badge = if (have != null && !upd) "Installed" else null, modifier = Modifier.testTag("script_available")) {
                        if (have == null || upd) IconButton(onClick = {
                            scope.launch {
                                loading = true
                                EcosystemRegistry.install(context, r, e)
                                    .onSuccess { AppMessages.success(context, "${e.name} installed"); OnlineRepository.clearCaches() }
                                    .onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
                                loading = false
                            }
                        }) { Icon(if (upd) Icons.Default.Refresh else Icons.Default.Download, if (upd) "Update ${e.name}" else "Install ${e.name}", tint = SpotifyGreen) }
                    }
                }
            }
        }
    }
    if (add) UrlDialog("Add script repository", "manifest.json link", "Vega provider repositories (manifest.json). Only add repositories you trust.", { add = false }) { url ->
        add = false
        addRepo(url)
    }
    confirmRemove?.let { r ->
        TsukiDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${r.name}?") },
            text = { Text("Installed sources from it stay until you uninstall them.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; scope.launch(Dispatchers.IO) { EcosystemRegistry.removeRepo(context, r.url) } }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}
