/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.vault

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.vault.VaultEntry
import dev.asfakul.yumeiro.online.vault.VaultLinks
import dev.asfakul.yumeiro.online.vault.VaultSource
import dev.asfakul.yumeiro.online.vault.VaultStore
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.StateMessage
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamFinderDialog
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.launch

/** F1: reads what the page links to (players, files, server buttons). Returns a JSON string of {u, t, f}. */
object VaultCollect {
    const val JS = """(function(){try{
var out=[],seen={};function add(u,t,f){if(!u)return;try{u=new URL(u,location.href).href}catch(e){return}
if(seen[u+f])return;seen[u+f]=1;out.push({u:u,t:(t||'').replace(/\s+/g,' ').trim().slice(0,120),f:f});}
document.querySelectorAll('iframe[src],iframe[data-src],embed[src]').forEach(function(e){add(e.getAttribute('src')||e.getAttribute('data-src'),e.title||e.name,'iframe')});
document.querySelectorAll('video[src],video source[src],audio[src],audio source[src]').forEach(function(e){add(e.getAttribute('src'),document.title,'video')});
document.querySelectorAll('[data-src],[data-url],[data-embed],[data-link],[data-video],[data-file]').forEach(function(e){if(e.tagName==='IFRAME'||e.tagName==='IMG')return;
add(e.getAttribute('data-embed')||e.getAttribute('data-video')||e.getAttribute('data-file')||e.getAttribute('data-link')||e.getAttribute('data-url')||e.getAttribute('data-src'),e.textContent||e.title,'server')});
document.querySelectorAll('a[href]').forEach(function(a){var h=a.getAttribute('href');if(!h||h.charAt(0)==='#'||/^javascript:/i.test(h))return;add(h,a.textContent||a.title||a.getAttribute('aria-label'),'link')});
return JSON.stringify(out.slice(0,2000));}catch(e){return '[]'}})()"""
}

/** What the browser hands the save dialog: the page, its title and the streams its player loaded. */
data class VaultDraft(val title: String, val pageUrl: String, val sources: List<VaultSource>)

/** F1: "Save to Vault" from the browser: edit the title, untick sources you don't want, save. */
@Composable
fun VaultSaveDialog(draft: VaultDraft, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(draft.title) }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<Boolean>().apply { repeat(draft.sources.size) { add(true) } } }
    var saving by remember { mutableStateOf(false) }
    TsukiDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("vault_save_dialog"),
        icon = { Icon(Icons.Default.Inventory2, null, tint = p.accent) },
        title = { Text(stringResource(R.string.save_to_vault)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it.take(160) }, label = { Text(stringResource(R.string.title)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_title_field"))
                Spacer(Modifier.size(12.dp))
                if (draft.sources.isEmpty()) {
                    Text(stringResource(R.string.no_video_sources_were_found_on),
                        color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.testTag("vault_no_sources"))
                } else {
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.vault_sources_found, draft.sources.size, draft.sources.size), color = p.textSecondary, fontSize = 12.sp)
                    draft.sources.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clip(yuRound(10)).clickable(role = Role.Checkbox) { picked[i] = !picked[i] }
                                .heightIn(min = 48.dp).padding(vertical = 2.dp).testTag("vault_pick_$i"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(picked[i], null)
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(s.name, color = p.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.kind.label + " · " + (VaultLinks.hostOf(s.url) ?: ""), color = p.textTertiary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                // With nothing found (or everything unticked) the page itself is kept, so it can be opened again.
                val chosen = draft.sources.filterIndexed { i, _ -> picked[i] }.ifEmpty {
                    listOfNotNull(draft.pageUrl.takeIf(VaultLinks::isSafeUrl)?.let { VaultSource(VaultLinks.hostOf(it) ?: "Page", it, VaultSource.Kind.LINK) })
                }
                val e = VaultStore.newEntry(title, draft.pageUrl, chosen)
                scope.launch {
                    val r = runCatching { VaultStore.save(context, e) }
                    saving = false
                    r.onSuccess { where -> onSaved(if (where != null) tr(R.string.saved_to_vault, "Saved to Vault · %1\$s", where) else tr(R.string.saved_to_vault_2, "Saved to Vault")) }
                        .onFailure { AppMessages.show(context, it.message ?: tr(R.string.couldnt_save_to_the_vault, "Couldn't save to the Vault")) }
                }
            }, modifier = Modifier.testTag("vault_save_confirm")) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("vault_save_cancel")) { Text(stringResource(R.string.cancel)) } },
    )
}

