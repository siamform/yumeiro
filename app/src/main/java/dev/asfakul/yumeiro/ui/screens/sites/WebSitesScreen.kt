/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.screens.settings.GuideCard
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamFinderDialog
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * Websites: sites the user saved to watch through Yumeiro's player. A site opens in the page
 * browser (ad / pop-up shield on); the stream its own player starts plays in Yumeiro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebSitesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { SavedSites.init(context) }
    val sites by SavedSites.sites.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<SavedSites.Site?>(null) }
    var renaming by remember { mutableStateOf<SavedSites.Site?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }

    fun add() {
        val err = SavedSites.add(context, input)
        if (err != null) error = err else { input = ""; error = null }
    }

    open?.let { site ->
        PageStreamFinderDialog(
            pageUrl = site.url,
            reason = "Browsing ${site.name}. Press play on any video; Yumeiro plays the stream in its own player.",
            base = null,
            onPlay = { req -> open = null; IncomingMedia.open(context, req.copy(title = req.title.ifBlank { site.name })) },
            onDismiss = { open = null },
        )
    }

    if (help) TsukiDialog(
        onDismissRequest = { help = false },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { GuideCard(SetupGuides.websites, startExpanded = true) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { help = false }, modifier = Modifier.testTag("site_help_close")) { Text("Got it") } },
    )

    renaming?.let { site ->
        var name by remember(site.url) { mutableStateOf(site.name) }
        TsukiDialog(
            onDismissRequest = { renaming = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename website") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("site_rename_field")) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { SavedSites.rename(context, site.url, name); renaming = null }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().testTag("websites_screen")) {
        SubPageHeader(title = "Websites", onBack = onBack, subtitle = if (sites.isEmpty()) "Play videos from any site" else "${sites.size} saved") {
            IconButton(onClick = { help = true }, modifier = Modifier.testTag("site_help")) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "How websites work", tint = p.textSecondary) }
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("websites"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("add") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surfaceContainerHigh)
                        .border(1.dp, p.textPrimary.copy(alpha = 0.06f), RoundedCornerShape(22.dp)).padding(14.dp),
                ) {
                    Text("Add website", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; error = null }, singleLine = true,
                            placeholder = { Text("example.com") }, isError = error != null,
                            leadingIcon = { Icon(Icons.Default.Public, null, tint = p.textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                            modifier = Modifier.weight(1f).testTag("site_add_field"),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(p.accent)
                                .combinedClickable(onClickLabel = "Add website", onClick = { add() }).testTag("site_add"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, "Add website", tint = p.onAccent) }
                    }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (sites.isEmpty()) item("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent2.copy(alpha = 0.18f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text("No websites yet", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Add a site you watch on. Its videos play in Yumeiro.", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else items(sites, key = { it.url }) { site ->
                SiteRow(site, onOpen = { open = site }, onRename = { renaming = site }, onRemove = { SavedSites.remove(context, site.url) })
            }
            // The step-by-step guide lives behind the "?" in the header (not in the list) so the
            // saved sites stay the focus of the page.
            item("help_hint") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClickLabel = "How it works", onClick = { help = true })
                        .padding(vertical = 10.dp).testTag("site_help_hint"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = p.textTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("How it works", color = p.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteRow(site: SavedSites.Site, onOpen: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    val p = LocalMtPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surfaceContainerHigh)
            .combinedClickable(onClickLabel = "Open ${site.name}", onLongClick = { menu = true }, onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp).testTag("site_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(site.name.take(1).uppercase(), color = p.accentBright, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            AsyncImage(
                model = "https://www.google.com/s2/favicons?domain=${site.host}&sz=64", contentDescription = null,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(site.name, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.host, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("site_menu")) { Icon(Icons.Default.MoreVert, "More for ${site.name}", tint = p.textSecondary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onRemove() }, modifier = Modifier.testTag("site_remove"))
            }
        }
    }
}
