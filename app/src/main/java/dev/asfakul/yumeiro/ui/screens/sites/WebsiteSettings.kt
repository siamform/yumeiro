/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.asfakul.yumeiro.online.web.BrowserFilters
import dev.asfakul.yumeiro.online.web.ElementBlocker
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.online.web.SiteHistory
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.components.sheetDragHandle
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette

/**
 * GROUP E1: the Websites page's settings, behind the gear in its header (they used to sit at the bottom of the
 * list, under the saved sites). Browsing switches first, then the list tools that used to crowd the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebsiteSettingsSheet(onDismiss: () -> Unit, onImport: () -> Unit, onExport: (() -> Unit)?) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val remember by SiteHistory.on.collectAsState()
    val ads by BrowserFilters.filterAds.collectAsState()
    val links by BrowserFilters.resolveLinks.collectAsState()
    val redirects by BrowserFilters.stopRedirects.collectAsState()
    var manage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (manage) ManageAdsDialog(onDismiss = { manage = false })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("site_settings_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Website settings", style = dev.asfakul.yumeiro.ui.theme.SectionTitleStyle, color = p.textPrimary, modifier = Modifier.padding(bottom = 6.dp))
            SettingSwitch("Remember sites I visit", "Shown under Recently visited, on this phone only", remember, "recent_switch") { SiteHistory.setOn(context, it) }
            SettingSwitch("Block ads and pop-ups", "Ad sites never load, pop-ups and pop-unders stay closed. The shield in a page's menu turns it off there only", ads, "filter_ads_switch") { BrowserFilters.setFilterAds(context, it) }
            SettingSwitch("Open the real link behind short links", "Waits out the timers and taps Continue on link-shortener pages; a check that needs you is left to you", links, "resolve_links_switch") { BrowserFilters.setResolveLinks(context, it) }
            SettingSwitch("Stop redirects", "A page can't send you to another site by itself; a bar lets you open it. App links never leave Yumeiro", redirects, "stop_redirects_switch") { BrowserFilters.setStopRedirects(context, it) }
            SettingLink(Icons.Default.VisibilityOff, "Manage ads", "Parts of pages you hid with Hide an element", "site_manage_ads") { manage = true }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.textPrimary.copy(alpha = 0.08f))
            SettingLink(Icons.AutoMirrored.Filled.PlaylistAdd, "Add websites from a list", "Paste links, one per line", "site_import") { onDismiss(); onImport() }
            if (onExport != null) SettingLink(Icons.Default.FileUpload, "Send your website list", "As text, to keep or to move to another phone", "site_export") { onDismiss(); onExport() }
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, line: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp).testTag(tag + "_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontSize = 14.sp)
            Text(line, color = p.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(on, null, modifier = Modifier.testTag(tag))
    }
}

@Composable
internal fun SettingLink(icon: ImageVector, title: String, line: String, tag: String, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = title, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accentBright, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.textPrimary, fontSize = 14.sp)
            Text(line, color = p.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary)
    }
}

/** GROUP E1 "Manage ads": every site's hidden parts, each one can be shown again, or a whole site at once. */
@Composable
internal fun ManageAdsDialog(onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var version by remember { mutableIntStateOf(0) }
    val all = remember(version) { ElementBlocker.all(context) }
    TsukiDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("manage_ads"),
        title = { Text("Manage ads") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ads and pop-ups are blocked for every site by the switch in settings. Parts you hid yourself are listed here.", color = p.textSecondary, fontSize = 13.sp)
                if (all.isEmpty()) Text("Nothing hidden yet. In a page, open the menu and pick Hide an element.", color = p.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).testTag("manage_ads_empty"))
                all.forEach { (site, rules) ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(site, color = p.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { ElementBlocker.clearSite(context, site); version++; onChanged() }, modifier = Modifier.testTag("manage_ads_clear_$site")) { Text("Show all") }
                    }
                    rules.forEach { rule ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            IconButton(onClick = { ElementBlocker.removeRule(context, site, rule); version++; onChanged() }, modifier = Modifier.testTag("manage_ads_rule")) {
                                Icon(Icons.Default.Delete, "Show this part again", tint = p.textSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("manage_ads_done")) { Text("Done") } },
    )
}

