/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.ui.components.sheetDragHandle
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/** UX-MARKET-1: web page for a repository address (GitHub raw / jsDelivr → the GitHub repo), else null. */
internal fun repoPage(url: String?): String? {
    val u = url?.trim().orEmpty()
    if (!u.startsWith("https://")) return null
    val path = u.removePrefix("https://").split('/')
    return when (path.firstOrNull()?.lowercase()) {
        "raw.githubusercontent.com" -> path.getOrNull(1)?.let { o -> path.getOrNull(2)?.let { r -> "https://github.com/$o/$r" } }
        "github.com" -> path.getOrNull(1)?.let { o -> path.getOrNull(2)?.let { r -> "https://github.com/$o/$r" } }
        "cdn.jsdelivr.net" -> if (path.getOrNull(1) == "gh") path.getOrNull(2)?.let { o -> path.getOrNull(3)?.substringBefore('@')?.let { r -> "https://github.com/$o/$r" } } else null
        else -> null
    }
}

/**
 * UX-MARKET-1 (R19): the market card cut the description to two lines and had no way to see the rest (authors,
 * content types, repository, compatibility). Tapping a card opens this sheet; it keeps the card's actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketInfoSheet(
    ext: RemoteExtension, installedVersion: Int?, hasUpdate: Boolean,
    onInstall: () -> Unit, onManage: () -> Unit, onDismiss: () -> Unit,
) {
    val p = LocalMtPalette.current
    val uri = LocalUriHandler.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("market_info_${ext.internalName}")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExtensionIcon(ext.iconUrl?.replace("%size%", "128"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ext.name, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(listOfNotNull("v${ext.version}", ext.statusLabel, ext.fileSize?.takeIf { it > 0 }?.let { formatSize(it) }).joinToString(" · "), color = p.textSecondary, fontSize = 12.sp)
                }
            }
            ext.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp)); Text(it, color = p.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            val rows = listOfNotNull(
                ext.authors.takeIf { it.isNotEmpty() }?.let { "By" to it.joinToString(", ") },
                ext.language?.takeIf { it.isNotBlank() }?.let { "Language" to it.uppercase() },
                ext.tvTypes.takeIf { it.isNotEmpty() }?.let { "Content" to it.joinToString(", ") },
                "Repository" to ext.repoName,
                ext.copies.takeIf { it > 1 }?.let { "Copies" to "Published in $it repositories" },
                "Installed" to (installedVersion?.let { "v$it" + if (hasUpdate) " (v${ext.version} available)" else "" } ?: "No"),
                "Works with" to if (ext.apiVersion <= OnlineRuntime.API_VERSION) "This version of Yumeiro" else "A newer Yumeiro (needs plugin API ${ext.apiVersion})",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (k, v) ->
                    Row { Text(k, color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.width(96.dp)); Text(v, color = p.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
                }
            }
            ext.compatNote?.let { Spacer(Modifier.height(10.dp)); Text(it, color = WarnColor, fontSize = 13.sp, lineHeight = 18.sp) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                repoPage(ext.repoUrl)?.let { page ->
                    OutlinedButton(onClick = { runCatching { uri.openUri(page) } }, modifier = Modifier.heightIn(min = 48.dp).testTag("market_info_repo")) { Text("Repository page", color = p.textPrimary) }
                }
                Spacer(Modifier.weight(1f))
                when {
                    hasUpdate -> Button(onClick = { onInstall(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent), modifier = Modifier.heightIn(min = 48.dp)) { Text("Update") }
                    installedVersion != null -> OutlinedButton(onClick = { onManage(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage", color = p.textPrimary) }
                    else -> Button(
                        onClick = { onInstall(); onDismiss() }, enabled = !ext.isDown && ext.apiVersion <= OnlineRuntime.API_VERSION,
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("market_info_install"),
                    ) { Text(if (ext.isDown) "Down" else "Install") }
                }
            }
        }
    }
}
