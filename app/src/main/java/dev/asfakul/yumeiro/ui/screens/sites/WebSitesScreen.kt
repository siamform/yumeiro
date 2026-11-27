/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
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
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * Websites: sites the user saved to watch through Yumeiro's player. A site opens in the page
 * browser (ad / pop-up shield on); the stream its own player starts plays in Yumeiro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebSitesScreen(onBack: () -> Unit, only: SavedSites.Kind? = null) {
    // GROUP F: the Telegram page is its own module now (TelegramHome); `only` never changes for a screen.
    if (only == SavedSites.Kind.TELEGRAM) { TelegramHome(onBack); return }
    val context = LocalContext.current
    val p = LocalMtPalette.current
    LaunchedEffect(Unit) { SavedSites.init(context) }
    val allSites by SavedSites.sites.collectAsState()
    // F-02 split: the Telegram page and the Websites page each show their own kind.
    val sites = remember(allSites, only) { SavedSites.ofKind(allSites, only) }
    val telegramPage = only == SavedSites.Kind.TELEGRAM
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<SavedSites.Site?>(null) }
    var renaming by remember { mutableStateOf<SavedSites.Site?>(null) }
    // GROUP E1: the gear opens the page's settings; owner item 10: a saved site can be duplicated.
    var settings by rememberSaveable { mutableStateOf(false) }
    var duplicating by remember { mutableStateOf<SavedSites.Site?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    // F-04: search (shown from 6 sites), pin, move, import / export.
    var query by rememberSaveable { mutableStateOf("") }
    var importing by rememberSaveable { mutableStateOf(false) }
    val shown = remember(sites, query) { SavedSites.filter(SavedSites.ordered(sites), query) }
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) { query = "" }

    // WEB-ADD-1 / WEB-SEARCH-1 (owner list 5): on the Websites page "+" opens the add card and the search icon
    // opens one box that searches saved sites or goes to an address / web search.
    var adding by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val webScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { dev.asfakul.yumeiro.online.web.SiteHistory.init(context) }
    val visits by dev.asfakul.yumeiro.online.web.SiteHistory.visits.collectAsState()

    /** Reads the new site's own name and icon in the background (the row shows the guess until then). */
    fun readMeta(url: String) {
        if (telegramPage || SavedSites.kindOf(url) != SavedSites.Kind.WEBSITE) return
        webScope.launch {
            val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                dev.asfakul.yumeiro.online.web.SiteMeta.fetch(dev.asfakul.yumeiro.network.NetworkClient.get(context), url)
            } ?: return@launch
            SavedSites.applyMeta(context, url, meta)
        }
    }

    fun add() {
        // F-02: "@name" or a t.me channel link saves the channel's public page (handled in SavedSites.add).
        val typed = input
        val err = SavedSites.wrongPage(typed, only) ?: SavedSites.add(context, typed)
        if (err != null) error = err else {
            input = ""; error = null; adding = false
            SavedSites.find(typed)?.let { readMeta(it.url) }
        }
    }

    // F-02 (ADR-052): the signed-in account opens in place, like a channel.
    var account by rememberSaveable { mutableStateOf(false) }
    val accountEnabled = remember(telegramPage) { telegramPage && dev.asfakul.yumeiro.online.telegram.td.TgAccounts.enabled(context) }
    val accountUnavailable = remember(telegramPage) { if (telegramPage) dev.asfakul.yumeiro.online.telegram.td.TgAccounts.unavailableReason(context) else null }
    // Read off the main thread (SharedPreferences + profile id); refreshed each time the page comes back from the account.
    val signedIn by androidx.compose.runtime.produceState(false, accountEnabled, account) {
        if (accountEnabled) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.online.telegram.td.TgAccounts.signedInHint(context) }
    }
    // TG-ME-1 (owner list 8A): the signed-in user's name and photo, as the account page last read them.
    val me by androidx.compose.runtime.produceState<dev.asfakul.yumeiro.online.telegram.td.TgAccounts.MeHint?>(null, accountEnabled, account, signedIn) {
        value = if (accountEnabled && signedIn) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.online.telegram.td.TgAccounts.meHint(context) } else null
    }
    if (account && accountEnabled) {
        TelegramAccountScreen(onBack = { account = false }, onPlay = { req -> IncomingMedia.open(context, req) })
        return
    }

    // F-02 (ADR-042): a saved public Telegram channel opens its video posts, not the page browser.
    val tgChannel = open?.let { site -> dev.asfakul.yumeiro.online.telegram.TelegramPublic.channelOf(site.url) }
        ?.takeIf { dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.TELEGRAM_PUBLIC) }
    if (tgChannel != null) {
        val site = open!!
        TelegramChannelScreen(
            channel = tgChannel, name = site.name, onBack = { open = null },
            onPlay = { req -> IncomingMedia.open(context, req) },
            onSignIn = if (accountEnabled) ({ open = null; account = true }) else null,
        )
        return
    }

    // OL-1: websites open in the kept tab: playing a video, Downloads, Minimize or Back hide it, never close it.
    // TG-TAB-1: the Telegram page shares this screen; the kept website tab (and its "Open tab" card) belongs to
    // the Websites page only. Before, the last website browsed showed as a shortcut on the Telegram page.
    val tabs = remember(telegramPage) { !telegramPage && dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.WEBSITE_TAB) }
    val tab by WebsiteTab.tab.collectAsState()
    val allTabs by WebsiteTab.tabs.collectAsState()
    var tabsOpen by rememberSaveable { mutableStateOf(false) }
    /** An address or web search that is not saved: opens in the browser (and lands in Recently visited). */
    fun openUnsaved(url: String, name: String) {
        if (tabs) WebsiteTab.open(context, url, name) else open = SavedSites.Site(url, name)
    }
    tab?.takeIf { tabs && it.visible }?.let { t -> androidx.compose.runtime.key(t.id) {
        PageStreamFinderDialog(
            pageUrl = t.keep.pageUrl,
            reason = "Browsing ${t.name}. Press play on any video; Yumeiro plays the stream in its own player.",
            base = null,
            keep = t.keep,
            onMinimize = { WebsiteTab.minimize(byPlay = false) },
            browser = true,
            onPlay = { req -> WebsiteTab.minimize(byPlay = true); IncomingMedia.open(context, req.copy(title = req.title.ifBlank { t.name })) },
            onDismiss = { WebsiteTab.close() },
            // GROUP E2: several tabs; a tapped link to another site opens in a new one.
            tabCount = allTabs.size,
            onTabs = { tabsOpen = true },
            onNewTab = { url -> WebsiteTab.open(context, url, SavedSites.defaultName(url)) },
            onCloseTab = { WebsiteTab.close() },
            overlay = { if (tabsOpen) WebsiteTabsSheet(onDismiss = { tabsOpen = false }) },
        )
    } }

    open?.let { site ->
        PageStreamFinderDialog(
            pageUrl = site.url,
            reason = "Browsing ${site.name}. Press play on any video; Yumeiro plays the stream in its own player.",
            base = null,
            onPlay = { req -> open = null; IncomingMedia.open(context, req.copy(title = req.title.ifBlank { site.name })) },
            onDismiss = { open = null },
            browser = !telegramPage,
        )
    }

    if (help) TsukiDialog(
        onDismissRequest = { help = false },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { GuideCard(if (telegramPage) SetupGuides.telegram else SetupGuides.websites, startExpanded = true) } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { help = false }, modifier = Modifier.testTag("site_help_close")) { Text("Got it") } },
    )

    if (importing) {
        var text by rememberSaveable { mutableStateOf("") }
        TsukiDialog(
            onDismissRequest = { importing = false },
            title = { Text(if (telegramPage) "Add channels from a list" else "Add websites from a list") },
            text = {
                Column {
                    Text(if (telegramPage) "Paste @names or t.me links, one per line. Channels you already saved stay as they are." else "Paste links, one per line. Sites you already saved stay as they are.", fontSize = 13.sp)
                    OutlinedTextField(text, { text = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("site_import_field"))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val r = SavedSites.importText(context, text)
                    importing = false
                    dev.asfakul.yumeiro.ui.components.AppMessages.info(context, buildString {
                        val noun = if (telegramPage) "channel" else "website"
                        append(if (r.added == 1) "1 $noun added" else "${r.added} ${noun}s added")
                        if (r.duplicates > 0) append(", ${r.duplicates} already saved")
                        if (r.bad > 0) append(if (telegramPage) ", ${r.bad} not channel links" else ", ${r.bad} not web addresses")
                        if (r.overLimit > 0) append(", ${r.overLimit} over the 200 limit")
                    })
                }, enabled = text.isNotBlank(), modifier = Modifier.testTag("site_import_confirm")) { Text("Add") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }

    fun exportList() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, SavedSites.export(sites))
        runCatching { context.startActivity(android.content.Intent.createChooser(send, if (telegramPage) "Share channel list" else "Share website list").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (settings) WebsiteSettingsSheet(onDismiss = { settings = false }, onImport = { importing = true }, onExport = if (sites.isNotEmpty()) ({ exportList() }) else null)
    duplicating?.let { site ->
        DuplicateSiteDialog(site, allSites.map { it.name }, onDismiss = { duplicating = null }, onSaved = { url ->
            duplicating = null
            dev.asfakul.yumeiro.ui.components.AppMessages.info(context, "Copy saved")
            SavedSites.find(url)?.let { readMeta(it.url) }
        })
    }

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

    Column(Modifier.fillMaxSize().testTag(if (telegramPage) "telegram_screen" else "websites_screen")) {
        SubPageHeader(
            title = if (telegramPage) "Telegram" else "Websites", onBack = onBack,
            subtitle = if (sites.isNotEmpty()) "${sites.size} saved" else if (telegramPage) "Public channels, no login" else "Play videos from any site",
        ) {
            if (!telegramPage) {
                dev.asfakul.yumeiro.ui.components.SearchIconButton(searchOpen, { searchOpen = !searchOpen; if (!searchOpen) query = "" }, "site_search_toggle", "Search or type an address")
                IconButton(onClick = { adding = !adding }, modifier = Modifier.testTag("site_add_open")) { Icon(Icons.Default.Add, if (adding) "Close add website" else "Add website", tint = if (adding) p.accent else p.textPrimary) }
            }
            if (!telegramPage) {
                IconButton(onClick = { settings = true }, modifier = Modifier.testTag("site_settings")) { Icon(Icons.Default.Settings, "Website settings", tint = p.textSecondary) }
            }
            // GROUP E1: no Share button on the website page (the Telegram page is TelegramHome; it returns above).
            IconButton(onClick = { help = true }, modifier = Modifier.testTag("site_help")) { Icon(Icons.AutoMirrored.Filled.HelpOutline, if (telegramPage) "How Telegram works" else "How websites work", tint = p.textSecondary) }
        }
        val addCard: @Composable () -> Unit = {
                Column(
                    Modifier.fillMaxWidth().clip(yuRound(22)).background(p.surfaceContainerHigh)
                        .border(1.dp, p.textPrimary.copy(alpha = 0.06f), yuRound(22)).padding(14.dp),
                ) {
                    Text(when (only) { SavedSites.Kind.TELEGRAM -> "Add a public Telegram channel"; SavedSites.Kind.WEBSITE -> "Add a website"; null -> "Add a website or Telegram channel" }, color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; error = null }, singleLine = true,
                            placeholder = { Text(if (telegramPage) "@channel or t.me/channel" else "example.com or @channel") }, isError = error != null,
                            leadingIcon = { Icon(Icons.Default.Public, null, tint = p.textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            shape = yuRound(16),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                            modifier = Modifier.weight(1f).testTag("site_add_field"),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(52.dp).clip(yuRound(16)).background(p.accent)
                                .combinedClickable(onClickLabel = if (telegramPage) "Add channel" else "Add website", onClick = { add() }).testTag("site_add"),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, if (telegramPage) "Add channel" else "Add website", tint = p.onAccent) }
                    }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("websites"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // OL-1: the website kept in the background, one tap from where it was.
            tab?.takeIf { tabs && !it.visible }?.let { t ->
                item("website_tab_card") {
                    Row(
                        Modifier.fillMaxWidth().clip(yuRound(22)).background(p.surfaceContainerHigh)
                            .clickable { WebsiteTab.resume() }.heightIn(min = 56.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).testTag("website_tab_card"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (allTabs.size > 1) "${allTabs.size} open tabs · ${t.name}" else "Open tab · ${t.name}", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.keep.title.ifBlank { t.keep.address }, color = p.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.TextButton(onClick = { WebsiteTab.resume() }, modifier = Modifier.testTag("website_tab_resume")) { Text("Resume") }
                        IconButton(onClick = { if (allTabs.size > 1) WebsiteTab.closeAll() else WebsiteTab.close() }, modifier = Modifier.testTag("website_tab_close")) { Icon(Icons.Default.Close, if (allTabs.size > 1) "Close all tabs" else "Close tab", tint = p.textSecondary) }
                    }
                }
            }
            // TG-PAGE-1: a clear way in. The card says whether this profile is signed in and has a real button;
            // when sign-in can't run on this phone it says why instead of disappearing.
            if (telegramPage) item("add") { addCard() }
            if (accountEnabled) item("tg_account_card") {
                Row(
                    Modifier.fillMaxWidth().clip(yuRound(22)).background(p.surfaceContainerHigh)
                        .clickable(onClickLabel = if (signedIn) "Open your chats" else "Sign in to Telegram") { account = true }
                        .heightIn(min = 56.dp).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp).testTag("tg_account_card"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (signedIn) {
                        TelegramAvatar(me?.photoPath, me?.name ?: "Telegram", size = 44.dp, modifier = Modifier.testTag("tg_account_avatar"))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (signedIn) me?.name ?: "Your Telegram account" else "Sign in to Telegram", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (signedIn) "Signed in. Videos and audio from your chats, groups and channels, including private ones you have joined"
                            else "Watch videos from your chats, groups and the private channels you have joined. Telegram sends the code; Yumeiro never sees your password",
                            color = p.textSecondary, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.Button(
                        onClick = { account = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("tg_account_button"),
                    ) { Text(if (signedIn) "Open" else "Sign in", fontWeight = FontWeight.SemiBold) }
                }
            } else if (telegramPage && accountUnavailable != null) item("tg_account_unavailable") {
                Column(
                    Modifier.fillMaxWidth().clip(yuRound(22)).background(p.surfaceContainerHigh).padding(16.dp).testTag("tg_account_unavailable"),
                ) {
                    Text("Sign-in isn't available here", color = p.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(accountUnavailable, color = p.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            // TG-ADD-1 (owner list 8A): on the Telegram page, adding a public channel comes first.
            if (!telegramPage && (adding || sites.isEmpty())) item("add") { addCard() }
            if (!telegramPage && searchOpen) item("search") {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Search your sites or type an address") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = p.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { SiteGo.target(query)?.let { (url, name) -> openUnsaved(url, name) } }),
                        shape = yuRound(16),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("site_search"),
                    )
                    SiteGo.target(query)?.let { (url, name) ->
                        val web = SiteGo.isSearch(url)
                        Row(
                            Modifier.fillMaxWidth().clip(yuRound(16)).background(p.surfaceContainerHigh)
                                .combinedClickable(onClickLabel = if (web) "Search the web" else "Open", onClick = { openUnsaved(url, name) })
                                .padding(horizontal = 14.dp, vertical = 12.dp).testTag(if (web) "site_web_search" else "site_go"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (web) Icons.Default.Search else Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (web) "Search the web for \"${query.trim()}\"" else "Go to ${SavedSites.hostOf(url) ?: url}", color = p.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (telegramPage && sites.size >= 6) item("search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(if (telegramPage) "Search your channels" else "Search your websites") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = p.textSecondary) },
                    shape = yuRound(16),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, cursorColor = p.accent),
                    modifier = Modifier.fillMaxWidth().testTag("site_search"),
                )
            }
            if (sites.isNotEmpty() && shown.isEmpty()) item("no_match") {
                Text(if (telegramPage) "No saved channel matches \"$query\"" else "No saved website matches \"$query\"", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(8.dp).testTag("site_no_match"))
            }
            if (sites.isEmpty()) item("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(yuRound(20))
                            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent2.copy(alpha = 0.18f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Language, null, tint = p.accentBright, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (telegramPage) "No channels yet" else "No websites yet", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (telegramPage) "Add a public channel with @name or a t.me link. Its video posts play in Yumeiro." else "Add a site you watch on. Its videos play in Yumeiro.", color = p.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else items(shown, key = { it.url }) { site ->
                SiteRow(
                    site, onOpen = {
                        // OL-1: Telegram channels keep their own screen; every other site opens in the kept tab.
                        val tg = dev.asfakul.yumeiro.online.telegram.TelegramPublic.channelOf(site.url) != null &&
                            dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.TELEGRAM_PUBLIC)
                        if (tabs && !tg) WebsiteTab.open(context, site.url, site.name) else open = site
                    }, onRename = { renaming = site }, onRemove = { SavedSites.remove(context, site.url) },
                    onDuplicate = if (telegramPage || SavedSites.kindOf(site.url) != SavedSites.Kind.WEBSITE) null else ({ duplicating = site }),
                    onPin = { SavedSites.setPinned(context, site.url, !site.pinned) },
                    onMove = if (query.isBlank()) ({ up -> SavedSites.moveSite(context, site.url, up) }) else null,
                    modifier = Modifier.animateItem(),
                )
            }
            // WEB-RECENT-1 (owner list 5): sites opened in the browser come here by themselves; + saves one.
            val recent = if (telegramPage) emptyList() else dev.asfakul.yumeiro.online.web.SiteHistory.unsaved(visits, allSites).let { v ->
                // WEB-RECENT-2: a typed address (archive.org/details/…) also matches the site it belongs to.
                val qHost = SavedSites.normalize(query.trim())?.let { SavedSites.hostOf(it) }
                if (query.isBlank()) v else v.filter { it.name.contains(query.trim(), true) || it.url.contains(query.trim(), true) || (qHost != null && SavedSites.hostOf(it.url) == qHost) }
            }.take(10)
            if (recent.isNotEmpty()) {
                item("recent_head") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recently visited", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { dev.asfakul.yumeiro.online.web.SiteHistory.clear(context) }, modifier = Modifier.testTag("recent_clear")) { Text("Clear") }
                    }
                }
                items(recent, key = { "recent_" + it.url }) { v ->
                    RecentRow(v, onOpen = { openUnsaved(v.url, v.name) }, onSave = {
                        if (SavedSites.add(context, v.url, v.name) == null) { SavedSites.find(v.url)?.let { readMeta(it.url) } }
                    }, onForget = { dev.asfakul.yumeiro.online.web.SiteHistory.forget(context, v.url) }, modifier = Modifier.animateItem())
                }
            }
            // The step-by-step guide lives behind the "?" in the header (not in the list) so the
            // saved sites stay the focus of the page.
            if (telegramPage) item("help_hint") {
                Row(
                    Modifier.fillMaxWidth().clip(yuRound(14)).combinedClickable(onClickLabel = "How it works", onClick = { help = true })
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
internal fun SiteRow(
    site: SavedSites.Site, onOpen: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit,
    onPin: () -> Unit = {}, onMove: ((up: Boolean) -> Unit)? = null, onDuplicate: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(yuRound(18)).background(p.surfaceContainerHigh)
            .combinedClickable(onClickLabel = "Open ${site.name}", onLongClick = { menu = true }, onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp).testTag("site_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TG-AVATAR-1 (owner list 8A): a saved Telegram channel shows its own photo, not t.me's favicon.
        val channel = remember(site.url) { dev.asfakul.yumeiro.online.telegram.TelegramPublic.channelOf(site.url) }
        if (channel != null) {
            val context = LocalContext.current
            val photo by androidx.compose.runtime.produceState<String?>(null, channel) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.online.telegram.TelegramAvatars.cached(context, channel) }
                dev.asfakul.yumeiro.online.telegram.TelegramAvatars.get(context, channel)?.let { value = it }
            }
            TelegramAvatar(photo, site.name, size = 44.dp, modifier = Modifier.testTag("site_avatar"))
        } else Box(Modifier.size(44.dp).clip(yuRound(14)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(site.name.take(1).uppercase(), color = p.accentBright, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            AsyncImage(
                model = site.iconUrl ?: "https://www.google.com/s2/favicons?domain=${site.host}&sz=64", contentDescription = null,
                modifier = Modifier.size(26.dp).clip(yuRound(6)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(site.name, color = p.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (site.pinned) "Pinned · ${site.host}" else site.host, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("site_menu")) { Icon(Icons.Default.MoreVert, "More for ${site.name}", tint = p.textSecondary) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (site.pinned) "Unpin" else "Pin to top") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; onPin() }, modifier = Modifier.testTag("site_pin"))
                if (onMove != null) {
                    DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menu = false; onMove(true) }, modifier = Modifier.testTag("site_move_up"))
                    DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menu = false; onMove(false) }, modifier = Modifier.testTag("site_move_down"))
                }
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                if (onDuplicate != null) DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onDuplicate() }, modifier = Modifier.testTag("site_duplicate"))
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onRemove() }, modifier = Modifier.testTag("site_remove"))
            }
        }
    }
}

/** WEB-SEARCH-1: what the Websites search box goes to: an address, or a web search for anything else. Pure. */
internal object SiteGo {
    const val SEARCH = "https://duckduckgo.com/?q="

    /** (url, name) for [query], or null when it is empty. */
    fun target(query: String): Pair<String, String>? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val url = SavedSites.normalize(q)?.takeIf { SavedSites.kindOf(it) == SavedSites.Kind.WEBSITE && (q.contains('.') || q.contains("://")) }
        return if (url != null) url to SavedSites.defaultName(url)
        else SEARCH + java.net.URLEncoder.encode(q, "UTF-8") to q.take(40)
    }

    fun isSearch(url: String) = url.startsWith(SEARCH)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(v: dev.asfakul.yumeiro.online.web.SiteHistory.Visit, onOpen: () -> Unit, onSave: () -> Unit, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalMtPalette.current
    val host = remember(v.url) { SavedSites.hostOf(v.url) ?: v.url }
    Row(
        modifier.fillMaxWidth().clip(yuRound(18)).background(p.surfaceContainerHigh.copy(alpha = 0.6f))
            .combinedClickable(onClickLabel = "Open ${v.name}", onLongClick = onForget, onClick = onOpen)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).testTag("recent_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(yuRound(12)).background(p.accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Text(v.name.take(1).uppercase(), color = p.accentBright, fontWeight = FontWeight.Bold)
            AsyncImage(model = v.iconUrl ?: "https://www.google.com/s2/favicons?domain=$host&sz=64", contentDescription = null, modifier = Modifier.size(22.dp).clip(yuRound(5)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, color = p.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, color = p.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSave, modifier = Modifier.testTag("recent_save")) { Icon(Icons.Default.Add, "Save ${v.name}", tint = p.accent) }
        IconButton(onClick = onForget, modifier = Modifier.testTag("recent_forget")) { Icon(Icons.Default.Close, "Remove ${v.name} from recent", tint = p.textSecondary) }
    }
}