/** Owner item 10 (Group E): duplicate a saved website under another address, with its own name. */
@Composable
internal fun DuplicateSiteDialog(site: SavedSites.Site, taken: Collection<String>, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    var name by remember(site.url) { mutableStateOf(SavedSites.copyName(site.name, taken)) }
    var address by remember(site.url) { mutableStateOf(site.url) }
    var error by remember(site.url) { mutableStateOf<String?>(null) }
    TsukiDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("site_duplicate_dialog"),
        icon = { Icon(Icons.Default.ContentCopy, null) },
        title = { Text("Duplicate website") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save ${site.name} again under another address, like a mirror domain or one section of the site.", fontSize = 13.sp)
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("site_duplicate_name"))
                OutlinedTextField(address, { address = it; error = null }, singleLine = true, label = { Text("Address") }, isError = error != null,
                    supportingText = error?.let { e -> { Text(e, modifier = Modifier.testTag("site_duplicate_error")) } },
                    modifier = Modifier.fillMaxWidth().testTag("site_duplicate_address"))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = SavedSites.duplicate(context, address, name)
                if (err != null) error = err else onSaved(address)
            }, enabled = address.isNotBlank(), modifier = Modifier.testTag("site_duplicate_save")) { Text("Save copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * GROUP E2: the open tabs, from the browser bar's tab button. Tap one to show it, X closes it; a new tab takes an
 * address or a web search. Drawn inside the page's own window (the browser is a full-screen dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebsiteTabsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    val tabs by WebsiteTab.tabs.collectAsState()
    val front by WebsiteTab.tab.collectAsState()
    var address by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun go() {
        val (url, name) = SiteGo.target(address) ?: return
        address = ""
        onDismiss()
        WebsiteTab.open(context, url, name)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surfaceContainerHigh, dragHandle = sheetDragHandle(), modifier = Modifier.testTag("browser_tabs_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (tabs.size == 1) "1 open tab" else "${tabs.size} open tabs", style = dev.asfakul.yumeiro.ui.theme.SectionTitleStyle, color = p.textPrimary, modifier = Modifier.weight(1f))
                if (tabs.size > 1) TextButton(onClick = { onDismiss(); WebsiteTab.closeAll() }, modifier = Modifier.testTag("browser_tabs_close_all")) { Text("Close all") }
            }
            tabs.forEach { t ->
                val isFront = t.id == front?.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (isFront) p.accent.copy(alpha = 0.16f) else p.textPrimary.copy(alpha = 0.05f))
                        .clickable(onClickLabel = "Show ${t.name}") { onDismiss(); WebsiteTab.switchTo(t.id) }
                        .heightIn(min = 56.dp).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("browser_tab_row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.keep.title.ifBlank { t.name }, color = p.textPrimary, fontWeight = if (isFront) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SavedSites.hostOf(t.keep.address) ?: t.keep.address, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { WebsiteTab.closeTab(t.id); if (WebsiteTab.tabs.value.isEmpty()) onDismiss() }, modifier = Modifier.testTag("browser_tab_close")) {
                        Icon(Icons.Default.Close, "Close ${t.name}", tint = p.textSecondary)
                    }
                }
            }
            Text("New tab", color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                dev.asfakul.yumeiro.ui.screens.watch.WatchSearchField(
                    query = address, onQuery = { address = it.take(800) }, hint = "Address or search", tag = "browser_new_tab_field",
                    modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Add, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, onDone = { go() },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { go() }, enabled = address.isNotBlank(), modifier = Modifier.testTag("browser_new_tab_go")) { Text("Open") }
            }
        }
    }
}