/** F1: the Vault page: saved titles, each opens its sources; rename, share the file, delete; import files. */
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val scope = rememberCoroutineScope()
    val entries by VaultStore.entries.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<VaultEntry?>(null) }
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }
    var page by remember { mutableStateOf<Pair<VaultEntry, VaultSource>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { VaultStore.refresh(context) }.onFailure { error = it.message ?: tr(R.string.couldnt_read_the_vault, "Couldn't read the Vault") } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val n = VaultStore.import(context, uris)
            AppMessages.show(context, when (n) { 0 -> tr(R.string.those_files_are_not_vault_files, "Those files are not Vault files"); 1 -> "1 title added"; else -> tr(R.string.titles_added, "%1\$s titles added", n) })
        }
    }

    fun play(e: VaultEntry, s: VaultSource) {
        when (s.kind) {
            VaultSource.Kind.STREAM, VaultSource.Kind.FILE -> IncomingMedia.open(context, StreamRequest(
                url = s.url, title = e.title, referer = s.referer, userAgent = s.userAgent,
                // An expired stream is found again on the page it was saved from.
                pageUrl = e.pageUrl.takeIf { it.isNotBlank() && s.kind == VaultSource.Kind.STREAM },
            ))
            else -> page = e to s
        }
    }

    page?.let { (e, s) ->
        PageStreamFinderDialog(
            pageUrl = s.url,
            reason = "Opening ${s.name}. Press play; Yumeiro plays the stream in its own player.",
            base = StreamRequest(url = s.url, title = e.title, referer = s.referer),
            onPlay = { req -> page = null; IncomingMedia.open(context, req.copy(title = req.title.ifBlank { e.title })) },
            onDismiss = { page = null },
            auto = s.kind == VaultSource.Kind.EMBED,
        )
    }

    val open = openId?.let { id -> entries?.firstOrNull { it.id == id } }
    BackHandler(enabled = openId != null) { openId = null }
    Column(Modifier.fillMaxSize().testTag("vault_screen")) {
        SubPageHeader(
            title = open?.title ?: "Vault", onBack = { if (openId != null) openId = null else onBack() }, handleBack = openId == null,
            subtitle = if (open != null) (VaultLinks.hostOf(open.pageUrl) ?: "") + " · " + sourcesLabel(open.sources.size)
                else entries?.let { if (it.isEmpty()) tr(R.string.titles_you_save_from_websites, "Titles you save from websites") else androidx.compose.ui.res.pluralStringResource(R.plurals.vault_titles, it.size, it.size) },
        ) {
            if (open == null) IconButton(onClick = { importer.launch(arrayOf("text/html", "application/octet-stream")) }, modifier = Modifier.testTag("vault_import")) {
                Icon(Icons.Default.Download, stringResource(R.string.import_vault_files), tint = p.textSecondary)
            } else {
                IconButton(onClick = { renaming = open }, modifier = Modifier.testTag("vault_rename")) { Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = p.textSecondary) }
                IconButton(onClick = { share(context, open) }, modifier = Modifier.testTag("vault_share")) { Icon(Icons.Default.Share, stringResource(R.string.share_the_vault_file), tint = p.textSecondary) }
                IconButton(onClick = { deleting = open }, modifier = Modifier.testTag("vault_delete")) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = p.textSecondary) }
            }
        }
        val list = entries
        when {
            error != null -> StateMessage(Icons.Default.Inventory2, tr(R.string.couldnt_open_the_vault, "Couldn't open the Vault"), error, primary = "Try again" to {
                error = null; scope.launch { runCatching { VaultStore.refresh(context) }.onFailure { error = it.message } }
            }, testTag = "vault_error")
            list == null -> dev.asfakul.yumeiro.ui.components.SkeletonList(rows = 4, modifier = Modifier.testTag("vault_loading"))
            open != null -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("vault_sources")) {
                if (open.sources.isEmpty()) item { StateMessage(Icons.Default.Inventory2, "No sources saved", "Open the page again and save it once the video plays.", testTag = "vault_sources_empty",
                    primary = open.pageUrl.takeIf(VaultLinks::isSafeUrl)?.let { u -> "Open page" to { page = open to VaultSource("Page", u, VaultSource.Kind.LINK) } }) }
                items(open.sources, key = { it.url + it.kind }) { s -> SourceRow(s) { play(open, s) } }
                if (open.pageUrl.isNotBlank() && open.sources.none { it.url == open.pageUrl }) item {
                    SourceRow(VaultSource(tr(R.string.saved_page, "Saved page"), open.pageUrl, VaultSource.Kind.LINK)) { page = open to VaultSource(tr(R.string.saved_page, "Saved page"), open.pageUrl, VaultSource.Kind.LINK) }
                }
            }
            list.isEmpty() -> StateMessage(Icons.Default.Inventory2, tr(R.string.your_vault_is_empty, "Your Vault is empty"),
                tr(R.string.open_a_website_play_the_title, "Open a website, play the title, then choose Save to Vault in the page menu. Each title is kept as a small page in Download › Yumeiro › Vault."),
                testTag = "vault_empty", primary = "Import files" to { importer.launch(arrayOf("text/html", "application/octet-stream")) })
            else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("vault_list")) {
                items(list, key = { it.id }) { e -> EntryRow(e, onOpen = { openId = e.id }, onRename = { renaming = e }, onShare = { share(context, e) }, onDelete = { deleting = e }) }
            }
        }
    }

    renaming?.let { e ->
        var t by rememberSaveable(e.id) { mutableStateOf(e.title) }
        TsukiDialog(
            onDismissRequest = { renaming = null }, title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(t, { t = it.take(160) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("vault_rename_field")) },
            confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { renaming = null; scope.launch { VaultStore.rename(context, e.id, t) } }, modifier = Modifier.testTag("vault_rename_confirm")) { Text(stringResource(R.string.rename)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { e ->
        TsukiDialog(
            onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_3, e.title)) },
            text = { Text(stringResource(R.string.the_title_and_its_saved_links)) },
            confirmButton = { TextButton(onClick = { deleting = null; if (openId == e.id) openId = null; scope.launch { VaultStore.delete(context, e.id) } }, modifier = Modifier.testTag("vault_delete_confirm")) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun sourcesLabel(n: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.vault_sources, n, n)

private fun share(context: android.content.Context, e: VaultEntry) {
    val f = VaultStore.appFile(context, e.id) ?: return
    val uri = runCatching { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f) }.getOrNull()
    if (uri == null) { AppMessages.show(context, tr(R.string.couldnt_share_the_file, "Couldn't share the file")); return }
    val send = Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, e.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, e.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun EntryRow(e: VaultEntry, onOpen: () -> Unit, onRename: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val p = LocalMtPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(yuRound(14)).background(p.glass).clickable(onClick = onOpen).heightIn(min = 64.dp)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("vault_entry"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(yuRound(10)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, null, tint = p.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(VaultLinks.hostOf(e.pageUrl), sourcesLabel(e.sources.size), VaultStore.savedLabel(e.savedAt)).joinToString(" · "),
                color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("vault_entry_menu")) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_for, e.title), tint = p.textSecondary) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.share_file)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("vault_entry_delete"))
            }
        }
    }
}

@Composable
private fun SourceRow(s: VaultSource, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val icon: ImageVector = when (s.kind) {
        VaultSource.Kind.STREAM -> Icons.Default.Stream
        VaultSource.Kind.FILE -> Icons.Default.PlayArrow
        VaultSource.Kind.EMBED -> Icons.AutoMirrored.Filled.OpenInNew
        VaultSource.Kind.LINK -> Icons.Default.Language
    }
    Row(
        Modifier.fillMaxWidth().clip(yuRound(12)).background(p.glass).clickable(onClick = onClick).heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp).testTag("vault_source"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.kind.label + " · " + (VaultLinks.hostOf(s.url) ?: "torrent"), color = p.textTertiary, fontSize = 11.5.sp, maxLines = 1)
        }
    }
}
