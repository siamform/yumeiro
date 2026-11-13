/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.sources.UniversalAdd
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.online.InputDialog
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Links and files opened from other apps, waiting for the user to confirm. */
object UniversalAddInbox {
    sealed interface Item { data class Link(val text: String) : Item; data class File(val uri: Uri) : Item }
    val pending = MutableStateFlow<Item?>(null)

    private val SCHEMES = setOf("cloudstreamrepo", "stremio", "aniyomi", "tachiyomi", "mihon", "mangayomi")

    /** True when [uri] is an add-extension link or file this inbox takes. */
    fun offer(uri: Uri, displayName: String? = null): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in SCHEMES) { pending.value = Item.Link(uri.toString()); return true }
        val name = (displayName ?: uri.lastPathSegment.orEmpty()).lowercase()
        if (scheme == "content" || scheme == "file") {
            if (name.endsWith(".cs3") || name.endsWith(".apk")) { pending.value = Item.File(uri); return true }
            return false
        }
        val path = uri.path.orEmpty().lowercase()
        val indexFile = listOf(".cs3", "/repo.json", "/plugins.json", "/index.min.json", "/manifest.json").any { path.endsWith(it) }
        if ((scheme == "https" || scheme == "http") && (indexFile || uri.host.equals("cs.repo", true))) {
            pending.value = Item.Link(uri.toString()); return true
        }
        return false
    }
}

/** Runs [UniversalAdd] and reports in the app's message bar. Returns the error text or null. */
internal suspend fun runUniversalAdd(context: android.content.Context, item: UniversalAddInbox.Item, onServer: (UniversalAdd.Outcome) -> Unit = {}): String? {
    val r = when (item) {
        is UniversalAddInbox.Item.Link -> UniversalAdd.add(context, item.text)
        is UniversalAddInbox.Item.File -> {
            val name = runCatching {
                context.contentResolver.query(item.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            UniversalAdd.addFile(context, item.uri, name)
        }
    }
    return r.fold(
        { out ->
            if (out.needsSignIn != null) onServer(out) else { AppMessages.success(context, out.message); OnlineRepository.clearCaches() }
            null
        },
        { e -> if (e is ExtensionReview.Declined) null else OnlineErrors.friendly(e) },
    )
}

/** "Add anything" dialog: paste any extension, repository, add-on or server link. */
@Composable
fun UniversalAddDialog(onDismiss: () -> Unit, onServer: (UniversalAdd.Outcome) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    InputDialog(
        title = "Add anything", label = "Link or short code",
        hint = ".cs3, repo.json, plugins.json, index.min.json, manifest.json, stremio://, aniyomi://, a GitHub repo or a server",
        confirm = "Add", onDismiss = onDismiss,
    ) { text, done ->
        scope.launch {
            val err = runUniversalAdd(context, UniversalAddInbox.Item.Link(text)) { onServer(it) }
            done(err); if (err == null) onDismiss()
        }
    }
}

/** Confirms links or files that other apps opened with Yumeiro, then adds them. */
@Composable
fun UniversalAddHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item by UniversalAddInbox.pending.collectAsState()
    val current = item ?: return
    val what = when (current) {
        is UniversalAddInbox.Item.Link -> current.text
        is UniversalAddInbox.Item.File -> current.uri.lastPathSegment ?: "this file"
    }
    TsukiDialog(
        onDismissRequest = { UniversalAddInbox.pending.value = null },
        title = { Text("Add to Yumeiro?") },
        text = {
            Column {
                Text("Yumeiro will check what this is (extension, repository, add-on or server) and add it with the right engine.", color = TextSecondary, fontSize = 13.sp)
                Text(what.take(200), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UniversalAddInbox.pending.value = null
                scope.launch { runUniversalAdd(context, current) { out -> AppMessages.show(context, out.message) }?.let { AppMessages.error(context, it) } }
            }, modifier = Modifier.testTag("universal_add_confirm")) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { UniversalAddInbox.pending.value = null }) { Text("Cancel") } },
    )
}
